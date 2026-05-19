
package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pricemanagement.constants.CommonStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "employee_id", unique = true, length = 6)
    private String employeeId;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommonStatus status = CommonStatus.ACTIVE;

    @Column(length = 50)
    private String nickname;

    @Column(length = 100)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String department;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(name = "login_type", length = 20)
    private String loginType = "PASSWORD";

    @Column(name = "wechat_openid", unique = true, length = 100)
    private String wechatOpenid;

    @Column(name = "wechat_unionid", length = 100)
    private String wechatUnionid;

    @Column(name = "wechat_nickname", length = 100)
    private String wechatNickname;

    @Column(name = "wechat_avatar", length = 500)
    private String wechatAvatar;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;

    @Column(name = "login_count")
    private Integer loginCount = 0;

    @Column(name = "password_updated_time")
    private LocalDateTime passwordUpdatedTime;

    @Column(name = "is_locked")
    private Boolean isLocked = false;

    @Column(name = "locked_time")
    private LocalDateTime lockedTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum Role {
        ADMIN,      // 管理员
        EDITOR,     // 编辑者
        VIEWER      // 查看者
    }
}
