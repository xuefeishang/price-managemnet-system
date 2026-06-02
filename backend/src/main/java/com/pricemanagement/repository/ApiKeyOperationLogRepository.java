package com.pricemanagement.repository;

import com.pricemanagement.entity.ApiKeyOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiKeyOperationLogRepository extends JpaRepository<ApiKeyOperationLog, Long> {
}
