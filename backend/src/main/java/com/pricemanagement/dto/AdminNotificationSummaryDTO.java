package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationMessage;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminNotificationSummaryDTO {
    private Long id;
    private String type;
    private String title;
    private String summary;
    private String content;
    private String businessType;
    private Long businessId;
    private String channels;
    private NotificationMessage.NotificationPriority priority;
    private String linkType;
    private String linkParams;
    private String dedupeKey;
    private LocalDateTime expireTime;
    private Long createdBy;
    private LocalDateTime createdTime;
    private Long recipientCount;
    private Long unreadCount;
    private Long failedDeliveryCount;

    public AdminNotificationSummaryDTO(Long id, String type, String title, String summary, String content,
                                       String businessType, Long businessId, String channels,
                                       NotificationMessage.NotificationPriority priority, String linkType,
                                       String linkParams, String dedupeKey, LocalDateTime expireTime,
                                       Long createdBy, LocalDateTime createdTime, Long recipientCount,
                                       Long unreadCount, Long failedDeliveryCount) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.content = content;
        this.businessType = businessType;
        this.businessId = businessId;
        this.channels = channels;
        this.priority = priority;
        this.linkType = linkType;
        this.linkParams = linkParams;
        this.dedupeKey = dedupeKey;
        this.expireTime = expireTime;
        this.createdBy = createdBy;
        this.createdTime = createdTime;
        this.recipientCount = recipientCount;
        this.unreadCount = unreadCount;
        this.failedDeliveryCount = failedDeliveryCount;
    }
}
