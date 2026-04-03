
import axios, { AxiosError } from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '@/store/useUserStore'

const baseURL = import.meta.env.VITE_API_BASE_URL || ''

// 创建 axios 实例
const instance = axios.create({
  baseURL,
  timeout: 30000,
  retryDelay: 1000,
  maxRetries: 3
})

// 扩展 AxiosRequestConfig 类型
declare module 'axios' {
  export interface AxiosRequestConfig {
    retryDelay?: number
    maxRetries?: number
    metadata?: {
      startTime?: number
    }
  }
}

// 请求拦截器
instance.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    // 设置请求开始时间
    config.metadata = { startTime: Date.now() }
    return config
  },
  error => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
instance.interceptors.response.use(
  response => {
    // 计算请求耗时
    const duration = response.config.metadata?.startTime
      ? Date.now() - response.config.metadata.startTime
      : 0
    if (duration > 3000) {
      console.debug(`请求耗时: ${duration}ms - ${response.config.url}`)
    }

    const data = response.data
    if (data.code === 200) {
      return data
    } else {
      // 业务错误
      const errorMsg = data.message || '请求失败'
      showToast({
        message: errorMsg,
        position: 'bottom'
      })
      return Promise.reject(new Error(errorMsg))
    }
  },
  error => {
    console.error('Response error:', error)
    const axiosError = error as AxiosError
    const url = error.config?.url || ''
    const status = error.response?.status

    // 提取错误消息
    const getErrorMessage = (): string => {
      if (error.response?.data) {
        const data = error.response.data as any
        return data.message || data.msg || '请求失败'
      }
      if (error.message) {
        return error.message
      }
      return '请求失败'
    }

    // 登录接口的401由业务逻辑处理，不弹全局弹窗
    // 其他401错误由调用方处理（如路由守卫会自动跳转登录页）
    if (status === 401 && !url.includes('/auth/login')) {
      // 不弹对话框，由路由守卫统一处理登出逻辑
      return Promise.reject(error)
    }

    if (status === 403) {
      showToast('您没有权限访问该资源')
    } else if (status === 404) {
      showToast('资源不存在')
    } else if (axiosError.code === 'ECONNABORTED') {
      showToast('请求超时，请稍后重试')
    } else if (status === 500) {
      showToast('服务器错误，请联系管理员')
    } else if (!status && !error.message) {
      showToast('网络连接失败，请检查网络')
    } else if (error.message && !status) {
      showToast(getErrorMessage())
    } else {
      showToast('网络错误，请稍后重试')
    }

    return Promise.reject(error)
  }
)

export default instance
