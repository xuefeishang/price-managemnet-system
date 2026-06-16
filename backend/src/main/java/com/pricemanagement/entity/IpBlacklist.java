package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ip_blacklist", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ip_blacklist_active", columnNames = {"ip_address", "is_active"})
}, indexes = {
        @Index(name = "idx_ip_blacklist_active_expires", columnList = "is_active, expires_at"),
        @Index(name = "idx_ip_blacklist_ip", columnList = "ip_address"),
        @Index(name = "idx_ip_blacklist_banned_at", columnList = "banned_at")
})
public class IpBlacklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "banned_by", nullable = false, length = 30)
    private BannedBy bannedBy;

    @Column(name = "banned_at", nullable = false)
    private LocalDateTime bannedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "banned_by_user_id")
    private Long bannedByUserId;

    @Column(name = "unban_at")
    private LocalDateTime unbanAt;

    @Column(name = "unban_by_user_id")
    private Long unbanByUserId;

    @Column(name = "unban_reason", length = 200)
    private String unbanReason;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @PrePersist
    void prePersist() {
        if (bannedAt == null) {
            bannedAt = LocalDateTime.now();
        }
    }

    public enum BannedBy {
        AUTO_FAIL2BAN,
        AUTO_NGINX,
        MANUAL_ADMIN
    }
}
