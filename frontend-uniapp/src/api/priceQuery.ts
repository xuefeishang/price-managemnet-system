import { get } from './request'
import type { ApiResponse, PageResponse, ProductStatus } from '@/types'

export interface PriceQueryParams {
  date?: string
  keyword?: string
  categoryId?: number
  status?: ProductStatus
  page?: number
  size?: number
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}

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
  budgetPrice?: number | null
  monthlyAveragePrice?: number | null
  latestPrice?: number | null
  latestPriceDate?: string | null
  previousPrice?: number | null
  previousPriceDate?: string | null
  previousChangeAmount?: number | null
  previousChangePercent?: number | null
  hasPrice: boolean
}

export const getPriceQueryRows = async (
  params: PriceQueryParams
): Promise<ApiResponse<PageResponse<PriceQueryRow>>> => {
  return await get('/api/price-query', params)
}
