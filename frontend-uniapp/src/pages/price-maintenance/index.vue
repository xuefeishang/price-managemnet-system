<template>
  <view class="page">
    <!-- 顶部导航 -->
    <view class="navbar">
      <view class="navbar-left">
        <text class="navbar-title">{{ formatDateDisplay(selectedDate) }}</text>
      </view>
      <text class="navbar-status">{{ hasChanges ? '有未保存修改' : '暂无修改' }}</text>
    </view>

    <!-- 日期选择 -->
    <view class="date-section">
      <view class="date-nav">
        <button class="date-btn" @click="goToPrevDate">前一天</button>
        <picker mode="date" :value="selectedDate" @change="onDateChange">
          <view class="date-picker">
            <text>{{ selectedDate }}</text>
          </view>
        </picker>
        <button class="date-btn" @click="goToNextDate">后一天</button>
      </view>
    </view>

    <view class="entry-summary">
      <view class="summary-card">
        <text class="summary-value">{{ enteredCount }}</text>
        <text class="summary-label">已录入</text>
      </view>
      <view class="summary-card">
        <text class="summary-value">{{ filteredProducts.length }}</text>
        <text class="summary-label">当前产品</text>
      </view>
      <view class="summary-card">
        <text class="summary-value">{{ missingCount }}</text>
        <text class="summary-label">未报价</text>
      </view>
    </view>

    <view class="filter-section">
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

    <!-- 产品列表 -->
    <scroll-view class="content" scroll-y v-if="!loading">
      <view class="price-list">
        <view v-for="(product, index) in filteredProducts" :key="product.id" class="price-card">
          <view class="card-header">
            <text class="card-seq">{{ index + 1 }}</text>
            <view class="card-title">
              <text class="card-name">{{ product.name }}</text>
              <text class="card-specs" v-if="product.specs">{{ product.specs }}</text>
            </view>
          </view>

          <view class="price-row">
            <view class="price-field">
              <text class="price-label">当日售价</text>
              <view class="price-input-wrapper">
                <text class="price-unit">¥</text>
                <input
                  class="price-input"
                  type="digit"
                  :value="editingPrices.get(product.id) || ''"
                  @input="updatePrice(product.id, $event)"
                  :placeholder="getPricePlaceholder(product.id)"
                />
              </view>
            </view>
          </view>

          <view class="stats-row">
            <view class="stat-item">
              <text class="stat-label">预算价</text>
              <text class="stat-value">{{ formatBudgetPrice(product.id) }}</text>
            </view>
            <view class="stat-item">
              <text class="stat-label">昨日价</text>
              <text class="stat-value">{{ formatPrice(getYesterdayPrice(product.id)) }}</text>
            </view>
            <view class="stat-item">
              <text class="stat-label">较昨日</text>
              <text class="stat-value" :class="getDiffClass(product.id)">{{ formatDiff(product.id) }}</text>
            </view>
            <view class="stat-item">
              <text class="stat-label">月均价</text>
              <text class="stat-value">{{ formatPrice(getMonthlyAvg(product.id)) }}</text>
            </view>
          </view>
        </view>
      </view>

      <view v-if="filteredProducts.length === 0" class="empty">
        <text>{{ searchQuery ? '未找到匹配产品' : '暂无产品数据' }}</text>
      </view>
    </scroll-view>

    <view v-else class="loading">
      <text>加载中...</text>
    </view>

    <view class="save-bar">
      <view class="save-info">
        <text class="save-title">{{ hasChanges ? '价格已修改' : '修改价格后可保存' }}</text>
        <text class="save-desc">保存 {{ selectedDate }} 报价</text>
      </view>
      <button class="save-btn" @click="handleSave" :disabled="saving || !hasChanges">
        {{ saving ? '保存中' : '保存' }}
      </button>
    </view>

    <CustomTabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { getProducts } from '@/api/products'
import { getPricesByDateWithStats, addProductPrice, updatePrice as updateProductPrice } from '@/api/products'
import { getCategories } from '@/api/categories'
import type { Product, Price, PageResponse, ProductCategory } from '@/types'
import CustomTabBar from '@/custom-tab-bar/index.vue'

const userStore = useUserStore()

const selectedDate = ref(getYesterday())
const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const loading = ref(false)
const saving = ref(false)
const searchQuery = ref('')
const selectedCategoryId = ref<number | null>(null)

const priceMap = ref<Map<number, Price>>(new Map())
const yesterdayPriceMap = ref<Map<number, Price>>(new Map())
const monthlyAverageMap = ref<Map<number, number>>(new Map())
const inheritedPriceMap = ref<Map<number, number>>(new Map())
const inheritedBudgetPriceMap = ref<Map<number, number>>(new Map())
const editingPrices = ref<Map<number, string>>(new Map())

const filteredProducts = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return products.value.filter(product => {
    const matchCategory = selectedCategoryId.value === null || product.categoryId === selectedCategoryId.value || product.category?.id === selectedCategoryId.value
    const haystack = `${product.name || ''} ${product.specs || ''} ${product.code || ''}`.toLowerCase()
    const matchKeyword = !keyword || haystack.includes(keyword)
    return matchCategory && matchKeyword
  })
})

const enteredCount = computed(() => filteredProducts.value.filter(product => editingPrices.value.get(product.id)?.trim()).length)
const missingCount = computed(() => Math.max(filteredProducts.value.length - enteredCount.value, 0))

const hasChanges = computed(() => {
  for (const [productId, editPrice] of editingPrices.value) {
    if (isProductChanged(productId, editPrice)) return true
  }
  return false
})

function getYesterday(): string {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return date.toISOString().split('T')[0]
}

const isProductChanged = (productId: number, editPrice: string) => {
  const normalizedEdit = editPrice.trim()
  const original = priceMap.value.get(productId)
  const originalValue = original?.currentPrice != null ? String(original.currentPrice) : ''
  return normalizedEdit !== originalValue
}

const formatDateDisplay = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const loadData = async () => {
  loading.value = true
  try {
    const [productRes, categoryRes, priceRes] = await Promise.all([
      getProducts({ page: 0, size: 1000, status: 'ACTIVE' }),
      getCategories('ACTIVE'),
      getPricesByDateWithStats(selectedDate.value)
    ])

    const productList = (productRes.data as PageResponse<Product>).content || []
    productList.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    products.value = productList
    categories.value = categoryRes.data || []

    priceMap.value = new Map()
    yesterdayPriceMap.value = new Map()
    monthlyAverageMap.value = new Map()
    inheritedPriceMap.value = new Map()
    inheritedBudgetPriceMap.value = new Map()
    editingPrices.value = new Map()

    const items = priceRes.data || []
    for (const item of items) {
      if (item.price?.product) {
        const productId = item.price.product.id
        priceMap.value.set(productId, item.price)
        if (item.price.currentPrice != null) {
          editingPrices.value.set(productId, String(item.price.currentPrice))
        } else {
          editingPrices.value.set(productId, '')
        }
        if (item.yesterdayPrice) {
          yesterdayPriceMap.value.set(productId, item.yesterdayPrice)
        }
        if (item.monthlyAveragePrice != null) {
          monthlyAverageMap.value.set(productId, item.monthlyAveragePrice)
        }
        if (item.inheritedPrice != null) {
          inheritedPriceMap.value.set(productId, item.inheritedPrice)
        }
        if (item.inheritedBudgetPrice != null) {
          inheritedBudgetPriceMap.value.set(productId, item.inheritedBudgetPrice)
        }
      }
    }

    products.value.forEach(product => {
      if (!editingPrices.value.has(product.id)) {
        editingPrices.value.set(product.id, '')
      }
    })
  } catch (error) {
    console.error('加载数据失败:', error)
  } finally {
    loading.value = false
  }
}

const updatePrice = (productId: number, event: any) => {
  editingPrices.value.set(productId, event.detail.value)
}

const getPricePlaceholder = (productId: number): string => {
  const inherited = inheritedPriceMap.value.get(productId)
  if (inherited != null) return inherited.toFixed(2)
  return '0.00'
}

const formatBudgetPrice = (productId: number): string => {
  const price = priceMap.value.get(productId)
  if (price?.budgetPrice != null) return '¥' + price.budgetPrice.toFixed(2)
  const inherited = inheritedBudgetPriceMap.value.get(productId)
  if (inherited != null) return '¥' + inherited.toFixed(2)
  const product = products.value.find(p => p.id === productId)
  if (product?.budgetPrice != null) return '¥' + product.budgetPrice.toFixed(2)
  return '-'
}

const getYesterdayPrice = (productId: number): number | null => {
  const yesterdayPrice = yesterdayPriceMap.value.get(productId)?.currentPrice
  if (yesterdayPrice != null) return yesterdayPrice
  return inheritedPriceMap.value.get(productId) ?? null
}

const getMonthlyAvg = (productId: number): number | null => {
  const avg = monthlyAverageMap.value.get(productId)
  if (avg != null) return avg
  return inheritedPriceMap.value.get(productId) ?? null
}

const formatPrice = (price: number | null | undefined): string => {
  if (price === null || price === undefined) return '-'
  return price.toFixed(2)
}

const getDiff = (productId: number): number | null => {
  const editValue = editingPrices.value.get(productId)
  const current = editValue ? parseFloat(editValue) : getYesterdayPrice(productId)
  const yesterday = getYesterdayPrice(productId)
  if (current == null || yesterday == null || isNaN(current)) return null
  return current - yesterday
}

const formatDiff = (productId: number): string => {
  const diff = getDiff(productId)
  if (diff === null) return '-'
  if (diff > 0) return `+${diff.toFixed(2)}`
  if (diff < 0) return `-${Math.abs(diff).toFixed(2)}`
  return '0.00'
}

const getDiffClass = (productId: number) => {
  const diff = getDiff(productId)
  return {
    up: diff != null && diff > 0,
    down: diff != null && diff < 0,
    flat: diff === 0
  }
}

const handleSave = async () => {
  if (!hasChanges.value) {
    uni.showToast({ title: '没有修改', icon: 'none' })
    return
  }

  saving.value = true
  let successCount = 0
  let failCount = 0

  try {
    const saveTasks: Promise<void>[] = []
    for (const [productId, priceStr] of editingPrices.value) {
      if (!isProductChanged(productId, priceStr)) continue
      if (!priceStr.trim()) continue
      const currentPrice = parseFloat(priceStr)
      if (isNaN(currentPrice)) continue

      const existingPrice = priceMap.value.get(productId)

      if (existingPrice?.id != null) {
        saveTasks.push(
          updateProductPrice(existingPrice.id, { currentPrice, effectiveDate: selectedDate.value })
            .then(() => { successCount++ })
            .catch(() => { failCount++ })
        )
      } else {
        saveTasks.push(
          addProductPrice(productId, { currentPrice, effectiveDate: selectedDate.value })
            .then(() => { successCount++ })
            .catch(() => { failCount++ })
        )
      }
    }

    await Promise.allSettled(saveTasks)
    loadData()

    if (failCount === 0) {
      uni.showToast({ title: `保存成功 ${successCount} 条`, icon: 'none' })
    } else {
      uni.showToast({ title: `成功 ${successCount} 失败 ${failCount}`, icon: 'none' })
    }
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

const goToPrevDate = () => {
  const date = new Date(selectedDate.value)
  date.setDate(date.getDate() - 1)
  selectedDate.value = date.toISOString().split('T')[0]
}

const goToNextDate = () => {
  const date = new Date(selectedDate.value)
  date.setDate(date.getDate() + 1)
  selectedDate.value = date.toISOString().split('T')[0]
}

const onDateChange = (e: any) => {
  selectedDate.value = e.detail.value
}

watch(selectedDate, () => {
  loadData()
})

onMounted(() => {
  userStore.restoreSession()
  if (!userStore.isAuthenticated) {
    uni.redirectTo({ url: '/pages/login/index' })
    return
  }
  if (!userStore.canEdit) {
    uni.showToast({ title: '无权访问价格录入', icon: 'none' })
    uni.switchTab({ url: '/pages/home/index' })
    return
  }
  loadData()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #F5F5F5;
  display: flex;
  flex-direction: column;
  padding-bottom: 240rpx;
}

.navbar {
  height: 88rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  border-bottom: 1px solid #E5E5E5;
}

.navbar-left {
  display: flex;
  align-items: center;
}

.navbar-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.navbar-status {
  color: #64748B;
  font-size: 24rpx;
}

.date-section {
  background: #FFFFFF;
  padding: 24rpx 32rpx;
  border-bottom: 1px solid #E5E5E5;
}

.date-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
}

.date-btn {
  padding: 16rpx 24rpx;
  background: #F5F5F5;
  font-size: 26rpx;
  color: #666666;
  border: none;
  border-radius: 8rpx;
}

.date-picker {
  flex: 1;
  text-align: center;
  padding: 16rpx;
  background: #F9FAFB;
  border-radius: 8rpx;
  border: 1px solid #E5E5E5;
}

.date-picker text {
  font-size: 28rpx;
  color: #1A1A1A;
}

.entry-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
  padding: 20rpx 32rpx 0;
}

.summary-card {
  border-radius: 14rpx;
  background: #FFFFFF;
  padding: 20rpx 12rpx;
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

.filter-section {
  background: #FFFFFF;
  margin: 20rpx 32rpx 0;
  border-radius: 16rpx;
  padding: 20rpx;
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

.content {
  flex: 1;
  padding: 24rpx 32rpx;
}

.price-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.price-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.card-seq {
  width: 48rpx;
  height: 48rpx;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
  font-size: 24rpx;
  font-weight: 600;
  border-radius: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.card-specs {
  font-size: 24rpx;
  color: #999999;
  margin-top: 4rpx;
}

.card-title {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.price-row {
  margin-bottom: 20rpx;
}

.price-field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.price-label {
  font-size: 24rpx;
  color: #888888;
}

.price-input-wrapper {
  display: flex;
  align-items: center;
  background: #F9FAFB;
  border: 1px solid #E5E5E5;
  border-radius: 8rpx;
  overflow: hidden;
}

.price-unit {
  padding: 16rpx 20rpx;
  font-size: 28rpx;
  font-weight: 500;
  color: #666666;
}

.price-input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  font-size: 32rpx;
  font-weight: 500;
  color: #1A1A1A;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16rpx;
  padding: 16rpx;
  background: #F9FAFB;
  border-radius: 8rpx;
}

.stat-item {
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 22rpx;
  color: #888888;
  margin-bottom: 8rpx;
}

.stat-value {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: #1A1A1A;
}

.stat-value.up {
  color: #DC2626;
}

.stat-value.down {
  color: #16A34A;
}

.stat-value.flat {
  color: #64748B;
}

.empty {
  padding: 160rpx 0;
  text-align: center;
}

.empty text {
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

.save-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(100rpx + env(safe-area-inset-bottom));
  z-index: 998;
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 112rpx;
  border-top: 1rpx solid #E5E7EB;
  background: #FFFFFF;
  padding: 16rpx 32rpx;
  box-sizing: border-box;
}

.save-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.save-title {
  color: #1A1A1A;
  font-size: 28rpx;
  font-weight: 600;
}

.save-desc {
  color: #64748B;
  font-size: 22rpx;
}

.save-btn {
  min-width: 176rpx;
  height: 76rpx;
  margin: 0;
  border: none;
  border-radius: 12rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 76rpx;
}

.save-btn[disabled] {
  background: #CBD5E1;
  color: #FFFFFF;
}
</style>
