package com.pricemanagement.repository;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCode(String code);

    List<Customer> findByStatusOrderBySortOrderAsc(CommonStatus status);

    boolean existsByCode(String code);
}
