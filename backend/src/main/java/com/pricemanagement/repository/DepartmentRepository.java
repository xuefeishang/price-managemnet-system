package com.pricemanagement.repository;

import com.pricemanagement.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByDeptCode(String deptCode);

    boolean existsByDeptCode(String deptCode);

    List<Department> findByParentIdIsNullOrderBySortOrderAsc();

    List<Department> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<Department> findByStatusOrderBySortOrderAsc(String status);

    List<Department> findByParentIdAndStatusOrderBySortOrderAsc(Long parentId, String status);

    @Query("SELECT d FROM Department d WHERE d.status = 'ACTIVE' ORDER BY d.sortOrder ASC")
    List<Department> findAllActive();

    @Query("SELECT d FROM Department d LEFT JOIN User u ON u.deptId = d.id GROUP BY d.id ORDER BY d.sortOrder ASC")
    List<Department> findAllWithUserCount();

    @Query("SELECT COUNT(u) FROM User u WHERE u.deptId = :deptId")
    Long countUsersByDeptId(@Param("deptId") Long deptId);

    @Query("SELECT d FROM Department d WHERE d.path LIKE :pathPrefix ORDER BY d.level, d.sortOrder ASC")
    List<Department> findByPathStartingWith(@Param("pathPrefix") String pathPrefix);

    @Query("SELECT MAX(d.sortOrder) FROM Department d WHERE d.parentId = :parentId")
    Integer findMaxSortOrderByParentId(@Param("parentId") Long parentId);

    @Query("SELECT MAX(d.sortOrder) FROM Department d WHERE d.parentId IS NULL")
    Integer findMaxSortOrderForRoot();
}
