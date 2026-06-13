<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getCategories } from '@/api/categories'
import { getPriceTrend, getProductAnnualBudgets, saveProductAnnualBudgets, type PriceTrendPoint } from '@/api/products'
import EmptyState from '@/components/EmptyState.vue'
import { useLayout } from '@/composables/useLayout'
import { getCurrencySymbol, getDictValue, loadAllDicts } from '@/composables/useDict'
import { getCategoryCardStyle, registerCategoryCodes } from '@/composables/useCategoryVisual'
import { useTheme } from '@/composables/useTheme'
import type { ProductAnnualBudget, ProductCategory } from '@/types'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const { isPCLayout } = useLayout()
const { themeConfig } = useTheme()

const currentYear = new Date().getFullYear()
const chartTheme = computed(() => ({
  primary: themeConfig.value.chartPrimaryColor || 'var(--chart-primary-color)',
  budget: themeConfig.value.chartBudgetColor || 'var(--chart-budget-color)',
  label: 'var(--text-secondary)',
  tooltipText: 'var(--text-primary)',
  border: 'var(--border-color)',
  splitLine: 'var(--gray-100)'
}))
const selectedYear = ref(currentYear)
const yearInput = ref(String(currentYear))
const keyword = ref('')
const debouncedKeyword = ref('')
const selectedCategoryId = ref<number | ''>('')
const appliedCategoryId = ref<number | ''>('')
const categories = ref<ProductCategory[]>([])
const budgets = ref<ProductAnnualBudget[]>([])
const loading = ref(false)
const saving = ref(false)
const selectedProductId = ref<number | null>(null)
const trendLoading = ref(false)
const trendData = ref<PriceTrendPoint[]>([])
const editMap = ref<Map<number, string>>(new Map())
const originalMap = ref<Map<number, string>>(new Map())
const budgetPage = ref(0)
const budgetPageSize = ref(8)
const budgetJumpPage = ref('1')
let searchTimer: ReturnType<typeof setTimeout> | null = null

const budgetTotalPages = computed(() => Math.max(Math.ceil(budgets.value.length / budgetPageSize.value), 1))
const paginatedBudgets = computed(() => {
  const start = budgetPage.value * budgetPageSize.value
  return budgets.value.slice(start, start + budgetPageSize.value)
})
const paginationItems = computed<Array<number | string>>(() => {
  const total = budgetTotalPages.value
  const current = budgetPage.value + 1
  if (total <= 5) {
    return Array.from({ length: total }, (_, index) => index + 1)
  }

  let pages: number[]
  if (current <= 3) {
    pages = [1, 2, 3, 4, total]
  } else if (current >= total - 2) {
    pages = [1, total - 3, total - 2, total - 1, total]
  } else {
    pages = [1, current - 1, current, current + 1, total]
  }

  const items: Array<number | string> = []
  const normalizedPages = [...new Set(pages)].filter(page => page >= 1 && page <= total).sort((a, b) => a - b)
  normalizedPages.forEach((page, index) => {
    const previous = normalizedPages[index - 1]
    if (previous && page - previous > 1) items.push(`ellipsis-${previous}-${page}`)
    items.push(page)
  })
  return items
})

const selectedBudget = computed(() =>
  budgets.value.find(item => item.productId === selectedProductId.value) || budgets.value[0] || null
)
const selectedBudgetValue = computed(() => selectedBudget.value ? toNumber(editMap.value.get(selectedBudget.value.productId)) : null)
const hasTrendChartData = computed(() =>
  trendData.value.some(point => point.currentPrice != null || point.budgetPrice != null)
)
const trendChartOption = computed(() => {
  const item = selectedBudget.value
  if (!item || !hasTrendChartData.value) return {}
  const colors = chartTheme.value
  const priceColor = colors.primary
  const budgetColor = colors.budget
  const dates = trendData.value.map(point => formatAxisDate(point.date))
  const prices = trendData.value.map(point => point.currentPrice == null ? null : Number(point.currentPrice))
  const budgets = trendData.value.map(point => point.budgetPrice == null ? null : Number(point.budgetPrice))
  const budgetLineValue = selectedBudgetValue.value
  const areaColor = themeConfig.value.chartPrimaryColor ? `${themeConfig.value.chartPrimaryColor}24` : 'rgba(13, 110, 110, 0.12)'
  const referenceLines = [
    budgetLineValue == null ? null : Number(budgetLineValue)
  ].filter((value): value is number => value != null)
  const comparableValues = [
    ...prices.filter((value): value is number => value != null),
    ...budgets.filter((value): value is number => value != null),
    ...referenceLines
  ]
  if (comparableValues.length === 0) return {}
  const min = Math.min(...comparableValues)
  const max = Math.max(...comparableValues)
  const padding = Math.max((max - min) * 0.18, max * 0.02, 1)
  const markLineData = budgetLineValue == null ? [] : [{
    yAxis: Number(budgetLineValue),
    name: '预算',
    lineStyle: { color: budgetColor, type: 'dashed', width: 1, opacity: 0.75 },
    label: { color: budgetColor, formatter: '预算' }
  }]
  return {
    color: [priceColor, budgetColor],
    grid: { left: 52, right: 54, top: 28, bottom: 34 },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(255,255,255,0.98)',
      borderColor: '#D0D5DD',
      textStyle: { color: colors.tooltipText, fontSize: 12 },
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : []
        return items
          .filter((entry: any) => entry.value != null)
          .map((entry: any) => `${entry.marker}${entry.seriesName}: ${formatPrice(item, entry.value)}`)
          .join('<br/>')
      }
    },
    legend: {
      show: false,
      top: 4,
      right: 8,
      itemWidth: 18,
      itemHeight: 8,
      textStyle: { color: colors.label, fontSize: 12 }
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#E5E7EB' } },
      axisTick: { show: false },
      axisLabel: {
        color: colors.label,
        fontSize: 11,
        interval: Math.max(Math.floor(dates.length / 7), 0)
      }
    },
    yAxis: {
      type: 'value',
      min: Math.max(min - padding, 0),
      max: max + padding,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#EEF2F6' } },
      axisLabel: {
        color: colors.label,
        fontSize: 11,
        formatter: (value: number) => formatNumber(value)
      }
    },
    series: [
      {
        name: '价格',
        type: 'line',
        data: prices,
        smooth: true,
        connectNulls: true,
        symbol: 'circle',
        symbolSize: 5,
        itemStyle: { color: priceColor },
        lineStyle: { width: 2.6, color: priceColor },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: areaColor },
              { offset: 1, color: 'rgba(255,255,255,0)' }
            ]
          }
        },
        markLine: {
          symbol: 'none',
          silent: true,
          label: {
            show: true,
            position: 'end',
            distance: 4,
            fontSize: 10,
            fontWeight: 600,
            backgroundColor: 'rgba(255,255,255,0.82)',
            padding: [2, 4],
            borderRadius: 4
          },
          data: markLineData
        }
      },
      {
        name: '预算',
        type: 'line',
        data: budgets,
        smooth: true,
        connectNulls: true,
        symbol: 'none',
        itemStyle: { color: budgetColor },
        lineStyle: { width: 1.6, type: 'dashed', color: budgetColor, opacity: 0.72 }
      }
    ]
  }
})

const configuredCount = computed(() => budgets.value.filter(item => toNumber(editMap.value.get(item.productId)) != null).length)
const pendingCount = computed(() => Math.max(budgets.value.length - configuredCount.value, 0))
const changedCount = computed(() => {
  let count = 0
  for (const [productId, value] of editMap.value.entries()) {
    if (value !== (originalMap.value.get(productId) || '')) count++
  }
  return count
})
const hasChanges = computed(() => changedCount.value > 0)

const formatPrice = (item: ProductAnnualBudget | null | undefined, value?: number | null) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '-'
  return `${getCurrencySymbol(item?.currency)}${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

const formatAxisDate = (dateStr: string) => {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return `${date.getMonth() + 1}/${date.getDate()}`
}

const formatNumber = (value: number | null | undefined, digits = 2) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '--'
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

const toNumber = (value: string | undefined) => {
  if (value === undefined || value.trim() === '') return null
  const next = Number(value)
  return Number.isFinite(next) ? next : null
}

const diffPercent = (item: ProductAnnualBudget | null) => {
  if (!item?.latestPrice) return null
  const budget = toNumber(editMap.value.get(item.productId))
  if (budget === null || item.latestPrice === 0) return null
  return ((item.latestPrice - budget) / budget) * 100
}

const formatDiffPercent = (value: number | null) => {
  if (value === null) return '-'
  const prefix = value > 0 ? '+' : ''
  return `${prefix}${value.toFixed(1)}%`
}

const diffClass = (value: number | null) => {
  if (value === null || value === 0) return 'flat'
  return value > 0 ? 'up' : 'down'
}

const getBudgetCategoryStyle = (item: ProductAnnualBudget | null | undefined) =>
  item?.categoryId ? getCategoryCardStyle(item.categoryId) : {}

const setSelected = (item: ProductAnnualBudget) => {
  selectedProductId.value = item.productId
}

const loadSelectedTrend = async () => {
  const item = selectedBudget.value
  if (!item) {
    trendData.value = []
    return
  }
  trendLoading.value = true
  try {
    const startDate = `${selectedYear.value}-01-01`
    const endDate = `${selectedYear.value}-12-31`
    const response = await getPriceTrend(item.productId, 365, endDate, startDate)
    trendData.value = response.data || []
  } catch (error) {
    console.error('Failed to load budget trend:', error)
    trendData.value = []
    showToast('加载预算趋势失败')
  } finally {
    trendLoading.value = false
  }
}

const syncSelectedWithPage = () => {
  const pageItems = paginatedBudgets.value
  if (!pageItems.length) {
    selectedProductId.value = null
    return
  }
  if (!pageItems.some(item => item.productId === selectedProductId.value)) {
    selectedProductId.value = pageItems[0].productId
  }
}

const applyBudgets = (items: ProductAnnualBudget[]) => {
  budgets.value = items
  editMap.value = new Map()
  originalMap.value = new Map()
  for (const item of items) {
    const text = item.budgetPrice === null || item.budgetPrice === undefined ? '' : String(item.budgetPrice)
    editMap.value.set(item.productId, text)
    originalMap.value.set(item.productId, text)
  }
  budgetPage.value = Math.min(budgetPage.value, Math.max(budgetTotalPages.value - 1, 0))
  budgetJumpPage.value = String(budgetPage.value + 1)
  syncSelectedWithPage()
}

const loadCategories = async () => {
  const response = await getCategories('ACTIVE')
  categories.value = response.data || []
  registerCategoryCodes(categories.value.filter(category => category.id && category.code).map(category => ({
    id: category.id,
    code: category.code
  })))
}

const loadBudgets = async (options: { resetPage?: boolean } = {}) => {
  loading.value = true
  try {
    const response = await getProductAnnualBudgets({
      year: selectedYear.value,
      keyword: debouncedKeyword.value || undefined,
      categoryId: selectedCategoryId.value || undefined,
      status: 'ACTIVE'
    })
    if (options.resetPage !== false) budgetPage.value = 0
    applyBudgets(response.data?.items || [])
  } catch (error) {
    console.error('Failed to load annual budgets:', error)
    showToast('加载预算数据失败')
  } finally {
    loading.value = false
  }
}

const ensureCanReload = async () => {
  if (!hasChanges.value) return true
  try {
    await showConfirmDialog({
      title: '存在未保存预算',
      message: '切换年份或筛选会重新加载数据，未保存的修改将被覆盖。',
      confirmButtonText: '继续',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
}

const setYear = async (year: number) => {
  if (!Number.isInteger(year) || year < 1900 || year > 2999) {
    yearInput.value = String(selectedYear.value)
    showToast('年份需在 1900-2999 之间')
    return
  }
  if (year === selectedYear.value) {
    yearInput.value = String(year)
    return
  }
  if (!await ensureCanReload()) {
    yearInput.value = String(selectedYear.value)
    return
  }
  selectedYear.value = year
  yearInput.value = String(year)
  await loadBudgets()
}

const submitYearInput = () => setYear(Number(yearInput.value))
const stepYear = (delta: number) => setYear(selectedYear.value + delta)
const goCurrentYear = () => setYear(currentYear)

const updateBudget = (productId: number, value: string) => {
  editMap.value.set(productId, value)
}

const goToPage = (page: number) => {
  if (page < 0 || page >= budgetTotalPages.value || page === budgetPage.value) return
  budgetPage.value = page
  budgetJumpPage.value = String(page + 1)
  syncSelectedWithPage()
}

const submitJumpPage = () => {
  const page = Number(budgetJumpPage.value)
  if (!Number.isFinite(page)) {
    budgetJumpPage.value = String(budgetPage.value + 1)
    return
  }
  goToPage(Math.min(Math.max(Math.floor(page), 1), budgetTotalPages.value) - 1)
}

const handleCategoryChange = async () => {
  const nextCategoryId = selectedCategoryId.value
  if (!await ensureCanReload()) {
    selectedCategoryId.value = appliedCategoryId.value
    return
  }
  await loadBudgets()
  appliedCategoryId.value = nextCategoryId
}

const handleSave = async () => {
  if (!hasChanges.value) {
    showToast('没有修改，无需保存')
    return
  }

  const items = []
  for (const item of budgets.value) {
    const value = editMap.value.get(item.productId) || ''
    if (value === (originalMap.value.get(item.productId) || '')) continue
    const budgetPrice = toNumber(value)
    if (value.trim() !== '' && budgetPrice === null) {
      showToast(`${item.productName} 的预算价格无效`)
      return
    }
    if (budgetPrice !== null && budgetPrice < 0) {
      showToast(`${item.productName} 的预算价格不能为负数`)
      return
    }
    items.push({
      productId: item.productId,
      version: item.version,
      budgetPrice
    })
  }

  saving.value = true
  try {
    await saveProductAnnualBudgets({
      budgetYear: selectedYear.value,
      items
    })
    await loadBudgets({ resetPage: false })
    showToast(`预算已保存，共 ${items.length} 项`)
  } catch (error: any) {
    console.error('Failed to save annual budgets:', error)
    showToast(error?.response?.data?.message || '保存预算失败')
  } finally {
    saving.value = false
  }
}

watch(keyword, value => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    if (!await ensureCanReload()) {
      keyword.value = debouncedKeyword.value
      return
    }
    debouncedKeyword.value = value.trim()
    await loadBudgets()
  }, 300)
})

watch([selectedProductId, selectedYear], () => {
  loadSelectedTrend()
})

onMounted(async () => {
  await loadAllDicts()
  await Promise.all([loadCategories(), loadBudgets()])
  await loadSelectedTrend()
})
</script>

<template>
  <section class="budget-page" :class="{ mobile: !isPCLayout }">
    <header class="budget-header">
      <div>
        <h1>预算管理</h1>
        <p>按产品与年度维护预算价格，所选年度全年统一执行同一预算</p>
      </div>
      <div class="header-actions">
        <button class="ghost-btn" type="button" @click="goCurrentYear">今年</button>
        <button class="primary-btn" type="button" :disabled="saving || !hasChanges" @click="handleSave">
          {{ saving ? '保存中...' : `保存预算${changedCount ? ` (${changedCount})` : ''}` }}
        </button>
      </div>
    </header>

    <div class="metrics">
      <article class="year">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="17" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/></svg>
        </div>
        <div class="metric-content"><span>当前年度</span><strong>{{ selectedYear }}</strong></div>
      </article>
      <article class="total">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/><path d="M7 8l7 4 7-4"/></svg>
        </div>
        <div class="metric-content"><span>产品总数</span><strong>{{ budgets.length }}</strong></div>
      </article>
      <article class="ok">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>
        </div>
        <div class="metric-content"><span>已设预算</span><strong>{{ configuredCount }}</strong></div>
      </article>
      <article class="warn">
        <div class="metric-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01"/></svg>
        </div>
        <div class="metric-content"><span>待设预算</span><strong>{{ pendingCount }}</strong></div>
      </article>
    </div>

    <section class="toolbar">
      <div class="year-control" aria-label="预算年份">
        <button type="button" @click="stepYear(-1)">‹</button>
        <input v-model="yearInput" type="number" min="1900" max="2999" @change="submitYearInput" @keyup.enter="submitYearInput" />
        <button type="button" @click="stepYear(1)">›</button>
      </div>
      <label class="search-box">
        <span>⌕</span>
        <input v-model="keyword" type="search" placeholder="搜索产品名称、编码" />
      </label>
      <select v-model="selectedCategoryId" @change="handleCategoryChange">
        <option value="">全部分类</option>
        <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
      </select>
      <p>年度预算保存后，{{ selectedYear }} 年 1 月 1 日至 12 月 31 日均使用该预算价格</p>
    </section>

    <main class="workspace">
      <section class="budget-list">
        <div class="list-title">
          <div><h2>产品年度预算</h2><span>{{ changedCount }} 项待保存</span></div>
          <small>{{ selectedYear }} 年</small>
        </div>
        <div class="budget-table">
          <div class="budget-row head">
            <span>产品</span>
            <span>分类 / 单位</span>
            <span>年度预算</span>
            <span>最新价</span>
            <span>较预算</span>
          </div>
          <button
            v-for="item in paginatedBudgets"
            :key="item.productId"
            class="budget-row"
            :class="{ selected: selectedBudget?.productId === item.productId, changed: editMap.get(item.productId) !== originalMap.get(item.productId), 'has-category': item.categoryId }"
            :style="getBudgetCategoryStyle(item)"
            type="button"
            @click="setSelected(item)"
          >
            <span class="product-cell-stack">
              <span class="product-cell">
                <span class="status-dot"></span>
                <span class="product-name">{{ item.productName }}</span>
              </span>
              <span class="product-spec" :title="item.specification || item.productCode || ''">{{ item.specification || item.productCode || '--' }}</span>
            </span>
            <span><strong>{{ item.categoryName || '-' }}</strong><small>{{ getDictValue('unit', item.unit || '') || item.unit || '-' }}</small></span>
            <span class="budget-input-cell" @click.stop>
              <em>{{ getCurrencySymbol(item.currency) }}</em>
              <input
                type="number"
                min="0"
                step="0.01"
                :value="editMap.get(item.productId)"
                placeholder="未设置"
                @input="updateBudget(item.productId, ($event.target as HTMLInputElement).value)"
                @focus="setSelected(item)"
              />
            </span>
            <span class="mono">{{ formatPrice(item, item.latestPrice) }}</span>
            <span class="mono diff" :class="diffClass(diffPercent(item))">{{ formatDiffPercent(diffPercent(item)) }}</span>
          </button>
          <div v-if="loading" class="state">预算数据加载中...</div>
          <EmptyState
            v-else-if="budgets.length === 0"
            type="no-result"
            title="未找到产品"
            description="请调整搜索或分类筛选"
          />
        </div>
        <div class="pagination">
          <span>共 {{ budgets.length }} 条</span>
          <div class="page-controls">
            <button class="page-btn" type="button" :disabled="budgetPage <= 0" @click="goToPage(budgetPage - 1)">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6" />
              </svg>
            </button>
            <template v-for="item in paginationItems" :key="item">
              <button
                v-if="typeof item === 'number'"
                class="page-btn number"
                :class="{ active: budgetPage === item - 1 }"
                type="button"
                @click="goToPage(item - 1)"
              >
                {{ item }}
              </button>
              <span v-else class="page-ellipsis">...</span>
            </template>
            <button class="page-btn" type="button" :disabled="budgetPage + 1 >= budgetTotalPages" @click="goToPage(budgetPage + 1)">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </button>
            <label class="jump-control">
              跳至
              <input v-model="budgetJumpPage" type="number" min="1" :max="budgetTotalPages" @change="submitJumpPage" />
              页
            </label>
          </div>
        </div>
      </section>

      <aside class="preview-panel" :class="{ 'has-category': selectedBudget?.categoryId }" :style="getBudgetCategoryStyle(selectedBudget)">
        <template v-if="selectedBudget">
          <section class="summary-card">
            <div class="summary-head">
              <div><h2>{{ selectedBudget.productName }}</h2><p>{{ selectedBudget.categoryName || '-' }} · {{ selectedBudget.productCode || '-' }}</p></div>
              <em>{{ toNumber(editMap.get(selectedBudget.productId)) == null ? '未设置' : `${selectedYear} 已设置` }}</em>
            </div>
            <div class="summary-grid">
              <div><span>年度预算</span><strong>{{ formatPrice(selectedBudget, toNumber(editMap.get(selectedBudget.productId))) }}</strong></div>
              <div><span>最新价格</span><strong>{{ formatPrice(selectedBudget, selectedBudget.latestPrice) }}</strong></div>
              <div><span>较预算</span><strong :class="diffClass(diffPercent(selectedBudget))">{{ formatDiffPercent(diffPercent(selectedBudget)) }}</strong></div>
            </div>
          </section>

          <section class="chart-card">
            <div class="chart-title">
              <div><h3>{{ selectedYear }} 年价格走势</h3><p>展示当前产品正式价格与年度预算</p></div>
              <div class="legend"><span></span>年度预算</div>
            </div>
            <div class="chart-area trend-chart-area">
              <div v-if="trendLoading" class="state">趋势数据加载中...</div>
              <v-chart
                v-else-if="hasTrendChartData"
                class="budget-trend-chart"
                :option="trendChartOption"
                autoresize
              />
              <div v-else class="state">该年度暂无价格或预算走势</div>
            </div>
            <div v-if="!hasTrendChartData" class="months">
              <span v-for="month in 12" :key="month">{{ month }}月</span>
            </div>
          </section>

          <section class="rule-card">
            <h3>年度预算执行规则</h3>
            <p>每个产品在同一年度仅维护一个预算价格。</p>
            <p>该预算自动应用于该年度 1 月 1 日至 12 月 31 日。</p>
            <p>切换年份时加载对应年度预算，历史年度预算保持不变。</p>
          </section>
        </template>
      </aside>
    </main>
  </section>
</template>

<style scoped>
.budget-page { min-height: 100%; display: flex; flex-direction: column; gap: 16px; color: var(--text-primary); }
button, input, select { font: inherit; }
button { cursor: pointer; }
.budget-header { min-height: 68px; display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.budget-header h1 { margin: 0; font-size: 28px; font-weight: 800; }
.budget-header p, .toolbar p, .chart-title p, .summary-head p { margin: 4px 0 0; color: var(--text-secondary); font-size: 12px; }
.header-actions { display: flex; gap: 8px; }
.ghost-btn, .primary-btn { min-height: 36px; border-radius: 6px; padding: 0 14px; font-weight: 700; }
.ghost-btn { border: 1px solid var(--border-color); background: var(--bg-card); color: var(--text-secondary); }
.primary-btn { border: 1px solid var(--primary-color); background: var(--primary-color); color: var(--bg-card); }
.primary-btn:disabled { opacity: .45; cursor: not-allowed; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--spacing-md); }
.metrics article { min-height: 58px; border: 1px solid var(--border-color); border-radius: var(--radius-md); background: var(--bg-card); padding: 12px 18px; display: flex; align-items: center; gap: 12px; min-width: 0; transition: all var(--transition-fast); }
.metrics article:hover { box-shadow: var(--shadow-md); transform: translateY(-2px); }
.metric-icon { width: 36px; height: 36px; flex: 0 0 36px; border-radius: 10px; display: flex; align-items: center; justify-content: center; background: color-mix(in srgb, var(--primary-color) 10%, transparent); color: var(--primary-color); }
.metric-icon svg { width: 20px; height: 20px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.metric-content { min-width: 0; flex: 1; display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-right: clamp(8px, 1vw, 18px); }
.metric-content span { min-width: 0; overflow: hidden; color: var(--text-primary); font-size: var(--font-size-sm); font-weight: 700; line-height: 1.2; text-overflow: ellipsis; white-space: nowrap; }
.metric-content strong { flex: 0 0 auto; font-family: var(--font-mono), monospace; font-size: clamp(1.3rem, 1.25vw, 1.7rem); font-weight: 800; line-height: 1; color: var(--primary-color); font-variant-numeric: tabular-nums; letter-spacing: 0; }
.metrics .total .metric-icon { background: color-mix(in srgb, var(--chart-color-6) 10%, transparent); color: var(--chart-color-6); }
.metrics .ok .metric-icon { background: color-mix(in srgb, var(--price-rise-color) 10%, transparent); color: var(--price-rise-color); }
.metrics .ok strong { color: var(--success-color); }
.metrics .warn .metric-icon { background: color-mix(in srgb, var(--chart-budget-color) 10%, transparent); color: var(--chart-budget-color); }
.metrics .warn strong { color: var(--chart-budget-color); }
.toolbar { min-height: 50px; display: flex; align-items: center; justify-content: space-between; gap: 10px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); padding: 8px 12px; }
.year-control { display: grid; grid-template-columns: 32px 112px 32px; gap: 6px; }
.year-control button, .year-control input, .toolbar select, .search-box { height: 34px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); }
.year-control button { color: var(--primary-color); font-weight: 800; }
.year-control input { text-align: center; color: var(--primary-color); font-family: var(--font-mono), monospace; font-weight: 800; }
.search-box { width: 260px; display: flex; align-items: center; gap: 8px; padding: 0 10px; }
.search-box span { color: var(--text-muted); }
.search-box input { width: 100%; min-width: 0; border: 0; outline: 0; }
.toolbar select { min-width: 120px; padding: 0 10px; color: var(--text-secondary); }
.toolbar p { margin-left: auto; font-weight: 600; }
.workspace { flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(620px, 1.55fr) minmax(360px, 1fr); gap: 14px; align-items: stretch; }
.budget-list, .preview-panel > section { border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); }
.budget-list { min-height: 0; height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.list-title { min-height: 54px; display: flex; justify-content: space-between; align-items: center; padding: 0 14px; border-bottom: 1px solid var(--border-color); }
.list-title h2, .summary-head h2, .chart-title h3, .rule-card h3 { margin: 0; font-size: 14px; }
.list-title span, .list-title small { color: var(--chart-budget-color); font-family: var(--font-mono), monospace; font-size: 12px; font-weight: 800; }
.budget-table { min-height: 0; flex: 1; overflow-y: auto; }
.budget-row { width: 100%; min-height: 58px; display: grid; grid-template-columns: 1.35fr 1fr 150px 110px 80px; align-items: center; gap: 10px; padding: 8px 14px; border: 0; border-bottom: 1px solid var(--gray-100); background: var(--bg-card); color: var(--text-primary); text-align: left; }
.budget-row.head { position: sticky; top: 0; z-index: 1; min-height: 36px; background: var(--gray-50); color: var(--text-secondary); font-size: 11px; font-weight: 800; }
button.budget-row:hover, .budget-row.selected { background: color-mix(in srgb, var(--primary-color) 7%, var(--bg-card)); box-shadow: inset 3px 0 var(--primary-color); }
button.budget-row.has-category:hover, .budget-row.has-category.selected { background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 42%, var(--bg-card)); box-shadow: inset 3px 0 var(--category-primary, var(--primary-color)); }
.budget-row.changed { box-shadow: inset 3px 0 var(--chart-budget-color); }
.budget-row.has-category.changed { box-shadow: inset 3px 0 var(--chart-budget-color); }
.budget-row > span { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.budget-row strong, .budget-row small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.budget-row strong { font-size: 12px; }
.budget-row small { color: var(--text-muted); font-size: 10px; }
.product-cell-stack { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.product-cell { display: flex; align-items: center; gap: 8px; min-width: 0; }
.status-dot { width: 9px; height: 9px; flex: 0 0 9px; border-radius: 999px; background: var(--category-primary, var(--primary-color)); box-shadow: 0 0 0 3px color-mix(in srgb, var(--category-primary, var(--primary-color)) 12%, transparent); }
.product-name, .product-spec { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-name { flex: 0 1 auto; max-width: 72%; color: var(--text-primary); font-size: 12px; font-weight: 700; }
.product-spec { padding-left: 17px; color: var(--text-muted); font-size: var(--font-size-xs); line-height: 1.35; }
.budget-input-cell { height: 34px; flex-direction: row !important; align-items: center; border: 1px solid var(--border-color); border-radius: 6px; background: var(--gray-50); overflow: hidden; }
.budget-input-cell:focus-within { border-color: var(--category-primary, var(--primary-color)); background: var(--bg-card); }
.budget-input-cell em { padding-left: 9px; color: var(--text-secondary); font-style: normal; font-weight: 700; }
.budget-input-cell input { width: 100%; min-width: 0; height: 100%; border: 0; outline: 0; background: transparent; color: var(--text-primary); font-family: var(--font-mono), monospace; font-weight: 800; }
.mono { font-family: var(--font-mono), monospace; color: var(--text-secondary); font-size: 12px; }
.diff.up { color: var(--price-rise-color); }
.diff.down { color: var(--price-fall-color); }
.diff.flat { color: var(--price-flat-color); }
.state { min-height: 160px; display: flex; align-items: center; justify-content: center; color: var(--text-muted); }
.pagination, .page-controls { display: flex; align-items: center; }
.pagination { justify-content: space-between; gap: var(--spacing-md); padding: var(--spacing-md) 14px 12px; border-top: 1px solid var(--border-color); color: var(--text-secondary); font-size: var(--font-size-sm); }
.page-controls { gap: 7px; flex-wrap: wrap; }
.page-btn { width: 34px; height: 34px; flex: 0 0 34px; display: inline-flex; align-items: center; justify-content: center; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--bg-card); color: var(--text-secondary); cursor: pointer; transition: border-color var(--transition-fast), color var(--transition-fast), background var(--transition-fast); }
.page-btn:hover:not(:disabled) { border-color: var(--primary-color); color: var(--primary-color); }
.page-btn:disabled { opacity: .45; cursor: not-allowed; }
.page-btn.number { font-weight: 700; }
.page-btn.active { border-color: var(--primary-color); background: var(--primary-color); color: var(--bg-card); }
.page-ellipsis { display: inline-flex; align-items: center; justify-content: center; min-width: 20px; height: 34px; color: var(--text-muted); font-weight: 700; line-height: 1; }
.jump-control { display: inline-flex; align-items: center; gap: 6px; color: var(--text-secondary); white-space: nowrap; }
.jump-control input { width: 58px; height: 34px; padding: 0 8px; border: 1px solid var(--border-color); border-radius: var(--radius); background: var(--bg-card); color: var(--text-primary); text-align: center; }
.preview-panel { min-height: 0; height: 100%; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
.summary-card { padding: 16px; border-color: color-mix(in srgb, var(--category-primary, var(--primary-color)) 26%, var(--border-color)) !important; background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 46%, var(--bg-card)) !important; color: var(--text-primary); }
.summary-head { display: flex; justify-content: space-between; gap: 12px; }
.summary-head p { color: var(--text-secondary); }
.summary-head em { align-self: flex-start; border-radius: 999px; background: color-mix(in srgb, var(--category-primary, var(--primary-color)) 12%, var(--bg-card)); color: var(--category-primary, var(--primary-color)); padding: 4px 8px; font-size: 11px; font-style: normal; font-weight: 800; }
.summary-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; margin-top: 18px; }
.summary-grid div { display: flex; flex-direction: column; gap: 4px; }
.summary-grid span { color: var(--text-secondary); font-size: 11px; }
.summary-grid strong { font-family: var(--font-mono), monospace; font-size: 15px; }
.chart-card, .rule-card { padding: 14px; }
.chart-title { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.legend { display: flex; align-items: center; gap: 5px; color: var(--text-secondary); font-size: 11px; }
.legend span { width: 8px; height: 8px; border-radius: 50%; background: var(--chart-budget-color); }
.chart-area { position: relative; height: 250px; margin-top: 12px; border: 1px solid var(--border-color); background: var(--bg-card); overflow: hidden; }
.trend-chart-area { padding: 8px; }
.budget-trend-chart { width: 100%; height: 100%; min-height: 232px; }
.months { display: grid; grid-template-columns: repeat(12, 1fr); gap: 2px; margin-top: 8px; color: var(--text-secondary); font-family: var(--font-mono), monospace; font-size: 10px; text-align: center; }
.rule-card { display: flex; flex-direction: column; gap: 8px; }
.rule-card p { margin: 0; color: var(--text-secondary); font-size: 12px; line-height: 1.5; }
.rule-card p::before { content: ''; display: inline-block; width: 6px; height: 6px; margin-right: 8px; border-radius: 50%; background: var(--primary-color); vertical-align: 1px; }

@media (max-width: 1120px) {
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .workspace { grid-template-columns: 1fr; }
  .toolbar { align-items: stretch; flex-wrap: wrap; }
  .toolbar p { width: 100%; margin-left: 0; }
  .search-box { flex: 1; min-width: 220px; }
  .pagination { align-items: stretch; flex-direction: column; }
  .page-controls { justify-content: flex-start; }
}

.mobile { padding: 12px; }
.mobile .budget-header, .mobile .toolbar { align-items: stretch; flex-direction: column; }
.mobile .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.mobile .budget-row { grid-template-columns: 1.2fr 120px; }
.mobile .budget-row > span:nth-child(2), .mobile .budget-row > span:nth-child(4), .mobile .budget-row > span:nth-child(5) { display: none; }
.mobile .preview-panel { display: none; }
</style>
