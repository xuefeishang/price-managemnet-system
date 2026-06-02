package com.pricemanagement.util;

import com.pricemanagement.constants.SystemConstants;
import com.pricemanagement.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SecurityUtils {

    /**
     * 获取当前登录用户的ID
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User) {
                return ((User) principal).getId();
            }
            Object details = authentication.getDetails();
            if (details instanceof Long) {
                return (Long) details;
            }
            if (details instanceof Number) {
                return ((Number) details).longValue();
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户的用户名
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return null;
    }

    /**
     * 获取当前登录用户的角色（主角色，兼容旧逻辑）
     */
    public static String getCurrentUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .map(role -> role.replace(SystemConstants.ROLE_PREFIX, ""))
                    .orElse(null);
        }
        return null;
    }

    /**
     * 获取当前登录用户的所有角色列表
     */
    public static List<String> getCurrentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(role -> role.replace(SystemConstants.ROLE_PREFIX, ""))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 获取当前登录用户对象
     */
    public static Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User) {
                return Optional.of((User) principal);
            }
        }
        return Optional.empty();
    }

    /**
     * 判断当前用户是否有指定角色
     */
    public static boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals(SystemConstants.ROLE_PREFIX + role));
        }
        return false;
    }

    /**
     * 判断当前用户是否有任意一个指定角色
     */
    public static boolean hasAnyRole(String... roles) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            for (String role : roles) {
                if (authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals(SystemConstants.ROLE_PREFIX + role))) {
                    return true;
                }
            }
        }
        return false;
    }
}
