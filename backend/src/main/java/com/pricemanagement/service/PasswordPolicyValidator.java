package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordPolicyValidator {

    private final SecurityProperties securityProperties;

    public void validate(User user, String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        SecurityProperties.PasswordPolicy policy = securityProperties.getPasswordPolicy();
        if (password.length() < policy.getMinLength() || password.length() > policy.getMaxLength()) {
            throw new IllegalArgumentException(
                    "密码长度必须在" + policy.getMinLength() + "-" + policy.getMaxLength() + "个字符之间");
        }
        if (policy.isRequireLetter() && !password.matches(".*[A-Za-z].*")) {
            throw new IllegalArgumentException("密码必须包含字母");
        }
        if (policy.isRequireDigit() && !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须包含数字");
        }
        if (policy.isDisallowWhitespace() && password.matches(".*\\s.*")) {
            throw new IllegalArgumentException("密码不能包含空白字符");
        }
        if (user != null && (password.equalsIgnoreCase(user.getUsername())
                || (user.getNickname() != null && password.equalsIgnoreCase(user.getNickname()))
                || (user.getPhone() != null && password.equals(user.getPhone())))) {
            throw new IllegalArgumentException("密码不能与账号、昵称或手机号相同");
        }
    }
}
