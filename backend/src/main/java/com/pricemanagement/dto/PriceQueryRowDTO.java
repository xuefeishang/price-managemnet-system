package com.pricemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceQueryRowDTO {

    private Long productId;

    private String productName;

    private Long categoryId;

    private String categoryName;

    private String originIds;

    private String specification;

    private String unit;

    private String currency;

    private LocalDate effectiveDate;

    private BigDecimal currentPrice;

    /**
     * @deprecated v1 兼容字段，语义等同于 {@link #budgetPrice}。
     */
    @Deprecated
    private BigDecimal yesterdayPrice;

    /**
     * @deprecated v1 兼容字段，语义等同于 {@link #budgetChangeAmount}。
     */
    @Deprecated
    private BigDecimal changeAmount;

    /**
     * @deprecated v1 兼容字段，语义等同于 {@link #budgetChangePercent}。
     */
    @Deprecated
    private BigDecimal changePercent;

    private BigDecimal budgetPrice;

    private BigDecimal monthlyAveragePrice;

    private BigDecimal latestPrice;

    private LocalDate latestPriceDate;

    private BigDecimal previousPrice;

    private LocalDate previousPriceDate;

    private BigDecimal previousChangeAmount;

    private BigDecimal previousChangePercent;

    private BigDecimal budgetChangeAmount;

    private BigDecimal budgetChangePercent;

    private BigDecimal previousMonthAveragePrice;

    private BigDecimal monthOverMonthPercent;

    private BigDecimal lastYearSamePeriodAveragePrice;

    private BigDecimal yearOverYearPercent;

    private Boolean hasPrice;
}
