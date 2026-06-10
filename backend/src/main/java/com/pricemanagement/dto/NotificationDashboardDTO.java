package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class NotificationDashboardDTO {
    private long todayMessageCount;
    private long todayDeliveryCount;
    private long successDeliveryCount;
    private long failedDeliveryCount;
    private long skippedDeliveryCount;
    private long pendingDeliveryCount;
    private double providerFailureRate;
    private long outboxPendingCount;
    private long outboxProcessingCount;
    private long outboxFailedCount;
    private long outboxRetryCount;
    private Long oldestPendingWaitSeconds;
    private LocalDateTime oldestPendingTime;
    private List<ChannelMetric> channelMetrics = new ArrayList<>();
    private List<TypeMetric> highFrequencyTypes = new ArrayList<>();

    @Data
    public static class ChannelMetric {
        private String channel;
        private long successCount;
        private long failedCount;
        private long skippedCount;
        private long pendingCount;
        private double failureRate;
    }

    @Data
    public static class TypeMetric {
        private String type;
        private long count;
    }
}
