package com.pricemanagement.repository;

import com.pricemanagement.entity.ProductAnnualBudget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAnnualBudgetRepository extends JpaRepository<ProductAnnualBudget, Long> {

    Optional<ProductAnnualBudget> findByProductIdAndBudgetYear(Long productId, Integer budgetYear);

    List<ProductAnnualBudget> findByProductIdInAndBudgetYear(Collection<Long> productIds, Integer budgetYear);

    @Query("SELECT DISTINCT b.budgetYear FROM ProductAnnualBudget b WHERE b.product.id = :productId ORDER BY b.budgetYear DESC")
    List<Integer> findBudgetYearsByProductId(@Param("productId") Long productId);
}
