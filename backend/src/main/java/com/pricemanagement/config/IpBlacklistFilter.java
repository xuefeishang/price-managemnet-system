package com.pricemanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.service.IpBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpBlacklistFilter extends OncePerRequestFilter {

    private final IpBlacklistService ipBlacklistService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = ipBlacklistService.resolveClientIp(request);
        IpBlacklistService.MatchResult matchResult = ipBlacklistService.match(clientIp);
        if (!matchResult.blocked()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (matchResult.observationMode()) {
            log.warn("IP blacklist observation hit: ip={}, uri={}, reason={}",
                    clientIp, request.getRequestURI(), matchResult.reason());
            ipBlacklistService.recordHit(request, matchResult, HttpServletResponse.SC_OK);
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("IP blacklist blocked request: ip={}, uri={}, blacklistId={}",
                clientIp, request.getRequestURI(), matchResult.blacklistId());
        ipBlacklistService.recordHit(request, matchResult, HttpServletResponse.SC_FORBIDDEN);
        writeForbidden(response);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", 403, "message", "请求来源已被限制", "timestamp", System.currentTimeMillis())
        ));
    }
}
