package com.pricemanagement.repository;

import com.pricemanagement.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    Page<OperationLog> findByUserId(Long userId, Pageable pageable);

    Page<OperationLog> findByOperationType(String operationType, Pageable pageable);

    Page<OperationLog> findByOperationModule(String operationType, Pageable pageable);

    @Query("SELECT o FROM OperationLog o WHERE o.operationTime BETWEEN :startTime AND :endTime ORDER BY o.operationTime DESC")
    Page<OperationLog> findByOperationTimeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    @Query("SELECT o FROM OperationLog o WHERE " +
           "(:username IS NULL OR o.username LIKE %:username%) AND " +
           "(:operationType IS NULL OR o.operationType = :operationType) AND " +
           "(:operationModule IS NULL OR o.operationModule = :operationModule) AND " +
           "(:startTime IS NULL OR o.operationTime >= :startTime) AND " +
           "(:endTime IS NULL OR o.operationTime <= :endTime) " +
           "ORDER BY o.operationTime DESC")
    Page<OperationLog> searchLogs(
            @Param("username") String username,
            @Param("operationType") String operationType,
            @Param("operationModule") String operationModule,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    List<OperationLog> findTop10ByOrderByOperationTimeDesc();

    // 统计：按操作类型分组计数
    @Query("SELECT o.operationType, COUNT(o) FROM OperationLog o " +
           "WHERE o.operationTime BETWEEN :startTime AND :endTime " +
           "GROUP BY o.operationType")
    List<Object[]> countByOperationTypeBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // 统计：按用户名分组计数（用户活跃度排行）
    @Query("SELECT o.username, COUNT(o) FROM OperationLog o " +
           "WHERE o.operationTime BETWEEN :startTime AND :endTime " +
           "GROUP BY o.username ORDER BY COUNT(o) DESC")
    List<Object[]> countByUserBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // 统计：按模块分组计数
    @Query("SELECT o.operationModule, COUNT(o) FROM OperationLog o " +
           "WHERE o.operationTime BETWEEN :startTime AND :endTime " +
           "GROUP BY o.operationModule")
    List<Object[]> countByModuleBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // 统计：按日期分组计数（每日趋势）
    @Query("SELECT FUNCTION('DATE', o.operationTime), COUNT(o) FROM OperationLog o " +
           "WHERE o.operationTime BETWEEN :startTime AND :endTime " +
           "GROUP BY FUNCTION('DATE', o.operationTime) ORDER BY FUNCTION('DATE', o.operationTime)")
    List<Object[]> countByDateBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // 统计：登录次数
    @Query("SELECT COUNT(o) FROM OperationLog o " +
           "WHERE o.operationType = 'LOGIN' AND o.operationTime BETWEEN :startTime AND :endTime")
    long countLoginBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // 统计：活跃用户数
    @Query("SELECT COUNT(DISTINCT o.username) FROM OperationLog o " +
           "WHERE o.operationTime BETWEEN :startTime AND :endTime")
    long countActiveUsersBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // 统计：总操作数
    @Query("SELECT COUNT(o) FROM OperationLog o " +
           "WHERE o.operationTime BETWEEN :startTime AND :endTime")
    long countTotalBetween(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // 获取某用户的操作统计
    @Query("SELECT o.operationType, COUNT(o) FROM OperationLog o " +
           "WHERE o.username = :username AND o.operationTime BETWEEN :startTime AND :endTime " +
           "GROUP BY o.operationType")
    List<Object[]> countByUsernameBetween(
            @Param("username") String username,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
