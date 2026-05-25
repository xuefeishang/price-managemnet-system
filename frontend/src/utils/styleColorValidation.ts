import type { StyleConfig } from '@/types/theme'

export type PriceColorField = 'priceRiseColor' | 'priceFallColor' | 'priceFlatColor'

export const PRICE_COLOR_FIELDS: Array<{ key: PriceColorField; label: string }> = [
  { key: 'priceRiseColor', label: '上涨色' },
  { key: 'priceFallColor', label: '下跌色' },
  { key: 'priceFlatColor', label: '持平色' }
]

export const isSupportedHexColor = (value: string | null | undefined): boolean => {
  return /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value?.trim() || '')
}

export const getInvalidPriceColorLabels = (config: StyleConfig | null): string[] => {
  if (!config) return []
  return PRICE_COLOR_FIELDS
    .filter(field => !isSupportedHexColor(config[field.key]))
    .map(field => field.label)
}
