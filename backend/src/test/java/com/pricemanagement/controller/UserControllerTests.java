package com.pricemanagement.controller;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.dto.UserCreateRequest;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.User;
import com.pricemanagement.exception.UserConflictException;
import com.pricemanagement.service.PermissionService;
import com.pricemanagement.service.UserService;
import com.pricemanagement.util.OperationLogHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.pricemanagement.config.GlobalExceptionHandler;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTests {

    @Mock
    private UserService userService;
    @Mock
    private PermissionService permissionService;
    @Mock
    private OperationLogHelper operationLogHelper;

    private UserController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setDefaultUserPassword("123456");
        controller = new UserController(userService, permissionService, operationLogHelper, securityProperties);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createUserRethrowsConflictAfterRecording409Log() {
        UserCreateRequest request = new UserCreateRequest();
        request.setUsername("existing");
        request.setPassword("Password123");
        request.setRole(User.Role.VIEWER);
        UserConflictException conflict = new UserConflictException(UserConflictException.Reason.USERNAME_EXISTS);
        when(userService.createUser(any(User.class))).thenThrow(conflict);

        assertThrows(UserConflictException.class, () -> controller.createUser(request));

        verify(operationLogHelper).logError(
                "用户管理",
                OperationLog.OperationType.CREATE,
                "创建用户失败",
                "username=existing",
                conflict.getMessage(),
                "409");
    }

    @Test
    void createUserReturnsRealHttp409ForConflict() throws Exception {
        when(userService.createUser(any(User.class)))
                .thenThrow(new UserConflictException(UserConflictException.Reason.USERNAME_EXISTS));

        mockMvc.perform(post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "existing",
                                  "password": "Password123",
                                  "role": "VIEWER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value("用户名已存在，请更换后重试"));
    }

    @Test
    void createUserReturnsRealHttp400ForInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "",
                                  "password": "",
                                  "employeeId": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("参数校验失败"));
    }

    @Test
    void updateUserRejectsPrivilegeAndPasswordFields() throws Exception {
        mockMvc.perform(put("/api/users/1")
                        .contentType("application/json")
                        .content("""
                                {
                                  "nickname": "new-name",
                                  "role": "ADMIN",
                                  "password": "Password123",
                                  "isLocked": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请求字段或格式不正确"));
    }

    @Test
    void resetPasswordReadsPasswordFromJsonBody() throws Exception {
        User user = new User();
        user.setUsername("target");
        when(userService.getUserById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/users/1/reset-password")
                        .contentType("application/json")
                        .content("""
                                {
                                  "newPassword": "Password123"
                                }
                                """))
                .andExpect(status().isOk());

        verify(userService).resetPassword(1L, "Password123");
    }

    @Test
    void resetPasswordIgnoresLegacyQueryPassword() throws Exception {
        User user = new User();
        user.setUsername("target");
        when(userService.getUserById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/users/1/reset-password")
                        .queryParam("newPassword", "LeakedPassword123")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(userService).resetPassword(1L, "123456");
    }

    @Test
    void adminEditRejectsUnknownPrivilegeField() throws Exception {
        mockMvc.perform(put("/api/users/1/admin-edit")
                        .contentType("application/json")
                        .content("""
                                {
                                  "nickname": "new-name",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
