package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.dto.ProfileSessionDTO;
import com.pricemanagement.entity.RefreshToken;
import com.pricemanagement.entity.User;
import com.pricemanagement.exception.TokenRefreshException;
import com.pricemanagement.repository.RefreshTokenRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.util.IpAddressUtil;
import com.pricemanagement.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 刷新令牌服务
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final SecurityProperties securityProperties;

    /**
     * 刷新令牌有效期（默认7天）
     */
    private static final long REFRESH_TOKEN_DURATION_DAYS = 7;

    /**
     * 创建刷新令牌
     */
    @Transactional
    public RefreshToken createRefreshToken(Long userId, String username) {
        return createRefreshToken(userId, username, null);
    }

    @Transactional
    public RefreshToken createRefreshToken(Long userId, String username, HttpServletRequest request) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUserId(userId);
        refreshToken.setUsername(username);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_DURATION_DAYS));
        refreshToken.setRevoked(false);
        refreshToken.setLastUsedTime(LocalDateTime.now());
        if (request != null) {
            refreshToken.setIpAddress(IpAddressUtil.getClientIp(request));
            String userAgent = request.getHeader("User-Agent");
            refreshToken.setUserAgent(truncate(userAgent, 500));
            refreshToken.setDeviceName(resolveDeviceName(userAgent));
        }

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * 验证刷新令牌
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isRevoked()) {
            throw new TokenRefreshException("刷新令牌已被撤销");
        }

        if (token.isExpired()) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException("刷新令牌已过期，请重新登录");
        }

        return token;
    }

    /**
     * 使用刷新令牌获取新的访问令牌
     */
    @Transactional
    public String refreshAccessToken(String refreshTokenStr) {
        return refreshAccessToken(refreshTokenStr, null);
    }

    @Transactional
    public String refreshAccessToken(String refreshTokenStr, HttpServletRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new TokenRefreshException("刷新令牌不存在"));

        verifyExpiration(refreshToken);

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new TokenRefreshException("用户不存在"));

        refreshToken.setLastUsedTime(LocalDateTime.now());
        if (request != null) {
            refreshToken.setIpAddress(IpAddressUtil.getClientIp(request));
            String userAgent = request.getHeader("User-Agent");
            refreshToken.setUserAgent(truncate(userAgent, 500));
            refreshToken.setDeviceName(resolveDeviceName(userAgent));
        }
        refreshTokenRepository.save(refreshToken);

        return jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
    }

    /**
     * 撤销用户的刷新令牌（登出时调用）
     */
    @Transactional
    public void revokeUserTokens(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.debug("Revoked all refresh tokens for user: {}", userId);
    }

    /**
     * 撤销指定的刷新令牌
     */
    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
    }

    public List<ProfileSessionDTO> getUserSessions(Long userId, String currentRefreshToken) {
        return refreshTokenRepository.findByUserIdAndRevokedFalseAndExpiryDateAfterOrderByCreatedTimeDesc(userId, LocalDateTime.now())
                .stream()
                .map(token -> toSessionDTO(token, currentRefreshToken))
                .toList();
    }

    @Transactional
    public void revokeSession(Long userId, Long sessionId, String currentRefreshToken) {
        RefreshToken refreshToken = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!refreshToken.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该会话");
        }
        if (currentRefreshToken != null && currentRefreshToken.equals(refreshToken.getToken())) {
            throw new IllegalArgumentException("不能在此处撤销当前会话");
        }
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void revokeOtherSessions(Long userId, String currentRefreshToken) {
        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            throw new IllegalArgumentException("缺少当前会话令牌，无法识别其他设备");
        }
        refreshTokenRepository.revokeOtherByUserId(userId, currentRefreshToken);
    }

    @Transactional
    public void revokeAllSessions(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    /**
     * 定期清理过期令牌（每小时执行）
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens();
        log.debug("Cleaned up expired refresh tokens");
    }

    private ProfileSessionDTO toSessionDTO(RefreshToken token, String currentRefreshToken) {
        ProfileSessionDTO dto = new ProfileSessionDTO();
        dto.setId(token.getId());
        dto.setCurrent(currentRefreshToken != null && currentRefreshToken.equals(token.getToken()));
        dto.setDeviceName(token.getDeviceName());
        dto.setIpAddress(token.getIpAddress());
        dto.setUserAgent(token.getUserAgent());
        dto.setCreatedTime(token.getCreatedTime());
        dto.setLastUsedTime(token.getLastUsedTime());
        dto.setExpiryDate(token.getExpiryDate());
        dto.setRevoked(token.isRevoked());
        return dto;
    }

    private String resolveDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "未知设备";
        }
        String browser = userAgent.contains("Edg") ? "Edge"
                : userAgent.contains("Chrome") ? "Chrome"
                : userAgent.contains("Firefox") ? "Firefox"
                : userAgent.contains("Safari") ? "Safari"
                : "浏览器";
        String os = userAgent.contains("Windows") ? "Windows"
                : userAgent.contains("Mac OS") ? "macOS"
                : userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS"
                : userAgent.contains("Linux") ? "Linux"
                : "未知系统";
        return browser + " / " + os;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
