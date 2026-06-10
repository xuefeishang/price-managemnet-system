package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationMiniProgramResolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationMiniProgramResolutionRepository
        extends JpaRepository<NotificationMiniProgramResolution, Long> {

    Optional<NotificationMiniProgramResolution> findByUserId(Long userId);

    List<NotificationMiniProgramResolution> findByUserIdIn(Collection<Long> userIds);

    @Query("""
            SELECT r.userId
            FROM NotificationMiniProgramResolution r
            WHERE r.userId IN :userIds
              AND r.resolveStatus = com.pricemanagement.entity.NotificationMiniProgramResolution.ResolveStatus.SNOOZED
              AND r.remindAfter > :now
            """)
    List<Long> findSnoozedUserIds(@Param("userIds") Collection<Long> userIds, @Param("now") LocalDateTime now);
}
