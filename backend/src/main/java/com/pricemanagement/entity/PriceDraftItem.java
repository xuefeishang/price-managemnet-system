package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "price_draft_item", indexes = {
        @Index(name = "idx_price_draft_item_batch", columnList = "batch_id"),
        @Index(name = "idx_price_draft_item_product", columnList = "product_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_price_draft_item_batch_product", columnNames = {"batch_id", "product_id"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PriceDraftItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(name = "base_price_id")
    private Long basePriceId;

    @Column(name = "base_price_version")
    private Long basePriceVersion;

    @Column(name = "original_price", precision = 15, scale = 4)
    private BigDecimal originalPrice;

    @Column(name = "current_price", precision = 15, scale = 4, nullable = false)
    private BigDecimal currentPrice;

    @Column(name = "cost_price", precision = 15, scale = 4)
    private BigDecimal costPrice;

    @Column(name = "budget_price", precision = 15, scale = 4)
    private BigDecimal budgetPrice;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "price_spec", length = 200)
    private String priceSpec;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 20)
    private ItemStatus itemStatus = ItemStatus.DRAFT;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "published_price_id")
    private Long publishedPriceId;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum ItemStatus {
        DRAFT,
        PUBLISHED,
        SKIPPED
    }
}
