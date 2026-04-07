
package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

    @Column(nullable = false, length = 200)
    private String name;


    @Column(length = 100)
    private String code;  // 产品编码

    @Column(name = "selling_price", precision = 15, scale = 4)
    private BigDecimal sellingPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private ProductCategory category;

    /**
     * 前端传递的分类ID，用于接收前端参数
     * 此字段不参与数据库映射，由服务层转换为category对象
     */
    @Transient
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.ACTIVE;

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

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum ProductStatus {
        ACTIVE,     // 启用
        INACTIVE    // 禁用
    }
}
