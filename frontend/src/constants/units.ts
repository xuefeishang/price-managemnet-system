import { getDictOptions } from '@/composables/useDict'

export const getUnitOptions = () => {
  return getDictOptions('unit')
}

export type UnitOption = string
