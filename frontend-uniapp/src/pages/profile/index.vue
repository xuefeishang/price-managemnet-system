<template>
  <view class="profile-page">
    <!-- 用户信息 -->
    <view class="user-card">
      <view class="avatar">
        <text class="avatar-text">{{ userStore.user?.nickname?.charAt(0) || 'U' }}</text>
      </view>
      <view class="user-info">
        <text class="nickname">{{ userStore.user?.nickname }}</text>
        <text class="role">{{ roleLabel }}</text>
      </view>
    </view>

    <!-- 功能列表 -->
    <view class="menu-list">
      <view class="menu-item" @click="navigateTo('/pages/products/list')">
        <text class="menu-label">产品列表</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages/price-maintenance/index')" v-if="userStore.canEdit">
        <text class="menu-label">价格维护</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages-sub/basic/categories/index')" v-if="userStore.canEdit">
        <text class="menu-label">分类管理</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages-sub/basic/origins/index')" v-if="userStore.canEdit">
        <text class="menu-label">产地管理</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages-sub/basic/customers/index')" v-if="userStore.canEdit">
        <text class="menu-label">客户管理</text>
        <text class="menu-arrow">></text>
      </view>
      <view class="menu-item" @click="navigateTo('/pages-sub/approval/index')" v-if="userStore.canEdit">
        <text class="menu-label">审批管理</text>
        <text class="menu-arrow">></text>
      </view>
    </view>

    <!-- 退出登录 -->
    <view class="logout-section">
      <button class="logout-btn" @click="handleLogout">退出登录</button>
    </view>

    <!-- 版本信息 -->
    <view class="version-info">
      <text class="version-text">版本 1.0.0</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useUserStore } from '@/store/useUserStore'

const userStore = useUserStore()

const roleLabel = computed(() => {
  const role = userStore.user?.role
  if (role === 'ADMIN') return '管理员'
  if (role === 'EDITOR') return '编辑者'
  return '查看者'
})

const navigateTo = (url: string) => {
  if (url.includes('pages-sub')) {
    uni.navigateTo({ url })
  } else {
    uni.switchTab({ url })
  }
}

const handleLogout = () => {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logoutAction()
        uni.reLaunch({ url: '/pages/login/index' })
      }
    }
  })
}

onMounted(() => {
  userStore.restoreSession()
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: #F5F5F5;
  padding: 32rpx;
  padding-bottom: 120rpx;
  box-sizing: border-box;
}

.user-card {
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  border-radius: 24rpx;
  padding: 48rpx 32rpx;
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-text {
  font-size: 40rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.nickname {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.role {
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.8);
}

.menu-list {
  background: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 32rpx;
}

.menu-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 32rpx;
  border-bottom: 1px solid #F5F5F5;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-label {
  font-size: 30rpx;
  color: #1A1A1A;
}

.menu-arrow {
  font-size: 28rpx;
  color: #999999;
}

.logout-section {
  padding: 32rpx 0;
}

.logout-btn {
  width: 100%;
  height: 88rpx;
  background: #FFFFFF;
  color: #E03B3B;
  font-size: 32rpx;
  border-radius: 12rpx;
  border: 1px solid #E03B3B;
}

.version-info {
  text-align: center;
  padding: 32rpx;
}

.version-text {
  font-size: 24rpx;
  color: #999999;
}
</style>