import { get, post } from './request'
import type {
  ApiResponse,
  NotificationMessage,
  NotificationMiniProgramSubscription,
  NotificationMiniProgramSubscriptionUpdateRequest,
  PageResponse
} from '@/types'

export const getMyNotifications = async (params?: {
  page?: number
  size?: number
  readStatus?: 'UNREAD' | 'READ'
}): Promise<ApiResponse<PageResponse<NotificationMessage>>> => {
  return await get('/api/notifications/my', params, { showLoading: false })
}

export const getUnreadNotificationCount = async (): Promise<ApiResponse<number>> => {
  return await get('/api/notifications/unread-count', undefined, { showLoading: false, showError: false })
}

export const markNotificationRead = async (messageId: number): Promise<ApiResponse<void>> => {
  return await post(`/api/notifications/${messageId}/read`, undefined, { showLoading: false, showError: false })
}

export const markAllNotificationsRead = async (): Promise<ApiResponse<number>> => {
  return await post('/api/notifications/read-all', undefined, { showLoading: false })
}

export const archiveNotification = async (messageId: number): Promise<ApiResponse<void>> => {
  return await post(`/api/notifications/${messageId}/archive`, undefined, { showLoading: false })
}

export const getMiniProgramSubscriptions = async (): Promise<ApiResponse<NotificationMiniProgramSubscription>> => {
  return await get('/api/notifications/mini-program/subscriptions', undefined, { showLoading: false })
}

export const updateMiniProgramSubscriptions = async (
  data: NotificationMiniProgramSubscriptionUpdateRequest
): Promise<ApiResponse<NotificationMiniProgramSubscription>> => {
  return await post('/api/notifications/mini-program/subscriptions', data, { showLoading: false })
}
