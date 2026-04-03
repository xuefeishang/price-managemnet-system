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
@Table(name = "approval_node")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ApprovalNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "node_order", nullable = false)
    private Integer nodeOrder;

    @Column(name = "node_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NodeType nodeType;

    @Column(name = "approver_role", length = 20)
    private String approverRole;

    @Column(name = "is_required", nullable = false)
    private Boolean isRequired;

    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        if (isRequired == null) {
            isRequired = true;
        }
    }

    public enum NodeType {
        APPROVER,  // 审批节点
        NOTIFIER    // 知会节点
    }
}
