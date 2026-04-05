// 计量单位选项（与后端 Product.unit 字段、数据库字典保持一致）
export const UNIT_OPTIONS = [
  { label: '元/吨', value: '元/吨' },
  { label: '万元/吨', value: '万元/吨' },
  { label: '元/克', value: '元/克' },
  { label: '元/千克', value: '元/千克' }
] as const

export type UnitOption = typeof UNIT_OPTIONS[number]['value']
