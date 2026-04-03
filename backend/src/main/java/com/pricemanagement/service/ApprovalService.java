package com.pricemanagement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricemanagement.entity.*;
import com.pricemanagement.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalWorkflowRepository workflowRepository;
    private final ApprovalNodeRepository nodeRepository;
    private final ApprovalRequestRepository requestRepository;
    private final ApprovalRecordRepository recordRepository;
    private final ProductRepository productRepository;
    private final PriceRepository priceRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取所有审批工作流
     */
    public List<ApprovalWorkflow> getAllWorkflows() {
        return workflowRepository.findAll();
    }

    /**
     * 获取激活的工作流
     */
    public List<ApprovalWorkflow> getActiveWorkflows() {
        return workflowRepository.findByIsActiveTrue();
    }

    /**
     * 获取工作流详情（包括节点）
     */
    public Optional<ApprovalWorkflow> getWorkflowById(Long id) {
        return workflowRepository.findById(id);
    }

    /**
     * 激活工作流
     */
    @Transactional
    public ApprovalWorkflow activateWorkflow(Long id) {
        ApprovalWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + id));
        workflow.setIsActive(true);
        ApprovalWorkflow saved = workflowRepository.save(workflow);
        log.info("Activated workflow: {}", saved.getWorkflowName());
        return saved;
    }

    /**
     * 停用工作流
     */
    @Transactional
    public ApprovalWorkflow deactivateWorkflow(Long id) {
        ApprovalWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + id));
        workflow.setIsActive(false);
        ApprovalWorkflow saved = workflowRepository.save(workflow);
        log.info("Deactivated workflow: {}", saved.getWorkflowName());
        return saved;
    }

    /**
     * 创建审批工作流
     */
    @Transactional
    public ApprovalWorkflow createWorkflow(ApprovalWorkflow workflow) {
        ApprovalWorkflow saved = workflowRepository.save(workflow);
        log.info("Created approval workflow: {}", saved.getWorkflowName());
        return saved;
    }

    /**
     * 更新工作流
     */
    @Transactional
    public ApprovalWorkflow updateWorkflow(Long id, ApprovalWorkflow workflow) {
        ApprovalWorkflow existing = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + id));

        existing.setWorkflowName(workflow.getWorkflowName());
        existing.setWorkflowType(workflow.getWorkflowType());
        existing.setApprovalLevel(workflow.getApprovalLevel());
        existing.setIsActive(workflow.getIsActive());

        ApprovalWorkflow saved = workflowRepository.save(existing);
        log.info("Updated approval workflow: {}", saved.getWorkflowName());
        return saved;
    }

    /**
     * 删除工作流
     */
    @Transactional
    public void deleteWorkflow(Long id) {
        ApprovalWorkflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("工作流不存在: " + id));

        // 删除关联的节点
        nodeRepository.deleteByWorkflowId(id);
        workflowRepository.delete(workflow);
        log.info("Deleted approval workflow: {}", workflow.getWorkflowName());
    }

    /**
     * 获取工作流的节点列表
     */
    public List<ApprovalNode> getWorkflowNodes(Long workflowId) {
        return nodeRepository.findByWorkflowIdOrderByNodeOrder(workflowId);
    }

    /**
     * 添加审批节点
     */
    @Transactional
    public ApprovalNode addWorkflowNode(ApprovalNode node) {
        ApprovalNode saved = nodeRepository.save(node);
        log.info("Added node to workflow {}: order={}, type={}", node.getWorkflowId(), node.getNodeOrder(), node.getNodeType());
        return saved;
    }

    /**
     * 更新审批节点
     */
    @Transactional
    public ApprovalNode updateNode(Long id, ApprovalNode node) {
        ApprovalNode existing = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + id));

        existing.setNodeOrder(node.getNodeOrder());
        existing.setNodeType(node.getNodeType());
        existing.setApproverRole(node.getApproverRole());
        existing.setIsRequired(node.getIsRequired());

        ApprovalNode saved = nodeRepository.save(existing);
        log.info("Updated node {} in workflow {}", id, existing.getWorkflowId());
        return saved;
    }

    /**
     * 删除审批节点
     */
    @Transactional
    public void deleteNode(Long id) {
        ApprovalNode node = nodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + id));
        nodeRepository.delete(node);
        log.info("Deleted node {} from workflow {}", id, node.getWorkflowId());
    }

    /**
     * 分页查询审批请求
     */
    public Page<ApprovalRequest> getRequests(int page, int size, ApprovalRequest.RequestStatus status,
                                              ApprovalRequest.BusinessType businessType, Long applicantId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime"));
        return requestRepository.findRequests(status, businessType, applicantId, pageable);
    }

    /**
     * 获取待我审批的请求
     */
    public Page<ApprovalRequest> getPendingApprovals(String approverRole, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdTime"));
        // 注意：当前实现返回所有PENDING状态的请求，前端可根据approverRole过滤
        return requestRepository.findPendingApprovals(pageable);
    }

    /**
     * 获取申请详情
     */
    public Optional<ApprovalRequest> getRequestById(Long id) {
        return requestRepository.findById(id);
    }

    /**
     * 创建审批请求（价格变更等业务触发）
     */
    @Transactional
    public ApprovalRequest createRequest(ApprovalRequest request, String approverRole) {
        // 查找对应类型的工作流
        ApprovalWorkflow.WorkflowType workflowType = request.getBusinessType() == ApprovalRequest.BusinessType.PRICE
                ? ApprovalWorkflow.WorkflowType.PRICE_CHANGE
                : ApprovalWorkflow.WorkflowType.PRODUCT_CREATE;

        ApprovalWorkflow workflow = workflowRepository.findByWorkflowTypeAndIsActiveTrue(workflowType)
                .orElseThrow(() -> new IllegalStateException("未找到可用的审批工作流"));

        // 获取第一个节点
        List<ApprovalNode> nodes = nodeRepository.findByWorkflowIdOrderByNodeOrder(workflow.getId());
        if (nodes.isEmpty()) {
            throw new IllegalStateException("审批工作流没有配置节点");
        }

        ApprovalNode firstNode = nodes.get(0);

        request.setWorkflowId(workflow.getId());
        request.setCurrentNodeId(firstNode.getId());
        request.setStatus(ApprovalRequest.RequestStatus.PENDING);

        ApprovalRequest saved = requestRepository.save(request);
        log.info("Created approval request: id={}, type={}, firstNode={}",
                saved.getId(), saved.getBusinessType(), firstNode.getNodeOrder());

        return saved;
    }

    /**
     * 执行审批操作
     */
    @Transactional
    public ApprovalRequest approve(Long requestId, Long approverId, String comment) {
        ApprovalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + requestId));

        if (request.getStatus() != ApprovalRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("该审批请求已处理，无法重复审批");
        }

        // 获取当前节点
        ApprovalNode currentNode = nodeRepository.findById(request.getCurrentNodeId())
                .orElseThrow(() -> new IllegalStateException("当前审批节点不存在"));

        // 记录审批操作
        ApprovalRecord record = new ApprovalRecord();
        record.setRequestId(requestId);
        record.setNodeId(currentNode.getId());
        record.setApproverId(approverId);
        record.setAction(ApprovalRecord.Action.APPROVE);
        record.setComment(comment);
        recordRepository.save(record);

        // 获取下一节点
        List<ApprovalNode> allNodes = nodeRepository.findByWorkflowIdOrderByNodeOrder(request.getWorkflowId());
        ApprovalNode nextNode = allNodes.stream()
                .filter(n -> n.getNodeOrder() > currentNode.getNodeOrder())
                .findFirst()
                .orElse(null);

        if (nextNode == null) {
            // 所有节点审批完成
            request.setStatus(ApprovalRequest.RequestStatus.APPROVED);
            request.setCurrentNodeId(null);
            log.info("Approval request {} completed and approved", requestId);

            // 执行实际业务变更（价格变更等）
            executeBusinessChange(request);
        } else {
            // 进入下一节点
            request.setCurrentNodeId(nextNode.getId());
            log.info("Approval request {} moved to next node: {}", requestId, nextNode.getNodeOrder());
        }

        return requestRepository.save(request);
    }

    /**
     * 拒绝审批
     */
    @Transactional
    public ApprovalRequest reject(Long requestId, Long approverId, String comment) {
        ApprovalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + requestId));

        if (request.getStatus() != ApprovalRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("该审批请求已处理，无法重复审批");
        }

        ApprovalNode currentNode = nodeRepository.findById(request.getCurrentNodeId())
                .orElseThrow(() -> new IllegalStateException("当前审批节点不存在"));

        // 记录拒绝操作
        ApprovalRecord record = new ApprovalRecord();
        record.setRequestId(requestId);
        record.setNodeId(currentNode.getId());
        record.setApproverId(approverId);
        record.setAction(ApprovalRecord.Action.REJECT);
        record.setComment(comment);
        recordRepository.save(record);

        request.setStatus(ApprovalRequest.RequestStatus.REJECTED);
        request.setCurrentNodeId(null);

        ApprovalRequest saved = requestRepository.save(request);
        log.info("Approval request {} rejected", requestId);
        return saved;
    }

    /**
     * 撤回审批请求
     */
    @Transactional
    public ApprovalRequest cancel(Long requestId, Long applicantId) {
        ApprovalRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("审批请求不存在: " + requestId));

        if (!request.getApplicantId().equals(applicantId)) {
            throw new IllegalArgumentException("只能撤回自己提交的审批请求");
        }

        if (request.getStatus() != ApprovalRequest.RequestStatus.PENDING) {
            throw new IllegalStateException("该审批请求已处理，无法撤回");
        }

        request.setStatus(ApprovalRequest.RequestStatus.CANCELLED);
        request.setCurrentNodeId(null);

        ApprovalRequest saved = requestRepository.save(request);
        log.info("Approval request {} cancelled by applicant", requestId);
        return saved;
    }

    /**
     * 获取审批记录
     */
    public List<ApprovalRecord> getRequestRecords(Long requestId) {
        return recordRepository.findByRequestIdOrderByActionTimeAsc(requestId);
    }

    /**
     * 执行业务变更（审批通过后）
     */
    @SuppressWarnings("unchecked")
    private void executeBusinessChange(ApprovalRequest request) {
        if (request.getBusinessType() == ApprovalRequest.BusinessType.PRICE) {
            executePriceChange(request);
        } else if (request.getBusinessType() == ApprovalRequest.BusinessType.PRODUCT) {
            executeProductChange(request);
        }
    }

    /**
     * 执行价格变更
     */
    @SuppressWarnings("unchecked")
    private void executePriceChange(ApprovalRequest request) {
        try {
            String requestData = request.getRequestData();
            if (requestData == null || requestData.isEmpty()) {
                log.error("No request data for price approval request {}", request.getId());
                return;
            }

            Map<String, Object> changeData = objectMapper.readValue(requestData, Map.class);
            String action = (String) changeData.get("action");
            Long productId = ((Number) changeData.get("productId")).longValue();

            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalStateException("产品不存在: " + productId));

            if ("CREATE".equals(action)) {
                // 创建价格
                Price price = new Price();
                price.setProduct(product);

                Object originalPrice = changeData.get("originalPrice");
                if (originalPrice != null) {
                    price.setOriginalPrice(new BigDecimal(originalPrice.toString()));
                }

                Object currentPrice = changeData.get("currentPrice");
                if (currentPrice != null) {
                    price.setCurrentPrice(new BigDecimal(currentPrice.toString()));
                }

                Object costPrice = changeData.get("costPrice");
                if (costPrice != null) {
                    price.setCostPrice(new BigDecimal(costPrice.toString()));
                }

                Object effectiveDate = changeData.get("effectiveDate");
                if (effectiveDate != null) {
                    price.setEffectiveDate(LocalDate.parse(effectiveDate.toString()));
                }

                Object expiryDate = changeData.get("expiryDate");
                if (expiryDate != null) {
                    price.setExpiryDate(LocalDate.parse(expiryDate.toString()));
                }

                Object unit = changeData.get("unit");
                if (unit != null) {
                    price.setUnit((String) unit);
                }

                Object priceSpec = changeData.get("priceSpec");
                if (priceSpec != null) {
                    price.setPriceSpec((String) priceSpec);
                }

                Price savedPrice = priceRepository.save(price);

                // 记录价格历史
                PriceHistory history = new PriceHistory();
                history.setPriceId(savedPrice.getId());
                history.setProductId(productId);
                history.setOldPrice(null);
                history.setNewPrice(savedPrice.getCurrentPrice());
                history.setChangeType(PriceHistory.ChangeType.CREATE);
                history.setRemark("审批通过 - 创建价格");
                priceHistoryRepository.save(history);

                log.info("Price created via approval: priceId={}, productId={}", savedPrice.getId(), productId);

            } else if ("UPDATE".equals(action)) {
                // 更新价格
                Long priceId = ((Number) changeData.get("priceId")).longValue();
                Price existingPrice = priceRepository.findById(priceId)
                        .orElseThrow(() -> new IllegalStateException("价格记录不存在: " + priceId));

                BigDecimal oldPrice = existingPrice.getCurrentPrice();

                Object originalPrice = changeData.get("originalPrice");
                if (originalPrice != null) {
                    existingPrice.setOriginalPrice(new BigDecimal(originalPrice.toString()));
                }

                Object currentPrice = changeData.get("currentPrice");
                if (currentPrice != null) {
                    existingPrice.setCurrentPrice(new BigDecimal(currentPrice.toString()));
                }

                Object costPrice = changeData.get("costPrice");
                if (costPrice != null) {
                    existingPrice.setCostPrice(new BigDecimal(costPrice.toString()));
                }

                Object effectiveDate = changeData.get("effectiveDate");
                if (effectiveDate != null) {
                    existingPrice.setEffectiveDate(LocalDate.parse(effectiveDate.toString()));
                }

                Object expiryDate = changeData.get("expiryDate");
                if (expiryDate != null) {
                    existingPrice.setExpiryDate(LocalDate.parse(expiryDate.toString()));
                }

                Object unit = changeData.get("unit");
                if (unit != null) {
                    existingPrice.setUnit((String) unit);
                }

                Object priceSpec = changeData.get("priceSpec");
                if (priceSpec != null) {
                    existingPrice.setPriceSpec((String) priceSpec);
                }

                Price updatedPrice = priceRepository.save(existingPrice);

                // 记录价格历史
                PriceHistory history = new PriceHistory();
                history.setPriceId(updatedPrice.getId());
                history.setProductId(productId);
                history.setOldPrice(oldPrice);
                history.setNewPrice(updatedPrice.getCurrentPrice());
                history.setChangeType(PriceHistory.ChangeType.UPDATE);
                history.setRemark("审批通过 - 更新价格");
                priceHistoryRepository.save(history);

                log.info("Price updated via approval: priceId={}, productId={}", updatedPrice.getId(), productId);
            }

        } catch (Exception e) {
            log.error("Failed to execute price change for request {}: {}", request.getId(), e.getMessage(), e);
            throw new RuntimeException("执行价格变更失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行产品变更
     */
    private void executeProductChange(ApprovalRequest request) {
        // 产品创建/编辑业务逻辑暂未实现
        log.info("Executing product change for request {}, businessId={}", request.getId(), request.getBusinessId());
    }

}
