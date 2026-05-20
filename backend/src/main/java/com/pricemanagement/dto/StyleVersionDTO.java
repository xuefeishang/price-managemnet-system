package com.pricemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 样式版本 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StyleVersionDTO {

    private Long id;
    private String versionNo;
    private String configSnapshot;
    private String changeSummary;
    private Long changedBy;
    private String changedByName;
    private LocalDateTime createdTime;

}
