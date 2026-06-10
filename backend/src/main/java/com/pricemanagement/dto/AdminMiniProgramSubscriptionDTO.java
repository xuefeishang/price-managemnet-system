package com.pricemanagement.dto;

import lombok.Data;
import com.pricemanagement.dto.NotificationPreferenceDTO;
import com.pricemanagement.entity.NotificationDeliveryLog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AdminMiniProgramSubscriptionDTO {
    private Long userId;
    private String username;
    private String nickname;
    private String role;
    private boolean openidBound;
    private String openidMasked;
    private int priceAvailableCount;
    private int noticeAvailableCount;
    private String priceStatus;
    private String noticeStatus;
    private String status;
    private LocalDateTime lastAuthorizedTime;
    private List<TemplateState> templates = new ArrayList<>();
    private Resolution resolution;
    private List<NotificationDeliveryLog> recentDeliveries = new ArrayList<>();
    private List<NotificationPreferenceDTO> preferences = new ArrayList<>();

    @Data
    public static class TemplateState {
        private String notificationType;
        private String templateIdMasked;
        private String status;
        private int availableCount;
        private boolean authorized;
        private LocalDateTime lastAuthorizedTime;
    }

    @Data
    public static class Resolution {
        private String status;
        private String remark;
        private LocalDateTime remindAfter;
        private boolean followUpRequired;
        private Long resolvedBy;
        private LocalDateTime resolvedTime;
    }
}
