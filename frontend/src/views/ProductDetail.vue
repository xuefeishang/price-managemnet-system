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
  getProductAnnualBudget,
  getProductPriceYears,
  getProduct,
  type PriceTrendPoint
} from '@/api/products'
import { getCurrencySymbol, getCustomerName, getDictOptions, getOriginName, getStatusLabel, loadAllDicts } from '@/composables/useDict'
import { getCategoryCardStyleByCode, getCategoryVisualByCode } from '@/composables/useCategoryVisual'
import { Permission, usePermission } from '@/composables/usePermission'
import { useTheme } from '@/composables/useTheme'
import { getUnitOptions } from '@/constants/units'
import { eventBus } from '@/utils/eventBus'
import type { Price, Product } from '@/types'

use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()
const { themeConfig } = useTheme()

const product = ref<Product | null>(null)
const currentPrice = ref<Price | null>(null)
const annualBudgetPrice = ref<number | null>(null)
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
const trendLoading = ref(false)
const selectedTrendRange = ref<'30' | '180' | '365'>('30')
const selectedTrendYear = ref('rolling')
const availableTrendYears = ref<number[]>([])
const trendData = ref<Record<'30' | '180' | '365', PriceTrendPoint[]>>({ '30': [], '180': [], '365': [] })
const comparisonPrices = ref<Record<'yesterday' | 'lastWeek' | 'lastMonth' | 'lastYear', Price | null>>({
  yesterday: null,
  lastWeek: null,
  lastMonth: null,
  lastYear: null
})

const resolveOptionValue = (category: string, rawValue?: string, defaultValue = '') => {
  const value = rawValue?.trim() || defaultValue
  const option = getDictOptions(category).find(item => item.value === value || item.label === value)
  return option?.value || value
}
const resolveOptionLabel = (category: string, rawValue?: string, defaultValue = '-') => {
  const value = rawValue?.trim()
  if (!value) return defaultValue
  const option = getDictOptions(category).find(item => item.value === value || item.label === value)
  return option?.label || value
}
const currencyCode = computed(() => resolveOptionValue('currency', product.value?.currency, 'CNY'))
const currencySymbol = computed(() => getCurrencySymbol(currencyCode.value))
const categoryVisual = computed(() => getCategoryVisualByCode(product.value?.category?.code))
const heroCategoryStyle = computed(() => getCategoryCardStyleByCode(product.value?.category?.code))
const displayPriceValue = computed(() => currentPrice.value?.currentPrice ?? null)
const budgetPriceValue = computed(() => annualBudgetPrice.value)
const selectedDisplayPrice = computed(() =>
  historyQueryActive.value ? historyQueryResult.value?.currentPrice ?? null : displayPriceValue.value
)
const selectedBudgetPrice = computed(() => budgetPriceValue.value)
const isHistoricalSnapshot = computed(() => historyQueryActive.value)
const trendRangeOptions = computed(() =>
  getDictOptions('chart_range')
    .map(option => ({
      key: option.value,
      label: option.label,
      days: Number(option.extra || option.value.replace(/\D/g, ''))
    }))
    .filter((option): option is { key: string; label: string; days: 30 | 180 | 365 } =>
      option.days === 30 || option.days === 180 || option.days === 365
    )
)
const trendYearOptions = computed(() => availableTrendYears.value)
const currentTrendData = computed(() => trendData.value[selectedTrendRange.value])
const validCurrentTrendPrices = computed(() =>
  currentTrendData.value
    .filter((item): item is PriceTrendPoint & { currentPrice: number } => item.currentPrice != null)
    .map(item => Number(item.currentPrice))
)
const hasCurrentTrendChartData = computed(() =>
  currentTrendData.value.some(item => item.currentPrice != null || item.budgetPrice != null)
)
const trendStats = computed(() => {
  const prices = validCurrentTrendPrices.value
  if (!prices.length) return { lowest: null, highest: null, average: null }
  return {
    lowest: Math.min(...prices),
    highest: Math.max(...prices),
    average: prices.reduce((sum, price) => sum + price, 0) / prices.length
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
const unitName = computed(() => {
  const unit = product.value?.unit?.trim()
  if (!unit) return '-'
  return getUnitOptions().find(option => option.value === unit || option.label === unit)?.label
    || resolveOptionLabel('unit', unit)
})
const currencyName = computed(() => resolveOptionLabel('currency', currencyCode.value, '人民币'))

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
const trendRangeLabel = computed(() => {
  if (selectedTrendYear.value !== 'rolling') {
    return selectedTrendRange.value === '365'
      ? `${selectedTrendYear.value} 年自然年`
      : `${selectedTrendYear.value} 年末前 ${selectedTrendRange.value} 天`
  }
  return selectedTrendRange.value === '30' ? '近 30 天' : selectedTrendRange.value === '180' ? '近 180 天' : '近 12 个月'
})
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
  const budgetColor = themeConfig.value.chartBudgetColor || 'var(--chart-budget-color)'
  const actualPriceColor = categoryVisual.value.chartLineColor || categoryVisual.value.primaryColor
  return {
    color: [actualPriceColor, budgetColor],
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
      data: data.map(item => item.date),
      axisLine: { lineStyle: { color: '#98A2B3' } },
      axisTick: { show: false },
      axisLabel: { color: '#98A2B3', fontSize: 10, formatter: formatChartDate }
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
        name: '实际价格',
        type: 'line',
        smooth: 0.35,
        connectNulls: false,
        showSymbol: validCurrentTrendPrices.value.length <= 1,
        symbol: 'circle',
        symbolSize: 5,
        data: data.map(item => item.currentPrice),
        lineStyle: { width: 2.5, color: actualPriceColor },
        itemStyle: { color: actualPriceColor }
      },
      {
        name: '预算',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: data.map(item => item.budgetPrice ?? null),
        lineStyle: { width: 2, color: budgetColor, type: 'dashed' }
      }
    ]
  }
})

const loadTrendData = async (id: number) => {
  trendLoading.value = true
  try {
    const selectedYear = selectedTrendYear.value === 'rolling' ? null : Number(selectedTrendYear.value)
    const endDate = selectedYear
      ? (selectedYear === new Date().getFullYear() ? today : `${selectedYear}-12-31`)
      : (historyQueryActive.value ? historyQueryDate.value : undefined)
    const yearStartDate = selectedYear ? `${selectedYear}-01-01` : undefined
    const [trend30Res, trend180Res, trend365Res] = await Promise.all([
      getPriceTrend(id, 30, endDate),
      getPriceTrend(id, 180, endDate),
      getPriceTrend(id, 365, endDate, yearStartDate)
    ])
    trendData.value = {
      '30': trend30Res.data ?? [],
      '180': trend180Res.data ?? [],
      '365': trend365Res.data ?? []
    }
  } finally {
    trendLoading.value = false
  }
}

const loadTrendYears = async (id: number) => {
  const response = await getProductPriceYears(id)
  availableTrendYears.value = response.data ?? []
  if (selectedTrendYear.value !== 'rolling' && !availableTrendYears.value.includes(Number(selectedTrendYear.value))) {
    selectedTrendYear.value = 'rolling'
  }
}

const loadAnnualBudget = async (id: number, dateText = today) => {
  const year = Number(dateText.slice(0, 4))
  if (!Number.isInteger(year)) {
    annualBudgetPrice.value = null
    return
  }
  try {
    const response = await getProductAnnualBudget(id, year)
    annualBudgetPrice.value = response.data?.budgetPrice ?? null
  } catch (err) {
    console.error('Failed to load annual budget:', err)
    annualBudgetPrice.value = null
  }
}

const handleTrendYearChange = async () => {
  if (!product.value) return
  if (selectedTrendYear.value !== 'rolling') selectedTrendRange.value = '365'
  await loadTrendData(product.value.id)
}

const selectTrendRange = (days: 30 | 180 | 365) => {
  selectedTrendRange.value = String(days) as '30' | '180' | '365'
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
    await loadAnnualBudget(id, historyQueryActive.value ? historyQueryDate.value : today)
    await loadTrendYears(id)
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
    await loadAnnualBudget(product.value.id, historyQueryDate.value)
    await Promise.all([loadTrendData(product.value.id), loadComparisonPrices()])
  } catch (err) {
    historyQueryResult.value = null
    console.error('Failed to query history price:', err)
  } finally {
    historyQueryLoading.value = false
  }
}

const editProduct = () => router.push(`/product-edit/${product.value?.id}`)
const goToPriceQuery = () => router.push('/price-query')
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
  <main class="detail-page" :style="heroCategoryStyle">
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
          <button class="snapshot-button" @click="goToPriceQuery">
            价格查询
          </button>
          <button v-if="hasPermission(Permission.PRODUCT_EDIT)" class="primary-button" @click="editProduct">
            <svg viewBox="0 0 24 24"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg>
            编辑产品
          </button>
        </div>
      </header>

      <section class="product-hero">
        <div class="hero-main">
          <div class="identity">
            <div class="tags">
              <span class="status-tag" :class="{ inactive: product.status === 'INACTIVE' }">{{ getStatusLabel(product.status) }}</span>
              <span v-if="product.showOnHome" class="soft-tag">首页展示</span>
              <span v-if="isHistoricalSnapshot" class="snapshot-tag">
                {{ historyQueryDate }} {{ historyQueryResult ? '有效价格快照' : '无有效价格' }}
              </span>
            </div>
            <h2>{{ product.name }}</h2>
            <p class="meta">{{ product.code || '-' }} · {{ product.category?.name || '-' }} · {{ product.specs || '-' }} / {{ unitName }} · {{ originNames }}</p>
            <p class="description">{{ product.description || product.remark || '暂无产品描述' }}</p>
          </div>
          <div class="hero-stats">
            <article class="primary-stat"><span>近期价格</span><strong>{{ formatPrice(selectedDisplayPrice) }}</strong><small>截至 {{ historyQueryDate }}</small></article>
            <article class="budget-stat"><span>年度预算</span><strong>{{ formatPrice(selectedBudgetPrice) }}</strong><small>{{ historyQueryDate.slice(0, 4) }} 年</small></article>
            <article><span>{{ trendRangeLabel }}最低</span><strong>{{ formatPrice(trendStats.lowest) }}</strong></article>
            <article><span>{{ trendRangeLabel }}最高</span><strong>{{ formatPrice(trendStats.highest) }}</strong></article>
            <article><span>{{ trendRangeLabel }}均价</span><strong>{{ formatPrice(trendStats.average) }}</strong></article>
          </div>
        </div>
        <div class="hero-comparisons">
          <article v-for="item in comparisonItems" :key="item.key" :class="getChangeClass(item.value)">
            <span>{{ item.label }}</span>
            <strong>{{ item.value == null ? '--' : formatSignedPrice(item.value) }}</strong>
            <small>{{ item.percent == null ? '--' : formatPercent(item.percent) }}</small>
          </article>
        </div>
      </section>

      <section class="detail-body">
        <article class="card trend-card">
          <div class="card-header">
            <div>
              <h3>价格走势</h3>
              <p>实际有效价格与年度预算对比</p>
            </div>
            <div class="trend-controls">
              <label class="year-select">
                <span>查看年份</span>
                <select v-model="selectedTrendYear" :disabled="trendLoading" @change="handleTrendYearChange">
                  <option value="rolling">滚动区间</option>
                  <option v-for="year in trendYearOptions" :key="year" :value="String(year)">{{ year }} 年</option>
                </select>
              </label>
              <div class="range-tabs">
                <button
                  v-for="range in trendRangeOptions"
                  :key="range.key"
                  :class="{ active: selectedTrendRange === String(range.days) }"
                  @click="selectTrendRange(range.days)"
                >
                  {{ range.label }}
                </button>
              </div>
            </div>
          </div>
          <div v-if="trendLoading" class="empty-content">正在加载价格走势...</div>
          <v-chart v-else-if="hasCurrentTrendChartData" class="price-chart" :option="chartOption" autoresize />
          <div v-else class="empty-content">该时间范围暂无足够的价格走势数据</div>
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

.page-header, .header-actions, .hero-main, .detail-body, .card-header {
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

.product-hero { margin-top: 16px; padding: 20px; border: 1px solid color-mix(in srgb, var(--category-border, var(--category-primary)) 72%, var(--border-color)); border-radius: 8px; background: linear-gradient(135deg, var(--bg-card) 0%, color-mix(in srgb, var(--category-surface) 72%, var(--bg-card)) 100%); box-shadow: 0 10px 24px var(--category-glow); }
.hero-main { align-items: stretch; gap: 24px; }
.identity { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 9px; min-width: 0; }
.tags { display: flex; gap: 7px; }
.tags span { padding: 5px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; }
.status-tag { background: color-mix(in srgb, var(--category-primary) 12%, var(--bg-card)); color: var(--category-text); }
.status-tag.inactive { background: #FDECEC; color: #C7524A; }
.soft-tag { background: color-mix(in srgb, var(--category-surface) 80%, var(--bg-card)); color: var(--category-text); }
.snapshot-tag { background: #FFF1E8; color: #9A3412; }
.identity h2 { margin: 0; color: var(--text-primary); font-size: 28px; }
.identity p { margin: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { color: var(--text-secondary); font-family: var(--font-mono); font-size: 11px; }
.description { color: var(--text-secondary); font-size: 11px; }
.hero-stats { width: min(760px, 64%); display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
.hero-stats article { min-width: 0; padding: 14px 12px; display: flex; flex-direction: column; justify-content: center; gap: 7px; border: 1px solid color-mix(in srgb, var(--category-border) 52%, var(--border-color)); border-radius: 7px; background: color-mix(in srgb, var(--bg-card) 88%, var(--category-surface)); }
.hero-stats .primary-stat { border-color: var(--category-primary); background: var(--bg-card); box-shadow: inset 0 3px var(--category-primary); }
.hero-stats .budget-stat { border-color: color-mix(in srgb, var(--chart-budget-color) 45%, var(--border-color)); background: color-mix(in srgb, var(--chart-budget-color) 8%, var(--bg-card)); }
.hero-stats span, .hero-stats small { overflow: hidden; color: var(--text-secondary); font-size: 9px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.hero-stats strong { overflow: hidden; color: var(--text-primary); font-family: var(--font-mono); font-size: 17px; text-overflow: ellipsis; white-space: nowrap; }
.hero-stats .primary-stat strong { color: var(--category-primary); font-size: 21px; }
.hero-stats .budget-stat strong { color: var(--chart-budget-color); }
.hero-comparisons { margin-top: 14px; padding-top: 14px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border-top: 1px solid color-mix(in srgb, var(--category-border) 58%, var(--border-color)); }
.hero-comparisons article { min-width: 0; padding: 0 14px; display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 4px 10px; border-right: 1px solid color-mix(in srgb, var(--category-border) 58%, var(--border-color)); }
.hero-comparisons article:first-child { padding-left: 0; }
.hero-comparisons article:last-child { padding-right: 0; border-right: 0; }
.hero-comparisons span { color: var(--text-secondary); font-size: 9px; font-weight: 700; }
.hero-comparisons strong { color: var(--text-primary); font-family: var(--font-mono); font-size: 13px; }
.hero-comparisons small { grid-row: 1 / 3; grid-column: 2; color: var(--text-secondary); font-family: var(--font-mono); font-size: 9px; }
.hero-comparisons .rise strong { color: var(--category-primary); }
.hero-comparisons .fall strong { color: var(--danger-color, #C7524A); }
.detail-body { height: 424px; margin-top: 16px; gap: 16px; }
.card { border: 1px solid #E4E7EC; border-radius: 6px; background: #FFF; }
.trend-card { flex: 1; min-width: 0; padding: 16px; display: flex; flex-direction: column; gap: 12px; }
.card-header { align-items: center; justify-content: space-between; gap: 16px; }
.card h3, .card-header h3 { margin: 0; font-size: 14px; }
.card-header p { margin: 3px 0 0; color: #98A2B3; font-size: 10px; }
.trend-controls { display: flex; align-items: center; gap: 8px; }
.year-select { display: flex; align-items: center; gap: 6px; color: #667085; font-size: 10px; font-weight: 700; }
.year-select select { height: 28px; padding: 0 24px 0 8px; border: 1px solid #D0D5DD; border-radius: 6px; background: #FFF; color: #344054; font: inherit; }
.range-tabs { display: flex; gap: 4px; }
.range-tabs button { height: 28px; padding: 0 10px; border: 0; border-radius: 6px; background: #F7F8FA; color: #667085; font-size: 10px; font-weight: 700; }
.range-tabs button.active { background: var(--category-primary, var(--detail-primary)); color: #FFF; }
.price-chart { flex: 1; min-height: 0; width: 100%; }
.info-column { width: 330px; display: flex; flex-direction: column; gap: 12px; }
.info-card { flex: 1; padding: 14px; overflow: hidden; }
.info-card dl { margin: 8px 0 0; }
.info-card dl div { min-height: 38px; display: flex; align-items: center; justify-content: space-between; gap: 16px; border-bottom: 1px solid #EAECF0; }
.info-card dt { color: #667085; font-size: 11px; }
.info-card dd { margin: 0; max-width: 65%; overflow: hidden; color: #1A1A1A; font-size: 11px; font-weight: 700; text-align: right; text-overflow: ellipsis; white-space: nowrap; }
.completeness-card { height: 110px; padding: 14px; display: flex; flex-direction: column; gap: 9px; }
.completeness-card > div { display: flex; align-items: center; justify-content: space-between; }
.completeness-card strong { color: var(--category-primary, var(--detail-primary)); font-family: var(--font-mono); }
.completeness-card small { color: #667085; font-size: 10px; }
.progress { height: 7px; overflow: hidden; border-radius: 6px; background: #EAECF0; }
.progress i { display: block; height: 100%; border-radius: inherit; background: var(--category-primary, var(--detail-primary)); }

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
  .product-hero { margin-top: 0; padding: 14px; }
  .hero-main { flex-direction: column; gap: 14px; }
  .identity { justify-content: flex-start; }
  .identity h2 { font-size: 22px; }
  .description { display: none; }
  .hero-stats { width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .hero-stats .primary-stat { grid-column: 1 / -1; }
  .hero-comparisons { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 0; }
  .hero-comparisons article:nth-child(2) { border-right: 0; }
  .hero-comparisons article:nth-child(3) { padding-left: 0; }
  .detail-body { height: auto; flex-direction: column; gap: 12px; margin-top: 12px; }
  .trend-card { height: 270px; flex: none; padding: 12px; }
  .card-header { align-items: flex-start; }
  .trend-controls { width: 100%; align-items: flex-start; flex-direction: column; }
  .range-tabs button { padding: 0 7px; font-size: 8px; }
  .info-column { width: 100%; }
  .info-card { flex: none; padding: 12px; }
  .completeness-card { display: none; }
}
</style>
