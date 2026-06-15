package com.pricemanagement.dto;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.User;

public record ValidatedUserImportRow(
        int rowNumber,
        String username,
        String employeeId,
        String nickname,
        String email,
        String phone,
        String department,
        User.Role role,
        CommonStatus status,
        String encodedPassword,
        Long roleId
) {
}
