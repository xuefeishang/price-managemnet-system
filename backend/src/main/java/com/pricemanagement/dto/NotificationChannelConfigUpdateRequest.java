package com.pricemanagement.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class NotificationChannelConfigUpdateRequest {
    private Boolean enabled;
    private String appId;
    private Boolean clearAppId = false;
    private String endpointUrl;
    private Boolean clearEndpointUrl = false;
    private String secret;
    private Boolean clearSecret = false;
    private Integer timeoutMs;
    private String defaultPage;
    private Boolean clearDefaultPage = false;
    private List<TemplateMappingRequest> templates;

    @Data
    public static class TemplateMappingRequest {
        private String notificationType;
        private String templateId;
        private Boolean clearTemplateId = false;
        private String page;
        private Map<String, String> fields;
    }
}
