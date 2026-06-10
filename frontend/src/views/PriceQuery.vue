<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, MarkLineComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { showToast } from 'vant'
import { useRoute, useRouter } from 'vue-router'
import { getCategories } from '@/api/categories'
import { getPriceTrend, type PriceTrendPoint } from '@/api/products'
import { exportPriceQueryRows, getPriceQueryRows, type PriceQueryParams } from '@/api/priceQuery'
import type { PageResponse, PriceQueryRow, ProductCategory } from '@/types'
import { useSafeChartAutoresize } from '@/composables/useSafeChartAutoresize'
import { getCurrencySymbol, getDictOptions, getDictValue, getOriginName, loadAllDicts } from '@/composables/useDict'
import { Permission, usePermission } from '@/composables/usePermission'
import { useTheme } from '@/composables/useTheme'
import { getCategoryCardStyle, getCategoryVisual, registerCategoryCodes } from '@/composables/useCategoryVisual'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, MarkLineComponent, CanvasRenderer])

const { chartAutoresize } = useSafeChartAutoresize()
const { hasPermission } = usePermission()
const { themeConfig } = useTheme()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const trendLoading = ref(false)
const exporting = ref(false)
const rows = ref<PriceQueryRow[]>([])
const categories = ref<ProductCategory[]>([])
const selectedRow = ref<PriceQueryRow | null>(null)
const trendData = ref<PriceTrendPoint[]>([])
const selectedDate = ref('')
const keyword = ref('')
const debouncedKeyword = ref('')
const selectedCategoryId = ref<number | ''>('')
const tablePage = ref(0)
const tableSize = ref(5)
const tableSizeMode = ref<'auto' | string>('auto')
const adaptiveTableSize = ref(5)
const tableTotalElements = ref(0)
const tableTotalPages = ref(0)
const jumpPage = ref('1')
const activeTrendDays = ref(30)
const trendRequestSeq = ref(0)
const dateInputRef = ref<HTMLInputElement | null>(null)
const listPanelRef = ref<HTMLElement | null>(null)
const tableShellRef = ref<HTMLElement | null>(null)
let isOpeningDatePicker = false
let resizeTimer: ReturnType<typeof setTimeout> | null = null
let listResizeObserver: ResizeObserver | null = null

const pageSizes = [5, 10, 20, 50, 100]
const AUTO_PAGE_SIZE_MIN = 5
const AUTO_PAGE_SIZE_MAX = 12
const AUTO_PAGE_SIZE_HEIGHT_OFFSET = 430
const AUTO_PAGE_SIZE_ROW_HEIGHT = 64
const AUTO_PAGE_SIZE_HEADER_HEIGHT = 45

const canExport = computed(() => hasPermission(Permission.PRICE_EXPORT))
const paginationItems = computed<Array<number | string>>(() => {
  const total = tableTotalPages.value
  const current = tablePage.value + 1
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
  const normalizedPages = [...new Set(pages)]
    .filter(page => page >= 1 && page <= total)
    .sort((a, b) => a - b)

  normalizedPages.forEach((page, index) => {
    const previous = normalizedPages[index - 1]
    if (previous && page - previous > 1) {
      items.push(`ellipsis-${previous}-${page}`)
    }
    items.push(page)
  })

  return items
})
const chartTheme = computed(() => ({
  primary: themeConfig.value.chartPrimaryColor || 'var(--chart-primary-color)',
  budget: themeConfig.value.chartBudgetColor || 'var(--chart-budget-color)',
  label: 'var(--text-secondary)',
  tooltipText: 'var(--text-primary)',
  border: 'var(--border-color)',
  splitLine: 'var(--gray-100)'
}))

const trendRanges = computed(() =>
  getDictOptions('chart_range')
    .map(option => ({
      key: option.value,
      label: option.label,
      days: Number(option.extra || option.value.replace(/\D/g, ''))
    }))
    .filter(option => Number.isFinite(option.days) && option.days > 0)
)

const formatLocalDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const calculateAdaptiveTableSize = () => {
  if (typeof window === 'undefined') return AUTO_PAGE_SIZE_MIN
  const shell = tableShellRef.value
  const tableHead = shell?.querySelector('thead') as HTMLElement | null
  const firstDataRow = shell?.querySelector('tbody tr.data-row') as HTMLElement | null
  const headerHeight = tableHead?.getBoundingClientRect().height || AUTO_PAGE_SIZE_HEADER_HEIGHT
  const rowHeight = firstDataRow?.getBoundingClientRect().height || AUTO_PAGE_SIZE_ROW_HEIGHT
  const shellHeight = shell?.clientHeight || 0
  const measured = shellHeight > headerHeight && rowHeight > 0
    ? Math.floor((shellHeight - headerHeight) / rowHeight)
    : Math.floor((window.innerHeight - AUTO_PAGE_SIZE_HEIGHT_OFFSET) / AUTO_PAGE_SIZE_ROW_HEIGHT)
  return Math.min(Math.max(measured, AUTO_PAGE_SIZE_MIN), AUTO_PAGE_SIZE_MAX)
}

const parseLocalDate = (dateStr: string) => {
  const [year, month, day] = dateStr.split('-').map(Number)
  return new Date(year, month - 1, day)
}

const getDefaultQueryDate = () => {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return formatLocalDate(date)
}

const getRouteQueryDate = () => {
  const value = route.query.date
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : ''
}

selectedDate.value = getRouteQueryDate() || getDefaultQueryDate()

const formatShortDate = (dateStr?: string) => {
  if (!dateStr) return '--'
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const formatAxisDate = (dateStr: string) => {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  return `${date.getMonth() + 1}/${date.getDate()}`
}

const getRelativeDateLabel = (daysBefore: number) => {
  const date = parseLocalDate(selectedDate.value)
  date.setDate(date.getDate() - daysBefore)
  return formatShortDate(formatLocalDate(date))
}

const formatNumber = (value: number | null | undefined, digits = 2) => {
  if (value === null || value === undefined || Number.isNaN(Number(value))) return '--'
  return Number(value).toLocaleString('zh-CN', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  })
}

const getRowCurrencySymbol = (row?: PriceQueryRow | null) => getCurrencySymbol(row?.currency)

const formatPrice = (row: PriceQueryRow | null | undefined, value: number | null | undefined) => {
  if (value === null || value === undefined) return '--'
  return `${getRowCurrencySymbol(row)}${formatNumber(value)}`
}

const formatPercent = (value: number | null | undefined) => {
  if (value === null || value === undefined) return '--'
  const prefix = value > 0 ? '+' : ''
  return `${prefix}${Number(value).toFixed(2)}%`
}

const formatChange = (row: PriceQueryRow, value: number | null | undefined) => {
  if (value === null || value === undefined) return '--'
  const prefix = value > 0 ? '+' : value < 0 ? '-' : ''
  return `${prefix}${getRowCurrencySymbol(row)}${formatNumber(Math.abs(value))}`
}

const getChangeClass = (value: number | null | undefined) => {
  if (value === null || value === undefined || Number(value) === 0) return 'flat'
  return Number(value) > 0 ? 'up' : 'down'
}

const getUnitLabel = (unit?: string | null) => unit ? getDictValue('unit', unit) : '--'

const parseOriginIds = (originIds?: string) => {
  if (!originIds) return []
  try {
    const parsed = JSON.parse(originIds)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

const getRowOriginLabel = (row: PriceQueryRow) => {
  const names = parseOriginIds(row.originIds)
    .map(key => getOriginName(key))
    .filter(Boolean)
    .join(' / ')
  return names || '--'
}

const displayPrice = (row: PriceQueryRow | null | undefined, value: number | null | undefined) =>
  value === null || value === undefined ? '--' : formatPrice(row, value)

const getRowCategoryStyle = (row: PriceQueryRow | null | undefined) =>
  row?.categoryId ? getCategoryCardStyle(row.categoryId) : {}

const queryParams = computed<PriceQueryParams>(() => ({
  date: selectedDate.value,
  keyword: debouncedKeyword.value || undefined,
  categoryId: selectedCategoryId.value || undefined,
  status: 'ACTIVE',
  page: tablePage.value,
  size: tableSize.value,
  sortBy: 'sortOrder',
  sortDirection: 'asc'
}))

const loadCategories = async () => {
  const response = await getCategories('ACTIVE')
  categories.value = (response.data || []).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  registerCategoryCodes(categories.value.map(category => ({ id: category.id, code: category.code })))
}

const selectDefaultRow = () => {
  if (rows.value.length === 0) {
    selectedRow.value = null
    return
  }
  const currentSelectedId = selectedRow.value?.productId
  const stillExists = rows.value.find(row => row.productId === currentSelectedId)
  if (stillExists) {
    selectedRow.value = stillExists
    return
  }
  selectedRow.value = rows.value.find(row => row.hasPrice) || rows.value[0]
}

const loadRows = async (syncAdaptive = true) => {
  loading.value = true
  try {
    const response = await getPriceQueryRows(queryParams.value)
    const pageData = response.data as PageResponse<PriceQueryRow>
    rows.value = pageData.content || []
    tableTotalElements.value = pageData.totalElements || 0
    tableTotalPages.value = pageData.totalPages || 0
    jumpPage.value = String((pageData.number ?? tablePage.value) + 1)
    selectDefaultRow()
  } catch (error) {
    console.error('Failed to load price query rows:', error)
    rows.value = []
    selectedRow.value = null
    showToast('加载价格查询数据失败')
  } finally {
    loading.value = false
  }
  if (syncAdaptive && tableSizeMode.value === 'auto') {
    await nextTick()
    applyAdaptiveTableSize(true)
  }
}

const applyAdaptiveTableSize = (reload = false) => {
  const nextSize = calculateAdaptiveTableSize()
  adaptiveTableSize.value = nextSize
  if (tableSizeMode.value !== 'auto' || tableSize.value === nextSize) return
  tableSize.value = nextSize
  tablePage.value = 0
  if (reload) loadRows(false)
}

const handleWindowResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    applyAdaptiveTableSize(true)
  }, 180)
}

const setupAdaptiveTableObserver = () => {
  if (typeof ResizeObserver === 'undefined') return
  listResizeObserver = new ResizeObserver(() => {
    handleWindowResize()
  })
  if (listPanelRef.value) listResizeObserver.observe(listPanelRef.value)
  if (tableShellRef.value) listResizeObserver.observe(tableShellRef.value)
}

const loadTrend = async () => {
  const row = selectedRow.value
  if (!row) {
    trendData.value = []
    return
  }
  const seq = ++trendRequestSeq.value
  trendLoading.value = true
  try {
    const response = await getPriceTrend(row.productId, activeTrendDays.value)
    if (seq !== trendRequestSeq.value) return
    trendData.value = response.data || []
  } catch (error) {
    if (seq !== trendRequestSeq.value) return
    console.error('Failed to load price trend:', error)
    trendData.value = []
    showToast('加载趋势数据失败')
  } finally {
    if (seq === trendRequestSeq.value) {
      trendLoading.value = false
    }
  }
}

const changeDateBy = async (offset: number) => {
  const date = parseLocalDate(selectedDate.value)
  date.setDate(date.getDate() + offset)
  selectedDate.value = formatLocalDate(date)
}

const onDateInputChange = (event: Event) => {
  const nextDate = (event.target as HTMLInputElement).value
  if (!nextDate || nextDate === selectedDate.value) return
  selectedDate.value = nextDate
}

const openDatePicker = () => {
  if (isOpeningDatePicker) return
  const input = dateInputRef.value
  if (!input) return

  isOpeningDatePicker = true
  input.focus()
  const pickerInput = input as HTMLInputElement & { showPicker?: () => void }
  if (typeof pickerInput.showPicker === 'function') {
    try {
      pickerInput.showPicker()
    } catch {
      input.click()
    } finally {
      window.setTimeout(() => {
        isOpeningDatePicker = false
      }, 0)
    }
    return
  }
  input.click()
  window.setTimeout(() => {
    isOpeningDatePicker = false
  }, 0)
}

const onCategoryChange = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  selectedCategoryId.value = value ? Number(value) : ''
  tablePage.value = 0
  loadRows()
}

const onTableSizeChange = (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  tableSizeMode.value = value
  tableSize.value = value === 'auto' ? adaptiveTableSize.value : Number(value)
  tablePage.value = 0
  loadRows()
}

const goToPage = (page: number) => {
  if (page < 0 || page >= tableTotalPages.value || page === tablePage.value) return
  tablePage.value = page
  loadRows()
}

const submitJumpPage = () => {
  const page = Number(jumpPage.value)
  if (!Number.isFinite(page)) {
    jumpPage.value = String(tablePage.value + 1)
    return
  }
  goToPage(Math.min(Math.max(Math.floor(page), 1), Math.max(tableTotalPages.value, 1)) - 1)
}

const selectRow = (row: PriceQueryRow) => {
  selectedRow.value = row
}

const viewSelectedProductDetail = () => {
  if (!selectedRow.value) return
  router.push({ path: `/product-detail/${selectedRow.value.productId}`, query: { date: selectedDate.value } })
}

const handleTrendRangeChange = (days: number) => {
  if (activeTrendDays.value === days) return
  activeTrendDays.value = days
  loadTrend()
}

const resolveExportErrorMessage = async (error: any) => {
  const data = error?.response?.data
  if (data instanceof Blob) {
    try {
      const text = await data.text()
      const parsed = JSON.parse(text)
      return parsed?.message || '导出失败'
    } catch {
      return '导出失败'
    }
  }
  return data?.message || error?.message || '导出失败'
}

const handleExport = async () => {
  if (!canExport.value) {
    showToast('您没有导出权限')
    return
  }
  if (exporting.value) return
  exporting.value = true
  try {
    await exportPriceQueryRows(queryParams.value)
    showToast('导出成功')
  } catch (error: any) {
    console.error('Failed to export price query:', error)
    showToast(await resolveExportErrorMessage(error))
  } finally {
    exporting.value = false
  }
}

const validTrendPoints = computed(() =>
  trendData.value.filter((point): point is PriceTrendPoint & { currentPrice: number } => point.currentPrice != null)
)

const trendStats = computed(() => {
  const prices = validTrendPoints.value.map(point => Number(point.currentPrice))
  if (prices.length === 0) {
    return { lowest: null, highest: null, average: null, latest: null, latestDate: '' }
  }
  const total = prices.reduce((sum, price) => sum + price, 0)
  const latestPoint = validTrendPoints.value[validTrendPoints.value.length - 1]
  return {
    lowest: Math.min(...prices),
    highest: Math.max(...prices),
    average: total / prices.length,
    latest: Number(latestPoint.currentPrice),
    latestDate: latestPoint.date
  }
})

const findTrendPriceBefore = (daysBefore: number) => {
  if (!selectedDate.value || validTrendPoints.value.length === 0) return null
  const targetDate = parseLocalDate(selectedDate.value)
  targetDate.setDate(targetDate.getDate() - daysBefore)
  const target = formatLocalDate(targetDate)
  const exact = validTrendPoints.value.find(point => point.date === target)
  if (exact) return Number(exact.currentPrice)
  const before = [...validTrendPoints.value].reverse().find(point => point.date <= target)
  return before ? Number(before.currentPrice) : null
}

const changeExplanations = computed(() => {
  const row = selectedRow.value
  if (!row) return []
  const current = row.currentPrice
  const build = (label: string, comparePrice: number | null, dateLabel: string) => {
    if (current == null || comparePrice == null) {
      return { label, value: null, percent: null, className: 'flat', dateLabel }
    }
    const diff = current - comparePrice
    const percent = comparePrice === 0 ? null : (diff / comparePrice) * 100
    return { label, value: diff, percent, className: getChangeClass(diff), dateLabel }
  }
  return [
    build('较昨日', row.yesterdayPrice ?? null, getRelativeDateLabel(1)),
    build('较上周', findTrendPriceBefore(7), getRelativeDateLabel(7)),
    build('较上月', findTrendPriceBefore(30), getRelativeDateLabel(30))
  ]
})

const chartOption = computed(() => {
  const row = selectedRow.value
  const points = validTrendPoints.value
  if (!row || points.length === 0) return {}
  const colors = chartTheme.value
  const categoryVisual = getCategoryVisual(row.categoryId)
  const lineColor = categoryVisual.chartLineColor || categoryVisual.primaryColor || colors.primary
  const averageLineColor = lineColor
  const budgetLineColor = themeConfig.value.chartBudgetColor || 'var(--chart-budget-color)'
  const areaColor = categoryVisual.chartAreaColor || categoryVisual.glowColor || `${categoryVisual.primaryColor || '#0D6E6E'}24`

  const dates = points.map(point => formatAxisDate(point.date))
  const prices = points.map(point => Number(point.currentPrice))
  const budgets = points.map(point => point.budgetPrice == null ? row.budgetPrice ?? null : Number(point.budgetPrice))
  const averageLineValue = trendStats.value.average
  const budgetLineValue = row.budgetPrice ?? null
  const referenceLines = [
    averageLineValue == null ? null : Number(averageLineValue),
    budgetLineValue == null ? null : Number(budgetLineValue)
  ].filter((value): value is number => value != null)
  const comparableValues = [...prices, ...budgets.filter((value): value is number => value != null), ...referenceLines]
  const min = Math.min(...comparableValues)
  const max = Math.max(...comparableValues)
  const padding = Math.max((max - min) * 0.18, max * 0.02, 1)
  const markLineData = [
    averageLineValue == null ? null : {
      yAxis: Number(averageLineValue),
      name: '均价',
      lineStyle: { color: averageLineColor, type: 'dashed', width: 1, opacity: 0.65 },
      label: { color: averageLineColor, formatter: '均价' }
    },
    budgetLineValue == null ? null : {
      yAxis: Number(budgetLineValue),
      name: '预算',
      lineStyle: { color: budgetLineColor, type: 'dashed', width: 1, opacity: 0.75 },
      label: { color: budgetLineColor, formatter: '预算' }
    }
  ].filter(Boolean)

  return {
    grid: { left: 52, right: 54, top: 28, bottom: 34 },
    legend: {
      show: false,
      top: 4,
      right: 8,
      itemWidth: 18,
      itemHeight: 8,
      textStyle: { color: colors.label, fontSize: 12 }
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(255,255,255,0.98)',
      borderColor: '#D0D5DD',
      textStyle: { color: colors.tooltipText, fontSize: 12 },
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : []
        return items.map((item: any) =>
          `${item.marker}${item.seriesName}: ${formatPrice(row, item.value)}`
        ).join('<br/>')
      }
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
        name: '当日售价',
        type: 'line',
        data: prices,
        smooth: true,
        connectNulls: true,
        symbol: 'circle',
        symbolSize: 5,
        itemStyle: { color: lineColor },
        lineStyle: { width: 2.6, color: lineColor },
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
        name: '预算价格',
        type: 'line',
        data: budgets,
        smooth: true,
        connectNulls: true,
        symbol: 'none',
        itemStyle: { color: colors.budget },
        lineStyle: { width: 1.6, type: 'dashed', color: colors.budget, opacity: 0.72 }
      }
    ]
  }
})

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, value => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    debouncedKeyword.value = value.trim()
    tablePage.value = 0
    loadRows()
  }, 300)
})

watch(selectedDate, () => {
  tablePage.value = 0
  loadRows()
})

watch(() => route.query.date, () => {
  const nextDate = getRouteQueryDate()
  if (nextDate && nextDate !== selectedDate.value) {
    selectedDate.value = nextDate
  }
})

watch(selectedRow, () => {
  loadTrend()
})

onMounted(async () => {
  await nextTick()
  setupAdaptiveTableObserver()
  applyAdaptiveTableSize(false)
  window.addEventListener('resize', handleWindowResize)
  await loadAllDicts()
  await loadCategories()
  await loadRows()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleWindowResize)
  if (resizeTimer) {
    clearTimeout(resizeTimer)
    resizeTimer = null
  }
  listResizeObserver?.disconnect()
  listResizeObserver = null
})
</script>

<template>
  <div class="price-query-page">
    <section class="query-header">
      <div class="header-left">
        <h1 class="page-title">价格查询</h1>
      </div>
      <div class="header-actions">
        <div class="date-control-group" @click="openDatePicker">
          <button class="date-nav-btn" type="button" title="前一天" @click.stop="changeDateBy(-1)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </button>
          <input ref="dateInputRef" type="date" class="date-input" :value="selectedDate" @click="openDatePicker" @change="onDateInputChange" />
          <button class="date-nav-btn" type="button" title="后一天" @click.stop="changeDateBy(1)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6" />
            </svg>
          </button>
        </div>
      </div>
    </section>

    <main class="query-layout">
      <section ref="listPanelRef" class="list-panel">
        <div class="panel-toolbar">
          <div class="toolbar-controls">
            <div class="toolbar-filter-group">
              <label class="search-box">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
                <input v-model="keyword" type="search" placeholder="搜索产品名称/规格" />
              </label>
              <select class="table-select category-select" :value="selectedCategoryId" @change="onCategoryChange">
                <option value="">全部分类</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">
                  {{ category.name }}
                </option>
              </select>
              <select class="table-select size-select" :value="tableSizeMode" @change="onTableSizeChange">
                <option value="auto">自适应（{{ adaptiveTableSize }}条/页）</option>
                <option v-for="size in pageSizes" :key="size" :value="size">{{ size }}条/页</option>
              </select>
            </div>
            <button class="export-btn" type="button" :disabled="exporting || !canExport" @click="handleExport">
              <svg v-if="!exporting" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
              <span v-else class="btn-spinner"></span>
              {{ exporting ? '导出中' : '导出数据' }}
            </button>
          </div>
        </div>

        <div ref="tableShellRef" class="table-shell">
          <table class="query-table">
            <colgroup>
              <col class="col-name" />
              <col class="col-price" />
              <col class="col-price" />
              <col class="col-change" />
            </colgroup>
            <thead>
              <tr>
                <th>产品名称</th>
                <th>当日售价</th>
                <th>昨日售价</th>
                <th>较昨日</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="4" class="state-cell">正在加载价格数据...</td>
              </tr>
              <tr v-else-if="rows.length === 0">
                <td colspan="4" class="state-cell">暂无符合条件的价格数据</td>
              </tr>
              <template v-else>
                <tr
                  v-for="row in rows"
                  :key="row.productId"
                  class="data-row"
                  :class="{ selected: selectedRow?.productId === row.productId, missing: !row.hasPrice, 'has-category': row.categoryId }"
                  :style="getRowCategoryStyle(row)"
                  @click="selectRow(row)"
                >
                  <td>
                    <div class="product-cell-stack">
                      <div class="product-cell">
                        <span class="status-dot"></span>
                        <span class="product-name">{{ row.productName }}</span>
                        <span
                          v-if="getRowOriginLabel(row) !== '--'"
                          class="table-origin-chip"
                          :title="getRowOriginLabel(row)"
                        >
                          {{ getRowOriginLabel(row) }}
                        </span>
                      </div>
                      <span class="product-spec" :title="row.specification || ''">{{ row.specification || '--' }}</span>
                    </div>
                  </td>
                  <td class="price-cell current">{{ displayPrice(row, row.currentPrice) }}</td>
                  <td class="price-cell">{{ displayPrice(row, row.yesterdayPrice) }}</td>
                  <td>
                    <span class="change-pill" :class="getChangeClass(row.changeAmount)">
                      <span class="change-value">{{ formatChange(row, row.changeAmount) }}</span>
                    </span>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>

        <div class="pagination">
          <span>共 {{ tableTotalElements }} 条</span>
          <div class="page-controls">
            <button class="page-btn" type="button" :disabled="tablePage <= 0" @click="goToPage(tablePage - 1)">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6" />
              </svg>
            </button>
            <template v-for="item in paginationItems" :key="item">
              <button
                v-if="typeof item === 'number'"
                class="page-btn number"
                :class="{ active: tablePage === item - 1 }"
                type="button"
                @click="goToPage(item - 1)"
              >
                {{ item }}
              </button>
              <span v-else class="page-ellipsis">...</span>
            </template>
            <button class="page-btn" type="button" :disabled="tablePage + 1 >= tableTotalPages" @click="goToPage(tablePage + 1)">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </button>
            <label class="jump-control">
              跳至
              <input v-model="jumpPage" type="number" min="1" :max="Math.max(tableTotalPages, 1)" @change="submitJumpPage" />
              页
            </label>
          </div>
        </div>
      </section>

      <aside class="trend-panel" :class="{ 'has-category': selectedRow?.categoryId }" :style="getRowCategoryStyle(selectedRow)">
        <div class="trend-top">
          <div v-if="selectedRow" class="trend-title-area">
            <div class="selected-name-row trend-title-row">
              <strong>{{ selectedRow.productName }}</strong>
              <span v-if="getRowOriginLabel(selectedRow) !== '--'" class="selected-origin">{{ getRowOriginLabel(selectedRow) }}</span>
              <span v-if="selectedRow.specification">{{ selectedRow.specification }}</span>
            </div>
          </div>
          <div class="selected-name-row trend-title-row muted" v-else>
            <strong>暂无选中产品</strong>
          </div>
          <div class="range-tabs" aria-label="走势时间范围">
            <button
              v-for="range in trendRanges"
              :key="range.key"
              type="button"
              class="range-tab"
              :class="{ active: activeTrendDays === range.days }"
              @click="handleTrendRangeChange(range.days)"
            >
              {{ range.label }}
            </button>
          </div>
        </div>

        <template v-if="selectedRow">
          <div class="selected-summary">
            <div class="selected-price">
              {{ displayPrice(selectedRow, selectedRow.currentPrice ?? selectedRow.latestPrice) }}
              <small v-if="selectedRow.unit">/ {{ getUnitLabel(selectedRow.unit) }}</small>
            </div>
            <button class="detail-link-btn" type="button" @click="viewSelectedProductDetail">
              查看详情
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </button>
          </div>

          <div class="trend-chart-shell">
            <div v-if="trendLoading" class="chart-state">正在加载趋势...</div>
            <v-chart
              v-else-if="validTrendPoints.length > 0"
              class="trend-chart"
              :option="chartOption"
              :autoresize="chartAutoresize"
            />
            <div v-else class="chart-state">暂无趋势数据</div>
          </div>

          <div class="stat-grid">
            <div class="stat-item stat-low">
              <span>最低价</span>
              <strong>{{ formatPrice(selectedRow, trendStats.lowest) }}</strong>
            </div>
            <div class="stat-item stat-high">
              <span>最高价</span>
              <strong>{{ formatPrice(selectedRow, trendStats.highest) }}</strong>
            </div>
            <div class="stat-item stat-average">
              <span>平均价</span>
              <strong>{{ formatPrice(selectedRow, trendStats.average ?? selectedRow.monthlyAveragePrice) }}</strong>
            </div>
            <div class="stat-item stat-budget">
              <span>预算价</span>
              <strong>{{ formatPrice(selectedRow, selectedRow.budgetPrice) }}</strong>
            </div>
            <div class="stat-item stat-latest">
              <span>最新价</span>
              <strong>{{ formatPrice(selectedRow, selectedRow.latestPrice ?? trendStats.latest) }}</strong>
            </div>
          </div>

          <div class="change-explain">
            <h3>价格变化说明</h3>
            <div v-for="item in changeExplanations" :key="item.label" class="explain-row" :class="item.className">
              <span class="explain-dot" :class="item.className"></span>
              <span class="explain-label">{{ item.label }}</span>
              <strong :class="item.className">
                {{ item.value == null ? '--' : formatChange(selectedRow, item.value) }}
                <template v-if="item.percent != null">（{{ formatPercent(item.percent) }}）</template>
              </strong>
              <small>较 {{ item.dateLabel }} 的价格变化</small>
            </div>
          </div>
        </template>

        <div v-else class="empty-trend">
          <h3>暂无选中产品</h3>
          <p>列表加载后点击任意产品行查看趋势</p>
        </div>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.price-query-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  max-width: 100%;
  min-height: calc(100dvh - 48px);
  min-width: 0;
  color: var(--text-primary);
}

.query-header,
.list-panel,
.trend-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.query-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-height: 48px;
  padding: 4px 0;
  background: transparent;
  border: none;
  box-shadow: none;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  flex: 0 0 auto;
}

.page-title {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  line-height: 1.2;
  letter-spacing: 0;
}

.toolbar-controls,
.search-box,
.date-control-group,
.pagination,
.page-controls,
.trend-top,
.range-tabs,
.selected-name-row,
.explain-row,
.product-cell {
  display: flex;
  align-items: center;
}

.panel-toolbar h2,
.change-explain h3,
.empty-trend h3 {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-heading);
  letter-spacing: 0;
}

.date-nav-btn,
.page-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  transition: border-color var(--transition-fast), color var(--transition-fast), background var(--transition-fast);
}

.date-nav-btn,
.page-btn {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: var(--radius-sm);
}

.date-nav-btn:hover,
.page-btn:hover:not(:disabled) {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.date-input,
.search-box,
.table-select,
.jump-control input {
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  outline: none;
}

.date-input {
  width: 150px;
  padding: 0 var(--spacing-sm);
}

.date-control-group {
  gap: var(--spacing-xs);
  flex: 0 0 auto;
}

.query-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(360px, 0.92fr);
  gap: var(--spacing-md);
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  align-items: stretch;
}

.list-panel,
.trend-panel {
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.list-panel {
  display: flex;
  flex-direction: column;
  padding: var(--spacing-lg);
}

.trend-panel {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  overflow-y: auto;
}

.trend-panel.has-category {
  border-color: color-mix(in srgb, var(--category-primary, var(--primary-color)) 18%, var(--border-color));
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.panel-toolbar h2 {
  font-family: var(--font-body);
  font-size: var(--font-size-lg);
  font-weight: 700;
}

.toolbar-controls {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-sm);
  width: 100%;
  min-width: 0;
}

.toolbar-filter-group {
  display: grid;
  grid-template-columns: minmax(180px, 260px) 132px 112px;
  gap: var(--spacing-sm);
  min-width: 0;
}

.search-box {
  width: 100%;
  min-width: 0;
  gap: 8px;
  padding: 0 var(--spacing-sm);
  background: var(--gray-50);
}

.search-box svg {
  flex: 0 0 auto;
  color: var(--text-muted);
}

.search-box input {
  width: 100%;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--text-primary);
  outline: none;
}

.table-select {
  width: 100%;
  min-width: 0;
  padding: 0 var(--spacing-sm);
}

.category-select {
  min-width: 0;
}

.size-select {
  min-width: 0;
}

.export-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  width: 116px;
  min-width: 116px;
  margin-left: auto;
  flex: 0 0 116px;
  height: 40px;
  padding: 0 var(--spacing-md);
  border-radius: var(--radius);
  background: var(--primary-color);
  color: #FFFFFF;
  font-size: var(--font-size-sm);
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
  transition: background var(--transition-fast), opacity var(--transition-fast);
}

.export-btn svg,
.export-btn .btn-spinner {
  flex: 0 0 auto;
}

.export-btn:hover:not(:disabled) {
  background: var(--primary-light);
}

.export-btn:disabled,
.page-btn:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}

.btn-spinner {
  width: 15px;
  height: 15px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #FFFFFF;
  border-radius: 999px;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.table-shell {
  flex: 1 1 auto;
  width: 100%;
  min-width: 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
}

.query-table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}

.query-table .col-name { width: 37%; }
.query-table .col-price { width: 21%; }
.query-table .col-change { width: 21%; }

.query-table th,
.query-table td {
  padding: 12px 6px;
  border-bottom: 1px solid var(--gray-100);
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.query-table th {
  background: var(--gray-50);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.data-row {
  cursor: pointer;
  transition: background var(--transition-fast);
}

.data-row:hover,
.data-row.selected {
  background: color-mix(in srgb, var(--primary-color) 7%, var(--bg-card));
}

.data-row.has-category:hover,
.data-row.has-category.selected {
  background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 42%, var(--bg-card));
}

.data-row.selected td:first-child {
  box-shadow: inset 3px 0 0 var(--primary-color);
}

.data-row.has-category.selected td:first-child {
  box-shadow: inset 3px 0 0 var(--category-primary, var(--primary-color));
}

.data-row.missing {
  color: var(--text-secondary);
}

.product-cell {
  gap: 8px;
  min-width: 0;
}

.product-cell-stack {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.status-dot {
  width: 9px;
  height: 9px;
  flex: 0 0 9px;
  border-radius: 999px;
  background: var(--category-primary, var(--primary-color));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--category-primary, var(--primary-color)) 12%, transparent);
}

.explain-dot {
  width: 9px;
  height: 9px;
  flex: 0 0 9px;
  border-radius: 999px;
  background: var(--price-flat-color);
}

.explain-dot.up {
  background: var(--price-rise-color);
}

.explain-dot.down {
  background: var(--price-fall-color);
}

.explain-dot.flat {
  background: var(--price-flat-color);
}

.status-dot.missing {
  background: var(--text-muted);
}

.product-name,
.product-spec {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-name {
  flex: 0 1 auto;
  max-width: 55%;
  color: var(--text-primary);
  font-weight: 700;
}

.data-row.has-category .product-name {
  color: var(--category-primary, var(--primary-color));
}

.product-spec {
  padding-left: 17px;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  line-height: 1.35;
}

.price-cell,
.change-value,
.stat-item strong,
.selected-price {
  font-family: var(--font-mono);
}

.price-cell.current {
  color: var(--price-rise-color);
  font-weight: 800;
}

.data-row.has-category .price-cell.current {
  color: var(--category-primary, var(--primary-color));
}

.change-pill {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  max-width: 100%;
  min-height: 28px;
  padding: 4px 9px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-weight: 800;
  line-height: 1.35;
}

.change-pill.up {
  background: color-mix(in srgb, var(--price-rise-color) 10%, transparent);
}

.change-pill.down {
  background: color-mix(in srgb, var(--price-fall-color) 10%, transparent);
}

.change-pill.flat {
  background: var(--gray-100);
}

.change-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.table-origin-chip {
  display: inline-flex;
  align-items: center;
  flex: 0 1 auto;
  max-width: 45%;
  height: 24px;
  padding: 0 9px;
  border: 1px solid color-mix(in srgb, var(--category-primary, var(--primary-color)) 20%, var(--border-color));
  border-radius: 999px;
  background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 16%, var(--bg-card));
  color: var(--category-primary, var(--primary-color));
  font-size: var(--font-size-xs);
  font-weight: 700;
  line-height: 22px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.up {
  color: var(--price-rise-color);
}

.down {
  color: var(--price-fall-color);
}

.flat {
  color: var(--price-flat-color);
}

.state-cell {
  padding: 36px !important;
  color: var(--text-muted);
  text-align: center !important;
}

.pagination {
  justify-content: space-between;
  gap: var(--spacing-md);
  padding-top: var(--spacing-md);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.page-controls {
  gap: 7px;
  flex-wrap: wrap;
}

.page-btn.number {
  font-weight: 700;
}

.page-ellipsis {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 34px;
  color: var(--text-muted);
  font-weight: 700;
  line-height: 1;
}

.page-btn.active {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #FFFFFF;
}

.jump-control {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.jump-control input {
  width: 58px;
  height: 34px;
  padding: 0 8px;
  text-align: center;
}

.trend-top {
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
}

.trend-title-area {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-width: 0;
}

.detail-link-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  flex: 0 0 auto;
  border: 1px solid var(--primary-color);
  border-radius: var(--radius-sm);
  background: var(--primary-color);
  color: #FFFFFF;
  font-size: var(--font-size-xs);
  font-weight: 700;
  white-space: nowrap;
  transition: border-color var(--transition-fast), background var(--transition-fast);
}

.detail-link-btn:hover {
  border-color: var(--primary-light);
  background: var(--primary-light);
}

.range-tabs {
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--gray-50);
  flex: 0 0 auto;
}

.range-tab {
  min-width: 46px;
  height: 32px;
  padding: 0 12px;
  border-radius: calc(var(--radius) - 3px);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  white-space: nowrap;
}

.range-tab:hover {
  color: var(--primary-color);
}

.range-tab.active {
  background: var(--primary-color);
  color: #FFFFFF;
}

.selected-summary {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  min-height: 42px;
}

.selected-name-row {
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.trend-title-row {
  flex: 1 1 auto;
  min-width: 0;
}

.trend-title-row.muted strong {
  color: var(--text-muted);
}

.selected-name-row strong {
  font-size: var(--font-size-base);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trend-panel.has-category .selected-name-row strong {
  color: var(--category-primary, var(--primary-color));
}

.selected-name-row span {
  max-width: 220px;
  height: 24px;
  padding: 0 8px;
  border-radius: 999px;
  background: var(--gray-100);
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  line-height: 24px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-name-row .selected-origin {
  background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 16%, var(--bg-card));
  color: var(--category-primary, var(--primary-color));
  font-weight: 700;
}

.selected-price {
  color: var(--category-primary, var(--primary-color));
  font-size: clamp(2rem, 3vw, 2.6rem);
  font-weight: 900;
  line-height: 1;
}

.selected-price small {
  color: var(--text-muted);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.trend-chart-shell {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  min-height: clamp(220px, 34dvh, 420px);
  height: auto;
}

.trend-chart {
  width: 100%;
  height: 100%;
}

.chart-state,
.empty-trend {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  text-align: center;
}

.chart-state {
  height: 100%;
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--gray-100);
}

.stat-item {
  min-width: 0;
}

.stat-item span {
  display: block;
  margin-bottom: 4px;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.stat-item strong {
  display: block;
  font-size: var(--font-size-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.stat-low strong {
  color: var(--price-fall-color);
}

.stat-high strong {
  color: var(--price-rise-color);
}

.stat-average strong {
  color: var(--chart-primary-color);
}

.stat-budget strong {
  color: var(--chart-budget-color);
}

.stat-latest strong {
  color: var(--chart-color-7);
}

.change-explain {
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--gray-100);
}

.change-explain h3 {
  margin-bottom: var(--spacing-sm);
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 800;
}

.explain-row {
  display: grid;
  grid-template-columns: 10px 58px minmax(110px, auto) minmax(0, 1fr);
  gap: var(--spacing-sm);
  align-items: center;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-left-width: 3px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.explain-row + .explain-row {
  margin-top: 6px;
}

.explain-row.up {
  border-color: color-mix(in srgb, var(--price-rise-color) 24%, var(--border-color));
  border-left-color: var(--price-rise-color);
  background: color-mix(in srgb, var(--price-rise-color) 8%, var(--bg-card));
}

.explain-row.down {
  border-color: color-mix(in srgb, var(--price-fall-color) 24%, var(--border-color));
  border-left-color: var(--price-fall-color);
  background: color-mix(in srgb, var(--price-fall-color) 8%, var(--bg-card));
}

.explain-row.flat {
  border-color: color-mix(in srgb, var(--price-flat-color) 26%, var(--border-color));
  border-left-color: var(--price-flat-color);
  background: color-mix(in srgb, var(--price-flat-color) 10%, var(--bg-card));
}

.explain-row strong {
  font-family: var(--font-mono);
  white-space: nowrap;
}

.explain-row.up .explain-label,
.explain-row.up strong {
  color: var(--price-rise-color);
}

.explain-row.down .explain-label,
.explain-row.down strong {
  color: var(--price-fall-color);
}

.explain-row.flat .explain-label,
.explain-row.flat strong {
  color: var(--price-flat-color);
}

.explain-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.explain-row.up small {
  color: color-mix(in srgb, var(--price-rise-color) 72%, var(--text-muted));
}

.explain-row.down small {
  color: color-mix(in srgb, var(--price-fall-color) 72%, var(--text-muted));
}

.explain-row.flat small {
  color: color-mix(in srgb, var(--price-flat-color) 72%, var(--text-muted));
}

.empty-trend {
  flex: 1;
  min-height: 360px;
  flex-direction: column;
  gap: 6px;
}

.empty-trend h3 {
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 800;
}

.empty-trend p {
  margin: 0;
  color: var(--text-muted);
}

@media (max-width: 1180px) {
  .price-query-page {
    min-height: 0;
  }

  .query-layout {
    grid-template-columns: 1fr;
    flex: 0 1 auto;
  }

  .trend-panel {
    min-height: 0;
    overflow: visible;
  }

  .table-shell {
    flex: 0 1 auto;
    overflow-y: visible;
  }
}

@media (max-width: 768px) {
  .price-query-page {
    gap: var(--spacing-sm);
  }

  .query-header {
    align-items: stretch;
    min-height: 0;
    padding: 0;
  }

  .header-spacer {
    display: none;
  }

  .header-actions {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(104px, 0.34fr);
    width: 100%;
  }

  .date-input {
    width: 100%;
  }

  .date-nav-btn {
    width: 40px;
    height: 40px;
  }

  .list-panel,
  .trend-panel {
    padding: var(--spacing-md);
    border-radius: var(--radius-md);
  }

  .panel-toolbar,
  .trend-top,
  .pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-controls {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(118px, 0.36fr);
    gap: var(--spacing-sm);
    justify-content: stretch;
  }

  .toolbar-filter-group {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(118px, 0.42fr);
    gap: var(--spacing-sm);
    min-width: 0;
  }

  .search-box,
  .table-select,
  .export-btn {
    width: 100%;
    min-width: 0;
  }

  .search-box {
    grid-column: 1 / -1;
  }

  .toolbar-filter-group {
    grid-column: 1 / -1;
  }

  .export-btn {
    width: 100%;
    margin-left: 0;
    grid-column: 2;
    justify-self: end;
  }

  .header-actions .date-control-group {
    display: grid;
    grid-template-columns: 40px minmax(0, 1fr) 40px;
  }

  .header-actions .export-btn {
    width: 100%;
    min-width: 0;
  }

  .page-controls {
    justify-content: flex-start;
  }

  .range-tabs {
    width: 100%;
    overflow-x: hidden;
  }

  .trend-title-row {
    width: 100%;
  }

  .trend-title-area {
    width: 100%;
    flex-wrap: wrap;
  }

  .detail-link-btn {
    margin-left: auto;
  }

  .selected-summary {
    flex-wrap: wrap;
  }

  .range-tab {
    flex: 1 0 auto;
  }

  .trend-chart-shell {
    min-height: clamp(220px, 42dvh, 320px);
    height: auto;
  }

  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .explain-row {
    grid-template-columns: 10px 58px minmax(0, 1fr);
  }

  .explain-row small {
    grid-column: 2 / -1;
  }
}

@media (max-width: 430px) {
  .toolbar-controls {
    grid-template-columns: 1fr;
  }

  .selected-price {
    font-size: 1.8rem;
  }
}
</style>
