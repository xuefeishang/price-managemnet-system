package com.pricemanagement.repository;

import com.pricemanagement.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {

    Optional<SysPermission> findByPermissionCode(String permissionCode);

    List<SysPermission> findByPermissionType(String permissionType);

    List<SysPermission> findByParentId(Long parentId);

    List<SysPermission> findByStatus(String status);

    List<SysPermission> findByStatusOrderBySortOrderAsc(String status);

    List<SysPermission> findAllByOrderBySortOrderAsc();

    @Query("SELECT p FROM SysPermission p JOIN RolePermission rp ON p.id = rp.permissionId WHERE rp.roleId = :roleId")
    List<SysPermission> findByRoleId(@Param("roleId") Long roleId);

    @Query("SELECT p.id FROM SysPermission p JOIN RolePermission rp ON p.id = rp.permissionId WHERE rp.roleId = :roleId")
    List<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    @Modifying
    @Query("INSERT INTO RolePermission (roleId, permissionId, createdTime) VALUES (:roleId, :permissionId, CURRENT_TIMESTAMP)")
    void insertRolePermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
}