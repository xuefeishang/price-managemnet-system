package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "price_draft_batch", indexes = {
        @Index(name = "idx_price_draft_batch_date_status", columnList = "effective_date, status"),
        @Index(name = "idx_price_draft_batch_created_by", columnList = "created_by")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PriceDraftBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DraftStatus status = DraftStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType = SourceType.MANUAL;

    @Column(name = "product_scope_snapshot", columnDefinition = "TEXT")
    private String productScopeSnapshot;

    @Column(name = "item_count")
    private Integer itemCount = 0;

    @Column(name = "saved_item_count")
    private Integer savedItemCount = 0;

    @Column(name = "last_modified_by")
    private Long lastModifiedBy;

    @Column(name = "published_time")
    private LocalDateTime publishedTime;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "remark", length = 500)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public enum DraftStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        PUBLISHING,
        PUBLISHED,
        CANCELLED
    }

    public enum SourceType {
        MANUAL,
        SCHEDULED
    }
}
