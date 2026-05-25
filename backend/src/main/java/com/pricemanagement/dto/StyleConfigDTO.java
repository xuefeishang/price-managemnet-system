package com.pricemanagement.dto;

import lombok.Data;

import java.util.List;

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

    // 新增：色彩方案和布局方案
    private String activeColorScheme;
    private String activeLayoutStyle;
    private String fontSizePreset;

    // 字体大小配置
    private String fontSizeXs;
    private String fontSizeSm;
    private String fontSizeBase;
    private String fontSizeLg;
    private String fontSizeXl;
    private String fontSize2xl;
    private String fontSize3xl;

    // 新增：双Logo配置
    private String logoUrlLogin;    // 登录页Logo URL
    private String logoUrlNav;      // 导航栏Logo URL
    private String logoSizeLogin;   // 登录页Logo尺寸
    private String logoSizeNav;     // 导航栏Logo尺寸

    // 新增：副标题配置
    private String subtitleText;      // 副标题文案
    private String subtitleFont;      // 副标题字体（heading/body）
    private String subtitleFontWeight; // 副标题字重（400/500/600）
    private String subtitleColor;     // 副标题颜色
}
