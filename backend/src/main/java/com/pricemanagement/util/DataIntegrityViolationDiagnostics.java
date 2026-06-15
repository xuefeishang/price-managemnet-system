package com.pricemanagement.util;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DataIntegrityViolationDiagnostics {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_.-]{1,128}");
    private static final Pattern MYSQL_KEY = Pattern.compile("(?i)for key ['`\"]([^'`\"]+)['`\"]");

    private DataIntegrityViolationDiagnostics() {
    }

    public static Diagnostic inspect(DataIntegrityViolationException exception) {
        String constraintName = findConstraintName(exception);
        Throwable root = exception.getMostSpecificCause();
        return new Diagnostic(sanitize(constraintName), root == null ? "unknown" : sanitizeType(root.getClass().getSimpleName()));
    }

    private static String findConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation
                    && constraintViolation.getConstraintName() != null) {
                return constraintViolation.getConstraintName();
            }
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = MYSQL_KEY.matcher(message);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private static String sanitize(String value) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            return "unknown";
        }
        return value;
    }

    private static String sanitizeType(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches() ? value : "unknown";
    }

    public record Diagnostic(String constraintName, String rootExceptionType) {
    }
}
