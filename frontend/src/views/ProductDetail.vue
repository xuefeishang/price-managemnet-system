<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProduct, getProductPriceHistory, getCurrentPrice, getPriceTrend, getPriceByDate } from '@/api/products'
import type { PriceTrendPoint } from '@/api/products'
import { usePermission, Permission } from '@/composables/usePermission'
import { useTheme } from '@/composables/useTheme'
import { useLayout } from '@/composables/useLayout'
import { getOriginName, getCustomerName, loadAllDicts, getCurrencySymbol, getStatusLabel, getDictValue } from '@/composables/useDict'
import { eventBus } from '@/utils/eventBus'
import type { Product, PriceHistory, Price } from '@/types'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const { themeConfig } = useTheme()
const { isPCLayout } = useLayout()

const product = ref<Product | null>(null)
const currentPrice = ref<Price | null>(null)
const priceHistory = ref<PriceHistory[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const trendData30 = ref<PriceTrendPoint[]>([])
const trendData180 = ref<PriceTrendPoint[]>([])
const trendData365 = ref<PriceTrendPoint[]>([])
const selectedTrendRange = ref<'30' | '180' | '365'>('30')

const currentTrendData = computed(() => {
  switch (selectedTrendRange.value) {
    case '180': return trendData180.value
    case '365': return trendData365.value
    default: return trendData30.value
  }
})

const historyQueryDate = ref('')
const historyQueryResult = ref<Price | null>(null)
const historyQueryLoading = ref(false)

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

const maxDate = new Date().toISOString().split('T')[0]

const formatTrendDate = (dateStr: string): string => {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${String(d.getDate()).padStart(2, '0')}`
}

const formatFullDate = (dateStr: string): string => {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const buildTrendChartOption = (trendData: PriceTrendPoint[]) => {
  if (trendData.length === 0) return null

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

  if (trendData.length > 30) {
    option.dataZoom = [{
      type: 'inside',
      start: Math.max(0, 100 - (30 / trendData.length) * 100),
      end: 100
    }]
  }

  return option
}

const trendChartConfig = computed(() => buildTrendChartOption(currentTrendData.value))

const loadProduct = async () => {
  const id = route.params.id as string
  if (!id) {
    router.push('/home')
    return
  }

  loading.value = true
  error.value = null
  try {
    const productResponse = await getProduct(parseInt(id))
    product.value = productResponse.data

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
  } catch (err: any) {
    error.value = err?.message || '加载数据失败，请重试'
    console.error('Failed to load product:', err)
  } finally {
    loading.value = false
  }
}

const onRefresh = () => loadProduct()

const editProduct = () => router.push(`/product-edit/${product.value?.id}`)
const goBack = () => router.push('/home')

const activeTab = ref('products')

const switchTab = (tab: string) => {
  activeTab.value = tab
  switch (tab) {
    case 'home': router.push('/home'); break
    case 'products': router.push('/products'); break
    case 'import': router.push('/import'); break
    case 'profile': router.push('/profile'); break
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

const clearHistoryQuery = () => {
  historyQueryDate.value = ''
  historyQueryResult.value = null
}

const getChangeTypeClass = (changeType: string) => {
  switch (changeType?.toUpperCase()) {
    case 'CREATE': return 'create'
    case 'UPDATE': return 'update'
    case 'DELETE': return 'delete'
    default: return ''
  }
}
</script>

<template>
  <div class="product-detail-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-detail" v-if="!loading && product">
        <div class="page-header-pc">
          <div class="header-left-pc">
            <button class="back-btn-pc" @click="goBack">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
              返回
            </button>
            <h1 class="page-title-pc">产品详情</h1>
          </div>
          <div class="header-actions-pc">
            <button class="btn-icon-pc" @click="onRefresh" :disabled="loading" title="刷新">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: loading }">
                <path d="M21 2v6h-6"/><path d="M3 12a9 9 0 0 1 15-6.7L21 8"/>
                <path d="M3 22v-6h6"/><path d="M21 12a9 9 0 0 1-15 6.7L3 16"/>
              </svg>
            </button>
            <button class="btn-edit-pc" @click="editProduct" v-if="hasPermission(Permission.PRODUCT_EDIT)">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              编辑产品
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

        <!-- 价格走势区域 -->
        <div class="trend-section-pc" v-if="trendData30.length > 1 || trendData180.length > 1 || trendData365.length > 1">
          <div class="trend-header-pc">
            <h3 class="trend-main-title-pc">
              <svg class="trend-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              价格走势
            </h3>
            <div class="trend-range-tabs">
              <button class="trend-tab" :class="{ active: selectedTrendRange === '30' }" @click="selectedTrendRange = '30'" :disabled="trendData30.length <= 1">近30天</button>
              <button class="trend-tab" :class="{ active: selectedTrendRange === '180' }" @click="selectedTrendRange = '180'" :disabled="trendData180.length <= 1">近180天</button>
              <button class="trend-tab" :class="{ active: selectedTrendRange === '365' }" @click="selectedTrendRange = '365'" :disabled="trendData365.length <= 1">近12个月</button>
            </div>
          </div>
          <v-chart class="price-chart-pc" :option="trendChartConfig" />
          <div class="trend-hint" v-if="currentTrendData.length > 30">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            可拖动查看详细数据
          </div>
        </div>

        <div class="empty-trend-pc" v-if="trendData30.length <= 1 && trendData180.length <= 1 && trendData365.length <= 1 && !loading">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
          <span>暂无足够的价格走势数据</span>
        </div>

        <!-- 第二行：左侧产品信息 + 右侧历史记录 -->
        <div class="detail-grid-pc">
          <div class="detail-main-pc">
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

          <div class="detail-sidebar-pc">
            <div class="history-query-card-pc">
              <h3 class="history-title-pc">历史价格查询</h3>
              <div class="query-form-pc">
                <input type="date" v-model="historyQueryDate" class="query-input-pc" :max="maxDate" />
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

            <div class="history-card-pc" v-if="priceHistory.length > 0">
              <h3 class="history-title-pc">近期价格变动</h3>
              <div class="history-list-pc">
                <div v-for="history in priceHistory.slice().reverse().slice(0, 10)" :key="history.id" class="history-item-pc">
                  <div class="history-header">
                    <span class="history-type" :class="getChangeTypeClass(history.changeType)">
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

            <div class="history-card-pc empty" v-else-if="!loading">
              <div class="empty-history">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M12 8v4l3 3"/><circle cx="12" cy="12" r="10"/>
                </svg>
                <span>暂无价格变动记录</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载状态 -->
      <div class="loading-state-pc" v-else-if="loading">
        <div class="loading-spinner"></div>
      </div>

      <!-- 错误状态 -->
      <div class="error-state-pc" v-else-if="error">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p>{{ error }}</p>
        <button class="btn-retry-pc" @click="onRefresh">重试</button>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
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
          <button class="btn-icon-mobile" @click="onRefresh" :disabled="loading" title="刷新">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" :class="{ spinning: loading }">
              <path d="M21 2v6h-6"/><path d="M3 12a9 9 0 0 1 15-6.7L21 8"/>
            </svg>
          </button>
          <button class="nav-icon-btn" @click="editProduct" v-if="hasPermission(Permission.PRODUCT_EDIT)">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          </button>
        </div>
      </header>

      <main class="content" v-if="!loading && product">
        <div v-if="error" class="alert-error-mobile">
          <span>{{ error }}</span>
          <button @click="onRefresh">重试</button>
        </div>

        <div class="info-card trend-section" v-if="trendData30.length > 1 || trendData180.length > 1 || trendData365.length > 1">
          <div class="card-label">价格走势</div>
          <div class="trend-range-tabs-mobile">
            <button class="trend-tab-mobile" :class="{ active: selectedTrendRange === '30' }" @click="selectedTrendRange = '30'" :disabled="trendData30.length <= 1">近30天</button>
            <button class="trend-tab-mobile" :class="{ active: selectedTrendRange === '180' }" @click="selectedTrendRange = '180'" :disabled="trendData180.length <= 1">近180天</button>
            <button class="trend-tab-mobile" :class="{ active: selectedTrendRange === '365' }" @click="selectedTrendRange = '365'" :disabled="trendData365.length <= 1">近12个月</button>
          </div>
          <v-chart class="price-chart-mobile" :option="trendChartConfig" />
          <div class="trend-hint-mobile" v-if="currentTrendData.length > 30">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            拖动查看详细
          </div>
        </div>

        <div class="info-card">
          <div class="card-label">历史价格查询</div>
          <div class="query-form-mobile">
            <input type="date" v-model="historyQueryDate" class="query-input-mobile" :max="maxDate" />
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

        <div class="info-card" v-if="priceHistory.length > 0">
          <div class="card-label">近期价格变动</div>
          <div class="history-list">
            <div v-for="history in priceHistory.slice().reverse().slice(0, 10)" :key="history.id" class="history-item">
              <div class="history-main">
                <span class="history-type" :class="getChangeTypeClass(history.changeType)">
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

        <div class="info-card empty" v-else-if="!loading && !error">
          <div class="empty-history">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M12 8v4l3 3"/><circle cx="12" cy="12" r="10"/>
            </svg>
            <span>暂无价格变动记录</span>
          </div>
        </div>
      </main>

      <div class="loading-state" v-else-if="loading">
        <div class="loading-spinner"></div>
      </div>

      <div class="error-state" v-else-if="error">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
        </svg>
        <p>{{ error }}</p>
        <button class="btn-retry" @click="onRefresh">重试</button>
      </div>

      <footer class="tab-bar">
        <button class="tab-item" @click="switchTab('home')">
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
        <button class="tab-item" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" @click="switchTab('profile')">
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
.product-detail-page {
  min-height: 100vh;
  background-color: var(--bg-page);
}

/* ==================== PC布局 ==================== */
.pc-detail {
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
  gap: var(--spacing-md);
}

.header-actions-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.back-btn-pc {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-family: var(--font-body);
  font-size: 0.875rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.back-btn-pc:hover {
  background: var(--gray-100);
}

.page-title-pc {
  font-family: var(--font-heading);
  font-size: 1.5rem;
  font-weight: 500;
  color: var(--text-primary);
  margin: 0;
}

.btn-icon-pc {
  width: 36px;
  height: 36px;
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

.btn-edit-pc {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--gradient-primary);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  box-shadow: var(--shadow);
}

.btn-edit-pc:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-1px);
}

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
  font-size: 0.875rem;
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
  font-size: 0.75rem;
}

/* 价格走势区域 */
.trend-section-pc {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  border: 1px solid var(--border-color);
  margin-bottom: var(--spacing-xl);
}

.trend-header-pc {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-lg);
}

.trend-main-title-pc {
  font-family: var(--font-body);
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.trend-icon {
  color: var(--primary-color);
}

.trend-range-tabs {
  display: flex;
  gap: var(--spacing-sm);
}

.trend-tab {
  padding: 6px 16px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 0.8125rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.trend-tab:hover:not(:disabled) {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.trend-tab.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #FFFFFF;
}

.trend-tab:disabled { opacity: 0.4; cursor: not-allowed; }

.price-chart-pc {
  width: 100%;
  height: 320px;
}

.trend-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: var(--spacing-sm);
  justify-content: center;
}

.empty-trend-pc {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-2xl);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  font-size: 0.875rem;
  margin-bottom: var(--spacing-xl);
}

.detail-grid-pc {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: var(--spacing-xl);
  align-items: stretch;
}

.detail-main-pc {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.detail-sidebar-pc {
  position: sticky;
  top: calc(56px + var(--spacing-lg));
  align-self: flex-start;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.info-card-pc {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  border: 1px solid var(--border-color);
}

.section-title-pc {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-md);
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--gray-100);
}

.info-grid-pc {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 var(--spacing-xl);
}

.info-row-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--gray-100);
}

.info-row-pc:last-child { border-bottom: none; }
.info-row-pc.full-row { grid-column: 1 / -1; }

.info-key {
  font-family: var(--font-body);
  font-size: 0.875rem;
  color: var(--text-muted);
  flex-shrink: 0;
}

.info-value {
  font-family: var(--font-body);
  font-size: 0.875rem;
  color: var(--text-primary);
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
  color: var(--primary-color);
  font-weight: 600;
  font-family: var(--font-mono);
  font-size: 0.9375rem;
}

/* 历史价格查询卡片 */
.history-query-card-pc {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  border: 1px solid var(--border-color);
}

.history-card-pc {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  border: 1px solid var(--border-color);
}

.history-card-pc.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
}

.history-title-pc {
  font-family: var(--font-body);
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--spacing-md) 0;
  padding-bottom: var(--spacing-sm);
  border-bottom: 1px solid var(--gray-100);
}

.empty-history {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--text-muted);
  font-size: 0.8125rem;
}

.empty-history svg { color: var(--gray-300); }

.query-form-pc {
  display: flex;
  gap: var(--spacing-sm);
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.query-input-pc {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 0.875rem;
  color: var(--text-primary);
  background: var(--gray-100);
  outline: none;
  transition: border-color var(--transition-fast);
}

.query-input-pc:focus { border-color: var(--primary-color); }

.query-btn-pc {
  padding: 10px 16px;
  background: var(--primary-color);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 0.8125rem;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.query-btn-pc:hover:not(:disabled) { background: var(--primary-light); }
.query-btn-pc:disabled { opacity: 0.5; cursor: not-allowed; }

.query-clear-btn-pc {
  padding: 8px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.query-clear-btn-pc:hover {
  border-color: var(--error-color);
  color: var(--error-color);
}

.query-result-pc {
  background: var(--gray-100);
  border-radius: var(--radius);
  padding: var(--spacing-md);
}

.result-date-pc {
  font-family: var(--font-body);
  font-size: 0.8125rem;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
  padding-bottom: var(--spacing-xs);
  border-bottom: 1px solid var(--border-color);
}

.result-row-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
}

.result-label {
  font-family: var(--font-body);
  font-size: 0.8125rem;
  color: var(--text-muted);
}

.result-value {
  font-family: var(--font-mono);
  font-size: 0.875rem;
  color: var(--primary-color);
  font-weight: 600;
}

.query-empty-pc,
.query-hint-pc {
  text-align: center;
  padding: var(--spacing-md);
  color: var(--text-muted);
  font-size: 0.8125rem;
}

.history-list-pc {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  overflow-y: auto;
  max-height: 280px;
}

.history-item-pc {
  padding: 12px;
  background: var(--gray-100);
  border-radius: var(--radius);
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-xs);
}

.history-type {
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-size: 0.6875rem;
  font-weight: 500;
}

.history-type.create { background: color-mix(in srgb, var(--success-color) 15%, transparent); color: var(--success-color); }
.history-type.update { background: color-mix(in srgb, var(--warning-color) 15%, transparent); color: var(--warning-color); }
.history-type.delete { background: color-mix(in srgb, var(--error-color) 15%, transparent); color: var(--error-color); }

.history-time { font-size: 0.75rem; color: var(--text-muted); }

.history-price {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 0.875rem;
}

.old-price { color: var(--text-muted); text-decoration: line-through; }
.arrow { color: var(--text-muted); }
.new-price { color: var(--primary-color); font-weight: 600; }

/* 加载/错误状态 */
.loading-state-pc,
.error-state-pc {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl);
  color: var(--text-muted);
  font-family: var(--font-body);
  font-size: 0.875rem;
  gap: var(--spacing-md);
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--gray-200);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.btn-retry-pc {
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--primary-color);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body);
  font-size: 0.875rem;
  cursor: pointer;
}

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

.navbar-left { display: flex; align-items: center; gap: var(--spacing-sm); }

.back-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  transition: background var(--transition-fast);
}

.back-btn:hover { background: var(--gray-100); }

.navbar-title {
  font-family: var(--font-heading);
  font-size: 1.25rem;
  font-weight: 500;
  color: var(--text-primary);
  margin: 0;
}

.navbar-right { display: flex; align-items: center; gap: var(--spacing-xs); }

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

.nav-icon-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--primary-color);
  cursor: pointer;
  border-radius: var(--radius);
}

.content {
  flex: 1;
  padding: var(--spacing-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding-bottom: calc(64px + var(--spacing-lg));
}

.alert-error-mobile {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--error-bg);
  color: var(--error-color);
  border-radius: var(--radius);
  font-size: 0.8125rem;
}

.alert-error-mobile button {
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--error-color);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.75rem;
}

.info-card,
.history-card {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
}

.info-card.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100px;
}

.trend-section { padding: var(--spacing-md); }

.card-label {
  font-family: var(--font-body);
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: var(--spacing-sm);
  padding-bottom: var(--spacing-xs);
  border-bottom: 1px solid var(--gray-100);
}

.trend-range-tabs-mobile {
  display: flex;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.trend-tab-mobile {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 0.75rem;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.trend-tab-mobile:hover:not(:disabled) { border-color: var(--primary-color); color: var(--primary-color); }
.trend-tab-mobile.active { background: var(--primary-color); border-color: var(--primary-color); color: #FFFFFF; }
.trend-tab-mobile:disabled { opacity: 0.4; cursor: not-allowed; }

.price-chart-mobile { width: 100%; height: 220px; }

.trend-hint-mobile {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.6875rem;
  color: var(--text-muted);
  margin-top: var(--spacing-sm);
  justify-content: center;
}

.query-form-mobile {
  display: flex;
  gap: var(--spacing-sm);
  align-items: center;
  margin-bottom: var(--spacing-sm);
}

.query-input-mobile {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 0.875rem;
  color: var(--text-primary);
  background: var(--gray-100);
  outline: none;
}

.query-input-mobile:focus { border-color: var(--primary-color); }

.query-btn-mobile {
  padding: 10px 16px;
  background: var(--primary-color);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius-sm);
  font-family: var(--font-body);
  font-size: 0.8125rem;
  cursor: pointer;
}

.query-btn-mobile:disabled { opacity: 0.5; cursor: not-allowed; }

.query-clear-btn-mobile {
  padding: 8px;
  background: transparent;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.query-result-mobile {
  background: var(--gray-100);
  border-radius: var(--radius);
  padding: 12px;
}

.result-date {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-xs);
  padding-bottom: 4px;
  border-bottom: 1px solid var(--border-color);
}

.result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 2px 0;
}

.query-empty-mobile {
  text-align: center;
  padding: 12px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 var(--spacing-md);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid var(--gray-100);
}

.info-row:last-child { border-bottom: none; }
.info-row.full-width { grid-column: 1 / -1; }

.info-label {
  font-size: 0.8125rem;
  color: var(--text-muted);
  flex-shrink: 0;
}

.info-value {
  font-size: 0.8125rem;
  color: var(--text-primary);
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
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  font-weight: 500;
}

.status-badge.active { background: color-mix(in srgb, var(--success-color) 15%, transparent); color: var(--success-color); }
.status-badge.inactive { background: color-mix(in srgb, var(--error-color) 15%, transparent); color: var(--error-color); }

.history-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.history-item {
  padding: 12px;
  background: var(--gray-100);
  border-radius: var(--radius);
}

.history-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-xs);
}

.history-time { font-size: 0.75rem; color: var(--text-muted); }

.history-price {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 0.875rem;
}

.old-price { color: var(--text-muted); text-decoration: line-through; }
.new-price { color: var(--primary-color); font-weight: 600; }

.loading-state,
.error-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl);
  color: var(--text-muted);
  font-family: var(--font-body);
  font-size: 0.875rem;
  gap: var(--spacing-md);
}

.btn-retry {
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--primary-color);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body);
  font-size: 0.875rem;
  cursor: pointer;
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
  font-size: 0.625rem;
  font-weight: 500;
}

/* ==================== 响应式 ==================== */
@media (max-width: 1023px) {
  .pc-detail { display: none; }
}
</style>