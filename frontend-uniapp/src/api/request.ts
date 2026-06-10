/**
 * uni-app 请求封装
 * 提供统一的 API 请求封装，包含：
 * - 请求拦截：自动添加 Authorization 头
 * - 响应拦截：统一错误处理、Token 自动刷新
 * - 加载提示：自动显示/隐藏加载动画
 */
import { useUserStore } from '@/store/useUserStore'
import type { ApiResponse } from '@/types'
import { getApiBaseUrl } from '@/utils/serverConfig'

// 请求配置
interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
  showLoading?: boolean
  showError?: boolean
}

class ApiRequestError extends Error {
  statusCode?: number
  response?: unknown

  constructor(message: string, statusCode?: number, response?: unknown) {
    super(message)
    this.name = 'ApiRequestError'
    this.statusCode = statusCode
    this.response = response
  }
}

// Token 刷新队列
let isRefreshing = false
let refreshQueue: Array<(token: string) => void> = []
let loadingCount = 0

const showRequestLoading = () => {
  loadingCount += 1
  if (loadingCount === 1) {
    uni.showLoading({ title: '加载中...', mask: true })
  }
}

const hideRequestLoading = () => {
  if (loadingCount <= 0) return
  loadingCount -= 1
  if (loadingCount === 0) {
    uni.hideLoading()
  }
}

const sanitizeQueryData = (data: any) => {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    return data
  }

  return Object.fromEntries(
    Object.entries(data).filter(([, value]) => value !== undefined && value !== null)
  )
}

/**
 * 统一请求方法
 */
export const request = async <T = any>(options: RequestOptions): Promise<ApiResponse<T>> => {
  const userStore = useUserStore()

  return new Promise((resolve, reject) => {
    // 显示加载提示
    if (options.showLoading !== false) {
      showRequestLoading()
    }

    const method = options.method || 'GET'
    const requestData = method === 'GET' ? sanitizeQueryData(options.data) : options.data
    const fullUrl = getApiBaseUrl() + options.url
    // 仅开发环境打印调试日志
    if (import.meta.env.DEV) {
      console.log('[API Request]', method, fullUrl, requestData)
    }

    uni.request({
      url: fullUrl,
      method,
      data: requestData,
      timeout: 30000,
      header: {
        'Content-Type': 'application/json',
        'Authorization': userStore.token ? `Bearer ${userStore.token}` : '',
        ...options.header
      },
      success: async (res) => {
        if (options.showLoading !== false) {
          hideRequestLoading()
        }

        const data = res.data as ApiResponse<T>

        if (import.meta.env.DEV) {
          console.log('[API Response]', method, fullUrl, res.statusCode, data)
        }
        // 小程序构建后 import.meta.env.DEV 可能被折叠为 false，本地联调仍保留关键响应日志。
        // #ifdef MP-WEIXIN
        if (!import.meta.env.DEV) {
          console.log('[API Response]', method, fullUrl, res.statusCode, data)
        }
        // #endif

        // 处理 401 Token 过期
        if (res.statusCode === 401 && !options.url.includes('/auth/login') && !options.url.includes('/auth/refresh-token')) {
          if (isRefreshing) {
            // 加入等待队列
            refreshQueue.push((token: string) => {
              options.header = { ...options.header, Authorization: `Bearer ${token}` }
              request(options).then(resolve).catch(reject)
            })
            return
          }

          isRefreshing = true
          try {
            const newToken = await userStore.refreshAccessToken()
            if (newToken) {
              // 重试队列中的请求
              refreshQueue.forEach(cb => cb(newToken))
              refreshQueue = []
              // 重试当前请求
              options.header = { ...options.header, Authorization: `Bearer ${newToken}` }
              request(options).then(resolve).catch(reject)
            } else {
              userStore.logoutAction()
              uni.reLaunch({ url: '/pages/login/index' })
              reject(new Error('Token刷新失败'))
            }
          } catch (error) {
            userStore.logoutAction()
            uni.reLaunch({ url: '/pages/login/index' })
            reject(error)
          } finally {
            isRefreshing = false
          }
          return
        }

        if (res.statusCode < 200 || res.statusCode >= 300) {
          const message = data?.message || `HTTP ${res.statusCode}`
          if (import.meta.env.DEV) {
            console.error('[API HTTP Error]', method, fullUrl, res.statusCode, res.data)
          }
          if (options.showError !== false) {
            uni.showToast({ title: message, icon: 'none' })
          }
          reject(new ApiRequestError(message, res.statusCode, res.data))
        } else if (data.code === 200) {
          resolve(data)
        } else {
          const message = data?.message || '请求失败'
          if (import.meta.env.DEV) {
            console.error('[API Business Error]', method, fullUrl, data)
          }
          if (options.showError !== false) {
            uni.showToast({ title: message, icon: 'none' })
          }
          reject(new ApiRequestError(message, res.statusCode, data))
        }
      },
      fail: (error) => {
        if (options.showLoading !== false) {
          hideRequestLoading()
        }
        const message = typeof error?.errMsg === 'string' ? error.errMsg : '网络错误'
        if (import.meta.env.DEV) {
          console.error('[API Network Error]', method, fullUrl, error)
        }
        // #ifdef MP-WEIXIN
        console.error('[API Network Error]', method, fullUrl, error)
        // #endif
        if (options.showError !== false) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new ApiRequestError(message, undefined, error))
      }
    })
  })
}

// 便捷方法
export const get = <T = any>(url: string, data?: any, options?: Partial<RequestOptions>) =>
  request<T>({ url, method: 'GET', data, ...options })

export const post = <T = any>(url: string, data?: any, options?: Partial<RequestOptions>) =>
  request<T>({ url, method: 'POST', data, ...options })

export const put = <T = any>(url: string, data?: any, options?: Partial<RequestOptions>) =>
  request<T>({ url, method: 'PUT', data, ...options })

export const del = <T = any>(url: string, data?: any, options?: Partial<RequestOptions>) =>
  request<T>({ url, method: 'DELETE', data, ...options })

export default { request, get, post, put, del }
