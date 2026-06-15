package com.pricemanagement.service;

import com.pricemanagement.dto.*;
import com.pricemanagement.entity.*;
import com.pricemanagement.repository.*;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final OperationLogRepository operationLogRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final OperationLogHelper operationLogHelper;

    public ProfileDTO getCurrentProfile() {
        User user = currentUser();
        return toProfileDTO(user);
    }

    @Transactional
    public ProfileDTO updateCurrentProfile(UpdateProfileRequest request) {
        User user = currentUser();
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname().trim());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail().isBlank() ? null : request.getEmail().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().isBlank() ? null : request.getPhone().trim());
        }
        User saved = userRepository.save(user);
        operationLogHelper.logSuccess("个人中心", OperationLog.OperationType.UPDATE,
                "更新个人资料", "fields=nickname,email,phone");
        return toProfileDTO(saved);
    }

    public ProfileSecurityDTO getSecurity() {
        User user = currentUser();
        ProfileSecurityDTO dto = new ProfileSecurityDTO();
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setLastLoginIp(user.getLastLoginIp());
        dto.setLoginCount(user.getLoginCount());
        dto.setPasswordUpdatedTime(user.getPasswordUpdatedTime());
        dto.setLoginType(user.getLoginType());
        dto.setLocked(user.getIsLocked());
        dto.setLockedTime(user.getLockedTime());
        dto.setStatus(user.getStatus().name());
        return dto;
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = currentUser();
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new IllegalArgumentException("旧密码错误");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("两次输入的新密码不一致");
        }
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }
        passwordPolicyValidator.validate(user, request.getNewPassword());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordUpdatedTime(LocalDateTime.now());
        userRepository.save(user);
        refreshTokenService.revokeAllSessions(user.getId());
        operationLogHelper.logSuccess("个人中心", OperationLog.OperationType.UPDATE, "修改登录密码");
    }

    public Page<OperationLog> getMyOperationLogs(int page, int size, String operationType, String operationModule,
                                                 String keyword, LocalDateTime startTime, LocalDateTime endTime) {
        User user = currentUser();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "operationTime"));
        Page<OperationLog> logs = operationLogRepository.findMine(
                user.getId(), user.getUsername(), blankToNull(operationType), blankToNull(operationModule),
                blankToNull(keyword), startTime, endTime, pageable);
        logs.getContent().forEach(log -> log.setOperatorName(
                user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname()));
        operationLogHelper.logSuccess("个人中心", OperationLog.OperationType.VIEW, "查看我的操作记录");
        return logs;
    }

    public List<ProfileSessionDTO> getSessions(String currentRefreshToken) {
        User user = currentUser();
        return refreshTokenService.getUserSessions(user.getId(), currentRefreshToken);
    }

    @Transactional
    public void revokeSession(Long sessionId, String currentRefreshToken) {
        User user = currentUser();
        refreshTokenService.revokeSession(user.getId(), sessionId, currentRefreshToken);
        operationLogHelper.logSuccess("个人中心", OperationLog.OperationType.UPDATE, "撤销设备会话");
    }

    @Transactional
    public void revokeOtherSessions(String currentRefreshToken) {
        User user = currentUser();
        refreshTokenService.revokeOtherSessions(user.getId(), currentRefreshToken);
        operationLogHelper.logSuccess("个人中心", OperationLog.OperationType.UPDATE, "退出其他设备");
    }

    @Transactional
    public void revokeAllSessions() {
        User user = currentUser();
        refreshTokenService.revokeAllSessions(user.getId());
        operationLogHelper.logSuccess("个人中心", OperationLog.OperationType.UPDATE, "退出全部设备");
    }

    public Page<ProfileLoginHistoryDTO> getLoginHistory(int page, int size, String result,
                                                        LocalDateTime startTime, LocalDateTime endTime) {
        User user = currentUser();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "loginTime"));
        return loginHistoryRepository.findMine(user.getId(), user.getUsername(), blankToNull(result), startTime, endTime, pageable)
                .map(this::toLoginHistoryDTO);
    }

    public ProfilePreferenceDTO getPreferences() {
        User user = currentUser();
        return toPreferenceDTO(findOrCreatePreference(user.getId()));
    }

    @Transactional
    public ProfilePreferenceDTO updatePreferences(ProfilePreferenceUpdateRequest request) {
        User user = currentUser();
        UserPreference preference = findOrCreatePreference(user.getId());
        if (request.getTableDensity() != null) {
            preference.setTableDensity(request.getTableDensity());
        }
        if (request.getThemeMode() != null) {
            preference.setThemeMode(request.getThemeMode());
        }
        if (request.getPageSize() != null) {
            if (!List.of(10, 20, 50, 100).contains(request.getPageSize())) {
                throw new IllegalArgumentException("默认分页大小不合法");
            }
            preference.setPageSize(request.getPageSize());
        }
        if (request.getDefaultHomePath() != null) {
            String path = request.getDefaultHomePath().trim();
            if (!isAllowedDefaultHomePath(path)) {
                throw new IllegalArgumentException("默认首页路径不合法");
            }
            preference.setDefaultHomePath(path);
        }
        UserPreference saved = userPreferenceRepository.save(preference);
        operationLogHelper.logSuccess("个人中心", OperationLog.OperationType.UPDATE, "更新个人偏好");
        return toPreferenceDTO(saved);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new IllegalStateException("未登录");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private ProfileDTO toProfileDTO(User user) {
        List<String> roleCodes = permissionService.getUserRoleCodes(user.getId());
        if (roleCodes.isEmpty()) {
            roleCodes = List.of(user.getRole().name());
        }
        Set<String> permissions = permissionService.getUserPermissions(user.getId());
        ProfileDTO dto = new ProfileDTO();
        dto.setId(user.getId());
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmployeeId(user.getEmployeeId());
        dto.setNickname(user.getNickname());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setDepartment(user.getDepartment());
        dto.setDeptId(user.getDeptId());
        dto.setRole(roleCodes.get(0));
        dto.setRoles(roleCodes);
        dto.setPermissions(permissions);
        dto.setStatus(user.getStatus().name());
        dto.setLoginType(user.getLoginType());
        dto.setWechatOpenid(user.getWechatOpenid());
        dto.setWechatNickname(user.getWechatNickname());
        dto.setWechatAvatar(user.getWechatAvatar());
        dto.setLastLoginTime(user.getLastLoginTime());
        dto.setLastLoginIp(user.getLastLoginIp());
        dto.setLoginCount(user.getLoginCount());
        dto.setPasswordUpdatedTime(user.getPasswordUpdatedTime());
        dto.setLocked(user.getIsLocked());
        dto.setCreatedTime(user.getCreatedTime());
        dto.setUpdatedTime(user.getUpdatedTime());
        return dto;
    }

    private ProfileLoginHistoryDTO toLoginHistoryDTO(LoginHistory history) {
        ProfileLoginHistoryDTO dto = new ProfileLoginHistoryDTO();
        dto.setId(history.getId());
        dto.setLoginTime(history.getLoginTime());
        dto.setIpAddress(history.getIpAddress());
        dto.setUserAgent(history.getUserAgent());
        dto.setResult(history.getResult());
        dto.setFailureReason(history.getFailureReason());
        return dto;
    }

    private UserPreference findOrCreatePreference(Long userId) {
        return userPreferenceRepository.findByUserId(userId).orElseGet(() -> {
            UserPreference preference = new UserPreference();
            preference.setUserId(userId);
            return userPreferenceRepository.save(preference);
        });
    }

    private ProfilePreferenceDTO toPreferenceDTO(UserPreference preference) {
        ProfilePreferenceDTO dto = new ProfilePreferenceDTO();
        dto.setTableDensity(preference.getTableDensity());
        dto.setDefaultHomePath(preference.getDefaultHomePath());
        dto.setThemeMode(preference.getThemeMode());
        dto.setPageSize(preference.getPageSize());
        return dto;
    }

    private boolean isAllowedDefaultHomePath(String path) {
        return path != null && List.of("/home", "/products", "/price-maintenance", "/price-query", "/profile").contains(path);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
