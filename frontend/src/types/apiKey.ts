import type { PageResponse } from '@/types'

export interface ApiKey {
  id: number
  name: string
  appId: string
  appSecretFingerprint: string
  appSecretKeyVersion: string
  description?: string
  status: string
  environment: string
  expireTime?: string
  ipWhitelist: string[]
  rateLimitPerMinute: number
  dailyLimit: number
  createdBy?: number
  createdTime: string
  updatedTime?: string
  lastUsedTime?: string
  permissionCodes: string[]
}

export interface ApiKeyCreateRequest {
  name: string
  description?: string
  environment: string
  expireTime?: string
  ipWhitelist: string[]
  rateLimitPerMinute: number
  dailyLimit: number
  permissionCodes: string[]
}

export interface ApiKeyUpdateRequest extends ApiKeyCreateRequest {}

export interface ApiKeyCreateResponse {
  apiKey: ApiKey
  appSecret: string
}

export interface ExternalApiServiceStatus {
  deploymentEnabled: boolean
  runtimeEnabled: boolean
  available: boolean
  message: string
}

export interface ExternalApiEndpoint {
  id: number
  permissionCode: string
  method: string
  pathPattern: string
  description?: string
  requestExample?: string
  responseExample?: string
  errorCodes?: string
  usageNotes?: string
  queryExample?: string
  bodyExample?: string
  pathParamsExample?: string
  querySchema?: string
  bodySchema?: string
  pathParamsSchema?: string
  successExample?: string
  failureExample?: string
  codeNotes?: string
  status: string
  sortOrder: number
}

export interface ApiCallLog {
  id: number
  apiKeyId?: number
  appId?: string
  endpoint: string
  queryString?: string
  method: string
  permissionCode?: string
  statusCode?: number
  responseTime?: number
  ipAddress?: string
  requestTime: string
  requestBodyHash?: string
  nonce?: string
  authResult: string
  errorMessage?: string
  createdTime: string
}

export interface ApiCallLogStatistics {
  totalCalls: number
  authResultCount: Record<string, number>
}

export type ApiKeyPage = PageResponse<ApiKey>
export type ApiCallLogPage = PageResponse<ApiCallLog>
