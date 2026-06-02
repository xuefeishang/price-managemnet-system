package com.pricemanagement.dto;

import com.pricemanagement.entity.Price;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class ExternalPriceDTO {
    private Long id;
    private Long productId;
    private String productName;
    private BigDecimal originalPrice;
    private BigDecimal currentPrice;
    private BigDecimal costPrice;
    private BigDecimal budgetPrice;
    private LocalDate effectiveDate;
    private LocalDate expiryDate;
    private String unit;
    private String priceSpec;
    private String remark;
    private LocalDateTime createdTime;

    public static ExternalPriceDTO from(Price price) {
        if (price == null) {
            return null;
        }
        ExternalPriceDTO dto = new ExternalPriceDTO();
        dto.setId(price.getId());
        if (price.getProduct() != null) {
            dto.setProductId(price.getProduct().getId());
            dto.setProductName(price.getProduct().getName());
        }
        dto.setOriginalPrice(price.getOriginalPrice());
        dto.setCurrentPrice(price.getCurrentPrice());
        dto.setCostPrice(price.getCostPrice());
        dto.setBudgetPrice(price.getBudgetPrice());
        dto.setEffectiveDate(price.getEffectiveDate());
        dto.setExpiryDate(price.getExpiryDate());
        dto.setUnit(price.getUnit());
        dto.setPriceSpec(price.getPriceSpec());
        dto.setRemark(price.getRemark());
        dto.setCreatedTime(price.getCreatedTime());
        return dto;
    }
}
