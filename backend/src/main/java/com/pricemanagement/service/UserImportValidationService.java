package com.pricemanagement.service;

import com.pricemanagement.config.properties.ImportProperties;
import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.UserExcelData;
import com.pricemanagement.dto.UserImportResult;
import com.pricemanagement.dto.UserImportValidationError;
import com.pricemanagement.dto.ValidatedUserImportRow;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.listener.UserExcelValidationListener;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserImportValidationService {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "用户名", "工号", "昵称", "邮箱", "手机号", "部门",
            "角色(ADMIN/EDITOR/VIEWER)", "状态(ACTIVE/INACTIVE)", "初始密码");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ImportProperties importProperties;
    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;
    private final SysRoleRepository sysRoleRepository;
    private final EmployeeIdService employeeIdService;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final PasswordEncoder passwordEncoder;

    public ValidationOutcome validate(UserExcelValidationListener listener) {
        List<UserImportValidationError> errors = new ArrayList<>();
        validateHeaders(listener.getHeaders().values(), errors);

        List<UserExcelValidationListener.RowData> sourceRows = listener.getRows().stream()
                .filter(row -> !isBlankRow(row.data()))
                .toList();
        if (sourceRows.isEmpty()) {
            errors.add(error(null, "file", "NO_DATA_ROWS", "模板中没有可导入的用户数据"));
        }
        if (sourceRows.size() > importProperties.getMaxRows()) {
            errors.add(error(null, "file", "MAX_ROWS_EXCEEDED",
                    "用户导入数据不能超过" + importProperties.getMaxRows() + "行"));
        }

        Map<String, Integer> usernameRows = new HashMap<>();
        Map<String, Integer> employeeIdRows = new HashMap<>();
        Set<String> explicitEmployeeIds = sourceRows.stream()
                .map(row -> normalizeNullable(row.data().getEmployeeId()))
                .filter(value -> value != null && employeeIdService.isValidEmployeeId(value))
                .collect(Collectors.toSet());
        int missingEmployeeIdCount = (int) sourceRows.stream()
                .filter(row -> normalizeNullable(row.data().getEmployeeId()) == null)
                .count();
        Queue<String> generatedEmployeeIds;
        try {
            generatedEmployeeIds = missingEmployeeIdCount == 0
                    ? new ArrayDeque<>()
                    : new ArrayDeque<>(employeeIdService.generateEmployeeIds(missingEmployeeIdCount, explicitEmployeeIds));
        } catch (IllegalStateException ex) {
            errors.add(error(null, "employeeId", "EMPLOYEE_ID_GENERATION_FAILED", "无法生成足量唯一工号"));
            generatedEmployeeIds = new ArrayDeque<>();
        }
        Map<String, SysRole> activeRoles = sysRoleRepository.findByStatus("ACTIVE").stream()
                .collect(Collectors.toMap(SysRole::getRoleCode, role -> role, (left, right) -> left));
        List<PendingRow> pendingRows = new ArrayList<>();

        for (UserExcelValidationListener.RowData sourceRow : sourceRows) {
            PendingRow pending = validateRow(sourceRow, activeRoles, generatedEmployeeIds, errors);
            if (pending.username() != null) {
                detectDuplicate(normalizeUsernameKey(pending.username()), sourceRow.rowNumber(), usernameRows,
                        "username", "DUPLICATE_USERNAME_IN_FILE", "Excel 中存在重复用户名", errors);
            }
            if (pending.employeeId() != null && employeeIdService.isValidEmployeeId(pending.employeeId())) {
                detectDuplicate(pending.employeeId(), sourceRow.rowNumber(), employeeIdRows,
                        "employeeId", "DUPLICATE_EMPLOYEE_ID_IN_FILE", "Excel 中存在重复工号", errors);
            }
            pendingRows.add(pending);
        }

        Set<String> usernames = pendingRows.stream().map(PendingRow::username).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Set<String> employeeIds = pendingRows.stream().map(PendingRow::employeeId)
                .filter(value -> value != null && employeeIdService.isValidEmployeeId(value)).collect(Collectors.toSet());
        Set<String> existingUsernames = findExistingUsernames(usernames).stream()
                .map(User::getUsername).map(this::normalizeUsernameKey).collect(Collectors.toSet());
        Set<String> existingEmployeeIds = findExistingEmployeeIds(employeeIds).stream()
                .map(User::getEmployeeId).collect(Collectors.toSet());

        for (PendingRow pending : pendingRows) {
            if (existingUsernames.contains(normalizeUsernameKey(pending.username()))) {
                errors.add(error(pending.rowNumber(), "username", "USERNAME_ALREADY_EXISTS", "用户名已存在"));
            }
            if (existingEmployeeIds.contains(pending.employeeId())) {
                errors.add(error(pending.rowNumber(), "employeeId", "EMPLOYEE_ID_ALREADY_EXISTS", "工号已存在"));
            }
        }

        if (!errors.isEmpty()) {
            return new ValidationOutcome(UserImportResult.invalid(sourceRows.size(), errors), List.of());
        }

        List<ValidatedUserImportRow> rows = pendingRows.stream()
                .map(row -> new ValidatedUserImportRow(
                        row.rowNumber(), row.username(), row.employeeId(), row.nickname(), row.email(), row.phone(),
                        row.department(), row.role(), row.status(), passwordEncoder.encode(row.rawPassword()), row.roleId()))
                .toList();
        return new ValidationOutcome(UserImportResult.success(0), rows);
    }

    private void validateHeaders(Collection<String> actualHeaders, List<UserImportValidationError> errors) {
        List<String> normalized = actualHeaders.stream().map(this::normalizeNullable).toList();
        Set<String> unique = new HashSet<>(normalized);
        if (normalized.size() != unique.size()) {
            errors.add(error(null, "header", "DUPLICATE_HEADER", "模板存在重复列名"));
        }
        for (String expected : EXPECTED_HEADERS) {
            if (!unique.contains(expected)) {
                errors.add(error(null, "header", "MISSING_HEADER", "模板缺少列：" + expected));
            }
        }
        for (String actual : unique) {
            if (actual != null && !EXPECTED_HEADERS.contains(actual)) {
                errors.add(error(null, "header", "UNKNOWN_HEADER", "模板包含未知列：" + actual));
            }
        }
    }

    private List<User> findExistingUsernames(Set<String> usernames) {
        return usernames.isEmpty() ? List.of() : userRepository.findByUsernameIn(usernames);
    }

    private List<User> findExistingEmployeeIds(Set<String> employeeIds) {
        return employeeIds.isEmpty() ? List.of() : userRepository.findByEmployeeIdIn(employeeIds);
    }

    private PendingRow validateRow(UserExcelValidationListener.RowData sourceRow, Map<String, SysRole> activeRoles,
                                   Queue<String> generatedEmployeeIds, List<UserImportValidationError> errors) {
        UserExcelData data = sourceRow.data();
        int rowNumber = sourceRow.rowNumber();
        String username = normalizeNullable(data.getUsername());
        String employeeId = normalizeNullable(data.getEmployeeId());
        String nickname = normalizeNullable(data.getNickname());
        String email = normalizeNullable(data.getEmail());
        String phone = normalizePhone(data.getPhone());
        String department = normalizeNullable(data.getDepartment());

        if (username == null) {
            errors.add(error(rowNumber, "username", "REQUIRED", "用户名不能为空"));
        } else if (username.length() > 50) {
            errors.add(error(rowNumber, "username", "TOO_LONG", "用户名长度不能超过50个字符"));
        }
        if (employeeId == null) {
            employeeId = generatedEmployeeIds.poll();
            if (employeeId == null) {
                errors.add(error(rowNumber, "employeeId", "EMPLOYEE_ID_GENERATION_FAILED", "无法生成唯一工号"));
            }
        } else if (!employeeIdService.isValidEmployeeId(employeeId)) {
            errors.add(error(rowNumber, "employeeId", "INVALID_FORMAT", "工号必须为6位数字"));
        }
        if (nickname != null && nickname.length() > 50) {
            errors.add(error(rowNumber, "nickname", "TOO_LONG", "昵称长度不能超过50个字符"));
        }
        if (email != null && (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches())) {
            errors.add(error(rowNumber, "email", "INVALID_FORMAT", "邮箱格式不正确"));
        }
        if (phone != null && phone.length() > 20) {
            errors.add(error(rowNumber, "phone", "TOO_LONG", "手机号长度不能超过20位"));
        }
        if (department != null && department.length() > 100) {
            errors.add(error(rowNumber, "department", "TOO_LONG", "部门名称长度不能超过100个字符"));
        }

        User.Role role = parseRole(data.getRole(), rowNumber, activeRoles, errors);
        CommonStatus status = parseStatus(data.getStatus(), rowNumber, errors);
        String rawPassword = normalizeNullable(data.getPassword());
        if (rawPassword == null) {
            rawPassword = securityProperties.getDefaultUserPassword();
        }

        User policyUser = new User();
        policyUser.setUsername(username);
        policyUser.setNickname(nickname != null ? nickname : username);
        policyUser.setPhone(phone);
        try {
            passwordPolicyValidator.validate(policyUser, rawPassword);
        } catch (IllegalArgumentException ex) {
            errors.add(error(rowNumber, "password", "INVALID_PASSWORD_POLICY", ex.getMessage()));
        }

        return new PendingRow(rowNumber, username, employeeId, nickname != null ? nickname : username, email, phone,
                department, role, status, rawPassword,
                role == null || activeRoles.get(role.name()) == null ? null : activeRoles.get(role.name()).getId());
    }

    private User.Role parseRole(String value, int rowNumber, Map<String, SysRole> activeRoles,
                                List<UserImportValidationError> errors) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            normalized = User.Role.VIEWER.name();
        }
        try {
            User.Role role = User.Role.valueOf(normalized.toUpperCase(Locale.ROOT));
            if (!activeRoles.containsKey(role.name())) {
                errors.add(error(rowNumber, "role", "ROLE_NOT_AVAILABLE", "角色不存在或未启用"));
                return null;
            }
            return role;
        } catch (IllegalArgumentException ex) {
            errors.add(error(rowNumber, "role", "INVALID_ROLE", "角色值无效"));
            return null;
        }
    }

    private CommonStatus parseStatus(String value, int rowNumber, List<UserImportValidationError> errors) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            return CommonStatus.ACTIVE;
        }
        try {
            return CommonStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            errors.add(error(rowNumber, "status", "INVALID_STATUS", "状态值无效"));
            return null;
        }
    }

    private void detectDuplicate(String value, int rowNumber, Map<String, Integer> firstRows, String field,
                                 String code, String message, List<UserImportValidationError> errors) {
        Integer first = firstRows.putIfAbsent(value, rowNumber);
        if (first != null) {
            addUniqueError(errors, error(first, field, code, message));
            addUniqueError(errors, error(rowNumber, field, code, message));
        }
    }

    private void addUniqueError(List<UserImportValidationError> errors, UserImportValidationError candidate) {
        boolean exists = errors.stream().anyMatch(error -> java.util.Objects.equals(error.rowNumber(), candidate.rowNumber())
                && error.field().equals(candidate.field()) && error.code().equals(candidate.code()));
        if (!exists) {
            errors.add(candidate);
        }
    }

    private boolean isBlankRow(UserExcelData data) {
        return data == null || Arrays.asList(data.getUsername(), data.getEmployeeId(), data.getNickname(), data.getEmail(),
                        data.getPhone(), data.getDepartment(), data.getRole(), data.getStatus(), data.getPassword())
                .stream().allMatch(value -> value == null || value.isBlank());
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizePhone(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalizeNullable(normalized.replaceAll("\\D", ""));
    }

    private String normalizeUsernameKey(String username) {
        return username == null ? null : username.toLowerCase(Locale.ROOT);
    }

    private UserImportValidationError error(Integer row, String field, String code, String message) {
        return new UserImportValidationError(row, field, code, message);
    }

    public record ValidationOutcome(UserImportResult result, List<ValidatedUserImportRow> rows) {
    }

    private record PendingRow(int rowNumber, String username, String employeeId, String nickname, String email,
                              String phone, String department, User.Role role, CommonStatus status,
                              String rawPassword, Long roleId) {
    }
}
