package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationMiniProgramEligibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationMiniProgramEligibilityRepository
        extends JpaRepository<NotificationMiniProgramEligibility, Long> {

    Optional<NotificationMiniProgramEligibility> findByUserId(Long userId);
}
