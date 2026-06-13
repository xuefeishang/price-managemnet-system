
package com.pricemanagement.repository;

import com.pricemanagement.entity.User;
import com.pricemanagement.entity.NotificationMiniProgramEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByUsernameIn(Collection<String> usernames);

    @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.nickname LIKE %:keyword%")
    List<User> findByUsernameOrNicknameContaining(@Param("keyword") String keyword);

    boolean existsByUsername(String username);

    Optional<User> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    Optional<User> findByWechatOpenid(String wechatOpenid);

    List<User> findByStatus(com.pricemanagement.constants.CommonStatus status);

    @Query("""
            SELECT COUNT(u)
            FROM User u
            WHERE u.status = :status
              AND u.wechatOpenid IS NOT NULL
              AND u.wechatOpenid <> ''
            """)
    long countActiveUsersWithWechatOpenid(@Param("status") com.pricemanagement.constants.CommonStatus status);

    @Query("""
            SELECT u FROM User u
            WHERE u.status = :status
              AND (:role IS NULL OR u.role = :role)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<User> findActiveMiniProgramSubscriptionTargets(
            @Param("status") com.pricemanagement.constants.CommonStatus status,
            @Param("role") User.Role role,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT u FROM User u
            JOIN NotificationMiniProgramEligibility e ON e.userId = u.id
            WHERE u.status = :status
              AND e.rowStatus = :rowStatus
              AND (:role IS NULL OR u.role = :role)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.nickname) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<User> findActiveMiniProgramSubscriptionTargetsByEligibilityStatus(
            @Param("status") com.pricemanagement.constants.CommonStatus status,
            @Param("rowStatus") NotificationMiniProgramEligibility.RowStatus rowStatus,
            @Param("role") User.Role role,
            @Param("keyword") String keyword,
            Pageable pageable);
}
