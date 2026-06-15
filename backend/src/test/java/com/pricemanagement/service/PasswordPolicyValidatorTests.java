package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyValidatorTests {

    private PasswordPolicyValidator validator;
    private User user;

    @BeforeEach
    void setUp() {
        validator = new PasswordPolicyValidator(new SecurityProperties());
        user = new User();
        user.setUsername("test-user");
        user.setNickname("测试用户");
        user.setPhone("13800138000");
    }

    @Test
    void acceptsPasswordMatchingConfiguredPolicy() {
        assertDoesNotThrow(() -> validator.validate(user, "Password123"));
    }

    @Test
    void rejectsWeakOrAccountBasedPasswords() {
        assertThrows(IllegalArgumentException.class, () -> validator.validate(user, "short"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(user, "passwordonly"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(user, "123456789"));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(user, "Pass word123"));
        user.setUsername("Password123");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(user, "Password123"));
        user.setUsername("test-user");
        user.setNickname("Nickname123");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(user, "Nickname123"));
        user.setNickname("测试用户");
        user.setPhone("13800138000A");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(user, "13800138000A"));
    }
}
