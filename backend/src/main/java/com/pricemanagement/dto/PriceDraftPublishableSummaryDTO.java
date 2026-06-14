package com.pricemanagement.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class PriceDraftPublishableSummaryDTO {
    private Boolean hasPublishableDrafts = false;
    private Integer publishableBatchCount = 0;
    private Integer publishableItemCount = 0;
    private Integer publishableDateCount = 0;
    private List<LocalDate> effectiveDates = new ArrayList<>();
    private List<Long> publishableBatchIds = new ArrayList<>();
}
