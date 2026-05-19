/**
 * uni-app 请求封装
 * 提供统一的 API 请求封装，包含：
 * - 请求拦截：自动添加 Authorization 头
 * - 响应拦截：统一错误处理、Token 自动刷新
 * - 加载提示：自动显示/隐藏加载动画
 */
import { useUserStore } from '@/store/useUserStore'
import type { ApiResponse } from '@/types'

// API 基础路径
// H5 开发环境使用代理，小程序/APP 需要完整地址
const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

// 请求配置
interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
  showLoading?: boolean
  showError?: boolean
}

// Token 刷新队列
let isRefreshing = false
let refreshQueue: Array<(token: string) => void> = []

/**
 * 统一请求方法
 */
export const request = async <T = any>(options: RequestOptions): Promise<ApiResponse<T>> => {
  const userStore = useUserStore()

  return new Promise((resolve, reject) => {
    // 显示加载提示
    if (options.showLoading !== false) {
      uni.showLoading({ title: '加载中...', mask: true })
    }

    const fullUrl = BASE_URL + options.url
    // 仅开发环境打印调试日志
    if (import.meta.env.DEV) {
      console.log('[API Request]', options.method || 'GET', fullUrl, options.data)
    }

    uni.request({
      url: fullUrl,
      method: options.method || 'GET',
      data: options.data,
      timeout: 30000,
      header: {
        'Content-Type': 'application/json',
        'Authorization': userStore.token ? `Bearer ${userStore.token}` : '',
        ...options.header
      },
      success: async (res) => {
        uni.hideLoading()

        const data = res.data as ApiResponse<T>

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

        if (data.code === 200) {
          resolve(data)
        } else {
          if (options.showError !== false) {
            uni.showToast({ title: data.message || '请求失败', icon: 'none' })
          }
          reject(new Error(data.message))
        }
      },
      fail: (error) => {
        uni.hideLoading()
        if (options.showError !== false) {
          uni.showToast({ title: '网络错误', icon: 'none' })
        }
        reject(error)
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