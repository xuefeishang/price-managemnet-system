package com.pricemanagement.repository;

import com.pricemanagement.entity.PricePublishLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PricePublishLogRepository extends JpaRepository<PricePublishLog, Long> {

    List<PricePublishLog> findByBatchIdOrderByCreatedTimeDesc(Long batchId);

    Page<PricePublishLog> findAllByOrderByCreatedTimeDesc(Pageable pageable);
}
