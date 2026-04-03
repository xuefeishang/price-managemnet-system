
import http from '@/utils/http'
import type { ApiResponse, User } from '@/types'

// 获取用户列表（仅管理员）
export const getUsers = async (): Promise<ApiResponse<User[]>> => {
  return await http.get('/api/users')
}

// 获取用户详情（仅管理员）
export const getUser = async (id: number): Promise<ApiResponse<User>> => {
  return await http.get(`/api/users/${id}`)
}

// 创建用户（仅管理员）
export interface CreateUserRequest {
  username: string
  password: string
  role: 'ADMIN' | 'EDITOR' | 'VIEWER'
  nickname?: string
  email?: string
  phone?: string
}

export const createUser = async (data: CreateUserRequest): Promise<ApiResponse<User>> => {
  return await http.post('/api/users', data)
}

// 更新用户（仅管理员）
export interface UpdateUserRequest {
  nickname?: string
  email?: string
  phone?: string
  role?: 'ADMIN' | 'EDITOR' | 'VIEWER'
  status?: 'ACTIVE' | 'INACTIVE'
}

export const updateUser = async (id: number, data: UpdateUserRequest): Promise<ApiResponse<User>> => {
  return await http.put(`/api/users/${id}`, data)
}

// 删除用户（仅管理员）
export const deleteUser = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/users/${id}`)
}

// 重置用户密码（仅管理员）
export const resetUserPassword = async (id: number, newPassword?: string): Promise<ApiResponse<void>> => {
  return await http.post(`/api/users/${id}/reset-password${newPassword ? `?newPassword=${newPassword}` : ''}`)
}
