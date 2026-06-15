package com.pricemanagement.exception;

/**
 * 用户唯一字段或角色关联发生冲突。
 */
public class UserConflictException extends RuntimeException {

    private final Reason reason;

    public UserConflictException(Reason reason) {
        super(reason.getMessage());
        this.reason = reason;
    }

    public UserConflictException(Reason reason, Throwable cause) {
        super(reason.getMessage(), cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        USERNAME_EXISTS("用户名已存在，请更换后重试"),
        EMPLOYEE_ID_EXISTS("工号已存在，请更换后重试"),
        WECHAT_ALREADY_BOUND("该微信账号已绑定其他用户"),
        USER_ROLE_EXISTS("用户角色关联已存在，请刷新页面后重试"),
        UNKNOWN_USER_CONFLICT("用户数据与现有记录冲突，请检查后重试");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}
