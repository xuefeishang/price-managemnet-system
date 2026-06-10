package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class NotificationCreateCommand {
    private String eventType;
    private String title;
    private String summary;
    private String content;
    private String businessType;
    private Long businessId;
    private List<Long> recipientUserIds;
    private List<User.Role> recipientRoles;
    @JsonIgnore
    private List<User> recipientUsers;
    private List<String> channels;
    private NotificationMessage.NotificationPriority priority = NotificationMessage.NotificationPriority.NORMAL;
    private String linkType;
    private String linkParams;
    private String dedupeKey;
    private LocalDateTime expireTime;
    private Long createdBy;
}
