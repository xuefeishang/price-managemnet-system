package com.pricemanagement.dto;

import lombok.Data;

@Data
public class StyleConfigDTO {
    private String systemName;
    private String priceRiseColor;
    private String priceFallColor;
    private String priceFlatColor;
    private String chartPrimaryColor;
    private String chartBudgetColor;
    private String chartColors;
    private String headingFont;
    private String bodyFont;
    private String numberFont;
    private String logoUrl;
    private String logoSize;
    private String activeTheme;

    // 字体大小配置
    private String fontSizeXs;
    private String fontSizeSm;
    private String fontSizeBase;
    private String fontSizeLg;
    private String fontSizeXl;
    private String fontSize2xl;
    private String fontSize3xl;
}
