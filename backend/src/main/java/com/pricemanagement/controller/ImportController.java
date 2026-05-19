
package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.service.ImportExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportExportService importExportService;

    // ==================== 产品导入导出 ====================

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<String> importProducts(@RequestParam("file") MultipartFile file) {
        try {
            importExportService.importProducts(file);
            return Result.success("产品导入成功", "共导入 " + file.getSize() + " 字节的数据");
        } catch (Exception e) {
            log.error("产品导入失败: {}", e.getMessage());
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
    public Result<ImportExportService.UserImportResult> importUsers(@RequestParam("file") MultipartFile file) {
        try {
            ImportExportService.UserImportResult result = importExportService.importUsers(file);
            if (result.skipCount() > 0) {
                return Result.success(
                    String.format("导入完成: 成功 %d 条, 跳过 %d 条", result.successCount(), result.skipCount()),
                    result);
            }
            return Result.success("用户导入成功，共 " + result.successCount() + " 条", result);
        } catch (Exception e) {
            log.error("用户导入失败: {}", e.getMessage());
            return Result.error(500, "用户导入失败: " + e.getMessage());
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
