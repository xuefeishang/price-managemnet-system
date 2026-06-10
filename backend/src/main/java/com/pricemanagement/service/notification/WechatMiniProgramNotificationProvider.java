package com.pricemanagement.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.config.properties.NotificationMiniProgramProperties;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.service.NotificationMiniProgramRuntimeConfigService;
import com.pricemanagement.service.NotificationMiniProgramSubscriptionService;
import com.pricemanagement.service.NotificationService;
import com.pricemanagement.dto.NotificationProviderTestResultDTO;
import com.pricemanagement.dto.NotificationChannelConfigDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WechatMiniProgramNotificationProvider implements NotificationChannelProvider {

    private static final String CHANNEL = "MINI_PROGRAM";

    private final NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    private final NotificationMiniProgramSubscriptionService subscriptionService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private volatile TokenCache tokenCache;

    @Override
    public String channel() {
        return CHANNEL;
    }

    public NotificationProviderTestResultDTO testAccessToken() {
        NotificationProviderTestResultDTO result = new NotificationProviderTestResultDTO();
        result.setChannel(CHANNEL);
        NotificationChannelConfigDTO.DiagnosticItem item = new NotificationChannelConfigDTO.DiagnosticItem();
        item.setKey("remote_access_token");
        item.setLabel("微信 access_token 远程校验");
        try {
            NotificationMiniProgramRuntimeConfigService.RuntimeConfig config = runtimeConfigService.activeConfig();
            if (!config.hasCredentials()) {
                throw new IllegalStateException("缺少 AppID 或 AppSecret");
            }
            accessToken(config, true);
            item.setStatus("PASS");
            item.setSeverity("INFO");
            item.setMessage("微信 access_token 获取成功，明文未返回");
            result.setPassed(true);
            result.setPassedCount(1);
        } catch (Exception ex) {
            item.setStatus("FAIL");
            item.setSeverity("ERROR");
            item.setMessage("微信 access_token 获取失败，请检查 AppID、AppSecret 和网络配置");
            result.setPassed(false);
            result.setPassedCount(0);
        }
        result.setTotalCount(1);
        result.setDiagnostics(java.util.List.of(item));
        return result;
    }

    @Override
    public DeliveryResult send(NotificationMessage message, NotificationDeliveryLog delivery) {
        NotificationMiniProgramRuntimeConfigService.RuntimeConfig runtimeConfig = runtimeConfigService.activeConfig();
        if (!runtimeConfig.isConfigured()) {
            return DeliveryResult.skipped("PROVIDER_NOT_CONFIGURED", "微信小程序订阅消息Provider未配置");
        }

        Optional<NotificationMiniProgramProperties.Template> templateOptional = runtimeConfig.resolveTemplate(message.getType());
        if (templateOptional.isEmpty()) {
            return DeliveryResult.skipped("TEMPLATE_NOT_CONFIGURED", "当前通知类型未配置小程序订阅模板");
        }
        NotificationMiniProgramProperties.Template template = templateOptional.get();
        String templateId = template.getTemplateId();
        if (template.getFields() == null || template.getFields().isEmpty()) {
            return DeliveryResult.skipped("TEMPLATE_FIELDS_NOT_CONFIGURED", "当前通知类型未配置小程序订阅模板字段映射");
        }

        User user = userRepository.findById(delivery.getUserId()).orElse(null);
        if (user == null || !StringUtils.hasText(user.getWechatOpenid())) {
            return DeliveryResult.skipped("USER_NOT_BOUND", "用户未绑定微信小程序openid");
        }
        if (!subscriptionService.isAuthorized(user.getId(), message.getType(), templateId)) {
            return DeliveryResult.skipped("SUBSCRIBE_NOT_AUTHORIZED", "用户未授权该订阅消息模板或授权次数已用完");
        }

        try {
            String accessToken = accessToken(runtimeConfig);
            Map<String, Object> payload = buildPayload(user.getWechatOpenid(), template, message);
            String body = objectMapper.writeValueAsString(payload);
            String url = runtimeConfig.getSendUrl()
                    + "?access_token="
                    + URLEncoder.encode(accessToken, StandardCharsets.UTF_8);
            HttpResponse<String> response = httpClient()
                    .send(HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .timeout(timeout())
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return DeliveryResult.failed("WECHAT_HTTP_" + response.statusCode(),
                        "微信订阅消息接口返回非成功状态: " + response.statusCode());
            }
            JsonNode result = objectMapper.readTree(response.body());
            int errcode = result.path("errcode").asInt(-1);
            if (errcode == 0) {
                subscriptionService.consume(user.getId(), message.getType(), templateId);
                return DeliveryResult.success("delivery-" + delivery.getId());
            }
            String errmsg = result.path("errmsg").asText("微信订阅消息接口返回失败");
            if (errcode == 43101) {
                return DeliveryResult.skipped("WECHAT_USER_REFUSED", errmsg);
            }
            return DeliveryResult.failed("WECHAT_ERR_" + errcode, errmsg);
        } catch (java.net.http.HttpTimeoutException ex) {
            return DeliveryResult.failed("WECHAT_TIMEOUT", "微信订阅消息接口调用超时");
        } catch (Exception ex) {
            log.warn("Wechat mini program notification failed: deliveryId={}, exception={}",
                    delivery.getId(), ex.getClass().getSimpleName());
            return DeliveryResult.failed("WECHAT_EXCEPTION", "微信订阅消息接口调用异常");
        }
    }

    private String accessToken(NotificationMiniProgramRuntimeConfigService.RuntimeConfig runtimeConfig) throws Exception {
        return accessToken(runtimeConfig, false);
    }

    private String accessToken(
            NotificationMiniProgramRuntimeConfigService.RuntimeConfig runtimeConfig,
            boolean forceRefresh) throws Exception {
        String cacheKey = runtimeConfig.tokenCacheKey();
        TokenCache current = tokenCache;
        if (!forceRefresh
                && current != null
                && cacheKey.equals(current.cacheKey())
                && current.expiresAt().isAfter(LocalDateTime.now().plusSeconds(60))) {
            return current.token();
        }
        synchronized (this) {
            current = tokenCache;
            if (!forceRefresh
                    && current != null
                    && cacheKey.equals(current.cacheKey())
                    && current.expiresAt().isAfter(LocalDateTime.now().plusSeconds(60))) {
                return current.token();
            }
            String url = runtimeConfig.getTokenUrl()
                    + "?grant_type=client_credential&appid="
                    + URLEncoder.encode(runtimeConfig.getAppId(), StandardCharsets.UTF_8)
                    + "&secret="
                    + URLEncoder.encode(runtimeConfig.getAppSecret(), StandardCharsets.UTF_8);
            HttpResponse<String> response = httpClient()
                    .send(HttpRequest.newBuilder()
                                    .uri(URI.create(url))
                                    .timeout(timeout())
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("微信access_token接口返回非成功状态: " + response.statusCode());
            }
            JsonNode result = objectMapper.readTree(response.body());
            String token = result.path("access_token").asText("");
            if (!StringUtils.hasText(token)) {
                int errcode = result.path("errcode").asInt(-1);
                String errmsg = result.path("errmsg").asText("微信access_token获取失败");
                throw new IllegalStateException("WECHAT_TOKEN_ERR_" + errcode + ": " + errmsg);
            }
            int expiresIn = Math.max(result.path("expires_in").asInt(7200), 300);
            tokenCache = new TokenCache(cacheKey, token, LocalDateTime.now().plusSeconds(expiresIn - 120L));
            return token;
        }
    }

    @EventListener
    public void onMiniProgramConfigChanged(NotificationMiniProgramRuntimeConfigService.MiniProgramConfigChangedEvent event) {
        tokenCache = null;
    }

    private Map<String, Object> buildPayload(
            String openid,
            NotificationMiniProgramProperties.Template template,
            NotificationMessage message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("touser", openid);
        payload.put("template_id", template.getTemplateId());
        payload.put("page", resolvePage(message, template));
        payload.put("data", buildTemplateData(message, template));
        return payload;
    }

    private Map<String, Object> buildTemplateData(
            NotificationMessage message,
            NotificationMiniProgramProperties.Template template) {
        Map<String, Object> data = new LinkedHashMap<>();
        template.getFields().forEach((semanticKey, fieldName) ->
                putTemplateValue(data, fieldName, resolveFieldValue(semanticKey, message)));
        return data;
    }

    private void putTemplateValue(Map<String, Object> data, String fieldName, String fieldValue) {
        if (StringUtils.hasText(fieldName)) {
            data.put(fieldName, value(fieldValue));
        }
    }

    private String resolveFieldValue(String semanticKey, NotificationMessage message) {
        return switch (semanticKey == null ? "" : semanticKey) {
            case "title" -> limit(message.getTitle(), 20);
            case "summary", "tip" -> limit(firstText(message.getSummary(), message.getContent(), message.getTitle()), 20);
            case "content" -> limit(firstText(message.getContent(), message.getSummary(), message.getTitle()), 20);
            case "date", "time" -> formatTime(message.getCreatedTime());
            case "business" -> limit(message.getBusinessType(), 20);
            case "type" -> limit(typeText(message.getType()), 5);
            case "creator", "operator" -> limit(resolveCreatorName(message.getCreatedBy()), 20);
            default -> limit(firstText(message.getSummary(), message.getTitle(), message.getType()), 20);
        };
    }

    private String resolvePage(NotificationMessage message, NotificationMiniProgramProperties.Template template) {
        if (StringUtils.hasText(template.getPage())) {
            return template.getPage();
        }
        if (NotificationService.LINK_TYPE_PRICE_QUERY.equals(message.getLinkType())) {
            return "pages/home/index";
        }
        String defaultPage = runtimeConfigService.activeConfig().getDefaultPage();
        if (NotificationService.LINK_TYPE_SYSTEM_NOTICE.equals(message.getLinkType())) {
            return defaultPage;
        }
        return StringUtils.hasText(defaultPage)
                ? defaultPage
                : "pages/notifications/index";
    }

    private Map<String, String> value(String value) {
        return Map.of("value", value == null ? "" : value);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String typeText(String type) {
        if (NotificationService.TYPE_PRICE_PUBLISHED.equals(type)) {
            return "报价变更";
        }
        if (NotificationService.TYPE_SYSTEM_NOTICE.equals(type)) {
            return "系统公告";
        }
        return type;
    }

    private String formatTime(LocalDateTime time) {
        return (time == null ? LocalDateTime.now() : time)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String resolveCreatorName(Long createdBy) {
        if (createdBy == null) {
            return "系统";
        }
        return userRepository.findById(createdBy)
                .map(user -> firstText(user.getNickname(), user.getUsername(), String.valueOf(createdBy)))
                .orElse("系统");
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(timeout())
                .build();
    }

    private Duration timeout() {
        return Duration.ofMillis(Math.max(runtimeConfigService.activeConfig().getTimeoutMs(), 1000));
    }

    private record TokenCache(String cacheKey, String token, LocalDateTime expiresAt) {
    }
}
