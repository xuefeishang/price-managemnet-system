package com.pricemanagement.dto;

import lombok.Data;

import java.util.Map;

/**
 * 样式预设 DTO
 */
@Data
public class StylePresetDTO {
    private String key;
    private String name;
    private String description;
    private Boolean active;
    private Boolean isDefault;
    private Integer sortOrder;
    private Map<String, Object> config;
}
