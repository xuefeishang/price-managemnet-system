package com.pricemanagement.repository;

import com.pricemanagement.entity.NotificationMiniProgramTemplateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationMiniProgramTemplateHistoryRepository
        extends JpaRepository<NotificationMiniProgramTemplateHistory, Long> {

    List<NotificationMiniProgramTemplateHistory> findByTemplateIdRefOrderByCreatedTimeDesc(Long templateIdRef);
}
