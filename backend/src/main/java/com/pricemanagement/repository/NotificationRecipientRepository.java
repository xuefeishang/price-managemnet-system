package com.pricemanagement.repository;

import com.pricemanagement.dto.NotificationRecipientDTO;
import com.pricemanagement.entity.NotificationRecipient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    @Query("""
            SELECT COUNT(r)
            FROM NotificationRecipient r
            JOIN NotificationMessage m ON m.id = r.messageId
            WHERE r.userId = :userId
              AND r.readStatus = :readStatus
              AND r.archived = false
              AND (m.expireTime IS NULL OR m.expireTime > :now)
            """)
    long countVisibleByUserIdAndReadStatus(@Param("userId") Long userId,
                                           @Param("readStatus") NotificationRecipient.ReadStatus readStatus,
                                           @Param("now") LocalDateTime now);

    Optional<NotificationRecipient> findByMessageIdAndUserId(Long messageId, Long userId);

    List<NotificationRecipient> findByMessageId(Long messageId);

    @Query(value = """
            SELECT new com.pricemanagement.dto.NotificationRecipientDTO(
                r.id,
                r.messageId,
                r.userId,
                u.username,
                u.nickname,
                r.readStatus,
                r.readTime,
                r.archived,
                r.archivedTime,
                r.firstSeenTime
            )
            FROM NotificationRecipient r
            LEFT JOIN User u ON u.id = r.userId
            WHERE r.messageId = :messageId
            ORDER BY r.id ASC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM NotificationRecipient r
            WHERE r.messageId = :messageId
            """)
    Page<NotificationRecipientDTO> findAdminRecipientDtosByMessageId(
            @Param("messageId") Long messageId,
            Pageable pageable);

    @Modifying
    @Query("UPDATE NotificationRecipient r SET r.readStatus = :readStatus, r.readTime = CURRENT_TIMESTAMP " +
            "WHERE r.userId = :userId AND r.readStatus = :unreadStatus AND r.archived = false " +
            "AND EXISTS (SELECT 1 FROM NotificationMessage m WHERE m.id = r.messageId AND (m.expireTime IS NULL OR m.expireTime > CURRENT_TIMESTAMP))")
    int markAllReadByUserId(@Param("userId") Long userId,
                            @Param("readStatus") NotificationRecipient.ReadStatus readStatus,
                            @Param("unreadStatus") NotificationRecipient.ReadStatus unreadStatus);

    @Modifying
    @Query("UPDATE NotificationRecipient r SET r.archived = true, r.archivedTime = CURRENT_TIMESTAMP " +
            "WHERE r.messageId = :messageId AND r.archived = false")
    int archiveByMessageId(@Param("messageId") Long messageId);

    @Query("SELECT r FROM NotificationRecipient r WHERE r.userId = :userId ORDER BY r.id DESC")
    Page<NotificationRecipient> findMyRecipients(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT r FROM NotificationRecipient r WHERE r.userId = :userId AND r.readStatus = :readStatus ORDER BY r.id DESC")
    Page<NotificationRecipient> findMyRecipientsByReadStatus(
            @Param("userId") Long userId,
            @Param("readStatus") NotificationRecipient.ReadStatus readStatus,
            Pageable pageable);

    @Query(value = """
            SELECT r
            FROM NotificationRecipient r
            JOIN NotificationMessage m ON m.id = r.messageId
            WHERE r.userId = :userId
              AND r.archived = false
              AND (m.expireTime IS NULL OR m.expireTime > :now)
              AND (:readStatus IS NULL OR r.readStatus = :readStatus)
            ORDER BY r.id DESC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM NotificationRecipient r
            JOIN NotificationMessage m ON m.id = r.messageId
            WHERE r.userId = :userId
              AND r.archived = false
              AND (m.expireTime IS NULL OR m.expireTime > :now)
              AND (:readStatus IS NULL OR r.readStatus = :readStatus)
            """)
    Page<NotificationRecipient> findVisibleMyRecipients(
            @Param("userId") Long userId,
            @Param("readStatus") NotificationRecipient.ReadStatus readStatus,
            @Param("now") LocalDateTime now,
            Pageable pageable);
}
