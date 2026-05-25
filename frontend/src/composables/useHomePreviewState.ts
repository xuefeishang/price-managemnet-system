/**
 * 首页预览状态管理
 * 实现 HomeExperiencePanel 和 HomePreview 状态同源
 * 支持草稿模式：修改后需调用 saveAll() 才持久化
 */

import { ref, computed } from 'vue'
import { getDictByCategory, refreshDictCache } from '@/composables/useDict'
import { updateDict } from '@/api/dict'

// 首页布局配置
export interface HomeLayoutConfig {
  layoutMode: 'dashboard' | 'simple'
  cardColumns: number
  cardColumnsMobile: number
  showTrendChart: boolean
  showAlerts: boolean
  featuredProductCount: number
}

// 首页组件配置
export interface HomeWidget {
  key: string
  name: string
  enabled: boolean
  order: number
  config?: Record<string, any>
}

// 数值字段类型
type NumericField = 'cardColumns' | 'cardColumnsMobile' | 'featuredProductCount'

// 预设组件列表
const defaultWidgets: HomeWidget[] = [
  { key: 'summary_stats', name: '经营摘要', enabled: true, order: 1 },
  { key: 'core_metrics', name: '核心指标', enabled: true, order: 2 },
  { key: 'trend_chart', name: '重点走势', enabled: true, order: 3 },
  { key: 'product_list', name: '产品列表', enabled: true, order: 4 },
  { key: 'risk_alerts', name: '风险预警', enabled: true, order: 5 }
]

// 全局状态（单例）
const layoutConfig = ref<HomeLayoutConfig>({
  layoutMode: 'dashboard',
  cardColumns: 4,
  cardColumnsMobile: 2,
  showTrendChart: true,
  showAlerts: true,
  featuredProductCount: 6
})

const widgets = ref<HomeWidget[]>([])
const loading = ref(false)
const saving = ref(false)
const isLoaded = ref(false)

// 草稿状态：记录是否有未保存的修改
const hasUnsavedChanges = ref(false)

const normalizeWidgetKey = (key: string) => key === 'price_alerts' ? 'risk_alerts' : key

/**
 * 加载配置（字典已在 Layout.vue 预加载）
 */
const loadConfig = async (): Promise<void> => {
  if (isLoaded.value || loading.value) return
  loading.value = true

  try {
    // 加载布局配置
    const layoutDicts = getDictByCategory('home_layout')
    layoutDicts.forEach(dict => {
      const extraVal = dict.extraValue || ''
      switch (dict.dictKey) {
        case 'card_columns':
          layoutConfig.value.cardColumns = parseInt(extraVal) || 4
          break
        case 'card_columns_mobile':
          layoutConfig.value.cardColumnsMobile = parseInt(extraVal) || 2
          break
        case 'featured_product_count':
          layoutConfig.value.featuredProductCount = parseInt(extraVal) || 6
          break
        case 'show_trend_chart':
          layoutConfig.value.showTrendChart = extraVal === 'true'
          break
        case 'show_alerts':
          layoutConfig.value.showAlerts = extraVal === 'true'
          break
      }
    })

    // 加载组件配置
    const widgetDicts = getDictByCategory('home_widget')
    if (widgetDicts.length > 0) {
      widgets.value = widgetDicts
        .filter(d => d.status === 'ACTIVE')
        .map(dict => {
          let config: Record<string, any> = {}
          if (dict.extraValue) {
            try {
              config = JSON.parse(dict.extraValue)
            } catch {
              console.warn('Failed to parse widget config:', dict.dictKey)
            }
          }
          const key = normalizeWidgetKey(dict.dictKey)
          return {
            key,
            name: key === 'risk_alerts' ? '风险预警' : key === 'trend_chart' ? '重点走势' : dict.dictValue,
            enabled: config.enabled ?? true,
            order: config.order ?? 0,
            config
          }
        })
        .sort((a, b) => a.order - b.order)
    } else {
      widgets.value = defaultWidgets
    }

    isLoaded.value = true
    hasUnsavedChanges.value = false
  } catch (error) {
    console.error('Failed to load home config:', error)
  } finally {
    loading.value = false
  }
}

/**
 * 保存布局配置到后端
 */
const saveLayoutConfig = async (key: string, value: string): Promise<void> => {
  saving.value = true
  try {
    const dicts = getDictByCategory('home_layout')
    const dict = dicts.find(d => d.dictKey === key)
    if (dict) {
      await updateDict(dict.id, { extraValue: value })
    }
  } catch (error) {
    console.error('Failed to save layout config:', error)
    throw error
  } finally {
    saving.value = false
  }
}

/**
 * 保存组件配置到后端
 */
const saveWidgetConfig = async (widget: HomeWidget): Promise<void> => {
  saving.value = true
  try {
    const dicts = getDictByCategory('home_widget')
    const dict = dicts.find(d => normalizeWidgetKey(d.dictKey) === widget.key)
    if (dict) {
      const config = {
        ...widget.config,
        enabled: widget.enabled,
        order: widget.order
      }
      await updateDict(dict.id, { extraValue: JSON.stringify(config) })
    }
  } catch (error) {
    console.error('Failed to save widget config:', error)
    throw error
  } finally {
    saving.value = false
  }
}

/**
 * 组件开关（只更新本地状态，标记为未保存）
 */
const toggleWidget = (widget: HomeWidget): void => {
  widget.enabled = !widget.enabled
  hasUnsavedChanges.value = true
}

/**
 * 组件上移（只更新本地状态，标记为未保存）
 */
const moveUp = (widget: HomeWidget): void => {
  const index = widgets.value.findIndex(w => w.key === widget.key)
  if (index > 0) {
    const prevWidget = widgets.value[index - 1]
    widget.order = prevWidget.order
    prevWidget.order = widget.order + 1
    widgets.value.sort((a, b) => a.order - b.order)
    hasUnsavedChanges.value = true
  }
}

/**
 * 组件下移（只更新本地状态，标记为未保存）
 */
const moveDown = (widget: HomeWidget): void => {
  const index = widgets.value.findIndex(w => w.key === widget.key)
  if (index < widgets.value.length - 1) {
    const nextWidget = widgets.value[index + 1]
    widget.order = nextWidget.order
    nextWidget.order = widget.order - 1
    widgets.value.sort((a, b) => a.order - b.order)
    hasUnsavedChanges.value = true
  }
}

/**
 * 组件拖拽重排（只更新本地状态，标记为未保存）
 */
const reorderWidgetToIndex = (sourceKey: string, targetIndex: number): void => {
  const orderedWidgets = [...widgets.value].sort((a, b) => a.order - b.order)
  const sourceIndex = orderedWidgets.findIndex(widget => widget.key === sourceKey)

  if (sourceIndex < 0 || targetIndex < 0 || targetIndex > orderedWidgets.length) return

  const [movedWidget] = orderedWidgets.splice(sourceIndex, 1)
  const insertIndex = sourceIndex < targetIndex ? targetIndex - 1 : targetIndex
  if (sourceIndex === insertIndex) return

  orderedWidgets.splice(insertIndex, 0, movedWidget)
  orderedWidgets.forEach((widget, index) => {
    widget.order = index + 1
  })

  widgets.value = orderedWidgets
  hasUnsavedChanges.value = true
}

/**
 * Stepper 增减（只更新本地状态，标记为未保存）
 */
const increment = (field: NumericField, max: number): void => {
  const current = layoutConfig.value[field]
  if (current < max) {
    layoutConfig.value[field] = current + 1
    hasUnsavedChanges.value = true
  }
}

const decrement = (field: NumericField, min: number): void => {
  const current = layoutConfig.value[field]
  if (current > min) {
    layoutConfig.value[field] = current - 1
    hasUnsavedChanges.value = true
  }
}

/**
 * Switch 切换（只更新本地状态，标记为未保存）
 */
const toggleSwitch = (field: 'showTrendChart' | 'showAlerts'): void => {
  layoutConfig.value[field] = !layoutConfig.value[field]
  hasUnsavedChanges.value = true
}

/**
 * 保存所有修改到后端
 */
const saveAll = async (): Promise<void> => {
  if (!hasUnsavedChanges.value) return
  saving.value = true

  try {
    // 保存布局配置
    const savePromises: Promise<void>[] = []

    savePromises.push(saveLayoutConfig('card_columns', String(layoutConfig.value.cardColumns)))
    savePromises.push(saveLayoutConfig('card_columns_mobile', String(layoutConfig.value.cardColumnsMobile)))
    savePromises.push(saveLayoutConfig('featured_product_count', String(layoutConfig.value.featuredProductCount)))
    savePromises.push(saveLayoutConfig('show_trend_chart', layoutConfig.value.showTrendChart ? 'true' : 'false'))
    savePromises.push(saveLayoutConfig('show_alerts', layoutConfig.value.showAlerts ? 'true' : 'false'))

    // 保存组件配置
    widgets.value.forEach(widget => {
      savePromises.push(saveWidgetConfig(widget))
    })

    await Promise.all(savePromises)
    await refreshDictCache()
    hasUnsavedChanges.value = false
  } catch (error) {
    console.error('Failed to save home config:', error)
    throw error
  } finally {
    saving.value = false
  }
}

/**
 * 已启用组件数量
 */
const enabledWidgets = computed(() => widgets.value.filter(w => w.enabled))

const enabledCount = computed(() => enabledWidgets.value.length)

/**
 * 组合式 API
 */
export function useHomePreviewState() {
  return {
    // 状态
    layoutConfig: computed(() => layoutConfig.value),
    widgets: computed(() => widgets.value),
    loading: computed(() => loading.value),
    saving: computed(() => saving.value),
    isLoaded: computed(() => isLoaded.value),
    hasUnsavedChanges: computed(() => hasUnsavedChanges.value),

    // 计算属性
    enabledWidgets,
    enabledCount,

    // 方法
    loadConfig,
    toggleWidget,
    moveUp,
    moveDown,
    reorderWidgetToIndex,
    increment,
    decrement,
    toggleSwitch,
    saveAll
  }
}
