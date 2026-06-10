package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.User;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SystemNoticeCreateRequest {
    private String title;
    private String summary;
    private String content;
    private List<User.Role> targetRoles;
    private List<String> channels;
    private NotificationMessage.NotificationPriority priority = NotificationMessage.NotificationPriority.NORMAL;
    private LocalDateTime scheduledPublishTime;
    private LocalDateTime expireTime;
}
