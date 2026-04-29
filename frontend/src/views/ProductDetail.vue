<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, DataZoomComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProduct, getProductPriceHistory, getCurrentPrice, getPriceTrend } from '@/api/products'
import type { PriceTrendPoint } from '@/api/products'
import { usePermission, Permission } from '@/composables/usePermission'
import { getOriginName, getCustomerName, loadAllDicts } from '@/composables/useDict'
import { getCurrencySymbol, getStatusLabel, getDictValue } from '@/composables/useDict'
import { eventBus } from '@/utils/eventBus'
import type { Product, PriceHistory, Price } from '@/types'

// 注册 ECharts 组件
use([LineChart, GridComponent, TooltipComponent, LegendComponent, DataZoomComponent, CanvasRenderer])

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

// 格式化走势日期（X轴标签）
const formatTrendDate = (dateStr: string, isShort: boolean = true): string => {
  const d = new Date(dateStr)
  if (isShort) {
    return `${d.getMonth() + 1}/${d.getDate()}`
  }
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 生成走势图配置
const buildTrendChartOption = (trendData: PriceTrendPoint[], _title: string, isShortPeriod: boolean = true) => {
  const dates = trendData.map(d => formatTrendDate(d.date, isShortPeriod))
  const fullDates = trendData.map(d => formatTrendDate(d.date, false))
  const currentPrices = trendData.map(d => d.currentPrice)
  const budgetPrices = trendData.map(d => d.budgetPrice)
  const hasBudget = budgetPrices.some(v => v != null)

  const series: any[] = [{
    name: '售价',
    type: 'line',
    data: currentPrices,
    smooth: true,
    symbol: isShortPeriod ? 'circle' : 'none',
    symbolSize: 6,
    lineStyle: { color: '#0D6E6E', width: 2 },
    itemStyle: { color: '#0D6E6E' },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: 'rgba(13, 110, 110, 0.2)' },
          { offset: 1, color: 'rgba(13, 110, 110, 0.02)' }
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
      lineStyle: { color: '#F59E0B', width: 1.5, type: 'dashed' },
      itemStyle: { color: '#F59E0B' }
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
  if (!isShortPeriod && trendData.length > 30) {
    option.dataZoom = [{
      type: 'inside',
      start: Math.max(0, 100 - (30 / trendData.length) * 100),
      end: 100
    }]
  }

  return option
}

// 3个走势图配置
const trendChart30 = computed(() => buildTrendChartOption(trendData30.value, '近30天', true))
const trendChart180 = computed(() => buildTrendChartOption(trendData180.value, '近180天', false))
const trendChart365 = computed(() => buildTrendChartOption(trendData365.value, '近12个月', false))

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
  router.push('/products')
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

        <!-- 第一行：3个走势图横排 -->
        <div class="charts-row-pc" v-if="trendData30.length > 1 || trendData180.length > 1 || trendData365.length > 1">
          <!-- 近30天走势图 -->
          <div class="chart-card-pc" v-if="trendData30.length > 1">
            <h3 class="chart-title-pc">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -2px; margin-right: 6px; color: #0D6E6E;">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              近30天走势
            </h3>
            <v-chart class="price-chart" :option="trendChart30" autoresize />
          </div>

          <!-- 近180天走势图 -->
          <div class="chart-card-pc" v-if="trendData180.length > 1">
            <h3 class="chart-title-pc">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -2px; margin-right: 6px; color: #2563EB;">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              近180天走势
            </h3>
            <v-chart class="price-chart" :option="trendChart180" autoresize />
          </div>

          <!-- 近12个月走势图 -->
          <div class="chart-card-pc" v-if="trendData365.length > 1">
            <h3 class="chart-title-pc">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="vertical-align: -2px; margin-right: 6px; color: #7C3AED;">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              近12个月走势
            </h3>
            <v-chart class="price-chart" :option="trendChart365" autoresize />
          </div>

          <!-- 暂无走势数据提示 -->
          <div class="chart-card-pc no-trend-tip" v-if="trendData30.length <= 1 && trendData180.length <= 1 && trendData365.length <= 1">
            <div class="empty-trend">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5">
                <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
              </svg>
              <span>暂无足够的价格走势数据</span>
            </div>
          </div>
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

          <!-- 右侧价格历史记录 -->
          <div class="detail-sidebar-pc">
            <div class="history-card-pc" v-if="priceHistory.length > 0">
              <h3 class="history-title-pc">价格历史记录</h3>
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
        <!-- 价格走势（第一行） -->
        <div class="info-card trend-section" v-if="trendData30.length > 1 || trendData180.length > 1 || trendData365.length > 1">
          <div class="card-label">价格走势</div>

          <!-- 近30天走势图 -->
          <div class="trend-chart-block" v-if="trendData30.length > 1">
            <h4 class="trend-chart-title">近30天走势</h4>
            <v-chart class="price-chart-mobile" :option="trendChart30" autoresize />
          </div>

          <!-- 近180天走势图 -->
          <div class="trend-chart-block" v-if="trendData180.length > 1">
            <h4 class="trend-chart-title">近180天走势</h4>
            <v-chart class="price-chart-mobile" :option="trendChart180" autoresize />
          </div>

          <!-- 近12个月走势图 -->
          <div class="trend-chart-block" v-if="trendData365.length > 1">
            <h4 class="trend-chart-title">近12个月走势</h4>
            <v-chart class="price-chart-mobile" :option="trendChart365" autoresize />
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

        <!-- 价格历史 -->
        <div class="history-card" v-if="priceHistory.length > 0">
          <div class="card-label">价格历史记录</div>
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
}

/* 价格走势图横排 */
.charts-row-pc {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

/* 价格图表 */
.chart-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.chart-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 12px 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #F0F0F0;
}

.price-chart {
  width: 100%;
  height: 240px;
}

.no-trend-tip {
  text-align: center;
  padding: 32px;
  grid-column: 1 / -1;
}

.empty-trend {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #999;
  font-size: 13px;
}

/* 移动端走势图 */
.trend-section {
  padding: 16px;
}

.trend-chart-block {
  margin-bottom: 20px;
}

.trend-chart-block:last-child {
  margin-bottom: 0;
}

.trend-chart-title {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 8px 0;
}

.price-chart-mobile {
  width: 100%;
  height: 220px;
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
  align-self: stretch;
}

.history-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.history-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 16px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
  flex-shrink: 0;
}

.history-list-pc {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  overflow-y: auto;
  max-height: 600px;
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
