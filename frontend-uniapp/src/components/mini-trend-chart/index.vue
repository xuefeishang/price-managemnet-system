<template>
  <view class="mini-chart" :style="{ width: widthRpx, height: heightRpx }">
    <canvas
      type="2d"
      :id="canvasId"
      class="chart-canvas"
    />

    <!-- 加载状态 -->
    <view v-if="loading" class="loading-overlay">
      <text class="loading-text">...</text>
    </view>

    <!-- 无数据状态 -->
    <view v-if="!loading && priceData.length === 0" class="empty-overlay">
      <text class="empty-text">--</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed, getCurrentInstance, onMounted } from 'vue'
import { get } from '@/api/request'

const props = withDefaults(defineProps<{
  productId: number
  days?: number
  width?: number  // rpx 单位
  height?: number // rpx 单位
  lineColor?: string
}>(), {
  days: 30,
  width: 232,
  height: 60,
  lineColor: '#0D6E6E'
})

const instance = getCurrentInstance()
const canvasId = `mini-chart-${props.productId}`

// rpx 转 px
const widthRpx = computed(() => `${props.width}rpx`)
const heightRpx = computed(() => `${props.height}rpx`)

const loading = ref(false)
const priceData = ref<number[]>([])
const canvasReady = ref(false)

// HEX转RGB
const hexToRgb = (hex: string) => {
  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  return result ? {
    r: parseInt(result[1], 16),
    g: parseInt(result[2], 16),
    b: parseInt(result[3], 16)
  } : { r: 13, g: 110, b: 110 }
}

// rpx 转 px
const rpxToPx = (rpx: number): number => {
  const sysInfo = uni.getWindowInfo()
  const screenWidth = sysInfo.windowWidth
  return (rpx / 750) * screenWidth
}

const drawChart = () => {
  if (priceData.value.length === 0 || !canvasReady.value) return

  // #ifdef MP-WEIXIN
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

      // 设置物理像素尺寸
      canvas.width = Math.floor(width * dpr)
      canvas.height = Math.floor(height * dpr)
      ctx.scale(dpr, dpr)

      drawChartContent(ctx, width, height)
    })
  // #endif

  // #ifdef H5
  const width = rpxToPx(props.width)
  const height = rpxToPx(props.height)
  const ctx = uni.createCanvasContext(canvasId)
  drawChartContent(ctx, width, height)
  ctx.draw()
  // #endif
}

const drawChartContent = (ctx: any, width: number, height: number) => {
  if (width <= 0 || height <= 0) return

  const padding = 2
  const chartWidth = width - 2 * padding
  const chartHeight = height - 2 * padding

  if (chartWidth <= 0 || chartHeight <= 0) return

  const prices = priceData.value
  const minPrice = Math.min(...prices)
  const maxPrice = Math.max(...prices)
  const range = maxPrice - minPrice || 1

  // 计算点坐标
  const points = prices.map((price, index) => {
    const x = padding + (index / (prices.length - 1 || 1)) * chartWidth
    const y = padding + (1 - (price - minPrice) / range) * chartHeight
    return { x, y }
  })

  // 填充区域
  ctx.beginPath()
  ctx.moveTo(points[0].x, height - padding)
  points.forEach(p => ctx.lineTo(p.x, p.y))
  ctx.lineTo(points[points.length - 1].x, height - padding)
  ctx.closePath()

  // 渐变填充
  const gradient = ctx.createLinearGradient(0, padding, 0, height - padding)
  const rgb = hexToRgb(props.lineColor)
  gradient.addColorStop(0, `rgba(${rgb.r},${rgb.g},${rgb.b},0.30)`)
  gradient.addColorStop(1, `rgba(${rgb.r},${rgb.g},${rgb.b},0.05)`)
  ctx.fillStyle = gradient
  ctx.fill()

  // 绘制曲线
  ctx.beginPath()
  points.forEach((p, i) => {
    if (i === 0) ctx.moveTo(p.x, p.y)
    else ctx.lineTo(p.x, p.y)
  })
  ctx.strokeStyle = props.lineColor
  ctx.lineWidth = 1.5
  ctx.stroke()

  // 绘制最新价格点
  const lastPoint = points[points.length - 1]
  ctx.beginPath()
  ctx.arc(lastPoint.x, lastPoint.y, 3, 0, 2 * Math.PI)
  ctx.fillStyle = props.lineColor
  ctx.fill()
}

const loadTrendData = async () => {
  if (!props.productId) return

  loading.value = true
  try {
    const res = await get<any[]>(`/api/products/${props.productId}/price-trend`, { days: props.days })

    if (res.code === 200 && res.data) {
      priceData.value = res.data
        .map((item: any) => item.currentPrice)
        .filter((p: number | null) => p != null)

      nextTick(() => {
        setTimeout(() => {
          drawChart()
        }, 100)
      })
    }
  } catch (error) {
    console.error('加载价格走势失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  canvasReady.value = true
})

watch(() => props.productId, () => {
  loadTrendData()
}, { immediate: true })
</script>

<style scoped>
.mini-chart {
  position: relative;
  display: block;
  overflow: hidden;
}

.chart-canvas {
  width: 100%;
  height: 100%;
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
  background: transparent;
}

.loading-text,
.empty-text {
  font-size: 20rpx;
  color: #999999;
}
</style>
