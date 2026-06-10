package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NotificationMiniProgramSubscriptionDTO {
    private boolean enabled;
    private boolean configured;
    private boolean openidBound;
    private List<TemplateSubscription> templates;

    @Data
    public static class TemplateSubscription {
        private String notificationType;
        private String templateId;
        private String status;
        private int availableCount;
        private boolean authorized;
        private LocalDateTime lastAuthorizedTime;
    }
}
