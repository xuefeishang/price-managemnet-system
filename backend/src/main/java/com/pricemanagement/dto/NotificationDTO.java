package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationRecipient;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class NotificationDTO {
    private Long id;
    private Long messageId;
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
    private NotificationRecipient.ReadStatus readStatus;
    private LocalDateTime readTime;
    private Boolean archived;
    private LocalDateTime createdTime;

    public NotificationDTO(Long id, Long messageId, String type, String title, String summary, String content,
                           String businessType, Long businessId, String channels,
                           NotificationMessage.NotificationPriority priority, String linkType, String linkParams,
                           NotificationRecipient.ReadStatus readStatus, LocalDateTime readTime, Boolean archived,
                           LocalDateTime createdTime) {
        this.id = id;
        this.messageId = messageId;
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
        this.readStatus = readStatus;
        this.readTime = readTime;
        this.archived = archived;
        this.createdTime = createdTime;
    }

    public static NotificationDTO from(NotificationRecipient recipient, NotificationMessage message) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(recipient.getId());
        dto.setMessageId(recipient.getMessageId());
        dto.setReadStatus(recipient.getReadStatus());
        dto.setReadTime(recipient.getReadTime());
        dto.setArchived(recipient.getArchived());
        if (message != null) {
            dto.setType(message.getType());
            dto.setTitle(message.getTitle());
            dto.setSummary(message.getSummary());
            dto.setContent(message.getContent());
            dto.setBusinessType(message.getBusinessType());
            dto.setBusinessId(message.getBusinessId());
            dto.setChannels(message.getChannels());
            dto.setPriority(message.getPriority());
            dto.setLinkType(message.getLinkType());
            dto.setLinkParams(message.getLinkParams());
            dto.setCreatedTime(message.getCreatedTime());
        }
        return dto;
    }
}
