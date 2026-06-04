package com.pricemanagement.dto;

import com.pricemanagement.entity.PriceDraftBatch;
import com.pricemanagement.entity.PricePublishLog;
import lombok.Data;

@Data
public class PricePublishResultDTO {
    private Long batchId;
    private Long publishLogId;
    private PricePublishLog.PublishStatus status;
    private PriceDraftBatch.DraftStatus batchStatus;
    private Integer successCount;
    private Integer failCount;
    private Long notificationMessageId;
    private String message;
}
