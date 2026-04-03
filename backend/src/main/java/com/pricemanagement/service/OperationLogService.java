package com.pricemanagement.service;

import com.pricemanagement.dto.LogStatistics;
import com.pricemanagement.entity.OperationLog;
import com.pricemanagement.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(OperationLog operationLog) {
        try {
            operationLogRepository.save(operationLog);
            log.debug("Operation log saved: {} - {}", operationLog.getOperationType(), operationLog.getOperationDesc());
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(OperationLog operationLog) {
        try {
            operationLogRepository.save(operationLog);
            log.debug("Operation log saved: {} - {}", operationLog.getOperationType(), operationLog.getOperationDesc());
        } catch (Exception e) {
            log.error("Failed to save operation log: {}", e.getMessage());
        }
    }

    public Page<OperationLog> getLogs(int page, int size, String username, String operationType,
                                       String operationModule, LocalDateTime startTime, LocalDateTime endTime) {
        Sort sort = Sort.by(Sort.Direction.DESC, "operationTime");
        PageRequest pageable = PageRequest.of(page, size, sort);

        return operationLogRepository.searchLogs(username, operationType, operationModule, startTime, endTime, pageable);
    }

    public Page<OperationLog> getLogsByUserId(Long userId, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "operationTime");
        PageRequest pageable = PageRequest.of(page, size, sort);
        return operationLogRepository.findByUserId(userId, pageable);
    }

    public Page<OperationLog> getLogsByType(String operationType, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "operationTime");
        PageRequest pageable = PageRequest.of(page, size, sort);
        return operationLogRepository.findByOperationType(operationType, pageable);
    }

    public Page<OperationLog> getLogsByModule(String operationModule, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "operationTime");
        PageRequest pageable = PageRequest.of(page, size, sort);
        return operationLogRepository.findByOperationModule(operationModule, pageable);
    }

    public Page<OperationLog> getLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "operationTime");
        PageRequest pageable = PageRequest.of(page, size, sort);
        return operationLogRepository.findByOperationTimeBetween(startTime, endTime, pageable);
    }

    public List<OperationLog> getRecentLogs(int limit) {
        return operationLogRepository.findTop10ByOrderByOperationTimeDesc();
    }

    /**
     * 获取日志统计信息
     */
    public LogStatistics getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        LogStatistics stats = new LogStatistics();

        // 基础统计
        stats.setTotalOperations(operationLogRepository.countTotalBetween(startTime, endTime));
        stats.setLoginCount(operationLogRepository.countLoginBetween(startTime, endTime));
        stats.setActiveUsers(operationLogRepository.countActiveUsersBetween(startTime, endTime));

        // 按操作类型统计
        List<Object[]> typeStats = operationLogRepository.countByOperationTypeBetween(startTime, endTime);
        Map<String, Long> operationTypeCount = new HashMap<>();
        for (Object[] row : typeStats) {
            operationTypeCount.put((String) row[0], (Long) row[1]);
        }
        stats.setOperationTypeCount(operationTypeCount);

        // 按模块统计
        List<Object[]> moduleStats = operationLogRepository.countByModuleBetween(startTime, endTime);
        Map<String, Long> moduleCount = new HashMap<>();
        for (Object[] row : moduleStats) {
            String module = row[0] != null ? (String) row[0] : "其他";
            moduleCount.put(module, (Long) row[1]);
        }
        stats.setModuleCount(moduleCount);

        // 按用户统计（活跃用户排行）
        List<Object[]> userStats = operationLogRepository.countByUserBetween(startTime, endTime);
        List<LogStatistics.UserActivity> userActivities = new ArrayList<>();
        for (Object[] row : userStats) {
            LogStatistics.UserActivity ua = new LogStatistics.UserActivity();
            ua.setUsername((String) row[0]);
            ua.setOperationCount((Long) row[1]);
            userActivities.add(ua);
        }
        stats.setUserActivities(userActivities);

        // 按日期统计（趋势图）
        List<Object[]> dateStats = operationLogRepository.countByDateBetween(startTime, endTime);
        Map<String, Long> dailyCount = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (Object[] row : dateStats) {
            if (row[0] instanceof java.sql.Date) {
                java.sql.Date sqlDate = (java.sql.Date) row[0];
                String dateStr = sqlDate.toLocalDate().format(formatter);
                dailyCount.put(dateStr, (Long) row[1]);
            } else if (row[0] instanceof LocalDate) {
                String dateStr = ((LocalDate) row[0]).format(formatter);
                dailyCount.put(dateStr, (Long) row[1]);
            }
        }
        stats.setDailyCount(dailyCount);

        return stats;
    }

    /**
     * 获取指定月份的日报表
     */
    public Map<String, Object> getMonthlyReport(int year, int month) {
        Map<String, Object> report = new HashMap<>();
        LocalDateTime startTime = LocalDateTime.of(year, month, 1, 0, 0, 0);
        LocalDateTime endTime = startTime.plusMonths(1).minusSeconds(1);

        report.put("year", year);
        report.put("month", month);
        report.put("statistics", getStatistics(startTime, endTime));

        // 按日统计
        List<Object[]> dailyStats = operationLogRepository.countByDateBetween(startTime, endTime);
        List<Map<String, Object>> dailyReports = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (Object[] row : dailyStats) {
            Map<String, Object> daily = new HashMap<>();
            if (row[0] instanceof java.sql.Date) {
                java.sql.Date sqlDate = (java.sql.Date) row[0];
                daily.put("date", sqlDate.toLocalDate().format(formatter));
            } else if (row[0] instanceof LocalDate) {
                daily.put("date", ((LocalDate) row[0]).format(formatter));
            }
            daily.put("count", row[1]);

            // 获取该日各操作类型的统计
            LocalDateTime dayStart = ((LocalDate) row[0]).atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1).minusSeconds(1);
            List<Object[]> typeStats = operationLogRepository.countByOperationTypeBetween(dayStart, dayEnd);
            Map<String, Long> typeCount = new HashMap<>();
            for (Object[] t : typeStats) {
                typeCount.put((String) t[0], (Long) t[1]);
            }
            daily.put("typeStats", typeCount);

            dailyReports.add(daily);
        }
        report.put("dailyReports", dailyReports);

        return report;
    }

    /**
     * 获取指定年份的年报表
     */
    public Map<String, Object> getYearlyReport(int year) {
        Map<String, Object> report = new HashMap<>();
        LocalDateTime startTime = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime endTime = startTime.plusYears(1).minusSeconds(1);

        report.put("year", year);
        report.put("statistics", getStatistics(startTime, endTime));

        // 按月统计
        Map<String, Long> monthlyCount = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            LocalDateTime monthStart = LocalDateTime.of(year, m, 1, 0, 0, 0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);
            long count = operationLogRepository.countTotalBetween(monthStart, monthEnd);
            monthlyCount.put(year + "-" + String.format("%02d", m), count);
        }
        report.put("monthlyCount", monthlyCount);

        // 年度用户排行
        List<Object[]> userStats = operationLogRepository.countByUserBetween(startTime, endTime);
        List<Map<String, Object>> userRankings = new ArrayList<>();
        int rank = 1;
        for (Object[] row : userStats) {
            Map<String, Object> user = new HashMap<>();
            user.put("rank", rank++);
            user.put("username", row[0]);
            user.put("count", row[1]);
            userRankings.add(user);
            if (rank > 10) break; // 只显示前10
        }
        report.put("userRankings", userRankings);

        return report;
    }
}
