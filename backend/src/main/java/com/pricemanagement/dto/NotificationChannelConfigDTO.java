package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class NotificationChannelConfigDTO {
    private String channel;
    private String provider;
    private boolean enabled;
    private boolean configured;
    private boolean registered;
    private String healthStatus;
    private String source;
    private String appId;
    private String appIdMasked;
    private String endpointUrlMasked;
    private boolean secretConfigured;
    private String secretSource;
    private String secretFingerprintMasked;
    private Integer timeoutMs;
    private String defaultPage;
    private String tokenUrlMasked;
    private String sendUrlMasked;
    private LocalDateTime updatedTime;
    private List<TemplateMapping> templates = new ArrayList<>();
    private List<DiagnosticItem> diagnostics = new ArrayList<>();

    @Data
    public static class TemplateMapping {
        private String notificationType;
        private String templateName;
        private String templateIdMasked;
        private String page;
        private Map<String, String> fields;
        private boolean configured;
    }

    @Data
    public static class DiagnosticItem {
        private String key;
        private String label;
        private String status;
        private String severity;
        private String message;
    }
}
