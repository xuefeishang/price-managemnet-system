package com.pricemanagement.repository;

import com.pricemanagement.entity.ApiKeyPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ApiKeyPermissionRepository extends JpaRepository<ApiKeyPermission, Long> {

    List<ApiKeyPermission> findByApiKeyId(Long apiKeyId);

    List<ApiKeyPermission> findByApiKeyIdIn(Collection<Long> apiKeyIds);

    void deleteByApiKeyId(Long apiKeyId);
}
