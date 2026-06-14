package com.pricemanagement.service;

import com.pricemanagement.dto.PriceDraftPublishableSummaryDTO;
import com.pricemanagement.dto.PricePublishResultDTO;
import com.pricemanagement.entity.*;
import com.pricemanagement.repository.PriceDraftBatchRepository;
import com.pricemanagement.repository.PriceDraftItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricePublishService {

    private final PriceDraftBatchRepository batchRepository;
    private final PriceDraftItemRepository itemRepository;
    private final NotificationEventService notificationEventService;
    private final PriceDraftBatchPublishExecutor batchPublishExecutor;

    @Transactional(readOnly = true)
    public PriceDraftPublishableSummaryDTO getPublishableSummary() {
        List<PriceDraftBatch> batches = batchRepository.findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
                List.of(PriceDraftBatch.DraftStatus.DRAFT));
        return buildPublishableSummary(batches);
    }

    @Transactional(readOnly = true)
    public PricePublishResultDTO publishAllDrafts(PricePublishLog.PublishType publishType, Long userId) {
        List<PriceDraftBatch> batches = batchRepository.findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
                List.of(PriceDraftBatch.DraftStatus.DRAFT));
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("暂无可发布草稿");
        }
        return publishBatches(batches, publishType, userId, true, null);
    }

    @Transactional(readOnly = true)
    public PricePublishResultDTO publishByDate(LocalDate effectiveDate, PricePublishLog.PublishType publishType, Long userId) {
        List<PriceDraftBatch> batches = batchRepository.findAllByStatusInOrderByEffectiveDateAscCreatedTimeAscIdAsc(
                        List.of(PriceDraftBatch.DraftStatus.DRAFT))
                .stream()
                .filter(batch -> effectiveDate.equals(batch.getEffectiveDate()))
                .toList();
        if (batches.isEmpty()) {
            throw new IllegalArgumentException("该日期暂无可发布草稿");
        }
        return publishBatches(batches, publishType, userId, false, effectiveDate);
    }

    private PriceDraftPublishableSummaryDTO buildPublishableSummary(List<PriceDraftBatch> batches) {
        PriceDraftPublishableSummaryDTO summary = new PriceDraftPublishableSummaryDTO();
        Set<LocalDate> dates = new LinkedHashSet<>();
        Map<Long, Long> itemCountByBatchId = countItemsByBatchId(batches);
        long itemCount = 0;
        for (PriceDraftBatch batch : batches) {
            summary.getPublishableBatchIds().add(batch.getId());
            if (batch.getEffectiveDate() != null) {
                dates.add(batch.getEffectiveDate());
            }
            itemCount += itemCountByBatchId.getOrDefault(batch.getId(), 0L);
        }
        summary.setHasPublishableDrafts(!batches.isEmpty());
        summary.setPublishableBatchCount(batches.size());
        summary.setPublishableItemCount(Math.toIntExact(itemCount));
        summary.setEffectiveDates(List.copyOf(dates));
        summary.setPublishableDateCount(dates.size());
        return summary;
    }

    private Map<Long, Long> countItemsByBatchId(List<PriceDraftBatch> batches) {
        if (batches.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> batchIds = batches.stream().map(PriceDraftBatch::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : itemRepository.countItemsByBatchIds(batchIds)) {
            Long batchId = (Long) row[0];
            Long count = (Long) row[1];
            counts.put(batchId, count);
        }
        return counts;
    }

    private PricePublishResultDTO publishBatches(
            List<PriceDraftBatch> batches,
            PricePublishLog.PublishType publishType,
            Long userId,
            boolean notifyAsGroup,
            LocalDate notificationDate) {
        PricePublishResultDTO result = new PricePublishResultDTO();
        result.setBatchId(batches.get(batches.size() - 1).getId());
        result.setStatus(PricePublishLog.PublishStatus.SUCCESS);
        result.setBatchStatus(PriceDraftBatch.DraftStatus.PUBLISHED);
        result.setSuccessCount(0);
        result.setFailCount(0);
        result.setAttemptedBatchCount(batches.size());
        result.setPublishedBatchCount(0);
        result.setFailedBatchCount(0);
        result.setRemainingDraftBatchCount(0);
        result.setPublishGroupId(UUID.randomUUID().toString());

        StringJoiner messages = new StringJoiner("; ");
        Long lastPublishLogId = null;
        Set<LocalDate> attemptedDates = new LinkedHashSet<>();
        Set<LocalDate> publishedDates = new LinkedHashSet<>();
        for (PriceDraftBatch batch : batches) {
            if (batch.getEffectiveDate() != null) {
                attemptedDates.add(batch.getEffectiveDate());
            }
            PricePublishResultDTO batchResult;
            try {
                batchResult = batchPublishExecutor.publishBatchInNewTransaction(batch.getId(), publishType, userId);
            } catch (RuntimeException ex) {
                batchResult = failedBatchResult(batch, ex.getMessage());
                log.error("Publish draft batch failed: batchId={}", batch.getId(), ex);
            }
            lastPublishLogId = batchResult.getPublishLogId();
            if (batchResult.getPublishLogId() != null) {
                result.getPublishLogIds().add(batchResult.getPublishLogId());
            }
            PricePublishResultDTO.BatchResult batchDetail = PricePublishResultDTO.BatchResult.from(batchResult);
            batchDetail.setEffectiveDate(batch.getEffectiveDate());
            result.getBatchResults().add(batchDetail);
            result.setSuccessCount(result.getSuccessCount() + batchResult.getSuccessCount());
            result.setFailCount(result.getFailCount() + batchResult.getFailCount());
            if (batchResult.getMessage() != null && !batchResult.getMessage().isBlank()) {
                messages.add("批次" + batch.getId() + ": " + batchResult.getMessage());
            }
            if (batchResult.getBatchStatus() != PriceDraftBatch.DraftStatus.PUBLISHED) {
                result.setBatchStatus(PriceDraftBatch.DraftStatus.DRAFT);
                result.setFailedBatchCount(result.getFailedBatchCount() + 1);
                if (batchResult.getBatchStatus() == PriceDraftBatch.DraftStatus.DRAFT) {
                    result.setRemainingDraftBatchCount(result.getRemainingDraftBatchCount() + 1);
                }
            } else {
                result.setPublishedBatchCount(result.getPublishedBatchCount() + 1);
                if (batch.getEffectiveDate() != null) {
                    publishedDates.add(batch.getEffectiveDate());
                }
            }
            if (batchResult.getStatus() == PricePublishLog.PublishStatus.FAILED) {
                result.setStatus(result.getStatus() == PricePublishLog.PublishStatus.SUCCESS
                        ? PricePublishLog.PublishStatus.PARTIAL
                        : result.getStatus());
            } else if (batchResult.getStatus() == PricePublishLog.PublishStatus.PARTIAL) {
                result.setStatus(PricePublishLog.PublishStatus.PARTIAL);
            }
        }

        if (result.getFailCount() > 0) {
            result.setStatus(result.getSuccessCount() > 0
                    ? PricePublishLog.PublishStatus.PARTIAL
                    : PricePublishLog.PublishStatus.FAILED);
        }
        result.setPublishLogId(lastPublishLogId);
        result.setAttemptedDateCount(attemptedDates.size());
        result.setPublishedDateCount(publishedDates.size());
        result.setEffectiveDates(List.copyOf(publishedDates));
        result.setStatus(resolveOverallStatus(result));
        result.setMessage(messages.length() == 0
                ? "发布成功"
                : "共发布 " + batches.size() + " 个草稿批次，" + messages);
        if (notifyAsGroup && result.getSuccessCount() > 0) {
            LocalDate groupNotificationDate = publishedDates.stream()
                    .max(LocalDate::compareTo)
                    .orElse(null);
            notificationEventService.pricePublishedByGroup(
                    "价格已更新",
                    "价格已发布，共发布 " + result.getPublishedDateCount() + " 个日期、"
                            + result.getPublishedBatchCount() + " 个草稿批次、"
                            + result.getSuccessCount() + " 条价格，请查看最新价格。",
                    lastPublishLogId,
                    result.getPublishGroupId(),
                    groupNotificationDate,
                    List.copyOf(publishedDates),
                    userId,
                    List.of(NotificationService.CHANNEL_IN_APP, NotificationService.CHANNEL_APP_PUSH, NotificationService.CHANNEL_MINI_PROGRAM),
                    List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER)
            );
        } else if (!notifyAsGroup && result.getSuccessCount() > 0 && result.getBatchStatus() == PriceDraftBatch.DraftStatus.PUBLISHED) {
            notificationEventService.pricePublishedByDate(
                    "价格已更新",
                    notificationDate + " 价格已发布，共更新 " + result.getSuccessCount() + " 个产品，请查看最新价格。",
                    lastPublishLogId,
                    notificationDate,
                    userId,
                    List.of(NotificationService.CHANNEL_IN_APP, NotificationService.CHANNEL_APP_PUSH, NotificationService.CHANNEL_MINI_PROGRAM),
                    List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER)
            );
        }
        return result;
    }

    private PricePublishLog.PublishStatus resolveOverallStatus(PricePublishResultDTO result) {
        if (result.getSuccessCount() != null && result.getSuccessCount() > 0
                && (result.getFailCount() == null || result.getFailCount() == 0)
                && (result.getRemainingDraftBatchCount() == null || result.getRemainingDraftBatchCount() == 0)) {
            return PricePublishLog.PublishStatus.SUCCESS;
        }
        if (result.getSuccessCount() != null && result.getSuccessCount() > 0) {
            return PricePublishLog.PublishStatus.PARTIAL;
        }
        return PricePublishLog.PublishStatus.FAILED;
    }

    private PricePublishResultDTO failedBatchResult(PriceDraftBatch batch, String message) {
        PricePublishResultDTO result = new PricePublishResultDTO();
        result.setBatchId(batch.getId());
        result.setStatus(PricePublishLog.PublishStatus.FAILED);
        result.setBatchStatus(batch.getStatus());
        result.setSuccessCount(0);
        result.setFailCount(resolveFailedItemCount(batch));
        result.setMessage(message == null || message.isBlank() ? "草稿批次发布失败" : message);
        return result;
    }

    private int resolveFailedItemCount(PriceDraftBatch batch) {
        try {
            long count = itemRepository.countByBatchId(batch.getId());
            return Math.toIntExact(count);
        } catch (RuntimeException ex) {
            Integer savedItemCount = batch.getSavedItemCount();
            return savedItemCount == null || savedItemCount < 0 ? 1 : savedItemCount;
        }
    }

    public PricePublishResultDTO publishBatch(Long batchId, PricePublishLog.PublishType publishType, Long userId) {
        return batchPublishExecutor.publishBatch(batchId, publishType, userId);
    }
}
