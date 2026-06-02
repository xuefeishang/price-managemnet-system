package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApiKeyDTO {
    private Long id;
    private String name;
    private String appId;
    private String appSecretFingerprint;
    private String appSecretKeyVersion;
    private String description;
    private String status;
    private String environment;
    private LocalDateTime expireTime;
    private List<String> ipWhitelist = new ArrayList<>();
    private Integer rateLimitPerMinute;
    private Integer dailyLimit;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime lastUsedTime;
    private List<String> permissionCodes = new ArrayList<>();
}
