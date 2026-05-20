<script setup lang="ts">
import { computed } from 'vue'
import type { StyleConfig } from '@/types/theme'

const props = defineProps<{
  editingConfig: StyleConfig
}>()

// 变化场景定义：展示所有受色彩方案影响的场景
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

// 图表色板（动态数量）
const chartColors = computed(() => {
  const colors = props.editingConfig.chartColors || []
  return colors.slice(0, 9).map((color, idx) => ({
    color,
    label: `色${idx + 1}`
  }))
})

const logoUrlFull = computed(() => {
  if (!props.editingConfig.logoUrl) return ''
  if (props.editingConfig.logoUrl.startsWith('data:')) return props.editingConfig.logoUrl
  if (props.editingConfig.logoUrl.startsWith('http')) return props.editingConfig.logoUrl
  return window.location.origin + props.editingConfig.logoUrl
})
</script>

<template>
  <div class="style-preview-panel">
    <div class="panel-header">
      <h3 class="panel-title">实时预览</h3>
      <span class="panel-hint">切换色彩方案后，以下场景颜色将同步变化</span>
    </div>

    <!-- 变化场景对照表 -->
    <div class="preview-section">
      <span class="section-label">变化场景对照</span>
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
    </div>

    <!-- 图表色板预览 -->
    <div class="preview-section">
      <span class="section-label">图表色板（{{ chartColors.length }}色）</span>
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
    </div>

    <!-- 导航栏预览 -->
    <div class="preview-section">
      <span class="section-label">导航栏</span>
      <div class="nav-preview">
        <div class="nav-brand">
          <img v-if="logoUrlFull" :src="logoUrlFull" alt="Logo" class="nav-logo" />
          <span class="nav-name">{{ editingConfig.systemName }}</span>
        </div>
        <div class="nav-items">
          <span class="nav-item active">首页</span>
          <span class="nav-item">产品</span>
          <span class="nav-item">设置</span>
        </div>
      </div>
    </div>

    <!-- 价格卡片预览 -->
    <div class="preview-section">
      <span class="section-label">价格显示</span>
      <div class="price-preview">
        <div class="price-card">
          <span class="price-name">电铜</span>
          <span class="price-value">¥68,500</span>
          <span class="price-change rise">+2.5%</span>
        </div>
        <div class="price-card">
          <span class="price-name">铁矿石</span>
          <span class="price-value">¥812</span>
          <span class="price-change fall">-1.1%</span>
        </div>
      </div>
    </div>

    <!-- 表格预览 -->
    <div class="preview-section">
      <span class="section-label">表格样式</span>
      <div class="table-preview">
        <div class="table-header">
          <span>产品</span>
          <span>价格</span>
          <span>涨跌</span>
        </div>
        <div class="table-row">
          <span>电铜</span>
          <span class="mono">¥68,500</span>
          <span class="price-rise">+2.5%</span>
        </div>
        <div class="table-row">
          <span>铝锭</span>
          <span class="mono">¥18,200</span>
          <span class="price-fall">-0.8%</span>
        </div>
      </div>
    </div>

    <!-- 图表柱状预览 -->
    <div class="preview-section">
      <span class="section-label">图表效果</span>
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
    </div>
  </div>
</template>

<style scoped>
.style-preview-panel {
  padding: 16px;
  background: var(--bg-secondary, #F5F5F5);
  border-radius: var(--app-card-radius, 12px);
  border: 1px solid var(--border-color, #E5E5E5);
}

.panel-header {
  margin-bottom: 16px;
}

.panel-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
  margin: 0;
}

.panel-hint {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
  margin-top: 4px;
}

.preview-section {
  margin-bottom: 16px;
}

.section-label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
  margin-bottom: 8px;
}

/* 变化场景对照表 */
.scenario-table {
  background: var(--bg-card, #FFFFFF);
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid var(--border-light, #E5E5E5);
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

/* 图表色板 */
.chart-palette {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 12px;
  background: var(--bg-card, #FFFFFF);
  border-radius: 8px;
  border: 1px solid var(--border-light, #E5E5E5);
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

.nav-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--app-nav-bg);
  color: var(--app-nav-text);
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-logo {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.nav-name {
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.nav-items {
  display: flex;
  gap: 12px;
}

.nav-item {
  font-size: var(--font-size-xs);
  padding: 4px 8px;
  border-radius: 4px;
}

.nav-item.active {
  background: rgba(13, 110, 110, 0.15);
}

.price-preview {
  display: flex;
  gap: 8px;
}

.price-card {
  flex: 1;
  padding: 12px;
  background: var(--bg-card, #FFFFFF);
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.price-name {
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
}

.price-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.price-change {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.price-change.rise {
  color: var(--price-rise-color);
}

.price-change.fall {
  color: var(--price-fall-color);
}

.table-preview {
  background: var(--bg-card, #FFFFFF);
  border-radius: 6px;
  overflow: hidden;
}

.table-header {
  display: flex;
  padding: 8px 12px;
  background: var(--bg-secondary, #FAFAFA);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.table-header span {
  flex: 1;
}

.table-row {
  display: flex;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-light, #F3F4F6);
  font-size: var(--font-size-xs);
}

.table-row:last-child {
  border-bottom: none;
}

.table-row span {
  flex: 1;
}

.table-row .mono {
  font-family: var(--font-mono);
}

.price-rise {
  color: var(--price-rise-color);
}

.price-fall {
  color: var(--price-fall-color);
}

.chart-preview {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 100px;
  padding: 12px;
  background: var(--bg-card, #FFFFFF);
  border-radius: 6px;
}

.chart-bar {
  flex: 1;
  border-radius: 2px 2px 0 0;
  min-width: 16px;
}
</style>