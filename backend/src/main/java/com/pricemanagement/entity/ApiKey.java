package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_api_key", indexes = {
        @Index(name = "idx_api_key_status", columnList = "status"),
        @Index(name = "idx_api_key_environment", columnList = "environment"),
        @Index(name = "idx_api_key_expire_time", columnList = "expire_time")
})
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "app_id", nullable = false, unique = true, length = 64)
    private String appId;

    @Column(name = "app_secret_cipher", nullable = false, columnDefinition = "TEXT")
    private String appSecretCipher;

    @Column(name = "app_secret_key_version", nullable = false, length = 20)
    private String appSecretKeyVersion = "v1";

    @Column(name = "app_secret_fingerprint", nullable = false, length = 64)
    private String appSecretFingerprint;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(nullable = false, length = 20)
    private String environment = "TESTING";

    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    @Column(name = "ip_whitelist", columnDefinition = "TEXT")
    private String ipWhitelist;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private Integer rateLimitPerMinute = 60;

    @Column(name = "daily_limit", nullable = false)
    private Integer dailyLimit = 10000;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Column(name = "last_used_time")
    private LocalDateTime lastUsedTime;

    @Version
    @Column(name = "version")
    private Long version;
}
