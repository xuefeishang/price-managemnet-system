package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.entity.RefreshToken;
import com.pricemanagement.entity.User;
import com.pricemanagement.exception.TokenRefreshException;
import com.pricemanagement.repository.RefreshTokenRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        // 删除用户旧的刷新令牌
        refreshTokenRepository.deleteByUserId(userId);

        // 生成新的刷新令牌
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUserId(userId);
        refreshToken.setUsername(username);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(REFRESH_TOKEN_DURATION_DAYS));
        refreshToken.setRevoked(false);

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
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new TokenRefreshException("刷新令牌不存在"));

        verifyExpiration(refreshToken);

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new TokenRefreshException("用户不存在"));

        // 生成新的访问令牌
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

    /**
     * 定期清理过期令牌（每小时执行）
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredTokens();
        log.debug("Cleaned up expired refresh tokens");
    }
}