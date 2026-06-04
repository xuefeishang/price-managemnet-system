package com.pricemanagement.controller;

import com.pricemanagement.dto.NotificationDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationRecipient;
import com.pricemanagement.entity.OperationLog.OperationType;
import com.pricemanagement.service.NotificationService;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Page<NotificationDTO>> myNotifications(
            @RequestParam(required = false) NotificationRecipient.ReadStatus readStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success("获取通知列表成功",
                notificationService.getMyNotifications(SecurityUtils.getCurrentUserId(), readStatus,
                        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Long> unreadCount() {
        return Result.success("获取未读通知数成功",
                notificationService.getUnreadCount(SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{messageId}/read")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    @OperationLog(module = "通知中心", type = OperationType.UPDATE, description = "标记通知已读")
    public Result<Void> markRead(@PathVariable Long messageId) {
        notificationService.markRead(messageId, SecurityUtils.getCurrentUserId());
        return Result.success("通知已读");
    }

    @GetMapping("/{messageId}/deliveries")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<NotificationDeliveryLog>> deliveries(@PathVariable Long messageId) {
        return Result.success("获取投递记录成功", notificationService.getDeliveries(messageId));
    }
}
