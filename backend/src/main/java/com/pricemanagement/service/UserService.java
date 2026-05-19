
package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.entity.UserRole;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeIdService employeeIdService;
    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 分页获取用户列表
     */
    public Page<User> getUsers(int page, int size, String keyword, String role, String status, Long deptId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime"));

        List<User> allUsers = userRepository.findAll();

        // 过滤
        List<User> filtered = allUsers.stream()
            .filter(u -> keyword == null || keyword.isBlank() ||
                u.getUsername().contains(keyword) ||
                (u.getNickname() != null && u.getNickname().contains(keyword)) ||
                (u.getEmployeeId() != null && u.getEmployeeId().contains(keyword)))
            .filter(u -> role == null || role.isBlank() || u.getRole().name().equals(role))
            .filter(u -> status == null || status.isBlank() || u.getStatus().name().equals(status))
            .filter(u -> deptId == null || deptId.equals(u.getDeptId()))
            .toList();

        // 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<User> pageContent = start < filtered.size() ? filtered.subList(start, end) : List.of();

        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
    }

    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public User createUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new IllegalArgumentException("用户名已存在: " + user.getUsername());
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 自动生成工号（如果未提供）
        if (user.getEmployeeId() == null || user.getEmployeeId().isEmpty()) {
            user.setEmployeeId(employeeIdService.generateEmployeeId());
        } else {
            // 验证工号格式
            if (!employeeIdService.isValidEmployeeId(user.getEmployeeId())) {
                throw new IllegalArgumentException("工号格式错误，应为6位数字");
            }
            if (userRepository.existsByEmployeeId(user.getEmployeeId())) {
                throw new IllegalArgumentException("工号已存在: " + user.getEmployeeId());
            }
        }

        User savedUser = userRepository.save(user);

        // 同步写入sys_user_role表（根据role枚举找到对应角色）
        if (user.getRole() != null) {
            Optional<SysRole> defaultRoleOpt = sysRoleRepository.findByRoleCode(user.getRole().name());
            if (defaultRoleOpt.isPresent()) {
                UserRole userRole = new UserRole();
                userRole.setUserId(savedUser.getId());
                userRole.setRoleId(defaultRoleOpt.get().getId());
                userRoleRepository.save(userRole);
                log.info("Assigned default role {} to user {}", user.getRole().name(), savedUser.getUsername());
            }
        }

        log.info("Created user: {} with employee ID: {}", savedUser.getUsername(), savedUser.getEmployeeId());
        return savedUser;
    }

    @Transactional
    public User updateUser(Long id, User user) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        if (user.getNickname() != null) {
            existingUser.setNickname(user.getNickname());
        }
        if (user.getEmail() != null) {
            existingUser.setEmail(user.getEmail());
        }
        if (user.getPhone() != null) {
            existingUser.setPhone(user.getPhone());
        }
        if (user.getRole() != null) {
            existingUser.setRole(user.getRole());

            // 同步更新sys_user_role表
            userRoleRepository.deleteByUserId(id);
            Optional<SysRole> roleOpt = sysRoleRepository.findByRoleCode(user.getRole().name());
            if (roleOpt.isPresent()) {
                UserRole userRole = new UserRole();
                userRole.setUserId(id);
                userRole.setRoleId(roleOpt.get().getId());
                userRoleRepository.save(userRole);
                log.info("Synced role {} to user {}", user.getRole().name(), existingUser.getUsername());
            }
        }
        if (user.getStatus() != null) {
            existingUser.setStatus(user.getStatus());
        }
        if (user.getDepartment() != null) {
            existingUser.setDepartment(user.getDepartment());
        }
        if (user.getDeptId() != null) {
            existingUser.setDeptId(user.getDeptId());
        }
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        if (user.getIsLocked() != null) {
            existingUser.setIsLocked(user.getIsLocked());
        }

        User savedUser = userRepository.save(existingUser);
        log.info("Updated user: {}", savedUser.getUsername());
        return savedUser;
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("用户不存在: " + id);
        }
        // 先删除用户角色关联
        userRoleRepository.deleteByUserId(id);
        userRepository.deleteById(id);
        log.info("Deleted user with id: {}", id);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 锁定用户
     */
    @Transactional
    public void lockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        user.setIsLocked(true);
        user.setLockedTime(java.time.LocalDateTime.now());
        userRepository.save(user);
        log.info("Locked user: {}", user.getUsername());
    }

    /**
     * 解锁用户
     */
    @Transactional
    public void unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));
        user.setIsLocked(false);
        user.setLockedTime(null);
        userRepository.save(user);
        log.info("Unlocked user: {}", user.getUsername());
    }
}

