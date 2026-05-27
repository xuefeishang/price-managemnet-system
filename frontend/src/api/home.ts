import http from '@/utils/http'
import type { ApiResponse, ProductCategory, ProductStatus } from '@/types'

export interface HomeDashboard {
  summary: HomeSummary
  alerts: PriceAlert[]
  featuredProducts: ProductMetric[]
  trendAnalysis: TrendAnalysis
}

export interface HomeSummary {
  totalProducts: number
  priceUpdatedToday: number
  coveredCategoryCount: number
  activeCategoryCount: number
  changedProductCount: number
  avgPriceChange: number
  risingCount: number
  fallingCount: number
  flatCount: number
}

export interface PriceAlert {
  productId: number
  productName: string
  productSpecs: string
  alertType: string
  alertMessage: string
  severity: 'info' | 'warning' | 'danger'
  currentValue: number
  threshold: number
  changePercent: number
}

export interface ProductMetric {
  productId: number
  productName: string
  specs: string
  originIds?: string
  currentPrice: number | null
  previousPrice: number | null
  priceDirection: 'up' | 'down' | 'flat'
  priceChange: number | null
  priceChangePercent: number | null
  formattedChange: string
  currencySymbol: string
  unit: string
  updateTime: string | null
  featured: boolean
}

export interface TrendAnalysis {
  rangeLabel: string
  days: number
  dates: string[]
  productTrends: Record<number, (number | null)[]>
  avgTrend: (number | null)[]
}

export interface HomeProductOrderItem {
  id: number
  name: string
  code?: string
  specs?: string
  originIds?: string
  sortOrder?: number
  showOnHome?: boolean
  status: ProductStatus
  unit?: string
  currency?: string
  categoryId?: number
}

export interface HomeProductOrderGroup {
  category: Pick<ProductCategory, 'id' | 'name' | 'code' | 'sortOrder' | 'status'> | null
  virtualKey?: 'uncategorized'
  name: string
  products: HomeProductOrderItem[]
}

export const getHomeDashboard = async (date?: string): Promise<ApiResponse<HomeDashboard>> => {
  const params = date ? { date } : {}
  return await http.get('/api/home/dashboard', { params })
}

export const getHomeSummary = async (date?: string): Promise<ApiResponse<HomeSummary>> => {
  const params = date ? { date } : {}
  return await http.get('/api/home/summary', { params })
}

export const getPriceAlerts = async (date?: string): Promise<ApiResponse<PriceAlert[]>> => {
  const params = date ? { date } : {}
  return await http.get('/api/home/alerts', { params })
}

export const getTrendAnalysis = async (date?: string, days?: number): Promise<ApiResponse<TrendAnalysis>> => {
  const params: Record<string, any> = {}
  if (date) params.date = date
  if (days) params.days = days
  return await http.get('/api/home/trend', { params })
}

export const getHomeProductOrder = async (): Promise<ApiResponse<HomeProductOrderGroup[]>> => {
  return await http.get('/api/home/product-order')
}
