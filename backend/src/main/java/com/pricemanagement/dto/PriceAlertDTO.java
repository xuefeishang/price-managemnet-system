package com.pricemanagement.dto;

import lombok.Data;

/**
 * 价格预警DTO
 */
@Data
public class PriceAlertDTO {
    /** 产品ID */
    private Long productId;

    /** 产品名称 */
    private String productName;

    /** 产品规格 */
    private String productSpecs;

    /** 预警类型 */
    private String alertType;

    /** 预警消息 */
    private String alertMessage;

    /** 严重程度: info, warning, danger */
    private String severity;

    /** 当前值 */
    private double currentValue;

    /** 阈值 */
    private double threshold;

    /** 变动百分比 */
    private Double changePercent;
}
