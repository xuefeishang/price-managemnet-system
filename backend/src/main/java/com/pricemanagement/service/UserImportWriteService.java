package com.pricemanagement.service;

import com.pricemanagement.dto.ValidatedUserImportRow;
import com.pricemanagement.entity.User;
import com.pricemanagement.entity.UserRole;
import com.pricemanagement.exception.UserConflictException;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserImportWriteService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final NotificationMiniProgramEligibilityService notificationMiniProgramEligibilityService;
    private final ActiveRoleResolver activeRoleResolver;

    @Transactional
    public int importValidatedRows(List<ValidatedUserImportRow> rows) {
        Set<String> usernames = rows.stream().map(ValidatedUserImportRow::username).collect(Collectors.toSet());
        Set<String> employeeIds = rows.stream().map(ValidatedUserImportRow::employeeId).collect(Collectors.toSet());
        if (!usernames.isEmpty() && !userRepository.findByUsernameIn(usernames).isEmpty()) {
            throw new UserConflictException(UserConflictException.Reason.USERNAME_EXISTS);
        }
        if (!employeeIds.isEmpty() && !userRepository.findByEmployeeIdIn(employeeIds).isEmpty()) {
            throw new UserConflictException(UserConflictException.Reason.EMPLOYEE_ID_EXISTS);
        }
        activeRoleResolver.requireAllActiveByIds(rows.stream().map(ValidatedUserImportRow::roleId).collect(Collectors.toSet()));

        List<User> users = rows.stream().map(row -> {
            User user = new User();
            user.setUsername(row.username());
            user.setEmployeeId(row.employeeId());
            user.setNickname(row.nickname());
            user.setEmail(row.email());
            user.setPhone(row.phone());
            user.setDepartment(row.department());
            user.setRole(row.role());
            user.setStatus(row.status());
            user.setPassword(row.encodedPassword());
            return user;
        }).toList();
        List<User> savedUsers = userRepository.saveAllAndFlush(users);
        List<UserRole> userRoles = java.util.stream.IntStream.range(0, rows.size()).mapToObj(index -> {
            UserRole userRole = new UserRole();
            userRole.setUserId(savedUsers.get(index).getId());
            userRole.setRoleId(rows.get(index).roleId());
            return userRole;
        }).toList();
        userRoleRepository.saveAllAndFlush(userRoles);
        savedUsers.forEach(user -> notificationMiniProgramEligibilityService.requestRefresh(user.getId()));
        return rows.size();
    }
}
