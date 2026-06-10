package com.pricemanagement.dto;

import lombok.Data;

@Data
public class NotificationMiniProgramCoverageDTO {
    private long targetCount;
    private long openidBound;
    private long authorized;
    private long reachable;
    private long inAppFallback;
    private long rejectedOrBanned;
    private long lowBalance;
    private String notificationType;
}
