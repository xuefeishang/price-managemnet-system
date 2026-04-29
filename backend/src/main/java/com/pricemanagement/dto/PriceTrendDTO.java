package com.pricemanagement.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 价格走势数据点DTO
 */
@Data
public class PriceTrendDTO {
    /** 生效日期 */
    private LocalDate date;
    /** 当日售价 */
    private BigDecimal currentPrice;
    /** 预算价格 */
    private BigDecimal budgetPrice;

    public PriceTrendDTO() {}

    public PriceTrendDTO(LocalDate date, BigDecimal currentPrice, BigDecimal budgetPrice) {
        this.date = date;
        this.currentPrice = currentPrice;
        this.budgetPrice = budgetPrice;
    }
}
