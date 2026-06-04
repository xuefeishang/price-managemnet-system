package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.dto.ScheduledTaskDTO;
import com.pricemanagement.annotation.OperationLog;
import com.pricemanagement.entity.ScheduledTaskLog;
import com.pricemanagement.entity.OperationLog.OperationType;
import com.pricemanagement.service.ScheduledTaskService;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduled-tasks")
@RequiredArgsConstructor
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<ScheduledTaskDTO>> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.success("获取定时任务列表成功",
                scheduledTaskService.list(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ScheduledTaskDTO> get(@PathVariable Long id) {
        return Result.success("获取定时任务成功", scheduledTaskService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "定时任务", type = OperationType.CREATE, description = "创建定时任务")
    public Result<ScheduledTaskDTO> create(@RequestBody ScheduledTaskDTO dto) {
        return Result.success("创建定时任务成功", scheduledTaskService.save(dto, SecurityUtils.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "定时任务", type = OperationType.UPDATE, description = "更新定时任务")
    public Result<ScheduledTaskDTO> update(@PathVariable Long id, @RequestBody ScheduledTaskDTO dto) {
        dto.setId(id);
        return Result.success("更新定时任务成功", scheduledTaskService.save(dto, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "定时任务", type = OperationType.UPDATE, description = "启用定时任务")
    public Result<ScheduledTaskDTO> enable(@PathVariable Long id) {
        return Result.success("启用定时任务成功", scheduledTaskService.setEnabled(id, true));
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "定时任务", type = OperationType.UPDATE, description = "停用定时任务")
    public Result<ScheduledTaskDTO> disable(@PathVariable Long id) {
        return Result.success("停用定时任务成功", scheduledTaskService.setEnabled(id, false));
    }

    @PostMapping("/{id}/run-once")
    @PreAuthorize("hasRole('ADMIN')")
    @OperationLog(module = "定时任务", type = OperationType.UPDATE, description = "手动执行定时任务")
    public Result<ScheduledTaskLog> runOnce(@PathVariable Long id) {
        return Result.success("手动执行完成", scheduledTaskService.runOnce(id, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<ScheduledTaskLog>> logs(@PathVariable Long id,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return Result.success("获取执行日志成功",
                scheduledTaskService.getLogs(id, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))));
    }
}
