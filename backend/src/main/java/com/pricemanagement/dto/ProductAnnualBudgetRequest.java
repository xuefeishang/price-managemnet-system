package com.pricemanagement.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductAnnualBudgetRequest {
    private Integer budgetYear;
    private List<Item> items;

    @Data
    public static class Item {
        private Long productId;
        private Long version;
        private BigDecimal budgetPrice;
        private String remark;
    }
}
