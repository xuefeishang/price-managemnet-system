package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationMiniProgramTemplateRequest;
import com.pricemanagement.entity.NotificationMiniProgramSubscription;
import com.pricemanagement.entity.NotificationMiniProgramTemplate;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.NotificationMiniProgramSubscriptionRepository;
import com.pricemanagement.repository.NotificationMiniProgramTemplateHistoryRepository;
import com.pricemanagement.repository.NotificationMiniProgramTemplateRepository;
import com.pricemanagement.repository.SysDictRepository;
import com.pricemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationMiniProgramTemplateServiceTests {

    @Mock
    private NotificationMiniProgramTemplateRepository templateRepository;
    @Mock
    private NotificationMiniProgramTemplateHistoryRepository historyRepository;
    @Mock
    private NotificationMiniProgramSubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SysDictRepository sysDictRepository;
    @Mock
    private NotificationMiniProgramRuntimeConfigService runtimeConfigService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationMiniProgramTemplateService service;

    @BeforeEach
    void setUp() {
        service = new NotificationMiniProgramTemplateService(
                templateRepository,
                historyRepository,
                subscriptionRepository,
                userRepository,
                sysDictRepository,
                new ObjectMapper(),
                runtimeConfigService,
                eventPublisher);
    }

    @Test
    void createValidatesFieldMapping() {
        stubType("PRICE_PUBLISHED");
        NotificationMiniProgramTemplateRequest request = new NotificationMiniProgramTemplateRequest();
        request.setNotificationType("PRICE_PUBLISHED");
        request.setTemplateId("tmpl");
        request.setFields(Map.of("title", "bad-field"));

        assertThatThrownBy(() -> service.create(request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("微信字段编号格式不正确");
    }

    @Test
    void publishDisablesOldActiveTemplateAndPublishesSelectedOne() {
        NotificationMiniProgramTemplate template = template(2L, "PRICE_PUBLISHED", "tmpl-new");
        template.setStatus(NotificationMiniProgramTemplate.TemplateStatus.TESTING);
        template.setLastTestStatus("PASS");
        when(templateRepository.findById(2L)).thenReturn(Optional.of(template));
        when(templateRepository.save(any(NotificationMiniProgramTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.countActiveUsersWithWechatOpenid(CommonStatus.ACTIVE)).thenReturn(0L);
        when(subscriptionRepository.countAuthorizedUsers("PRICE_PUBLISHED", "tmpl-new")).thenReturn(0L);

        var result = service.publish(2L, 99L);

        verify(templateRepository).lockByNotificationType("PRICE_PUBLISHED");
        verify(templateRepository).disableOtherActive("PRICE_PUBLISHED", 2L, 99L);
        verify(eventPublisher).publishEvent(any(NotificationMiniProgramRuntimeConfigService.MiniProgramConfigChangedEvent.class));
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void listShowsReauthorizationImpactForActiveTemplate() {
        NotificationMiniProgramTemplate template = template(1L, "SYSTEM_NOTICE", "tmpl-active");
        template.setStatus(NotificationMiniProgramTemplate.TemplateStatus.ACTIVE);
        when(templateRepository.findAll()).thenReturn(List.of(template));
        when(sysDictRepository.findByCategoryOrderBySortOrderAsc("notification_type")).thenReturn(List.of());
        when(templateRepository.findFirstByNotificationTypeAndStatusOrderByPublishedTimeDescIdDesc(
                eq("SYSTEM_NOTICE"),
                eq(NotificationMiniProgramTemplate.TemplateStatus.ACTIVE))).thenReturn(Optional.of(template));
        when(userRepository.countActiveUsersWithWechatOpenid(CommonStatus.ACTIVE)).thenReturn(1L);
        when(subscriptionRepository.countAuthorizedUsers("SYSTEM_NOTICE", "tmpl-active")).thenReturn(1L);

        var catalog = service.list();

        assertThat(catalog.getGroups()).hasSize(1);
        assertThat(catalog.getGroups().get(0).getAuthorizedUsers()).isEqualTo(1);
        assertThat(catalog.getGroups().get(0).getNeedReauthorizeUsers()).isZero();
    }

    @Test
    void rollbackRejectsCurrentActiveTemplate() {
        NotificationMiniProgramTemplate template = template(1L, "SYSTEM_NOTICE", "tmpl-active");
        template.setStatus(NotificationMiniProgramTemplate.TemplateStatus.ACTIVE);
        template.setLastTestStatus("PASS");
        when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> service.rollback(1L, 99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前生效模板无需回滚");
    }

    private NotificationMiniProgramTemplate template(Long id, String notificationType, String templateId) {
        NotificationMiniProgramTemplate template = new NotificationMiniProgramTemplate();
        template.setId(id);
        template.setNotificationType(notificationType);
        template.setTemplateId(templateId);
        template.setFieldsJson("{\"title\":\"thing1\"}");
        return template;
    }

    private void stubType(String key) {
        SysDict dict = new SysDict();
        dict.setCategory("notification_type");
        dict.setDictKey(key);
        dict.setStatus(CommonStatus.ACTIVE);
        when(sysDictRepository.findByCategoryAndDictKey("notification_type", key)).thenReturn(Optional.of(dict));
    }
}
