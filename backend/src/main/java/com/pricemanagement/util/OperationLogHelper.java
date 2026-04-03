package com.pricemanagement.util;

import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.entity.User;
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
            operationLog.setRequestParams(requestParams);
            operationLog.setErrorMessage(errorMessage);
            operationLog.setResponseCode(errorMessage == null ? "200" : "500");

            operationLogService.log(operationLog);
        } catch (Exception e) {
            log.error("Failed to log operation: {}", e.getMessage());
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
        try {
            OperationLog operationLog = buildBaseLog(module, type, description);
            operationLog.setRequestParams(requestParams);
            operationLog.setResponseCode("200");
            operationLogService.log(operationLog);
        } catch (Exception e) {
            log.error("Failed to log operation: {}", e.getMessage());
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
        try {
            OperationLog operationLog = buildBaseLog(module, type, description);
            operationLog.setRequestParams(requestParams);
            operationLog.setResponseCode("500");
            operationLog.setErrorMessage(errorMessage);
            operationLogService.log(operationLog);
        } catch (Exception e) {
            log.error("Failed to log operation: {}", e.getMessage());
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
                operationLog.setIpAddress(getClientIp(request));
                operationLog.setUserAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            log.debug("Could not get request info: {}", e.getMessage());
        }

        return operationLog;
    }

    /**
     * 获取客户端IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
