package com.pricemanagement.controller;

import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.dto.AdminMiniProgramSubscriptionDTO;
import com.pricemanagement.dto.AdminNotificationSummaryDTO;
import com.pricemanagement.dto.NotificationAuthorizationGuideRequest;
import com.pricemanagement.dto.NotificationChannelConfigDTO;
import com.pricemanagement.dto.NotificationChannelConfigUpdateRequest;
import com.pricemanagement.dto.NotificationDashboardDTO;
import com.pricemanagement.dto.NotificationProviderHealthDTO;
import com.pricemanagement.dto.NotificationProviderTestResultDTO;
import com.pricemanagement.dto.NotificationMiniProgramCoverageDTO;
import com.pricemanagement.dto.NotificationMiniProgramResolveRequest;
import com.pricemanagement.dto.NotificationMiniProgramTestDeliveryRequest;
import com.pricemanagement.dto.NotificationRecipientDTO;
import com.pricemanagement.dto.NotificationThrottleRuleDTO;
import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.OperationLog.OperationType;
import com.pricemanagement.entity.User;
import com.pricemanagement.service.AdminMiniProgramSubscriptionManagementService;
import com.pricemanagement.service.NotificationMiniProgramRuntimeConfigService;
import com.pricemanagement.service.NotificationService;
import com.pricemanagement.service.NotificationObservabilityService;
import com.pricemanagement.service.notification.WechatMiniProgramNotificationProvider;
import com.pricemanagement.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Arrays;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('notification:view')")
public class AdminNotificationController {

    private final NotificationService notificationService;
    private final NotificationObservabilityService notificationObservabilityService;
    private final NotificationMiniProgramRuntimeConfigService miniProgramRuntimeConfigService;
    private final AdminMiniProgramSubscriptionManagementService miniProgramSubscriptionManagementService;
    private final WechatMiniProgramNotificationProvider miniProgramNotificationProvider;

    @GetMapping("/dashboard")
    public Result<NotificationDashboardDTO> dashboard() {
        return Result.success("获取通知指标成功", notificationObservabilityService.dashboard());
    }

    @GetMapping("/providers/health")
    public Result<List<NotificationProviderHealthDTO>> providerHealth() {
        return Result.success("获取Provider健康状态成功", notificationObservabilityService.providerHealth());
    }

    @GetMapping("/throttle-rules")
    public Result<List<NotificationThrottleRuleDTO>> throttleRules() {
        return Result.success("获取通知频控规则成功", notificationObservabilityService.throttleRules());
    }

    @GetMapping("/channels/{channel}/config")
    public Result<NotificationChannelConfigDTO> channelConfig(@PathVariable String channel) {
        requireMiniProgram(channel);
        return Result.success("获取渠道配置成功", miniProgramRuntimeConfigService.getConfigView());
    }

    @PutMapping("/channels/{channel}/config")
    @PreAuthorize("hasAuthority('system:setting')")
    @OperationLog(module = "通知中心", type = OperationType.UPDATE, description = "保存通知渠道配置", logParams = false)
    public Result<NotificationChannelConfigDTO> saveChannelConfig(
            @PathVariable String channel,
            @RequestBody NotificationChannelConfigUpdateRequest request) {
        requireMiniProgram(channel);
        return Result.success("保存渠道配置成功",
                miniProgramRuntimeConfigService.saveConfig(request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/channels/{channel}/test")
    @PreAuthorize("hasAuthority('notification:view')")
    @OperationLog(module = "通知中心", type = OperationType.VIEW, description = "诊断通知渠道配置")
    public Result<NotificationProviderTestResultDTO> testChannelConfig(@PathVariable String channel) {
        requireMiniProgram(channel);
        return Result.success("渠道配置诊断完成", miniProgramRuntimeConfigService.testConfig());
    }

    @PostMapping("/channels/{channel}/test-token")
    @PreAuthorize("hasAuthority('notification:test-token')")
    @OperationLog(module = "通知中心", type = OperationType.VIEW, description = "远程校验小程序access_token")
    public Result<NotificationProviderTestResultDTO> testChannelToken(@PathVariable String channel) {
        requireMiniProgram(channel);
        return Result.success("access_token远程校验完成", miniProgramNotificationProvider.testAccessToken());
    }

    @PostMapping("/channels/{channel}/test-delivery")
    @PreAuthorize("hasAuthority('notification:test-delivery')")
    @OperationLog(module = "通知中心", type = OperationType.CREATE, description = "发起小程序渠道测试投递")
    public Result<Long> testChannelDelivery(
            @PathVariable String channel,
            @Valid @RequestBody NotificationMiniProgramTestDeliveryRequest request) {
        requireMiniProgram(channel);
        return Result.success("测试投递已创建",
                miniProgramSubscriptionManagementService.sendTestDelivery(request, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/mini-program/coverage")
    public Result<NotificationMiniProgramCoverageDTO> miniProgramCoverage(
            @RequestParam(required = false) String roles,
            @RequestParam(required = false) String notificationType) {
        return Result.success("获取小程序触达覆盖率成功",
                miniProgramSubscriptionManagementService.coverage(parseRoles(roles), notificationType));
    }

    @GetMapping("/mini-program/subscriptions")
    @PreAuthorize("hasAuthority('notification:subscription:view')")
    public Result<Page<AdminMiniProgramSubscriptionDTO>> miniProgramSubscriptions(
            @RequestParam(required = false) User.Role role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success("获取小程序订阅授权列表成功",
                miniProgramSubscriptionManagementService.list(
                        role,
                        status,
                        keyword,
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.ASC, "id"))));
    }

    @GetMapping("/mini-program/subscriptions/{userId}")
    @PreAuthorize("hasAuthority('notification:subscription:view')")
    public Result<AdminMiniProgramSubscriptionDTO> miniProgramSubscriptionDetail(@PathVariable Long userId) {
        return Result.success("获取小程序订阅授权详情成功",
                miniProgramSubscriptionManagementService.detail(userId));
    }

    @PostMapping("/mini-program/subscriptions/{userId}/resolve")
    @PreAuthorize("hasAuthority('notification:subscription:resolve')")
    @OperationLog(module = "通知中心", type = OperationType.UPDATE, description = "处理小程序订阅授权异常")
    public Result<AdminMiniProgramSubscriptionDTO> resolveMiniProgramSubscription(
            @PathVariable Long userId,
            @Valid @RequestBody NotificationMiniProgramResolveRequest request) {
        return Result.success("订阅授权异常处理成功",
                miniProgramSubscriptionManagementService.resolve(userId, request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/mini-program/authorization-guides")
    @PreAuthorize("hasAuthority('notification:subscription:guide')")
    @OperationLog(module = "通知中心", type = OperationType.CREATE, description = "发送小程序订阅授权引导")
    public Result<Integer> sendMiniProgramAuthorizationGuides(
            @RequestBody NotificationAuthorizationGuideRequest request) {
        return Result.success("发送授权引导成功",
                miniProgramSubscriptionManagementService.sendGuide(request, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/mini-program/authorization-guides/{userId}")
    @PreAuthorize("hasAuthority('notification:subscription:guide')")
    @OperationLog(module = "通知中心", type = OperationType.CREATE, description = "发送单用户小程序订阅授权引导")
    public Result<Integer> sendMiniProgramAuthorizationGuide(
            @PathVariable Long userId) {
        return Result.success("发送授权引导成功",
                miniProgramSubscriptionManagementService.sendGuideToUser(userId, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping
    public Result<Page<AdminNotificationSummaryDTO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) NotificationMessage.NotificationPriority priority,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) NotificationDeliveryLog.DeliveryStatus deliveryStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success("获取通知列表成功",
                notificationService.getAdminNotificationSummaries(
                        type,
                        priority,
                        businessType,
                        channel,
                        deliveryStatus,
                        keyword,
                        startTime,
                        endTime,
                        PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdTime"))));
    }

    @GetMapping("/{id}")
    public Result<NotificationMessage> detail(@PathVariable Long id) {
        return Result.success("获取通知详情成功", notificationService.getAdminNotification(id));
    }

    @GetMapping("/{id}/recipients")
    public Result<Page<NotificationRecipientDTO>> recipients(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success("获取通知收件人成功",
                notificationService.getAdminRecipients(id, PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 100))));
    }

    @GetMapping("/{id}/deliveries")
    public Result<List<NotificationDeliveryLog>> deliveries(@PathVariable Long id) {
        return Result.success("获取投递记录成功", notificationService.getDeliveries(id));
    }

    @GetMapping("/{id}/delivery-logs")
    public Result<Page<NotificationDeliveryLog>> deliveryLogs(
            @PathVariable Long id,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) NotificationDeliveryLog.DeliveryStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success("获取投递日志成功",
                notificationService.getAdminDeliveryLogs(
                        id,
                        channel,
                        status,
                        keyword,
                        PageRequest.of(
                                Math.max(page, 0),
                                Math.min(Math.max(size, 1), 100),
                                Sort.by(Sort.Direction.DESC, "createdTime"))));
    }

    @PostMapping("/deliveries/{id}/retry")
    @PreAuthorize("hasAuthority('notification:retry')")
    @OperationLog(module = "通知中心", type = OperationType.UPDATE, description = "重试通知投递")
    public Result<Void> retryDelivery(@PathVariable Long id) {
        notificationService.retryDelivery(id);
        return Result.success("已提交投递重试");
    }

    private void requireMiniProgram(String channel) {
        if (!NotificationService.CHANNEL_MINI_PROGRAM.equals(channel)) {
            throw new IllegalArgumentException("当前仅支持小程序订阅消息渠道配置");
        }
    }

    private List<User.Role> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(String::toUpperCase)
                    .map(User.Role::valueOf)
                    .distinct()
                    .toList();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("roles包含无效角色");
        }
    }

}
