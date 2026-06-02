package com.pricemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalApiServiceStatusDTO {
    private boolean deploymentEnabled;
    private boolean runtimeEnabled;
    private boolean available;
    private String message;
}
