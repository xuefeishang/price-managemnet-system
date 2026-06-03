package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
public class ProfileDTO {
    private Long id;
    private Long userId;
    private String username;
    private String employeeId;
    private String nickname;
    private String email;
    private String phone;
    private String department;
    private Long deptId;
    private String role;
    private List<String> roles;
    private Set<String> permissions;
    private String status;
    private String loginType;
    private String wechatOpenid;
    private String wechatNickname;
    private String wechatAvatar;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private Integer loginCount;
    private LocalDateTime passwordUpdatedTime;
    private Boolean locked;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

