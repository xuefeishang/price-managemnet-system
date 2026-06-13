package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notification_mini_program_template", indexes = {
        @Index(name = "idx_notification_mini_template_type_status", columnList = "notification_type,status"),
        @Index(name = "idx_notification_mini_template_template_id", columnList = "template_id")
})
public class NotificationMiniProgramTemplate {

    public enum TemplateStatus {
        DRAFT,
        TESTING,
        ACTIVE,
        DISABLED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_type", nullable = false, length = 50)
    private String notificationType;

    @Column(name = "template_id", nullable = false, length = 100)
    private String templateId;

    @Column(name = "page", length = 200)
    private String page;

    @Column(name = "fields_json", nullable = false, columnDefinition = "TEXT")
    private String fieldsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TemplateStatus status = TemplateStatus.DRAFT;

    @Column(name = "last_test_status", length = 20)
    private String lastTestStatus;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "last_test_delivery_id")
    private Long lastTestDeliveryId;

    @Column(name = "last_test_time")
    private LocalDateTime lastTestTime;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_time")
    private LocalDateTime publishedTime;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Version
    @Column(name = "version")
    private Long version;
}
