package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTests {

    private SecurityProperties securityProperties;
    private ClientIpResolver resolver;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getClientIp().setTrustedProxies(List.of("10.0.0.2", "10.10.0.0/16"));
        resolver = new ClientIpResolver(securityProperties);
    }

    @Test
    void directRequestIgnoresSpoofedForwardedHeaders() {
        MockHttpServletRequest request = request("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        request.addHeader("X-Real-IP", "198.51.100.2");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void trustedProxyUsesFirstForwardedIp() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void trustedProxyCanBeMatchedByCidr() {
        MockHttpServletRequest request = request("10.10.1.8");
        request.addHeader("X-Forwarded-For", "203.0.113.20");

        assertEquals("203.0.113.20", resolver.resolve(request));
    }

    @Test
    void invalidForwardedFallsBackToRealIpThenRemoteAddr() {
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "unknown, 203.0.113.10");
        request.addHeader("X-Real-IP", "198.51.100.5");

        assertEquals("198.51.100.5", resolver.resolve(request));
    }

    @Test
    void disabledForwardedHeaderAlwaysUsesRemoteAddr() {
        securityProperties.getClientIp().setForwardedHeaderEnabled(false);
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("X-Real-IP", "198.51.100.5");

        assertEquals("10.0.0.2", resolver.resolve(request));
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}
