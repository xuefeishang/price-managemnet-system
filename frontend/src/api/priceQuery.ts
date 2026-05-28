import http from '@/utils/http'
import type { ApiResponse, PageResponse, PriceQueryRow, ProductStatus } from '@/types'

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

const buildParams = (params: PriceQueryParams = {}) => {
  const result: Record<string, string | number> = {}
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value as string | number
    }
  })
  return result
}

export const getPriceQueryRows = async (
  params?: PriceQueryParams
): Promise<ApiResponse<PageResponse<PriceQueryRow>>> => {
  return await http.get('/api/price-query', { params: buildParams(params) })
}

export const exportPriceQueryRows = async (params?: PriceQueryParams): Promise<void> => {
  const response = await http.get('/api/price-query/export', {
    params: buildParams({ ...params, page: undefined, size: undefined }),
    responseType: 'blob'
  })
  const url = window.URL.createObjectURL(new Blob([response.data]))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `日常价格查询_${params?.date || new Date().toISOString().slice(0, 10)}.xlsx`
  anchor.click()
  window.URL.revokeObjectURL(url)
}
