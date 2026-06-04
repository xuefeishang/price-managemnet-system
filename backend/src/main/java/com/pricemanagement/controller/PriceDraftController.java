package com.pricemanagement.controller;

import com.pricemanagement.dto.PriceDraftDTO;
import com.pricemanagement.dto.PriceDraftSaveRequest;
import com.pricemanagement.dto.PricePublishResultDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.entity.OperationLog.OperationType;
import com.pricemanagement.entity.PricePublishLog;
import com.pricemanagement.service.PriceDraftService;
import com.pricemanagement.service.PricePublishService;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/price-drafts")
@RequiredArgsConstructor
public class PriceDraftController {

    private final PriceDraftService priceDraftService;
    private final PricePublishService pricePublishService;

    @GetMapping("/by-date")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<PriceDraftDTO> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success("获取价格草稿成功", priceDraftService.getActiveDraftByDate(date).orElse(null));
    }

    @PostMapping("/batch-save")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @OperationLog(module = "价格维护", type = OperationType.UPDATE, description = "保存价格草稿")
    public Result<PriceDraftDTO> saveDraft(@RequestBody PriceDraftSaveRequest request) {
        try {
            return Result.success("价格草稿保存成功",
                    priceDraftService.saveDraft(request, SecurityUtils.getCurrentUserId()));
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/{batchId}/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @OperationLog(module = "价格维护", type = OperationType.UPDATE, description = "发布价格草稿")
    public Result<PricePublishResultDTO> publish(@PathVariable Long batchId) {
        try {
            return Result.success("价格发布完成",
                    pricePublishService.publishBatch(batchId, PricePublishLog.PublishType.MANUAL, SecurityUtils.getCurrentUserId()));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/by-date/publish")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @OperationLog(module = "价格维护", type = OperationType.UPDATE, description = "按日期发布价格草稿")
    public Result<PricePublishResultDTO> publishByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        try {
            return Result.success("价格发布完成",
                    pricePublishService.publishByDate(date, PricePublishLog.PublishType.MANUAL, SecurityUtils.getCurrentUserId()));
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/{batchId}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    @OperationLog(module = "价格维护", type = OperationType.UPDATE, description = "取消价格草稿")
    public Result<Void> cancel(@PathVariable Long batchId) {
        try {
            priceDraftService.cancelDraft(batchId);
            return Result.success("草稿已取消");
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        }
    }
}
