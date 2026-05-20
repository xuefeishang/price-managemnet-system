package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.StyleConfigDTO;
import com.pricemanagement.dto.StyleThemeDTO;
import com.pricemanagement.dto.StylePresetDTO;
import com.pricemanagement.entity.SysStyleConfig;
import com.pricemanagement.entity.SysStylePreset;
import com.pricemanagement.repository.SysStyleConfigRepository;
import com.pricemanagement.repository.SysStylePresetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StyleConfigService {

    private final SysStyleConfigRepository styleConfigRepository;
    private final SysStylePresetRepository stylePresetRepository;
    private final StyleVersionService versionService;
    private final ObjectMapper objectMapper;

    private static final long MAX_LOGO_SIZE = 1536 * 1024; // 1.5MB (Base64编码后约2MB)
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern FONT_SIZE_PATTERN = Pattern.compile("^(\\d+(\\.\\d+)?)(rem|px|em)$");

    // 预设类型常量
    public static final String PRESET_TYPE_COLOR_SCHEME = "color_scheme";
    public static final String PRESET_TYPE_LAYOUT_STYLE = "layout_style";
    public static final String PRESET_TYPE_FONT_PRESET = "font_preset";

    /**
     * 获取当前样式配置
     */
    @Cacheable(value = "style", key = "'config'")
    public StyleConfigDTO getStyleConfig() {
        StyleConfigDTO config = new StyleConfigDTO();

        // 从新表读取配置
        config.setSystemName(getConfigValue("system_name", "价格管理系统"));
        config.setPriceRiseColor(getConfigValue("price_rise_color", "#EF4444"));
        config.setPriceFallColor(getConfigValue("price_fall_color", "#10B981"));
        config.setPriceFlatColor(getConfigValue("price_flat_color", "#9CA3AF"));
        config.setChartPrimaryColor(getConfigValue("chart_primary_color", "#0D6E6E"));
        config.setChartBudgetColor(getConfigValue("chart_budget_color", "#F59E0B"));
        config.setChartColors(getConfigValue("chart_colors", "#0D6E6E,#10B981,#F59E0B,#EF4444,#8B5CF6,#EC4899,#6366F1,#14B8A6,#64748B"));
        config.setHeadingFont(getConfigValue("heading_font", "Newsreader"));
        config.setBodyFont(getConfigValue("body_font", "Inter"));
        config.setNumberFont(getConfigValue("number_font", "JetBrains Mono"));
        config.setLogoUrl(getConfigValue("logo_url", ""));
        config.setLogoSize(getConfigValue("logo_size", "medium"));
        config.setActiveTheme(getConfigValue("active_theme", "theme_red_green"));

        // 新增字段
        config.setActiveColorScheme(getConfigValue("active_color_scheme", "scheme_teal_classic"));
        config.setActiveLayoutStyle(getConfigValue("active_layout_style", "layout_top_nav"));
        config.setFontSizePreset(getConfigValue("font_size_preset", "standard"));

        // 字体大小配置
        config.setFontSizeXs(getConfigValue("font_size_xs", "0.75rem"));
        config.setFontSizeSm(getConfigValue("font_size_sm", "0.875rem"));
        config.setFontSizeBase(getConfigValue("font_size_base", "1rem"));
        config.setFontSizeLg(getConfigValue("font_size_lg", "1.125rem"));
        config.setFontSizeXl(getConfigValue("font_size_xl", "1.25rem"));
        config.setFontSize2xl(getConfigValue("font_size_2xl", "1.5rem"));
        config.setFontSize3xl(getConfigValue("font_size_3xl", "1.875rem"));

        return config;
    }

    /**
     * 获取所有预设主题（兼容旧接口）
     */
    @Cacheable(value = "style", key = "'themes'")
    public List<StyleThemeDTO> getPresetThemes() {
        List<SysStylePreset> presets = stylePresetRepository
                .findByPresetTypeAndStatusOrderBySortOrderAsc(PRESET_TYPE_COLOR_SCHEME, CommonStatus.ACTIVE);

        String activeScheme = getConfigValue("active_color_scheme", "scheme_teal_classic");

        return presets.stream().map(preset -> {
            StyleThemeDTO theme = new StyleThemeDTO();
            theme.setThemeKey(preset.getPresetKey());
            theme.setThemeName(preset.getPresetName());
            theme.setDescription(preset.getPresetDescription());
            theme.setActive(preset.getPresetKey().equals(activeScheme));

            if (preset.getConfigJson() != null) {
                try {
                    Map<String, Object> colors = objectMapper.readValue(preset.getConfigJson(),
                            new TypeReference<Map<String, Object>>() {});
                    // 转换为 String 格式以兼容旧接口
                    Map<String, String> colorMap = new HashMap<>();
                    if (colors.get("priceRise") != null) colorMap.put("priceRise", colors.get("priceRise").toString());
                    if (colors.get("priceFall") != null) colorMap.put("priceFall", colors.get("priceFall").toString());
                    if (colors.get("priceFlat") != null) colorMap.put("priceFlat", colors.get("priceFlat").toString());
                    if (colors.get("chartPrimary") != null) colorMap.put("chartPrimary", colors.get("chartPrimary").toString());
                    if (colors.get("chartBudget") != null) colorMap.put("chartBudget", colors.get("chartBudget").toString());
                    if (colors.get("chartColors") != null) {
                        Object chartColors = colors.get("chartColors");
                        if (chartColors instanceof List) {
                            colorMap.put("chartColors", String.join(",", ((List<?>) chartColors).stream()
                                    .map(Object::toString).collect(Collectors.toList())));
                        } else {
                            colorMap.put("chartColors", chartColors.toString());
                        }
                    }
                    theme.setColors(colorMap);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse preset colors for {}: {}", preset.getPresetKey(), e.getMessage());
                }
            }

            return theme;
        }).collect(Collectors.toList());
    }

    /**
     * 获取所有色彩方案预设
     */
    @Cacheable(value = "style", key = "'color_schemes'")
    public List<StylePresetDTO> getColorSchemes() {
        List<SysStylePreset> presets = stylePresetRepository
                .findByPresetTypeAndStatusOrderBySortOrderAsc(PRESET_TYPE_COLOR_SCHEME, CommonStatus.ACTIVE);

        String activeScheme = getConfigValue("active_color_scheme", "scheme_teal_classic");

        return presets.stream().map(preset -> {
            StylePresetDTO dto = new StylePresetDTO();
            dto.setKey(preset.getPresetKey());
            dto.setName(preset.getPresetName());
            dto.setDescription(preset.getPresetDescription());
            dto.setActive(preset.getPresetKey().equals(activeScheme));
            dto.setIsDefault(preset.getIsDefault());
            dto.setSortOrder(preset.getSortOrder());

            if (preset.getConfigJson() != null) {
                try {
                    Map<String, Object> config = objectMapper.readValue(preset.getConfigJson(),
                            new TypeReference<Map<String, Object>>() {});
                    dto.setConfig(config);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse color scheme config: {}", e.getMessage());
                }
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 获取所有布局方案预设
     */
    @Cacheable(value = "style", key = "'layout_styles'")
    public List<StylePresetDTO> getLayoutStyles() {
        List<SysStylePreset> presets = stylePresetRepository
                .findByPresetTypeAndStatusOrderBySortOrderAsc(PRESET_TYPE_LAYOUT_STYLE, CommonStatus.ACTIVE);

        String activeLayout = getConfigValue("active_layout_style", "layout_top_nav");

        return presets.stream().map(preset -> {
            StylePresetDTO dto = new StylePresetDTO();
            dto.setKey(preset.getPresetKey());
            dto.setName(preset.getPresetName());
            dto.setDescription(preset.getPresetDescription());
            dto.setActive(preset.getPresetKey().equals(activeLayout));
            dto.setIsDefault(preset.getIsDefault());
            dto.setSortOrder(preset.getSortOrder());

            if (preset.getConfigJson() != null) {
                try {
                    Map<String, Object> config = objectMapper.readValue(preset.getConfigJson(),
                            new TypeReference<Map<String, Object>>() {});
                    dto.setConfig(config);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse layout style config: {}", e.getMessage());
                }
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 获取所有字号预设
     */
    @Cacheable(value = "style", key = "'font_presets'")
    public List<StylePresetDTO> getFontPresets() {
        List<SysStylePreset> presets = stylePresetRepository
                .findByPresetTypeAndStatusOrderBySortOrderAsc(PRESET_TYPE_FONT_PRESET, CommonStatus.ACTIVE);

        String activePreset = getConfigValue("font_size_preset", "standard");

        return presets.stream().map(preset -> {
            StylePresetDTO dto = new StylePresetDTO();
            dto.setKey(preset.getPresetKey());
            dto.setName(preset.getPresetName());
            dto.setDescription(preset.getPresetDescription());
            dto.setActive(preset.getPresetKey().equals(activePreset));
            dto.setIsDefault(preset.getIsDefault());
            dto.setSortOrder(preset.getSortOrder());

            if (preset.getConfigJson() != null) {
                try {
                    Map<String, Object> config = objectMapper.readValue(preset.getConfigJson(),
                            new TypeReference<Map<String, Object>>() {});
                    dto.setConfig(config);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse font preset config: {}", e.getMessage());
                }
            }

            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 更新样式配置
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void updateStyleConfig(StyleConfigDTO config) {
        updateStyleConfig(config, null, null);
    }

    /**
     * 更新样式配置（带版本快照）
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void updateStyleConfig(StyleConfigDTO config, String changeSummary, Long changedBy) {
        if (config.getSystemName() != null && !config.getSystemName().isBlank()) {
            updateConfig("system_name", config.getSystemName());
        }
        if (config.getPriceRiseColor() != null) {
            validateHexColor(config.getPriceRiseColor());
            updateConfig("price_rise_color", config.getPriceRiseColor());
        }
        if (config.getPriceFallColor() != null) {
            validateHexColor(config.getPriceFallColor());
            updateConfig("price_fall_color", config.getPriceFallColor());
        }
        if (config.getPriceFlatColor() != null) {
            validateHexColor(config.getPriceFlatColor());
            updateConfig("price_flat_color", config.getPriceFlatColor());
        }
        if (config.getChartPrimaryColor() != null) {
            validateHexColor(config.getChartPrimaryColor());
            updateConfig("chart_primary_color", config.getChartPrimaryColor());
        }
        if (config.getChartBudgetColor() != null) {
            validateHexColor(config.getChartBudgetColor());
            updateConfig("chart_budget_color", config.getChartBudgetColor());
        }
        if (config.getChartColors() != null) {
            updateConfig("chart_colors", config.getChartColors());
        }
        if (config.getHeadingFont() != null) {
            updateConfig("heading_font", config.getHeadingFont());
        }
        if (config.getBodyFont() != null) {
            updateConfig("body_font", config.getBodyFont());
        }
        if (config.getNumberFont() != null) {
            updateConfig("number_font", config.getNumberFont());
        }
        if (config.getLogoUrl() != null) {
            updateConfig("logo_url", config.getLogoUrl());
        }
        if (config.getLogoSize() != null) {
            updateConfig("logo_size", config.getLogoSize());
        }
        if (config.getActiveTheme() != null) {
            updateConfig("active_theme", config.getActiveTheme());
        }

        // 新增字段
        if (config.getActiveColorScheme() != null) {
            updateConfig("active_color_scheme", config.getActiveColorScheme());
        }
        if (config.getActiveLayoutStyle() != null) {
            updateConfig("active_layout_style", config.getActiveLayoutStyle());
        }
        if (config.getFontSizePreset() != null) {
            updateConfig("font_size_preset", config.getFontSizePreset());
        }

        // 字体大小配置
        if (config.getFontSizeXs() != null) {
            validateFontSize(config.getFontSizeXs());
            updateConfig("font_size_xs", config.getFontSizeXs());
        }
        if (config.getFontSizeSm() != null) {
            validateFontSize(config.getFontSizeSm());
            updateConfig("font_size_sm", config.getFontSizeSm());
        }
        if (config.getFontSizeBase() != null) {
            validateFontSize(config.getFontSizeBase());
            updateConfig("font_size_base", config.getFontSizeBase());
        }
        if (config.getFontSizeLg() != null) {
            validateFontSize(config.getFontSizeLg());
            updateConfig("font_size_lg", config.getFontSizeLg());
        }
        if (config.getFontSizeXl() != null) {
            validateFontSize(config.getFontSizeXl());
            updateConfig("font_size_xl", config.getFontSizeXl());
        }
        if (config.getFontSize2xl() != null) {
            validateFontSize(config.getFontSize2xl());
            updateConfig("font_size_2xl", config.getFontSize2xl());
        }
        if (config.getFontSize3xl() != null) {
            validateFontSize(config.getFontSize3xl());
            updateConfig("font_size_3xl", config.getFontSize3xl());
        }

        // 保存版本快照
        StyleConfigDTO snapshotConfig = getStyleConfig();
        versionService.saveSnapshot(snapshotConfig, changeSummary, changedBy);
    }

    /**
     * 切换主题（兼容旧接口）
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void switchTheme(String themeKey) {
        // 更新 active_theme（兼容）
        updateConfig("active_theme", themeKey);

        // 同时更新 active_color_scheme
        updateConfig("active_color_scheme", themeKey);

        // 应用色彩方案
        applyColorScheme(themeKey);
    }

    /**
     * 切换色彩方案
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void switchColorScheme(String schemeKey) {
        SysStylePreset preset = stylePresetRepository
                .findByPresetTypeAndPresetKey(PRESET_TYPE_COLOR_SCHEME, schemeKey)
                .orElseThrow(() -> new IllegalArgumentException("色彩方案不存在: " + schemeKey));

        updateConfig("active_color_scheme", schemeKey);
        applyColorScheme(schemeKey);
    }

    /**
     * 切换布局方案
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void switchLayoutStyle(String layoutKey) {
        SysStylePreset preset = stylePresetRepository
                .findByPresetTypeAndPresetKey(PRESET_TYPE_LAYOUT_STYLE, layoutKey)
                .orElseThrow(() -> new IllegalArgumentException("布局方案不存在: " + layoutKey));

        updateConfig("active_layout_style", layoutKey);
    }

    /**
     * 切换字号预设
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void switchFontPreset(String presetKey) {
        SysStylePreset preset = stylePresetRepository
                .findByPresetTypeAndPresetKey(PRESET_TYPE_FONT_PRESET, presetKey)
                .orElseThrow(() -> new IllegalArgumentException("字号预设不存在: " + presetKey));

        updateConfig("font_size_preset", presetKey);

        // 应用字号配置
        if (preset.getConfigJson() != null) {
            try {
                Map<String, String> sizes = objectMapper.readValue(preset.getConfigJson(),
                        new TypeReference<Map<String, String>>() {});
                if (sizes.get("xs") != null) updateConfig("font_size_xs", sizes.get("xs"));
                if (sizes.get("sm") != null) updateConfig("font_size_sm", sizes.get("sm"));
                if (sizes.get("base") != null) updateConfig("font_size_base", sizes.get("base"));
                if (sizes.get("lg") != null) updateConfig("font_size_lg", sizes.get("lg"));
                if (sizes.get("xl") != null) updateConfig("font_size_xl", sizes.get("xl"));
                if (sizes.get("2xl") != null) updateConfig("font_size_2xl", sizes.get("2xl"));
                if (sizes.get("3xl") != null) updateConfig("font_size_3xl", sizes.get("3xl"));
            } catch (JsonProcessingException e) {
                log.error("Failed to apply font preset: {}", e.getMessage());
            }
        }
    }

    /**
     * 应用色彩方案到配置
     */
    private void applyColorScheme(String schemeKey) {
        SysStylePreset preset = stylePresetRepository
                .findByPresetTypeAndPresetKey(PRESET_TYPE_COLOR_SCHEME, schemeKey)
                .orElse(null);

        if (preset != null && preset.getConfigJson() != null) {
            try {
                Map<String, Object> colors = objectMapper.readValue(preset.getConfigJson(),
                        new TypeReference<Map<String, Object>>() {});

                if (colors.get("priceRiseColor") != null) {
                    updateConfig("price_rise_color", colors.get("priceRiseColor").toString());
                }
                if (colors.get("priceFallColor") != null) {
                    updateConfig("price_fall_color", colors.get("priceFallColor").toString());
                }
                if (colors.get("priceFlatColor") != null) {
                    updateConfig("price_flat_color", colors.get("priceFlatColor").toString());
                }
                if (colors.get("chartPrimaryColor") != null) {
                    updateConfig("chart_primary_color", colors.get("chartPrimaryColor").toString());
                }
                if (colors.get("chartBudgetColor") != null) {
                    updateConfig("chart_budget_color", colors.get("chartBudgetColor").toString());
                }
                if (colors.get("chartColors") != null) {
                    Object chartColors = colors.get("chartColors");
                    if (chartColors instanceof List) {
                        updateConfig("chart_colors", String.join(",", ((List<?>) chartColors).stream()
                                .map(Object::toString).collect(Collectors.toList())));
                    } else {
                        updateConfig("chart_colors", chartColors.toString());
                    }
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to apply color scheme: {}", e.getMessage());
            }
        }
    }

    /**
     * 上传 Logo - 存储为Base64编码
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public String uploadLogo(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Logo 文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Logo 文件必须是图片格式");
        }

        if (file.getSize() > MAX_LOGO_SIZE) {
            throw new IllegalArgumentException("Logo 文件大小不能超过 1.5MB");
        }

        // 将图片转为Base64编码存储
        byte[] imageBytes = file.getBytes();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // 存储格式: data:image/png;base64,xxxxx
        String mimeType = contentType;
        String dataUrl = "data:" + mimeType + ";base64," + base64Image;

        log.info("Logo uploaded as Base64, size: {} bytes -> {} chars", file.getSize(), dataUrl.length());

        updateConfig("logo_url", dataUrl);

        return dataUrl;
    }

    /**
     * 获取配置值
     */
    private String getConfigValue(String configKey, String defaultValue) {
        return styleConfigRepository.findByConfigKey(configKey)
                .map(SysStyleConfig::getConfigValue)
                .orElse(defaultValue);
    }

    /**
     * 更新配置值
     */
    private void updateConfig(String configKey, String value) {
        SysStyleConfig config = styleConfigRepository.findByConfigKey(configKey)
                .orElseGet(() -> {
                    SysStyleConfig newConfig = new SysStyleConfig();
                    newConfig.setConfigKey(configKey);
                    return newConfig;
                });

        config.setConfigValue(value);
        styleConfigRepository.save(config);
    }

    private void validateHexColor(String color) {
        if (color != null && !HEX_COLOR_PATTERN.matcher(color).matches()) {
            throw new IllegalArgumentException("无效的颜色格式: " + color + "，应为 #RRGGBB 格式");
        }
    }

    private void validateFontSize(String fontSize) {
        if (fontSize != null && !FONT_SIZE_PATTERN.matcher(fontSize).matches()) {
            throw new IllegalArgumentException("无效的字体大小格式: " + fontSize + "，应为数字+单位(rem/px/em)");
        }
    }

    /**
     * 回滚到指定版本
     */
    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void rollbackToVersion(Long versionId, Long changedBy) {
        StyleConfigDTO snapshotConfig = versionService.getConfigSnapshot(versionId)
                .orElseThrow(() -> new IllegalArgumentException("版本不存在或快照损坏: " + versionId));

        // 写回配置
        if (snapshotConfig.getSystemName() != null) {
            updateConfig("system_name", snapshotConfig.getSystemName());
        }
        if (snapshotConfig.getPriceRiseColor() != null) {
            updateConfig("price_rise_color", snapshotConfig.getPriceRiseColor());
        }
        if (snapshotConfig.getPriceFallColor() != null) {
            updateConfig("price_fall_color", snapshotConfig.getPriceFallColor());
        }
        if (snapshotConfig.getPriceFlatColor() != null) {
            updateConfig("price_flat_color", snapshotConfig.getPriceFlatColor());
        }
        if (snapshotConfig.getChartPrimaryColor() != null) {
            updateConfig("chart_primary_color", snapshotConfig.getChartPrimaryColor());
        }
        if (snapshotConfig.getChartBudgetColor() != null) {
            updateConfig("chart_budget_color", snapshotConfig.getChartBudgetColor());
        }
        if (snapshotConfig.getChartColors() != null) {
            updateConfig("chart_colors", snapshotConfig.getChartColors());
        }
        if (snapshotConfig.getHeadingFont() != null) {
            updateConfig("heading_font", snapshotConfig.getHeadingFont());
        }
        if (snapshotConfig.getBodyFont() != null) {
            updateConfig("body_font", snapshotConfig.getBodyFont());
        }
        if (snapshotConfig.getNumberFont() != null) {
            updateConfig("number_font", snapshotConfig.getNumberFont());
        }
        if (snapshotConfig.getLogoUrl() != null) {
            updateConfig("logo_url", snapshotConfig.getLogoUrl());
        }
        if (snapshotConfig.getLogoSize() != null) {
            updateConfig("logo_size", snapshotConfig.getLogoSize());
        }
        if (snapshotConfig.getActiveTheme() != null) {
            updateConfig("active_theme", snapshotConfig.getActiveTheme());
        }
        if (snapshotConfig.getActiveColorScheme() != null) {
            updateConfig("active_color_scheme", snapshotConfig.getActiveColorScheme());
        }
        if (snapshotConfig.getActiveLayoutStyle() != null) {
            updateConfig("active_layout_style", snapshotConfig.getActiveLayoutStyle());
        }
        if (snapshotConfig.getFontSizePreset() != null) {
            updateConfig("font_size_preset", snapshotConfig.getFontSizePreset());
        }
        if (snapshotConfig.getFontSizeXs() != null) {
            updateConfig("font_size_xs", snapshotConfig.getFontSizeXs());
        }
        if (snapshotConfig.getFontSizeSm() != null) {
            updateConfig("font_size_sm", snapshotConfig.getFontSizeSm());
        }
        if (snapshotConfig.getFontSizeBase() != null) {
            updateConfig("font_size_base", snapshotConfig.getFontSizeBase());
        }
        if (snapshotConfig.getFontSizeLg() != null) {
            updateConfig("font_size_lg", snapshotConfig.getFontSizeLg());
        }
        if (snapshotConfig.getFontSizeXl() != null) {
            updateConfig("font_size_xl", snapshotConfig.getFontSizeXl());
        }
        if (snapshotConfig.getFontSize2xl() != null) {
            updateConfig("font_size_2xl", snapshotConfig.getFontSize2xl());
        }
        if (snapshotConfig.getFontSize3xl() != null) {
            updateConfig("font_size_3xl", snapshotConfig.getFontSize3xl());
        }

        // 生成回滚版本快照
        StyleConfigDTO newSnapshot = getStyleConfig();
        versionService.saveSnapshot(newSnapshot, "回滚到版本 #" + versionId, changedBy);

        log.info("样式配置已回滚到版本: versionId={}", versionId);
    }
}
