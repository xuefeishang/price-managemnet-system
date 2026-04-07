
// 用户角色类型
export type Role = 'ADMIN' | 'EDITOR' | 'VIEWER'

// 用户状态类型
export type UserStatus = 'ACTIVE' | 'INACTIVE'

// 产品状态类型
export type ProductStatus = 'ACTIVE' | 'INACTIVE'

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
  createdTime: string
  updatedTime: string
}

// 价格类型
export interface Price {
  id: number
  productId: number
  product?: Product
  originalPrice?: number
  currentPrice: number
  costPrice?: number
  effectiveDate?: string
  expiryDate?: string
  unit?: string
  priceSpec?: string
  createdBy?: number
  createdTime: string
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

// 登录请求类型
export interface LoginRequest {
  username: string
  password: string
}

// 登录响应类型
export interface LoginResponse {
  token: string
  userId: number
  username: string
  nickname: string
  role: Role
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
