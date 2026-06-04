package com.pricemanagement.service;

import com.pricemanagement.dto.PricePublishResultDTO;
import com.pricemanagement.entity.NotificationMessage;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.PriceDraftBatch;
import com.pricemanagement.entity.PriceDraftItem;
import com.pricemanagement.entity.PricePublishLog;
import com.pricemanagement.entity.Product;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.PriceDraftBatchRepository;
import com.pricemanagement.repository.PriceDraftItemRepository;
import com.pricemanagement.repository.PricePublishLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricePublishServiceTests {

    @Mock
    private PriceDraftBatchRepository batchRepository;
    @Mock
    private PriceDraftItemRepository itemRepository;
    @Mock
    private PricePublishLogRepository publishLogRepository;
    @Mock
    private PriceService priceService;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PricePublishService pricePublishService;

    @Test
    void publishBatchSkipsAlreadyPublishedItemsAndUsesBatchNotificationDedupe() {
        PriceDraftBatch batch = createBatch();
        PriceDraftItem publishedItem = createItem(1L, 101L, PriceDraftItem.ItemStatus.PUBLISHED, 9001L);
        PriceDraftItem draftItem = createItem(2L, 102L, PriceDraftItem.ItemStatus.DRAFT, null);
        Price savedPrice = new Price();
        savedPrice.setId(9002L);
        NotificationMessage notification = new NotificationMessage();
        notification.setId(7001L);

        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdOrderByIdAsc(batch.getId())).thenReturn(List.of(publishedItem, draftItem));
        when(priceService.doSavePrice(eq(draftItem.getProduct()), any(Price.class), eq(null))).thenReturn(savedPrice);
        when(publishLogRepository.save(any(PricePublishLog.class))).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(6001L);
            return log;
        });
        when(notificationService.createPricePublishedNotification(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(notification);

        PricePublishResultDTO result = pricePublishService.publishBatch(
                batch.getId(),
                PricePublishLog.PublishType.MANUAL,
                1L
        );

        verify(priceService).doSavePrice(eq(draftItem.getProduct()), any(Price.class), eq(null));
        assertThat(draftItem.getItemStatus()).isEqualTo(PriceDraftItem.ItemStatus.PUBLISHED);
        assertThat(draftItem.getPublishedPriceId()).isEqualTo(savedPrice.getId());
        assertThat(batch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getBatchStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        assertThat(result.getNotificationMessageId()).isEqualTo(notification.getId());

        verify(notificationService).createPricePublishedNotification(
                eq("价格已更新"),
                eq("2026-06-03 价格已发布，共更新 2 个产品，请查看最新价格。"),
                eq(6001L),
                eq(batch.getEffectiveDate()),
                eq(batch.getId()),
                eq(1L),
                eq(List.of(NotificationService.CHANNEL_IN_APP, NotificationService.CHANNEL_APP_PUSH, NotificationService.CHANNEL_MINI_PROGRAM)),
                eq(List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER))
        );
    }

    @Test
    void publishBatchDoesNotNotifyWhenRetryStillFails() {
        PriceDraftBatch batch = createBatch();
        PriceDraftItem publishedItem = createItem(1L, 101L, PriceDraftItem.ItemStatus.PUBLISHED, 9001L);
        PriceDraftItem draftItem = createItem(2L, 102L, PriceDraftItem.ItemStatus.DRAFT, null);

        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdOrderByIdAsc(batch.getId())).thenReturn(List.of(publishedItem, draftItem));
        when(priceService.doSavePrice(eq(draftItem.getProduct()), any(Price.class), eq(null)))
                .thenThrow(new IllegalStateException("价格写入失败"));
        when(publishLogRepository.save(any(PricePublishLog.class))).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(6002L);
            return log;
        });

        PricePublishResultDTO result = pricePublishService.publishBatch(
                batch.getId(),
                PricePublishLog.PublishType.MANUAL,
                1L
        );

        assertThat(batch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.DRAFT);
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getNotificationMessageId()).isNull();
        verify(notificationService, never()).createPricePublishedNotification(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void publishBatchLogCountsOnlyItemsAttemptedInCurrentRun() {
        PriceDraftBatch batch = createBatch();
        PriceDraftItem publishedItem = createItem(1L, 101L, PriceDraftItem.ItemStatus.PUBLISHED, 9001L);
        PriceDraftItem draftItem = createItem(2L, 102L, PriceDraftItem.ItemStatus.DRAFT, null);
        Price savedPrice = new Price();
        savedPrice.setId(9002L);
        NotificationMessage notification = new NotificationMessage();
        notification.setId(7002L);
        ArgumentCaptor<PricePublishLog> logCaptor = ArgumentCaptor.forClass(PricePublishLog.class);

        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdOrderByIdAsc(batch.getId())).thenReturn(List.of(publishedItem, draftItem));
        when(priceService.doSavePrice(eq(draftItem.getProduct()), any(Price.class), eq(null))).thenReturn(savedPrice);
        when(publishLogRepository.save(logCaptor.capture())).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(6003L);
            return log;
        });
        when(notificationService.createPricePublishedNotification(
                any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(notification);

        pricePublishService.publishBatch(batch.getId(), PricePublishLog.PublishType.MANUAL, 1L);

        PricePublishLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getTotalCount()).isEqualTo(1);
        assertThat(savedLog.getSuccessCount()).isEqualTo(1);
        assertThat(savedLog.getFailCount()).isZero();
    }

    private PriceDraftBatch createBatch() {
        PriceDraftBatch batch = new PriceDraftBatch();
        batch.setId(10L);
        batch.setEffectiveDate(LocalDate.of(2026, 6, 3));
        batch.setStatus(PriceDraftBatch.DraftStatus.DRAFT);
        return batch;
    }

    private PriceDraftItem createItem(Long id, Long productId, PriceDraftItem.ItemStatus status, Long publishedPriceId) {
        Product product = new Product();
        product.setId(productId);
        product.setName("产品" + productId);

        PriceDraftItem item = new PriceDraftItem();
        item.setId(id);
        item.setBatchId(10L);
        item.setProduct(product);
        item.setCurrentPrice(BigDecimal.TEN);
        item.setEffectiveDate(LocalDate.of(2026, 6, 3));
        item.setItemStatus(status);
        item.setPublishedPriceId(publishedPriceId);
        return item;
    }
}
