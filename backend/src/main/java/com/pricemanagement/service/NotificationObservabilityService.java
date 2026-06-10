package com.pricemanagement.service;

import com.pricemanagement.config.properties.NotificationWebhookProperties;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationDashboardDTO;
import com.pricemanagement.dto.NotificationProviderHealthDTO;
import com.pricemanagement.dto.NotificationThrottleRuleDTO;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationOutbox;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.NotificationOutboxRepository;
import com.pricemanagement.repository.SysDictRepository;
import com.pricemanagement.service.notification.NotificationChannelProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationObservabilityService {

    private static final int PROVIDER_HEALTH_WINDOW_HOURS = 24;

    private final NotificationMessageRepository messageRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationOutboxRepository outboxRepository;
    private final SysDictRepository sysDictRepository;
    private final NotificationChannelProviderRegistry providerRegistry;
    private final NotificationWebhookProperties webhookProperties;
    private final NotificationMiniProgramRuntimeConfigService miniProgramRuntimeConfigService;
    private final NotificationThrottleService throttleService;

    @Transactional(readOnly = true)
    public NotificationDashboardDTO dashboard() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        NotificationDashboardDTO dto = new NotificationDashboardDTO();
        dto.setTodayMessageCount(messageRepository.countByCreatedTimeBetween(todayStart, tomorrowStart));
        dto.setTodayDeliveryCount(deliveryLogRepository.countByCreatedTimeBetween(todayStart, tomorrowStart));
        dto.setSuccessDeliveryCount(deliveryLogRepository.countByStatusAndCreatedTimeBetween(
                NotificationDeliveryLog.DeliveryStatus.SUCCESS, todayStart, tomorrowStart));
        dto.setFailedDeliveryCount(deliveryLogRepository.countByStatusAndCreatedTimeBetween(
                NotificationDeliveryLog.DeliveryStatus.FAILED, todayStart, tomorrowStart));
        dto.setSkippedDeliveryCount(deliveryLogRepository.countByStatusAndCreatedTimeBetween(
                NotificationDeliveryLog.DeliveryStatus.SKIPPED, todayStart, tomorrowStart));
        dto.setPendingDeliveryCount(deliveryLogRepository.countByStatusAndCreatedTimeBetween(
                NotificationDeliveryLog.DeliveryStatus.PENDING, todayStart, tomorrowStart));
        dto.setProviderFailureRate(rate(dto.getFailedDeliveryCount(), dto.getSuccessDeliveryCount() + dto.getFailedDeliveryCount()));
        dto.setOutboxPendingCount(outboxRepository.countByStatus(NotificationOutbox.OutboxStatus.PENDING));
        dto.setOutboxProcessingCount(outboxRepository.countByStatus(NotificationOutbox.OutboxStatus.PROCESSING));
        dto.setOutboxFailedCount(outboxRepository.countByStatus(NotificationOutbox.OutboxStatus.FAILED));
        dto.setOutboxRetryCount(outboxRepository.sumActiveRetryCount());
        outboxRepository.findTopByStatusOrderByCreatedTimeAsc(NotificationOutbox.OutboxStatus.PENDING)
                .ifPresent(outbox -> {
                    dto.setOldestPendingTime(outbox.getCreatedTime());
                    dto.setOldestPendingWaitSeconds(Duration.between(outbox.getCreatedTime(), LocalDateTime.now()).getSeconds());
                });
        dto.setChannelMetrics(channelMetrics(todayStart));
        dto.setHighFrequencyTypes(highFrequencyTypes(todayStart));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<NotificationProviderHealthDTO> providerHealth() {
        return sysDictRepository.findByCategoryAndStatusOrderBySortOrderAsc("notification_channel", CommonStatus.ACTIVE).stream()
                .filter(dict -> !NotificationService.CHANNEL_IN_APP.equals(dict.getDictKey()))
                .map(dict -> providerHealth(dict.getDictKey()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationThrottleRuleDTO> throttleRules() {
        return throttleService.listRules();
    }

    private NotificationProviderHealthDTO providerHealth(String channel) {
        LocalDateTime healthWindowStart = LocalDateTime.now().minusHours(PROVIDER_HEALTH_WINDOW_HOURS);
        NotificationProviderHealthDTO dto = new NotificationProviderHealthDTO();
        dto.setChannel(channel);
        dto.setProvider(channel);
        dto.setRegistered(providerRegistry.hasProvider(channel));
        dto.setConfigured(isConfigured(channel));
        dto.setPendingCount(deliveryLogRepository.countByChannelAndStatusAndCreatedTimeAfter(
                channel, NotificationDeliveryLog.DeliveryStatus.PENDING, healthWindowStart));
        dto.setFailedCount(deliveryLogRepository.countByChannelAndStatusAndCreatedTimeAfter(
                channel, NotificationDeliveryLog.DeliveryStatus.FAILED, healthWindowStart));
        dto.setConsecutiveFailureCount(consecutiveFailureCount(channel));
        deliveryLogRepository.findTopByChannelOrderByUpdatedTimeDesc(channel)
                .ifPresent(delivery -> {
                    dto.setLastDeliveryTime(delivery.getUpdatedTime());
                    dto.setLastStatus(delivery.getStatus().name());
        dto.setLastErrorCode(delivery.getErrorCode());
                    dto.setLastErrorMessage(delivery.getErrorMessage());
                });
        dto.setHealthStatus(resolveHealthStatus(dto, channel));
        return dto;
    }

    private boolean isConfigured(String channel) {
        if (NotificationService.CHANNEL_WEBHOOK.equals(channel)) {
            return webhookProperties.isEnabled()
                    && webhookProperties.getUrl() != null
                    && !webhookProperties.getUrl().isBlank();
        }
        if (NotificationService.CHANNEL_MINI_PROGRAM.equals(channel)) {
            NotificationMiniProgramRuntimeConfigService.RuntimeConfig runtimeConfig =
                    miniProgramRuntimeConfigService.activeConfig();
            return runtimeConfig.isOperationallyReady();
        }
        return providerRegistry.hasProvider(channel);
    }

    private long consecutiveFailureCount(String channel) {
        long count = 0;
        List<NotificationDeliveryLog.DeliveryStatus> statuses =
                deliveryLogRepository.findRecentStatusesByChannel(channel, PageRequest.of(0, 10));
        for (NotificationDeliveryLog.DeliveryStatus status : statuses) {
            if (status == NotificationDeliveryLog.DeliveryStatus.FAILED) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    private String resolveHealthStatus(NotificationProviderHealthDTO dto, String channel) {
        if (!dto.isRegistered()) {
            return "NOT_CONFIGURED";
        }
        if (!dto.isConfigured()) {
            if (NotificationService.CHANNEL_MINI_PROGRAM.equals(channel)
                    && miniProgramRuntimeConfigService.activeConfig().hasAnyConfiguration()) {
                return "DEGRADED";
            }
            return "NOT_CONFIGURED";
        }
        if (dto.getConsecutiveFailureCount() >= 3 || dto.getFailedCount() >= 20) {
            return "DOWN";
        }
        if (dto.getConsecutiveFailureCount() > 0 || dto.getFailedCount() > 0 || dto.getPendingCount() > 0) {
            return "DEGRADED";
        }
        return "OK";
    }

    private List<NotificationDashboardDTO.ChannelMetric> channelMetrics(LocalDateTime startTime) {
        Map<String, NotificationDashboardDTO.ChannelMetric> metrics = new LinkedHashMap<>();
        for (Object[] row : deliveryLogRepository.countByChannelAndStatusSince(startTime)) {
            String channel = (String) row[0];
            NotificationDeliveryLog.DeliveryStatus status = (NotificationDeliveryLog.DeliveryStatus) row[1];
            long count = (Long) row[2];
            NotificationDashboardDTO.ChannelMetric metric =
                    metrics.computeIfAbsent(channel, ignored -> {
                        NotificationDashboardDTO.ChannelMetric item = new NotificationDashboardDTO.ChannelMetric();
                        item.setChannel(channel);
                        return item;
                    });
            switch (status) {
                case SUCCESS -> metric.setSuccessCount(count);
                case FAILED -> metric.setFailedCount(count);
                case SKIPPED -> metric.setSkippedCount(count);
                case PENDING -> metric.setPendingCount(count);
            }
        }
        metrics.values().forEach(metric ->
                metric.setFailureRate(rate(metric.getFailedCount(), metric.getSuccessCount() + metric.getFailedCount())));
        return List.copyOf(metrics.values());
    }

    private List<NotificationDashboardDTO.TypeMetric> highFrequencyTypes(LocalDateTime startTime) {
        return messageRepository.countByTypeSince(startTime).stream()
                .limit(8)
                .map(row -> {
                    NotificationDashboardDTO.TypeMetric metric = new NotificationDashboardDTO.TypeMetric();
                    metric.setType((String) row[0]);
                    metric.setCount((Long) row[1]);
                    return metric;
                })
                .toList();
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return Math.round((numerator * 10000.0 / denominator)) / 100.0;
    }
}
