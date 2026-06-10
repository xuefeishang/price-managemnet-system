package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "system_notice", indexes = {
        @Index(name = "idx_system_notice_status_schedule", columnList = "status, scheduled_publish_time"),
        @Index(name = "idx_system_notice_created", columnList = "created_time")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SystemNotice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "target_roles", nullable = false, columnDefinition = "TEXT")
    private String targetRoles;

    @Column(name = "channels", nullable = false, columnDefinition = "TEXT")
    private String channels;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private NotificationMessage.NotificationPriority priority = NotificationMessage.NotificationPriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NoticeStatus status = NoticeStatus.DRAFT;

    @Column(name = "scheduled_publish_time")
    private LocalDateTime scheduledPublishTime;

    @Column(name = "published_time")
    private LocalDateTime publishedTime;

    @Column(name = "cancelled_time")
    private LocalDateTime cancelledTime;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "notification_message_id")
    private Long notificationMessageId;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum NoticeStatus {
        DRAFT,
        SCHEDULED,
        PUBLISHED,
        CANCELLED,
        EXPIRED
    }
}
