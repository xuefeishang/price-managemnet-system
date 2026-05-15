
package com.pricemanagement.repository;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByNameContaining(String name, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    List<Product> findByStatus(CommonStatus status);

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByStatus(CommonStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    Page<Product> findByCategoryIdAndStatus(Long categoryId, CommonStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    @Override
    Page<Product> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"category"})
    @Override
    Optional<Product> findById(Long id);
}
