package com.pricemanagement.dto;

import com.pricemanagement.entity.User;
import lombok.Data;

import java.util.List;

@Data
public class NotificationAuthorizationGuideRequest {
    private List<User.Role> targetRoles;
    private String status;
    private String keyword;
    private Long userId;
}
