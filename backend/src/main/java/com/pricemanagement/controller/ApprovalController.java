package com.pricemanagement.controller;

import com.pricemanagement.dto.Result;
import com.pricemanagement.entity.*;
import com.pricemanagement.service.ApprovalService;
import com.pricemanagement.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    // ==================== 工作流管理 ====================

    /**
     * 获取所有审批工作流
     */
    @GetMapping("/workflows")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<ApprovalWorkflow>> getWorkflows() {
        List<ApprovalWorkflow> workflows = approvalService.getAllWorkflows();
        return Result.success("获取工作流列表成功", workflows);
    }

    /**
     * 获取激活的工作流
     */
    @GetMapping("/workflows/active")
    public Result<List<ApprovalWorkflow>> getActiveWorkflows() {
        List<ApprovalWorkflow> workflows = approvalService.getActiveWorkflows();
        return Result.success("获取激活工作流成功", workflows);
    }

    /**
     * 获取工作流详情
     */
    @GetMapping("/workflows/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ApprovalWorkflow> getWorkflow(@PathVariable Long id) {
        return approvalService.getWorkflowById(id)
                .map(w -> Result.success("获取工作流成功", w))
                .orElse(Result.error(404, "工作流不存在"));
    }

    /**
     * 创建审批工作流
     */
    @PostMapping("/workflows")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ApprovalWorkflow> createWorkflow(@RequestBody ApprovalWorkflow workflow) {
        try {
            ApprovalWorkflow saved = approvalService.createWorkflow(workflow);
            return Result.success("创建工作流成功", saved);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新审批工作流
     */
    @PutMapping("/workflows/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ApprovalWorkflow> updateWorkflow(@PathVariable Long id, @RequestBody ApprovalWorkflow workflow) {
        try {
            ApprovalWorkflow updated = approvalService.updateWorkflow(id, workflow);
            return Result.success("更新工作流成功", updated);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除审批工作流
     */
    @DeleteMapping("/workflows/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteWorkflow(@PathVariable Long id) {
        try {
            approvalService.deleteWorkflow(id);
            return Result.success("删除工作流成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 激活工作流
     */
    @PutMapping("/workflows/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ApprovalWorkflow> activateWorkflow(@PathVariable Long id) {
        try {
            ApprovalWorkflow workflow = approvalService.activateWorkflow(id);
            return Result.success("激活工作流成功", workflow);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 停用工作流
     */
    @PutMapping("/workflows/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ApprovalWorkflow> deactivateWorkflow(@PathVariable Long id) {
        try {
            ApprovalWorkflow workflow = approvalService.deactivateWorkflow(id);
            return Result.success("停用工作流成功", workflow);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ==================== 节点管理 ====================

    /**
     * 获取工作流的节点列表
     */
    @GetMapping("/workflows/{workflowId}/nodes")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<ApprovalNode>> getWorkflowNodes(@PathVariable Long workflowId) {
        List<ApprovalNode> nodes = approvalService.getWorkflowNodes(workflowId);
        return Result.success("获取节点列表成功", nodes);
    }

    /**
     * 添加审批节点
     */
    @PostMapping("/workflows/{workflowId}/nodes")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ApprovalNode> addNode(@PathVariable Long workflowId, @RequestBody ApprovalNode node) {
        try {
            node.setWorkflowId(workflowId);
            ApprovalNode saved = approvalService.addWorkflowNode(node);
            return Result.success("添加节点成功", saved);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 更新审批节点
     */
    @PutMapping("/nodes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ApprovalNode> updateNode(@PathVariable Long id, @RequestBody ApprovalNode node) {
        try {
            ApprovalNode updated = approvalService.updateNode(id, node);
            return Result.success("更新节点成功", updated);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除审批节点
     */
    @DeleteMapping("/nodes/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> deleteNode(@PathVariable Long id) {
        try {
            approvalService.deleteNode(id);
            return Result.success("删除节点成功");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ==================== 审批请求管理 ====================

    /**
     * 分页查询审批请求
     */
    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Page<ApprovalRequest>> getRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) ApprovalRequest.RequestStatus status,
            @RequestParam(required = false) ApprovalRequest.BusinessType businessType,
            @RequestParam(required = false) Long applicantId) {
        Page<ApprovalRequest> requests = approvalService.getRequests(page, size, status, businessType, applicantId);
        return Result.success("获取审批请求成功", requests);
    }

    /**
     * 获取待我审批的请求
     */
    @GetMapping("/requests/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Page<ApprovalRequest>> getPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        String role = SecurityUtils.getCurrentUserRole();
        Page<ApprovalRequest> requests = approvalService.getPendingApprovals(role, page, size);
        return Result.success("获取待审批请求成功", requests);
    }

    /**
     * 获取我提交的审批请求
     */
    @GetMapping("/requests/my")
    @PreAuthorize("isAuthenticated()")
    public Result<Page<ApprovalRequest>> getMyRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        Page<ApprovalRequest> requests = approvalService.getRequests(page, size, null, null, userId);
        return Result.success("获取我的审批请求成功", requests);
    }

    /**
     * 获取审批请求详情
     */
    @GetMapping("/requests/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<ApprovalRequest> getRequest(@PathVariable Long id) {
        return approvalService.getRequestById(id)
                .map(r -> Result.success("获取审批请求成功", r))
                .orElse(Result.error(404, "审批请求不存在"));
    }

    /**
     * 创建审批请求
     */
    @PostMapping("/requests")
    @PreAuthorize("isAuthenticated()")
    public Result<ApprovalRequest> createRequest(@RequestBody ApprovalRequest request) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            request.setApplicantId(userId);
            ApprovalRequest saved = approvalService.createRequest(request, null);
            return Result.success("创建审批请求成功", saved);
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("创建审批请求失败", e);
            return Result.error(500, "创建审批请求失败");
        }
    }

    /**
     * 审批通过
     */
    @PutMapping("/requests/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<ApprovalRequest> approve(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            ApprovalRequest updated = approvalService.approve(id, userId, comment);
            return Result.success("审批通过成功", updated);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("审批失败", e);
            return Result.error(500, "审批失败");
        }
    }

    /**
     * 审批拒绝
     */
    @PutMapping("/requests/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<ApprovalRequest> reject(
            @PathVariable Long id,
            @RequestParam(required = false) String comment) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            ApprovalRequest updated = approvalService.reject(id, userId, comment);
            return Result.success("审批拒绝成功", updated);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("审批失败", e);
            return Result.error(500, "审批失败");
        }
    }

    /**
     * 撤回审批请求
     */
    @PutMapping("/requests/{id}/cancel")
    @PreAuthorize("isAuthenticated()")
    public Result<ApprovalRequest> cancel(@PathVariable Long id) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            ApprovalRequest updated = approvalService.cancel(id, userId);
            return Result.success("撤回成功", updated);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("撤回失败", e);
            return Result.error(500, "撤回失败");
        }
    }

    /**
     * 获取审批记录
     */
    @GetMapping("/requests/{id}/records")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ApprovalRecord>> getRequestRecords(@PathVariable Long id) {
        List<ApprovalRecord> records = approvalService.getRequestRecords(id);
        return Result.success("获取审批记录成功", records);
    }
}
