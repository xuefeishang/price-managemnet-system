import { reactive, ref } from 'vue'
import { getDicts } from '@/api/dict'
import type { SysDict } from '@/types'

const dictCache = reactive(new Map<string, SysDict[]>())
const loaded = ref(false)

export const loadAllDicts = async () => {
  if (loaded.value) return
  try {
    const response = await getDicts()
    dictCache.clear()
    const grouped = new Map<string, SysDict[]>()
    for (const dict of response.data || []) {
      const items = grouped.get(dict.category) || []
      items.push(dict)
      grouped.set(dict.category, items)
    }
    grouped.forEach((items, category) => dictCache.set(category, items))
    loaded.value = true
  } catch (error) {
    console.error('加载字典失败:', error)
  }
}

export const getDictValue = (category: string, key?: string) => {
  if (!key) return ''
  const item = (dictCache.get(category) || []).find(dict => dict.dictKey === key)
  return item?.dictValue || key
}

export const getDictExtraValue = (category: string, key?: string) => {
  if (!key) return ''
  const item = (dictCache.get(category) || []).find(dict => dict.dictKey === key)
  return item?.extraValue || ''
}

export const getCurrencySymbol = (currency?: string) => {
  if (!currency) return '¥'
  return getDictExtraValue('currency', currency) || '¥'
}
