package com.pricemanagement.controller;

import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.PriceQueryRowDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog.OperationType;
import com.pricemanagement.service.PriceQueryService;
import com.pricemanagement.util.OperationLogHelper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/price-query")
@RequiredArgsConstructor
public class PriceQueryController {

    private final PriceQueryService priceQueryService;
    private final OperationLogHelper operationLogHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Page<PriceQueryRowDTO>> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CommonStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection) {
        Page<PriceQueryRowDTO> rows = priceQueryService.query(date, keyword, categoryId, status,
                page, size, sortBy, sortDirection);
        return Result.success("获取价格查询列表成功", rows);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    @OperationLog(module = "价格查询", type = OperationType.EXPORT, description = "导出日常价格查询")
    public void export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CommonStatus status,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String sortDirection,
            HttpServletResponse response) throws IOException {
        try {
            priceQueryService.export(date, keyword, categoryId, status, sortBy, sortDirection, response);
            operationLogHelper.logSuccess("价格查询", OperationType.EXPORT,
                    "导出日常价格查询", buildExportParams(date, keyword, categoryId, status));
        } catch (IOException | RuntimeException e) {
            operationLogHelper.logError("价格查询", OperationType.EXPORT,
                    "导出日常价格查询失败", buildExportParams(date, keyword, categoryId, status), e.getMessage());
            throw e;
        }
    }

    private String buildExportParams(LocalDate date, String keyword, Long categoryId, CommonStatus status) {
        return "date=" + date + ", keyword=" + keyword + ", categoryId=" + categoryId + ", status=" + status;
    }
}
