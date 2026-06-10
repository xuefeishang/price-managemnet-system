package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    Optional<NotificationOutbox> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);

    long countByStatus(NotificationOutbox.OutboxStatus status);

    @Query("""
            SELECT COALESCE(SUM(o.retryCount), 0)
            FROM NotificationOutbox o
            WHERE o.status IN (
                com.pricemanagement.entity.NotificationOutbox.OutboxStatus.PENDING,
                com.pricemanagement.entity.NotificationOutbox.OutboxStatus.PROCESSING,
                com.pricemanagement.entity.NotificationOutbox.OutboxStatus.FAILED
            )
            """)
    long sumActiveRetryCount();

    Optional<NotificationOutbox> findTopByStatusOrderByCreatedTimeAsc(NotificationOutbox.OutboxStatus status);

    @Query("""
            SELECT o.id
            FROM NotificationOutbox o
            WHERE (
                o.status = com.pricemanagement.entity.NotificationOutbox.OutboxStatus.PENDING
                AND (o.nextRetryTime IS NULL OR o.nextRetryTime <= :now)
            ) OR (
                o.status = com.pricemanagement.entity.NotificationOutbox.OutboxStatus.PROCESSING
                AND o.lockUntil < :now
            )
            ORDER BY o.id ASC
            """)
    List<Long> findClaimableIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            UPDATE NotificationOutbox o
            SET o.status = com.pricemanagement.entity.NotificationOutbox.OutboxStatus.PROCESSING,
                o.lockedBy = :workerId,
                o.lockUntil = :lockUntil
            WHERE o.id = :id
              AND (
                (
                    o.status = com.pricemanagement.entity.NotificationOutbox.OutboxStatus.PENDING
                    AND (o.nextRetryTime IS NULL OR o.nextRetryTime <= :now)
                ) OR (
                    o.status = com.pricemanagement.entity.NotificationOutbox.OutboxStatus.PROCESSING
                    AND o.lockUntil < :now
                )
              )
            """)
    int claim(@Param("id") Long id,
              @Param("workerId") String workerId,
              @Param("now") LocalDateTime now,
              @Param("lockUntil") LocalDateTime lockUntil);
}
