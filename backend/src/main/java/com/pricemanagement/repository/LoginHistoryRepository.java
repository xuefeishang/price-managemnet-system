package com.pricemanagement.repository;

import com.pricemanagement.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    @Query("SELECT h FROM LoginHistory h WHERE " +
            "(:userId IS NULL OR h.userId = :userId) AND " +
            "(:username IS NULL OR h.username = :username) AND " +
            "(:result IS NULL OR h.result = :result) AND " +
            "(:startTime IS NULL OR h.loginTime >= :startTime) AND " +
            "(:endTime IS NULL OR h.loginTime <= :endTime) " +
            "ORDER BY h.loginTime DESC")
    Page<LoginHistory> findMine(
            @Param("userId") Long userId,
            @Param("username") String username,
            @Param("result") String result,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);
}

