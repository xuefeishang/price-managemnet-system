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
const columns = computed(() => {
  return viewport.value === 'pc'
    ? homeState.layoutConfig.value.cardColumns
    : homeState.layoutConfig.value.cardColumnsMobile
})

const widgetLabelMap: Record<string, string> = {
  summary_stats: '经营摘要',
  core_metrics: '重点关注指标',
  trend_chart: '重点走势',
  product_list: '分类产品列表',
  risk_alerts: '风险预警'
}

const visibleWidgets = computed(() =>
  homeState.widgets.value
    .filter(widget => widget.enabled)
    .map(widget => ({
      ...widget,
      key: widget.key === 'price_alerts' ? 'risk_alerts' : widget.key,
      name: widgetLabelMap[widget.key] || widget.name
    }))
    .filter(widget => {
      if (widget.key === 'trend_chart') return homeState.layoutConfig.value.showTrendChart
      if (widget.key === 'risk_alerts') return homeState.layoutConfig.value.showAlerts
      return ['summary_stats', 'core_metrics', 'trend_chart', 'product_list', 'risk_alerts'].includes(widget.key)
    })
    .sort((a, b) => a.order - b.order)
)

const metricCardCount = computed(() => Math.max(1, Math.min(homeState.layoutConfig.value.featuredProductCount || 4, viewport.value === 'pc' ? 6 : 4)))
const trendCardCount = computed(() => Math.min(metricCardCount.value, 4))

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
              <span v-for="i in 4" :key="i" class="summary-card"></span>
            </div>
          </section>

          <section v-else-if="widget.key === 'core_metrics'" class="preview-section">
            <div class="section-label">{{ widget.name }}</div>
            <div class="metric-grid" :style="{ gridTemplateColumns: `repeat(${viewport === 'mobile' ? 1 : Math.min(columns, 3)}, 1fr)` }">
              <span v-for="i in renderCardCount(widget)" :key="i" class="metric-card">
                <span class="metric-title"></span>
                <span class="origin-chip"></span>
                <span class="metric-price"></span>
                <span class="metric-line"></span>
              </span>
            </div>
          </section>

          <section v-else-if="widget.key === 'trend_chart'" class="preview-section trend-preview">
            <div class="section-label">{{ widget.name }}</div>
            <div class="trend-grid" :style="{ gridTemplateColumns: `repeat(${viewport === 'mobile' ? 1 : 2}, 1fr)` }">
              <span v-for="i in renderCardCount(widget)" :key="i" class="trend-card">
                <span class="trend-title"></span>
                <span class="origin-chip"></span>
                <span class="trend-line"></span>
              </span>
            </div>
          </section>

          <section v-else-if="widget.key === 'product_list'" class="preview-section product-preview">
            <div class="section-label">{{ widget.name }}</div>
            <div class="category-block">
              <span class="category-title"></span>
              <div class="product-grid" :style="{ gridTemplateColumns: `repeat(${viewport === 'mobile' ? 1 : Math.min(columns, 3)}, 1fr)` }">
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
  height: 28px;
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

.trend-card {
  height: 78px;
  padding: 8px;
}

.trend-title {
  width: 54%;
  height: 7px;
  background: #E5E5E5;
}

.trend-line {
  height: 24px;
  margin-top: 12px;
  background:
    linear-gradient(135deg, transparent 30%, #0D6E6E 31%, #0D6E6E 36%, transparent 37%),
    linear-gradient(25deg, transparent 52%, #0D6E6E 53%, #0D6E6E 58%, transparent 59%);
  opacity: 0.55;
}

.category-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.category-title {
  width: 72px;
  height: 12px;
  background: #D7EAEA;
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
