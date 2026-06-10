package com.pricemanagement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class NotificationMiniProgramSubscriptionUpdateRequest {

    @Valid
    @NotEmpty
    private List<SubscriptionResult> results;

    @Data
    public static class SubscriptionResult {
        @NotBlank
        private String notificationType;

        @NotBlank
        private String templateId;

        @NotBlank
        private String result;
    }
}
