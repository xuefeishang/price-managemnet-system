package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.ValidatedUserImportRow;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserImportWriteServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private NotificationMiniProgramEligibilityService eligibilityService;
    @Mock
    private ActiveRoleResolver activeRoleResolver;

    @Test
    void writesUserRoleAndRefreshesEligibility() {
        User saved = new User();
        saved.setId(10L);
        when(userRepository.saveAllAndFlush(any())).thenReturn(List.of(saved));
        when(activeRoleResolver.requireAllActiveByIds(anySet())).thenReturn(List.of(role()));
        UserImportWriteService service = new UserImportWriteService(
                userRepository, userRoleRepository, eligibilityService, activeRoleResolver);

        int imported = service.importValidatedRows(List.of(row()));

        assertEquals(1, imported);
        verify(userRoleRepository).saveAllAndFlush(any());
        verify(eligibilityService).requestRefresh(10L);
    }

    @Test
    void stopsBeforeWritingWhenConcurrentUsernameConflictExists() {
        User existing = new User();
        existing.setUsername("new-user");
        when(userRepository.findByUsernameIn(anySet())).thenReturn(List.of(existing));
        UserImportWriteService service = new UserImportWriteService(
                userRepository, userRoleRepository, eligibilityService, activeRoleResolver);

        assertThrows(RuntimeException.class, () -> service.importValidatedRows(List.of(row())));

        verify(userRepository, never()).saveAllAndFlush(any());
        verify(userRoleRepository, never()).saveAllAndFlush(any());
    }

    private ValidatedUserImportRow row() {
        return new ValidatedUserImportRow(2, "new-user", "100001", "New User", null, null, null,
                User.Role.VIEWER, CommonStatus.ACTIVE, "encoded", 3L);
    }

    private SysRole role() {
        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("VIEWER");
        return role;
    }
}
