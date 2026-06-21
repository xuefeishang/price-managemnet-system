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

    <view class="pc-tip" v-if="userStore.canEdit">
      <text class="pc-tip-title">完整运维请前往 PC 端</text>
      <text class="pc-tip-desc">产品、分类、产地、客户、审批配置等复杂维护不在小程序端提供。</text>
    </view>

    <view class="menu-section">
      <view class="menu-item" @click="goNotifications">
        <view>
          <text class="menu-title">消息通知</text>
          <text class="menu-desc">查看站内消息和业务提醒</text>
        </view>
        <view class="menu-right">
          <text v-if="unreadCount > 0" class="badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
      <button class="menu-item share-item" open-type="share">
        <view>
          <text class="menu-title">分享小程序</text>
          <text class="menu-desc">转发给同事或微信群</text>
        </view>
        <text class="arrow">›</text>
      </button>
      <view class="menu-item subscribe-item">
        <view class="subscribe-main">
          <text class="menu-title">小程序消息订阅</text>
          <text class="menu-desc">{{ subscribeDescription }}</text>
          <view v-if="miniSubscription.templates.length > 0" class="subscribe-tags">
            <text
              v-for="template in miniSubscription.templates"
              :key="template.notificationType + template.templateId"
              class="subscribe-tag"
            >
              {{ subscriptionTemplateLabel(template.notificationType) }} · {{ subscriptionStatusLabel(template.status) }} · {{ template.availableCount }}
            </text>
          </view>
        </view>
        <button class="subscribe-btn" :disabled="!canRequestSubscribe" @click="requestMiniProgramSubscribe">
          订阅
        </button>
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
import { getDictValue, loadAllDicts } from '@/composables/useDict'
import { useTheme } from '@/composables/useTheme'
import { useNotificationIndicator } from '@/composables/useNotificationIndicator'
import { useMiniProgramSubscription } from '@/composables/useMiniProgramSubscription'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { getMiniappEntryShareMessage } from '@/utils/share'

const userStore = useUserStore()
const { themeConfig, loadThemeConfig } = useTheme()
const { unreadCount, refreshNotificationIndicator } = useNotificationIndicator()
const {
  miniSubscription,
  canRequestSubscribe,
  subscribeDescription,
  loadMiniSubscriptions,
  requestMiniProgramSubscribe,
  subscriptionTemplateLabel,
  subscriptionStatusLabel
} = useMiniProgramSubscription()

const roleLabel = computed(() => {
  const role = userStore.user?.role
  return role ? getDictValue('user_role', role) : ''
})

const loadUnreadCount = async () => {
  await refreshNotificationIndicator(false)
}

const goNotifications = () => {
  uni.navigateTo({ url: '/pages/notifications/index' })
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

onShareAppMessage(() => getMiniappEntryShareMessage(themeConfig.value.systemName))

onMounted(async () => {
  userStore.restoreSession()
  await Promise.all([loadThemeConfig(), loadAllDicts()])
  await loadUnreadCount()
  await loadMiniSubscriptions()
})

onShow(async () => {
  await loadUnreadCount()
  await loadMiniSubscriptions()
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

.pc-tip {
  border: 1px solid #D7E7E7;
  border-radius: 16rpx;
  background: #F0FAFA;
  padding: 24rpx;
  margin-bottom: 32rpx;
}

.pc-tip-title {
  display: block;
  color: #0D6E6E;
  font-size: 28rpx;
  font-weight: 600;
  margin-bottom: 8rpx;
}

.pc-tip-desc {
  display: block;
  color: #64748B;
  font-size: 24rpx;
  line-height: 1.6;
}

.menu-section {
  background: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 32rpx;
  overflow: hidden;
}

.menu-item {
  width: 100%;
  min-height: 104rpx;
  padding: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  margin: 0;
  border: 0;
  border-radius: 0;
  background: #FFFFFF;
  box-sizing: border-box;
  line-height: 1.4;
  text-align: left;
}

.menu-item::after {
  border: 0;
}

.share-item {
  border-top: 1px solid #EEF2F7;
}

.menu-title,
.menu-desc {
  display: block;
}

.menu-title {
  color: #1A1A1A;
  font-size: 30rpx;
  font-weight: 600;
}

.menu-desc {
  color: #64748B;
  font-size: 24rpx;
  margin-top: 6rpx;
}

.menu-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.subscribe-item {
  align-items: flex-start;
  border-top: 1px solid #EEF2F7;
}

.subscribe-main {
  flex: 1;
  min-width: 0;
}

.subscribe-tags {
  margin-top: 16rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.subscribe-tag {
  max-width: 100%;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: #F1F5F9;
  color: #475569;
  font-size: 22rpx;
  line-height: 1.3;
}

.subscribe-btn {
  width: 128rpx;
  height: 64rpx;
  border-radius: 12rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 26rpx;
  line-height: 64rpx;
  padding: 0;
  margin: 0;
}

.subscribe-btn[disabled] {
  background: #CBD5E1;
  color: #FFFFFF;
}

.badge {
  min-width: 36rpx;
  height: 36rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background: #E03B3B;
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 36rpx;
  text-align: center;
}

.arrow {
  color: #94A3B8;
  font-size: 42rpx;
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
