package com.pricemanagement.config;

import com.pricemanagement.constants.SystemConstants;
import com.pricemanagement.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@SuppressWarnings("deprecation")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    // 不需要认证的路径（统一从 SystemConstants 获取）
    private static final List<String> PUBLIC_PATHS = List.of(SystemConstants.PUBLIC_PATHS);

    // 允许未登录访问的路径（但如果带 token 会正常认证）
    private static final List<String> OPTIONAL_AUTH_PATHS = List.of();

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String token = extractToken(request);

        // 公开路径：无需认证，但如果提供了 token 仍然进行认证（支持权限检查）
        if (isPublicPath(path)) {
            if (StringUtils.hasText(token)) {
                authenticateToken(token);
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 可选认证路径：有 token 则认证，无 token 也不拒绝（Controller 层可选择放行未登录用户）
        if (isOptionalAuthPath(path)) {
            if (StringUtils.hasText(token)) {
                authenticateToken(token);
            }
            filterChain.doFilter(request, response);
            return;
        }

        // 其他路径：有 token 则认证，无 token 继续（由 Spring Security 的 anyRequest().authenticated() 拦截）
        if (StringUtils.hasText(token)) {
            authenticateToken(token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateToken(String token) {
        try {
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                String role = jwtUtil.getRoleFromToken(token);

                if (username != null && role != null) {
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority(SystemConstants.ROLE_PREFIX + role)
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(userId);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("Authenticated user: {} with role: {}, authorities: {}", username, role, authorities);
                }
            } else {
                log.warn("Invalid JWT token for request");
            }
        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(pattern -> {
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                return path.startsWith(prefix);
            }
            return path.equals(pattern);
        });
    }

    private boolean isOptionalAuthPath(String path) {
        return OPTIONAL_AUTH_PATHS.contains(path);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(SystemConstants.AUTH_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(SystemConstants.BEARER_PREFIX)) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
