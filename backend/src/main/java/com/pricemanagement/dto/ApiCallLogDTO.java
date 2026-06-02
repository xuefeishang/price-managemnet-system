package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApiCallLogDTO {
    private Long id;
    private Long apiKeyId;
    private String appId;
    private String endpoint;
    private String queryString;
    private String method;
    private String permissionCode;
    private Integer statusCode;
    private Integer responseTime;
    private String ipAddress;
    private LocalDateTime requestTime;
    private String requestBodyHash;
    private String nonce;
    private String authResult;
    private String errorMessage;
    private LocalDateTime createdTime;
}
