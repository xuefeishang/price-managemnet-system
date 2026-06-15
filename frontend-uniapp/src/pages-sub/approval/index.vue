<template>
  <view class="page">
    <!-- 顶部导航 -->
    <view class="navbar">
      <text class="navbar-title">审批管理</text>
    </view>

    <!-- Tab 切换 -->
    <view class="tabs">
      <view class="tab" :class="{ active: activeTab === 'pending' }" @click="activeTab = 'pending'">
        <text>待审批</text>
        <view class="badge" v-if="pendingCount > 0">{{ pendingCount }}</view>
      </view>
      <view class="tab" :class="{ active: activeTab === 'my' }" @click="activeTab = 'my'">
        <text>我的申请</text>
      </view>
    </view>

    <!-- 列表 -->
    <scroll-view class="content" scroll-y @scrolltolower="loadMore">
      <view v-if="!loading && requests.length > 0" class="list">
        <view v-for="item in requests" :key="item.id" class="card">
          <view class="card-header">
            <view class="card-type" :class="item.businessType">
              <text>{{ item.businessType === 'PRICE' ? '价格' : '产品' }}</text>
            </view>
            <view class="card-status" :class="item.status">
              <text>{{ getStatusLabel(item.status) }}</text>
            </view>
          </view>
          <view class="card-body">
            <text class="card-title">{{ getBusinessTitle(item) }}</text>
            <text class="card-time">{{ item.createdTime }}</text>
          </view>
          <view class="card-actions" v-if="activeTab === 'pending' && item.status === 'PENDING'">
            <button class="btn-reject" @click="handleReject(item)">拒绝</button>
            <button class="btn-approve" @click="handleApprove(item)">通过</button>
          </view>
          <view class="card-actions" v-else-if="activeTab === 'my' && item.status === 'PENDING'">
            <button class="btn-cancel" @click="handleCancel(item)">撤回</button>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else-if="!loading" class="empty">
        <text class="empty-text">{{ activeTab === 'pending' ? '暂无待审批' : '暂无申请记录' }}</text>
      </view>

      <!-- 加载中 -->
      <view v-else class="loading">
        <text>加载中...</text>
      </view>

      <!-- 加载更多 -->
      <view v-if="loadingMore" class="loading-more">
        <text>加载更多...</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { getPendingApprovals, getMyRequests, approveRequest, rejectRequest, cancelRequest } from '@/api/approval'
import type { ApprovalRequest, ApprovalPageResponse } from '@/api/approval'

const userStore = useUserStore()

const activeTab = ref<'pending' | 'my'>('pending')
const requests = ref<ApprovalRequest[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const page = ref(0)
const hasMore = ref(true)
const pendingCount = ref(0)

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    PENDING: '待审批',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    CANCELLED: '已撤回'
  }
  return map[status] || status
}

const getBusinessTitle = (item: ApprovalRequest) => {
  return `${item.businessType === 'PRICE' ? '价格变更' : '产品创建'} #${item.businessId}`
}

const loadData = async (isRefresh = false) => {
  if (loading.value || loadingMore.value) return

  if (isRefresh) {
    page.value = 0
    hasMore.value = true
  }

  if (!hasMore.value) return

  if (isRefresh) {
    loading.value = true
  } else {
    loadingMore.value = true
  }

  try {
    const res = activeTab.value === 'pending'
      ? await getPendingApprovals(page.value, 20)
      : await getMyRequests(page.value, 20)

    if (res.code === 200 && res.data) {
      const data = res.data as ApprovalPageResponse
      if (isRefresh) {
        requests.value = data.content
      } else {
        requests.value = [...requests.value, ...data.content]
      }
      hasMore.value = !data.content.length || data.content.length >= 20
      page.value++

      if (activeTab.value === 'pending') {
        pendingCount.value = data.totalElements
      }
    }
  } catch (error) {
    console.error('加载数据失败:', error)
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

const loadMore = () => {
  if (hasMore.value && !loading.value && !loadingMore.value) {
    loadData()
  }
}

const handleApprove = async (item: ApprovalRequest) => {
  uni.showModal({
    title: '确认通过',
    content: '确定要通过此审批吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await approveRequest(item.id!)
          uni.showToast({ title: '审批通过', icon: 'none' })
          loadData(true)
        } catch (error) {
          console.error('审批失败:', error)
        }
      }
    }
  })
}

const handleReject = async (item: ApprovalRequest) => {
  uni.showModal({
    title: '确认拒绝',
    content: '确定要拒绝此审批吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await rejectRequest(item.id!)
          uni.showToast({ title: '已拒绝', icon: 'none' })
          loadData(true)
        } catch (error) {
          console.error('操作失败:', error)
        }
      }
    }
  })
}

const handleCancel = async (item: ApprovalRequest) => {
  uni.showModal({
    title: '确认撤回',
    content: '确定要撤回此申请吗？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await cancelRequest(item.id!)
          uni.showToast({ title: '已撤回', icon: 'none' })
          loadData(true)
        } catch (error) {
          console.error('操作失败:', error)
        }
      }
    }
  })
}

watch(activeTab, () => {
  loadData(true)
})

onMounted(() => {
  userStore.restoreSession()
  if (!userStore.isAuthenticated) {
    uni.reLaunch({ url: '/pages/login/index' })
    return
  }
  loadData(true)
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #F5F5F5;
  display: flex;
  flex-direction: column;
}

.navbar {
  height: 88rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #E5E5E5;
}

.navbar-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.tabs {
  display: flex;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
}

.tab {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.tab text {
  font-size: 28rpx;
  color: #666666;
}

.tab.active text {
  color: #0D6E6E;
  font-weight: 600;
}

.tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: #0D6E6E;
  border-radius: 2rpx;
}

.badge {
  position: absolute;
  top: 16rpx;
  right: 32rpx;
  min-width: 32rpx;
  height: 32rpx;
  background: #EF4444;
  color: #FFFFFF;
  font-size: 20rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 8rpx;
}

.content {
  flex: 1;
  padding: 24rpx 32rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.card-type {
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
}

.card-type.PRICE {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.card-type.PRODUCT {
  background: rgba(102, 126, 234, 0.1);
  color: #667eea;
}

.card-status {
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
}

.card-status.PENDING {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.card-status.APPROVED {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.card-status.REJECTED {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.card-status.CANCELLED {
  background: rgba(156, 163, 175, 0.1);
  color: #9CA3AF;
}

.card-body {
  margin-bottom: 16rpx;
}

.card-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 8rpx;
}

.card-time {
  display: block;
  font-size: 24rpx;
  color: #888888;
}

.card-actions {
  display: flex;
  gap: 16rpx;
  padding-top: 16rpx;
  border-top: 1px solid #F3F4F6;
}

.btn-approve, .btn-reject, .btn-cancel {
  flex: 1;
  height: 72rpx;
  font-size: 28rpx;
  border-radius: 8rpx;
  border: none;
}

.btn-approve {
  background: #0D6E6E;
  color: #FFFFFF;
}

.btn-reject {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.btn-cancel {
  background: #F5F5F5;
  color: #666666;
}

.empty {
  padding: 160rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 28rpx;
  color: #999999;
}

.loading {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading text {
  font-size: 28rpx;
  color: #666666;
}

.loading-more {
  padding: 32rpx;
  text-align: center;
}

.loading-more text {
  font-size: 26rpx;
  color: #888888;
}
</style>
