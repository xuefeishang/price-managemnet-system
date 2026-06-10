package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_channel_config", indexes = {
        @Index(name = "idx_notification_channel_config_channel", columnList = "channel", unique = true),
        @Index(name = "idx_notification_channel_config_status", columnList = "enabled")
})
public class NotificationChannelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel", nullable = false, unique = true, length = 50)
    private String channel;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;

    @Column(name = "app_id", length = 100)
    private String appId;

    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    @Column(name = "secret_cipher", columnDefinition = "TEXT")
    private String secretCipher;

    @Column(name = "secret_key_version", length = 20)
    private String secretKeyVersion;

    @Column(name = "secret_fingerprint", length = 64)
    private String secretFingerprint;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "default_page", length = 200)
    private String defaultPage;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Version
    @Column(name = "version")
    private Long version;
}
