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
@Table(name = "approval_record")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "action", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Action action;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "action_time", nullable = false, updatable = false)
    private LocalDateTime actionTime;

    @PrePersist
    protected void onCreate() {
        actionTime = LocalDateTime.now();
    }

    public enum Action {
        APPROVE,  // 通过
        REJECT     // 拒绝
    }
}
