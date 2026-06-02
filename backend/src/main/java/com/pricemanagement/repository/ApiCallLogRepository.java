package com.pricemanagement.repository;

import com.pricemanagement.entity.ApiCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApiCallLogRepository extends JpaRepository<ApiCallLog, Long>, JpaSpecificationExecutor<ApiCallLog> {

    @Query("""
            SELECT l.authResult, COUNT(l)
            FROM ApiCallLog l
            WHERE l.requestTime BETWEEN :startTime AND :endTime
            GROUP BY l.authResult
            """)
    List<Object[]> countByAuthResult(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime);

    @Query("""
            SELECT COUNT(l)
            FROM ApiCallLog l
            WHERE l.requestTime BETWEEN :startTime AND :endTime
            """)
    long countTotal(@Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime);

    Page<ApiCallLog> findByApiKeyId(Long apiKeyId, Pageable pageable);

    long deleteByCreatedTimeBefore(LocalDateTime cutoff);
}
