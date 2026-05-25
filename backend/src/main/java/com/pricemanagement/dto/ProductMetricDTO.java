package com.pricemanagement.dto;

import lombok.Data;

/**
 * 产品指标DTO（用于首页卡片展示）
 */
@Data
public class ProductMetricDTO {
    /** 产品ID */
    private Long productId;

    /** 产品名称 */
    private String productName;

    /** 产品规格 */
    private String specs;

    /** 产地ID列表（JSON字符串，前端通过字典服务解析显示名称） */
    private String originIds;

    /** 当前价格 */
    private Double currentPrice;

    /** 前一日价格 */
    private Double previousPrice;

    /** 价格变动方向: up, down, flat */
    private String priceDirection;

    /** 价格变动值 */
    private Double priceChange;

    /** 价格变动百分比 */
    private Double priceChangePercent;

    /** 格式化的价格变动（如 +3.2%） */
    private String formattedChange;

    /** 货币符号 */
    private String currencySymbol;

    /** 单位 */
    private String unit;

    /** 更新时间 */
    private String updateTime;

    /** 是否重点产品 */
    private Boolean featured;
}
