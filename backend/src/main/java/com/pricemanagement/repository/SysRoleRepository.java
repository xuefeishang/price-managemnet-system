package com.pricemanagement.repository;

import com.pricemanagement.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByRoleCode(String roleCode);

    boolean existsByRoleCode(String roleCode);

    List<SysRole> findByStatus(String status);

    List<SysRole> findByStatusOrderBySortOrderAsc(String status);
}