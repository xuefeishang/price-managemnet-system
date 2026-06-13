package com.pricemanagement.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProductAnnualBudgetSummaryDTO {
    private Integer budgetYear;
    private long totalProducts;
    private long configuredProducts;
    private long pendingProducts;
    private List<ProductAnnualBudgetDTO> items;
}
