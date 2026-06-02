package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_api_call_log", indexes = {
        @Index(name = "idx_api_call_api_key_time", columnList = "api_key_id, request_time"),
        @Index(name = "idx_api_call_app_time", columnList = "app_id, request_time"),
        @Index(name = "idx_api_call_status", columnList = "status_code"),
        @Index(name = "idx_api_call_auth_result", columnList = "auth_result"),
        @Index(name = "idx_api_call_created_time", columnList = "created_time")
})
public class ApiCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    @Column(name = "app_id", length = 64)
    private String appId;

    @Column(nullable = false, length = 200)
    private String endpoint;

    @Column(name = "query_string", length = 1000)
    private String queryString;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "permission_code", length = 100)
    private String permissionCode;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_time")
    private Integer responseTime;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "request_time", nullable = false)
    private LocalDateTime requestTime;

    @Column(name = "request_body_hash", length = 64)
    private String requestBodyHash;

    @Column(length = 64)
    private String nonce;

    @Column(name = "auth_result", nullable = false, length = 30)
    private String authResult;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
