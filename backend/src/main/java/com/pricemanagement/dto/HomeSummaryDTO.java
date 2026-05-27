package com.pricemanagement.dto;

import lombok.Data;

/**
 * 首页摘要统计DTO
 */
@Data
public class HomeSummaryDTO {
    /** 产品总数 */
    private int totalProducts;

    /** 所选日期已更新报价的产品数 */
    private int priceUpdatedToday;

    /** 当前启用产品覆盖的品类数 */
    private int coveredCategoryCount;

    /** 启用品类总数 */
    private int activeCategoryCount;

    /** 发生价格变化的产品数（上涨 + 下跌） */
    private int changedProductCount;

    /** 平均价格变动百分比 */
    private double avgPriceChange;

    /** 上涨产品数 */
    private int risingCount;

    /** 下跌产品数 */
    private int fallingCount;

    /** 持平产品数 */
    private int flatCount;
}
