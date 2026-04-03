package com.pricemanagement.config;

import com.pricemanagement.entity.User;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MenuItemService menuItemService;

    @Override
    public void run(String... args) {
        initUsers();
        initMenus();
    }

    private void initUsers() {
        initUser("admin", "admin123", User.Role.ADMIN, "管理员", "admin@pricemanagement.com", "13800138000");
        initUser("editor", "admin123", User.Role.EDITOR, "编辑员", "editor@pricemanagement.com", "13800138001");
        initUser("viewer", "admin123", User.Role.VIEWER, "查看员", "viewer@pricemanagement.com", "13800138002");
    }

    private void initUser(String username, String password, User.Role role, String nickname, String email, String phone) {
        if (!userRepository.existsByUsername(username)) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(role);
            user.setStatus(User.UserStatus.ACTIVE);
            user.setNickname(nickname);
            user.setEmail(email);
            user.setPhone(phone);
            userRepository.save(user);
            log.info("Created default user: {}", username);
        } else {
            // 用户已存在时，重置密码确保默认凭据可用
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setPassword(passwordEncoder.encode(password));
                user.setRole(role);
                user.setStatus(User.UserStatus.ACTIVE);
                userRepository.save(user);
                log.info("Reset password for default user: {}", username);
            });
        }
    }

    private void initMenus() {
        try {
            log.info("Starting menu initialization...");
            menuItemService.initializeDefaultMenus();
            log.info("Menu initialization completed");
        } catch (Exception e) {
            log.error("Menu initialization failed: {}", e.getMessage(), e);
        }
    }
}