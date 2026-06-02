package com.pricemanagement.dto;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.Product;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ExternalProductDTO {
    private Long id;
    private String code;
    private String name;
    private BigDecimal sellingPrice;
    private BigDecimal budgetPrice;
    private Long categoryId;
    private String categoryName;
    private String categoryCode;
    private CommonStatus status;
    private String description;
    private String specs;
    private String originIds;
    private String customerIds;
    private String unit;
    private Integer sortOrder;
    private Boolean showOnHome;
    private String currency;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public static ExternalProductDTO from(Product product) {
        ExternalProductDTO dto = new ExternalProductDTO();
        dto.setId(product.getId());
        dto.setCode(product.getCode());
        dto.setName(product.getName());
        dto.setSellingPrice(product.getSellingPrice());
        dto.setBudgetPrice(product.getBudgetPrice());
        if (product.getCategory() != null) {
            dto.setCategoryId(product.getCategory().getId());
            dto.setCategoryName(product.getCategory().getName());
            dto.setCategoryCode(product.getCategory().getCode());
        }
        dto.setStatus(product.getStatus());
        dto.setDescription(product.getDescription());
        dto.setSpecs(product.getSpecs());
        dto.setOriginIds(product.getOriginIds());
        dto.setCustomerIds(product.getCustomerIds());
        dto.setUnit(product.getUnit());
        dto.setSortOrder(product.getSortOrder());
        dto.setShowOnHome(product.getShowOnHome());
        dto.setCurrency(product.getCurrency());
        dto.setCreatedTime(product.getCreatedTime());
        dto.setUpdatedTime(product.getUpdatedTime());
        return dto;
    }
}
