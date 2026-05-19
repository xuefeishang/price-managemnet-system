package com.pricemanagement.dto;

import lombok.Data;
import java.util.List;

/**
 * 首页仪表盘数据DTO
 */
@Data
public class HomeDashboardDTO {
    /** 经营摘要统计 */
    private HomeSummaryDTO summary;

    /** 价格预警列表 */
    private List<PriceAlertDTO> alerts;

    /** 重点产品指标列表 */
    private List<ProductMetricDTO> featuredProducts;

    /** 趋势分析数据 */
    private TrendAnalysisDTO trendAnalysis;
}
