
package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "price", indexes = {
    @Index(name = "idx_price_product", columnList = "product_id"),
    @Index(name = "idx_price_effective", columnList = "effective_date, expiry_date")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_product_effective_date", columnNames = {"product_id", "effective_date"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(name = "original_price", precision = 15, scale = 4)
    private BigDecimal originalPrice;

    @Column(name = "current_price", precision = 15, scale = 4, nullable = false)
    private BigDecimal currentPrice;

    @Column(name = "cost_price", precision = 15, scale = 4)
    private BigDecimal costPrice;

    @Column(name = "budget_price", precision = 15, scale = 4)
    private BigDecimal budgetPrice;  // 预算价格（按日期存储，支持跨年保留历史预算）

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "unit", length = 50)
    private String unit;  // 单位：元/吨、元/吨度、元/克、元/千克、万元/吨 等

    @Column(name = "price_spec", length = 200)
    private String priceSpec;  // 价格规格说明

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "remark", length = 500)
    private String remark;  // 备注（用于存储待审批标记等）

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
