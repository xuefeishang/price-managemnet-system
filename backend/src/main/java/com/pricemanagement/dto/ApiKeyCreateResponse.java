package com.pricemanagement.dto;

import lombok.Data;

@Data
public class ApiKeyCreateResponse {
    private ApiKeyDTO apiKey;
    private String appSecret;
}
