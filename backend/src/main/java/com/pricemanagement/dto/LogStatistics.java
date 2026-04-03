package com.pricemanagement.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class LogStatistics {

    private long totalOperations;
    private long loginCount;
    private long activeUsers;
    private Map<String, Long> operationTypeCount = new HashMap<>();
    private Map<String, Long> moduleCount = new HashMap<>();
    private List<UserActivity> userActivities = new ArrayList<>();
    private Map<String, Long> dailyCount = new LinkedHashMap<>();

    @Data
    public static class UserActivity {
        private String username;
        private Long operationCount;
    }
}
