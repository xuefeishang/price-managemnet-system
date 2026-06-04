package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationDTO;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationRecipient;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.NotificationRecipientRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String TYPE_PRICE_PUBLISHED = "PRICE_PUBLISHED";
    public static final String CHANNEL_IN_APP = "IN_APP";
    public static final String CHANNEL_APP_PUSH = "APP_PUSH";
    public static final String CHANNEL_MINI_PROGRAM = "MINI_PROGRAM";
    public static final String LINK_TYPE_PRICE_QUERY = "PRICE_QUERY";

    private final NotificationMessageRepository messageRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationMessage createPricePublishedNotification(String title, String content, Long businessId,
                                                                LocalDate effectiveDate,
                                                                Long batchId,
                                                                Long createdBy, List<String> channels,
                                                                List<User.Role> recipientRoles) {
        List<User> users = userRepository.findByStatus(CommonStatus.ACTIVE).stream()
                .filter(user -> recipientRoles == null || recipientRoles.isEmpty() || recipientRoles.contains(user.getRole()))
                .toList();
        return createMessage(
                TYPE_PRICE_PUBLISHED,
                title,
                "价格已发布，可查看最新价格",
                content,
                "PRICE",
                businessId,
                channels,
                users,
                createdBy,
                NotificationMessage.NotificationPriority.NORMAL,
                LINK_TYPE_PRICE_QUERY,
                toJson(Map.of("date", effectiveDate.toString())),
                "PRICE_PUBLISHED:BATCH:" + batchId
        );
    }

    @Transactional
    public NotificationMessage createMessage(String type, String title, String content, String businessType,
                                             Long businessId, List<String> channels, List<User> recipients,
                                             Long createdBy) {
        return createMessage(type, title, title, content, businessType, businessId, channels, recipients, createdBy,
                NotificationMessage.NotificationPriority.NORMAL, null, null, null);
    }

    @Transactional
    public NotificationMessage createMessage(String type, String title, String summary, String content, String businessType,
                                             Long businessId, List<String> channels, List<User> recipients,
                                             Long createdBy, NotificationMessage.NotificationPriority priority,
                                             String linkType, String linkParams, String dedupeKey) {
        if (dedupeKey != null && !dedupeKey.isBlank()) {
            var existing = messageRepository.findByDedupeKey(dedupeKey);
            if (existing.isPresent()) {
                log.info("Skipped duplicated notification: type={}, dedupeKey={}", type, dedupeKey);
                return existing.get();
            }
        }

        List<String> resolvedChannels = channels == null || channels.isEmpty()
                ? List.of(CHANNEL_IN_APP)
                : channels;

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

            for (String channel : resolvedChannels) {
                NotificationDeliveryLog delivery = new NotificationDeliveryLog();
                delivery.setMessageId(savedMessage.getId());
                delivery.setRecipientId(savedRecipient.getId());
                delivery.setUserId(user.getId());
                delivery.setChannel(channel);
                delivery.setProvider(channel);
                if (CHANNEL_IN_APP.equals(channel)) {
                    delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.SUCCESS);
                    delivery.setDeliveredTime(LocalDateTime.now());
                } else {
                    delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.SKIPPED);
                    delivery.setErrorCode("PROVIDER_NOT_CONFIGURED");
                    delivery.setErrorMessage("外部推送渠道未配置，已保留站内通知");
                }
                deliveryLogRepository.save(delivery);
            }
        }

        log.info("Created notification: type={}, recipients={}", type, recipients.size());
        return savedMessage;
    }

    private String toJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @Transactional(readOnly = true)
    public Page<NotificationDTO> getMyNotifications(Long userId, NotificationRecipient.ReadStatus readStatus, Pageable pageable) {
        Page<NotificationRecipient> recipients = readStatus == null
                ? recipientRepository.findMyRecipients(userId, pageable)
                : recipientRepository.findMyRecipientsByReadStatus(userId, readStatus, pageable);
        return recipients.map(recipient -> NotificationDTO.from(
                recipient,
                messageRepository.findById(recipient.getMessageId()).orElse(null)
        ));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return recipientRepository.countByUserIdAndReadStatus(userId, NotificationRecipient.ReadStatus.UNREAD);
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
    }

    @Transactional
    public int markAllRead(Long userId) {
        return recipientRepository.markAllReadByUserId(
                userId,
                NotificationRecipient.ReadStatus.READ,
                NotificationRecipient.ReadStatus.UNREAD
        );
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryLog> getDeliveries(Long messageId) {
        return deliveryLogRepository.findByMessageIdOrderByIdAsc(messageId);
    }
}
