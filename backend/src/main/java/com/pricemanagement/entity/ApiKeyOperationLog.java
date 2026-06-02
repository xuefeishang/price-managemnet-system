package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_api_key_operation_log", indexes = {
        @Index(name = "idx_api_key_operation_key", columnList = "api_key_id"),
        @Index(name = "idx_api_key_operation_time", columnList = "created_time")
})
public class ApiKeyOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(nullable = false, length = 50)
    private String operation;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "operator_ip", length = 50)
    private String operatorIp;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
