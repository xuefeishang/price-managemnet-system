<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
//import { useUserStore } from '@/store/useUserStore'
import { useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProducts } from '@/api/products'
import { getPricesByDate, getProductPriceHistory } from '@/api/products'
import { usePermission, Permission } from '@/composables/usePermission'
import type { Product, Price, PriceHistory } from '@/types'

// 注册 ECharts 组件
use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

// const userStore = useUserStore()
const router = useRouter()
const { hasPermission } = usePermission()

const products = ref<Product[]>([])
const loading = ref(false)
const searchQuery = ref('')

// 选中的日期（默认昨天）
const getYesterday = () => {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}
const selectedDate = ref(getYesterday())

// 价格映射 (productId -> price)
const priceMap = ref<Map<number, Price>>(new Map())
// 前一天价格映射
const previousPriceMap = ref<Map<number, Price>>(new Map())
// 30天价格历史映射 (productId -> PriceHistory[])
const priceHistoryMap = ref<Map<number, PriceHistory[]>>(new Map())
// 折线图选项映射 (productId -> ECharts option)
const chartOptionsMap = ref<Map<number, any>>(new Map())

// 判断是否为PC布局
const isPCLayout = computed(() => {
  if (typeof window !== 'undefined') {
    return window.innerWidth >= 1024
  }
  return false
})

const activeTab = ref('home')

// 首页展示的产品
const homeProducts = computed(() => {
  return products.value.filter(p => p.showOnHome && p.status === 'ACTIVE')
})

// 过滤后的产品（仅展示启用的产品）
const filteredProducts = computed(() => {
  const activeProducts = products.value.filter(p => p.status === 'ACTIVE')
  if (!searchQuery.value) return activeProducts
  return activeProducts.filter(p => p.name.includes(searchQuery.value))
})

// 获取产品的当日价格
const getTodayPrice = (productId: number) => {
  return priceMap.value.get(productId)
}

// 获取前一天的日期字符串
const getPreviousDate = (dateStr: string) => {
  const date = new Date(dateStr)
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}

// 获取产品的价格涨跌信息
const getPriceChange = (productId: number) => {
  const current = priceMap.value.get(productId)
  const previous = previousPriceMap.value.get(productId)
  if (!current || !previous) return null
  const currentVal = current.currentPrice
  const previousVal = previous.currentPrice
  if (currentVal == null || previousVal == null) return null
  const diff = currentVal - previousVal
  if (diff === 0) return { direction: 'flat', diff: 0 }
  return { direction: diff > 0 ? 'up' : 'down', diff }
}

// 获取最后一次价格（当天有价格取当天，否则取继承价或最近历史）
const getLastPrice = (productId: number): string | null => {
  const todayPrice = priceMap.value.get(productId)
  if (todayPrice && todayPrice.currentPrice != null) {
    return String(todayPrice.currentPrice)
  }
  // 从历史记录中取最近一次价格
  const history = priceHistoryMap.value.get(productId)
  if (history && history.length > 0) {
    const sorted = [...history].sort((a, b) => new Date(b.changedTime).getTime() - new Date(a.changedTime).getTime())
    if (sorted[0].newPrice != null) return String(sorted[0].newPrice)
  }
  return null
}

// 生成30天折线图选项
const generateChartOption = (productId: number) => {
  const history = priceHistoryMap.value.get(productId) || []
  if (history.length === 0) return null

  // 按时间排序
  const sorted = [...history].sort((a, b) => new Date(a.changedTime).getTime() - new Date(b.changedTime).getTime())

  // 取最近30条
  const recent = sorted.slice(-30)

  const dates = recent.map(h => {
    const d = new Date(h.changedTime)
    return `${d.getMonth() + 1}/${d.getDate()}`
  })
  const prices = recent.map(h => h.newPrice)

  // 判断整体趋势颜色
  let lineColor = '#0D6E6E'
  if (prices.length >= 2) {
    const first = prices[0]
    const last = prices[prices.length - 1]
    if (first != null && last != null) {
      if (last > first) lineColor = '#EF4444'
      else if (last < first) lineColor = '#10B981'
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

// 格式化日期显示
const formatDateDisplay = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

// 获取字典数据
import { getCurrencySymbol as _getCurrencySymbol, loadAllDicts } from '@/composables/useDict'
const getCurrencySymbol = _getCurrencySymbol

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const prevDate = getPreviousDate(selectedDate.value)
    const [productsRes, pricesRes, prevPricesRes] = await Promise.all([
      getProducts({ page: 0, size: 1000 }),
      getPricesByDate(selectedDate.value),
      getPricesByDate(prevDate)
    ])
    products.value = productsRes.data.content || []

    // 处理当日价格数据
    const prices = pricesRes.data || []
    priceMap.value.clear()
    prices.forEach((price: Price) => {
      if (price.product?.id) {
        priceMap.value.set(price.product.id, price)
      }
    })

    // 处理前一天价格数据
    const prevPrices = prevPricesRes.data || []
    previousPriceMap.value.clear()
    prevPrices.forEach((price: Price) => {
      if (price.product?.id) {
        previousPriceMap.value.set(price.product.id, price)
      }
    })

    // 加载所有产品的30天价格历史
    priceHistoryMap.value.clear()
    chartOptionsMap.value.clear()
    await Promise.all(products.value.map(async (product) => {
      try {
        const historyRes = await getProductPriceHistory(product.id)
        const historyData = historyRes.data || []
        priceHistoryMap.value.set(product.id, historyData)
        const option = generateChartOption(product.id)
        if (option) {
          chartOptionsMap.value.set(product.id, option)
        }
      } catch (e) {
        console.error(`Failed to load price history for product ${product.id}:`, e)
      }
    }))
  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    loading.value = false
  }
}

// 日期变化时重新加载
const onDateChange = () => {
  loadData()
}

// 导航
// const navigateTo = (path: string) => {
//   router.push(path)
// }

// Tab切换（移动端）
const switchTab = (tab: string) => {
  activeTab.value = tab
  switch (tab) {
    case 'home':
      router.push('/home')
      break
    case 'products':
      router.push('/products')
      break
    case 'import':
      router.push('/import')
      break
    case 'profile':
      router.push('/profile')
      break
  }
}

// 跳转到产品详情
const viewProduct = (product: Product) => {
  router.push(`/product-detail/${product.id}`)
}

// 跳转到价格维护
const goToPriceMaintenance = () => {
  router.push('/price-maintenance')
}

onMounted(() => {
  loadAllDicts()
  loadData()
})
</script>

<template>
  <div class="home-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-home">
        <!-- 页面标题 -->
        <div class="page-header-pc">
          <div class="header-left-pc">
            <h1 class="page-title-pc">{{ formatDateDisplay(selectedDate) }} 价格概览</h1>
            <div class="date-picker-wrapper">
              <input
                type="date"
                v-model="selectedDate"
                @change="onDateChange"
                class="date-input-pc"
              />
            </div>
          </div>
          <button class="btn-primary-pc" @click="goToPriceMaintenance" v-if="hasPermission(Permission.PRODUCT_EDIT)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            价格维护
          </button>
        </div>

        <!-- 首页展示产品 -->
        <div class="home-featured-pc" v-if="homeProducts.length > 0 && !loading">
          <div class="section-header-pc">
            <h2 class="section-title-pc">重点关注指标</h2>
          </div>
          <div class="home-featured-grid-pc">
            <div
              v-for="product in homeProducts"
              :key="product.id"
              class="product-card-pc featured"
              @click="viewProduct(product)"
            >
              <div class="product-card-header">
                <span class="product-name">{{ product.name }}</span>
                <span class="price-change-badge" :class="getPriceChange(product.id)?.direction || 'none'" v-if="getPriceChange(product.id)">
                  <span class="change-arrow" v-if="getPriceChange(product.id)?.direction === 'up'">↑</span>
                  <span class="change-arrow" v-else-if="getPriceChange(product.id)?.direction === 'down'">↓</span>
                  <span class="change-arrow" v-else>—</span>
                  {{ (getPriceChange(product.id)?.diff ?? 0) > 0 ? '+' : '' }}{{ getPriceChange(product.id)?.diff }}
                </span>
                <span class="price-change-badge none" v-else>—</span>
              </div>
              <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
              <div class="product-price" v-if="getLastPrice(product.id)">
                <span class="price-current">{{ getCurrencySymbol(product.currency) }}{{ getLastPrice(product.id) }}</span>
                <span class="price-unit">{{ getTodayPrice(product.id)?.unit || product.unit || '元' }}</span>
              </div>
              <div class="price-diff" v-if="getLastPrice(product.id) && getPriceChange(product.id)">
                <span class="price-diff-value" :class="getPriceChange(product.id)!.direction">{{ getPriceChange(product.id)!.diff > 0 ? '+' : '' }}{{ getPriceChange(product.id)!.diff }}</span>
              </div>
              <div class="product-price empty" v-if="!getLastPrice(product.id)">
                暂无价格
              </div>
              <div class="product-chart-area" v-if="chartOptionsMap.get(product.id)">
                <v-chart class="mini-chart" :option="chartOptionsMap.get(product.id)" autoresize />
              </div>
            </div>
          </div>
        </div>

        <!-- 产品列表区域 -->
        <div class="product-section-pc">
          <div class="section-header-pc">
            <h2 class="section-title-pc">产品列表</h2>
            <div class="search-box-pc">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input
                v-model="searchQuery"
                type="text"
                placeholder="搜索产品..."
                class="search-input-pc"
              />
            </div>
          </div>

          <!-- PC产品网格 -->
          <div class="product-grid-pc" v-if="!loading">
            <div
              v-for="product in filteredProducts"
              :key="product.id"
              class="product-card-pc list-card"
              @click="viewProduct(product)"
            >
              <div class="list-card-left">
                <div class="product-card-header">
                  <span class="product-name">{{ product.name }}</span>
                </div>
                <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
                <div class="product-price" v-if="getLastPrice(product.id)">
                  <span class="price-current">{{ getCurrencySymbol(product.currency) }}{{ getLastPrice(product.id) }}</span>
                  <span class="price-unit">{{ getTodayPrice(product.id)?.unit || product.unit || '元' }}</span>
                </div>
                <div class="price-diff" v-if="getLastPrice(product.id) && getPriceChange(product.id)">
                  <span class="price-diff-value" :class="getPriceChange(product.id)!.direction">{{ getPriceChange(product.id)!.diff > 0 ? '+' : '' }}{{ getPriceChange(product.id)!.diff }}</span>
                </div>
                <div class="product-price empty" v-if="!getLastPrice(product.id)">
                  暂无价格
                </div>
              </div>
              <div class="list-card-chart" v-if="chartOptionsMap.get(product.id)">
                <v-chart class="list-chart-pc" :option="chartOptionsMap.get(product.id)" autoresize />
              </div>
            </div>
            <div v-if="filteredProducts.length === 0" class="empty-state-pc">
              暂无产品数据
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 顶部导航栏 -->
      <header class="navbar">
        <div class="navbar-left">
          <h1 class="navbar-title">{{ formatDateDisplay(selectedDate) }}</h1>
        </div>
        <div class="navbar-right">
          <input
            type="date"
            v-model="selectedDate"
            @change="onDateChange"
            class="date-input-mobile"
          />
        </div>
      </header>

      <!-- 主内容区 -->
      <main class="content">
        <!-- 日期选择提示 -->
        <div class="date-tip">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/>
            <line x1="8" y1="2" x2="8" y2="6"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          <span>{{ formatDateDisplay(selectedDate) }} 价格</span>
        </div>

        <!-- 首页展示产品 -->
        <div class="home-featured-mobile" v-if="homeProducts.length > 0 && !loading">
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
              <div class="featured-item-top">
                <span class="product-name">{{ product.name }}</span>
                <span class="price-change-badge" :class="getPriceChange(product.id)?.direction || 'none'" v-if="getPriceChange(product.id)">
                  <span class="change-arrow" v-if="getPriceChange(product.id)?.direction === 'up'">↑</span>
                  <span class="change-arrow" v-else-if="getPriceChange(product.id)?.direction === 'down'">↓</span>
                  <span class="change-arrow" v-else>—</span>
                  {{ (getPriceChange(product.id)?.diff ?? 0) > 0 ? '+' : '' }}{{ getPriceChange(product.id)?.diff }}
                </span>
                <span class="price-change-badge none" v-else>—</span>
              </div>
              <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
              <div class="featured-item-price" v-if="getLastPrice(product.id)">
                <span class="price-current">{{ getCurrencySymbol(product.currency) }}{{ getLastPrice(product.id) }}</span>
                <span class="price-unit">{{ getTodayPrice(product.id)?.unit || product.unit || '元' }}</span>
              </div>
              <div class="price-diff" v-if="getLastPrice(product.id) && getPriceChange(product.id)">
                <span class="price-diff-value" :class="getPriceChange(product.id)!.direction">{{ getPriceChange(product.id)!.diff > 0 ? '+' : '' }}{{ getPriceChange(product.id)!.diff }}</span>
              </div>
              <div class="featured-item-price empty" v-if="!getLastPrice(product.id)">暂无价格</div>
              <div class="product-chart-area-mobile" v-if="chartOptionsMap.get(product.id)">
                <v-chart class="mini-chart-mobile" :option="chartOptionsMap.get(product.id)" autoresize />
              </div>
            </div>
          </div>
        </div>

        <!-- 产品列表区域 -->
        <div class="product-section">
          <div class="section-header">
            <h2 class="section-title">产品列表</h2>
            <button class="add-btn" @click="goToPriceMaintenance" v-if="hasPermission(Permission.PRODUCT_EDIT)">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>

          <!-- 搜索框 -->
          <div class="search-bar">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索产品..."
              class="search-input"
            />
          </div>

          <!-- 产品列表 -->
          <div class="product-list" v-if="!loading">
            <div
              v-for="product in filteredProducts"
              :key="product.id"
              class="product-item"
              @click="viewProduct(product)"
            >
              <div class="product-info">
                <span class="product-name">{{ product.name }}</span>
                <span class="product-specs" v-if="product.specs">{{ product.specs }}</span>
                <div class="product-price-mobile" v-if="getLastPrice(product.id)">
                  <span class="price-current">{{ getCurrencySymbol(product.currency) }}{{ getLastPrice(product.id) }}</span>
                  <span class="price-unit">{{ getTodayPrice(product.id)?.unit || product.unit || '元' }}</span>
                </div>
                <div class="price-diff" v-if="getLastPrice(product.id) && getPriceChange(product.id)">
                  <span class="price-diff-value" :class="getPriceChange(product.id)!.direction">{{ getPriceChange(product.id)!.diff > 0 ? '+' : '' }}{{ getPriceChange(product.id)!.diff }}</span>
                </div>
                <div class="product-price-mobile empty" v-if="!getLastPrice(product.id)">暂无价格</div>
              </div>
              <div class="product-list-chart" v-if="chartOptionsMap.get(product.id)">
                <v-chart class="list-chart-mobile" :option="chartOptionsMap.get(product.id)" autoresize />
              </div>
              <div class="product-price-display" v-else-if="getLastPrice(product.id)">
                <span class="price-current">{{ getCurrencySymbol(product.currency) }}{{ getLastPrice(product.id) }}</span>
                <span class="price-unit">{{ getTodayPrice(product.id)?.unit || product.unit || '元' }}</span>
              </div>
              <div class="product-price-display empty" v-else>
                暂无价格
              </div>
            </div>
            <div v-if="filteredProducts.length === 0" class="empty-state">
              暂无产品数据
            </div>
          </div>
          <div v-else class="loading-state">
            <div class="loading-spinner"></div>
          </div>
        </div>
      </main>

      <!-- 底部标签栏 -->
      <footer class="tab-bar">
        <button class="tab-item" :class="{ active: activeTab === 'home' }" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'products' }" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'import' }" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'profile' }" @click="switchTab('profile')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span class="tab-label">我的</span>
        </button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.home-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

/* ==================== PC布局 ==================== */
.pc-home {
  padding: 32px;
}

.page-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.header-left-pc {
  display: flex;
  align-items: center;
  gap: 20px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.date-picker-wrapper {
  display: flex;
  align-items: center;
}

.date-input-pc {
  padding: 8px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  background: white;
  cursor: pointer;
  outline: none;
}

.date-input-pc:focus {
  border-color: #0D6E6E;
}

.btn-primary-pc {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A8A7A 100%);
  color: #FFFFFF;
  border: none;
  border-radius: 10px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 200ms ease;
  box-shadow: 0 2px 8px rgba(13, 110, 110, 0.25);
}

.btn-primary-pc:hover {
  background: linear-gradient(135deg, #0A8A7A 0%, #0D6E6E 100%);
  box-shadow: 0 4px 16px rgba(13, 110, 110, 0.35);
  transform: translateY(-1px);
}

.btn-primary-pc:active {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(13, 110, 110, 0.2);
}

.btn-primary-pc svg {
  flex-shrink: 0;
}

.overview-section-pc {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 32px;
}

.overview-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.overview-card-label {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  margin-bottom: 8px;
}

.overview-card-value {
  font-family: 'Inter', sans-serif;
  font-size: 32px;
  font-weight: 600;
  color: #1A1A1A;
}

.overview-card-value.success {
  color: #10B981;
}

.overview-card-value.danger {
  color: #EF4444;
}

/* 首页展示产品 - PC */
.home-featured-pc {
  margin-bottom: 24px;
}

.home-featured-grid-pc {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.product-card-pc.featured {
  background: #FFFFFF;
  border: 1px solid #0D6E6E;
  border-radius: 10px;
  padding: 16px;
  cursor: pointer;
  transition: all 150ms;
  box-shadow: 0 2px 8px rgba(13, 110, 110, 0.08);
  display: flex;
  flex-direction: column;
  aspect-ratio: 1 / 1;
  position: relative;
  overflow: hidden;
}

.product-card-pc.featured:hover {
  box-shadow: 0 4px 16px rgba(13, 110, 110, 0.15);
}

.product-card-pc.featured .product-card-header {
  flex-shrink: 0;
}

.product-card-pc.featured .product-specs {
  flex-shrink: 0;
}

.product-card-pc.featured .product-price {
  flex-shrink: 0;
}

.product-card-pc.featured .product-chart-area {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  margin-top: 4px;
}

.mini-chart {
  width: 100%;
  height: 80px;
}

.price-change-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.price-change-badge.up {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.price-change-badge.down {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.price-change-badge.flat {
  background: rgba(156, 163, 175, 0.1);
  color: #9CA3AF;
}

.price-change-badge.none {
  background: rgba(156, 163, 175, 0.1);
  color: #CCCCCC;
}

.change-arrow {
  font-size: 12px;
  line-height: 1;
}

.featured-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

/* 首页展示产品 - 移动端 */
.home-featured-mobile {
  margin-bottom: 8px;
}

.home-featured-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 4px;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none;
}

.home-featured-scroll::-webkit-scrollbar {
  display: none;
}

.home-featured-item-mobile {
  min-width: 160px;
  max-width: 200px;
  width: 180px;
  height: 200px;
  flex-shrink: 0;
  background: #FFFFFF;
  border: 1px solid #0D6E6E;
  border-radius: 10px;
  padding: 14px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(13, 110, 110, 0.08);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.product-chart-area-mobile {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  margin-top: 4px;
}

.mini-chart-mobile {
  width: 100%;
  height: 60px;
}

.featured-item-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 6px;
  gap: 8px;
}

.featured-item-top .product-name {
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.featured-item-price {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.featured-item-price .price-label {
  font-size: 10px;
  color: #888888;
}

.featured-item-price .price-current {
  font-size: 16px;
  font-weight: 600;
  color: #0D6E6E;
}

.featured-item-price .price-unit {
  font-size: 11px;
  color: #888888;
}

.featured-item-price.empty {
  font-size: 12px;
  color: #CCCCCC;
}

.product-section-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.section-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0;
}

.search-box-pc {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #F5F5F5;
  border-radius: 8px;
  width: 240px;
}

.search-box-pc svg {
  color: #888888;
  flex-shrink: 0;
}

.search-input-pc {
  flex: 1;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  outline: none;
}

.search-input-pc::placeholder {
  color: #888888;
}

.product-grid-pc {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.product-card-pc {
  background: #FAFAFA;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 150ms;
  border: 1px solid transparent;
}

.product-card-pc.list-card {
  display: flex;
  align-items: center;
  gap: 16px;
}

.list-card-left {
  flex: 1;
  min-width: 0;
}

.list-card-chart {
  flex-shrink: 0;
  width: 160px;
  height: 60px;
}

.list-chart-pc {
  width: 160px;
  height: 60px;
}

.product-card-pc:hover {
  border-color: #0D6E6E;
  background: #FFFFFF;
}

.product-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.product-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
}

.product-status {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.product-status.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.product-status.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.product-specs {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #666666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.price-label {
  font-family: 'Inter', sans-serif;
  font-size: 11px;
  color: #888888;
  margin-right: 2px;
}

.product-price .price-current {
  font-family: 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #0D6E6E;
}

.product-price .price-unit {
  font-size: 12px;
  color: #888888;
}

.price-diff {
  margin-top: 2px;
}

.price-diff-value {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 600;
}

.price-diff-value.up {
  color: #10B981;
}

.price-diff-value.down {
  color: #EF4444;
}

.price-diff-value.flat {
  color: #9CA3AF;
}

.product-price.empty {
  font-size: 13px;
  color: #CCCCCC;
}

.empty-state-pc {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px;
  color: #888888;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

/* ==================== 移动端布局 ==================== */
.navbar {
  height: 56px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-left {
  display: flex;
  align-items: center;
}

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 20px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.date-input-mobile {
  padding: 6px 10px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: 12px;
  color: #1A1A1A;
  background: white;
  cursor: pointer;
  outline: none;
}

.date-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #0D6E6E;
  color: white;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
}

.date-tip svg {
  flex-shrink: 0;
}

.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding-bottom: 100px;
}

.product-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.add-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #0D6E6E;
  color: #FFFFFF;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-btn:hover {
  background: #0D8A8A;
}

.search-bar {
  height: 44px;
  background: #F0F0F0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  gap: 8px;
}

.search-bar svg {
  color: #888888;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  outline: none;
}

.search-input::placeholder {
  color: #888888;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-item {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.product-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-list-chart {
  flex-shrink: 0;
  width: 120px;
  height: 50px;
}

.list-chart-mobile {
  width: 120px;
  height: 50px;
}

.product-price-mobile {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 4px;
}

.product-price-mobile .price-current {
  font-size: 16px;
  font-weight: 600;
  color: #0D6E6E;
}

.product-price-mobile .price-unit {
  font-size: 11px;
  color: #888888;
}

.product-price-mobile.empty {
  font-size: 12px;
  color: #CCCCCC;
}

.product-item:hover {
  border-color: #0D6E6E;
}

.product-price-display {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.product-price-display .price-current {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #0D6E6E;
}

.product-price-display .price-unit {
  font-size: 11px;
  color: #888888;
}

.product-price-display.empty {
  font-size: 12px;
  color: #CCCCCC;
}

.product-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
}

.product-status {
  padding: 4px 8px;
  border-radius: 4px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
}

.product-status.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.product-status.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #888888;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.tab-bar {
  height: 64px;
  background: #FFFFFF;
  border-top: 1px solid #E5E5E5;
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 0 20px;
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  max-width: 100%;
  z-index: 100;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  color: #AAAAAA;
}

.tab-item.active {
  color: #0D6E6E;
}

.tab-label {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 500;
}

@media (max-width: 1024px) {
  .pc-home {
    display: none;
  }
}
</style>
