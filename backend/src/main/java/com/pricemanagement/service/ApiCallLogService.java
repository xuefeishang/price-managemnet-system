package com.pricemanagement.service;

import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.dto.ApiCallLogDTO;
import com.pricemanagement.dto.ApiCallLogStatisticsDTO;
import com.pricemanagement.entity.ApiCallLog;
import com.pricemanagement.repository.ApiCallLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCallLogService {

    private final ApiCallLogRepository callLogRepository;
    private final ApiKeyProperties properties;
    private final NotificationEventService notificationEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(ApiCallLog callLog) {
        try {
            if (callLog == null) {
                return;
            }
            if (!shouldLog(callLog)) {
                return;
            }
            callLog.setQueryString(truncate(callLog.getQueryString(), properties.getLog().getMaxQueryLength()));
            callLog.setErrorMessage(truncate(callLog.getErrorMessage(), properties.getLog().getMaxErrorMessageLength()));
            ApiCallLog saved = callLogRepository.save(callLog);
            notifyApiWarning(saved);
        } catch (Exception ex) {
            log.warn("Failed to save external API call log: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<ApiCallLogDTO> query(int page, int size, String appId, String authResult,
                                     Integer statusCode, LocalDateTime startTime, LocalDateTime endTime) {
        Specification<ApiCallLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (appId != null && !appId.isBlank()) {
                predicates.add(cb.like(root.get("appId"), "%" + appId.trim() + "%"));
            }
            if (authResult != null && !authResult.isBlank()) {
                predicates.add(cb.equal(root.get("authResult"), authResult));
            }
            if (statusCode != null) {
                predicates.add(cb.equal(root.get("statusCode"), statusCode));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("requestTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("requestTime"), endTime));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "requestTime"));
        return callLogRepository.findAll(spec, pageable).map(this::toDTO);
    }

    @Transactional(readOnly = true)
    public ApiCallLogStatisticsDTO statistics(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusDays(1);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        ApiCallLogStatisticsDTO dto = new ApiCallLogStatisticsDTO();
        dto.setTotalCalls(callLogRepository.countTotal(start, end));
        for (Object[] row : callLogRepository.countByAuthResult(start, end)) {
            dto.getAuthResultCount().put((String) row[0], (Long) row[1]);
        }
        return dto;
    }

    private ApiCallLogDTO toDTO(ApiCallLog log) {
        ApiCallLogDTO dto = new ApiCallLogDTO();
        dto.setId(log.getId());
        dto.setApiKeyId(log.getApiKeyId());
        dto.setAppId(log.getAppId());
        dto.setEndpoint(log.getEndpoint());
        dto.setQueryString(log.getQueryString());
        dto.setMethod(log.getMethod());
        dto.setPermissionCode(log.getPermissionCode());
        dto.setStatusCode(log.getStatusCode());
        dto.setResponseTime(log.getResponseTime());
        dto.setIpAddress(log.getIpAddress());
        dto.setRequestTime(log.getRequestTime());
        dto.setRequestBodyHash(log.getRequestBodyHash());
        dto.setNonce(log.getNonce());
        dto.setAuthResult(log.getAuthResult());
        dto.setErrorMessage(log.getErrorMessage());
        dto.setCreatedTime(log.getCreatedTime());
        return dto;
    }

    @Scheduled(cron = "0 15 3 * * ?")
    @Transactional
    public void cleanupExpiredLogs() {
        int retentionDays = properties.getLog().getRetentionDays();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long deleted = callLogRepository.deleteByCreatedTimeBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned {} external API call logs before {}", deleted, cutoff);
        }
    }

    private boolean shouldLog(ApiCallLog callLog) {
        if (callLog == null || "SUCCESS".equals(callLog.getAuthResult())) {
            return true;
        }
        double sampleRate = properties.getLog().getAuthFailureSampleRate();
        if (sampleRate >= 1.0) {
            return true;
        }
        if (sampleRate <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private void notifyApiWarning(ApiCallLog callLog) {
        if (callLog == null) {
            return;
        }
        boolean authFailed = callLog.getAuthResult() != null && !"SUCCESS".equals(callLog.getAuthResult());
        boolean serverOrLimitError = callLog.getStatusCode() != null
                && (callLog.getStatusCode() == 429 || callLog.getStatusCode() >= 500);
        if (authFailed || serverOrLimitError) {
            notificationEventService.apiLimitWarning(
                    callLog.getAppId(),
                    callLog.getEndpoint(),
                    callLog.getErrorMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
