package com.pricemanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.dto.ScheduledTaskDTO;
import com.pricemanagement.entity.PricePublishLog;
import com.pricemanagement.entity.ScheduledTask;
import com.pricemanagement.entity.ScheduledTaskLog;
import com.pricemanagement.repository.ScheduledTaskLogRepository;
import com.pricemanagement.repository.ScheduledTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskService {

    public static final String TYPE_PRICE_PUBLISH = "PRICE_PUBLISH";

    private final ScheduledTaskRepository taskRepository;
    private final ScheduledTaskLogRepository logRepository;
    private final PricePublishService pricePublishService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Page<ScheduledTaskDTO> list(Pageable pageable) {
        return taskRepository.findAllByOrderByUpdatedTimeDesc(pageable).map(ScheduledTaskDTO::from);
    }

    @Transactional(readOnly = true)
    public ScheduledTaskDTO get(Long id) {
        return taskRepository.findById(id).map(ScheduledTaskDTO::from)
                .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));
    }

    @Transactional
    public ScheduledTaskDTO save(ScheduledTaskDTO dto, Long userId) {
        ScheduledTask task = dto.getId() == null ? new ScheduledTask()
                : taskRepository.findById(dto.getId()).orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));
        task.setTaskCode(dto.getTaskCode());
        task.setTaskName(dto.getTaskName());
        task.setTaskType(dto.getTaskType());
        task.setCronExpression(dto.getCronExpression());
        task.setTimezone(dto.getTimezone() == null || dto.getTimezone().isBlank() ? "Asia/Shanghai" : dto.getTimezone());
        task.setEnabled(Boolean.TRUE.equals(dto.getEnabled()));
        task.setConfigJson(dto.getConfigJson());
        task.setRemark(dto.getRemark());
        if (task.getCreatedBy() == null) {
            task.setCreatedBy(userId);
        }
        task.setNextRunTime(calculateNextRun(task, LocalDateTime.now()));
        return ScheduledTaskDTO.from(taskRepository.save(task));
    }

    @Transactional
    public ScheduledTaskDTO setEnabled(Long id, boolean enabled) {
        ScheduledTask task = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));
        task.setEnabled(enabled);
        task.setNextRunTime(enabled ? calculateNextRun(task, LocalDateTime.now()) : task.getNextRunTime());
        return ScheduledTaskDTO.from(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public Page<ScheduledTaskLog> getLogs(Long taskId, Pageable pageable) {
        return logRepository.findByTaskIdOrderByStartedTimeDesc(taskId, pageable);
    }

    @Transactional
    public ScheduledTaskLog runOnce(Long taskId, Long userId) {
        ScheduledTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));
        return executeTask(task, ScheduledTaskLog.TriggerType.MANUAL_RUN, LocalDateTime.now(), userId);
    }

    @Transactional
    public void scanAndRunDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<ScheduledTask> tasks = taskRepository.findByEnabledTrue();
        for (ScheduledTask task : tasks) {
            LocalDateTime nextRun = task.getNextRunTime();
            if (nextRun == null) {
                task.setNextRunTime(calculateNextRun(task, now.minusMinutes(1)));
                taskRepository.save(task);
                continue;
            }
            if (!nextRun.isAfter(now)) {
                runDueTask(task.getId(), nextRun);
            }
        }
    }

    @Transactional
    public void runDueTask(Long taskId, LocalDateTime scheduledTime) {
        ScheduledTask task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new IllegalArgumentException("定时任务不存在"));
        LocalDateTime now = LocalDateTime.now();
        if (task.getLockUntil() != null && task.getLockUntil().isAfter(now)) {
            return;
        }
        if (logRepository.findByTaskIdAndScheduledTimeAndTriggerType(taskId, scheduledTime, ScheduledTaskLog.TriggerType.SCHEDULED).isPresent()) {
            task.setNextRunTime(calculateNextRun(task, now));
            taskRepository.save(task);
            return;
        }
        task.setLockUntil(now.plusMinutes(5));
        task.setLockedBy(getInstanceId());
        taskRepository.saveAndFlush(task);
        executeTask(task, ScheduledTaskLog.TriggerType.SCHEDULED, scheduledTime, 0L);
    }

    private ScheduledTaskLog executeTask(ScheduledTask task, ScheduledTaskLog.TriggerType triggerType,
                                         LocalDateTime scheduledTime, Long userId) {
        ScheduledTaskLog runLog = new ScheduledTaskLog();
        runLog.setTaskId(task.getId());
        runLog.setTaskCode(task.getTaskCode());
        runLog.setTriggerType(triggerType);
        runLog.setScheduledTime(scheduledTime);
        runLog.setStartedTime(LocalDateTime.now());
        runLog.setStatus(ScheduledTaskLog.RunStatus.RUNNING);
        runLog = logRepository.save(runLog);

        try {
            if (!TYPE_PRICE_PUBLISH.equals(task.getTaskType())) {
                runLog.setStatus(ScheduledTaskLog.RunStatus.SKIPPED);
                runLog.setMessage("未实现的任务类型: " + task.getTaskType());
                return finish(task, runLog);
            }

            JsonNode config = task.getConfigJson() == null || task.getConfigJson().isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(task.getConfigJson());
            int dateOffsetDays = config.path("dateOffsetDays").asInt(-1);
            boolean skipIfNoDraft = config.path("skipIfNoDraft").asBoolean(true);
            LocalDate targetDate = LocalDate.now(ZoneId.of(task.getTimezone())).plusDays(dateOffsetDays);
            try {
                var result = pricePublishService.publishByDate(targetDate, PricePublishLog.PublishType.SCHEDULED, userId);
                runLog.setStatus(result.getFailCount() != null && result.getFailCount() > 0
                        ? ScheduledTaskLog.RunStatus.FAILED : ScheduledTaskLog.RunStatus.SUCCESS);
                runLog.setBusinessType("PRICE_PUBLISH");
                runLog.setBusinessId(result.getPublishLogId());
                runLog.setMessage(result.getMessage());
            } catch (IllegalArgumentException ex) {
                runLog.setStatus(skipIfNoDraft ? ScheduledTaskLog.RunStatus.SKIPPED : ScheduledTaskLog.RunStatus.FAILED);
                runLog.setMessage(ex.getMessage());
            }
        } catch (Exception ex) {
            runLog.setStatus(ScheduledTaskLog.RunStatus.FAILED);
            runLog.setErrorStack(ex.toString());
            runLog.setMessage("执行失败: " + ex.getMessage());
            log.error("Scheduled task failed: {}", task.getTaskCode(), ex);
        }

        return finish(task, runLog);
    }

    private ScheduledTaskLog finish(ScheduledTask task, ScheduledTaskLog runLog) {
        LocalDateTime finished = LocalDateTime.now();
        runLog.setFinishedTime(finished);
        runLog.setDurationMs(Duration.between(runLog.getStartedTime(), finished).toMillis());
        ScheduledTaskLog savedLog = logRepository.save(runLog);

        task.setLastRunTime(finished);
        task.setLastRunStatus(savedLog.getStatus());
        task.setLastScheduledTime(runLog.getScheduledTime());
        task.setNextRunTime(calculateNextRun(task, finished));
        task.setLockUntil(finished);
        task.setLockedBy(null);
        taskRepository.save(task);
        return savedLog;
    }

    private LocalDateTime calculateNextRun(ScheduledTask task, LocalDateTime from) {
        try {
            CronExpression cron = CronExpression.parse(task.getCronExpression());
            ZoneId zoneId = ZoneId.of(task.getTimezone() == null ? "Asia/Shanghai" : task.getTimezone());
            ZonedDateTime next = cron.next(from.atZone(zoneId));
            return next == null ? null : next.toLocalDateTime();
        } catch (Exception e) {
            log.warn("Invalid cron expression for task {}: {}", task.getTaskCode(), task.getCronExpression());
            return null;
        }
    }

    private String getInstanceId() {
        return System.getProperty("user.name", "app") + "@" + System.getenv().getOrDefault("HOSTNAME", "local");
    }
}
