package com.pricemanagement.service.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.config.properties.NotificationWebhookProperties;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookNotificationProvider implements NotificationChannelProvider {

    private final NotificationWebhookProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String channel() {
        return "WEBHOOK";
    }

    @Override
    public DeliveryResult send(NotificationMessage message, NotificationDeliveryLog delivery) {
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getUrl())) {
            return DeliveryResult.skipped("PROVIDER_NOT_CONFIGURED", "Webhook Provider 未配置");
        }

        String idempotencyKey = "delivery-" + delivery.getId();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("idempotencyKey", idempotencyKey);
            payload.put("messageId", message.getId());
            payload.put("deliveryId", delivery.getId());
            payload.put("type", message.getType());
            payload.put("title", message.getTitle());
            payload.put("summary", message.getSummary() == null ? "" : message.getSummary());
            payload.put("content", message.getContent() == null ? "" : message.getContent());
            payload.put("businessType", message.getBusinessType() == null ? "" : message.getBusinessType());
            payload.put("businessId", message.getBusinessId() == null ? "" : message.getBusinessId());
            payload.put("priority", message.getPriority().name());
            payload.put("linkType", message.getLinkType() == null ? "" : message.getLinkType());
            payload.put("linkParams", message.getLinkParams() == null ? "" : message.getLinkParams());
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getUrl()))
                    .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1000)))
                    .header("Content-Type", "application/json")
                    .header("X-Notification-Idempotency-Key", idempotencyKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            if (StringUtils.hasText(properties.getSecret())) {
                requestBuilder.header("X-Notification-Signature", sign(body, properties.getSecret()));
            }

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1000)))
                    .build()
                    .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return DeliveryResult.success(idempotencyKey);
            }
            return DeliveryResult.failed("WEBHOOK_HTTP_" + response.statusCode(),
                    "Webhook 返回非成功状态: " + response.statusCode());
        } catch (java.net.http.HttpTimeoutException ex) {
            return DeliveryResult.failed("WEBHOOK_TIMEOUT", "Webhook 调用超时");
        } catch (Exception ex) {
            log.warn("Webhook notification failed: deliveryId={}", delivery.getId(), ex);
            return DeliveryResult.failed("WEBHOOK_EXCEPTION", ex.getMessage());
        }
    }

    private String sign(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
