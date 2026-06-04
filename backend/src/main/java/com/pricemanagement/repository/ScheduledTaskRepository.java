package com.pricemanagement.repository;

import com.pricemanagement.entity.ScheduledTask;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduledTaskRepository extends JpaRepository<ScheduledTask, Long> {

    Optional<ScheduledTask> findByTaskCode(String taskCode);

    List<ScheduledTask> findByEnabledTrue();

    Page<ScheduledTask> findAllByOrderByUpdatedTimeDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ScheduledTask t WHERE t.id = :id")
    Optional<ScheduledTask> findByIdForUpdate(@Param("id") Long id);
}
