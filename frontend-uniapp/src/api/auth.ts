/**
 * 认证相关 API
 */
import { post, get } from './request'
import type { LoginRequest, LoginResponse, User, ApiResponse } from '@/types'

// 登录
export const login = async (data: LoginRequest): Promise<ApiResponse<LoginResponse>> => {
  return await post('/api/auth/login', data, { showLoading: false })
}

// 微信登录
export interface WechatLoginRequest {
  code: string
}

export const wechatLogin = async (data: WechatLoginRequest): Promise<ApiResponse<LoginResponse>> => {
  return await post('/api/auth/wechat-login', data)
}

// 登出
export const logout = async (): Promise<ApiResponse<void>> => {
  return await post('/api/auth/logout')
}

// 刷新令牌
export interface TokenRefreshRequest {
  refreshToken: string
}

export interface TokenRefreshResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export const refreshToken = async (data: TokenRefreshRequest): Promise<ApiResponse<TokenRefreshResponse>> => {
  return await post('/api/auth/refresh-token', data, { showLoading: false, showError: false })
}

// 获取用户信息
export const getProfile = async (): Promise<ApiResponse<User>> => {
  return await get('/api/auth/profile')
}

// 更新用户信息
export interface UpdateProfileRequest {
  nickname?: string
  email?: string
  phone?: string
}

export const updateProfile = async (data: UpdateProfileRequest): Promise<ApiResponse<User>> => {
  return await post('/api/auth/profile', data)
}

// 修改密码
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export const changePassword = async (data: ChangePasswordRequest): Promise<ApiResponse<void>> => {
  return await post('/api/auth/password', data)
}