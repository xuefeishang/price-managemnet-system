package com.pricemanagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProfilePreferenceUpdateRequest {

    @Pattern(regexp = "COMPACT|DEFAULT|COMFORTABLE", message = "表格密度不合法")
    private String tableDensity;

    private String defaultHomePath;

    @Pattern(regexp = "SYSTEM|LIGHT|DARK", message = "主题模式不合法")
    private String themeMode;

    @Min(value = 10, message = "默认分页大小不能小于10")
    @Max(value = 100, message = "默认分页大小不能大于100")
    private Integer pageSize;
}
