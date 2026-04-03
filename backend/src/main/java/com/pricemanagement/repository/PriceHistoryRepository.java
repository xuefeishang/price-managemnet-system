
package com.pricemanagement.repository;

import com.pricemanagement.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByProductIdOrderByChangedTimeDesc(Long productId);

    List<PriceHistory> findByPriceIdOrderByChangedTimeDesc(Long priceId);
}
