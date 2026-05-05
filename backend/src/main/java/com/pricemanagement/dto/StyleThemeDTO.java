package com.pricemanagement.dto;

import lombok.Data;

import java.util.Map;

@Data
public class StyleThemeDTO {
    private String themeKey;
    private String themeName;
    private String description;
    private Map<String, String> colors;
    private Map<String, String> fonts;
    private boolean isActive;
}
