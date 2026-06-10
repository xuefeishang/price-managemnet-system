package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationMiniProgramSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
