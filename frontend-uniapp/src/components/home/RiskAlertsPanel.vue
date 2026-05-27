<script setup lang="ts">
import type { PriceAlert } from '@/api/home'

defineProps<{
  alerts: PriceAlert[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'click', alert: PriceAlert): void
}>()

const getSeverityStyle = (severity: string) => {
  switch (severity) {
    case 'danger':
      return { bg: 'rgba(224, 59, 59, 0.1)', color: '#E03B3B', text: '高风险' }
    case 'warning':
      return { bg: 'rgba(245, 158, 11, 0.1)', color: '#F59E0B', text: '中风险' }
    default:
      return { bg: 'rgba(59, 130, 246, 0.1)', color: '#3B82F6', text: '低风险' }
  }
}

const formatChange = (change: number) => {
  if (change > 0) return `+${change.toFixed(2)}%`
  if (change < 0) return `${change.toFixed(2)}%`
  return '0%'
}
</script>

<template>
  <view class="alerts-section">
    <view class="section-header">
      <text class="section-title">⚠️ 风险预警</text>
      <text class="alert-count" v-if="alerts.length > 0">{{ alerts.length }}</text>
    </view>

    <view v-if="loading" class="alerts-loading">
      <view v-for="i in 2" :key="i" class="skeleton-alert"></view>
    </view>

    <view v-else-if="alerts.length === 0" class="alerts-empty">
      <text class="empty-icon">✅</text>
      <text class="empty-text">暂无价格预警</text>
    </view>

    <view v-else class="alerts-list">
      <view
        v-for="alert in alerts"
        :key="alert.productId"
        class="alert-item"
        @click="emit('click', alert)"
      >
        <view class="alert-main">
          <text class="alert-name">{{ alert.productName }}</text>
          <text class="alert-specs" v-if="alert.productSpecs">{{ alert.productSpecs }}</text>
        </view>
        <view class="alert-meta">
          <text
            class="alert-change"
            :style="{ color: alert.changePercent > 0 ? '#E03B3B' : '#52C41A' }"
          >
            {{ formatChange(alert.changePercent) }}
          </text>
          <text
            class="alert-severity"
            :style="{
              background: getSeverityStyle(alert.severity).bg,
              color: getSeverityStyle(alert.severity).color
            }"
          >
            {{ getSeverityStyle(alert.severity).text }}
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.alerts-section {
  padding: 24rpx;
  background: #FFFFFF;
  margin-bottom: 16rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.alert-count {
  padding: 4rpx 16rpx;
  background: #E03B3B;
  color: #FFFFFF;
  font-size: 24rpx;
  border-radius: 20rpx;
}

.alerts-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.alert-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx;
  background: #FAFAFA;
  border-radius: 12rpx;
  border-left: 6rpx solid #E03B3B;
}

.alert-main {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  flex: 1;
  min-width: 0;
}

.alert-name {
  font-size: 28rpx;
  font-weight: 500;
  color: #1A1A1A;
}

.alert-specs {
  font-size: 22rpx;
  color: #999999;
}

.alert-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
  flex-shrink: 0;
}

.alert-change {
  font-size: 32rpx;
  font-weight: 600;
}

.alert-severity {
  padding: 4rpx 12rpx;
  font-size: 20rpx;
  border-radius: 6rpx;
}

.alerts-loading {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.skeleton-alert {
  height: 80rpx;
  background: linear-gradient(90deg, #E5E5E5 25%, #F5F5F5 50%, #E5E5E5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 12rpx;
}

.alerts-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx;
  gap: 16rpx;
}

.empty-icon {
  font-size: 48rpx;
}

.empty-text {
  font-size: 26rpx;
  color: #999999;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
