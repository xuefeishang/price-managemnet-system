package com.pricemanagement.service;

import com.pricemanagement.config.properties.AlertProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 告警服务
 * 支持钉钉、企业微信告警
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertService {

    private final AlertProperties alertProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 发送告警
     */
    public void sendAlert(String title, String content, AlertLevel level) {
        if (!alertProperties.isEnabled()) {
            log.debug("Alert is disabled, skip sending: {}", title);
            return;
        }

        String message = buildMessage(title, content, level);

        // 发送钉钉告警
        if (alertProperties.getDingTalk().isEnabled()) {
            sendDingTalkAlert(title, message, level);
        }

        // 发送企业微信告警
        if (alertProperties.getWeChat().isEnabled()) {
            sendWeChatAlert(title, message, level);
        }
    }

    /**
     * 发送钉钉告警
     */
    private void sendDingTalkAlert(String title, String message, AlertLevel level) {
        try {
            String webhook = alertProperties.getDingTalk().getWebhook();
            String secret = alertProperties.getDingTalk().getSecret();

            if (webhook == null || webhook.isEmpty()) {
                log.warn("DingTalk webhook is not configured");
                return;
            }

            // 构建带签名的 URL
            String url = buildDingTalkUrl(webhook, secret);

            // 构建消息体
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");

            Map<String, String> markdown = new HashMap<>();
            markdown.put("title", title);
            markdown.put("text", message);
            body.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    url,
                    new org.springframework.http.HttpEntity<>(body, headers),
                    Map.class
            );

            if (response != null && Objects.equals(response.get("errcode"), 0)) {
                log.info("DingTalk alert sent successfully: {}", title);
            } else {
                log.warn("DingTalk alert send failed: {}", response);
            }
        } catch (Exception e) {
            log.error("Send DingTalk alert failed", e);
        }
    }

    /**
     * 构建钉钉带签名的 URL
     */
    private String buildDingTalkUrl(String webhook, String secret) {
        if (secret == null || secret.isEmpty()) {
            return webhook;
        }

        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
            return webhook + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            log.error("Calculate DingTalk sign failed", e);
            return webhook;
        }
    }

    /**
     * 发送企业微信告警
     */
    private void sendWeChatAlert(String title, String message, AlertLevel level) {
        try {
            String webhook = alertProperties.getWeChat().getWebhook();

            if (webhook == null || webhook.isEmpty()) {
                log.warn("WeChat webhook is not configured");
                return;
            }

            // 构建消息体
            Map<String, Object> body = new HashMap<>();
            body.put("msgtype", "markdown");

            Map<String, String> markdown = new HashMap<>();
            markdown.put("content", "### " + title + "\n\n" + message);
            body.put("markdown", markdown);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    webhook,
                    new org.springframework.http.HttpEntity<>(body, headers),
                    Map.class
            );

            if (response != null && Objects.equals(response.get("errcode"), 0)) {
                log.info("WeChat alert sent successfully: {}", title);
            } else {
                log.warn("WeChat alert send failed: {}", response);
            }
        } catch (Exception e) {
            log.error("Send WeChat alert failed", e);
        }
    }

    /**
     * 构建告警消息
     */
    private String buildMessage(String title, String content, AlertLevel level) {
        String levelEmoji = switch (level) {
            case CRITICAL -> "🔴";
            case WARNING -> "🟡";
            case INFO -> "🔵";
        };

        String suggestion = switch (level) {
            case CRITICAL -> "立即检查服务器状态，必要时回滚或切换备用服务";
            case WARNING -> "监控相关指标，准备应急预案";
            case INFO -> "持续观察，无需立即处理";
        };

        return String.format("""
                ## %s %s

                **时间：** %s
                **级别：** %s

                **详情：**
                %s

                **建议操作：**
                %s
                """,
                levelEmoji, title,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                level.name(),
                content,
                suggestion
        );
    }

    /**
     * 告警级别
     */
    public enum AlertLevel {
        CRITICAL,   // 严重
        WARNING,    // 警告
        INFO        // 信息
    }
}
