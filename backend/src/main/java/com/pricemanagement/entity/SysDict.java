package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pricemanagement.constants.CommonStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_dict", indexes = {
    @Index(name = "idx_dict_category", columnList = "category"),
    @Index(name = "idx_dict_category_key", columnList = "category, dict_key", unique = true),
    @Index(name = "idx_dict_status", columnList = "status"),
    @Index(name = "idx_dict_sort", columnList = "sort_order")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SysDict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "dict_key", nullable = false, length = 100)
    private String dictKey;

    @Column(name = "dict_value", nullable = false, length = 200)
    private String dictValue;

    @Column(name = "extra_value", length = 200)
    private String extraValue;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommonStatus status = CommonStatus.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String remark;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

}
