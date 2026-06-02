package com.pricemanagement.repository;

import com.pricemanagement.entity.ApiKey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long>, JpaSpecificationExecutor<ApiKey> {

    Optional<ApiKey> findByAppId(String appId);

    boolean existsByAppId(String appId);

    Page<ApiKey> findByAppIdContainingOrNameContaining(String appId, String name, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE ApiKey k SET k.lastUsedTime = :lastUsedTime WHERE k.id = :id")
    void updateLastUsedTime(@Param("id") Long id, @Param("lastUsedTime") LocalDateTime lastUsedTime);
}
