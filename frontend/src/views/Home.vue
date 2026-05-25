<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProducts, getPricesByDate, getPriceTrend } from '@/api/products'
import { getCategories } from '@/api/categories'
import { getHomeDashboard, getHomeSummary, getPriceAlerts } from '@/api/home'
import { usePermission, Permission } from '@/composables/usePermission'
import { useTheme } from '@/composables/useTheme'
import { useLayout } from '@/composables/useLayout'
import { useHomeConfig } from '@/composables/useHomeConfig'
import { loadAllDicts, getCurrencySymbol, getOriginName } from '@/composables/useDict'
import { getCategoryVisual, getCategoryCardStyle, registerCategoryCodes } from '@/composables/useCategoryVisual'
import { eventBus } from '@/utils/eventBus'
import CategoryFilterPanel from '@/components/CategoryFilterPanel.vue'
import CategoryIcons from '@/components/icons/CategoryIcons.vue'
import SummarySection from '@/components/home/SummarySection.vue'
import TrendAnalysisChart from '@/components/home/TrendAnalysisChart.vue'
import RiskAlertsPanel from '@/components/home/RiskAlertsPanel.vue'
import type { HomeSummary, PriceAlert } from '@/api/home'
import type { ProductTrendItem } from '@/components/home/TrendAnalysisChart.vue'
import type { Product, Price, ProductCategory } from '@/types'

// ECharts 注册
use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const { hasPermission } = usePermission()
const { themeConfig } = useTheme()
const { isPCLayout, windowWidth } = useLayout()
const { layoutConfig, widgets, chartRanges, loadHomeConfig } = useHomeConfig()

// 状态
const loading = ref(false)
const error = ref<string | null>(null)
const searchQuery = ref('')
const searchQueryDebounced = ref('')
const selectedCategoryIds = ref<number[]>([])

// 产品数据
const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const priceMap = ref<Map<number, Price>>(new Map())
const previousPriceMap = ref<Map<number, Price>>(new Map())
const priceHistoryMap = ref<Map<number, any[]>>(new Map())
const chartOptionsMap = ref<Map<number, any>>(new Map())
const homeSummary = ref<HomeSummary | null>(null)
const priceAlerts = ref<PriceAlert[]>([])

// 日期选择
const getYesterday = () => {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}
const selectedDate = ref(getYesterday())
const trendDays = ref(30)

// 计算属性
const gridCols = computed(() => {
  if (windowWidth.value >= 1400) return layoutConfig.value.cardColumns
  if (windowWidth.value >= 1024) return Math.min(layoutConfig.value.cardColumns, 3)
  return layoutConfig.value.cardColumnsMobile
})

const visibleHomeSections = computed(() => {
  const configured = widgets.value.length > 0
    ? widgets.value.filter(widget => widget.enabled)
    : [
        { key: 'summary_stats', name: '经营摘要', enabled: true, order: 1 },
        { key: 'core_metrics', name: '核心指标', enabled: true, order: 2 },
        { key: 'trend_chart', name: '重点走势', enabled: true, order: 3 },
        { key: 'product_list', name: '产品列表', enabled: true, order: 4 },
        { key: 'risk_alerts', name: '风险预警', enabled: true, order: 5 }
      ]

  return configured
    .map(widget => ({ ...widget, key: widget.key === 'price_alerts' ? 'risk_alerts' : widget.key }))
    .filter(widget => {
      if (widget.key === 'trend_chart') return layoutConfig.value.showTrendChart
      if (widget.key === 'risk_alerts') return layoutConfig.value.showAlerts
      return ['summary_stats', 'core_metrics', 'trend_chart', 'product_list', 'risk_alerts'].includes(widget.key)
    })
    .sort((a, b) => a.order - b.order)
})

const getCategorySortOrder = (product: Product) => {
  const categoryId = getProductCategoryId(product)
  if (!categoryId) return Number.MAX_SAFE_INTEGER
  return categoryMap.value.get(categoryId)?.sortOrder ?? product.category?.sortOrder ?? Number.MAX_SAFE_INTEGER
}

const sortProductsByHomeOrder = (items: Product[]) =>
  [...items].sort((a, b) =>
    getCategorySortOrder(a) - getCategorySortOrder(b) ||
    (a.sortOrder ?? 0) - (b.sortOrder ?? 0) ||
    a.name.localeCompare(b.name, 'zh-CN') ||
    a.id - b.id
  )

const filteredProducts = computed(() => {
  const active = sortProductsByHomeOrder(products.value.filter(p => p.status === 'ACTIVE'))

  // 分类筛选
  if (selectedCategoryIds.value.length > 0) {
    const filtered = active.filter(p =>
      getProductCategoryId(p) && selectedCategoryIds.value.includes(getProductCategoryId(p)!)
    )
    if (filtered.length === 0) return []
    if (!searchQueryDebounced.value) return filtered
    const q = searchQueryDebounced.value.toLowerCase()
    return filtered.filter(p => p.name.toLowerCase().includes(q))
  }

  // 名称搜索
  if (!searchQueryDebounced.value) return active
  const q = searchQueryDebounced.value.toLowerCase()
  return active.filter(p => p.name.toLowerCase().includes(q))
})

const homeProducts = computed(() =>
  sortProductsByHomeOrder(products.value.filter(p => p.showOnHome && p.status === 'ACTIVE'))
)

const categoryMap = computed(() => {
  const map = new Map<number, ProductCategory>()
  categories.value.forEach(category => map.set(category.id, category))
  return map
})

const filteredProductGroups = computed(() => {
  const groups = new Map<number | string, {
    id: number | string
    name: string
    category?: ProductCategory
    products: Product[]
  }>()

  filteredProducts.value.forEach(product => {
    const categoryId = getProductCategoryId(product)
    const category = categoryId ? (categoryMap.value.get(categoryId) || product.category) : undefined
    const groupId = categoryId || 'uncategorized'
    const groupName = category?.name || '未分类'

    if (!groups.has(groupId)) {
      groups.set(groupId, {
        id: groupId,
        name: groupName,
        category,
        products: []
      })
    }
    groups.get(groupId)!.products.push(product)
  })

  groups.forEach(group => {
    group.products = sortProductsByHomeOrder(group.products)
  })

  return Array.from(groups.values()).sort((a, b) => {
    if (a.id === 'uncategorized') return 1
    if (b.id === 'uncategorized') return -1
    return (categoryMap.value.get(Number(a.id))?.sortOrder ?? a.category?.sortOrder ?? 0) -
      (categoryMap.value.get(Number(b.id))?.sortOrder ?? b.category?.sortOrder ?? 0) ||
      a.name.localeCompare(b.name, 'zh-CN') ||
      Number(a.id) - Number(b.id)
  })
})

const priceChangeCache = computed(() => {
  const cache = new Map<number, ReturnType<typeof getPriceChangeInfo>>()
  products.value.forEach(p => cache.set(p.id, getPriceChangeInfo(p.id)))
  return cache
})

const lastPriceCache = computed(() => {
  const cache = new Map<number, string | null>()
  products.value.forEach(p => cache.set(p.id, getLastPriceInfo(p.id)))
  return cache
})

const summaryForDisplay = computed<HomeSummary>(() => {
  if (homeSummary.value) return homeSummary.value

  const activeProducts = products.value.filter(p => p.status === 'ACTIVE')
  const changes = activeProducts
    .map(product => {
      const current = priceMap.value.get(product.id)?.currentPrice
      const previous = previousPriceMap.value.get(product.id)?.currentPrice
      if (current == null || previous == null || previous === 0) return null
      return ((current - previous) / previous) * 100
    })
    .filter((value): value is number => value != null)

  const risingCount = changes.filter(value => value > 0).length
  const fallingCount = changes.filter(value => value < 0).length
  const avgPriceChange = changes.length
    ? changes.reduce((sum, value) => sum + value, 0) / changes.length
    : 0

  return {
    totalProducts: activeProducts.length,
    priceUpdatedToday: priceMap.value.size,
    avgPriceChange,
    risingCount,
    fallingCount,
    flatCount: Math.max(changes.length - risingCount - fallingCount, 0)
  }
})

const trendRangesForDisplay = computed(() => chartRanges.value.length > 0
  ? chartRanges.value
  : [
      { key: '7d', label: '7日', days: 7 },
      { key: '30d', label: '30日', days: 30 },
      { key: '90d', label: '90日', days: 90 }
    ])

const normalizeTrendDirection = (direction?: string): ProductTrendItem['direction'] => {
  if (direction === 'up' || direction === 'down') return direction
  return 'flat'
}

const parseOriginIds = (originIds?: string) => {
  if (!originIds) return []
  try {
    const parsed = JSON.parse(originIds)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

const getProductOriginLabel = (product: Pick<Product, 'originIds'>) => {
  const originNames = parseOriginIds(product.originIds)
    .map(key => getOriginName(key))
    .filter(Boolean)
    .join(' / ')
  return originNames
}

const hasProductOrigin = (product: Pick<Product, 'originIds'>) => getProductOriginLabel(product).length > 0

const trendProductItems = computed<ProductTrendItem[]>(() =>
  homeProducts.value
    .slice(0, Math.min(layoutConfig.value.featuredProductCount, 4))
    .map(product => {
      const history = priceHistoryMap.value.get(product.id) || buildFallbackTrend(product.id)
      const change = priceChangeCache.value.get(product.id)
      const categoryVisual = getCategoryVisual(getProductCategoryId(product))

      return {
        id: product.id,
        name: product.name,
        specs: product.specs,
        originLabel: getProductOriginLabel(product),
        hasOrigin: hasProductOrigin(product),
        currencySymbol: getCurrencySymbolLocal(product.currency),
        unit: getTodayPrice(product.id)?.unit || product.unit,
        direction: normalizeTrendDirection(change?.direction),
        formattedDiff: change?.formattedDiff || '0',
        currentPrice: getTodayPrice(product.id)?.currentPrice ?? null,
        points: history.map(item => ({
          date: item.date || item.effectiveDate || item.createdTime,
          price: item.currentPrice ?? item.newPrice ?? null
        })).filter(point => point.date),
        lineColor: categoryVisual.chartLineColor || categoryVisual.primaryColor,
        areaColor: categoryVisual.chartAreaColor || categoryVisual.glowColor
      }
    })
)

// 方法
const getPriceChangeInfo = (productId: number) => {
  const current = priceMap.value.get(productId)
  const previous = previousPriceMap.value.get(productId)
  if (!current || !previous) return null
  const currentVal = current.currentPrice
  const previousVal = previous.currentPrice
  if (currentVal == null || previousVal == null) return null
  const diff = currentVal - previousVal
  if (diff === 0) return { direction: 'flat', diff: 0, formattedDiff: '0' }
  const formattedDiff = diff > 0
    ? `+${diff.toFixed(2).replace(/\.?0+$/, '')}`
    : diff.toFixed(2).replace(/\.?0+$/, '')
  return { direction: diff > 0 ? 'up' : 'down', diff, formattedDiff }
}

const getLastPriceInfo = (productId: number): string | null => {
  const todayPrice = priceMap.value.get(productId)
  if (todayPrice && todayPrice.currentPrice != null) {
    return String(todayPrice.currentPrice)
  }
  const history = priceHistoryMap.value.get(productId)
  if (history && history.length > 0) {
    const latest = history[history.length - 1]
    if (latest && latest.currentPrice != null) return String(latest.currentPrice)
  }
  return null
}

const getTodayPrice = (productId: number) => priceMap.value.get(productId)

const buildFallbackTrend = (productId: number) => {
  const current = priceMap.value.get(productId)
  const previous = previousPriceMap.value.get(productId)
  const points = []

  if (previous?.currentPrice != null) {
    points.push({
      date: getPreviousDate(selectedDate.value),
      currentPrice: previous.currentPrice
    })
  }

  if (current?.currentPrice != null) {
    points.push({
      date: selectedDate.value,
      currentPrice: current.currentPrice
    })
  }

  return points
}

const generateChartOption = (productId: number) => {
  const history = priceHistoryMap.value.get(productId) || buildFallbackTrend(productId)
  if (history.length === 0) return null

  const recent = history.slice(-30)
  const dates = recent.map(h => {
    const d = new Date(h.date)
    return `${d.getMonth() + 1}/${d.getDate()}`
  })
  const prices = recent.map(h => h.currentPrice).filter((price: number | null | undefined) => price != null)
  if (prices.length === 0) return null

  const product = products.value.find(item => item.id === productId)
  const categoryVisual = getCategoryVisual(product ? getProductCategoryId(product) : undefined)
  const lineColor = categoryVisual.chartLineColor || categoryVisual.primaryColor || themeConfig.value.chartPrimaryColor || '#0D6E6E'
  const areaColor = categoryVisual.chartAreaColor || categoryVisual.glowColor || 'rgba(13, 110, 110, 0.12)'

  return {
    grid: { left: 0, right: 0, top: 2, bottom: 0 },
    xAxis: { type: 'category', show: false, data: dates },
    yAxis: { type: 'value', show: false },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        return p ? `${p.axisValue}<br/>价格: ${p.value}` : ''
      },
      confine: true,
      textStyle: { fontSize: 10 }
    },
    series: [{
      type: 'line',
      data: prices,
      smooth: true,
      symbol: 'none',
      itemStyle: { color: lineColor },
      lineStyle: { width: 1.5, color: lineColor },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: areaColor },
            { offset: 1, color: 'rgba(255, 255, 255, 0)' }
          ]
        }
      }
    }]
  }
}

const formatDateDisplay = (dateStr: string) => {
  const date = new Date(dateStr + 'T00:00:00')
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

const getPreviousDate = (dateStr: string) => {
  const date = new Date(dateStr + 'T00:00:00')
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}

const loadHomeDashboardData = async () => {
  try {
    const dashboardRes = await getHomeDashboard(selectedDate.value)
    homeSummary.value = dashboardRes.data?.summary || null
    priceAlerts.value = dashboardRes.data?.alerts || []
  } catch (dashboardError) {
    console.warn('Failed to load dashboard aggregate, fallback to separate APIs:', dashboardError)
    const [summaryRes, alertsRes] = await Promise.allSettled([
      getHomeSummary(selectedDate.value),
      getPriceAlerts(selectedDate.value)
    ])

    homeSummary.value = summaryRes.status === 'fulfilled' ? summaryRes.value.data : null
    priceAlerts.value = alertsRes.status === 'fulfilled' ? (alertsRes.value.data || []) : []
  }
}

const loadData = async () => {
  loading.value = true
  error.value = null
  try {
    const prevDate = getPreviousDate(selectedDate.value)
    const [productsRes, pricesRes, prevPricesRes] = await Promise.all([
      getProducts({ page: 0, size: 100 }),
      getPricesByDate(selectedDate.value),
      getPricesByDate(prevDate)
    ])
    await loadHomeDashboardData()

    products.value = productsRes.data.content || []
    priceMap.value.clear()
    previousPriceMap.value.clear()
    chartOptionsMap.value.clear()

    const prices = pricesRes.data || []
    prices.forEach((price: Price) => {
      if (price.product?.id) priceMap.value.set(price.product.id, price)
    })

    const prevPrices = prevPricesRes.data || []
    prevPrices.forEach((price: Price) => {
      if (price.product?.id) previousPriceMap.value.set(price.product.id, price)
    })

    // 加载价格历史（批量）
    const allProducts = products.value
    const batchSize = 5
    const delay = (ms: number) => new Promise(r => setTimeout(r, ms))

    for (let i = 0; i < allProducts.length; i += batchSize) {
      const batch = allProducts.slice(i, i + batchSize)
      await Promise.all(batch.map(async (product) => {
        try {
          const trendRes = await getPriceTrend(product.id, 30)
          const trendData = (trendRes.data && trendRes.data.length > 0)
            ? trendRes.data
            : buildFallbackTrend(product.id)
          priceHistoryMap.value.set(product.id, trendData)
          const option = generateChartOption(product.id)
          if (option) chartOptionsMap.value.set(product.id, option)
        } catch (e) {
          console.error(`Failed to load price trend for product ${product.id}:`, e)
        }
      }))
      if (i + batchSize < allProducts.length) await delay(50)
    }
  } catch (err: any) {
    error.value = err?.message || '加载数据失败，请重试'
    console.error('Failed to load data:', err)
  } finally {
    loading.value = false
  }
}

const onRefresh = () => loadData()
const onDateChange = () => loadData()

const onTrendRangeChange = async (days: number) => {
  trendDays.value = days
  // 重新加载趋势数据
  for (const product of homeProducts.value.slice(0, 6)) {
    try {
      const trendRes = await getPriceTrend(product.id, days)
      const trendData = (trendRes.data && trendRes.data.length > 0)
        ? trendRes.data
        : buildFallbackTrend(product.id)
      priceHistoryMap.value.set(product.id, trendData)
      const option = generateChartOption(product.id)
      if (option) chartOptionsMap.value.set(product.id, option)
    } catch (e) {
      console.error(`Failed to load trend for ${product.id}:`, e)
    }
  }
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(searchQuery, (val) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { searchQueryDebounced.value = val }, 300)
})

const switchTab = (tab: string) => {
  activeTab.value = tab
  switch (tab) {
    case 'home': router.push('/home'); break
    case 'products': router.push('/products'); break
    case 'import': router.push('/import'); break
    case 'profile': router.push('/profile'); break
  }
}

const viewProduct = (product: Product) => router.push(`/product-detail/${product.id}`)
const goToPriceMaintenance = () => router.push('/price-maintenance')

// 分类筛选处理
const handleCategorySelect = (ids: number[]) => {
  selectedCategoryIds.value = ids
}

const clearCategoryFilter = () => {
  selectedCategoryIds.value = []
}

// 获取产品的分类ID
const getProductCategoryId = (product: Product): number | undefined => {
  return product.categoryId || product.category?.id
}

// 获取产品卡片分类样式
const getCardStyle = (product: Product) => {
  const catId = getProductCategoryId(product)
  if (!catId) return {}
  const style = getCategoryCardStyle(catId)
  console.log(`[Home] getCardStyle for product ${product.name} (categoryId=${catId}):`, style)
  return style
}

const getCardClass = (product: Product) => {
  return getProductCategoryId(product) ? 'has-category' : ''
}

const activeTab = ref('home')
const getCurrencySymbolLocal = getCurrencySymbol
let unsubscribeProductSort: (() => void) | null = null
let unsubscribeCategorySort: (() => void) | null = null

const loadCategories = async () => {
  try {
    const catRes = await getCategories('ACTIVE')
    const cats = [...(catRes.data || [])].sort((a, b) =>
      (a.sortOrder ?? 0) - (b.sortOrder ?? 0) ||
      a.name.localeCompare(b.name, 'zh-CN') ||
      a.id - b.id
    )
    categories.value = cats
    registerCategoryCodes(cats.map(c => ({ id: c.id, code: c.code })))
  } catch (e) {
    console.error('Failed to load categories:', e)
  }
}

onMounted(async () => {
  // 加载分类数据并注册映射（必须在渲染产品卡片前完成）
  await loadCategories()

  await Promise.all([
    loadAllDicts(),
    loadHomeConfig()
  ])

  loadData()
  unsubscribeProductSort = eventBus.on('product-sort-updated', loadData)
  unsubscribeCategorySort = eventBus.on('category-sort-updated', async () => {
    await loadCategories()
    await loadData()
  })
})

onUnmounted(() => {
  unsubscribeProductSort?.()
  unsubscribeCategorySort?.()
})
</script>

<template>
  <div class="home-page">
    <!-- ==================== PC布局 - 驾驶舱模式 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-home">
        <!-- 页面标题区 -->
        <div class="page-header-pc">
          <div class="header-left-pc">
            <div class="date-picker-wrapper">
              <input type="date" v-model="selectedDate" @change="onDateChange" class="date-input-pc" />
            </div>
            <h1 class="page-title-pc">价格概览</h1>
          </div>
          <div class="header-actions-pc">
            <button class="btn-icon-pc" @click="onRefresh" :disabled="loading" title="刷新">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: loading }">
                <path d="M21 2v6h-6"/><path d="M3 12a9 9 0 0 1 15-6.7L21 8"/>
                <path d="M3 22v-6h6"/><path d="M21 12a9 9 0 0 1-15 6.7L3 16"/>
              </svg>
            </button>
            <button class="btn-primary-pc" @click="goToPriceMaintenance" v-if="hasPermission(Permission.PRODUCT_EDIT)">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              价格维护
            </button>
          </div>
        </div>

        <!-- 错误提示 -->
        <div v-if="error" class="alert-error">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          {{ error }}
          <button @click="onRefresh">重试</button>
        </div>

        <!-- 骨架屏 -->
        <template v-if="loading">
          <div class="skeleton-summary">
            <div v-for="i in 4" :key="i" class="skeleton-stat"></div>
          </div>
          <div class="product-grid-pc" :style="{ gridTemplateColumns: `repeat(${gridCols}, 1fr)` }">
            <div v-for="i in 8" :key="i" class="skeleton-card-pc"></div>
          </div>
        </template>

        <template v-else>
          <template v-for="section in visibleHomeSections" :key="section.key">
          <SummarySection
            v-if="section.key === 'summary_stats'"
            :summary="summaryForDisplay"
          />

          <!-- 重点关注指标区 - 大卡片 -->
          <div class="home-featured-pc" v-else-if="section.key === 'core_metrics' && homeProducts.length > 0">
            <div class="section-header-pc">
              <h2 class="section-title-pc">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                </svg>
                重点关注指标
              </h2>
              <div class="trend-range-tabs">
                <button
                  v-for="range in trendRangesForDisplay"
                  :key="range.key"
                  class="range-tab"
                  :class="{ active: trendDays === range.days }"
                  @click="onTrendRangeChange(range.days)"
                >
                  {{ range.label }}
                </button>
              </div>
            </div>
            <div class="product-grid-pc featured" :style="{ gridTemplateColumns: `repeat(${gridCols}, 1fr)` }">
              <div
                v-for="product in homeProducts.slice(0, layoutConfig.featuredProductCount)"
                :key="product.id"
                class="product-card-pc featured"
                :class="getCardClass(product)"
                :style="getCardStyle(product)"
                @click="viewProduct(product)"
              >
                <div class="card-category-icon" v-if="getProductCategoryId(product)">
                  <CategoryIcons
                    :icon="getCategoryVisual(getProductCategoryId(product)).icon"
                    :size="16"
                    :color="getCategoryVisual(getProductCategoryId(product)).primaryColor"
                  />
                </div>
                <div class="card-top">
                  <div class="card-title-row">
                    <span class="product-name" :class="{ 'category-name': getProductCategoryId(product) }">{{ product.name }}</span>
                    <span class="trend-badge" :class="priceChangeCache.get(product.id)?.direction || 'flat'" v-if="priceChangeCache.get(product.id)">
                      {{ priceChangeCache.get(product.id)?.direction === 'up' ? '↑' : priceChangeCache.get(product.id)?.direction === 'down' ? '↓' : '—' }}
                      {{ priceChangeCache.get(product.id)?.formattedDiff }}
                    </span>
                    <span class="trend-badge flat" v-else>—</span>
                  </div>
                  <div class="product-meta-stack">
                    <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
                    <div class="product-origin" v-if="hasProductOrigin(product)">
                      <span class="origin-label">产地</span>
                      <span class="origin-value">{{ getProductOriginLabel(product) }}</span>
                    </div>
                  </div>
                </div>
                <div class="card-bottom">
                  <div class="price-row">
                    <span class="price-value" v-if="lastPriceCache.get(product.id)">
                      {{ getCurrencySymbolLocal(product.currency) }}{{ lastPriceCache.get(product.id) }}
                    </span>
                    <span class="price-value empty" v-else>--</span>
                    <span class="price-unit" v-if="getTodayPrice(product.id)?.unit || product.unit">
                      / {{ getTodayPrice(product.id)?.unit || product.unit }}
                    </span>
                  </div>
                  <div class="chart-area" v-if="chartOptionsMap.get(product.id)">
                    <v-chart class="mini-chart" :option="chartOptionsMap.get(product.id)" autoresize />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <TrendAnalysisChart
            v-else-if="section.key === 'trend_chart' && trendProductItems.length > 0"
            class="featured-trend-cards"
            title="重点走势"
            :show-overview="false"
            :products="trendProductItems"
            :columns="2"
            @range-change="onTrendRangeChange"
          />

          <!-- 产品列表区 -->
          <div class="product-section-pc" v-else-if="section.key === 'product_list'">
            <div class="section-header-pc">
              <h2 class="section-title-pc">产品列表</h2>
              <div class="search-area-pc">
                <!-- 分类筛选按钮 -->
                <CategoryFilterPanel
                  :selected-ids="selectedCategoryIds"
                  :multi-select="true"
                  @select="handleCategorySelect"
                  @clear="clearCategoryFilter"
                />
                <!-- 搜索框 -->
                <div class="search-box-pc">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                  </svg>
                  <input v-model="searchQuery" type="text" placeholder="搜索产品..." class="search-input-pc" />
                </div>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-if="filteredProducts.length === 0" class="empty-state-pc">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/><path d="M3 5h18M3 19h18M12 9v4"/>
              </svg>
              <p>{{ searchQuery ? '未找到匹配的产品' : selectedCategoryIds.length > 0 ? '当前分类暂无产品' : '暂无产品数据' }}</p>
            </div>

            <!-- 产品网格 -->
            <div v-else class="category-product-groups">
              <section
                v-for="group in filteredProductGroups"
                :key="group.id"
                class="category-product-group"
              >
                <div class="category-group-header" :style="group.category ? getCategoryCardStyle(group.category.id) : {}">
                  <CategoryIcons
                    v-if="group.category"
                    :icon="getCategoryVisual(group.category.id).icon"
                    :size="16"
                    :color="getCategoryVisual(group.category.id).primaryColor"
                  />
                  <span class="category-group-name">{{ group.name }}</span>
                  <span class="category-group-count">{{ group.products.length }} 个产品</span>
                </div>
                <div class="product-grid-pc" :style="{ gridTemplateColumns: `repeat(${gridCols}, 1fr)` }">
                  <div
                    v-for="product in group.products"
                    :key="product.id"
                    class="product-card-pc"
                    :class="getCardClass(product)"
                    :style="getCardStyle(product)"
                    @click="viewProduct(product)"
                  >
                    <!-- 分类图标 -->
                    <div class="card-category-icon" v-if="getProductCategoryId(product)">
                      <CategoryIcons
                        :icon="getCategoryVisual(getProductCategoryId(product)).icon"
                        :size="16"
                        :color="getCategoryVisual(getProductCategoryId(product)).primaryColor"
                      />
                    </div>
                    <div class="card-top">
                      <div class="card-title-row">
                        <span class="product-name" :class="{ 'category-name': getProductCategoryId(product) }">{{ product.name }}</span>
                        <span class="trend-badge" :class="priceChangeCache.get(product.id)?.direction || 'flat'" v-if="priceChangeCache.get(product.id)">
                          {{ priceChangeCache.get(product.id)?.direction === 'up' ? '↑' : priceChangeCache.get(product.id)?.direction === 'down' ? '↓' : '—' }}
                          {{ priceChangeCache.get(product.id)?.formattedDiff }}
                        </span>
                      </div>
                      <div class="product-meta-stack">
                        <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
                        <div class="product-origin" v-if="hasProductOrigin(product)">
                          <span class="origin-label">产地</span>
                          <span class="origin-value">{{ getProductOriginLabel(product) }}</span>
                        </div>
                      </div>
                    </div>
                    <div class="card-bottom">
                      <div class="price-row">
                        <span class="price-value" v-if="lastPriceCache.get(product.id)">
                          {{ getCurrencySymbolLocal(product.currency) }}{{ lastPriceCache.get(product.id) }}
                        </span>
                        <span class="price-value empty" v-else>--</span>
                        <span class="price-unit" v-if="getTodayPrice(product.id)?.unit || product.unit">
                          / {{ getTodayPrice(product.id)?.unit || product.unit }}
                        </span>
                      </div>
                      <div class="chart-area" v-if="chartOptionsMap.get(product.id)">
                        <v-chart class="mini-chart" :option="chartOptionsMap.get(product.id)" autoresize />
                      </div>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </div>

          <RiskAlertsPanel
            v-else-if="section.key === 'risk_alerts'"
            :alerts="priceAlerts"
          />
          </template>
        </template>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <header class="navbar">
        <div class="navbar-left">
          <h1 class="navbar-title">{{ formatDateDisplay(selectedDate) }}</h1>
        </div>
        <div class="navbar-right">
          <button class="btn-icon-mobile" @click="onRefresh" :disabled="loading" title="刷新">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: loading }">
              <path d="M21 2v6h-6"/><path d="M3 12a9 9 0 0 1 15-6.7L21 8"/>
            </svg>
          </button>
          <input type="date" v-model="selectedDate" @change="onDateChange" class="date-input-mobile" />
        </div>
      </header>

      <main class="content">
        <div class="date-tip">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          <span>{{ formatDateDisplay(selectedDate) }} 价格</span>
        </div>

        <div v-if="error" class="alert-error-mobile">
          <span>{{ error }}</span>
          <button @click="onRefresh">重试</button>
        </div>

        <div v-if="loading" class="loading-scroll-mobile">
          <div v-for="i in 4" :key="i" class="skeleton-card-mobile"></div>
        </div>

        <template v-else>
          <template v-for="section in visibleHomeSections" :key="section.key">
          <SummarySection
            v-if="section.key === 'summary_stats'"
            :summary="summaryForDisplay"
          />

          <div class="home-featured-mobile" v-else-if="section.key === 'core_metrics' && homeProducts.length > 0">
            <div class="section-header">
              <h2 class="section-title">重点关注指标</h2>
            </div>
            <div class="home-featured-scroll">
              <div
                v-for="product in homeProducts.slice(0, layoutConfig.featuredProductCount)"
                :key="product.id"
                class="home-featured-item-mobile"
                :class="getCardClass(product)"
                :style="getCardStyle(product)"
                @click="viewProduct(product)"
              >
                <div class="item-category-icon" v-if="getProductCategoryId(product)">
                  <CategoryIcons
                    :icon="getCategoryVisual(getProductCategoryId(product)).icon"
                    :size="14"
                    :color="getCategoryVisual(getProductCategoryId(product)).primaryColor"
                  />
                </div>
                <div class="card-top">
                  <div class="card-title-row">
                    <span class="product-name" :class="{ 'category-name': getProductCategoryId(product) }">{{ product.name }}</span>
                  </div>
                  <div class="product-meta-stack">
                    <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
                    <div class="product-origin" v-if="hasProductOrigin(product)">
                      <span class="origin-label">产地</span>
                      <span class="origin-value">{{ getProductOriginLabel(product) }}</span>
                    </div>
                  </div>
                </div>
                <div class="card-bottom">
                  <div class="price-row">
                    <span class="price-value" v-if="lastPriceCache.get(product.id)">
                      {{ getCurrencySymbolLocal(product.currency) }}{{ lastPriceCache.get(product.id) }}
                    </span>
                    <span class="price-value empty" v-else>--</span>
                    <span class="price-unit" v-if="getTodayPrice(product.id)?.unit || product.unit">
                      / {{ getTodayPrice(product.id)?.unit || product.unit }}
                    </span>
                  </div>
                  <span class="trend-badge" :class="priceChangeCache.get(product.id)?.direction || 'flat'" v-if="priceChangeCache.get(product.id)">
                    {{ priceChangeCache.get(product.id)?.direction === 'up' ? '↑' : priceChangeCache.get(product.id)?.direction === 'down' ? '↓' : '—' }}
                    {{ priceChangeCache.get(product.id)?.formattedDiff }}
                  </span>
                </div>
                <div class="chart-area-sm featured-mobile-chart" v-if="chartOptionsMap.get(product.id)">
                  <v-chart class="mini-chart-sm" :option="chartOptionsMap.get(product.id)" autoresize />
                </div>
              </div>
            </div>
          </div>

          <TrendAnalysisChart
            v-else-if="section.key === 'trend_chart' && trendProductItems.length > 0"
            class="featured-trend-cards"
            title="重点走势"
            :show-overview="false"
            :products="trendProductItems"
            :columns="1"
            @range-change="onTrendRangeChange"
          />

          <div class="product-section" v-else-if="section.key === 'product_list'">
            <div class="section-header">
              <h2 class="section-title">产品列表</h2>
              <button class="add-btn" @click="goToPriceMaintenance" v-if="hasPermission(Permission.PRODUCT_EDIT)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
            </div>

            <div class="search-bar">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input v-model="searchQuery" type="text" placeholder="搜索产品..." class="search-input" />
            </div>

            <CategoryFilterPanel
              :selected-ids="selectedCategoryIds"
              :multi-select="true"
              @select="handleCategorySelect"
              @clear="clearCategoryFilter"
            />

            <div v-if="filteredProducts.length === 0" class="empty-state-mobile">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/><path d="M3 5h18M3 19h18M12 9v4"/>
              </svg>
              <p>{{ searchQuery ? '未找到匹配的产品' : selectedCategoryIds.length > 0 ? '当前分类暂无产品' : '暂无产品数据' }}</p>
            </div>

            <div v-else class="category-product-groups mobile">
              <section
                v-for="group in filteredProductGroups"
                :key="group.id"
                class="category-product-group"
              >
                <div class="category-group-header" :style="group.category ? getCategoryCardStyle(group.category.id) : {}">
                  <CategoryIcons
                    v-if="group.category"
                    :icon="getCategoryVisual(group.category.id).icon"
                    :size="14"
                    :color="getCategoryVisual(group.category.id).primaryColor"
                  />
                  <span class="category-group-name">{{ group.name }}</span>
                  <span class="category-group-count">{{ group.products.length }} 个产品</span>
                </div>
                <div class="product-list">
                  <div
                    v-for="product in group.products"
                    :key="product.id"
                    class="product-item"
                    :class="getCardClass(product)"
                    :style="getCardStyle(product)"
                    @click="viewProduct(product)"
                  >
                    <!-- 分类图标 -->
                    <div class="item-category-icon" v-if="getProductCategoryId(product)">
                      <CategoryIcons
                        :icon="getCategoryVisual(getProductCategoryId(product)).icon"
                        :size="14"
                        :color="getCategoryVisual(getProductCategoryId(product)).primaryColor"
                      />
                    </div>
                    <div class="item-main">
                      <div class="item-header">
                        <span class="product-name" :class="{ 'category-name': getProductCategoryId(product) }">{{ product.name }}</span>
                        <span class="trend-badge" :class="priceChangeCache.get(product.id)?.direction || 'flat'" v-if="priceChangeCache.get(product.id)">
                          {{ priceChangeCache.get(product.id)?.direction === 'up' ? '↑' : priceChangeCache.get(product.id)?.direction === 'down' ? '↓' : '—' }}
                          {{ priceChangeCache.get(product.id)?.formattedDiff }}
                        </span>
                      </div>
                      <div class="product-meta-stack">
                        <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
                        <div class="product-origin" v-if="hasProductOrigin(product)">
                          <span class="origin-label">产地</span>
                          <span class="origin-value">{{ getProductOriginLabel(product) }}</span>
                        </div>
                      </div>
                    </div>
                    <div class="item-aside">
                      <div class="price-row">
                        <span class="price-value" v-if="lastPriceCache.get(product.id)">
                          {{ getCurrencySymbolLocal(product.currency) }}{{ lastPriceCache.get(product.id) }}
                        </span>
                        <span class="price-value empty" v-else>--</span>
                        <span class="price-unit" v-if="getTodayPrice(product.id)?.unit || product.unit">
                          / {{ getTodayPrice(product.id)?.unit || product.unit }}
                        </span>
                      </div>
                      <div class="chart-area-sm" v-if="chartOptionsMap.get(product.id)">
                        <v-chart class="mini-chart-sm" :option="chartOptionsMap.get(product.id)" autoresize />
                      </div>
                    </div>
                  </div>
                </div>
              </section>
            </div>
          </div>

          <RiskAlertsPanel
            v-else-if="section.key === 'risk_alerts'"
            :alerts="priceAlerts"
          />
          </template>
        </template>
      </main>

      <footer class="tab-bar">
        <button class="tab-item" :class="{ active: activeTab === 'home' }" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'products' }" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/><path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'import' }" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'profile' }" @click="switchTab('profile')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>
          </svg>
          <span class="tab-label">我的</span>
        </button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.home-page {
  background-color: var(--bg-page);
}

/* ==================== PC布局 ==================== */
.pc-home {
  padding: var(--spacing-xl);
  max-width: 1400px;
  margin: 0 auto;
}

.page-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-xl);
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.header-left-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
  flex-wrap: wrap;
}

.page-title-pc {
  font-family: var(--font-heading);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.date-picker-wrapper {
  flex-shrink: 0;
}

.date-input-pc {
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  background: var(--bg-card);
  cursor: pointer;
  outline: none;
  transition: border-color var(--transition-fast);
}

.date-input-pc:focus { border-color: var(--primary-color); }

.header-actions-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.btn-primary-pc {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--gradient-primary);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  box-shadow: var(--shadow);
}

.btn-primary-pc:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

.btn-icon-pc {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  cursor: pointer;
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.btn-icon-pc:hover:not(:disabled) {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.btn-icon-pc:disabled { opacity: 0.5; cursor: not-allowed; }

@keyframes spin { to { transform: rotate(360deg); } }
.spinning { animation: spin 1s linear infinite; }

.alert-error {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--error-bg);
  color: var(--error-color);
  border-radius: var(--radius);
  font-size: var(--font-size-sm);
  margin-bottom: var(--spacing-lg);
}

.alert-error button {
  margin-left: auto;
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--error-color);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: var(--font-size-xs);
}

/* 骨架屏 */
.skeleton-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.skeleton-stat {
  height: 80px;
  background: linear-gradient(90deg, var(--gray-100) 25%, var(--gray-50) 50%, var(--gray-100) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-lg);
}

.skeleton-card-pc {
  height: 160px;
  background: linear-gradient(90deg, var(--gray-100) 25%, var(--gray-50) 50%, var(--gray-100) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-lg);
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 产品网格 - 统一卡片设计 */
.product-grid-pc {
  display: grid;
  gap: var(--spacing-md);
}

.product-grid-pc.featured {
  margin-bottom: var(--spacing-xl);
}

.product-card-pc {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
  display: flex;
  flex-direction: column;
  min-height: 176px;
  overflow: hidden;
  position: relative;
}

.product-card-pc:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
}

.product-card-pc.featured {
  border-color: var(--primary-color);
  border-width: 2px;
  background: linear-gradient(135deg, var(--bg-card) 0%, rgba(13, 110, 110, 0.02) 100%);
}

/* 有分类的产品卡片 - 分类视觉样式 */
.product-card-pc.has-category {
  border-color: var(--category-border);
  background: linear-gradient(135deg, var(--bg-card) 0%, color-mix(in srgb, var(--category-surface) 70%, var(--bg-card)) 100%);
}

.product-card-pc.has-category:hover {
  border-color: var(--category-primary);
  box-shadow: 0 0 16px var(--category-glow), var(--shadow-lg);
}

.product-card-pc.has-category .product-name.category-name {
  color: var(--category-primary);
}

.card-category-icon {
  position: absolute;
  top: 12px;
  right: 12px;
  opacity: 0.7;
}

/* 卡片内部结构 */
.card-top {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  min-height: 0;
}

.card-title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-sm);
}

.card-title-row .product-name {
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.3;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-meta-stack {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.product-specs {
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-origin {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  padding: 2px 8px 2px 3px;
  border: 1px solid color-mix(in srgb, var(--category-primary, var(--primary-color)) 22%, var(--border-color));
  border-radius: 999px;
  background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 14%, var(--bg-card));
  color: var(--category-primary, var(--primary-color));
  font-size: var(--font-size-xs);
  line-height: 1.35;
}

.origin-label {
  flex-shrink: 0;
  padding: 1px 5px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--category-primary, var(--primary-color)) 12%, transparent);
  color: var(--category-primary, var(--primary-color));
  font-weight: 600;
}

.origin-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--gray-100);
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.price-value {
  font-family: var(--font-mono);
  font-size: 2.25rem;
  font-weight: 700;
  color: var(--primary-color);
  line-height: 1;
}

.price-value.empty {
  color: var(--gray-300);
  font-size: 1.5rem;
}

.price-unit {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.trend-badge {
  padding: 4px 10px;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
}

.trend-badge.up {
  background: rgba(239, 68, 68, 0.1);
  color: var(--price-rise-color);
}

.trend-badge.down {
  background: rgba(16, 185, 129, 0.1);
  color: var(--price-fall-color);
}

.trend-badge.flat {
  background: var(--gray-100);
  color: var(--gray-400);
}

.chart-area {
  width: 80px;
  height: 40px;
  flex-shrink: 0;
}

.mini-chart {
  width: 80px;
  height: 40px;
}

/* 区块标题 */
.section-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.section-title-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-family: var(--font-body);
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.section-title-pc svg {
  color: #FFB800;
}

.trend-range-tabs {
  display: flex;
  gap: var(--spacing-xs);
}

.range-tab {
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.range-tab:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.range-tab.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.home-featured-pc {
  margin-bottom: var(--spacing-xl);
}

.product-section-pc {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  border: 1px solid var(--border-color);
}

.category-product-groups {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
}

.category-product-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.category-group-header {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: fit-content;
  max-width: 100%;
  padding: var(--spacing-sm) var(--spacing-md);
  border: 1px solid var(--category-border, var(--border-color));
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--category-surface, var(--bg-card)) 70%, var(--bg-card));
  color: var(--category-primary, var(--text-primary));
}

.category-group-name {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--category-primary, var(--text-primary));
}

.category-group-count {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.search-area-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.search-box-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--gray-100);
  border-radius: var(--radius);
  width: 260px;
}

.search-box-pc svg { color: var(--text-muted); flex-shrink: 0; }

.search-input-pc {
  flex: 1;
  border: none;
  background: transparent;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  outline: none;
}

.search-input-pc::placeholder { color: var(--text-muted); }

.empty-state-pc {
  grid-column: 1 / -1;
  text-align: center;
  padding: var(--spacing-2xl);
  color: var(--text-muted);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
}

.empty-state-pc svg { color: var(--gray-300); }

/* ==================== 移动端布局 ==================== */
.navbar {
  height: 56px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-md);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-xl);
  font-weight: 500;
  color: var(--text-primary);
  margin: 0;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.btn-icon-mobile {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  color: var(--text-secondary);
  border-radius: var(--radius);
  transition: background var(--transition-fast);
}

.btn-icon-mobile:hover:not(:disabled) { background: var(--gray-100); }
.btn-icon-mobile:disabled { opacity: 0.5; cursor: not-allowed; }

.date-input-mobile {
  padding: var(--spacing-xs) var(--spacing-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  font-size: var(--font-size-xs);
  color: var(--text-primary);
  background: var(--bg-card);
  cursor: pointer;
  outline: none;
}

.date-tip {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--primary-color);
  color: white;
  border-radius: var(--radius);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.date-tip svg { flex-shrink: 0; }

.alert-error-mobile {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--error-bg);
  color: var(--error-color);
  border-radius: var(--radius);
  font-size: var(--font-size-sm);
}

.alert-error-mobile button {
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--error-color);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: var(--font-size-xs);
}

.loading-scroll-mobile {
  display: flex;
  gap: var(--spacing-sm);
  overflow-x: auto;
  padding-bottom: var(--spacing-xs);
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.loading-scroll-mobile::-webkit-scrollbar { display: none; }

.skeleton-card-mobile {
  min-width: 160px;
  height: 180px;
  background: linear-gradient(90deg, var(--gray-100) 25%, var(--gray-50) 50%, var(--gray-100) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-md);
  flex-shrink: 0;
}

.content {
  flex: 1;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
  padding-bottom: calc(64px + var(--spacing-lg));
}

.home-featured-mobile {
  margin-bottom: var(--spacing-xs);
}

.home-featured-scroll {
  display: flex;
  gap: var(--spacing-sm);
  overflow-x: auto;
  padding-bottom: var(--spacing-xs);
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.home-featured-scroll::-webkit-scrollbar { display: none; }

.home-featured-item-mobile {
  min-width: 160px;
  width: calc(50vw - var(--spacing-lg));
  max-width: 200px;
  background: var(--bg-card);
  border: 2px solid var(--primary-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  display: flex;
  flex-direction: column;
  min-height: 156px;
  flex-shrink: 0;
}

.home-featured-item-mobile.has-category {
  border-color: var(--category-border);
  background: linear-gradient(135deg, var(--bg-card) 0%, color-mix(in srgb, var(--category-surface) 70%, var(--bg-card)) 100%);
}

.home-featured-item-mobile.has-category .product-name.category-name {
  color: var(--category-primary);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--text-primary);
  margin: 0;
}

.add-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: var(--primary-color);
  color: #FFFFFF;
  border-radius: var(--radius);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background var(--transition-fast);
}

.add-btn:hover { background: var(--primary-light); }

.search-bar {
  height: 44px;
  background: var(--gray-100);
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-md);
  gap: var(--spacing-sm);
}

.search-bar svg { color: var(--text-muted); flex-shrink: 0; }

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: var(--text-primary);
  outline: none;
}

.search-input::placeholder { color: var(--text-muted); }

.empty-state-mobile {
  text-align: center;
  padding: var(--spacing-xl);
  color: var(--text-muted);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
}

.empty-state-mobile svg { color: var(--gray-300); }

.product-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.product-item {
  background: var(--bg-card);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  cursor: pointer;
  transition: border-color var(--transition-fast);
  position: relative;
}

/* 有分类的产品项 - 分类视觉样式 */
.product-item.has-category {
  border-color: var(--category-border);
  background: linear-gradient(135deg, var(--bg-card) 0%, color-mix(in srgb, var(--category-surface) 70%, var(--bg-card)) 100%);
}

.product-item.has-category:hover {
  border-color: var(--category-primary);
}

.product-item.has-category .product-name.category-name {
  color: var(--category-primary);
}

.item-category-icon {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0.7;
}

.product-item:hover { border-color: var(--primary-color); }

.item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-sm);
}

.item-header .product-name {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-main .product-specs {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-aside {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.item-aside .price-value {
  font-size: 1.25rem;
}

.chart-area-sm {
  width: 70px;
  height: 30px;
}

.featured-mobile-chart {
  width: 100%;
  height: 34px;
  margin-top: var(--spacing-xs);
}

.mini-chart-sm {
  width: 70px;
  height: 30px;
}

.featured-mobile-chart .mini-chart-sm {
  width: 100%;
  height: 34px;
}

/* 底部标签栏 */
.tab-bar {
  height: 64px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 0 var(--spacing-md);
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  z-index: 100;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius);
  color: var(--gray-400);
  transition: color var(--transition-fast);
}

.tab-item.active { color: var(--primary-color); }
.tab-item:hover:not(.active) { color: var(--text-secondary); }

.tab-label {
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  font-weight: 500;
}

/* ==================== 响应式 ==================== */
@media (max-width: 1023px) {
  .pc-home { display: none; }
}
</style>
