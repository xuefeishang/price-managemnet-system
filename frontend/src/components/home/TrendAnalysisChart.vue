<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { TrendAnalysis } from '@/api/home'
import { useTheme } from '@/composables/useTheme'
import { useHomeConfig } from '@/composables/useHomeConfig'
import { useSafeChartAutoresize } from '@/composables/useSafeChartAutoresize'

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

export interface ProductTrendPoint {
  date: string
  price: number | null
  budgetPrice?: number | null
}

export interface ProductTrendItem {
  id: number
  name: string
  specs?: string
  originLabel?: string
  hasOrigin?: boolean
  currencySymbol: string
  unit?: string
  direction: 'up' | 'down' | 'flat'
  formattedDiff?: string
  formattedPercent?: string
  currentPrice: number | null
  points: ProductTrendPoint[]
  lineColor?: string
  areaColor?: string
}

const props = defineProps<{
  trend?: TrendAnalysis
  products?: ProductTrendItem[]
  columns?: number
  showOverview?: boolean
  title?: string
}>()

const emit = defineEmits<{
  (e: 'rangeChange', days: number): void
}>()

const { themeConfig } = useTheme()
const { chartRanges } = useHomeConfig()
const { chartAutoresize } = useSafeChartAutoresize()

const selectedRange = ref(30)

const formatPrice = (value: number | null | undefined, currencySymbol = '') => {
  if (value == null) return '--'
  return `${currencySymbol}${value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })}`
}

const getValidPoints = (product: ProductTrendItem) =>
  product.points.filter(point => point.price != null) as Array<{ date: string; price: number }>

const hasProductChartData = (product: ProductTrendItem) =>
  product.points.some(point => point.date && (point.price != null || point.budgetPrice != null))

const getProductStats = (product: ProductTrendItem) => {
  const validPoints = getValidPoints(product)
  if (validPoints.length === 0) {
    return { latest: null, highest: null, lowest: null }
  }

  const latest = validPoints[validPoints.length - 1]
  const highest = validPoints.reduce((max, point) => point.price > max.price ? point : max, validPoints[0])
  const lowest = validPoints.reduce((min, point) => point.price < min.price ? point : min, validPoints[0])

  return { latest, highest, lowest }
}

const chartOption = computed(() => {
  const trend = props.trend
  if (!trend || !trend.dates || trend.dates.length === 0) return {}

  const avgTrend = trend.avgTrend || []
  const lineColor = themeConfig.value.chartPrimaryColor || '#0D6E6E'

  return {
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: trend.dates,
      axisLine: { lineStyle: { color: '#E5E5E5' } },
      axisLabel: {
        color: '#888',
        fontSize: 10,
        interval: Math.floor(trend.dates.length / 6)
      }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F0F0F0' } },
      axisLabel: {
        color: '#888',
        fontSize: 10,
        formatter: (value: number) => formatPrice(value)
      }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#E5E5E5',
      textStyle: { color: '#333', fontSize: 12 },
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const p = params[0]
        const val = formatPrice(p.value)
        return `${p.axisValue}<br/><span style="color:${lineColor}">●</span> 平均价格: ${val}`
      }
    },
    series: [{
      type: 'line',
      data: avgTrend,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2.5, color: lineColor },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: lineColor + '30' },
            { offset: 1, color: lineColor + '05' }
          ]
        }
      }
    }]
  }
})

const generateProductChartOption = (product: ProductTrendItem) => {
  const points = (product.points || []).filter(point => point.date && (point.price != null || point.budgetPrice != null))
  const dates = points.map(point => {
    const d = new Date(point.date)
    return Number.isNaN(d.getTime()) ? point.date : `${d.getMonth() + 1}/${d.getDate()}`
  })
  const prices = points.map(point => point.price)
  const budgets = points.map(point => point.budgetPrice ?? null)
  const validPrices = prices.filter((price): price is number => price != null)
  const validBudgets = budgets.filter((price): price is number => price != null)
  const lineColor = product.lineColor || themeConfig.value.chartPrimaryColor || '#0D6E6E'
  const areaColor = product.areaColor || `${lineColor}24`
  const budgetColor = themeConfig.value.chartBudgetColor || '#F59E0B'
  const stats = getProductStats(product)

  if (validPrices.length === 0 && validBudgets.length === 0) return {}

  const markData: any[] = validPrices.length > 0
    ? [
        { type: 'max', name: '最高价', label: { formatter: (params: any) => `高 ${formatPrice(params.value, product.currencySymbol)}` } },
        { type: 'min', name: '最低价', label: { formatter: (params: any) => `低 ${formatPrice(params.value, product.currencySymbol)}` } }
      ]
    : []

  if (stats.latest) {
    markData.push({
      name: '最新价',
      coord: [dates[dates.length - 1], stats.latest.price],
      value: stats.latest.price,
      itemStyle: { color: lineColor },
      label: {
        formatter: `现 ${formatPrice(stats.latest.price, product.currencySymbol)}`,
        position: 'right'
      }
    })
  }

  const chartValues = [...validPrices, ...validBudgets]
  const min = Math.min(...chartValues)
  const max = Math.max(...chartValues)
  const padding = Math.max((max - min) * 0.16, max * 0.02, 1)

  return {
    grid: { left: 36, right: 56, top: 28, bottom: 24 },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#E5E5E5' } },
      axisTick: { show: false },
      axisLabel: {
        color: '#888',
        fontSize: 10,
        interval: Math.max(Math.floor(dates.length / 4), 0)
      }
    },
    yAxis: {
      type: 'value',
      min: Math.max(min - padding, 0),
      max: max + padding,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: '#F0F0F0' } },
      axisLabel: {
        color: '#888',
        fontSize: 10,
        formatter: (value: number) => formatPrice(value)
      }
    },
    tooltip: {
      trigger: 'axis',
      confine: true,
      backgroundColor: 'rgba(255,255,255,0.96)',
      borderColor: '#E5E5E5',
      textStyle: { color: '#333', fontSize: 12 },
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : []
        if (!items.length) return ''
        return [
          items[0].axisValue,
          ...items
            .filter((item: any) => item.value != null)
            .map((item: any) => `${item.marker}${item.seriesName}: ${formatPrice(item.value, product.currencySymbol)}`)
        ].join('<br/>')
      }
    },
    series: [
      {
        name: '价格',
        type: 'line',
        data: prices,
        smooth: true,
        symbol: validPrices.length <= 1 ? 'circle' : 'none',
        symbolSize: 5,
        connectNulls: true,
        itemStyle: { color: lineColor },
        lineStyle: { width: 2.2, color: lineColor },
        label: {
          show: true,
          position: 'top',
          formatter: (params: any) => params.dataIndex === prices.length - 1
            ? formatPrice(params.value, product.currencySymbol)
            : '',
          color: lineColor,
          fontSize: 10,
          fontWeight: 600
        },
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
        markPoint: {
          symbolSize: 42,
          label: {
            color: '#fff',
            fontSize: 9
          },
          data: markData
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
        lineStyle: { width: 1.8, type: 'dashed', color: budgetColor, opacity: 0.82 }
      }
    ]
  }
}

const onRangeChange = (days: number) => {
  selectedRange.value = days
  emit('rangeChange', days)
}

watch(() => props.trend, (newTrend) => {
  if (newTrend && newTrend.days) {
    selectedRange.value = newTrend.days
  }
})
</script>

<template>
  <div class="trend-section">
    <div class="section-header">
      <h3 class="section-title">{{ title || '趋势分析' }}</h3>
      <div class="range-tabs">
        <button
          v-for="range in chartRanges"
          :key="range.key"
          class="range-tab"
          :class="{ active: selectedRange === range.days }"
          @click="onRangeChange(range.days)"
        >
          {{ range.label }}
        </button>
      </div>
    </div>

    <div class="chart-container" v-if="showOverview !== false">
      <v-chart
        v-if="trend && trend.dates && trend.dates.length > 0"
        class="trend-chart"
        :option="chartOption"
        :autoresize="chartAutoresize"
      />
      <div v-else class="chart-empty">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M3 3v18h18"/><path d="M18 9l-5 5-4-4-3 3"/>
        </svg>
        <span>暂无趋势数据</span>
      </div>
    </div>

    <div class="product-trends" v-if="products && products.length > 0" :style="{ gridTemplateColumns: `repeat(${columns || 2}, minmax(0, 1fr))` }">
      <article
        v-for="product in products"
        :key="product.id"
        class="product-trend-card"
      >
        <div class="product-trend-header">
          <div class="product-title">
            <span class="product-name">{{ product.name }}</span>
            <span class="product-specs" v-if="product.specs">{{ product.specs }}</span>
            <span class="product-origin" v-if="product.hasOrigin && product.originLabel">
              <span class="origin-label">产地</span>
              <span class="origin-value">{{ product.originLabel }}</span>
            </span>
          </div>
          <div class="product-price">
            <span class="current-price">{{ formatPrice(product.currentPrice, product.currencySymbol) }}</span>
            <span class="price-unit" v-if="product.unit">/ {{ product.unit }}</span>
            <span class="trend-badge" :class="product.direction">
              {{ product.direction === 'up' ? '↑' : product.direction === 'down' ? '↓' : '—' }}
              {{ product.formattedDiff || '0' }}
              <template v-if="product.formattedPercent">（{{ product.formattedPercent }}）</template>
            </span>
          </div>
        </div>

        <div class="product-stat-row">
          <span>最新：{{ formatPrice(getProductStats(product).latest?.price, product.currencySymbol) }}</span>
          <span>最高：{{ formatPrice(getProductStats(product).highest?.price, product.currencySymbol) }}</span>
          <span>最低：{{ formatPrice(getProductStats(product).lowest?.price, product.currencySymbol) }}</span>
        </div>

        <div class="product-chart">
          <v-chart
            v-if="hasProductChartData(product)"
            class="product-line-chart"
            :option="generateProductChartOption(product)"
            :autoresize="chartAutoresize"
          />
          <div v-else class="chart-empty compact">
            <span>暂无该产品走势</span>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.trend-section {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  margin-bottom: var(--spacing-xl);
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-md);
}

.section-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.range-tabs {
  display: flex;
  gap: var(--spacing-xs);
}

.range-tab {
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: transparent;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.range-tab:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.range-tab.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.chart-container {
  height: 220px;
  margin-bottom: var(--spacing-lg);
}

.trend-chart {
  width: 100%;
  height: 100%;
}

.chart-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  color: var(--text-muted);
}

.chart-empty.compact {
  height: 160px;
  font-size: var(--font-size-sm);
}

.product-trends {
  display: grid;
  gap: var(--spacing-md);
}

.product-trend-card {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-card);
  padding: var(--spacing-md);
  min-width: 0;
}

.product-trend-header {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-md);
  align-items: flex-start;
  margin-bottom: var(--spacing-sm);
}

.product-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.product-name {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-specs {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-origin {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  max-width: 100%;
  padding: 2px 8px 2px 3px;
  border: 1px solid color-mix(in srgb, var(--primary-color) 20%, var(--border-color));
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary-color) 10%, var(--bg-card));
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  line-height: 1.35;
}

.origin-label {
  flex-shrink: 0;
  padding: 1px 5px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--primary-color) 12%, transparent);
  font-weight: 600;
}

.origin-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: baseline;
  gap: 4px;
  flex-shrink: 0;
}

.current-price {
  font-family: var(--font-mono);
  font-size: var(--font-size-xl);
  font-weight: 700;
  color: var(--primary-color);
}

.price-unit {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.trend-badge {
  padding: 3px 8px;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  white-space: nowrap;
}

.trend-badge.up {
  background: rgba(239, 68, 68, 0.1);
  color: var(--price-rise-color);
}

.trend-badge.down {
  background: rgba(16, 185, 129, 0.1);
  color: var(--price-fall-color);
}

.trend-badge.flat {
  background: var(--gray-100);
  color: var(--gray-400);
}

.product-stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  margin-bottom: var(--spacing-sm);
}

.product-chart {
  height: 180px;
}

.product-line-chart {
  width: 100%;
  height: 100%;
}

@media (max-width: 1023px) {
  .trend-section {
    padding: var(--spacing-md);
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-sm);
  }

  .chart-container {
    height: 180px;
  }

  .product-trends {
    grid-template-columns: 1fr !important;
  }

  .product-trend-header {
    flex-direction: column;
    gap: var(--spacing-xs);
  }

  .product-price {
    justify-content: flex-start;
  }
}
</style>
