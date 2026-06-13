package com.pricemanagement.service;

import com.pricemanagement.dto.NotificationMiniProgramSubscriptionDTO;
import com.pricemanagement.dto.NotificationMiniProgramSubscriptionUpdateRequest;
import com.pricemanagement.entity.NotificationMiniProgramSubscription;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationMiniProgramSubscriptionService {

    private static final String TYPE_PRICE_PUBLISHED = NotificationService.TYPE_PRICE_PUBLISHED;
    private static final String TYPE_SYSTEM_NOTICE = NotificationService.TYPE_SYSTEM_NOTICE;
    private static final String SOURCE_MINI_PROGRAM = "MINI_PROGRAM";

    private final NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    private final NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationMiniProgramEligibilityService eligibilityService;

    @Transactional(readOnly = true)
    public NotificationMiniProgramSubscriptionDTO status(Long userId) {
        User user = findUser(userId);
        Map<String, NotificationMiniProgramSubscription> stored = new java.util.LinkedHashMap<>();
        subscriptionRepository.findByUserIdOrderByNotificationTypeAscTemplateIdAsc(userId)
                .forEach(item -> stored.put(key(item.getNotificationType(), item.getTemplateId()), item));

        NotificationMiniProgramSubscriptionDTO dto = new NotificationMiniProgramSubscriptionDTO();
        NotificationMiniProgramRuntimeConfigService.RuntimeConfig runtimeConfig = runtimeConfigService.activeConfig();
        dto.setEnabled(runtimeConfig.isEnabled());
        dto.setConfigured(runtimeConfig.isConfigured());
        dto.setOpenidBound(StringUtils.hasText(user.getWechatOpenid()));
        dto.setTemplates(configuredTemplates().stream()
                .map(template -> toTemplateSubscription(template.notificationType(), template.templateId(), stored))
                .toList());
        return dto;
    }

    @Transactional
    public NotificationMiniProgramSubscriptionDTO update(
            Long userId,
            NotificationMiniProgramSubscriptionUpdateRequest request) {
        User user = findUser(userId);
        if (!StringUtils.hasText(user.getWechatOpenid())) {
            throw new IllegalArgumentException("当前用户未绑定微信小程序openid");
        }
        Set<String> allowedTemplates = configuredTemplates().stream()
                .map(template -> key(template.notificationType(), template.templateId()))
                .collect(Collectors.toSet());
        for (NotificationMiniProgramSubscriptionUpdateRequest.SubscriptionResult result : request.getResults()) {
            if (!allowedTemplates.contains(key(result.getNotificationType(), result.getTemplateId()))) {
                throw new IllegalArgumentException("订阅模板未配置或不允许授权");
            }
            upsert(user, result);
        }
        eligibilityService.requestRefresh(userId);
        return status(userId);
    }

    @Transactional
    public boolean consume(Long userId, String notificationType, String templateId) {
        boolean consumed = subscriptionRepository.consumeOne(userId, notificationType, templateId) == 1;
        if (consumed) {
            eligibilityService.requestRefresh(userId);
        }
        return consumed;
    }

    @Transactional
    public void releaseConsumed(Long userId, String notificationType, String templateId) {
        if (subscriptionRepository.releaseOne(userId, notificationType, templateId) == 1) {
            eligibilityService.requestRefresh(userId);
        }
    }

    @Transactional
    public void markUnauthorized(Long userId, String notificationType, String templateId, boolean refreshEligibility) {
        if (subscriptionRepository.markRejected(userId, notificationType, templateId) == 1 && refreshEligibility) {
            eligibilityService.requestRefresh(userId);
        }
    }

    @Transactional(readOnly = true)
    public boolean isAuthorized(Long userId, String notificationType, String templateId) {
        return subscriptionRepository.findByUserIdAndNotificationTypeAndTemplateId(userId, notificationType, templateId)
                .filter(subscription -> subscription.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT)
                .filter(subscription -> subscription.getAvailableCount() != null && subscription.getAvailableCount() > 0)
                .isPresent();
    }

    public List<TemplateConfig> configuredTemplates() {
        List<TemplateConfig> templates = new ArrayList<>();
        runtimeConfigService.activeConfig().configuredTemplates().forEach((notificationType, template) ->
                templates.add(new TemplateConfig(notificationType, template.getTemplateId())));
        return templates;
    }

    private void upsert(
            User user,
            NotificationMiniProgramSubscriptionUpdateRequest.SubscriptionResult result) {
        NotificationMiniProgramSubscription subscription = subscriptionRepository
                .findByUserIdAndNotificationTypeAndTemplateId(
                        user.getId(), result.getNotificationType(), result.getTemplateId())
                .orElseGet(NotificationMiniProgramSubscription::new);
        subscription.setUserId(user.getId());
        subscription.setOpenid(user.getWechatOpenid());
        subscription.setNotificationType(result.getNotificationType());
        subscription.setTemplateId(result.getTemplateId());
        subscription.setStatus(resolveStatus(result.getResult()));
        subscription.setSource(SOURCE_MINI_PROGRAM);
        if (subscription.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT) {
            subscription.setAvailableCount((subscription.getAvailableCount() == null ? 0 : subscription.getAvailableCount()) + 1);
            subscription.setLastAuthorizedTime(LocalDateTime.now());
        } else {
            subscription.setAvailableCount(0);
        }
        subscriptionRepository.save(subscription);
    }

    private NotificationMiniProgramSubscription.SubscriptionStatus resolveStatus(String result) {
        if ("accept".equalsIgnoreCase(result)) {
            return NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT;
        }
        if ("reject".equalsIgnoreCase(result)) {
            return NotificationMiniProgramSubscription.SubscriptionStatus.REJECT;
        }
        if ("ban".equalsIgnoreCase(result)) {
            return NotificationMiniProgramSubscription.SubscriptionStatus.BAN;
        }
        return NotificationMiniProgramSubscription.SubscriptionStatus.UNKNOWN;
    }

    private NotificationMiniProgramSubscriptionDTO.TemplateSubscription toTemplateSubscription(
            String notificationType,
            String templateId,
            Map<String, NotificationMiniProgramSubscription> stored) {
        NotificationMiniProgramSubscription subscription = stored.get(key(notificationType, templateId));
        NotificationMiniProgramSubscriptionDTO.TemplateSubscription dto =
                new NotificationMiniProgramSubscriptionDTO.TemplateSubscription();
        dto.setNotificationType(notificationType);
        dto.setTemplateId(templateId);
        dto.setStatus(subscription == null ? "UNKNOWN" : subscription.getStatus().name());
        dto.setAvailableCount(subscription == null || subscription.getAvailableCount() == null
                ? 0
                : subscription.getAvailableCount());
        dto.setAuthorized(subscription != null
                && subscription.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT
                && subscription.getAvailableCount() != null
                && subscription.getAvailableCount() > 0);
        dto.setLastAuthorizedTime(subscription == null ? null : subscription.getLastAuthorizedTime());
        return dto;
    }

    private User findUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("当前用户未登录");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("当前用户不存在"));
    }

    private String key(String notificationType, String templateId) {
        return notificationType + "::" + templateId;
    }

    public record TemplateConfig(String notificationType, String templateId) {
    }
}
