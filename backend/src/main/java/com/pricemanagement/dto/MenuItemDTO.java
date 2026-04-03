package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class MenuItemDTO {
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private List<String> roles;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private List<MenuItemDTO> children;
}
