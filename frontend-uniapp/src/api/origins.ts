/**
 * 产地相关 API
 */
import { get, post, put, del } from './request'
import type { Origin, ApiResponse } from '@/types'

// 获取产地列表
export const getOrigins = async (status?: string): Promise<ApiResponse<Origin[]>> => {
  const params = status ? { status } : {}
  return await get('/api/origins', params)
}

// 获取产地详情
export const getOrigin = async (id: number): Promise<ApiResponse<Origin>> => {
  return await get(`/api/origins/${id}`)
}

// 创建产地
export const createOrigin = async (data: Partial<Origin>): Promise<ApiResponse<Origin>> => {
  return await post('/api/origins', data)
}

// 更新产地
export const updateOrigin = async (id: number, data: Partial<Origin>): Promise<ApiResponse<Origin>> => {
  return await put(`/api/origins/${id}`, data)
}

// 删除产地
export const deleteOrigin = async (id: number): Promise<ApiResponse<void>> => {
  return await del(`/api/origins/${id}`)
}
