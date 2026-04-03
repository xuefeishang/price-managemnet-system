package com.pricemanagement.repository;

import com.pricemanagement.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<MenuItem> findByParentIdIsNullOrderBySortOrderAsc();

    Optional<MenuItem> findByPath(String path);
}
