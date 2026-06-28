package com.pricemanagement.config;

import com.pricemanagement.annotation.RateLimiter;
import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.exception.RateLimitException;
import com.pricemanagement.service.ClientIpResolver;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimiterAspectTests {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ipLimitIgnoresSpoofedForwardedHeaderForDirectRequests() throws NoSuchMethodException {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.getClientIp().setTrustedProxies(List.of("10.0.0.2"));
        RateLimiterAspect aspect = new RateLimiterAspect(new ClientIpResolver(securityProperties));
        JoinPoint joinPoint = joinPoint();
        RateLimiter limiter = limiter();

        MockHttpServletRequest first = request("203.0.113.10");
        first.addHeader("X-Forwarded-For", "198.51.100.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(first));
        assertDoesNotThrow(() -> aspect.doBefore(joinPoint, limiter));

        MockHttpServletRequest second = request("203.0.113.10");
        second.addHeader("X-Forwarded-For", "198.51.100.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(second));
        assertThrows(RateLimitException.class, () -> aspect.doBefore(joinPoint, limiter));
    }

    private JoinPoint joinPoint() {
        Signature signature = mock(Signature.class);
        when(signature.getDeclaringType()).thenReturn(AuthActions.class);
        when(signature.getName()).thenReturn("login");

        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    private RateLimiter limiter() throws NoSuchMethodException {
        Method method = AuthActions.class.getDeclaredMethod("login");
        return method.getAnnotation(RateLimiter.class);
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private static class AuthActions {
        @RateLimiter(time = 60, count = 1, limitType = RateLimiter.LimitType.IP)
        void login() {
        }
    }
}
