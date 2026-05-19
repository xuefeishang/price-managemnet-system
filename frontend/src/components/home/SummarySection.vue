<script setup lang="ts">
import type { HomeSummary } from '@/api/home'

defineProps<{
  summary: HomeSummary
  loading?: boolean
}>()

const formatPercent = (val: number) => {
  const sign = val >= 0 ? '+' : ''
  return `${sign}${val.toFixed(1)}%`
}
</script>

<template>
  <div class="summary-section">
    <div v-if="loading" class="summary-loading">
      <div v-for="i in 4" :key="i" class="skeleton-stat"></div>
    </div>
    <div v-else class="summary-grid">
      <div class="stat-card">
        <div class="stat-icon products">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ summary.totalProducts }}</span>
          <span class="stat-label">产品总数</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon updated">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2v4m0 12v4M4.93 4.93l2.83 2.83m8.48 8.48l2.83 2.83M2 12h4m12 0h4M4.93 19.07l2.83-2.83m8.48-8.48l2.83-2.83"/>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value">{{ summary.priceUpdatedToday }}</span>
          <span class="stat-label">今日更新</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-icon avg">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 3v18h18"/><path d="M18 9l-5 5-4-4-3 3"/>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-value" :class="summary.avgPriceChange >= 0 ? 'rise' : 'fall'">
            {{ formatPercent(summary.avgPriceChange) }}
          </span>
          <span class="stat-label">平均变动</span>
        </div>
      </div>

      <div class="stat-card split">
        <div class="split-item rise">
          <span class="split-value">{{ summary.risingCount }}</span>
          <span class="split-label">上涨</span>
        </div>
        <div class="split-divider"></div>
        <div class="split-item fall">
          <span class="split-value">{{ summary.fallingCount }}</span>
          <span class="split-label">下跌</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.summary-section {
  margin-bottom: var(--spacing-xl);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-md);
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  transition: all var(--transition-fast);
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon.products {
  background: rgba(13, 110, 110, 0.1);
  color: var(--primary-color);
}

.stat-icon.updated {
  background: rgba(59, 130, 246, 0.1);
  color: #3B82F6;
}

.stat-icon.avg {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.stat-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-2xl);
  font-weight: 700;
  color: var(--text-primary);
}

.stat-value.rise {
  color: var(--price-rise-color);
}

.stat-value.fall {
  color: var(--price-fall-color);
}

.stat-label {
  font-size: var(--font-size-sm);
  color: var(--text-muted);
}

.stat-card.split {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: var(--spacing-md);
}

.split-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.split-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-xl);
  font-weight: 700;
}

.split-item.rise .split-value {
  color: var(--price-rise-color);
}

.split-item.fall .split-value {
  color: var(--price-fall-color);
}

.split-label {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.split-divider {
  width: 1px;
  height: 40px;
  background: var(--border-color);
}

.skeleton-stat {
  height: 80px;
  background: linear-gradient(90deg, var(--gray-100) 25%, var(--gray-50) 50%, var(--gray-100) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-lg);
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@media (max-width: 1023px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: var(--spacing-sm);
  }

  .stat-card {
    padding: var(--spacing-md);
  }

  .stat-icon {
    width: 40px;
    height: 40px;
  }

  .stat-value {
    font-size: var(--font-size-xl);
  }
}
</style>