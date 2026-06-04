package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "price_publish_log", indexes = {
        @Index(name = "idx_price_publish_batch", columnList = "batch_id"),
        @Index(name = "idx_price_publish_date", columnList = "effective_date")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PricePublishLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "publish_type", nullable = false, length = 20)
    private PublishType publishType = PublishType.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PublishStatus status = PublishStatus.SUCCESS;

    @Column(name = "total_count")
    private Integer totalCount = 0;

    @Column(name = "success_count")
    private Integer successCount = 0;

    @Column(name = "fail_count")
    private Integer failCount = 0;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    public enum PublishType {
        MANUAL,
        SCHEDULED
    }

    public enum PublishStatus {
        SUCCESS,
        FAILED,
        PARTIAL
    }
}
