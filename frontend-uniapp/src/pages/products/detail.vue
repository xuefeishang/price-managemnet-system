<template>
  <view class="detail-page">
    <view v-if="loading" class="state-panel">
      <text>正在加载产品详情</text>
    </view>

    <view v-else-if="!product" class="state-panel">
      <text>产品不存在或已删除</text>
      <button class="back-btn" @click="goBack">返回上一页</button>
    </view>

    <view v-else class="detail-content">
      <view class="product-hero">
        <view class="hero-tags">
          <text class="status-tag" :class="{ inactive: product.status === 'INACTIVE' }">
            {{ statusLabel }}
          </text>
          <text v-if="product.showOnHome" class="home-tag">首页展示</text>
        </view>
        <text class="product-name">{{ product.name }}</text>
        <text class="product-meta">{{ productMeta }}</text>
        <text class="product-description">{{ product.description || product.remark || '暂无产品描述' }}</text>

        <view class="price-grid">
          <view class="price-metric primary">
            <text class="metric-label">最新价格</text>
            <text class="metric-value">{{ formatPrice(displayPrice) }}</text>
            <text class="metric-note">{{ currentPrice?.effectiveDate ? `${formatDate(currentPrice.effectiveDate)} 生效` : '当前有效价格' }}</text>
          </view>
          <view class="price-metric">
            <text class="metric-label">预算价格</text>
            <text class="metric-value">{{ formatPrice(budgetPrice) }}</text>
            <text class="metric-note">差额 {{ formatSignedPrice(budgetDifference) }}</text>
          </view>
        </view>
      </view>

      <view class="info-card trend-card">
        <view class="section-header">
          <view>
            <text class="section-title">价格走势</text>
            <text class="section-subtitle">价格与预算价格趋势参考</text>
          </view>
        </view>
        <price-trend-chart
          :productId="productId"
          :currency-symbol="currencySymbol"
          :budget-price="budgetPrice"
        />
      </view>

      <view class="info-card">
        <view class="section-header">
          <view>
            <text class="section-title">基础资料</text>
            <text class="section-subtitle">产品识别与适用范围</text>
          </view>
          <text class="completeness">{{ completeness }}%</text>
        </view>
        <view class="progress"><view class="progress-value" :style="{ width: `${completeness}%` }" /></view>
        <view class="info-list">
          <view v-for="item in infoItems" :key="item.label" class="info-row">
            <text class="info-label">{{ item.label }}</text>
            <text class="info-value">{{ item.value }}</text>
          </view>
        </view>
      </view>

      <view v-if="userStore.canEdit" class="pc-tip">
        <text>产品资料维护请前往 PC 端完成。</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/useUserStore'
import { getCurrentPrice, getProductById } from '@/api/products'
import { getCurrencySymbol, getDictValue, loadAllDicts } from '@/composables/useDict'
import type { Price, Product } from '@/types'

const userStore = useUserStore()
const product = ref<Product | null>(null)
const currentPrice = ref<Price | null>(null)
const loading = ref(true)
const productId = ref(0)

const parseDictIds = (value?: string) => {
  if (!value) return []
  try {
    const result = JSON.parse(value)
    return Array.isArray(result) ? result : []
  } catch {
    return []
  }
}

const statusLabel = computed(() => product.value ? getDictValue('common_status', product.value.status) : '-')
const unitLabel = computed(() => product.value?.unit ? getDictValue('unit', product.value.unit) : '-')
const currencyLabel = computed(() => product.value?.currency ? getDictValue('currency', product.value.currency) : '-')
const currencySymbol = computed(() => getCurrencySymbol(product.value?.currency))
const originNames = computed(() =>
  parseDictIds(product.value?.originIds).map(key => getDictValue('origin', key)).filter(Boolean).join('、') || '-'
)
const customerNames = computed(() =>
  parseDictIds(product.value?.customerIds).map(key => getDictValue('customer', key)).filter(Boolean).join('、') || '-'
)
const displayPrice = computed(() => currentPrice.value?.currentPrice ?? product.value?.sellingPrice ?? null)
const budgetPrice = computed(() => currentPrice.value?.budgetPrice ?? product.value?.budgetPrice ?? null)
const budgetDifference = computed(() => {
  if (displayPrice.value == null || budgetPrice.value == null) return null
  return Number(displayPrice.value) - Number(budgetPrice.value)
})
const productMeta = computed(() =>
  [product.value?.category?.name, product.value?.specs, unitLabel.value, originNames.value]
    .filter(value => value && value !== '-')
    .join(' · ') || '暂无产品规格信息'
)
const completeness = computed(() => {
  if (!product.value) return 0
  const values = [
    product.value.name,
    product.value.category?.name,
    product.value.specs,
    product.value.unit,
    product.value.originIds,
    product.value.customerIds,
    product.value.description,
    product.value.currency
  ]
  return Math.round((values.filter(Boolean).length / values.length) * 100)
})
const infoItems = computed(() => [
  { label: '所属分类', value: product.value?.category?.name || '-' },
  { label: '规格型号', value: product.value?.specs || '-' },
  { label: '计量单位', value: unitLabel.value },
  { label: '产地', value: originNames.value },
  { label: '报价适用客户', value: customerNames.value },
  { label: '币种', value: currencyLabel.value }
])

const formatPrice = (value?: number | null) =>
  value == null ? '-' : `${currencySymbol.value}${Number(value).toFixed(2)}`

const formatSignedPrice = (value?: number | null) => {
  if (value == null) return '-'
  return `${value > 0 ? '+' : ''}${currencySymbol.value}${Number(value).toFixed(2)}`
}

const formatDate = (dateStr: string) => dateStr ? dateStr.split('T')[0] : '-'

const loadProduct = async () => {
  if (!productId.value) return
  loading.value = true
  try {
    const [productRes, priceRes] = await Promise.all([
      getProductById(productId.value, false),
      getCurrentPrice(productId.value, false)
    ])
    product.value = productRes.data || null
    currentPrice.value = priceRes.data || null
  } catch (error) {
    product.value = null
    console.error('加载产品详情失败:', error)
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: '/pages/home/index' })
}

onLoad((options) => {
  if (options?.id) productId.value = Number(options.id)
})

onMounted(async () => {
  userStore.restoreSession()
  await Promise.all([loadAllDicts(), loadProduct()])
})
</script>

<style scoped>
.detail-page {
  min-height: 100vh;
  padding: 24rpx 28rpx 120rpx;
  box-sizing: border-box;
  background: #F4F6F8;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.state-panel {
  min-height: 720rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28rpx;
  color: #667085;
  font-size: 28rpx;
}

.product-hero {
  padding: 28rpx;
  border-radius: 16rpx;
  background: #0A5555;
}

.hero-tags {
  display: flex;
  gap: 12rpx;
  margin-bottom: 18rpx;
}

.status-tag,
.home-tag {
  padding: 8rpx 14rpx;
  border-radius: 10rpx;
  font-size: 21rpx;
  font-weight: 650;
}

.status-tag {
  background: #E7F3F3;
  color: #0D6E6E;
}

.status-tag.inactive {
  background: #FDECEC;
  color: #C7524A;
}

.home-tag {
  background: rgba(255, 255, 255, .14);
  color: #FFFFFF;
}

.product-name {
  display: block;
  color: #FFFFFF;
  font-size: 42rpx;
  font-weight: 750;
  line-height: 1.25;
}

.product-meta,
.product-description {
  display: block;
  margin-top: 10rpx;
  color: #B8D8D8;
  font-size: 23rpx;
  line-height: 1.5;
}

.product-description {
  color: #D0E4E4;
}

.price-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx;
  margin-top: 24rpx;
}

.price-metric {
  min-width: 0;
  padding: 20rpx;
  border-radius: 12rpx;
  background: #FFFFFF;
}

.metric-label,
.metric-note {
  display: block;
  color: #667085;
  font-size: 21rpx;
}

.metric-value {
  font-family: Arial, sans-serif;
  font-variant-numeric: tabular-nums;
  display: block;
  margin: 8rpx 0;
  overflow: hidden;
  color: #1A1A1A;
  font-size: 28rpx;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.price-metric.primary .metric-value {
  color: #0D6E6E;
}

.metric-note {
  color: #98A2B3;
  font-size: 19rpx;
}

.info-card {
  padding: 24rpx;
  border: 1px solid #E4E7EC;
  border-radius: 16rpx;
  background: #FFFFFF;
}

.trend-card {
  padding-bottom: 14rpx;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 20rpx;
}

.section-title,
.section-subtitle {
  display: block;
}

.section-title {
  color: #1A1A1A;
  font-size: 28rpx;
  font-weight: 700;
}

.section-subtitle {
  margin-top: 5rpx;
  color: #98A2B3;
  font-size: 20rpx;
}

.completeness {
  color: #0D6E6E;
  font-size: 26rpx;
  font-weight: 750;
}

.progress {
  height: 12rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: #EAECF0;
}

.progress-value {
  height: 100%;
  border-radius: inherit;
  background: #0D6E6E;
}

.info-list {
  margin-top: 14rpx;
}

.info-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 18rpx 0;
  border-bottom: 1px solid #EAECF0;
}

.info-row:last-child {
  border-bottom: 0;
}

.info-label {
  flex: 0 0 auto;
  color: #667085;
  font-size: 24rpx;
}

.info-value {
  flex: 1;
  color: #1A1A1A;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.45;
  text-align: right;
}

.pc-tip {
  padding: 22rpx;
  border: 1px solid #D7E7E7;
  border-radius: 16rpx;
  background: #F0FAFA;
  color: #0D6E6E;
  font-size: 24rpx;
  line-height: 1.6;
}

.back-btn {
  width: 240rpx;
  height: 72rpx;
  border: 0;
  border-radius: 12rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 26rpx;
}
</style>
