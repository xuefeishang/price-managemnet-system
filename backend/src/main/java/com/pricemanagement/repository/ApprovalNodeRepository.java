package com.pricemanagement.repository;

import com.pricemanagement.entity.ApprovalNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalNodeRepository extends JpaRepository<ApprovalNode, Long> {

    List<ApprovalNode> findByWorkflowIdOrderByNodeOrderAsc(Long workflowId);

    List<ApprovalNode> findByWorkflowIdOrderByNodeOrder(Long workflowId);

    void deleteByWorkflowId(Long workflowId);
}
