<template>
  <view class="page">
    <view class="summary">
      <view class="summary-main">
        <text class="summary-title">消息通知</text>
        <text class="summary-desc">{{ unreadCount }} 条未读</text>
      </view>
      <button class="read-all-btn" :disabled="unreadCount <= 0" @click="handleReadAll">全部已读</button>
    </view>

    <view class="tabs">
      <view class="tab" :class="{ active: filter === 'ALL' }" @click="switchFilter('ALL')">全部</view>
      <view class="tab" :class="{ active: filter === 'UNREAD' }" @click="switchFilter('UNREAD')">未读</view>
    </view>

    <scroll-view class="list" scroll-y @scrolltolower="loadMore">
      <view v-if="loading && notifications.length === 0" class="state">加载中...</view>
      <view v-else-if="loadError && notifications.length === 0" class="state">
        <text>{{ loadError }}</text>
        <button class="retry-btn" @click="refresh">重新加载</button>
      </view>
      <view v-else-if="notifications.length === 0" class="state">暂无通知</view>

      <view
        v-for="item in notifications"
        :key="item.id"
        class="notice-card"
        :class="{ unread: item.readStatus === 'UNREAD' }"
        @click="openNotification(item)"
      >
        <view class="notice-dot"></view>
        <view class="notice-content">
          <view class="notice-title-row">
            <text class="notice-title">{{ item.title }}</text>
            <text v-if="item.priority && item.priority !== 'NORMAL'" class="priority">{{ priorityLabel(item.priority) }}</text>
          </view>
          <text class="notice-summary">{{ item.summary || item.content || '暂无内容' }}</text>
          <view class="notice-meta">
            <text>{{ typeLabel(item.type) }}</text>
            <text>{{ formatTime(item.createdTime) }}</text>
          </view>
        </view>
        <text class="archive-btn" @click.stop="handleArchive(item)">归档</text>
      </view>

      <view v-if="loading && notifications.length > 0" class="state small">加载中...</view>
      <view v-else-if="finished && notifications.length > 0" class="state small">没有更多了</view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import {
  archiveNotification,
  getMyNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead
} from '@/api/notifications'
import { useUserStore } from '@/store/useUserStore'
import type { NotificationMessage, PageResponse } from '@/types'
import { getDictValue, loadAllDicts } from '@/composables/useDict'
import { refreshNotificationIndicator } from '@/composables/useNotificationIndicator'

const userStore = useUserStore()
const notifications = ref<NotificationMessage[]>([])
const unreadCount = ref(0)
const filter = ref<'ALL' | 'UNREAD'>('ALL')
const page = ref(0)
const size = 20
const loading = ref(false)
const finished = ref(false)
const loadError = ref('')

const typeLabel = (type: string) => getDictValue('notification_type', type)
const priorityLabel = (priority: string) => getDictValue('notification_priority', priority)

const formatTime = (time?: string) => {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

const parseLinkParams = (item: NotificationMessage): Record<string, string> => {
  if (!item.linkParams) return {}
  try {
    return JSON.parse(item.linkParams)
  } catch {
    return {}
  }
}

const loadUnread = async () => {
  try {
    const response = await getUnreadNotificationCount()
    unreadCount.value = response.data || 0
    await refreshNotificationIndicator(false)
  } catch {
    // 列表与未读数独立降级，避免辅助角标失败阻断通知列表。
  }
}

const loadList = async (reset = false) => {
  if (loading.value || (finished.value && !reset)) return
  if (reset) {
    page.value = 0
    finished.value = false
    notifications.value = []
    loadError.value = ''
  }
  loading.value = true
  try {
    const response = await getMyNotifications({
      page: page.value,
      size,
      readStatus: filter.value === 'UNREAD' ? 'UNREAD' : undefined
    })
    const pageData = response.data as PageResponse<NotificationMessage>
    notifications.value = reset
      ? pageData.content || []
      : [...notifications.value, ...(pageData.content || [])]
    finished.value = pageData.last || (page.value + 1 >= pageData.totalPages)
    page.value += 1
    await loadUnread()
  } catch (error) {
    loadError.value = '通知加载失败，请稍后重试'
    console.error('加载通知列表失败:', error)
    if (reset) {
      notifications.value = []
    }
  } finally {
    loading.value = false
  }
}

const loadMore = () => loadList(false)

const refresh = async () => {
  await Promise.all([loadList(true), loadUnread()])
}

const switchFilter = (next: 'ALL' | 'UNREAD') => {
  if (filter.value === next) return
  filter.value = next
  loadList(true)
}

const handleReadAll = async () => {
  if (unreadCount.value <= 0) return
  await markAllNotificationsRead()
  unreadCount.value = 0
  notifications.value = notifications.value.map(item => ({ ...item, readStatus: 'READ' }))
  if (filter.value === 'UNREAD') {
    await loadList(true)
  }
}

const openNotification = async (item: NotificationMessage) => {
  if (item.readStatus === 'UNREAD') {
    item.readStatus = 'READ'
    unreadCount.value = Math.max(unreadCount.value - 1, 0)
    markNotificationRead(item.messageId).catch(loadUnread)
  }

  if (item.linkType === 'PRICE_QUERY' || item.type === 'PRICE_PUBLISHED') {
    const params = parseLinkParams(item)
    const date = params.date || ''
    if (date) {
      uni.setStorageSync('notificationTargetHistoryDate', date)
    }
    uni.switchTab({ url: '/pages/history/index' })
    return
  }

  uni.showModal({
    title: item.title,
    content: item.content || item.summary || '暂无内容',
    showCancel: false
  })
}

const handleArchive = async (item: NotificationMessage) => {
  await archiveNotification(item.messageId)
  notifications.value = notifications.value.filter(notification => notification.id !== item.id)
  await loadUnread()
}

onMounted(() => {
  userStore.restoreSession()
  if (!userStore.isAuthenticated) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  loadAllDicts().finally(refresh)
})

onShow(loadUnread)

onPullDownRefresh(async () => {
  await refresh()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #F5F5F5;
  display: flex;
  flex-direction: column;
}

.summary {
  min-height: 128rpx;
  background: #FFFFFF;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #E5E7EB;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.summary-title,
.summary-desc {
  display: block;
}

.summary-main {
  flex: 1;
  min-width: 0;
}

.summary-title {
  color: #1A1A1A;
  font-size: 34rpx;
  font-weight: 700;
}

.summary-desc {
  color: #64748B;
  font-size: 24rpx;
  margin-top: 8rpx;
}

.read-all-btn {
  box-sizing: border-box;
  flex-shrink: 0;
  margin: 0 0 0 auto;
  min-width: 176rpx;
  height: 68rpx;
  border-radius: 999rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 26rpx;
  line-height: 68rpx;
}

.read-all-btn[disabled] {
  background: #CBD5E1;
}

.tabs {
  display: flex;
  gap: 16rpx;
  background: #FFFFFF;
  padding: 20rpx 32rpx;
}

.tab {
  min-width: 120rpx;
  min-height: 56rpx;
  border-radius: 999rpx;
  background: #F1F5F9;
  color: #64748B;
  font-size: 26rpx;
  line-height: 56rpx;
  text-align: center;
}

.tab.active {
  background: rgba(13, 110, 110, 0.12);
  color: #0D6E6E;
  font-weight: 700;
}

.list {
  flex: 1;
  padding: 24rpx 32rpx;
  box-sizing: border-box;
}

.notice-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
  padding: 24rpx;
}

.notice-card.unread {
  background: #F0FAFA;
}

.notice-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: transparent;
  margin-top: 12rpx;
}

.notice-card.unread .notice-dot {
  background: #0D6E6E;
}

.notice-content {
  flex: 1;
  min-width: 0;
}

.notice-title-row,
.notice-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.notice-title {
  color: #1A1A1A;
  font-size: 30rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.priority {
  border-radius: 999rpx;
  background: rgba(224, 59, 59, 0.1);
  color: #B42318;
  flex-shrink: 0;
  font-size: 22rpx;
  padding: 4rpx 12rpx;
}

.notice-summary {
  color: #475569;
  display: block;
  font-size: 26rpx;
  line-height: 1.5;
  margin: 12rpx 0;
}

.notice-meta text {
  color: #94A3B8;
  font-size: 22rpx;
}

.archive-btn {
  align-self: flex-start;
  border: 1rpx solid #E2E8F0;
  border-radius: 999rpx;
  color: #64748B;
  flex-shrink: 0;
  font-size: 22rpx;
  padding: 8rpx 16rpx;
}

.state {
  color: #94A3B8;
  font-size: 28rpx;
  padding: 120rpx 0;
  text-align: center;
}

.state text {
  display: block;
}

.retry-btn {
  width: 200rpx;
  height: 64rpx;
  margin: 24rpx auto 0;
  border: 0;
  border-radius: 12rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 24rpx;
  line-height: 64rpx;
}

.state.small {
  font-size: 24rpx;
  padding: 32rpx 0;
}
</style>
