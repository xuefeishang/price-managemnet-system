package com.pricemanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.entity.ApiCallLog;
import com.pricemanagement.entity.ApiKey;
import com.pricemanagement.entity.ExternalApiEndpoint;
import com.pricemanagement.repository.ApiKeyPermissionRepository;
import com.pricemanagement.repository.ApiKeyRepository;
import com.pricemanagement.service.ApiCallLogService;
import com.pricemanagement.service.ApiKeySecretService;
import com.pricemanagement.service.ApiNonceService;
import com.pricemanagement.service.ApiRateLimitService;
import com.pricemanagement.service.ClientIpResolver;
import com.pricemanagement.service.ExternalApiPermissionService;
import com.pricemanagement.service.ExternalApiServiceStatusService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiKeyAuthenticationFilterTests {

    private ApiKeyRepository apiKeyRepository;
    private ApiKeyPermissionRepository permissionRepository;
    private ExternalApiPermissionService permissionService;
    private ApiCallLogService callLogService;
    private ExternalApiServiceStatusService serviceStatusService;
    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        ApiKeyProperties apiKeyProperties = new ApiKeyProperties(mock(Environment.class));
        apiKeyProperties.setEnabled(true);

        apiKeyRepository = mock(ApiKeyRepository.class);
        permissionRepository = mock(ApiKeyPermissionRepository.class);
        permissionService = mock(ExternalApiPermissionService.class);
        callLogService = mock(ApiCallLogService.class);
        serviceStatusService = mock(ExternalApiServiceStatusService.class);

        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getClientIp().setTrustedProxies(List.of("10.0.0.2"));

        filter = new ApiKeyAuthenticationFilter(
                apiKeyProperties,
                apiKeyRepository,
                permissionRepository,
                permissionService,
                mock(ApiKeySecretService.class),
                mock(ApiNonceService.class),
                mock(ApiRateLimitService.class),
                callLogService,
                serviceStatusService,
                new ClientIpResolver(securityProperties),
                new ObjectMapper());

        when(serviceStatusService.isRuntimeEnabled()).thenReturn(true);
        when(permissionService.match("GET", "/api/external/v1/products")).thenReturn(Optional.of(endpoint()));
        when(apiKeyRepository.findByAppId("app-1")).thenReturn(Optional.of(apiKey()));
    }

    @Test
    void whitelistIgnoresSpoofedForwardedHeaderForDirectRequests() throws ServletException, IOException {
        MockHttpServletRequest request = request("198.51.100.9");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("IP不在白名单"));
        ApiCallLog log = capturedLog();
        assertEquals("198.51.100.9", log.getIpAddress());
        assertEquals("IP_DENIED", log.getAuthResult());
    }

    @Test
    void whitelistUsesForwardedIpFromTrustedProxy() throws ServletException, IOException {
        when(permissionRepository.findByApiKeyId(1L)).thenReturn(List.of());
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("API Key未授权该接口"));
        ApiCallLog log = capturedLog();
        assertEquals("203.0.113.10", log.getIpAddress());
        assertEquals("FORBIDDEN", log.getAuthResult());
    }

    private ApiCallLog capturedLog() {
        ArgumentCaptor<ApiCallLog> captor = ArgumentCaptor.forClass(ApiCallLog.class);
        verify(callLogService).log(captor.capture());
        return captor.getValue();
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/external/v1/products");
        request.setRemoteAddr(remoteAddr);
        request.addHeader(ApiKeyAuthenticationFilter.HEADER_APP_ID, "app-1");
        request.addHeader(ApiKeyAuthenticationFilter.HEADER_TIMESTAMP, String.valueOf(Instant.now().getEpochSecond()));
        request.addHeader(ApiKeyAuthenticationFilter.HEADER_NONCE, "nonce-1");
        request.addHeader(ApiKeyAuthenticationFilter.HEADER_SIGNATURE, "signature");
        return request;
    }

    private ExternalApiEndpoint endpoint() {
        ExternalApiEndpoint endpoint = new ExternalApiEndpoint();
        endpoint.setPermissionCode("PRODUCT_READ");
        endpoint.setMethod("GET");
        endpoint.setPathPattern("/api/external/v1/products");
        return endpoint;
    }

    private ApiKey apiKey() {
        ApiKey apiKey = new ApiKey();
        apiKey.setId(1L);
        apiKey.setAppId("app-1");
        apiKey.setName("测试应用");
        apiKey.setStatus("ACTIVE");
        apiKey.setIpWhitelist("[\"203.0.113.10\"]");
        return apiKey;
    }
}
