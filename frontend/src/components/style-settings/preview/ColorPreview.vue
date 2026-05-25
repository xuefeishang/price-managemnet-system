<script setup lang="ts">
import { computed } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import type { StyleConfig } from '@/types/theme'

const props = defineProps<{
  editingConfig: StyleConfig
}>()

const changeScenarios = computed(() => [
  {
    name: '价格上涨',
    key: 'rise',
    color: props.editingConfig.priceRiseColor,
    examples: ['+2.5%', '+1.8%', '+0.5%']
  },
  {
    name: '价格下跌',
    key: 'fall',
    color: props.editingConfig.priceFallColor,
    examples: ['-1.2%', '-3.5%', '-0.8%']
  },
  {
    name: '价格持平',
    key: 'flat',
    color: props.editingConfig.priceFlatColor,
    examples: ['0.0%', '—', '=']
  }
])

const chartColors = computed(() => {
  const colors = props.editingConfig.chartColors || []
  return colors.slice(0, 9).map((color, idx) => ({
    color,
    label: `色${idx + 1}`
  }))
})
</script>

<template>
  <!-- 价格涨跌色 -->
  <PreviewFrame title="价格涨跌色" hint="涨跌/持平颜色">
    <div class="scenario-table">
      <div class="scenario-header">
        <span class="col-name">场景</span>
        <span class="col-color">颜色</span>
        <span class="col-examples">示例效果</span>
      </div>
      <div
        v-for="scenario in changeScenarios"
        :key="scenario.key"
        class="scenario-row"
      >
        <span class="col-name">{{ scenario.name }}</span>
        <span class="col-color">
          <span class="color-dot" :style="{ background: scenario.color }"></span>
          <span class="color-value">{{ scenario.color }}</span>
        </span>
        <span class="col-examples">
          <span
            v-for="(example, idx) in scenario.examples"
            :key="idx"
            class="example-badge"
            :style="{ color: scenario.color, background: scenario.color + '1A' }"
          >
            {{ example }}
          </span>
        </span>
      </div>
    </div>
  </PreviewFrame>

  <!-- 图表色板 -->
  <PreviewFrame title="图表色板" hint="9色配色">
    <div class="chart-palette">
      <div
        v-for="item in chartColors"
        :key="item.label"
        class="palette-item"
      >
        <span class="palette-color" :style="{ background: item.color }"></span>
        <span class="palette-label">{{ item.label }}</span>
      </div>
    </div>
    <!-- 图表柱状预览 -->
    <div class="chart-preview">
      <div
        v-for="(item, idx) in chartColors"
        :key="idx"
        class="chart-bar"
        :style="{
          background: item.color,
          height: `${30 + idx * 8}px`
        }"
      ></div>
    </div>
  </PreviewFrame>
</template>

<style scoped>
.scenario-table {
  overflow: hidden;
}

.scenario-header {
  display: flex;
  padding: 10px 12px;
  background: var(--bg-secondary, #FAFAFA);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
  border-bottom: 1px solid var(--border-light, #E5E5E5);
}

.scenario-row {
  display: flex;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-light, #F3F4F6);
  font-size: var(--font-size-xs);
}

.scenario-row:last-child {
  border-bottom: none;
}

.col-name {
  width: 80px;
  flex-shrink: 0;
  color: var(--text-primary, #1A1A1A);
}

.col-color {
  width: 120px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.color-dot {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  flex-shrink: 0;
}

.color-value {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-secondary, #888);
}

.col-examples {
  flex: 1;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.example-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.chart-palette {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.palette-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.palette-color {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.palette-label {
  font-size: 10px;
  color: var(--text-secondary, #888);
}

.chart-preview {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 80px;
  padding: 12px;
  background: var(--bg-card, #FFFFFF);
  border-radius: 6px;
}

.chart-bar {
  flex: 1;
  border-radius: 2px 2px 0 0;
  min-width: 12px;
}
</style>