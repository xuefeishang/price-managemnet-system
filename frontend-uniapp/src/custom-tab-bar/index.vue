<template>
  <view class="custom-tabbar-shell">
    <view
      class="notification-bubble"
      :class="{ visible: bubbleVisible && currentPath === activeHostPath }"
      @click="goNotifications"
    >
      <view class="bubble-dot" />
      <text>{{ bubbleText }}</text>
      <text class="bubble-arrow">›</text>
    </view>

    <view class="custom-tabbar">
      <view
        v-for="item in tabList"
        :key="item.key"
        class="tab-item"
        :class="{ active: currentPath === item.pagePath }"
        @click="switchTab(item.pagePath)"
      >
        <view class="tab-icon-wrap">
          <text class="tab-icon">{{ item.icon }}</text>
          <text
            v-if="item.key === 'profile'"
            class="tab-badge"
            :class="{ visible: unreadCount > 0 }"
          >
            {{ unreadCount > 99 ? '99+' : unreadCount }}
          </text>
        </view>
        <text class="tab-text">{{ item.text }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/useUserStore'
import { useNotificationIndicator } from '@/composables/useNotificationIndicator'

const userStore = useUserStore()
const currentPath = ref('')
const {
  unreadCount,
  bubbleVisible,
  bubbleText,
  activeHostPath,
  refreshNotificationIndicator,
  startNotificationPolling,
  setActiveNotificationHost
} = useNotificationIndicator()

const baseTabs = [
  { key: 'home', pagePath: '/pages/home/index', text: '首页', icon: '🏠' },
  { key: 'history', pagePath: '/pages/history/index', text: '历史', icon: '📈' },
  { key: 'profile', pagePath: '/pages/profile/index', text: '我的', icon: '👤' }
]

const entryTab = { key: 'entry', pagePath: '/pages/price-maintenance/index', text: '录入', icon: '✍️' }

const tabList = computed(() => {
  const items = [...baseTabs]
  if (userStore.canEdit) {
    items.splice(2, 0, entryTab)
  }
  return items
})

const refreshCurrentPath = () => {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] as any
  currentPath.value = current?.route ? `/${current.route}` : ''
  setActiveNotificationHost(currentPath.value)
}

const switchTab = (pagePath: string) => {
  if (currentPath.value === pagePath) return
  uni.switchTab({ url: pagePath })
}

const goNotifications = () => {
  bubbleVisible.value = false
  uni.navigateTo({ url: '/pages/notifications/index' })
}

onMounted(() => {
  userStore.restoreSession()
  refreshCurrentPath()
  startNotificationPolling()
})

onShow(() => {
  refreshCurrentPath()
  refreshNotificationIndicator(true)
})
</script>

<style scoped>
.custom-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 100rpx;
  background: #FFFFFF;
  display: flex;
  border-top: 1rpx solid #E5E5E5;
  z-index: 999;
  padding-bottom: env(safe-area-inset-bottom);
}

.custom-tabbar-shell {
  position: relative;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

.tab-icon-wrap {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tab-icon {
  font-size: 44rpx;
  line-height: 1;
  opacity: 0.6;
  transition: all 0.2s ease;
}

.tab-badge {
  position: absolute;
  top: -12rpx;
  right: -24rpx;
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 7rpx;
  border: 3rpx solid #FFFFFF;
  border-radius: 999rpx;
  background: #E03B3B;
  color: #FFFFFF;
  font-family: Arial, sans-serif;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 30rpx;
  text-align: center;
  opacity: 0;
  transform: scale(0.7);
  visibility: hidden;
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.tab-badge.visible {
  opacity: 1;
  transform: scale(1);
  visibility: visible;
}

.notification-bubble {
  position: fixed;
  right: 24rpx;
  bottom: calc(118rpx + env(safe-area-inset-bottom));
  z-index: 1001;
  display: flex;
  align-items: center;
  gap: 12rpx;
  max-width: 560rpx;
  padding: 20rpx 24rpx;
  border: 1rpx solid #B7DBDB;
  border-radius: 16rpx;
  background: #FFFFFF;
  box-shadow: 0 12rpx 36rpx rgba(15, 23, 42, 0.16);
  color: #1A1A1A;
  font-size: 26rpx;
  opacity: 0;
  pointer-events: none;
  transform: translateY(20rpx) scale(0.96);
  visibility: hidden;
  transition: opacity 0.28s ease-out, transform 0.28s ease-out;
}

.notification-bubble.visible {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0) scale(1);
  visibility: visible;
}

.bubble-dot {
  flex: 0 0 16rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #E03B3B;
}

.bubble-arrow {
  color: #0D6E6E;
  font-size: 34rpx;
  line-height: 1;
}

.tab-text {
  font-size: 22rpx;
  color: #999999;
}

.tab-item.active .tab-icon {
  opacity: 1;
}

.tab-item.active .tab-text {
  color: #0D6E6E;
  font-weight: 600;
}
</style>
