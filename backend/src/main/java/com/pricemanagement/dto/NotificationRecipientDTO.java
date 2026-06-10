package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationRecipient;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class NotificationRecipientDTO {
    private Long id;
    private Long messageId;
    private Long userId;
    private String username;
    private String nickname;
    private NotificationRecipient.ReadStatus readStatus;
    private LocalDateTime readTime;
    private Boolean archived;
    private LocalDateTime archivedTime;
    private LocalDateTime firstSeenTime;

    public NotificationRecipientDTO(Long id, Long messageId, Long userId, String username, String nickname,
                                    NotificationRecipient.ReadStatus readStatus, LocalDateTime readTime,
                                    Boolean archived, LocalDateTime archivedTime, LocalDateTime firstSeenTime) {
        this.id = id;
        this.messageId = messageId;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.readStatus = readStatus;
        this.readTime = readTime;
        this.archived = archived;
        this.archivedTime = archivedTime;
        this.firstSeenTime = firstSeenTime;
    }
}
