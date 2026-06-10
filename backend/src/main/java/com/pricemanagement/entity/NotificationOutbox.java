package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_outbox", indexes = {
        @Index(name = "idx_notification_outbox_status_retry", columnList = "status, next_retry_time"),
        @Index(name = "idx_notification_outbox_lock", columnList = "locked_by, lock_until")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_notification_outbox_aggregate",
                columnNames = {"aggregate_type", "aggregate_id"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NotificationOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_time")
    private LocalDateTime nextRetryTime;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "last_error_message", length = 500)
    private String lastErrorMessage;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        SUCCESS,
        FAILED
    }
}
