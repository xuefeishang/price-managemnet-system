package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.dto.MenuItemDTO;
import com.pricemanagement.entity.MenuItem;
import com.pricemanagement.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuItemRepository menuItemRepository;
    private final ObjectMapper objectMapper;

    public List<MenuItemDTO> getAllMenus() {
        List<MenuItem> menus = menuItemRepository.findAll();
        return menus.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<MenuItemDTO> getMenuTree() {
        // Fetch all menus and build tree manually to avoid lazy loading issues
        List<MenuItem> allMenus = menuItemRepository.findAll();
        List<MenuItem> rootMenus = allMenus.stream()
                .filter(m -> m.getParentId() == null)
                .sorted((a, b) -> a.getSortOrder().compareTo(b.getSortOrder()))
                .collect(Collectors.toList());

        return rootMenus.stream().map(menu -> buildTreeDTO(menu, allMenus)).collect(Collectors.toList());
    }

    private MenuItemDTO buildTreeDTO(MenuItem menu, List<MenuItem> allMenus) {
        MenuItemDTO dto = toDTO(menu);
        List<MenuItem> children = allMenus.stream()
                .filter(m -> m.getParentId() != null && m.getParentId().equals(menu.getId()))
                .sorted((a, b) -> a.getSortOrder().compareTo(b.getSortOrder()))
                .collect(Collectors.toList());
        if (!children.isEmpty()) {
            dto.setChildren(children.stream().map(c -> buildTreeDTO(c, allMenus)).collect(Collectors.toList()));
        }
        return dto;
    }

    public List<MenuItemDTO> getVisibleMenus(String userRole) {
        List<MenuItem> allMenus = menuItemRepository.findAll();
        List<MenuItem> visibleMenus = allMenus.stream()
                .filter(menu -> menu.getVisible() && hasAccess(menu, userRole))
                .collect(Collectors.toList());

        // Get root menus from visible menus
        List<MenuItem> rootMenus = visibleMenus.stream()
                .filter(m -> m.getParentId() == null)
                .sorted((a, b) -> a.getSortOrder().compareTo(b.getSortOrder()))
                .collect(Collectors.toList());

        return rootMenus.stream().map(menu -> buildTreeDTO(menu, visibleMenus)).collect(Collectors.toList());
    }

    private boolean hasAccess(MenuItem menu, String userRole) {
        if (menu.getRoles() == null || menu.getRoles().isEmpty()) {
            return true; // No roles specified means accessible to all
        }
        try {
            List<String> roles = objectMapper.readValue(menu.getRoles(), new TypeReference<List<String>>() {});
            if (roles == null || roles.isEmpty()) {
                return true; // Empty array means accessible to all
            }
            return roles.contains(userRole);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse roles for menu {}: {}", menu.getId(), menu.getRoles());
            return false; // If roles are malformed, deny access for safety
        }
    }

    public Optional<MenuItemDTO> getMenuById(Long id) {
        return menuItemRepository.findById(id).map(this::toDTO);
    }

    @Transactional
    public MenuItemDTO createMenu(MenuItemDTO dto) {
        MenuItem menu = new MenuItem();
        updateMenuFromDTO(menu, dto);

        if (dto.getParentId() != null) {
            // Verify parent exists
            menuItemRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("父级菜单不存在: " + dto.getParentId()));
            menu.setParentId(dto.getParentId());
        }

        MenuItem saved = menuItemRepository.save(menu);
        log.info("Created menu: {}", saved.getName());
        return toDTO(saved);
    }

    @Transactional
    public MenuItemDTO updateMenu(Long id, MenuItemDTO dto) {
        MenuItem menu = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));

        updateMenuFromDTO(menu, dto);

        if (dto.getParentId() != null) {
            if (dto.getParentId().equals(id)) {
                throw new IllegalArgumentException("不能将自己设置为父级菜单");
            }
            // Verify parent exists
            menuItemRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("父级菜单不存在: " + dto.getParentId()));
            menu.setParentId(dto.getParentId());
        } else {
            menu.setParentId(null);
        }

        MenuItem saved = menuItemRepository.save(menu);
        log.info("Updated menu: {}", saved.getName());
        return toDTO(saved);
    }

    @Transactional
    public void deleteMenu(Long id) {
        MenuItem menu = menuItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("菜单不存在: " + id));

        // Check if menu has children by checking if any menu has this as parent
        List<MenuItem> allMenus = menuItemRepository.findAll();
        boolean hasChildren = allMenus.stream()
                .anyMatch(m -> id.equals(m.getParentId()));
        if (hasChildren) {
            throw new IllegalArgumentException("请先删除子菜单");
        }

        menuItemRepository.delete(menu);
        log.info("Deleted menu: {}", menu.getName());
    }

    private void updateMenuFromDTO(MenuItem menu, MenuItemDTO dto) {
        menu.setName(dto.getName());
        menu.setPath(dto.getPath());
        menu.setIcon(dto.getIcon());
        menu.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        menu.setVisible(dto.getVisible() != null ? dto.getVisible() : true);

        if (dto.getRoles() != null) {
            try {
                menu.setRoles(objectMapper.writeValueAsString(dto.getRoles()));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize roles", e);
            }
        }
    }

    private MenuItemDTO toDTO(MenuItem menu) {
        MenuItemDTO dto = new MenuItemDTO();
        dto.setId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setName(menu.getName());
        dto.setPath(menu.getPath());
        dto.setIcon(menu.getIcon());
        dto.setSortOrder(menu.getSortOrder());
        dto.setVisible(menu.getVisible());
        dto.setCreatedTime(menu.getCreatedTime());
        dto.setUpdatedTime(menu.getUpdatedTime());

        if (menu.getRoles() != null && !menu.getRoles().isEmpty()) {
            try {
                dto.setRoles(objectMapper.readValue(menu.getRoles(), new TypeReference<List<String>>() {}));
            } catch (JsonProcessingException e) {
                dto.setRoles(new ArrayList<>());
            }
        } else {
            dto.setRoles(new ArrayList<>());
        }

        return dto;
    }

    @Transactional
    public void initializeDefaultMenus() {
        if (menuItemRepository.count() == 0) {
            log.info("Initializing default menu items");

            // Create root menus
            MenuItem home = createMenuItem(null, "首页", "/home", "home", 1, true, null);
            MenuItem productMgmt = createMenuItem(null, "产品管理", null, "product", 2, true, null);
            MenuItem basicMgmt = createMenuItem(null, "基础运维", null, "category", 3, true, "[\"ADMIN\",\"EDITOR\"]");
            MenuItem systemMgmt = createMenuItem(null, "系统管理", null, "settings", 4, true, "[\"ADMIN\"]");

            // Create child menus under 产品管理
            createMenuItem(productMgmt, "产品列表", "/products", null, 1, true, null);
            createMenuItem(productMgmt, "价格维护", "/price-maintenance", "price", 2, true, "[\"ADMIN\",\"EDITOR\"]");

            // Create child menus under 基础运维
            createMenuItem(basicMgmt, "产品维护", "/product-edit", null, 1, true, "[\"ADMIN\",\"EDITOR\"]");
            createMenuItem(basicMgmt, "分类管理", "/categories", null, 2, true, "[\"ADMIN\",\"EDITOR\"]");
            createMenuItem(basicMgmt, "导入导出", "/import", null, 3, true, "[\"ADMIN\",\"EDITOR\"]");

            // Create child menus under 系统管理
            createMenuItem(systemMgmt, "用户管理", "/users", "users", 1, true, "[\"ADMIN\"]");
            createMenuItem(systemMgmt, "菜单配置", "/menu-config", "menu", 2, true, "[\"ADMIN\"]");
            createMenuItem(systemMgmt, "日志管理", "/operation-log", "log", 3, true, "[\"ADMIN\"]");
            createMenuItem(systemMgmt, "审批流配置", "/approval-config", "workflow", 4, true, "[\"ADMIN\"]");

            // Create child menus under 基础运维 - 审批管理
            createMenuItem(basicMgmt, "审批管理", "/approval", "check-circle", 4, true, "[\"ADMIN\",\"EDITOR\"]");

            // Create child menus under 基础运维 - 字典管理
            createMenuItem(basicMgmt, "字典管理", null, "dict", 5, true, "[\"ADMIN\",\"EDITOR\"]");
            MenuItem dictMgmt = menuItemRepository.findByPath("/dict").orElse(null);
            if (dictMgmt != null) {
                createMenuItem(dictMgmt, "产地管理", "/origins", null, 1, true, "[\"ADMIN\",\"EDITOR\"]");
                createMenuItem(dictMgmt, "客户管理", "/customers", null, 2, true, "[\"ADMIN\",\"EDITOR\"]");
            }

            log.info("Default menu items initialized");
        }
    }

    private MenuItem createMenuItem(MenuItem parent, String name, String path, String icon, int sortOrder, boolean visible, String roles) {
        MenuItem item = new MenuItem();
        if (parent != null) {
            item.setParentId(parent.getId());
        }
        item.setName(name);
        item.setPath(path);
        item.setIcon(icon);
        item.setSortOrder(sortOrder);
        item.setVisible(visible);
        item.setRoles(roles);
        return menuItemRepository.save(item);
    }
}
