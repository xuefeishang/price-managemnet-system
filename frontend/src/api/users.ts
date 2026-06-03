
import http from '@/utils/http'
import type { ApiResponse, User, PageResponse } from '@/types'

// 获取用户列表（分页，仅管理员）
export const getUsers = async (params?: {
  page?: number
  size?: number
  keyword?: string
  role?: string
  status?: string
  deptId?: number
}): Promise<ApiResponse<PageResponse<User>>> => {
  return await http.get('/api/users', { params })
}

// 获取所有用户列表（不分页）
export const getAllUsers = async (): Promise<ApiResponse<User[]>> => {
  return await http.get('/api/users/all')
}

// 获取用户详情（仅管理员）
export const getUser = async (id: number): Promise<ApiResponse<User>> => {
  return await http.get(`/api/users/${id}`)
}

// 创建用户（仅管理员）
export interface CreateUserRequest {
  username: string
  password: string
  employeeId?: string
  role: 'ADMIN' | 'EDITOR' | 'VIEWER'
  nickname?: string
  email?: string
  phone?: string
  department?: string
  deptId?: number
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
  department?: string
  deptId?: number
  isLocked?: boolean
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
  const params = newPassword ? { newPassword } : {}
  return await http.post(`/api/users/${id}/reset-password`, undefined, { params })
}

// 锁定用户（仅管理员）
export const lockUser = async (id: number): Promise<ApiResponse<void>> => {
  return await http.post(`/api/users/${id}/lock`)
}

// 解锁用户（仅管理员）
export const unlockUser = async (id: number): Promise<ApiResponse<void>> => {
  return await http.post(`/api/users/${id}/unlock`)
}

// 获取用户的角色ID列表
export const getUserRoles = async (id: number): Promise<ApiResponse<number[]>> => {
  return await http.get(`/api/users/${id}/roles`)
}

// 批量获取多个用户的角色ID映射
export const getUserRolesBatch = async (ids: number[]): Promise<ApiResponse<Record<number, number[]>>> => {
  return await http.get(`/api/users/roles-batch`, { params: { ids: ids.join(',') } })
}

// 为用户分配角色
export const assignUserRoles = async (id: number, roleIds: number[]): Promise<ApiResponse<void>> => {
  return await http.post(`/api/roles/assign/${id}`, roleIds)
}

// ==================== 用户导入导出 ====================

// 导入用户
export const importUsers = async (formData: FormData): Promise<ApiResponse<{ successCount: number; skipCount: number; errors: string[] }>> => {
  return await http.post('/api/import/users', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 导出用户
export const exportUsers = async (): Promise<void> => {
  try {
    const response = await http.get('/api/import/users', {
      responseType: 'blob'
    })
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const a = document.createElement('a')
    a.href = url
    const filename = `用户列表_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.download = filename
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('导出失败:', error)
    throw error
  }
}

// 下载用户导入模板
export const downloadUserTemplate = async (): Promise<void> => {
  try {
    const response = await http.get('/api/import/users/template', {
      responseType: 'blob'
    })
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const a = document.createElement('a')
    a.href = url
    a.download = '用户导入模板.xlsx'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('模板下载失败:', error)
    throw error
  }
}
