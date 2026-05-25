package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.dto.StyleConfigDTO;
import com.pricemanagement.dto.StyleThemeDTO;
import com.pricemanagement.dto.StylePresetDTO;
import com.pricemanagement.dto.StyleVersionDTO;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.service.StyleConfigService;
import com.pricemanagement.service.StyleVersionService;
import com.pricemanagement.util.OperationLogHelper;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/style")
@RequiredArgsConstructor
public class StyleConfigController {

    private final StyleConfigService styleConfigService;
    private final StyleVersionService versionService;
    private final OperationLogHelper operationLogHelper;

    @GetMapping("/config")
    public Result<StyleConfigDTO> getStyleConfig() {
        return Result.success("获取样式配置成功", styleConfigService.getStyleConfig());
    }

    @GetMapping("/themes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<StyleThemeDTO>> getPresetThemes() {
        return Result.success("获取预设主题成功", styleConfigService.getPresetThemes());
    }

    /**
     * 获取所有预设（色彩方案、布局方案、字号预设）
     */
    @GetMapping("/presets")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Map<String, Object>> getPresets() {
        Map<String, Object> presets = new HashMap<>();
        presets.put("colorSchemes", styleConfigService.getColorSchemes());
        presets.put("layoutStyles", styleConfigService.getLayoutStyles());
        presets.put("fontPresets", styleConfigService.getFontPresets());
        return Result.success("获取预设成功", presets);
    }

    /**
     * 获取色彩方案列表
     */
    @GetMapping("/color-schemes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<StylePresetDTO>> getColorSchemes() {
        return Result.success("获取色彩方案成功", styleConfigService.getColorSchemes());
    }

    /**
     * 获取布局方案列表
     */
    @GetMapping("/layout-styles")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<StylePresetDTO>> getLayoutStyles() {
        return Result.success("获取布局方案成功", styleConfigService.getLayoutStyles());
    }

    /**
     * 获取字号预设列表
     */
    @GetMapping("/font-presets")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<List<StylePresetDTO>> getFontPresets() {
        return Result.success("获取字号预设成功", styleConfigService.getFontPresets());
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateStyleConfig(@RequestBody StyleConfigDTO config) {
        try {
            styleConfigService.updateStyleConfig(config);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "保存样式配置", null);
            return Result.success("更新样式配置成功");
        } catch (Exception e) {
            log.error("Update style config failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "保存样式配置失败", null, e.getMessage());
            return Result.error(400, "更新样式配置失败: " + e.getMessage());
        }
    }

    @PutMapping("/theme/{themeKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> switchTheme(@PathVariable String themeKey) {
        try {
            styleConfigService.switchTheme(themeKey);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "切换主题: " + themeKey, "themeKey=" + themeKey);
            return Result.success("切换主题成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换主题失败", "themeKey=" + themeKey, e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Switch theme failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换主题失败", "themeKey=" + themeKey, e.getMessage());
            return Result.error(400, "切换主题失败: " + e.getMessage());
        }
    }

    /**
     * 切换色彩方案
     */
    @PutMapping("/color-scheme/{schemeKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> switchColorScheme(@PathVariable String schemeKey) {
        try {
            styleConfigService.switchColorScheme(schemeKey);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "切换色彩方案: " + schemeKey, "schemeKey=" + schemeKey);
            return Result.success("切换色彩方案成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换色彩方案失败", "schemeKey=" + schemeKey, e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Switch color scheme failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换色彩方案失败", "schemeKey=" + schemeKey, e.getMessage());
            return Result.error(400, "切换色彩方案失败: " + e.getMessage());
        }
    }

    /**
     * 切换布局方案
     */
    @PutMapping("/layout-style/{layoutKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> switchLayoutStyle(@PathVariable String layoutKey) {
        try {
            styleConfigService.switchLayoutStyle(layoutKey);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "切换布局方案: " + layoutKey, "layoutKey=" + layoutKey);
            return Result.success("切换布局方案成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换布局方案失败", "layoutKey=" + layoutKey, e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Switch layout style failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换布局方案失败", "layoutKey=" + layoutKey, e.getMessage());
            return Result.error(400, "切换布局方案失败: " + e.getMessage());
        }
    }

    /**
     * 切换字号预设
     */
    @PutMapping("/font-preset/{presetKey}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> switchFontPreset(@PathVariable String presetKey) {
        try {
            styleConfigService.switchFontPreset(presetKey);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "切换字号预设: " + presetKey, "presetKey=" + presetKey);
            return Result.success("切换字号预设成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换字号预设失败", "presetKey=" + presetKey, e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Switch font preset failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "切换字号预设失败", "presetKey=" + presetKey, e.getMessage());
            return Result.error(400, "切换字号预设失败: " + e.getMessage());
        }
    }

    @PostMapping("/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        try {
            String logoUrl = styleConfigService.uploadLogo(file);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "上传Logo", null);
            return Result.success("上传Logo成功", logoUrl);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "上传Logo失败", null, e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            log.error("Upload logo failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "上传Logo失败", null, e.getMessage());
            return Result.error(400, "上传Logo失败: " + e.getMessage());
        }
    }

    /**
     * 上传登录页Logo
     */
    @PostMapping("/logo/login")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> uploadLogoLogin(@RequestParam("file") MultipartFile file) {
        try {
            String logoUrl = styleConfigService.uploadLogoLogin(file);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "上传登录页Logo", null);
            return Result.success("上传登录页Logo成功", logoUrl);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "上传登录页Logo失败", null, e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            log.error("Upload login logo failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "上传登录页Logo失败", null, e.getMessage());
            return Result.error(400, "上传登录页Logo失败: " + e.getMessage());
        }
    }

    /**
     * 上传导航栏Logo
     */
    @PostMapping("/logo/nav")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<String> uploadLogoNav(@RequestParam("file") MultipartFile file) {
        try {
            String logoUrl = styleConfigService.uploadLogoNav(file);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "上传导航栏Logo", null);
            return Result.success("上传导航栏Logo成功", logoUrl);
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "上传导航栏Logo失败", null, e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (IOException e) {
            log.error("Upload nav logo failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "上传导航栏Logo失败", null, e.getMessage());
            return Result.error(400, "上传导航栏Logo失败: " + e.getMessage());
        }
    }

    // ==================== 版本管理接口 ====================

    /**
     * 获取版本列表
     */
    @GetMapping("/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<StyleVersionDTO>> getVersionList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success("获取版本列表成功", versionService.getVersionList(page, size));
    }

    /**
     * 获取版本详情
     */
    @GetMapping("/versions/{versionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<StyleVersionDTO> getVersionById(@PathVariable Long versionId) {
        return versionService.getVersionById(versionId)
                .map(v -> Result.success("获取版本详情成功", v))
                .orElse(Result.error(404, "版本不存在"));
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/rollback/{versionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> rollbackToVersion(@PathVariable Long versionId) {
        try {
            Long changedBy = SecurityUtils.getCurrentUserId();
            styleConfigService.rollbackToVersion(versionId, changedBy);
            operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
                    "回滚样式配置到版本: " + versionId, "versionId=" + versionId);
            log.info("样式配置回滚成功: versionId={}, changedBy={}", versionId, changedBy);
            return Result.success("回滚成功");
        } catch (IllegalArgumentException e) {
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "回滚样式配置失败", "versionId=" + versionId, e.getMessage());
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Rollback failed: {}", e.getMessage(), e);
            operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
                    "回滚样式配置失败", "versionId=" + versionId, e.getMessage());
            return Result.error(400, "回滚失败: " + e.getMessage());
        }
    }
}
