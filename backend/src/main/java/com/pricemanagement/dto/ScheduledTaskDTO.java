package com.pricemanagement.dto;

import com.pricemanagement.entity.ScheduledTask;
import com.pricemanagement.entity.ScheduledTaskLog;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ScheduledTaskDTO {
    private Long id;
    private Long version;
    private String taskCode;
    private String taskName;
    private String taskType;
    private String cronExpression;
    private String timezone;
    private Boolean enabled;
    private String configJson;
    private LocalDateTime lastRunTime;
    private LocalDateTime nextRunTime;
    private ScheduledTaskLog.RunStatus lastRunStatus;
    private Long createdBy;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public static ScheduledTaskDTO from(ScheduledTask task) {
        ScheduledTaskDTO dto = new ScheduledTaskDTO();
        dto.setId(task.getId());
        dto.setVersion(task.getVersion());
        dto.setTaskCode(task.getTaskCode());
        dto.setTaskName(task.getTaskName());
        dto.setTaskType(task.getTaskType());
        dto.setCronExpression(task.getCronExpression());
        dto.setTimezone(task.getTimezone());
        dto.setEnabled(task.getEnabled());
        dto.setConfigJson(task.getConfigJson());
        dto.setLastRunTime(task.getLastRunTime());
        dto.setNextRunTime(task.getNextRunTime());
        dto.setLastRunStatus(task.getLastRunStatus());
        dto.setCreatedBy(task.getCreatedBy());
        dto.setRemark(task.getRemark());
        dto.setCreatedTime(task.getCreatedTime());
        dto.setUpdatedTime(task.getUpdatedTime());
        return dto;
    }
}
