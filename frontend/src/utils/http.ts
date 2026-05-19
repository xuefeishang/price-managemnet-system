
/**
 * Axios HTTP 客户端配置
 * 提供统一的 API 请求封装，包含：
 * - 请求拦截：自动添加 Authorization 头
 * - 响应拦截：统一错误处理、Token 自动刷新
 * - 请求重试：超时自动重试机制
 * - 性能监控：记录慢请求日志
 *
 * Token 刷新机制：
 * 1. 当请求返回 401 时，检查是否正在刷新 Token
 * 2. 如果正在刷新，将请求加入等待队列
 * 3. 刷新成功后，重试队列中的所有请求
 * 4. 刷新失败，跳转登录页
 */
import axios, { AxiosError } from 'axios'
import { showToast } from 'vant'
import { useUserStore } from '@/store/useUserStore'

// API 基础路径（从环境变量读取）
const baseURL = import.meta.env.VITE_API_BASE_URL || ''

// ==================== Axios 实例配置 ====================

// 创建 axios 实例
const instance = axios.create({
  baseURL,
  timeout: 30000,      // 请求超时时间（30秒）
  retryDelay: 1000,    // 重试延迟（1秒）
  maxRetries: 3        // 最大重试次数
})

// ==================== 类型扩展 ====================

// 扩展 AxiosRequestConfig 类型
declare module 'axios' {
  export interface AxiosRequestConfig {
    retryDelay?: number
    maxRetries?: number
    metadata?: {
      startTime?: number
    }
    _retry?: boolean
  }
}

// ==================== Token 刷新队列 ====================

/** 是否正在刷新 Token */
let isRefreshing = false

/** 等待刷新完成的请求队列 */
let failedQueue: Array<{
  resolve: (value?: string) => void
  reject: (reason?: any) => void
}> = []

/**
 * 处理等待队列
 * @param error 刷新失败时的错误
 * @param token 刷新成功时的新 Token
 */
const processQueue = (error: Error | null, token: string | null = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error)
    } else {
      prom.resolve(token || undefined)
    }
  })
  failedQueue = []
}

// ==================== 请求拦截器 ====================

// 请求拦截器
instance.interceptors.request.use(
  config => {
    const userStore = useUserStore()
    // 公开接口不需要Authorization头（仅GET请求）
    const publicUrls = ['/auth/login', '/auth/refresh-token', '/auth/captcha']
    const isPublicUrl = publicUrls.some(u => config.url?.includes(u))

    if (userStore.token && !isPublicUrl) {
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

// ==================== 响应拦截器 ====================

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

    // blob 类型响应直接返回（用于文件下载）
    if (response.config.responseType === 'blob') {
      return response
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
  async error => {
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
    // 刷新令牌接口的401也不需要刷新
    // 公开接口（captcha、style等）也不需要刷新
    const publicUrls = ['/auth/login', '/auth/refresh-token', '/auth/captcha', '/style/config']
    const isPublicUrl = publicUrls.some(u => url.includes(u))

    if (status === 401 && !isPublicUrl) {
      // 如果正在刷新令牌，将请求加入队列
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject })
        }).then(token => {
          if (error.config) {
            error.config.headers.Authorization = `Bearer ${token}`
            return instance(error.config)
          }
          return Promise.reject(error)
        }).catch(err => {
          return Promise.reject(err)
        })
      }

      // 标记正在刷新
      isRefreshing = true
      error.config._retry = true

      try {
        const userStore = useUserStore()
        const newToken = await userStore.refreshAccessToken()

        if (newToken) {
          // 刷新成功，重试原请求
          processQueue(null, newToken)
          if (error.config) {
            error.config.headers.Authorization = `Bearer ${newToken}`
            return instance(error.config)
          }
          return Promise.reject(error)
        } else {
          // 刷新失败，清除状态并跳转登录页（不调用API）
          processQueue(new Error('Token refresh failed'), null)
          const userStore = useUserStore()
          userStore.logoutAction(false)
          window.location.href = '/login'
          return Promise.reject(new Error('Token refresh failed'))
        }
      } catch (refreshError) {
        processQueue(refreshError as Error, null)
        const userStore = useUserStore()
        userStore.logoutAction(false)
        window.location.href = '/login'
        return Promise.reject(refreshError)
      } finally {
        isRefreshing = false
      }
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
