package com.pricemanagement.controller;

import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.dto.Result;
import com.pricemanagement.dto.SystemNoticeCreateRequest;
import com.pricemanagement.entity.OperationLog.OperationType;
import com.pricemanagement.entity.SystemNotice;
import com.pricemanagement.service.SystemNoticeService;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/system-notices")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('notification:view')")
public class AdminSystemNoticeController {

    private final SystemNoticeService systemNoticeService;

    @GetMapping
    public Result<Page<SystemNotice>> list(
            @RequestParam(required = false) SystemNotice.NoticeStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success("获取系统公告成功",
                systemNoticeService.list(status, PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdTime"))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('system-notice:create')")
    @OperationLog(module = "通知中心", type = OperationType.CREATE, description = "创建系统公告")
    public Result<SystemNotice> create(@RequestBody SystemNoticeCreateRequest request) {
        return Result.success("创建系统公告成功",
                systemNoticeService.create(request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('system-notice:create')")
    @OperationLog(module = "通知中心", type = OperationType.UPDATE, description = "发布系统公告")
    public Result<SystemNotice> publish(@PathVariable Long id) {
        return Result.success("发布系统公告成功", systemNoticeService.publish(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('system-notice:cancel')")
    @OperationLog(module = "通知中心", type = OperationType.UPDATE, description = "撤回系统公告")
    public Result<SystemNotice> cancel(@PathVariable Long id) {
        return Result.success("撤回系统公告成功", systemNoticeService.cancel(id));
    }
}
