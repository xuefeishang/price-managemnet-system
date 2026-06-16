package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "security_event", indexes = {
        @Index(name = "idx_security_event_type_time", columnList = "event_type, created_time"),
        @Index(name = "idx_security_event_ip_time", columnList = "source_ip, created_time"),
        @Index(name = "idx_security_event_severity_resolved", columnList = "severity, resolved"),
        @Index(name = "idx_security_event_created_time", columnList = "created_time"),
        @Index(name = "idx_security_event_resolved_time", columnList = "resolved, created_time")
})
public class SecurityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity = Severity.INFO;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_uri", length = 500)
    private String requestUri;

    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "action_taken", length = 200)
    private String actionTaken;

    @Column(name = "event_count", nullable = false)
    private Long eventCount = 1L;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved = false;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum EventType {
        ATTACK_SIGNATURE_BLOCKED,
        AUTH_LOGIN_FAILED,
        RATE_LIMITED,
        PERMISSION_DENIED,
        SUSPICIOUS_REQUEST,
        SERVER_ERROR
    }

    public enum Severity {
        INFO,
        WARN,
        ERROR,
        CRITICAL
    }
}
