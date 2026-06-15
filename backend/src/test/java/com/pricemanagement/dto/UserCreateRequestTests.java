package com.pricemanagement.dto;

import com.pricemanagement.entity.User;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserCreateRequestTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesRequiredFieldsAndEmployeeIdFormat() {
        UserCreateRequest request = new UserCreateRequest();
        request.setEmployeeId("123");

        var violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("role")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("employeeId")));
    }

    @Test
    void mapsOnlyAllowedCreateFields() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("new-user");
        request.setPassword("Password123");
        request.setRole(User.Role.VIEWER);
        request.setNickname("新用户");

        assertFalse(validator.validate(request).stream().findAny().isPresent());

        User user = request.toUser();

        assertEquals("new-user", user.getUsername());
        assertEquals(User.Role.VIEWER, user.getRole());
        assertNull(user.getWechatOpenid());
        assertFalse(user.getIsLocked());
        assertEquals("PASSWORD", user.getLoginType());
    }
}
