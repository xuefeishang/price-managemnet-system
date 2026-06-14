package com.pricemanagement.dto;

import com.pricemanagement.entity.PriceDraftBatch;
import com.pricemanagement.entity.PricePublishLog;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class PricePublishResultDTO {
    private Long batchId;
    private Long publishLogId;
    private String publishGroupId;
    private PricePublishLog.PublishStatus status;
    private PriceDraftBatch.DraftStatus batchStatus;
    private Integer successCount;
    private Integer failCount;
    private Integer attemptedBatchCount;
    private Integer publishedBatchCount;
    private Integer failedBatchCount;
    private Integer remainingDraftBatchCount;
    private Integer attemptedDateCount;
    private Integer publishedDateCount;
    private List<LocalDate> effectiveDates = new ArrayList<>();
    private List<Long> publishLogIds = new ArrayList<>();
    private List<BatchResult> batchResults = new ArrayList<>();
    private Long notificationMessageId;
    private String message;

    @Data
    public static class BatchResult {
        private LocalDate effectiveDate;
        private Long batchId;
        private Long publishLogId;
        private PricePublishLog.PublishStatus status;
        private PriceDraftBatch.DraftStatus batchStatus;
        private Integer successCount;
        private Integer failCount;
        private String message;

        public static BatchResult from(PricePublishResultDTO result) {
            BatchResult item = new BatchResult();
            item.setBatchId(result.getBatchId());
            item.setPublishLogId(result.getPublishLogId());
            item.setStatus(result.getStatus());
            item.setBatchStatus(result.getBatchStatus());
            item.setSuccessCount(result.getSuccessCount());
            item.setFailCount(result.getFailCount());
            item.setMessage(result.getMessage());
            return item;
        }
    }
}
