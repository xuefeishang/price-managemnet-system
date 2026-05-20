package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.pricemanagement.constants.CommonStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 样式预设实体
 * 存储色彩方案、布局方案、字号预设等
 */
@Data
@Entity
@Table(name = "sys_style_preset", indexes = {
    @Index(name = "idx_preset_type", columnList = "preset_type"),
    @Index(name = "idx_preset_status", columnList = "status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_preset_type_key", columnNames = {"preset_type", "preset_key"})
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SysStylePreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preset_type", nullable = false, length = 50)
    private String presetType;

    @Column(name = "preset_key", nullable = false, length = 100)
    private String presetKey;

    @Column(name = "preset_name", nullable = false, length = 200)
    private String presetName;

    @Column(name = "preset_description", length = 500)
    private String presetDescription;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommonStatus status = CommonStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

}
