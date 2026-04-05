import http from '@/utils/http'
import type { ApiResponse, MenuItem } from '@/types'

// 获取菜单树
export const getMenuTree = async (): Promise<ApiResponse<MenuItem[]>> => {
  return await http.get('/api/menus/tree')
}

// 获取可见菜单（根据角色）
export const getVisibleMenus = async (role: string): Promise<ApiResponse<MenuItem[]>> => {
  return await http.get('/api/menus/visible', { params: { role } })
}

// 获取所有菜单（仅管理员）
export const getAllMenus = async (): Promise<ApiResponse<MenuItem[]>> => {
  return await http.get('/api/menus')
}

// 获取单个菜单
export const getMenu = async (id: number): Promise<ApiResponse<MenuItem>> => {
  return await http.get(`/api/menus/${id}`)
}

// 创建菜单
export const createMenu = async (data: MenuItem): Promise<ApiResponse<MenuItem>> => {
  return await http.post('/api/menus', data)
}

// 更新菜单
export const updateMenu = async (id: number, data: Partial<MenuItem>): Promise<ApiResponse<MenuItem>> => {
  return await http.put(`/api/menus/${id}`, data)
}

// 删除菜单
export const deleteMenu = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/menus/${id}`)
}

// 批量更新菜单排序
export const batchUpdateMenuSort = async (items: { id: number; parentId: number | null; sortOrder: number }[]): Promise<ApiResponse<void>> => {
  return await http.post('/api/menus/batch-sort', items)
}

// 初始化默认菜单
export const initDefaultMenus = async (): Promise<ApiResponse<void>> => {
  return await http.post('/api/menus/init')
}
