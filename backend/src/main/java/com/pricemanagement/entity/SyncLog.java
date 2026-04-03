
package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sync_log", indexes = {
    @Index(name = "idx_sync_type", columnList = "sync_type"),
    @Index(name = "idx_sync_status", columnList = "sync_status"),
    @Index(name = "idx_sync_time", columnList = "sync_time")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_type", nullable = false, length = 20)
    private SyncType syncType;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 20)
    private SyncStatus syncStatus = SyncStatus.SUCCESS;

    @Column(name = "sync_time")
    @CreationTimestamp
    private LocalDateTime syncTime;

    @Column(name = "total_count")
    private Integer totalCount;

    @Column(name = "success_count")
    private Integer successCount;

    @Column(name = "error_count")
    private Integer errorCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sync_desc", length = 200)
    private String syncDesc;

    public enum SyncType {
        PRODUCT_SYNC,    // 产品同步
        PRICE_SYNC,      // 价格同步
        FULL_SYNC        // 全量同步
    }

    public enum SyncStatus {
        SUCCESS,         // 成功
        PARTIAL_SUCCESS, // 部分成功
        FAILED,          // 失败
        PROCESSING       // 处理中
    }
}
