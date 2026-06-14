import type { Product } from '@/types'
import { getDictValue } from '@/composables/useDict'

export const parseProductOriginIds = (originIds?: string) => {
  if (!originIds) return []
  try {
    const parsed = JSON.parse(originIds)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

export const getProductOriginLabel = (product: Pick<Product, 'originIds'>) => {
  return parseProductOriginIds(product.originIds)
    .map(key => getDictValue('origin', key))
    .filter(Boolean)
    .join('/')
}

export const getProductDisplayName = (product: Pick<Product, 'name' | 'originIds'>) => {
  const originLabel = getProductOriginLabel(product)
  return originLabel ? `${product.name}.${originLabel}` : product.name
}
