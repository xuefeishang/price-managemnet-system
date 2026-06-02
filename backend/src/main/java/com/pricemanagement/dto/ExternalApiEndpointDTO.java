package com.pricemanagement.dto;

import lombok.Data;

@Data
public class ExternalApiEndpointDTO {
    private Long id;
    private String permissionCode;
    private String method;
    private String pathPattern;
    private String description;
    private String requestExample;
    private String responseExample;
    private String errorCodes;
    private String usageNotes;
    private String queryExample;
    private String bodyExample;
    private String pathParamsExample;
    private String querySchema;
    private String bodySchema;
    private String pathParamsSchema;
    private String successExample;
    private String failureExample;
    private String codeNotes;
    private String status;
    private Integer sortOrder;
}
