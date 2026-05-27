<script setup lang="ts">
import { computed } from 'vue'
import type { HomeSummary } from '@/api/home'

const props = defineProps<{
  summary: HomeSummary
  loading?: boolean
}>()

const summaryCards = computed(() => [
  {
    key: 'products',
    icon: 'products',
    value: props.summary.totalProducts,
    label: '产品总数'
  },
  {
    key: 'updated',
    icon: 'updated',
    value: props.summary.priceUpdatedToday,
    label: '当日更新'
  },
  {
    key: 'coverage',
    icon: 'changes',
    value: props.summary.totalProducts
      ? `${Math.round(((props.summary.priceUpdatedToday || 0) / props.summary.totalProducts) * 100)}%`
      : '0%',
    label: '更新率'
  },
  {
    key: 'categories',
    icon: 'categories',
    value: props.summary.coveredCategoryCount ?? 0,
    label: '覆盖品类'
  }
])
</script>

<template>
  <div class="summary-section">
    <div v-if="loading" class="summary-loading">
      <div v-for="i in 3" :key="i" class="skeleton-stat"></div>
    </div>
    <div v-else class="summary-grid">
      <div v-for="card in summaryCards" :key="card.key" class="stat-card">
        <div class="stat-icon" :class="card.icon">
          <svg v-if="card.icon === 'products'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <svg v-else-if="card.icon === 'updated'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12a9 9 0 1 1-2.64-6.36"/><path d="M21 3v6h-6"/>
          </svg>
          <svg v-else-if="card.icon === 'categories'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
          </svg>
          <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 3v18h18"/><path d="M7 15l4-4 3 3 5-7"/>
          </svg>
        </div>
        <div class="stat-content">
          <span class="stat-label">{{ card.label }}</span>
          <span class="stat-main">
            <span class="stat-value">{{ card.value }}</span>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.summary-section {
  margin-bottom: var(--spacing-md);
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-md);
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 12px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  min-width: 0;
  transition: all var(--transition-fast);
}

.stat-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.stat-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
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

.stat-icon.categories {
  background: rgba(139, 92, 246, 0.1);
  color: #8B5CF6;
}

.stat-icon.changes {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex: 1;
  min-width: 0;
  padding-right: clamp(10px, 1.5vw, 24px);
}

.stat-main {
  display: flex;
  align-items: baseline;
  justify-content: flex-start;
  min-width: 0;
}

.stat-value {
  font-family: var(--font-mono);
  font-size: clamp(1.45rem, 1.4vw, 1.85rem);
  font-weight: 800;
  line-height: 1;
  color: var(--primary-color);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.stat-label {
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skeleton-stat {
  height: 58px;
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
    padding: 10px 12px;
  }

  .stat-icon {
    width: 32px;
    height: 32px;
  }

  .stat-value {
    font-size: var(--font-size-xl);
  }
}

@media (max-width: 560px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
