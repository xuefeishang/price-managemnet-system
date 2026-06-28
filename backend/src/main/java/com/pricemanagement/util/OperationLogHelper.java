package com.pricemanagement.util;

import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.service.ClientIpResolver;
import com.pricemanagement.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作日志帮助类
 * 用于记录用户操作日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationLogHelper {

    private final OperationLogService operationLogService;
    private final ClientIpResolver clientIpResolver;

    /**
     * 记录操作日志
     */
    public void log(String module, OperationLog.OperationType type, String description) {
        log(module, type, description, null, null);
    }

    /**
     * 记录操作日志
     */
    public void log(String module, OperationLog.OperationType type, String description, String requestParams) {
        log(module, type, description, requestParams, null);
    }

    /**
     * 记录操作日志
     */
    public void log(String module, OperationLog.OperationType type, String description, String requestParams, String errorMessage) {
        try {
            OperationLog operationLog = buildBaseLog(module, type, description);
            operationLog.setRequestParams(SensitiveDataMasker.mask(requestParams));
            applyUsernameFallback(operationLog, type, requestParams);
            operationLog.setErrorMessage(SensitiveDataMasker.mask(errorMessage));
            operationLog.setResponseCode(errorMessage == null ? "200" : "500");

            operationLogService.log(operationLog);
        } catch (Exception e) {
            log.error("Failed to log operation: type={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 记录成功操作
     */
    public void logSuccess(String module, OperationLog.OperationType type, String description) {
        logSuccess(module, type, description, null);
    }

    /**
     * 记录成功操作
     */
    public void logSuccess(String module, OperationLog.OperationType type, String description, String requestParams) {
        logSuccess(module, type, description, requestParams, null);
    }

    public void logSuccess(String module, OperationLog.OperationType type, String description,
                           String requestParams, Long executionTime) {
        try {
            OperationLog operationLog = buildBaseLog(module, type, description);
            operationLog.setRequestParams(SensitiveDataMasker.mask(requestParams));
            applyUsernameFallback(operationLog, type, requestParams);
            operationLog.setResponseCode("200");
            operationLog.setExecutionTime(executionTime);
            operationLogService.log(operationLog);
        } catch (Exception e) {
            log.error("Failed to log operation: type={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 记录失败操作
     */
    public void logError(String module, OperationLog.OperationType type, String description, String errorMessage) {
        logError(module, type, description, null, errorMessage);
    }

    /**
     * 记录失败操作
     */
    public void logError(String module, OperationLog.OperationType type, String description, String requestParams, String errorMessage) {
        logError(module, type, description, requestParams, errorMessage, "500");
    }

    /**
     * 记录带指定响应码的失败操作
     */
    public void logError(String module, OperationLog.OperationType type, String description,
                         String requestParams, String errorMessage, String responseCode) {
        logError(module, type, description, requestParams, errorMessage, responseCode, null);
    }

    public void logError(String module, OperationLog.OperationType type, String description,
                         String requestParams, String errorMessage, String responseCode, Long executionTime) {
        try {
            OperationLog operationLog = buildBaseLog(module, type, description);
            operationLog.setRequestParams(SensitiveDataMasker.mask(requestParams));
            applyUsernameFallback(operationLog, type, requestParams);
            operationLog.setResponseCode(responseCode);
            operationLog.setErrorMessage(SensitiveDataMasker.mask(errorMessage));
            operationLog.setExecutionTime(executionTime);
            operationLogService.log(operationLog);
        } catch (Exception e) {
            log.error("Failed to log operation: type={}", e.getClass().getSimpleName());
        }
    }

    /**
     * 构建基础日志对象
     */
    private OperationLog buildBaseLog(String module, OperationLog.OperationType type, String description) {
        OperationLog operationLog = new OperationLog();

        // 设置操作信息
        operationLog.setOperationModule(module);
        operationLog.setOperationType(type.name());
        operationLog.setOperationDesc(description);
        operationLog.setOperationTime(LocalDateTime.now());

        // 获取当前用户信息
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !"anonymousUser".equals(authentication.getPrincipal())) {
                operationLog.setUsername(authentication.getName());
                Object details = authentication.getDetails();
                if (details instanceof Long userId) {
                    operationLog.setUserId(userId);
                }
            }
        } catch (Exception e) {
            log.debug("Could not get username: {}", e.getMessage());
        }

        // 获取请求信息
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operationLog.setRequestMethod(request.getMethod());
                operationLog.setRequestUrl(request.getRequestURI());
                operationLog.setIpAddress(clientIpResolver.resolve(request));
                operationLog.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            log.debug("Could not get request info: {}", e.getMessage());
        }

        return operationLog;
    }

    private void applyUsernameFallback(OperationLog operationLog, OperationLog.OperationType type, String requestParams) {
        if (operationLog.getUsername() != null && !operationLog.getUsername().isBlank()) {
            return;
        }
        if (type == OperationLog.OperationType.LOGIN && requestParams != null && !requestParams.isBlank()) {
            operationLog.setUsername(requestParams);
        }
    }

}
