package com.pricemanagement.repository;

import com.pricemanagement.entity.ApprovalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Page<ApprovalRequest> findByApplicantIdOrderByCreatedTimeDesc(Long applicantId, Pageable pageable);

    Page<ApprovalRequest> findByStatusOrderByCreatedTimeDesc(ApprovalRequest.RequestStatus status, Pageable pageable);

    Optional<ApprovalRequest> findByBusinessTypeAndBusinessIdAndStatus(
            ApprovalRequest.BusinessType businessType,
            Long businessId,
            ApprovalRequest.RequestStatus status);

    boolean existsByBusinessTypeAndBusinessIdAndStatus(
            ApprovalRequest.BusinessType businessType,
            Long businessId,
            ApprovalRequest.RequestStatus status);

    @Query("SELECT r FROM ApprovalRequest r WHERE " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:businessType IS NULL OR r.businessType = :businessType) AND " +
           "(:applicantId IS NULL OR r.applicantId = :applicantId)")
    Page<ApprovalRequest> findRequests(
            @Param("status") ApprovalRequest.RequestStatus status,
            @Param("businessType") ApprovalRequest.BusinessType businessType,
            @Param("applicantId") Long applicantId,
            Pageable pageable);

    @Query("SELECT r FROM ApprovalRequest r WHERE " +
           "r.status = 'PENDING'")
    Page<ApprovalRequest> findPendingApprovals(Pageable pageable);
}
