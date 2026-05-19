package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_captcha")
public class Captcha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "captcha_key", unique = true, nullable = false, length = 100)
    private String captchaKey;

    @Column(name = "captcha_code", nullable = false, length = 4)
    private String captchaCode;

    @Column(name = "captcha_image", columnDefinition = "TEXT")
    private String captchaImage;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "expire_time", nullable = false)
    private LocalDateTime expireTime;

    @Column(name = "used")
    private Boolean used = false;

    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
    }
}