<script setup lang="ts">
import { ref, onMounted, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getProducts } from '@/api/products'
import { getCategories } from '@/api/categories'
import { usePermission, Permission } from '@/composables/usePermission'
import EmptyState from '@/components/EmptyState.vue'
import { eventBus } from '@/utils/eventBus'
import type { Product, ProductCategory } from '@/types'

const router = useRouter()
const { hasPermission } = usePermission()

const products = ref<Product[]>([])
const loading = ref(false)
const searchQuery = ref('')
const categories = ref<ProductCategory[]>([])
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

// 筛选条件
const filterCategoryId = ref<number | undefined>()
const filterStatus = ref<string | undefined>()
const sortBy = ref('id')
const sortDirection = ref<'asc' | 'desc'>('desc')

// 响应式布局
const isPCLayout = computed(() => windowWidth.value >= 1024)

const activeTab = ref('products')

const loadProducts = async () => {
  loading.value = true
  try {
    const response = await getProducts({
      page: 0,
      size: 100,
      keyword: searchQuery.value || undefined,
      categoryId: filterCategoryId.value,
      status: filterStatus.value as any,
      sortBy: sortBy.value,
      sortDirection: sortDirection.value
    })
    products.value = response.data.content || []
  } catch (error) {
    console.error('Failed to load products:', error)
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    const response = await getCategories('ACTIVE')
    categories.value = response.data || []
  } catch (error) {
    console.error('Failed to load categories:', error)
  }
}

const handleSearch = () => {
  loadProducts()
}

const handleFilterChange = () => {
  loadProducts()
}

const handleSortChange = () => {
  loadProducts()
}

const clearFilters = () => {
  filterCategoryId.value = undefined
  filterStatus.value = undefined
  searchQuery.value = ''
  sortBy.value = 'id'
  sortDirection.value = 'desc'
  loadProducts()
}

const hasActiveFilters = computed(() => {
  return filterCategoryId.value !== undefined || filterStatus.value !== undefined || searchQuery.value !== ''
})

// 空状态类型
const emptyStateType = computed(() => {
  if (loading.value) return 'loading'
  if (hasActiveFilters.value && products.value.length === 0) return 'no-result'
  return 'no-data'
})

const handleEmptyAction = () => {
  if (emptyStateType.value === 'no-result') {
    clearFilters()
  } else {
    loadProducts()
  }
}

const viewProduct = (product: Product) => {
  router.push(`/product-detail/${product.id}`)
}

const editProduct = (product: Product) => {
  router.push(`/product-edit/${product.id}`)
}

const addProduct = () => {
  router.push('/product-edit')
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

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadCategories()
  loadProducts()
  eventBus.on('prices-updated', loadProducts)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  eventBus.off('prices-updated', loadProducts)
})
</script>

<template>
  <div class="products-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-products">
        <!-- 页面标题 -->
        <div class="page-header-pc">
          <h1 class="page-title-pc">产品列表</h1>
          <button class="btn-primary" @click="addProduct" v-if="hasPermission(Permission.PRODUCT_CREATE)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            新增产品
          </button>
        </div>

        <!-- 搜索和筛选区域 -->
        <div class="search-section-pc">
          <div class="search-box-pc">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="11" cy="11" r="8"/>
              <line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索产品名称..."
              class="search-input-pc"
              @keyup.enter="handleSearch"
            />
            <button v-if="searchQuery" class="search-clear" @click="searchQuery = ''; loadProducts()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
            </button>
          </div>
          <button class="btn-search" @click="handleSearch">
            搜索
          </button>
        </div>

        <!-- 筛选工具栏 -->
        <div class="filter-toolbar-pc">
          <div class="filter-group-pc">
            <select v-model="filterCategoryId" class="filter-select-pc" @change="handleFilterChange">
              <option :value="undefined">全部分类</option>
              <option v-for="cat in categories" :key="cat.id" :value="cat.id">
                {{ cat.name }}
              </option>
            </select>

            <select v-model="filterStatus" class="filter-select-pc" @change="handleFilterChange">
              <option :value="undefined">全部状态</option>
              <option value="ACTIVE">展示</option>
              <option value="INACTIVE">隐藏</option>
            </select>

            <select v-model="sortBy" class="filter-select-pc" @change="handleSortChange">
              <option value="id">按ID排序</option>
              <option value="name">按名称排序</option>
              <option value="sortOrder">按排序号排序</option>
              <option value="createdTime">按创建时间排序</option>
            </select>

            <select v-model="sortDirection" class="filter-select-pc" @change="handleSortChange">
              <option value="desc">降序</option>
              <option value="asc">升序</option>
            </select>

            <button v-if="hasActiveFilters" class="btn-clear-filters-pc" @click="clearFilters">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18"/>
                <line x1="6" y1="6" x2="18" y2="18"/>
              </svg>
              清除筛选
            </button>
          </div>

          <div class="result-count-pc">
            共 {{ products.length }} 个产品
          </div>
        </div>

        <!-- 产品表格 -->
        <div class="product-table-pc" v-if="!loading">
          <div class="table-header">
            <div class="table-cell seq">序号</div>
            <div class="table-cell name">产品名称</div>
            <div class="table-cell unit">计量单位</div>
            <div class="table-cell specs">产品规格</div>
            <div class="table-cell description">产品描述</div>
            <div class="table-cell category">分类</div>
            <div class="table-cell status">状态</div>
            <div class="table-cell actions">操作</div>
          </div>
          <div
            v-for="(product, index) in products"
            :key="product.id"
            class="table-row"
            @click="viewProduct(product)"
          >
            <div class="table-cell seq">{{ index + 1 }}</div>
            <div class="table-cell name">{{ product.name }}</div>
            <div class="table-cell unit">{{ product.unit || '-' }}</div>
            <div class="table-cell specs"><span class="scroll-text">{{ product.specs || '-' }}</span></div>
            <div class="table-cell description"><span class="scroll-text">{{ product.description || '-' }}</span></div>
            <div class="table-cell category">{{ product.category?.name || '-' }}</div>
            <div class="table-cell status">
              <span class="status-badge" :class="product.status?.toLowerCase()">
                {{ product.status === 'ACTIVE' ? '启用' : '停用' }}
              </span>
            </div>
            <div class="table-cell actions" @click.stop>
              <button class="action-btn" @click="viewProduct(product)">查看</button>
              <button v-if="hasPermission(Permission.PRODUCT_EDIT)" class="action-btn edit" @click="editProduct(product)">编辑</button>
            </div>
          </div>
          <EmptyState
            v-if="products.length === 0"
            :type="emptyStateType"
            :title="emptyStateType === 'no-result' ? '搜索无结果' : '暂无产品'"
            :description="emptyStateType === 'no-result' ? '没有找到匹配的产品' : '还没有添加任何产品'"
            :action-text="emptyStateType === 'no-result' ? '清除筛选' : ''"
            @action="handleEmptyAction"
          />
        </div>
        <div v-else class="loading-state-pc">
          <div class="loading-spinner"></div>
        </div>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 顶部导航栏 -->
      <header class="navbar">
        <div class="navbar-left">
          <h1 class="navbar-title">产品列表</h1>
        </div>
      </header>

      <!-- 主内容区 -->
      <main class="content">
        <!-- 搜索框 -->
        <div class="search-bar">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索产品名称..."
            class="search-input"
            @keyup.enter="handleSearch"
          />
          <button v-if="searchQuery" class="search-clear" @click="searchQuery = ''; loadProducts()">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        <!-- 产品列表 -->
        <div class="product-list" v-if="!loading">
          <div
            v-for="(product, index) in products"
            :key="product.id"
            class="product-item"
            @click="viewProduct(product)"
          >
            <div class="product-main">
              <div class="product-info">
                <span class="product-name">{{ index + 1 }}. {{ product.name }}</span>
                <span class="product-specs" v-if="product.specs">{{ product.specs }}</span>
              </div>
              <div class="product-status" :class="product.status?.toLowerCase()">
                {{ product.status === 'ACTIVE' ? '启用' : '停用' }}
              </div>
            </div>
            <div class="product-meta" v-if="product.unit || product.description">
              <div class="meta-row" v-if="product.unit">
                <span class="meta-label">单位</span>
                <span class="meta-value">{{ product.unit }}</span>
              </div>
              <div class="meta-row" v-if="product.description">
                <span class="meta-label">描述</span>
                <span class="meta-value">{{ product.description }}</span>
              </div>
            </div>
            <div class="product-actions" v-if="hasPermission(Permission.PRODUCT_EDIT)">
              <button class="action-btn edit" @click.stop="editProduct(product)">
                编辑
              </button>
            </div>
          </div>
          <EmptyState
            v-if="products.length === 0"
            :type="emptyStateType"
            :title="emptyStateType === 'no-result' ? '搜索无结果' : '暂无产品'"
            :description="emptyStateType === 'no-result' ? '没有找到匹配的产品' : hasPermission(Permission.PRODUCT_CREATE) ? '点击下方按钮添加产品' : '暂无产品数据'"
            :action-text="emptyStateType === 'no-result' ? '清除筛选' : (hasPermission(Permission.PRODUCT_CREATE) ? '添加产品' : '')"
            @action="emptyStateType === 'no-result' ? handleEmptyAction : (hasPermission(Permission.PRODUCT_CREATE) ? addProduct : handleEmptyAction)"
          />
        </div>
        <div v-else class="loading-state">
          <div class="loading-spinner"></div>
          <span>加载中...</span>
        </div>
      </main>

      <!-- 底部标签栏 -->
      <footer class="tab-bar">
        <button class="tab-item" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item active" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" @click="switchTab('profile')">
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
.products-page {
  background-color: var(--gray-100);
}

/* ==================== PC布局 ==================== */
.pc-products {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.page-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.page-title-pc {
  font-family: var(--font-heading),serif;
  font-size: 24px;
  font-weight: 500;
  color: var(--text-primary);
  margin: 0;
}

.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 10px 20px;
  background: var(--primary-color);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.btn-primary:hover {
  background: var(--primary-light);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.search-section-pc {
  display: flex;
  gap: var(--spacing-md);
}

.search-box-pc {
  flex: 1;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 10px var(--spacing-md);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
}

.search-box-pc svg {
  color: var(--text-muted);
  flex-shrink: 0;
}

.search-input-pc {
  flex: 1;
  border: none;
  background: transparent;
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
}

.search-input-pc::placeholder {
  color: var(--gray-400);
}

.search-clear {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-clear:hover {
  color: var(--text-primary);
}

.btn-search {
  padding: 10px 20px;
  background: var(--primary-color);
  color: #FFFFFF;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.btn-search:hover {
  background: var(--primary-light);
}

/* 筛选工具栏 */
.filter-toolbar-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.filter-group-pc {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.filter-select-pc {
  height: 36px;
  padding: 0 var(--spacing-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  font-family: var(--font-body), sans-serif;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
  outline: none;
  transition: border-color var(--transition-fast);
}

.filter-select-pc:focus {
  border-color: var(--primary-color);
}

.btn-clear-filters-pc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 36px;
  padding: 0 var(--spacing-sm);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  font-family: var(--font-body), sans-serif;
  font-size: 13px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-clear-filters-pc:hover {
  color: var(--error-color);
  border-color: var(--error-color);
}

.result-count-pc {
  font-family: var(--font-body), sans-serif;
  font-size: 13px;
  color: var(--text-muted);
}

.product-table-pc {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  overflow: hidden;
}

.table-header {
  display: flex;
  background: var(--gray-50);
  padding: 12px var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  font-family: var(--font-body), sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.table-row {
  display: flex;
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--gray-100);
  cursor: pointer;
  transition: background-color var(--transition-fast);
}

.table-row:last-child {
  border-bottom: none;
}

.table-row:hover {
  background: var(--gray-50);
}

.table-cell {
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
  color: var(--text-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.table-header .table-cell {
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 13px;
  justify-content: center;
  text-align: center;
}

.table-cell.seq {
  flex: 0 0 50px;
  justify-content: center;
  color: var(--text-muted);
  font-size: 13px;
}

.table-cell.name {
  flex: 2;
  font-weight: 500;
  min-width: 120px;
}

.table-cell.unit {
  flex: 0 0 80px;
  font-size: 14px;
  color: var(--text-secondary);
}

.table-cell.specs {
  flex: 2;
  font-size: 14px;
  color: var(--text-secondary);
  overflow: hidden;
  white-space: nowrap;
  justify-content: flex-start;
  padding-left: 8px;
}

.table-header .table-cell.specs {
  justify-content: center;
  padding-left: 0;
}

.table-cell.specs:hover .scroll-text {
  animation: marquee-scroll 4s ease-in-out infinite alternate;
}

.table-cell.description {
  flex: 2.5;
  font-size: 14px;
  color: var(--text-secondary);
  overflow: hidden;
  white-space: nowrap;
  justify-content: flex-start;
  padding-left: 8px;
}

.table-cell.description:hover .scroll-text {
  animation: marquee-scroll 4s ease-in-out infinite alternate;
}

.scroll-text {
  display: inline-block;
  white-space: nowrap;
}

@keyframes marquee-scroll {
  from { transform: translateX(0); }
  to { transform: translateX(calc(-100%)); }
}

.table-cell.category {
  flex: 0 0 80px;
  color: var(--text-secondary);
}

.table-cell.status {
  flex: 0 0 60px;
  justify-content: center;
}

.table-header .table-cell.actions {
  justify-content: center;
}

.table-cell.actions {
  flex: 0 0 120px;
  justify-content: center;
  gap: var(--spacing-sm);
}

.status-badge {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-family: var(--font-body), sans-serif;
  font-size: 12px;
  font-weight: 500;
}

.status-badge.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.status-badge.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.action-btn {
  padding: 6px 14px;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body), sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  background: var(--gray-100);
  color: var(--text-secondary);
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--gray-200);
}

.action-btn.edit {
  background: var(--primary-bg);
  color: var(--primary-color);
}

.action-btn.edit:hover {
  background: rgba(13, 110, 110, 0.15);
}

.loading-state-pc {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl);
  color: var(--text-muted);
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--gray-200);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-md);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ==================== 移动端布局 ==================== */
.navbar {
  height: 56px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--spacing-md);
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-title {
  font-family: var(--font-heading), serif;
  font-size: 20px;
  font-weight: 500;
  color: var(--text-primary);
  margin: 0;
}

.content {
  flex: 1;
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  padding-bottom: 100px;
}

.search-bar {
  height: 44px;
  background: var(--gray-100);
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-sm);
  gap: var(--spacing-sm);
}

.search-bar svg {
  color: var(--text-muted);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
  color: var(--text-primary);
  outline: none;
}

.search-input::placeholder {
  color: var(--gray-400);
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.product-item {
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: border-color var(--transition-fast);
}

.product-item:hover {
  border-color: var(--primary-color);
}

.product-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--spacing-sm);
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.product-name {
  font-family: var(--font-body), sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  line-height: 1.4;
}

.product-specs {
  font-family: var(--font-body), sans-serif;
  font-size: 13px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-status {
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  font-family: var(--font-body), sans-serif;
  font-size: 12px;
  font-weight: 500;
  flex-shrink: 0;
  margin-left: var(--spacing-sm);
}

.product-status.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.product-status.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.product-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0;
  margin-bottom: var(--spacing-sm);
  border-top: 1px solid var(--gray-100);
  border-bottom: 1px solid var(--gray-100);
}

.meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
}

.meta-label {
  color: var(--text-muted);
  flex-shrink: 0;
}

.meta-value {
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 60%;
  text-align: right;
}

.meta-value.price {
  color: var(--primary-color);
  font-weight: 600;
  font-family: var(--font-mono), monospace;
  font-size: 15px;
}

.product-actions {
  display: flex;
  gap: var(--spacing-sm);
  justify-content: flex-end;
}

.action-btn.edit {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius);
  font-family: var(--font-body), sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  background: var(--primary-bg);
  color: var(--primary-color);
  transition: all var(--transition-fast);
}

.action-btn.edit:hover {
  background: rgba(13, 110, 110, 0.15);
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl) var(--spacing-md);
  color: var(--text-muted);
  gap: var(--spacing-sm);
}

.empty-state svg {
  color: var(--gray-300);
}

.empty-state p {
  font-family: var(--font-body), sans-serif;
  font-size: 14px;
  margin: 0;
}

.tab-bar {
  height: 64px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
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
  padding: 8px var(--spacing-md);
  border-radius: var(--radius);
  color: var(--gray-400);
  transition: color var(--transition-fast);
}

.tab-item.active {
  color: var(--primary-color);
}

.tab-label {
  font-family: var(--font-body), sans-serif;
  font-size: 10px;
  font-weight: 500;
}

@media (max-width: 1024px) {
  .pc-products {
    display: none;
  }
}
</style>
