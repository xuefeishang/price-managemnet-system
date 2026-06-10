package com.pricemanagement.repository;

import com.pricemanagement.entity.SystemNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SystemNoticeRepository extends JpaRepository<SystemNotice, Long>, JpaSpecificationExecutor<SystemNotice> {

    List<SystemNotice> findByStatusAndScheduledPublishTimeLessThanEqual(SystemNotice.NoticeStatus status,
                                                                        LocalDateTime now);

    List<SystemNotice> findByStatusAndExpireTimeLessThanEqual(SystemNotice.NoticeStatus status, LocalDateTime now);
}
