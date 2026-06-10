package com.pricemanagement.dto;

import com.pricemanagement.entity.NotificationMiniProgramResolution;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationMiniProgramResolveRequest {
    @NotNull
    private NotificationMiniProgramResolution.ResolveStatus status;

    @Size(max = 500)
    private String remark;

    private LocalDateTime remindAfter;
}
