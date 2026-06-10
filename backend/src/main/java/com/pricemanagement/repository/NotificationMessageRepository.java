package com.pricemanagement.repository;

import com.pricemanagement.dto.AdminNotificationSummaryDTO;
import com.pricemanagement.entity.NotificationDeliveryLog;
import com.pricemanagement.entity.NotificationMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, Long> {

    Optional<NotificationMessage> findByDedupeKey(String dedupeKey);

    long countByCreatedTimeBetween(LocalDateTime startTime, LocalDateTime endTime);

    @Query("""
            SELECT COALESCE(SUM(m.eventCount), 0)
            FROM NotificationMessage m
            WHERE m.type = :type
              AND m.createdTime > :startTime
            """)
    long sumEventCountByTypeAfter(@Param("type") String type,
                                  @Param("startTime") LocalDateTime startTime);

    @Query("""
            SELECT m.type, COUNT(m)
            FROM NotificationMessage m
            WHERE m.createdTime >= :startTime
            GROUP BY m.type
            ORDER BY COUNT(m) DESC
            """)
    List<Object[]> countByTypeSince(@Param("startTime") LocalDateTime startTime);

    @Query(value = """
            SELECT new com.pricemanagement.dto.AdminNotificationSummaryDTO(
                m.id,
                m.type,
                m.title,
                m.summary,
                m.content,
                m.businessType,
                m.businessId,
                m.channels,
                m.priority,
                m.linkType,
                m.linkParams,
                m.dedupeKey,
                m.expireTime,
                m.createdBy,
                m.createdTime,
                (SELECT COUNT(r) FROM NotificationRecipient r WHERE r.messageId = m.id),
                (SELECT COUNT(r) FROM NotificationRecipient r WHERE r.messageId = m.id AND r.readStatus = com.pricemanagement.entity.NotificationRecipient.ReadStatus.UNREAD),
                (SELECT COUNT(d) FROM NotificationDeliveryLog d WHERE d.messageId = m.id AND d.status = com.pricemanagement.entity.NotificationDeliveryLog.DeliveryStatus.FAILED)
            )
            FROM NotificationMessage m
            WHERE (:type IS NULL OR m.type = :type)
              AND (:priority IS NULL OR m.priority = :priority)
              AND (:businessType IS NULL OR m.businessType = :businessType)
              AND (:keyword IS NULL OR LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(m.summary) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:startTime IS NULL OR m.createdTime >= :startTime)
              AND (:endTime IS NULL OR m.createdTime <= :endTime)
              AND (:channel IS NULL OR m.channels LIKE CONCAT('%', :channel, '%'))
              AND (:deliveryStatus IS NULL OR EXISTS (
                    SELECT d.id FROM NotificationDeliveryLog d WHERE d.messageId = m.id AND d.status = :deliveryStatus
              ))
            """)
    Page<AdminNotificationSummaryDTO> findAdminSummaries(
            @Param("type") String type,
            @Param("priority") NotificationMessage.NotificationPriority priority,
            @Param("businessType") String businessType,
            @Param("channel") String channel,
            @Param("deliveryStatus") NotificationDeliveryLog.DeliveryStatus deliveryStatus,
            @Param("keyword") String keyword,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}
