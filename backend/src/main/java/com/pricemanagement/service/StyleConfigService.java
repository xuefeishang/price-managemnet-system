package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.StyleConfigDTO;
import com.pricemanagement.dto.StyleThemeDTO;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.SysDictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class StyleConfigService {

    private final SysDictRepository sysDictRepository;
    private final ObjectMapper objectMapper;

    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024; // 2MB
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    @Value("${style.logo.dir:#{systemProperties['java.io.tmpdir']}/logos/}")
    private String logoDir;

    public static final String CATEGORY_STYLE = "style";
    public static final String CATEGORY_THEME = "theme";

    @Cacheable(value = "style", key = "'config'")
    public StyleConfigDTO getStyleConfig() {
        StyleConfigDTO config = new StyleConfigDTO();

        // 系统名称
        config.setSystemName(getDictValue(CATEGORY_STYLE, "system_name").orElse("价格管理系统"));
        getDictValue(CATEGORY_STYLE, "price_rise_color").ifPresent(config::setPriceRiseColor);
        getDictValue(CATEGORY_STYLE, "price_fall_color").ifPresent(config::setPriceFallColor);
        getDictValue(CATEGORY_STYLE, "price_flat_color").ifPresent(config::setPriceFlatColor);
        getDictValue(CATEGORY_STYLE, "chart_primary_color").ifPresent(config::setChartPrimaryColor);
        getDictValue(CATEGORY_STYLE, "chart_budget_color").ifPresent(config::setChartBudgetColor);
        getDictValue(CATEGORY_STYLE, "chart_colors").ifPresent(config::setChartColors);
        getDictValue(CATEGORY_STYLE, "heading_font").ifPresent(config::setHeadingFont);
        getDictValue(CATEGORY_STYLE, "body_font").ifPresent(config::setBodyFont);
        getDictValue(CATEGORY_STYLE, "number_font").ifPresent(config::setNumberFont);
        getDictValue(CATEGORY_STYLE, "logo_url").ifPresent(config::setLogoUrl);
        getDictValue(CATEGORY_STYLE, "logo_size").ifPresent(config::setLogoSize);
        getDictValue(CATEGORY_STYLE, "active_theme").ifPresent(config::setActiveTheme);

        return config;
    }

    @Cacheable(value = "style", key = "'themes'")
    public List<StyleThemeDTO> getPresetThemes() {
        List<StyleThemeDTO> themes = new ArrayList<>();
        List<SysDict> themeDicts = sysDictRepository.findByCategoryOrderBySortOrderAsc(CATEGORY_THEME);

        String activeTheme = getStyleConfig().getActiveTheme();

        for (SysDict dict : themeDicts) {
            StyleThemeDTO theme = new StyleThemeDTO();
            theme.setThemeKey(dict.getDictKey());
            theme.setThemeName(dict.getDictValue());
            theme.setDescription(dict.getRemark());
            theme.setActive(dict.getDictKey().equals(activeTheme));

            if (dict.getExtraValue() != null) {
                try {
                    Map<String, String> colors = objectMapper.readValue(dict.getExtraValue(),
                            new TypeReference<Map<String, String>>() {});
                    theme.setColors(colors);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse theme colors for {}: {}", dict.getDictKey(), e.getMessage());
                }
            }

            themes.add(theme);
        }

        return themes;
    }

    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void updateStyleConfig(StyleConfigDTO config) {
        if (config.getSystemName() != null && !config.getSystemName().isBlank()) {
            updateDict(CATEGORY_STYLE, "system_name", config.getSystemName());
        }
        if (config.getPriceRiseColor() != null) {
            validateHexColor(config.getPriceRiseColor());
            updateDict(CATEGORY_STYLE, "price_rise_color", config.getPriceRiseColor());
        }
        if (config.getPriceFallColor() != null) {
            validateHexColor(config.getPriceFallColor());
            updateDict(CATEGORY_STYLE, "price_fall_color", config.getPriceFallColor());
        }
        if (config.getPriceFlatColor() != null) {
            validateHexColor(config.getPriceFlatColor());
            updateDict(CATEGORY_STYLE, "price_flat_color", config.getPriceFlatColor());
        }
        if (config.getChartPrimaryColor() != null) {
            validateHexColor(config.getChartPrimaryColor());
            updateDict(CATEGORY_STYLE, "chart_primary_color", config.getChartPrimaryColor());
        }
        if (config.getChartBudgetColor() != null) {
            validateHexColor(config.getChartBudgetColor());
            updateDict(CATEGORY_STYLE, "chart_budget_color", config.getChartBudgetColor());
        }
        if (config.getChartColors() != null) {
            updateDict(CATEGORY_STYLE, "chart_colors", config.getChartColors());
        }
        if (config.getHeadingFont() != null) {
            updateDict(CATEGORY_STYLE, "heading_font", config.getHeadingFont());
        }
        if (config.getBodyFont() != null) {
            updateDict(CATEGORY_STYLE, "body_font", config.getBodyFont());
        }
        if (config.getNumberFont() != null) {
            updateDict(CATEGORY_STYLE, "number_font", config.getNumberFont());
        }
        if (config.getLogoUrl() != null) {
            updateDict(CATEGORY_STYLE, "logo_url", config.getLogoUrl());
        }
        if (config.getLogoSize() != null) {
            updateDict(CATEGORY_STYLE, "logo_size", config.getLogoSize());
        }
        if (config.getActiveTheme() != null) {
            updateDict(CATEGORY_STYLE, "active_theme", config.getActiveTheme());
        }
    }

    @Transactional
    @CacheEvict(value = {"style", "dict"}, allEntries = true)
    public void switchTheme(String themeKey) {
        List<SysDict> themeDicts = sysDictRepository.findByCategoryOrderBySortOrderAsc(CATEGORY_THEME);
        SysDict targetTheme = themeDicts.stream()
                .filter(t -> t.getDictKey().equals(themeKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("主题不存在: " + themeKey));

        updateDict(CATEGORY_STYLE, "active_theme", themeKey);

        if (targetTheme.getExtraValue() != null) {
            try {
                Map<String, String> colors = objectMapper.readValue(targetTheme.getExtraValue(),
                        new TypeReference<Map<String, String>>() {});
                if (colors.get("priceRise") != null) {
                    updateDict(CATEGORY_STYLE, "price_rise_color", colors.get("priceRise"));
                }
                if (colors.get("priceFall") != null) {
                    updateDict(CATEGORY_STYLE, "price_fall_color", colors.get("priceFall"));
                }
                if (colors.get("priceFlat") != null) {
                    updateDict(CATEGORY_STYLE, "price_flat_color", colors.get("priceFlat"));
                }
                if (colors.get("chartPrimary") != null) {
                    updateDict(CATEGORY_STYLE, "chart_primary_color", colors.get("chartPrimary"));
                }
                if (colors.get("chartBudget") != null) {
                    updateDict(CATEGORY_STYLE, "chart_budget_color", colors.get("chartBudget"));
                }
                if (colors.get("chartColors") != null) {
                    updateDict(CATEGORY_STYLE, "chart_colors", colors.get("chartColors"));
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to apply theme colors: {}", e.getMessage());
            }
        }
    }

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
            throw new IllegalArgumentException("Logo 文件大小不能超过 2MB");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String newFilename = "logo_" + System.currentTimeMillis() + extension;
        Path logoDir = Paths.get(this.logoDir);

        if (!Files.exists(logoDir)) {
            Files.createDirectories(logoDir);
        }

        Path targetPath = logoDir.resolve(newFilename);
        Files.copy(file.getInputStream(), targetPath);

        log.info("Logo uploaded: {}", targetPath.toAbsolutePath());

        // 自动保存 logo URL 到样式配置
        String logoUrl = "/api/static/logo/" + newFilename;
        updateDict(CATEGORY_STYLE, "logo_url", logoUrl);

        return logoUrl;
    }

    private void validateHexColor(String color) {
        if (color != null && !HEX_COLOR_PATTERN.matcher(color).matches()) {
            throw new IllegalArgumentException("无效的颜色格式: " + color + "，应为 #RRGGBB 格式");
        }
    }

    private Optional<String> getDictValue(String category, String dictKey) {
        return sysDictRepository.findByCategoryAndDictKey(category, dictKey)
                .map(SysDict::getExtraValue);
    }

    private void updateDict(String category, String dictKey, String value) {
        Optional<SysDict> existing = sysDictRepository.findByCategoryAndDictKey(category, dictKey);
        if (existing.isPresent()) {
            SysDict dict = existing.get();
            dict.setExtraValue(value);
            sysDictRepository.save(dict);
        } else {
            SysDict dict = new SysDict();
            dict.setCategory(category);
            dict.setDictKey(dictKey);
            dict.setDictValue(dictKey);
            dict.setExtraValue(value);
            dict.setSortOrder(1);
            dict.setStatus(CommonStatus.ACTIVE);
            sysDictRepository.save(dict);
        }
    }
}
