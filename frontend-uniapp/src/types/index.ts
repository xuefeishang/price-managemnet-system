/**
 * TypeScript 类型定义文件
 * 统一管理前端所有接口类型定义，与后端 Entity 保持一致
 *
 * 类型命名规范：
 * - 类型别名（Type）：如 Role, UserStatus 等
 * - 接口（Interface）：如 User, Product 等
 * - 请求类型：xxxRequest
 * - 响应类型：xxxResponse
 */

// ==================== 枚举类型 ====================

// 用户角色类型
export type Role = 'ADMIN' | 'EDITOR' | 'VIEWER'

// 用户状态类型
export type UserStatus = 'ACTIVE' | 'INACTIVE'

// 产品状态类型
export type ProductStatus = 'ACTIVE' | 'INACTIVE'

// 计价币种类型
export type Currency = 'CNY' | 'USD' | 'EUR'

// 分类状态类型
export type CategoryStatus = 'ACTIVE' | 'INACTIVE'

// 产地状态类型
export type OriginStatus = 'ACTIVE' | 'INACTIVE'

// 客户状态类型
export type CustomerStatus = 'ACTIVE' | 'INACTIVE'

// 价格变更类型
export type ChangeType = 'CREATE' | 'UPDATE' | 'DELETE'

// 同步类型
export type SyncType = 'PRODUCT_SYNC' | 'PRICE_SYNC' | 'FULL_SYNC'

// 同步状态类型
export type SyncStatus = 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'PROCESSING'

// ==================== 实体类型 ====================

// 用户信息类型
export interface User {
  id: number
  username: string
  password?: string
  role: Role
  status: UserStatus
  nickname: string
  email: string
  phone: string
  createdTime: string
  updatedTime: string
}

// 产品分类类型
export interface ProductCategory {
  id: number
  name: string
  code: string
  sortOrder: number
  status: CategoryStatus
  remark?: string
  createdTime: string
  updatedTime: string
}

// 产地类型
export interface Origin {
  id: number
  name: string
  code: string
  sortOrder: number
  status: OriginStatus
  remark?: string
  createdTime: string
  updatedTime: string
}

// 客户类型
export interface Customer {
  id: number
  name: string
  code: string
  contact?: string
  phone?: string
  address?: string
  sortOrder: number
  status: CustomerStatus
  remark?: string
  createdTime: string
  updatedTime: string
}

// 产品类型
export interface Product {
  id: number
  code?: string
  name: string
  sellingPrice?: number
  budgetPrice?: number
  categoryId?: number
  category?: ProductCategory
  status: ProductStatus
  description?: string
  specs?: string
  imageUrl?: string
  originIds?: string
  customerIds?: string
  remark?: string
  unit?: string
  sortOrder?: number
  showOnHome?: boolean
  currency?: string
  createdTime: string
  updatedTime: string
}

// 价格类型
export interface Price {
  id: number
  version?: number
  productId: number
  product?: Product
  originalPrice?: number
  currentPrice: number
  costPrice?: number
  budgetPrice?: number
  effectiveDate?: string
  expiryDate?: string
  unit?: string
  priceSpec?: string
  createdBy?: number
  createdTime: string
}

export type PriceDraftStatus = 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED' | 'PUBLISHING' | 'PUBLISHED' | 'CANCELLED'
export type PricePublishStatus = 'SUCCESS' | 'FAILED' | 'PARTIAL'

export interface PriceDraftItem {
  id?: number
  batchId?: number
  productId: number
  basePriceId?: number
  basePriceVersion?: number
  originalPrice?: number
  currentPrice: number
  costPrice?: number
  budgetPrice?: number
  effectiveDate?: string
  expiryDate?: string
  unit?: string
  priceSpec?: string
  itemStatus?: string
  lastModifiedBy?: number
  publishedPriceId?: number
  createdTime?: string
  updatedTime?: string
}

export interface PriceDraftBatch {
  id: number
  version?: number
  effectiveDate: string
  status: PriceDraftStatus
  sourceType?: 'MANUAL' | 'SCHEDULED'
  itemCount?: number
  savedItemCount?: number
  lastModifiedBy?: number
  publishedTime?: string
  publishedBy?: number
  createdBy?: number
  createdTime?: string
  updatedTime?: string
  items: PriceDraftItem[]
}

export interface PriceDraftPublishableSummary {
  hasPublishableDrafts: boolean
  publishableBatchCount: number
  publishableItemCount: number
  publishableDateCount: number
  effectiveDates: string[]
  publishableBatchIds: number[]
}

export interface PriceDraftSaveRequest {
  batchId?: number
  batchVersion?: number
  effectiveDate: string
  items: PriceDraftItem[]
}

export interface PricePublishResult {
  batchId: number
  publishLogId?: number
  publishGroupId?: string
  status: PricePublishStatus
  batchStatus: PriceDraftStatus
  successCount: number
  failCount: number
  attemptedBatchCount?: number
  publishedBatchCount?: number
  failedBatchCount?: number
  remainingDraftBatchCount?: number
  attemptedDateCount?: number
  publishedDateCount?: number
  effectiveDates?: string[]
  publishLogIds?: number[]
  batchResults?: Array<{
    effectiveDate?: string
    batchId: number
    publishLogId?: number
    status: PricePublishStatus
    batchStatus: PriceDraftStatus
    successCount: number
    failCount: number
    message?: string
  }>
  notificationMessageId?: number
  message?: string
}

// 价格历史类型
export interface PriceHistory {
  id: number
  priceId: number
  productId: number
  oldPrice?: number
  newPrice?: number
  changeType: ChangeType
  changedBy?: number
  changedTime: string
  remark?: string
}

// 数据同步日志类型
export interface SyncLog {
  id: number
  syncType: SyncType
  syncStatus: SyncStatus
  syncTime: string
  totalCount?: number
  successCount?: number
  errorCount?: number
  errorMessage?: string
  syncDesc?: string
}

// ==================== 请求/响应类型 ====================

// 登录请求类型
export interface LoginRequest {
  username: string
  password: string
}

// 登录响应类型
export interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: User
}

// API响应通用类型
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

// 分页查询请求类型
export interface PageRequest {
  page?: number
  size?: number
  keyword?: string
  categoryId?: number
  status?: ProductStatus
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}

// 分页响应类型
export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export interface NotificationMessage {
  id: number
  messageId: number
  type: string
  title: string
  summary?: string
  content?: string
  businessType?: string
  businessId?: number
  channels?: string
  priority?: 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'
  linkType?: string
  linkParams?: string
  archived?: boolean
  readStatus: 'UNREAD' | 'READ'
  readTime?: string
  createdTime?: string
}

export interface NotificationMiniProgramTemplateSubscription {
  notificationType: string
  templateId: string
  status: string
  availableCount: number
  authorized: boolean
  lastAuthorizedTime?: string
}

export interface NotificationMiniProgramSubscription {
  enabled: boolean
  configured: boolean
  openidBound: boolean
  templates: NotificationMiniProgramTemplateSubscription[]
}

export interface NotificationMiniProgramSubscriptionUpdateRequest {
  results: Array<{
    notificationType: string
    templateId: string
    result: string
  }>
}

// ==================== 菜单/字典类型 ====================

// 菜单项类型
export interface MenuItem {
  id: number
  parentId: number | null
  name: string
  path: string | null
  icon: string | null
  sortOrder: number
  visible: boolean
  roles: Role[]
  createdTime: string
  updatedTime: string
  children?: MenuItem[]
}

// 数据字典类型
export type DictStatus = 'ACTIVE' | 'INACTIVE'

export interface SysDict {
  id: number
  category: string
  dictKey: string
  dictValue: string
  extraValue?: string
  sortOrder: number
  status: DictStatus
  remark?: string
  createdTime: string
  updatedTime: string
}
