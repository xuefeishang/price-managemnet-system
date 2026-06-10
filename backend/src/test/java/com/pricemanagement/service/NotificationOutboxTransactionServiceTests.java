package com.pricemanagement.service;

import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.NotificationOutbox;
import com.pricemanagement.entity.NotificationRecipient;
import com.pricemanagement.repository.NotificationDeliveryLogRepository;
import com.pricemanagement.repository.NotificationMessageRepository;
import com.pricemanagement.repository.NotificationOutboxRepository;
import com.pricemanagement.repository.NotificationRecipientRepository;
import com.pricemanagement.service.notification.DeliveryResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxTransactionServiceTests {

    @Mock
    private NotificationOutboxRepository outboxRepository;
    @Mock
    private NotificationDeliveryLogRepository deliveryLogRepository;
    @Mock
    private NotificationMessageRepository messageRepository;
    @Mock
    private NotificationRecipientRepository recipientRepository;

    @InjectMocks
    private NotificationOutboxTransactionService transactionService;

    @Test
    void loadClaimedReturnsWorkItemWhenOutboxDeliveryAndMessageExist() {
        NotificationOutbox outbox = processingOutbox();
        NotificationDeliveryLog delivery = pendingDelivery();
        NotificationMessage message = new NotificationMessage();
        message.setId(20L);
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(30L);

        when(outboxRepository.findById(1L)).thenReturn(Optional.of(outbox));
        when(deliveryLogRepository.findById(10L)).thenReturn(Optional.of(delivery));
        when(messageRepository.findById(20L)).thenReturn(Optional.of(message));
        when(recipientRepository.findById(30L)).thenReturn(Optional.of(recipient));

        Optional<NotificationOutboxTransactionService.DeliveryWorkItem> workItem = transactionService.loadClaimed(1L);

        assertThat(workItem).isPresent();
        assertThat(workItem.get().delivery().getId()).isEqualTo(10L);
        assertThat(workItem.get().message().getId()).isEqualTo(20L);
        assertThat(workItem.get().recipient().getId()).isEqualTo(30L);
    }

    @Test
    void applyFailedResultTrimsLongErrorAndSchedulesRetry() {
        ReflectionTestUtils.setField(transactionService, "maxRetries", 3);
        NotificationOutbox outbox = processingOutbox();
        outbox.setRetryCount(0);
        NotificationDeliveryLog delivery = pendingDelivery();
        delivery.setRetryCount(0);
        String longError = "x".repeat(600);

        when(outboxRepository.findById(1L)).thenReturn(Optional.of(outbox));
        when(deliveryLogRepository.findById(10L)).thenReturn(Optional.of(delivery));

        transactionService.applyResult(1L, 10L, DeliveryResult.failed("TIMEOUT", longError));

        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryLog.DeliveryStatus.FAILED);
        assertThat(delivery.getRetryCount()).isEqualTo(1);
        assertThat(delivery.getErrorMessage()).hasSize(500);
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.OutboxStatus.PENDING);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLastErrorMessage()).hasSize(500);
        assertThat(outbox.getNextRetryTime()).isNotNull();
        verify(deliveryLogRepository).save(delivery);
        verify(outboxRepository).save(outbox);
    }

    @Test
    void applySuccessResultMarksOutboxSuccessAndClearsErrors() {
        NotificationOutbox outbox = processingOutbox();
        outbox.setLastErrorCode("TIMEOUT");
        outbox.setLastErrorMessage("timeout");
        NotificationDeliveryLog delivery = pendingDelivery();
        delivery.setErrorCode("TIMEOUT");
        delivery.setErrorMessage("timeout");

        when(outboxRepository.findById(1L)).thenReturn(Optional.of(outbox));
        when(deliveryLogRepository.findById(10L)).thenReturn(Optional.of(delivery));

        transactionService.applyResult(1L, 10L, DeliveryResult.success("provider-message-id"));

        assertThat(delivery.getStatus()).isEqualTo(NotificationDeliveryLog.DeliveryStatus.SUCCESS);
        assertThat(delivery.getProviderMessageId()).isEqualTo("provider-message-id");
        assertThat(delivery.getErrorCode()).isNull();
        assertThat(outbox.getStatus()).isEqualTo(NotificationOutbox.OutboxStatus.SUCCESS);
        assertThat(outbox.getLastErrorCode()).isNull();
        verify(deliveryLogRepository).save(delivery);
        verify(outboxRepository).save(outbox);
    }

    private NotificationOutbox processingOutbox() {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.setId(1L);
        outbox.setAggregateId(10L);
        outbox.setStatus(NotificationOutbox.OutboxStatus.PROCESSING);
        return outbox;
    }

    private NotificationDeliveryLog pendingDelivery() {
        NotificationDeliveryLog delivery = new NotificationDeliveryLog();
        delivery.setId(10L);
        delivery.setMessageId(20L);
        delivery.setRecipientId(30L);
        delivery.setUserId(40L);
        delivery.setChannel(NotificationService.CHANNEL_APP_PUSH);
        delivery.setStatus(NotificationDeliveryLog.DeliveryStatus.PENDING);
        return delivery;
    }
}
