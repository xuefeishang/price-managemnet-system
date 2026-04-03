package com.pricemanagement.controller;

import com.pricemanagement.dto.LogStatistics;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    /**
     * 分页查询操作日志
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<OperationLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String operationModule,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        Page<OperationLog> logs = operationLogService.getLogs(page, size, username, operationType, operationModule, startTime, endTime);
        return Result.success("获取操作日志成功", logs);
    }

    /**
     * 获取最近的操作日志
     */
    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<OperationLog>> getRecentLogs(@RequestParam(defaultValue = "10") int limit) {
        List<OperationLog> logs = operationLogService.getRecentLogs(limit);
        return Result.success("获取最近操作日志成功", logs);
    }

    /**
     * 根据用户ID查询操作日志
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<OperationLog>> getLogsByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<OperationLog> logs = operationLogService.getLogsByUserId(userId, page, size);
        return Result.success("获取用户操作日志成功", logs);
    }

    /**
     * 根据操作类型查询日志
     */
    @GetMapping("/type/{operationType}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<OperationLog>> getLogsByType(
            @PathVariable String operationType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<OperationLog> logs = operationLogService.getLogsByType(operationType, page, size);
        return Result.success("获取操作日志成功", logs);
    }

    /**
     * 获取日志统计信息（默认最近30天）
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<LogStatistics> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        // 默认最近30天
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(30);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        LogStatistics statistics = operationLogService.getStatistics(startTime, endTime);
        return Result.success("获取统计信息成功", statistics);
    }

    /**
     * 获取月度报表
     */
    @GetMapping("/reports/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getMonthlyReport(
            @RequestParam(defaultValue = "2026") int year,
            @RequestParam(defaultValue = "4") int month) {
        Map<String, Object> report = operationLogService.getMonthlyReport(year, month);
        return Result.success("获取月度报表成功", report);
    }

    /**
     * 获取年度报表
     */
    @GetMapping("/reports/yearly")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getYearlyReport(
            @RequestParam(defaultValue = "2026") int year) {
        Map<String, Object> report = operationLogService.getYearlyReport(year);
        return Result.success("获取年度报表成功", report);
    }
}
