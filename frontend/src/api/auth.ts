
import http from '@/utils/http'
import type { LoginRequest, LoginResponse, ApiResponse, User } from '@/types'

// 登录
export const login = async (data: LoginRequest): Promise<ApiResponse<LoginResponse>> => {
  return await http.post('/api/auth/login', data)
}

// 登出
export const logout = async (): Promise<ApiResponse<void>> => {
  return await http.post('/api/auth/logout')
}

// 获取用户信息
export const getProfile = async (): Promise<ApiResponse<User>> => {
  return await http.get('/api/auth/profile')
}

// 更新用户信息
export interface UpdateProfileRequest {
  nickname?: string
  email?: string
  phone?: string
}

export const updateProfile = async (data: UpdateProfileRequest): Promise<ApiResponse<User>> => {
  return await http.put('/api/auth/profile', data)
}

// 修改密码
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export const changePassword = async (data: ChangePasswordRequest): Promise<ApiResponse<void>> => {
  return await http.put('/api/auth/password', data)
}
