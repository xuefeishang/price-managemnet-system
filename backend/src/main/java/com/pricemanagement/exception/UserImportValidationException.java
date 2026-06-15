package com.pricemanagement.exception;

import com.pricemanagement.dto.UserImportResult;

public class UserImportValidationException extends RuntimeException {

    private final UserImportResult result;

    public UserImportValidationException(UserImportResult result) {
        super("用户导入预检失败，未导入任何用户");
        this.result = result;
    }

    public UserImportResult getResult() {
        return result;
    }
}
