package com.pricemanagement.service;

import com.pricemanagement.dto.PricePublishResultDTO;
import com.pricemanagement.entity.*;
import com.pricemanagement.repository.PriceDraftBatchRepository;
import com.pricemanagement.repository.PriceDraftItemRepository;
import com.pricemanagement.repository.PricePublishLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PricePublishService {

    private final PriceDraftBatchRepository batchRepository;
    private final PriceDraftItemRepository itemRepository;
    private final PricePublishLogRepository publishLogRepository;
    private final PriceService priceService;
    private final NotificationService notificationService;

    @Transactional
    public PricePublishResultDTO publishByDate(LocalDate effectiveDate, PricePublishLog.PublishType publishType, Long userId) {
        PriceDraftBatch batch = batchRepository.findActiveByDateForUpdate(effectiveDate, List.of(PriceDraftBatch.DraftStatus.DRAFT))
                .orElseThrow(() -> new IllegalArgumentException("该日期暂无可发布草稿"));
        return publishBatch(batch.getId(), publishType, userId);
    }

    @Transactional
    public PricePublishResultDTO publishBatch(Long batchId, PricePublishLog.PublishType publishType, Long userId) {
        PriceDraftBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException("草稿批次不存在"));
        if (batch.getStatus() != PriceDraftBatch.DraftStatus.DRAFT) {
            throw new IllegalArgumentException("当前状态不允许发布");
        }
        List<PriceDraftItem> items = itemRepository.findByBatchIdOrderByIdAsc(batch.getId());
        if (items.isEmpty()) {
            throw new IllegalArgumentException("草稿批次没有可发布明细");
        }

        batch.setStatus(PriceDraftBatch.DraftStatus.PUBLISHING);
        batchRepository.saveAndFlush(batch);

        int successCount = 0;
        int failCount = 0;
        StringBuilder message = new StringBuilder();

        for (PriceDraftItem draftItem : items) {
            try {
                Price price = new Price();
                price.setOriginalPrice(draftItem.getOriginalPrice());
                price.setCurrentPrice(draftItem.getCurrentPrice());
                price.setCostPrice(draftItem.getCostPrice());
                price.setBudgetPrice(draftItem.getBudgetPrice());
                price.setEffectiveDate(draftItem.getEffectiveDate());
                price.setExpiryDate(draftItem.getExpiryDate());
                price.setUnit(draftItem.getUnit());
                price.setPriceSpec(draftItem.getPriceSpec());
                price.setCreatedBy(userId);
                price.setProduct(draftItem.getProduct());
                Price savedPrice = priceService.doSavePrice(draftItem.getProduct(), price, null);
                draftItem.setItemStatus(PriceDraftItem.ItemStatus.PUBLISHED);
                draftItem.setPublishedPriceId(savedPrice.getId());
                itemRepository.save(draftItem);
                successCount++;
            } catch (Exception ex) {
                failCount++;
                message.append("产品ID ").append(draftItem.getProduct().getId()).append(" 发布失败: ")
                        .append(ex.getMessage()).append("; ");
                log.error("Publish draft item failed: itemId={}", draftItem.getId(), ex);
            }
        }

        PricePublishLog logEntity = new PricePublishLog();
        logEntity.setBatchId(batch.getId());
        logEntity.setEffectiveDate(batch.getEffectiveDate());
        logEntity.setPublishType(publishType);
        logEntity.setTotalCount(items.size());
        logEntity.setSuccessCount(successCount);
        logEntity.setFailCount(failCount);
        logEntity.setStatus(failCount == 0 ? PricePublishLog.PublishStatus.SUCCESS
                : successCount == 0 ? PricePublishLog.PublishStatus.FAILED : PricePublishLog.PublishStatus.PARTIAL);
        logEntity.setMessage(message.isEmpty() ? "发布成功" : message.toString());
        logEntity.setCreatedBy(userId);
        PricePublishLog savedLog = publishLogRepository.save(logEntity);

        if (failCount == 0) {
            batch.setStatus(PriceDraftBatch.DraftStatus.PUBLISHED);
            batch.setPublishedBy(userId);
            batch.setPublishedTime(LocalDateTime.now());
        } else {
            batch.setStatus(PriceDraftBatch.DraftStatus.DRAFT);
        }
        batchRepository.save(batch);

        Long notificationMessageId = null;
        if (successCount > 0) {
            NotificationMessage notification = notificationService.createPricePublishedNotification(
                    "价格已更新",
                    batch.getEffectiveDate() + " 价格已发布，共更新 " + successCount + " 个产品，请查看最新价格。",
                    savedLog.getId(),
                    userId,
                    List.of(NotificationService.CHANNEL_IN_APP, NotificationService.CHANNEL_APP_PUSH, NotificationService.CHANNEL_MINI_PROGRAM),
                    List.of(User.Role.ADMIN, User.Role.EDITOR, User.Role.VIEWER)
            );
            notificationMessageId = notification.getId();
        }

        PricePublishResultDTO result = new PricePublishResultDTO();
        result.setBatchId(batch.getId());
        result.setPublishLogId(savedLog.getId());
        result.setStatus(savedLog.getStatus());
        result.setBatchStatus(batch.getStatus());
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setNotificationMessageId(notificationMessageId);
        result.setMessage(savedLog.getMessage());
        return result;
    }
}
