<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { TrendAnalysis } from '@/api/home'

const props = defineProps<{
  trend: TrendAnalysis | null
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'rangeChange', days: number): void
}>()

const selectedRange = ref(30)
const rangeOptions = [
  { label: '30天', value: 30 },
  { label: '90天', value: 90 },
  { label: '180天', value: 180 }
]

// 简化版图表 - 使用 canvas 绘制
const canvasId = 'trendCanvas'
let canvasContext: any = null

const drawChart = () => {
  if (!props.trend || !props.trend.dates || props.trend.dates.length === 0) return

  const dates = props.trend.dates
  const avgTrend = props.trend.avgTrend || []

  // uni-app canvas 需要在 onReady 后获取
  uni.createSelectorQuery()
    .select(`#${canvasId}`)
    .fields({ node: true, size: true })
    .exec((res) => {
      if (!res[0]) return

      const canvas = res[0].node
      const ctx = canvas.getContext('2d')
      const width = res[0].width
      const height = res[0].height

      canvas.width = width
      canvas.height = height

      // 绘制背景
      ctx.fillStyle = '#FFFFFF'
      ctx.fillRect(0, 0, width, height)

      // 获取有效价格数据
      const validPrices = avgTrend.filter((p): p is number => p != null)
      if (validPrices.length < 2) return

      const minPrice = Math.min(...validPrices)
      const maxPrice = Math.max(...validPrices)
      const priceRange = maxPrice - minPrice || 1

      // 绘制网格线
      ctx.strokeStyle = '#F0F0F0'
      ctx.lineWidth = 1
      for (let i = 0; i <= 4; i++) {
        const y = (height - 40) * i / 4 + 20
        ctx.beginPath()
        ctx.moveTo(40, y)
        ctx.lineTo(width - 20, y)
        ctx.stroke()
      }

      // 绘制折线
      const lineColor = '#0D6E6E'
      ctx.strokeStyle = lineColor
      ctx.lineWidth = 2
      ctx.beginPath()

      const stepX = (width - 60) / (dates.length - 1 || 1)
      let firstPoint = true

      dates.forEach((date, i) => {
        const price = avgTrend[i]
        if (price == null) return

        const x = 40 + i * stepX
        const y = height - 40 - ((price - minPrice) / priceRange) * (height - 60)

        if (firstPoint) {
          ctx.moveTo(x, y)
          firstPoint = false
        } else {
          ctx.lineTo(x, y)
        }
      })

      ctx.stroke()

      // 绘制填充区域
      ctx.fillStyle = `${lineColor}20`
      ctx.beginPath()
      firstPoint = true
      dates.forEach((date, i) => {
        const price = avgTrend[i]
        if (price == null) return

        const x = 40 + i * stepX
        const y = height - 40 - ((price - minPrice) / priceRange) * (height - 60)

        if (firstPoint) {
          ctx.moveTo(x, y)
          firstPoint = false
        } else {
          ctx.lineTo(x, y)
        }
      })

      // 闭合填充区域
      const lastValidIndex = avgTrend.findLastIndex((p) => p != null)
      if (lastValidIndex >= 0) {
        ctx.lineTo(40 + lastValidIndex * stepX, height - 40)
        ctx.lineTo(40, height - 40)
      }
      ctx.closePath()
      ctx.fill()
    })
}

watch(() => props.trend, () => {
  setTimeout(drawChart, 100)
}, { immediate: true })

const onRangeChange = (days: number) => {
  selectedRange.value = days
  emit('rangeChange', days)
}

const formatPrice = (value: number | null) => {
  if (value == null) return '--'
  return value.toFixed(2)
}

const latestPrice = computed(() => {
  if (!props.trend?.avgTrend) return null
  const valid = props.trend.avgTrend.filter((p): p is number => p != null)
  return valid.length > 0 ? valid[valid.length - 1] : null
})
</script>

<template>
  <view class="trend-section">
    <view class="section-header">
      <text class="section-title">重点走势</text>
      <view class="range-tabs">
        <text
          v-for="opt in rangeOptions"
          :key="opt.value"
          class="range-tab"
          :class="{ active: selectedRange === opt.value }"
          @click="onRangeChange(opt.value)"
        >
          {{ opt.label }}
        </text>
      </view>
    </view>

    <view v-if="loading" class="chart-loading">
      <view class="skeleton-chart"></view>
    </view>

    <view v-else-if="trend && trend.dates && trend.dates.length > 0" class="chart-container">
      <canvas
        :id="canvasId"
        type="2d"
        class="trend-canvas"
        :style="{ width: '100%', height: '200px' }"
      ></canvas>
      <view class="chart-info">
        <text class="chart-label">平均价格趋势</text>
        <text class="chart-value">¥{{ formatPrice(latestPrice) }}</text>
      </view>
    </view>

    <view v-else class="chart-empty">
      <text class="empty-text">暂无趋势数据</text>
    </view>
  </view>
</template>

<style scoped>
.trend-section {
  padding: 24rpx;
  background: #FFFFFF;
  margin-bottom: 16rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.range-tabs {
  display: flex;
  gap: 8rpx;
}

.range-tab {
  padding: 8rpx 16rpx;
  font-size: 24rpx;
  color: #666666;
  background: #F5F5F5;
  border-radius: 8rpx;
}

.range-tab.active {
  color: #FFFFFF;
  background: #0D6E6E;
}

.chart-container {
  position: relative;
}

.trend-canvas {
  width: 100%;
  height: 400rpx;
}

.chart-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16rpx;
  padding: 16rpx;
  background: #F5F5F5;
  border-radius: 8rpx;
}

.chart-label {
  font-size: 24rpx;
  color: #666666;
}

.chart-value {
  font-size: 32rpx;
  font-weight: 600;
  color: #0D6E6E;
}

.chart-loading {
  padding: 40rpx;
}

.skeleton-chart {
  height: 400rpx;
  background: linear-gradient(90deg, #E5E5E5 25%, #F5F5F5 50%, #E5E5E5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 16rpx;
}

.chart-empty {
  padding: 80rpx;
  text-align: center;
}

.empty-text {
  font-size: 28rpx;
  color: #999999;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>