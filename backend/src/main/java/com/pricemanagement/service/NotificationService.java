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

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    public static final String TYPE_PRICE_PUBLISHED = "PRICE_PUBLISHED";
    public static final String CHANNEL_IN_APP = "IN_APP";
    public static final String CHANNEL_APP_PUSH = "APP_PUSH";
    public static final String CHANNEL_MINI_PROGRAM = "MINI_PROGRAM";

    private final NotificationMessageRepository messageRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public NotificationMessage createPricePublishedNotification(String title, String content, Long businessId,
                                                                Long createdBy, List<String> channels,
                                                                List<User.Role> recipientRoles) {
        List<User> users = userRepository.findByStatus(CommonStatus.ACTIVE).stream()
                .filter(user -> recipientRoles == null || recipientRoles.isEmpty() || recipientRoles.contains(user.getRole()))
                .toList();
        return createMessage(TYPE_PRICE_PUBLISHED, title, content, "PRICE", businessId, channels, users, createdBy);
    }

    @Transactional
    public NotificationMessage createMessage(String type, String title, String content, String businessType,
                                             Long businessId, List<String> channels, List<User> recipients,
                                             Long createdBy) {
        List<String> resolvedChannels = channels == null || channels.isEmpty()
                ? List.of(CHANNEL_IN_APP)
                : channels;

        NotificationMessage message = new NotificationMessage();
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setBusinessType(businessType);
        message.setBusinessId(businessId);
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
        recipient.setReadStatus(NotificationRecipient.ReadStatus.READ);
        recipient.setReadTime(LocalDateTime.now());
        recipientRepository.save(recipient);
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryLog> getDeliveries(Long messageId) {
        return deliveryLogRepository.findByMessageIdOrderByIdAsc(messageId);
    }
}
