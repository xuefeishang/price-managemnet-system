package com.pricemanagement.service;

import com.pricemanagement.dto.PricePublishResultDTO;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private ProductAnnualBudgetService annualBudgetService;
    @Mock
    private NotificationEventService notificationEventService;

    private PricePublishService pricePublishService;
    private PriceDraftBatchPublishExecutor batchPublishExecutor;

    @BeforeEach
    void setUp() {
        batchPublishExecutor = new PriceDraftBatchPublishExecutor(
                batchRepository,
                itemRepository,
                publishLogRepository,
                priceService,
                annualBudgetService,
                notificationEventService
        );
        pricePublishService = new PricePublishService(
                batchRepository,
                itemRepository,
                notificationEventService,
                batchPublishExecutor
        );
    }

    @Test
    void publishBatchSkipsAlreadyPublishedItemsAndUsesBatchNotificationDedupe() {
        PriceDraftBatch batch = createBatch();
        PriceDraftItem publishedItem = createItem(1L, 101L, PriceDraftItem.ItemStatus.PUBLISHED, 9001L);
        PriceDraftItem draftItem = createItem(2L, 102L, PriceDraftItem.ItemStatus.DRAFT, null);
        Price savedPrice = new Price();
        savedPrice.setId(9002L);

        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdOrderByIdAsc(batch.getId())).thenReturn(List.of(publishedItem, draftItem));
        when(annualBudgetService.getBudgetPrice(draftItem.getProduct().getId(), draftItem.getEffectiveDate()))
                .thenReturn(Optional.of(new BigDecimal("88.88")));
        when(priceService.doSavePrice(eq(draftItem.getProduct()), any(Price.class), eq(null))).thenReturn(savedPrice);
        when(publishLogRepository.save(any(PricePublishLog.class))).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(6001L);
            return log;
        });
        PricePublishResultDTO result = pricePublishService.publishBatch(
                batch.getId(),
                PricePublishLog.PublishType.MANUAL,
                1L
        );

        ArgumentCaptor<Price> priceCaptor = ArgumentCaptor.forClass(Price.class);
        verify(priceService).doSavePrice(eq(draftItem.getProduct()), priceCaptor.capture(), eq(null));
        assertThat(priceCaptor.getValue().getBudgetPrice()).isEqualByComparingTo("88.88");
        assertThat(draftItem.getItemStatus()).isEqualTo(PriceDraftItem.ItemStatus.PUBLISHED);
        assertThat(draftItem.getPublishedPriceId()).isEqualTo(savedPrice.getId());
        assertThat(batch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getBatchStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        assertThat(result.getNotificationMessageId()).isNull();

        verify(notificationEventService).pricePublished(
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
        when(annualBudgetService.getBudgetPrice(draftItem.getProduct().getId(), draftItem.getEffectiveDate()))
                .thenReturn(Optional.empty());
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
        verify(notificationEventService, never()).pricePublished(
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
        ArgumentCaptor<PricePublishLog> logCaptor = ArgumentCaptor.forClass(PricePublishLog.class);

        when(batchRepository.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));
        when(itemRepository.findByBatchIdOrderByIdAsc(batch.getId())).thenReturn(List.of(publishedItem, draftItem));
        when(annualBudgetService.getBudgetPrice(draftItem.getProduct().getId(), draftItem.getEffectiveDate()))
                .thenReturn(Optional.empty());
        when(priceService.doSavePrice(eq(draftItem.getProduct()), any(Price.class), eq(null))).thenReturn(savedPrice);
        when(publishLogRepository.save(logCaptor.capture())).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(6003L);
            return log;
        });
        pricePublishService.publishBatch(batch.getId(), PricePublishLog.PublishType.MANUAL, 1L);

        PricePublishLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getTotalCount()).isEqualTo(1);
        assertThat(savedLog.getSuccessCount()).isEqualTo(1);
        assertThat(savedLog.getFailCount()).isZero();
    }

    @Test
    void publishByDatePublishesAllDraftBatchesForDate() {
        LocalDate effectiveDate = LocalDate.of(2026, 6, 3);
        PriceDraftBatch firstBatch = createBatch(10L);
        PriceDraftBatch secondBatch = createBatch(11L);
        PriceDraftItem firstItem = createItem(1L, 101L, PriceDraftItem.ItemStatus.DRAFT, null);
        PriceDraftItem secondItem = createItem(2L, 102L, PriceDraftItem.ItemStatus.DRAFT, null);
        secondItem.setBatchId(secondBatch.getId());
        Price firstSavedPrice = new Price();
        firstSavedPrice.setId(9001L);
        Price secondSavedPrice = new Price();
        secondSavedPrice.setId(9002L);

        when(batchRepository.findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
                List.of(PriceDraftBatch.DraftStatus.DRAFT)))
                .thenReturn(List.of(firstBatch, secondBatch));
        when(batchRepository.findByIdForUpdate(firstBatch.getId())).thenReturn(Optional.of(firstBatch));
        when(batchRepository.findByIdForUpdate(secondBatch.getId())).thenReturn(Optional.of(secondBatch));
        when(itemRepository.findByBatchIdOrderByIdAsc(firstBatch.getId())).thenReturn(List.of(firstItem));
        when(itemRepository.findByBatchIdOrderByIdAsc(secondBatch.getId())).thenReturn(List.of(secondItem));
        when(annualBudgetService.getBudgetPrice(any(), any())).thenReturn(Optional.empty());
        when(priceService.doSavePrice(eq(firstItem.getProduct()), any(Price.class), eq(null))).thenReturn(firstSavedPrice);
        when(priceService.doSavePrice(eq(secondItem.getProduct()), any(Price.class), eq(null))).thenReturn(secondSavedPrice);
        when(publishLogRepository.save(any(PricePublishLog.class))).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(log.getBatchId() + 6000L);
            return log;
        });

        PricePublishResultDTO result = pricePublishService.publishByDate(
                effectiveDate,
                PricePublishLog.PublishType.MANUAL,
                1L
        );

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getStatus()).isEqualTo(PricePublishLog.PublishStatus.SUCCESS);
        assertThat(firstBatch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        assertThat(secondBatch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        verify(priceService).doSavePrice(eq(firstItem.getProduct()), any(Price.class), eq(null));
        verify(priceService).doSavePrice(eq(secondItem.getProduct()), any(Price.class), eq(null));
        verify(notificationEventService).pricePublishedByDate(
                eq("价格已更新"),
                eq("2026-06-03 价格已发布，共更新 2 个产品，请查看最新价格。"),
                eq(6011L),
                eq(effectiveDate),
                eq(1L),
                eq(List.of(NotificationService.CHANNEL_IN_APP, NotificationService.CHANNEL_APP_PUSH, NotificationService.CHANNEL_MINI_PROGRAM)),
                eq(List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER))
        );
        verify(notificationEventService, never()).pricePublished(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void getPublishableSummaryCountsAllDraftBatchesAcrossDates() {
        PriceDraftBatch firstBatch = createBatch(10L);
        firstBatch.setSavedItemCount(2);
        PriceDraftBatch secondBatch = createBatch(11L);
        secondBatch.setEffectiveDate(LocalDate.of(2026, 6, 4));
        secondBatch.setSavedItemCount(3);

        when(batchRepository.findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
                List.of(PriceDraftBatch.DraftStatus.DRAFT)))
                .thenReturn(List.of(firstBatch, secondBatch));
        when(itemRepository.countItemsByBatchIds(List.of(10L, 11L)))
                .thenReturn(List.of(new Object[]{10L, 2L}, new Object[]{11L, 3L}));

        var summary = pricePublishService.getPublishableSummary();

        assertThat(summary.getHasPublishableDrafts()).isTrue();
        assertThat(summary.getPublishableBatchCount()).isEqualTo(2);
        assertThat(summary.getPublishableItemCount()).isEqualTo(5);
        assertThat(summary.getPublishableDateCount()).isEqualTo(2);
        assertThat(summary.getEffectiveDates()).containsExactly(
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 4)
        );
        assertThat(summary.getPublishableBatchIds()).containsExactly(10L, 11L);
    }

    @Test
    void publishAllDraftsPublishesDraftBatchesAcrossDatesAndSendsGroupNotification() {
        PriceDraftBatch firstBatch = createBatch(10L);
        PriceDraftBatch secondBatch = createBatch(11L);
        secondBatch.setEffectiveDate(LocalDate.of(2026, 6, 4));
        PriceDraftItem firstItem = createItem(1L, 101L, PriceDraftItem.ItemStatus.DRAFT, null);
        PriceDraftItem secondItem = createItem(2L, 102L, PriceDraftItem.ItemStatus.DRAFT, null);
        secondItem.setBatchId(secondBatch.getId());
        secondItem.setEffectiveDate(secondBatch.getEffectiveDate());
        Price firstSavedPrice = new Price();
        firstSavedPrice.setId(9001L);
        Price secondSavedPrice = new Price();
        secondSavedPrice.setId(9002L);

        when(batchRepository.findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
                List.of(PriceDraftBatch.DraftStatus.DRAFT)))
                .thenReturn(List.of(firstBatch, secondBatch));
        when(batchRepository.findByIdForUpdate(firstBatch.getId())).thenReturn(Optional.of(firstBatch));
        when(batchRepository.findByIdForUpdate(secondBatch.getId())).thenReturn(Optional.of(secondBatch));
        when(itemRepository.findByBatchIdOrderByIdAsc(firstBatch.getId())).thenReturn(List.of(firstItem));
        when(itemRepository.findByBatchIdOrderByIdAsc(secondBatch.getId())).thenReturn(List.of(secondItem));
        when(annualBudgetService.getBudgetPrice(any(), any())).thenReturn(Optional.empty());
        when(priceService.doSavePrice(eq(firstItem.getProduct()), any(Price.class), eq(null))).thenReturn(firstSavedPrice);
        when(priceService.doSavePrice(eq(secondItem.getProduct()), any(Price.class), eq(null))).thenReturn(secondSavedPrice);
        when(publishLogRepository.save(any(PricePublishLog.class))).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(log.getBatchId() + 6000L);
            return log;
        });

        PricePublishResultDTO result = pricePublishService.publishAllDrafts(
                PricePublishLog.PublishType.MANUAL,
                1L
        );

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getAttemptedBatchCount()).isEqualTo(2);
        assertThat(result.getPublishedBatchCount()).isEqualTo(2);
        assertThat(result.getFailedBatchCount()).isZero();
        assertThat(result.getRemainingDraftBatchCount()).isZero();
        assertThat(result.getAttemptedDateCount()).isEqualTo(2);
        assertThat(result.getPublishedDateCount()).isEqualTo(2);
        assertThat(result.getEffectiveDates()).containsExactly(
                LocalDate.of(2026, 6, 3),
                LocalDate.of(2026, 6, 4)
        );
        assertThat(result.getPublishLogIds()).containsExactly(6010L, 6011L);
        assertThat(result.getBatchResults()).hasSize(2);
        assertThat(firstBatch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        assertThat(secondBatch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        verify(notificationEventService).pricePublishedByGroup(
                eq("价格已更新"),
                eq("价格已发布，共发布 2 个日期、2 个草稿批次、2 条价格，请查看最新价格。"),
                eq(6011L),
                any(),
                eq(LocalDate.of(2026, 6, 4)),
                eq(List.of(LocalDate.of(2026, 6, 3), LocalDate.of(2026, 6, 4))),
                eq(1L),
                eq(List.of(NotificationService.CHANNEL_IN_APP, NotificationService.CHANNEL_APP_PUSH, NotificationService.CHANNEL_MINI_PROGRAM)),
                eq(List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER))
        );
        verify(notificationEventService, never()).pricePublishedByDate(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void publishAllDraftsReturnsPartialWhenOneBatchHasNoItems() {
        PriceDraftBatch firstBatch = createBatch(10L);
        PriceDraftBatch emptyBatch = createBatch(11L);
        emptyBatch.setEffectiveDate(LocalDate.of(2026, 6, 4));
        PriceDraftItem firstItem = createItem(1L, 101L, PriceDraftItem.ItemStatus.DRAFT, null);
        Price firstSavedPrice = new Price();
        firstSavedPrice.setId(9001L);

        when(batchRepository.findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
                List.of(PriceDraftBatch.DraftStatus.DRAFT)))
                .thenReturn(List.of(firstBatch, emptyBatch));
        when(batchRepository.findByIdForUpdate(firstBatch.getId())).thenReturn(Optional.of(firstBatch));
        when(batchRepository.findByIdForUpdate(emptyBatch.getId())).thenReturn(Optional.of(emptyBatch));
        when(itemRepository.findByBatchIdOrderByIdAsc(firstBatch.getId())).thenReturn(List.of(firstItem));
        when(itemRepository.findByBatchIdOrderByIdAsc(emptyBatch.getId())).thenReturn(List.of());
        when(annualBudgetService.getBudgetPrice(any(), any())).thenReturn(Optional.empty());
        when(priceService.doSavePrice(eq(firstItem.getProduct()), any(Price.class), eq(null))).thenReturn(firstSavedPrice);
        when(publishLogRepository.save(any(PricePublishLog.class))).thenAnswer(invocation -> {
            PricePublishLog log = invocation.getArgument(0);
            log.setId(log.getBatchId() + 6000L);
            return log;
        });

        PricePublishResultDTO result = pricePublishService.publishAllDrafts(
                PricePublishLog.PublishType.MANUAL,
                1L
        );

        assertThat(result.getStatus()).isEqualTo(PricePublishLog.PublishStatus.PARTIAL);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getPublishedBatchCount()).isEqualTo(1);
        assertThat(result.getFailedBatchCount()).isEqualTo(1);
        assertThat(result.getRemainingDraftBatchCount()).isEqualTo(1);
        assertThat(result.getEffectiveDates()).containsExactly(LocalDate.of(2026, 6, 3));
        assertThat(firstBatch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.PUBLISHED);
        assertThat(emptyBatch.getStatus()).isEqualTo(PriceDraftBatch.DraftStatus.DRAFT);
        assertThat(result.getBatchResults())
                .extracting(PricePublishResultDTO.BatchResult::getMessage)
                .anyMatch(message -> message.contains("草稿批次没有可发布明细"));
        verify(notificationEventService).pricePublishedByGroup(
                eq("价格已更新"),
                eq("价格已发布，共发布 1 个日期、1 个草稿批次、1 条价格，请查看最新价格。"),
                eq(6011L),
                any(),
                eq(LocalDate.of(2026, 6, 3)),
                eq(List.of(LocalDate.of(2026, 6, 3))),
                eq(1L),
                eq(List.of(NotificationService.CHANNEL_IN_APP, NotificationService.CHANNEL_APP_PUSH, NotificationService.CHANNEL_MINI_PROGRAM)),
                eq(List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER))
        );
    }

    private PriceDraftBatch createBatch() {
        return createBatch(10L);
    }

    private PriceDraftBatch createBatch(Long id) {
        PriceDraftBatch batch = new PriceDraftBatch();
        batch.setId(id);
        batch.setEffectiveDate(LocalDate.of(2026, 6, 3));
        batch.setStatus(PriceDraftBatch.DraftStatus.DRAFT);
        batch.setSavedItemCount(1);
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
