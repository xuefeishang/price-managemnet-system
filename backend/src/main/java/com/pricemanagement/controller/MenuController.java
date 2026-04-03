package com.pricemanagement.controller;

import com.pricemanagement.dto.MenuItemDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuItemService menuItemService;

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<MenuItemDTO>> getMenuTree() {
        return Result.success("获取菜单树成功", menuItemService.getMenuTree());
    }

    @GetMapping("/visible")
    public Result<List<MenuItemDTO>> getVisibleMenus(@RequestParam(required = false) String role) {
        return Result.success("获取菜单成功", menuItemService.getVisibleMenus(role != null ? role : "VIEWER"));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<MenuItemDTO>> getAllMenus() {
        return Result.success("获取所有菜单成功", menuItemService.getAllMenus());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MenuItemDTO> getMenuById(@PathVariable Long id) {
        return menuItemService.getMenuById(id)
                .map(menu -> Result.<MenuItemDTO>success("获取菜单成功", menu))
                .orElse(Result.error(404, "菜单不存在: " + id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MenuItemDTO> createMenu(@RequestBody MenuItemDTO dto) {
        return Result.success("创建菜单成功", menuItemService.createMenu(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<MenuItemDTO> updateMenu(@PathVariable Long id, @RequestBody MenuItemDTO dto) {
        return Result.success("更新菜单成功", menuItemService.updateMenu(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteMenu(@PathVariable Long id) {
        menuItemService.deleteMenu(id);
        return Result.success("删除菜单成功");
    }

    @PostMapping("/init")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> initializeDefaultMenus() {
        menuItemService.initializeDefaultMenus();
        return Result.success("菜单初始化成功");
    }
}
