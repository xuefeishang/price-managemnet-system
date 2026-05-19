import http from '@/utils/http'
import type { Department } from '@/types'

// 获取部门树
export const getDepartmentTree = () => {
  return http.get<any, { data: Department[] }>('/api/departments/tree')
}

// 获取部门列表（扁平）
export const getDepartments = () => {
  return http.get<any, { data: Department[] }>('/api/departments')
}

// 获取部门详情
export const getDepartment = (id: number) => {
  return http.get<any, { data: Department }>(`/api/departments/${id}`)
}

// 创建部门
export const createDepartment = (data: Partial<Department>) => {
  return http.post<any, { data: Department }>('/api/departments', data)
}

// 更新部门
export const updateDepartment = (id: number, data: Partial<Department>) => {
  return http.put<any, { data: Department }>(`/api/departments/${id}`, data)
}

// 移动部门
export const moveDepartment = (id: number, parentId: number | null) => {
  return http.put<any, { data: Department }>(`/api/departments/${id}/move`, null, {
    params: { parentId: parentId || '' }
  })
}

// 批量排序
export const batchSortDepartments = (orderedIds: number[]) => {
  return http.put<any, { data: void }>('/api/departments/sort', orderedIds)
}

// 删除部门
export const deleteDepartment = (id: number) => {
  return http.delete<any, { data: void }>(`/api/departments/${id}`)
}
