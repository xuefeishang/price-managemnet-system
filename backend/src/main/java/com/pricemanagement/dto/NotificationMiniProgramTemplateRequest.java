package com.pricemanagement.dto;

import lombok.Data;

import java.util.Map;

@Data
public class NotificationMiniProgramTemplateRequest {
    private String notificationType;
    private String templateId;
    private String page;
    private Map<String, String> fields;
}
