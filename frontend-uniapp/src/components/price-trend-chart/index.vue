<template>
  <view class="trend-chart">
    <!-- 选点信息置于交互区域上方，避免手指滑动折线图时遮挡内容 -->
    <view v-if="selectedPoint" class="selected-detail">
      <view>
        <text class="selected-date">{{ selectedPoint.date }}</text>
        <text class="selected-hint">触摸趋势线可查看其他日期</text>
      </view>
      <view class="selected-prices">
        <text>售价 {{ selectedPoint.price == null ? '--' : formatPrice(selectedPoint.price) }}</text>
        <text v-if="selectedPoint.budgetPrice != null" class="budget-text">
          预算 {{ formatPrice(selectedPoint.budgetPrice) }}
        </text>
      </view>
    </view>

    <!-- 时间范围选择 -->
    <view class="period-tabs">
      <view
        class="period-tab"
        :class="{ active: selectedPeriod === 30 }"
        @click="selectPeriod(30)"
      >
        <text>30天</text>
      </view>
      <view
        class="period-tab"
        :class="{ active: selectedPeriod === 180 }"
        @click="selectPeriod(180)"
      >
        <text>180天</text>
      </view>
      <view
        class="period-tab"
        :class="{ active: selectedPeriod === 365 }"
        @click="selectPeriod(365)"
      >
        <text>12个月</text>
      </view>
      <picker class="year-picker" mode="selector" :range="yearPickerLabels" :value="selectedYearIndex" @change="handleYearChange">
        <view class="period-tab year-tab" :class="{ active: selectedTrendYear !== 'rolling' }">
          <text>{{ selectedTrendYear === 'rolling' ? '查看年份' : `${selectedTrendYear}年` }}</text>
        </view>
      </picker>
    </view>

    <view class="chart-legend">
      <view class="legend-item">
        <view class="legend-line selling" />
        <text>价格</text>
      </view>
      <view v-if="hasBudgetPrice" class="legend-item">
        <view class="legend-line budget" />
        <text>预算</text>
      </view>
    </view>

    <!-- 图表区域 -->
    <view class="chart-container">
      <canvas
        type="2d"
        :id="canvasId"
        class="chart-canvas"
        :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
        @touchstart="handleChartTouch"
        @touchmove="handleChartTouch"
      />

      <!-- 加载状态 -->
      <view v-if="loading" class="loading-overlay">
        <text>加载中...</text>
      </view>

      <!-- 无数据状态 -->
      <view v-if="!loading && priceData.length === 0" class="empty-overlay">
        <text>暂无价格数据</text>
      </view>
    </view>

    <!-- 统计信息 -->
    <view class="stats-row" v-if="!loading && prices.length > 0">
      <view class="stat-item">
        <text class="stat-label">最高</text>
        <text class="stat-value high">{{ formatPrice(maxPrice) }}</text>
      </view>
      <view class="stat-item">
        <text class="stat-label">最低</text>
        <text class="stat-value low">{{ formatPrice(minPrice) }}</text>
      </view>
      <view class="stat-item">
        <text class="stat-label">平均</text>
        <text class="stat-value">{{ formatPrice(avgPrice) }}</text>
      </view>
      <view class="stat-item">
        <text class="stat-label">最新</text>
        <text class="stat-value current">{{ formatPrice(lastPrice) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, getCurrentInstance } from 'vue'
import { getPriceTrend, getProductPriceYears } from '@/api/products'

const props = defineProps<{
  productId: number
  currencySymbol?: string
  budgetPrice?: number | null
}>()

const instance = getCurrentInstance()
const canvasId = `trend-chart-${props.productId}`
const canvasWidth = 320
const canvasHeight = 180
const yAxisWidth = 50
const xAxisHeight = 20
const padding = 10
const lineColor = '#0D6E6E'
const budgetColor = '#E07B54'

const selectedPeriod = ref(30)
const selectedTrendYear = ref('rolling')
const availableTrendYears = ref<number[]>([])
const loading = ref(false)
const priceData = ref<{ date: string; price: number | null; budgetPrice: number | null }[]>([])
const selectedIndex = ref(-1)
let lastPlotMetrics: { left: number; width: number } | null = null
let lastCanvasLeft = 0

const prices = computed(() => priceData.value
  .map(d => d.price)
  .filter((value): value is number => value != null && Number.isFinite(value)))
const budgets = computed(() => priceData.value
  .map(d => d.budgetPrice)
  .filter((value): value is number => value != null && Number.isFinite(value)))
const hasBudgetPrice = computed(() => budgets.value.length > 0)
const chartPrices = computed(() => [...prices.value, ...budgets.value])
const hasChartData = computed(() => chartPrices.value.length > 0)
const selectedPoint = computed(() =>
  selectedIndex.value >= 0 ? priceData.value[selectedIndex.value] || null : null
)
const trendYears = computed(() => availableTrendYears.value)
const yearPickerLabels = computed(() => ['滚动区间', ...trendYears.value.map(year => `${year}年`)])
const selectedYearIndex = computed(() => {
  if (selectedTrendYear.value === 'rolling') return 0
  const index = trendYears.value.indexOf(Number(selectedTrendYear.value))
  return index < 0 ? 0 : index + 1
})

const minPrice = computed(() => {
  const arr = prices.value
  if (arr.length === 0) return 0
  return Math.min(...arr)
})

const maxPrice = computed(() => {
  const arr = prices.value
  if (arr.length === 0) return 0
  return Math.max(...arr)
})

const avgPrice = computed(() => {
  const arr = prices.value
  if (arr.length === 0) return 0
  return arr.reduce((a, b) => a + b, 0) / arr.length
})

const lastPrice = computed(() => prices.value[prices.value.length - 1] || 0)

const formatPrice = (price: number) => {
  return `${props.currencySymbol || ''}${price.toFixed(2)}`
}

const drawChart = () => {
  if (!hasChartData.value) return

  // #ifdef MP-WEIXIN
  // Canvas 2D API
  const query = uni.createSelectorQuery().in(instance)
  query.select(`#${canvasId}`)
    .fields({ node: true, size: true, rect: true }, () => {})
    .exec((res) => {
      if (!res || !res[0] || !res[0].node) {
        console.warn('Canvas node not found')
        return
      }

      const canvas = res[0].node as any
      const ctx = canvas.getContext('2d')
      const dpr = uni.getWindowInfo().pixelRatio || 2

      // 使用实际节点尺寸
      const width = res[0].width
      const height = res[0].height
      lastCanvasLeft = Number(res[0].left || 0)

      canvas.width = width * dpr
      canvas.height = height * dpr
      ctx.scale(dpr, dpr)

      drawChartContent(ctx, width, height)
    })
  // #endif

  // #ifdef H5
  const ctx = uni.createCanvasContext(canvasId)
  drawChartContent(ctx, canvasWidth, canvasHeight)
  ctx.draw()
  // #endif
}

const drawChartContent = (ctx: any, width: number, height: number) => {
  const rawMin = Math.min(...chartPrices.value)
  const rawMax = Math.max(...chartPrices.value)
  const rawRange = rawMax - rawMin
  const axisPadding = Math.max(rawRange * 0.12, rawMax * 0.015, 1)
  const min = Math.max(rawMin - axisPadding, 0)
  const max = rawMax + axisPadding
  const range = max - min || 1

  const plotWidth = width - yAxisWidth - padding
  const plotHeight = height - padding - xAxisHeight
  lastPlotMetrics = { left: yAxisWidth, width: plotWidth }

  // 绘制Y轴网格线
  ctx.strokeStyle = '#E5E5E5'
  ctx.lineWidth = 0.5
  for (let i = 0; i <= 4; i++) {
    const y = padding + plotHeight * i / 4
    ctx.beginPath()
    ctx.moveTo(yAxisWidth, y)
    ctx.lineTo(width - padding, y)
    ctx.stroke()
  }

  // 绘制Y轴标签
  ctx.font = '10px sans-serif'
  ctx.fillStyle = '#999999'
  ctx.textAlign = 'right'
  const step = range / 4 || 1
  for (let i = 0; i <= 4; i++) {
    const val = min + step * i
    const y = padding + plotHeight * (1 - i / 4) + 4
    ctx.fillText(`${props.currencySymbol || ''}${val.toFixed(2)}`, yAxisWidth - 8, y)
  }

  const pricePoints = priceData.value
    .map((item, index) => {
      if (item.price == null) return null
      const x = yAxisWidth + (index / (priceData.value.length - 1 || 1)) * plotWidth
      const y = padding + (1 - (item.price - min) / range) * plotHeight
      return { x, y }
    })
    .filter((point): point is { x: number; y: number } => point != null)

  if (pricePoints.length > 0) {
    ctx.beginPath()
    ctx.moveTo(pricePoints[0].x, pricePoints[0].y)
    for (let i = 1; i < pricePoints.length; i++) {
      const previous = pricePoints[i - 1]
      const current = pricePoints[i]
      const middleX = (previous.x + current.x) / 2
      ctx.quadraticCurveTo(previous.x, previous.y, middleX, (previous.y + current.y) / 2)
    }
    const lastPoint = pricePoints[pricePoints.length - 1]
    ctx.quadraticCurveTo(lastPoint.x, lastPoint.y, lastPoint.x, lastPoint.y)
    ctx.strokeStyle = lineColor
    ctx.lineWidth = 2.5
    ctx.stroke()

    if (pricePoints.length === 1) {
      ctx.beginPath()
      ctx.arc(pricePoints[0].x, pricePoints[0].y, 4, 0, Math.PI * 2)
      ctx.fillStyle = lineColor
      ctx.fill()
    }
  }

  // 每个日期使用接口返回的预算价格；缺失时沿用当前预算价作为兜底。
  if (hasBudgetPrice.value) {
    const budgetPoints = priceData.value
      .map((item, index) => {
        if (item.budgetPrice == null) return null
        const x = yAxisWidth + (index / (priceData.value.length - 1 || 1)) * plotWidth
        const y = padding + (1 - (item.budgetPrice - min) / range) * plotHeight
        return { x, y }
      })
      .filter((point): point is { x: number; y: number } => point != null)

    ctx.save?.()
    ctx.beginPath()
    ctx.setLineDash?.([6, 4])
    budgetPoints.forEach((point, index) => {
      if (index === 0) ctx.moveTo(point.x, point.y)
      else ctx.lineTo(point.x, point.y)
    })
    ctx.strokeStyle = budgetColor
    ctx.lineWidth = 2
    ctx.stroke()
    ctx.setLineDash?.([])
    ctx.restore?.()
  }

  // 显示首尾日期，触摸后显示所选日期。
  ctx.font = '10px sans-serif'
  ctx.fillStyle = '#98A2B3'
  ctx.textAlign = 'left'
  ctx.fillText(formatAxisDate(priceData.value[0]?.date), yAxisWidth, height - 3)
  ctx.textAlign = 'right'
  ctx.fillText(formatAxisDate(priceData.value[priceData.value.length - 1]?.date), width - padding, height - 3)

  const selected = selectedPoint.value
  if (selected && selectedIndex.value >= 0) {
    const selectedX = yAxisWidth + (selectedIndex.value / (priceData.value.length - 1 || 1)) * plotWidth
    const selectedValue = selected.price ?? selected.budgetPrice
    if (selectedValue == null) return
    const selectedY = padding + (1 - (selectedValue - min) / range) * plotHeight
    ctx.beginPath()
    ctx.setLineDash?.([3, 3])
    ctx.moveTo(selectedX, padding)
    ctx.lineTo(selectedX, padding + plotHeight)
    ctx.strokeStyle = '#98A2B3'
    ctx.lineWidth = 1
    ctx.stroke()
    ctx.setLineDash?.([])
    ctx.beginPath()
    ctx.arc(selectedX, selectedY, 4, 0, Math.PI * 2)
    ctx.fillStyle = selected.price == null ? budgetColor : lineColor
    ctx.fill()
  }
}

const formatAxisDate = (date?: string) => {
  if (!date) return ''
  const parts = date.split('-')
  return parts.length >= 3 ? `${Number(parts[1])}/${Number(parts[2])}` : date
}

const handleChartTouch = (event: any) => {
  if (!lastPlotMetrics || priceData.value.length === 0) return
  const touch = event?.touches?.[0] || event?.changedTouches?.[0]
  const offsetX = Number(touch?.x ?? event?.detail?.x)
  const pageX = Number(touch?.clientX ?? touch?.pageX)
  const x = Number.isFinite(offsetX) ? offsetX : pageX - lastCanvasLeft
  if (!Number.isFinite(x)) return
  const ratio = Math.max(0, Math.min(1, (x - lastPlotMetrics.left) / lastPlotMetrics.width))
  selectedIndex.value = Math.round(ratio * (priceData.value.length - 1))
  nextTick(drawChart)
}

const selectPeriod = (days: number) => {
  selectedPeriod.value = days
  loadTrendData()
}

const handleYearChange = (event: any) => {
  const index = Number(event?.detail?.value || 0)
  selectedTrendYear.value = index === 0 ? 'rolling' : String(trendYears.value[index - 1])
  if (selectedTrendYear.value !== 'rolling') selectedPeriod.value = 365
  loadTrendData()
}

const loadTrendYears = async () => {
  if (!props.productId) return
  try {
    const response = await getProductPriceYears(props.productId, false)
    availableTrendYears.value = response.data ?? []
    if (selectedTrendYear.value !== 'rolling' && !availableTrendYears.value.includes(Number(selectedTrendYear.value))) {
      selectedTrendYear.value = 'rolling'
    }
  } catch (error) {
    console.error('加载价格年份失败:', error)
    availableTrendYears.value = []
    selectedTrendYear.value = 'rolling'
  }
}

const loadTrendData = async () => {
  if (!props.productId) return

  loading.value = true
  try {
    const selectedYear = selectedTrendYear.value === 'rolling' ? null : Number(selectedTrendYear.value)
    const now = new Date()
    const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    const endDate = selectedYear
      ? (selectedYear === now.getFullYear() ? today : `${selectedYear}-12-31`)
      : undefined
    const startDate = selectedYear && selectedPeriod.value === 365 ? `${selectedYear}-01-01` : undefined
    const res = await getPriceTrend(props.productId, selectedPeriod.value, false, endDate, startDate)

    if (res.code === 200 && res.data) {
      priceData.value = res.data
        .filter((item: any) => item.date && (item.currentPrice != null || item.budgetPrice != null))
        .map((item: any) => ({
          date: item.date,
          price: item.currentPrice == null ? null : Number(item.currentPrice),
          budgetPrice: item.budgetPrice == null ? null : Number(item.budgetPrice)
        }))

      selectedIndex.value = priceData.value.length - 1
      nextTick(() => {
        drawChart()
      })
    }
  } catch (error) {
    console.error('加载价格走势失败:', error)
  } finally {
    loading.value = false
  }
}

watch(() => props.productId, async () => {
  selectedTrendYear.value = 'rolling'
  await loadTrendYears()
  loadTrendData()
}, { immediate: true })

watch(() => props.budgetPrice, () => {
  if (priceData.value.length > 0) nextTick(drawChart)
})
</script>

<style scoped>
.trend-chart {
  background: #FFFFFF;
  border-radius: 12rpx;
  padding: 24rpx;
}

.period-tabs {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 24rpx;
  width: 100%;
}

.chart-legend {
  display: flex;
  justify-content: flex-end;
  gap: 24rpx;
  margin: -4rpx 0 10rpx;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #667085;
  font-size: 22rpx;
}

.legend-line {
  width: 34rpx;
  height: 4rpx;
  border-radius: 999rpx;
}

.legend-line.selling {
  background: #0D6E6E;
}

.legend-line.budget {
  height: 0;
  border-top: 4rpx dashed #E07B54;
}

.period-tab {
  flex-shrink: 0;
  padding: 12rpx 20rpx;
  background: #F5F5F5;
  border-radius: 8rpx;
}

.year-picker {
  flex-shrink: 0;
}

.year-tab {
  border: 1rpx solid #D0D5DD;
  background: #FFFFFF;
}

.period-tab text {
  font-size: 26rpx;
  color: #666666;
}

.period-tab.active {
  background: #0D6E6E;
}

.period-tab.active text {
  color: #FFFFFF;
}

.chart-container {
  position: relative;
  width: 100%;
  height: 360rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chart-canvas {
  display: block;
}

.loading-overlay,
.empty-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.8);
}

.loading-overlay text,
.empty-overlay text {
  font-size: 28rpx;
  color: #999999;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
  margin-top: 24rpx;
  padding: 16rpx;
  background: #F9FAFB;
  border-radius: 8rpx;
}

.selected-detail {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 18rpx;
  padding: 18rpx 20rpx;
  border: 1px solid #E4E7EC;
  border-radius: 10rpx;
  background: #F9FAFB;
}

.selected-date,
.selected-hint,
.selected-prices text {
  display: block;
}

.selected-date {
  color: #1A1A1A;
  font-size: 24rpx;
  font-weight: 700;
}

.selected-hint {
  margin-top: 5rpx;
  color: #98A2B3;
  font-size: 19rpx;
}

.selected-prices {
  color: #0D6E6E;
  font-size: 22rpx;
  font-weight: 650;
  line-height: 1.6;
  text-align: right;
}

.selected-prices .budget-text {
  color: #E07B54;
}

.stat-item {
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 22rpx;
  color: #888888;
  margin-bottom: 8rpx;
}

.stat-value {
  font-family: Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: #1A1A1A;
}

.stat-value.high {
  color: #E03B3B;
}

.stat-value.low {
  color: #52C41A;
}

.stat-value.current {
  color: #0D6E6E;
  font-weight: 600;
}
</style>
