package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.dto.ApiKeyCreateRequest;
import com.pricemanagement.dto.ApiKeyCreateResponse;
import com.pricemanagement.dto.ApiKeyDTO;
import com.pricemanagement.dto.ApiKeyUpdateRequest;
import com.pricemanagement.dto.ExternalApiEndpointDTO;
import com.pricemanagement.entity.ApiKey;
import com.pricemanagement.entity.ApiKeyOperationLog;
import com.pricemanagement.entity.ApiKeyPermission;
import com.pricemanagement.entity.ExternalApiEndpoint;
import com.pricemanagement.repository.ApiKeyOperationLogRepository;
import com.pricemanagement.repository.ApiKeyPermissionRepository;
import com.pricemanagement.repository.ApiKeyRepository;
import com.pricemanagement.repository.ExternalApiEndpointRepository;
import com.pricemanagement.util.SecurityUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyPermissionRepository permissionRepository;
    private final ExternalApiEndpointRepository endpointRepository;
    private final ApiKeyOperationLogRepository operationLogRepository;
    private final ApiKeySecretService secretService;
    private final ApiKeyProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ApiKeyDTO> query(int page, int size, String keyword, String status, String environment) {
        Specification<ApiKey> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim() + "%";
                predicates.add(cb.or(cb.like(root.get("name"), like), cb.like(root.get("appId"), like)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(environment)) {
                predicates.add(cb.equal(root.get("environment"), environment));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdTime"));
        Page<ApiKey> apiKeys = apiKeyRepository.findAll(spec, pageable);
        Map<Long, List<String>> permissionMap = loadPermissionMap(apiKeys.getContent().stream().map(ApiKey::getId).toList());
        return apiKeys.map(apiKey -> toDTO(apiKey, permissionMap.getOrDefault(apiKey.getId(), List.of())));
    }

    @Transactional
    public ApiKeyCreateResponse create(ApiKeyCreateRequest request, String operatorIp) {
        ensureSecretEncryptionReady();
        String appSecret = secretService.generateSecret();
        ApiKey apiKey = new ApiKey();
        apiKey.setName(required(request.getName(), "密钥名称不能为空"));
        apiKey.setAppId(generateUniqueAppId());
        apiKey.setAppSecretCipher(secretService.encrypt(appSecret));
        apiKey.setAppSecretKeyVersion(properties.getEncryptionKeyVersion());
        apiKey.setAppSecretFingerprint(secretService.fingerprint(appSecret));
        apiKey.setDescription(request.getDescription());
        apiKey.setStatus("ACTIVE");
        apiKey.setEnvironment(defaultIfBlank(request.getEnvironment(), "TESTING"));
        apiKey.setExpireTime(request.getExpireTime());
        apiKey.setIpWhitelist(toJson(request.getIpWhitelist()));
        apiKey.setRateLimitPerMinute(defaultIfNull(request.getRateLimitPerMinute(), 60));
        apiKey.setDailyLimit(defaultIfNull(request.getDailyLimit(), 10000));
        apiKey.setCreatedBy(SecurityUtils.getCurrentUserId());
        ApiKey saved = apiKeyRepository.save(apiKey);
        savePermissions(saved.getId(), request.getPermissionCodes());
        logOperation(saved.getId(), "CREATE", operatorIp, Map.of("name", saved.getName()));

        ApiKeyCreateResponse response = new ApiKeyCreateResponse();
        response.setApiKey(toDTO(saved, normalizePermissions(request.getPermissionCodes())));
        response.setAppSecret(appSecret);
        return response;
    }

    private void ensureSecretEncryptionReady() {
        try {
            properties.requireValidEncryptionKey("创建API Key前必须配置 API_KEY_ENCRYPTION_KEY");
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public ApiKeyDTO get(Long id) {
        ApiKey apiKey = findById(id);
        return toDTO(apiKey, permissionRepository.findByApiKeyId(id).stream()
                .map(ApiKeyPermission::getPermissionCode)
                .toList());
    }

    @Transactional
    public ApiKeyDTO update(Long id, ApiKeyUpdateRequest request, String operatorIp) {
        ApiKey apiKey = findById(id);
        if ("REVOKED".equals(apiKey.getStatus())) {
            throw new IllegalArgumentException("已吊销密钥不能编辑");
        }
        if (StringUtils.hasText(request.getName())) {
            apiKey.setName(request.getName());
        }
        apiKey.setDescription(request.getDescription());
        if (StringUtils.hasText(request.getEnvironment())) {
            apiKey.setEnvironment(request.getEnvironment());
        }
        apiKey.setExpireTime(request.getExpireTime());
        apiKey.setIpWhitelist(toJson(request.getIpWhitelist()));
        apiKey.setRateLimitPerMinute(defaultIfNull(request.getRateLimitPerMinute(), apiKey.getRateLimitPerMinute()));
        apiKey.setDailyLimit(defaultIfNull(request.getDailyLimit(), apiKey.getDailyLimit()));
        ApiKey saved = apiKeyRepository.save(apiKey);
        savePermissions(id, request.getPermissionCodes());
        logOperation(id, "UPDATE", operatorIp, Map.of("name", saved.getName()));
        return get(id);
    }

    @Transactional
    public ApiKeyDTO enable(Long id, String operatorIp) {
        ApiKey apiKey = findById(id);
        if ("REVOKED".equals(apiKey.getStatus())) {
            throw new IllegalArgumentException("已吊销密钥不能启用");
        }
        apiKey.setStatus("ACTIVE");
        apiKeyRepository.save(apiKey);
        logOperation(id, "ENABLE", operatorIp, Map.of("appId", apiKey.getAppId()));
        return get(id);
    }

    @Transactional
    public ApiKeyDTO disable(Long id, String operatorIp) {
        ApiKey apiKey = findById(id);
        if ("REVOKED".equals(apiKey.getStatus())) {
            throw new IllegalArgumentException("已吊销密钥不能停用");
        }
        apiKey.setStatus("DISABLED");
        apiKeyRepository.save(apiKey);
        logOperation(id, "DISABLE", operatorIp, Map.of("appId", apiKey.getAppId()));
        return get(id);
    }

    @Transactional
    public ApiKeyDTO revoke(Long id, String operatorIp) {
        ApiKey apiKey = findById(id);
        apiKey.setStatus("REVOKED");
        apiKeyRepository.save(apiKey);
        logOperation(id, "REVOKE", operatorIp, Map.of("appId", apiKey.getAppId()));
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<ExternalApiEndpointDTO> getPermissionEndpoints() {
        return endpointRepository.findByStatusOrderBySortOrderAsc("ACTIVE").stream()
                .map(this::toEndpointDTO)
                .toList();
    }

    private ApiKey findById(Long id) {
        return apiKeyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("API Key不存在: " + id));
    }

    private String generateUniqueAppId() {
        for (int i = 0; i < 5; i++) {
            String appId = secretService.generateAppId();
            if (!apiKeyRepository.existsByAppId(appId)) {
                return appId;
            }
        }
        throw new IllegalStateException("生成App ID失败，请重试");
    }

    private void savePermissions(Long apiKeyId, Collection<String> permissionCodes) {
        permissionRepository.deleteByApiKeyId(apiKeyId);
        permissionRepository.flush();
        List<ApiKeyPermission> permissions = normalizePermissions(permissionCodes).stream().map(code -> {
            ApiKeyPermission permission = new ApiKeyPermission();
            permission.setApiKeyId(apiKeyId);
            permission.setPermissionCode(code);
            return permission;
        }).toList();
        permissionRepository.saveAll(permissions);
    }

    private List<String> normalizePermissions(Collection<String> permissionCodes) {
        if (permissionCodes == null) {
            return List.of();
        }
        Set<String> validCodes = endpointRepository.findByStatusOrderBySortOrderAsc("ACTIVE").stream()
                .map(ExternalApiEndpoint::getPermissionCode)
                .collect(Collectors.toSet());
        return permissionCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .filter(validCodes::contains)
                .distinct()
                .toList();
    }

    private Map<Long, List<String>> loadPermissionMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return permissionRepository.findByApiKeyIdIn(ids).stream()
                .collect(Collectors.groupingBy(ApiKeyPermission::getApiKeyId,
                        Collectors.mapping(ApiKeyPermission::getPermissionCode, Collectors.toList())));
    }

    private ApiKeyDTO toDTO(ApiKey apiKey, List<String> permissions) {
        ApiKeyDTO dto = new ApiKeyDTO();
        dto.setId(apiKey.getId());
        dto.setName(apiKey.getName());
        dto.setAppId(apiKey.getAppId());
        dto.setAppSecretFingerprint(maskFingerprint(apiKey.getAppSecretFingerprint()));
        dto.setAppSecretKeyVersion(apiKey.getAppSecretKeyVersion());
        dto.setDescription(apiKey.getDescription());
        dto.setStatus(resolveRuntimeStatus(apiKey));
        dto.setEnvironment(apiKey.getEnvironment());
        dto.setExpireTime(apiKey.getExpireTime());
        dto.setIpWhitelist(fromJsonList(apiKey.getIpWhitelist()));
        dto.setRateLimitPerMinute(apiKey.getRateLimitPerMinute());
        dto.setDailyLimit(apiKey.getDailyLimit());
        dto.setCreatedBy(apiKey.getCreatedBy());
        dto.setCreatedTime(apiKey.getCreatedTime());
        dto.setUpdatedTime(apiKey.getUpdatedTime());
        dto.setLastUsedTime(apiKey.getLastUsedTime());
        dto.setPermissionCodes(permissions == null ? List.of() : permissions);
        return dto;
    }

    private ExternalApiEndpointDTO toEndpointDTO(ExternalApiEndpoint endpoint) {
        ExternalApiEndpointDTO dto = new ExternalApiEndpointDTO();
        dto.setId(endpoint.getId());
        dto.setPermissionCode(endpoint.getPermissionCode());
        dto.setMethod(endpoint.getMethod());
        dto.setPathPattern(endpoint.getPathPattern());
        dto.setDescription(endpoint.getDescription());
        dto.setRequestExample(endpoint.getRequestExample());
        dto.setResponseExample(endpoint.getResponseExample());
        dto.setErrorCodes(endpoint.getErrorCodes());
        dto.setUsageNotes(endpoint.getUsageNotes());
        dto.setQueryExample(endpoint.getQueryExample());
        dto.setBodyExample(endpoint.getBodyExample());
        dto.setPathParamsExample(endpoint.getPathParamsExample());
        dto.setQuerySchema(endpoint.getQuerySchema());
        dto.setBodySchema(endpoint.getBodySchema());
        dto.setPathParamsSchema(endpoint.getPathParamsSchema());
        dto.setSuccessExample(endpoint.getSuccessExample());
        dto.setFailureExample(endpoint.getFailureExample());
        dto.setCodeNotes(endpoint.getCodeNotes());
        dto.setStatus(endpoint.getStatus());
        dto.setSortOrder(endpoint.getSortOrder());
        return dto;
    }

    private String resolveRuntimeStatus(ApiKey apiKey) {
        if ("ACTIVE".equals(apiKey.getStatus())
                && apiKey.getExpireTime() != null
                && apiKey.getExpireTime().isBefore(LocalDateTime.now())) {
            return "EXPIRED";
        }
        return apiKey.getStatus();
    }

    private String maskFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.length() <= 8) {
            return fingerprint;
        }
        return fingerprint.substring(0, 8);
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON序列化失败", ex);
        }
    }

    private List<String> fromJsonList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void logOperation(Long apiKeyId, String operation, String operatorIp, Map<String, Object> detail) {
        ApiKeyOperationLog log = new ApiKeyOperationLog();
        log.setApiKeyId(apiKeyId);
        log.setOperation(operation);
        log.setOperatorId(SecurityUtils.getCurrentUserId());
        log.setOperatorIp(operatorIp);
        try {
            log.setDetail(objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException ex) {
            log.setDetail("{}");
        }
        operationLogRepository.save(log);
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
