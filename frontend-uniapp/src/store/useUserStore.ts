/**
 * 用户状态管理
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi, wechatLogin as wechatLoginApi, refreshToken as refreshTokenApi, getProfile as getProfileApi } from '@/api/auth'
import type { User, LoginRequest, LoginResponse } from '@/types'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref<string>('')
  const refreshTokenValue = ref<string>('')
  const user = ref<User | null>(null)

  // 计算属性
  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const canEdit = computed(() => ['ADMIN', 'EDITOR'].includes(user.value?.role || ''))

  // 从本地存储恢复状态
  const restoreSession = () => {
    try {
      token.value = uni.getStorageSync('token') || ''
      refreshTokenValue.value = uni.getStorageSync('refreshToken') || ''
      const userStr = uni.getStorageSync('user')
      if (userStr) {
        user.value = JSON.parse(userStr)
      }
    } catch (error) {
      console.error('恢复会话失败:', error)
    }
  }

  // 保存状态到本地存储
  const saveSession = (data: LoginResponse) => {
    token.value = data.accessToken
    refreshTokenValue.value = data.refreshToken
    user.value = data.user

    try {
      uni.setStorageSync('token', data.accessToken)
      uni.setStorageSync('refreshToken', data.refreshToken)
      uni.setStorageSync('user', JSON.stringify(data.user))
    } catch (error) {
      console.error('保存会话失败:', error)
    }
  }

  // 账号密码登录
  const loginAction = async (credentials: LoginRequest): Promise<boolean> => {
    try {
      const res = await loginApi(credentials)
      if (res.code === 200 && res.data) {
        saveSession(res.data)
        return true
      }
      return false
    } catch (error) {
      console.error('登录失败:', error)
      return false
    }
  }

  // 微信登录
  const wechatLoginAction = async (code: string): Promise<boolean> => {
    try {
      const res = await wechatLoginApi({ code })
      if (res.code === 200 && res.data) {
        saveSession(res.data)
        return true
      }
      return false
    } catch (error) {
      console.error('微信登录失败:', error)
      return false
    }
  }

  // 刷新 Token
  const refreshAccessToken = async (): Promise<string | null> => {
    try {
      const res = await refreshTokenApi({ refreshToken: refreshTokenValue.value })
      if (res.code === 200 && res.data) {
        token.value = res.data.accessToken
        refreshTokenValue.value = res.data.refreshToken
        uni.setStorageSync('token', res.data.accessToken)
        uni.setStorageSync('refreshToken', res.data.refreshToken)
        return res.data.accessToken
      }
      return null
    } catch (error) {
      console.error('刷新Token失败:', error)
      return null
    }
  }

  // 登出
  const logoutAction = () => {
    token.value = ''
    refreshTokenValue.value = ''
    user.value = null
    try {
      uni.removeStorageSync('token')
      uni.removeStorageSync('refreshToken')
      uni.removeStorageSync('user')
    } catch (error) {
      console.error('清除会话失败:', error)
    }
  }

  // 获取用户信息
  const fetchProfile = async () => {
    try {
      const res = await getProfileApi()
      if (res.code === 200 && res.data) {
        user.value = res.data
        uni.setStorageSync('user', JSON.stringify(res.data))
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
      throw error
    }
  }

  return {
    // 状态
    token,
    refreshToken: refreshTokenValue,
    user,
    // 计算属性
    isAuthenticated,
    isAdmin,
    canEdit,
    // 方法
    restoreSession,
    saveSession,
    loginAction,
    wechatLoginAction,
    refreshAccessToken,
    logoutAction,
    fetchProfile
  }
})