package com.pricemanagement.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Data
@Component
@ConfigurationProperties(prefix = "notification.mini-program")
public class NotificationMiniProgramProperties {

    private boolean enabled = false;
    private String appId;
    private String appSecret;
    private int timeoutMs = 5000;
    private String tokenUrl = "https://api.weixin.qq.com/cgi-bin/token";
    private String sendUrl = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";
    private String pricePublishedTemplateId;
    private String systemNoticeTemplateId;
    private String defaultPage = "pages/notifications/index";
    private Map<String, Template> templates = new LinkedHashMap<>();

    /**
     * Legacy global fields kept for compatibility. Prefer templates.{TYPE}.fields.
     */
    private String titleField = "thing1";
    private String summaryField = "thing2";
    private String dateField = "time3";
    private String businessField = "thing4";

    public boolean hasCredentials() {
        return hasText(appId) && hasText(appSecret);
    }

    public boolean isConfigured() {
        return enabled && hasCredentials();
    }

    public boolean hasAnyTemplateConfigured() {
        return !configuredTemplates().isEmpty();
    }

    public Optional<Template> resolveTemplate(String notificationType) {
        if (!StringUtils.hasText(notificationType)) {
            return Optional.empty();
        }
        Template template = findTemplate(notificationType);
        if (template != null && StringUtils.hasText(template.getTemplateId())) {
            return Optional.of(template);
        }
        String legacyTemplateId = legacyTemplateId(notificationType);
        if (!StringUtils.hasText(legacyTemplateId)) {
            return Optional.empty();
        }
        Template legacy = new Template();
        legacy.setTemplateId(legacyTemplateId);
        legacy.setPage(defaultPage);
        legacy.getFields().put("title", titleField);
        legacy.getFields().put("summary", summaryField);
        legacy.getFields().put("date", dateField);
        legacy.getFields().put("business", businessField);
        return Optional.of(legacy);
    }

    public Map<String, Template> configuredTemplates() {
        Map<String, Template> configured = new LinkedHashMap<>();
        templates.forEach((type, template) -> {
            if (StringUtils.hasText(type) && template != null && StringUtils.hasText(template.getTemplateId())) {
                configured.put(normalizeKey(type), template);
            }
        });
        resolveTemplate("PRICE_PUBLISHED").ifPresent(template -> configured.putIfAbsent("PRICE_PUBLISHED", template));
        resolveTemplate("SYSTEM_NOTICE").ifPresent(template -> configured.putIfAbsent("SYSTEM_NOTICE", template));
        return configured;
    }

    private String legacyTemplateId(String notificationType) {
        if ("PRICE_PUBLISHED".equals(notificationType)) {
            return pricePublishedTemplateId;
        }
        if ("SYSTEM_NOTICE".equals(notificationType)) {
            return systemNoticeTemplateId;
        }
        return null;
    }

    private Template findTemplate(String notificationType) {
        Template template = templates.get(notificationType);
        if (template != null) {
            return template;
        }
        String normalizedType = normalizeKey(notificationType);
        return templates.entrySet().stream()
                .filter(entry -> normalizeKey(entry.getKey()).equals(normalizedType))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("-", "_").toUpperCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Data
    public static class Template {
        private String templateId;
        private String page;
        private Map<String, String> fields = new LinkedHashMap<>();
    }
}
