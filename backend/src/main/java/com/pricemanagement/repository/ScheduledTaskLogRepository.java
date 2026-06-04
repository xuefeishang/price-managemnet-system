package com.pricemanagement.repository;

import com.pricemanagement.entity.ScheduledTaskLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ScheduledTaskLogRepository extends JpaRepository<ScheduledTaskLog, Long> {

    Page<ScheduledTaskLog> findByTaskIdOrderByStartedTimeDesc(Long taskId, Pageable pageable);

    Optional<ScheduledTaskLog> findByTaskIdAndScheduledTimeAndTriggerType(
            Long taskId,
            LocalDateTime scheduledTime,
            ScheduledTaskLog.TriggerType triggerType);
}
