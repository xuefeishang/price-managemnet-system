package com.pricemanagement.dto;

import lombok.Data;

@Data
public class ProfilePreferenceDTO {
    private String tableDensity;
    private String defaultHomePath;
    private String themeMode;
    private Integer pageSize;
}

