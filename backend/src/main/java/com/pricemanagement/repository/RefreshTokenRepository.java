package com.pricemanagement.repository;

import com.pricemanagement.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * 刷新令牌 Repository
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 根据令牌查找
     */
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserIdAndRevokedFalseAndExpiryDateAfterOrderByCreatedTimeDesc(Long userId, java.time.LocalDateTime now);

    /**
     * 根据用户ID查找有效令牌
     */
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.userId = :userId AND rt.revoked = false AND rt.expiryDate > CURRENT_TIMESTAMP")
    Optional<RefreshToken> findValidTokenByUserId(@Param("userId") Long userId);

    /**
     * 删除用户的刷新令牌
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    /**
     * 撤销用户的所有刷新令牌
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.userId = :userId AND rt.token <> :token")
    void revokeOtherByUserId(@Param("userId") Long userId, @Param("token") String token);

    /**
     * 删除过期令牌
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < CURRENT_TIMESTAMP")
    void deleteExpiredTokens();
}
