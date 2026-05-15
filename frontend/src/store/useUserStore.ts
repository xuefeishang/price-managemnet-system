/**
 * 用户状态管理 Store
 * 管理用户登录状态、Token、用户信息等
 *
 * 功能说明：
 * - 登录/登出：loginAction, logoutAction
 * - Token 管理：token, refreshTokenValue, refreshAccessToken
 * - 用户信息：user, fetchProfile
 * - 权限判断：isAdmin, canEdit, getUserRole
 *
 * Token 存储策略：
 * - Access Token：短期有效（默认2小时），存储在 localStorage
 * - Refresh Token：长期有效（7天），存储在 localStorage
 * - 自动刷新：http.ts 拦截器在 401 时自动使用 refresh token 刷新
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest } from '@/types'
import { login, getProfile, logout as logoutApi, refreshToken as refreshTokenApi } from '@/api/auth'

/**
 * 用户 Store
 * 使用 Pinia Composition API 风格定义
 */
export const useUserStore = defineStore('user', () => {
  // ==================== 状态定义 ====================

  /** 当前登录用户信息 */
  const user = ref<User | null>(null)
  /** Access Token（用于 API 认证） */
  const token = ref<string | null>(localStorage.getItem('token'))
  /** Refresh Token（用于刷新 Access Token） */
  const refreshTokenValue = ref<string | null>(localStorage.getItem('refreshToken'))
  /** 是否已认证（基于 token 是否存在） */
  const isAuthenticated = computed(() => !!token.value)

  // ==================== 登录/登出 ====================

  /**
   * 用户登录
   * @param data 登录请求（用户名 + 密码）
   * @returns 登录是否成功
   */
  const loginAction = async (data: LoginRequest) => {
    try {
      const response = await login(data)
      // 新的响应格式包含 accessToken 和 refreshToken
      const responseData = response.data as any
      token.value = responseData.accessToken
      refreshTokenValue.value = responseData.refreshToken
      localStorage.setItem('token', responseData.accessToken)
      localStorage.setItem('refreshToken', responseData.refreshToken)
      // 直接使用登录响应数据设置用户信息
      const userData = responseData.user || responseData
      user.value = {
        id: userData.id || userData.userId,
        username: userData.username,
        nickname: userData.nickname,
        role: userData.role,
        status: 'ACTIVE',
        email: '',
        phone: '',
        createdTime: '',
        updatedTime: ''
      }
      return true
    } catch (error) {
      console.error('Login failed:', error)
      return false
    }
  }

  /**
   * 用户登出
   * 调用后端登出接口并清除本地状态
   */
  const logoutAction = async () => {
    try {
      await logoutApi()
    } catch (error) {
      console.error('Logout API failed:', error)
    }
    // 清除本地状态
    user.value = null
    token.value = null
    refreshTokenValue.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
  }

  // ==================== Token 管理 ====================

  /**
   * 刷新 Access Token
   * 使用 Refresh Token 获取新的 Access Token
   * @returns 新的 Access Token，失败返回 null
   */
  const refreshAccessToken = async (): Promise<string | null> => {
    if (!refreshTokenValue.value) {
      return null
    }

    try {
      const response = await refreshTokenApi({ refreshToken: refreshTokenValue.value })
      const { accessToken, refreshToken: newRefreshToken } = response.data
      token.value = accessToken
      refreshTokenValue.value = newRefreshToken
      localStorage.setItem('token', accessToken)
      localStorage.setItem('refreshToken', newRefreshToken)
      return accessToken
    } catch (error) {
      console.error('Token refresh failed:', error)
      // 刷新失败，清除状态
      await logoutAction()
      return null
    }
  }

  // ==================== 用户信息 ====================

  /**
   * 获取当前用户信息
   * 从后端 /api/auth/profile 获取完整用户信息
   */
  const fetchProfile = async () => {
    const response = await getProfile()
    user.value = response.data
  }

  // ==================== 权限计算 ====================

  /** 获取用户角色 */
  const getUserRole = computed(() => user.value?.role)

  /** 判断用户是否是管理员 */
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  /** 判断用户是否有编辑权限（ADMIN 或 EDITOR） */
  const canEdit = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'EDITOR')

  // ==================== 导出 ====================

  return {
    user,
    token,
    refreshTokenValue,
    isAuthenticated,
    loginAction,
    logoutAction,
    refreshAccessToken,
    fetchProfile,
    getUserRole,
    isAdmin,
    canEdit
  }
})
