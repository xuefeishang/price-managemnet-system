package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, Long> {

    Optional<NotificationMessage> findByDedupeKey(String dedupeKey);
}
