<template>
  <view class="detail-page">
    <!-- 加载中 -->
    <view class="loading-state" v-if="loading">
      <text>加载中...</text>
    </view>

    <!-- 产品详情 -->
    <view class="detail-content" v-else-if="product">
      <!-- 价格走势图（置顶） -->
      <view class="info-card">
        <text class="info-title">价格走势</text>
        <price-trend-chart :productId="productId" />
      </view>

      <!-- 基本信息 -->
      <view class="info-card">
        <view class="info-header">
          <text class="info-title">基本信息</text>
          <text class="info-status" :class="product.status">{{ product.status === 'ACTIVE' ? '启用' : '停用' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">产品名称</text>
          <text class="info-value">{{ product.name }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">产品编码</text>
          <text class="info-value">{{ product.code || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">所属分类</text>
          <text class="info-value">{{ product.category?.name || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">规格型号</text>
          <text class="info-value">{{ product.specs || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">计量单位</text>
          <text class="info-value">{{ product.unit || '-' }}</text>
        </view>
      </view>

      <!-- 价格信息 -->
      <view class="info-card">
        <text class="info-title">价格信息</text>
        <view class="price-highlight">
          <view class="price-main-info">
            <text class="price-label">销售价</text>
            <text class="price-main">¥{{ currentPrice?.currentPrice || product?.sellingPrice || '-' }}</text>
          </view>
          <text class="price-date" v-if="currentPrice?.effectiveDate">
            {{ formatDate(currentPrice.effectiveDate) }}
          </text>
        </view>
        <view class="info-row">
          <text class="info-label">预算价</text>
          <text class="info-value">¥{{ currentPrice?.budgetPrice || product?.budgetPrice || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">币种</text>
          <text class="info-value">{{ product?.currency || 'CNY' }}</text>
        </view>
      </view>

      <!-- 其他信息 -->
      <view class="info-card">
        <text class="info-title">其他信息</text>
        <view class="info-row">
          <text class="info-label">产品描述</text>
          <text class="info-value">{{ product.description || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">备注</text>
          <text class="info-value">{{ product.remark || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">创建时间</text>
          <text class="info-value">{{ product.createdTime }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">更新时间</text>
          <text class="info-value">{{ product.updatedTime }}</text>
        </view>
      </view>

      <view class="pc-tip" v-if="userStore.canEdit">
        <text>产品资料维护请前往 PC 端完成。</text>
      </view>
    </view>

    <!-- 错误状态 -->
    <view class="error-state" v-else>
      <text class="error-text">产品不存在或已删除</text>
      <button class="back-btn" @click="goBack">返回列表</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/useUserStore'
import { getProductById, getCurrentPrice } from '@/api/products'
import type { Product, Price } from '@/types'

const userStore = useUserStore()
const product = ref<Product | null>(null)
const currentPrice = ref<Price | null>(null)
const loading = ref(true)
const productId = ref<number>(0)

const loadProduct = async () => {
  if (!productId.value) return

  loading.value = true
  try {
    const [productRes, priceRes] = await Promise.all([
      getProductById(productId.value),
      getCurrentPrice(productId.value)
    ])
    if (productRes.code === 200 && productRes.data) {
      product.value = productRes.data
    }
    if (priceRes.code === 200 && priceRes.data) {
      currentPrice.value = priceRes.data
    }
  } catch (error) {
    console.error('加载产品详情失败:', error)
  } finally {
    loading.value = false
  }
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  return dateStr.split('T')[0]
}

const goBack = () => {
  uni.navigateBack()
}

onLoad((options) => {
  if (options?.id) {
    productId.value = Number(options.id)
    loadProduct()
  }
})

onMounted(() => {
  userStore.restoreSession()
})
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: #F5F5F5;
  padding: 24rpx 32rpx;
  padding-bottom: 120rpx;
  box-sizing: border-box;
}

.loading-state {
  padding: 160rpx 0;
  text-align: center;
}

.loading-state text {
  font-size: 28rpx;
  color: #666666;
}

.info-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.info-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.info-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
  display: block;
  margin-bottom: 24rpx;
}

.info-header .info-title {
  margin-bottom: 0;
}

.info-status {
  font-size: 24rpx;
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
}

.info-status.ACTIVE {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.info-status.INACTIVE {
  background: rgba(153, 153, 153, 0.1);
  color: #999999;
}

.pc-tip {
  border: 1px solid #D7E7E7;
  border-radius: 16rpx;
  background: #F0FAFA;
  color: #0D6E6E;
  font-size: 26rpx;
  line-height: 1.6;
  padding: 24rpx;
  margin-bottom: 24rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1px solid #F5F5F5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 28rpx;
  color: #666666;
}

.info-value {
  font-size: 28rpx;
  color: #1A1A1A;
  text-align: right;
  flex: 1;
  margin-left: 32rpx;
}

.price-highlight {
  background: rgba(13, 110, 110, 0.1);
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price-main-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.price-highlight .price-label {
  font-size: 28rpx;
  color: #666666;
}

.price-main {
  font-size: 40rpx;
  font-weight: 600;
  color: #0D6E6E;
}

.price-date {
  font-size: 24rpx;
  color: #999999;
  background: rgba(255, 255, 255, 0.6);
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
}

.action-bar {
  padding: 32rpx 0;
}

.edit-btn {
  width: 100%;
  height: 88rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 32rpx;
  border-radius: 12rpx;
  border: none;
}

.error-state {
  padding: 160rpx 0;
  text-align: center;
}

.error-text {
  display: block;
  font-size: 28rpx;
  color: #999999;
  margin-bottom: 32rpx;
}

.back-btn {
  width: 240rpx;
  height: 72rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 28rpx;
  border-radius: 12rpx;
  border: none;
}
</style>
