<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProduct, getProductPriceHistory, getCurrentPrice, getPriceTrend, getPriceByDate } from '@/api/products'
import type { PriceTrendPoint } from '@/api/products'
import { usePermission, Permission } from '@/composables/usePermission'
import { getOriginName, getCustomerName, loadAllDicts } from '@/composables/useDict'
import { getCurrencySymbol, getStatusLabel, getDictValue } from '@/composables/useDict'
import { useTheme } from '@/composables/useTheme'
import { eventBus } from '@/utils/eventBus'
import type { Product, PriceHistory, Price } from '@/types'

// 注册 ECharts 组件
use([LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, CanvasRenderer])

const { themeConfig } = useTheme()

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const product = ref<Product | null>(null)
const currentPrice = ref<Price | null>(null)
const priceHistory = ref<PriceHistory[]>([])
const loading = ref(false)

// 价格走势数据
const trendData30 = ref<PriceTrendPoint[]>([])
const trendData180 = ref<PriceTrendPoint[]>([])
const trendData365 = ref<PriceTrendPoint[]>([])

// 当前选中的时间范围
const selectedTrendRange = ref<'30' | '180' | '365'>('30')

// 当前走势数据（根据选中的时间范围）
const currentTrendData = computed(() => {
  switch (selectedTrendRange.value) {
    case '180': return trendData180.value
    case '365': return trendData365.value
    default: return trendData30.value
  }
})

// 历史价格查询
const historyQueryDate = ref('')
const historyQueryResult = ref<Price | null>(null)
const historyQueryLoading = ref(false)

// 解析产地和客户名称（从字典缓存）
const originName = computed(() => {
  if (!product.value?.originIds) return '-'
  try {
    const keys = JSON.parse(product.value.originIds)
    if (keys.length === 0) return '-'
    return getOriginName(keys[0]) || keys[0]
  } catch {
    return '-'
  }
})

const customerNames = computed(() => {
  if (!product.value?.customerIds) return []
  try {
    const keys = JSON.parse(product.value.customerIds)
    return keys.map((key: string) => getCustomerName(key)).filter(Boolean) as string[]
  } catch {
    return []
  }
})

// 判断是否为PC布局
const isPCLayout = computed(() => {
  if (typeof window !== 'undefined') {
    return window.innerWidth >= 1024
  }
  return false
})

// 格式化走势日期（X轴标签）- 统一格式：MM/DD
const formatTrendDate = (dateStr: string): string => {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${String(d.getDate()).padStart(2, '0')}`
}

// 格式化完整日期（Tooltip）
const formatFullDate = (dateStr: string): string => {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 生成走势图配置（统一X轴格式 MM/DD）
const buildTrendChartOption = (trendData: PriceTrendPoint[]) => {
  const dates = trendData.map(d => formatTrendDate(d.date))
  const fullDates = trendData.map(d => formatFullDate(d.date))
  const currentPrices = trendData.map(d => d.currentPrice)
  const budgetPrices = trendData.map(d => d.budgetPrice)
  const hasBudget = budgetPrices.some(v => v != null)

  const chartPrimaryColor = themeConfig.value.chartPrimaryColor
  const chartColors = themeConfig.value.chartColors

  const series: any[] = [{
    name: '售价',
    type: 'line',
    data: currentPrices,
    smooth: true,
    symbol: 'none',
    lineStyle: { color: chartPrimaryColor, width: 2 },
    itemStyle: { color: chartPrimaryColor },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: chartPrimaryColor + '33' },
          { offset: 1, color: chartPrimaryColor + '05' }
        ]
      }
    }
  }]

  if (hasBudget) {
    series.push({
      name: '预算价格',
      type: 'line',
      data: budgetPrices,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: chartColors[2] || '#F59E0B', width: 1.5, type: 'dashed' },
      itemStyle: { color: chartColors[2] || '#F59E0B' }
    })
  }

  const option: any = {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        let html = `${fullDates[params[0].dataIndex]}<br/>`
        for (const p of params) {
          if (p.value != null) {
            html += `${p.marker} ${p.seriesName}: ${getCurrencySymbol(product.value?.currency)}${Number(p.value).toFixed(2)}<br/>`
          }
        }
        return html
      }
    },
    legend: hasBudget ? { data: ['售价', '预算价格'], bottom: 0, textStyle: { fontSize: 11 } } : undefined,
    grid: { left: '3%', right: '4%', bottom: hasBudget ? '14%' : '3%', top: '8%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: { fontSize: 11, color: '#666', rotate: trendData.length > 30 ? 45 : 0, interval: 'auto' }
    },
    yAxis: {
      type: 'value',
      name: '价格',
      nameTextStyle: { fontSize: 11, color: '#666' },
      axisLabel: { fontSize: 11, color: '#666' },
      splitLine: { lineStyle: { color: '#eee', type: 'dashed' } }
    },
    series
  }

  // 长周期图表加 dataZoom 支持滑动查看
  if (trendData.length > 30) {
    option.dataZoom = [{
      type: 'inside',
      start: Math.max(0, 100 - (30 / trendData.length) * 100),
      end: 100
    }]
  }

  return option
}

// 走势图配置（根据当前选中的时间范围）
const trendChartConfig = computed(() => buildTrendChartOption(currentTrendData.value))

const loadProduct = async () => {
  const id = route.params.id as string
  if (!id) {
    router.push('/home')
    return
  }

  loading.value = true
  try {
    const productResponse = await getProduct(parseInt(id))
    product.value = productResponse.data

    // 并行获取：当前价格、价格历史、3个走势数据
    const [priceResponse, historyResponse, trend30Res, trend180Res, trend365Res] = await Promise.all([
      getCurrentPrice(parseInt(id)),
      getProductPriceHistory(parseInt(id)),
      getPriceTrend(parseInt(id), 30),
      getPriceTrend(parseInt(id), 180),
      getPriceTrend(parseInt(id), 365)
    ])

    currentPrice.value = priceResponse.data || null
    priceHistory.value = historyResponse.data || []
    trendData30.value = trend30Res.data || []
    trendData180.value = trend180Res.data || []
    trendData365.value = trend365Res.data || []
  } catch (error) {
    console.error('Failed to load product:', error)
  } finally {
    loading.value = false
  }
}

const editProduct = () => {
  router.push(`/product-edit/${product.value?.id}`)
}

const goBack = () => {
  router.push('/home')
}

// 底部标签栏导航（移动端）
const activeTab = ref('products')

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

onMounted(() => {
  loadAllDicts()
  loadProduct()
  eventBus.on('product-updated', handleProductUpdated)
})

onUnmounted(() => {
  eventBus.off('product-updated', handleProductUpdated)
})

const handleProductUpdated = (updatedId: number | null) => {
  if (!updatedId || updatedId === parseInt(route.params.id as string)) {
    loadProduct()
  }
}

// 查询指定日期的历史价格
const queryHistoryPrice = async () => {
  if (!historyQueryDate.value || !product.value) return

  historyQueryLoading.value = true
  try {
    const res = await getPriceByDate(product.value.id, historyQueryDate.value)
    historyQueryResult.value = res.data || null
  } catch (error) {
    console.error('Failed to query history price:', error)
    historyQueryResult.value = null
  } finally {
    historyQueryLoading.value = false
  }
}

// 清除历史价格查询结果
const clearHistoryQuery = () => {
  historyQueryDate.value = ''
  historyQueryResult.value = null
}
</script>

<template>
  <div class="product-detail-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-detail" v-if="!loading && product">
        <div class="page-header-pc">
          <button class="back-btn-pc" @click="goBack">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
            返回
          </button>
          <h1 class="page-title-pc">产品详情</h1>
          <button class="btn-edit-pc" @click="editProduct" v-if="hasPermission(Permission.PRODUCT_EDIT)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑产品
          </button>
        </div>

        <!-- 价格走势区域（单一图表 + 时间范围切换） -->
        <div class="trend-section-pc" v-if="trendData30.length > 1 || trendData180.length > 1 || trendData365.length > 1">
          <div class="trend-header-pc">
            <h3 class="trend-main-title-pc">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -3px; margin-right: 8px; color: #0D6E6E;">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              价格走势
            </h3>
            <div class="trend-range-tabs">
              <button
                class="trend-tab"
                :class="{ active: selectedTrendRange === '30' }"
                @click="selectedTrendRange = '30'"
                :disabled="trendData30.length <= 1"
              >近30天</button>
              <button
                class="trend-tab"
                :class="{ active: selectedTrendRange === '180' }"
                @click="selectedTrendRange = '180'"
                :disabled="trendData180.length <= 1"
              >近180天</button>
              <button
                class="trend-tab"
                :class="{ active: selectedTrendRange === '365' }"
                @click="selectedTrendRange = '365'"
                :disabled="trendData365.length <= 1"
              >近12个月</button>
            </div>
          </div>
          <v-chart class="price-chart-pc" :option="trendChartConfig" autoresize />
          <div class="trend-hint" v-if="currentTrendData.length > 30">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            可拖动查看详细数据
          </div>
        </div>

        <!-- 暂无走势数据提示 -->
        <div class="empty-trend-pc" v-if="trendData30.length <= 1 && trendData180.length <= 1 && trendData365.length <= 1">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
          <span>暂无足够的价格走势数据</span>
        </div>

        <!-- 第二行：左侧产品信息 + 右侧历史记录 -->
        <div class="detail-grid-pc">
          <!-- 左侧产品信息 -->
          <div class="detail-main-pc">
            <!-- 产品信息 -->
            <div class="info-card-pc">
              <div class="section-title-pc">产品信息</div>
              <div class="info-grid-pc">
                <div class="info-row-pc">
                  <span class="info-key">产品名称</span>
                  <span class="info-value">{{ product.name }}</span>
                </div>
                <div class="info-row-pc">
                  <span class="info-key">产品分类</span>
                  <span class="info-value">{{ product.category?.name || '未分类' }}</span>
                </div>
                <div class="info-row-pc">
                  <span class="info-key">产品规格</span>
                  <span class="info-value">{{ product.specs || '-' }}</span>
                </div>
                <div class="info-row-pc">
                  <span class="info-key">计量单位</span>
                  <span class="info-value">{{ product.unit || '-' }}</span>
                </div>
                <div class="info-row-pc">
                  <span class="info-key">产地</span>
                  <span class="info-value">{{ originName }}</span>
                </div>
                <div class="info-row-pc">
                  <span class="info-key">显示状态</span>
                  <span class="info-value">
                    <span class="status-badge" :class="product.status?.toLowerCase()">
                      {{ getStatusLabel(product.status) }}
                    </span>
                  </span>
                </div>
              </div>
            </div>

            <!-- 价格信息 -->
            <div class="info-card-pc">
              <div class="section-title-pc">价格信息</div>
              <div class="info-grid-pc">
                <div class="info-row-pc">
                  <span class="info-key">当前售价</span>
                  <span class="info-value price-highlight">{{ currentPrice?.currentPrice != null ? getCurrencySymbol(product.currency) + Number(currentPrice.currentPrice).toFixed(2) : (product.sellingPrice != null ? getCurrencySymbol(product.currency) + product.sellingPrice.toFixed(2) : '-') }}</span>
                </div>
                <div class="info-row-pc">
                  <span class="info-key">预算价格</span>
                  <span class="info-value price-highlight">{{ currentPrice?.budgetPrice != null ? getCurrencySymbol(product.currency) + currentPrice.budgetPrice.toFixed(2) : (product.budgetPrice != null ? getCurrencySymbol(product.currency) + product.budgetPrice.toFixed(2) : '-') }}</span>
                </div>
              </div>
            </div>

            <!-- 客户与描述 -->
            <div class="info-card-pc">
              <div class="section-title-pc">客户与描述</div>
              <div class="info-grid-pc">
                <div class="info-row-pc full-row">
                  <span class="info-key">客户信息</span>
                  <span class="info-value">{{ customerNames.length > 0 ? customerNames.join('、') : '-' }}</span>
                </div>
                <div class="info-row-pc full-row" v-if="product.description">
                  <span class="info-key">产品描述</span>
                  <span class="info-value desc">{{ product.description }}</span>
                </div>
                <div class="info-row-pc full-row" v-if="product.remark">
                  <span class="info-key">备注说明</span>
                  <span class="info-value desc">{{ product.remark }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：历史价格查询 + 近期价格变动 -->
          <div class="detail-sidebar-pc">
            <!-- 历史价格查询 -->
            <div class="history-query-card-pc">
              <h3 class="history-title-pc">历史价格查询</h3>
              <div class="query-form-pc">
                <input
                  type="date"
                  v-model="historyQueryDate"
                  class="query-input-pc"
                  :max="new Date().toISOString().split('T')[0]"
                />
                <button class="query-btn-pc" @click="queryHistoryPrice" :disabled="!historyQueryDate || historyQueryLoading">
                  <span v-if="historyQueryLoading">查询中...</span>
                  <span v-else>查询</span>
                </button>
                <button class="query-clear-btn-pc" @click="clearHistoryQuery" v-if="historyQueryResult" title="清除">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
                  </svg>
                </button>
              </div>
              <!-- 查询结果 -->
              <div class="query-result-pc" v-if="historyQueryResult">
                <div class="result-date-pc">{{ historyQueryDate }}</div>
                <div class="result-row-pc">
                  <span class="result-label">售价</span>
                  <span class="result-value">{{ historyQueryResult.currentPrice != null ? getCurrencySymbol(product.currency) + Number(historyQueryResult.currentPrice).toFixed(2) : '-' }}</span>
                </div>
                <div class="result-row-pc">
                  <span class="result-label">预算价</span>
                  <span class="result-value">{{ historyQueryResult.budgetPrice != null ? getCurrencySymbol(product.currency) + Number(historyQueryResult.budgetPrice).toFixed(2) : '-' }}</span>
                </div>
              </div>
              <div class="query-empty-pc" v-else-if="!historyQueryLoading && historyQueryDate">
                <span>该日期无价格记录</span>
              </div>
              <div class="query-hint-pc" v-else>
                <span>选择日期查询历史价格</span>
              </div>
            </div>

            <!-- 近期价格变动 -->
            <div class="history-card-pc" v-if="priceHistory.length > 0">
              <h3 class="history-title-pc">近期价格变动</h3>
              <div class="history-list-pc">
                <div v-for="history in priceHistory.slice().reverse().slice(0, 10)" :key="history.id" class="history-item-pc">
                  <div class="history-header">
                    <span class="history-type" :class="history.changeType?.toLowerCase()">
                      {{ getDictValue('change_type', history.changeType) }}
                    </span>
                    <span class="history-time">{{ new Date(history.changedTime).toLocaleString() }}</span>
                  </div>
                  <div class="history-price" v-if="history.newPrice">
                    <span v-if="history.oldPrice" class="old-price">{{ getCurrencySymbol(product.currency) }}{{ history.oldPrice }}</span>
                    <span class="arrow">→</span>
                    <span class="new-price">{{ getCurrencySymbol(product.currency) }}{{ history.newPrice }}</span>
                  </div>
                </div>
              </div>
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
          <button class="back-btn" @click="goBack">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <h1 class="navbar-title">产品详情</h1>
        </div>
        <div class="navbar-right">
          <button class="nav-icon-btn" @click="editProduct" v-if="hasPermission(Permission.PRODUCT_EDIT)">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          </button>
        </div>
      </header>

      <!-- 主内容区 -->
      <main class="content" v-if="!loading && product">
        <!-- 价格走势（单一图表 + 时间切换） -->
        <div class="info-card trend-section" v-if="trendData30.length > 1 || trendData180.length > 1 || trendData365.length > 1">
          <div class="card-label">价格走势</div>

          <!-- 时间范围切换 -->
          <div class="trend-range-tabs-mobile">
            <button
              class="trend-tab-mobile"
              :class="{ active: selectedTrendRange === '30' }"
              @click="selectedTrendRange = '30'"
              :disabled="trendData30.length <= 1"
            >近30天</button>
            <button
              class="trend-tab-mobile"
              :class="{ active: selectedTrendRange === '180' }"
              @click="selectedTrendRange = '180'"
              :disabled="trendData180.length <= 1"
            >近180天</button>
            <button
              class="trend-tab-mobile"
              :class="{ active: selectedTrendRange === '365' }"
              @click="selectedTrendRange = '365'"
              :disabled="trendData365.length <= 1"
            >近12个月</button>
          </div>

          <v-chart class="price-chart-mobile" :option="trendChartConfig" autoresize />
          <div class="trend-hint-mobile" v-if="currentTrendData.length > 30">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            拖动查看详细
          </div>
        </div>

        <!-- 历史价格查询 -->
        <div class="info-card">
          <div class="card-label">历史价格查询</div>
          <div class="query-form-mobile">
            <input
              type="date"
              v-model="historyQueryDate"
              class="query-input-mobile"
              :max="new Date().toISOString().split('T')[0]"
            />
            <button class="query-btn-mobile" @click="queryHistoryPrice" :disabled="!historyQueryDate || historyQueryLoading">
              {{ historyQueryLoading ? '查询中...' : '查询' }}
            </button>
            <button class="query-clear-btn-mobile" @click="clearHistoryQuery" v-if="historyQueryResult" title="清除">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <div class="query-result-mobile" v-if="historyQueryResult">
            <div class="result-date">{{ historyQueryDate }}</div>
            <div class="result-row">
              <span class="result-label">售价</span>
              <span class="result-value">{{ historyQueryResult.currentPrice != null ? getCurrencySymbol(product.currency) + Number(historyQueryResult.currentPrice).toFixed(2) : '-' }}</span>
            </div>
            <div class="result-row">
              <span class="result-label">预算价</span>
              <span class="result-value">{{ historyQueryResult.budgetPrice != null ? getCurrencySymbol(product.currency) + Number(historyQueryResult.budgetPrice).toFixed(2) : '-' }}</span>
            </div>
          </div>
          <div class="query-empty-mobile" v-else-if="!historyQueryLoading && historyQueryDate">
            <span>该日期无价格记录</span>
          </div>
        </div>

        <!-- 产品信息 -->
        <div class="info-card">
          <div class="card-label">产品信息</div>
          <div class="info-grid">
            <div class="info-row">
              <span class="info-label">产品名称</span>
              <span class="info-value">{{ product.name }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">产品分类</span>
              <span class="info-value">{{ product.category?.name || '未分类' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">产品规格</span>
              <span class="info-value">{{ product.specs || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">计量单位</span>
              <span class="info-value">{{ product.unit || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">产地</span>
              <span class="info-value">{{ originName }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">显示状态</span>
              <span class="info-value">
                <span class="status-badge" :class="product.status?.toLowerCase()">
                  {{ getStatusLabel(product.status) }}
                </span>
              </span>
            </div>
          </div>
        </div>

        <!-- 价格信息 -->
        <div class="info-card">
          <div class="card-label">价格信息</div>
          <div class="info-grid">
            <div class="info-row">
              <span class="info-label">当前售价</span>
              <span class="info-value price-highlight">{{ currentPrice?.currentPrice != null ? getCurrencySymbol(product.currency) + Number(currentPrice.currentPrice).toFixed(2) : (product.sellingPrice != null ? getCurrencySymbol(product.currency) + product.sellingPrice.toFixed(2) : '-') }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">预算价格</span>
              <span class="info-value price-highlight">{{ currentPrice?.budgetPrice != null ? getCurrencySymbol(product.currency) + currentPrice.budgetPrice.toFixed(2) : (product.budgetPrice != null ? getCurrencySymbol(product.currency) + product.budgetPrice.toFixed(2) : '-') }}</span>
            </div>
          </div>
        </div>

        <!-- 客户与描述 -->
        <div class="info-card">
          <div class="card-label">客户与描述</div>
          <div class="info-row full-width">
            <span class="info-label">客户信息</span>
            <span class="info-value">{{ customerNames.length > 0 ? customerNames.join('、') : '-' }}</span>
          </div>
          <div class="info-row full-width" v-if="product.description">
            <span class="info-label">产品描述</span>
            <span class="info-value desc">{{ product.description }}</span>
          </div>
          <div class="info-row full-width" v-if="product.remark">
            <span class="info-label">备注说明</span>
            <span class="info-value desc">{{ product.remark }}</span>
          </div>
        </div>

        <!-- 近期价格变动 -->
        <div class="history-card" v-if="priceHistory.length > 0">
          <div class="card-label">近期价格变动</div>
          <div class="history-list">
            <div v-for="history in priceHistory.slice().reverse().slice(0, 10)" :key="history.id" class="history-item">
              <div class="history-main">
                <span class="history-type" :class="history.changeType?.toLowerCase()">
                  {{ getDictValue('change_type', history.changeType) }}
                </span>
                <span class="history-time">{{ new Date(history.changedTime).toLocaleString() }}</span>
              </div>
              <div class="history-price" v-if="history.newPrice">
                <span v-if="history.oldPrice" class="old-price">{{ getCurrencySymbol(product.currency) }}{{ history.oldPrice }}</span>
                <span class="arrow">→</span>
                <span class="new-price">{{ getCurrencySymbol(product.currency) }}{{ history.newPrice }}</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      <!-- 加载状态 -->
      <main class="loading-state" v-else-if="loading">
        <div class="loading-spinner"></div>
      </main>

      <!-- 底部标签栏 -->
      <footer class="tab-bar">
        <button class="tab-item" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item active" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" @click="switchTab('profile')">
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

.product-detail-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

/* ==================== PC布局 ==================== */
.pc-detail {
  padding: 32px;
  max-width: 1400px;
  margin: 0 auto;
}

/* 价格走势区域 */
.trend-section-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
  margin-bottom: 24px;
}

.trend-header-pc {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.trend-main-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0;
}

.trend-range-tabs {
  display: flex;
  gap: 8px;
}

.trend-tab {
  padding: 6px 16px;
  border: 1px solid #E5E5E5;
  background: #FFFFFF;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.trend-tab:hover:not(:disabled) {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.trend-tab.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

.trend-tab:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.price-chart-pc {
  width: 100%;
  height: 320px;
}

.trend-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #999;
  margin-top: 8px;
  justify-content: center;
}

.empty-trend-pc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 48px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  color: #999;
  font-size: 14px;
}

/* 移动端走势图 */
.trend-section {
  padding: 16px;
}

.trend-range-tabs-mobile {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.trend-tab-mobile {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #E5E5E5;
  background: #FFFFFF;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.trend-tab-mobile:hover:not(:disabled) {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.trend-tab-mobile.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

.trend-tab-mobile:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.price-chart-mobile {
  width: 100%;
  height: 220px;
}

.trend-hint-mobile {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #999;
  margin-top: 8px;
  justify-content: center;
}

.page-header-pc {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.back-btn-pc {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  cursor: pointer;
}

.back-btn-pc:hover {
  background: #F5F5F5;
}

.page-title-pc {
  flex: 1;
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.detail-grid-pc {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 24px;
  align-items: stretch;
}

.detail-main-pc {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.info-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.section-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
}

.info-grid-pc {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 32px;
}

.info-row-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #F5F5F5;
}

.info-row-pc:last-child {
  border-bottom: none;
}

.info-row-pc.full-row {
  grid-column: 1 / -1;
}

.info-key {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #888888;
  flex-shrink: 0;
}

.info-value {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-value.desc {
  white-space: normal;
  word-break: break-all;
  text-align: left;
  line-height: 1.5;
}

.info-value.price-highlight {
  color: #0D6E6E;
  font-weight: 600;
  font-family: 'JetBrains Mono', monospace;
  font-size: 15px;
}

.detail-sidebar-pc {
  position: sticky;
  top: 96px;
  align-self: flex-start;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.history-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.history-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 12px 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #F0F0F0;
  flex-shrink: 0;
}

.history-list-pc {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  max-height: 280px;
}

.history-item-pc {
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.history-type {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.history-type.create {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.history-type.update {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.history-type.delete {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.history-time {
  font-size: 12px;
  color: #888888;
}

.history-price {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.old-price {
  color: #888888;
  text-decoration: line-through;
}

.arrow {
  color: #888888;
}

.new-price {
  color: #0D6E6E;
  font-weight: 600;
}

/* 历史价格查询卡片 */
.history-query-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.query-form-pc {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}

.query-input-pc {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  background: #FAFAFA;
}

.query-input-pc:focus {
  outline: none;
  border-color: #0D6E6E;
}

.query-btn-pc {
  padding: 10px 16px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s;
}

.query-btn-pc:hover:not(:disabled) {
  background: #0A5A5A;
}

.query-btn-pc:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.query-clear-btn-pc {
  padding: 8px;
  background: transparent;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.query-clear-btn-pc:hover {
  border-color: #EF4444;
  color: #EF4444;
}

.query-result-pc {
  background: #FAFAFA;
  border-radius: 8px;
  padding: 16px;
}

.result-date-pc {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #666;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #E5E5E5;
}

.result-row-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
}

.result-label {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #888;
}

.result-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
  color: #0D6E6E;
  font-weight: 600;
}

.query-empty-pc {
  text-align: center;
  padding: 16px;
  color: #999;
  font-size: 13px;
}

.query-hint-pc {
  text-align: center;
  padding: 8px;
  color: #aaa;
  font-size: 12px;
}

/* 移动端查询样式 */
.query-form-mobile {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.query-input-mobile {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  background: #FAFAFA;
}

.query-input-mobile:focus {
  outline: none;
  border-color: #0D6E6E;
}

.query-btn-mobile {
  padding: 10px 16px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  cursor: pointer;
}

.query-btn-mobile:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.query-clear-btn-mobile {
  padding: 8px;
  background: transparent;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  color: #999;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.query-result-mobile {
  background: #FAFAFA;
  border-radius: 8px;
  padding: 12px;
}

.result-date {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #E5E5E5;
}

.result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.query-empty-mobile {
  text-align: center;
  padding: 12px;
  color: #999;
  font-size: 12px;
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
  gap: 12px;
}

.back-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #1A1A1A;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.back-btn:hover {
  background: #F5F5F5;
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
  gap: 8px;
}

.nav-icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #0D6E6E;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 100px;
}

.info-card,
.history-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
}

.card-label {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #F0F0F0;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid #F5F5F5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-row.full-width {
  grid-column: 1 / -1;
}

.info-label {
  font-size: 13px;
  color: #888888;
  flex-shrink: 0;
}

.info-value {
  font-size: 13px;
  color: #1A1A1A;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-value.desc {
  white-space: normal;
  word-break: break-all;
  text-align: left;
  line-height: 1.4;
}

.status-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.status-badge.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
}

.history-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.history-type {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.history-type.create {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.history-type.update {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.history-type.delete {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.history-time {
  font-size: 12px;
  color: #888888;
}

.history-price {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.old-price {
  color: #888888;
  text-decoration: line-through;
}

.new-price {
  color: #0D6E6E;
  font-weight: 600;
}

.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #E5E5E5;
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
  .pc-detail {
    display: none;
  }
}
</style>
