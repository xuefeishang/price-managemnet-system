
package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.User;
import com.pricemanagement.service.UserService;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final OperationLogHelper operationLogHelper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取所有用户列表（仅管理员）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<User>> getUsers() {
        List<User> users = userService.getAllUsers();
        // 清除密码字段
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
     * 创建用户（仅管理员）
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<User> createUser(@RequestBody User user) {
        try {
            User savedUser = userService.createUser(user);
            savedUser.setPassword(null);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.CREATE,
                    "创建用户：" + savedUser.getUsername(), "用户ID：" + savedUser.getId());
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
    public Result<Void> resetPassword(@PathVariable Long id, @RequestParam(defaultValue = "123456") String newPassword) {
        try {
            User user = userService.getUserById(id)
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            user.setPassword(passwordEncoder.encode(newPassword));
            userService.updateUser(id, user);
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.UPDATE,
                    "重置用户密码：" + user.getUsername(), "用户ID：" + id);
            return Result.success("密码重置成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
