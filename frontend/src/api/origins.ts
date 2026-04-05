import http from '@/utils/http'
import type { ApiResponse, Origin } from '@/types'

// 获取产地列表
export const getOrigins = async (status?: string): Promise<ApiResponse<Origin[]>> => {
  const params = status ? { status } : {}
  return await http.get('/api/origins', { params })
}

// 获取产地详情
export const getOrigin = async (id: number): Promise<ApiResponse<Origin>> => {
  return await http.get(`/api/origins/${id}`)
}

// 创建产地
export const createOrigin = async (data: Omit<Origin, 'id' | 'createdTime' | 'updatedTime'>): Promise<ApiResponse<Origin>> => {
  return await http.post('/api/origins', data)
}

// 更新产地
export const updateOrigin = async (id: number, data: Partial<Origin>): Promise<ApiResponse<Origin>> => {
  return await http.put(`/api/origins/${id}`, data)
}

// 删除产地
export const deleteOrigin = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/origins/${id}`)
}
