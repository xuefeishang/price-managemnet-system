package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_mini_program_template_history", indexes = {
        @Index(name = "idx_notification_mini_template_history_template", columnList = "template_id_ref"),
        @Index(name = "idx_notification_mini_template_history_type", columnList = "notification_type")
})
public class NotificationMiniProgramTemplateHistory {

    public enum TemplateAction {
        CREATE,
        UPDATE,
        TEST,
        PUBLISH,
        DISABLE,
        ROLLBACK
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id_ref", nullable = false)
    private Long templateIdRef;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private TemplateAction action;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "status_before", length = 20)
    private String statusBefore;

    @Column(name = "status_after", length = 20)
    private String statusAfter;

    @Column(name = "template_id_masked", length = 120)
    private String templateIdMasked;

    @Column(name = "message", length = 500)
    private String message;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
