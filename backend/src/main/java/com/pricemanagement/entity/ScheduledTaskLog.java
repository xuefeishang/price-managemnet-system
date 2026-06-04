package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_scheduled_task_log", indexes = {
        @Index(name = "idx_scheduled_task_log_task", columnList = "task_id"),
        @Index(name = "idx_scheduled_task_log_status", columnList = "status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_scheduled_time", columnNames = {"task_id", "scheduled_time", "trigger_type"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ScheduledTaskLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "task_code", nullable = false, length = 80)
    private String taskCode;

    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType = TriggerType.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RunStatus status = RunStatus.RUNNING;

    @Column(name = "started_time")
    private LocalDateTime startedTime;

    @Column(name = "finished_time")
    private LocalDateTime finishedTime;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "business_type", length = 50)
    private String businessType;

    @Column(name = "business_id")
    private Long businessId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "error_stack", columnDefinition = "TEXT")
    private String errorStack;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    public enum TriggerType {
        SCHEDULED,
        MANUAL_TEST,
        MANUAL_RUN
    }

    public enum RunStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        SKIPPED
    }
}
