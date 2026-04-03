package com.pricemanagement.repository;

import com.pricemanagement.entity.ApprovalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {

    List<ApprovalRecord> findByRequestIdOrderByActionTimeAsc(Long requestId);
}
