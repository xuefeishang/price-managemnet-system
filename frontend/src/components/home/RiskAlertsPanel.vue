<script setup lang="ts">
import type { PriceAlert } from '@/api/home'

defineProps<{
  alerts: PriceAlert[]
}>()

const getSeverityClass = (severity: string) => {
  switch (severity) {
    case 'danger': return 'alert-danger'
    case 'warning': return 'alert-warning'
    default: return 'alert-info'
  }
}

const getSeverityIcon = (severity: string) => {
  switch (severity) {
    case 'danger': return '⚠️'
    case 'warning': return '⚡'
    default: return '📊'
  }
}
</script>

<template>
  <div class="alerts-section" v-if="alerts.length > 0">
    <div class="section-header">
      <h3 class="section-title">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
          <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
        </svg>
        价格预警
      </h3>
      <span class="alert-count">{{ alerts.length }} 条</span>
    </div>

    <div class="alerts-grid">
      <div
        v-for="alert in alerts"
        :key="`${alert.productId}-${alert.alertType}`"
        class="alert-card"
        :class="getSeverityClass(alert.severity)"
      >
        <div class="alert-header">
          <span class="alert-icon">{{ getSeverityIcon(alert.severity) }}</span>
          <span class="alert-product">{{ alert.productName }}</span>
        </div>
        <div class="alert-message">{{ alert.alertMessage }}</div>
        <div class="alert-detail" v-if="alert.productSpecs">
          <span class="specs-label">规格:</span>
          <span class="specs-value">{{ alert.productSpecs }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.alerts-section {
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
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.section-title svg {
  color: #FF7A45;
}

.alert-count {
  padding: 4px 12px;
  background: rgba(255, 122, 69, 0.1);
  color: #FF7A45;
  border-radius: var(--radius);
  font-size: var(--font-size-sm);
  font-weight: 500;
}

.alerts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-md);
}

.alert-card {
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
  border: 1px solid transparent;
}

.alert-card.alert-danger {
  background: var(--alert-danger-bg);
  border-color: rgba(245, 63, 63, 0.3);
}

.alert-card.alert-warning {
  background: var(--alert-warning-bg);
  border-color: rgba(255, 122, 69, 0.3);
}

.alert-card.alert-info {
  background: var(--alert-info-bg);
  border-color: rgba(59, 130, 246, 0.3);
}

.alert-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.alert-icon {
  font-size: 16px;
}

.alert-product {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
}

.alert-message {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  margin-bottom: var(--spacing-xs);
}

.alert-detail {
  display: flex;
  gap: var(--spacing-xs);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

.specs-label {
  color: var(--gray-400);
}

.specs-value {
  color: var(--text-secondary);
}

@media (max-width: 1023px) {
  .alerts-section {
    padding: var(--spacing-md);
  }

  .alerts-grid {
    grid-template-columns: 1fr;
    gap: var(--spacing-sm);
  }
}
</style>