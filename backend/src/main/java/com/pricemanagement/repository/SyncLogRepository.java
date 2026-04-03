
package com.pricemanagement.repository;

import com.pricemanagement.entity.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    List<SyncLog> findBySyncTypeOrderBySyncTimeDesc(SyncLog.SyncType syncType);

    Page<SyncLog> findBySyncTypeOrderBySyncTimeDesc(SyncLog.SyncType syncType, Pageable pageable);

    List<SyncLog> findBySyncTypeAndSyncStatusOrderBySyncTimeDesc(SyncLog.SyncType syncType, SyncLog.SyncStatus syncStatus);

    List<SyncLog> findBySyncStatusOrderBySyncTimeDesc(SyncLog.SyncStatus syncStatus);
}
