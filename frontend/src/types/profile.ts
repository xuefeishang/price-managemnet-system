import type { PageResponse, User } from '@/types'
import type { OperationLog } from '@/api/logs'

export interface Profile extends User {
  userId?: number
  permissions?: string[]
  lastLoginIp?: string
  loginCount?: number
  passwordUpdatedTime?: string
  locked?: boolean
}

export interface ProfileSecurity {
  lastLoginTime?: string
  lastLoginIp?: string
  loginCount?: number
  passwordUpdatedTime?: string
  loginType?: string
  locked?: boolean
  lockedTime?: string
  status?: string
}

export interface ProfileUpdateRequest {
  nickname?: string
  email?: string
  phone?: string
}

export interface ProfilePasswordChangeRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

export interface ProfileSession {
  id: number
  current: boolean
  deviceName?: string
  ipAddress?: string
  userAgent?: string
  createdTime?: string
  lastUsedTime?: string
  expiryDate?: string
  revoked: boolean
}

export interface ProfileLoginHistory {
  id: number
  loginTime?: string
  ipAddress?: string
  userAgent?: string
  result: string
  failureReason?: string
}

export interface ProfilePreference {
  tableDensity: 'COMPACT' | 'DEFAULT' | 'COMFORTABLE'
  defaultHomePath: string
  themeMode: 'SYSTEM' | 'LIGHT' | 'DARK'
  pageSize: number
}

export type ProfileOperationLogPage = PageResponse<OperationLog>
export type ProfileLoginHistoryPage = PageResponse<ProfileLoginHistory>

