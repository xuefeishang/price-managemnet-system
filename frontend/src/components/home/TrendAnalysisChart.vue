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

use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const props = defineProps<{
  trend: TrendAnalysis
}>()

const emit = defineEmits<{
  (e: 'rangeChange', days: number): void
}>()

const { themeConfig } = useTheme()
const { chartRanges } = useHomeConfig()

const selectedRange = ref(30)

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
      axisLabel: { color: '#888', fontSize: 10 }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255,255,255,0.95)',
      borderColor: '#E5E5E5',
      textStyle: { color: '#333', fontSize: 12 },
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const p = params[0]
        const val = p.value != null ? p.value.toFixed(2) : '--'
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
      <h3 class="section-title">趋势分析</h3>
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

    <div class="chart-container">
      <v-chart
        v-if="trend && trend.dates && trend.dates.length > 0"
        class="trend-chart"
        :option="chartOption"
        autoresize
      />
      <div v-else class="chart-empty">
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M3 3v18h18"/><path d="M18 9l-5 5-4-4-3 3"/>
        </svg>
        <span>暂无趋势数据</span>
      </div>
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
  height: 280px;
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
    height: 200px;
  }
}
</style>