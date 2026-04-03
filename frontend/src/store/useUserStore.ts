
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, LoginRequest } from '@/types'
import { login, getProfile } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  // 状态
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))
  const isAuthenticated = computed(() => !!token.value)

  // 登录
  const loginAction = async (data: LoginRequest) => {
    try {
      const response = await login(data)
      token.value = response.data.token
      localStorage.setItem('token', response.data.token)
      // 直接使用登录响应数据设置用户信息
      user.value = {
        id: response.data.userId,
        username: response.data.username,
        nickname: response.data.nickname,
        role: response.data.role,
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

  // 退出登录
  const logoutAction = async () => {
    // JWT是无状态的，不需要调用服务端logout接口
    // 直接清除本地状态即可
    user.value = null
    token.value = null
    localStorage.removeItem('token')
  }

  // 获取用户信息
  const fetchProfile = async () => {
    const response = await getProfile()
    user.value = response.data
  }

  // 获取用户角色
  const getUserRole = computed(() => user.value?.role)

  // 判断用户是否是管理员
  const isAdmin = computed(() => user.value?.role === 'ADMIN')

  // 判断用户是否有编辑权限
  const canEdit = computed(() => user.value?.role === 'ADMIN' || user.value?.role === 'EDITOR')

  return {
    user,
    token,
    isAuthenticated,
    loginAction,
    logoutAction,
    fetchProfile,
    getUserRole,
    isAdmin,
    canEdit
  }
})
