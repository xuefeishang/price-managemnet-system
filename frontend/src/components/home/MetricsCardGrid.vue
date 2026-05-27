<script setup lang="ts">
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { ProductMetric } from '@/api/home'
import { useTheme } from '@/composables/useTheme'
import { getOriginName } from '@/composables/useDict'
import { useSafeChartAutoresize } from '@/composables/useSafeChartAutoresize'

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])
const { chartAutoresize } = useSafeChartAutoresize()

const props = defineProps<{
  products: ProductMetric[]
  columns?: number
}>()

const { themeConfig } = useTheme()

const parseOriginIds = (originIds?: string) => {
  if (!originIds) return []
  try {
    const parsed = JSON.parse(originIds)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

const getProductOriginLabel = (product: ProductMetric) =>
  parseOriginIds(product.originIds)
    .map(key => getOriginName(key))
    .filter(Boolean)
    .join(' / ')

const generateChartOption = (product: ProductMetric) => {
  const lineColor = product.priceDirection === 'up'
    ? themeConfig.value.priceRiseColor
    : product.priceDirection === 'down'
      ? themeConfig.value.priceFallColor
      : themeConfig.value.priceFlatColor || '#9CA3AF'

  return {
    grid: { left: 0, right: 0, top: 4, bottom: 0 },
    xAxis: { type: 'category', show: false },
    yAxis: { type: 'value', show: false },
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        return p ? `${product.productName}<br/>价格: ${product.currencySymbol}${p.value}` : ''
      },
      confine: true,
      textStyle: { fontSize: 10 }
    },
    series: [{
      type: 'line',
      data: [product.currentPrice, product.previousPrice].filter(Boolean),
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2, color: lineColor },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: lineColor + '40' },
            { offset: 1, color: lineColor + '05' }
          ]
        }
      }
    }]
  }
}
</script>

<template>
  <div class="metrics-grid" :style="{ gridTemplateColumns: `repeat(${columns || 4}, 1fr)` }">
    <div
      v-for="product in products"
      :key="product.productId"
      class="metric-card"
      :class="{ featured: product.featured }"
    >
      <div class="card-header">
        <div class="product-info">
          <span class="product-name">{{ product.productName }}</span>
          <span class="product-specs" v-if="product.specs">{{ product.specs }}</span>
          <span class="product-origin" v-if="getProductOriginLabel(product)">
            <span class="origin-label">产地</span>
            <span class="origin-value">{{ getProductOriginLabel(product) }}</span>
          </span>
        </div>
        <span
          class="trend-badge"
          :class="product.priceDirection"
        >
          {{ product.priceDirection === 'up' ? '↑' : product.priceDirection === 'down' ? '↓' : '—' }}
          {{ product.formattedChange }}
        </span>
      </div>

      <div class="card-price">
        <span class="price-symbol">{{ product.currencySymbol }}</span>
        <span class="price-value" v-if="product.currentPrice != null">
          {{ product.currentPrice.toLocaleString('zh-CN', { minimumFractionDigits: 0, maximumFractionDigits: 2 }) }}
        </span>
        <span class="price-value empty" v-else>--</span>
        <span class="price-unit" v-if="product.unit">/ {{ product.unit }}</span>
      </div>

      <div class="card-chart" v-if="product.currentPrice != null">
        <v-chart
          class="mini-chart"
          :option="generateChartOption(product)"
          :autoresize="chartAutoresize"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.metrics-grid {
  display: grid;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
}

.metric-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  cursor: pointer;
  transition: all var(--transition-fast);
  display: flex;
  flex-direction: column;
  min-height: 160px;
}

.metric-card:hover {
  box-shadow: var(--shadow-lg);
  transform: translateY(-4px);
  border-color: var(--primary-color);
}

.metric-card.featured {
  border-color: var(--primary-color);
  border-width: 2px;
  background: linear-gradient(135deg, var(--bg-card) 0%, rgba(13, 110, 110, 0.02) 100%);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
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

.trend-badge {
  padding: 4px 8px;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  font-weight: 600;
  white-space: nowrap;
  flex-shrink: 0;
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

.card-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
  margin-bottom: var(--spacing-sm);
}

.price-symbol {
  font-size: var(--font-size-lg);
  color: var(--text-secondary);
}

.price-value {
  font-family: var(--font-mono);
  font-size: 2.25rem;
  font-weight: 700;
  color: var(--primary-color);
  line-height: 1;
}

.price-value.empty {
  color: var(--gray-300);
  font-size: 1.5rem;
}

.price-unit {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.card-chart {
  flex: 1;
  min-height: 40px;
}

.mini-chart {
  width: 100%;
  height: 40px;
}

@media (max-width: 1023px) {
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr) !important;
    gap: var(--spacing-sm);
  }

  .metric-card {
    padding: var(--spacing-md);
    min-height: 140px;
  }

  .price-value {
    font-size: 1.75rem;
  }
}
</style>
