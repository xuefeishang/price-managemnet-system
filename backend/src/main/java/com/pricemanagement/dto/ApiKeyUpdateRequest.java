package com.pricemanagement.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class ApiKeyUpdateRequest {
    @NotBlank(message = "密钥名称不能为空")
    private String name;
    private String description;

    @NotBlank(message = "环境不能为空")
    private String environment;
    private LocalDateTime expireTime;
    private List<String> ipWhitelist = new ArrayList<>();

    @NotNull(message = "分钟限流不能为空")
    @Min(value = 0, message = "分钟限流必须为0或正整数")
    private Integer rateLimitPerMinute;

    @NotNull(message = "日限额不能为空")
    @Min(value = 0, message = "日限额必须为0或正整数")
    private Integer dailyLimit;
    private List<String> permissionCodes = new ArrayList<>();
}
