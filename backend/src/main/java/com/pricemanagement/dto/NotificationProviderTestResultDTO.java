package com.pricemanagement.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NotificationProviderTestResultDTO {
    private String channel;
    private boolean passed;
    private int passedCount;
    private int totalCount;
    private List<NotificationChannelConfigDTO.DiagnosticItem> diagnostics = new ArrayList<>();
}
