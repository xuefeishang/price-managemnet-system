package com.pricemanagement.repository;

import com.pricemanagement.entity.SecurityEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SecurityEventRepository
        extends JpaRepository<SecurityEvent, Long>, JpaSpecificationExecutor<SecurityEvent> {
}
