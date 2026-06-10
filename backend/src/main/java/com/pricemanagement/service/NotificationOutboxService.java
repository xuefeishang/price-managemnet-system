package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationOutbox;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationOutboxRepository;
import com.pricemanagement.service.notification.DeliveryResult;
import com.pricemanagement.service.notification.NotificationChannelProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxService {

    private static final String EVENT_DELIVERY_REQUESTED = "NOTIFICATION_DELIVERY_REQUESTED";
    private static final String AGGREGATE_DELIVERY = "NOTIFICATION_DELIVERY";

    private final NotificationOutboxRepository outboxRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationOutboxTransactionService transactionService;
    private final NotificationChannelProviderRegistry providerRegistry;
    private final NotificationPreferenceService notificationPreferenceService;
    private final ObjectMapper objectMapper;

    @Value("${notification.outbox.enabled:true}")
    private boolean enabled;

    @Value("${notification.outbox.batch-size:20}")
    private int batchSize;

    @Value("${notification.outbox.lock-seconds:120}")
    private int lockSeconds;

    @Transactional
    public NotificationOutbox enqueueDelivery(NotificationDeliveryLog delivery) {
        return enqueueDelivery(delivery, null);
    }

    @Transactional
    public NotificationOutbox enqueueDelivery(NotificationDeliveryLog delivery, LocalDateTime nextRetryTime) {
        return outboxRepository.findByAggregateTypeAndAggregateId(AGGREGATE_DELIVERY, delivery.getId())
                .orElseGet(() -> {
                    NotificationOutbox outbox = new NotificationOutbox();
                    outbox.setEventType(EVENT_DELIVERY_REQUESTED);
                    outbox.setAggregateType(AGGREGATE_DELIVERY);
                    outbox.setAggregateId(delivery.getId());
                    outbox.setStatus(NotificationOutbox.OutboxStatus.PENDING);
                    outbox.setNextRetryTime(nextRetryTime);
                    outbox.setPayloadJson(toPayload(delivery));
                    return outboxRepository.save(outbox);
                });
    }

    @Scheduled(fixedDelayString = "${notification.outbox.poll-delay-ms:30000}")
    public void processDueOutbox() {
        if (!enabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String workerId = workerId();
        for (Long id : outboxRepository.findClaimableIds(now, PageRequest.of(0, Math.max(batchSize, 1)))) {
            int claimed = transactionService.claim(id, workerId, now, now.plusSeconds(Math.max(lockSeconds, 30)));
            if (claimed == 1) {
                processClaimed(id);
            }
        }
    }

    @Transactional
    public void retryDelivery(Long deliveryId) {
        NotificationDeliveryLog delivery = deliveryLogRepository.findById(deliveryId)
                .orElseThrow(() -> new IllegalArgumentException("投递记录不存在"));
        validateRetryable(delivery);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);
        delivery.setErrorCode(null);
        delivery.setErrorMessage(null);
        delivery.setProviderMessageId(null);
        delivery.setDeliveredTime(null);
        deliveryLogRepository.save(delivery);

        NotificationOutbox outbox = enqueueDelivery(delivery);
        outbox.setStatus(NotificationOutbox.OutboxStatus.PENDING);
        outbox.setRetryCount(0);
        outbox.setNextRetryTime(LocalDateTime.now());
        outbox.setLockedBy(null);
        outbox.setLockUntil(null);
        outbox.setLastErrorCode(null);
        outbox.setLastErrorMessage(null);
        outboxRepository.save(outbox);
    }

    public void processClaimed(Long outboxId) {
        transactionService.loadClaimed(outboxId).ifPresent(workItem -> {
            NotificationDeliveryLog delivery = workItem.delivery();
            boolean testDelivery = Boolean.TRUE.equals(delivery.getTest());
            if (!testDelivery && Boolean.TRUE.equals(workItem.recipient().getArchived())) {
                transactionService.applyResult(outboxId, delivery.getId(),
                        DeliveryResult.skipped("RECIPIENT_ARCHIVED", "通知已归档，取消外部投递"));
                return;
            }
            if (workItem.message().getExpireTime() != null
                    && !workItem.message().getExpireTime().isAfter(LocalDateTime.now())) {
                transactionService.applyResult(outboxId, delivery.getId(),
                        DeliveryResult.skipped("MESSAGE_EXPIRED", "通知已过期，取消外部投递"));
                return;
            }
            if (!testDelivery) {
                NotificationPreferenceService.DeliveryPreferenceDecision decision =
                        notificationPreferenceService.resolveExternalDelivery(
                                delivery.getUserId(),
                                workItem.message().getType(),
                                delivery.getChannel(),
                                workItem.message().getPriority(),
                                LocalDateTime.now());
                if (!decision.enabled()) {
                    transactionService.applyResult(outboxId, delivery.getId(),
                            DeliveryResult.skipped(decision.errorCode(), decision.errorMessage()));
                    return;
                }
                if (decision.delayed()) {
                    transactionService.defer(outboxId, delivery.getId(), decision.nextDeliveryTime(),
                            decision.errorCode(), decision.errorMessage());
                    return;
                }
            }

            DeliveryResult result;
            try {
                result = providerRegistry.find(delivery.getChannel())
                        .map(provider -> provider.send(workItem.message(), delivery))
                        .orElseGet(() -> DeliveryResult.skipped("PROVIDER_NOT_CONFIGURED", "外部推送渠道未配置，已保留站内通知"));
            } catch (RuntimeException e) {
                log.warn("Notification provider failed: deliveryId={}, channel={}, exception={}",
                        delivery.getId(), delivery.getChannel(), e.getClass().getSimpleName());
                result = DeliveryResult.failed("PROVIDER_EXCEPTION", "外部通知 Provider 调用异常");
            }
            transactionService.applyResult(outboxId, delivery.getId(), result);
        });
    }

    private void validateRetryable(NotificationDeliveryLog delivery) {
        if (NotificationService.CHANNEL_IN_APP.equals(delivery.getChannel())) {
            throw new IllegalArgumentException("站内通知投递不需要重试");
        }
        if (delivery.getStatus() == NotificationDeliveryLog.DeliveryStatus.SUCCESS) {
            throw new IllegalArgumentException("成功投递不允许重试，避免重复发送");
        }
        if (delivery.getStatus() == NotificationDeliveryLog.DeliveryStatus.PENDING) {
            throw new IllegalArgumentException("待处理投递已在队列中，无需重复重试");
        }
        if (delivery.getStatus() == NotificationDeliveryLog.DeliveryStatus.SKIPPED) {
            throw new IllegalArgumentException("已跳过投递不允许重试，请先确认跳过原因");
        }
        if (delivery.getStatus() != NotificationDeliveryLog.DeliveryStatus.FAILED) {
            throw new IllegalArgumentException("仅失败的外部通知投递允许重试");
        }
    }

    private String toPayload(NotificationDeliveryLog delivery) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "messageId", delivery.getMessageId(),
                    "recipientId", delivery.getRecipientId(),
                    "userId", delivery.getUserId(),
                    "channel", delivery.getChannel()
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String workerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ProcessHandle.current().pid();
        } catch (UnknownHostException e) {
            return "notification-worker:" + ProcessHandle.current().pid();
        }
    }
}
