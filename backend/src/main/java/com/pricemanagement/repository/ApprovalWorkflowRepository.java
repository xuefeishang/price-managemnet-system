package com.pricemanagement.repository;

import com.pricemanagement.entity.ApprovalWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, Long> {

    Optional<ApprovalWorkflow> findByWorkflowTypeAndIsActiveTrue(ApprovalWorkflow.WorkflowType workflowType);

    List<ApprovalWorkflow> findByIsActiveTrue();
}
