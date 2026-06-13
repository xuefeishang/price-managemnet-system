package com.pricemanagement.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationMiniProgramTemplateDTO;
import com.pricemanagement.dto.NotificationMiniProgramTemplateRequest;
import com.pricemanagement.entity.NotificationMiniProgramTemplate;
import com.pricemanagement.entity.NotificationMiniProgramTemplateHistory;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.NotificationMiniProgramTemplateHistoryRepository;
import com.pricemanagement.repository.NotificationMiniProgramTemplateRepository;
import com.pricemanagement.repository.SysDictRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationMiniProgramTemplateService {

    private static final Pattern WECHAT_FIELD_PATTERN = Pattern.compile("^[a-zA-Z]+\\d+$");
    private static final TypeReference<LinkedHashMap<String, String>> FIELD_MAP_TYPE = new TypeReference<>() {
    };

    private final NotificationMiniProgramTemplateRepository templateRepository;
    private final NotificationMiniProgramTemplateHistoryRepository historyRepository;
    private final NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final SysDictRepository sysDictRepository;
    private final ObjectMapper objectMapper;
    private final NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NotificationMiniProgramTemplateDTO list() {
        backfillStoredChannelTemplatesInternal(0L);
        List<NotificationMiniProgramTemplate> stored = templateRepository.findAll().stream()
                .sorted(Comparator
                        .comparing(NotificationMiniProgramTemplate::getNotificationType)
                        .thenComparing(NotificationMiniProgramTemplate::getUpdatedTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(NotificationMiniProgramTemplate::getId, Comparator.reverseOrder()))
                .toList();
        Map<String, List<NotificationMiniProgramTemplate>> byType = stored.stream()
                .collect(Collectors.groupingBy(
                        NotificationMiniProgramTemplate::getNotificationType,
                        LinkedHashMap::new,
                        Collectors.toCollection(ArrayList::new)));
        activeNotificationTypes(stored).forEach(type -> byType.putIfAbsent(type, new ArrayList<>()));

        NotificationMiniProgramTemplateDTO dto = new NotificationMiniProgramTemplateDTO();
        long targetCount = targetUserCount();
        byType.forEach((type, versions) -> {
            Impact impact = impact(type, activeTemplateId(type), targetCount);
            NotificationMiniProgramTemplateDTO.Group group = new NotificationMiniProgramTemplateDTO.Group();
            group.setNotificationType(type);
            group.setAuthorizedUsers(impact.authorizedUsers());
            group.setNeedReauthorizeUsers(impact.needReauthorizeUsers());
            group.setEstimatedReachableUsers(impact.estimatedReachableUsers());
            group.setVersions(versions.stream()
                    .map(item -> toItem(item, impact(item.getNotificationType(), item.getTemplateId(), targetCount)))
                    .toList());
            dto.getGroups().add(group);
        });
        dto.setSummary(summary(stored));
        return dto;
    }

    @Transactional
    public NotificationMiniProgramTemplateDTO.Item create(NotificationMiniProgramTemplateRequest request, Long operatorId) {
        validateRequest(request, true);
        NotificationMiniProgramTemplate template = new NotificationMiniProgramTemplate();
        template.setNotificationType(normalizeType(request.getNotificationType()));
        template.setTemplateId(normalize(request.getTemplateId()));
        template.setPage(normalizeNullable(request.getPage()));
        template.setFieldsJson(fieldsJson(request.getFields()));
        template.setStatus(NotificationMiniProgramTemplate.TemplateStatus.DRAFT);
        template.setCreatedBy(operatorId);
        template.setUpdatedBy(operatorId);
        NotificationMiniProgramTemplate saved = templateRepository.save(template);
        record(saved, NotificationMiniProgramTemplateHistory.TemplateAction.CREATE, operatorId, null,
                saved.getStatus().name(), "创建模板草稿");
        return toItem(saved, impact(saved.getNotificationType(), saved.getTemplateId(), targetUserCount()));
    }

    @Transactional
    public NotificationMiniProgramTemplateDTO.Item update(
            Long id,
            NotificationMiniProgramTemplateRequest request,
            Long operatorId) {
        NotificationMiniProgramTemplate template = requireTemplate(id);
        if (template.getStatus() == NotificationMiniProgramTemplate.TemplateStatus.ACTIVE) {
            throw new IllegalArgumentException("已生效模板不能直接编辑，请新建草稿后发布");
        }
        validateRequest(request, false);
        String before = template.getStatus().name();
        if (StringUtils.hasText(request.getNotificationType())) {
            template.setNotificationType(normalizeType(request.getNotificationType()));
        }
        if (StringUtils.hasText(request.getTemplateId())) {
            template.setTemplateId(normalize(request.getTemplateId()));
        }
        if (request.getPage() != null) {
            template.setPage(normalizeNullable(request.getPage()));
        }
        if (request.getFields() != null) {
            template.setFieldsJson(fieldsJson(request.getFields()));
            template.setLastTestStatus(null);
            template.setLastTestMessage("字段映射已变更，需重新测试");
            template.setLastTestDeliveryId(null);
            template.setLastTestTime(null);
        }
        template.setUpdatedBy(operatorId);
        NotificationMiniProgramTemplate saved = templateRepository.save(template);
        record(saved, NotificationMiniProgramTemplateHistory.TemplateAction.UPDATE, operatorId, before,
                saved.getStatus().name(), "更新模板草稿");
        return toItem(saved, impact(saved.getNotificationType(), saved.getTemplateId(), targetUserCount()));
    }

    @Transactional
    public NotificationMiniProgramTemplateDTO.Item validate(Long id, Long operatorId) {
        NotificationMiniProgramTemplate template = requireTemplate(id);
        validateTemplatePayload(template);
        String before = template.getStatus().name();
        String message = "模板本地结构校验通过";
        template.setStatus(template.getStatus() == NotificationMiniProgramTemplate.TemplateStatus.ACTIVE
                ? NotificationMiniProgramTemplate.TemplateStatus.ACTIVE
                : NotificationMiniProgramTemplate.TemplateStatus.TESTING);
        template.setLastTestStatus("PASS");
        template.setLastTestMessage(message);
        template.setLastTestDeliveryId(null);
        template.setLastTestTime(LocalDateTime.now());
        template.setUpdatedBy(operatorId);
        NotificationMiniProgramTemplate saved = templateRepository.save(template);
        record(saved, NotificationMiniProgramTemplateHistory.TemplateAction.TEST, operatorId, before,
                saved.getStatus().name(), message);
        return toItem(saved, impact(saved.getNotificationType(), saved.getTemplateId(), targetUserCount()));
    }

    @Transactional
    public NotificationMiniProgramTemplateDTO.Item publish(Long id, Long operatorId) {
        NotificationMiniProgramTemplate template = requireTemplate(id);
        validateTemplatePayload(template);
        if (!"PASS".equals(template.getLastTestStatus())) {
            throw new IllegalArgumentException("模板发布前必须完成测试校验");
        }
        String before = template.getStatus().name();
        templateRepository.lockByNotificationType(template.getNotificationType());
        templateRepository.disableOtherActive(template.getNotificationType(), template.getId(), operatorId);
        template.setStatus(NotificationMiniProgramTemplate.TemplateStatus.ACTIVE);
        template.setPublishedBy(operatorId);
        template.setPublishedTime(LocalDateTime.now());
        template.setUpdatedBy(operatorId);
        NotificationMiniProgramTemplate saved = templateRepository.save(template);
        record(saved, NotificationMiniProgramTemplateHistory.TemplateAction.PUBLISH, operatorId, before,
                saved.getStatus().name(), "发布为生效模板");
        notifyTemplateChanged();
        return toItem(saved, impact(saved.getNotificationType(), saved.getTemplateId(), targetUserCount()));
    }

    @Transactional
    public NotificationMiniProgramTemplateDTO.Item disable(Long id, Long operatorId) {
        NotificationMiniProgramTemplate template = requireTemplate(id);
        String before = template.getStatus().name();
        template.setStatus(NotificationMiniProgramTemplate.TemplateStatus.DISABLED);
        template.setUpdatedBy(operatorId);
        NotificationMiniProgramTemplate saved = templateRepository.save(template);
        record(saved, NotificationMiniProgramTemplateHistory.TemplateAction.DISABLE, operatorId, before,
                saved.getStatus().name(), "停用模板");
        notifyTemplateChanged();
        return toItem(saved, impact(saved.getNotificationType(), saved.getTemplateId(), targetUserCount()));
    }

    @Transactional
    public NotificationMiniProgramTemplateDTO.Item rollback(Long id, Long operatorId) {
        NotificationMiniProgramTemplate source = requireTemplate(id);
        validateTemplatePayload(source);
        if (source.getStatus() == NotificationMiniProgramTemplate.TemplateStatus.ACTIVE) {
            throw new IllegalArgumentException("当前生效模板无需回滚，请选择已停用且验证通过的历史版本");
        }
        if (!"PASS".equals(source.getLastTestStatus())) {
            throw new IllegalArgumentException("只能回滚到已测试通过的历史模板版本");
        }
        templateRepository.lockByNotificationType(source.getNotificationType());
        templateRepository.disableActiveByNotificationType(source.getNotificationType(), operatorId);
        NotificationMiniProgramTemplate rollback = new NotificationMiniProgramTemplate();
        rollback.setNotificationType(source.getNotificationType());
        rollback.setTemplateId(source.getTemplateId());
        rollback.setPage(source.getPage());
        rollback.setFieldsJson(source.getFieldsJson());
        rollback.setStatus(NotificationMiniProgramTemplate.TemplateStatus.ACTIVE);
        rollback.setLastTestStatus(firstText(source.getLastTestStatus(), "PASS"));
        rollback.setLastTestMessage("由历史版本回滚生效");
        rollback.setPublishedBy(operatorId);
        rollback.setPublishedTime(LocalDateTime.now());
        rollback.setCreatedBy(operatorId);
        rollback.setUpdatedBy(operatorId);
        NotificationMiniProgramTemplate saved = templateRepository.save(rollback);
        record(saved, NotificationMiniProgramTemplateHistory.TemplateAction.ROLLBACK, operatorId, null,
                saved.getStatus().name(), "回滚自模板版本 #" + source.getId());
        notifyTemplateChanged();
        return toItem(saved, impact(saved.getNotificationType(), saved.getTemplateId(), targetUserCount()));
    }

    @Transactional(readOnly = true)
    public Map<String, com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template> activeTemplateMap() {
        Map<String, com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template> map =
                new LinkedHashMap<>();
        templateRepository.findByStatusOrderByNotificationTypeAscUpdatedTimeDesc(
                        NotificationMiniProgramTemplate.TemplateStatus.ACTIVE)
                .forEach(template -> map.putIfAbsent(
                        normalizeType(template.getNotificationType()),
                        toRuntimeTemplate(template)));
        return map;
    }

    @Transactional
    public int backfillStoredChannelTemplates(Long operatorId) {
        return backfillStoredChannelTemplatesInternal(operatorId);
    }

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void backfillStoredChannelTemplatesOnReady() {
        backfillStoredChannelTemplatesInternal(0L);
    }

    private int backfillStoredChannelTemplatesInternal(Long operatorId) {
        Map<String, com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template> stored =
                runtimeConfigService.storedChannelTemplates();
        if (stored == null || stored.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (Map.Entry<String, com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template> entry
                : stored.entrySet()) {
            String notificationType = normalizeType(entry.getKey());
            com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template legacy = entry.getValue();
            if (!StringUtils.hasText(notificationType)
                    || legacy == null
                    || !StringUtils.hasText(legacy.getTemplateId())
                    || legacy.getFields() == null
                    || legacy.getFields().isEmpty()
                    || hasActiveTemplate(notificationType)) {
                continue;
            }
            try {
                validateNotificationType(notificationType);
                if (StringUtils.hasText(legacy.getPage())) {
                    runtimeConfigService.validateMiniProgramPageForTemplate(legacy.getPage(), "模板跳转页");
                }
                templateRepository.lockByNotificationType(notificationType);
                if (hasActiveTemplate(notificationType)) {
                    continue;
                }
                NotificationMiniProgramTemplate template = new NotificationMiniProgramTemplate();
                template.setNotificationType(notificationType);
                template.setTemplateId(normalize(legacy.getTemplateId()));
                template.setPage(normalizeNullable(legacy.getPage()));
                template.setFieldsJson(fieldsJson(legacy.getFields()));
                template.setStatus(NotificationMiniProgramTemplate.TemplateStatus.ACTIVE);
                template.setLastTestStatus("PASS");
                template.setLastTestMessage("由历史渠道配置自动迁移，需补充真实微信测试投递证据");
                template.setLastTestTime(LocalDateTime.now());
                template.setPublishedBy(operatorId);
                template.setPublishedTime(LocalDateTime.now());
                template.setCreatedBy(operatorId);
                template.setUpdatedBy(operatorId);
                NotificationMiniProgramTemplate saved = templateRepository.save(template);
                record(saved, NotificationMiniProgramTemplateHistory.TemplateAction.CREATE, operatorId, null,
                        saved.getStatus().name(), "从历史渠道配置迁移为生效模板");
                created++;
            } catch (IllegalArgumentException ignored) {
                // 跳过无法通过当前字典或字段校验的历史配置，避免阻断模板列表查询。
            }
        }
        if (created > 0) {
            notifyTemplateChanged();
        }
        return created;
    }

    private boolean hasActiveTemplate(String notificationType) {
        return templateRepository.findFirstByNotificationTypeAndStatus(
                notificationType,
                NotificationMiniProgramTemplate.TemplateStatus.ACTIVE).isPresent();
    }

    private NotificationMiniProgramTemplateDTO.Summary summary(List<NotificationMiniProgramTemplate> stored) {
        NotificationMiniProgramTemplateDTO.Summary summary = new NotificationMiniProgramTemplateDTO.Summary();
        summary.setConfiguredCount((int) stored.stream()
                .filter(item -> item.getStatus() == NotificationMiniProgramTemplate.TemplateStatus.ACTIVE)
                .count());
        summary.setPendingValidationCount((int) stored.stream()
                .filter(item -> item.getStatus() != NotificationMiniProgramTemplate.TemplateStatus.ACTIVE)
                .filter(item -> !"PASS".equals(item.getLastTestStatus()))
                .count());
        summary.setDraftCount((int) stored.stream()
                .filter(item -> item.getStatus() == NotificationMiniProgramTemplate.TemplateStatus.DRAFT)
                .count());
        summary.setActiveCount(summary.getConfiguredCount());
        return summary;
    }

    private Set<String> activeNotificationTypes(List<NotificationMiniProgramTemplate> stored) {
        Set<String> types = sysDictRepository.findByCategoryOrderBySortOrderAsc("notification_type").stream()
                .filter(dict -> dict.getStatus() == CommonStatus.ACTIVE)
                .map(SysDict::getDictKey)
                .filter(key -> NotificationService.TYPE_PRICE_PUBLISHED.equals(key)
                        || NotificationService.TYPE_SYSTEM_NOTICE.equals(key))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        stored.stream().map(NotificationMiniProgramTemplate::getNotificationType).forEach(types::add);
        return types;
    }

    private NotificationMiniProgramTemplateDTO.Item toItem(NotificationMiniProgramTemplate template, Impact impact) {
        NotificationMiniProgramTemplateDTO.Item item = new NotificationMiniProgramTemplateDTO.Item();
        item.setId(template.getId());
        item.setNotificationType(template.getNotificationType());
        item.setTemplateIdMasked(mask(template.getTemplateId(), 8));
        item.setPage(template.getPage());
        item.setFields(readFields(template.getFieldsJson()));
        item.setStatus(template.getStatus().name());
        item.setLastTestStatus(template.getLastTestStatus());
        item.setLastTestMessage(template.getLastTestMessage());
        item.setLastTestDeliveryId(template.getLastTestDeliveryId());
        item.setLastTestTime(template.getLastTestTime());
        item.setAuthorizedUsers(impact.authorizedUsers());
        item.setNeedReauthorizeUsers(impact.needReauthorizeUsers());
        item.setEstimatedReachableUsers(impact.estimatedReachableUsers());
        item.setPublishedBy(template.getPublishedBy());
        item.setPublishedTime(template.getPublishedTime());
        item.setUpdatedTime(template.getUpdatedTime());
        return item;
    }

    private Impact impact(String notificationType, String candidateTemplateId, long targetCount) {
        if (!StringUtils.hasText(candidateTemplateId)) {
            return new Impact(0, targetCount, 0);
        }
        long authorized = subscriptionRepository.countAuthorizedUsers(notificationType, candidateTemplateId);
        return new Impact(authorized, Math.max(targetCount - authorized, 0), authorized);
    }

    private long targetUserCount() {
        return userRepository.countActiveUsersWithWechatOpenid(CommonStatus.ACTIVE);
    }

    private String activeTemplateId(String notificationType) {
        return templateRepository.findFirstByNotificationTypeAndStatusOrderByPublishedTimeDescIdDesc(
                        notificationType,
                        NotificationMiniProgramTemplate.TemplateStatus.ACTIVE)
                .map(NotificationMiniProgramTemplate::getTemplateId)
                .orElse(null);
    }

    private NotificationMiniProgramTemplate requireTemplate(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("小程序模板不存在"));
    }

    private void validateRequest(NotificationMiniProgramTemplateRequest request, boolean requireAll) {
        if (request == null) {
            throw new IllegalArgumentException("模板请求不能为空");
        }
        if (requireAll || StringUtils.hasText(request.getNotificationType())) {
            validateNotificationType(normalizeType(request.getNotificationType()));
        }
        if (requireAll && !StringUtils.hasText(request.getTemplateId())) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        if (requireAll || request.getFields() != null) {
            validateFields(request.getFields());
        }
        if (StringUtils.hasText(request.getPage())) {
            runtimeConfigService.validateMiniProgramPageForTemplate(normalize(request.getPage()), "模板跳转页");
        }
    }

    private void validateTemplatePayload(NotificationMiniProgramTemplate template) {
        if (!StringUtils.hasText(template.getTemplateId())) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        validateFields(readFields(template.getFieldsJson()));
        if (StringUtils.hasText(template.getPage())) {
            runtimeConfigService.validateMiniProgramPageForTemplate(template.getPage(), "模板跳转页");
        }
    }

    private void validateNotificationType(String notificationType) {
        boolean activeType = sysDictRepository.findByCategoryAndDictKey("notification_type", notificationType)
                .map(dict -> dict.getStatus() == CommonStatus.ACTIVE)
                .orElse(false);
        if (!activeType) {
            throw new IllegalArgumentException("小程序模板通知类型不存在或未启用: " + notificationType);
        }
    }

    private void validateFields(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("小程序模板字段映射不能为空");
        }
        fields.forEach((semanticKey, fieldName) -> {
            if (!StringUtils.hasText(semanticKey) || !StringUtils.hasText(fieldName)) {
                throw new IllegalArgumentException("小程序模板字段映射不能包含空键或空值");
            }
            if (!WECHAT_FIELD_PATTERN.matcher(fieldName.trim()).matches()) {
                throw new IllegalArgumentException("微信字段编号格式不正确: " + fieldName);
            }
        });
    }

    private String fieldsJson(Map<String, String> fields) {
        validateFields(fields);
        try {
            return objectMapper.writeValueAsString(normalizedFields(fields));
        } catch (Exception ex) {
            throw new IllegalArgumentException("字段映射无法序列化");
        }
    }

    private Map<String, String> readFields(String fieldsJson) {
        if (!StringUtils.hasText(fieldsJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(fieldsJson, FIELD_MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, String> normalizedFields(Map<String, String> fields) {
        Map<String, String> normalized = new LinkedHashMap<>();
        fields.forEach((key, value) -> normalized.put(normalize(key), normalize(value)));
        return normalized;
    }

    private com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template toRuntimeTemplate(
            NotificationMiniProgramTemplate source) {
        com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template template =
                new com.pricemanagement.config.properties.NotificationMiniProgramProperties.Template();
        template.setTemplateId(source.getTemplateId());
        template.setPage(source.getPage());
        template.setFields(new LinkedHashMap<>(readFields(source.getFieldsJson())));
        return template;
    }

    private void record(
            NotificationMiniProgramTemplate template,
            NotificationMiniProgramTemplateHistory.TemplateAction action,
            Long operatorId,
            String statusBefore,
            String statusAfter,
            String message) {
        NotificationMiniProgramTemplateHistory history = new NotificationMiniProgramTemplateHistory();
        history.setTemplateIdRef(template.getId());
        history.setNotificationType(template.getNotificationType());
        history.setAction(action);
        history.setOperatorId(operatorId);
        history.setStatusBefore(statusBefore);
        history.setStatusAfter(statusAfter);
        history.setTemplateIdMasked(mask(template.getTemplateId(), 8));
        history.setMessage(message);
        historyRepository.save(history);
    }

    private void notifyTemplateChanged() {
        eventPublisher.publishEvent(new NotificationMiniProgramRuntimeConfigService.MiniProgramConfigChangedEvent());
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeType(String key) {
        return key == null ? "" : key.replace("-", "_").trim().toUpperCase();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String mask(String value, int tailLength) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= tailLength) {
            return "****" + trimmed;
        }
        return trimmed.substring(0, Math.min(4, trimmed.length())) + "****"
                + trimmed.substring(trimmed.length() - tailLength);
    }

    private record Impact(long authorizedUsers, long needReauthorizeUsers, long estimatedReachableUsers) {
    }
}
