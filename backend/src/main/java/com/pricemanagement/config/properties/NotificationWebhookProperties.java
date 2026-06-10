package com.pricemanagement.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "notification.webhook")
public class NotificationWebhookProperties {
    private boolean enabled = false;
    private String url;
    private String secret;
    private int timeoutMs = 5000;
}
