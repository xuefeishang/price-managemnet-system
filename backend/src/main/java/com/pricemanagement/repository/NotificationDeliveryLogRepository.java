package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, Long> {

    List<NotificationDeliveryLog> findByMessageIdOrderByIdAsc(Long messageId);

    List<NotificationDeliveryLog> findByRecipientIdOrderByIdAsc(Long recipientId);
}
