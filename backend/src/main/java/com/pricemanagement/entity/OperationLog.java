package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "operation_log", indexes = {
    @Index(name = "idx_operation_user", columnList = "user_id"),
    @Index(name = "idx_operation_type", columnList = "operation_type"),
    @Index(name = "idx_operation_time", columnList = "operation_time"),
    @Index(name = "idx_operation_risk_score", columnList = "risk_score"),
    @Index(name = "idx_operation_security_event", columnList = "security_event_id")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Transient
    private String operatorName;

    @Column(name = "operation_type", length = 50)
    private String operationType;

    @Column(name = "operation_module", length = 100)
    private String operationModule;

    @Column(name = "operation_desc", length = 500)
    private String operationDesc;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "request_url", length = 500)
    private String requestUrl;

    @Column(name = "request_params", columnDefinition = "TEXT")
    private String requestParams;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "response_data", columnDefinition = "TEXT")
    private String responseData;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "operation_time", nullable = false)
    private LocalDateTime operationTime;

    @Column(name = "execution_time")
    private Long executionTime; // 毫秒

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "risk_score", nullable = false)
    private Integer riskScore = 0;

    @Column(name = "security_event_id")
    private Long securityEventId;

    @Transient
    public String getStatus() {
        if (errorMessage != null && !errorMessage.isBlank()) {
            return "失败";
        }
        if (responseCode == null || responseCode.isBlank()) {
            return "成功";
        }
        try {
            return Integer.parseInt(responseCode) >= 400 ? "失败" : "成功";
        } catch (NumberFormatException e) {
            return responseCode.startsWith("4") || responseCode.startsWith("5") ? "失败" : "成功";
        }
    }

    @Transient
    public String getErrorMsg() {
        return errorMessage;
    }

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @Getter
    public enum OperationType {
        LOGIN("登录"),
        LOGOUT("登出"),
        CREATE("创建"),
        UPDATE("更新"),
        DELETE("删除"),
        VIEW("查看"),
        EXPORT("导出"),
        IMPORT("导入"),
        QUERY("查询"),
        OTHER("其他");

        private final String description;

        OperationType(String description) {
            this.description = description;
        }
    }
}
