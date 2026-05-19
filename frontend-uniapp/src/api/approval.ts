/**
 * 审批相关 API
 */
import { get, post, put } from './request'
import type { ApiResponse } from '@/types'

// 审批请求类型
export interface ApprovalRequest {
  id?: number
  workflowId: number
  businessType: 'PRICE' | 'PRODUCT'
  businessId: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'
  applicantId: number
  createdTime?: string
  updatedTime?: string
}

export interface ApprovalPageResponse {
  content: ApprovalRequest[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// 获取待我审批的请求
export const getPendingApprovals = async (page = 0, size = 20): Promise<ApiResponse<ApprovalPageResponse>> => {
  return await get('/api/approvals/requests/pending', { page, size })
}

// 获取我提交的审批请求
export const getMyRequests = async (page = 0, size = 20): Promise<ApiResponse<ApprovalPageResponse>> => {
  return await get('/api/approvals/requests/my', { page, size })
}

// 审批通过
export const approveRequest = async (id: number, comment?: string): Promise<ApiResponse<ApprovalRequest>> => {
  return await put(`/api/approvals/requests/${id}/approve`, { comment })
}

// 审批拒绝
export const rejectRequest = async (id: number, comment?: string): Promise<ApiResponse<ApprovalRequest>> => {
  return await put(`/api/approvals/requests/${id}/reject`, { comment })
}

// 撤回审批请求
export const cancelRequest = async (id: number): Promise<ApiResponse<ApprovalRequest>> => {
  return await put(`/api/approvals/requests/${id}/cancel`)
}
