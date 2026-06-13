package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class NotificationMiniProgramTemplateDTO {
    private List<Group> groups = new ArrayList<>();
    private Summary summary = new Summary();

    @Data
    public static class Summary {
        private int configuredCount;
        private int pendingValidationCount;
        private int draftCount;
        private int activeCount;
    }

    @Data
    public static class Group {
        private String notificationType;
        private long authorizedUsers;
        private long needReauthorizeUsers;
        private long estimatedReachableUsers;
        private List<Item> versions = new ArrayList<>();
    }

    @Data
    public static class Item {
        private Long id;
        private String notificationType;
        private String templateIdMasked;
        private String page;
        private Map<String, String> fields;
        private String status;
        private String lastTestStatus;
        private String lastTestMessage;
        private Long lastTestDeliveryId;
        private LocalDateTime lastTestTime;
        private long authorizedUsers;
        private long needReauthorizeUsers;
        private long estimatedReachableUsers;
        private Long publishedBy;
        private LocalDateTime publishedTime;
        private LocalDateTime updatedTime;
    }
}
