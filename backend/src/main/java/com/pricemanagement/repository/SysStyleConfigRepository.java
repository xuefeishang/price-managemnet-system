package com.pricemanagement.repository;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysStyleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysStyleConfigRepository extends JpaRepository<SysStyleConfig, Long> {

    Optional<SysStyleConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);

    void deleteByConfigKey(String configKey);

}
