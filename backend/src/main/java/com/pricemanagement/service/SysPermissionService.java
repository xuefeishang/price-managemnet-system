package com.pricemanagement.service;

import com.pricemanagement.entity.RolePermission;
import com.pricemanagement.entity.SysPermission;
import com.pricemanagement.repository.RolePermissionRepository;
import com.pricemanagement.repository.SysPermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysPermissionService {

    private final SysPermissionRepository sysPermissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public List<SysPermission> getAllPermissions() {
        return sysPermissionRepository.findAll();
    }

    public List<SysPermission> getPermissionsByType(String type) {
        return sysPermissionRepository.findByPermissionType(type);
    }

    public Optional<SysPermission> getPermissionById(Long id) {
        return sysPermissionRepository.findById(id);
    }

    public Optional<SysPermission> getPermissionByCode(String code) {
        return sysPermissionRepository.findByPermissionCode(code);
    }

    @Transactional
    public SysPermission createPermission(SysPermission permission) {
        Optional<SysPermission> existing = sysPermissionRepository.findByPermissionCode(permission.getPermissionCode());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("权限编码已存在: " + permission.getPermissionCode());
        }
        SysPermission savedPermission = sysPermissionRepository.save(permission);
        log.info("Created permission: {}", savedPermission.getPermissionCode());
        return savedPermission;
    }

    @Transactional
    public SysPermission updatePermission(Long id, SysPermission permission) {
        SysPermission existingPermission = sysPermissionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在: " + id));

        if (permission.getPermissionName() != null) {
            existingPermission.setPermissionName(permission.getPermissionName());
        }
        if (permission.getPermissionType() != null) {
            existingPermission.setPermissionType(permission.getPermissionType());
        }
        if (permission.getResourceUrl() != null) {
            existingPermission.setResourceUrl(permission.getResourceUrl());
        }
        if (permission.getIcon() != null) {
            existingPermission.setIcon(permission.getIcon());
        }
        if (permission.getSortOrder() != null) {
            existingPermission.setSortOrder(permission.getSortOrder());
        }
        if (permission.getStatus() != null) {
            existingPermission.setStatus(permission.getStatus());
        }

        SysPermission savedPermission = sysPermissionRepository.save(existingPermission);
        log.info("Updated permission: {}", savedPermission.getPermissionCode());
        return savedPermission;
    }

    @Transactional
    public void deletePermission(Long id) {
        sysPermissionRepository.deleteById(id);
        log.info("Deleted permission with id: {}", id);
    }

    /**
     * 获取角色的权限ID列表
     */
    public List<Long> getRolePermissionIds(Long roleId) {
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId);
    }

    /**
     * 为角色分配权限
     */
    @Transactional
    public void assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        // 先删除现有权限
        List<RolePermission> existing = rolePermissionRepository.findByRoleId(roleId);
        rolePermissionRepository.deleteAll(existing);

        // 分配新权限
        for (Long permissionId : permissionIds) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionId(permissionId);
            rolePermissionRepository.save(rolePermission);
        }
        log.info("Assigned permissions {} to role {}", permissionIds, roleId);
    }

    /**
     * 获取用户的所有权限编码
     */
    public List<String> getUserPermissionCodes(Long userId) {
        // 获取用户角色ID列表
        List<Long> roleIds = rolePermissionRepository.findPermissionIdsByRoleId(userId);
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取角色对应的权限编码
        return rolePermissionRepository.findPermissionCodesByRoleIds(roleIds);
    }
}