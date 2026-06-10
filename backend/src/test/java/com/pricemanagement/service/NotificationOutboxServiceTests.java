package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationOutbox;
import com.pricemanagement.entity.NotificationRecipient;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationOutboxRepository;
import com.pricemanagement.service.notification.DeliveryResult;
import com.pricemanagement.service.notification.NotificationChannelProvider;
import com.pricemanagement.service.notification.NotificationChannelProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTests {

    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private NotificationDeliveryLogRepository deliveryLogRepository;
    @Mock
    private NotificationOutboxTransactionService transactionService;
    @Mock
    private NotificationChannelProviderRegistry providerRegistry;
    @Mock
    private NotificationPreferenceService notificationPreferenceService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationOutboxService outboxService;

    @Test
    void processClaimedSendsSkippedResultWhenProviderIsNotConfigured() {
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(10L);
        delivery.setMessageId(20L);
        delivery.setRecipientId(30L);
        delivery.setUserId(40L);
        delivery.setChannel(NotificationService.CHANNEL_APP_PUSH);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);

        com.pricemanagement.entity.NotificationMessage message = new com.pricemanagement.entity.NotificationMessage();
        message.setId(20L);
        message.setTitle("价格已更新");
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);

        when(transactionService.loadClaimed(1L))
                .thenReturn(Optional.of(new NotificationOutboxTransactionService.DeliveryWorkItem(message, delivery, recipient)));
        when(notificationPreferenceService.resolveExternalDelivery(
                org.mockito.ArgumentMatchers.eq(40L),
                any(),
                org.mockito.ArgumentMatchers.eq(NotificationService.CHANNEL_APP_PUSH),
                any(),
                any()))
                .thenReturn(NotificationPreferenceService.DeliveryPreferenceDecision.deliverNow());
        when(providerRegistry.find(NotificationService.CHANNEL_APP_PUSH)).thenReturn(Optional.empty());

        outboxService.processClaimed(1L);

        ArgumentCaptor<DeliveryResult> resultCaptor = ArgumentCaptor.forClass(DeliveryResult.class);
        verify(transactionService).applyResult(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(10L), resultCaptor.capture());
        assertThat(resultCaptor.getValue().skipped()).isTrue();
        assertThat(resultCaptor.getValue().errorCode()).isEqualTo("PROVIDER_NOT_CONFIGURED");
    }

    @Test
    void enqueueDeliveryCreatesIdempotentOutboxByAggregate() {
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(10L);
        delivery.setMessageId(20L);
        delivery.setRecipientId(30L);
        delivery.setUserId(40L);
        delivery.setChannel(NotificationService.CHANNEL_APP_PUSH);
        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);

        when(outboxRepository.findByAggregateTypeAndAggregateId("NOTIFICATION_DELIVERY", 10L))
                .thenReturn(Optional.empty());
        when(outboxRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        outboxService.enqueueDelivery(delivery);

        NotificationOutbox saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("NOTIFICATION_DELIVERY_REQUESTED");
        assertThat(saved.getAggregateType()).isEqualTo("NOTIFICATION_DELIVERY");
        assertThat(saved.getAggregateId()).isEqualTo(10L);
        assertThat(saved.getStatus()).isEqualTo(NotificationOutbox.OutboxStatus.PENDING);
        assertThat(saved.getPayloadJson()).contains("\"channel\":\"APP_PUSH\"");
    }

    @Test
    void processClaimedCapturesProviderExceptionAsFailedResult() {
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(10L);
        delivery.setMessageId(20L);
        delivery.setRecipientId(30L);
        delivery.setUserId(40L);
        delivery.setChannel(NotificationService.CHANNEL_APP_PUSH);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);
        delivery.setRetryCount(0);

        com.pricemanagement.entity.NotificationMessage message = new com.pricemanagement.entity.NotificationMessage();
        message.setId(20L);
        message.setTitle("价格已更新");
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);

        NotificationChannelProvider provider = new NotificationChannelProvider() {
            @Override
            public String channel() {
                return NotificationService.CHANNEL_APP_PUSH;
            }

            @Override
            public com.pricemanagement.service.notification.DeliveryResult send(
                    com.pricemanagement.entity.NotificationMessage ignoredMessage,
                    NotificationDeliveryLog ignoredDelivery) {
                throw new IllegalStateException("provider timeout");
            }
        };

        when(transactionService.loadClaimed(1L))
                .thenReturn(Optional.of(new NotificationOutboxTransactionService.DeliveryWorkItem(message, delivery, recipient)));
        when(notificationPreferenceService.resolveExternalDelivery(
                org.mockito.ArgumentMatchers.eq(40L),
                any(),
                org.mockito.ArgumentMatchers.eq(NotificationService.CHANNEL_APP_PUSH),
                any(),
                any()))
                .thenReturn(NotificationPreferenceService.DeliveryPreferenceDecision.deliverNow());
        when(providerRegistry.find(NotificationService.CHANNEL_APP_PUSH)).thenReturn(Optional.of(provider));

        outboxService.processClaimed(1L);

        ArgumentCaptor<DeliveryResult> resultCaptor = ArgumentCaptor.forClass(DeliveryResult.class);
        verify(transactionService).applyResult(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(10L), resultCaptor.capture());
        assertThat(resultCaptor.getValue().success()).isFalse();
        assertThat(resultCaptor.getValue().skipped()).isFalse();
        assertThat(resultCaptor.getValue().errorCode()).isEqualTo("PROVIDER_EXCEPTION");
    }

    @Test
    void retryDeliveryRejectsSuccessfulDeliveryToAvoidDuplicateSend() {
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(10L);
        delivery.setChannel(NotificationService.CHANNEL_APP_PUSH);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.SUCCESS);

        when(deliveryLogRepository.findById(10L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> outboxService.retryDelivery(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成功投递不允许重试");
        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    void retryDeliveryRejectsInAppDelivery() {
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(10L);
        delivery.setChannel(NotificationService.CHANNEL_IN_APP);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.FAILED);

        when(deliveryLogRepository.findById(10L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> outboxService.retryDelivery(10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("站内通知投递不需要重试");
        verify(deliveryLogRepository, never()).save(any());
    }

    @Test
    void processClaimedSkipsArchivedRecipientBeforeProviderSend() {
        NotificationDeliveryLog delivery = delivery(10L);
        com.pricemanagement.entity.NotificationMessage message = new com.pricemanagement.entity.NotificationMessage();
        message.setId(20L);
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);
        recipient.setArchived(true);

        when(transactionService.loadClaimed(1L))
                .thenReturn(Optional.of(new NotificationOutboxTransactionService.DeliveryWorkItem(message, delivery, recipient)));

        outboxService.processClaimed(1L);

        verify(transactionService).applyResult(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.argThat(result ->
                        result.skipped() && "RECIPIENT_ARCHIVED".equals(result.errorCode())));
        verify(providerRegistry, never()).find(any());
    }

    @Test
    void processClaimedTestDeliveryBypassesArchivedRecipientAndPreferences() {
        NotificationDeliveryLog delivery = delivery(10L);
        delivery.setTest(true);
        com.pricemanagement.entity.NotificationMessage message = new com.pricemanagement.entity.NotificationMessage();
        message.setId(20L);
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);
        recipient.setArchived(true);
        NotificationChannelProvider provider = new NotificationChannelProvider() {
            @Override
            public String channel() {
                return NotificationService.CHANNEL_APP_PUSH;
            }

            @Override
            public DeliveryResult send(
                    com.pricemanagement.entity.NotificationMessage ignoredMessage,
                    NotificationDeliveryLog ignoredDelivery) {
                return DeliveryResult.success("provider-1");
            }
        };

        when(transactionService.loadClaimed(1L))
                .thenReturn(Optional.of(new NotificationOutboxTransactionService.DeliveryWorkItem(message, delivery, recipient)));
        when(providerRegistry.find(NotificationService.CHANNEL_APP_PUSH)).thenReturn(Optional.of(provider));

        outboxService.processClaimed(1L);

        verify(notificationPreferenceService, never()).resolveExternalDelivery(any(), any(), any(), any(), any());
        verify(transactionService).applyResult(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.argThat(DeliveryResult::success));
    }

    @Test
    void processClaimedSkipsExpiredMessageBeforeProviderSend() {
        NotificationDeliveryLog delivery = delivery(10L);
        com.pricemanagement.entity.NotificationMessage message = new com.pricemanagement.entity.NotificationMessage();
        message.setId(20L);
        message.setExpireTime(java.time.LocalDateTime.now().minusMinutes(1));
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);
        recipient.setArchived(false);

        when(transactionService.loadClaimed(1L))
                .thenReturn(Optional.of(new NotificationOutboxTransactionService.DeliveryWorkItem(message, delivery, recipient)));

        outboxService.processClaimed(1L);

        verify(transactionService).applyResult(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.argThat(result ->
                        result.skipped() && "MESSAGE_EXPIRED".equals(result.errorCode())));
        verify(providerRegistry, never()).find(any());
    }

    private NotificationDeliveryLog delivery(Long id) {
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(id);
        delivery.setMessageId(20L);
        delivery.setRecipientId(30L);
        delivery.setUserId(40L);
        delivery.setChannel(NotificationService.CHANNEL_APP_PUSH);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);
        return delivery;
    }
}
