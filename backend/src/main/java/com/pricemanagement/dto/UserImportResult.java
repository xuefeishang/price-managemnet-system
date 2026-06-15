package com.pricemanagement.dto;

import java.util.List;

public record UserImportResult(
        boolean valid,
        boolean imported,
        int totalRows,
        int importedCount,
        List<UserImportValidationError> errors
) {

    public static UserImportResult invalid(int totalRows, List<UserImportValidationError> errors) {
        return new UserImportResult(false, false, totalRows, 0, List.copyOf(errors));
    }

    public static UserImportResult success(int importedCount) {
        return new UserImportResult(true, true, importedCount, importedCount, List.of());
    }
}
