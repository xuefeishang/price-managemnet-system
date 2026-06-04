package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_recipient", indexes = {
        @Index(name = "idx_notification_recipient_user", columnList = "user_id, read_status"),
        @Index(name = "idx_notification_recipient_message", columnList = "message_id"),
        @Index(name = "idx_notification_recipient_user_time", columnList = "user_id, id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_message_user", columnNames = {"message_id", "user_id"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "read_status", nullable = false, length = 20)
    private ReadStatus readStatus = ReadStatus.UNREAD;

    @Column(name = "read_time")
    private LocalDateTime readTime;

    @Column(name = "archived", nullable = false)
    private Boolean archived = false;

    @Column(name = "archived_time")
    private LocalDateTime archivedTime;

    @Column(name = "first_seen_time")
    private LocalDateTime firstSeenTime;

    public enum ReadStatus {
        UNREAD,
        READ
    }
}
