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
import java.util.Optional;

/**
 * 样式版本管理服务
 * 负责版本快照的创建、查询、回滚
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

        String configJson;
        try {
            configJson = objectMapper.writeValueAsString(config);
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
        log.info("保存样式版本快照: versionNo={}, changedBy={}", versionNo, changedBy);

        // 清理旧版本（保留最近100条）
        cleanupOldVersions();

        return versionNo;
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
     */
    public Optional<StyleConfigDTO> getConfigSnapshot(Long versionId) {
        return versionRepository.findById(versionId).map(version -> {
            try {
                return objectMapper.readValue(version.getConfigSnapshot(), StyleConfigDTO.class);
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
