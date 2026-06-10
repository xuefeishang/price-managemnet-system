package com.pricemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationSseEventDTO {
    private String eventType;
    private Long unreadCount;
    private Long messageId;
    private String notificationType;
}
