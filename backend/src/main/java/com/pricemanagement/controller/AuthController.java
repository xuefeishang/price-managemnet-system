
package com.pricemanagement.controller;

import com.pricemanagement.annotation.RateLimiter;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.*;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.RefreshToken;
import com.pricemanagement.entity.User;
import com.pricemanagement.exception.TokenRefreshException;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.service.RefreshTokenService;
import com.pricemanagement.util.JwtUtil;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogHelper operationLogHelper;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/login")
    @RateLimiter(time = 60, count = 5, limitType = RateLimiter.LimitType.IP,
            message = "登录尝试次数过多，请1分钟后再试")
    public Result<?> login(@Validated @RequestBody LoginRequest loginRequest) {
        log.debug("Attempting login for user: {}", loginRequest.getUsername());

        try {
            Optional<User> userOptional = userRepository.findByUsername(loginRequest.getUsername());
            if (userOptional.isEmpty()) {
                log.debug("User not found: {}", loginRequest.getUsername());
                operationLogHelper.logError("用户认证", OperationLog.OperationType.LOGIN,
                        "用户登录失败：用户不存在", loginRequest.getUsername(), "用户不存在");
                return Result.error(401, "用户名或密码错误");
            }

            User user = userOptional.get();
            if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                log.debug("Incorrect password for user: {}", loginRequest.getUsername());
                operationLogHelper.logError("用户认证", OperationLog.OperationType.LOGIN,
                        "用户登录失败：密码错误", loginRequest.getUsername(), "密码错误");
                return Result.error(401, "用户名或密码错误");
            }

            if (user.getStatus() != CommonStatus.ACTIVE) {
                log.debug("User account is inactive: {}", loginRequest.getUsername());
                operationLogHelper.logError("用户认证", OperationLog.OperationType.LOGIN,
                        "用户登录失败：账号被禁用", loginRequest.getUsername(), "账号已被禁用");
                return Result.error(403, "账号已被禁用");
            }

            String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole().name());
            log.debug("User logged in successfully: {}", loginRequest.getUsername());

            // 创建刷新令牌
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUsername());

            // 记录登录成功日志
            operationLogHelper.logSuccess("用户认证", OperationLog.OperationType.LOGIN,
                    "用户登录成功", loginRequest.getUsername());

            LoginResponse response = new LoginResponse(token, user.getId(), user.getUsername(),
                    user.getNickname(), user.getRole().name());

            // 返回包含刷新令牌的响应
            return Result.success("登录成功", Map.of(
                    "accessToken", token,
                    "refreshToken", refreshToken.getToken(),
                    "tokenType", "Bearer",
                    "user", response
            ));
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage());
            operationLogHelper.logError("用户认证", OperationLog.OperationType.LOGIN,
                    "用户登录异常", loginRequest.getUsername(), e.getMessage());
            return Result.error(500, "登录异常：" + e.getMessage());
        }
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        log.debug("Logout request received");

        // 获取当前用户并撤销其刷新令牌
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            userRepository.findByUsername(username).ifPresent(user -> {
                refreshTokenService.revokeUserTokens(user.getId());
                log.debug("Revoked refresh tokens for user: {}", username);
            });
        }

        return Result.success("退出成功");
    }

    /**
     * 刷新访问令牌
     */
    @PostMapping("/refresh-token")
    @RateLimiter(time = 60, count = 10, limitType = RateLimiter.LimitType.IP,
            message = "令牌刷新次数过多，请1分钟后再试")
    public Result<TokenRefreshResponse> refreshToken(@Validated @RequestBody TokenRefreshRequest request) {
        try {
            String refreshToken = request.getRefreshToken();
            String newAccessToken = refreshTokenService.refreshAccessToken(refreshToken);

            TokenRefreshResponse response = TokenRefreshResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken)
                    .tokenType("Bearer")
                    .expiresIn(86400L) // 24小时
                    .build();

            return Result.success("令牌刷新成功", response);
        } catch (TokenRefreshException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return Result.error(401, e.getMessage());
        }
    }

    @GetMapping("/profile")
    public Result<User> getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.error(401, "未登录");
        }
        String username = authentication.getName();
        return Result.success("获取用户信息成功", userRepository.findByUsername(username).orElse(null));
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@Validated @RequestBody UpdateProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.error(401, "未登录");
        }

        String username = authentication.getName();
        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            return Result.error(404, "用户不存在");
        }

        User user = userOptional.get();

        // 更新可选字段
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }

        User savedUser = userRepository.save(user);
        log.debug("User profile updated: {}", username);

        // 清除密码字段返回
        savedUser.setPassword(null);
        return Result.success("更新个人信息成功", savedUser);
    }

    @PutMapping("/password")
    public Result<Void> changePassword(@Validated @RequestBody ChangePasswordRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Result.error(401, "未登录");
        }

        String username = authentication.getName();
        Optional<User> userOptional = userRepository.findByUsername(username);

        if (userOptional.isEmpty()) {
            return Result.error(404, "用户不存在");
        }

        User user = userOptional.get();

        // 验证旧密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            return Result.error(400, "旧密码错误");
        }

        // 验证新密码和确认密码是否一致
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return Result.error(400, "两次输入的新密码不一致");
        }

        // 验证新密码和旧密码是否相同
        if (request.getOldPassword().equals(request.getNewPassword())) {
            return Result.error(400, "新密码不能与旧密码相同");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.debug("User password changed: {}", username);
        return Result.success("密码修改成功");
    }

    // 注册功能（如果需要）
    @PostMapping("/register")
    public Result<User> register(@Validated @RequestBody LoginRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.getUsername())) {
            return Result.error(400, "用户名已存在");
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setRole(User.Role.VIEWER); // 默认角色为查看者
        user.setStatus(CommonStatus.ACTIVE);
        user.setNickname(registerRequest.getUsername());
        user.setEmail(registerRequest.getUsername() + "@pricemanagement.com");
        user.setPhone("");

        User savedUser = userRepository.save(user);
        log.debug("New user registered: {}", registerRequest.getUsername());

        return Result.success("注册成功", savedUser);
    }
}
