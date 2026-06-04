import http from '@/utils/http'
import type { ApiResponse, PriceDraftBatch, PriceDraftSaveRequest, PricePublishResult } from '@/types'

export const getPriceDraftByDate = async (date: string): Promise<ApiResponse<PriceDraftBatch | null>> => {
  return await http.get('/api/price-drafts/by-date', { params: { date } })
}

export const savePriceDraft = async (data: PriceDraftSaveRequest): Promise<ApiResponse<PriceDraftBatch>> => {
  return await http.post('/api/price-drafts/batch-save', data)
}

export const publishPriceDraft = async (batchId: number): Promise<ApiResponse<PricePublishResult>> => {
  return await http.post(`/api/price-drafts/${batchId}/publish`)
}

export const publishPriceDraftByDate = async (date: string): Promise<ApiResponse<PricePublishResult>> => {
  return await http.post('/api/price-drafts/by-date/publish', null, { params: { date } })
}

export const cancelPriceDraft = async (batchId: number): Promise<ApiResponse<void>> => {
  return await http.post(`/api/price-drafts/${batchId}/cancel`)
}
