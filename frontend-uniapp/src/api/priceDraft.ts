import { get, post } from './request'
import type { ApiResponse, PriceDraftBatch, PriceDraftSaveRequest, PricePublishResult } from '@/types'

export const getPriceDraftByDate = async (date: string): Promise<ApiResponse<PriceDraftBatch | null>> =>
  await get('/api/price-drafts/by-date', { date })

export const savePriceDraft = async (data: PriceDraftSaveRequest): Promise<ApiResponse<PriceDraftBatch>> =>
  await post('/api/price-drafts/batch-save', data)

export const publishPriceDraft = async (batchId: number): Promise<ApiResponse<PricePublishResult>> =>
  await post(`/api/price-drafts/${batchId}/publish`)
