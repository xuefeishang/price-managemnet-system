<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, MarkLineComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { useSafeChartAutoresize } from '@/composables/useSafeChartAutoresize'
import type { ProductTrendItem } from './TrendAnalysisChart.vue'

use([LineChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer])
const { chartAutoresize } = useSafeChartAutoresize()

const props = defineProps<{
  product: ProductTrendItem | null
  ranges: Array<{ key: string; label: string; days: number }>
  activeDays: number
}>()

const emit = defineEmits<{
  (e: 'rangeChange', days: number): void
}>()

const validPoints = computed(() =>
  (props.product?.points || [])
    .filter((point): point is { date: string; price: number } => point.price != null)
)

const stats = computed(() => {
  if (validPoints.value.length === 0) {
    return { latest: null, highest: null, lowest: null, average: null, latestDate: '' }
  }
  const prices = validPoints.value.map(point => point.price)
  const total = prices.reduce((sum, price) => sum + price, 0)
  return {
    latest: validPoints.value[validPoints.value.length - 1].price,
    highest: Math.max(...prices),
    lowest: Math.min(...prices),
    average: total / prices.length,
    latestDate: validPoints.value[validPoints.value.length - 1].date
  }
})

const formatNumber = (value: number | null | undefined) => {
  if (value == null) return '--'
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

const formatPrice = (value: number | null | undefined) =>
  props.product ? `${props.product.currencySymbol}${formatNumber(value)}` : '--'

const formatDate = (dateValue: string) => {
  if (!dateValue) return '--'
  const date = new Date(dateValue)
  if (Number.isNaN(date.getTime())) return dateValue
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const chartOption = computed(() => {
  const product = props.product
  if (!product || validPoints.value.length === 0) return {}

  const dates = validPoints.value.map(point => {
    const date = new Date(point.date)
    return Number.isNaN(date.getTime()) ? point.date : `${date.getMonth() + 1}/${date.getDate()}`
  })
  const prices = validPoints.value.map(point => point.price)
  const min = Math.min(...prices)
  const max = Math.max(...prices)
  const padding = Math.max((max - min) * 0.18, max * 0.02, 1)
  const lineColor = product.lineColor || '#0D6E6E'
  const areaColor = product.areaColor || `${lineColor}24`

  return {
    grid: { left: 52, right: 28, top: 28, bottom: 34 },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#E5E7EB' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#667085',
        fontSize: 11,
        interval: Math.max(Math.floor(dates.length / 6), 0)
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
        color: '#667085',
        fontSize: 11,
        formatter: (value: number) => formatNumber(value)
      }
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(255,255,255,0.97)',
      borderColor: '#D0D5DD',
      textStyle: { color: '#344054', fontSize: 12 },
      formatter: (params: any) => {
        const point = params?.[0]
        return point
          ? `${point.axisValue}<br/><span style="color:${lineColor}">●</span> ${product.name}: ${formatPrice(point.value)}`
          : ''
      }
    },
    series: [{
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
        lineStyle: { color: '#D0D5DD', type: 'dashed', width: 1 },
        label: { color: '#667085', fontSize: 10, formatter: '均价' },
        data: stats.value.average != null ? [{ yAxis: stats.value.average }] : []
      }
    }]
  }
})
</script>

<template>
  <div class="curve-panel-inner">
    <div class="curve-panel-top">
      <div class="curve-product-block">
        <p class="curve-product" v-if="product">
          <span>{{ product.name }}</span>
          <small v-if="product.originLabel" class="product-chip">{{ product.originLabel }}</small>
          <small v-if="product.specs" class="product-chip muted">{{ product.specs }}</small>
        </p>
        <p v-else class="curve-product muted">暂无选中产品</p>
      </div>
      <div class="trend-range-tabs" aria-label="走势时间范围">
        <button
          v-for="range in ranges"
          :key="range.key"
          type="button"
          class="range-tab"
          :class="{ active: activeDays === range.days }"
          @click="emit('rangeChange', range.days)"
        >
          {{ range.label }}
        </button>
      </div>
    </div>

    <div v-if="product" class="curve-price-row">
      <div class="curve-price-main">
        <span class="curve-price">{{ formatPrice(product.currentPrice ?? stats.latest) }}</span>
        <span class="curve-unit" v-if="product.unit">/ {{ product.unit }}</span>
      </div>
      <div class="curve-price-meta">
        <span class="curve-change" :class="product.direction">
          较昨日 {{ product.formattedDiff || '--' }}
          <template v-if="product.formattedPercent">（{{ product.formattedPercent }}）</template>
        </span>
        <span class="curve-updated">{{ formatDate(stats.latestDate) }} 更新</span>
      </div>
    </div>

    <div class="curve-chart-shell">
      <v-chart v-if="product && validPoints.length > 0" class="curve-chart" :option="chartOption" :autoresize="chartAutoresize" />
      <div v-else class="curve-empty">暂无可展示曲线的产品</div>
    </div>

    <div v-if="product" class="curve-stat-row">
      <span><small>最低价</small>{{ formatPrice(stats.lowest) }}</span>
      <span><small>最高价</small>{{ formatPrice(stats.highest) }}</span>
      <span><small>均价</small>{{ formatPrice(stats.average) }}</span>
      <span><small>最新价</small>{{ formatPrice(stats.latest) }}</span>
    </div>
  </div>
</template>

<style scoped>
.curve-panel-inner {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  min-width: 0;
  height: 100%;
}

.curve-panel-top,
.curve-stat-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-width: 0;
}

.curve-panel-top {
  align-items: flex-start;
}

.curve-product-block {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.curve-product {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  min-width: 0;
}

.curve-product span {
  color: var(--text-primary);
  font-weight: 600;
  font-size: var(--font-size-base);
  line-height: 1.35;
}

.curve-product small {
  color: var(--text-muted);
}

.curve-product.muted {
  color: var(--text-muted);
}

.product-chip {
  display: inline-flex;
  align-items: center;
  max-width: 160px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid color-mix(in srgb, var(--primary-color) 18%, var(--border-color));
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary-color) 8%, var(--bg-card));
  color: var(--primary-color) !important;
  font-size: var(--font-size-xs);
  font-weight: 600;
  line-height: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-chip.muted {
  border-color: var(--border-color);
  background: var(--gray-50);
  color: var(--text-secondary) !important;
}

.trend-range-tabs {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--gray-50);
  flex-shrink: 0;
}

.range-tab {
  min-width: 42px;
  height: 30px;
  padding: 0 12px;
  border: none;
  border-radius: calc(var(--radius) - 3px);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast), box-shadow var(--transition-fast);
}

.range-tab:hover {
  color: var(--primary-color);
}

.range-tab.active {
  background: var(--primary-color);
  color: #FFFFFF;
  box-shadow: var(--shadow-sm);
}

.curve-price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-width: 0;
}

.curve-price-main {
  min-width: 0;
}

.curve-price-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  min-width: 0;
  flex-wrap: wrap;
}

.curve-price {
  font-family: var(--font-mono);
  font-size: clamp(1.8rem, 2.2vw, 2.45rem);
  font-weight: 800;
  color: var(--primary-color);
  line-height: 1;
}

.curve-unit,
.curve-updated {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.curve-change {
  padding: 4px 9px;
  border-radius: 999px;
  background: var(--gray-100);
  color: var(--price-flat-color);
  font-size: var(--font-size-xs);
  font-weight: 700;
  white-space: nowrap;
}

.curve-change.up {
  background: rgba(239, 68, 68, 0.08);
  color: var(--price-rise-color);
}

.curve-change.down {
  background: rgba(16, 185, 129, 0.08);
  color: var(--price-fall-color);
}

.curve-chart-shell {
  min-width: 0;
  flex: 1 1 190px;
  min-height: 190px;
  height: auto;
}

.curve-chart {
  width: 100%;
  height: 100%;
  min-height: 190px;
}

.curve-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

.curve-stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--gray-100);
}

.curve-stat-row span {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-weight: 700;
}

.curve-stat-row small {
  color: var(--text-muted);
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  font-weight: 400;
}

@media (max-width: 768px) {
  .curve-panel-top,
  .curve-price-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .trend-range-tabs {
    width: 100%;
    overflow-x: auto;
  }

  .range-tab {
    flex: 1 0 auto;
  }

  .curve-price-meta {
    justify-content: flex-start;
  }

  .curve-chart-shell,
  .curve-chart {
    min-height: 220px;
  }

  .curve-stat-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: var(--spacing-sm);
  }
}
</style>
