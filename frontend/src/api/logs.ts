import http from '@/utils/http'
import type { ApiResponse } from '@/types'

export interface OperationLog {
  id: number
  username: string
  operationModule: string
  operationType: string
  operationDesc: string
  ipAddress: string
  status: string
  errorMsg: string
  createdTime: string
}

export interface LogPageResponse {
  content: OperationLog[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface LogStatistics {
  totalOperations: number
  loginCount: number
  activeUsers: number
  operationTypeCount: Record<string, number>
  moduleCount: Record<string, number>
  userActivities: { username: string; operationCount: number }[]
  dailyCount: Record<string, number>
}

export interface MonthlyReport {
  year: number
  month: number
  statistics: LogStatistics
  dailyReports: {
    date: string
    count: number
    typeStats: Record<string, number>
  }[]
}

export interface YearlyReport {
  year: number
  statistics: LogStatistics
  monthlyCount: Record<string, number>
  userRankings: {
    rank: number
    username: string
    count: number
  }[]
}

// 分页查询操作日志
export const getLogs = async (params: {
  page?: number
  size?: number
  username?: string
  operationType?: string
  operationModule?: string
  startTime?: string
  endTime?: string
}): Promise<ApiResponse<LogPageResponse>> => {
  return await http.get('/api/logs', { params })
}

// 获取最近的操作日志
export const getRecentLogs = async (limit?: number): Promise<ApiResponse<OperationLog[]>> => {
  return await http.get('/api/logs/recent', { params: { limit } })
}

// 根据用户ID查询操作日志
export const getLogsByUserId = async (userId: number, page?: number, size?: number): Promise<ApiResponse<LogPageResponse>> => {
  return await http.get(`/api/logs/user/${userId}`, { params: { page, size } })
}

// 根据操作类型查询日志
export const getLogsByType = async (operationType: string, page?: number, size?: number): Promise<ApiResponse<LogPageResponse>> => {
  return await http.get(`/api/logs/type/${operationType}`, { params: { page, size } })
}

// 获取日志统计信息
export const getLogStatistics = async (startTime?: string, endTime?: string): Promise<ApiResponse<LogStatistics>> => {
  return await http.get('/api/logs/statistics', { params: { startTime, endTime } })
}

// 获取月度报表
export const getMonthlyReport = async (year: number, month: number): Promise<ApiResponse<MonthlyReport>> => {
  return await http.get('/api/logs/reports/monthly', { params: { year, month } })
}

// 获取年度报表
export const getYearlyReport = async (year: number): Promise<ApiResponse<YearlyReport>> => {
  return await http.get('/api/logs/reports/yearly', { params: { year } })
}
