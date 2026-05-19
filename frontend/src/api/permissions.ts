import http from '@/utils/http'
import type { ApiResponse, SysPermission } from '@/types'

// 获取所有权限列表
export const getPermissions = async (): Promise<ApiResponse<SysPermission[]>> => {
  return await http.get('/api/permissions')
}

// 获取权限树
export const getPermissionTree = async (): Promise<ApiResponse<SysPermission[]>> => {
  return await http.get('/api/permissions/tree')
}

// 获取角色的权限ID列表
export const getRolePermissions = async (roleId: number): Promise<ApiResponse<number[]>> => {
  return await http.get(`/api/roles/${roleId}/permissions`)
}

// 为角色分配权限
export const assignRolePermissions = async (roleId: number, permissionIds: number[]): Promise<ApiResponse<void>> => {
  return await http.put(`/api/roles/${roleId}/permissions`, permissionIds)
}

// 获取用户的权限列表
export const getUserPermissions = async (userId: number): Promise<ApiResponse<string[]>> => {
  return await http.get(`/api/users/${userId}/permissions`)
}
