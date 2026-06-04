package com.pricemanagement.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PriceDraftSaveRequest {
    private Long batchId;
    private Long batchVersion;
    private LocalDate effectiveDate;
    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Long basePriceId;
        private Long basePriceVersion;
        private BigDecimal originalPrice;
        private BigDecimal currentPrice;
        private BigDecimal costPrice;
        private BigDecimal budgetPrice;
        private LocalDate effectiveDate;
        private LocalDate expiryDate;
        private String unit;
        private String priceSpec;
    }
}
