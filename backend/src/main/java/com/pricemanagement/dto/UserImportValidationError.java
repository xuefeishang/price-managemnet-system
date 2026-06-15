package com.pricemanagement.dto;

public record UserImportValidationError(
        Integer rowNumber,
        String field,
        String code,
        String message
) {
}
