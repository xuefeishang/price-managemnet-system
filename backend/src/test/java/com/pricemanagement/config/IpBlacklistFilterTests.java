package com.pricemanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.entity.IpBlacklist;
import com.pricemanagement.entity.SecurityEvent;
import com.pricemanagement.repository.IpBlacklistRepository;
import com.pricemanagement.repository.SecurityEventRepository;
import com.pricemanagement.service.ClientIpResolver;
import com.pricemanagement.service.IpBlacklistService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IpBlacklistFilterTests {

    @Mock
    private IpBlacklistRepository ipBlacklistRepository;
    @Mock
    private SecurityEventRepository securityEventRepository;

    private SecurityProperties securityProperties;
    private IpBlacklistService ipBlacklistService;
    private IpBlacklistFilter filter;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getClientIp().setTrustedProxies(List.of());
        securityProperties.getIpBlacklist().setBypassSources(List.of());
        ipBlacklistService = new IpBlacklistService(
                ipBlacklistRepository,
                securityEventRepository,
                securityProperties,
                new ClientIpResolver(securityProperties));
        filter = new IpBlacklistFilter(ipBlacklistService, new ObjectMapper());
    }

    @Test
    void blocksActiveBlacklistedIpBeforeAuthentication() throws ServletException, IOException {
        when(ipBlacklistRepository.findByIpAddressAndActiveTrue("203.0.113.10"))
                .thenReturn(Optional.of(activeBlacklist()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("203.0.113.10"), response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"message\":\"请求来源已被限制\""));
        verify(securityEventRepository).save(any(SecurityEvent.class));
    }

    @Test
    void observationModeRecordsAndAllowsRequest() throws ServletException, IOException {
        securityProperties.getIpBlacklist().setObservationMode(true);
        when(ipBlacklistRepository.findByIpAddressAndActiveTrue("203.0.113.10"))
                .thenReturn(Optional.of(activeBlacklist()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("203.0.113.10"), response, chain);

        assertEquals(200, response.getStatus());
        verify(securityEventRepository).save(any(SecurityEvent.class));
    }

    @Test
    void expiredBlacklistIsDeactivatedAndAllowed() {
        IpBlacklist record = activeBlacklist();
        record.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(ipBlacklistRepository.findByIpAddressAndActiveTrue("203.0.113.10"))
                .thenReturn(Optional.of(record));

        IpBlacklistService.MatchResult result = ipBlacklistService.match("203.0.113.10");

        assertFalse(result.blocked());
        assertFalse(record.getActive());
        verify(ipBlacklistRepository).save(record);
    }

    @Test
    void directRequestCannotSpoofLoopbackForwardedHeader() throws ServletException, IOException {
        when(ipBlacklistRepository.findByIpAddressAndActiveTrue("203.0.113.10"))
                .thenReturn(Optional.of(activeBlacklist()));
        MockHttpServletRequest request = request("203.0.113.10");
        request.addHeader("X-Forwarded-For", "127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void trustedProxyForwardedHeaderIsUsedForBlacklistMatch() throws ServletException, IOException {
        securityProperties.getClientIp().setTrustedProxies(List.of("10.0.0.2"));
        when(ipBlacklistRepository.findByIpAddressAndActiveTrue("203.0.113.10"))
                .thenReturn(Optional.of(activeBlacklist()));
        MockHttpServletRequest request = request("10.0.0.2");
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void notBlockedDecisionIsNotCachedByDefault() {
        when(ipBlacklistRepository.findByIpAddressAndActiveTrue("203.0.113.10"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(activeBlacklist()));

        IpBlacklistService.MatchResult first = ipBlacklistService.match("203.0.113.10");
        IpBlacklistService.MatchResult second = ipBlacklistService.match("203.0.113.10");

        assertFalse(first.blocked());
        assertTrue(second.blocked());
        verify(ipBlacklistRepository, times(2)).findByIpAddressAndActiveTrue("203.0.113.10");
    }

    @Test
    void cachedBlockedDecisionExpiresAtRecordExpiry() throws InterruptedException {
        IpBlacklist record = activeBlacklist();
        record.setExpiresAt(LocalDateTime.now().plusSeconds(1));
        when(ipBlacklistRepository.findByIpAddressAndActiveTrue("203.0.113.10"))
                .thenReturn(Optional.of(record))
                .thenReturn(Optional.of(record));

        IpBlacklistService.MatchResult first = ipBlacklistService.match("203.0.113.10");
        Thread.sleep(1200);
        IpBlacklistService.MatchResult second = ipBlacklistService.match("203.0.113.10");

        assertTrue(first.blocked());
        assertFalse(second.blocked());
        assertFalse(record.getActive());
        verify(ipBlacklistRepository, times(2)).findByIpAddressAndActiveTrue("203.0.113.10");
        verify(ipBlacklistRepository).save(record);
    }

    private MockHttpServletRequest request(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setRemoteAddr(ip);
        request.addHeader("User-Agent", "JUnit");
        return request;
    }

    private IpBlacklist activeBlacklist() {
        IpBlacklist blacklist = new IpBlacklist();
        blacklist.setId(9L);
        blacklist.setIpAddress("203.0.113.10");
        blacklist.setReason("too many failures");
        blacklist.setBannedBy(IpBlacklist.BannedBy.MANUAL_ADMIN);
        blacklist.setBannedAt(LocalDateTime.now().minusMinutes(10));
        blacklist.setActive(true);
        return blacklist;
    }
}
