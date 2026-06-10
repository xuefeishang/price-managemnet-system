package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_message", indexes = {
        @Index(name = "idx_notification_type", columnList = "type"),
        @Index(name = "idx_notification_business", columnList = "business_type, business_id"),
        @Index(name = "idx_notification_type_created", columnList = "type, created_time")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_dedupe_key", columnNames = "dedupe_key")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NotificationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "summary", length = 300)
    private String summary;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "business_type", length = 50)
    private String businessType;

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "channels", length = 200)
    private String channels;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "link_type", length = 50)
    private String linkType;

    @Column(name = "link_params", columnDefinition = "TEXT")
    private String linkParams;

    @Column(name = "dedupe_key", length = 150)
    private String dedupeKey;

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "event_count", nullable = false)
    private Long eventCount = 1L;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    public enum NotificationPriority {
        LOW,
        NORMAL,
        HIGH,
        URGENT
    }
}
