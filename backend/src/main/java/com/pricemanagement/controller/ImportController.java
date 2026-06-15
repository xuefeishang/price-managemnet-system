
package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.dto.UserImportResult;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.exception.UserConflictException;
import com.pricemanagement.exception.UserImportValidationException;
import com.pricemanagement.service.ImportExportService;
import com.pricemanagement.service.NotificationEventService;
import com.pricemanagement.util.SecurityUtils;
import com.pricemanagement.util.OperationLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportExportService importExportService;
    private final NotificationEventService notificationEventService;
    private final OperationLogHelper operationLogHelper;

    // ==================== 产品导入导出 ====================

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<String> importProducts(@RequestParam("file") MultipartFile file) {
        try {
            importExportService.importProducts(file);
            notificationEventService.importExportFinished(SecurityUtils.getCurrentUserId(),
                    "产品导入完成", "产品导入成功，共处理 " + file.getSize() + " 字节数据", true);
            return Result.success("产品导入成功", "共导入 " + file.getSize() + " 字节的数据");
        } catch (Exception e) {
            log.error("产品导入失败: {}", e.getMessage());
            notificationEventService.importExportFinished(SecurityUtils.getCurrentUserId(),
                    "产品导入失败", e.getMessage(), false);
            return Result.error(500, "产品导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public void exportProducts(HttpServletResponse response) {
        try {
            importExportService.exportProducts(response);
        } catch (IOException e) {
            log.error("产品导出失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // ==================== 用户导入导出 ====================

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<UserImportResult> importUsers(@RequestParam("file") MultipartFile file) {
        long startedAt = System.currentTimeMillis();
        try {
            UserImportResult result = importExportService.importUsers(file);
            long duration = System.currentTimeMillis() - startedAt;
            operationLogHelper.logSuccess("用户管理", OperationLog.OperationType.IMPORT, "批量导入用户",
                    importSummary(result.totalRows(), result.importedCount(), 0, null), duration);
            sendUserImportNotification("用户导入完成", "用户导入成功，共 " + result.importedCount() + " 条", true);
            return Result.success("用户导入成功，共 " + result.importedCount() + " 条", result);
        } catch (UserImportValidationException ex) {
            long duration = System.currentTimeMillis() - startedAt;
            UserImportResult result = ex.getResult();
            operationLogHelper.logError("用户管理", OperationLog.OperationType.IMPORT, "批量导入用户失败",
                    importSummary(result.totalRows(), 0, result.errors().size(), errorCodeSummary(result)),
                    ex.getMessage(), "400", duration);
            sendUserImportNotification("用户导入失败",
                    "用户导入预检发现 " + result.errors().size() + " 项问题，未导入任何用户", false);
            throw ex;
        } catch (UserConflictException ex) {
            long duration = System.currentTimeMillis() - startedAt;
            operationLogHelper.logError("用户管理", OperationLog.OperationType.IMPORT, "批量导入用户失败",
                    importSummary(0, 0, 1, ex.getReason().name()), ex.getMessage(), "409", duration);
            sendUserImportNotification("用户导入失败", "用户数据发生并发冲突，未导入任何用户", false);
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            long duration = System.currentTimeMillis() - startedAt;
            operationLogHelper.logError("用户管理", OperationLog.OperationType.IMPORT, "批量导入用户失败",
                    importSummary(0, 0, 1, "CONSTRAINT_CONFLICT"), "用户数据发生并发冲突", "409", duration);
            sendUserImportNotification("用户导入失败", "用户数据发生并发冲突，未导入任何用户", false);
            throw ex;
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startedAt;
            operationLogHelper.logError("用户管理", OperationLog.OperationType.IMPORT, "批量导入用户失败",
                    importSummary(0, 0, 1, "SYSTEM_ERROR"), "用户导入系统异常", "500", duration);
            sendUserImportNotification("用户导入失败", "用户导入发生系统异常，未导入任何用户", false);
            throw new IllegalStateException("用户导入系统异常", ex);
        }
    }

    private String importSummary(int totalRows, int importedCount, int errorCount, String safeErrorCodes) {
        return "totalRows=" + totalRows + ",importedCount=" + importedCount + ",validationErrorCount=" + errorCount
                + (safeErrorCodes == null ? "" : ",safeErrorCodes=" + safeErrorCodes);
    }

    private String errorCodeSummary(UserImportResult result) {
        return result.errors().stream()
                .collect(Collectors.groupingBy(error -> error.code(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .sorted()
                .collect(Collectors.joining("|"));
    }

    private void sendUserImportNotification(String title, String message, boolean success) {
        try {
            notificationEventService.importExportFinished(SecurityUtils.getCurrentUserId(), title, message, success);
        } catch (Exception ex) {
            log.warn("User import notification failed: type={}", ex.getClass().getSimpleName());
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public void exportUsers(HttpServletResponse response) {
        try {
            importExportService.exportUsers(response);
        } catch (IOException e) {
            log.error("用户导出失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/users/template")
    @PreAuthorize("hasRole('ADMIN')")
    public void downloadUserTemplate(HttpServletResponse response) {
        try {
            importExportService.downloadUserTemplate(response);
        } catch (IOException e) {
            log.error("用户模板下载失败: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
