// 计量单位选项
// 优先从字典服务获取，这里保留硬编码作为回退
import { getDictOptions } from '@/composables/useDict'

// 硬编码回退（字典接口未加载时使用）
const UNIT_OPTIONS_FALLBACK = [
  { label: '元/吨', value: '元/吨' },
  { label: '万元/吨', value: '万元/吨' },
  { label: '元/克', value: '元/克' },
  { label: '元/千克', value: '元/千克' },
  { label: '元/吨度', value: '元/吨度' }
] as const

// 动态获取单位选项（优先字典，回退硬编码）
export const getUnitOptions = () => {
  const dictOpts = getDictOptions('unit')
  return dictOpts.length > 0 ? dictOpts : [...UNIT_OPTIONS_FALLBACK]
}

// 兼容旧代码的常量引用（动态获取）
export const UNIT_OPTIONS = getUnitOptions()

export type UnitOption = typeof UNIT_OPTIONS_FALLBACK[number]['value']
