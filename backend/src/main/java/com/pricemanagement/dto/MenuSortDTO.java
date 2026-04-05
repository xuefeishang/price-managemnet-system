package com.pricemanagement.dto;

import lombok.Data;

@Data
public class MenuSortDTO {
    private Long id;
    private Long parentId;
    private Integer sortOrder;
}
