import { showToast } from 'vant'

export interface ApiError extends Error {
  response?: {
    status?: number
    data?: any
  }
  toastShown?: boolean
}

export const showApiError = (error: unknown, fallbackMessage = '操作失败') => {
  const apiError = error as ApiError
  if (apiError?.toastShown) return
  showToast(apiError?.message || fallbackMessage)
  if (apiError) apiError.toastShown = true
}
