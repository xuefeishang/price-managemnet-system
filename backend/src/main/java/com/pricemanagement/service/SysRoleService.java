package com.pricemanagement.service;

import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.UserRole;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;

    public List<SysRole> getAllRoles() {
        return sysRoleRepository.findAll();
    }

    public List<SysRole> getActiveRoles() {
        return sysRoleRepository.findByStatusOrderBySortOrderAsc("ACTIVE");
    }

    public Optional<SysRole> getRoleById(Long id) {
        return sysRoleRepository.findById(id);
    }

    public Optional<SysRole> getRoleByCode(String code) {
        return sysRoleRepository.findByRoleCode(code);
    }

    @Transactional
    public SysRole createRole(SysRole role) {
        if (sysRoleRepository.existsByRoleCode(role.getRoleCode())) {
            throw new IllegalArgumentException("角色编码已存在: " + role.getRoleCode());
        }
        SysRole savedRole = sysRoleRepository.save(role);
        log.info("Created role: {}", savedRole.getRoleCode());
        return savedRole;
    }

    @Transactional
    public SysRole updateRole(Long id, SysRole role) {
        SysRole existingRole = sysRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));

        if (role.getRoleName() != null) {
            existingRole.setRoleName(role.getRoleName());
        }
        if (role.getDescription() != null) {
            existingRole.setDescription(role.getDescription());
        }
        if (role.getSortOrder() != null) {
            existingRole.setSortOrder(role.getSortOrder());
        }
        if (role.getStatus() != null) {
            existingRole.setStatus(role.getStatus());
        }

        SysRole savedRole = sysRoleRepository.save(existingRole);
        log.info("Updated role: {}", savedRole.getRoleCode());
        return savedRole;
    }

    @Transactional
    public void deleteRole(Long id) {
        SysRole role = sysRoleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + id));

        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new IllegalArgumentException("系统内置角色不能删除: " + role.getRoleCode());
        }

        // 检查是否有用户使用该角色
        long userCount = userRoleRepository.findByRoleId(id).size();
        if (userCount > 0) {
            throw new IllegalArgumentException("该角色已被 " + userCount + " 个用户使用，请先移除用户角色后再删除");
        }

        sysRoleRepository.deleteById(id);
        log.info("Deleted role: {}", role.getRoleCode());
    }

    /**
     * 获取用户的角色ID列表
     */
    public List<Long> getUserRoleIds(Long userId) {
        return userRoleRepository.findRoleIdsByUserId(userId);
    }

    /**
     * 为用户分配角色
     */
    @Transactional
    public void assignRolesToUser(Long userId, List<Long> roleIds) {
        // 先删除现有角色
        userRoleRepository.deleteByUserId(userId);

        // 批量分配新角色
        if (!roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                    .map(roleId -> {
                        UserRole userRole = new UserRole();
                        userRole.setUserId(userId);
                        userRole.setRoleId(roleId);
                        return userRole;
                    })
                    .toList();
            userRoleRepository.saveAll(userRoles);
        }
        log.info("Assigned roles {} to user {}", roleIds, userId);
    }
}