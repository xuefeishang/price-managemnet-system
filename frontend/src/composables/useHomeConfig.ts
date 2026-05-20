import { ref, computed } from 'vue'
import { getDictByCategory, loadAllDicts } from './useDict'

export interface HomeLayoutConfig {
  layoutMode: 'dashboard' | 'simple'
  cardColumns: number
  cardColumnsMobile: number
  showTrendChart: boolean
  showAlerts: boolean
  featuredProductCount: number
}

export interface HomeWidget {
  key: string
  name: string
  enabled: boolean
  order: number
  config?: Record<string, any>
}

const defaultLayoutConfig: HomeLayoutConfig = {
  layoutMode: 'dashboard',
  cardColumns: 4,
  cardColumnsMobile: 2,
  showTrendChart: true,
  showAlerts: true,
  featuredProductCount: 6
}

const layoutConfig = ref<HomeLayoutConfig>({ ...defaultLayoutConfig })
const widgets = ref<HomeWidget[]>([])
const chartRanges = ref<{ key: string; label: string; days: number }[]>([])

export function useHomeConfig() {
  const loadHomeConfig = async () => {
    await loadAllDicts()

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
    widgets.value = widgetDicts
      .filter(d => d.status === 'ACTIVE')
      .map(dict => {
        const config = dict.extraValue ? JSON.parse(dict.extraValue) : {}
        return {
          key: dict.dictKey,
          name: dict.dictValue,
          enabled: config.enabled ?? true,
          order: config.order ?? 0,
          config
        }
      })
      .sort((a, b) => a.order - b.order)

    // 加载图表时间范围
    const rangeDicts = getDictByCategory('chart_range')
    chartRanges.value = rangeDicts
      .filter(d => d.status === 'ACTIVE')
      .map(dict => ({
        key: dict.dictKey,
        label: dict.dictValue,
        days: parseInt(dict.extraValue || '30') || 30
      }))
      .sort((a, b) => a.days - b.days)
  }

  const enabledWidgets = computed(() => widgets.value.filter(w => w.enabled))

  const getWidgetConfig = (key: string): HomeWidget | undefined => {
    return widgets.value.find(w => w.key === key)
  }

  return {
    layoutConfig,
    widgets,
    enabledWidgets,
    chartRanges,
    loadHomeConfig,
    getWidgetConfig
  }
}