import http from '@/utils/http'
import type { ApiResponse } from '@/types'
import type {
  ApiCallLogPage,
  ApiCallLogStatistics,
  ApiKey,
  ApiKeyCreateRequest,
  ApiKeyCreateResponse,
  ApiKeyPage,
  ApiKeyUpdateRequest,
  ExternalApiEndpoint,
  ExternalApiServiceStatus
} from '@/types/apiKey'

export const getApiKeys = async (params: {
  page?: number
  size?: number
  keyword?: string
  status?: string
  environment?: string
}): Promise<ApiResponse<ApiKeyPage>> => {
  return await http.get('/api/api-keys', { params })
}

export const createApiKey = async (data: ApiKeyCreateRequest): Promise<ApiResponse<ApiKeyCreateResponse>> => {
  return await http.post('/api/api-keys', data)
}

export const getApiKey = async (id: number): Promise<ApiResponse<ApiKey>> => {
  return await http.get(`/api/api-keys/${id}`)
}

export const updateApiKey = async (id: number, data: ApiKeyUpdateRequest): Promise<ApiResponse<ApiKey>> => {
  return await http.put(`/api/api-keys/${id}`, data)
}

export const enableApiKey = async (id: number): Promise<ApiResponse<ApiKey>> => {
  return await http.put(`/api/api-keys/${id}/enable`)
}

export const disableApiKey = async (id: number): Promise<ApiResponse<ApiKey>> => {
  return await http.put(`/api/api-keys/${id}/disable`)
}

export const revokeApiKey = async (id: number): Promise<ApiResponse<ApiKey>> => {
  return await http.put(`/api/api-keys/${id}/revoke`)
}

export const getApiPermissions = async (): Promise<ApiResponse<ExternalApiEndpoint[]>> => {
  return await http.get('/api/api-keys/permissions/tree')
}

export const getExternalApiServiceStatus = async (): Promise<ApiResponse<ExternalApiServiceStatus>> => {
  return await http.get('/api/api-keys/service-status')
}

export const updateExternalApiServiceStatus = async (enabled: boolean): Promise<ApiResponse<ExternalApiServiceStatus>> => {
  return await http.put('/api/api-keys/service-status', { enabled })
}

export const getApiCallLogs = async (params: {
  page?: number
  size?: number
  appId?: string
  authResult?: string
  statusCode?: number
  startTime?: string
  endTime?: string
}): Promise<ApiResponse<ApiCallLogPage>> => {
  return await http.get('/api/api-call-logs', { params })
}

export const getApiCallLogStatistics = async (params?: {
  startTime?: string
  endTime?: string
}): Promise<ApiResponse<ApiCallLogStatistics>> => {
  return await http.get('/api/api-call-logs/statistics', { params })
}
