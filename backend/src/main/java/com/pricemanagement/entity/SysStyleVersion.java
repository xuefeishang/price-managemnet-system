package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 样式版本历史实体
 * 存储样式配置的版本快照，支持回滚功能
 */
@Data
@Entity
@Table(name = "sys_style_version", indexes = {
    @Index(name = "idx_style_version_created_time", columnList = "created_time"),
    @Index(name = "idx_style_version_changed_by", columnList = "changed_by")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SysStyleVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_no", nullable = false, unique = true, length = 50)
    private String versionNo;

    @Column(name = "config_snapshot", nullable = false, columnDefinition = "TEXT")
    private String configSnapshot;

    @Column(name = "change_summary", length = 500)
    private String changeSummary;

    @Column(name = "changed_by")
    private Long changedBy;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

}
