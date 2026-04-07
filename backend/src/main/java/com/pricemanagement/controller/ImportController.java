
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
}
