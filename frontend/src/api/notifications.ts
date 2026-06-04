import http from '@/utils/http'
import type { ApiResponse, NotificationMessage, PageResponse } from '@/types'

export const getMyNotifications = async (params?: {
  page?: number
  size?: number
  readStatus?: 'UNREAD' | 'READ'
}): Promise<ApiResponse<PageResponse<NotificationMessage>>> => {
  return await http.get('/api/notifications/my', { params })
}

export const getUnreadNotificationCount = async (): Promise<ApiResponse<number>> => {
  return await http.get('/api/notifications/unread-count')
}

export const markNotificationRead = async (messageId: number): Promise<ApiResponse<void>> => {
  return await http.post(`/api/notifications/${messageId}/read`)
}

export const markAllNotificationsRead = async (): Promise<ApiResponse<number>> => {
  return await http.post('/api/notifications/read-all')
}
