package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_mini_program_eligibility", indexes = {
        @Index(name = "idx_notification_mini_eligibility_status_user", columnList = "row_status,user_id"),
        @Index(name = "idx_notification_mini_eligibility_authorized", columnList = "last_authorized_time")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_mini_eligibility_user", columnNames = "user_id")
})
public class NotificationMiniProgramEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "row_status", nullable = false, length = 20)
    private RowStatus rowStatus;

    @Column(name = "openid_bound", nullable = false)
    private Boolean openidBound = false;

    @Column(name = "configured_template_count", nullable = false)
    private Integer configuredTemplateCount = 0;

    @Column(name = "authorized_template_count", nullable = false)
    private Integer authorizedTemplateCount = 0;

    @Column(name = "available_total", nullable = false)
    private Integer availableTotal = 0;

    @Column(name = "last_authorized_time")
    private LocalDateTime lastAuthorizedTime;

    @Column(name = "config_fingerprint", nullable = false, length = 64)
    private String configFingerprint;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum RowStatus {
        NORMAL,
        LOW_BALANCE,
        UNBOUND,
        REJECTED
    }
}
