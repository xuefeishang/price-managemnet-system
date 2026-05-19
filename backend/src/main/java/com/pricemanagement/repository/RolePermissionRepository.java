package com.pricemanagement.repository;

import com.pricemanagement.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);

    void deleteByRoleIdAndPermissionId(Long roleId, Long permissionId);

    @Query("SELECT rp.permissionId FROM RolePermission rp WHERE rp.roleId = ?1")
    List<Long> findPermissionIdsByRoleId(Long roleId);

    @Query("SELECT p.permissionCode FROM RolePermission rp JOIN SysPermission p ON rp.permissionId = p.id WHERE rp.roleId IN ?1")
    List<String> findPermissionCodesByRoleIds(List<Long> roleIds);
}