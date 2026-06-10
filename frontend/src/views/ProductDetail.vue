<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getCurrentPrice,
  getPriceByDate,
  getPriceTrend,
  getProduct,
  type PriceTrendPoint
} from '@/api/products'
import { getCurrencySymbol, getCustomerName, getDictValue, getOriginName, getStatusLabel, loadAllDicts } from '@/composables/useDict'
import { Permission, usePermission } from '@/composables/usePermission'
import { useTheme } from '@/composables/useTheme'
import { eventBus } from '@/utils/eventBus'
import type { Price, Product } from '@/types'

use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const { themeConfig } = useTheme()

const product = ref<Product | null>(null)
const currentPrice = ref<Price | null>(null)
const loading = ref(false)
const error = ref('')
const today = new Date().toISOString().slice(0, 10)
const routeQueryDate = typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date)
  ? route.query.date
  : ''
const historyQueryDate = ref(routeQueryDate || today)
const historyQueryResult = ref<Price | null>(null)
const historyQueryActive = ref(Boolean(routeQueryDate && routeQueryDate !== today))
const historyQueryLoading = ref(false)
const selectedTrendRange = ref<'30' | '180' | '365'>('30')
const trendData = ref<Record<'30' | '180' | '365', PriceTrendPoint[]>>({ '30': [], '180': [], '365': [] })
const comparisonPrices = ref<Record<'yesterday' | 'lastWeek' | 'lastMonth' | 'lastYear', Price | null>>({
  yesterday: null,
  lastWeek: null,
  lastMonth: null,
  lastYear: null
})

const currencySymbol = computed(() => getCurrencySymbol(product.value?.currency))
const displayPriceValue = computed(() => currentPrice.value?.currentPrice ?? product.value?.sellingPrice ?? null)
const budgetPriceValue = computed(() => currentPrice.value?.budgetPrice ?? product.value?.budgetPrice ?? null)
const selectedDisplayPrice = computed(() =>
  historyQueryActive.value ? historyQueryResult.value?.currentPrice ?? null : displayPriceValue.value
)
const selectedBudgetPrice = computed(() =>
  historyQueryActive.value ? historyQueryResult.value?.budgetPrice ?? null : budgetPriceValue.value
)
const isHistoricalSnapshot = computed(() => historyQueryActive.value)
const budgetDifference = computed(() => {
  if (selectedDisplayPrice.value == null || selectedBudgetPrice.value == null) return null
  return selectedDisplayPrice.value - selectedBudgetPrice.value
})
const currentTrendData = computed(() => trendData.value[selectedTrendRange.value])
const validThirtyDayPrices = computed(() => trendData.value['30'].filter(item => item.currentPrice != null))
const thirtyDayChange = computed(() => {
  const points = validThirtyDayPrices.value
  if (points.length < 2) return null
  return Number(points[points.length - 1].currentPrice) - Number(points[0].currentPrice)
})
const thirtyDayChangeRate = computed(() => {
  const first = validThirtyDayPrices.value[0]?.currentPrice
  if (first == null || first === 0 || thirtyDayChange.value == null) return null
  return (thirtyDayChange.value / first) * 100
})
const validCurrentTrendPrices = computed(() =>
  currentTrendData.value
    .filter((item): item is PriceTrendPoint & { currentPrice: number } => item.currentPrice != null)
    .map(item => Number(item.currentPrice))
)
const trendStats = computed(() => {
  const prices = validCurrentTrendPrices.value
  if (!prices.length) return { lowest: null, highest: null, average: null, latest: null }
  return {
    lowest: Math.min(...prices),
    highest: Math.max(...prices),
    average: prices.reduce((sum, price) => sum + price, 0) / prices.length,
    latest: prices[prices.length - 1]
  }
})
const completeness = computed(() => {
  if (!product.value) return 0
  const values = [
    product.value.name,
    product.value.code,
    product.value.category?.name,
    product.value.specs,
    product.value.unit,
    product.value.originIds,
    product.value.customerIds,
    product.value.description,
    product.value.imageUrl,
    product.value.currency
  ]
  return Math.round((values.filter(Boolean).length / values.length) * 100)
})

const parseDictIds = (value?: string) => {
  if (!value) return []
  try {
    const result = JSON.parse(value)
    return Array.isArray(result) ? result : []
  } catch {
    return []
  }
}

const originNames = computed(() => parseDictIds(product.value?.originIds).map(getOriginName).filter(Boolean).join('、') || '-')
const customerNames = computed(() => parseDictIds(product.value?.customerIds).map(getCustomerName).filter(Boolean).join('、') || '-')
const unitName = computed(() => product.value?.unit ? getDictValue('unit', product.value.unit) : '-')
const currencyName = computed(() => product.value?.currency ? getDictValue('currency', product.value.currency) : '-')

const formatPrice = (value?: number | null) => value == null ? '-' : `${currencySymbol.value}${Number(value).toFixed(2)}`
const formatSignedPrice = (value?: number | null) => {
  if (value == null) return '-'
  const sign = value > 0 ? '+' : ''
  return `${sign}${currencySymbol.value}${Number(value).toFixed(2)}`
}
const formatChartDate = (value: string) => {
  const date = new Date(value)
  return `${date.getMonth() + 1}/${String(date.getDate()).padStart(2, '0')}`
}
const parseLocalDate = (value: string) => {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}
const formatLocalDate = (date: Date) =>
  `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const getRelativeDate = (days: number, years = 0) => {
  const date = parseLocalDate(historyQueryDate.value)
  if (years) date.setFullYear(date.getFullYear() - years)
  if (days) date.setDate(date.getDate() - days)
  return formatLocalDate(date)
}
const getChangeClass = (value?: number | null) => value == null || value === 0 ? 'flat' : value > 0 ? 'rise' : 'fall'
const formatPercent = (value?: number | null) => value == null ? '--' : `${value > 0 ? '+' : ''}${value.toFixed(2)}%`
const trendRangeLabel = computed(() => selectedTrendRange.value === '30' ? '近 30 天' : selectedTrendRange.value === '180' ? '近 180 天' : '近 12 个月')
const comparisonItems = computed(() => {
  const current = selectedDisplayPrice.value
  const build = (
    key: keyof typeof comparisonPrices.value,
    label: string,
    periodLabel: string,
    date: string
  ) => {
    const comparePrice = comparisonPrices.value[key]?.currentPrice ?? null
    const value = current == null || comparePrice == null ? null : Number(current) - Number(comparePrice)
    const percent = value == null || comparePrice === 0 ? null : (value / Number(comparePrice)) * 100
    return { key, label, periodLabel, date, value, percent }
  }
  return [
    build('yesterday', '较昨日', '日环比', getRelativeDate(1)),
    build('lastWeek', '较上周', '周环比', getRelativeDate(7)),
    build('lastMonth', '较上月', '月环比', getRelativeDate(30)),
    build('lastYear', '较去年同期', '同比', getRelativeDate(0, 1))
  ]
})

const chartOption = computed(() => {
  const data = currentTrendData.value
  const budgetColor = '#E07B54'
  return {
    color: [themeConfig.value.chartPrimaryColor, budgetColor],
    tooltip: {
      trigger: 'axis',
      valueFormatter: (value: number | null) => formatPrice(value)
    },
    legend: {
      top: 0,
      right: 0,
      itemWidth: 18,
      itemHeight: 3,
      textStyle: { color: '#667085', fontSize: 11 }
    },
    grid: { left: 16, right: 10, top: 38, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map(item => formatChartDate(item.date)),
      axisLine: { lineStyle: { color: '#98A2B3' } },
      axisTick: { show: false },
      axisLabel: { color: '#98A2B3', fontSize: 10 }
    },
    yAxis: {
      type: 'value',
      name: '价格',
      nameTextStyle: { color: '#98A2B3', fontSize: 10 },
      axisLine: { show: true, lineStyle: { color: '#98A2B3' } },
      axisLabel: { color: '#98A2B3', fontSize: 10, formatter: (value: number) => `${currencySymbol.value}${value}` },
      splitLine: { lineStyle: { color: '#EAECF0' } }
    },
    series: [
      {
        name: '售价',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: data.map(item => item.currentPrice),
        lineStyle: { width: 2.5, color: themeConfig.value.chartPrimaryColor }
      },
      {
        name: '预算',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: data.map(() => selectedBudgetPrice.value),
        lineStyle: { width: 2, color: budgetColor, type: 'dashed' }
      }
    ]
  }
})

const loadTrendData = async (id: number) => {
  const endDate = historyQueryActive.value ? historyQueryDate.value : undefined
  const [trend30Res, trend180Res, trend365Res] = await Promise.all([
    getPriceTrend(id, 30, endDate),
    getPriceTrend(id, 180, endDate),
    getPriceTrend(id, 365, endDate)
  ])
  trendData.value = {
    '30': trend30Res.data ?? [],
    '180': trend180Res.data ?? [],
    '365': trend365Res.data ?? []
  }
}

const loadProduct = async () => {
  const id = Number(route.params.id)
  if (!id) return router.push('/products')
  loading.value = true
  error.value = ''
  try {
    const [productRes, currentRes] = await Promise.all([
      getProduct(id),
      getCurrentPrice(id)
    ])
    product.value = productRes.data
    currentPrice.value = currentRes.data ?? null
    await loadTrendData(id)
    if (historyQueryActive.value) {
      const historyResponse = await getPriceByDate(id, historyQueryDate.value)
      historyQueryResult.value = historyResponse.data ?? null
    }
    await loadComparisonPrices()
  } catch (err: any) {
    error.value = err?.message || '加载产品详情失败'
  } finally {
    loading.value = false
  }
}

const loadComparisonPrices = async () => {
  if (!product.value) return
  const id = product.value.id
  try {
    const [yesterday, lastWeek, lastMonth, lastYear] = await Promise.all([
      getPriceByDate(id, getRelativeDate(1)),
      getPriceByDate(id, getRelativeDate(7)),
      getPriceByDate(id, getRelativeDate(30)),
      getPriceByDate(id, getRelativeDate(0, 1))
    ])
    comparisonPrices.value = {
      yesterday: yesterday.data ?? null,
      lastWeek: lastWeek.data ?? null,
      lastMonth: lastMonth.data ?? null,
      lastYear: lastYear.data ?? null
    }
  } catch (err) {
    console.error('Failed to load comparison prices:', err)
  }
}

const queryHistoryPrice = async () => {
  if (!product.value || !historyQueryDate.value) return
  historyQueryLoading.value = true
  historyQueryActive.value = true
  try {
    const response = await getPriceByDate(product.value.id, historyQueryDate.value)
    historyQueryResult.value = response.data ?? null
    await Promise.all([loadTrendData(product.value.id), loadComparisonPrices()])
  } catch (err) {
    historyQueryResult.value = null
    console.error('Failed to query history price:', err)
  } finally {
    historyQueryLoading.value = false
  }
}

const showCurrentPrice = async () => {
  historyQueryDate.value = today
  historyQueryResult.value = null
  historyQueryActive.value = false
  if (product.value) await Promise.all([loadTrendData(product.value.id), loadComparisonPrices()])
}

const editProduct = () => router.push(`/product-edit/${product.value?.id}`)
const handleProductUpdated = (id: number | null) => {
  if (!id || id === Number(route.params.id)) loadProduct()
}

onMounted(async () => {
  await loadAllDicts()
  await loadProduct()
  eventBus.on('product-updated', handleProductUpdated)
})
onUnmounted(() => eventBus.off('product-updated', handleProductUpdated))
</script>

<template>
  <main class="detail-page">
    <div v-if="loading" class="state-panel">
      <span class="spinner"></span>
      <span>正在加载产品详情</span>
    </div>

    <div v-else-if="error || !product" class="state-panel error">
      <strong>{{ error || '未找到产品' }}</strong>
      <button @click="loadProduct">重新加载</button>
    </div>

    <template v-else>
      <header class="page-header">
        <div class="title-block">
          <button class="breadcrumb" @click="router.push('/products')">产品管理</button>
          <span>/</span>
          <span>产品详情</span>
          <h1>产品详情</h1>
        </div>
        <div class="header-actions">
          <button class="icon-button" title="刷新" @click="loadProduct">
            <svg viewBox="0 0 24 24"><path d="M20 11a8.1 8.1 0 0 0-15.5-2M4 4v5h5M4 13a8.1 8.1 0 0 0 15.5 2m.5 5v-5h-5"/></svg>
          </button>
          <label class="date-button" :class="{ loading: historyQueryLoading }" title="查看指定日期价格">
            <svg viewBox="0 0 24 24"><path d="M8 2v4m8-4v4M3 10h18M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Z"/></svg>
            <input v-model="historyQueryDate" type="date" :disabled="historyQueryLoading" :max="new Date().toISOString().slice(0, 10)" @change="queryHistoryPrice">
          </label>
          <button v-if="isHistoricalSnapshot" class="snapshot-button" :disabled="historyQueryLoading" @click="showCurrentPrice">
            返回当前
          </button>
          <button v-if="hasPermission(Permission.PRODUCT_EDIT)" class="primary-button" @click="editProduct">
            <svg viewBox="0 0 24 24"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg>
            编辑产品
          </button>
        </div>
      </header>

      <section class="product-hero">
        <div class="identity">
          <div class="tags">
            <span class="status-tag" :class="{ inactive: product.status === 'INACTIVE' }">{{ getStatusLabel(product.status) }}</span>
            <span v-if="product.showOnHome" class="soft-tag">首页展示</span>
            <span v-if="isHistoricalSnapshot" class="snapshot-tag">
              {{ historyQueryDate }} {{ historyQueryResult ? '历史快照' : '无价格记录' }}
            </span>
          </div>
          <h2>{{ product.name }}</h2>
          <p class="meta">{{ product.code || '-' }} · {{ product.category?.name || '-' }} · {{ product.specs || '-' }} / {{ unitName }} · {{ originNames }}</p>
          <p class="description">{{ product.description || product.remark || '暂无产品描述' }}</p>
        </div>
        <div class="price-metrics">
          <article>
            <span>{{ isHistoricalSnapshot ? '基准日售价' : '当前售价' }}</span>
            <strong>{{ formatPrice(selectedDisplayPrice) }}</strong>
            <small>{{ isHistoricalSnapshot ? (historyQueryResult ? `${historyQueryDate} 快照` : '该日期暂无有效价格') : '当前有效价格' }}</small>
          </article>
          <article>
            <span>预算价格</span>
            <strong>{{ formatPrice(selectedBudgetPrice) }}</strong>
            <small>差额 {{ formatSignedPrice(budgetDifference) }}</small>
          </article>
          <article>
            <span>近 30 日变化</span>
            <strong :class="{ negative: (thirtyDayChange ?? 0) < 0 }">{{ formatSignedPrice(thirtyDayChange) }}</strong>
            <small>{{ thirtyDayChangeRate == null ? '数据不足' : `${thirtyDayChangeRate >= 0 ? '+' : ''}${thirtyDayChangeRate.toFixed(1)}%` }}</small>
          </article>
        </div>
      </section>

      <section class="detail-body">
        <article class="card trend-card">
          <div class="card-header">
            <div>
              <h3>价格走势</h3>
              <p>售价与预算价格对比</p>
            </div>
            <div class="range-tabs">
              <button v-for="range in ['30', '180', '365'] as const" :key="range" :class="{ active: selectedTrendRange === range }" @click="selectedTrendRange = range">
                {{ range === '30' ? '近30天' : range === '180' ? '近180天' : '近12个月' }}
              </button>
            </div>
          </div>
          <v-chart v-if="currentTrendData.length > 1" class="price-chart" :option="chartOption" autoresize />
          <div v-else class="empty-content">暂无足够的价格走势数据</div>
        </article>

        <aside class="info-column">
          <article class="card info-card">
            <h3>基础资料</h3>
            <dl>
              <div><dt>分类</dt><dd>{{ product.category?.name || '-' }}</dd></div>
              <div><dt>规格</dt><dd>{{ product.specs || '-' }}</dd></div>
              <div><dt>计量单位</dt><dd>{{ unitName }}</dd></div>
              <div><dt>产地</dt><dd>{{ originNames }}</dd></div>
              <div><dt>适用客户</dt><dd>{{ customerNames }}</dd></div>
              <div><dt>币种</dt><dd>{{ currencyName }}</dd></div>
            </dl>
          </article>
          <article class="card completeness-card">
            <div><h3>资料完整度</h3><strong>{{ completeness }}%</strong></div>
            <span class="progress"><i :style="{ width: `${completeness}%` }"></i></span>
            <small>{{ completeness === 100 ? '产品资料已完整' : '继续完善产品资料可提升可读性' }}</small>
          </article>
        </aside>
      </section>

      <section class="card analytics-card">
        <div class="card-header">
          <div>
            <h3>价格统计与对比分析</h3>
            <p>统计范围：{{ trendRangeLabel }}；对比基准日：{{ historyQueryDate }}</p>
          </div>
        </div>
        <div class="stat-grid">
          <article>
            <span>最低价</span>
            <strong>{{ formatPrice(trendStats.lowest) }}</strong>
          </article>
          <article>
            <span>最高价</span>
            <strong>{{ formatPrice(trendStats.highest) }}</strong>
          </article>
          <article>
            <span>平均价</span>
            <strong>{{ formatPrice(trendStats.average) }}</strong>
          </article>
          <article class="budget-stat">
            <span>预算价</span>
            <strong>{{ formatPrice(selectedBudgetPrice) }}</strong>
          </article>
          <article class="latest-stat">
            <span>{{ isHistoricalSnapshot ? '基准日价格' : '最新价' }}</span>
            <strong>{{ formatPrice(isHistoricalSnapshot ? selectedDisplayPrice : selectedDisplayPrice ?? trendStats.latest) }}</strong>
          </article>
        </div>
        <div class="comparison-grid">
          <article v-for="item in comparisonItems" :key="item.key" :class="getChangeClass(item.value)">
            <div class="comparison-title">
              <span>{{ item.label }}</span>
              <em>{{ item.periodLabel }}</em>
            </div>
            <strong>{{ item.value == null ? '--' : formatSignedPrice(item.value) }}</strong>
            <small>{{ item.percent == null ? '--' : formatPercent(item.percent) }}</small>
            <p>较 {{ item.date }} 的价格变化</p>
          </article>
        </div>
      </section>

    </template>
  </main>
</template>

<style scoped>
.detail-page {
  --detail-primary: var(--primary-color, #0D6E6E);
  --detail-deep: #0A5555;
  --detail-orange: #E07B54;
  min-height: 100%;
  padding: 24px;
  background: var(--bg-main, #F4F6F8);
  color: var(--text-primary, #1A1A1A);
}

button, input { font: inherit; }
button { cursor: pointer; }
svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }

.page-header, .header-actions, .product-hero, .price-metrics, .detail-body, .card-header {
  display: flex;
}
.page-header { min-height: 64px; align-items: center; justify-content: space-between; gap: 20px; }
.title-block { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; color: #98A2B3; font-size: 11px; }
.title-block h1 { width: 100%; margin: 0; color: #1A1A1A; font-size: 26px; line-height: 1.1; }
.breadcrumb { padding: 0; border: 0; background: none; color: var(--detail-primary); font-weight: 700; }
.header-actions { align-items: center; gap: 8px; }
.icon-button, .date-button, .snapshot-button, .primary-button { height: 36px; border-radius: 6px; display: inline-flex; align-items: center; justify-content: center; gap: 7px; }
.icon-button { width: 36px; border: 1px solid #D0D5DD; background: #FFF; color: #667085; }
.date-button { padding: 0 12px; border: 1px solid #D0D5DD; background: #FFF; color: var(--detail-primary); }
.date-button.loading { opacity: .65; }
.date-button input { width: 108px; border: 0; outline: 0; color: #344054; background: transparent; font-family: var(--font-mono); font-size: 11px; }
.snapshot-button { padding: 0 12px; border: 1px solid #D0D5DD; background: #FFF; color: #344054; font-size: 11px; font-weight: 700; }
.snapshot-button:disabled { cursor: wait; opacity: .55; }
.primary-button { padding: 0 14px; border: 0; background: var(--detail-primary); color: #FFF; font-size: 12px; font-weight: 700; }

.product-hero { min-height: 170px; margin-top: 16px; padding: 20px; align-items: stretch; gap: 18px; border-radius: 6px; background: var(--detail-deep); }
.identity { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 9px; min-width: 0; }
.tags { display: flex; gap: 7px; }
.tags span { padding: 5px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; }
.status-tag { background: #E7F3F3; color: var(--detail-primary); }
.status-tag.inactive { background: #FDECEC; color: #C7524A; }
.soft-tag { background: #FFFFFF22; color: #FFF; }
.snapshot-tag { background: #FFF1E8; color: #9A3412; }
.identity h2 { margin: 0; color: #FFF; font-size: 28px; }
.identity p { margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { color: #D0D5DD; font-family: var(--font-mono); font-size: 11px; }
.description { color: #B8D8D8; font-size: 11px; }
.price-metrics { width: 430px; gap: 8px; }
.price-metrics article { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 5px; border-radius: 6px; background: #FFF; }
.price-metrics article { padding: 12px; }
.price-metrics span { color: #667085; font-size: 10px; font-weight: 600; }
.price-metrics strong { font-family: var(--font-mono); font-size: 20px; }
.price-metrics small { color: #98A2B3; font-size: 9px; }
.price-metrics article:last-child strong { color: var(--detail-primary); }
.negative { color: #C7524A !important; }

.detail-body { height: 424px; margin-top: 16px; gap: 16px; }
.card { border: 1px solid #E4E7EC; border-radius: 6px; background: #FFF; }
.trend-card { flex: 1; min-width: 0; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.card-header { align-items: center; justify-content: space-between; gap: 16px; }
.card h3, .card-header h3 { margin: 0; font-size: 14px; }
.card-header p { margin: 3px 0 0; color: #98A2B3; font-size: 10px; }
.range-tabs { display: flex; gap: 4px; }
.range-tabs button { height: 28px; padding: 0 10px; border: 0; border-radius: 6px; background: #F7F8FA; color: #667085; font-size: 10px; font-weight: 700; }
.range-tabs button.active { background: var(--detail-primary); color: #FFF; }
.price-chart { flex: 1; min-height: 0; width: 100%; }
.info-column { width: 330px; display: flex; flex-direction: column; gap: 12px; }
.info-card { flex: 1; padding: 14px; overflow: hidden; }
.info-card dl { margin: 8px 0 0; }
.info-card dl div { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 16px; border-bottom: 1px solid #EAECF0; }
.info-card dt { color: #667085; font-size: 11px; }
.info-card dd { margin: 0; max-width: 65%; overflow: hidden; color: #1A1A1A; font-size: 11px; font-weight: 700; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.completeness-card { height: 110px; padding: 14px; display: flex; flex-direction: column; gap: 9px; }
.completeness-card > div { display: flex; align-items: center; justify-content: space-between; }
.completeness-card strong { color: var(--detail-primary); font-family: var(--font-mono); }
.completeness-card small { color: #667085; font-size: 10px; }
.progress { height: 7px; overflow: hidden; border-radius: 6px; background: #EAECF0; }
.progress i { display: block; height: 100%; border-radius: inherit; background: var(--detail-primary); }

.analytics-card { margin-top: 16px; padding: 16px; }
.stat-grid { margin-top: 12px; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
.stat-grid article { min-height: 74px; padding: 12px; display: flex; flex-direction: column; justify-content: center; gap: 7px; border-radius: 6px; background: #F7F8FA; border-top: 3px solid #DDEEEE; }
.stat-grid span { color: #667085; font-size: 10px; font-weight: 600; }
.stat-grid strong { font-family: var(--font-mono); font-size: 18px; }
.stat-grid .budget-stat { border-top-color: var(--detail-orange); }
.stat-grid .budget-stat strong { color: var(--detail-orange); }
.stat-grid .latest-stat { border-top-color: var(--detail-primary); background: #F2F8F8; }
.stat-grid .latest-stat strong { color: var(--detail-primary); }
.comparison-grid { margin-top: 10px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.comparison-grid article { min-height: 104px; padding: 12px; display: grid; grid-template-columns: 1fr auto; align-content: center; gap: 5px 10px; border: 1px solid #EAECF0; border-radius: 6px; background: #FFF; }
.comparison-title { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.comparison-title span { color: #344054; font-size: 11px; font-weight: 700; }
.comparison-title em { padding: 3px 6px; border-radius: 6px; background: #F2F4F7; color: #667085; font-size: 9px; font-style: normal; }
.comparison-grid strong { font-family: var(--font-mono); font-size: 16px; }
.comparison-grid small { align-self: center; color: #667085; font-family: var(--font-mono); font-size: 10px; text-align: right; }
.comparison-grid p { grid-column: 1 / -1; margin: 0; color: #98A2B3; font-size: 9px; }
.comparison-grid article.rise { border-left: 3px solid var(--detail-primary); }
.comparison-grid article.rise strong { color: var(--detail-primary); }
.comparison-grid article.fall { border-left: 3px solid #C7524A; }
.comparison-grid article.fall strong { color: #C7524A; }
.comparison-grid article.flat { border-left: 3px solid #D0D5DD; }

.empty-content { flex: 1; display: grid; place-items: center; color: #98A2B3; font-size: 12px; }

.state-panel { min-height: 420px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; color: #667085; }
.state-panel button { padding: 8px 14px; border: 0; border-radius: 6px; background: var(--detail-primary); color: #FFF; }
.spinner { width: 30px; height: 30px; border: 3px solid #DDEEEE; border-top-color: var(--detail-primary); border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 1023px) {
  .detail-page { padding: 0 16px 20px; }
  .page-header { min-height: 64px; position: sticky; top: 0; z-index: 5; background: var(--bg-main, #F4F6F8); }
  .title-block { flex: 1; }
  .title-block > span, .breadcrumb { display: none; }
  .title-block h1 { font-size: 18px; }
  .icon-button { display: none; }
  .date-button { width: 36px; padding: 0; }
  .date-button input { position: absolute; width: 36px; opacity: 0; }
  .snapshot-button { height: 32px; padding: 0 9px; font-size: 9px; }
  .primary-button { width: 36px; padding: 0; font-size: 0; }
  .product-hero { min-height: 188px; margin-top: 0; padding: 14px; flex-direction: column; gap: 10px; }
  .identity { justify-content: flex-start; }
  .identity h2 { font-size: 22px; }
  .description { display: none; }
  .price-metrics { width: 100%; min-height: 66px; }
  .price-metrics article { padding: 9px; }
  .price-metrics strong { font-size: 14px; }
  .detail-body { height: auto; flex-direction: column; gap: 12px; margin-top: 12px; }
  .trend-card { height: 270px; flex: none; padding: 12px; }
  .card-header { align-items: flex-start; }
  .range-tabs button { padding: 0 7px; font-size: 8px; }
  .info-column { width: 100%; }
  .info-card { flex: none; padding: 12px; }
  .completeness-card { display: none; }
  .analytics-card { margin-top: 12px; padding: 12px; }
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .stat-grid .latest-stat { grid-column: 1 / -1; }
  .comparison-grid { grid-template-columns: 1fr; }
}
</style>
