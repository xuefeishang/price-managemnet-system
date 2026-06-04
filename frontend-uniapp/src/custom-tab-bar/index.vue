<template>
  <view class="custom-tabbar">
    <view
      v-for="item in tabList"
      :key="item.key"
      class="tab-item"
      :class="{ active: currentPath === item.pagePath }"
      @click="switchTab(item.pagePath)"
    >
      <text class="tab-icon">{{ item.icon }}</text>
      <text class="tab-text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/useUserStore'

const userStore = useUserStore()
const currentPath = ref('')

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
}

const switchTab = (pagePath: string) => {
  if (currentPath.value === pagePath) return
  uni.switchTab({ url: pagePath })
}

onMounted(() => {
  userStore.restoreSession()
  refreshCurrentPath()
})

onShow(refreshCurrentPath)
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

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

.tab-icon {
  font-size: 44rpx;
  line-height: 1;
  opacity: 0.6;
  transition: all 0.2s ease;
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
