package com.pricemanagement.service;

import com.pricemanagement.config.properties.ImportProperties;
import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.dto.UserExcelData;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.listener.UserExcelValidationListener;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserImportValidationServiceTests {

    private static final Map<Integer, String> HEADERS = new LinkedHashMap<>();

    static {
        HEADERS.put(0, "用户名");
        HEADERS.put(1, "工号");
        HEADERS.put(2, "昵称");
        HEADERS.put(3, "邮箱");
        HEADERS.put(4, "手机号");
        HEADERS.put(5, "部门");
        HEADERS.put(6, "角色(ADMIN/EDITOR/VIEWER)");
        HEADERS.put(7, "状态(ACTIVE/INACTIVE)");
        HEADERS.put(8, "初始密码");
    }

    @Mock
    private UserRepository userRepository;
    @Mock
    private SysRoleRepository sysRoleRepository;
    @Mock
    private EmployeeIdService employeeIdService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserExcelValidationListener listener;

    private UserImportValidationService service;

    @BeforeEach
    void setUp() {
        ImportProperties importProperties = new ImportProperties();
        SecurityProperties securityProperties = new SecurityProperties();
        securityProperties.setDefaultUserPassword("Password123");
        service = new UserImportValidationService(importProperties, securityProperties, userRepository, sysRoleRepository,
                employeeIdService, new PasswordPolicyValidator(securityProperties), passwordEncoder);

        SysRole viewer = new SysRole();
        viewer.setId(3L);
        viewer.setRoleCode("VIEWER");
        when(sysRoleRepository.findByStatus("ACTIVE")).thenReturn(List.of(viewer));
        when(listener.getHeaders()).thenReturn(HEADERS);
    }

    @Test
    void rejectsDuplicateUsernameBeforeEncodingOrWriting() {
        when(listener.getRows()).thenReturn(List.of(row(2, "same-user", "100001"), row(3, "SAME-USER", "100002")));
        when(employeeIdService.isValidEmployeeId("100001")).thenReturn(true);
        when(employeeIdService.isValidEmployeeId("100002")).thenReturn(true);

        UserImportValidationService.ValidationOutcome outcome = service.validate(listener);

        assertFalse(outcome.result().valid());
        assertEquals(2, outcome.result().errors().stream()
                .filter(error -> error.code().equals("DUPLICATE_USERNAME_IN_FILE")).count());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsUsernameAlreadyInDatabase() {
        User existing = new User();
        existing.setUsername("Existing");
        when(listener.getRows()).thenReturn(List.of(row(2, "existing", "100001")));
        when(employeeIdService.isValidEmployeeId("100001")).thenReturn(true);
        when(userRepository.findByUsernameIn(anySet())).thenReturn(List.of(existing));

        UserImportValidationService.ValidationOutcome outcome = service.validate(listener);

        assertTrue(outcome.result().errors().stream()
                .anyMatch(error -> error.code().equals("USERNAME_ALREADY_EXISTS")));
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void returnsEncodedValidatedRowsWhenAllChecksPass() {
        when(listener.getRows()).thenReturn(List.of(row(2, "new-user", "100001")));
        when(employeeIdService.isValidEmployeeId("100001")).thenReturn(true);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");

        UserImportValidationService.ValidationOutcome outcome = service.validate(listener);

        assertTrue(outcome.result().valid());
        assertEquals(1, outcome.rows().size());
        assertEquals("encoded", outcome.rows().getFirst().encodedPassword());
        verify(userRepository).findByUsernameIn(anySet());
        verify(userRepository).findByEmployeeIdIn(anySet());
    }

    @Test
    void invalidRowsStillParticipateInDuplicateChecks() {
        UserExcelValidationListener.RowData first = row(2, "same-user", "100001");
        first.data().setEmail("invalid-email");
        UserExcelValidationListener.RowData second = row(3, "SAME-USER", "100002");
        when(listener.getRows()).thenReturn(List.of(first, second));
        when(employeeIdService.isValidEmployeeId("100001")).thenReturn(true);
        when(employeeIdService.isValidEmployeeId("100002")).thenReturn(true);

        UserImportValidationService.ValidationOutcome outcome = service.validate(listener);

        assertTrue(outcome.result().errors().stream().anyMatch(error -> error.code().equals("INVALID_FORMAT")));
        assertEquals(2, outcome.result().errors().stream()
                .filter(error -> error.code().equals("DUPLICATE_USERNAME_IN_FILE")).count());
    }

    @Test
    void missingEmployeeIdsAreGeneratedInOneBatch() {
        when(listener.getRows()).thenReturn(List.of(row(2, "first", null), row(3, "second", null)));
        when(employeeIdService.generateEmployeeIds(anyInt(), anyCollection()))
                .thenReturn(List.of("100001", "100002"));
        when(employeeIdService.isValidEmployeeId("100001")).thenReturn(true);
        when(employeeIdService.isValidEmployeeId("100002")).thenReturn(true);
        when(passwordEncoder.encode("Password123")).thenReturn("encoded");

        UserImportValidationService.ValidationOutcome outcome = service.validate(listener);

        assertTrue(outcome.result().valid());
        verify(employeeIdService).generateEmployeeIds(2, java.util.Set.of());
        verify(employeeIdService, never()).generateEmployeeId();
        verify(userRepository, never()).existsByEmployeeId(org.mockito.ArgumentMatchers.anyString());
    }

    private UserExcelValidationListener.RowData row(int rowNumber, String username, String employeeId) {
        UserExcelData data = new UserExcelData();
        data.setUsername(username);
        data.setEmployeeId(employeeId);
        data.setRole("VIEWER");
        data.setStatus("ACTIVE");
        data.setPassword("Password123");
        return new UserExcelValidationListener.RowData(rowNumber, data);
    }
}
