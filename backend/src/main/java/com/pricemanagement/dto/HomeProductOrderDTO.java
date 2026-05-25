package com.pricemanagement.dto;

import com.pricemanagement.constants.CommonStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HomeProductOrderDTO {

    private CategoryOrderItem category;
    private String virtualKey;
    private String name;
    private List<ProductOrderItem> products;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryOrderItem {
        private Long id;
        private String name;
        private String code;
        private Integer sortOrder;
        private CommonStatus status;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductOrderItem {
        private Long id;
        private String name;
        private String code;
        private String specs;
        private String originIds;
        private Integer sortOrder;
        private Boolean showOnHome;
        private CommonStatus status;
        private String unit;
        private String currency;
        private Long categoryId;
    }
}
