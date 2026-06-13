
package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.constants.SystemConstants;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product", indexes = {
    @Index(name = "idx_product_category", columnList = "category_id"),
    @Index(name = "idx_product_sort", columnList = "sort_order")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(nullable = false, length = 200)
    private String name;


    @Column(length = 100)
    private String code;  // 产品编码

    @Column(name = "selling_price", precision = 15, scale = 4)
    private BigDecimal sellingPrice;

    @Column(name = "budget_price", precision = 15, scale = 4)
    @JsonIgnore
    private BigDecimal budgetPrice;  // 预算价格

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @com.fasterxml.jackson.annotation.JsonIgnore
    private ProductCategory category;

    /**
     * 用于JSON序列化的分类信息（避免Hibernate代理问题）
     */
    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("category")
    private CategoryInfo categoryInfo;

    /**
     * 分类信息DTO（用于JSON序列化）
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class CategoryInfo {
        private Long id;
        private String name;
        private String code;

        public static CategoryInfo from(ProductCategory category) {
            if (category == null) return null;
            return new CategoryInfo(category.getId(), category.getName(), category.getCode());
        }
    }

    /**
     * 获取用于序列化的分类信息
     */
    public CategoryInfo getCategoryInfo() {
        return CategoryInfo.from(this.category);
    }

    /**
     * 前端传递的分类ID，用于接收前端参数
     * 此字段不参与数据库映射，由服务层转换为category对象
     */
    @Transient
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommonStatus status = CommonStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "specs", columnDefinition = "TEXT")
    private String specs;  // 规格参数

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "origin_ids", length = 500)
    private String originIds;

    @Column(name = "customer_ids", length = 500)
    private String customerIds;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @Column(name = "unit", length = 50)
    private String unit;  // 计量单位：元/吨、万元/吨、元/克、元/千克 等

    @Column(name = "sort_order")
    private Integer sortOrder = 0;  // 排序顺序

    @Column(name = "show_on_home", nullable = false)
    private Boolean showOnHome = false;  // 是否在首页展示

    @Column(name = "currency", length = 20)
    private String currency = SystemConstants.DEFAULT_CURRENCY;  // 计价币种：CNY-人民币、USD-美元、EUR-欧元

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

}

