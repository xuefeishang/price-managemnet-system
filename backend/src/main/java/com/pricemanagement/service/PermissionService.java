package com.pricemanagement.service;

import com.pricemanagement.entity.SysPermission;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.RolePermission;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.SysPermissionRepository;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import com.pricemanagement.repository.RolePermissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final SysPermissionRepository permissionRepository;
    private final SysRoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * 获取所有权限列表
     */
    public List<SysPermission> getAllPermissions() {
        return permissionRepository.findAllByOrderBySortOrderAsc();
    }

    /**
     * 获取启用的权限列表
     */
    public List<SysPermission> getActivePermissions() {
        return permissionRepository.findByStatusOrderBySortOrderAsc("ACTIVE");
    }

    /**
     * 获取权限树（按层级组织）
     */
    public List<SysPermission> getPermissionTree() {
        List<SysPermission> allPermissions = getActivePermissions();
        return buildPermissionTree(allPermissions, null);
    }

    private List<SysPermission> buildPermissionTree(List<SysPermission> all, Long parentId) {
        List<SysPermission> children = all.stream()
                .filter(p -> Objects.equals(p.getParentId(), parentId))
                .collect(Collectors.toList());
        // 递归构建子节点
        for (SysPermission child : children) {
            List<SysPermission> grandChildren = buildPermissionTree(all, child.getId());
            child.setChildren(grandChildren);
        }
        return children;
    }

    /**
     * 获取角色的权限ID列表
     */
    public List<Long> getRolePermissionIds(Long roleId) {
        return permissionRepository.findPermissionIdsByRoleId(roleId);
    }

    /**
     * 获取角色的权限列表
     */
    public List<SysPermission> getRolePermissions(Long roleId) {
        return permissionRepository.findByRoleId(roleId);
    }

    /**
     * 获取用户的所有权限（通过角色）
     */
    public Set<String> getUserPermissions(Long userId) {
        // 1. 获取用户的所有角色ID
        List<Long> roleIds = resolveUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }

        // 2. 获取这些角色的所有权限
        Set<String> permissions = new HashSet<>();
        for (Long roleId : roleIds) {
            List<SysPermission> rolePermissions = permissionRepository.findByRoleId(roleId);
            permissions.addAll(rolePermissions.stream()
                    .map(SysPermission::getPermissionCode)
                    .collect(Collectors.toSet()));
        }

        return permissions;
    }

    /**
     * 检查用户是否有某权限
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        return getUserPermissions(userId).contains(permissionCode);
    }

    /**
     * 为角色分配权限
     */
    @Transactional
    public void assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + roleId));

        // 先删除现有权限
        permissionRepository.deleteByRoleId(roleId);

        // 批量分配新权限
        if (!permissionIds.isEmpty()) {
            List<RolePermission> rolePermissions = permissionIds.stream()
                    .map(permissionId -> {
                        RolePermission rp = new RolePermission();
                        rp.setRoleId(roleId);
                        rp.setPermissionId(permissionId);
                        return rp;
                    })
                    .collect(Collectors.toList());
            rolePermissionRepository.saveAll(rolePermissions);
        }

        log.info("Assigned {} permissions to role {}", permissionIds.size(), role.getRoleCode());
    }

    /**
     * 获取用户的角色编码列表
     */
    public List<String> getUserRoleCodes(Long userId) {
        List<Long> roleIds = resolveUserRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        return roleRepository.findAllById(roleIds).stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    private List<Long> resolveUserRoleIds(Long userId) {
        List<Long> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (!roleIds.isEmpty()) {
            return roleIds;
        }

        return userRepository.findById(userId)
                .map(User::getRole)
                .flatMap(role -> roleRepository.findByRoleCode(role.name()))
                .map(role -> List.of(role.getId()))
                .orElseGet(Collections::emptyList);
    }

    /**
     * 获取用户的角色ID列表
     */
    public List<Long> getUserRoleIds(Long userId) {
        return userRoleRepository.findRoleIdsByUserId(userId);
    }

    /**
     * 批量获取多个用户的角色ID映射
     */
    public Map<Long, List<Long>> getUserRoleIdsBatch(List<Long> userIds) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }

        // 批量查询所有用户的角色关联
        List<Object[]> userRolePairs = userRoleRepository.findUserRolePairsByUserIds(userIds);

        // 按用户ID分组
        for (Object[] pair : userRolePairs) {
            Long userId = (Long) pair[0];
            Long roleId = (Long) pair[1];
            result.computeIfAbsent(userId, k -> new ArrayList<>()).add(roleId);
        }

        // 确保所有请求的用户都有条目（即使没有角色）
        for (Long userId : userIds) {
            result.putIfAbsent(userId, Collections.emptyList());
        }

        return result;
    }
}
