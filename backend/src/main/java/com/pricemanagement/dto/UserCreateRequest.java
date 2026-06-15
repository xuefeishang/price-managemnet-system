package com.pricemanagement.dto;

import com.pricemanagement.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserCreateRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50个字符")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    private String password;

    @Pattern(regexp = "^\\s*$|\\d{6}$", message = "工号格式错误，应为6位数字")
    private String employeeId;

    @NotNull(message = "角色不能为空")
    private User.Role role;

    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20位")
    private String phone;

    @Size(max = 100, message = "部门名称长度不能超过100个字符")
    private String department;

    private Long deptId;

    public User toUser() {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setEmployeeId(employeeId);
        user.setRole(role);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setPhone(phone);
        user.setDepartment(department);
        user.setDeptId(deptId);
        return user;
    }
}
