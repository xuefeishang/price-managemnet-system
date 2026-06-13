package com.pricemanagement.dto;

import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.ProductAnnualBudget;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ProductAnnualBudgetDTO {
    private Long id;
    private Long version;
    private Long productId;
    private String productName;
    private String productCode;
    private String specification;
    private Long categoryId;
    private String categoryName;
    private String unit;
    private String currency;
    private Integer budgetYear;
    private BigDecimal budgetPrice;
    private BigDecimal latestPrice;
    private LocalDateTime updatedTime;
    private boolean configured;

    public static ProductAnnualBudgetDTO of(Product product, Integer year, ProductAnnualBudget budget, BigDecimal latestPrice) {
        return ProductAnnualBudgetDTO.builder()
                .id(budget != null ? budget.getId() : null)
                .version(budget != null ? budget.getVersion() : null)
                .productId(product.getId())
                .productName(product.getName())
                .productCode(product.getCode())
                .specification(product.getSpecs())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .unit(product.getUnit())
                .currency(product.getCurrency())
                .budgetYear(year)
                .budgetPrice(budget != null ? budget.getBudgetPrice() : null)
                .latestPrice(latestPrice)
                .updatedTime(budget != null ? budget.getUpdatedTime() : null)
                .configured(budget != null && budget.getBudgetPrice() != null)
                .build();
    }
}
