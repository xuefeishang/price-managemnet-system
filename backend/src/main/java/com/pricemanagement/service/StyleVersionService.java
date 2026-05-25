package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.dto.StyleConfigDTO;
import com.pricemanagement.dto.StyleVersionDTO;
import com.pricemanagement.entity.SysStyleVersion;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.SysStyleVersionRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 样式版本管理服务
 * 负责版本快照的创建、查询、回滚
 *
 * 快照策略：排除 Logo base64 大字段，仅保留配置项
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StyleVersionService {

    private final SysStyleVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final int MAX_VERSIONS = 100;
    private static final DateTimeFormatter VERSION_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // 快照中排除的字段（Logo base64 大字段）
    private static final String[] EXCLUDED_FIELDS = {"logoUrl", "logoUrlLogin", "logoUrlNav"};

    /**
     * 保存配置快照
     *
     * @param config        配置DTO
     * @param changeSummary 变更说明
     * @param changedBy     变更人ID
     * @return 版本号
     */
    @Transactional
    public String saveSnapshot(StyleConfigDTO config, String changeSummary, Long changedBy) {
        String versionNo = generateVersionNo();

        // 创建轻量快照（排除 Logo base64 字段）
        Map<String, Object> snapshotData = createLightweightSnapshot(config);

        String configJson;
        try {
            configJson = objectMapper.writeValueAsString(snapshotData);
        } catch (JsonProcessingException e) {
            log.error("序列化配置快照失败", e);
            throw new RuntimeException("序列化配置快照失败", e);
        }

        SysStyleVersion version = new SysStyleVersion();
        version.setVersionNo(versionNo);
        version.setConfigSnapshot(configJson);
        version.setChangeSummary(changeSummary != null ? changeSummary : "样式配置更新");
        version.setChangedBy(changedBy);

        versionRepository.save(version);
        log.info("保存样式版本快照: versionNo={}, changedBy={}, snapshotSize={} chars", versionNo, changedBy, configJson.length());

        // 清理旧版本（保留最近100条）
        cleanupOldVersions();

        return versionNo;
    }

    /**
     * 创建轻量快照（排除 Logo base64 字段）
     * Logo 数据单独存储在 sys_style_config 表，版本快照仅记录配置项
     */
    private Map<String, Object> createLightweightSnapshot(StyleConfigDTO config) {
        Map<String, Object> snapshot = new HashMap<>();

        // 基础配置
        snapshot.put("systemName", config.getSystemName());
        snapshot.put("priceRiseColor", config.getPriceRiseColor());
        snapshot.put("priceFallColor", config.getPriceFallColor());
        snapshot.put("priceFlatColor", config.getPriceFlatColor());
        snapshot.put("chartPrimaryColor", config.getChartPrimaryColor());
        snapshot.put("chartBudgetColor", config.getChartBudgetColor());
        snapshot.put("chartColors", config.getChartColors());
        snapshot.put("headingFont", config.getHeadingFont());
        snapshot.put("bodyFont", config.getBodyFont());
        snapshot.put("numberFont", config.getNumberFont());
        snapshot.put("logoSize", config.getLogoSize());
        snapshot.put("activeTheme", config.getActiveTheme());
        snapshot.put("activeColorScheme", config.getActiveColorScheme());
        snapshot.put("activeLayoutStyle", config.getActiveLayoutStyle());
        snapshot.put("fontSizePreset", config.getFontSizePreset());

        // 字体大小
        snapshot.put("fontSizeXs", config.getFontSizeXs());
        snapshot.put("fontSizeSm", config.getFontSizeSm());
        snapshot.put("fontSizeBase", config.getFontSizeBase());
        snapshot.put("fontSizeLg", config.getFontSizeLg());
        snapshot.put("fontSizeXl", config.getFontSizeXl());
        snapshot.put("fontSize2xl", config.getFontSize2xl());
        snapshot.put("fontSize3xl", config.getFontSize3xl());

        // Logo 尺寸配置（保留）
        snapshot.put("logoSizeLogin", config.getLogoSizeLogin());
        snapshot.put("logoSizeNav", config.getLogoSizeNav());

        // 副标题配置
        snapshot.put("subtitleText", config.getSubtitleText());
        snapshot.put("subtitleFont", config.getSubtitleFont());
        snapshot.put("subtitleFontWeight", config.getSubtitleFontWeight());
        snapshot.put("subtitleColor", config.getSubtitleColor());

        // Logo 引用信息（仅记录是否有 Logo，不存储 base64）
        Map<String, Object> assetRefs = new HashMap<>();
        assetRefs.put("logoUrl", createAssetRef(config.getLogoUrl()));
        assetRefs.put("logoUrlLogin", createAssetRef(config.getLogoUrlLogin()));
        assetRefs.put("logoUrlNav", createAssetRef(config.getLogoUrlNav()));
        snapshot.put("assetRefs", assetRefs);

        // 记录排除的字段
        snapshot.put("excludedFields", EXCLUDED_FIELDS);

        return snapshot;
    }

    /**
     * 创建资源引用信息（不包含实际数据）
     */
    private Map<String, Object> createAssetRef(String url) {
        Map<String, Object> ref = new HashMap<>();
        if (url == null || url.isEmpty()) {
            ref.put("hasValue", false);
        } else if (url.startsWith("data:")) {
            // Base64 数据：记录类型和大小估算
            ref.put("hasValue", true);
            ref.put("type", "base64");
            ref.put("mimeType", extractMimeType(url));
            ref.put("sizeEstimate", url.length());
        } else {
            // URL 引用：记录完整 URL
            ref.put("hasValue", true);
            ref.put("type", "url");
            ref.put("url", url);
        }
        return ref;
    }

    /**
     * 从 data URL 中提取 MIME 类型
     */
    private String extractMimeType(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            return "unknown";
        }
        int semicolon = dataUrl.indexOf(';');
        if (semicolon > 5) {
            return dataUrl.substring(5, semicolon);
        }
        return "unknown";
    }

    /**
     * 查询版本列表
     */
    public Page<StyleVersionDTO> getVersionList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SysStyleVersion> versions = versionRepository.findAllByOrderByCreatedTimeDesc(pageable);

        return versions.map(this::toDTO);
    }

    /**
     * 查询版本详情
     */
    public Optional<StyleVersionDTO> getVersionById(Long versionId) {
        return versionRepository.findById(versionId).map(this::toDTO);
    }

    /**
     * 根据版本号查询版本详情
     */
    public Optional<StyleVersionDTO> getVersionByNo(String versionNo) {
        return versionRepository.findByVersionNo(versionNo).map(this::toDTO);
    }

    /**
     * 获取版本快照中的配置
     * 注意：快照中不包含 Logo base64 数据，回滚时 Logo 保持当前值
     */
    public Optional<StyleConfigDTO> getConfigSnapshot(Long versionId) {
        return versionRepository.findById(versionId).map(version -> {
            try {
                Map<String, Object> snapshotMap = objectMapper.readValue(
                    version.getConfigSnapshot(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );

                StyleConfigDTO config = new StyleConfigDTO();

                // 基础配置
                config.setSystemName((String) snapshotMap.get("systemName"));
                config.setPriceRiseColor((String) snapshotMap.get("priceRiseColor"));
                config.setPriceFallColor((String) snapshotMap.get("priceFallColor"));
                config.setPriceFlatColor((String) snapshotMap.get("priceFlatColor"));
                config.setChartPrimaryColor((String) snapshotMap.get("chartPrimaryColor"));
                config.setChartBudgetColor((String) snapshotMap.get("chartBudgetColor"));
                config.setChartColors((String) snapshotMap.get("chartColors"));
                config.setHeadingFont((String) snapshotMap.get("headingFont"));
                config.setBodyFont((String) snapshotMap.get("bodyFont"));
                config.setNumberFont((String) snapshotMap.get("numberFont"));
                config.setLogoSize((String) snapshotMap.get("logoSize"));
                config.setActiveTheme((String) snapshotMap.get("activeTheme"));
                config.setActiveColorScheme((String) snapshotMap.get("activeColorScheme"));
                config.setActiveLayoutStyle((String) snapshotMap.get("activeLayoutStyle"));
                config.setFontSizePreset((String) snapshotMap.get("fontSizePreset"));

                // 字体大小
                config.setFontSizeXs((String) snapshotMap.get("fontSizeXs"));
                config.setFontSizeSm((String) snapshotMap.get("fontSizeSm"));
                config.setFontSizeBase((String) snapshotMap.get("fontSizeBase"));
                config.setFontSizeLg((String) snapshotMap.get("fontSizeLg"));
                config.setFontSizeXl((String) snapshotMap.get("fontSizeXl"));
                config.setFontSize2xl((String) snapshotMap.get("fontSize2xl"));
                config.setFontSize3xl((String) snapshotMap.get("fontSize3xl"));

                // Logo 尺寸
                config.setLogoSizeLogin((String) snapshotMap.get("logoSizeLogin"));
                config.setLogoSizeNav((String) snapshotMap.get("logoSizeNav"));

                // 副标题配置
                config.setSubtitleText((String) snapshotMap.get("subtitleText"));
                config.setSubtitleFont((String) snapshotMap.get("subtitleFont"));
                config.setSubtitleFontWeight((String) snapshotMap.get("subtitleFontWeight"));
                config.setSubtitleColor((String) snapshotMap.get("subtitleColor"));

                // Logo URL 不从快照恢复（保持当前值）
                // 快照中的 assetRefs 仅用于审计，不参与回滚

                return config;
            } catch (JsonProcessingException e) {
                log.error("解析配置快照失败: versionId={}", versionId, e);
                throw new IllegalStateException("版本快照损坏，无法解析: " + versionId, e);
            }
        });
    }

    /**
     * 获取最新版本
     */
    public Optional<StyleVersionDTO> getLatestVersion() {
        return versionRepository.findFirstByOrderByCreatedTimeDesc().map(this::toDTO);
    }

    /**
     * 统计版本总数
     */
    public long countVersions() {
        return versionRepository.count();
    }

    /**
     * 生成版本号
     */
    private String generateVersionNo() {
        String timestamp = LocalDateTime.now().format(VERSION_FORMATTER);
        return "v" + timestamp;
    }

    /**
     * 清理旧版本（保留最近100条）
     */
    @Transactional
    public void cleanupOldVersions() {
        long total = versionRepository.count();
        if (total > MAX_VERSIONS) {
            log.info("版本数量超过限制({}), 开始清理旧版本, 当前数量: {}", MAX_VERSIONS, total);
            int deleted = versionRepository.deleteOldVersions(MAX_VERSIONS);
            log.info("已清理 {} 条旧版本, 保留最近 {} 条", deleted, MAX_VERSIONS);
        }
    }

    /**
     * 转换为DTO
     */
    private StyleVersionDTO toDTO(SysStyleVersion version) {
        String changedByName = null;
        if (version.getChangedBy() != null) {
            changedByName = userRepository.findById(version.getChangedBy())
                    .map(User::getUsername)
                    .orElse("系统");
        }

        return StyleVersionDTO.builder()
                .id(version.getId())
                .versionNo(version.getVersionNo())
                .configSnapshot(version.getConfigSnapshot())
                .changeSummary(version.getChangeSummary())
                .changedBy(version.getChangedBy())
                .changedByName(changedByName)
                .createdTime(version.getCreatedTime())
                .build();
    }

}
