package com.pricemanagement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.dto.PriceDraftDTO;
import com.pricemanagement.dto.PriceDraftSaveRequest;
import com.pricemanagement.entity.Price;
import com.pricemanagement.entity.PriceDraftBatch;
import com.pricemanagement.entity.PriceDraftItem;
import com.pricemanagement.entity.Product;
import com.pricemanagement.repository.PriceDraftBatchRepository;
import com.pricemanagement.repository.PriceDraftItemRepository;
import com.pricemanagement.repository.PriceRepository;
import com.pricemanagement.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceDraftService {

    private static final List<PriceDraftBatch.DraftStatus> ACTIVE_STATUSES = List.of(
            PriceDraftBatch.DraftStatus.DRAFT,
            PriceDraftBatch.DraftStatus.PENDING_APPROVAL,
            PriceDraftBatch.DraftStatus.APPROVED,
            PriceDraftBatch.DraftStatus.PUBLISHING
    );

    private final PriceDraftBatchRepository batchRepository;
    private final PriceDraftItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final ProductAnnualBudgetService annualBudgetService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Optional<PriceDraftDTO> getActiveDraftByDate(LocalDate date) {
        return batchRepository.findFirstByEffectiveDateAndStatusInOrderByCreatedTimeDesc(date, ACTIVE_STATUSES)
                .map(batch -> PriceDraftDTO.from(batch, itemRepository.findByBatchIdOrderByIdAsc(batch.getId())));
    }

    @Transactional
    public PriceDraftDTO saveDraft(PriceDraftSaveRequest request, Long userId) {
        if (request.getEffectiveDate() == null) {
            throw new IllegalArgumentException("报价日期不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("保存明细不能为空");
        }

        PriceDraftBatch batch = resolveBatchForSave(request, userId);
        ensureEditable(batch);

        if (request.getBatchVersion() != null && batch.getVersion() != null
                && !request.getBatchVersion().equals(batch.getVersion())) {
            throw new IllegalStateException("草稿已被其他用户修改，请刷新后重试");
        }

        for (PriceDraftSaveRequest.Item itemRequest : request.getItems()) {
            if (itemRequest.getProductId() == null || itemRequest.getCurrentPrice() == null) {
                continue;
            }
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("产品不存在: " + itemRequest.getProductId()));
            Optional<Price> currentPublished = priceRepository.findValidPriceByProductIdAndDate(product.getId(), request.getEffectiveDate());
            if (itemRequest.getBasePriceVersion() != null
                    && currentPublished.isPresent()
                    && currentPublished.get().getVersion() != null
                    && !itemRequest.getBasePriceVersion().equals(currentPublished.get().getVersion())) {
                throw new IllegalStateException("产品 " + product.getName() + " 已被发布更新，请刷新后再编辑");
            }

            PriceDraftItem item = itemRepository.findByBatchIdAndProductId(batch.getId(), product.getId())
                    .orElseGet(PriceDraftItem::new);
            item.setBatchId(batch.getId());
            item.setProduct(product);
            item.setBasePriceId(itemRequest.getBasePriceId());
            item.setBasePriceVersion(itemRequest.getBasePriceVersion());
            item.setOriginalPrice(itemRequest.getOriginalPrice());
            item.setCurrentPrice(itemRequest.getCurrentPrice());
            item.setCostPrice(itemRequest.getCostPrice());
            item.setBudgetPrice(annualBudgetService.getBudgetPrice(product.getId(), request.getEffectiveDate()).orElse(null));
            item.setEffectiveDate(request.getEffectiveDate());
            item.setExpiryDate(itemRequest.getExpiryDate());
            item.setUnit(itemRequest.getUnit() != null ? itemRequest.getUnit() : product.getUnit());
            item.setPriceSpec(itemRequest.getPriceSpec());
            item.setItemStatus(PriceDraftItem.ItemStatus.DRAFT);
            item.setLastModifiedBy(userId);
            itemRepository.save(item);
        }

        batch.setSavedItemCount((int) itemRepository.countByBatchId(batch.getId()));
        batch.setLastModifiedBy(userId);
        PriceDraftBatch savedBatch = batchRepository.save(batch);
        return PriceDraftDTO.from(savedBatch, itemRepository.findByBatchIdOrderByIdAsc(savedBatch.getId()));
    }

    @Transactional
    public void cancelDraft(Long batchId) {
        PriceDraftBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new IllegalArgumentException("草稿批次不存在"));
        if (batch.getStatus() != PriceDraftBatch.DraftStatus.DRAFT) {
            throw new IllegalArgumentException("当前状态不允许取消");
        }
        batch.setStatus(PriceDraftBatch.DraftStatus.CANCELLED);
        batchRepository.save(batch);
    }

    private PriceDraftBatch resolveBatchForSave(PriceDraftSaveRequest request, Long userId) {
        if (request.getBatchId() != null) {
            return batchRepository.findByIdForUpdate(request.getBatchId())
                    .orElseThrow(() -> new IllegalArgumentException("草稿批次不存在"));
        }
        return batchRepository.findActiveByDateForUpdate(request.getEffectiveDate(), ACTIVE_STATUSES)
                .orElseGet(() -> createBatch(request.getEffectiveDate(), userId));
    }

    private PriceDraftBatch createBatch(LocalDate effectiveDate, Long userId) {
        List<Product> activeProducts = productRepository.findByStatus(CommonStatus.ACTIVE);
        PriceDraftBatch batch = new PriceDraftBatch();
        batch.setEffectiveDate(effectiveDate);
        batch.setStatus(PriceDraftBatch.DraftStatus.DRAFT);
        batch.setSourceType(PriceDraftBatch.SourceType.MANUAL);
        batch.setItemCount(activeProducts.size());
        batch.setSavedItemCount(0);
        batch.setCreatedBy(userId);
        batch.setLastModifiedBy(userId);
        try {
            batch.setProductScopeSnapshot(objectMapper.writeValueAsString(
                    activeProducts.stream().map(Product::getId).toList()));
        } catch (JsonProcessingException e) {
            batch.setProductScopeSnapshot("[]");
        }
        return batchRepository.save(batch);
    }

    private void ensureEditable(PriceDraftBatch batch) {
        if (batch.getStatus() != PriceDraftBatch.DraftStatus.DRAFT) {
            throw new IllegalArgumentException("当前草稿状态不允许保存");
        }
    }
}
