import { get } from './request'
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

export const getHomeSummary = async (date?: string): Promise<ApiResponse<HomeSummary>> => {
  const params = date ? { date } : {}
  return await get('/api/home/summary', params)
}

export const getPriceAlerts = async (date?: string): Promise<ApiResponse<PriceAlert[]>> => {
  const params = date ? { date } : {}
  return await get('/api/home/alerts', params)
}
