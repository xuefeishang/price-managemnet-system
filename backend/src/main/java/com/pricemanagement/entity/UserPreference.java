package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user_preference", indexes = {
        @Index(name = "uk_user_preference_user", columnList = "user_id", unique = true)
})
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "table_density", nullable = false, length = 20)
    private String tableDensity = "DEFAULT";

    @Column(name = "default_home_path", nullable = false, length = 200)
    private String defaultHomePath = "/home";

    @Column(name = "theme_mode", nullable = false, length = 20)
    private String themeMode = "SYSTEM";

    @Column(name = "page_size", nullable = false)
    private Integer pageSize = 20;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
