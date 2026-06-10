package com.pricemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NotificationMiniProgramTestDeliveryRequest {
    @NotNull
    private Long userId;

    @NotBlank
    private String notificationType;
}
