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

    private BigDecimal yesterdayPrice;

    private BigDecimal changeAmount;

    private BigDecimal changePercent;

    private BigDecimal budgetPrice;

    private BigDecimal monthlyAveragePrice;

    private BigDecimal latestPrice;

    private Boolean hasPrice;
}
