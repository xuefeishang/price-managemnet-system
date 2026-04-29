package com.pricemanagement.repository;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysDict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysDictRepository extends JpaRepository<SysDict, Long> {

    List<SysDict> findByCategoryOrderBySortOrderAsc(String category);

    List<SysDict> findByCategoryAndStatusOrderBySortOrderAsc(String category, CommonStatus status);

    Optional<SysDict> findByCategoryAndDictKey(String category, String dictKey);

    boolean existsByCategoryAndDictKey(String category, String dictKey);

    List<SysDict> findAllByOrderByCategoryAscSortOrderAsc();

    List<SysDict> findByStatusOrderByCategoryAscSortOrderAsc(CommonStatus status);

    @Query("SELECT DISTINCT d.category FROM SysDict d WHERE d.status = :status ORDER BY d.category")
    List<String> findDistinctCategoryByStatus(CommonStatus status);

    @Query("SELECT DISTINCT d.category FROM SysDict d ORDER BY d.category")
    List<String> findAllDistinctCategory();

    void deleteByCategory(String category);
}
