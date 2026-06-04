package com.pricemanagement.dto;

import com.pricemanagement.entity.PriceDraftBatch;
import com.pricemanagement.entity.PriceDraftItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PriceDraftDTO {
    private Long id;
    private Long version;
    private LocalDate effectiveDate;
    private PriceDraftBatch.DraftStatus status;
    private PriceDraftBatch.SourceType sourceType;
    private Integer itemCount;
    private Integer savedItemCount;
    private Long lastModifiedBy;
    private LocalDateTime publishedTime;
    private Long publishedBy;
    private Long createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private List<Item> items;

    public static PriceDraftDTO from(PriceDraftBatch batch, List<PriceDraftItem> items) {
        PriceDraftDTO dto = new PriceDraftDTO();
        dto.setId(batch.getId());
        dto.setVersion(batch.getVersion());
        dto.setEffectiveDate(batch.getEffectiveDate());
        dto.setStatus(batch.getStatus());
        dto.setSourceType(batch.getSourceType());
        dto.setItemCount(batch.getItemCount());
        dto.setSavedItemCount(batch.getSavedItemCount());
        dto.setLastModifiedBy(batch.getLastModifiedBy());
        dto.setPublishedTime(batch.getPublishedTime());
        dto.setPublishedBy(batch.getPublishedBy());
        dto.setCreatedBy(batch.getCreatedBy());
        dto.setCreatedTime(batch.getCreatedTime());
        dto.setUpdatedTime(batch.getUpdatedTime());
        dto.setItems(items == null ? List.of() : items.stream().map(Item::from).toList());
        return dto;
    }

    @Data
    public static class Item {
        private Long id;
        private Long batchId;
        private Long productId;
        private Long basePriceId;
        private Long basePriceVersion;
        private BigDecimal originalPrice;
        private BigDecimal currentPrice;
        private BigDecimal costPrice;
        private BigDecimal budgetPrice;
        private LocalDate effectiveDate;
        private LocalDate expiryDate;
        private String unit;
        private String priceSpec;
        private PriceDraftItem.ItemStatus itemStatus;
        private Long lastModifiedBy;
        private Long publishedPriceId;
        private LocalDateTime createdTime;
        private LocalDateTime updatedTime;

        public static Item from(PriceDraftItem item) {
            Item dto = new Item();
            dto.setId(item.getId());
            dto.setBatchId(item.getBatchId());
            dto.setProductId(item.getProduct() != null ? item.getProduct().getId() : null);
            dto.setBasePriceId(item.getBasePriceId());
            dto.setBasePriceVersion(item.getBasePriceVersion());
            dto.setOriginalPrice(item.getOriginalPrice());
            dto.setCurrentPrice(item.getCurrentPrice());
            dto.setCostPrice(item.getCostPrice());
            dto.setBudgetPrice(item.getBudgetPrice());
            dto.setEffectiveDate(item.getEffectiveDate());
            dto.setExpiryDate(item.getExpiryDate());
            dto.setUnit(item.getUnit());
            dto.setPriceSpec(item.getPriceSpec());
            dto.setItemStatus(item.getItemStatus());
            dto.setLastModifiedBy(item.getLastModifiedBy());
            dto.setPublishedPriceId(item.getPublishedPriceId());
            dto.setCreatedTime(item.getCreatedTime());
            dto.setUpdatedTime(item.getUpdatedTime());
            return dto;
        }
    }
}
