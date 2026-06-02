package com.pricemanagement.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ApiCallLogStatisticsDTO {
    private long totalCalls;
    private Map<String, Long> authResultCount = new HashMap<>();
}
