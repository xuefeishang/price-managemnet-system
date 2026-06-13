package com.pricemanagement.controller;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.ProductAnnualBudgetDTO;
import com.pricemanagement.dto.ProductAnnualBudgetRequest;
import com.pricemanagement.dto.ProductAnnualBudgetSummaryDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.service.ProductAnnualBudgetService;
import com.pricemanagement.util.OperationLogHelper;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/product-budgets")
@RequiredArgsConstructor
public class ProductAnnualBudgetController {

    private final ProductAnnualBudgetService budgetService;
    private final OperationLogHelper operationLogHelper;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<ProductAnnualBudgetSummaryDTO> listBudgets(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) CommonStatus status) {
        return Result.success("获取年度预算成功", budgetService.listBudgets(year, keyword, categoryId, status));
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<ProductAnnualBudgetDTO> getBudget(
            @PathVariable Long productId,
            @RequestParam(required = false) Integer year) {
        try {
            return Result.success("获取产品年度预算成功", budgetService.getBudget(productId, year));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage().contains("不存在") ? 404 : 400, e.getMessage());
        }
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<ProductAnnualBudgetSummaryDTO> saveBudgets(@RequestBody ProductAnnualBudgetRequest request) {
        try {
            ProductAnnualBudgetSummaryDTO result = budgetService.saveBudgets(request, SecurityUtils.getCurrentUserId());
            operationLogHelper.logSuccess("预算管理", OperationLog.OperationType.UPDATE,
                    "保存年度预算", "year=" + result.getBudgetYear() + ", count=" + (request.getItems() == null ? 0 : request.getItems().size()));
            return Result.success("保存年度预算成功", result);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("预算管理", OperationLog.OperationType.UPDATE,
                    "保存年度预算失败", request == null ? "" : "year=" + request.getBudgetYear(), e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }
}
