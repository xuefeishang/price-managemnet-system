package com.pricemanagement.service;

import com.pricemanagement.config.properties.NotificationWebhookProperties;
import com.pricemanagement.config.properties.NotificationMiniProgramProperties;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.NotificationProviderHealthDTO;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.NotificationOutboxRepository;
import com.pricemanagement.repository.SysDictRepository;
import com.pricemanagement.service.notification.NotificationChannelProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationObservabilityServiceTests {

    @Mock
    private NotificationMessageRepository messageRepository;
    @Mock
    private NotificationDeliveryLogRepository deliveryLogRepository;
    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private SysDictRepository sysDictRepository;
    @Mock
    private NotificationChannelProviderRegistry providerRegistry;
    @Mock
    private NotificationWebhookProperties webhookProperties;
    @Mock
    private NotificationMiniProgramProperties miniProgramProperties;
    @Mock
    private NotificationMiniProgramRuntimeConfigService miniProgramRuntimeConfigService;
    @Mock
    private NotificationThrottleService throttleService;

    @InjectMocks
    private NotificationObservabilityService observabilityService;

    @Test
    void providerHealthUsesRecentWindowCountsForStatus() {
        SysDict webhook = new SysDict();
        webhook.setCategory("notification_channel");
        webhook.setDictKey(NotificationService.CHANNEL_WEBHOOK);
        webhook.setStatus(CommonStatus.ACTIVE);
        when(sysDictRepository.findByCategoryAndStatusOrderBySortOrderAsc("notification_channel", CommonStatus.ACTIVE))
                .thenReturn(List.of(webhook));
        when(providerRegistry.hasProvider(NotificationService.CHANNEL_WEBHOOK)).thenReturn(true);
        when(webhookProperties.isEnabled()).thenReturn(true);
        when(webhookProperties.getUrl()).thenReturn("https://example.test/webhook");
        when(deliveryLogRepository.countByChannelAndStatusAndCreatedTimeAfter(
                eq(NotificationService.CHANNEL_WEBHOOK),
                eq(NotificationDeliveryLog.DeliveryStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(0L);
        when(deliveryLogRepository.countByChannelAndStatusAndCreatedTimeAfter(
                eq(NotificationService.CHANNEL_WEBHOOK),
                eq(NotificationDeliveryLog.DeliveryStatus.FAILED),
                any(LocalDateTime.class)))
                .thenReturn(0L);
        when(deliveryLogRepository.findRecentStatusesByChannel(eq(NotificationService.CHANNEL_WEBHOOK), any(Pageable.class)))
                .thenReturn(List.of(NotificationDeliveryLog.DeliveryStatus.SUCCESS));
        when(deliveryLogRepository.findTopByChannelOrderByUpdatedTimeDesc(NotificationService.CHANNEL_WEBHOOK))
                .thenReturn(Optional.empty());

        List<NotificationProviderHealthDTO> result = observabilityService.providerHealth();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getHealthStatus()).isEqualTo("OK");
        verify(deliveryLogRepository).countByChannelAndStatusAndCreatedTimeAfter(
                eq(NotificationService.CHANNEL_WEBHOOK),
                eq(NotificationDeliveryLog.DeliveryStatus.FAILED),
                any(LocalDateTime.class));
    }

    @Test
    void miniProgramHealthRequiresCredentialsAndTemplate() {
        SysDict miniProgram = new SysDict();
        miniProgram.setCategory("notification_channel");
        miniProgram.setDictKey(NotificationService.CHANNEL_MINI_PROGRAM);
        miniProgram.setStatus(CommonStatus.ACTIVE);
        when(sysDictRepository.findByCategoryAndStatusOrderBySortOrderAsc("notification_channel", CommonStatus.ACTIVE))
                .thenReturn(List.of(miniProgram));
        when(providerRegistry.hasProvider(NotificationService.CHANNEL_MINI_PROGRAM)).thenReturn(true);
        NotificationMiniProgramRuntimeConfigService.RuntimeConfig runtimeConfig =
                new NotificationMiniProgramRuntimeConfigService.RuntimeConfig();
        runtimeConfig.setEnabled(true);
        runtimeConfig.setAppId("test-app-id");
        runtimeConfig.setAppSecret("test-secret");
        when(miniProgramRuntimeConfigService.activeConfig()).thenReturn(runtimeConfig);
        when(deliveryLogRepository.countByChannelAndStatusAndCreatedTimeAfter(
                eq(NotificationService.CHANNEL_MINI_PROGRAM),
                eq(NotificationDeliveryLog.DeliveryStatus.PENDING),
                any(LocalDateTime.class)))
                .thenReturn(0L);
        when(deliveryLogRepository.countByChannelAndStatusAndCreatedTimeAfter(
                eq(NotificationService.CHANNEL_MINI_PROGRAM),
                eq(NotificationDeliveryLog.DeliveryStatus.FAILED),
                any(LocalDateTime.class)))
                .thenReturn(0L);
        when(deliveryLogRepository.findRecentStatusesByChannel(eq(NotificationService.CHANNEL_MINI_PROGRAM), any(Pageable.class)))
                .thenReturn(List.of());
        when(deliveryLogRepository.findTopByChannelOrderByUpdatedTimeDesc(NotificationService.CHANNEL_MINI_PROGRAM))
                .thenReturn(Optional.empty());

        List<NotificationProviderHealthDTO> result = observabilityService.providerHealth();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().isRegistered()).isTrue();
        assertThat(result.getFirst().isConfigured()).isFalse();
        assertThat(result.getFirst().getHealthStatus()).isEqualTo("NOT_CONFIGURED");
    }
}
