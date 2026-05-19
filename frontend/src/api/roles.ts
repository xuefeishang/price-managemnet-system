import http from '@/utils/http'
import type { ApiResponse, SysRole } from '@/types'

// 获取所有角色列表
export const getRoles = async (): Promise<ApiResponse<SysRole[]>> => {
  return await http.get('/api/roles')
}

// 获取启用的角色列表
export const getActiveRoles = async (): Promise<ApiResponse<SysRole[]>> => {
  return await http.get('/api/roles/active')
}

// 获取角色详情
export const getRole = async (id: number): Promise<ApiResponse<SysRole>> => {
  return await http.get(`/api/roles/${id}`)
}

// 创建角色
export const createRole = async (data: Partial<SysRole>): Promise<ApiResponse<SysRole>> => {
  return await http.post('/api/roles', data)
}

// 更新角色
export const updateRole = async (id: number, data: Partial<SysRole>): Promise<ApiResponse<SysRole>> => {
  return await http.put(`/api/roles/${id}`, data)
}

// 删除角色
export const deleteRole = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/roles/${id}`)
}

// 获取角色的权限ID列表
export const getRolePermissionIds = async (roleId: number): Promise<ApiResponse<number[]>> => {
  return await http.get(`/api/roles/${roleId}/permissions`)
}

// 为角色分配权限
export const assignRolePermissions = async (roleId: number, permissionIds: number[]): Promise<ApiResponse<void>> => {
  return await http.put(`/api/roles/${roleId}/permissions`, permissionIds)
}

// 为用户分配角色
export const assignUserRoles = async (userId: number, roleIds: number[]): Promise<ApiResponse<void>> => {
  return await http.post(`/api/roles/assign/${userId}`, roleIds)
}
