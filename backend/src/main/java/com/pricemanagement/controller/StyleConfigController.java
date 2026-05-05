package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.dto.StyleConfigDTO;
import com.pricemanagement.dto.StyleThemeDTO;
import com.pricemanagement.service.StyleConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/style")
@RequiredArgsConstructor
public class StyleConfigController {

    private final StyleConfigService styleConfigService;

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<StyleConfigDTO> getStyleConfig() {
        return Result.success("获取样式配置成功", styleConfigService.getStyleConfig());
    }

    @GetMapping("/themes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<StyleThemeDTO>> getPresetThemes() {
        return Result.success("获取预设主题成功", styleConfigService.getPresetThemes());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateStyleConfig(@RequestBody StyleConfigDTO config) {
        try {
            styleConfigService.updateStyleConfig(config);
            return Result.success("更新样式配置成功");
        } catch (Exception e) {
            log.error("Update style config failed: {}", e.getMessage(), e);
            return Result.error(400, "更新样式配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/theme/{themeKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> switchTheme(@PathVariable String themeKey) {
        try {
            styleConfigService.switchTheme(themeKey);
            return Result.success("切换主题成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Switch theme failed: {}", e.getMessage(), e);
            return Result.error(400, "切换主题失败: " + e.getMessage());
        }
    }

    @PostMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        try {
            String logoUrl = styleConfigService.uploadLogo(file);
            return Result.success("上传Logo成功", logoUrl);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            log.error("Upload logo failed: {}", e.getMessage(), e);
            return Result.error(400, "上传Logo失败: " + e.getMessage());
        }
    }
}
