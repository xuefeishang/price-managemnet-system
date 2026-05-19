<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProducts, getPricesByDate, getPriceTrend } from '@/api/products'
import { getCategories } from '@/api/categories'
import { usePermission, Permission } from '@/composables/usePermission'
import { useTheme } from '@/composables/useTheme'
import { useLayout } from '@/composables/useLayout'
import { useHomeConfig } from '@/composables/useHomeConfig'
import { loadAllDicts, getCurrencySymbol } from '@/composables/useDict'
import { getCategoryVisual, getCategoryCardStyle, registerCategoryCodes } from '@/composables/useCategoryVisual'
import CategoryFilterPanel from '@/components/CategoryFilterPanel.vue'
import CategoryIcons from '@/components/icons/CategoryIcons.vue'
import type { Product, Price } from '@/types'

// ECharts 注册
use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const router = useRouter()
const { hasPermission } = usePermission()
const { themeConfig } = useTheme()
const { isPCLayout, windowWidth } = useLayout()
const { layoutConfig, loadHomeConfig } = useHomeConfig()

// 状态
const loading = ref(false)
const error = ref<string | null>(null)
const searchQuery = ref('')
const searchQueryDebounced = ref('')
const selectedCategoryIds = ref<number[]>([])

// 产品数据
const products = ref<Product[]>([])
const priceMap = ref<Map<number, Price>>(new Map())
const previousPriceMap = ref<Map<number, Price>>(new Map())
const priceHistoryMap = ref<Map<number, any[]>>(new Map())
const chartOptionsMap = ref<Map<number, any>>(new Map())

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

const filteredProducts = computed(() => {
  const active = products.value.filter(p => p.status === 'ACTIVE')

  // 分类筛选
  if (selectedCategoryIds.value.length > 0) {
    const filtered = active.filter(p =>
      p.categoryId && selectedCategoryIds.value.includes(p.categoryId)
    )
    if (filtered.length === 0 && !searchQueryDebounced.value) return active
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
  products.value.filter(p => p.showOnHome && p.status === 'ACTIVE')
)

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

const generateChartOption = (productId: number) => {
  const history = priceHistoryMap.value.get(productId) || []
  if (history.length === 0) return null

  const recent = history.slice(-30)
  const dates = recent.map(h => {
    const d = new Date(h.date)
    return `${d.getMonth() + 1}/${d.getDate()}`
  })
  const prices = recent.map(h => h.currentPrice)

  let lineColor = themeConfig.value.chartPrimaryColor || '#0D6E6E'
  if (prices.length >= 2) {
    const first = prices[0]
    const last = prices[prices.length - 1]
    if (first != null && last != null) {
      if (last > first) lineColor = themeConfig.value.priceRiseColor
      else if (last < first) lineColor = themeConfig.value.priceFallColor
    }
  }

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
      lineStyle: { width: 1.5, color: lineColor },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: lineColor + '30' },
            { offset: 1, color: lineColor + '05' }
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
          const trendData = trendRes.data || []
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
      const trendData = trendRes.data || []
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

// 获取产品卡片分类样式
const getCardStyle = (product: Product) => {
  if (!product.categoryId) return {}
  return getCategoryCardStyle(product.categoryId)
}

const getCardClass = (product: Product) => {
  return product.categoryId ? 'has-category' : ''
}

const activeTab = ref('home')
const getCurrencySymbolLocal = getCurrencySymbol

onMounted(async () => {
  // 加载分类数据并注册映射（必须在渲染产品卡片前完成）
  try {
    const catRes = await getCategories('ACTIVE')
    registerCategoryCodes((catRes.data || []).map(c => ({ id: c.id, code: c.code })))
  } catch (e) {
    console.error('Failed to load categories:', e)
  }

  await Promise.all([
    loadAllDicts(),
    loadHomeConfig()
  ])
  loadData()
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
          <!-- 重点关注指标区 - 大卡片 -->
          <div class="home-featured-pc" v-if="homeProducts.length > 0">
            <div class="section-header-pc">
              <h2 class="section-title-pc">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/>
                </svg>
                重点关注指标
              </h2>
              <div class="trend-range-tabs">
                <button
                  v-for="range in [{ key: '7d', label: '7日', days: 7 }, { key: '30d', label: '30日', days: 30 }, { key: '90d', label: '90日', days: 90 }]"
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
                @click="viewProduct(product)"
              >
                <div class="card-top">
                  <div class="card-title-row">
                    <span class="product-name">{{ product.name }}</span>
                    <span class="trend-badge" :class="priceChangeCache.get(product.id)?.direction || 'flat'" v-if="priceChangeCache.get(product.id)">
                      {{ priceChangeCache.get(product.id)?.direction === 'up' ? '↑' : priceChangeCache.get(product.id)?.direction === 'down' ? '↓' : '—' }}
                      {{ priceChangeCache.get(product.id)?.formattedDiff }}
                    </span>
                    <span class="trend-badge flat" v-else>—</span>
                  </div>
                  <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
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
                    <v-chart class="mini-chart" :option="chartOptionsMap.get(product.id)" />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 产品列表区 -->
          <div class="product-section-pc">
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
              <p>{{ searchQuery ? '未找到匹配的产品' : '暂无产品数据' }}</p>
            </div>

            <!-- 产品网格 -->
            <div v-else class="product-grid-pc" :style="{ gridTemplateColumns: `repeat(${gridCols}, 1fr)` }">
              <div
                v-for="product in filteredProducts"
                :key="product.id"
                class="product-card-pc"
                :class="getCardClass(product)"
                :style="getCardStyle(product)"
                @click="viewProduct(product)"
              >
                <!-- 分类图标 -->
                <div class="card-category-icon" v-if="product.categoryId">
                  <CategoryIcons
                    :icon="getCategoryVisual(product.categoryId).icon"
                    :size="16"
                    :color="getCategoryVisual(product.categoryId).primaryColor"
                  />
                </div>
                <div class="card-top">
                  <div class="card-title-row">
                    <span class="product-name" :class="{ 'category-name': product.categoryId }">{{ product.name }}</span>
                    <span class="trend-badge" :class="priceChangeCache.get(product.id)?.direction || 'flat'" v-if="priceChangeCache.get(product.id)">
                      {{ priceChangeCache.get(product.id)?.direction === 'up' ? '↑' : priceChangeCache.get(product.id)?.direction === 'down' ? '↓' : '—' }}
                      {{ priceChangeCache.get(product.id)?.formattedDiff }}
                    </span>
                  </div>
                  <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
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
                    <v-chart class="mini-chart" :option="chartOptionsMap.get(product.id)" />
                  </div>
                </div>
              </div>
            </div>
          </div>
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
          <div class="home-featured-mobile" v-if="homeProducts.length > 0">
            <div class="section-header">
              <h2 class="section-title">重点关注指标</h2>
            </div>
            <div class="home-featured-scroll">
              <div
                v-for="product in homeProducts"
                :key="product.id"
                class="home-featured-item-mobile"
                @click="viewProduct(product)"
              >
                <div class="card-top">
                  <div class="card-title-row">
                    <span class="product-name">{{ product.name }}</span>
                  </div>
                  <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
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
              </div>
            </div>
          </div>

          <div class="product-section">
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

            <div v-if="filteredProducts.length === 0" class="empty-state-mobile">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/><path d="M3 5h18M3 19h18M12 9v4"/>
              </svg>
              <p>{{ searchQuery ? '未找到匹配的产品' : '暂无产品数据' }}</p>
            </div>

            <div v-else class="product-list">
              <div
                v-for="product in filteredProducts"
                :key="product.id"
                class="product-item"
                @click="viewProduct(product)"
              >
                <div class="item-main">
                  <div class="item-header">
                    <span class="product-name">{{ product.name }}</span>
                    <span class="trend-badge" :class="priceChangeCache.get(product.id)?.direction || 'flat'" v-if="priceChangeCache.get(product.id)">
                      {{ priceChangeCache.get(product.id)?.direction === 'up' ? '↑' : priceChangeCache.get(product.id)?.direction === 'down' ? '↓' : '—' }}
                      {{ priceChangeCache.get(product.id)?.formattedDiff }}
                    </span>
                  </div>
                  <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
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
                    <v-chart class="mini-chart-sm" :option="chartOptionsMap.get(product.id)" />
                  </div>
                </div>
              </div>
            </div>
          </div>
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
  min-height: 160px;
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
  background: linear-gradient(135deg, var(--bg-card) 0%, var(--category-secondary)08 100%);
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

.card-top .product-specs {
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
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
  min-height: 140px;
  flex-shrink: 0;
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

.item-header .product-specs {
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

.mini-chart-sm {
  width: 70px;
  height: 30px;
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