package com.pricemanagement.repository;

import com.pricemanagement.entity.ExternalApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExternalApiEndpointRepository extends JpaRepository<ExternalApiEndpoint, Long> {

    List<ExternalApiEndpoint> findByStatusOrderBySortOrderAsc(String status);
}
