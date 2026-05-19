<template>
  <view class="custom-tabbar">
    <view
      v-for="(item, index) in tabList"
      :key="index"
      class="tab-item"
      :class="{ active: props.current === index }"
      @click="switchTab(index)"
    >
      <text class="tab-icon">{{ item.icon }}</text>
      <text class="tab-text">{{ item.text }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
const props = defineProps<{
  current?: number
}>()

const tabList = [
  { pagePath: '/pages/home/index', text: '首页', icon: '🏠' },
  { pagePath: '/pages/products/list', text: '产品', icon: '📦' },
  { pagePath: '/pages/profile/index', text: '我的', icon: '👤' }
]

const switchTab = (index: number) => {
  if (props.current === index) return
  uni.switchTab({ url: tabList[index].pagePath })
}
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