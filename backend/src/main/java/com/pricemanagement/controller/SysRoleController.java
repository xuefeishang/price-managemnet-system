package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.service.PermissionService;
import com.pricemanagement.service.SysRoleService;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService sysRoleService;
    private final PermissionService permissionService;
    private final OperationLogHelper operationLogHelper;

    /**
     * 获取所有角色列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysRole>> getRoles() {
        List<SysRole> roles = sysRoleService.getAllRoles();
        return Result.success("获取角色列表成功", roles);
    }

    /**
     * 获取启用的角色列表
     */
    @GetMapping("/active")
    public Result<List<SysRole>> getActiveRoles() {
        List<SysRole> roles = sysRoleService.getActiveRoles();
        return Result.success("获取角色列表成功", roles);
    }

    /**
     * 获取角色详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysRole> getRole(@PathVariable Long id) {
        return sysRoleService.getRoleById(id)
                .map(role -> Result.success("获取角色成功", role))
                .orElse(Result.error(404, "角色不存在"));
    }

    /**
     * 获取角色的权限ID列表
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Long>> getRolePermissionIds(@PathVariable Long id) {
        List<Long> permissionIds = permissionService.getRolePermissionIds(id);
        return Result.success("获取角色权限成功", permissionIds);
    }

    /**
     * 为角色分配权限
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        try {
            permissionService.assignPermissionsToRole(id, permissionIds);
            operationLogHelper.logSuccess("角色管理", OperationLog.OperationType.UPDATE,
                    "分配角色权限", "角色ID：" + id + "，权限数量：" + permissionIds.size());
            return Result.success("分配权限成功");
        } catch (Exception e) {
            operationLogHelper.logError("角色管理", OperationLog.OperationType.UPDATE,
                    "分配角色权限失败", "角色ID：" + id, e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }

    /**
     * 创建角色
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysRole> createRole(@RequestBody SysRole role) {
        try {
            SysRole savedRole = sysRoleService.createRole(role);
            operationLogHelper.logSuccess("角色管理", OperationLog.OperationType.CREATE,
                    "创建角色：" + savedRole.getRoleName(), "角色编码：" + savedRole.getRoleCode());
            return Result.success("创建角色成功", savedRole);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("角色管理", OperationLog.OperationType.CREATE,
                    "创建角色失败", role.getRoleCode(), e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新角色
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysRole> updateRole(@PathVariable Long id, @RequestBody SysRole role) {
        try {
            SysRole updatedRole = sysRoleService.updateRole(id, role);
            operationLogHelper.logSuccess("角色管理", OperationLog.OperationType.UPDATE,
                    "更新角色：" + updatedRole.getRoleName(), "角色ID：" + id);
            return Result.success("更新角色成功", updatedRole);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("角色管理", OperationLog.OperationType.UPDATE,
                    "更新角色失败", "角色ID：" + id, e.getMessage());
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteRole(@PathVariable Long id) {
        try {
            String roleName = sysRoleService.getRoleById(id).map(SysRole::getRoleName).orElse("ID:" + id);
            sysRoleService.deleteRole(id);
            operationLogHelper.logSuccess("角色管理", OperationLog.OperationType.DELETE,
                    "删除角色：" + roleName, "角色ID：" + id);
            return Result.success("删除角色成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("角色管理", OperationLog.OperationType.DELETE,
                    "删除角色失败", "角色ID：" + id, e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 为用户分配角色
     */
    @PostMapping("/assign/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        try {
            sysRoleService.assignRolesToUser(userId, roleIds);
            operationLogHelper.logSuccess("角色管理", OperationLog.OperationType.UPDATE,
                    "分配角色", "用户ID：" + userId + "，角色ID：" + roleIds);
            return Result.success("分配角色成功");
        } catch (Exception e) {
            operationLogHelper.logError("角色管理", OperationLog.OperationType.UPDATE,
                    "分配角色失败", "用户ID：" + userId, e.getMessage());
            return Result.error(500, e.getMessage());
        }
    }
}