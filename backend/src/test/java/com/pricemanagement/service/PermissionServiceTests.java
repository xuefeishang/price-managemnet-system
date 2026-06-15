package com.pricemanagement.service;

import com.pricemanagement.entity.SysPermission;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.RolePermissionRepository;
import com.pricemanagement.repository.SysPermissionRepository;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTests {

    @Mock
    private SysPermissionRepository permissionRepository;
    @Mock
    private SysRoleRepository roleRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private ActiveRoleResolver activeRoleResolver;

    @Test
    void inactiveAssignedRoleDoesNotGrantPermissions() {
        when(userRoleRepository.findRoleIdsByUserId(1L)).thenReturn(List.of(9L));
        when(activeRoleResolver.activeRoleIds(List.of(9L))).thenReturn(List.of());

        assertTrue(service().getUserPermissions(1L).isEmpty());

        verify(permissionRepository, never()).findByRoleId(9L);
        verify(userRepository, never()).findById(1L);
    }

    @Test
    void activeFallbackRoleCanGrantPermissions() {
        User user = new User();
        user.setRole(User.Role.VIEWER);
        SysRole role = new SysRole();
        role.setId(3L);
        role.setRoleCode("VIEWER");
        SysPermission permission = new SysPermission();
        permission.setPermissionCode("product:view");
        when(userRoleRepository.findRoleIdsByUserId(1L)).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findByRoleCodeAndStatus("VIEWER", "ACTIVE")).thenReturn(Optional.of(role));
        when(permissionRepository.findByRoleId(3L)).thenReturn(List.of(permission));

        assertEquals(java.util.Set.of("product:view"), service().getUserPermissions(1L));
    }

    private PermissionService service() {
        return new PermissionService(permissionRepository, roleRepository, userRepository, userRoleRepository,
                rolePermissionRepository, activeRoleResolver);
    }
}
