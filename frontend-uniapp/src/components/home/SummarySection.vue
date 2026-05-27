<script setup lang="ts">
import { computed } from 'vue'
import type { HomeSummary } from '@/api/home'

const props = defineProps<{
  summary: HomeSummary | null
  loading?: boolean
}>()

const summaryCards = computed(() => {
  if (!props.summary) return []
  return [
    {
      key: 'products',
      icon: 'cube',
      value: props.summary.totalProducts,
      label: '产品总数',
      bgColor: 'rgba(13, 110, 110, 0.1)',
      iconColor: '#0D6E6E'
    },
    {
      key: 'updated',
      icon: 'refresh',
      value: props.summary.priceUpdatedToday,
      label: '今日更新',
      bgColor: 'rgba(59, 130, 246, 0.1)',
      iconColor: '#3B82F6'
    },
    {
      key: 'categories',
      icon: 'grid',
      value: props.summary.coveredCategoryCount ?? 0,
      label: '覆盖品类',
      bgColor: 'rgba(139, 92, 246, 0.1)',
      iconColor: '#8B5CF6'
    },
    {
      key: 'changes',
      icon: 'trending',
      value: props.summary.changedProductCount ?? 0,
      label: '价格异动',
      bgColor: 'rgba(245, 158, 11, 0.1)',
      iconColor: '#F59E0B'
    }
  ]
})
</script>

<template>
  <view class="summary-section">
    <view v-if="loading" class="summary-loading">
      <view v-for="i in 4" :key="i" class="skeleton-stat"></view>
    </view>
    <view v-else class="summary-grid">
      <view v-for="card in summaryCards" :key="card.key" class="stat-card">
        <view class="stat-icon" :style="{ background: card.bgColor }">
          <text class="icon-text" :style="{ color: card.iconColor }">
            {{ card.icon === 'cube' ? '📦' : card.icon === 'refresh' ? '🔄' : card.icon === 'grid' ? '📊' : '📈' }}
          </text>
        </view>
        <view class="stat-content">
          <text class="stat-label">{{ card.label }}</text>
          <text class="stat-value">{{ card.value }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.summary-section {
  padding: 24rpx;
  background: #FFFFFF;
  margin-bottom: 16rpx;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
}

.stat-card {
  background: #FAFAFA;
  border-radius: 16rpx;
  padding: 20rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.stat-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-text {
  font-size: 32rpx;
}

.stat-content {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 22rpx;
  color: #999999;
}

.stat-value {
  font-size: 40rpx;
  font-weight: 700;
  color: #1A1A1A;
  font-variant-numeric: tabular-nums;
}

.summary-loading {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
}

.skeleton-stat {
  height: 104rpx;
  background: linear-gradient(90deg, #E5E5E5 25%, #F5F5F5 50%, #E5E5E5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 16rpx;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
