import http from '@/utils/http'
import type { ApiResponse, PageResponse, ScheduledTask, ScheduledTaskLog } from '@/types'

export const getScheduledTasks = async (params?: { page?: number; size?: number }): Promise<ApiResponse<PageResponse<ScheduledTask>>> => {
  return await http.get('/api/scheduled-tasks', { params })
}

export const getScheduledTask = async (id: number): Promise<ApiResponse<ScheduledTask>> => {
  return await http.get(`/api/scheduled-tasks/${id}`)
}

export const createScheduledTask = async (data: ScheduledTask): Promise<ApiResponse<ScheduledTask>> => {
  return await http.post('/api/scheduled-tasks', data)
}

export const updateScheduledTask = async (id: number, data: ScheduledTask): Promise<ApiResponse<ScheduledTask>> => {
  return await http.put(`/api/scheduled-tasks/${id}`, data)
}

export const enableScheduledTask = async (id: number): Promise<ApiResponse<ScheduledTask>> => {
  return await http.post(`/api/scheduled-tasks/${id}/enable`)
}

export const disableScheduledTask = async (id: number): Promise<ApiResponse<ScheduledTask>> => {
  return await http.post(`/api/scheduled-tasks/${id}/disable`)
}

export const runScheduledTaskOnce = async (id: number): Promise<ApiResponse<ScheduledTaskLog>> => {
  return await http.post(`/api/scheduled-tasks/${id}/run-once`)
}

export const getScheduledTaskLogs = async (
  id: number,
  params?: { page?: number; size?: number }
): Promise<ApiResponse<PageResponse<ScheduledTaskLog>>> => {
  return await http.get(`/api/scheduled-tasks/${id}/logs`, { params })
}
