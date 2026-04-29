
package com.pricemanagement.controller;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.ChangePasswordRequest;
import com.pricemanagement.dto.LoginRequest;
import com.pricemanagement.dto.LoginResponse;
import com.pricemanagement.dto.Result;
import com.pricemanagement.dto.UpdateProfileRequest;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.util.JwtUtil;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    public Result<LoginResponse> login(@Validated @RequestBody LoginRequest loginRequest) {
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

            // 记录登录成功日志
            operationLogHelper.logSuccess("用户认证", OperationLog.OperationType.LOGIN,
                    "用户登录成功", loginRequest.getUsername());

            LoginResponse response = new LoginResponse(token, user.getId(), user.getUsername(),
                    user.getNickname(), user.getRole().name());

            return Result.success("登录成功", response);
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
        // JWT令牌是无状态的，此处可以记录日志或做一些清理工作
        return Result.success("退出成功");
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
