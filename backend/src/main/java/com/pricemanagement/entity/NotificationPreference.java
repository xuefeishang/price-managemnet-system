package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "notification_preference", indexes = {
        @Index(name = "idx_notification_preference_user", columnList = "user_id"),
        @Index(name = "idx_notification_preference_type_channel", columnList = "notification_type, channel")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_preference_user_type_channel",
                columnNames = {"user_id", "notification_type", "channel"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "channel", nullable = false, length = 50)
    private String channel;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "quiet_start_time")
    private LocalTime quietStartTime;

    @Column(name = "quiet_end_time")
    private LocalTime quietEndTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
