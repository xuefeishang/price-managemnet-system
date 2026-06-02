package com.pricemanagement.controller;

import com.pricemanagement.dto.ApiCallLogDTO;
import com.pricemanagement.dto.ApiCallLogStatisticsDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.service.ApiCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/api-call-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ApiCallLogController {

    private final ApiCallLogService apiCallLogService;

    @GetMapping
    public Result<Page<ApiCallLogDTO>> query(@RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String appId,
                                             @RequestParam(required = false) String authResult,
                                             @RequestParam(required = false) Integer statusCode,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success("获取外部API调用日志成功",
                apiCallLogService.query(page, size, appId, authResult, statusCode, startTime, endTime));
    }

    @GetMapping("/statistics")
    public Result<ApiCallLogStatisticsDTO> statistics(@RequestParam(required = false)
                                                      @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success("获取外部API调用统计成功", apiCallLogService.statistics(startTime, endTime));
    }
}
