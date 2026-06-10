package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationChannelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationChannelConfigRepository extends JpaRepository<NotificationChannelConfig, Long> {

    Optional<NotificationChannelConfig> findByChannel(String channel);
}
