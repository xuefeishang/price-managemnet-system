package com.pricemanagement.controller.external;

import com.pricemanagement.dto.*;
import com.pricemanagement.service.HomeDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/external/v1/home")
@RequiredArgsConstructor
public class ExternalHomeController {

    private final HomeDashboardService homeDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('API_home:read')")
    public Result<HomeDashboardDTO> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        return Result.success("获取仪表盘数据成功", homeDashboardService.getDashboardData(targetDate));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('API_home:read')")
    public Result<HomeSummaryDTO> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        return Result.success("获取摘要统计成功", homeDashboardService.getSummaryStats(targetDate));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('API_home:read')")
    public Result<List<PriceAlertDTO>> getAlerts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        return Result.success("获取预警数据成功", homeDashboardService.getPriceAlerts(targetDate));
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('API_home:read')")
    public Result<TrendAnalysisDTO> getTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "30") int days) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        return Result.success("获取趋势分析成功", homeDashboardService.getTrendAnalysis(targetDate, days));
    }

    @GetMapping("/product-order")
    @PreAuthorize("hasAuthority('API_home:read')")
    public Result<List<HomeProductOrderDTO>> getProductOrder() {
        return Result.success("获取首页产品排序成功", homeDashboardService.getProductOrder());
    }
}
