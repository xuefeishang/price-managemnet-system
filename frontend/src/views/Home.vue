<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useRouter } from 'vue-router'
import { getProducts } from '@/api/products'
import { getCategories } from '@/api/categories'
import { getPricesByDate } from '@/api/products'
import { usePermission, Permission } from '@/composables/usePermission'
import type { Product, ProductCategory, Price } from '@/types'

const userStore = useUserStore()
const router = useRouter()
const { hasPermission } = usePermission()

const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const loading = ref(false)
const searchQuery = ref('')

// 选中的日期
const selectedDate = ref(new Date().toISOString().split('T')[0])

// 价格映射 (productId -> price)
const priceMap = ref<Map<number, Price>>(new Map())

// 判断是否为PC布局
const isPCLayout = computed(() => {
  if (typeof window !== 'undefined') {
    return window.innerWidth >= 1024
  }
  return false
})

const activeTab = ref('home')

// 统计数据
const stats = computed(() => {
  const activeProducts = products.value.filter(p => p.status === 'ACTIVE').length
  return {
    totalProducts: products.value.length,
    activeProducts,
    totalCategories: categories.value.length
  }
})

// 过滤后的产品
const filteredProducts = computed(() => {
  if (!searchQuery.value) return products.value.slice(0, 6)
  return products.value
    .filter(p => p.name.includes(searchQuery.value))
    .slice(0, 6)
})

// 获取产品的当日价格
const getTodayPrice = (productId: number) => {
  return priceMap.value.get(productId)
}

// 格式化日期显示
const formatDateDisplay = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

// 加载数据
const loadData = async () => {
  loading.value = true
  try {
    const [productsRes, categoriesRes, pricesRes] = await Promise.all([
      getProducts({ page: 0, size: 1000 }),
      getCategories(),
      getPricesByDate(selectedDate.value)
    ])
    products.value = productsRes.data.content || []
    categories.value = categoriesRes.data || []

    // 处理价格数据
    const prices = pricesRes.data || []
    priceMap.value.clear()
    prices.forEach((price: Price) => {
      if (price.product?.id) {
        priceMap.value.set(price.product.id, price)
      }
    })
  } catch (error) {
    console.error('Failed to load data:', error)
  } finally {
    loading.value = false
  }
}

// 日期变化时重新加载
const onDateChange = () => {
  loadData()
}

// 导航
const navigateTo = (path: string) => {
  router.push(path)
}

// Tab切换（移动端）
const switchTab = (tab: string) => {
  activeTab.value = tab
  switch (tab) {
    case 'home':
      router.push('/home')
      break
    case 'products':
      router.push('/products')
      break
    case 'import':
      router.push('/import')
      break
    case 'profile':
      router.push('/profile')
      break
  }
}

// 跳转到产品详情
const viewProduct = (product: Product) => {
  router.push(`/product-detail/${product.id}`)
}

// 跳转到价格维护
const goToPriceMaintenance = () => {
  router.push('/price-maintenance')
}

onMounted(() => {
  loadData()
})
</script>

<template>
  <div class="home-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-home">
        <!-- 页面标题 -->
        <div class="page-header-pc">
          <div class="header-left-pc">
            <h1 class="page-title-pc">{{ formatDateDisplay(selectedDate) }} 价格概览</h1>
            <div class="date-picker-wrapper">
              <input
                type="date"
                v-model="selectedDate"
                @change="onDateChange"
                class="date-input-pc"
              />
            </div>
          </div>
          <button class="btn-primary-pc" @click="goToPriceMaintenance" v-if="hasPermission(Permission.PRODUCT_EDIT)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            价格维护
          </button>
        </div>

        <!-- 概览卡片区域 -->
        <div class="overview-section-pc">
          <div class="overview-card-pc">
            <div class="overview-card-label">产品总数</div>
            <div class="overview-card-value">{{ stats.totalProducts }}</div>
          </div>
          <div class="overview-card-pc">
            <div class="overview-card-label">展示产品</div>
            <div class="overview-card-value success">{{ stats.activeProducts }}</div>
          </div>
          <div class="overview-card-pc">
            <div class="overview-card-label">隐藏产品</div>
            <div class="overview-card-value danger">{{ stats.totalProducts - stats.activeProducts }}</div>
          </div>
          <div class="overview-card-pc">
            <div class="overview-card-label">产品分类</div>
            <div class="overview-card-value">{{ stats.totalCategories }}</div>
          </div>
        </div>

        <!-- 产品列表区域 -->
        <div class="product-section-pc">
          <div class="section-header-pc">
            <h2 class="section-title-pc">产品列表</h2>
            <div class="search-box-pc">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="11" cy="11" r="8"/>
                <line x1="21" y1="21" x2="16.65" y2="16.65"/>
              </svg>
              <input
                v-model="searchQuery"
                type="text"
                placeholder="搜索产品..."
                class="search-input-pc"
              />
            </div>
          </div>

          <!-- PC产品网格 -->
          <div class="product-grid-pc" v-if="!loading">
            <div
              v-for="product in filteredProducts"
              :key="product.id"
              class="product-card-pc"
              @click="viewProduct(product)"
            >
              <div class="product-card-header">
                <span class="product-name">{{ product.name }}</span>
                <span class="product-status" :class="product.status?.toLowerCase()">
                  {{ product.status === 'ACTIVE' ? '展示' : '隐藏' }}
                </span>
              </div>
              <div class="product-specs" v-if="product.specs">{{ product.specs }}</div>
              <div class="product-price" v-if="getTodayPrice(product.id)">
                <span class="price-current">¥{{ getTodayPrice(product.id)?.currentPrice }}</span>
                <span class="price-unit">{{ getTodayPrice(product.id)?.unit || '元' }}</span>
              </div>
              <div class="product-price empty" v-else>
                暂无价格
              </div>
            </div>
            <div v-if="filteredProducts.length === 0" class="empty-state-pc">
              暂无产品数据
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 顶部导航栏 -->
      <header class="navbar">
        <div class="navbar-left">
          <h1 class="navbar-title">{{ formatDateDisplay(selectedDate) }}</h1>
        </div>
        <div class="navbar-right">
          <input
            type="date"
            v-model="selectedDate"
            @change="onDateChange"
            class="date-input-mobile"
          />
        </div>
      </header>

      <!-- 主内容区 -->
      <main class="content">
        <!-- 日期选择提示 -->
        <div class="date-tip">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/>
            <line x1="8" y1="2" x2="8" y2="6"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          <span>{{ formatDateDisplay(selectedDate) }} 价格</span>
        </div>

        <!-- 概览卡片 -->
        <div class="overview-card">
          <div class="card-header">
            <span class="card-label">今日概况</span>
          </div>
          <div class="stats-row">
            <div class="stat-item">
              <span class="stat-value">{{ stats.totalProducts }}</span>
              <span class="stat-label">产品总数</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.activeProducts }}</span>
              <span class="stat-label">展示产品</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.totalCategories }}</span>
              <span class="stat-label">产品分类</span>
            </div>
          </div>
        </div>

        <!-- 产品列表区域 -->
        <div class="product-section">
          <div class="section-header">
            <h2 class="section-title">产品列表</h2>
            <button class="add-btn" @click="goToPriceMaintenance" v-if="hasPermission(Permission.PRODUCT_EDIT)">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
          </div>

          <!-- 搜索框 -->
          <div class="search-bar">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索产品..."
              class="search-input"
            />
          </div>

          <!-- 产品列表 -->
          <div class="product-list" v-if="!loading">
            <div
              v-for="product in filteredProducts"
              :key="product.id"
              class="product-item"
              @click="viewProduct(product)"
            >
              <div class="product-info">
                <span class="product-name">{{ product.name }}</span>
                <span class="product-specs" v-if="product.specs">{{ product.specs }}</span>
              </div>
              <div class="product-price-display" v-if="getTodayPrice(product.id)">
                <span class="price-current">¥{{ getTodayPrice(product.id)?.currentPrice }}</span>
                <span class="price-unit">{{ getTodayPrice(product.id)?.unit || '元' }}</span>
              </div>
              <div class="product-price-display empty" v-else>
                暂无价格
              </div>
            </div>
            <div v-if="filteredProducts.length === 0" class="empty-state">
              暂无产品数据
            </div>
          </div>
          <div v-else class="loading-state">
            <div class="loading-spinner"></div>
          </div>
        </div>
      </main>

      <!-- 底部标签栏 -->
      <footer class="tab-bar">
        <button class="tab-item" :class="{ active: activeTab === 'home' }" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'products' }" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'import' }" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'profile' }" @click="switchTab('profile')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span class="tab-label">我的</span>
        </button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.home-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

/* ==================== PC布局 ==================== */
.pc-home {
  padding: 32px;
}

.page-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.header-left-pc {
  display: flex;
  align-items: center;
  gap: 20px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.date-picker-wrapper {
  display: flex;
  align-items: center;
}

.date-input-pc {
  padding: 8px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  background: white;
  cursor: pointer;
  outline: none;
}

.date-input-pc:focus {
  border-color: #0D6E6E;
}

.overview-section-pc {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
  margin-bottom: 32px;
}

.overview-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.overview-card-label {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  margin-bottom: 8px;
}

.overview-card-value {
  font-family: 'Inter', sans-serif;
  font-size: 32px;
  font-weight: 600;
  color: #1A1A1A;
}

.overview-card-value.success {
  color: #10B981;
}

.overview-card-value.danger {
  color: #EF4444;
}

.product-section-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.section-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.section-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0;
}

.search-box-pc {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #F5F5F5;
  border-radius: 8px;
  width: 240px;
}

.search-box-pc svg {
  color: #888888;
  flex-shrink: 0;
}

.search-input-pc {
  flex: 1;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  outline: none;
}

.search-input-pc::placeholder {
  color: #888888;
}

.product-grid-pc {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.product-card-pc {
  background: #FAFAFA;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 150ms;
  border: 1px solid transparent;
}

.product-card-pc:hover {
  border-color: #0D6E6E;
  background: #FFFFFF;
}

.product-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.product-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
}

.product-status {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.product-status.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.product-status.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.product-specs {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #666666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.product-price .price-current {
  font-family: 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #0D6E6E;
}

.product-price .price-unit {
  font-size: 12px;
  color: #888888;
}

.product-price.empty {
  font-size: 13px;
  color: #CCCCCC;
}

.empty-state-pc {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px;
  color: #888888;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

/* ==================== 移动端布局 ==================== */
.navbar {
  height: 56px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-left {
  display: flex;
  align-items: center;
}

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 20px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.date-input-mobile {
  padding: 6px 10px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: 12px;
  color: #1A1A1A;
  background: white;
  cursor: pointer;
  outline: none;
}

.date-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #0D6E6E;
  color: white;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
}

.date-tip svg {
  flex-shrink: 0;
}

.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 32px;
  padding-bottom: 100px;
}

.overview-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.card-header {
  margin-bottom: 16px;
}

.card-label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  color: #888888;
  letter-spacing: 2px;
  text-transform: uppercase;
}

.stats-row {
  display: flex;
  gap: 16px;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-family: 'Inter', sans-serif;
  font-size: 24px;
  font-weight: 600;
  color: #1A1A1A;
}

.stat-label {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #888888;
}

.product-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.add-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #0D6E6E;
  color: #FFFFFF;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.add-btn:hover {
  background: #0D8A8A;
}

.search-bar {
  height: 44px;
  background: #F0F0F0;
  border-radius: 8px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  gap: 8px;
}

.search-bar svg {
  color: #888888;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  outline: none;
}

.search-input::placeholder {
  color: #888888;
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-item {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
}

.product-item:hover {
  border-color: #0D6E6E;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-price-display {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.product-price-display .price-current {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #0D6E6E;
}

.product-price-display .price-unit {
  font-size: 11px;
  color: #888888;
}

.product-price-display.empty {
  font-size: 12px;
  color: #CCCCCC;
}

.product-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
}

.product-status {
  padding: 4px 8px;
  border-radius: 4px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
}

.product-status.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.product-status.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32px;
  color: #888888;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.tab-bar {
  height: 64px;
  background: #FFFFFF;
  border-top: 1px solid #E5E5E5;
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 0 20px;
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  max-width: 100%;
  z-index: 100;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  color: #AAAAAA;
}

.tab-item.active {
  color: #0D6E6E;
}

.tab-label {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 500;
}

@media (max-width: 1024px) {
  .pc-home {
    display: none;
  }
}
</style>
