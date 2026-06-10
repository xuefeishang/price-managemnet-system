import http from '@/utils/http'
import type {
  ApiResponse,
  AdminNotificationSummary,
  AdminMiniProgramSubscription,
  NotificationDashboard,
  NotificationChannelConfig,
  NotificationChannelConfigUpdateRequest,
  NotificationDeliveryLog,
  NotificationMiniProgramCoverage,
  NotificationMessage,
  NotificationPreference,
  NotificationProviderHealth,
  NotificationProviderTestResult,
  NotificationRecipient,
  NotificationThrottleRule,
  PageResponse,
  SystemNotice,
  SystemNoticeCreateRequest,
  SystemNoticeStatus
} from '@/types'

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

export const archiveNotification = async (messageId: number): Promise<ApiResponse<void>> => {
  return await http.post(`/api/notifications/${messageId}/archive`)
}

export const getNotificationPreferences = async (): Promise<ApiResponse<NotificationPreference[]>> => {
  return await http.get('/api/notifications/preferences')
}

export const updateNotificationPreferences = async (
  preferences: NotificationPreference[]
): Promise<ApiResponse<NotificationPreference[]>> => {
  return await http.put('/api/notifications/preferences', preferences)
}

export const getAdminNotifications = async (params?: {
  page?: number
  size?: number
  type?: string
  priority?: string
  businessType?: string
  channel?: string
  deliveryStatus?: string
  keyword?: string
  startTime?: string
  endTime?: string
}): Promise<ApiResponse<PageResponse<AdminNotificationSummary>>> => {
  return await http.get('/api/admin/notifications', { params })
}

export const getAdminNotification = async (id: number): Promise<ApiResponse<NotificationMessage>> => {
  return await http.get(`/api/admin/notifications/${id}`)
}

export const getAdminNotificationRecipients = async (
  id: number,
  params?: { page?: number; size?: number }
): Promise<ApiResponse<PageResponse<NotificationRecipient>>> => {
  return await http.get(`/api/admin/notifications/${id}/recipients`, { params })
}

export const getAdminNotificationDeliveries = async (id: number): Promise<ApiResponse<NotificationDeliveryLog[]>> => {
  return await http.get(`/api/admin/notifications/${id}/deliveries`)
}

export const getAdminNotificationDeliveryLogs = async (
  id: number,
  params?: {
    page?: number
    size?: number
    channel?: string
    status?: string
    keyword?: string
  }
): Promise<ApiResponse<PageResponse<NotificationDeliveryLog>>> => {
  return await http.get(`/api/admin/notifications/${id}/delivery-logs`, { params })
}

export const retryAdminNotificationDelivery = async (id: number): Promise<ApiResponse<void>> => {
  return await http.post(`/api/admin/notifications/deliveries/${id}/retry`)
}

export const getNotificationDashboard = async (): Promise<ApiResponse<NotificationDashboard>> => {
  return await http.get('/api/admin/notifications/dashboard')
}

export const getNotificationProviderHealth = async (): Promise<ApiResponse<NotificationProviderHealth[]>> => {
  return await http.get('/api/admin/notifications/providers/health')
}

export const getNotificationThrottleRules = async (): Promise<ApiResponse<NotificationThrottleRule[]>> => {
  return await http.get('/api/admin/notifications/throttle-rules')
}

export const getNotificationChannelConfig = async (
  channel: string
): Promise<ApiResponse<NotificationChannelConfig>> => {
  return await http.get(`/api/admin/notifications/channels/${channel}/config`)
}

export const saveNotificationChannelConfig = async (
  channel: string,
  request: NotificationChannelConfigUpdateRequest
): Promise<ApiResponse<NotificationChannelConfig>> => {
  return await http.put(`/api/admin/notifications/channels/${channel}/config`, request)
}

export const testNotificationChannelConfig = async (
  channel: string
): Promise<ApiResponse<NotificationProviderTestResult>> => {
  return await http.post(`/api/admin/notifications/channels/${channel}/test`)
}

export const testNotificationChannelToken = async (
  channel: string
): Promise<ApiResponse<NotificationProviderTestResult>> => {
  return await http.post(`/api/admin/notifications/channels/${channel}/test-token`)
}

export const testNotificationChannelDelivery = async (
  channel: string,
  request: { userId: number; notificationType: string }
): Promise<ApiResponse<number>> => {
  return await http.post(`/api/admin/notifications/channels/${channel}/test-delivery`, request)
}

export const getMiniProgramCoverage = async (params?: {
  roles?: string
  notificationType?: string
}): Promise<ApiResponse<NotificationMiniProgramCoverage>> => {
  return await http.get('/api/admin/notifications/mini-program/coverage', { params })
}

export const getMiniProgramSubscriptions = async (params?: {
  page?: number
  size?: number
  role?: string
  status?: string
  keyword?: string
}): Promise<ApiResponse<PageResponse<AdminMiniProgramSubscription>>> => {
  return await http.get('/api/admin/notifications/mini-program/subscriptions', { params })
}

export const getMiniProgramSubscriptionDetail = async (
  userId: number
): Promise<ApiResponse<AdminMiniProgramSubscription>> => {
  return await http.get(`/api/admin/notifications/mini-program/subscriptions/${userId}`)
}

export const resolveMiniProgramSubscription = async (
  userId: number,
  request: { status: 'OPEN' | 'RESOLVED' | 'SNOOZED' | 'FOLLOW_UP'; remark?: string; remindAfter?: string }
): Promise<ApiResponse<AdminMiniProgramSubscription>> => {
  return await http.post(`/api/admin/notifications/mini-program/subscriptions/${userId}/resolve`, request)
}

export const sendMiniProgramAuthorizationGuides = async (request: {
  targetRoles?: string[]
  status?: string
  keyword?: string
}): Promise<ApiResponse<number>> => {
  return await http.post('/api/admin/notifications/mini-program/authorization-guides', request)
}

export const sendMiniProgramAuthorizationGuide = async (userId: number): Promise<ApiResponse<number>> => {
  return await http.post(`/api/admin/notifications/mini-program/authorization-guides/${userId}`)
}

export const getSystemNotices = async (params?: {
  page?: number
  size?: number
  status?: SystemNoticeStatus
}): Promise<ApiResponse<PageResponse<SystemNotice>>> => {
  return await http.get('/api/admin/system-notices', { params })
}

export const createSystemNotice = async (
  request: SystemNoticeCreateRequest
): Promise<ApiResponse<SystemNotice>> => {
  return await http.post('/api/admin/system-notices', request)
}

export const publishSystemNotice = async (id: number): Promise<ApiResponse<SystemNotice>> => {
  return await http.post(`/api/admin/system-notices/${id}/publish`)
}

export const cancelSystemNotice = async (id: number): Promise<ApiResponse<SystemNotice>> => {
  return await http.post(`/api/admin/system-notices/${id}/cancel`)
}
