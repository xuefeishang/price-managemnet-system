package com.pricemanagement.repository;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.Origin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OriginRepository extends JpaRepository<Origin, Long> {

    Optional<Origin> findByCode(String code);

    List<Origin> findByStatusOrderBySortOrderAsc(CommonStatus status);

    boolean existsByCode(String code);
}
