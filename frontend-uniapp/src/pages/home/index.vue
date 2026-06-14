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
      <!-- 经营摘要 -->
      <SummarySection :summary="homeSummary" :loading="summaryLoading" />

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

      <!-- 重点关注指标 -->
      <view class="featured-section" v-if="homeProducts.length > 0">
        <view class="section-header">
          <text class="section-title">重点关注指标</text>
        </view>
        <view class="featured-grid">
          <view
            class="featured-card"
            v-for="product in homeProducts.slice(0, 4)"
            :key="product.id"
            @click="goToDetail(product.id)"
          >
            <view class="featured-main">
              <text class="product-name">{{ getProductDisplayName(product) }}</text>
              <text class="product-specs" v-if="product.specs">{{ product.specs }}</text>
            </view>
            <view class="featured-price">
              <text class="price-large">{{ formatPriceValue(product, getCurrentPrice(product.id)) }}</text>
              <view class="trend-badge" :class="getDiffClass(product.id)">
                <text>{{ getDiffText(product) }}</text>
              </view>
            </view>
          </view>
        </view>
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
        <view class="product-list" v-else>
          <view
            class="product-row"
            v-for="product in filteredProducts"
            :key="product.id"
            @click="goToDetail(product.id)"
          >
            <view class="product-main">
              <text class="product-name">{{ getProductDisplayName(product) }}</text>
              <text class="product-specs">{{ getProductMeta(product) }}</text>
            </view>
            <view class="price-columns">
              <view class="price-col current">
                <text class="price-label">最新</text>
                <text class="price-value">{{ formatPriceValue(product, getCurrentPrice(product.id)) }}</text>
              </view>
              <view class="price-col">
                <text class="price-label">上期</text>
                <text class="price-sub">{{ formatPriceValue(product, getPreviousPrice(product.id)) }}</text>
              </view>
              <view class="price-col diff" :class="getDiffClass(product.id)">
                <text class="price-label">较上期</text>
                <text class="price-sub">{{ getDiffText(product) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 风险预警 -->
      <RiskAlertsPanel
        :alerts="priceAlerts"
        :loading="alertsLoading"
        @click="onAlertClick"
      />
    </template>

    <CustomTabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { getProducts, getPricesByDateWithStats } from '@/api/products'
import { getPriceQueryRows, type PriceQueryRow } from '@/api/priceQuery'
import { getCategories } from '@/api/categories'
import { getHomeSummary, getPriceAlerts } from '@/api/home'
import type { Product, ProductCategory } from '@/types'
import type { PriceWithStats } from '@/api/products'
import type { HomeSummary, PriceAlert } from '@/api/home'
import SummarySection from '@/components/home/SummarySection.vue'
import RiskAlertsPanel from '@/components/home/RiskAlertsPanel.vue'
import CustomTabBar from '@/custom-tab-bar/index.vue'
import { getCurrencySymbol, loadAllDicts } from '@/composables/useDict'
import { getProductCategoryId, sortProductsByHomeOrder } from '@/utils/productOrder'
import { getProductDisplayName } from '@/utils/productDisplay'

const userStore = useUserStore()

// 状态
const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const selectedCategoryId = ref<number | null>(null)
const priceDataMap = ref<Map<number, PriceWithStats>>(new Map())
const priceQueryRowMap = ref<Map<number, PriceQueryRow>>(new Map())
const isLoading = ref(false)
const errorMsg = ref('')
const searchQuery = ref('')
const selectedDate = ref(getYesterday())
const showScrollIndicator = ref(true)

// 新增状态
const homeSummary = ref<HomeSummary | null>(null)
const priceAlerts = ref<PriceAlert[]>([])
const summaryLoading = ref(false)
const alertsLoading = ref(false)

// 计算属性
const homeProducts = computed(() => {
  let result = sortProductsByHomeOrder(
    products.value.filter(p => p.showOnHome && p.status === 'ACTIVE'),
    categories.value
  )
  if (selectedCategoryId.value !== null) {
    result = result.filter(p => getProductCategoryId(p) === selectedCategoryId.value)
  }
  return result
})

const filteredProducts = computed(() => {
  let active = sortProductsByHomeOrder(
    products.value.filter(p => p.status === 'ACTIVE'),
    categories.value
  )

  // 按分类筛选
  if (selectedCategoryId.value !== null) {
    active = active.filter(p => getProductCategoryId(p) === selectedCategoryId.value)
  }

  // 按搜索词筛选
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    active = active.filter(p => getProductSearchText(p).includes(q))
  }

  return active
})

// 工具函数
function getYesterday(): string {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}

function getCurrentPrice(productId: number): number | null {
  const queryRow = priceQueryRowMap.value.get(productId)
  if (queryRow?.latestPrice != null) return queryRow.latestPrice
  const data = priceDataMap.value.get(productId)
  return data?.price?.currentPrice ?? data?.inheritedPrice ?? null
}

function getPreviousPrice(productId: number): number | null {
  const queryRow = priceQueryRowMap.value.get(productId)
  if (queryRow?.previousPrice != null) return queryRow.previousPrice
  const data = priceDataMap.value.get(productId)
  return data?.yesterdayPrice?.currentPrice ?? data?.inheritedPrice ?? null
}

function formatPriceValue(product: Product, value: number | null | undefined): string {
  if (value == null) return '--'
  return `${getCurrencySymbol(product.currency)}${Number(value).toFixed(2)}`
}

function getDiffValue(productId: number): number | null {
  const queryRow = priceQueryRowMap.value.get(productId)
  if (queryRow?.previousChangeAmount != null) return Number(queryRow.previousChangeAmount)
  const current = getCurrentPrice(productId)
  const previous = getPreviousPrice(productId)
  if (current == null || previous == null) return null
  return current - previous
}

function getDiffText(product: Product): string {
  const diff = getDiffValue(product.id)
  if (diff == null) return '--'
  const symbol = getCurrencySymbol(product.currency)
  if (diff > 0) return `+${symbol}${diff.toFixed(2)}`
  if (diff < 0) return `-${symbol}${Math.abs(diff).toFixed(2)}`
  return `${symbol}0.00`
}

function getDiffClass(productId: number): string {
  const diff = getDiffValue(productId)
  if (diff == null || diff === 0) return 'flat'
  return diff > 0 ? 'up' : 'down'
}

function getProductMeta(product: Product): string {
  const parts = [product.category?.name, product.specs, product.unit].filter(Boolean)
  return parts.length ? parts.join(' · ') : '暂无规格'
}

function getProductSearchText(product: Product): string {
  return `${getProductDisplayName(product)} ${product.name || ''} ${product.specs || ''} ${product.code || ''}`.toLowerCase()
}

// 数据加载
async function loadCategories() {
  try {
    const res = await getCategories('ACTIVE')
    if (res.code === 200 && res.data) {
      categories.value = [...res.data].sort((a, b) =>
        (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
        || a.name.localeCompare(b.name, 'zh-CN')
        || a.id - b.id
      )
    }
  } catch (error) {
    console.error('加载分类失败:', error)
  }
}

async function loadSummary() {
  summaryLoading.value = true
  try {
    const res = await getHomeSummary(selectedDate.value)
    if (res.code === 200 && res.data) {
      homeSummary.value = res.data
    }
  } catch (error) {
    console.error('加载摘要失败:', error)
  } finally {
    summaryLoading.value = false
  }
}

async function loadAlerts() {
  alertsLoading.value = true
  try {
    const res = await getPriceAlerts(selectedDate.value)
    if (res.code === 200 && res.data) {
      priceAlerts.value = res.data
    }
  } catch (error) {
    console.error('加载预警失败:', error)
  } finally {
    alertsLoading.value = false
  }
}

async function loadData() {
  isLoading.value = true
  errorMsg.value = ''

  try {
    // 并行加载所有数据
    const [productsRes, pricesRes, priceQueryRes] = await Promise.all([
      getProducts({ page: 0, size: 100, sortBy: 'sortOrder', sortDirection: 'asc' }),
      getPricesByDateWithStats(selectedDate.value),
      getPriceQueryRows({
        date: selectedDate.value,
        page: 0,
        size: 100,
        status: 'ACTIVE',
        sortBy: 'sortOrder',
        sortDirection: 'asc'
      })
    ])

    products.value = productsRes.data?.content || []

    const prices = pricesRes.data || []
    priceDataMap.value.clear()
    prices.forEach((item: PriceWithStats) => {
      if (item.price?.product?.id) {
        priceDataMap.value.set(item.price.product.id, item)
      }
    })
    priceQueryRowMap.value = new Map((priceQueryRes.data?.content || []).map(row => [row.productId, row]))

    // 加载新增数据
    loadSummary()
    loadAlerts()
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
  const scrollLeft = e.detail.scrollLeft
  const scrollWidth = e.detail.scrollWidth
  const clientWidth = e.detail.clientWidth || 375
  showScrollIndicator.value = scrollLeft + clientWidth < scrollWidth - 20
}

function onAlertClick(alert: PriceAlert) {
  goToDetail(alert.productId)
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

onMounted(() => {
  userStore.restoreSession()

  if (!userStore.token) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }

  Promise.all([loadAllDicts(), loadCategories(), loadData()])
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
  margin-bottom: 16rpx;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.featured-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx;
}

.featured-card {
  min-width: 0;
  background: #F8FAFC;
  border: 1rpx solid #E5E7EB;
  border-radius: 12rpx;
  padding: 18rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.featured-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.featured-main .product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.featured-main .product-specs {
  font-size: 22rpx;
  color: #999999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.featured-price {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12rpx;
}

.price-large {
  font-family: Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  font-size: 34rpx;
  font-weight: 700;
  color: #0D6E6E;
}

.trend-badge {
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 600;
  white-space: nowrap;
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

.product-list {
  display: flex;
  flex-direction: column;
  border-top: 1rpx solid #EEF2F7;
}

.product-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-height: 118rpx;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #EEF2F7;
}

.product-main {
  flex: 1.1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.product-main .product-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-main .product-specs {
  font-size: 22rpx;
  color: #999999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-columns {
  flex: 1.55;
  min-width: 0;
  display: flex;
  justify-content: flex-end;
  gap: 14rpx;
}

.price-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 84rpx;
  gap: 4rpx;
}

.price-label {
  font-size: 20rpx;
  color: #9CA3AF;
}

.price-value {
  font-family: Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  font-size: 28rpx;
  font-weight: 700;
  color: #0D6E6E;
  white-space: nowrap;
}

.price-sub {
  font-size: 24rpx;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
}

.price-col.up .price-sub {
  color: #E03B3B;
}

.price-col.down .price-sub {
  color: #16A34A;
}

.price-col.flat .price-sub {
  color: #64748B;
}

</style>
