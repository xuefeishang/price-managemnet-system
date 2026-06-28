package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.RefreshToken;
import com.pricemanagement.entity.User;
import com.pricemanagement.exception.TokenRefreshException;
import com.pricemanagement.repository.RefreshTokenRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PermissionService permissionService;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                jwtUtil,
                new SecurityProperties(),
                permissionService,
                new ClientIpResolver(new SecurityProperties()));
    }

    @Test
    void refreshAccessTokenAllowsActiveUnlockedUserWithActiveRole() {
        RefreshToken refreshToken = token();
        User user = user(CommonStatus.ACTIVE, false);
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(permissionService.getUserRoleCodes(1L)).thenReturn(List.of("EDITOR"));
        when(permissionService.getUserPermissions(1L)).thenReturn(Set.of("product:view"));
        when(jwtUtil.generateToken(1L, "editor", List.of("EDITOR"), "EDITOR", Set.of("product:view")))
                .thenReturn("new-access-token");

        String accessToken = refreshTokenService.refreshAccessToken("rt-1");

        assertEquals("new-access-token", accessToken);
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void refreshAccessTokenRevokesTokenWhenUserInactive() {
        assertRefreshRejected(user(CommonStatus.INACTIVE, false));
    }

    @Test
    void refreshAccessTokenRevokesTokenWhenUserLocked() {
        assertRefreshRejected(user(CommonStatus.ACTIVE, true));
    }

    @Test
    void refreshAccessTokenRevokesTokenWhenRoleIsMissing() {
        RefreshToken refreshToken = token();
        User user = user(CommonStatus.ACTIVE, false);
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(permissionService.getUserRoleCodes(1L)).thenReturn(List.of());

        TokenRefreshException exception = assertThrows(TokenRefreshException.class,
                () -> refreshTokenService.refreshAccessToken("rt-1"));

        assertEquals("刷新令牌无效，请重新登录", exception.getMessage());
        assertTrue(refreshToken.isRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void refreshAccessTokenRevokesTokenWhenUserNoLongerExists() {
        RefreshToken refreshToken = token();
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        TokenRefreshException exception = assertThrows(TokenRefreshException.class,
                () -> refreshTokenService.refreshAccessToken("rt-1"));

        assertEquals("刷新令牌无效，请重新登录", exception.getMessage());
        assertTrue(refreshToken.isRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    private void assertRefreshRejected(User user) {
        RefreshToken refreshToken = token();
        when(refreshTokenRepository.findByToken("rt-1")).thenReturn(Optional.of(refreshToken));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        TokenRefreshException exception = assertThrows(TokenRefreshException.class,
                () -> refreshTokenService.refreshAccessToken("rt-1"));

        assertEquals("刷新令牌无效，请重新登录", exception.getMessage());
        assertTrue(refreshToken.isRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    private RefreshToken token() {
        RefreshToken token = new RefreshToken();
        token.setToken("rt-1");
        token.setUserId(1L);
        token.setUsername("editor");
        token.setExpiryDate(LocalDateTime.now().plusDays(1));
        token.setRevoked(false);
        return token;
    }

    private User user(CommonStatus status, boolean locked) {
        User user = new User();
        user.setId(1L);
        user.setUsername("editor");
        user.setRole(User.Role.EDITOR);
        user.setStatus(status);
        user.setIsLocked(locked);
        return user;
    }
}
