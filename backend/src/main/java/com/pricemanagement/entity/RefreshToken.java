package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 刷新令牌实体
 * 用于 JWT Token 刷新机制
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 刷新令牌
     */
    @Column(name = "token", nullable = false, unique = true, length = 500)
    private String token;

    /**
     * 用户ID
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 用户名
     */
    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * 过期时间
     */
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    /**
     * 是否已撤销
     */
    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

    /**
     * 创建时间
     */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(name = "last_used_time")
    private LocalDateTime lastUsedTime;

    @PrePersist
    protected void onCreate() {
        if (createdTime == null) {
            createdTime = LocalDateTime.now();
        }
    }

    /**
     * 检查令牌是否过期
     */
    public boolean isExpired() {
        return expiryDate.isBefore(LocalDateTime.now());
    }

    /**
     * 检查令牌是否有效
     */
    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
