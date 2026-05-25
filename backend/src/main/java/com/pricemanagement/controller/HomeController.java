package com.pricemanagement.controller;

import com.pricemanagement.dto.HomeDashboardDTO;
import com.pricemanagement.dto.HomeProductOrderDTO;
import com.pricemanagement.dto.PriceAlertDTO;
import com.pricemanagement.dto.HomeSummaryDTO;
import com.pricemanagement.dto.TrendAnalysisDTO;
import com.pricemanagement.service.HomeDashboardService;
import com.pricemanagement.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 首页仪表盘控制器
 */
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeDashboardService homeDashboardService;

    /**
     * 获取仪表盘数据
     */
    @GetMapping("/dashboard")
    public Result<HomeDashboardDTO> getDashboard(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        HomeDashboardDTO dashboard = homeDashboardService.getDashboardData(targetDate);
        return Result.success("获取仪表盘数据成功", dashboard);
    }

    /**
     * 获取摘要统计
     */
    @GetMapping("/summary")
    public Result<HomeSummaryDTO> getSummary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        HomeSummaryDTO summary = homeDashboardService.getSummaryStats(targetDate);
        return Result.success("获取摘要统计成功", summary);
    }

    /**
     * 获取价格预警
     */
    @GetMapping("/alerts")
    public Result<List<PriceAlertDTO>> getAlerts(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        List<PriceAlertDTO> alerts = homeDashboardService.getPriceAlerts(targetDate);
        return Result.success("获取预警数据成功", alerts);
    }

    /**
     * 获取趋势分析
     */
    @GetMapping("/trend")
    public Result<TrendAnalysisDTO> getTrend(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "30") int days) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        TrendAnalysisDTO trend = homeDashboardService.getTrendAnalysis(targetDate, days);
        return Result.success("获取趋势分析成功", trend);
    }

    /**
     * 获取首页产品列表排序树
     */
    @GetMapping("/product-order")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<HomeProductOrderDTO>> getProductOrder() {
        return Result.success("获取首页产品排序成功", homeDashboardService.getProductOrder());
    }
}
