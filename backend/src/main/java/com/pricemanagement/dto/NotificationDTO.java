package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationRecipient;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationDTO {
    private Long id;
    private Long messageId;
    private String type;
    private String title;
    private String content;
    private String businessType;
    private Long businessId;
    private String channels;
    private NotificationRecipient.ReadStatus readStatus;
    private LocalDateTime readTime;
    private LocalDateTime createdTime;

    public static NotificationDTO from(NotificationRecipient recipient, NotificationMessage message) {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(recipient.getId());
        dto.setMessageId(recipient.getMessageId());
        dto.setReadStatus(recipient.getReadStatus());
        dto.setReadTime(recipient.getReadTime());
        if (message != null) {
            dto.setType(message.getType());
            dto.setTitle(message.getTitle());
            dto.setContent(message.getContent());
            dto.setBusinessType(message.getBusinessType());
            dto.setBusinessId(message.getBusinessId());
            dto.setChannels(message.getChannels());
            dto.setCreatedTime(message.getCreatedTime());
        }
        return dto;
    }
}
