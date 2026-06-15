<template>
  <view class="history-page">
    <view class="header">
      <view>
        <text class="page-title">历史价格</text>
        <text class="page-subtitle">按日期追溯产品报价</text>
      </view>
      <picker mode="date" :value="selectedDate" @change="onDateChange">
        <view class="date-picker">
          <text>{{ selectedDate }}</text>
          <text class="date-arrow">▼</text>
        </view>
      </picker>
    </view>

    <view class="filter-card">
      <view class="search-bar">
        <text class="search-icon">🔍</text>
        <input
          v-model="searchQuery"
          class="search-input"
          type="text"
          placeholder="搜索产品名称或规格"
        />
        <text class="clear-btn" v-if="searchQuery" @click="searchQuery = ''">✕</text>
      </view>

      <scroll-view class="category-scroll" scroll-x enable-flex :show-scrollbar="false">
        <view
          class="category-chip"
          :class="{ active: selectedCategoryId === null }"
          @click="selectedCategoryId = null"
        >
          全部
        </view>
        <view
          v-for="category in categories"
          :key="category.id"
          class="category-chip"
          :class="{ active: selectedCategoryId === category.id }"
          @click="selectedCategoryId = category.id"
        >
          {{ category.name }}
        </view>
      </scroll-view>
    </view>

    <view class="summary-row">
      <view class="summary-item">
        <text class="summary-value">{{ filteredProducts.length }}</text>
        <text class="summary-label">产品</text>
      </view>
      <view class="summary-item">
        <text class="summary-value">{{ quotedCount }}</text>
        <text class="summary-label">已报价</text>
      </view>
      <view class="summary-item">
        <text class="summary-value">{{ missingCount }}</text>
        <text class="summary-label">未报价</text>
      </view>
    </view>

    <view v-if="loading" class="loading">
      <text>加载中...</text>
    </view>

    <scroll-view v-else class="content" scroll-y>
      <view v-if="filteredProducts.length === 0" class="empty">
        <text class="empty-icon">📦</text>
        <text class="empty-text">{{ searchQuery ? '未找到匹配产品' : '暂无产品数据' }}</text>
      </view>

      <view v-else class="price-list">
        <view
          v-for="product in filteredProducts"
          :key="product.id"
          class="price-card"
          @click="goToDetail(product.id)"
        >
          <view class="card-main">
            <view class="product-info">
              <text class="product-name">{{ getProductDisplayName(product) }}</text>
              <text class="product-meta">{{ getProductMeta(product) }}</text>
            </view>
            <view class="price-info">
              <text class="current-price">{{ formatPrice(product, getCurrentPrice(product.id)) }}</text>
              <text class="price-label">最新价格</text>
            </view>
          </view>

          <view class="compare-row">
            <view class="compare-item">
              <text class="compare-label">最新</text>
              <text class="compare-value">{{ formatPrice(product, getCurrentPrice(product.id)) }}</text>
            </view>
            <view class="compare-item">
              <text class="compare-label">较上期</text>
              <text class="compare-value" :class="getDiffClass(product.id)">
                {{ formatDiff(product, product.id) }}
              </text>
            </view>
            <view class="compare-item">
              <text class="compare-label">月均</text>
              <text class="compare-value">{{ formatPrice(product, getMonthlyAvg(product.id)) }}</text>
            </view>
          </view>
          <view class="card-actions">
            <view class="detail-btn" @click.stop="goToDetail(product.id)">
              <text>查看详情</text>
              <text class="detail-arrow">›</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getProducts, getPricesByDateWithStats, type PriceWithStats } from '@/api/products'
import { getPriceQueryRows, type PriceQueryRow } from '@/api/priceQuery'
import { getCategories } from '@/api/categories'
import type { PageResponse, Product, ProductCategory } from '@/types'
import { getCurrencySymbol, loadAllDicts } from '@/composables/useDict'
import { getProductDisplayName } from '@/utils/productDisplay'

const selectedDate = ref(getYesterday())
const searchQuery = ref('')
const selectedCategoryId = ref<number | null>(null)
const loading = ref(false)
const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const priceDataMap = ref<Map<number, PriceWithStats>>(new Map())
const priceQueryRowMap = ref<Map<number, PriceQueryRow>>(new Map())

const filteredProducts = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return products.value.filter(product => {
    const matchCategory = selectedCategoryId.value === null || product.categoryId === selectedCategoryId.value || product.category?.id === selectedCategoryId.value
    const haystack = `${getProductDisplayName(product)} ${product.name || ''} ${product.specs || ''} ${product.code || ''}`.toLowerCase()
    const matchKeyword = !keyword || haystack.includes(keyword)
    return matchCategory && matchKeyword
  })
})

const quotedCount = computed(() => filteredProducts.value.filter(product => getCurrentPrice(product.id) != null).length)
const missingCount = computed(() => Math.max(filteredProducts.value.length - quotedCount.value, 0))

function getYesterday(): string {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}

const loadData = async () => {
  loading.value = true
  try {
    const [productRes, categoryRes, priceRes, priceQueryRes] = await Promise.all([
      getProducts({ page: 0, size: 1000, status: 'ACTIVE' }),
      getCategories('ACTIVE'),
      getPricesByDateWithStats(selectedDate.value),
      getPriceQueryRows({
        date: selectedDate.value,
        page: 0,
        size: 1000,
        status: 'ACTIVE',
        sortBy: 'sortOrder',
        sortDirection: 'asc'
      })
    ])

    const pageData = productRes.data as PageResponse<Product>
    products.value = (pageData.content || []).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    categories.value = categoryRes.data || []

    const nextMap = new Map<number, PriceWithStats>()
    for (const item of priceRes.data || []) {
      const productId = item.price?.product?.id || item.price?.productId
      if (productId) nextMap.set(productId, item)
    }
    priceDataMap.value = nextMap
    priceQueryRowMap.value = new Map((priceQueryRes.data?.content || []).map(row => [row.productId, row]))
  } catch (error) {
    console.error('加载历史价格失败:', error)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

const consumeNotificationTargetDate = () => {
  const targetDate = uni.getStorageSync('notificationTargetHistoryDate')
  if (typeof targetDate !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(targetDate)) return
  uni.removeStorageSync('notificationTargetHistoryDate')
  if (targetDate === selectedDate.value) {
    loadData()
    return
  }
  selectedDate.value = targetDate
}

const onDateChange = (event: any) => {
  selectedDate.value = event.detail.value
}

const getProductMeta = (product: Product) => {
  const parts = [product.category?.name, product.specs, product.unit].filter(Boolean)
  return parts.length ? parts.join(' · ') : '暂无规格信息'
}

const getCurrentPrice = (productId: number): number | null => {
  const queryRow = priceQueryRowMap.value.get(productId)
  if (queryRow?.latestPrice != null) return queryRow.latestPrice
  const item = priceDataMap.value.get(productId)
  if (item?.price?.currentPrice != null) return item.price.currentPrice
  return item?.inheritedPrice ?? null
}

const getPreviousPrice = (productId: number): number | null => {
  const queryRow = priceQueryRowMap.value.get(productId)
  if (queryRow?.previousPrice != null) return queryRow.previousPrice
  const item = priceDataMap.value.get(productId)
  if (item?.yesterdayPrice?.currentPrice != null) return item.yesterdayPrice.currentPrice
  return item?.inheritedPrice ?? null
}

const getMonthlyAvg = (productId: number): number | null => {
  return priceDataMap.value.get(productId)?.monthlyAveragePrice ?? null
}

const formatPrice = (product: Product, price: number | null | undefined) => {
  if (price == null) return '--'
  return `${getCurrencySymbol(product.currency)}${Number(price).toFixed(2)}`
}

const getDiff = (productId: number): number | null => {
  const queryRow = priceQueryRowMap.value.get(productId)
  if (queryRow?.previousChangeAmount != null) return Number(queryRow.previousChangeAmount)
  const current = getCurrentPrice(productId)
  const previous = getPreviousPrice(productId)
  if (current == null || previous == null) return null
  return current - previous
}

const formatDiff = (product: Product, productId: number) => {
  const diff = getDiff(productId)
  if (diff == null) return '--'
  const symbol = getCurrencySymbol(product.currency)
  if (diff > 0) return `+${symbol}${diff.toFixed(2)}`
  if (diff < 0) return `-${symbol}${Math.abs(diff).toFixed(2)}`
  return `${symbol}0.00`
}

const getDiffClass = (productId: number) => {
  const diff = getDiff(productId)
  return {
    up: diff != null && diff > 0,
    down: diff != null && diff < 0,
    flat: diff === 0
  }
}

const goToDetail = (productId: number) => {
  uni.navigateTo({ url: `/pages/products/detail?id=${productId}` })
}

watch(selectedDate, loadData)

onMounted(() => Promise.all([loadAllDicts(), loadData()]))
onShow(consumeNotificationTargetDate)
</script>

<style scoped>
.history-page {
  min-height: 100vh;
  background: #F5F5F5;
  padding: 24rpx 32rpx 140rpx;
  box-sizing: border-box;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 24rpx;
}

.page-title {
  display: block;
  color: #1A1A1A;
  font-size: 40rpx;
  font-weight: 700;
}

.page-subtitle {
  display: block;
  margin-top: 6rpx;
  color: #888888;
  font-size: 24rpx;
}

.date-picker {
  display: flex;
  align-items: center;
  gap: 8rpx;
  border: 1px solid #E5E5E5;
  border-radius: 12rpx;
  background: #FFFFFF;
  color: #0D6E6E;
  font-size: 26rpx;
  font-weight: 600;
  padding: 16rpx 20rpx;
}

.date-arrow {
  color: #94A3B8;
  font-size: 18rpx;
}

.filter-card,
.summary-row,
.price-card {
  border-radius: 16rpx;
  background: #FFFFFF;
}

.filter-card {
  padding: 20rpx;
  margin-bottom: 20rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  height: 72rpx;
  border-radius: 12rpx;
  background: #F8FAFC;
  padding: 0 20rpx;
  margin-bottom: 18rpx;
}

.search-icon {
  margin-right: 12rpx;
  color: #94A3B8;
  font-size: 26rpx;
}

.search-input {
  flex: 1;
  height: 72rpx;
  color: #1A1A1A;
  font-size: 28rpx;
}

.clear-btn {
  color: #94A3B8;
  font-size: 24rpx;
  padding: 12rpx;
}

.category-scroll {
  white-space: nowrap;
}

.category-chip {
  display: inline-flex;
  align-items: center;
  min-height: 56rpx;
  margin-right: 12rpx;
  border: 1px solid #E5E7EB;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #64748B;
  font-size: 24rpx;
  padding: 0 22rpx;
}

.category-chip.active {
  border-color: #0D6E6E;
  background: #ECFDF5;
  color: #0D6E6E;
  font-weight: 600;
}

.summary-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  padding: 20rpx;
  margin-bottom: 20rpx;
}

.summary-item {
  text-align: center;
}

.summary-value {
  display: block;
  color: #0D6E6E;
  font-size: 34rpx;
  font-weight: 700;
}

.summary-label {
  display: block;
  margin-top: 4rpx;
  color: #888888;
  font-size: 22rpx;
}

.content {
  max-height: calc(100vh - 360rpx);
}

.price-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.price-card {
  padding: 24rpx;
}

.card-main {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 20rpx;
}

.product-info {
  flex: 1;
  min-width: 0;
}

.product-name {
  display: block;
  color: #1A1A1A;
  font-size: 30rpx;
  font-weight: 650;
}

.product-meta {
  display: block;
  margin-top: 6rpx;
  color: #888888;
  font-size: 23rpx;
}

.price-info {
  text-align: right;
}

.current-price {
  font-family: Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  display: block;
  color: #0D6E6E;
  font-size: 34rpx;
  font-weight: 700;
}

.price-label {
  display: block;
  margin-top: 4rpx;
  color: #94A3B8;
  font-size: 22rpx;
}

.compare-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12rpx;
  border-radius: 12rpx;
  background: #F8FAFC;
  padding: 16rpx;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 18rpx;
}

.detail-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  min-width: 176rpx;
  height: 64rpx;
  border-radius: 12rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 650;
}

.detail-arrow {
  color: #FFFFFF;
  font-size: 32rpx;
  line-height: 1;
}

.compare-item {
  text-align: center;
}

.compare-label {
  display: block;
  color: #888888;
  font-size: 21rpx;
}

.compare-value {
  font-family: Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  display: block;
  margin-top: 6rpx;
  color: #1A1A1A;
  font-size: 24rpx;
  font-weight: 600;
}

.compare-value.up {
  color: #DC2626;
}

.compare-value.down {
  color: #16A34A;
}

.compare-value.flat {
  color: #64748B;
}

.loading,
.empty {
  padding: 140rpx 0;
  text-align: center;
}

.loading text,
.empty-text {
  color: #888888;
  font-size: 28rpx;
}

.empty-icon {
  display: block;
  margin-bottom: 16rpx;
  font-size: 56rpx;
}
</style>
