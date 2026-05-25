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

const defaultWidgets: HomeWidget[] = [
  { key: 'summary_stats', name: '经营摘要', enabled: true, order: 1 },
  { key: 'core_metrics', name: '核心指标', enabled: true, order: 2 },
  { key: 'trend_chart', name: '重点走势', enabled: true, order: 3 },
  { key: 'product_list', name: '产品列表', enabled: true, order: 4 },
  { key: 'risk_alerts', name: '风险预警', enabled: true, order: 5 }
]

const defaultChartRanges = [
  { key: '7d', label: '7日', days: 7 },
  { key: '30d', label: '30日', days: 30 },
  { key: '90d', label: '90日', days: 90 }
]

const layoutConfig = ref<HomeLayoutConfig>({ ...defaultLayoutConfig })
const widgets = ref<HomeWidget[]>([])
const chartRanges = ref<{ key: string; label: string; days: number }[]>([])

const normalizeWidgetKey = (key: string) => key === 'price_alerts' ? 'risk_alerts' : key

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
    const loadedWidgets = widgetDicts
      .filter(d => d.status === 'ACTIVE')
      .map(dict => {
        let config: Record<string, any> = {}
        if (dict.extraValue) {
          try {
            config = JSON.parse(dict.extraValue)
          } catch {
            console.warn('Failed to parse home widget config:', dict.dictKey)
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

    const widgetMap = new Map<string, HomeWidget>()
    defaultWidgets.forEach(widget => widgetMap.set(widget.key, { ...widget }))
    loadedWidgets.forEach(widget => widgetMap.set(widget.key, widget))
    widgets.value = Array.from(widgetMap.values()).sort((a, b) => a.order - b.order)

    // 加载图表时间范围
    const rangeDicts = getDictByCategory('chart_range')
    const loadedRanges = rangeDicts
      .filter(d => d.status === 'ACTIVE')
      .map(dict => ({
        key: dict.dictKey,
        label: dict.dictValue,
        days: parseInt(dict.extraValue || '30') || 30
      }))
      .sort((a, b) => a.days - b.days)
    chartRanges.value = loadedRanges.length > 0 ? loadedRanges : defaultChartRanges
  }

  const enabledWidgets = computed(() => widgets.value.filter(w => w.enabled))

  const getWidgetConfig = (key: string): HomeWidget | undefined => {
    const normalizedKey = normalizeWidgetKey(key)
    return widgets.value.find(w => w.key === normalizedKey)
  }

  const isWidgetEnabled = (key: string): boolean => {
    return getWidgetConfig(key)?.enabled ?? true
  }

  return {
    layoutConfig,
    widgets,
    enabledWidgets,
    chartRanges,
    loadHomeConfig,
    getWidgetConfig,
    isWidgetEnabled
  }
}
