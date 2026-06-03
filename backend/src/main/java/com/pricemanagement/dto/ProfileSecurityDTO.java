package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProfileSecurityDTO {
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private Integer loginCount;
    private LocalDateTime passwordUpdatedTime;
    private String loginType;
    private Boolean locked;
    private LocalDateTime lockedTime;
    private String status;
}

