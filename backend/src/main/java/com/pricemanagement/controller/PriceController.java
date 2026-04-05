
package com.pricemanagement.controller;

import com.pricemanagement.dto.PriceWithStatsDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.PriceHistory;
import com.pricemanagement.service.PriceService;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    @GetMapping("/products/{productId}/price-history")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<PriceHistory>> getProductPriceHistory(@PathVariable Long productId) {
        List<PriceHistory> historyList = priceService.getProductPriceHistory(productId);
        return Result.success("获取价格历史成功", historyList);
    }

    /**
     * 获取产品的当前价格
     */
    @GetMapping("/products/{productId}/current-price")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Price> getCurrentPrice(@PathVariable Long productId) {
        return priceService.getCurrentPriceByProductId(productId)
                .map(price -> Result.success("获取当前价格成功", price))
                .orElse(Result.success("暂无价格信息", null));
    }

    /**
     * 按日期获取所有产品的价格（用于价格维护）
     * @param date 日期，默认为当天
     */
    @GetMapping("/prices/by-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<Price>> getPricesByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        List<Price> prices = priceService.getValidPricesByDate(date);
        return Result.success("获取价格列表成功", prices);
    }

    /**
     * 按日期获取所有产品的价格（带昨日价格和月均价，用于价格维护优化）
     * @param date 日期，默认为当天
     */
    @GetMapping("/prices/by-date-with-stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<PriceWithStatsDTO>> getPricesByDateWithStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        List<PriceWithStatsDTO> result = priceService.getValidPricesWithStatsByDate(date);
        return Result.success("获取价格列表成功", result);
    }

    /**
     * 获取某产品在指定日期的价格
     */
    @GetMapping("/products/{productId}/price-by-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Price> getPriceByDate(
            @PathVariable Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        return priceService.getValidPriceByProductIdAndDate(productId, date)
                .map(price -> Result.success("获取价格成功", price))
                .orElse(Result.success("该日期暂无价格信息", null));
    }

    /**
     * 获取某产品昨日的价格
     */
    @GetMapping("/products/{productId}/yesterday-price")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Price> getYesterdayPrice(@PathVariable Long productId) {
        return priceService.getYesterdayPriceByProductId(productId)
                .map(price -> Result.success("获取昨日价格成功", price))
                .orElse(Result.success("暂无昨日价格信息", null));
    }

    /**
     * 获取某产品当月的平均价格
     */
    @GetMapping("/products/{productId}/monthly-average-price")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Double> getMonthlyAveragePrice(@PathVariable Long productId) {
        Double avgPrice = priceService.getMonthlyAveragePriceByProductId(productId);
        return Result.success("获取月均价格成功", avgPrice);
    }

    @PostMapping("/products/{productId}/prices")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Price> addProductPrice(@PathVariable Long productId,
                                          @RequestBody Price price) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Price savedPrice = priceService.addProductPrice(productId, price, userId);
            if ("PENDING_APPROVAL".equals(savedPrice.getRemark())) {
                return Result.success("价格已提交，等待审批", savedPrice);
            }
            return Result.success("添加价格成功", savedPrice);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @PutMapping("/prices/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Price> updatePrice(@PathVariable Long id,
                                      @RequestBody Price price) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Price updatedPrice = priceService.updatePrice(id, price, userId);
            if ("PENDING_APPROVAL".equals(updatedPrice.getRemark())) {
                return Result.success("价格已提交，等待审批", updatedPrice);
            }
            return Result.success("更新价格成功", updatedPrice);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
