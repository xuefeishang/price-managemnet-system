package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.config.properties.ApiKeyProperties;
import com.pricemanagement.config.properties.NotificationMiniProgramProperties;
import com.pricemanagement.dto.NotificationChannelConfigUpdateRequest;
import com.pricemanagement.entity.NotificationChannelConfig;
import com.pricemanagement.entity.NotificationMiniProgramTemplate;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.NotificationChannelConfigRepository;
import com.pricemanagement.repository.NotificationMiniProgramTemplateRepository;
import com.pricemanagement.repository.SysDictRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMiniProgramRuntimeConfigServiceTests {

    @Mock
    private NotificationChannelConfigRepository configRepository;
    @Mock
    private ApiKeySecretService secretService;
    @Mock
    private ApiKeyProperties apiKeyProperties;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SysDictRepository sysDictRepository;
    @Mock
    private NotificationMiniProgramTemplateRepository templateRepository;

    private NotificationMiniProgramRuntimeConfigService service;
    private NotificationMiniProgramProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        properties = new NotificationMiniProgramProperties();
        properties.setEnabled(true);
        properties.setAppId("env-appid");
        properties.setAppSecret("env-secret");
        NotificationMiniProgramProperties.Template template = new NotificationMiniProgramProperties.Template();
        template.setTemplateId("env-template");
        template.setPage("pages/notifications/index");
        template.getFields().put("title", "thing1");
        properties.setTemplates(new LinkedHashMap<>(java.util.Map.of("PRICE_PUBLISHED", template)));

        service = new NotificationMiniProgramRuntimeConfigService(
                properties,
                configRepository,
                secretService,
                apiKeyProperties,
                objectMapper,
                eventPublisher,
                sysDictRepository,
                templateRepository);
        when(templateRepository.findByStatusOrderByNotificationTypeAscUpdatedTimeDesc(any()))
                .thenReturn(List.of());
    }

    @Test
    void activeDatabaseConfigDoesNotFallbackToEnvironmentBusinessValues() {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setChannel(NotificationService.CHANNEL_MINI_PROGRAM);
        config.setEnabled(true);
        config.setConfigJson("{\"templates\":{}}");
        when(configRepository.findByChannel(NotificationService.CHANNEL_MINI_PROGRAM))
                .thenReturn(Optional.of(config));

        var active = service.activeConfig();

        assertThat(active.getAppId()).isNull();
        assertThat(active.isSecretConfigured()).isFalse();
        assertThat(active.getDefaultPage()).isNull();
        assertThat(active.configuredTemplates()).isEmpty();
    }

    @Test
    void savingEmptyTemplateListDeletesStoredTemplates() {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setChannel(NotificationService.CHANNEL_MINI_PROGRAM);
        config.setEnabled(true);
        config.setAppId("db-appid");
        config.setDefaultPage("pages/notifications/index");
        config.setConfigJson("""
                {"templates":{"PRICE_PUBLISHED":{"templateId":"old-template","page":"pages/notifications/index","fields":{"title":"thing1"}}}}
                """);
        when(configRepository.findByChannel(NotificationService.CHANNEL_MINI_PROGRAM))
                .thenReturn(Optional.of(config));
        when(configRepository.save(any(NotificationChannelConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationChannelConfigUpdateRequest request = new NotificationChannelConfigUpdateRequest();
        request.setTemplates(List.of());

        var view = service.saveConfig(request, 1L);

        assertThat(view.getTemplates()).isEmpty();
        assertThat(service.activeConfig().configuredTemplates()).isEmpty();
    }

    @Test
    void clearTemplateIdKeepsRowButRemovesDeliverableTemplate() {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setChannel(NotificationService.CHANNEL_MINI_PROGRAM);
        config.setEnabled(true);
        config.setConfigJson("""
                {"templates":{"PRICE_PUBLISHED":{"templateId":"old-template","page":"pages/notifications/index","fields":{"title":"thing1"}}}}
                """);
        when(configRepository.findByChannel(NotificationService.CHANNEL_MINI_PROGRAM))
                .thenReturn(Optional.of(config));
        when(configRepository.save(any(NotificationChannelConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubActiveDict("notification_type", "PRICE_PUBLISHED");

        NotificationChannelConfigUpdateRequest.TemplateMappingRequest template =
                new NotificationChannelConfigUpdateRequest.TemplateMappingRequest();
        template.setNotificationType("PRICE_PUBLISHED");
        template.setClearTemplateId(true);
        template.setFields(java.util.Map.of("title", "thing1"));
        NotificationChannelConfigUpdateRequest request = new NotificationChannelConfigUpdateRequest();
        request.setTemplates(List.of(template));

        var view = service.saveConfig(request, 1L);

        assertThat(view.getTemplates()).isEmpty();
        assertThat(service.activeConfig().resolveTemplate("PRICE_PUBLISHED")).isEmpty();
    }

    @Test
    void latestActiveTemplateOverridesStoredChannelFallback() {
        NotificationChannelConfig config = new NotificationChannelConfig();
        config.setChannel(NotificationService.CHANNEL_MINI_PROGRAM);
        config.setEnabled(true);
        config.setConfigJson("""
                {"templates":{"PRICE_PUBLISHED":{"templateId":"legacy-template","fields":{"title":"thing1"}}}}
                """);
        NotificationMiniProgramTemplate latest = new NotificationMiniProgramTemplate();
        latest.setNotificationType("PRICE_PUBLISHED");
        latest.setTemplateId("latest-template");
        latest.setFieldsJson("{\"title\":\"thing2\"}");
        NotificationMiniProgramTemplate older = new NotificationMiniProgramTemplate();
        older.setNotificationType("PRICE_PUBLISHED");
        older.setTemplateId("older-template");
        older.setFieldsJson("{\"title\":\"thing3\"}");
        when(configRepository.findByChannel(NotificationService.CHANNEL_MINI_PROGRAM))
                .thenReturn(Optional.of(config));
        when(templateRepository.findByStatusOrderByNotificationTypeAscUpdatedTimeDesc(any()))
                .thenReturn(List.of(latest, older));

        var active = service.activeConfig().resolveTemplate("PRICE_PUBLISHED").orElseThrow();

        assertThat(active.getTemplateId()).isEqualTo("latest-template");
        assertThat(active.getFields()).containsEntry("title", "thing2");
    }

    private void stubActiveDict(String category, String key) {
        SysDict dict = new SysDict();
        dict.setCategory(category);
        dict.setDictKey(key);
        dict.setStatus(com.pricemanagement.constants.CommonStatus.ACTIVE);
        when(sysDictRepository.findByCategoryAndDictKey(category, key)).thenReturn(Optional.of(dict));
    }
}
