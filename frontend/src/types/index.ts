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

// 用户信息类型
export interface User {
  id: number
  username: string
  employeeId?: string
  password?: string
  role: Role  // 主角色（兼容）
  roles?: Role[]  // 角色列表
  status: UserStatus
  nickname: string
  email: string
  phone: string
  department?: string
  deptId?: number
  loginType?: string
  wechatOpenid?: string
  wechatNickname?: string
  wechatAvatar?: string
  lastLoginTime?: string
  lastLoginIp?: string
  loginCount?: number
  passwordUpdatedTime?: string
  isLocked?: boolean
  lockedTime?: string
  createdTime: string
  updatedTime: string
}

// 部门类型
export type DeptType = 'HEADQUARTERS' | 'COMPANY' | 'DEPARTMENT'

export interface Department {
  id: number
  parentId: number | null
  deptCode: string
  deptName: string
  deptType: DeptType
  leaderId?: number
  leaderName?: string
  sortOrder: number
  status: string
  path?: string
  level?: number
  userCount?: number
  children?: Department[]
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
  captchaKey?: string
  captchaCode?: string
  loginType?: string
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

// 日常价格查询行
export interface PriceQueryRow {
  productId: number
  productName: string
  categoryId?: number
  categoryName?: string
  originIds?: string
  specification?: string
  unit?: string
  currency?: string
  effectiveDate: string
  currentPrice?: number | null
  yesterdayPrice?: number | null
  changeAmount?: number | null
  changePercent?: number | null
  budgetPrice?: number | null
  monthlyAveragePrice?: number | null
  latestPrice?: number | null
  hasPrice: boolean
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

// 系统角色类型
export interface SysRole {
  id: number
  roleCode: string
  roleName: string
  description?: string
  deptId?: number
  sortOrder: number
  status: string
  isSystem: boolean
  createdTime: string
  updatedTime: string
}

// 系统权限类型
export interface SysPermission {
  id: number
  permissionCode: string
  permissionName: string
  permissionType: string
  parentId?: number
  resourceUrl?: string
  icon?: string
  sortOrder: number
  status: string
  createdTime: string
  updatedTime: string
  children?: SysPermission[]
}

// ==================== 分类视觉配置 ====================

// 分类视觉配置类型（深色主题配置）
export interface CategoryDarkModeConfig {
  primaryColor: string
  textColor: string
  borderColor: string
  surfaceColor?: string
  glowColor: string
}

// 分类视觉配置类型
export interface CategoryVisualConfig {
  categoryId?: number
  categoryCode?: string      // 分类编码（用于匹配）
  presetId?: string          // 视觉预设ID
  presetVersion?: number     // 视觉预设版本
  customized?: boolean       // 是否经过高级微调
  primaryColor: string      // 主色调（产品名称、图标）
  secondaryColor: string    // 辅助色（背景渐变）
  textColor: string         // 文字颜色
  borderColor: string       // 边框颜色（随分类视觉方案变化）
  surfaceColor?: string      // 分类浅底色
  chartLineColor?: string    // 趋势图线条色
  chartAreaColor?: string    // 趋势图面积色
  glowColor: string         // 光晕颜色（rgba带透明度0.15-0.2）
  icon: string              // 图标标识符
  iconType: 'builtin' | 'svg' | 'image'
  darkMode?: CategoryDarkModeConfig  // 深色主题配置
}
