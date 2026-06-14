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
import { getPriceTrend, getProductPriceYears, type PriceTrendPoint } from '@/api/products'
import { exportPriceQueryRows, getPriceQueryRows, type PriceQueryParams } from '@/api/priceQuery'
import type { PageResponse, PriceQueryRow, ProductCategory, SysDict } from '@/types'
import { useSafeChartAutoresize } from '@/composables/useSafeChartAutoresize'
import { getActiveDictByCategory, getCurrencySymbol, getDictOptions, getDictValue, getOriginName, loadAllDicts } from '@/composables/useDict'
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
const tableSize = ref(10)
const tableSizeMode = ref<'auto' | string>('auto')
const adaptiveTableSize = ref(10)
const tableTotalElements = ref(0)
const tableTotalPages = ref(0)
const jumpPage = ref('1')
const activeTrendDays = ref(30)
const selectedTrendYear = ref('rolling')
const availableTrendYears = ref<number[]>([])
const trendRequestSeq = ref(0)
const rowRequestSeq = ref(0)
const dateInputRef = ref<HTMLInputElement | null>(null)
const pageRef = ref<HTMLElement | null>(null)
const listPanelRef = ref<HTMLElement | null>(null)
const tableShellRef = ref<HTMLElement | null>(null)
const trendPanelRef = ref<HTMLElement | null>(null)
const trendChartShellRef = ref<HTMLElement | null>(null)
const pageAvailableHeight = ref<number | null>(null)
const trendChartHeight = ref<number | null>(null)
let isOpeningDatePicker = false
let resizeTimer: ReturnType<typeof setTimeout> | null = null
let listResizeObserver: ResizeObserver | null = null

const pageSizes = [5, 10, 20, 50, 100]
const AUTO_PAGE_SIZE_MIN = 5
const AUTO_PAGE_SIZE_MAX = 12
const AUTO_PAGE_SIZE_HEIGHT_OFFSET = 430
const AUTO_PAGE_SIZE_ROW_HEIGHT = 64
const AUTO_PAGE_SIZE_HEADER_HEIGHT = 45
const STACKED_LAYOUT_BREAKPOINT = 1180
const MOBILE_LAYOUT_BREAKPOINT = 768
const STACKED_LAYOUT_PAGE_SIZE = 10
const MOBILE_LAYOUT_PAGE_SIZE = 5
const TREND_CHART_MIN_HEIGHT = 150
const TREND_CHART_MAX_HEIGHT = 360

const displayedTableSize = computed(() => (
  tableSizeMode.value === 'auto' ? 'auto' : String(tableSize.value)
))
const pageLayoutStyle = computed<Record<string, string>>(() => {
  const style: Record<string, string> = {}
  if (pageAvailableHeight.value != null) {
    style['--price-query-page-height'] = `${pageAvailableHeight.value}px`
  }
  if (trendChartHeight.value != null) {
    style['--price-query-chart-height'] = `${trendChartHeight.value}px`
  }
  return style
})
const visiblePageSizes = computed(() => (
  [...new Set([...pageSizes, adaptiveTableSize.value])]
    .filter(size => Number.isFinite(size) && size > 0)
    .sort((a, b) => a - b)
))
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
const trendYearOptions = computed(() => availableTrendYears.value)

const formatLocalDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const calculateAdaptiveTableSize = () => {
  if (typeof window === 'undefined') return AUTO_PAGE_SIZE_MIN
  if (window.innerWidth <= MOBILE_LAYOUT_BREAKPOINT) return MOBILE_LAYOUT_PAGE_SIZE
  if (window.innerWidth <= STACKED_LAYOUT_BREAKPOINT) return STACKED_LAYOUT_PAGE_SIZE

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
  const seq = ++rowRequestSeq.value
  loading.value = true
  try {
    const response = await getPriceQueryRows(queryParams.value)
    if (seq !== rowRequestSeq.value) return
    const pageData = response.data as PageResponse<PriceQueryRow>
    rows.value = pageData.content || []
    tableTotalElements.value = pageData.totalElements || 0
    tableTotalPages.value = pageData.totalPages || 0
    jumpPage.value = String((pageData.number ?? tablePage.value) + 1)
    selectDefaultRow()
  } catch (error) {
    if (seq !== rowRequestSeq.value) return
    console.error('Failed to load price query rows:', error)
    rows.value = []
    selectedRow.value = null
    showToast('加载价格查询数据失败')
  } finally {
    if (seq === rowRequestSeq.value) {
      loading.value = false
    }
  }
  if (seq === rowRequestSeq.value && syncAdaptive && tableSizeMode.value === 'auto') {
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

const updateResponsiveLayout = async () => {
  if (typeof window === 'undefined') return
  if (window.innerWidth <= STACKED_LAYOUT_BREAKPOINT) {
    pageAvailableHeight.value = null
    trendChartHeight.value = null
    return
  }

  const page = pageRef.value
  if (!page) return
  const parent = page.parentElement
  const parentRect = parent?.getBoundingClientRect()
  const parentPaddingBottom = parent ? Number.parseFloat(window.getComputedStyle(parent).paddingBottom) || 0 : 0
  const pageTop = page.getBoundingClientRect().top
  const availableBottom = Math.min(window.innerHeight, parentRect?.bottom || window.innerHeight)
  pageAvailableHeight.value = Math.max(0, Math.floor(availableBottom - pageTop - parentPaddingBottom))

  const preferredChartHeight = Math.min(
    TREND_CHART_MAX_HEIGHT,
    Math.max(TREND_CHART_MIN_HEIGHT, Math.floor(pageAvailableHeight.value * 0.32))
  )
  trendChartHeight.value = preferredChartHeight
  await nextTick()

  const panel = trendPanelRef.value
  const chartShell = trendChartShellRef.value
  if (!panel || !chartShell) return
  const nonChartHeight = panel.scrollHeight - chartShell.offsetHeight
  const availableChartHeight = panel.clientHeight - nonChartHeight
  trendChartHeight.value = Math.max(
    TREND_CHART_MIN_HEIGHT,
    Math.min(preferredChartHeight, Math.floor(availableChartHeight))
  )
}

const handleWindowResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(async () => {
    await updateResponsiveLayout()
    applyAdaptiveTableSize(true)
  }, 180)
}

const setupAdaptiveTableObserver = () => {
  if (typeof ResizeObserver === 'undefined') return
  listResizeObserver = new ResizeObserver(() => {
    handleWindowResize()
  })
  if (listPanelRef.value) listResizeObserver.observe(listPanelRef.value)
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
    const selectedYear = selectedTrendYear.value === 'rolling' ? null : Number(selectedTrendYear.value)
    const endDate = selectedYear
      ? (selectedYear === new Date().getFullYear() ? formatLocalDate(new Date()) : `${selectedYear}-12-31`)
      : undefined
    const startDate = selectedYear && activeTrendDays.value === 365 ? `${selectedYear}-01-01` : undefined
    const response = await getPriceTrend(row.productId, activeTrendDays.value, endDate, startDate)
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

const loadTrendYears = async () => {
  const row = selectedRow.value
  if (!row) {
    availableTrendYears.value = []
    selectedTrendYear.value = 'rolling'
    return
  }
  try {
    const response = await getProductPriceYears(row.productId)
    availableTrendYears.value = response.data ?? []
    if (selectedTrendYear.value !== 'rolling' && !availableTrendYears.value.includes(Number(selectedTrendYear.value))) {
      selectedTrendYear.value = 'rolling'
    }
  } catch (error) {
    console.error('Failed to load price years:', error)
    availableTrendYears.value = []
    selectedTrendYear.value = 'rolling'
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
  const rawValue = (event.target as HTMLSelectElement).value
  if (rawValue === 'auto') {
    tableSizeMode.value = 'auto'
    applyAdaptiveTableSize(true)
    return
  }
  const value = Number(rawValue)
  if (!Number.isFinite(value) || value <= 0) return
  tableSizeMode.value = String(value)
  tableSize.value = value
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

const handleTrendYearChange = () => {
  if (selectedTrendYear.value !== 'rolling') activeTrendDays.value = 365
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
const hasTrendChartData = computed(() =>
  trendData.value.some(point => point.currentPrice != null || point.budgetPrice != null)
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

type PriceMetricValueType = 'price' | 'change' | 'percent' | 'date'

interface PriceMetricItem {
  key: string
  label: string
  description: string
  type: PriceMetricValueType
  value: number | string | null | undefined
}

interface PriceMetricExtra {
  group?: string
  valueType?: PriceMetricValueType
  description?: string
  rule?: string
  note?: string
}

const parsePriceMetricExtra = (dict: SysDict): PriceMetricExtra | null => {
  const extra = dict.extraValue
  if (!extra) return null
  try {
    return JSON.parse(extra) as PriceMetricExtra
  } catch {
    return null
  }
}

const metricAccessors: Record<string, { type: PriceMetricValueType, getValue: (row: PriceQueryRow) => PriceMetricItem['value'] }> = {
  LATEST_PRICE: { type: 'price', getValue: row => row.latestPrice },
  LATEST_PRICE_DATE: { type: 'date', getValue: row => row.latestPriceDate },
  PREVIOUS_EFFECTIVE_PRICE: { type: 'price', getValue: row => row.previousPrice },
  PREVIOUS_PRICE_DATE: { type: 'date', getValue: row => row.previousPriceDate },
  PREVIOUS_CHANGE_AMOUNT: { type: 'change', getValue: row => row.previousChangeAmount },
  PREVIOUS_CHANGE_PERCENT: { type: 'percent', getValue: row => row.previousChangePercent },
  BUDGET_PRICE: { type: 'price', getValue: row => row.budgetPrice },
  BUDGET_CHANGE_AMOUNT: { type: 'change', getValue: row => row.budgetChangeAmount },
  BUDGET_CHANGE_PERCENT: { type: 'percent', getValue: row => row.budgetChangePercent },
  CURRENT_MONTH_AVERAGE_PRICE: { type: 'price', getValue: row => row.monthlyAveragePrice },
  PREVIOUS_MONTH_AVERAGE_PRICE: { type: 'price', getValue: row => row.previousMonthAveragePrice },
  MONTH_OVER_MONTH_PERCENT: { type: 'percent', getValue: row => row.monthOverMonthPercent },
  LAST_YEAR_SAME_PERIOD_AVERAGE_PRICE: { type: 'price', getValue: row => row.lastYearSamePeriodAveragePrice },
  YEAR_OVER_YEAR_PERCENT: { type: 'percent', getValue: row => row.yearOverYearPercent }
}
const metricGroupClasses: Record<string, string> = {
  PRICE_STATUS: 'status',
  SHORT_TERM_BUDGET: 'deviation',
  MONTHLY_TREND: 'monthly'
}

const formatPriceMetricValue = (item: PriceMetricItem) => {
  const row = selectedRow.value
  if (!row || item.value === null || item.value === undefined || item.value === '') return '--'
  if (item.type === 'date') return formatShortDate(String(item.value))
  if (item.type === 'percent') return formatPercent(Number(item.value))
  if (item.type === 'change') return formatChange(row, Number(item.value))
  return formatPrice(row, Number(item.value))
}

const getPriceMetricTone = (item: PriceMetricItem) => {
  if (item.type !== 'change' && item.type !== 'percent') return ''
  return getChangeClass(typeof item.value === 'number' ? item.value : null)
}

const priceMetricGroups = computed(() => {
  const row = selectedRow.value
  if (!row) return []
  const itemsByGroup = new Map<string, PriceMetricItem[]>()
  getActiveDictByCategory('price_metric')
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .forEach(dict => {
      const accessor = metricAccessors[dict.dictKey]
      const extra = parsePriceMetricExtra(dict)
      if (!accessor || !extra?.group || extra.valueType !== accessor.type) return
      const items = itemsByGroup.get(extra.group) || []
      items.push({
        key: dict.dictKey,
        label: dict.dictValue,
        description: [extra.description, extra.rule, extra.note].filter(Boolean).join('；'),
        type: accessor.type,
        value: accessor.getValue(row)
      })
      itemsByGroup.set(extra.group, items)
    })

  return getActiveDictByCategory('price_metric_group')
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .map(group => ({
      key: group.dictKey,
      label: group.dictValue,
      className: metricGroupClasses[group.dictKey] || '',
      items: itemsByGroup.get(group.dictKey) || []
    }))
    .filter(group => group.items.length > 0)
})

const chartOption = computed(() => {
  const row = selectedRow.value
  const points = trendData.value
  if (!row || !hasTrendChartData.value) return {}
  const colors = chartTheme.value
  const categoryVisual = getCategoryVisual(row.categoryId)
  const lineColor = categoryVisual.chartLineColor || categoryVisual.primaryColor || colors.primary
  const averageLineColor = lineColor
  const budgetLineColor = themeConfig.value.chartBudgetColor || 'var(--chart-budget-color)'
  const areaColor = categoryVisual.chartAreaColor || categoryVisual.glowColor || `${categoryVisual.primaryColor || '#0D6E6E'}24`

  const dates = points.map(point => formatAxisDate(point.date))
  const prices = points.map(point => point.currentPrice == null ? null : Number(point.currentPrice))
  const budgets = points.map(point => point.budgetPrice == null ? null : Number(point.budgetPrice))
  const averageLineValue = trendStats.value.average
  const budgetLineValue = row.budgetPrice ?? null
  const referenceLines = [
    averageLineValue == null ? null : Number(averageLineValue),
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
        name: '近期价格',
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

watch(selectedRow, async () => {
  selectedTrendYear.value = 'rolling'
  await loadTrendYears()
  loadTrend()
  await nextTick()
  updateResponsiveLayout()
})

onMounted(async () => {
  await nextTick()
  await updateResponsiveLayout()
  setupAdaptiveTableObserver()
  applyAdaptiveTableSize(false)
  window.addEventListener('resize', handleWindowResize)
  await loadAllDicts()
  await loadCategories()
  await loadRows()
  await nextTick()
  updateResponsiveLayout()
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
  <div ref="pageRef" class="price-query-page" :style="pageLayoutStyle">
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
              <select class="table-select size-select" :value="displayedTableSize" @change="onTableSizeChange">
                <option value="auto">自适应（{{ adaptiveTableSize }}条）</option>
                <option v-for="size in visiblePageSizes" :key="size" :value="size">{{ size }}条/页</option>
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
                <th>近期价格</th>
                <th>预算价格</th>
                <th>较预算</th>
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
                  <td class="price-cell current">{{ displayPrice(row, row.currentPrice ?? row.latestPrice) }}</td>
                  <td class="price-cell">{{ displayPrice(row, row.budgetPrice) }}</td>
                  <td>
                    <span class="change-pill" :class="getChangeClass(row.budgetChangeAmount)">
                      <span class="change-value">{{ formatChange(row, row.budgetChangeAmount) }}</span>
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

      <aside ref="trendPanelRef" class="trend-panel" :class="{ 'has-category': selectedRow?.categoryId }" :style="getRowCategoryStyle(selectedRow)">
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
          <div class="trend-controls">
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
            <label class="trend-year-control">
              <span>查看年份</span>
              <select v-model="selectedTrendYear" :disabled="trendLoading" @change="handleTrendYearChange">
                <option value="rolling">滚动区间</option>
                <option v-for="year in trendYearOptions" :key="year" :value="String(year)">{{ year }} 年</option>
              </select>
            </label>
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

          <div ref="trendChartShellRef" class="trend-chart-shell">
            <div v-if="trendLoading" class="chart-state">正在加载趋势...</div>
            <v-chart
              v-else-if="hasTrendChartData"
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
              <span>近期价</span>
              <strong>{{ formatPrice(selectedRow, selectedRow.latestPrice ?? trendStats.latest) }}</strong>
            </div>
          </div>

          <section class="metric-insights">
            <div class="metric-insights-header">
              <h3>价格指标洞察</h3>
              <span>口径：最新有效价格日，自动跳过无价格记录日期</span>
            </div>
            <div class="metric-group-grid">
              <article
                v-for="group in priceMetricGroups"
                :key="group.key"
                class="metric-group"
                :class="group.className"
              >
                <header>
                  <strong>{{ group.label }}</strong>
                  <span>{{ group.items.length }}项</span>
                </header>
                <div
                  v-for="metric in group.items"
                  :key="metric.key"
                  class="metric-row"
                  :title="metric.description"
                >
                  <span>{{ metric.label }}</span>
                  <strong :class="getPriceMetricTone(metric)">{{ formatPriceMetricValue(metric) }}</strong>
                </div>
              </article>
            </div>
          </section>
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
  height: var(--price-query-page-height, auto);
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
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
.metric-insights h3,
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
  overflow: hidden;
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
  overscroll-behavior: contain;
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
  flex: 0 0 auto;
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

.trend-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  flex: 0 0 auto;
}

.trend-year-control {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 700;
  white-space: nowrap;
}

.trend-year-control select {
  height: 38px;
  padding: 0 28px 0 10px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-primary);
  font: inherit;
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
  flex: 0 0 var(--price-query-chart-height, clamp(180px, 30dvh, 360px));
  min-width: 0;
  min-height: var(--price-query-chart-height, clamp(180px, 30dvh, 360px));
  height: var(--price-query-chart-height, clamp(180px, 30dvh, 360px));
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

.metric-insights {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--gray-100);
}

.metric-insights-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.metric-insights h3 {
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 800;
}

.metric-insights-header span {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  text-align: right;
}

.metric-group-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.metric-group {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--metric-border);
  border-radius: var(--radius-sm);
  background: var(--metric-surface);
}

.metric-group.status {
  --metric-color: var(--category-primary, var(--primary-color));
  --metric-surface: color-mix(in srgb, var(--category-surface, var(--primary-color)) 12%, var(--bg-card));
  --metric-border: color-mix(in srgb, var(--category-primary, var(--primary-color)) 20%, var(--border-color));
}

.metric-group.deviation {
  --metric-color: var(--chart-budget-color);
  --metric-surface: color-mix(in srgb, var(--chart-budget-color) 7%, var(--bg-card));
  --metric-border: color-mix(in srgb, var(--chart-budget-color) 18%, var(--border-color));
}

.metric-group.monthly {
  --metric-color: var(--chart-primary-color);
  --metric-surface: color-mix(in srgb, var(--chart-primary-color) 7%, var(--bg-card));
  --metric-border: color-mix(in srgb, var(--chart-primary-color) 18%, var(--border-color));
}

.metric-group header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 30px;
  padding: 0 8px;
  border-bottom: 1px solid var(--metric-border);
  color: var(--metric-color);
  font-size: var(--font-size-xs);
}

.metric-group header span {
  color: color-mix(in srgb, var(--metric-color) 72%, var(--text-muted));
  font-size: 10px;
}

.metric-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 38px;
  padding: 0 8px;
  border-bottom: 1px solid color-mix(in srgb, var(--metric-border) 58%, transparent);
  color: var(--text-secondary);
  font-size: 11px;
}

.metric-row:last-child {
  border-bottom: none;
}

.metric-row span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-row strong {
  flex: 0 0 auto;
  color: var(--metric-color);
  font-family: var(--font-mono);
  font-size: 11px;
  white-space: nowrap;
}

.metric-row strong.up {
  color: var(--price-rise-color);
}

.metric-row strong.down {
  color: var(--price-fall-color);
}

.metric-row strong.flat {
  color: var(--price-flat-color);
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
    height: auto;
    min-height: 0;
    overflow: visible;
  }

  .query-layout {
    grid-template-columns: 1fr;
    flex: 0 1 auto;
    height: auto;
    overflow: visible;
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

  .trend-controls {
    width: 100%;
    align-items: stretch;
    flex-direction: column;
  }

  .trend-year-control {
    justify-content: space-between;
  }

  .trend-year-control select {
    flex: 1;
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

  .metric-insights-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-insights-header span {
    text-align: left;
  }

  .metric-group-grid {
    grid-template-columns: 1fr;
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
