package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findByUserIdOrderByNotificationTypeAscChannelAsc(Long userId);

    Optional<NotificationPreference> findByUserIdAndNotificationTypeAndChannel(
            Long userId,
            String notificationType,
            String channel);
}
