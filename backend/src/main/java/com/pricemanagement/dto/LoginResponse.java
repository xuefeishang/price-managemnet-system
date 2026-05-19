
package com.pricemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String role;  // 主角色（兼容）
    private List<String> roles;  // 角色列表

    // 兼容旧构造函数
    public LoginResponse(String token, Long userId, String username, String nickname, String role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.roles = List.of(role);
    }
}
