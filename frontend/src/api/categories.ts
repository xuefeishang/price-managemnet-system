
import http from '@/utils/http'
import type { ApiResponse, ProductCategory } from '@/types'

// 获取分类列表
export const getCategories = async (status?: string): Promise<ApiResponse<ProductCategory[]>> => {
  const params = status ? { status } : {}
  return await http.get('/api/categories', { params })
}

// 获取分类详情
export const getCategory = async (id: number): Promise<ApiResponse<ProductCategory>> => {
  return await http.get(`/api/categories/${id}`)
}

// 创建分类
export const createCategory = async (data: ProductCategory): Promise<ApiResponse<ProductCategory>> => {
  return await http.post('/api/categories', data)
}

// 更新分类
export const updateCategory = async (id: number, data: Partial<ProductCategory>): Promise<ApiResponse<ProductCategory>> => {
  return await http.put(`/api/categories/${id}`, data)
}

// 删除分类
export const deleteCategory = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/categories/${id}`)
}
