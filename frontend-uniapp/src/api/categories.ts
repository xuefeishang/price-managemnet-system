/**
 * 分类相关 API
 */
import { get, post, put, del } from './request'
import type { ProductCategory, ApiResponse } from '@/types'

// 获取分类列表
export const getCategories = async (status?: string): Promise<ApiResponse<ProductCategory[]>> => {
  const params = status ? { status } : {}
  return await get('/api/categories', params)
}

// 获取分类详情
export const getCategory = async (id: number): Promise<ApiResponse<ProductCategory>> => {
  return await get(`/api/categories/${id}`)
}

// 创建分类
export const createCategory = async (data: Partial<ProductCategory>): Promise<ApiResponse<ProductCategory>> => {
  return await post('/api/categories', data)
}

// 更新分类
export const updateCategory = async (id: number, data: Partial<ProductCategory>): Promise<ApiResponse<ProductCategory>> => {
  return await put(`/api/categories/${id}`, data)
}

// 删除分类
export const deleteCategory = async (id: number): Promise<ApiResponse<void>> => {
  return await del(`/api/categories/${id}`)
}
