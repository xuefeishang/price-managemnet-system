package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_api_key_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_api_key_permission", columnNames = {"api_key_id", "permission_code"})
}, indexes = {
        @Index(name = "idx_api_key_permission_key", columnList = "api_key_id")
})
public class ApiKeyPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key_id", nullable = false)
    private Long apiKeyId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
