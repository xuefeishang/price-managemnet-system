import http from '@/utils/http'
import type { ApiResponse } from '@/types'

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

export interface TrendAnalysis {
  rangeLabel: string
  days: number
  dates: string[]
  productTrends: Record<number, (number | null)[]>
  avgTrend: (number | null)[]
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