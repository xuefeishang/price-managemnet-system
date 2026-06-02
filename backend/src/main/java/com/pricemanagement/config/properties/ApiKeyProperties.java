package com.pricemanagement.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Base64;

@Data
@Component
@ConfigurationProperties(prefix = "api-key")
public class ApiKeyProperties {

    private boolean enabled = false;
    private String encryptionKey;
    private String encryptionKeyVersion = "v1";
    private long timestampWindowSeconds = 300;
    private long nonceTtlSeconds = 600;
    private long cacheTtlSeconds = 300;
    private Log log = new Log();

    private final Environment environment;

    public ApiKeyProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        requireValidEncryptionKey("启用外部API授权时必须配置 API_KEY_ENCRYPTION_KEY");
    }

    public void requireValidEncryptionKey(String missingKeyMessage) {
        if (!StringUtils.hasText(encryptionKeyVersion)) {
            throw new IllegalStateException("api-key.encryption-key-version不能为空");
        }
        if (!StringUtils.hasText(encryptionKey)) {
            throw new IllegalStateException(missingKeyMessage);
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptionKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("API_KEY_ENCRYPTION_KEY必须是Base64编码", ex);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("API_KEY_ENCRYPTION_KEY解码后必须为32字节");
        }
        if (isProductionProfile() && isKnownDevKey(decoded)) {
            throw new IllegalStateException("生产环境禁止使用示例API Key加密主密钥");
        }
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }

    private boolean isKnownDevKey(byte[] decoded) {
        return Arrays.equals(decoded, "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Data
    public static class Log {
        private int retentionDays = 180;
        private double authFailureSampleRate = 1.0;
        private int maxErrorMessageLength = 500;
        private int maxQueryLength = 1000;
    }
}
