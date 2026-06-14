import { get, post } from './request'
import type { ApiResponse, PriceDraftBatch, PriceDraftPublishableSummary, PriceDraftSaveRequest, PricePublishResult } from '@/types'

export const getPriceDraftByDate = async (date: string): Promise<ApiResponse<PriceDraftBatch | null>> =>
  await get('/api/price-drafts/by-date', { date })

export const getPriceDraftPublishableSummary = async (): Promise<ApiResponse<PriceDraftPublishableSummary>> =>
  await get('/api/price-drafts/publishable-summary')

export const savePriceDraft = async (data: PriceDraftSaveRequest): Promise<ApiResponse<PriceDraftBatch>> =>
  await post('/api/price-drafts/batch-save', data)

export const publishPriceDraft = async (batchId: number): Promise<ApiResponse<PricePublishResult>> =>
  await post(`/api/price-drafts/${batchId}/publish`)

export const publishPriceDraftByDate = async (date: string): Promise<ApiResponse<PricePublishResult>> =>
  await post(`/api/price-drafts/by-date/publish?date=${encodeURIComponent(date)}`)

export const publishAllPriceDrafts = async (): Promise<ApiResponse<PricePublishResult>> =>
  await post('/api/price-drafts/publish-all')
