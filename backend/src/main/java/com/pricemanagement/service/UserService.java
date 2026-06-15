
package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.entity.UserRole;
import com.pricemanagement.dto.UserUpdateRequest;
import com.pricemanagement.dto.AdminUserEditRequest;
import com.pricemanagement.exception.UserConflictException;
import com.pricemanagement.exception.UserConflictException.Reason;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import com.pricemanagement.util.DataIntegrityViolationDiagnostics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final EmployeeIdService employeeIdService;
    private final UserRoleRepository userRoleRepository;
    private final NotificationMiniProgramEligibilityService notificationMiniProgramEligibilityService;
    private final ActiveRoleResolver activeRoleResolver;

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
        user.setUsername(normalizeRequiredText(user.getUsername(), "用户名不能为空"));
        user.setEmployeeId(normalizeNullableText(user.getEmployeeId()));
        user.setNickname(normalizeNullableText(user.getNickname()));
        user.setEmail(normalizeNullableText(user.getEmail()));
        user.setDepartment(normalizeNullableText(user.getDepartment()));
        user.setWechatOpenid(normalizeNullableText(user.getWechatOpenid()));

        if (userRepository.existsByUsername(user.getUsername())) {
            throw new UserConflictException(Reason.USERNAME_EXISTS);
        }
        passwordPolicyValidator.validate(user, user.getPassword());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setPhone(normalizePhone(user.getPhone()));

        // 自动生成工号（如果未提供）
        if (user.getEmployeeId() == null || user.getEmployeeId().isEmpty()) {
            user.setEmployeeId(employeeIdService.generateEmployeeId());
        } else {
            // 验证工号格式
            if (!employeeIdService.isValidEmployeeId(user.getEmployeeId())) {
                throw new IllegalArgumentException("工号格式错误，应为6位数字");
            }
            if (userRepository.existsByEmployeeId(user.getEmployeeId())) {
                throw new UserConflictException(Reason.EMPLOYEE_ID_EXISTS);
            }
        }
        SysRole defaultRole = activeRoleResolver.requireActiveByCode(user.getRole().name());

        try {
            User savedUser = userRepository.saveAndFlush(user);

            UserRole userRole = new UserRole();
            userRole.setUserId(savedUser.getId());
            userRole.setRoleId(defaultRole.getId());
            userRoleRepository.saveAndFlush(userRole);
            log.info("Assigned default role {} to user {}", user.getRole().name(), savedUser.getUsername());

            notificationMiniProgramEligibilityService.requestRefresh(savedUser.getId());
            log.info("Created user: {} with employee ID: {}", savedUser.getUsername(), savedUser.getEmployeeId());
            return savedUser;
        } catch (DataIntegrityViolationException ex) {
            throw new UserConflictException(resolveCreateConflictReason(ex), ex);
        }
    }

    @Transactional
    public User updateUser(Long id, UserUpdateRequest request) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        if (request.isNicknamePresent()) {
            existingUser.setNickname(normalizeNullableText(request.getNickname()));
        }
        if (request.isEmailPresent()) {
            existingUser.setEmail(normalizeNullableText(request.getEmail()));
        }
        if (request.isPhonePresent()) {
            existingUser.setPhone(normalizePhone(request.getPhone()));
        }
        if (request.getStatus() != null) {
            existingUser.setStatus(request.getStatus());
        }
        if (request.isDepartmentPresent()) {
            existingUser.setDepartment(normalizeNullableText(request.getDepartment()));
        }
        if (request.isDeptIdPresent()) {
            existingUser.setDeptId(request.getDeptId());
        }

        User savedUser = userRepository.save(existingUser);
        notificationMiniProgramEligibilityService.requestRefresh(savedUser.getId());
        log.info("Updated user: {}", savedUser.getUsername());
        return savedUser;
    }

    @Transactional
    public User adminEditUser(Long id, AdminUserEditRequest request) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        String newPassword = normalizeNullableText(request.getNewPassword());
        if (newPassword != null) {
            passwordPolicyValidator.validate(existingUser, newPassword);
        }
        if (request.isNicknamePresent()) {
            existingUser.setNickname(normalizeNullableText(request.getNickname()));
        }
        if (request.isEmailPresent()) {
            existingUser.setEmail(normalizeNullableText(request.getEmail()));
        }
        if (request.isPhonePresent()) {
            existingUser.setPhone(normalizePhone(request.getPhone()));
        }
        if (request.isDepartmentPresent()) {
            existingUser.setDepartment(normalizeNullableText(request.getDepartment()));
        }
        if (request.isDeptIdPresent()) {
            existingUser.setDeptId(request.getDeptId());
        }
        if (request.getStatus() != null) {
            existingUser.setStatus(request.getStatus());
        }
        if (newPassword != null) {
            existingUser.setPassword(passwordEncoder.encode(newPassword));
            existingUser.setPasswordUpdatedTime(LocalDateTime.now());
        }

        User savedUser = userRepository.save(existingUser);
        notificationMiniProgramEligibilityService.requestRefresh(savedUser.getId());
        log.info("Admin edited user: {}", savedUser.getUsername());
        return savedUser;
    }

    @Transactional
    public void resetPassword(Long id, String rawPassword) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + id));

        passwordPolicyValidator.validate(existingUser, rawPassword);
        existingUser.setPassword(passwordEncoder.encode(rawPassword));
        existingUser.setPasswordUpdatedTime(LocalDateTime.now());
        userRepository.save(existingUser);
        log.info("Reset password for user: {}", existingUser.getUsername());
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

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        return normalizeNullableText(phone.replaceAll("\\D", ""));
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeRequiredText(String value, String errorMessage) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return normalized;
    }

    private Reason resolveCreateConflictReason(DataIntegrityViolationException ex) {
        String detail = DataIntegrityViolationDiagnostics.inspect(ex).constraintName().toLowerCase();
        if (detail.contains("username")) {
            return Reason.USERNAME_EXISTS;
        }
        if (detail.contains("employee_id")) {
            return Reason.EMPLOYEE_ID_EXISTS;
        }
        if (detail.contains("wechat_openid")) {
            return Reason.WECHAT_ALREADY_BOUND;
        }
        if (detail.contains("uk_user_role") || detail.contains("sys_user_role")) {
            return Reason.USER_ROLE_EXISTS;
        }
        return Reason.UNKNOWN_USER_CONFLICT;
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
