package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.config.properties.NotificationMiniProgramProperties;
import com.pricemanagement.dto.NotificationChannelConfigDTO;
import com.pricemanagement.dto.NotificationChannelConfigUpdateRequest;
import com.pricemanagement.dto.NotificationProviderTestResultDTO;
import com.pricemanagement.entity.NotificationChannelConfig;
import com.pricemanagement.repository.NotificationChannelConfigRepository;
import com.pricemanagement.repository.SysDictRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationMiniProgramRuntimeConfigService {

    public static final String CHANNEL = NotificationService.CHANNEL_MINI_PROGRAM;
    public static final String MINI_PROGRAM_PAGE_DICT_CATEGORY = "notification_mini_program_page";

    private final NotificationMiniProgramProperties properties;
    private final NotificationChannelConfigRepository configRepository;
    private final ApiKeySecretService secretService;
    private final ApiKeyProperties apiKeyProperties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final SysDictRepository sysDictRepository;

    @Transactional(readOnly = true)
    public RuntimeConfig activeConfig() {
        Optional<NotificationChannelConfig> dbConfig = configRepository.findByChannel(CHANNEL);
        if (dbConfig.isPresent()) {
            return fromDb(dbConfig.get());
        }
        return fromProperties();
    }

    @Transactional(readOnly = true)
    public NotificationChannelConfigDTO getConfigView() {
        return toDto(activeConfig());
    }

    @Transactional
    public NotificationChannelConfigDTO saveConfig(NotificationChannelConfigUpdateRequest request, Long operatorId) {
        NotificationChannelConfig config = configRepository.findByChannel(CHANNEL).orElseGet(() -> {
            NotificationChannelConfig created = new NotificationChannelConfig();
            created.setChannel(CHANNEL);
            created.setCreatedBy(operatorId);
            return created;
        });

        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (Boolean.TRUE.equals(request.getClearAppId())) {
            config.setAppId(null);
        } else if (StringUtils.hasText(request.getAppId())) {
            config.setAppId(normalize(request.getAppId()));
        }
        if (Boolean.TRUE.equals(request.getClearEndpointUrl())) {
            config.setEndpointUrl(null);
        } else if (StringUtils.hasText(request.getEndpointUrl())) {
            config.setEndpointUrl(normalize(request.getEndpointUrl()));
        }
        if (request.getTimeoutMs() != null) {
            config.setTimeoutMs(clampTimeout(request.getTimeoutMs()));
        }
        if (Boolean.TRUE.equals(request.getClearDefaultPage())) {
            config.setDefaultPage(null);
        } else if (StringUtils.hasText(request.getDefaultPage())) {
            String defaultPage = normalize(request.getDefaultPage());
            validateMiniProgramPage(defaultPage, "默认跳转页");
            config.setDefaultPage(defaultPage);
        }
        if (Boolean.TRUE.equals(request.getClearSecret())) {
            config.setSecretCipher(null);
            config.setSecretKeyVersion(null);
            config.setSecretFingerprint(null);
        } else if (StringUtils.hasText(request.getSecret())) {
            apiKeyProperties.requireValidEncryptionKey("保存通知渠道密钥前必须配置 API_KEY_ENCRYPTION_KEY");
            config.setSecretCipher(secretService.encrypt(request.getSecret().trim()));
            config.setSecretKeyVersion(apiKeyProperties.getEncryptionKeyVersion());
            config.setSecretFingerprint(secretService.fingerprint(request.getSecret().trim()));
        }
        config.setConfigJson(toJson(toStoredConfig(request, storedConfigBaseline(config))));
        config.setUpdatedBy(operatorId);
        configRepository.save(config);
        notifyConfigChanged();
        return toDto(fromDb(config));
    }

    @Transactional(readOnly = true)
    public NotificationProviderTestResultDTO testConfig() {
        RuntimeConfig config = activeConfig();
        NotificationProviderTestResultDTO result = new NotificationProviderTestResultDTO();
        result.setChannel(CHANNEL);
        result.setDiagnostics(diagnostics(config));
        result.setTotalCount(result.getDiagnostics().size());
        result.setPassedCount((int) result.getDiagnostics().stream()
                .filter(item -> "PASS".equals(item.getStatus()))
                .count());
        result.setPassed(result.getPassedCount() == result.getTotalCount());
        return result;
    }

    public Optional<NotificationMiniProgramProperties.Template> resolveTemplate(String notificationType) {
        return activeConfig().resolveTemplate(notificationType);
    }

    public List<TemplateConfig> configuredTemplates() {
        return activeConfig().configuredTemplates().entrySet().stream()
                .map(entry -> new TemplateConfig(entry.getKey(), entry.getValue().getTemplateId()))
                .toList();
    }

    private RuntimeConfig fromProperties() {
        RuntimeConfig config = new RuntimeConfig();
        config.setEnabled(properties.isEnabled());
        config.setAppId(properties.getAppId());
        config.setAppSecret(properties.getAppSecret());
        config.setTimeoutMs(properties.getTimeoutMs());
        config.setTokenUrl(properties.getTokenUrl());
        config.setSendUrl(properties.getSendUrl());
        config.setDefaultPage(properties.getDefaultPage());
        config.setTemplates(properties.configuredTemplates());
        config.setSource("ENV");
        config.setSecretConfigured(StringUtils.hasText(properties.getAppSecret()));
        config.setSecretSource(StringUtils.hasText(properties.getAppSecret()) ? "ENV" : "NONE");
        config.setSecretFingerprint(fingerprint(properties.getAppSecret()));
        return config;
    }

    private RuntimeConfig fromDb(NotificationChannelConfig dbConfig) {
        StoredConfig stored = readStoredConfig(dbConfig.getConfigJson());
        RuntimeConfig config = new RuntimeConfig();
        config.setEnabled(Boolean.TRUE.equals(dbConfig.getEnabled()));
        config.setAppId(firstText(dbConfig.getAppId(), properties.getAppId()));
        config.setTimeoutMs(dbConfig.getTimeoutMs() == null ? properties.getTimeoutMs() : dbConfig.getTimeoutMs());
        config.setTokenUrl(firstText(stored.getTokenUrl(), properties.getTokenUrl()));
        config.setSendUrl(firstText(stored.getSendUrl(), properties.getSendUrl(), dbConfig.getEndpointUrl()));
        config.setDefaultPage(firstText(dbConfig.getDefaultPage(), properties.getDefaultPage()));
        config.setTemplates(stored.getTemplates().isEmpty() ? properties.configuredTemplates() : stored.getTemplates());
        config.setSource("DATABASE");
        config.setUpdatedTime(dbConfig.getUpdatedTime());

        if (StringUtils.hasText(dbConfig.getSecretCipher())) {
            apiKeyProperties.requireValidEncryptionKey("读取通知渠道密钥前必须配置 API_KEY_ENCRYPTION_KEY");
            config.setAppSecret(secretService.decrypt(dbConfig.getSecretCipher()));
            config.setSecretConfigured(true);
            config.setSecretSource("DATABASE");
            config.setSecretFingerprint(firstText(dbConfig.getSecretFingerprint(), fingerprint(config.getAppSecret())));
        } else {
            config.setAppSecret(properties.getAppSecret());
            config.setSecretConfigured(StringUtils.hasText(properties.getAppSecret()));
            config.setSecretSource(StringUtils.hasText(properties.getAppSecret()) ? "ENV" : "NONE");
            config.setSecretFingerprint(fingerprint(properties.getAppSecret()));
        }
        return config;
    }

    private NotificationChannelConfigDTO toDto(RuntimeConfig config) {
        NotificationChannelConfigDTO dto = new NotificationChannelConfigDTO();
        dto.setChannel(CHANNEL);
        dto.setProvider(CHANNEL);
        dto.setEnabled(config.isEnabled());
        dto.setConfigured(config.isConfigured());
        dto.setRegistered(true);
        dto.setHealthStatus(config.isOperationallyReady()
                ? "OK"
                : (config.hasAnyConfiguration() ? "DEGRADED" : "NOT_CONFIGURED"));
        dto.setSource(config.getSource());
        dto.setAppId(config.getAppId());
        dto.setAppIdMasked(mask(config.getAppId(), 6));
        dto.setEndpointUrlMasked(maskUrl(config.getSendUrl()));
        dto.setSecretConfigured(config.isSecretConfigured());
        dto.setSecretSource(config.getSecretSource());
        dto.setSecretFingerprintMasked(maskFingerprint(config.getSecretFingerprint()));
        dto.setTimeoutMs(config.getTimeoutMs());
        dto.setDefaultPage(config.getDefaultPage());
        dto.setTokenUrlMasked(maskUrl(config.getTokenUrl()));
        dto.setSendUrlMasked(maskUrl(config.getSendUrl()));
        dto.setUpdatedTime(config.getUpdatedTime());
        dto.setTemplates(config.configuredTemplates().entrySet().stream()
                .map(entry -> templateMapping(entry.getKey(), entry.getValue()))
                .toList());
        dto.setDiagnostics(diagnostics(config));
        return dto;
    }

    private NotificationChannelConfigDTO.TemplateMapping templateMapping(
            String notificationType,
            NotificationMiniProgramProperties.Template template) {
        NotificationChannelConfigDTO.TemplateMapping mapping = new NotificationChannelConfigDTO.TemplateMapping();
        mapping.setNotificationType(notificationType);
        mapping.setTemplateName(notificationType);
        mapping.setTemplateIdMasked(mask(template.getTemplateId(), 8));
        mapping.setPage(template.getPage());
        mapping.setFields(template.getFields());
        mapping.setConfigured(StringUtils.hasText(template.getTemplateId())
                && template.getFields() != null
                && !template.getFields().isEmpty());
        return mapping;
    }

    private List<NotificationChannelConfigDTO.DiagnosticItem> diagnostics(RuntimeConfig config) {
        return List.of(
                diagnostic("provider_enabled", "Provider 已启用", config.isEnabled(), "ERROR",
                        "小程序订阅 Provider 未启用"),
                diagnostic("app_id", "AppID 已配置", StringUtils.hasText(config.getAppId()), "ERROR",
                        "缺少微信小程序 AppID"),
                diagnostic("app_secret", "AppSecret 已托管", config.isSecretConfigured(), "ERROR",
                        "缺少 AppSecret，无法获取 access_token"),
                diagnostic("template", "模板 ID 已配置", config.hasAnyTemplateConfigured(), "ERROR",
                        "至少需要配置一个订阅消息模板"),
                diagnostic("fields", "字段映射完整", config.configuredTemplates().values().stream()
                                .allMatch(template -> template.getFields() != null && !template.getFields().isEmpty()),
                        "WARNING",
                        "模板字段映射缺失会导致消息无法组装"),
                diagnostic("default_page", "跳转页已配置", StringUtils.hasText(config.getDefaultPage()), "WARNING",
                        "缺少默认跳转页时会回退到通知列表"),
                diagnostic("timeout", "接口超时合理", config.getTimeoutMs() >= 1000 && config.getTimeoutMs() <= 30000,
                        "WARNING",
                        "建议超时控制在 1000-30000ms")
        );
    }

    private NotificationChannelConfigDTO.DiagnosticItem diagnostic(
            String key,
            String label,
            boolean passed,
            String severity,
            String message) {
        NotificationChannelConfigDTO.DiagnosticItem item = new NotificationChannelConfigDTO.DiagnosticItem();
        item.setKey(key);
        item.setLabel(label);
        item.setStatus(passed ? "PASS" : "FAIL");
        item.setSeverity(passed ? "INFO" : severity);
        item.setMessage(passed ? "检查通过" : message);
        return item;
    }

    private StoredConfig toStoredConfig(NotificationChannelConfigUpdateRequest request, StoredConfig current) {
        StoredConfig stored = new StoredConfig();
        stored.setTokenUrl(firstText(current.getTokenUrl(), properties.getTokenUrl()));
        if (Boolean.TRUE.equals(request.getClearEndpointUrl())) {
            stored.setSendUrl(null);
        } else {
            stored.setSendUrl(firstText(request.getEndpointUrl(), current.getSendUrl(), properties.getSendUrl()));
        }
        Map<String, NotificationMiniProgramProperties.Template> templates = new LinkedHashMap<>(current.getTemplates());
        if (request.getTemplates() != null) {
            Set<String> seenTypes = new HashSet<>();
            for (NotificationChannelConfigUpdateRequest.TemplateMappingRequest item : request.getTemplates()) {
                if (!StringUtils.hasText(item.getNotificationType())) {
                    continue;
                }
                String key = normalizeKey(item.getNotificationType());
                validateTemplateMapping(key, item, seenTypes);
                NotificationMiniProgramProperties.Template existing = templates.get(key);
                NotificationMiniProgramProperties.Template template = new NotificationMiniProgramProperties.Template();
                template.setTemplateId(firstText(item.getTemplateId(), existing == null ? null : existing.getTemplateId()));
                template.setPage(item.getPage() == null
                        ? (existing == null ? null : existing.getPage())
                        : (StringUtils.hasText(item.getPage()) ? normalize(item.getPage()) : null));
                template.setFields(item.getFields() == null
                        ? (existing == null || existing.getFields() == null ? new LinkedHashMap<>() : existing.getFields())
                        : item.getFields());
                templates.put(key, template);
            }
        }
        stored.setTemplates(templates.isEmpty() ? properties.configuredTemplates() : templates);
        return stored;
    }

    private void validateTemplateMapping(
            String notificationType,
            NotificationChannelConfigUpdateRequest.TemplateMappingRequest item,
            Set<String> seenTypes) {
        if (!seenTypes.add(notificationType)) {
            throw new IllegalArgumentException("小程序模板通知类型重复: " + notificationType);
        }
        boolean activeType = sysDictRepository.findByCategoryAndDictKey("notification_type", notificationType)
                .map(dict -> dict.getStatus() == com.pricemanagement.constants.CommonStatus.ACTIVE)
                .orElse(false);
        if (!activeType) {
            throw new IllegalArgumentException("小程序模板通知类型不存在或未启用: " + notificationType);
        }
        if (StringUtils.hasText(item.getTemplateId())
                && (item.getFields() == null || item.getFields().isEmpty())) {
            throw new IllegalArgumentException("小程序模板字段映射不能为空: " + notificationType);
        }
        if (StringUtils.hasText(item.getPage())) {
            validateMiniProgramPage(normalize(item.getPage()), "模板跳转页");
        }
        if (item.getFields() != null && item.getFields().entrySet().stream()
                .anyMatch(entry -> !StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue()))) {
            throw new IllegalArgumentException("小程序模板字段映射不能包含空键或空值: " + notificationType);
        }
    }

    private StoredConfig readStoredConfig(String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return new StoredConfig();
        }
        try {
            StoredConfig stored = objectMapper.readValue(configJson, StoredConfig.class);
            if (stored.getTemplates() == null) {
                stored.setTemplates(new LinkedHashMap<>());
            }
            return stored;
        } catch (Exception ex) {
            return new StoredConfig();
        }
    }

    private StoredConfig storedConfigBaseline(NotificationChannelConfig config) {
        StoredConfig stored = readStoredConfig(config.getConfigJson());
        if (!StringUtils.hasText(stored.getTokenUrl())) {
            stored.setTokenUrl(properties.getTokenUrl());
        }
        if (!StringUtils.hasText(stored.getSendUrl())) {
            stored.setSendUrl(firstText(config.getEndpointUrl(), properties.getSendUrl()));
        }
        if (stored.getTemplates().isEmpty()) {
            stored.setTemplates(properties.configuredTemplates());
        }
        return stored;
    }

    private String toJson(StoredConfig stored) {
        try {
            return objectMapper.writeValueAsString(stored);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private int clampTimeout(int timeoutMs) {
        return Math.min(Math.max(timeoutMs, 1000), 30000);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeKey(String key) {
        return key == null ? "" : key.replace("-", "_").toUpperCase();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String mask(String value, int tailLength) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= tailLength) {
            return "****" + trimmed;
        }
        return trimmed.substring(0, Math.min(4, trimmed.length())) + "****"
                + trimmed.substring(trimmed.length() - tailLength);
    }

    private String maskUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPath() == null ? "" : uri.getPath());
        } catch (Exception ex) {
            return mask(value, 8);
        }
    }

    private String maskFingerprint(String fingerprint) {
        if (!StringUtils.hasText(fingerprint)) {
            return "-";
        }
        String trimmed = fingerprint.trim();
        return "SHA-256 …" + trimmed.substring(Math.max(0, trimmed.length() - 8));
    }

    private String fingerprint(String secret) {
        return StringUtils.hasText(secret) ? secretService.fingerprint(secret.trim()) : null;
    }

    private void validateMiniProgramPage(String page, String fieldName) {
        boolean activePage = sysDictRepository.findByCategoryAndDictKey(MINI_PROGRAM_PAGE_DICT_CATEGORY, page)
                .map(dict -> dict.getStatus() == com.pricemanagement.constants.CommonStatus.ACTIVE)
                .orElse(false);
        if (!activePage) {
            throw new IllegalArgumentException(fieldName + "不存在或未启用: " + page);
        }
    }

    private void notifyConfigChanged() {
        eventPublisher.publishEvent(new MiniProgramConfigChangedEvent());
    }

    public static class MiniProgramConfigChangedEvent {
    }

    public record TemplateConfig(String notificationType, String templateId) {
    }

    @Data
    public static class RuntimeConfig {
        private boolean enabled;
        private String appId;
        private String appSecret;
        private int timeoutMs = 5000;
        private String tokenUrl;
        private String sendUrl;
        private String defaultPage;
        private Map<String, NotificationMiniProgramProperties.Template> templates = new LinkedHashMap<>();
        private String source;
        private boolean secretConfigured;
        private String secretSource;
        private String secretFingerprint;
        private LocalDateTime updatedTime;

        public String tokenCacheKey() {
            return String.join("|",
                    fingerprintPart(appId),
                    fingerprintPart(appSecret),
                    fingerprintPart(tokenUrl));
        }

        private String fingerprintPart(String value) {
            if (!StringUtils.hasText(value)) {
                return "-";
            }
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(hash);
            } catch (Exception ex) {
                return Integer.toHexString(value.hashCode());
            }
        }

        public boolean hasCredentials() {
            return StringUtils.hasText(appId) && StringUtils.hasText(appSecret);
        }

        public boolean isConfigured() {
            return enabled && hasCredentials();
        }

        public boolean isOperationallyReady() {
            return isConfigured() && hasAnyDeliverableTemplate();
        }

        public boolean hasAnyConfiguration() {
            return enabled
                    || StringUtils.hasText(appId)
                    || secretConfigured
                    || hasAnyTemplateConfigured();
        }

        public boolean hasAnyTemplateConfigured() {
            return !configuredTemplates().isEmpty();
        }

        public boolean hasAnyDeliverableTemplate() {
            return configuredTemplates().values().stream()
                    .anyMatch(template -> template.getFields() != null && !template.getFields().isEmpty());
        }

        public Optional<NotificationMiniProgramProperties.Template> resolveTemplate(String notificationType) {
            if (!StringUtils.hasText(notificationType)) {
                return Optional.empty();
            }
            return configuredTemplates().entrySet().stream()
                    .filter(entry -> normalizeConfigKey(entry.getKey()).equals(normalizeConfigKey(notificationType)))
                    .map(Map.Entry::getValue)
                    .filter(template -> StringUtils.hasText(template.getTemplateId()))
                    .findFirst();
        }

        public Map<String, NotificationMiniProgramProperties.Template> configuredTemplates() {
            Map<String, NotificationMiniProgramProperties.Template> configured = new LinkedHashMap<>();
            templates.forEach((type, template) -> {
                if (StringUtils.hasText(type) && template != null && StringUtils.hasText(template.getTemplateId())) {
                    configured.put(normalizeConfigKey(type), template);
                }
            });
            return configured;
        }

        private static String normalizeConfigKey(String key) {
            return key == null ? "" : key.replace("-", "_").toUpperCase();
        }
    }

    @Data
    public static class StoredConfig {
        private String tokenUrl;
        private String sendUrl;
        private Map<String, NotificationMiniProgramProperties.Template> templates = new LinkedHashMap<>();
    }
}
