<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { showToast } from 'vant'
import { useRouter } from 'vue-router'
import { getCategories } from '@/api/categories'
import { getCurrentPrice, getProductAnnualBudgets, getProducts } from '@/api/products'
import EmptyState from '@/components/EmptyState.vue'
import { useLayout } from '@/composables/useLayout'
import { Permission, usePermission } from '@/composables/usePermission'
import {
  getCurrencySymbol,
  getCustomerName,
  getDictOptions,
  getDictValue,
  getOriginName,
  getStatusLabel,
  loadAllDicts
} from '@/composables/useDict'
import { eventBus } from '@/utils/eventBus'
import type { PageResponse, Price, Product, ProductCategory, ProductStatus } from '@/types'

const router = useRouter()
const { hasPermission } = usePermission()
const { isPCLayout } = useLayout()

const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const selectedProduct = ref<Product | null>(null)
const selectedPrice = ref<Price | null>(null)
const annualBudgetMap = ref<Map<number, number | null>>(new Map())
const loading = ref(false)
const detailLoading = ref(false)
const keyword = ref('')
const debouncedKeyword = ref('')
const categoryId = ref<number | ''>('')
const status = ref<ProductStatus | ''>('')
const sortValue = ref('sortOrder:asc')
const currentPage = ref(0)
const pageSize = ref(8)
const totalElements = ref(0)
const totalPages = ref(0)
const jumpPage = ref('1')
const totalProductCount = ref(0)
const activeProductCount = ref(0)
const listShellRef = ref<HTMLElement | null>(null)
const tableBodyRef = ref<HTMLElement | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null
let resizeTimer: ReturnType<typeof setTimeout> | null = null
let resizeObserver: ResizeObserver | null = null
let detailRequestSeq = 0

const AUTO_SIZE_MIN = 5
const AUTO_SIZE_MAX = 12
const FALLBACK_ROW_HEIGHT = 68

const statusOptions = computed(() => getDictOptions('common_status'))
const hiddenProductCount = computed(() => Math.max(totalProductCount.value - activeProductCount.value, 0))
const hasActiveFilters = computed(() => Boolean(keyword.value || categoryId.value || status.value))
const firstResultIndex = computed(() => totalElements.value ? currentPage.value * pageSize.value + 1 : 0)
const lastResultIndex = computed(() => Math.min((currentPage.value + 1) * pageSize.value, totalElements.value))
const selectedOrigins = computed(() => parseDictList(selectedProduct.value?.originIds, getOriginName))
const selectedCustomers = computed(() => parseDictList(selectedProduct.value?.customerIds, getCustomerName))
const selectedBudgetPrice = computed(() => selectedProduct.value ? annualBudgetMap.value.get(selectedProduct.value.id) ?? null : null)

const paginationItems = computed<Array<number | string>>(() => {
  const total = totalPages.value
  const current = currentPage.value + 1
  if (total <= 7) return Array.from({ length: total }, (_, index) => index + 1)

  const pages = new Set([1, total, current - 1, current, current + 1])
  const normalized = [...pages].filter(page => page >= 1 && page <= total).sort((a, b) => a - b)
  const items: Array<number | string> = []
  normalized.forEach((page, index) => {
    const previous = normalized[index - 1]
    if (previous && page - previous > 1) items.push(`ellipsis-${previous}-${page}`)
    items.push(page)
  })
  return items
})

const profileCompleteness = computed(() => {
  const product = selectedProduct.value
  if (!product) return 0
  const fields = [
    product.name,
    product.code,
    product.category?.id || product.categoryId,
    product.specs,
    product.unit,
    product.currency,
    parseCodes(product.originIds).length,
    parseCodes(product.customerIds).length,
    product.description,
    product.imageUrl
  ]
  return Math.round((fields.filter(Boolean).length / fields.length) * 100)
})

const completenessHint = computed(() => {
  const product = selectedProduct.value
  if (!product) return ''
  const missing: string[] = []
  if (!product.imageUrl) missing.push('产品图片')
  if (!parseCodes(product.originIds).length) missing.push('产地')
  if (!parseCodes(product.customerIds).length) missing.push('客户')
  if (!product.description) missing.push('描述')
  return missing.length ? `待补充：${missing.join('、')}` : '产品资料已完整'
})

const parseCodes = (value?: string): string[] => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

const parseDictList = (value: string | undefined, resolver: (key: string) => string) =>
  parseCodes(value).map(resolver).filter(Boolean)

const formatDictList = (value: string | undefined, resolver: (key: string) => string) =>
  parseDictList(value, resolver).join('、') || '-'

const getUnitLabel = (unit?: string) => unit ? getDictValue('unit', unit) : '-'
const getCurrencyLabel = (currency?: string) => {
  const value = currency?.trim() || 'CNY'
  const option = getDictOptions('currency').find(item => item.value === value || item.label === value)
  return option?.label || getDictValue('currency', value) || value
}
const formatPrice = (product: Product | null | undefined, value?: number | null) =>
  value === null || value === undefined ? '-' : `${getCurrencySymbol(product?.currency)}${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const formatAnnualBudget = (product: Product) => formatPrice(product, annualBudgetMap.value.get(product.id) ?? null)

const formatUpdatedTime = (value?: string) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
}

const getSortParams = () => {
  const [sortBy, sortDirection] = sortValue.value.split(':')
  return { sortBy, sortDirection: sortDirection as 'asc' | 'desc' }
}

const selectProduct = (product: Product | null) => {
  selectedProduct.value = product
  loadSelectedPrice(product)
}

const syncSelectedProduct = () => {
  if (!products.value.length) {
    selectProduct(null)
    return
  }
  const currentId = selectedProduct.value?.id
  selectProduct(products.value.find(product => product.id === currentId) || products.value[0])
}

const loadSelectedPrice = async (product: Product | null) => {
  const seq = ++detailRequestSeq
  selectedPrice.value = null
  if (!product) return
  detailLoading.value = true
  try {
    const response = await getCurrentPrice(product.id)
    if (seq === detailRequestSeq) selectedPrice.value = response.data || null
  } catch (error) {
    console.error('Failed to load current product price:', error)
  } finally {
    if (seq === detailRequestSeq) detailLoading.value = false
  }
}

const loadStats = async () => {
  try {
    const [allResponse, activeResponse] = await Promise.all([
      getProducts({ page: 0, size: 1 }),
      getProducts({ page: 0, size: 1, status: 'ACTIVE' })
    ])
    totalProductCount.value = allResponse.data.totalElements || 0
    activeProductCount.value = activeResponse.data.totalElements || 0
  } catch (error) {
    console.error('Failed to load product stats:', error)
  }
}

const loadProducts = async (syncAdaptive = true) => {
  loading.value = true
  try {
    const response = await getProducts({
      page: currentPage.value,
      size: pageSize.value,
      keyword: debouncedKeyword.value || undefined,
      categoryId: categoryId.value || undefined,
      status: status.value || undefined,
      ...getSortParams()
    })
    const pageData = response.data as PageResponse<Product>
    products.value = pageData.content || []
    totalElements.value = pageData.totalElements || 0
    totalPages.value = pageData.totalPages || 0
    currentPage.value = pageData.number ?? currentPage.value
    jumpPage.value = String(currentPage.value + 1)
    syncSelectedProduct()
    await loadAnnualBudgets()
  } catch (error) {
    console.error('Failed to load products:', error)
    products.value = []
    selectProduct(null)
    showToast('加载产品列表失败')
  } finally {
    loading.value = false
  }
  if (syncAdaptive && isPCLayout.value) {
    await nextTick()
    applyAdaptivePageSize(true)
  }
}

const loadAnnualBudgets = async () => {
  if (!products.value.length) {
    annualBudgetMap.value = new Map()
    return
  }
  try {
    const response = await getProductAnnualBudgets({
      year: new Date().getFullYear(),
      keyword: debouncedKeyword.value || undefined,
      categoryId: categoryId.value || undefined,
      status: status.value || undefined
    })
    annualBudgetMap.value = new Map((response.data?.items || []).map(item => [item.productId, item.budgetPrice ?? null]))
  } catch (error) {
    console.error('Failed to load annual budgets:', error)
    annualBudgetMap.value = new Map()
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

const refreshProducts = () => {
  loadProducts()
  loadStats()
}

const clearFilters = () => {
  keyword.value = ''
  debouncedKeyword.value = ''
  categoryId.value = ''
  status.value = ''
  currentPage.value = 0
  loadProducts()
}

const handleFilterChange = () => {
  currentPage.value = 0
  loadProducts()
}

const changePage = (page: number) => {
  if (page < 1 || page > totalPages.value || page === currentPage.value + 1) return
  currentPage.value = page - 1
  loadProducts(false)
}

const submitJumpPage = () => {
  const page = Number(jumpPage.value)
  if (!Number.isInteger(page) || page < 1 || page > Math.max(totalPages.value, 1)) {
    jumpPage.value = String(currentPage.value + 1)
    return
  }
  changePage(page)
}

const calculateAdaptivePageSize = () => {
  const shell = tableBodyRef.value
  const firstRow = shell?.querySelector('.product-row') as HTMLElement | null
  const rowHeight = firstRow?.getBoundingClientRect().height || FALLBACK_ROW_HEIGHT
  const availableHeight = shell?.clientHeight || 0
  const measured = availableHeight > rowHeight ? Math.floor(availableHeight / rowHeight) : pageSize.value
  return Math.min(Math.max(measured, AUTO_SIZE_MIN), AUTO_SIZE_MAX)
}

const applyAdaptivePageSize = (reload = false) => {
  if (!isPCLayout.value) return
  const nextSize = calculateAdaptivePageSize()
  if (nextSize === pageSize.value) return
  pageSize.value = nextSize
  currentPage.value = 0
  if (reload) loadProducts(false)
}

const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => applyAdaptivePageSize(true), 180)
}

const setupResizeObserver = () => {
  if (typeof ResizeObserver === 'undefined' || !listShellRef.value) return
  resizeObserver = new ResizeObserver(handleResize)
  resizeObserver.observe(listShellRef.value)
  if (tableBodyRef.value) resizeObserver.observe(tableBodyRef.value)
}

const goToImport = () => router.push('/import')
const addProduct = () => router.push('/product-edit')
const editProduct = (product: Product) => router.push(`/product-edit/${product.id}`)
const viewProduct = (product: Product) => router.push(`/product-detail/${product.id}`)
const maintainPrice = () => router.push('/price-maintenance')
const manageBudget = () => router.push('/budget-management')
const manageHomeOrder = () => router.push({ path: '/style-settings', query: { section: 'home-sort' } })
const switchTab = (path: string) => router.push(path)

watch(keyword, value => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    debouncedKeyword.value = value.trim()
    currentPage.value = 0
    loadProducts()
  }, 300)
})

watch(isPCLayout, value => {
  pageSize.value = value ? 8 : 20
  currentPage.value = 0
  nextTick(() => {
    resizeObserver?.disconnect()
    resizeObserver = null
    if (value) setupResizeObserver()
    loadProducts()
  })
})

onMounted(async () => {
  await loadAllDicts()
  await Promise.all([loadCategories(), loadStats()])
  await loadProducts()
  await nextTick()
  setupResizeObserver()
  eventBus.on('prices-updated', refreshProducts)
  eventBus.on('product-sort-updated', refreshProducts)
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeObserver?.disconnect()
  eventBus.off('prices-updated', refreshProducts)
  eventBus.off('product-sort-updated', refreshProducts)
})
</script>

<template>
  <div class="products-page">
    <template v-if="isPCLayout">
      <section class="desktop-products">
        <header class="page-header">
          <div>
            <h1>产品管理</h1>
            <p>维护产品基础资料、分类、产地、客户范围与价格入口</p>
          </div>
          <div class="header-actions">
            <button
              v-if="hasPermission(Permission.PRODUCT_IMPORT) || hasPermission(Permission.PRODUCT_EXPORT)"
              class="btn secondary"
              type="button"
              @click="goToImport"
            >
              导入导出
            </button>
            <button v-if="hasPermission(Permission.PRODUCT_CREATE)" class="btn primary" type="button" @click="addProduct">
              <span aria-hidden="true">+</span>
              新增产品
            </button>
          </div>
        </header>

        <div class="metrics">
          <article class="metric-card">
            <span>产品总数</span>
            <strong>{{ totalProductCount }}</strong>
            <small>全部资料档案</small>
          </article>
          <article class="metric-card accent">
            <span>展示产品</span>
            <strong>{{ activeProductCount }}</strong>
            <small>当前启用产品</small>
          </article>
          <article class="metric-card warning">
            <span>隐藏产品</span>
            <strong>{{ hiddenProductCount }}</strong>
            <small>暂不展示或停用</small>
          </article>
          <article class="metric-card category">
            <span>产品分类</span>
            <strong>{{ categories.length }}</strong>
            <small>按矿种与用途维护</small>
          </article>
        </div>

        <div class="workspace">
          <section ref="listShellRef" class="list-panel">
            <div class="filters">
              <label class="search-field">
                <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
                <input v-model="keyword" type="search" placeholder="搜索产品名称、编码、规格" />
              </label>
              <select v-model="categoryId" aria-label="产品分类" @change="handleFilterChange">
                <option value="">全部分类</option>
                <option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select>
              <select v-model="status" aria-label="产品状态" @change="handleFilterChange">
                <option value="">全部状态</option>
                <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
              </select>
              <select v-model="sortValue" aria-label="产品排序" @change="handleFilterChange">
                <option value="sortOrder:asc">排序：自定义</option>
                <option value="updatedTime:desc">排序：最近更新</option>
                <option value="name:asc">排序：产品名称</option>
              </select>
              <button v-if="hasActiveFilters" class="clear-button" type="button" @click="clearFilters">清空</button>
            </div>

            <div class="list-summary">
              <span>共 {{ totalElements }} 个产品</span>
              <span v-if="hasActiveFilters" class="filter-tag">当前筛选</span>
            </div>

            <div class="product-table">
              <div class="product-header">
                <span>产品</span>
                <span>分类 / 规格</span>
                <span>产地 / 客户</span>
                <span>售价 / 预算</span>
                <span>首页</span>
                <span>状态</span>
              </div>
              <div ref="tableBodyRef" class="product-body">
                <button
                  v-for="product in products"
                  :key="product.id"
                  class="product-row"
                  :class="{ selected: selectedProduct?.id === product.id }"
                  type="button"
                  @click="selectProduct(product)"
                  @dblclick="viewProduct(product)"
                >
                  <span class="product-identity">
                    <strong>{{ product.name }}</strong>
                    <small>{{ product.code || '-' }}</small>
                  </span>
                  <span>
                    <strong>{{ product.category?.name || '-' }}</strong>
                    <small>{{ product.specs || '-' }} / {{ getUnitLabel(product.unit) }}</small>
                  </span>
                  <span>
                    <strong>{{ formatDictList(product.originIds, getOriginName) }}</strong>
                    <small>{{ formatDictList(product.customerIds, getCustomerName) }}</small>
                  </span>
                  <span class="price-cell">
                    <strong>{{ formatPrice(product, product.sellingPrice) }}</strong>
                    <small>预算 {{ formatAnnualBudget(product) }}</small>
                  </span>
                  <span>
                    <em class="home-status" :class="{ active: product.showOnHome }">
                      {{ product.showOnHome ? '首页展示' : '未上首页' }}
                    </em>
                  </span>
                  <span>
                    <em class="product-status-badge" :class="{ inactive: product.status !== 'ACTIVE' }">
                      {{ getStatusLabel(product.status) }}
                    </em>
                  </span>
                </button>

                <div v-if="loading" class="panel-state">
                  <span class="spinner"></span>
                  加载产品资料...
                </div>
                <EmptyState
                  v-else-if="products.length === 0"
                  :type="hasActiveFilters ? 'no-result' : 'no-data'"
                  :title="hasActiveFilters ? '未找到匹配产品' : '暂无产品资料'"
                  :description="hasActiveFilters ? '请调整筛选条件后重试' : '新增产品后将在这里展示'"
                  :action-text="hasActiveFilters ? '清空筛选' : ''"
                  @action="clearFilters"
                />
              </div>
            </div>

            <footer class="pagination">
              <span>第 {{ firstResultIndex }}-{{ lastResultIndex }} 条 / 共 {{ totalElements }} 条</span>
              <div v-if="totalPages > 1" class="pagination-controls">
                <button type="button" :disabled="currentPage === 0" @click="changePage(currentPage)">上一页</button>
                <template v-for="item in paginationItems" :key="item">
                  <button
                    v-if="typeof item === 'number'"
                    type="button"
                    :class="{ active: item === currentPage + 1 }"
                    @click="changePage(item)"
                  >
                    {{ item }}
                  </button>
                  <span v-else>...</span>
                </template>
                <button type="button" :disabled="currentPage >= totalPages - 1" @click="changePage(currentPage + 2)">下一页</button>
                <label>跳至 <input v-model="jumpPage" type="number" min="1" :max="totalPages" @change="submitJumpPage" /> 页</label>
              </div>
            </footer>
          </section>

          <aside class="detail-panel">
            <template v-if="selectedProduct">
              <section class="product-summary">
                <div class="summary-status">
                  <span>当前选中</span>
                  <em>{{ getStatusLabel(selectedProduct.status) }}</em>
                </div>
                <h2>{{ selectedProduct.name }}</h2>
                <p>编码 {{ selectedProduct.code || '-' }} · {{ selectedProduct.category?.name || '-' }} · {{ selectedProduct.specs || '-' }} / {{ getUnitLabel(selectedProduct.unit) }}</p>
                <div class="summary-actions">
                  <button v-if="hasPermission(Permission.PRODUCT_EDIT)" type="button" @click="editProduct(selectedProduct)">编辑资料</button>
                  <button class="dark" type="button" @click="viewProduct(selectedProduct)">查看详情</button>
                </div>
              </section>

              <section class="detail-card completeness-card">
                <div class="card-title">
                  <h3>资料完整度</h3>
                  <strong>{{ profileCompleteness }}%</strong>
                </div>
                <div class="progress"><span :style="{ width: `${profileCompleteness}%` }"></span></div>
                <p>{{ completenessHint }}</p>
              </section>

              <section class="detail-card fields-card">
                <dl>
                  <div><dt>分类</dt><dd>{{ selectedProduct.category?.name || '-' }}</dd></div>
                  <div><dt>产地</dt><dd>{{ selectedOrigins.join('、') || '-' }}</dd></div>
                  <div><dt>适用客户</dt><dd>{{ selectedCustomers.join('、') || '-' }}</dd></div>
                  <div><dt>币种 / 单位</dt><dd>{{ getCurrencyLabel(selectedProduct.currency) }} / {{ getUnitLabel(selectedProduct.unit) }}</dd></div>
                </dl>
              </section>

              <section class="detail-card price-snapshot">
                <div class="card-title"><h3>价格快照</h3><span>{{ formatUpdatedTime(selectedPrice?.createdTime || selectedProduct.updatedTime) }}</span></div>
                <div v-if="detailLoading" class="mini-loading">价格加载中...</div>
                <div v-else class="price-grid">
                  <div><span>当前售价</span><strong>{{ formatPrice(selectedProduct, selectedPrice?.currentPrice ?? selectedProduct.sellingPrice) }}</strong></div>
                  <div><span>预算价</span><strong>{{ formatPrice(selectedProduct, selectedBudgetPrice) }}</strong></div>
                </div>
              </section>

              <section class="detail-card actions-card">
                <h3>管理执行</h3>
                <button v-if="hasPermission(Permission.PRICE_EDIT)" type="button" @click="maintainPrice">
                  <span class="action-icon">↗</span><span><strong>维护价格</strong><small>录入售价并发布</small></span><em>去处理</em>
                </button>
                <button v-if="hasPermission(Permission.PRICE_EDIT)" type="button" @click="manageBudget">
                  <span class="action-icon">◎</span><span><strong>预算管理</strong><small>按产品与年份维护年度预算</small></span><em>去处理</em>
                </button>
                <button v-if="hasPermission(Permission.SYSTEM_SETTING)" type="button" @click="manageHomeOrder">
                  <span class="action-icon">≡</span><span><strong>展示与排序</strong><small>切换首页展示并调整顺序</small></span><em>去处理</em>
                </button>
                <button
                  v-if="hasPermission(Permission.PRODUCT_IMPORT) || hasPermission(Permission.PRODUCT_EXPORT)"
                  class="import-action"
                  type="button"
                  @click="goToImport"
                >
                  <span class="action-icon">⇧</span><span><strong>批量导入导出</strong><small>下载模板、导入或导出产品</small></span><em>去处理</em>
                </button>
              </section>
            </template>
            <div v-else class="detail-empty">选择产品后查看资料与管理入口</div>
          </aside>
        </div>
      </section>
    </template>

    <template v-else>
      <section class="mobile-products">
        <header class="mobile-header">
          <div><h1>产品管理</h1><p>{{ totalProductCount }} 个产品档案</p></div>
          <button v-if="hasPermission(Permission.PRODUCT_CREATE)" type="button" aria-label="新增产品" @click="addProduct">+</button>
        </header>
        <main class="mobile-content">
          <label class="mobile-search">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="m20 20-4-4"/></svg>
            <input v-model="keyword" type="search" placeholder="搜索产品名称、编码、规格" />
          </label>
          <div class="mobile-filters">
            <select v-model="categoryId" aria-label="产品分类" @change="handleFilterChange">
              <option value="">全部分类</option>
              <option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option>
            </select>
            <select v-model="status" aria-label="产品状态" @change="handleFilterChange">
              <option value="">全部状态</option>
              <option v-for="item in statusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
            </select>
            <select v-model="sortValue" aria-label="产品排序" @change="handleFilterChange">
              <option value="sortOrder:asc">排序</option>
              <option value="updatedTime:desc">最近更新</option>
              <option value="name:asc">名称</option>
            </select>
          </div>
          <div class="mobile-metrics">
            <div><strong>{{ totalProductCount }}</strong><span>总数</span></div>
            <div><strong>{{ activeProductCount }}</strong><span>展示</span></div>
            <div><strong>{{ hiddenProductCount }}</strong><span>隐藏</span></div>
          </div>
          <div class="mobile-list">
            <button v-for="product in products" :key="product.id" type="button" class="mobile-card" @click="viewProduct(product)">
              <span class="mobile-card-head">
                <span><strong>{{ product.name }}</strong><small>{{ product.code || '-' }}</small></span>
                <em :class="{ inactive: product.status !== 'ACTIVE' }">{{ getStatusLabel(product.status) }}</em>
              </span>
              <span class="mobile-card-meta"><strong>{{ product.category?.name || '-' }} · {{ product.specs || '-' }} / {{ getUnitLabel(product.unit) }}</strong><small>{{ formatDictList(product.originIds, getOriginName) }} · {{ formatDictList(product.customerIds, getCustomerName) }}</small></span>
              <span class="mobile-card-price"><strong>{{ formatPrice(product, product.sellingPrice) }}</strong><small>预算 {{ formatAnnualBudget(product) }}</small><em :class="{ inactive: !product.showOnHome }">{{ product.showOnHome ? '首页展示' : '未上首页' }}</em></span>
            </button>
            <div v-if="loading" class="panel-state"><span class="spinner"></span>加载产品资料...</div>
            <EmptyState
              v-else-if="products.length === 0"
              :type="hasActiveFilters ? 'no-result' : 'no-data'"
              :title="hasActiveFilters ? '未找到匹配产品' : '暂无产品资料'"
              :action-text="hasActiveFilters ? '清空筛选' : ''"
              @action="clearFilters"
            />
          </div>
          <div v-if="totalPages > 1" class="mobile-pagination">
            <button type="button" :disabled="currentPage === 0" @click="changePage(currentPage)">上一页</button>
            <span>{{ currentPage + 1 }} / {{ totalPages }}</span>
            <button type="button" :disabled="currentPage >= totalPages - 1" @click="changePage(currentPage + 2)">下一页</button>
          </div>
        </main>
        <footer class="mobile-tabs">
          <button type="button" @click="switchTab('/home')">首页</button>
          <button class="active" type="button">产品</button>
          <button v-if="hasPermission(Permission.PRODUCT_IMPORT)" type="button" @click="switchTab('/import')">导入</button>
          <button type="button" @click="switchTab('/profile')">我的</button>
        </footer>
      </section>
    </template>
  </div>
</template>

<style scoped>
.products-page { min-height: 100%; color: var(--text-primary); font-family: var(--font-body), sans-serif; }
button, input, select { font: inherit; }
button { cursor: pointer; }
.desktop-products { min-height: calc(100vh - 48px); display: flex; flex-direction: column; gap: 16px; }
.page-header { min-height: 76px; display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.page-header h1, .mobile-header h1 { margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -.03em; }
.page-header p, .mobile-header p { margin: 6px 0 0; color: var(--text-secondary); font-size: 13px; }
.header-actions, .summary-actions { display: flex; gap: 10px; }
.btn, .summary-actions button { min-height: 36px; border: 1px solid var(--border-color); border-radius: 6px; padding: 0 14px; background: var(--bg-card); color: var(--text-primary); font-weight: 700; }
.btn.primary { border-color: var(--primary-color); background: var(--primary-color); color: #fff; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.metric-card { min-height: 96px; display: flex; flex-direction: column; gap: 4px; padding: 16px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); }
.metric-card span { color: var(--text-secondary); font-size: 12px; font-weight: 600; }
.metric-card strong { font-family: var(--font-mono), monospace; font-size: 26px; line-height: 1.1; }
.metric-card small { color: var(--text-muted); font-size: 11px; }
.metric-card.accent strong { color: var(--primary-color); }
.metric-card.warning strong { color: var(--error-color); }
.metric-card.category strong { color: #e07b54; }
.workspace { min-height: 610px; flex: 1; display: grid; grid-template-columns: minmax(620px, 2.3fr) minmax(300px, 1fr); gap: 16px; }
.list-panel, .detail-panel { min-height: 0; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); }
.list-panel { display: flex; flex-direction: column; overflow: hidden; }
.filters { display: grid; grid-template-columns: minmax(220px, 1fr) repeat(3, auto) auto; gap: 8px; padding: 14px; border-bottom: 1px solid var(--border-color); }
.search-field, .mobile-search { display: flex; align-items: center; gap: 8px; border: 1px solid var(--border-color); border-radius: 6px; background: #fff; padding: 0 11px; }
.search-field svg, .mobile-search svg { width: 16px; fill: none; stroke: var(--text-muted); stroke-width: 2; }
.search-field input, .mobile-search input { width: 100%; min-width: 0; height: 36px; border: 0; outline: 0; background: transparent; color: var(--text-primary); }
.filters select, .mobile-filters select { min-width: 108px; height: 38px; border: 1px solid var(--border-color); border-radius: 6px; background: #fff; color: var(--text-secondary); padding: 0 9px; font-size: 12px; font-weight: 600; }
.clear-button { border: 0; background: transparent; color: var(--text-secondary); font-size: 12px; }
.list-summary { min-height: 38px; display: flex; align-items: center; gap: 8px; padding: 0 14px; color: var(--text-secondary); font-size: 12px; }
.filter-tag, .home-status { display: inline-flex; align-items: center; width: fit-content; border-radius: 999px; padding: 3px 8px; background: var(--primary-bg); color: var(--primary-color); font-style: normal; font-size: 10px; font-weight: 700; }
.home-status:not(.active) { background: var(--gray-100); color: var(--text-muted); }
.product-table { min-height: 0; flex: 1; display: flex; flex-direction: column; }
.product-header, .product-row { display: grid; grid-template-columns: 1.35fr 1fr 1.25fr .95fr .55fr .72fr; gap: 10px; align-items: center; }
.product-header { min-height: 38px; padding: 0 14px; border-block: 1px solid var(--border-color); background: var(--gray-50); color: var(--text-secondary); font-size: 11px; font-weight: 700; }
.product-body { min-height: 340px; flex: 1; overflow-y: auto; }
.product-row { width: 100%; min-height: 68px; padding: 8px 14px; border: 0; border-bottom: 1px solid var(--gray-100); background: #fff; color: var(--text-primary); text-align: left; transition: background .15s ease, box-shadow .15s ease; }
.product-row:hover, .product-row.selected { background: var(--primary-bg); box-shadow: inset 3px 0 var(--primary-color); }
.product-row > span { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.product-row strong, .product-row small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.product-row strong { font-size: 12px; }
.product-row small { color: var(--text-muted); font-size: 10px; }
.product-identity small, .price-cell strong { font-family: var(--font-mono), monospace; }
.product-status-badge { display: inline-flex; align-items: center; width: fit-content; border-radius: 999px; padding: 4px 9px; background: var(--primary-bg); color: var(--primary-color); font-style: normal; font-size: 10px; font-weight: 700; }
.product-status-badge.inactive { background: color-mix(in srgb, var(--error-color) 10%, #fff); color: var(--error-color); }
.pagination { min-height: 50px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 14px; border-top: 1px solid var(--border-color); color: var(--text-secondary); font-size: 12px; }
.pagination-controls { display: flex; align-items: center; gap: 4px; }
.pagination button, .pagination input, .mobile-pagination button { min-width: 30px; height: 30px; border: 1px solid var(--border-color); border-radius: 5px; background: #fff; color: var(--text-secondary); }
.pagination button.active { border-color: var(--primary-color); background: var(--primary-color); color: #fff; }
.pagination button:disabled, .mobile-pagination button:disabled { opacity: .45; cursor: not-allowed; }
.pagination label { display: flex; align-items: center; gap: 4px; margin-left: 4px; }
.pagination input { width: 42px; text-align: center; }
.detail-panel { display: flex; flex-direction: column; gap: 12px; overflow-y: auto; padding: 16px; }
.product-summary { border-radius: 6px; background: var(--primary-dark, #0a5555); color: #fff; padding: 16px; }
.summary-status, .card-title { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.summary-status span { color: #98a2b3; font-size: 11px; font-weight: 700; }
.summary-status em { border-radius: 999px; padding: 3px 7px; background: rgba(13,110,110,.22); color: #8dd2d2; font-family: var(--font-mono), monospace; font-size: 10px; font-style: normal; font-weight: 700; }
.product-summary h2 { margin: 14px 0 5px; font-size: 20px; }
.product-summary p { min-height: 32px; margin: 0 0 14px; color: #d0d5dd; font-size: 11px; line-height: 1.5; }
.summary-actions button { flex: 1; height: 30px; min-height: 30px; padding: 0 8px; }
.summary-actions button.dark { border-color: #fff; background: #fff; color: #1a1a1a; }
.detail-card { border: 1px solid var(--border-color); border-radius: 6px; padding: 14px; }
.detail-card h3 { margin: 0; font-size: 13px; }
.card-title strong { color: var(--primary-color); font-family: var(--font-mono), monospace; font-size: 17px; }
.card-title span, .detail-card p { color: var(--text-muted); font-size: 10px; }
.progress { height: 5px; margin: 10px 0; overflow: hidden; border-radius: 999px; background: var(--gray-100); }
.progress span { display: block; height: 100%; border-radius: inherit; background: var(--primary-color); }
.detail-card p { margin: 0; }
.fields-card dl { display: grid; gap: 9px; margin: 0; }
.fields-card dl div { display: grid; grid-template-columns: 86px 1fr; gap: 10px; }
.fields-card dt { color: var(--text-secondary); font-size: 11px; }
.fields-card dd { margin: 0; font-size: 11px; font-weight: 700; text-align: right; }
.price-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 10px; }
.price-grid div { display: flex; flex-direction: column; gap: 4px; border-radius: 5px; background: var(--gray-50); padding: 10px; }
.price-grid span, .mini-loading { color: var(--text-secondary); font-size: 10px; }
.price-grid strong { font-family: var(--font-mono), monospace; font-size: 15px; }
.mini-loading { margin-top: 10px; }
.actions-card { display: flex; flex-direction: column; gap: 9px; }
.actions-card button { display: grid; grid-template-columns: 18px 1fr auto; align-items: center; gap: 8px; border: 0; border-radius: 6px; background: var(--gray-50); padding: 8px 10px; text-align: left; }
.actions-card button > span:nth-child(2) { display: flex; flex-direction: column; gap: 2px; }
.actions-card strong { font-size: 11px; }
.actions-card small { color: var(--text-muted); font-size: 9px; }
.actions-card em, .action-icon { color: var(--primary-color); font-size: 9px; font-style: normal; font-weight: 700; }
.actions-card .import-action { background: #fff7f3; }
.actions-card .import-action em, .actions-card .import-action .action-icon { color: #e07b54; }
.detail-empty, .panel-state { min-height: 180px; display: flex; align-items: center; justify-content: center; gap: 8px; color: var(--text-muted); font-size: 12px; }
.spinner { width: 20px; height: 20px; border: 2px solid var(--gray-200); border-top-color: var(--primary-color); border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.mobile-products { min-height: 100dvh; display: flex; flex-direction: column; background: #f4f6f8; }
.mobile-header { min-height: 64px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; border-bottom: 1px solid var(--border-color); background: #fff; }
.mobile-header h1 { font-size: 20px; }
.mobile-header p { margin-top: 2px; font-size: 11px; }
.mobile-header button { width: 36px; height: 36px; border: 0; border-radius: 6px; background: var(--primary-color); color: #fff; font-size: 22px; }
.mobile-content { flex: 1; display: flex; flex-direction: column; gap: 12px; padding: 0 16px 16px; }
.mobile-search { min-height: 42px; }
.mobile-filters, .mobile-metrics { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.mobile-filters select { min-width: 0; }
.mobile-metrics div { min-height: 70px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; border: 1px solid var(--border-color); border-radius: 6px; background: #fff; }
.mobile-metrics strong { font-family: var(--font-mono), monospace; font-size: 20px; }
.mobile-metrics span { color: var(--text-secondary); font-size: 11px; }
.mobile-list { display: flex; flex-direction: column; gap: 8px; }
.mobile-card { min-height: 116px; display: flex; flex-direction: column; gap: 8px; border: 1px solid var(--border-color); border-radius: 6px; background: #fff; padding: 12px; text-align: left; }
.mobile-card-head, .mobile-card-price { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.mobile-card-head > span, .mobile-card-meta { min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.mobile-card-head strong { font-size: 14px; }
.mobile-card-head small, .mobile-card-meta small, .mobile-card-price small { color: var(--text-muted); font-size: 10px; }
.mobile-card-head em, .mobile-card-price em { border-radius: 999px; background: var(--primary-bg); color: var(--primary-color); padding: 3px 7px; font-size: 10px; font-style: normal; font-weight: 700; }
.mobile-card-head em.inactive, .mobile-card-price em.inactive { background: var(--gray-100); color: var(--text-muted); }
.mobile-card-meta strong { font-size: 11px; }
.mobile-card-price strong { font-family: var(--font-mono), monospace; font-size: 14px; }
.mobile-pagination { display: flex; align-items: center; justify-content: center; gap: 12px; color: var(--text-secondary); font-size: 12px; }
.mobile-pagination button { padding: 0 12px; }
.mobile-tabs { min-height: 64px; display: flex; border-top: 1px solid var(--border-color); background: #fff; }
.mobile-tabs button { flex: 1; border: 0; background: transparent; color: var(--text-muted); font-size: 11px; }
.mobile-tabs button.active { color: var(--primary-color); font-weight: 700; }
@media (max-width: 1180px) {
  .workspace { grid-template-columns: minmax(560px, 1.9fr) minmax(280px, 1fr); }
  .filters { grid-template-columns: minmax(180px, 1fr) repeat(2, auto); }
  .filters select:nth-of-type(3) { display: none; }
  .product-header, .product-row { grid-template-columns: 1.3fr 1fr 1.1fr .9fr .55fr; }
  .product-header > :last-child, .product-row > :last-child { display: none; }
}
</style>
