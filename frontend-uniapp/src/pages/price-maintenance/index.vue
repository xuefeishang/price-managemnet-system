<template>
  <view class="page">
    <!-- 顶部导航 -->
    <view class="navbar">
      <view class="navbar-left">
        <text class="navbar-title">{{ formatDateDisplay(selectedDate) }}</text>
      </view>
      <button class="save-btn" @click="handleSave" :disabled="saving || !hasChanges">
        {{ saving ? '保存中...' : '保存' }}
      </button>
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

    <!-- 产品列表 -->
    <scroll-view class="content" scroll-y v-if="!loading">
      <view class="price-list">
        <view v-for="(product, index) in products" :key="product.id" class="price-card">
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
              <text class="stat-label">月均价</text>
              <text class="stat-value">{{ formatPrice(getMonthlyAvg(product.id)) }}</text>
            </view>
          </view>
        </view>
      </view>

      <view v-if="products.length === 0" class="empty">
        <text>暂无产品数据</text>
      </view>
    </scroll-view>

    <view v-else class="loading">
      <text>加载中...</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { getProducts } from '@/api/products'
import { getPricesByDateWithStats, addProductPrice, updatePrice } from '@/api/products'
import type { Product, Price, PageResponse } from '@/types'

const userStore = useUserStore()

const selectedDate = ref(new Date().toISOString().split('T')[0])
const products = ref<Product[]>([])
const loading = ref(false)
const saving = ref(false)

const priceMap = ref<Map<number, Price>>(new Map())
const yesterdayPriceMap = ref<Map<number, Price>>(new Map())
const monthlyAverageMap = ref<Map<number, number>>(new Map())
const inheritedPriceMap = ref<Map<number, number>>(new Map())
const inheritedBudgetPriceMap = ref<Map<number, number>>(new Map())
const editingPrices = ref<Map<number, string>>(new Map())

const hasChanges = computed(() => {
  for (const [productId, editPrice] of editingPrices.value) {
    const original = priceMap.value.get(productId)
    if (!original) {
      if (editPrice) return true
    } else {
      if (editPrice !== String(original.currentPrice || '')) return true
    }
  }
  return false
})

const formatDateDisplay = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const loadData = async () => {
  loading.value = true
  try {
    const [productRes, priceRes] = await Promise.all([
      getProducts({ page: 0, size: 1000, status: 'ACTIVE' }),
      getPricesByDateWithStats(selectedDate.value)
    ])

    const productList = (productRes.data as PageResponse<Product>).content || []
    productList.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    products.value = productList

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
      if (!priceStr) continue
      const currentPrice = parseFloat(priceStr)
      if (isNaN(currentPrice)) continue

      const existingPrice = priceMap.value.get(productId)

      if (existingPrice?.id != null) {
        saveTasks.push(
          updatePrice(existingPrice.id, { currentPrice, effectiveDate: selectedDate.value })
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
  loadData()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #F5F5F5;
  display: flex;
  flex-direction: column;
  padding-bottom: 120rpx;
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

.save-btn {
  padding: 12rpx 32rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 28rpx;
  border-radius: 8rpx;
  border: none;
}

.save-btn[disabled] {
  opacity: 0.5;
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
  grid-template-columns: repeat(3, 1fr);
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
</style>