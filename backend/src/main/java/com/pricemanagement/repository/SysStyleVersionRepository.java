package com.pricemanagement.repository;

import com.pricemanagement.entity.SysStyleVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 样式版本历史 Repository
 */
@Repository
public interface SysStyleVersionRepository extends JpaRepository<SysStyleVersion, Long> {

    /**
     * 根据版本号查询
     */
    Optional<SysStyleVersion> findByVersionNo(String versionNo);

    /**
     * 分页查询版本列表（按创建时间倒序）
     */
    Page<SysStyleVersion> findAllByOrderByCreatedTimeDesc(Pageable pageable);

    /**
     * 查询最新版本
     */
    Optional<SysStyleVersion> findFirstByOrderByCreatedTimeDesc();

    /**
     * 统计版本总数
     */
    long count();

    /**
     * 删除超出保留数量的旧版本
     * 保留最新的 keepCount 条，删除其余的
     *
     * @param keepCount 保留数量
     * @return 删除的记录数
     */
    @Modifying
    @Query(value = "DELETE FROM sys_style_version WHERE id NOT IN " +
            "(SELECT id FROM (SELECT id FROM sys_style_version ORDER BY created_time DESC LIMIT :keepCount) AS tmp)",
            nativeQuery = true)
    int deleteOldVersions(int keepCount);

}
