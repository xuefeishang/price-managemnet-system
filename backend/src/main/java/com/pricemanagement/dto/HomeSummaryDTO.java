package com.pricemanagement.dto;

import lombok.Data;

/**
 * 首页摘要统计DTO
 */
@Data
public class HomeSummaryDTO {
    /** 产品总数 */
    private int totalProducts;

    /** 今日已更新价格数 */
    private int priceUpdatedToday;

    /** 平均价格变动百分比 */
    private double avgPriceChange;

    /** 上涨产品数 */
    private int risingCount;

    /** 下跌产品数 */
    private int fallingCount;

    /** 持平产品数 */
    private int flatCount;
}
