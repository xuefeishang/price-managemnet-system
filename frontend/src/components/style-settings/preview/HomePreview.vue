<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import { useHomePreviewState } from '@/composables/useHomePreviewState'
import type { HomeWidget } from '@/composables/useHomePreviewState'

// 使用共享状态
const homeState = useHomePreviewState()

// 视口切换（本地 UI 状态）
const viewport = ref<'pc' | 'mobile'>('pc')

// 列数
const columns = computed(() => homeState.layoutConfig.value.cardColumns)

const widgetLabelMap: Record<string, string> = {
  summary_stats: '经营摘要',
  core_metrics: '重点关注指标',
  trend_chart: '重点走势',
  product_list: '产品列表',
  risk_alerts: '风险预警'
}

const summaryLabels = ['产品总数', '当日更新', '更新率', '覆盖品类']

const visibleWidgets = computed(() =>
  homeState.widgets.value
    .filter(widget => widget.enabled)
    .map(widget => ({
      ...widget,
      key: widget.key === 'price_alerts' ? 'risk_alerts' : widget.key,
      name: widgetLabelMap[widget.key] || widget.name
    }))
    .filter(widget => ['summary_stats', 'core_metrics', 'trend_chart', 'product_list', 'risk_alerts'].includes(widget.key))
    .sort((a, b) => a.order - b.order)
)

const hasVisibleCoreMetrics = computed(() =>
  visibleWidgets.value.some(widget => widget.key === 'core_metrics')
)

const hasVisibleTrendChart = computed(() =>
  visibleWidgets.value.some(widget => widget.key === 'trend_chart')
)

const metricCardCount = computed(() => Math.max(1, Math.min(homeState.layoutConfig.value.featuredProductCount || 4, 4)))
const trendCardCount = computed(() => Math.min(metricCardCount.value, 4))
const productListPreviewMode = computed<'table' | 'cards'>(() => {
  if (viewport.value === 'mobile') return 'cards'
  if (homeState.layoutConfig.value.productListMode === 'cards') return 'cards'
  if (homeState.layoutConfig.value.productListMode === 'auto') return 'table'
  return 'table'
})

const renderCardCount = (widget: HomeWidget & { key: string }) => {
  if (widget.key === 'core_metrics') return metricCardCount.value
  if (widget.key === 'trend_chart') return trendCardCount.value
  return 0
}

onMounted(() => {
  homeState.loadConfig()
})
</script>

<template>
  <PreviewFrame title="首页布局缩略图" hint="组件显隐 + 排序">
    <div class="viewport-tabs">
      <span class="tab" :class="{ active: viewport === 'pc' }" @click="viewport = 'pc'">PC</span>
      <span class="tab" :class="{ active: viewport === 'mobile' }" @click="viewport = 'mobile'">移动</span>
    </div>

    <div class="home-viewport" :class="{ mobile: viewport === 'mobile' }">
      <div class="preview-header">
        <span class="date-pill"></span>
        <span class="page-title-line"></span>
        <span class="action-pill"></span>
      </div>

      <div class="preview-body">
        <template v-for="widget in visibleWidgets" :key="widget.key">
          <section v-if="widget.key === 'summary_stats'" class="preview-section summary-preview">
            <div class="section-label">{{ widget.name }}</div>
            <div class="summary-grid">
              <span v-for="label in summaryLabels" :key="label" class="summary-card">
                <span class="summary-icon"></span>
                <span class="summary-copy">
                  <span class="summary-label-line"></span>
                  <span class="summary-value-line"></span>
                  <span class="summary-helper-line"></span>
                </span>
              </span>
            </div>
          </section>

          <section v-else-if="widget.key === 'core_metrics'" class="preview-section core-preview">
            <div class="section-label">重点产品价格{{ hasVisibleTrendChart ? ' / 主价格曲线' : '' }}</div>
            <div class="core-preview-grid" :class="{ mobile: viewport === 'mobile' }">
              <div class="metric-grid" :style="{ gridTemplateColumns: `repeat(${viewport === 'mobile' ? 1 : 2}, 1fr)` }">
                <span v-for="i in renderCardCount(widget)" :key="i" class="metric-card">
                  <span class="metric-title"></span>
                  <span class="origin-chip"></span>
                  <span class="metric-price"></span>
                  <span class="metric-line"></span>
                </span>
              </div>
              <div v-if="hasVisibleTrendChart && viewport === 'pc'" class="main-curve-preview compact">
                <span class="curve-preview-head">
                  <span class="trend-title"></span>
                  <span class="range-dots"></span>
                </span>
                <span class="curve-preview-price"></span>
                <span class="trend-line"></span>
                <span class="curve-preview-footer"></span>
              </div>
            </div>
          </section>

          <section v-else-if="widget.key === 'trend_chart' && !hasVisibleCoreMetrics" class="preview-section trend-preview">
            <div class="section-label">{{ widget.name }}</div>
            <div class="main-curve-preview">
              <span class="curve-preview-head">
                <span class="trend-title"></span>
                <span class="range-dots"></span>
              </span>
              <span class="curve-preview-price"></span>
              <span class="trend-line"></span>
              <span class="curve-preview-footer"></span>
            </div>
          </section>

          <section v-else-if="widget.key === 'product_list'" class="preview-section product-preview">
            <div class="section-label">{{ widget.name }}</div>
            <div class="category-block">
              <div class="list-toolbar">
                <span class="category-title"></span>
                <span class="page-size-pill">{{ homeState.layoutConfig.value.productTablePageSize }}/页</span>
              </div>
              <div v-if="productListPreviewMode === 'table'" class="product-table-preview">
                <span v-for="i in 5" :key="i" class="table-row">
                  <span class="table-name"></span>
                  <span class="table-price"></span>
                  <span class="table-line"></span>
                </span>
              </div>
              <div v-else class="product-grid" :style="{ gridTemplateColumns: `repeat(${viewport === 'mobile' ? 1 : Math.min(columns, 3)}, 1fr)` }">
                <span v-for="i in 6" :key="i" class="product-card">
                  <span class="product-title-line"></span>
                  <span class="origin-chip"></span>
                </span>
              </div>
            </div>
          </section>

          <section v-else-if="widget.key === 'risk_alerts'" class="preview-section alert-preview">
            <div class="section-label">{{ widget.name }}</div>
            <div class="alert-row">
              <span v-for="i in 2" :key="i" class="alert-card"></span>
            </div>
          </section>
        </template>
      </div>
    </div>

    <div class="home-info">
      <span>已启用 {{ visibleWidgets.length }} 个组件</span>
      <span v-if="homeState.layoutConfig.value.featuredProductCount">，重点产品 {{ homeState.layoutConfig.value.featuredProductCount }} 个</span>
      <span>，列表{{ productListPreviewMode === 'table' ? '表格' : '卡片' }}</span>
    </div>
  </PreviewFrame>
</template>

<style scoped>
.viewport-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.tab {
  padding: 4px 12px;
  font-size: 10px;
  border-radius: 4px;
  background: var(--bg-secondary, #FAFAFA);
  color: var(--text-secondary, #888);
  cursor: pointer;
}

.tab.active {
  background: #0D6E6E;
  color: #FFFFFF;
}

.home-viewport {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 10px;
  background: var(--bg-secondary, #FAFAFA);
  border: 1px solid var(--border-light, #E5E5E5);
  border-radius: 8px;
  max-height: 520px;
  overflow: hidden;
}

.home-viewport.mobile {
  max-width: 190px;
  margin: 0 auto;
}

.preview-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: var(--bg-card, #FFFFFF);
  border-radius: 6px;
}

.date-pill,
.action-pill,
.page-title-line {
  display: block;
  height: 10px;
  border-radius: 999px;
  background: var(--border-light, #E5E5E5);
}

.date-pill { width: 36px; }
.page-title-line { width: 58px; background: #D7EAEA; }
.action-pill { width: 28px; margin-left: auto; background: #0D6E6E; opacity: 0.7; }

.preview-body,
.preview-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--text-secondary, #666);
}

.summary-grid,
.metric-grid,
.trend-grid,
.product-grid,
.alert-row {
  display: grid;
  gap: 6px;
}

.core-preview-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.86fr) minmax(0, 1.14fr);
  gap: 8px;
}

.core-preview-grid.mobile {
  display: block;
}

.summary-grid {
  grid-template-columns: repeat(4, 1fr);
}

.home-viewport.mobile .summary-grid {
  grid-template-columns: repeat(2, 1fr);
}

.summary-card,
.metric-card,
.trend-card,
.product-card,
.alert-card {
  display: block;
  border: 1px solid var(--border-light, #E5E5E5);
  border-radius: 5px;
  background: var(--bg-card, #FFFFFF);
}

.summary-card {
  height: 46px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px;
}

.summary-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border-radius: 5px;
  background: #D7EAEA;
}

.summary-copy {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.summary-label-line,
.summary-value-line,
.summary-helper-line {
  display: block;
  border-radius: 999px;
}

.summary-label-line {
  width: 52%;
  height: 5px;
  background: #E5E5E5;
}

.summary-value-line {
  width: 38%;
  height: 8px;
  background: #0D6E6E;
  opacity: 0.8;
}

.summary-helper-line {
  width: 70%;
  height: 5px;
  background: #EEF2F6;
}

.metric-card {
  height: 54px;
  padding: 6px;
}

.metric-title,
.metric-price,
.metric-line,
.trend-title,
.trend-line,
.category-title,
.origin-chip,
.product-title-line {
  display: block;
  border-radius: 999px;
}

.metric-title {
  width: 52%;
  height: 7px;
  background: #D7EAEA;
}

.metric-price {
  width: 42%;
  height: 10px;
  margin-top: 7px;
  background: #0D6E6E;
  opacity: 0.8;
}

.metric-line {
  width: 70%;
  height: 8px;
  margin-top: 8px;
  background: linear-gradient(90deg, transparent, #0D6E6E, transparent);
  opacity: 0.45;
}

.trend-card,
.main-curve-preview {
  height: 112px;
  padding: 8px;
}

.main-curve-preview.compact {
  height: 100%;
  min-height: 112px;
}

.main-curve-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid var(--border-light, #E5E5E5);
  border-radius: 5px;
  background: var(--bg-card, #FFFFFF);
}

.curve-preview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.trend-title {
  width: 54%;
  height: 7px;
  background: #E5E5E5;
}

.range-dots {
  width: 44px;
  height: 8px;
  border-radius: 999px;
  background: #D7EAEA;
}

.curve-preview-price {
  width: 34%;
  height: 11px;
  border-radius: 999px;
  background: #0D6E6E;
  opacity: 0.8;
}

.trend-line {
  height: 42px;
  margin-top: 0;
  background:
    linear-gradient(135deg, transparent 30%, #0D6E6E 31%, #0D6E6E 36%, transparent 37%),
    linear-gradient(25deg, transparent 52%, #0D6E6E 53%, #0D6E6E 58%, transparent 59%);
  opacity: 0.55;
}

.curve-preview-footer {
  width: 88%;
  height: 7px;
  border-radius: 999px;
  background: #EEF2F6;
}

.category-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
}

.category-title {
  width: 72px;
  height: 12px;
  background: #D7EAEA;
}

.page-size-pill {
  padding: 2px 6px;
  border-radius: 999px;
  background: #F0F8F8;
  color: #0D6E6E;
  font-size: 9px;
  white-space: nowrap;
}

.product-table-preview {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-light, #E5E5E5);
  border-radius: 5px;
  overflow: hidden;
}

.table-row {
  display: grid;
  grid-template-columns: 1.2fr 0.7fr 0.8fr;
  gap: 6px;
  align-items: center;
  height: 22px;
  padding: 0 6px;
  background: var(--bg-card, #FFFFFF);
  border-bottom: 1px solid #F0F0F0;
}

.table-row:last-child {
  border-bottom: none;
}

.table-name,
.table-price,
.table-line {
  height: 6px;
  border-radius: 999px;
  background: #E5E5E5;
}

.table-price {
  background: #0D6E6E;
  opacity: 0.7;
}

.table-line {
  background: linear-gradient(90deg, transparent, #0D6E6E, transparent);
  opacity: 0.45;
}

.product-card {
  height: 48px;
  padding: 7px;
}

.product-title-line {
  width: 64%;
  height: 7px;
  background: #E5E5E5;
}

.origin-chip {
  width: 42px;
  height: 9px;
  margin-top: 6px;
  border: 1px solid #D7EAEA;
  background: #F0F8F8;
}

.alert-row {
  grid-template-columns: repeat(2, 1fr);
}

.home-viewport.mobile .alert-row {
  grid-template-columns: 1fr;
}

.alert-card {
  height: 32px;
  background: #FFF7ED;
  border-color: #FED7AA;
}

.home-info {
  margin-top: 8px;
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
  text-align: center;
}
</style>
