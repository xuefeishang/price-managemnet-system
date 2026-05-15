package com.pricemanagement.config;

import com.pricemanagement.config.properties.AlertProperties;
import com.pricemanagement.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;

/**
 * 健康检查告警监听器
 * 监控系统关键指标并发送告警
 */
@Component
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class HealthCheckAlertListener {

    private final AlertService alertService;
    private final AlertProperties alertProperties;

    /**
     * 应用启动完成通知
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!alertProperties.isEnabled()) {
            return;
        }

        alertService.sendAlert(
                "系统启动完成",
                "矿产品价格管理系统已成功启动，服务正常运行",
                AlertService.AlertLevel.INFO
        );
    }

    /**
     * 定期检查系统指标（每5分钟）
     */
    @Scheduled(fixedRate = 300000)
    public void checkSystemMetrics() {
        if (!alertProperties.isEnabled()) {
            return;
        }

        // 检查内存使用率
        checkMemoryUsage();

        // 检查 CPU 使用率
        checkCpuUsage();
    }

    /**
     * 检查内存使用率
     */
    private void checkMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double memoryUsagePercent = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;

        double threshold = alertProperties.getThreshold().getMemoryUsage();

        if (memoryUsagePercent > threshold) {
            String content = String.format(
                    "当前堆内存使用率: %.2f%%\n" +
                    "已使用: %d MB\n" +
                    "最大可用: %d MB\n" +
                    "阈值: %.1f%%",
                    memoryUsagePercent,
                    heapUsage.getUsed() / 1024 / 1024,
                    heapUsage.getMax() / 1024 / 1024,
                    threshold
            );

            alertService.sendAlert(
                    "内存使用率过高",
                    content,
                    AlertService.AlertLevel.WARNING
            );
        }
    }

    /**
     * 检查 CPU 使用率
     */
    private void checkCpuUsage() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

        // 获取系统 CPU 负载
        double systemCpuLoad = osBean.getSystemLoadAverage();

        // 如果无法获取 CPU 负载，跳过检查
        if (systemCpuLoad < 0) {
            return;
        }

        int availableProcessors = osBean.getAvailableProcessors();
        double cpuUsagePercent = (systemCpuLoad / availableProcessors) * 100;

        double threshold = alertProperties.getThreshold().getCpuUsage();

        if (cpuUsagePercent > threshold) {
            String content = String.format(
                    "当前 CPU 使用率: %.2f%%\n" +
                    "系统负载: %.2f\n" +
                    "可用处理器: %d\n" +
                    "阈值: %.1f%%",
                    cpuUsagePercent,
                    systemCpuLoad,
                    availableProcessors,
                    threshold
            );

            alertService.sendAlert(
                    "CPU 使用率过高",
                    content,
                    AlertService.AlertLevel.WARNING
            );
        }
    }
}
