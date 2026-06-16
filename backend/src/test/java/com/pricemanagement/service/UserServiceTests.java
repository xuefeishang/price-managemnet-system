package com.pricemanagement.service;

import com.pricemanagement.dto.AdminUserEditRequest;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.exception.UserConflictException;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;
    @Mock
    private EmployeeIdService employeeIdService;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private NotificationMiniProgramEligibilityService notificationMiniProgramEligibilityService;
    @Mock
    private ActiveRoleResolver activeRoleResolver;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder,
                passwordPolicyValidator,
                employeeIdService,
                userRoleRepository,
                notificationMiniProgramEligibilityService,
                activeRoleResolver);
    }

    @Test
    void createUserNormalizesOptionalFieldsAndFlushes() {
        User user = createUser();
        user.setUsername(" new-user ");
        user.setEmployeeId(" ");
        user.setPhone(" ");
        user.setEmail(" ");
        user.setDepartment(" 采购部 ");

        when(userRepository.existsByUsername("new-user")).thenReturn(false);
        when(employeeIdService.generateEmployeeId()).thenReturn("123456");
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(activeRoleResolver.requireActiveByCode("VIEWER")).thenReturn(role(3L, "VIEWER"));
        when(userRepository.saveAndFlush(user)).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        User saved = userService.createUser(user);

        assertEquals("new-user", saved.getUsername());
        assertEquals("123456", saved.getEmployeeId());
        assertNull(saved.getPhone());
        assertNull(saved.getEmail());
        assertEquals("采购部", saved.getDepartment());
        verify(passwordPolicyValidator).validate(user, "password");
        verify(notificationMiniProgramEligibilityService).requestRefresh(10L);
    }

    @Test
    void createUserReportsUsernameConflictBeforeSaving() {
        User user = createUser();
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        UserConflictException exception = assertThrows(UserConflictException.class,
                () -> userService.createUser(user));

        assertEquals(UserConflictException.Reason.USERNAME_EXISTS, exception.getReason());
        assertEquals("用户名已存在，请更换后重试", exception.getMessage());
        verifyNoInteractions(passwordPolicyValidator);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void createUserConvertsDatabaseEmployeeIdConflict() {
        User user = createUser();
        when(userRepository.existsByUsername("existing")).thenReturn(false);
        when(employeeIdService.generateEmployeeId()).thenReturn("123456");
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(activeRoleResolver.requireActiveByCode("VIEWER")).thenReturn(role(3L, "VIEWER"));
        when(userRepository.saveAndFlush(user)).thenThrow(new DataIntegrityViolationException(
                "duplicate",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '123456' for key 'sys_user.employee_id'")));

        UserConflictException exception = assertThrows(UserConflictException.class,
                () -> userService.createUser(user));

        assertEquals(UserConflictException.Reason.EMPLOYEE_ID_EXISTS, exception.getReason());
        assertEquals("工号已存在，请更换后重试", exception.getMessage());
        verify(notificationMiniProgramEligibilityService, never()).requestRefresh(any());
    }

    @Test
    void createUserRejectsInactiveRoleBeforeWriting() {
        User user = createUser();
        when(userRepository.existsByUsername("existing")).thenReturn(false);
        when(employeeIdService.generateEmployeeId()).thenReturn("123456");
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(activeRoleResolver.requireActiveByCode("VIEWER"))
                .thenThrow(new IllegalArgumentException("角色不存在或未启用"));

        assertThrows(IllegalArgumentException.class, () -> userService.createUser(user));

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(userRoleRepository);
    }

    @Test
    void adminEditExplicitlyClearsDepartmentId() {
        User user = createUser();
        user.setId(1L);
        user.setDeptId(8L);
        AdminUserEditRequest request = new AdminUserEditRequest();
        request.setDeptId(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        User saved = userService.adminEditUser(1L, request);

        assertNull(saved.getDeptId());
    }

    @Test
    void adminEditPasswordFailureLeavesProfileUnchanged() {
        User user = createUser();
        user.setId(1L);
        user.setNickname("old");
        AdminUserEditRequest request = new AdminUserEditRequest();
        request.setNickname("new");
        request.setNewPassword("weak");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("密码不符合策略"))
                .when(passwordPolicyValidator).validate(user, "weak");

        assertThrows(IllegalArgumentException.class, () -> userService.adminEditUser(1L, request));

        assertEquals("old", user.getNickname());
        verify(userRepository, never()).save(any(User.class));
    }

    private User createUser() {
        User user = new User();
        user.setUsername("existing");
        user.setPassword("password");
        user.setRole(User.Role.VIEWER);
        return user;
    }

    private SysRole role(Long id, String code) {
        SysRole role = new SysRole();
        role.setId(id);
        role.setRoleCode(code);
        return role;
    }
}
