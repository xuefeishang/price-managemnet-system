import { ref, reactive, computed } from 'vue'
import { getActiveDicts, getDicts, getDictCategories } from '@/api/dict'
import type { SysDict } from '@/types'

// 全局字典缓存（reactive 代理 Map，使 get/set/clear 等操作均触发响应式更新）
const dictCache = reactive(new Map<string, SysDict[]>())
const allDicts = ref<SysDict[]>([])
const categories = ref<string[]>([])
const loaded = ref(false)

// 字典分类中文标签映射
export const CATEGORY_LABELS: Record<string, string> = {
  currency: '币种',
  common_status: '通用状态',
  user_role: '用户角色',
  approval_status: '审批状态',
  workflow_type: '工作流类型',
  node_type: '节点类型',
  business_type: '业务类型',
  approval_action: '审批操作',
  change_type: '变更类型',
  unit: '计量单位',
  operation_type: '操作类型',
  operation_module: '操作模块',
  menu_icon: '菜单图标',
  origin: '产地',
  customer: '客户',
  sync_status: '同步状态',
}

// 加载所有字典数据到缓存
export const loadAllDicts = async () => {
  if (loaded.value) return
  try {
    const response = await getDicts()
    if (response.data) {
      allDicts.value = response.data
      dictCache.clear()
      const map = new Map<string, SysDict[]>()
      for (const dict of response.data) {
        const list = map.get(dict.category) || []
        list.push(dict)
        map.set(dict.category, list)
      }
      // 逐个 set，确保 reactive Map 追踪每个 key 的写入
      map.forEach((value, key) => dictCache.set(key, value))
      loaded.value = true
    }
  } catch (error) {
    console.error('Failed to load dicts:', error)
  }
}

// 加载字典分类列表
export const loadCategories = async () => {
  try {
    const response = await getDictCategories()
    if (response.data) {
      categories.value = response.data
    }
  } catch (error) {
    console.error('Failed to load dict categories:', error)
  }
}

// 刷新缓存
export const refreshDictCache = async () => {
  loaded.value = false
  allDicts.value = []
  dictCache.clear()
  await loadAllDicts()
}

// 获取指定分类的字典列表
export const getDictByCategory = (category: string): SysDict[] => {
  return dictCache.get(category) || []
}

// 获取指定分类的启用字典列表
export const getActiveDictByCategory = (category: string): SysDict[] => {
  return (dictCache.get(category) || []).filter(d => d.status === 'ACTIVE')
}

// 根据 category 和 key 获取字典项
export const getDictItem = (category: string, dictKey: string): SysDict | undefined => {
  return (dictCache.get(category) || []).find(d => d.dictKey === dictKey)
}

// 根据 category 和 key 获取显示值
export const getDictValue = (category: string, dictKey: string): string => {
  const item = getDictItem(category, dictKey)
  return item?.dictValue || dictKey
}

// 根据 category 和 key 获取扩展值
export const getDictExtraValue = (category: string, dictKey: string): string => {
  const item = getDictItem(category, dictKey)
  return item?.extraValue || ''
}

// 获取货币符号（便捷方法）
export const getCurrencySymbol = (currency?: string): string => {
  if (!currency) return '¥'
  return getDictExtraValue('currency', currency) || '¥'
}

// 获取状态显示名称（便捷方法）
export const getStatusLabel = (status: string): string => {
  return getDictValue('common_status', status)
}

// 获取角色显示名称（便捷方法）
export const getRoleLabel = (role: string): string => {
  return getDictValue('user_role', role)
}

// 获取操作类型显示名称（便捷方法）
export const getOperationTypeLabel = (type: string): string => {
  return getDictValue('operation_type', type)
}

// 获取操作模块显示名称（便捷方法）
export const getOperationModuleLabel = (module: string): string => {
  return getDictValue('operation_module', module)
}

// ========== 产地相关便捷方法 ==========

// 获取产地名称（便捷方法）
export const getOriginName = (code: string): string => {
  return getDictValue('origin', code)
}

// 获取产地下拉选项
export const getOriginOptions = (): { value: string; label: string; extra?: string }[] => {
  return getDictOptions('origin')
}

// ========== 客户相关便捷方法 ==========

// 获取客户名称（便捷方法）
export const getCustomerName = (code: string): string => {
  return getDictValue('customer', code)
}

// 获取客户下拉选项
export const getCustomerOptions = (): { value: string; label: string; extra?: string }[] => {
  return getDictOptions('customer')
}

// 获取客户联系信息（从 extraValue 解析）
export const getCustomerContact = (code: string): { contact?: string; phone?: string } => {
  const extra = getDictExtraValue('customer', code)
  if (!extra) return {}
  try {
    return JSON.parse(extra)
  } catch {
    return {}
  }
}

// 获取下拉选项列表（便捷方法，用于 select/radio 等表单组件）
// 直接从字典缓存获取启用状态的项
export const getDictOptions = (category: string): { value: string; label: string; extra?: string }[] => {
  const cached = getActiveDictByCategory(category)
  if (cached.length > 0) {
    return cached.map(d => ({ value: d.dictKey, label: d.dictValue, extra: d.extraValue || undefined }))
  }
  return []
}

// 获取所有字典选项（含停用项，用于管理页面）
export const getAllDictOptions = (category: string): { value: string; label: string; extra?: string }[] => {
  const cached = getDictByCategory(category)
  return cached.map(d => ({ value: d.dictKey, label: d.dictValue, extra: d.extraValue || undefined }))
}

// 组合式 API
export function useDict() {
  const loading = ref(false)

  const loadDictsForCategory = async (category: string) => {
    loading.value = true
    try {
      const response = await getActiveDicts(category)
      if (response.data) {
        dictCache.set(category, response.data)
      }
    } catch (error) {
      console.error(`Failed to load dict for category: ${category}`, error)
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    allDicts: computed(() => allDicts.value),
    categories: computed(() => categories.value),
    loadAllDicts,
    loadCategories,
    loadDictsForCategory,
    refreshDictCache,
    getDictByCategory,
    getActiveDictByCategory,
    getDictItem,
    getDictValue,
    getDictExtraValue,
    getCurrencySymbol,
    getStatusLabel,
    getRoleLabel,
    getOperationTypeLabel,
    getOperationModuleLabel,
    getDictOptions,
    getAllDictOptions,
    getOriginName,
    getOriginOptions,
    getCustomerName,
    getCustomerOptions,
    getCustomerContact,
  }
}
