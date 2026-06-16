package com.pricemanagement.service;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.ValidatedUserImportRow;
import com.pricemanagement.entity.SysRole;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.SysRoleRepository;
import com.pricemanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class UserImportWriteServiceTransactionTests {

    @Autowired
    private UserImportWriteService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SysRoleRepository sysRoleRepository;

    @Test
    void rollsBackWholeBatchWhenLaterRowViolatesUniqueConstraint() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "import-rollback-" + suffix;
        Long roleId = ensureViewerRole();
        List<ValidatedUserImportRow> rows = List.of(
                row(2, username, suffix.substring(0, 6), roleId),
                row(3, username, suffix.substring(2, 8), roleId));

        assertThrows(RuntimeException.class, () -> service.importValidatedRows(rows));

        assertFalse(userRepository.existsByUsername(username));
    }

    private ValidatedUserImportRow row(int rowNumber, String username, String employeeId, Long roleId) {
        return new ValidatedUserImportRow(rowNumber, username, employeeId, username, null, null, null,
                User.Role.VIEWER, CommonStatus.ACTIVE, "encoded-password", roleId);
    }

    private Long ensureViewerRole() {
        return sysRoleRepository.findByRoleCode("VIEWER").map(SysRole::getId).orElseGet(() -> {
            SysRole role = new SysRole();
            role.setRoleCode("VIEWER");
            role.setRoleName("查看者");
            role.setStatus("ACTIVE");
            return sysRoleRepository.saveAndFlush(role).getId();
        });
    }
}
