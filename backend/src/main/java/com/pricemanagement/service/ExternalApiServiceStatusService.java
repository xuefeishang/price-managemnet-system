package com.pricemanagement.service;

import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.dto.ExternalApiServiceStatusDTO;
import com.pricemanagement.entity.SysStyleConfig;
import com.pricemanagement.repository.SysStyleConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExternalApiServiceStatusService {

    public static final String CONFIG_KEY = "external_api_service_enabled";

    private final ApiKeyProperties properties;
    private final SysStyleConfigRepository configRepository;

    @Transactional(readOnly = true)
    public ExternalApiServiceStatusDTO getStatus() {
        boolean deploymentEnabled = properties.isEnabled();
        boolean runtimeEnabled = isRuntimeEnabled();
        return new ExternalApiServiceStatusDTO(
                deploymentEnabled,
                runtimeEnabled,
                deploymentEnabled && runtimeEnabled,
                resolveMessage(deploymentEnabled, runtimeEnabled)
        );
    }

    @Transactional(readOnly = true)
    public boolean isRuntimeEnabled() {
        return configRepository.findByConfigKey(CONFIG_KEY)
                .map(SysStyleConfig::getConfigValue)
                .map(Boolean::parseBoolean)
                .orElse(true);
    }

    @Transactional
    public ExternalApiServiceStatusDTO updateRuntimeEnabled(boolean enabled) {
        SysStyleConfig config = configRepository.findByConfigKey(CONFIG_KEY)
                .orElseGet(() -> {
                    SysStyleConfig created = new SysStyleConfig();
                    created.setConfigKey(CONFIG_KEY);
                    created.setConfigType("boolean");
                    created.setDescription("外部 API 运行时服务开关");
                    return created;
                });
        config.setConfigValue(Boolean.toString(enabled));
        config.setConfigType("boolean");
        config.setDescription("外部 API 运行时服务开关");
        configRepository.save(config);
        return getStatus();
    }

    private String resolveMessage(boolean deploymentEnabled, boolean runtimeEnabled) {
        if (!deploymentEnabled) {
            return "部署配置未启用外部 API";
        }
        if (!runtimeEnabled) {
            return "外部 API 服务已暂停";
        }
        return "外部 API 服务运行中";
    }
}
