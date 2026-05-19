/**
 * 产品相关 API
 */
import { get, post, put, del } from './request'
import type { Product, ApiResponse, PageResponse, PageRequest } from '@/types'

// 获取产品列表（分页）
export const getProducts = async (params: PageRequest): Promise<ApiResponse<PageResponse<Product>>> => {
  return await get('/api/products', params)
}

// 获取产品详情
export const getProductById = async (id: number): Promise<ApiResponse<Product>> => {
  return await get(`/api/products/${id}`)
}

// 获取产品当前价格（含时间信息）
export const getCurrentPrice = async (productId: number): Promise<ApiResponse<Price>> => {
  return await get(`/api/products/${productId}/current-price`)
}

// 创建产品
export interface CreateProductRequest {
  name: string
  code?: string
  sellingPrice?: number
  budgetPrice?: number
  categoryId?: number
  status?: string
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
}

export const createProduct = async (data: CreateProductRequest): Promise<ApiResponse<Product>> => {
  return await post('/api/products', data)
}

// 更新产品
export const updateProduct = async (id: number, data: Partial<CreateProductRequest>): Promise<ApiResponse<Product>> => {
  return await put(`/api/products/${id}`, data)
}

// 删除产品
export const deleteProduct = async (id: number): Promise<ApiResponse<void>> => {
  return await del(`/api/products/${id}`)
}

// 获取首页展示产品（使用分页接口获取启用状态产品）
export const getHomeProducts = async (): Promise<ApiResponse<PageResponse<Product>>> => {
  return await get('/api/products', { page: 0, size: 100, status: 'ACTIVE' })
}

// 批量更新产品排序
export const batchUpdateProductSort = async (items: { id: number; sortOrder: number }[]): Promise<ApiResponse<void>> => {
  return await post('/api/products/batch-sort', items)
}

// 价格相关接口
export interface PriceWithStats {
  price: Price
  yesterdayPrice: Price | null
  monthlyAveragePrice: number | null
  inheritedPrice: number | null
  inheritedBudgetPrice: number | null
}

// 按日期获取所有产品的价格（带昨日价格和月均价）
export const getPricesByDateWithStats = async (date: string): Promise<ApiResponse<PriceWithStats[]>> => {
  return await get('/api/prices/by-date-with-stats', { date })
}

// 添加产品价格
export const addProductPrice = async (productId: number, price: Partial<Price>): Promise<ApiResponse<Price>> => {
  return await post(`/api/products/${productId}/prices`, price)
}

// 更新价格
export const updatePrice = async (id: number, price: Partial<Price>): Promise<ApiResponse<Price>> => {
  return await put(`/api/prices/${id}`, price)
}

// 价格走势数据点
export interface PriceTrendPoint {
  date: string
  currentPrice: number | null
  budgetPrice: number | null
}

// 获取某产品的价格走势数据
export const getPriceTrend = async (productId: number, days: number): Promise<ApiResponse<PriceTrendPoint[]>> => {
  return await get(`/api/products/${productId}/price-trend`, { days })
}