package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProfileLoginHistoryDTO {
    private Long id;
    private LocalDateTime loginTime;
    private String ipAddress;
    private String userAgent;
    private String result;
    private String failureReason;
}

