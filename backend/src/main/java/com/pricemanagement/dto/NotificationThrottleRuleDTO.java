package com.pricemanagement.dto;

import lombok.Data;

@Data
public class NotificationThrottleRuleDTO {
    private String type;
    private boolean enabled;
    private int windowMinutes;
    private int maxCount;
    private long currentCount;
    private boolean throttled;
}
