package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationMiniProgramSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationMiniProgramSubscriptionRepository
        extends JpaRepository<NotificationMiniProgramSubscription, Long> {

    List<NotificationMiniProgramSubscription> findByUserIdOrderByNotificationTypeAscTemplateIdAsc(Long userId);

    List<NotificationMiniProgramSubscription> findByUserIdIn(Collection<Long> userIds);

    Optional<NotificationMiniProgramSubscription> findByUserIdAndNotificationTypeAndTemplateId(
            Long userId,
            String notificationType,
            String templateId);

    @Query("""
            SELECT COUNT(DISTINCT s.userId)
            FROM NotificationMiniProgramSubscription s
            WHERE s.notificationType = :notificationType
              AND s.templateId = :templateId
              AND s.status = com.pricemanagement.entity.NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT
              AND s.availableCount > 0
            """)
    long countAuthorizedUsers(
            @Param("notificationType") String notificationType,
            @Param("templateId") String templateId);

    @Modifying
    @Query("""
            UPDATE NotificationMiniProgramSubscription s
            SET s.availableCount = s.availableCount - 1
            WHERE s.userId = :userId
              AND s.notificationType = :notificationType
              AND s.templateId = :templateId
              AND s.status = com.pricemanagement.entity.NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT
              AND s.availableCount > 0
            """)
    int consumeOne(
            @Param("userId") Long userId,
            @Param("notificationType") String notificationType,
            @Param("templateId") String templateId);

    @Modifying
    @Query("""
            UPDATE NotificationMiniProgramSubscription s
            SET s.availableCount = s.availableCount + 1
            WHERE s.userId = :userId
              AND s.notificationType = :notificationType
              AND s.templateId = :templateId
              AND s.status = com.pricemanagement.entity.NotificationMiniProgramSubscription.SubscriptionStatus.ACCEPT
            """)
    int releaseOne(
            @Param("userId") Long userId,
            @Param("notificationType") String notificationType,
            @Param("templateId") String templateId);

    @Modifying
    @Query("""
            UPDATE NotificationMiniProgramSubscription s
            SET s.status = com.pricemanagement.entity.NotificationMiniProgramSubscription.SubscriptionStatus.REJECT,
                s.availableCount = 0
            WHERE s.userId = :userId
              AND s.notificationType = :notificationType
              AND s.templateId = :templateId
            """)
    int markRejected(
            @Param("userId") Long userId,
            @Param("notificationType") String notificationType,
            @Param("templateId") String templateId);
}
