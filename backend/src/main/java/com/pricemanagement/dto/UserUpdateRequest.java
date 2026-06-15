package com.pricemanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.pricemanagement.constants.CommonStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class UserUpdateRequest {

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object value) {
        throw new IllegalArgumentException("不支持更新字段：" + fieldName);
    }

    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
    private boolean nicknamePresent;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;
    private boolean emailPresent;

    @Size(max = 20, message = "手机号长度不能超过20位")
    private String phone;
    private boolean phonePresent;

    @Size(max = 100, message = "部门名称长度不能超过100个字符")
    private String department;
    private boolean departmentPresent;

    private Long deptId;
    private boolean deptIdPresent;

    private CommonStatus status;

    @JsonSetter
    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknamePresent = true;
    }

    @JsonSetter
    public void setEmail(String email) {
        this.email = email;
        this.emailPresent = true;
    }

    @JsonSetter
    public void setPhone(String phone) {
        this.phone = phone;
        this.phonePresent = true;
    }

    @JsonSetter
    public void setDepartment(String department) {
        this.department = department;
        this.departmentPresent = true;
    }

    @JsonSetter
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
        this.deptIdPresent = true;
    }
}
