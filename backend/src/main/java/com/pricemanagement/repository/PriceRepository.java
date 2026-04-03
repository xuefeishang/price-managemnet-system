
package com.pricemanagement.repository;

import com.pricemanagement.entity.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRepository extends JpaRepository<Price, Long> {

    Optional<Price> findFirstByProductIdOrderByCreatedTimeDesc(Long productId);

    List<Price> findByProductIdOrderByCreatedTimeDesc(Long productId);

    List<Price> findByEffectiveDateLessThanEqualAndExpiryDateGreaterThanEqual(
        LocalDate effectiveDate, LocalDate expiryDate);

    /**
     * 查询指定日期有效的价格（生效日期 <= 查询日期 且 (失效日期 >= 查询日期 或 失效日期为null)）
     * 每个产品只返回最近生效日期的那条记录
     */
    @Query(value = "SELECT p.* FROM price p INNER JOIN (" +
            "SELECT product_id, MAX(effective_date) as max_effective_date FROM price " +
            "WHERE effective_date <= :date AND (expiry_date >= :date OR expiry_date IS NULL) " +
            "GROUP BY product_id" +
            ") latest ON p.product_id = latest.product_id AND p.effective_date = latest.max_effective_date " +
            "ORDER BY p.product_id", nativeQuery = true)
    List<Price> findValidPricesByDate(@Param("date") LocalDate date);

    /**
     * 查询某产品在指定日期有效的价格
     */
    @Query("SELECT p FROM Price p WHERE p.product.id = :productId AND p.effectiveDate <= :date AND (p.expiryDate >= :date OR p.expiryDate IS NULL) ORDER BY p.effectiveDate DESC")
    Optional<Price> findValidPriceByProductIdAndDate(@Param("productId") Long productId, @Param("date") LocalDate date);

    /**
     * 查询某产品在某月所有有效价格的平均当前售价
     */
    @Query("SELECT AVG(p.currentPrice) FROM Price p WHERE p.product.id = :productId AND p.effectiveDate >= :startDate AND p.effectiveDate <= :endDate")
    Double findAveragePriceByProductIdAndMonth(@Param("productId") Long productId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
