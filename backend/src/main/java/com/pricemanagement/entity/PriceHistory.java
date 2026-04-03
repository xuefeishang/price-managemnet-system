
package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "price_history", indexes = {
    @Index(name = "idx_history_product", columnList = "product_id"),
    @Index(name = "idx_history_price", columnList = "price_id"),
    @Index(name = "idx_history_time", columnList = "changed_time")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "price_id")
    private Long priceId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "old_price", precision = 15, scale = 4)
    private BigDecimal oldPrice;

    @Column(name = "new_price", precision = 15, scale = 4)
    private BigDecimal newPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 30)
    private ChangeType changeType;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "changed_time", nullable = false)
    @CreationTimestamp
    private LocalDateTime changedTime;

    @Column(columnDefinition = "TEXT")
    private String remark;

    public enum ChangeType {
        CREATE,     // 新建价格
        UPDATE,     // 更新价格
        DELETE      // 删除价格
    }
}
