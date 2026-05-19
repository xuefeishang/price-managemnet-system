/**
 * 客户相关 API
 */
import { get, post, put, del } from './request'
import type { Customer, ApiResponse } from '@/types'

// 获取客户列表
export const getCustomers = async (status?: string): Promise<ApiResponse<Customer[]>> => {
  const params = status ? { status } : {}
  return await get('/api/customers', params)
}

// 获取客户详情
export const getCustomer = async (id: number): Promise<ApiResponse<Customer>> => {
  return await get(`/api/customers/${id}`)
}

// 创建客户
export const createCustomer = async (data: Partial<Customer>): Promise<ApiResponse<Customer>> => {
  return await post('/api/customers', data)
}

// 更新客户
export const updateCustomer = async (id: number, data: Partial<Customer>): Promise<ApiResponse<Customer>> => {
  return await put(`/api/customers/${id}`, data)
}

// 删除客户
export const deleteCustomer = async (id: number): Promise<ApiResponse<void>> => {
  return await del(`/api/customers/${id}`)
}
