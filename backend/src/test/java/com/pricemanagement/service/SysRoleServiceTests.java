package com.pricemanagement.service;

import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysRoleServiceTests {

    @Mock
    private SysRoleRepository sysRoleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActiveRoleResolver activeRoleResolver;

    @Test
    void invalidRoleAssignmentKeepsExistingRoleRelations() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(activeRoleResolver.requireAllActiveByIds(List.of(9L)))
                .thenThrow(new IllegalArgumentException("包含不存在或未启用的角色"));
        SysRoleService service = new SysRoleService(
                sysRoleRepository, userRoleRepository, userRepository, activeRoleResolver);

        assertThrows(IllegalArgumentException.class, () -> service.assignRolesToUser(1L, List.of(9L)));

        verify(userRoleRepository, never()).deleteByUserId(1L);
    }

    @Test
    void emptyRoleAssignmentKeepsExistingRoleRelations() {
        User user = new User();
        user.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        SysRoleService service = new SysRoleService(
                sysRoleRepository, userRoleRepository, userRepository, activeRoleResolver);

        assertThrows(IllegalArgumentException.class, () -> service.assignRolesToUser(1L, List.of()));

        verify(userRoleRepository, never()).deleteByUserId(1L);
    }
}
