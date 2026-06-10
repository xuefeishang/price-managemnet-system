package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, Long> {

    List<NotificationDeliveryLog> findByMessageIdOrderByIdAsc(Long messageId);

    @Query("""
            SELECT d
            FROM NotificationDeliveryLog d
            WHERE d.messageId = :messageId
              AND (:channel IS NULL OR :channel = '' OR d.channel = :channel)
              AND (:status IS NULL OR d.status = :status)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(d.provider) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(d.providerMessageId) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(d.errorCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(d.errorMessage) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<NotificationDeliveryLog> findAdminDeliveryLogs(
            @Param("messageId") Long messageId,
            @Param("channel") String channel,
            @Param("status") NotificationDeliveryLog.DeliveryStatus status,
            @Param("keyword") String keyword,
            Pageable pageable);

    List<NotificationDeliveryLog> findByRecipientIdOrderByIdAsc(Long recipientId);

    List<NotificationDeliveryLog> findByUserIdAndChannelOrderByCreatedTimeDesc(
            Long userId,
            String channel,
            org.springframework.data.domain.Pageable pageable);

    long countByCreatedTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    long countByStatusAndCreatedTimeBetween(NotificationDeliveryLog.DeliveryStatus status,
                                           LocalDateTime startTime,
                                           LocalDateTime endTime);

    long countByChannelAndStatus(String channel, NotificationDeliveryLog.DeliveryStatus status);

    long countByChannelAndStatusAndCreatedTimeAfter(String channel,
                                                    NotificationDeliveryLog.DeliveryStatus status,
                                                    LocalDateTime startTime);

    @Query("""
            SELECT d.channel, d.status, COUNT(d)
            FROM NotificationDeliveryLog d
            WHERE d.createdTime >= :startTime
            GROUP BY d.channel, d.status
            """)
    List<Object[]> countByChannelAndStatusSince(@Param("startTime") LocalDateTime startTime);

    Optional<NotificationDeliveryLog> findTopByChannelOrderByUpdatedTimeDesc(String channel);

    @Query("""
            SELECT d.status
            FROM NotificationDeliveryLog d
            WHERE d.channel = :channel
            ORDER BY d.updatedTime DESC, d.id DESC
            """)
    List<NotificationDeliveryLog.DeliveryStatus> findRecentStatusesByChannel(
            @Param("channel") String channel,
            org.springframework.data.domain.Pageable pageable);
}
