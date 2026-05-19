
package com.pricemanagement.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.pricemanagement.dto.UserExcelData;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.entity.UserRole;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class UserExcelListener extends AnalysisEventListener<UserExcelData> {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;
    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;
    private final List<String> errors = new ArrayList<>();
    private int successCount = 0;
    private int skipCount = 0;

    public UserExcelListener(UserRepository userRepository, PasswordEncoder passwordEncoder, String defaultPassword,
                             SysRoleRepository sysRoleRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
        this.sysRoleRepository = sysRoleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public void invoke(UserExcelData data, AnalysisContext context) {
        int rowNum = context.readRowHolder().getRowIndex() + 1;

        // 验证必填字段
        if (data.getUsername() == null || data.getUsername().isBlank()) {
            errors.add("第" + rowNum + "行: 用户名不能为空");
            skipCount++;
            return;
        }

        // 检查用户名是否已存在
        if (userRepository.findByUsername(data.getUsername()).isPresent()) {
            errors.add("第" + rowNum + "行: 用户名 " + data.getUsername() + " 已存在");
            skipCount++;
            return;
        }

        // 检查工号是否已存在
        if (data.getEmployeeId() != null && !data.getEmployeeId().isBlank()) {
            if (userRepository.findByEmployeeId(data.getEmployeeId()).isPresent()) {
                errors.add("第" + rowNum + "行: 工号 " + data.getEmployeeId() + " 已存在");
                skipCount++;
                return;
            }
        }

        // 创建用户
        User user = new User();
        user.setUsername(data.getUsername().trim());
        user.setEmployeeId(data.getEmployeeId() != null && !data.getEmployeeId().isBlank()
            ? data.getEmployeeId().trim() : null);
        user.setNickname(data.getNickname() != null && !data.getNickname().isBlank()
            ? data.getNickname().trim() : data.getUsername().trim());
        user.setEmail(data.getEmail());
        user.setPhone(data.getPhone());
        user.setDepartment(data.getDepartment());

        // 解析角色
        try {
            user.setRole(data.getRole() != null && !data.getRole().isBlank()
                ? User.Role.valueOf(data.getRole().trim().toUpperCase())
                : User.Role.VIEWER);
        } catch (IllegalArgumentException e) {
            errors.add("第" + rowNum + "行: 无效的角色 " + data.getRole() + ", 使用默认角色 VIEWER");
            user.setRole(User.Role.VIEWER);
        }

        // 解析状态
        try {
            user.setStatus(data.getStatus() != null && !data.getStatus().isBlank()
                ? com.pricemanagement.constants.CommonStatus.valueOf(data.getStatus().trim().toUpperCase())
                : com.pricemanagement.constants.CommonStatus.ACTIVE);
        } catch (IllegalArgumentException e) {
            user.setStatus(com.pricemanagement.constants.CommonStatus.ACTIVE);
        }

        // 设置密码
        String rawPassword = data.getPassword() != null && !data.getPassword().isBlank()
            ? data.getPassword()
            : defaultPassword;
        user.setPassword(passwordEncoder.encode(rawPassword));

        userRepository.save(user);
        successCount++;

        // 同步写入sys_user_role表
        if (user.getRole() != null) {
            Optional<SysRole> roleOpt = sysRoleRepository.findByRoleCode(user.getRole().name());
            if (roleOpt.isPresent()) {
                UserRole userRole = new UserRole();
                userRole.setUserId(user.getId());
                userRole.setRoleId(roleOpt.get().getId());
                userRoleRepository.save(userRole);
                log.debug("Assigned role {} to imported user {}", user.getRole().name(), user.getUsername());
            }
        }

        log.debug("导入用户: {}", user.getUsername());
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("用户导入完成: 成功 {}, 跳过 {}", successCount, skipCount);
        if (!errors.isEmpty()) {
            log.warn("导入警告: {}", errors);
        }
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getSkipCount() {
        return skipCount;
    }

    public List<String> getErrors() {
        return errors;
    }
}
