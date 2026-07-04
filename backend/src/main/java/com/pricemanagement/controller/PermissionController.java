package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.SysPermission;
import com.pricemanagement.service.PermissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * 获取所有权限列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysPermission>> getAllPermissions() {
        List<SysPermission> permissions = permissionService.getAllPermissions();
        return Result.success("获取权限列表成功", permissions);
    }

    /**
     * 获取权限树
     */
    @GetMapping("/tree")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<SysPermission>> getPermissionTree() {
        List<SysPermission> permissions = permissionService.getPermissionTree();
        return Result.success("获取权限树成功", permissions);
    }
}
