import http from '@/utils/http'
import type { ApiResponse } from '@/types'

// ============ 审批工作流相关类型 ============

export interface ApprovalWorkflow {
  id?: number
  workflowName: string
  workflowType: 'PRICE_CHANGE' | 'PRODUCT_CREATE'
  approvalLevel: number
  isActive: boolean
  createdTime?: string
  updatedTime?: string
}

export interface ApprovalNode {
  id?: number
  workflowId: number
  nodeOrder: number
  nodeType: 'APPROVER' | 'NOTIFIER'
  approverRole: string
  isRequired: boolean
  createdTime?: string
}

export interface ApprovalRequest {
  id?: number
  workflowId: number
  businessType: 'PRICE' | 'PRODUCT'
  businessId: number
  currentNodeId?: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  applicantId: number
  createdTime?: string
  updatedTime?: string
}

export interface ApprovalRecord {
  id?: number
  requestId: number
  nodeId: number
  approverId?: number
  action: 'APPROVE' | 'REJECT'
  comment?: string
  oldValue?: string
  newValue?: string
  actionTime?: string
}

export interface ApprovalPageResponse {
  content: ApprovalRequest[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// ============ 工作流管理 API ============

// 获取所有工作流
export const getWorkflows = async (): Promise<ApiResponse<ApprovalWorkflow[]>> => {
  return await http.get('/api/approvals/workflows')
}

// 获取激活的工作流
export const getActiveWorkflows = async (): Promise<ApiResponse<ApprovalWorkflow[]>> => {
  return await http.get('/api/approvals/workflows/active')
}

// 获取工作流详情
export const getWorkflow = async (id: number): Promise<ApiResponse<ApprovalWorkflow>> => {
  return await http.get(`/api/approvals/workflows/${id}`)
}

// 创建工作流
export const createWorkflow = async (workflow: ApprovalWorkflow): Promise<ApiResponse<ApprovalWorkflow>> => {
  return await http.post('/api/approvals/workflows', workflow)
}

// 更新工作流
export const updateWorkflow = async (id: number, workflow: ApprovalWorkflow): Promise<ApiResponse<ApprovalWorkflow>> => {
  return await http.put(`/api/approvals/workflows/${id}`, workflow)
}

// 删除工作流
export const deleteWorkflow = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/approvals/workflows/${id}`)
}

// 激活工作流
export const activateWorkflow = async (id: number): Promise<ApiResponse<ApprovalWorkflow>> => {
  return await http.put(`/api/approvals/workflows/${id}/activate`)
}

// 停用工作流
export const deactivateWorkflow = async (id: number): Promise<ApiResponse<ApprovalWorkflow>> => {
  return await http.put(`/api/approvals/workflows/${id}/deactivate`)
}

// ============ 节点管理 API ============

// 获取工作流的节点列表
export const getWorkflowNodes = async (workflowId: number): Promise<ApiResponse<ApprovalNode[]>> => {
  return await http.get(`/api/approvals/workflows/${workflowId}/nodes`)
}

// 添加审批节点
export const addWorkflowNode = async (workflowId: number, node: ApprovalNode): Promise<ApiResponse<ApprovalNode>> => {
  return await http.post(`/api/approvals/workflows/${workflowId}/nodes`, node)
}

// 更新审批节点
export const updateWorkflowNode = async (id: number, node: ApprovalNode): Promise<ApiResponse<ApprovalNode>> => {
  return await http.put(`/api/approvals/nodes/${id}`, node)
}

// 删除审批节点
export const deleteWorkflowNode = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/approvals/nodes/${id}`)
}

// ============ 审批请求管理 API ============

// 分页查询审批请求
export const getRequests = async (params: {
  page?: number
  size?: number
  status?: string
  businessType?: string
  applicantId?: number
}): Promise<ApiResponse<ApprovalPageResponse>> => {
  return await http.get('/api/approvals/requests', { params })
}

// 获取待我审批的请求
export const getPendingApprovals = async (page?: number, size?: number): Promise<ApiResponse<ApprovalPageResponse>> => {
  return await http.get('/api/approvals/requests/pending', { params: { page, size } })
}

// 获取我提交的审批请求
export const getMyRequests = async (page?: number, size?: number): Promise<ApiResponse<ApprovalPageResponse>> => {
  return await http.get('/api/approvals/requests/my', { params: { page, size } })
}

// 获取审批请求详情
export const getRequest = async (id: number): Promise<ApiResponse<ApprovalRequest>> => {
  return await http.get(`/api/approvals/requests/${id}`)
}

// 创建审批请求
export const createRequest = async (request: Partial<ApprovalRequest>): Promise<ApiResponse<ApprovalRequest>> => {
  return await http.post('/api/approvals/requests', request)
}

// 审批通过
export const approveRequest = async (id: number, comment?: string): Promise<ApiResponse<ApprovalRequest>> => {
  return await http.put(`/api/approvals/requests/${id}/approve`, null, { params: { comment } })
}

// 审批拒绝
export const rejectRequest = async (id: number, comment?: string): Promise<ApiResponse<ApprovalRequest>> => {
  return await http.put(`/api/approvals/requests/${id}/reject`, null, { params: { comment } })
}

// 撤回审批请求
export const cancelRequest = async (id: number): Promise<ApiResponse<ApprovalRequest>> => {
  return await http.put(`/api/approvals/requests/${id}/cancel`)
}

// 获取审批记录
export const getRequestRecords = async (requestId: number): Promise<ApiResponse<ApprovalRecord[]>> => {
  return await http.get(`/api/approvals/requests/${requestId}/records`)
}