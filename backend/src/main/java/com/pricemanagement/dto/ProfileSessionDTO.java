package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProfileSessionDTO {
    private Long id;
    private Boolean current;
    private String deviceName;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdTime;
    private LocalDateTime lastUsedTime;
    private LocalDateTime expiryDate;
    private Boolean revoked;
}

