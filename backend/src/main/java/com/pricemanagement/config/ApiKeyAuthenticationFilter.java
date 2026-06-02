package com.pricemanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.dto.ExternalApiPrincipal;
import com.pricemanagement.entity.ApiCallLog;
import com.pricemanagement.entity.ApiKey;
import com.pricemanagement.entity.ApiKeyPermission;
import com.pricemanagement.entity.ExternalApiEndpoint;
import com.pricemanagement.repository.ApiKeyPermissionRepository;
import com.pricemanagement.repository.ApiKeyRepository;
import com.pricemanagement.service.ApiCallLogService;
import com.pricemanagement.service.ApiKeySecretService;
import com.pricemanagement.service.ApiNonceService;
import com.pricemanagement.service.ApiRateLimitService;
import com.pricemanagement.service.ExternalApiServiceStatusService;
import com.pricemanagement.service.ExternalApiPermissionService;
import com.pricemanagement.util.ApiSignatureUtil;
import com.pricemanagement.util.IpAddressUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_APP_ID = "X-App-Id";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";
    public static final String HEADER_APP_SECRET = "X-App-Secret";

    private final ApiKeyProperties properties;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyPermissionRepository permissionRepository;
    private final ExternalApiPermissionService permissionService;
    private final ApiKeySecretService secretService;
    private final ApiNonceService nonceService;
    private final ApiRateLimitService rateLimitService;
    private final ApiCallLogService callLogService;
    private final ExternalApiServiceStatusService serviceStatusService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/external/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        ExternalApiAuthException preflightFailure = preflight(request);
        if (preflightFailure != null) {
            ApiCallLog callLog = buildBaseLog(request);
            callLog.setAuthResult(preflightFailure.authResult());
            callLog.setStatusCode(preflightFailure.statusCode());
            callLog.setResponseTime((int) (System.currentTimeMillis() - start));
            callLog.setErrorMessage(preflightFailure.getMessage());
            callLogService.log(callLog);
            writeError(response, preflightFailure.statusCode(), preflightFailure.getMessage());
            return;
        }
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
        ApiCallLog callLog = buildBaseLog(wrapped);
        String bodyHash = ApiSignatureUtil.sha256Hex(wrapped.getCachedBody());
        callLog.setRequestBodyHash(bodyHash);

        AuthenticationResult result;
        try {
            result = authenticate(wrapped, bodyHash);
        } catch (ExternalApiAuthException ex) {
            SecurityContextHolder.clearContext();
            callLog.setAuthResult(ex.authResult());
            callLog.setStatusCode(ex.statusCode());
            callLog.setResponseTime((int) (System.currentTimeMillis() - start));
            callLog.setPermissionCode(ex.permissionCode());
            callLog.setErrorMessage(ex.getMessage());
            callLogService.log(callLog);
            writeError(response, ex.statusCode(), ex.getMessage());
            return;
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            log.error("External API authentication failed unexpectedly", ex);
            callLog.setAuthResult("INVALID_SIGNATURE");
            callLog.setStatusCode(HttpServletResponse.SC_UNAUTHORIZED);
            callLog.setResponseTime((int) (System.currentTimeMillis() - start));
            callLog.setErrorMessage("外部API认证失败");
            callLogService.log(callLog);
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "外部API认证失败");
            return;
        }

        callLog.setApiKeyId(result.apiKey().getId());
        callLog.setPermissionCode(result.endpoint().getPermissionCode());
        callLog.setAuthResult("SUCCESS");
        SecurityContextHolder.getContext().setAuthentication(result.authentication());
        try {
            filterChain.doFilter(wrapped, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            if (response.getStatus() < HttpServletResponse.SC_BAD_REQUEST) {
                callLog.setStatusCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            callLog.setErrorMessage("外部API业务处理失败");
            throw ex;
        } finally {
            if (callLog.getStatusCode() == null) {
                callLog.setStatusCode(response.getStatus());
            }
            callLog.setResponseTime((int) (System.currentTimeMillis() - start));
            updateLastUsedTime(result.apiKey().getId());
            callLogService.log(callLog);
            SecurityContextHolder.clearContext();
        }
    }

    private AuthenticationResult authenticate(CachedBodyHttpServletRequest request, String bodyHash) {
        String appId = requiredHeader(request, HEADER_APP_ID);
        String timestamp = requiredHeader(request, HEADER_TIMESTAMP);
        String nonce = requiredHeader(request, HEADER_NONCE);
        String signature = requiredHeader(request, HEADER_SIGNATURE);

        ExternalApiEndpoint endpoint = permissionService.match(request.getMethod(), request.getRequestURI())
                .orElseThrow(() -> new ExternalApiAuthException(HttpServletResponse.SC_FORBIDDEN,
                        "FORBIDDEN", "外部API未开放", null));

        ApiKey apiKey = apiKeyRepository.findByAppId(appId)
                .orElseThrow(() -> new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                        "INVALID_APP_ID", "App ID不存在", endpoint.getPermissionCode()));

        assertApiKeyActive(apiKey, endpoint.getPermissionCode());
        assertTimestamp(timestamp, endpoint.getPermissionCode());
        assertIpAllowed(request, apiKey, endpoint.getPermissionCode());
        assertPermission(apiKey, endpoint.getPermissionCode());

        String appSecret = secretService.decrypt(apiKey.getAppSecretCipher());
        String canonicalString = ApiSignatureUtil.canonicalString(request, timestamp, nonce, bodyHash);
        String expected = ApiSignatureUtil.hmacSha256Hex(appSecret, canonicalString);
        if (!ApiSignatureUtil.constantTimeEquals(expected, signature)) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_SIGNATURE", "签名错误", endpoint.getPermissionCode());
        }
        if (!nonceService.registerNonce(appId, nonce)) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "REPLAY", "请求Nonce已使用", endpoint.getPermissionCode());
        }
        if (!rateLimitService.allow(apiKey)) {
            throw new ExternalApiAuthException(429, "RATE_LIMITED", "外部API调用超出限额", endpoint.getPermissionCode());
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_EXTERNAL_API"));
        permissionRepository.findByApiKeyId(apiKey.getId()).stream()
                .map(ApiKeyPermission::getPermissionCode)
                .map(code -> new SimpleGrantedAuthority("API_" + code))
                .forEach(authorities::add);

        ExternalApiPrincipal principal = new ExternalApiPrincipal(apiKey.getId(), apiKey.getAppId(), apiKey.getName());
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return new AuthenticationResult(apiKey, endpoint, authentication);
    }

    private String requiredHeader(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (!StringUtils.hasText(value)) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "MISSING_HEADER", "缺少认证头: " + headerName, null);
        }
        return value.trim();
    }

    private void assertApiKeyActive(ApiKey apiKey, String permissionCode) {
        if (!"ACTIVE".equals(apiKey.getStatus())) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "INVALID_APP_ID", "API Key不可用", permissionCode);
        }
        if (apiKey.getExpireTime() != null && apiKey.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "EXPIRED_TIMESTAMP", "API Key已过期", permissionCode);
        }
    }

    private void assertTimestamp(String timestamp, String permissionCode) {
        long requestEpoch;
        try {
            requestEpoch = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "EXPIRED_TIMESTAMP", "时间戳格式错误", permissionCode);
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - requestEpoch) > properties.getTimestampWindowSeconds()) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "EXPIRED_TIMESTAMP", "时间戳超出允许窗口", permissionCode);
        }
    }

    private void assertIpAllowed(HttpServletRequest request, ApiKey apiKey, String permissionCode) {
        List<String> whitelist = parseIpWhitelist(apiKey.getIpWhitelist());
        String clientIp = IpAddressUtil.getClientIp(request);
        if (!IpAddressUtil.isAllowed(clientIp, whitelist)) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_FORBIDDEN,
                    "IP_DENIED", "IP不在白名单", permissionCode);
        }
    }

    private List<String> parseIpWhitelist(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ex) {
            log.warn("Invalid API key IP whitelist JSON: {}", ex.getMessage());
            return List.of("__INVALID__");
        }
    }

    private void assertPermission(ApiKey apiKey, String permissionCode) {
        boolean allowed = permissionRepository.findByApiKeyId(apiKey.getId()).stream()
                .map(ApiKeyPermission::getPermissionCode)
                .anyMatch(permissionCode::equals);
        if (!allowed) {
            throw new ExternalApiAuthException(HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN", "API Key未授权该接口", permissionCode);
        }
    }

    private ApiCallLog buildBaseLog(HttpServletRequest request) {
        ApiCallLog callLog = new ApiCallLog();
        callLog.setAppId(request.getHeader(HEADER_APP_ID));
        callLog.setEndpoint(request.getRequestURI());
        callLog.setQueryString(request.getQueryString());
        callLog.setMethod(request.getMethod());
        callLog.setIpAddress(IpAddressUtil.getClientIp(request));
        callLog.setRequestTime(LocalDateTime.now());
        callLog.setNonce(request.getHeader(HEADER_NONCE));
        callLog.setAuthResult("MISSING_HEADER");
        return callLog;
    }

    private ExternalApiAuthException preflight(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return new ExternalApiAuthException(HttpServletResponse.SC_NOT_FOUND, "DISABLED", "外部API未启用", null);
        }
        if (!serviceStatusService.isRuntimeEnabled()) {
            return new ExternalApiAuthException(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "SERVICE_DISABLED", "外部API服务已暂停", null);
        }
        if (StringUtils.hasText(request.getHeader("Authorization"))) {
            return new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "FORBIDDEN", "外部API不允许携带Authorization", null);
        }
        if (StringUtils.hasText(request.getHeader(HEADER_APP_SECRET))) {
            return new ExternalApiAuthException(HttpServletResponse.SC_UNAUTHORIZED,
                    "FORBIDDEN", "禁止传输App Secret", null);
        }
        if (isMultipartRequest(request)) {
            return new ExternalApiAuthException(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_CONTENT_TYPE", "阶段一不支持multipart外部API", null);
        }
        return null;
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private void updateLastUsedTime(Long apiKeyId) {
        try {
            apiKeyRepository.updateLastUsedTime(apiKeyId, LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Failed to update API key last used time: {}", ex.getMessage());
        }
    }

    private void writeError(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", statusCode, "message", message, "timestamp", System.currentTimeMillis())
        ));
    }

    private record AuthenticationResult(ApiKey apiKey, ExternalApiEndpoint endpoint,
                                        UsernamePasswordAuthenticationToken authentication) {
    }

    private static class ExternalApiAuthException extends RuntimeException {
        private final int statusCode;
        private final String authResult;
        private final String permissionCode;

        ExternalApiAuthException(int statusCode, String authResult, String message, String permissionCode) {
            super(message);
            this.statusCode = statusCode;
            this.authResult = authResult;
            this.permissionCode = permissionCode;
        }

        int statusCode() {
            return statusCode;
        }

        String authResult() {
            return authResult;
        }

        String permissionCode() {
            return permissionCode;
        }
    }
}
