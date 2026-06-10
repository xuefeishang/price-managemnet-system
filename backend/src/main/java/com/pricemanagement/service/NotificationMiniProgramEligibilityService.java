package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.NotificationMiniProgramEligibility;
import com.pricemanagement.entity.NotificationMiniProgramSubscription;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationMiniProgramEligibilityRepository;
import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationMiniProgramEligibilityService {

    private static final int LOW_BALANCE_THRESHOLD = 1;
    private static final int REBUILD_PAGE_SIZE = 200;

    private final UserRepository userRepository;
    private final NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    private final NotificationMiniProgramEligibilityRepository eligibilityRepository;
    private final NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    private final ApplicationEventPublisher eventPublisher;

    public void requestRefresh(Long userId) {
        if (userId != null) {
            eventPublisher.publishEvent(new UserEligibilityRefreshRequested(userId));
        }
    }

    @Transactional
    public NotificationMiniProgramEligibility refreshUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        List<NotificationMiniProgramSubscription> subscriptions =
                subscriptionRepository.findByUserIdOrderByNotificationTypeAscTemplateIdAsc(userId);
        return saveEvaluation(user, subscriptions, runtimeConfigService.configuredTemplates());
    }

    public Evaluation evaluate(User user, List<NotificationMiniProgramSubscription> subscriptions) {
        return evaluate(user, subscriptions, runtimeConfigService.configuredTemplates());
    }

    @Transactional
    public int rebuildAll() {
        int pageNumber = 0;
        int refreshed = 0;
        Page<User> page;
        List<NotificationMiniProgramRuntimeConfigService.TemplateConfig> templates =
                runtimeConfigService.configuredTemplates();
        do {
            page = userRepository.findActiveMiniProgramSubscriptionTargets(
                    CommonStatus.ACTIVE,
                    null,
                    "",
                    PageRequest.of(pageNumber, REBUILD_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            if (!page.getContent().isEmpty()) {
                List<Long> userIds = page.getContent().stream().map(User::getId).toList();
                var subscriptionsByUser = subscriptionRepository.findByUserIdIn(userIds).stream()
                        .collect(Collectors.groupingBy(NotificationMiniProgramSubscription::getUserId));
                page.getContent().forEach(user -> saveEvaluation(
                        user,
                        subscriptionsByUser.getOrDefault(user.getId(), List.of()),
                        templates));
                refreshed += page.getNumberOfElements();
            }
            pageNumber++;
        } while (page.hasNext());
        return refreshed;
    }

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initializeAfterStartup() {
        rebuildWithLogging("application-ready");
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onMiniProgramConfigChanged(
            NotificationMiniProgramRuntimeConfigService.MiniProgramConfigChangedEvent event) {
        rebuildWithLogging("mini-program-config-changed");
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onUserEligibilityRefreshRequested(UserEligibilityRefreshRequested event) {
        try {
            refreshUser(event.userId());
        } catch (Exception ex) {
            log.error("Failed to refresh mini-program eligibility snapshot: userId={}", event.userId(), ex);
        }
    }

    @Scheduled(cron = "${notification.mini-program.eligibility-reconcile-cron:0 30 3 * * ?}")
    public void reconcileScheduled() {
        rebuildWithLogging("scheduled-reconciliation");
    }

    private void rebuildWithLogging(String trigger) {
        try {
            int refreshed = rebuildAll();
            log.info("Rebuilt mini-program eligibility snapshots: trigger={}, users={}", trigger, refreshed);
        } catch (Exception ex) {
            log.error("Failed to rebuild mini-program eligibility snapshots: trigger={}", trigger, ex);
        }
    }

    private NotificationMiniProgramEligibility saveEvaluation(
            User user,
            List<NotificationMiniProgramSubscription> subscriptions,
            List<NotificationMiniProgramRuntimeConfigService.TemplateConfig> templates) {
        Evaluation evaluation = evaluate(user, subscriptions, templates);
        NotificationMiniProgramEligibility eligibility = eligibilityRepository.findByUserId(user.getId())
                .orElseGet(NotificationMiniProgramEligibility::new);
        eligibility.setUserId(user.getId());
        eligibility.setRowStatus(evaluation.rowStatus());
        eligibility.setOpenidBound(evaluation.openidBound());
        eligibility.setConfiguredTemplateCount(evaluation.configuredTemplateCount());
        eligibility.setAuthorizedTemplateCount(evaluation.authorizedTemplateCount());
        eligibility.setAvailableTotal(evaluation.availableTotal());
        eligibility.setLastAuthorizedTime(evaluation.lastAuthorizedTime());
        eligibility.setConfigFingerprint(evaluation.configFingerprint());
        return eligibilityRepository.save(eligibility);
    }

    private Evaluation evaluate(
            User user,
            List<NotificationMiniProgramSubscription> subscriptions,
            List<NotificationMiniProgramRuntimeConfigService.TemplateConfig> templates) {
        List<NotificationMiniProgramSubscription> rows = subscriptions == null ? List.of() : subscriptions;
        Set<String> templateKeys = templates.stream()
                .map(template -> key(template.notificationType(), template.templateId()))
                .collect(Collectors.toSet());
        List<NotificationMiniProgramSubscription> configuredRows = rows.stream()
                .filter(row -> templateKeys.contains(key(row.getNotificationType(), row.getTemplateId())))
                .toList();
        List<NotificationMiniProgramSubscription> authorizedRows = configuredRows.stream()
                .filter(row -> row.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT)
                .filter(row -> row.getAvailableCount() != null && row.getAvailableCount() > 0)
                .toList();
        boolean openidBound = StringUtils.hasText(user.getWechatOpenid());
        NotificationMiniProgramEligibility.RowStatus rowStatus;
        if (!openidBound) {
            rowStatus = NotificationMiniProgramEligibility.RowStatus.UNBOUND;
        } else if (templates.isEmpty()) {
            rowStatus = NotificationMiniProgramEligibility.RowStatus.LOW_BALANCE;
        } else if (configuredRows.stream().anyMatch(row ->
                row.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.REJECT
                        || row.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.BAN)) {
            rowStatus = NotificationMiniProgramEligibility.RowStatus.REJECTED;
        } else if (authorizedRows.isEmpty() || authorizedRows.stream()
                .anyMatch(row -> row.getAvailableCount() <= LOW_BALANCE_THRESHOLD)) {
            rowStatus = NotificationMiniProgramEligibility.RowStatus.LOW_BALANCE;
        } else {
            rowStatus = NotificationMiniProgramEligibility.RowStatus.NORMAL;
        }
        int availableTotal = authorizedRows.stream()
                .map(NotificationMiniProgramSubscription::getAvailableCount)
                .mapToInt(Integer::intValue)
                .sum();
        LocalDateTime lastAuthorizedTime = configuredRows.stream()
                .map(NotificationMiniProgramSubscription::getLastAuthorizedTime)
                .filter(time -> time != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new Evaluation(
                rowStatus,
                openidBound,
                templates.size(),
                authorizedRows.size(),
                availableTotal,
                lastAuthorizedTime,
                fingerprint(templates));
    }

    private String fingerprint(List<NotificationMiniProgramRuntimeConfigService.TemplateConfig> templates) {
        String value = templates.stream()
                .map(template -> key(template.notificationType(), template.templateId()))
                .sorted()
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算小程序模板配置指纹", ex);
        }
    }

    private String key(String notificationType, String templateId) {
        return notificationType + "::" + templateId;
    }

    public record Evaluation(
            NotificationMiniProgramEligibility.RowStatus rowStatus,
            boolean openidBound,
            int configuredTemplateCount,
            int authorizedTemplateCount,
            int availableTotal,
            LocalDateTime lastAuthorizedTime,
            String configFingerprint) {
    }

    public record UserEligibilityRefreshRequested(Long userId) {
    }
}
