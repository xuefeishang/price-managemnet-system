<template>
  <view class="page">
    <!-- 与首页一致的紧凑日期栏 -->
    <view class="header">
      <picker mode="date" :value="selectedDate" @change="onDateChange">
        <view class="date-picker">
          <text class="date-text">{{ selectedDate }}</text>
          <text class="date-icon">▼</text>
        </view>
      </picker>
      <view class="header-status" :class="{ changed: hasChanges }">
        <text>{{ hasChanges ? '有未保存修改' : '暂无修改' }}</text>
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
          <view class="card-main">
            <view class="card-header">
              <text class="card-seq">{{ index + 1 }}</text>
              <view class="card-title">
                <text class="card-name">{{ product.name }}</text>
                <text class="card-specs" v-if="product.specs">{{ product.specs }}</text>
              </view>
            </view>

            <view class="price-field">
              <text class="price-label">当日价格</text>
              <view class="price-input-wrapper">
                <text class="price-unit">{{ getProductCurrencySymbol(product.id) }}</text>
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
              <text class="stat-value">{{ formatPrice(product.id, getYesterdayPrice(product.id)) }}</text>
            </view>
            <view class="stat-item">
              <text class="stat-label">较昨日</text>
              <text class="stat-value" :class="getDiffClass(product.id)">{{ formatDiff(product.id) }}</text>
            </view>
            <view class="stat-item">
              <text class="stat-label">月均价</text>
              <text class="stat-value">{{ formatPrice(product.id, getMonthlyAvg(product.id)) }}</text>
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
        <text class="save-title">{{ hasChanges ? '价格已修改' : currentDraft ? '草稿已保存' : '修改价格后可保存' }}</text>
        <text class="save-desc">
          {{ currentDraft ? `草稿 ${currentDraft.savedItemCount || 0} 条 · ${selectedDate}` : `尚未保存草稿 · ${selectedDate}` }}
        </text>
      </view>
      <button class="action-btn save-btn" @click="handleSave" :disabled="saving || publishing || !hasChanges">
        {{ saving ? '保存中' : '保存' }}
      </button>
      <button class="action-btn publish-btn" @click="handlePublish" :disabled="publishing || saving || !currentDraft || hasChanges">
        {{ publishing ? '发布中' : '发布' }}
      </button>
    </view>

    <CustomTabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { getProducts } from '@/api/products'
import { getPricesByDateWithStats } from '@/api/products'
import { getPriceDraftByDate, publishPriceDraft, savePriceDraft } from '@/api/priceDraft'
import { getCategories } from '@/api/categories'
import type { PriceDraftBatch, Product, Price, PageResponse, ProductCategory } from '@/types'
import CustomTabBar from '@/custom-tab-bar/index.vue'
import { getCurrencySymbol, loadAllDicts } from '@/composables/useDict'
import { refreshNotificationIndicator, showNotificationBubble } from '@/composables/useNotificationIndicator'

const userStore = useUserStore()

const selectedDate = ref(getYesterday())
const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const searchQuery = ref('')
const selectedCategoryId = ref<number | null>(null)

const priceMap = ref<Map<number, Price>>(new Map())
const yesterdayPriceMap = ref<Map<number, Price>>(new Map())
const monthlyAverageMap = ref<Map<number, number>>(new Map())
const inheritedPriceMap = ref<Map<number, number>>(new Map())
const inheritedBudgetPriceMap = ref<Map<number, number>>(new Map())
const editingPrices = ref<Map<number, string>>(new Map())
const originalPriceTextMap = ref<Map<number, string>>(new Map())
const currentDraft = ref<PriceDraftBatch | null>(null)

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
  return editPrice.trim() !== (originalPriceTextMap.value.get(productId) || '')
}

const applyDraft = (draft: PriceDraftBatch | null) => {
  currentDraft.value = draft
  if (!draft?.items?.length) return
  for (const item of draft.items) {
    const value = String(item.currentPrice)
    editingPrices.value.set(item.productId, value)
    originalPriceTextMap.value.set(item.productId, value)
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const [productRes, categoryRes, priceRes, draftRes] = await Promise.all([
      getProducts({ page: 0, size: 1000, status: 'ACTIVE' }),
      getCategories('ACTIVE'),
      getPricesByDateWithStats(selectedDate.value),
      getPriceDraftByDate(selectedDate.value)
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
    originalPriceTextMap.value = new Map()
    currentDraft.value = null

    const items = priceRes.data || []
    for (const item of items) {
      if (item.price?.product) {
        const productId = item.price.product.id
        priceMap.value.set(productId, item.price)
        if (item.price.currentPrice != null) {
          const value = String(item.price.currentPrice)
          editingPrices.value.set(productId, value)
          originalPriceTextMap.value.set(productId, value)
        } else {
          editingPrices.value.set(productId, '')
          originalPriceTextMap.value.set(productId, '')
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
        originalPriceTextMap.value.set(product.id, '')
      }
    })
    applyDraft(draftRes.data || null)
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

const getProductCurrencySymbol = (productId: number) =>
  getCurrencySymbol(products.value.find(product => product.id === productId)?.currency)

const formatBudgetPrice = (productId: number): string => {
  const price = priceMap.value.get(productId)
  const symbol = getProductCurrencySymbol(productId)
  if (price?.budgetPrice != null) return symbol + price.budgetPrice.toFixed(2)
  const inherited = inheritedBudgetPriceMap.value.get(productId)
  if (inherited != null) return symbol + inherited.toFixed(2)
  const product = products.value.find(p => p.id === productId)
  if (product?.budgetPrice != null) return symbol + product.budgetPrice.toFixed(2)
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

const formatPrice = (productId: number, price: number | null | undefined): string => {
  if (price === null || price === undefined) return '-'
  return `${getProductCurrencySymbol(productId)}${price.toFixed(2)}`
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
  const symbol = getProductCurrencySymbol(productId)
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

const handleSave = async () => {
  if (!hasChanges.value) {
    uni.showToast({ title: '没有修改', icon: 'none' })
    return
  }

  saving.value = true
  let failCount = 0

  try {
    const draftItems = []
    for (const [productId, priceStr] of editingPrices.value) {
      if (!isProductChanged(productId, priceStr)) continue
      if (!priceStr.trim()) continue
      const currentPrice = Number(priceStr)
      if (Number.isNaN(currentPrice)) {
        failCount++
        continue
      }

      const existingPrice = priceMap.value.get(productId)
      const product = products.value.find(item => item.id === productId)
      draftItems.push({
        productId,
        basePriceId: existingPrice?.id,
        basePriceVersion: existingPrice?.version,
        currentPrice,
        budgetPrice: existingPrice?.budgetPrice ?? product?.budgetPrice,
        unit: existingPrice?.unit ?? product?.unit,
        priceSpec: existingPrice?.priceSpec,
        effectiveDate: selectedDate.value
      })
    }

    if (draftItems.length === 0) {
      uni.showToast({ title: failCount > 0 ? `存在 ${failCount} 条无效价格` : '没有可保存的修改', icon: 'none' })
      return
    }

    const response = await savePriceDraft({
      batchId: currentDraft.value?.id,
      batchVersion: currentDraft.value?.version,
      effectiveDate: selectedDate.value,
      items: draftItems
    })
    applyDraft(response.data)
    uni.showToast({ title: `草稿保存成功 ${draftItems.length} 条`, icon: 'none' })
  } catch (error) {
    console.error('保存草稿失败:', error)
    uni.showToast({ title: '草稿保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}

const handlePublish = async () => {
  if (!currentDraft.value?.id) {
    uni.showToast({ title: '请先保存草稿', icon: 'none' })
    return
  }
  if (hasChanges.value) {
    uni.showToast({ title: '请先保存当前修改', icon: 'none' })
    return
  }

  const confirmed = await new Promise<boolean>(resolve => {
    uni.showModal({
      title: '确认发布价格',
      content: `发布后 ${selectedDate.value} 的价格将对所有用户可见，并生成通知。`,
      confirmText: '发布',
      confirmColor: '#0D6E6E',
      success: result => resolve(result.confirm),
      fail: () => resolve(false)
    })
  })
  if (!confirmed) return

  publishing.value = true
  try {
    const response = await publishPriceDraft(currentDraft.value.id)
    uni.showToast({ title: `发布完成 ${response.data.successCount} 条`, icon: 'none' })
    showNotificationBubble('价格已发布，通知已生成')
    await refreshNotificationIndicator(false)
    await loadData()
  } catch (error) {
    console.error('发布价格失败:', error)
    uni.showToast({ title: '发布失败', icon: 'none' })
  } finally {
    publishing.value = false
  }
}

const onDateChange = (e: any) => {
  selectedDate.value = e.detail.value
}

watch(selectedDate, () => {
  Promise.all([loadAllDicts(), loadData()])
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
  width: 100%;
  max-width: 100vw;
  box-sizing: border-box;
  overflow-x: hidden;
}

.header {
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  padding: 24rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  overflow: hidden;
}

.date-picker {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.2);
}

.date-text {
  font-size: 26rpx;
  color: #FFFFFF;
}

.date-icon {
  color: rgba(255, 255, 255, 0.8);
  font-size: 18rpx;
}

.header-status {
  flex-shrink: 0;
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.82);
  font-size: 22rpx;
}

.header-status.changed {
  background: #E07B54;
  color: #FFFFFF;
}

.entry-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16rpx;
  padding: 20rpx 32rpx 0;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.summary-card {
  min-width: 0;
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
  min-width: 0;
  box-sizing: border-box;
  overflow: hidden;
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
  min-width: 0;
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
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
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
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  overflow-x: hidden;
}

.price-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.price-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 20rpx;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.card-main {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.card-header {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.card-seq {
  flex: 0 0 42rpx;
  width: 42rpx;
  height: 42rpx;
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
  display: block;
  overflow: hidden;
  font-size: 30rpx;
  font-weight: 600;
  color: #1A1A1A;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-specs {
  display: block;
  overflow: hidden;
  font-size: 24rpx;
  color: #999999;
  margin-top: 4rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-title {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.price-field {
  flex: 0 0 264rpx;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 6rpx;
}

.price-label {
  align-self: flex-start;
  font-size: 20rpx;
  color: #888888;
}

.price-input-wrapper {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  background: #F9FAFB;
  border: 1px solid #E5E5E5;
  border-radius: 8rpx;
  overflow: hidden;
}

.price-unit {
  font-family: Arial, sans-serif;
  padding: 12rpx 10rpx 12rpx 14rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: #666666;
}

.price-input {
  flex: 1;
  min-width: 0;
  height: 64rpx;
  padding: 0 12rpx;
  font-size: 30rpx;
  font-weight: 500;
  color: #1A1A1A;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
  padding: 14rpx;
  background: #F9FAFB;
  border-radius: 8rpx;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.stat-item {
  min-width: 0;
  text-align: center;
}

.stat-label {
  display: block;
  font-size: 22rpx;
  color: #888888;
  margin-bottom: 8rpx;
}

.stat-value {
  font-family: Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  width: 100%;
  max-width: 100vw;
  box-sizing: border-box;
  overflow: hidden;
}

.save-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.save-title {
  display: block;
  color: #1A1A1A;
  font-size: 28rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.save-desc {
  display: block;
  color: #64748B;
  font-size: 22rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-btn {
  flex: 0 0 132rpx;
  min-width: 132rpx;
  height: 76rpx;
  margin: 0;
  padding: 0;
  box-sizing: border-box;
  border: none;
  border-radius: 12rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 76rpx;
}

.save-btn {
  background: #FFFFFF;
  color: #0D6E6E;
  border: 1rpx solid #0D6E6E;
}

.publish-btn {
  background: #0D6E6E;
  color: #FFFFFF;
}

.action-btn[disabled] {
  background: #CBD5E1;
  border-color: #CBD5E1;
  color: #FFFFFF;
}
</style>
