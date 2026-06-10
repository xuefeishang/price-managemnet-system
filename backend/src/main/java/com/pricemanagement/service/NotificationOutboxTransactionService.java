package com.pricemanagement.service;

import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationOutbox;
import com.pricemanagement.entity.NotificationRecipient;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.NotificationOutboxRepository;
import com.pricemanagement.repository.NotificationRecipientRepository;
import com.pricemanagement.service.notification.DeliveryResult;
import com.pricemanagement.util.SensitiveDataMasker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationOutboxTransactionService {

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationMessageRepository messageRepository;
    private final NotificationRecipientRepository recipientRepository;

    @Value("${notification.outbox.max-retries:3}")
    private int maxRetries;

    @Transactional
    public int claim(Long id, String workerId, LocalDateTime now, LocalDateTime lockUntil) {
        return outboxRepository.claim(id, workerId, now, lockUntil);
    }

    @Transactional
    public Optional<DeliveryWorkItem> loadClaimed(Long outboxId) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != NotificationOutbox.OutboxStatus.PROCESSING) {
            return Optional.empty();
        }

        NotificationDeliveryLog delivery = deliveryLogRepository.findById(outbox.getAggregateId()).orElse(null);
        if (delivery == null) {
            markOutboxFailed(outbox, "DELIVERY_NOT_FOUND", "投递记录不存在", true);
            return Optional.empty();
        }

        NotificationMessage message = messageRepository.findById(delivery.getMessageId()).orElse(null);
        if (message == null) {
            markDeliveryFailed(delivery, "MESSAGE_NOT_FOUND", "通知消息不存在");
            markOutboxFailed(outbox, "MESSAGE_NOT_FOUND", "通知消息不存在", true);
            return Optional.empty();
        }

        NotificationRecipient recipient = recipientRepository.findById(delivery.getRecipientId()).orElse(null);
        if (recipient == null) {
            markDeliveryFailed(delivery, "RECIPIENT_NOT_FOUND", "通知收件记录不存在");
            markOutboxFailed(outbox, "RECIPIENT_NOT_FOUND", "通知收件记录不存在", true);
            return Optional.empty();
        }

        return Optional.of(new DeliveryWorkItem(message, delivery, recipient));
    }

    @Transactional
    public void applyResult(Long outboxId, Long deliveryId, DeliveryResult result) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != NotificationOutbox.OutboxStatus.PROCESSING) {
            return;
        }
        NotificationDeliveryLog delivery = deliveryLogRepository.findById(deliveryId).orElse(null);
        if (delivery == null) {
            markOutboxFailed(outbox, "DELIVERY_NOT_FOUND", "投递记录不存在", true);
            return;
        }

        DeliveryResult normalized = result == null
                ? DeliveryResult.failed("PROVIDER_EMPTY_RESULT", "外部通知 Provider 未返回投递结果")
                : result;

        if (normalized.success()) {
            delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.SUCCESS);
            delivery.setProviderMessageId(trimToLength(normalized.providerMessageId(), 100));
            delivery.setDeliveredTime(LocalDateTime.now());
            delivery.setErrorCode(null);
            delivery.setErrorMessage(null);
            deliveryLogRepository.save(delivery);
            markOutboxSuccess(outbox);
            return;
        }

        if (normalized.skipped()) {
            delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.SKIPPED);
            delivery.setErrorCode(normalized.errorCode());
            delivery.setErrorMessage(trimError(normalized.errorMessage()));
            delivery.setDeliveredTime(LocalDateTime.now());
            deliveryLogRepository.save(delivery);
            markOutboxSuccess(outbox);
            return;
        }

        markDeliveryFailed(delivery, normalized.errorCode(), normalized.errorMessage());
        markOutboxFailed(outbox, normalized.errorCode(), normalized.errorMessage(), false);
    }

    @Transactional
    public void defer(Long outboxId, Long deliveryId, LocalDateTime nextRetryTime, String errorCode, String errorMessage) {
        NotificationOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if (outbox == null || outbox.getStatus() != NotificationOutbox.OutboxStatus.PROCESSING) {
            return;
        }
        NotificationDeliveryLog delivery = deliveryLogRepository.findById(deliveryId).orElse(null);
        if (delivery != null) {
            delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);
            delivery.setErrorCode(errorCode);
            delivery.setErrorMessage(trimError(errorMessage));
            deliveryLogRepository.save(delivery);
        }
        outbox.setStatus(NotificationOutbox.OutboxStatus.PENDING);
        outbox.setLockedBy(null);
        outbox.setLockUntil(null);
        outbox.setNextRetryTime(nextRetryTime);
        outbox.setLastErrorCode(errorCode);
        outbox.setLastErrorMessage(trimError(errorMessage));
        outboxRepository.save(outbox);
    }

    private void markDeliveryFailed(NotificationDeliveryLog delivery, String errorCode, String errorMessage) {
        delivery.setRetryCount((delivery.getRetryCount() == null ? 0 : delivery.getRetryCount()) + 1);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.FAILED);
        delivery.setErrorCode(errorCode);
        delivery.setErrorMessage(trimError(errorMessage));
        deliveryLogRepository.save(delivery);
    }

    private void markOutboxSuccess(NotificationOutbox outbox) {
        outbox.setStatus(NotificationOutbox.OutboxStatus.SUCCESS);
        outbox.setLockedBy(null);
        outbox.setLockUntil(null);
        outbox.setLastErrorCode(null);
        outbox.setLastErrorMessage(null);
        outboxRepository.save(outbox);
    }

    private void markOutboxFailed(NotificationOutbox outbox, String errorCode, String errorMessage, boolean terminal) {
        int retryCount = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        outbox.setRetryCount(retryCount);
        outbox.setLockedBy(null);
        outbox.setLockUntil(null);
        outbox.setLastErrorCode(errorCode);
        outbox.setLastErrorMessage(trimError(errorMessage));
        if (terminal || retryCount >= maxRetries) {
            outbox.setStatus(NotificationOutbox.OutboxStatus.FAILED);
        } else {
            outbox.setStatus(NotificationOutbox.OutboxStatus.PENDING);
            outbox.setNextRetryTime(LocalDateTime.now().plusSeconds((long) Math.pow(2, retryCount) * 60));
        }
        outboxRepository.save(outbox);
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "外部通知 Provider 调用异常";
        }
        return trimToLength(SensitiveDataMasker.mask(message), 500);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public record DeliveryWorkItem(NotificationMessage message, NotificationDeliveryLog delivery,
                                   NotificationRecipient recipient) {
    }
}
