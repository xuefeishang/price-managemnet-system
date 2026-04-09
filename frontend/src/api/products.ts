
import http from '@/utils/http'
import type { ApiResponse, Product, PageResponse, PageRequest, Price, PriceHistory } from '@/types'

// 获取产品列表（分页）
export const getProducts = async (params?: PageRequest): Promise<ApiResponse<PageResponse<Product>>> => {
  return await http.get('/api/products', { params })
}

// 获取产品详情
export const getProduct = async (id: number): Promise<ApiResponse<Product>> => {
  return await http.get(`/api/products/${id}`)
}

// 创建产品
export const createProduct = async (data: Omit<Product, 'id' | 'createdTime' | 'updatedTime'>): Promise<ApiResponse<Product>> => {
  return await http.post('/api/products', data)
}

// 更新产品
export const updateProduct = async (id: number, data: Partial<Product>): Promise<ApiResponse<Product>> => {
  return await http.put(`/api/products/${id}`, data)
}

// 删除产品
export const deleteProduct = async (id: number): Promise<ApiResponse<void>> => {
  return await http.delete(`/api/products/${id}`)
}

// 批量更新产品排序
export const batchUpdateProductSort = async (items: { id: number; sortOrder: number }[]): Promise<ApiResponse<void>> => {
  return await http.post('/api/products/batch-sort', items)
}

// 获取产品的价格历史
export const getProductPriceHistory = async (productId: number): Promise<ApiResponse<PriceHistory[]>> => {
  return await http.get(`/api/products/${productId}/price-history`)
}

// 获取产品的当前价格
export const getCurrentPrice = async (productId: number): Promise<ApiResponse<Price | null>> => {
  return await http.get(`/api/products/${productId}/current-price`)
}

// 按日期获取所有产品的价格
export const getPricesByDate = async (date: string): Promise<ApiResponse<Price[]>> => {
  return await http.get('/api/prices/by-date', { params: { date } })
}

// 按日期获取所有产品的价格（带昨日价格和月均价，批量优化接口）
export interface PriceWithStats {
  price: Price
  yesterdayPrice: Price | null
  monthlyAveragePrice: number | null
  inheritedPrice: number | null  // 继承的价格：当天无维护价格时取最近一次价格
}

export const getPricesByDateWithStats = async (date: string): Promise<ApiResponse<PriceWithStats[]>> => {
  return await http.get('/api/prices/by-date-with-stats', { params: { date } })
}

// 获取某产品在指定日期的价格
export const getPriceByDate = async (productId: number, date: string): Promise<ApiResponse<Price | null>> => {
  return await http.get(`/api/products/${productId}/price-by-date`, { params: { date } })
}

// 添加产品价格
export const addProductPrice = async (productId: number, price: Price): Promise<ApiResponse<Price>> => {
  return await http.post(`/api/products/${productId}/prices`, price)
}

// 更新价格
export const updatePrice = async (id: number, price: Partial<Price>): Promise<ApiResponse<Price>> => {
  return await http.put(`/api/prices/${id}`, price)
}

// 获取某产品昨日的价格
export const getYesterdayPrice = async (productId: number): Promise<ApiResponse<Price | null>> => {
  return await http.get(`/api/products/${productId}/yesterday-price`)
}

// 获取某产品当月的平均价格
export const getMonthlyAveragePrice = async (productId: number): Promise<ApiResponse<number | null>> => {
  return await http.get(`/api/products/${productId}/monthly-average-price`)
}
