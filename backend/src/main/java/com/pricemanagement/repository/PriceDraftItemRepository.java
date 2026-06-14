package com.pricemanagement.repository;

import com.pricemanagement.entity.PriceDraftItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceDraftItemRepository extends JpaRepository<PriceDraftItem, Long> {

    List<PriceDraftItem> findByBatchIdOrderByIdAsc(Long batchId);

    List<PriceDraftItem> findByBatchIdAndProductIdIn(Long batchId, Collection<Long> productIds);

    Optional<PriceDraftItem> findByBatchIdAndProductId(Long batchId, Long productId);

    long countByBatchId(Long batchId);

    @Query("SELECT i.batchId, COUNT(i) FROM PriceDraftItem i WHERE i.batchId IN :batchIds GROUP BY i.batchId")
    List<Object[]> countItemsByBatchIds(@Param("batchIds") Collection<Long> batchIds);
}
