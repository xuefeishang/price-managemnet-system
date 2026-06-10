package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_mini_program_subscription", indexes = {
        @Index(name = "idx_notification_mini_sub_user", columnList = "user_id"),
        @Index(name = "idx_notification_mini_sub_template", columnList = "template_id"),
        @Index(name = "idx_notification_mini_sub_type", columnList = "notification_type")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_mini_sub_user_type_template",
                columnNames = {"user_id", "notification_type", "template_id"})
})
public class NotificationMiniProgramSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "openid", nullable = false, length = 100)
    private String openid;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "template_id", nullable = false, length = 100)
    private String templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.UNKNOWN;

    @Column(name = "available_count", nullable = false)
    private Integer availableCount = 0;

    @Column(name = "last_authorized_time")
    private LocalDateTime lastAuthorizedTime;

    @Column(name = "source", length = 50)
    private String source;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum SubscriptionStatus {
        UNKNOWN,
        ACCEPT,
        REJECT,
        BAN
    }
}
