package com.pricemanagement.config;

import com.pricemanagement.service.ScheduledTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledTaskRunner {

    private final ScheduledTaskService scheduledTaskService;

    @Scheduled(fixedDelay = 60000)
    public void runDueTasks() {
        scheduledTaskService.scanAndRunDueTasks();
    }
}
