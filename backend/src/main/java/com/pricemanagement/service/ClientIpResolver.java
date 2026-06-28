package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.util.IpAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Service
@RequiredArgsConstructor
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";

    private final SecurityProperties securityProperties;

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String remoteAddr = normalize(request.getRemoteAddr());
        if (!securityProperties.getClientIp().isForwardedHeaderEnabled()
                || !IpAddressUtil.matchesAny(remoteAddr, securityProperties.getClientIp().getTrustedProxies())) {
            return remoteAddr;
        }

        String forwardedIp = firstForwardedIp(request.getHeader("X-Forwarded-For"));
        if (forwardedIp != null) {
            return forwardedIp;
        }

        String realIp = normalize(request.getHeader("X-Real-IP"));
        if (realIp != null && isValidIp(realIp)) {
            return realIp;
        }

        return remoteAddr;
    }

    private String firstForwardedIp(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String first = normalize(headerValue.split(",", 2)[0]);
        return first != null && isValidIp(first) ? first : null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank() || UNKNOWN.equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private boolean isValidIp(String value) {
        try {
            InetAddress.getByName(value);
            return value.chars().allMatch(ch -> Character.digit(ch, 16) >= 0 || ch == '.' || ch == ':' || ch == '%');
        } catch (Exception ex) {
            return false;
        }
    }
}
