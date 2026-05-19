package com.pricemanagement.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 趋势分析DTO
 */
@Data
public class TrendAnalysisDTO {
    /** 时间范围标签 */
    private String rangeLabel;

    /** 天数 */
    private int days;

    /** 日期列表 */
    private List<String> dates;

    /** 各产品趋势数据 Map<productId, List<价格>> */
    private Map<Long, List<Double>> productTrends;

    /** 整体平均趋势 */
    private List<Double> avgTrend;
}
