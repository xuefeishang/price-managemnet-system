package com.pricemanagement.controller.external;

import com.pricemanagement.dto.ExternalPriceDTO;
import com.pricemanagement.dto.PriceTrendDTO;
import com.pricemanagement.dto.PriceWithStatsDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.PriceHistory;
import com.pricemanagement.service.PriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/external/v1")
@RequiredArgsConstructor
public class ExternalPriceController {

    private static final Long EXTERNAL_APPLICANT_ID = 0L;

    private final PriceService priceService;

    @GetMapping("/products/{productId}/price-history")
    @PreAuthorize("hasAuthority('API_price:read')")
    public Result<List<PriceHistory>> getProductPriceHistory(@PathVariable Long productId) {
        return Result.success("获取价格历史成功", priceService.getProductPriceHistory(productId));
    }

    @GetMapping("/products/{productId}/current-price")
    @PreAuthorize("hasAuthority('API_price:read')")
    public Result<ExternalPriceDTO> getCurrentPrice(@PathVariable Long productId) {
        return priceService.getCurrentPriceByProductId(productId)
                .map(ExternalPriceDTO::from)
                .map(price -> Result.success("获取当前价格成功", price))
                .orElse(Result.success("暂无价格信息", null));
    }

    @GetMapping("/prices/by-date")
    @PreAuthorize("hasAuthority('API_price:read')")
    public Result<List<ExternalPriceDTO>> getPricesByDate(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        List<ExternalPriceDTO> prices = priceService.getValidPricesByDate(target).stream()
                .map(ExternalPriceDTO::from)
                .toList();
        return Result.success("获取价格列表成功", prices);
    }

    @GetMapping("/prices/by-date-with-stats")
    @PreAuthorize("hasAuthority('API_price:read')")
    public Result<List<PriceWithStatsDTO>> getPricesByDateWithStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return Result.success("获取价格列表成功", priceService.getValidPricesWithStatsByDate(target));
    }

    @GetMapping("/products/{productId}/price-by-date")
    @PreAuthorize("hasAuthority('API_price:read')")
    public Result<ExternalPriceDTO> getPriceByDate(
            @PathVariable Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return priceService.getValidPriceByProductIdAndDate(productId, target)
                .map(ExternalPriceDTO::from)
                .map(price -> Result.success("获取价格成功", price))
                .orElse(Result.success("该日期暂无价格信息", null));
    }

    @GetMapping("/products/{productId}/price-trend")
    @PreAuthorize("hasAuthority('API_price:read')")
    public Result<List<PriceTrendDTO>> getPriceTrend(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success("获取价格走势成功", priceService.getPriceTrend(productId, days, startDate, endDate));
    }

    @PostMapping("/products/{productId}/prices")
    @PreAuthorize("hasAuthority('API_price:write')")
    public Result<ExternalPriceDTO> addProductPrice(@PathVariable Long productId, @RequestBody Price price) {
        price.setId(null);
        price.setVersion(null);
        price.setCreatedTime(null);
        price.setCreatedBy(EXTERNAL_APPLICANT_ID);
        return Result.success("添加价格成功",
                ExternalPriceDTO.from(priceService.addProductPrice(productId, price, EXTERNAL_APPLICANT_ID)));
    }

    @PutMapping("/prices/{id}")
    @PreAuthorize("hasAuthority('API_price:write')")
    public Result<ExternalPriceDTO> updatePrice(@PathVariable Long id, @RequestBody Price price) {
        return Result.success("更新价格成功",
                ExternalPriceDTO.from(priceService.updatePrice(id, price, EXTERNAL_APPLICANT_ID)));
    }
}
