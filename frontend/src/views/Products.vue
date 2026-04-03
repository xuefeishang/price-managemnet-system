<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useRouter } from 'vue-router'
import { getProducts } from '@/api/products'
import { getCategories } from '@/api/categories'
import { usePermission, Permission } from '@/composables/usePermission'
import EmptyState from '@/components/EmptyState.vue'
import type { Product, ProductCategory } from '@/types'

const userStore = useUserStore()
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
          <button class="btn-primary-pc" @click="addProduct" v-if="hasPermission(Permission.PRODUCT_CREATE)">
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
              placeholder="搜索产品名称或编码..."
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
          <button class="btn-search-pc" @click="handleSearch">
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
              <option value="code">按编码排序</option>
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
            <div class="table-cell name">产品名称</div>
            <div class="table-cell code">产品编码</div>
            <div class="table-cell category">分类</div>
            <div class="table-cell status">状态</div>
            <div class="table-cell actions">操作</div>
          </div>
          <div
            v-for="product in products"
            :key="product.id"
            class="table-row"
            @click="viewProduct(product)"
          >
            <div class="table-cell name">{{ product.name }}</div>
            <div class="table-cell code">{{ product.code }}</div>
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
            placeholder="搜索产品名称或编码..."
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
            v-for="product in products"
            :key="product.id"
            class="product-item"
            @click="viewProduct(product)"
          >
            <div class="product-main">
              <div class="product-info">
                <span class="product-name">{{ product.name }}</span>
                <span class="product-code">{{ product.code }}</span>
              </div>
              <div class="product-status" :class="product.status?.toLowerCase()">
                {{ product.status === 'ACTIVE' ? '启用' : '停用' }}
              </div>
            </div>
            <div class="product-meta" v-if="product.specs">
              <span class="product-specs">{{ product.specs }}</span>
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
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.products-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

/* ==================== PC布局 ==================== */
.pc-products {
  padding: 32px;
}

.page-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.btn-primary-pc {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary-pc:hover {
  background: #0D8A8A;
}

.search-section-pc {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.search-box-pc {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
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

.search-clear {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #888888;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-clear:hover {
  color: #1A1A1A;
}

.btn-search-pc {
  padding: 10px 24px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-search-pc:hover {
  background: #0D8A8A;
}

/* 筛选工具栏 */
.filter-toolbar-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-group-pc {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-select-pc {
  height: 36px;
  padding: 0 12px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  background: #FFFFFF;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #1A1A1A;
  cursor: pointer;
  outline: none;
}

.filter-select-pc:focus {
  border-color: #0D6E6E;
}

.btn-clear-filters-pc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 36px;
  padding: 0 12px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #888888;
  cursor: pointer;
}

.btn-clear-filters-pc:hover {
  color: #E07B54;
  border-color: #E07B54;
}

.result-count-pc {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #888888;
}

.product-table-pc {
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  overflow: hidden;
}

.table-header {
  display: flex;
  background: #FAFAFA;
  padding: 12px 16px;
  border-bottom: 1px solid #E5E5E5;
}

.table-row {
  display: flex;
  padding: 16px;
  border-bottom: 1px solid #F5F5F5;
  cursor: pointer;
  transition: background-color 150ms;
}

.table-row:last-child {
  border-bottom: none;
}

.table-row:hover {
  background: #FAFAFA;
}

.table-cell {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  display: flex;
  align-items: center;
}

.table-cell.name {
  flex: 2;
  font-weight: 500;
}

.table-cell.code {
  flex: 1;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #888888;
}

.table-cell.category {
  flex: 1;
  color: #666666;
}

.table-cell.status {
  flex: 0.5;
}

.table-cell.actions {
  flex: 0.8;
  justify-content: flex-end;
  gap: 8px;
}

.status-badge {
  padding: 4px 8px;
  border-radius: 4px;
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
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  background: #F5F5F5;
  color: #666666;
}

.action-btn:hover {
  background: #E5E5E5;
}

.action-btn.edit {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.action-btn.edit:hover {
  background: rgba(13, 110, 110, 0.15);
}

.empty-state-pc,
.loading-state-pc {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: #888888;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 20px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 100px;
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

.search-clear {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: #888888;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-clear:hover {
  background: #E5E5E5;
  border-radius: 4px;
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
  cursor: pointer;
}

.product-item:hover {
  border-color: #0D6E6E;
}

.product-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 8px;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.product-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
}

.product-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #888888;
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

.product-meta {
  margin-bottom: 12px;
}

.product-specs {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #666666;
}

.product-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.action-btn.edit {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
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
  padding: 48px 24px;
  color: #888888;
  gap: 12px;
}

.empty-state svg {
  color: #D1D5DB;
}

.empty-state p {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  margin: 0;
}

.add-product-btn {
  padding: 10px 20px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  margin-top: 8px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
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
  .pc-products {
    display: none;
  }
}
</style>
