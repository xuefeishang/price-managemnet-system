<template>
  <view class="products-page">
    <!-- 统计卡片 -->
    <view class="stats-section">
      <view class="stat-card">
        <text class="stat-value">{{ stats.productCount }}</text>
        <text class="stat-label">产品总数</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ stats.categoryCount }}</text>
        <text class="stat-label">分类数量</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ stats.customerCount }}</text>
        <text class="stat-label">客户数量</text>
      </view>
    </view>

    <!-- 最近产品 -->
    <view class="recent-section">
      <text class="section-title">最近产品</text>
      <view class="product-list" v-if="recentProducts.length > 0">
        <view
          class="product-item"
          v-for="product in recentProducts"
          :key="product.id"
          @click="navigateTo(`/pages/products/detail?id=${product.id}`)"
        >
          <text class="product-name">{{ product.name }}</text>
          <text class="product-price">¥{{ product.sellingPrice || '-' }}</text>
        </view>
      </view>
      <view class="empty-state" v-else>
        <text>暂无产品数据</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHomeProducts } from '@/api/products'
import { getCategories } from '@/api/categories'
import { getCustomers } from '@/api/customers'
import type { Product } from '@/types'

const stats = ref({
  productCount: 0,
  categoryCount: 0,
  customerCount: 0
})

const recentProducts = ref<Product[]>([])

const navigateTo = (url: string) => {
  if (url.includes('pages-sub')) {
    uni.navigateTo({ url })
  } else if (url.includes('home') || url.includes('price-maintenance')) {
    uni.switchTab({ url })
  } else {
    uni.navigateTo({ url })
  }
}

const loadData = async () => {
  try {
    // 并行加载所有数据
    const [productsRes, categoriesRes, customersRes] = await Promise.all([
      getHomeProducts(),
      getCategories('ACTIVE'),
      getCustomers('ACTIVE')
    ])

    // 产品数据
    if (productsRes.code === 200 && productsRes.data) {
      recentProducts.value = productsRes.data.content.slice(0, 5)
      stats.value.productCount = productsRes.data.totalElements
    }

    // 分类数量
    if (categoriesRes.code === 200 && categoriesRes.data) {
      stats.value.categoryCount = categoriesRes.data.length
    }

    // 客户数量
    if (customersRes.code === 200 && customersRes.data) {
      stats.value.customerCount = customersRes.data.length
    }
  } catch (error) {
    console.error('加载数据失败:', error)
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.products-page {
  min-height: 100vh;
  background: #F5F5F5;
  padding: 32rpx;
  padding-bottom: 120rpx;
  box-sizing: border-box;
}

.stats-section {
  display: flex;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.stat-card {
  flex: 1;
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 32rpx 24rpx;
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 48rpx;
  font-weight: 600;
  color: #0D6E6E;
  margin-bottom: 8rpx;
}

.stat-label {
  display: block;
  font-size: 24rpx;
  color: #666666;
}

.section-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 24rpx;
}

.recent-section {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 32rpx;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.product-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx;
  background: #F5F5F5;
  border-radius: 12rpx;
}

.product-name {
  font-size: 28rpx;
  color: #1A1A1A;
}

.product-price {
  font-size: 28rpx;
  font-weight: 600;
  color: #0D6E6E;
}

.empty-state {
  text-align: center;
  padding: 48rpx;
}

.empty-state text {
  font-size: 28rpx;
  color: #999999;
}
</style>
