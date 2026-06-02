package com.pricemanagement.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_external_api_endpoint", uniqueConstraints = {
        @UniqueConstraint(name = "uk_external_endpoint", columnNames = {"method", "path_pattern"})
}, indexes = {
        @Index(name = "idx_external_endpoint_permission", columnList = "permission_code"),
        @Index(name = "idx_external_endpoint_status", columnList = "status")
})
public class ExternalApiEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "path_pattern", nullable = false, length = 200)
    private String pathPattern;

    @Column(length = 200)
    private String description;

    @Column(name = "request_example", columnDefinition = "TEXT")
    private String requestExample;

    @Column(name = "response_example", columnDefinition = "TEXT")
    private String responseExample;

    @Column(name = "error_codes", columnDefinition = "TEXT")
    private String errorCodes;

    @Column(name = "usage_notes", columnDefinition = "TEXT")
    private String usageNotes;

    @Column(name = "query_example", columnDefinition = "TEXT")
    private String queryExample;

    @Column(name = "body_example", columnDefinition = "TEXT")
    private String bodyExample;

    @Column(name = "path_params_example", columnDefinition = "TEXT")
    private String pathParamsExample;

    @Column(name = "query_schema", columnDefinition = "TEXT")
    private String querySchema;

    @Column(name = "body_schema", columnDefinition = "TEXT")
    private String bodySchema;

    @Column(name = "path_params_schema", columnDefinition = "TEXT")
    private String pathParamsSchema;

    @Column(name = "success_example", columnDefinition = "TEXT")
    private String successExample;

    @Column(name = "failure_example", columnDefinition = "TEXT")
    private String failureExample;

    @Column(name = "code_notes", columnDefinition = "TEXT")
    private String codeNotes;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;
}
