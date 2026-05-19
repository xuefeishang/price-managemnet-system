<template>
  <view class="home-page">
    <!-- 顶部日期选择 -->
    <view class="header">
      <view class="date-section">
        <picker mode="date" :value="selectedDate" @change="onDateChange">
          <view class="date-picker">
            <text class="date-text">{{ selectedDate }}</text>
            <text class="date-icon">▼</text>
          </view>
        </picker>
      </view>
      <view class="header-actions">
        <view class="refresh-btn" @click="onRefresh" :class="{ loading: isLoading }">
          <text class="refresh-icon" :class="{ spinning: isLoading }">↻</text>
        </view>
      </view>
    </view>

    <!-- 分类筛选 -->
    <view class="category-filter" v-if="categories.length > 0">
      <scroll-view
        class="category-scroll"
        scroll-x
        enable-flex
        :show-scrollbar="false"
        @scroll="onCategoryScroll"
      >
        <view
          class="category-tag"
          :class="{ active: selectedCategoryId === null }"
          @click="selectCategory(null)"
        >
          <text>全部</text>
        </view>
        <view
          class="category-tag"
          v-for="cat in categories"
          :key="cat.id"
          :class="{ active: selectedCategoryId === cat.id }"
          @click="selectCategory(cat.id)"
        >
          <text>{{ cat.name }}</text>
        </view>
      </scroll-view>
      <view class="scroll-indicator" v-if="showScrollIndicator">
        <text class="indicator-arrow">›</text>
      </view>
    </view>

    <!-- 错误提示 -->
    <view class="error-msg" v-if="errorMsg">
      <text>{{ errorMsg }}</text>
      <text class="retry-btn" @click="onRefresh">重试</text>
    </view>

    <!-- 加载中 -->
    <view class="loading-container" v-if="isLoading && products.length === 0">
      <view class="skeleton-card" v-for="i in 4" :key="i"></view>
    </view>

    <template v-else>
      <!-- 重点关注指标 -->
      <view class="featured-section" v-if="homeProducts.length > 0">
        <view class="section-header">
          <text class="section-title">重点关注指标</text>
        </view>
        <scroll-view class="featured-scroll" scroll-x enable-flex>
          <view
            class="featured-card"
            v-for="product in homeProducts"
            :key="product.id"
            @click="goToDetail(product.id)"
          >
            <view class="card-left">
              <text class="product-name">{{ product.name }}</text>
              <text class="product-specs" v-if="product.specs">{{ product.specs }}</text>
            </view>
            <view class="card-right">
              <view class="price-area">
                <text class="price-large" v-if="getPriceInfo(product.id)?.price">
                  ¥{{ getPriceInfo(product.id)?.price }}
                </text>
                <text class="price-large empty" v-else>--</text>
              </view>
              <view class="trend-area">
                <view
                  class="trend-badge"
                  :class="getPriceInfo(product.id)?.trend || 'flat'"
                  v-if="getPriceInfo(product.id)?.diff"
                >
                  <text>{{ getTrendIcon(getPriceInfo(product.id)?.trend) }} {{ getPriceInfo(product.id)?.diff }}</text>
                </view>
                <text class="trend-flat" v-else>--</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- 产品列表 -->
      <view class="product-section">
        <view class="section-header">
          <text class="section-title">产品列表</text>
          <view class="product-count">
            <text>{{ filteredProducts.length }} 个产品</text>
          </view>
        </view>

        <!-- 搜索框 -->
        <view class="search-bar">
          <text class="search-icon">🔍</text>
          <input
            v-model="searchQuery"
            class="search-input"
            type="text"
            placeholder="搜索产品..."
            @input="onSearchInput"
          />
          <text class="clear-btn" v-if="searchQuery" @click="searchQuery = ''">✕</text>
        </view>

        <!-- 空状态 -->
        <view class="empty-state" v-if="filteredProducts.length === 0">
          <text class="empty-icon">📦</text>
          <text class="empty-text">{{ searchQuery ? '未找到匹配的产品' : '暂无产品数据' }}</text>
        </view>

        <!-- 产品列表 -->
        <view class="product-grid" v-else>
          <view
            class="product-card"
            v-for="product in filteredProducts"
            :key="product.id"
            @click="goToDetail(product.id)"
          >
            <view class="card-left-info">
              <text class="product-name">{{ product.name }}</text>
              <text class="product-specs" v-if="product.specs">{{ product.specs }}</text>
            </view>
            <view class="card-right-info">
              <view class="price-top">
                <text class="price-value" v-if="getPriceInfo(product.id)?.price">
                  ¥{{ getPriceInfo(product.id)?.price }}
                </text>
                <text class="price-value empty" v-else>--</text>
              </view>
              <view class="trend-bottom">
                <view
                  class="trend-badge small"
                  :class="getPriceInfo(product.id)?.trend || 'flat'"
                  v-if="getPriceInfo(product.id)?.diff"
                >
                  <text>{{ getTrendIcon(getPriceInfo(product.id)?.trend) }} {{ getPriceInfo(product.id)?.diff }}</text>
                </view>
                <text class="trend-flat" v-else>--</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>

    <!-- 价格维护入口 -->
    <view class="fab-btn" v-if="userStore.canEdit" @click="goToPriceMaintenance">
      <text>价格维护</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { getProducts, getPricesByDateWithStats } from '@/api/products'
import { getCategories } from '@/api/categories'
import type { Product, ProductCategory } from '@/types'
import type { PriceWithStats } from '@/api/products'

const userStore = useUserStore()

// 状态
const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const selectedCategoryId = ref<number | null>(null)
const priceDataMap = ref<Map<number, PriceWithStats>>(new Map())
const isLoading = ref(false)
const errorMsg = ref('')
const searchQuery = ref('')
const selectedDate = ref(getYesterday())
const showScrollIndicator = ref(true)

// 计算属性
const homeProducts = computed(() => {
  let result = products.value.filter(p => p.showOnHome && p.status === 'ACTIVE')
  if (selectedCategoryId.value !== null) {
    result = result.filter(p => p.category?.id === selectedCategoryId.value)
  }
  return result
})

const filteredProducts = computed(() => {
  let active = products.value.filter(p => p.status === 'ACTIVE')

  // 按分类筛选
  if (selectedCategoryId.value !== null) {
    active = active.filter(p => p.category?.id === selectedCategoryId.value)
  }

  // 按搜索词筛选
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    active = active.filter(p => p.name.toLowerCase().includes(q))
  }

  return active
})

// 工具函数
function getYesterday(): string {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}

function getDateOffset(offset: number): string {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  return date.toISOString().split('T')[0]
}

function formatDateShort(dateStr: string): string {
  const date = new Date(dateStr + 'T00:00:00')
  return `${date.getMonth() + 1}/${date.getDate()}`
}

function formatDateDisplay(dateStr: string): string {
  const date = new Date(dateStr + 'T00:00:00')
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function selectDate(date: string) {
  selectedDate.value = date
  loadData()
}

function getPriceInfo(productId: number) {
  const data = priceDataMap.value.get(productId)

  // 当前价格：优先今日维护价格，否则继承价格
  let current: number | null = null
  if (data?.price?.currentPrice != null) {
    current = data.price.currentPrice
  } else if (data?.inheritedPrice != null) {
    current = data.inheritedPrice
  }

  if (current == null) return null

  // 昨日价格：优先昨日维护价格，否则继承价格（与PC端一致）
  let previous: number | null = null
  if (data?.yesterdayPrice?.currentPrice != null) {
    previous = data.yesterdayPrice.currentPrice
  } else if (data?.inheritedPrice != null) {
    // PC端逻辑：没有昨日价格时，用继承价格作为昨日价格基准
    previous = data.inheritedPrice
  }

  // 如果没有昨日价格也没有继承价格，不显示差值
  if (previous == null) {
    return { price: current, trend: 'flat', diff: '' }
  }

  const diff = current - previous

  // 格式化差值，去掉多余的小数位
  const formattedDiff = diff === 0
    ? '0'
    : diff > 0
      ? `+${diff.toFixed(2).replace(/\.?0+$/, '')}`
      : diff.toFixed(2).replace(/\.?0+$/, '')

  return {
    price: current,
    trend: diff > 0 ? 'up' : diff < 0 ? 'down' : 'flat',
    diff: formattedDiff
  }
}

function getTrendIcon(trend?: string): string {
  if (trend === 'up') return '↑'
  if (trend === 'down') return '↓'
  return '—'
}

// 数据加载
async function loadCategories() {
  try {
    const res = await getCategories('ACTIVE')
    if (res.code === 200 && res.data) {
      categories.value = res.data
    }
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

async function loadData() {
  isLoading.value = true
  errorMsg.value = ''

  try {
    // 加载产品列表
    const productsRes = await getProducts({ page: 0, size: 100 })
    products.value = productsRes.data?.content || []

    // 加载价格数据
    const pricesRes = await getPricesByDateWithStats(selectedDate.value)
    const prices = pricesRes.data || []

    priceDataMap.value.clear()
    prices.forEach((item: PriceWithStats) => {
      if (item.price?.product?.id) {
        priceDataMap.value.set(item.price.product.id, item)
      }
    })
  } catch (err: any) {
    errorMsg.value = err?.message || '加载数据失败，请重试'
    console.error('Failed to load data:', err)
  } finally {
    isLoading.value = false
  }
}

function onRefresh() {
  loadData()
}

function onDateChange(e: any) {
  selectedDate.value = e.detail.value
  loadData()
}

function selectCategory(categoryId: number | null) {
  selectedCategoryId.value = categoryId
}

function onCategoryScroll(e: any) {
  // 滚动到末尾时隐藏指示器
  const scrollLeft = e.detail.scrollLeft
  const scrollWidth = e.detail.scrollWidth
  const clientWidth = e.detail.clientWidth || 375
  showScrollIndicator.value = scrollLeft + clientWidth < scrollWidth - 20
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearchInput() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    // 搜索已通过 computed 自动处理
  }, 300)
}

function goToDetail(id: number) {
  uni.navigateTo({ url: `/pages/products/detail?id=${id}` })
}

function goToPriceMaintenance() {
  uni.navigateTo({ url: '/pages/price-maintenance/index' })
}

onMounted(() => {
  userStore.restoreSession()

  if (!userStore.token) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }

  loadCategories()
  loadData()
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #F5F5F5;
  padding-bottom: 120rpx;
  overflow-x: hidden;
  width: 100%;
  box-sizing: border-box;
}

/* 头部 */
.header {
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  padding: 24rpx 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.date-section {
  display: flex;
  align-items: center;
}

.date-picker {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: rgba(255, 255, 255, 0.2);
  padding: 12rpx 20rpx;
  border-radius: 8rpx;
}

.date-text {
  font-size: 26rpx;
  color: #FFFFFF;
}

.date-icon {
  font-size: 18rpx;
  color: rgba(255, 255, 255, 0.8);
}

.header-actions {
  display: flex;
  align-items: center;
}

.refresh-btn {
  width: 72rpx;
  height: 72rpx;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.refresh-btn.loading {
  opacity: 0.6;
}

.refresh-icon {
  font-size: 36rpx;
  color: #FFFFFF;
}

.refresh-icon.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* 分类筛选 */
.category-filter {
  background: #FFFFFF;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #E5E5E5;
  position: relative;
}

.category-scroll {
  display: flex;
  gap: 16rpx;
  padding: 0 32rpx;
  white-space: nowrap;
}

.scroll-indicator {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 48rpx;
  height: 100%;
  background: linear-gradient(90deg, transparent 0%, #FFFFFF 60%);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding-right: 16rpx;
}

.indicator-arrow {
  font-size: 32rpx;
  color: #0D6E6E;
  font-weight: bold;
}

.category-tag {
  padding: 12rpx 24rpx;
  background: #F5F5F5;
  border-radius: 24rpx;
  flex-shrink: 0;
}

.category-tag text {
  font-size: 26rpx;
  color: #666666;
}

.category-tag.active {
  background: #0D6E6E;
}

.category-tag.active text {
  color: #FFFFFF;
}

/* 错误提示 */
.error-msg {
  margin: 24rpx 32rpx;
  padding: 24rpx;
  background: rgba(255, 77, 79, 0.1);
  border-radius: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.error-msg text {
  font-size: 28rpx;
  color: #E03B3B;
}

.retry-btn {
  color: #0D6E6E !important;
  font-weight: 500;
}

/* 加载骨架 */
.loading-container {
  padding: 32rpx;
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.skeleton-card {
  width: calc(50% - 12rpx);
  height: 200rpx;
  background: linear-gradient(90deg, #E5E5E5 25%, #F5F5F5 50%, #E5E5E5 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 16rpx;
  flex-shrink: 0;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* 区块标题 */
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.product-count text {
  font-size: 24rpx;
  color: #999999;
}

/* 重点关注指标 */
.featured-section {
  padding: 32rpx;
  background: #FFFFFF;
  margin-bottom: 24rpx;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.featured-scroll {
  display: flex;
  gap: 24rpx;
  white-space: nowrap;
  width: 100%;
}

.featured-card {
  min-width: 280rpx;
  width: 280rpx;
  background: #FFFFFF;
  border: 2rpx solid #E5E5E5;
  border-radius: 12rpx;
  padding: 20rpx;
  display: flex;
  flex-direction: row;
  gap: 16rpx;
  flex-shrink: 0;
}

.card-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8rpx;
}

.card-left .product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-left .product-specs {
  font-size: 22rpx;
  color: #999999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-right {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-end;
  gap: 8rpx;
  flex-shrink: 0;
}

.price-area {
  display: flex;
  align-items: baseline;
}

.price-large {
  font-size: 36rpx;
  font-weight: 700;
  color: #0D6E6E;
}

.price-large.empty {
  color: #CCCCCC;
  font-size: 28rpx;
}

.trend-area {
  display: flex;
  align-items: center;
}

.trend-flat {
  font-size: 22rpx;
  color: #CCCCCC;
}

.product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-specs {
  font-size: 24rpx;
  color: #999999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.price-value {
  font-size: 28rpx;
  font-weight: 600;
  color: #0D6E6E;
}

.price-value.empty {
  color: #CCCCCC;
  font-size: 24rpx;
}

.price-unit {
  font-size: 18rpx;
  color: #999999;
}

.trend-badge {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
  font-weight: 600;
}

.trend-badge.up {
  background: rgba(224, 59, 59, 0.1);
  color: #E03B3B;
}

.trend-badge.down {
  background: rgba(82, 196, 26, 0.1);
  color: #52C41A;
}

.trend-badge.flat {
  background: #F0F0F0;
  color: #999999;
}

.trend-badge.small {
  font-size: 20rpx;
  padding: 4rpx 8rpx;
}

/* 产品列表 */
.product-section {
  padding: 32rpx;
  background: #FFFFFF;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: #F5F5F5;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}

.search-icon {
  font-size: 28rpx;
  color: #999999;
}

.search-input {
  flex: 1;
  font-size: 28rpx;
  color: #1A1A1A;
}

.clear-btn {
  font-size: 28rpx;
  color: #999999;
  padding: 8rpx;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx;
  gap: 24rpx;
}

.empty-icon {
  font-size: 80rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #999999;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
  width: 100%;
  box-sizing: border-box;
}

.product-card {
  background: #F5F5F5;
  border-radius: 12rpx;
  padding: 16rpx;
  display: flex;
  flex-direction: row;
  gap: 12rpx;
  min-width: 0;
  box-sizing: border-box;
}

.card-left-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6rpx;
}

.card-left-info .product-name {
  font-size: 24rpx;
  font-weight: 600;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-left-info .product-specs {
  font-size: 20rpx;
  color: #999999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-right-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-end;
  gap: 6rpx;
  flex-shrink: 0;
}

.price-top {
  display: flex;
  align-items: baseline;
}

.price-top .price-value {
  font-size: 30rpx;
  font-weight: 700;
  color: #0D6E6E;
}

.price-top .price-value.empty {
  color: #CCCCCC;
  font-size: 24rpx;
}

.trend-bottom {
  display: flex;
  align-items: center;
}

/* 浮动按钮 */
.fab-btn {
  position: fixed;
  right: 32rpx;
  bottom: 180rpx;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  color: #FFFFFF;
  padding: 24rpx 32rpx;
  border-radius: 40rpx;
  box-shadow: 0 8rpx 24rpx rgba(13, 110, 110, 0.3);
  z-index: 100;
}

.fab-btn text {
  font-size: 28rpx;
  font-weight: 500;
}
</style>
