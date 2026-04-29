import http from '@/utils/http'
import type { ApiResponse, SysDict } from '@/types'

// 获取字典列表
export const getDicts = async (category?: string): Promise<ApiResponse<SysDict[]>> => {
  const params = category ? { category } : {}
  return await http.get('/api/dict', { params })
}

// 获取启用的字典列表
export const getActiveDicts = async (category: string): Promise<ApiResponse<SysDict[]>> => {
  return await http.get('/api/dict/active', { params: { category } })
}

// 获取字典分类列表
export const getDictCategories = async (all?: boolean): Promise<ApiResponse<string[]>> => {
  const params = all ? { all: true } : {}
  return await http.get('/api/dict/categories', { params })
}

// 获取字典详情
export const getDict = async (id: number): Promise<ApiResponse<SysDict>> => {
  return await http.get(`/api/dict/${id}`)
}

// 创建字典项
export const createDict = async (data: Omit<SysDict, 'id' | 'createdTime' | 'updatedTime'>): Promise<ApiResponse<SysDict>> => {
  return await http.post('/api/dict', data)
}

// 更新字典项
export const updateDict = async (id: number, data: Partial<SysDict>): Promise<ApiResponse<SysDict>> => {
  return await http.put(`/api/dict/${id}`, data)
}

// 删除字典项
export const deleteDict = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/dict/${id}`)
}

// 批量创建字典项
export const batchCreateDicts = async (data: Omit<SysDict, 'id' | 'createdTime' | 'updatedTime'>[]): Promise<ApiResponse<void>> => {
  return await http.post('/api/dict/batch', data)
}
