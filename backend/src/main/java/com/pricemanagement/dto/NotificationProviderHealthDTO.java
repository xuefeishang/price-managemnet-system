package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationProviderHealthDTO {
    private String channel;
    private String provider;
    private boolean registered;
    private boolean configured;
    private long pendingCount;
    private long failedCount;
    private long consecutiveFailureCount;
    private LocalDateTime lastDeliveryTime;
    private String lastStatus;
    private String lastErrorCode;
    private String lastErrorMessage;
    private String healthStatus;
}
