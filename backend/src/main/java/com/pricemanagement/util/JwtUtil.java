
package com.pricemanagement.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT令牌（支持多角色）
     * @param userId 用户ID
     * @param username 用户名
     * @param roles 角色列表
     * @param primaryRole 主角色（用于兼容旧逻辑）
     */
    public String generateToken(Long userId, String username, List<String> roles, String primaryRole) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("roles", roles);  // 角色列表
        claims.put("role", primaryRole);  // 主角色（兼容）
        return createToken(claims, username);
    }

    /**
     * 生成JWT令牌（单角色，兼容旧接口）
     */
    public String generateToken(Long userId, String username, String role) {
        return generateToken(userId, username, List.of(role), role);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("userId", Long.class) : null;
    }

    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 获取主角色（兼容旧逻辑）
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    /**
     * 获取角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return List.of();
        }
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List) {
            List<String> roles = (List<String>) rolesObj;
            if (!roles.isEmpty()) {
                return roles;
            }
        }
        // 兼容旧令牌：从 role 字段获取
        String role = claims.get("role", String.class);
        return role != null ? List.of(role) : List.of();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                return false;
            }
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
