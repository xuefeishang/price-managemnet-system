package com.pricemanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExternalApiServiceStatusUpdateRequest {
    @NotNull(message = "服务状态不能为空")
    private Boolean enabled;
}
