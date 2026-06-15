package com.pricemanagement.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

@Data
public class AdminPasswordResetRequest {

    private String newPassword;

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("不支持字段：" + fieldName);
    }
}
