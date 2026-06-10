package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.AdminMiniProgramSubscriptionDTO;
import com.pricemanagement.dto.NotificationAuthorizationGuideRequest;
import com.pricemanagement.dto.NotificationMiniProgramCoverageDTO;
import com.pricemanagement.dto.NotificationMiniProgramResolveRequest;
import com.pricemanagement.dto.NotificationPreferenceDTO;
import com.pricemanagement.dto.NotificationMiniProgramTestDeliveryRequest;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMiniProgramSubscription;
import com.pricemanagement.entity.NotificationMiniProgramResolution;
import com.pricemanagement.entity.NotificationMiniProgramEligibility;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.NotificationMiniProgramResolutionRepository;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationPreferenceRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMiniProgramSubscriptionManagementService {

    private static final int LOW_BALANCE_THRESHOLD = 1;
    private static final int GUIDE_BATCH_SIZE = 200;

    private final UserRepository userRepository;
    private final NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    private final NotificationMiniProgramResolutionRepository resolutionRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    private final NotificationMiniProgramEligibilityService eligibilityService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public NotificationMiniProgramCoverageDTO coverage(List<User.Role> roles, String notificationType) {
        List<User> users = targetUsers(roles, null);
        Map<Long, List<NotificationMiniProgramSubscription>> subscriptions = subscriptionMap(users);
        Set<String> templateKeys = templateKeys(notificationType);

        NotificationMiniProgramCoverageDTO dto = new NotificationMiniProgramCoverageDTO();
        dto.setNotificationType(notificationType);
        dto.setTargetCount(users.size());
        dto.setOpenidBound(users.stream().filter(user -> StringUtils.hasText(user.getWechatOpenid())).count());
        dto.setAuthorized(users.stream().filter(user -> hasAuthorized(subscriptions.get(user.getId()), templateKeys)).count());
        dto.setReachable(dto.getAuthorized());
        dto.setInAppFallback(dto.getTargetCount());
        dto.setRejectedOrBanned(users.stream().filter(user -> hasRejectedOrBanned(subscriptions.get(user.getId()), templateKeys)).count());
        dto.setLowBalance(users.stream().filter(user -> isLowBalance(subscriptions.get(user.getId()), templateKeys)).count());
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<AdminMiniProgramSubscriptionDTO> list(
            User.Role role,
            String status,
            String keyword,
            Pageable pageable) {
        Page<User> userPage;
        if (!StringUtils.hasText(status)) {
            userPage = userRepository.findActiveMiniProgramSubscriptionTargets(
                    CommonStatus.ACTIVE,
                    role,
                    normalizeKeyword(keyword),
                    pageable);
        } else {
            userPage = userRepository.findActiveMiniProgramSubscriptionTargetsByEligibilityStatus(
                    CommonStatus.ACTIVE,
                    parseRowStatus(status),
                    role,
                    normalizeKeyword(keyword),
                    pageable);
        }
        Map<Long, List<NotificationMiniProgramSubscription>> subscriptions = subscriptionMap(userPage.getContent());
        List<AdminMiniProgramSubscriptionDTO> rows = userPage.getContent().stream()
                .map(user -> toRow(user, subscriptions.get(user.getId())))
                .toList();
        enrichResolutions(rows);
        return new PageImpl<>(rows, pageable, userPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public AdminMiniProgramSubscriptionDTO detail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        AdminMiniProgramSubscriptionDTO dto =
                toRow(user, subscriptionRepository.findByUserIdOrderByNotificationTypeAscTemplateIdAsc(userId));
        resolutionRepository.findByUserId(userId).ifPresent(resolution -> dto.setResolution(toResolution(resolution)));
        dto.setRecentDeliveries(deliveryLogRepository.findByUserIdAndChannelOrderByCreatedTimeDesc(
                userId,
                NotificationService.CHANNEL_MINI_PROGRAM,
                org.springframework.data.domain.PageRequest.of(0, 10)));
        dto.setPreferences(preferenceRepository.findByUserIdOrderByNotificationTypeAscChannelAsc(userId).stream()
                .map(NotificationPreferenceDTO::from)
                .toList());
        return dto;
    }

    @Transactional
    public AdminMiniProgramSubscriptionDTO resolve(
            Long userId,
            NotificationMiniProgramResolveRequest request,
            Long operatorId) {
        userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (request == null || request.getStatus() == null) {
            throw new IllegalArgumentException("处理状态不能为空");
        }
        NotificationMiniProgramResolution resolution = resolutionRepository.findByUserId(userId).orElseGet(() -> {
            NotificationMiniProgramResolution created = new NotificationMiniProgramResolution();
            created.setUserId(userId);
            return created;
        });
        resolution.setResolveStatus(request.getStatus());
        resolution.setResolveRemark(normalizeRemark(request.getRemark()));
        resolution.setRemindAfter(request.getStatus() == NotificationMiniProgramResolution.ResolveStatus.SNOOZED
                ? requireFutureRemindAfter(request.getRemindAfter())
                : null);
        resolution.setFollowUpRequired(
                request.getStatus() == NotificationMiniProgramResolution.ResolveStatus.FOLLOW_UP);
        resolution.setResolvedBy(operatorId);
        resolution.setResolvedTime(LocalDateTime.now());
        resolutionRepository.save(resolution);
        return detail(userId);
    }

    @Transactional
    public Long sendTestDelivery(NotificationMiniProgramTestDeliveryRequest request, Long operatorId) {
        if (request == null || request.getUserId() == null || !StringUtils.hasText(request.getNotificationType())) {
            throw new IllegalArgumentException("测试用户和通知类型不能为空");
        }
        User user = userRepository.findById(request.getUserId())
                .filter(item -> item.getStatus() == CommonStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("测试用户不存在或未启用"));
        if (!runtimeConfigService.resolveTemplate(request.getNotificationType()).isPresent()) {
            throw new IllegalArgumentException("当前通知类型未配置小程序模板");
        }
        return notificationService.createTestDelivery(
                request.getNotificationType(),
                user,
                NotificationService.CHANNEL_MINI_PROGRAM,
                operatorId).getId();
    }

    @Transactional
    public int sendGuide(NotificationAuthorizationGuideRequest request, Long operatorId) {
        int pageNumber = 0;
        int sent = 0;
        Page<User> page;
        do {
            page = userRepository.findActiveMiniProgramSubscriptionTargets(
                    CommonStatus.ACTIVE,
                    singleRole(request.getTargetRoles()),
                    normalizeKeyword(request.getKeyword()),
                    PageRequest.of(pageNumber, GUIDE_BATCH_SIZE, Sort.by(Sort.Direction.ASC, "id")));
            List<User> users = page.getContent();
            Map<Long, List<NotificationMiniProgramSubscription>> subscriptions = subscriptionMap(users);
            List<Long> userIds = users.stream()
                    .filter(user -> matchesRoles(user, request.getTargetRoles()))
                    .map(user -> toRow(user, subscriptions.get(user.getId())))
                    .filter(row -> matchesStatus(row, request.getStatus()))
                    .map(AdminMiniProgramSubscriptionDTO::getUserId)
                    .toList();
            sent += createGuideNotification(userIds, operatorId);
            pageNumber++;
        } while (page.hasNext());
        return sent;
    }

    @Transactional
    public int sendGuideToUser(Long userId, Long operatorId) {
        return createGuideNotification(List.of(userId), operatorId);
    }

    private int createGuideNotification(List<Long> userIds, Long operatorId) {
        Set<Long> snoozedUserIds = userIds.isEmpty()
                ? Set.of()
                : Set.copyOf(resolutionRepository.findSnoozedUserIds(userIds, LocalDateTime.now()));
        if (!userIds.isEmpty() && snoozedUserIds.containsAll(userIds)) {
            return 0;
        }
        List<User> users = userRepository.findAllById(userIds).stream()
                .filter(user -> user.getStatus() == CommonStatus.ACTIVE)
                .filter(user -> !snoozedUserIds.contains(user.getId()))
                .toList();
        if (users.isEmpty()) {
            return 0;
        }
        com.pricemanagement.dto.NotificationCreateCommand command = new com.pricemanagement.dto.NotificationCreateCommand();
        command.setEventType(NotificationService.TYPE_SYSTEM_NOTICE);
        command.setTitle("小程序订阅授权提醒");
        command.setSummary("请在小程序端完成订阅消息授权");
        command.setContent("为及时接收报价变更和系统公告，请在小程序通知中心完成订阅消息授权。");
        command.setBusinessType("SYSTEM");
        command.setBusinessId(0L);
        command.setRecipientUsers(users);
        command.setRecipientUserIds(users.stream().map(User::getId).toList());
        command.setChannels(List.of(NotificationService.CHANNEL_IN_APP));
        command.setPriority(com.pricemanagement.entity.NotificationMessage.NotificationPriority.NORMAL);
        command.setLinkType(NotificationService.LINK_TYPE_SYSTEM_NOTICE);
        command.setCreatedBy(operatorId);
        notificationService.create(command);
        return users.size();
    }

    private List<User> targetUsers(List<User.Role> roles, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        return userRepository.findByStatus(CommonStatus.ACTIVE).stream()
                .filter(user -> roles == null || roles.isEmpty() || roles.contains(user.getRole()))
                .filter(user -> !StringUtils.hasText(normalizedKeyword)
                        || contains(user.getUsername(), normalizedKeyword)
                        || contains(user.getNickname(), normalizedKeyword)
                        || contains(user.getPhone(), normalizedKeyword))
                .toList();
    }

    private User.Role singleRole(List<User.Role> roles) {
        return roles == null || roles.size() != 1 ? null : roles.get(0);
    }

    private boolean matchesRoles(User user, List<User.Role> roles) {
        return roles == null || roles.isEmpty() || roles.contains(user.getRole());
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim().toLowerCase();
    }

    private String normalizeRemark(String remark) {
        if (!StringUtils.hasText(remark)) {
            return null;
        }
        String value = remark.trim();
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private LocalDateTime requireFutureRemindAfter(LocalDateTime remindAfter) {
        if (remindAfter == null || !remindAfter.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("暂不提醒时间必须晚于当前时间");
        }
        return remindAfter;
    }

    private AdminMiniProgramSubscriptionDTO.Resolution toResolution(NotificationMiniProgramResolution resolution) {
        AdminMiniProgramSubscriptionDTO.Resolution dto = new AdminMiniProgramSubscriptionDTO.Resolution();
        dto.setStatus(resolution.getResolveStatus().name());
        dto.setRemark(resolution.getResolveRemark());
        dto.setRemindAfter(resolution.getRemindAfter());
        dto.setFollowUpRequired(Boolean.TRUE.equals(resolution.getFollowUpRequired()));
        dto.setResolvedBy(resolution.getResolvedBy());
        dto.setResolvedTime(resolution.getResolvedTime());
        return dto;
    }

    private void enrichResolutions(List<AdminMiniProgramSubscriptionDTO> rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<Long, NotificationMiniProgramResolution> resolutions = resolutionRepository.findByUserIdIn(
                        rows.stream().map(AdminMiniProgramSubscriptionDTO::getUserId).toList()).stream()
                .collect(Collectors.toMap(NotificationMiniProgramResolution::getUserId, item -> item));
        rows.forEach(row -> {
            NotificationMiniProgramResolution resolution = resolutions.get(row.getUserId());
            if (resolution != null) {
                row.setResolution(toResolution(resolution));
            }
        });
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private Map<Long, List<NotificationMiniProgramSubscription>> subscriptionMap(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return subscriptionRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        NotificationMiniProgramSubscription::getUserId,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private Set<String> templateKeys(String notificationType) {
        return runtimeConfigService.configuredTemplates().stream()
                .filter(template -> !StringUtils.hasText(notificationType)
                        || template.notificationType().equals(notificationType))
                .map(template -> key(template.notificationType(), template.templateId()))
                .collect(Collectors.toSet());
    }

    private AdminMiniProgramSubscriptionDTO toRow(
            User user,
            List<NotificationMiniProgramSubscription> subscriptions) {
        List<NotificationMiniProgramSubscription> rows = subscriptions == null ? List.of() : subscriptions;
        AdminMiniProgramSubscriptionDTO dto = new AdminMiniProgramSubscriptionDTO();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setRole(user.getRole().name());
        dto.setOpenidBound(StringUtils.hasText(user.getWechatOpenid()));
        dto.setOpenidMasked(mask(user.getWechatOpenid()));
        dto.setPriceAvailableCount(availableCount(rows, NotificationService.TYPE_PRICE_PUBLISHED));
        dto.setNoticeAvailableCount(availableCount(rows, NotificationService.TYPE_SYSTEM_NOTICE));
        dto.setPriceStatus(status(rows, NotificationService.TYPE_PRICE_PUBLISHED));
        dto.setNoticeStatus(status(rows, NotificationService.TYPE_SYSTEM_NOTICE));
        dto.setLastAuthorizedTime(rows.stream()
                .map(NotificationMiniProgramSubscription::getLastAuthorizedTime)
                .filter(time -> time != null)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        dto.setTemplates(runtimeConfigService.configuredTemplates().stream()
                .map(template -> toTemplateState(rows, template.notificationType(), template.templateId()))
                .toList());
        dto.setStatus(eligibilityService.evaluate(user, rows).rowStatus().name());
        return dto;
    }

    private AdminMiniProgramSubscriptionDTO.TemplateState toTemplateState(
            List<NotificationMiniProgramSubscription> rows,
            String notificationType,
            String templateId) {
        NotificationMiniProgramSubscription subscription = rows.stream()
                .filter(item -> notificationType.equals(item.getNotificationType()) && templateId.equals(item.getTemplateId()))
                .findFirst()
                .orElse(null);
        AdminMiniProgramSubscriptionDTO.TemplateState state = new AdminMiniProgramSubscriptionDTO.TemplateState();
        state.setNotificationType(notificationType);
        state.setTemplateIdMasked(mask(templateId));
        state.setStatus(subscription == null ? "UNKNOWN" : subscription.getStatus().name());
        state.setAvailableCount(subscription == null || subscription.getAvailableCount() == null
                ? 0
                : subscription.getAvailableCount());
        state.setAuthorized(subscription != null
                && subscription.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT
                && subscription.getAvailableCount() != null
                && subscription.getAvailableCount() > 0);
        state.setLastAuthorizedTime(subscription == null ? null : subscription.getLastAuthorizedTime());
        return state;
    }

    private boolean matchesStatus(AdminMiniProgramSubscriptionDTO row, String status) {
        return !StringUtils.hasText(status) || status.equals(row.getStatus());
    }

    private NotificationMiniProgramEligibility.RowStatus parseRowStatus(String status) {
        try {
            return NotificationMiniProgramEligibility.RowStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("无效的小程序订阅行状态: " + status);
        }
    }

    private boolean hasAuthorized(List<NotificationMiniProgramSubscription> rows, Set<String> templateKeys) {
        return rows != null && rows.stream()
                .anyMatch(item -> templateKeys.contains(key(item.getNotificationType(), item.getTemplateId()))
                        && item.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT
                        && item.getAvailableCount() != null
                        && item.getAvailableCount() > 0);
    }

    private boolean hasRejectedOrBanned(List<NotificationMiniProgramSubscription> rows, Set<String> templateKeys) {
        return rows != null && rows.stream()
                .anyMatch(item -> templateKeys.contains(key(item.getNotificationType(), item.getTemplateId()))
                        && (item.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.REJECT
                        || item.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.BAN));
    }

    private boolean isLowBalance(List<NotificationMiniProgramSubscription> rows, Set<String> templateKeys) {
        return rows != null && rows.stream()
                .anyMatch(item -> templateKeys.contains(key(item.getNotificationType(), item.getTemplateId()))
                        && item.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT
                        && item.getAvailableCount() != null
                        && item.getAvailableCount() > 0
                        && item.getAvailableCount() <= LOW_BALANCE_THRESHOLD);
    }

    private int availableCount(List<NotificationMiniProgramSubscription> rows, String notificationType) {
        return rows.stream()
                .filter(item -> notificationType.equals(item.getNotificationType()))
                .filter(item -> item.getStatus() == NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT)
                .map(NotificationMiniProgramSubscription::getAvailableCount)
                .filter(count -> count != null)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private String status(List<NotificationMiniProgramSubscription> rows, String notificationType) {
        return rows.stream()
                .filter(item -> notificationType.equals(item.getNotificationType()))
                .findFirst()
                .map(item -> item.getStatus().name())
                .orElse("UNKNOWN");
    }

    private String key(String notificationType, String templateId) {
        return notificationType + "::" + templateId;
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 8) {
            return "****" + trimmed;
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
