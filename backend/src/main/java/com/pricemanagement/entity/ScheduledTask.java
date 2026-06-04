package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_scheduled_task", indexes = {
        @Index(name = "idx_scheduled_task_enabled", columnList = "enabled"),
        @Index(name = "idx_scheduled_task_next_run", columnList = "next_run_time")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_scheduled_task_code", columnNames = "task_code")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ScheduledTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "task_code", nullable = false, length = 80)
    private String taskCode;

    @Column(name = "task_name", nullable = false, length = 100)
    private String taskName;

    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Asia/Shanghai";

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "last_scheduled_time")
    private LocalDateTime lastScheduledTime;

    @Column(name = "last_run_time")
    private LocalDateTime lastRunTime;

    @Column(name = "next_run_time")
    private LocalDateTime nextRunTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_run_status", length = 20)
    private ScheduledTaskLog.RunStatus lastRunStatus;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;
}
