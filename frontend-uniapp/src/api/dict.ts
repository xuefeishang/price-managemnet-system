import { get } from './request'
import type { ApiResponse, SysDict } from '@/types'

export const getDicts = async (): Promise<ApiResponse<SysDict[]>> => {
  return await get('/api/dict', undefined, { showLoading: false, showError: false })
}
