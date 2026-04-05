
package com.pricemanagement.repository;

import com.pricemanagement.entity.Price;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
     * 使用 LEFT JOIN FETCH 确保产品关联被正确加载
     */
    @Query("SELECT DISTINCT p FROM Price p LEFT JOIN FETCH p.product WHERE p.effectiveDate <= :date AND (p.expiryDate >= :date OR p.expiryDate IS NULL) AND p.effectiveDate = (SELECT MAX(p2.effectiveDate) FROM Price p2 WHERE p2.product.id = p.product.id AND p2.effectiveDate <= :date AND (p2.expiryDate >= :date OR p2.expiryDate IS NULL)) ORDER BY p.product.id")
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

    /**
     * 批量查询指定日期有效的价格（传入产品ID列表），使用 JOIN FETCH 确保产品关联加载
     */
    @Query("SELECT DISTINCT p FROM Price p LEFT JOIN FETCH p.product WHERE p.product.id IN :productIds AND p.effectiveDate <= :date AND (p.expiryDate >= :date OR p.expiryDate IS NULL) ORDER BY p.product.id, p.effectiveDate DESC")
    List<Price> findValidPricesByProductIdsAndDate(@Param("productIds") List<Long> productIds, @Param("date") LocalDate date);

    /**
     * 批量查询指定日期范围的月均价（传入产品ID列表）
     */
    @Query("SELECT p.product.id, AVG(p.currentPrice) FROM Price p WHERE p.product.id IN :productIds AND p.effectiveDate >= :startDate AND p.effectiveDate <= :endDate GROUP BY p.product.id")
    List<Object[]> findAveragePricesByProductIdsAndMonth(@Param("productIds") List<Long> productIds, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
