<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { getCategories } from '@/api/categories'
import { addProductPrice, updatePrice, getPricesByDateWithStats, getProducts, batchUpdateProductSort } from '@/api/products'
import type { PageResponse, Price, Product, ProductCategory } from '@/types'
import { eventBus } from '@/utils/eventBus'
import { useLayout } from '@/composables/useLayout'
import { getCurrencySymbol, getOriginName, loadAllDicts } from '@/composables/useDict'
import { getCategoryCardStyle, registerCategoryCodes } from '@/composables/useCategoryVisual'

const router = useRouter()
const { isPCLayout } = useLayout()

const loading = ref(false)
const tableLoading = ref(false)
const saving = ref(false)
const products = ref<Product[]>([])
const categories = ref<ProductCategory[]>([])
const tablePage = ref(0)
const tableSize = ref(10)
const tableTotalElements = ref(0)
const tableTotalPages = ref(0)
const searchQuery = ref('')
const searchQueryDebounced = ref('')
const selectedCategoryId = ref<number | ''>('')
const sorting = ref(false)
const draggingProductId = ref<number | null>(null)
const dragOverProductId = ref<number | null>(null)
const dragOverPosition = ref<'before' | 'after'>('before')

const formatLocalDate = (date: Date) => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const parseLocalDate = (dateStr: string) => {
  const [year, month, day] = dateStr.split('-').map(Number)
  return new Date(year, month - 1, day)
}

const getYesterday = () => {
  const date = new Date()
  date.setDate(date.getDate() - 1)
  return formatLocalDate(date)
}

const selectedDate = ref(getYesterday())

const priceMap = ref<Map<number, Price>>(new Map())
const yesterdayPriceMap = ref<Map<number, Price>>(new Map())
const monthlyAverageMap = ref<Map<number, number>>(new Map())
const inheritedPriceMap = ref<Map<number, number>>(new Map())
const inheritedBudgetPriceMap = ref<Map<number, number>>(new Map())
const editingPrices = ref<Map<number, string>>(new Map())
const originalPriceTextMap = ref<Map<number, string>>(new Map())

const productById = computed(() => {
  const map = new Map<number, Product>()
  products.value.forEach(product => map.set(product.id, product))
  priceMap.value.forEach(price => {
    if (price.product && !map.has(price.product.id)) {
      map.set(price.product.id, price.product)
    }
  })
  return map
})

const hasChanges = computed(() => {
  for (const [productId, editPrice] of editingPrices.value) {
    if (editPrice !== (originalPriceTextMap.value.get(productId) || '')) return true
  }
  return false
})

const formatDateDisplay = (dateStr: string) => {
  const [year, month, day] = dateStr.split('-').map(Number)
  return `${year}年${month}月${day}日`
}

const normalizePriceText = (value: unknown) => {
  if (value === null || value === undefined || value === '') return ''
  return String(value)
}

const initEditingData = (product: Product) => {
  if (!editingPrices.value.has(product.id)) {
    const text = normalizePriceText(priceMap.value.get(product.id)?.currentPrice)
    editingPrices.value.set(product.id, text)
    originalPriceTextMap.value.set(product.id, text)
  }
}

const getEditData = (productId: number) => {
  const product = productById.value.get(productId)
  if (product) initEditingData(product)
  return editingPrices.value.get(productId) || ''
}

const updateEditPrice = (productId: number, value: string) => {
  editingPrices.value.set(productId, value)
}

const getPriceChange = (productId: number) => {
  const editPrice = editingPrices.value.get(productId)
  const currentPrice = editPrice ? Number(editPrice) : inheritedPriceMap.value.get(productId) ?? null
  const yesterdayPrice = yesterdayPriceMap.value.get(productId)?.currentPrice ?? inheritedPriceMap.value.get(productId) ?? null

  if (currentPrice === null || yesterdayPrice === null || Number.isNaN(currentPrice)) return null
  return currentPrice - yesterdayPrice
}

const formatPriceWithCurrency = (productId: number, price: number | null | undefined) => {
  if (price === null || price === undefined) return '-'
  return `${getProductCurrencySymbol(productId)}${price.toFixed(2)}`
}

const formatPriceChange = (productId: number, change: number | null | undefined) => {
  if (change === null || change === undefined) return '-'
  const symbol = getProductCurrencySymbol(productId)
  if (change > 0) return `+${symbol}${change.toFixed(2)}`
  if (change < 0) return `-${symbol}${Math.abs(change).toFixed(2)}`
  return `${symbol}0.00`
}

const getPriceChangeClass = (change: number | null | undefined) => {
  if (change === null || change === undefined) return 'flat'
  if (change > 0) return 'up'
  if (change < 0) return 'down'
  return 'flat'
}

const getProductCurrencySymbol = (productId: number): string => {
  const product = productById.value.get(productId)
  return getCurrencySymbol(product?.currency)
}

const getProductOriginLabel = (product: Product): string => {
  if (!product.originIds) return ''
  try {
    const keys = JSON.parse(product.originIds)
    return Array.isArray(keys)
      ? keys.map((key: string) => getOriginName(key)).filter(Boolean).join('、')
      : ''
  } catch {
    return ''
  }
}

const getDisplayYesterdayPrice = (productId: number): number | null | undefined => {
  const yesterdayPrice = yesterdayPriceMap.value.get(productId)?.currentPrice
  if (yesterdayPrice != null) return yesterdayPrice
  return inheritedPriceMap.value.get(productId) ?? null
}

const getDisplayMonthlyAvg = (productId: number): number | null | undefined => {
  const avg = monthlyAverageMap.value.get(productId)
  if (avg != null) return avg
  return inheritedPriceMap.value.get(productId) ?? null
}

const getPricePlaceholder = (productId: number): string => {
  const inherited = inheritedPriceMap.value.get(productId)
  return inherited != null ? inherited.toFixed(2) : '0.00'
}

const formatBudgetPrice = (productId: number): string => {
  const symbol = getProductCurrencySymbol(productId)
  const price = priceMap.value.get(productId)
  if (price?.budgetPrice != null) return `${symbol}${price.budgetPrice.toFixed(2)}`
  const product = productById.value.get(productId)
  if (product?.budgetPrice != null) return `${symbol}${product.budgetPrice.toFixed(2)}`
  const inherited = inheritedBudgetPriceMap.value.get(productId)
  if (inherited != null) return `${symbol}${inherited.toFixed(2)}`
  return '-'
}

const getPriceUnit = (product: Product) => priceMap.value.get(product.id)?.unit || product.unit || '-'

const getProductCategoryId = (product: Product): number | undefined => {
  return product.categoryId || product.category?.id
}

const getCardStyle = (product: Product) => {
  const categoryId = getProductCategoryId(product)
  return categoryId ? getCategoryCardStyle(categoryId) : {}
}

const getCardClass = (product: Product) => {
  return getProductCategoryId(product) ? 'has-category' : ''
}

const loadCategories = async () => {
  const response = await getCategories('ACTIVE')
  categories.value = (response.data || []).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  registerCategoryCodes(categories.value.map(category => ({ id: category.id, code: category.code })))
}

const applyPriceStats = (items: Awaited<ReturnType<typeof getPricesByDateWithStats>>['data']) => {
  priceMap.value = new Map()
  yesterdayPriceMap.value = new Map()
  monthlyAverageMap.value = new Map()
  inheritedPriceMap.value = new Map()
  inheritedBudgetPriceMap.value = new Map()
  editingPrices.value = new Map()
  originalPriceTextMap.value = new Map()

  for (const item of items || []) {
    if (!item.price?.product) continue
    const productId = item.price.product.id
    priceMap.value.set(productId, item.price)
    const text = normalizePriceText(item.price.currentPrice)
    editingPrices.value.set(productId, text)
    originalPriceTextMap.value.set(productId, text)
    if (item.yesterdayPrice) yesterdayPriceMap.value.set(productId, item.yesterdayPrice)
    if (item.monthlyAveragePrice != null) monthlyAverageMap.value.set(productId, item.monthlyAveragePrice)
    if (item.inheritedPrice != null) inheritedPriceMap.value.set(productId, item.inheritedPrice)
    if (item.inheritedBudgetPrice != null) inheritedBudgetPriceMap.value.set(productId, item.inheritedBudgetPrice)
  }

  products.value.forEach(initEditingData)
}

const loadPrices = async () => {
  tableLoading.value = true
  try {
    const response = await getPricesByDateWithStats(selectedDate.value)
    applyPriceStats(response.data || [])
  } catch (error) {
    console.error('Failed to load prices:', error)
    showToast('加载价格数据失败')
  } finally {
    tableLoading.value = false
  }
}

const loadProducts = async (options: { silent?: boolean } = {}) => {
  if (!options.silent) tableLoading.value = true
  try {
    const response = await getProducts({
      page: tablePage.value,
      size: tableSize.value,
      keyword: searchQueryDebounced.value || undefined,
      categoryId: selectedCategoryId.value || undefined,
      status: 'ACTIVE',
      sortBy: 'sortOrder',
      sortDirection: 'asc'
    })
    const pageData = response.data as PageResponse<Product>
    products.value = pageData.content || []
    tableTotalElements.value = pageData.totalElements || 0
    tableTotalPages.value = pageData.totalPages || 0
    products.value.forEach(initEditingData)
  } catch (error) {
    console.error('Failed to load products:', error)
    showToast('加载产品数据失败')
  } finally {
    if (!options.silent) tableLoading.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    await Promise.all([loadCategories(), loadPrices()])
    await loadProducts({ silent: true })
  } finally {
    loading.value = false
  }
}

const ensureCanChangeDate = async () => {
  if (!hasChanges.value) return true
  try {
    await showConfirmDialog({
      title: '存在未保存价格',
      message: '切换日期会重新加载价格数据，未保存的修改将被覆盖。',
      confirmButtonText: '继续',
      cancelButtonText: '取消'
    })
    return true
  } catch {
    return false
  }
}

const onCategoryChange = async (event: Event) => {
  const value = (event.target as HTMLSelectElement).value
  selectedCategoryId.value = value ? Number(value) : ''
  tablePage.value = 0
  await loadProducts()
}

const onTableSizeChange = async (event: Event) => {
  tableSize.value = Number((event.target as HTMLSelectElement).value)
  tablePage.value = 0
  await loadProducts()
}

const goToPage = async (page: number) => {
  if (page < 0 || page >= tableTotalPages.value || page === tablePage.value) return
  tablePage.value = page
  await loadProducts()
}

const createProductDragImage = (product: Product) => {
  if (typeof document === 'undefined') return null
  const preview = document.createElement('div')
  preview.textContent = `${product.name} · ${product.category?.name || '未分类'}`
  preview.style.position = 'fixed'
  preview.style.top = '-1000px'
  preview.style.left = '-1000px'
  preview.style.maxWidth = '280px'
  preview.style.padding = '10px 14px'
  preview.style.border = '1px solid var(--primary-color)'
  preview.style.borderRadius = '10px'
  preview.style.background = 'var(--bg-card)'
  preview.style.color = 'var(--text-primary)'
  preview.style.boxShadow = '0 14px 32px rgba(15, 23, 42, 0.18)'
  preview.style.fontSize = '13px'
  preview.style.fontWeight = '700'
  preview.style.whiteSpace = 'nowrap'
  preview.style.pointerEvents = 'none'
  document.body.appendChild(preview)
  return preview
}

const onProductDragStart = (product: Product, event: DragEvent) => {
  const productId = product.id
  draggingProductId.value = productId
  dragOverProductId.value = null
  dragOverPosition.value = 'before'
  event.dataTransfer?.setData('text/plain', String(productId))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    const preview = createProductDragImage(product)
    if (preview) {
      event.dataTransfer.setDragImage(preview, 18, 18)
      window.setTimeout(() => preview.remove(), 0)
    }
  }
}

const onProductDragOver = (productId: number, event: DragEvent) => {
  if (draggingProductId.value === null || draggingProductId.value === productId) return
  event.preventDefault()
  const target = event.currentTarget as HTMLElement | null
  if (target) {
    const rect = target.getBoundingClientRect()
    dragOverPosition.value = event.clientY < rect.top + rect.height / 2 ? 'before' : 'after'
  }
  dragOverProductId.value = productId
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

const clearProductDrag = () => {
  draggingProductId.value = null
  dragOverProductId.value = null
  dragOverPosition.value = 'before'
}

const persistProductOrder = async (nextProducts: Product[], previousProducts: Product[]) => {
  const pageOffset = tablePage.value * tableSize.value
  const items = nextProducts.map((product, index) => ({
    id: product.id,
    sortOrder: pageOffset + index + 1
  }))

  products.value = nextProducts.map((product, index) => ({
    ...product,
    sortOrder: items[index].sortOrder
  }))

  sorting.value = true
  try {
    await batchUpdateProductSort(items)
    eventBus.emit('product-sort-updated')
    showToast('产品排序已同步到首页')
  } catch (error) {
    console.error('Failed to update product sort:', error)
    products.value = previousProducts
    showToast('产品排序保存失败')
  } finally {
    sorting.value = false
  }
}

const onProductDrop = async (targetProductId: number, event: DragEvent) => {
  event.preventDefault()
  const sourceProductId = draggingProductId.value
  const dropPosition = dragOverPosition.value
  clearProductDrag()
  if (sourceProductId === null || sourceProductId === targetProductId || sorting.value) return

  const previousProducts = [...products.value]
  const sourceIndex = products.value.findIndex(product => product.id === sourceProductId)
  const targetIndex = products.value.findIndex(product => product.id === targetProductId)
  if (sourceIndex < 0 || targetIndex < 0) return

  const nextProducts = [...products.value]
  const [movedProduct] = nextProducts.splice(sourceIndex, 1)
  let insertIndex = targetIndex + (dropPosition === 'after' ? 1 : 0)
  if (sourceIndex < insertIndex) insertIndex--
  if (insertIndex === sourceIndex) return
  nextProducts.splice(insertIndex, 0, movedProduct)
  await persistProductOrder(nextProducts, previousProducts)
}

const handleSave = async () => {
  if (!hasChanges.value) {
    showToast('没有修改，无需保存')
    return
  }

  saving.value = true
  let successCount = 0
  let failCount = 0

  try {
    const saveTasks: Promise<void>[] = []
    for (const [productId, priceStr] of editingPrices.value) {
      if (priceStr === (originalPriceTextMap.value.get(productId) || '')) continue
      if (!priceStr) continue

      const currentPrice = Number(priceStr)
      if (Number.isNaN(currentPrice)) {
        failCount++
        continue
      }

      const existingPrice = priceMap.value.get(productId)
      if (existingPrice?.id != null) {
        saveTasks.push(updatePrice(existingPrice.id, {
          currentPrice,
          effectiveDate: selectedDate.value
        }).then(() => { successCount++ }).catch((error) => {
          console.error(`Failed to update price for product ${productId}:`, error)
          failCount++
        }))
      } else {
        saveTasks.push(addProductPrice(productId, {
          currentPrice,
          effectiveDate: selectedDate.value
        } as Price).then(() => { successCount++ }).catch((error) => {
          console.error(`Failed to add price for product ${productId}:`, error)
          failCount++
        }))
      }
    }

    await Promise.allSettled(saveTasks)
    await loadPrices()
    eventBus.emit('prices-updated')

    if (failCount === 0) {
      showToast(`保存成功，共 ${successCount} 条价格记录`)
    } else {
      showToast(`部分保存成功，成功 ${successCount} 条，失败 ${failCount} 条`)
    }
  } catch (error) {
    console.error('Failed to save prices:', error)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

const goBack = () => {
  if (hasChanges.value) {
    showConfirmDialog({
      title: '确认返回',
      message: '有未保存的修改，确定要返回吗？',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }).then(() => router.push('/products')).catch(() => {})
    return
  }
  router.push('/products')
}

const goToPrevDate = async () => {
  if (!await ensureCanChangeDate()) return
  const date = parseLocalDate(selectedDate.value)
  date.setDate(date.getDate() - 1)
  selectedDate.value = formatLocalDate(date)
}

const goToNextDate = async () => {
  if (!await ensureCanChangeDate()) return
  const date = parseLocalDate(selectedDate.value)
  date.setDate(date.getDate() + 1)
  selectedDate.value = formatLocalDate(date)
}

const onDateInputChange = async (event: Event) => {
  const nextDate = (event.target as HTMLInputElement).value
  if (!nextDate || nextDate === selectedDate.value) return
  if (!await ensureCanChangeDate()) {
    ;(event.target as HTMLInputElement).value = selectedDate.value
    return
  }
  selectedDate.value = nextDate
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(searchQuery, (value) => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    searchQueryDebounced.value = value.trim()
    tablePage.value = 0
    await loadProducts()
  }, 300)
})

watch(selectedDate, async () => {
  await loadPrices()
})

onMounted(async () => {
  await loadAllDicts()
  await loadData()
})
</script>

<template>
  <div class="price-maintenance-page">
    <template v-if="isPCLayout">
      <div class="pc-maintenance">
        <div class="pc-header">
          <div class="header-content">
            <button class="back-button" type="button" @click="goBack" title="返回产品列表">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <div class="header-text">
              <h1 class="page-title-pc">{{ formatDateDisplay(selectedDate) }} 价格维护</h1>
              <p class="page-subtitle">按当前日期录入当日售价，保留昨日售价、价格变化与月均价对照</p>
            </div>
          </div>
          <div class="header-actions">
            <div class="date-picker">
              <button class="date-nav-btn" type="button" @click="goToPrevDate" title="前一天">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="15 18 9 12 15 6"/>
                </svg>
              </button>
              <input type="date" :value="selectedDate" class="date-input" @change="onDateInputChange" />
              <button class="date-nav-btn" type="button" @click="goToNextDate" title="后一天">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </button>
            </div>
            <button class="btn-save" type="button" @click="handleSave" :disabled="saving || !hasChanges">
              <svg v-if="!saving" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
                <polyline points="17 21 17 13 7 13 7 21"/>
                <polyline points="7 3 7 8 15 8"/>
              </svg>
              <span v-if="saving" class="btn-spinner"></span>
              {{ saving ? '保存中...' : '保存修改' }}
            </button>
          </div>
        </div>

        <section class="maintenance-table-section">
          <div class="product-table-toolbar">
            <div>
              <h2 class="section-title-pc">产品价格表</h2>
            </div>
            <div class="table-filters">
              <div class="search-box-pc">
                <span class="search-icon-text">⌕</span>
                <input v-model="searchQuery" type="text" placeholder="搜索产品名称" class="search-input-pc" />
              </div>
              <select class="table-select category-select" :value="selectedCategoryId" @change="onCategoryChange" aria-label="产品分类筛选">
                <option value="">全部分类</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
              </select>
              <select :value="tableSize" class="table-select" @change="onTableSizeChange">
                <option :value="10">10条/页</option>
                <option :value="20">20条/页</option>
                <option :value="50">50条/页</option>
              </select>
            </div>
          </div>

          <div class="product-table-shell">
            <table class="maintenance-product-table">
              <colgroup>
                <col class="col-drag" />
                <col class="col-product" />
                <col class="col-category" />
                <col class="col-spec" />
                <col class="col-current" />
                <col class="col-budget" />
                <col class="col-yesterday" />
                <col class="col-change" />
                <col class="col-month" />
                <col class="col-unit" />
              </colgroup>
              <thead>
                <tr>
                  <th class="drag-header" aria-label="拖拽排序"></th>
                  <th>产品名称</th>
                  <th>类别</th>
                  <th>规格</th>
                  <th>当日售价</th>
                  <th>预算价格</th>
                  <th>昨日售价</th>
                  <th>价格变化</th>
                  <th>月均价</th>
                  <th>单位</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading || tableLoading">
                  <td colspan="10" class="table-state-cell">正在加载产品...</td>
                </tr>
                <tr v-else-if="products.length === 0">
                  <td colspan="10" class="table-state-cell">{{ searchQuery || selectedCategoryId ? '未找到匹配产品' : '暂无产品数据' }}</td>
                </tr>
                <tr
                  v-for="product in products"
                  v-else
                  :key="product.id"
                  :class="[getCardClass(product), {
                    dragging: draggingProductId === product.id,
                    'drag-over': dragOverProductId === product.id,
                    'drag-before': dragOverProductId === product.id && dragOverPosition === 'before',
                    'drag-after': dragOverProductId === product.id && dragOverPosition === 'after'
                  }]"
                  :style="getCardStyle(product)"
                  @dragover="onProductDragOver(product.id, $event)"
                  @drop="onProductDrop(product.id, $event)"
                  @dragend="clearProductDrag"
                >
                  <td class="drag-cell">
                    <button
                      class="drag-handle"
                      type="button"
                      draggable="true"
                      :disabled="sorting"
                      title="拖拽调整产品顺序"
                      @click.prevent
                      @dragstart="onProductDragStart(product, $event)"
                      @dragend="clearProductDrag"
                    >⋮⋮</button>
                  </td>
                  <td>
                    <div class="table-product-name">
                      <span class="table-category-dot"></span>
                      <div class="table-product-main">
                        <strong>{{ product.name }}</strong>
                        <span class="table-origin-chip" v-if="getProductOriginLabel(product)">{{ getProductOriginLabel(product) }}</span>
                      </div>
                    </div>
                  </td>
                  <td>{{ product.category?.name || '-' }}</td>
                  <td>
                    <div class="table-spec-marquee" :title="product.specs || '-'">
                      <span class="table-spec-text">{{ product.specs || '-' }}</span>
                    </div>
                  </td>
                  <td>
                    <div class="price-input-wrapper">
                      <span class="price-unit">{{ getProductCurrencySymbol(product.id) }}</span>
                      <input
                        type="number"
                        :value="getEditData(product.id)"
                        @input="updateEditPrice(product.id, ($event.target as HTMLInputElement).value)"
                        class="price-input"
                        :placeholder="getPricePlaceholder(product.id)"
                      />
                    </div>
                  </td>
                  <td><span class="budget-price-value">{{ formatBudgetPrice(product.id) }}</span></td>
                  <td><span class="table-price-muted">{{ formatPriceWithCurrency(product.id, getDisplayYesterdayPrice(product.id)) }}</span></td>
                  <td>
                    <span class="table-change" :class="getPriceChangeClass(getPriceChange(product.id))">
                      {{ formatPriceChange(product.id, getPriceChange(product.id)) }}
                    </span>
                  </td>
                  <td><span class="table-price-muted">{{ formatPriceWithCurrency(product.id, getDisplayMonthlyAvg(product.id)) }}</span></td>
                  <td>{{ getPriceUnit(product) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="table-pagination">
            <span>第 {{ tableTotalPages === 0 ? 0 : tablePage + 1 }} / {{ tableTotalPages }} 页</span>
            <div class="pagination-actions">
              <button class="page-btn" type="button" :disabled="tablePage <= 0" @click="goToPage(tablePage - 1)">上一页</button>
              <button class="page-btn" type="button" :disabled="tablePage + 1 >= tableTotalPages" @click="goToPage(tablePage + 1)">下一页</button>
            </div>
          </div>
        </section>
      </div>
    </template>

    <template v-else>
      <header class="navbar">
        <div class="navbar-left">
          <button class="back-btn" type="button" @click="goBack">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <h1 class="navbar-title">{{ formatDateDisplay(selectedDate) }}</h1>
        </div>
        <button class="save-btn" type="button" @click="handleSave" :disabled="saving || !hasChanges">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </header>

      <div class="mobile-toolbar">
        <div class="date-nav">
          <button class="date-nav-btn-mobile" type="button" @click="goToPrevDate" title="前一天">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <input type="date" :value="selectedDate" class="date-input-mobile" @change="onDateInputChange" />
          <button class="date-nav-btn-mobile" type="button" @click="goToNextDate" title="后一天">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </button>
        </div>
        <div class="mobile-filters">
          <input v-model="searchQuery" type="text" placeholder="搜索产品名称" class="mobile-search-input" />
          <select class="table-select mobile-select" :value="selectedCategoryId" @change="onCategoryChange">
            <option value="">全部分类</option>
            <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
          </select>
        </div>
      </div>

      <main class="content">
        <div v-if="loading || tableLoading" class="table-state-card">正在加载产品...</div>
        <div v-else-if="products.length === 0" class="table-state-card">{{ searchQuery || selectedCategoryId ? '未找到匹配产品' : '暂无产品数据' }}</div>
        <div v-else class="price-list">
          <article
            v-for="product in products"
            :key="product.id"
            class="price-card"
            :class="[getCardClass(product), {
              dragging: draggingProductId === product.id,
              'drag-over': dragOverProductId === product.id,
              'drag-before': dragOverProductId === product.id && dragOverPosition === 'before',
              'drag-after': dragOverProductId === product.id && dragOverPosition === 'after'
            }]"
            :style="getCardStyle(product)"
            @dragover="onProductDragOver(product.id, $event)"
            @drop="onProductDrop(product.id, $event)"
            @dragend="clearProductDrag"
          >
            <div class="card-header">
              <div class="table-product-main">
                <button
                  class="drag-handle mobile-drag-handle"
                  type="button"
                  draggable="true"
                  :disabled="sorting"
                  title="拖拽调整产品顺序"
                  @click.prevent
                  @dragstart="onProductDragStart(product, $event)"
                  @dragend="clearProductDrag"
                >⋮⋮</button>
                <strong class="product-name">{{ product.name }}</strong>
                <span class="table-origin-chip" v-if="getProductOriginLabel(product)">{{ getProductOriginLabel(product) }}</span>
              </div>
              <span class="product-category">{{ product.category?.name || '未分类' }}</span>
            </div>

            <div class="card-meta-row">
              <span>{{ product.specs || '-' }}</span>
              <span>{{ getPriceUnit(product) }}</span>
            </div>

            <div class="main-price-section">
              <label class="field-label">当日售价</label>
              <div class="price-input-wrapper">
                <span class="price-unit">{{ getProductCurrencySymbol(product.id) }}</span>
                <input
                  type="number"
                  :value="getEditData(product.id)"
                  @input="updateEditPrice(product.id, ($event.target as HTMLInputElement).value)"
                  class="price-input"
                  :placeholder="getPricePlaceholder(product.id)"
                />
              </div>
            </div>

            <div class="price-compare-row">
              <div class="compare-item">
                <span class="compare-label">预算价格</span>
                <span class="compare-value">{{ formatBudgetPrice(product.id) }}</span>
              </div>
              <div class="compare-item">
                <span class="compare-label">昨日售价</span>
                <span class="compare-value">{{ formatPriceWithCurrency(product.id, getDisplayYesterdayPrice(product.id)) }}</span>
              </div>
              <div class="compare-item">
                <span class="compare-label">价格变化</span>
                <span class="compare-value table-change" :class="getPriceChangeClass(getPriceChange(product.id))">
                  {{ formatPriceChange(product.id, getPriceChange(product.id)) }}
                </span>
              </div>
              <div class="compare-item">
                <span class="compare-label">月均价</span>
                <span class="compare-value">{{ formatPriceWithCurrency(product.id, getDisplayMonthlyAvg(product.id)) }}</span>
              </div>
            </div>
          </article>
        </div>

        <div class="table-pagination mobile-pagination">
          <span>第 {{ tableTotalPages === 0 ? 0 : tablePage + 1 }} / {{ tableTotalPages }} 页，共 {{ tableTotalElements }} 个</span>
          <div class="pagination-actions">
            <button class="page-btn" type="button" :disabled="tablePage <= 0" @click="goToPage(tablePage - 1)">上一页</button>
            <button class="page-btn" type="button" :disabled="tablePage + 1 >= tableTotalPages" @click="goToPage(tablePage + 1)">下一页</button>
          </div>
        </div>
      </main>
    </template>
  </div>
</template>

<style scoped>
.price-maintenance-page {
  display: flex;
  flex-direction: column;
  max-width: 100%;
  background: var(--bg-page);
}

.pc-maintenance {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xl);
}

.pc-header,
.maintenance-table-section {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-sm);
}

.pc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  flex-wrap: wrap;
}

.header-content,
.header-actions,
.product-table-toolbar,
.table-filters,
.date-picker,
.date-nav,
.pagination-actions,
.navbar-left,
.card-header,
.card-meta-row {
  display: flex;
  align-items: center;
}

.header-content {
  gap: var(--spacing-md);
  min-width: 0;
}

.header-actions,
.table-filters {
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.back-button,
.date-nav-btn,
.back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  transition: border-color var(--transition-fast), color var(--transition-fast), background var(--transition-fast);
}

.back-button {
  width: 40px;
  height: 40px;
  border-radius: var(--radius);
}

.back-button:hover,
.date-nav-btn:hover,
.back-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.page-title-pc {
  margin: 0;
  font-family: var(--font-heading);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: var(--text-primary);
}

.page-subtitle,
.panel-subtitle {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.date-picker {
  display: grid;
  grid-template-columns: 32px 150px 32px;
  gap: var(--spacing-xs);
  align-items: center;
  flex: 0 0 auto;
}

.date-input,
.date-input-mobile,
.mobile-search-input,
.table-select {
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  outline: none;
}

.date-input {
  width: 150px;
  padding: 0 var(--spacing-sm);
  cursor: pointer;
  flex: 0 0 150px;
}

.date-nav-btn {
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
}

.date-nav-btn:active,
.date-nav-btn-mobile:active {
  transform: none;
}

.btn-save,
.save-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: none;
  border-radius: var(--radius);
  background: var(--primary-color);
  color: #FFFFFF;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition: opacity var(--transition-fast), background var(--transition-fast);
}

.btn-save {
  min-height: 40px;
  padding: 0 var(--spacing-md);
}

.btn-save:disabled,
.save-btn:disabled,
.page-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.32);
  border-top-color: #FFFFFF;
  border-radius: 999px;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.maintenance-table-section {
  padding: var(--spacing-lg);
  overflow: hidden;
}

.product-table-toolbar {
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
  flex-wrap: wrap;
}

.section-title-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-body);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.search-box-pc {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: clamp(180px, 24vw, 260px);
  min-width: 0;
  padding: 0 var(--spacing-md);
  height: 40px;
  background: var(--gray-100);
  border-radius: var(--radius);
}

.search-icon-text {
  color: var(--text-muted);
}

.search-input-pc {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  outline: none;
}

.table-select {
  min-width: 112px;
  padding: 0 var(--spacing-sm);
}

.category-select {
  min-width: 128px;
}

.product-table-shell {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow-x: hidden;
}

.maintenance-product-table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: var(--font-size-sm);
}

.maintenance-product-table .col-drag { width: 3%; }
.maintenance-product-table .col-product { width: 18%; }
.maintenance-product-table .col-category { width: 8%; }
.maintenance-product-table .col-spec { width: 10%; }
.maintenance-product-table .col-current { width: 12%; }
.maintenance-product-table .col-budget { width: 10%; }
.maintenance-product-table .col-yesterday { width: 10%; }
.maintenance-product-table .col-change { width: 10%; }
.maintenance-product-table .col-month { width: 10%; }
.maintenance-product-table .col-unit { width: 9%; }

.maintenance-product-table th,
.maintenance-product-table td {
  padding: 10px 6px;
  border-bottom: 1px solid var(--gray-100);
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.maintenance-product-table th {
  color: var(--text-secondary);
  font-weight: 600;
  background: var(--gray-50);
}

.maintenance-product-table .drag-header {
  color: transparent;
  padding-left: 6px;
  padding-right: 6px;
  overflow: visible;
  text-overflow: clip;
}

.maintenance-product-table tbody tr:hover {
  background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 42%, var(--bg-card));
}

.maintenance-product-table tbody tr.dragging,
.price-card.dragging {
  opacity: 0.62;
  filter: saturate(1.1);
}

.maintenance-product-table tbody tr.dragging td {
  background: color-mix(in srgb, var(--primary-color) 6%, var(--bg-card));
}

.maintenance-product-table tbody tr.drag-before td {
  border-top: 3px solid var(--primary-color);
}

.maintenance-product-table tbody tr.drag-after td {
  border-bottom: 3px solid var(--primary-color);
}

.price-card.dragging {
  transform: scale(0.985);
  box-shadow: var(--shadow-md);
}

.price-card.drag-before {
  border-top: 3px solid var(--primary-color);
  box-shadow: 0 -8px 18px color-mix(in srgb, var(--primary-color) 14%, transparent);
}

.price-card.drag-after {
  border-bottom: 3px solid var(--primary-color);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--primary-color) 14%, transparent);
}

.maintenance-product-table tbody tr.drag-over td,
.price-card.drag-over {
  background: color-mix(in srgb, var(--primary-color) 5%, var(--bg-card));
}

.maintenance-product-table tbody tr.has-category .table-product-name strong {
  color: var(--category-primary);
}

.drag-cell {
  padding-left: 2px !important;
  padding-right: 2px !important;
  overflow: visible !important;
  text-overflow: clip !important;
  text-align: center !important;
}

.drag-handle {
  width: 26px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-muted);
  font-size: var(--font-size-base);
  font-weight: 700;
  cursor: grab;
  transition: all var(--transition-fast);
}

.drag-handle:hover:not(:disabled) {
  border-color: var(--primary-color);
  background: color-mix(in srgb, var(--primary-color) 8%, var(--bg-card));
  color: var(--primary-color);
}

.drag-handle:active {
  cursor: grabbing;
}

.drag-handle:disabled {
  cursor: wait;
  opacity: 0.45;
}

.table-product-name,
.table-product-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.table-product-main {
  overflow: hidden;
}

.table-product-name strong,
.product-name {
  overflow: hidden;
  color: var(--text-primary);
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-category-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  flex-shrink: 0;
  background: var(--category-primary, var(--primary-color));
}

.table-origin-chip {
  display: inline-flex;
  align-items: center;
  max-width: 72px;
  height: 22px;
  padding: 0 7px;
  border: 1px solid color-mix(in srgb, var(--category-primary, var(--primary-color)) 20%, var(--border-color));
  border-radius: 999px;
  background: color-mix(in srgb, var(--category-surface, var(--primary-color)) 38%, var(--bg-card));
  color: var(--category-primary, var(--primary-color));
  font-size: var(--font-size-xs);
  font-weight: 600;
  line-height: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-spec-marquee {
  width: 100%;
  overflow: hidden;
  white-space: nowrap;
}

.table-spec-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.table-spec-marquee:hover .table-spec-text {
  max-width: none;
  min-width: 100%;
  animation: spec-marquee 7s linear infinite;
}

@keyframes spec-marquee {
  0%, 12% { transform: translateX(0); }
  88%, 100% { transform: translateX(-100%); }
}

.price-input-wrapper {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  height: 38px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--gray-50);
  overflow: hidden;
}

.price-input-wrapper:focus-within {
  border-color: var(--primary-color);
  background: var(--bg-card);
}

.price-unit {
  flex-shrink: 0;
  padding-left: 10px;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.price-input {
  width: 100%;
  min-width: 0;
  height: 100%;
  padding: 0 8px 0 4px;
  border: none;
  background: transparent;
  color: var(--text-primary);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  font-weight: 700;
  outline: none;
}

.price-input::placeholder {
  color: var(--text-muted);
  font-weight: 500;
}

.budget-price-value,
.table-price-muted,
.compare-value {
  color: var(--text-secondary);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
}

.table-change {
  font-family: var(--font-mono);
  font-weight: 800;
}

.table-change.up {
  color: var(--price-rise-color);
}

.table-change.down {
  color: var(--price-fall-color);
}

.table-change.flat {
  color: var(--price-flat-color);
}

.table-state-cell {
  padding: 32px !important;
  color: var(--text-muted);
  text-align: center !important;
}

.table-state-card {
  padding: 32px;
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-muted);
  text-align: center;
}

.table-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
  padding-top: var(--spacing-md);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.page-btn {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-secondary);
  padding: 6px 10px;
  cursor: pointer;
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
}

.navbar-left {
  gap: var(--spacing-sm);
  min-width: 0;
}

.back-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--radius);
}

.navbar-title {
  margin: 0;
  color: var(--text-primary);
  font-family: var(--font-heading);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.save-btn {
  min-height: 36px;
  padding: 0 var(--spacing-md);
}

.mobile-toolbar {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
}

.date-nav {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 40px;
  gap: var(--spacing-sm);
  align-items: center;
}

.date-input-mobile {
  min-width: 0;
  width: 100%;
  padding: 0 var(--spacing-sm);
}

.date-nav-btn-mobile {
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-secondary);
  transition: border-color var(--transition-fast), color var(--transition-fast), background var(--transition-fast);
}

.mobile-filters {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 128px;
  gap: var(--spacing-sm);
}

.mobile-search-input {
  min-width: 0;
  padding: 0 var(--spacing-sm);
}

.mobile-select {
  width: 100%;
  min-width: 0;
}

.content {
  flex: 1;
  padding: var(--spacing-md);
  padding-bottom: 96px;
}

.price-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.mobile-sort-hint {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
}

.price-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--bg-card);
}

.price-card.has-category {
  border-color: var(--category-border);
  background: linear-gradient(135deg, var(--bg-card) 0%, color-mix(in srgb, var(--category-surface) 64%, var(--bg-card)) 100%);
}

.price-card.has-category .product-name {
  color: var(--category-primary);
}

.card-header {
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-sm);
  min-width: 0;
}

.product-category,
.compare-label,
.card-meta-row {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.product-category {
  flex-shrink: 0;
  padding-top: 2px;
}

.mobile-drag-handle {
  flex-shrink: 0;
  margin-left: -6px;
}

.card-meta-row {
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.main-price-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  color: var(--text-secondary);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.price-compare-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  border-radius: var(--radius);
  background: var(--gray-50);
}

.compare-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.mobile-pagination {
  flex-direction: column;
  align-items: flex-start;
}

@media (max-width: 480px) {
  .mobile-filters {
    grid-template-columns: 1fr;
  }
}
</style>
