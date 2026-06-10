package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_mini_program_resolution", uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_mini_resolution_user", columnNames = "user_id")
})
public class NotificationMiniProgramResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolve_status", nullable = false, length = 20)
    private ResolveStatus resolveStatus = ResolveStatus.OPEN;

    @Column(name = "resolve_remark", length = 500)
    private String resolveRemark;

    @Column(name = "remind_after")
    private LocalDateTime remindAfter;

    @Column(name = "follow_up_required", nullable = false)
    private Boolean followUpRequired = false;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum ResolveStatus {
        OPEN,
        RESOLVED,
        SNOOZED,
        FOLLOW_UP
    }
}
