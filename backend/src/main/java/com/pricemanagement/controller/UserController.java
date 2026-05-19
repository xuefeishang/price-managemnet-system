
package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.User;
import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.service.PermissionService;
import com.pricemanagement.service.UserService;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;
    private final OperationLogHelper operationLogHelper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    /**
     * 获取用户列表（分页，仅管理员）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<User>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deptId) {
        Page<User> users = userService.getUsers(page, size, keyword, role, status, deptId);
        users.forEach(u -> u.setPassword(null));
        return Result.success("获取用户列表成功", users);
    }

    /**
     * 获取所有用户列表（不分页，用于下拉选择）
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> getAllUsers() {
        List<User> users = userService.getAllUsers();
        users.forEach(u -> u.setPassword(null));
        return Result.success("获取用户列表成功", users);
    }

    /**
     * 获取用户详情（仅管理员）
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> getUser(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> {
                    user.setPassword(null);
                    return Result.success("获取用户成功", user);
                })
                .orElse(Result.error(404, "用户不存在"));
    }

    /**
     * 获取用户的权限列表
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Set<String>> getUserPermissions(@PathVariable Long id) {
        Set<String> permissions = permissionService.getUserPermissions(id);
        return Result.success("获取用户权限成功", permissions);
    }

    /**
     * 获取用户的角色ID列表
     */
    @GetMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Long>> getUserRoles(@PathVariable Long id) {
        List<Long> roleIds = permissionService.getUserRoleIds(id);
        return Result.success("获取用户角色成功", roleIds);
    }

    /**
     * 批量获取多个用户的角色ID映射
     */
    @GetMapping("/roles-batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<Long, List<Long>>> getUserRolesBatch(@RequestParam List<Long> ids) {
        Map<Long, List<Long>> rolesMap = permissionService.getUserRoleIdsBatch(ids);
        return Result.success("获取用户角色成功", rolesMap);
    }

    /**
     * 创建用户（仅管理员）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> createUser(@RequestBody User user) {
        try {
            User savedUser = userService.createUser(user);
            savedUser.setPassword(null);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.CREATE,
                    "创建用户：" + savedUser.getUsername(), "用户ID：" + savedUser.getId() + "，工号：" + savedUser.getEmployeeId());
            return Result.success("创建用户成功", savedUser);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("用户管理", OperationLog.OperationType.CREATE,
                    "创建用户失败", user.getUsername(), e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新用户（仅管理员）
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        try {
            User updatedUser = userService.updateUser(id, user);
            updatedUser.setPassword(null);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.UPDATE,
                    "更新用户：" + updatedUser.getUsername(), "用户ID：" + id);
            return Result.success("更新用户成功", updatedUser);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("用户管理", OperationLog.OperationType.UPDATE,
                    "更新用户失败", "用户ID：" + id, e.getMessage());
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 删除用户（仅管理员）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        try {
            // 获取用户信息用于日志
            String username = userService.getUserById(id).map(User::getUsername).orElse("ID:" + id);
            userService.deleteUser(id);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.DELETE,
                    "删除用户：" + username, "用户ID：" + id);
            return Result.success("删除用户成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("用户管理", OperationLog.OperationType.DELETE,
                    "删除用户失败", "用户ID：" + id, e.getMessage());
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 重置用户密码（仅管理员）
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam(required = false) String newPassword) {
        try {
            User user = userService.getUserById(id)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            // 使用配置的默认密码或传入的密码
            String password = newPassword != null && !newPassword.isEmpty()
                    ? newPassword
                    : securityProperties.getDefaultUserPassword();
            user.setPassword(passwordEncoder.encode(password));
            userService.updateUser(id, user);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.UPDATE,
                    "重置用户密码：" + user.getUsername(), "用户ID：" + id);
            return Result.success("密码重置成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 锁定用户（仅管理员）
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> lockUser(@PathVariable Long id) {
        try {
            userService.lockUser(id);
            User user = userService.getUserById(id).orElse(null);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.UPDATE,
                    "锁定用户：" + (user != null ? user.getUsername() : "ID:" + id), "用户ID：" + id);
            return Result.success("用户已锁定");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    /**
     * 解锁用户（仅管理员）
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> unlockUser(@PathVariable Long id) {
        try {
            userService.unlockUser(id);
            User user = userService.getUserById(id).orElse(null);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.UPDATE,
                    "解锁用户：" + (user != null ? user.getUsername() : "ID:" + id), "用户ID：" + id);
            return Result.success("用户已解锁");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
