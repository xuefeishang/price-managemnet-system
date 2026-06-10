package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.AdminNotificationSummaryDTO;
import com.pricemanagement.dto.NotificationCreateCommand;
import com.pricemanagement.dto.NotificationDTO;
import com.pricemanagement.dto.NotificationRecipientDTO;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationRecipient;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.NotificationRecipientRepository;
import com.pricemanagement.repository.SysDictRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String TYPE_PRICE_PUBLISHED = "PRICE_PUBLISHED";
    public static final String TYPE_SYSTEM_NOTICE = "SYSTEM_NOTICE";
    public static final String CHANNEL_IN_APP = "IN_APP";
    public static final String CHANNEL_APP_PUSH = "APP_PUSH";
    public static final String CHANNEL_MINI_PROGRAM = "MINI_PROGRAM";
    public static final String CHANNEL_WEBHOOK = "WEBHOOK";
    public static final String LINK_TYPE_PRICE_QUERY = "PRICE_QUERY";
    public static final String LINK_TYPE_SYSTEM_NOTICE = "SYSTEM_NOTICE";

    private final NotificationMessageRepository messageRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final SysDictRepository sysDictRepository;
    private final ObjectMapper objectMapper;
    private final NotificationOutboxService notificationOutboxService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final NotificationThrottleService notificationThrottleService;
    private final NotificationRealtimeService notificationRealtimeService;

    @Transactional
    public NotificationMessage createMessage(String type, String title, String content, String businessType,
                                             Long businessId, List<String> channels, List<User> recipients,
                                             Long createdBy) {
        return createMessage(command(type, title, title, content, businessType, businessId)
                .channels(channels)
                .recipientUsers(recipients)
                .createdBy(createdBy)
                .build());
    }

    @Transactional
    public NotificationMessage createMessage(String type, String title, String summary, String content, String businessType,
                                             Long businessId, List<String> channels, List<User> recipients,
                                             Long createdBy, NotificationMessage.NotificationPriority priority,
                                             String linkType, String linkParams, String dedupeKey) {
        return createMessage(command(type, title, summary, content, businessType, businessId)
                .channels(channels)
                .recipientUsers(recipients)
                .createdBy(createdBy)
                .priority(priority)
                .link(linkType, linkParams)
                .dedupeKey(dedupeKey)
                .build());
    }

    @Transactional
    public NotificationMessage createMessage(NotificationCreateCommand command) {
        validateCommand(command);
        String type = command.getEventType();
        String title = command.getTitle();
        String summary = command.getSummary();
        String content = command.getContent();
        String businessType = command.getBusinessType();
        Long businessId = command.getBusinessId();
        List<String> channels = command.getChannels();
        Long createdBy = command.getCreatedBy();
        NotificationMessage.NotificationPriority priority = command.getPriority();
        String linkType = command.getLinkType();
        String linkParams = command.getLinkParams();
        String dedupeKey = command.getDedupeKey();
        boolean aggregated = notificationThrottleService.shouldAggregate(type, priority);
        List<String> resolvedChannels = resolveChannels(channels);

        if (aggregated) {
            String typeLabel = sysDictRepository.findByCategoryAndDictKey("notification_type", type)
                    .map(dict -> dict.getDictValue())
                    .orElse(type);
            title = typeLabel + "聚合提醒";
            summary = notificationThrottleService.aggregationSummary(type);
            content = summary + "\n\n最新事件：" + content;
            dedupeKey = notificationThrottleService.aggregationDedupeKey(type);
        }

        if (!aggregated && dedupeKey != null && !dedupeKey.isBlank()) {
            var existing = messageRepository.findByDedupeKey(dedupeKey);
            if (existing.isPresent()) {
                log.info("Skipped duplicated notification: type={}, dedupeKey={}", type, dedupeKey);
                return existing.get();
            }
        }

        List<User> recipients = resolveRecipients(command);

        if (aggregated && dedupeKey != null && !dedupeKey.isBlank()) {
            var existing = messageRepository.findByDedupeKey(dedupeKey);
            if (existing.isPresent()) {
                NotificationMessage aggregateMessage = existing.get();
                aggregateMessage.setTitle(title);
                aggregateMessage.setSummary(summary);
                aggregateMessage.setContent(content);
                aggregateMessage.setChannels(toJsonList(resolvedChannels));
                aggregateMessage.setEventCount((aggregateMessage.getEventCount() == null
                        ? 1L
                        : aggregateMessage.getEventCount()) + 1);
                NotificationMessage savedAggregate = messageRepository.save(aggregateMessage);
                refreshAggregateRecipients(savedAggregate, recipients, resolvedChannels);
                log.info("Updated aggregated notification: type={}, dedupeKey={}", type, dedupeKey);
                return savedAggregate;
            }
        }

        NotificationMessage message = new NotificationMessage();
        message.setType(type);
        message.setTitle(title);
        message.setSummary(summary);
        message.setContent(content);
        message.setBusinessType(businessType);
        message.setBusinessId(businessId);
        message.setPriority(priority == null ? NotificationMessage.NotificationPriority.NORMAL : priority);
        message.setLinkType(linkType);
        message.setLinkParams(linkParams);
        message.setDedupeKey(dedupeKey);
        message.setExpireTime(command.getExpireTime());
        message.setCreatedBy(createdBy);
        try {
            message.setChannels(objectMapper.writeValueAsString(resolvedChannels));
        } catch (JsonProcessingException e) {
            message.setChannels("[\"IN_APP\"]");
        }
        NotificationMessage savedMessage = messageRepository.save(message);

        for (User user : recipients) {
            NotificationRecipient recipient = new NotificationRecipient();
            recipient.setMessageId(savedMessage.getId());
            recipient.setUserId(user.getId());
            NotificationRecipient savedRecipient = recipientRepository.save(recipient);

            createDeliveries(savedMessage, savedRecipient, user, resolvedChannels);
            notificationRealtimeService.publishNewNotification(
                    user.getId(),
                    savedMessage.getId(),
                    savedMessage.getType(),
                    getUnreadCount(user.getId()));
        }

        log.info("Created notification: type={}, recipients={}", type, recipients.size());
        return savedMessage;
    }

    private void refreshAggregateRecipients(NotificationMessage message, List<User> recipients, List<String> channels) {
        for (User user : recipients) {
            NotificationRecipient recipient = recipientRepository.findByMessageIdAndUserId(message.getId(), user.getId())
                    .orElseGet(() -> {
                        NotificationRecipient created = new NotificationRecipient();
                        created.setMessageId(message.getId());
                        created.setUserId(user.getId());
                        return created;
                    });
            recipient.setReadStatus(NotificationRecipient.ReadStatus.UNREAD);
            recipient.setReadTime(null);
            recipient.setArchived(false);
            recipient.setArchivedTime(null);
            NotificationRecipient savedRecipient = recipientRepository.save(recipient);
            createDeliveries(message, savedRecipient, user, channels);
            notificationRealtimeService.publishNewNotification(
                    user.getId(),
                    message.getId(),
                    message.getType(),
                    getUnreadCount(user.getId()));
        }
    }

    private void createDeliveries(NotificationMessage message, NotificationRecipient recipient,
                                  User user, List<String> channels) {
        for (String channel : channels) {
            NotificationDeliveryLog delivery = new NotificationDeliveryLog();
            delivery.setMessageId(message.getId());
            delivery.setRecipientId(recipient.getId());
            delivery.setUserId(user.getId());
            delivery.setChannel(channel);
            delivery.setProvider(channel);
            LocalDateTime nextDeliveryTime = null;
            if (CHANNEL_IN_APP.equals(channel)) {
                delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.SUCCESS);
                delivery.setDeliveredTime(LocalDateTime.now());
            } else {
                NotificationPreferenceService.DeliveryPreferenceDecision decision =
                        notificationPreferenceService.resolveExternalDelivery(
                                user.getId(),
                                message.getType(),
                                channel,
                                message.getPriority(),
                                LocalDateTime.now());
                if (!decision.enabled()) {
                    delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.SKIPPED);
                    delivery.setDeliveredTime(LocalDateTime.now());
                } else {
                    delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);
                }
                delivery.setErrorCode(decision.errorCode());
                delivery.setErrorMessage(decision.errorMessage());
                nextDeliveryTime = decision.nextDeliveryTime();
            }
            NotificationDeliveryLog savedDelivery = deliveryLogRepository.save(delivery);
            if (savedDelivery.getStatus() == NotificationDeliveryLog.DeliveryStatus.PENDING) {
                notificationOutboxService.enqueueDelivery(savedDelivery, nextDeliveryTime);
            }
        }
    }

    @Transactional
    public NotificationMessage create(NotificationCreateCommand command) {
        return createMessage(command);
    }

    @Transactional
    public NotificationDeliveryLog createTestDelivery(
            String notificationType,
            User user,
            String channel,
            Long createdBy) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("测试用户不能为空");
        }
        if (isBlank(notificationType) || !isActiveDict("notification_type", notificationType)) {
            throw new IllegalArgumentException("通知类型不存在或未启用");
        }
        if (isBlank(channel) || CHANNEL_IN_APP.equals(channel) || !isActiveDict("notification_channel", channel)) {
            throw new IllegalArgumentException("测试投递仅支持已启用的外部渠道");
        }

        NotificationMessage message = new NotificationMessage();
        message.setType(notificationType);
        message.setTitle("小程序渠道联调测试");
        message.setSummary("由通知管理后台发起的受控测试投递");
        message.setContent("用于验证当前小程序渠道配置、用户绑定和模板授权状态。");
        message.setBusinessType("NOTIFICATION_TEST");
        message.setBusinessId(user.getId());
        message.setChannels(toJsonList(List.of(channel)));
        message.setPriority(NotificationMessage.NotificationPriority.NORMAL);
        message.setLinkType(LINK_TYPE_SYSTEM_NOTICE);
        message.setCreatedBy(createdBy);
        NotificationMessage savedMessage = messageRepository.save(message);

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setMessageId(savedMessage.getId());
        recipient.setUserId(user.getId());
        recipient.setArchived(true);
        recipient.setArchivedTime(LocalDateTime.now());
        NotificationRecipient savedRecipient = recipientRepository.save(recipient);

        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setMessageId(savedMessage.getId());
        delivery.setRecipientId(savedRecipient.getId());
        delivery.setUserId(user.getId());
        delivery.setChannel(channel);
        delivery.setProvider(channel);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);
        delivery.setTest(true);
        NotificationDeliveryLog savedDelivery = deliveryLogRepository.save(delivery);
        notificationOutboxService.enqueueDelivery(savedDelivery, LocalDateTime.now());
        return savedDelivery;
    }

    private void validateCommand(NotificationCreateCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("通知命令不能为空");
        }
        if (isBlank(command.getEventType()) || !isActiveDict("notification_type", command.getEventType())) {
            throw new IllegalArgumentException("通知类型不存在或未启用");
        }
        if (isBlank(command.getTitle())) {
            throw new IllegalArgumentException("通知标题不能为空");
        }
        if (isBlank(command.getContent())) {
            throw new IllegalArgumentException("通知内容不能为空");
        }
        if (isBlank(command.getBusinessType()) || !isActiveDict("notification_business_type", command.getBusinessType())) {
            throw new IllegalArgumentException("通知业务类型不存在或未启用");
        }
        if (command.getLinkType() != null && !command.getLinkType().isBlank()
                && !isActiveDict("notification_link_type", command.getLinkType())) {
            throw new IllegalArgumentException("通知跳转类型不存在或未启用");
        }
        for (String channel : resolveChannels(command.getChannels())) {
            if (isBlank(channel) || !isActiveDict("notification_channel", channel)) {
                throw new IllegalArgumentException("通知渠道不存在或未启用: " + channel);
            }
        }
    }

    private List<String> resolveChannels(List<String> channels) {
        List<String> resolved = new ArrayList<>();
        resolved.add(CHANNEL_IN_APP);
        if (channels == null) {
            return resolved;
        }
        for (String channel : channels) {
            if (!isBlank(channel) && !resolved.contains(channel)) {
                resolved.add(channel);
            }
        }
        return resolved;
    }

    private boolean isActiveDict(String category, String key) {
        return sysDictRepository.findByCategoryAndDictKey(category, key)
                .map(dict -> dict.getStatus() == CommonStatus.ACTIVE)
                .orElse(false);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<User> resolveRecipients(NotificationCreateCommand command) {
        Map<Long, User> users = new LinkedHashMap<>();
        if (command.getRecipientUsers() != null && !command.getRecipientUsers().isEmpty()) {
            command.getRecipientUsers().stream()
                    .filter(Objects::nonNull)
                    .filter(user -> user.getId() != null)
                    .forEach(user -> users.put(user.getId(), user));
        }
        if (command.getRecipientUserIds() != null && !command.getRecipientUserIds().isEmpty()) {
            userRepository.findAllById(command.getRecipientUserIds()).stream()
                    .filter(user -> user.getStatus() == CommonStatus.ACTIVE)
                    .forEach(user -> users.put(user.getId(), user));
        }
        if (command.getRecipientRoles() != null && !command.getRecipientRoles().isEmpty()) {
            userRepository.findByStatus(CommonStatus.ACTIVE).stream()
                    .filter(user -> command.getRecipientRoles().contains(user.getRole()))
                    .forEach(user -> users.put(user.getId(), user));
        }
        if (users.isEmpty()) {
            throw new IllegalArgumentException("通知接收人不能为空");
        }
        return new ArrayList<>(users.values());
    }

    private String toJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String toJsonList(List<String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getMyNotifications(Long userId, NotificationRecipient.ReadStatus readStatus, Pageable pageable) {
        Page<NotificationRecipient> recipients =
                recipientRepository.findVisibleMyRecipients(userId, readStatus, LocalDateTime.now(), pageable);
        if (recipients.isEmpty()) {
            return recipients.map(recipient -> NotificationDTO.from(recipient, null));
        }

        Map<Long, NotificationMessage> messagesById = messageRepository.findAllById(
                        recipients.getContent().stream()
                                .map(NotificationRecipient::getMessageId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(Collectors.toMap(NotificationMessage::getId, message -> message));
        return recipients.map(recipient -> NotificationDTO.from(
                recipient,
                messagesById.get(recipient.getMessageId())));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return recipientRepository.countVisibleByUserIdAndReadStatus(
                userId,
                NotificationRecipient.ReadStatus.UNREAD,
                LocalDateTime.now());
    }

    @Transactional
    public void markRead(Long messageId, Long userId) {
        NotificationRecipient recipient = recipientRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (recipient.getReadStatus() == NotificationRecipient.ReadStatus.READ) {
            return;
        }
        recipient.setReadStatus(NotificationRecipient.ReadStatus.READ);
        recipient.setReadTime(LocalDateTime.now());
        recipientRepository.save(recipient);
        notificationRealtimeService.publishUnreadChanged(userId, getUnreadCount(userId));
    }

    @Transactional
    public int markAllRead(Long userId) {
        int count = recipientRepository.markAllReadByUserId(
                userId,
                NotificationRecipient.ReadStatus.READ,
                NotificationRecipient.ReadStatus.UNREAD
        );
        notificationRealtimeService.publishUnreadChanged(userId, getUnreadCount(userId));
        return count;
    }

    @Transactional
    public void archive(Long messageId, Long userId) {
        NotificationRecipient recipient = recipientRepository.findByMessageIdAndUserId(messageId, userId)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
        if (Boolean.TRUE.equals(recipient.getArchived())) {
            return;
        }
        recipient.setArchived(true);
        recipient.setArchivedTime(LocalDateTime.now());
        recipientRepository.save(recipient);
        notificationRealtimeService.publishUnreadChanged(userId, getUnreadCount(userId));
    }

    @Transactional
    public int archiveMessageForAll(Long messageId) {
        if (messageId == null) {
            return 0;
        }
        return recipientRepository.archiveByMessageId(messageId);
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryLog> getDeliveries(Long messageId) {
        return deliveryLogRepository.findByMessageIdOrderByIdAsc(messageId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationMessage> getAdminNotifications(Pageable pageable) {
        return messageRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<AdminNotificationSummaryDTO> getAdminNotificationSummaries(
            String type,
            NotificationMessage.NotificationPriority priority,
            String businessType,
            String channel,
            NotificationDeliveryLog.DeliveryStatus deliveryStatus,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable) {
        return messageRepository.findAdminSummaries(
                normalize(type),
                priority,
                normalize(businessType),
                normalize(channel),
                deliveryStatus,
                normalize(keyword),
                startTime,
                endTime,
                pageable);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional(readOnly = true)
    public NotificationMessage getAdminNotification(Long id) {
        return messageRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("通知不存在"));
    }

    @Transactional(readOnly = true)
    public Page<NotificationRecipientDTO> getAdminRecipients(Long messageId, Pageable pageable) {
        return recipientRepository.findAdminRecipientDtosByMessageId(messageId, pageable);
    }

    @Transactional
    public void retryDelivery(Long deliveryId) {
        notificationOutboxService.retryDelivery(deliveryId);
    }

    private NotificationCommandBuilder command(String eventType, String title, String summary,
                                               String content, String businessType, Long businessId) {
        return new NotificationCommandBuilder(eventType, title, summary, content, businessType, businessId);
    }

    private static class NotificationCommandBuilder {
        private final NotificationCreateCommand command = new NotificationCreateCommand();

        NotificationCommandBuilder(String eventType, String title, String summary, String content,
                                   String businessType, Long businessId) {
            command.setEventType(eventType);
            command.setTitle(title);
            command.setSummary(summary);
            command.setContent(content);
            command.setBusinessType(businessType);
            command.setBusinessId(businessId);
        }

        NotificationCommandBuilder channels(List<String> channels) {
            command.setChannels(channels);
            return this;
        }

        NotificationCommandBuilder recipientUsers(List<User> users) {
            command.setRecipientUsers(users);
            command.setRecipientUserIds(users == null ? null : users.stream()
                    .filter(Objects::nonNull)
                    .map(User::getId)
                    .collect(Collectors.toList()));
            return this;
        }

        NotificationCommandBuilder createdBy(Long createdBy) {
            command.setCreatedBy(createdBy);
            return this;
        }

        NotificationCommandBuilder priority(NotificationMessage.NotificationPriority priority) {
            command.setPriority(priority);
            return this;
        }

        NotificationCommandBuilder link(String linkType, String linkParams) {
            command.setLinkType(linkType);
            command.setLinkParams(linkParams);
            return this;
        }

        NotificationCommandBuilder dedupeKey(String dedupeKey) {
            command.setDedupeKey(dedupeKey);
            return this;
        }

        NotificationCreateCommand build() {
            return command;
        }
    }
}
