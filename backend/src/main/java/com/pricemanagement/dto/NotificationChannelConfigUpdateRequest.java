package com.pricemanagement.dto;

import lombok.Data;

import java.util.ArrayList;
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
    private List<TemplateMappingRequest> templates = new ArrayList<>();

    @Data
    public static class TemplateMappingRequest {
        private String notificationType;
        private String templateId;
        private String page;
        private Map<String, String> fields;
    }
}
