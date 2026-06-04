<template>
  <view class="trend-chart">
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
    </view>

    <!-- 图表区域 -->
    <view class="chart-container">
      <canvas
        type="2d"
        :id="canvasId"
        class="chart-canvas"
        :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
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
    <view class="stats-row" v-if="!loading && priceData.length > 0">
      <view class="stat-item">
        <text class="stat-label">最高</text>
        <text class="stat-value high">¥{{ formatPrice(maxPrice) }}</text>
      </view>
      <view class="stat-item">
        <text class="stat-label">最低</text>
        <text class="stat-value low">¥{{ formatPrice(minPrice) }}</text>
      </view>
      <view class="stat-item">
        <text class="stat-label">平均</text>
        <text class="stat-value">¥{{ formatPrice(avgPrice) }}</text>
      </view>
      <view class="stat-item">
        <text class="stat-label">最新</text>
        <text class="stat-value current">¥{{ formatPrice(lastPrice) }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick, getCurrentInstance } from 'vue'
import { get } from '@/api/request'

const props = defineProps<{
  productId: number
}>()

const instance = getCurrentInstance()
const canvasId = `trend-chart-${props.productId}`
const canvasWidth = 320
const canvasHeight = 180
const yAxisWidth = 50
const xAxisHeight = 20
const padding = 10
const lineColor = '#0D6E6E'

const selectedPeriod = ref(30)
const loading = ref(false)
const priceData = ref<{ date: string; price: number }[]>([])

const prices = computed(() => priceData.value.map(d => d.price))

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
  return price.toFixed(2)
}

// HEX转RGB
const hexToRgb = (hex: string) => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : { r: 13, g: 110, b: 110 }
}

const drawChart = () => {
  if (prices.value.length === 0) return

  // #ifdef MP-WEIXIN
  // Canvas 2D API
  const query = uni.createSelectorQuery().in(instance)
  query.select(`#${canvasId}`)
    .fields({ node: true, size: true }, () => {})
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
  const min = minPrice.value
  const max = maxPrice.value
  const range = max - min || 1

  const plotWidth = width - yAxisWidth - padding
  const plotHeight = height - padding - xAxisHeight

  // 绘制Y轴网格线
  ctx.strokeStyle = '#E5E5E5'
  ctx.lineWidth = 0.5
  for (let i = 1; i <= 4; i++) {
    const y = padding + plotHeight * i / 5
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
    const y = padding + plotHeight * (4 - i) / 5 + 4
    ctx.fillText(val.toFixed(2), yAxisWidth - 8, y)
  }

  // 计算曲线点
  const points = prices.value.map((price, index) => {
    const x = yAxisWidth + (index / (prices.value.length - 1 || 1)) * plotWidth
    const y = padding + (1 - (price - min) / range) * plotHeight
    return { x, y }
  })

  // 填充区域
  ctx.beginPath()
  ctx.moveTo(points[0].x, height - xAxisHeight)
  points.forEach(p => ctx.lineTo(p.x, p.y))
  ctx.lineTo(points[points.length - 1].x, height - xAxisHeight)
  ctx.closePath()

  const gradient = ctx.createLinearGradient(0, padding, 0, height - xAxisHeight)
  const rgb = hexToRgb(lineColor)
  gradient.addColorStop(0, `rgba(${rgb.r},${rgb.g},${rgb.b},0.20)`)
  gradient.addColorStop(1, `rgba(${rgb.r},${rgb.g},${rgb.b},0.02)`)
  ctx.fillStyle = gradient
  ctx.fill()

  // 绘制曲线
  ctx.beginPath()
  points.forEach((p, i) => {
    if (i === 0) ctx.moveTo(p.x, p.y)
    else ctx.lineTo(p.x, p.y)
  })
  ctx.strokeStyle = lineColor
  ctx.lineWidth = 1
  ctx.stroke()

  // 绘制数据点
  const visibleCount = Math.min(10, points.length)
  const stepIndex = Math.max(1, Math.floor(points.length / visibleCount))
  ctx.fillStyle = lineColor
  for (let i = 0; i < points.length; i += stepIndex) {
    ctx.beginPath()
    ctx.arc(points[i].x, points[i].y, 1, 0, 2 * Math.PI)
    ctx.fill()
  }
  const lastPt = points[points.length - 1]
  ctx.beginPath()
  ctx.arc(lastPt.x, lastPt.y, 1, 0, 2 * Math.PI)
  ctx.fill()
}

const selectPeriod = (days: number) => {
  selectedPeriod.value = days
  loadTrendData()
}

const loadTrendData = async () => {
  if (!props.productId) return

  loading.value = true
  try {
    const res = await get<any[]>(`/api/products/${props.productId}/price-trend`, { days: selectedPeriod.value })

    if (res.code === 200 && res.data) {
      priceData.value = res.data
        .filter((item: any) => item.currentPrice != null)
        .map((item: any) => ({
          date: item.date,
          price: item.currentPrice
        }))

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

watch(() => props.productId, () => {
  loadTrendData()
}, { immediate: true })
</script>

<style scoped>
.trend-chart {
  background: #FFFFFF;
  border-radius: 12rpx;
  padding: 24rpx;
}

.period-tabs {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.period-tab {
  padding: 12rpx 24rpx;
  background: #F5F5F5;
  border-radius: 8rpx;
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
