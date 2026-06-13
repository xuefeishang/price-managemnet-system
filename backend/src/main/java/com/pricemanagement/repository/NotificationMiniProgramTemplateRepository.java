package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationMiniProgramTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationMiniProgramTemplateRepository
        extends JpaRepository<NotificationMiniProgramTemplate, Long> {

    List<NotificationMiniProgramTemplate> findByNotificationTypeOrderByUpdatedTimeDescIdDesc(String notificationType);

    List<NotificationMiniProgramTemplate> findByStatusOrderByNotificationTypeAscUpdatedTimeDesc(
            NotificationMiniProgramTemplate.TemplateStatus status);

    List<NotificationMiniProgramTemplate> findByNotificationTypeIn(Collection<String> notificationTypes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM NotificationMiniProgramTemplate t
            WHERE t.notificationType = :notificationType
            ORDER BY t.id ASC
            """)
    List<NotificationMiniProgramTemplate> lockByNotificationType(@Param("notificationType") String notificationType);

    Optional<NotificationMiniProgramTemplate> findFirstByNotificationTypeAndStatus(
            String notificationType,
            NotificationMiniProgramTemplate.TemplateStatus status);

    Optional<NotificationMiniProgramTemplate> findFirstByNotificationTypeAndStatusOrderByPublishedTimeDescIdDesc(
            String notificationType,
            NotificationMiniProgramTemplate.TemplateStatus status);

    @Modifying
    @Query("""
            UPDATE NotificationMiniProgramTemplate t
            SET t.status = com.pricemanagement.entity.NotificationMiniProgramTemplate.TemplateStatus.DISABLED,
                t.updatedBy = :operatorId
            WHERE t.notificationType = :notificationType
              AND t.status = com.pricemanagement.entity.NotificationMiniProgramTemplate.TemplateStatus.ACTIVE
              AND t.id <> :exceptId
            """)
    int disableOtherActive(
            @Param("notificationType") String notificationType,
            @Param("exceptId") Long exceptId,
            @Param("operatorId") Long operatorId);

    @Modifying
    @Query("""
            UPDATE NotificationMiniProgramTemplate t
            SET t.status = com.pricemanagement.entity.NotificationMiniProgramTemplate.TemplateStatus.DISABLED,
                t.updatedBy = :operatorId
            WHERE t.notificationType = :notificationType
              AND t.status = com.pricemanagement.entity.NotificationMiniProgramTemplate.TemplateStatus.ACTIVE
            """)
    int disableActiveByNotificationType(
            @Param("notificationType") String notificationType,
            @Param("operatorId") Long operatorId);
}
