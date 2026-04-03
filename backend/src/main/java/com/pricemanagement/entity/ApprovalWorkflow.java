package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "approval_workflow")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ApprovalWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_name", nullable = false, length = 100)
    private String workflowName;

    @Column(name = "workflow_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private WorkflowType workflowType;

    @Column(name = "approval_level", nullable = false)
    private Integer approvalLevel;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    public enum WorkflowType {
        PRICE_CHANGE,    // 价格变更
        PRODUCT_CREATE   // 产品创建
    }
}
