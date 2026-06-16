package com.pricemanagement.util;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataIntegrityViolationDiagnosticsTests {

    @Test
    void extractsSafeMysqlConstraintNameWithoutReturningSensitiveMessage() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate value secret-user@example.com for key 'uk_sys_user_username'");

        DataIntegrityViolationDiagnostics.Diagnostic diagnostic = DataIntegrityViolationDiagnostics.inspect(exception);

        assertEquals("uk_sys_user_username", diagnostic.constraintName());
        assertEquals("DataIntegrityViolationException", diagnostic.rootExceptionType());
    }

    @Test
    void rejectsUnsafeConstraintIdentifier() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate value for key 'unsafe constraint username@example.com'");

        DataIntegrityViolationDiagnostics.Diagnostic diagnostic = DataIntegrityViolationDiagnostics.inspect(exception);

        assertEquals("unknown", diagnostic.constraintName());
    }
}
