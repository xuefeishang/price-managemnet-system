package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    long countByUserIdAndReadStatus(Long userId, NotificationRecipient.ReadStatus readStatus);

    Optional<NotificationRecipient> findByMessageIdAndUserId(Long messageId, Long userId);

    List<NotificationRecipient> findByMessageId(Long messageId);

    @Query("SELECT r FROM NotificationRecipient r WHERE r.userId = :userId ORDER BY r.id DESC")
    Page<NotificationRecipient> findMyRecipients(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT r FROM NotificationRecipient r WHERE r.userId = :userId AND r.readStatus = :readStatus ORDER BY r.id DESC")
    Page<NotificationRecipient> findMyRecipientsByReadStatus(
            @Param("userId") Long userId,
            @Param("readStatus") NotificationRecipient.ReadStatus readStatus,
            Pageable pageable);
}
