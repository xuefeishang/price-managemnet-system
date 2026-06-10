package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.dto.NotificationCreateCommand;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.ScheduledTaskLog;
import com.pricemanagement.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationEventService {

    private final NotificationEventTransactionService transactionService;
    private final ObjectMapper objectMapper;

    public void pricePublished(String title, String content, Long publishLogId, LocalDate effectiveDate,
                               Long batchId, Long createdBy, List<String> channels, List<User.Role> recipientRoles) {
        NotificationCreateCommand command = command(NotificationService.TYPE_PRICE_PUBLISHED, title,
                "价格已发布，可查看最新价格", content, "PRICE", publishLogId);
        command.setRecipientRoles(recipientRoles);
        command.setChannels(channels);
        command.setPriority(NotificationMessage.NotificationPriority.NORMAL);
        command.setLinkType(NotificationService.LINK_TYPE_PRICE_QUERY);
        command.setLinkParams(json(Map.of("date", effectiveDate.toString())));
        command.setDedupeKey("PRICE_PUBLISHED:BATCH:" + batchId);
        command.setCreatedBy(createdBy);
        create(command);
    }

    public void approvalPending(Long requestId, String approverRole, Long createdBy) {
        if (approverRole == null || approverRole.isBlank()) {
            return;
        }
        User.Role role;
        try {
            role = User.Role.valueOf(approverRole);
        } catch (IllegalArgumentException ex) {
            log.warn("Unsupported approval notification role: {}", approverRole);
            return;
        }
        NotificationCreateCommand command = command("APPROVAL_PENDING", "审批待处理", "有新的审批请求需要处理",
                "审批请求 #" + requestId + " 等待处理", "APPROVAL", requestId);
        command.setRecipientRoles(List.of(role));
        command.setPriority(NotificationMessage.NotificationPriority.HIGH);
        command.setLinkType("APPROVAL_DETAIL");
        command.setLinkParams(json(Map.of("requestId", requestId)));
        command.setDedupeKey("APPROVAL_PENDING:" + requestId + ":" + approverRole);
        command.setCreatedBy(createdBy);
        create(command);
    }

    public void scheduledTaskFailed(ScheduledTaskLog logItem) {
        if (logItem == null || logItem.getStatus() != ScheduledTaskLog.RunStatus.FAILED) {
            return;
        }
        NotificationCreateCommand command = command("TASK_FAILED", "定时任务执行失败", logItem.getTaskCode() + " 执行失败",
                logItem.getMessage() == null ? "定时任务执行失败" : logItem.getMessage(),
                "TASK", logItem.getId());
        command.setRecipientRoles(List.of(User.Role.ADMIN));
        command.setPriority(NotificationMessage.NotificationPriority.HIGH);
        command.setLinkType("TASK_LOG");
        command.setLinkParams(json(Map.of("taskLogId", logItem.getId(), "taskId", logItem.getTaskId())));
        command.setDedupeKey("TASK_FAILED:" + logItem.getId());
        command.setCreatedBy(0L);
        create(command);
    }

    public void apiLimitWarning(String appId, String endpoint, String message) {
        String normalizedAppId = appId == null || appId.isBlank() ? "UNKNOWN" : appId;
        NotificationCreateCommand command = command("API_LIMIT_WARNING", "外部 API 告警", normalizedAppId + " 调用异常",
                message == null || message.isBlank() ? "外部 API 调用出现异常" : message,
                "SECURITY", null);
        command.setRecipientRoles(List.of(User.Role.ADMIN));
        command.setPriority(NotificationMessage.NotificationPriority.HIGH);
        command.setLinkType(NotificationService.LINK_TYPE_SYSTEM_NOTICE);
        command.setLinkParams(json(Map.of("appId", normalizedAppId, "endpoint", endpoint == null ? "" : endpoint)));
        command.setDedupeKey("API_LIMIT_WARNING:" + normalizedAppId + ":" + LocalDate.now());
        command.setCreatedBy(0L);
        create(command);
    }

    public void importExportFinished(Long userId, String title, String message, boolean success) {
        if (userId == null) {
            return;
        }
        NotificationCreateCommand command = command("IMPORT_EXPORT_FINISHED", title, title, message, "TASK", userId);
        command.setRecipientUserIds(List.of(userId));
        command.setPriority(success ? NotificationMessage.NotificationPriority.LOW : NotificationMessage.NotificationPriority.HIGH);
        command.setLinkType(NotificationService.LINK_TYPE_SYSTEM_NOTICE);
        command.setLinkParams(json(Map.of("module", "import-export")));
        command.setDedupeKey("IMPORT_EXPORT_FINISHED:" + userId + ":" + System.currentTimeMillis());
        command.setCreatedBy(userId);
        create(command);
    }

    private NotificationCreateCommand command(String type, String title, String summary, String content,
                                              String businessType, Long businessId) {
        NotificationCreateCommand command = new NotificationCreateCommand();
        command.setEventType(type);
        command.setTitle(title);
        command.setSummary(summary);
        command.setContent(content);
        command.setBusinessType(businessType);
        command.setBusinessId(businessId);
        command.setChannels(List.of(NotificationService.CHANNEL_IN_APP));
        return command;
    }

    private void create(NotificationCreateCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    createInNewTransaction(command);
                }
            });
            return;
        }
        createInNewTransaction(command);
    }

    private void createInNewTransaction(NotificationCreateCommand command) {
        try {
            transactionService.create(command);
        } catch (Exception ex) {
            log.warn("Failed to create notification event: type={}, message={}", command.getEventType(), ex.getMessage());
        }
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

}
