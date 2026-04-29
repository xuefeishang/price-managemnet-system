
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

    /**
     * 按产品和生效日期精确查找价格记录（取最新创建的一条，防止重复数据导致异常）
     */
    Optional<Price> findFirstByProductIdAndEffectiveDateOrderByCreatedTimeDesc(Long productId, LocalDate effectiveDate);

    /**
     * 查找某产品某日期的所有价格记录（用于清理重复数据）
     */
    List<Price> findAllByProductIdAndEffectiveDateOrderByCreatedTimeDesc(Long productId, LocalDate effectiveDate);

    /**
     * 查找所有存在重复 (product_id, effective_date) 的产品ID和日期
     */
    @Query("SELECT p.product.id, p.effectiveDate FROM Price p GROUP BY p.product.id, p.effectiveDate HAVING COUNT(p) > 1")
    List<Object[]> findDuplicateProductDateCombinations();

    /**
     * 查询指定日期有效的价格（精确匹配 effectiveDate）
     * 使用 LEFT JOIN FETCH 确保产品关联被正确加载
     * 按product_id和createdTime排序，确保同产品同日期重复数据中最新记录被保留
     */
    @Query("SELECT DISTINCT p FROM Price p LEFT JOIN FETCH p.product WHERE p.effectiveDate = :date ORDER BY p.product.id, p.createdTime DESC")
    List<Price> findValidPricesByDate(@Param("date") LocalDate date);

    /**
     * 查询某产品在指定日期有效的价格（精确匹配 effectiveDate）
     * 如有重复数据，返回createdTime最新的一条
     */
    @Query("SELECT p FROM Price p WHERE p.product.id = :productId AND p.effectiveDate = :date ORDER BY p.createdTime DESC LIMIT 1")
    Optional<Price> findValidPriceByProductIdAndDate(@Param("productId") Long productId, @Param("date") LocalDate date);

    /**
     * 查询某产品在某月所有有效价格的平均当前售价
     */
    @Query("SELECT AVG(p.currentPrice) FROM Price p WHERE p.product.id = :productId AND p.effectiveDate >= :startDate AND p.effectiveDate <= :endDate")
    Double findAveragePriceByProductIdAndMonth(@Param("productId") Long productId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * 批量查询指定日期有效的价格（传入产品ID列表，精确匹配 effectiveDate）
     * 按product_id和createdTime排序，确保同产品同日期重复数据中最新记录被保留
     */
    @Query("SELECT DISTINCT p FROM Price p LEFT JOIN FETCH p.product WHERE p.product.id IN :productIds AND p.effectiveDate = :date ORDER BY p.product.id, p.createdTime DESC")
    List<Price> findValidPricesByProductIdsAndDate(@Param("productIds") List<Long> productIds, @Param("date") LocalDate date);

    /**
     * 批量查询指定日期范围的月均价（传入产品ID列表）
     */
    @Query("SELECT p.product.id, AVG(p.currentPrice) FROM Price p WHERE p.product.id IN :productIds AND p.effectiveDate >= :startDate AND p.effectiveDate <= :endDate GROUP BY p.product.id")
    List<Object[]> findAveragePricesByProductIdsAndMonth(@Param("productIds") List<Long> productIds, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * 查找某产品在指定日期之前（含当天）最近的一条价格记录
     */
    @Query("SELECT p FROM Price p WHERE p.product.id = :productId AND p.effectiveDate <= :date ORDER BY p.effectiveDate DESC LIMIT 1")
    Optional<Price> findLatestPriceBeforeDate(@Param("productId") Long productId, @Param("date") LocalDate date);

    /**
     * 批量查找多个产品在指定日期之前（含当天）最近的价格记录
     * 返回每个产品的最新价格
     */
    @Query("SELECT p FROM Price p WHERE p.product.id IN :productIds AND p.effectiveDate <= :date AND p.effectiveDate = (SELECT MAX(p2.effectiveDate) FROM Price p2 WHERE p2.product.id = p.product.id AND p2.effectiveDate <= :date)")
    List<Price> findLatestPricesBeforeDate(@Param("productIds") List<Long> productIds, @Param("date") LocalDate date);

    /**
     * 查询某产品在指定日期范围内的价格记录，按生效日期升序排列
     * 如同一天有多条记录，只取createdTime最新的一条
     */
    @Query("SELECT p FROM Price p WHERE p.product.id = :productId AND p.effectiveDate >= :startDate AND p.effectiveDate <= :endDate ORDER BY p.effectiveDate ASC, p.createdTime DESC")
    List<Price> findByProductIdAndDateRange(@Param("productId") Long productId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
