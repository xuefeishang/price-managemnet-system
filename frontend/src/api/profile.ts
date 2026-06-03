import http from '@/utils/http'
import type { ApiResponse } from '@/types'
import type {
  Profile,
  ProfileLoginHistoryPage,
  ProfileOperationLogPage,
  ProfilePasswordChangeRequest,
  ProfilePreference,
  ProfileSecurity,
  ProfileSession,
  ProfileUpdateRequest
} from '@/types/profile'

const refreshTokenHeader = () => {
  const refreshToken = localStorage.getItem('refreshToken')
  return refreshToken ? { 'X-Refresh-Token': refreshToken } : {}
}

export const getProfileDetail = async (): Promise<ApiResponse<Profile>> => {
  return await http.get('/api/profile')
}

export const updateProfileDetail = async (data: ProfileUpdateRequest): Promise<ApiResponse<Profile>> => {
  return await http.put('/api/profile', data)
}

export const getProfileSecurity = async (): Promise<ApiResponse<ProfileSecurity>> => {
  return await http.get('/api/profile/security')
}

export const changeProfilePassword = async (data: ProfilePasswordChangeRequest): Promise<ApiResponse<void>> => {
  return await http.put('/api/profile/password', data)
}

export const getMyOperationLogs = async (params: {
  page?: number
  size?: number
  operationType?: string
  operationModule?: string
  keyword?: string
  startTime?: string
  endTime?: string
}): Promise<ApiResponse<ProfileOperationLogPage>> => {
  return await http.get('/api/profile/operation-logs', { params })
}

export const getProfileSessions = async (): Promise<ApiResponse<ProfileSession[]>> => {
  return await http.get('/api/profile/sessions', { headers: refreshTokenHeader() })
}

export const revokeProfileSession = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/profile/sessions/${id}`, { headers: refreshTokenHeader() })
}

export const revokeOtherProfileSessions = async (): Promise<ApiResponse<void>> => {
  return await http.delete('/api/profile/sessions/others', { headers: refreshTokenHeader() })
}

export const revokeAllProfileSessions = async (): Promise<ApiResponse<void>> => {
  return await http.delete('/api/profile/sessions/all')
}

export const getProfileLoginHistory = async (params: {
  page?: number
  size?: number
  result?: string
  startTime?: string
  endTime?: string
}): Promise<ApiResponse<ProfileLoginHistoryPage>> => {
  return await http.get('/api/profile/login-history', { params })
}

export const getProfilePreferences = async (): Promise<ApiResponse<ProfilePreference>> => {
  return await http.get('/api/profile/preferences')
}

export const updateProfilePreferences = async (data: Partial<ProfilePreference>): Promise<ApiResponse<ProfilePreference>> => {
  return await http.put('/api/profile/preferences', data)
}
