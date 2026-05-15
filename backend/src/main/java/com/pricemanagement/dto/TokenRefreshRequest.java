package com.pricemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token 刷新请求
 */
@Data
public class TokenRefreshRequest {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}