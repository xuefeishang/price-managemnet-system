/**
 * 用户状态管理 Store
 * 管理用户登录状态、Token、用户信息、权限等
 *
 * 功能说明：
 * - 登录/登出：loginAction, logoutAction
 * - Token 管理：token, refreshTokenValue, refreshAccessToken
 * - 用户信息：user, fetchProfile
 * - 权限判断：isAdmin, canEdit, getUserRole, hasPermission
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
  /** 用户权限列表（从后端动态获取） */
  const permissions = ref<Set<string>>(new Set())
  /** 是否已认证（基于 token 是否存在） */
  const isAuthenticated = computed(() => !!token.value)

  const normalizeUser = (userData: any): User => ({
    id: userData.id || userData.userId,
    username: userData.username,
    nickname: userData.nickname,
    role: userData.role,
    roles: userData.roles || [userData.role],
    status: userData.status || 'ACTIVE',
    email: userData.email || '',
    phone: userData.phone || '',
    createdTime: userData.createdTime || '',
    updatedTime: userData.updatedTime || '',
    employeeId: userData.employeeId,
    department: userData.department,
    deptId: userData.deptId,
    loginType: userData.loginType,
    wechatOpenid: userData.wechatOpenid,
    wechatNickname: userData.wechatNickname,
    wechatAvatar: userData.wechatAvatar,
    lastLoginTime: userData.lastLoginTime,
    lastLoginIp: userData.lastLoginIp,
    loginCount: userData.loginCount,
    passwordUpdatedTime: userData.passwordUpdatedTime,
    isLocked: userData.locked ?? userData.isLocked
  })

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
      user.value = normalizeUser(userData)
      // 设置权限列表
      if (responseData.permissions) {
        permissions.value = new Set(responseData.permissions)
      }
      return true
    } catch (error: any) {
      // 仅开发环境打印日志
      if (import.meta.env.DEV) {
        console.error('Login failed:', error)
      }
      // 从 error.message 或 error.response.data.message 获取错误信息
      const message = error.message || error?.response?.data?.message || '登录失败，请稍后重试'
      throw new Error(message)
    }
  }

  /**
   * 用户登出
   * 调用后端登出接口并清除本地状态
   * @param callApi 是否调用后端登出API（默认true）
   */
  const logoutAction = async (callApi: boolean = true) => {
    if (callApi) {
      try {
        await logoutApi()
      } catch (error) {
        console.error('Logout API failed:', error)
      }
    }
    // 清除本地状态
    user.value = null
    token.value = null
    refreshTokenValue.value = null
    permissions.value = new Set()
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
      // 刷新失败，清除本地状态（不调用API，避免循环）
      await logoutAction(false)
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
    const responseData = response.data as any
    const userData = responseData.user || responseData
    user.value = normalizeUser(userData)
    if (responseData.permissions) {
      permissions.value = new Set(responseData.permissions)
    }
  }

  // ==================== 权限计算 ====================

  /** 获取用户角色 */
  const getUserRole = computed(() => user.value?.role)

  /** 获取用户所有角色 */
  const getUserRoles = computed(() => user.value?.roles || [user.value?.role])

  /** 判断用户是否是管理员 */
  const isAdmin = computed(() => user.value?.role === 'ADMIN' || user.value?.roles?.includes('ADMIN'))

  /** 判断用户是否有编辑权限（ADMIN 或 EDITOR） */
  const canEdit = computed(() => {
    if (!user.value) return false
    if (user.value.role === 'ADMIN' || user.value.role === 'EDITOR') return true
    return user.value.roles?.some(r => r === 'ADMIN' || r === 'EDITOR') ?? false
  })

  /**
   * 判断用户是否有指定角色
   */
  const hasRole = (role: string): boolean => {
    if (!user.value) return false
    if (user.value.role === role) return true
    return user.value.roles?.includes(role as any) ?? false
  }

  /**
   * 检查用户是否有指定权限（动态）
   * @param permissionCode 权限编码
   */
  const hasPermission = (permissionCode: string): boolean => {
    // 管理员拥有所有权限
    if (user.value?.role === 'ADMIN') return true
    // 检查权限列表
    return permissions.value.has(permissionCode)
  }

  // ==================== 导出 ====================

  return {
    user,
    token,
    refreshTokenValue,
    permissions,
    isAuthenticated,
    loginAction,
    logoutAction,
    refreshAccessToken,
    fetchProfile,
    getUserRole,
    getUserRoles,
    isAdmin,
    canEdit,
    hasPermission,
    hasRole
  }
})
