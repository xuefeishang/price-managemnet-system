<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { VueDraggable } from 'vue-draggable-plus'
import { getProducts } from '@/api/products'
import { addProductPrice, updatePrice, getPricesByDateWithStats, batchUpdateProductSort } from '@/api/products'
import type { Product, Price } from '@/types'
import { eventBus } from '@/utils/eventBus'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

// 响应式布局
const isPCLayout = computed(() => windowWidth.value >= 1024)
defineExpose({ isPCLayout })

// 格式化日期显示
const formatDateDisplay = (dateStr: string) => {
  const date = new Date(dateStr)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

// 选中的日期
const selectedDate = ref(new Date().toISOString().split('T')[0])

// 产品列表
const products = ref<Product[]>([])

// 价格映射 (productId -> price)
const priceMap = ref<Map<number, Price>>(new Map())

// 昨日价格映射 (productId -> price)
const yesterdayPriceMap = ref<Map<number, Price>>(new Map())

// 月均价映射 (productId -> average price)
const monthlyAverageMap = ref<Map<number, number>>(new Map())

// 编辑中的价格
const editingPrices = ref<Map<number, string>>(new Map())

// 是否有修改
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

// 计算价格变化
const getPriceChange = (productId: number) => {
  const currentPrice = editingPrices.value.get(productId) ? parseFloat(editingPrices.value.get(productId)!) : null
  const yesterdayPrice = yesterdayPriceMap.value.get(productId)?.currentPrice || null

  if (currentPrice === null || yesterdayPrice === null) {
    return null
  }
  return currentPrice - yesterdayPrice
}

// 加载产品列表
const loadProducts = async () => {
  try {
    const response = await getProducts({ page: 0, size: 1000, status: 'ACTIVE' })
    const list = response.data.content || []
    // 按 sortOrder 排序，无排序值的排到最后
    list.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    products.value = list
  } catch (error) {
    console.error('Failed to load products:', error)
    showToast('加载产品列表失败')
  }
}

// 拖拽排序后自动保存
const savingSort = ref(false)
const handleDragEnd = async () => {
  savingSort.value = true
  try {
    const items = products.value.map((product, index) => ({
      id: product.id,
      sortOrder: index + 1
    }))
    await batchUpdateProductSort(items)
    products.value.forEach((p, i) => { p.sortOrder = i + 1 })
    showToast('排序已保存')
    eventBus.emit('product-sort-updated')
  } catch (error) {
    console.error('Failed to save sort order:', error)
    showToast('排序保存失败，请重试')
    // 恢复原始顺序
    loadProducts()
  } finally {
    savingSort.value = false
  }
}

// 加载指定日期的价格
const loadPrices = async () => {
  loading.value = true
  try {
    const response = await getPricesByDateWithStats(selectedDate.value)
    const items = response.data || []
    priceMap.value = new Map()
    yesterdayPriceMap.value = new Map()
    monthlyAverageMap.value = new Map()
    editingPrices.value = new Map()

    for (const item of items) {
      if (item.price && item.price.product) {
        const productId = item.price.product.id
        priceMap.value.set(productId, item.price)
        editingPrices.value.set(productId, String(item.price.currentPrice || ''))
        if (item.yesterdayPrice) {
          yesterdayPriceMap.value.set(productId, item.yesterdayPrice)
        }
        if (item.monthlyAveragePrice != null) {
          monthlyAverageMap.value.set(productId, item.monthlyAveragePrice)
        }
      }
    }

    // 为没有价格的产品初始化编辑数据
    products.value.forEach(product => {
      if (!editingPrices.value.has(product.id)) {
        editingPrices.value.set(product.id, '')
      }
    })
  } catch (error) {
    console.error('Failed to load prices:', error)
    showToast('加载价格数据失败')
  } finally {
    loading.value = false
  }
}

// 初始化编辑数据
const initEditingData = (product: Product) => {
  if (!editingPrices.value.has(product.id)) {
    const existingPrice = priceMap.value.get(product.id)
    editingPrices.value.set(product.id, existingPrice ? String(existingPrice.currentPrice || '') : '')
  }
}

// 获取编辑中的价格数据
const getEditData = (productId: number) => {
  initEditingData(products.value.find(p => p.id === productId)!)
  return editingPrices.value.get(productId) || ''
}

// 更新价格
const updateEditPrice = (productId: number, value: string) => {
  editingPrices.value.set(productId, value)
}

// 格式化数字显示
const formatPrice = (price: number | null | undefined) => {
  if (price === null || price === undefined) return '-'
  return price.toFixed(2)
}

// 格式化价格变化显示
const formatPriceChange = (change: number | null | undefined) => {
  if (change === null || change === undefined) return '-'
  if (change > 0) return `+${change.toFixed(2)}`
  if (change < 0) return change.toFixed(2)
  return '0.00'
}

// 获取价格变化样式
const getPriceChangeClass = (change: number | null | undefined) => {
  if (change === null || change === undefined) return ''
  if (change > 0) return 'positive'
  if (change < 0) return 'negative'
  return ''
}

// 保存所有价格
const handleSave = async () => {
  if (!hasChanges.value) {
    showToast('没有修改，无需保存')
    return
  }

  saving.value = true
  let successCount = 0
  let failCount = 0

  try {
    // 收集所有需要保存的变更
    const saveTasks: Promise<void>[] = []
    for (const [productId, priceStr] of editingPrices.value) {
      const currentPrice = priceStr ? parseFloat(priceStr) : undefined

      // 如果没有填写价格，跳过
      if (!priceStr) {
        continue
      }

      const existingPrice = priceMap.value.get(productId)

      if (existingPrice) {
        saveTasks.push(
          updatePrice(existingPrice.id, {
            currentPrice,
            effectiveDate: selectedDate.value
          }).then(() => { successCount++ }).catch((error) => {
            console.error(`Failed to save price for product ${productId}:`, error)
            failCount++
          })
        )
      } else {
        saveTasks.push(
          addProductPrice(productId, {
            currentPrice,
            effectiveDate: selectedDate.value
          } as Price).then(() => { successCount++ }).catch((error) => {
            console.error(`Failed to save price for product ${productId}:`, error)
            failCount++
          })
        )
      }
    }

    await Promise.allSettled(saveTasks)

    if (failCount === 0) {
      showToast(`保存成功，共 ${successCount} 条价格记录`)
      loadPrices()
      eventBus.emit('prices-updated')
    } else {
      showToast(`部分保存成功，成功 ${successCount} 条，失败 ${failCount} 条`)
      eventBus.emit('prices-updated')
    }
  } catch (error) {
    console.error('Failed to save prices:', error)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

// 返回
const goBack = () => {
  if (hasChanges.value) {
    showConfirmDialog({
      title: '确认返回',
      message: '有未保存的修改，确定要返回吗？',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    }).then(() => {
      router.push('/products')
    }).catch(() => {})
  } else {
    router.push('/products')
  }
}

// 日期变化时重新加载
watch(selectedDate, () => {
  loadPrices()
})

// 前一天
const goToPrevDate = () => {
  const date = new Date(selectedDate.value)
  date.setDate(date.getDate() - 1)
  selectedDate.value = date.toISOString().split('T')[0]
}

// 后一天
const goToNextDate = () => {
  const date = new Date(selectedDate.value)
  date.setDate(date.getDate() + 1)
  selectedDate.value = date.toISOString().split('T')[0]
}

// 响应式监听
const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadProducts()
  loadPrices()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="price-maintenance-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-maintenance">
        <!-- 页面标题区 -->
        <div class="pc-header">
          <div class="header-content">
            <button class="back-button" @click="goBack">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <div class="header-text">
              <h1 class="page-title-pc">{{ formatDateDisplay(selectedDate) }} 价格维护</h1>
              <p class="page-subtitle">按日期维护产品价格，支持批量更新</p>
            </div>
          </div>
          <div class="header-actions">
            <div class="date-picker">
              <button class="date-nav-btn" @click="goToPrevDate" title="前一天">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="15 18 9 12 15 6"/>
                </svg>
              </button>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
                <line x1="16" y1="2" x2="16" y2="6"/>
                <line x1="8" y1="2" x2="8" y2="6"/>
                <line x1="3" y1="10" x2="21" y2="10"/>
              </svg>
              <input
                type="date"
                v-model="selectedDate"
                class="date-input"
              />
              <button class="date-nav-btn" @click="goToNextDate" title="后一天">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </button>
            </div>
            <button
              class="btn-save"
              @click="handleSave"
              :disabled="saving || !hasChanges"
            >
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

        <!-- 价格表格 -->
        <div class="price-table" v-if="!loading">
          <div class="table-header">
            <div class="table-cell seq-col">序号</div>
            <div class="table-cell product">产品信息</div>
            <div class="table-cell price-col">当日售价</div>
            <div class="table-cell price-col">昨日售价</div>
            <div class="table-cell price-col">价格变化</div>
            <div class="table-cell price-col">月均价</div>
            <div class="table-cell unit">单位</div>
          </div>
          <VueDraggable
            v-model="products"
            tag="div"
            class="table-body"
            :animation="150"
            handle=".drag-handle"
            ghost-class="drag-ghost"
            @end="handleDragEnd"
          >
            <div
              v-for="(product, index) in products"
              :key="product.id"
              class="table-row"
            >
              <div class="table-cell seq-col">
                <span class="drag-handle" title="拖拽排序">
                  <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
                    <circle cx="5.5" cy="3" r="1.2"/>
                    <circle cx="10.5" cy="3" r="1.2"/>
                    <circle cx="5.5" cy="8" r="1.2"/>
                    <circle cx="10.5" cy="8" r="1.2"/>
                    <circle cx="5.5" cy="13" r="1.2"/>
                    <circle cx="10.5" cy="13" r="1.2"/>
                  </svg>
                </span>
                <span class="seq-number">{{ index + 1 }}</span>
              </div>
              <div class="table-cell product">
                <div class="product-info">
                  <span class="product-name">{{ product.name }}</span>
                </div>
              </div>
              <div class="table-cell price-col">
                <div class="price-input-wrapper">
                  <span class="price-unit">¥</span>
                  <input
                    type="number"
                    :value="getEditData(product.id)"
                    @input="updateEditPrice(product.id, ($event.target as HTMLInputElement).value)"
                    class="price-input"
                    placeholder="0.00"
                  />
                </div>
              </div>
              <div class="table-cell price-col">
                <span class="price-value">
                  {{ formatPrice(yesterdayPriceMap.get(product.id)?.currentPrice) }}
                </span>
              </div>
              <div class="table-cell price-col">
                <span class="price-change" :class="getPriceChangeClass(getPriceChange(product.id))">
                  {{ formatPriceChange(getPriceChange(product.id)) }}
                </span>
              </div>
              <div class="table-cell price-col">
                <span class="price-value">
                  {{ formatPrice(monthlyAverageMap.get(product.id)) }}
                </span>
              </div>
              <div class="table-cell unit">
                <span class="unit-text">{{ priceMap.get(product.id)?.unit || '-' }}</span>
              </div>
            </div>
          </VueDraggable>

          <div v-if="products.length === 0" class="empty-state">
            暂无产品数据
          </div>
        </div>

        <div v-else class="loading-state">
          <div class="loading-spinner"></div>
          <span>加载中...</span>
        </div>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 顶部导航栏 -->
      <header class="navbar">
        <div class="navbar-left">
          <button class="back-btn" @click="goBack">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <h1 class="navbar-title">{{ formatDateDisplay(selectedDate) }}</h1>
        </div>
        <button
          class="save-btn"
          @click="handleSave"
          :disabled="saving || !hasChanges"
        >
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </header>

      <!-- 日期选择 -->
      <div class="date-section">
        <div class="date-nav">
          <button class="date-nav-btn-mobile" @click="goToPrevDate" title="前一天">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <div class="date-center">
            <label class="date-label">选择日期</label>
            <input
              type="date"
              v-model="selectedDate"
              class="date-input-mobile"
            />
          </div>
          <button class="date-nav-btn-mobile" @click="goToNextDate" title="后一天">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </button>
        </div>
      </div>

      <!-- 产品价格列表 -->
      <main class="content" v-if="!loading">
        <div class="price-list">
          <VueDraggable
            v-model="products"
            tag="div"
            class="price-list"
            :animation="150"
            handle=".drag-handle"
            ghost-class="drag-ghost-mobile"
            @end="handleDragEnd"
          >
            <div
              v-for="(product, index) in products"
              :key="product.id"
              class="price-card"
            >
              <div class="card-header">
                <span class="drag-handle" title="拖拽排序">
                  <svg width="18" height="18" viewBox="0 0 16 16" fill="currentColor">
                    <circle cx="5.5" cy="3" r="1.2"/>
                    <circle cx="10.5" cy="3" r="1.2"/>
                    <circle cx="5.5" cy="8" r="1.2"/>
                    <circle cx="10.5" cy="8" r="1.2"/>
                    <circle cx="5.5" cy="13" r="1.2"/>
                    <circle cx="10.5" cy="13" r="1.2"/>
                  </svg>
                </span>
                <span class="card-seq">{{ index + 1 }}</span>
                <div class="product-info">
                  <span class="product-name">{{ product.name }}</span>
                </div>
              </div>

            <!-- 主要价格信息 -->
            <div class="main-price-section">
              <div class="main-price-field">
                <label class="field-label">当日售价</label>
                <div class="price-input-wrapper">
                  <span class="price-unit">¥</span>
                  <input
                    type="number"
                    :value="getEditData(product.id)"
                    @input="updateEditPrice(product.id, ($event.target as HTMLInputElement).value)"
                    class="price-input"
                    placeholder="0.00"
                  />
                </div>
              </div>
            </div>

            <!-- 价格对比信息 -->
            <div class="price-compare-row">
              <div class="compare-item">
                <span class="compare-label">昨日售价</span>
                <span class="compare-value">{{ formatPrice(yesterdayPriceMap.get(product.id)?.currentPrice) }}</span>
              </div>
              <div class="compare-item">
                <span class="compare-label">价格变化</span>
                <span class="compare-value" :class="getPriceChangeClass(getPriceChange(product.id))">
                  {{ formatPriceChange(getPriceChange(product.id)) }}
                </span>
              </div>
              <div class="compare-item">
                <span class="compare-label">月均价</span>
                <span class="compare-value">{{ formatPrice(monthlyAverageMap.get(product.id)) }}</span>
              </div>
            </div>

            <div class="unit-row">
              <span class="unit-label">单位：</span>
              <span class="unit-value">{{ priceMap.get(product.id)?.unit || '-' }}</span>
            </div>
          </div>
          </VueDraggable>

          <div v-if="products.length === 0" class="empty-state">
            暂无产品数据
          </div>
        </div>
      </main>

      <div v-else class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.price-maintenance-page {
  background-color: #F5F5F5;
}

/* ==================== PC布局 ==================== */
.pc-maintenance {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.pc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-button {
  width: 40px;
  height: 40px;
  border: 1px solid #E5E5E5;
  background: #FFFFFF;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  transition: all 150ms;
}

.back-button:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.page-subtitle {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #888888;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.date-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #F9FAFB;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  color: #666666;
}

.date-input {
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  outline: none;
  cursor: pointer;
}

.date-nav-btn {
  width: 28px;
  height: 28px;
  border: 1px solid #E5E5E5;
  background: #FFFFFF;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  transition: all 150ms;
  flex-shrink: 0;
}

.date-nav-btn:hover {
  background: #F5F5F5;
  color: #1A1A1A;
  border-color: #CCCCCC;
}

.btn-save {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-save:hover:not(:disabled) {
  background: #0D8A8A;
}

.btn-save:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #FFFFFF;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 价格表格 */
.price-table {
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: center;
  background: #FAFAFA;
  border-bottom: 1px solid #E5E5E5;
  padding: 0 20px;
}

.table-row {
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid #F3F4F6;
  transition: background-color 150ms;
}

.table-row:last-child {
  border-bottom: none;
}

.table-row:hover {
  background: #FAFAFA;
}

.table-cell {
  padding: 14px 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
}

.table-cell.product {
  flex: 2;
  min-width: 200px;
}

.table-cell.price-col {
  flex: 1.2;
  min-width: 120px;
}

.table-cell.unit {
  flex: 0.6;
  min-width: 80px;
  color: #888888;
  font-size: 13px;
}

.table-cell.seq-col {
  flex: 0.7;
  min-width: 70px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #888888;
  font-size: 13px;
}

.drag-handle {
  cursor: grab;
  color: #C0C4CC;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  border-radius: 4px;
  transition: color 150ms, background 150ms;
  flex-shrink: 0;
}

.drag-handle:hover {
  color: #0D6E6E;
  background: #F0F0F0;
}

.drag-handle:active {
  cursor: grabbing;
}

.seq-number {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #888888;
  font-weight: 500;
}

.table-body {
  display: flex;
  flex-direction: column;
}

.drag-ghost {
  opacity: 0.4;
  background: #E8F5F5;
}

.drag-ghost .drag-handle {
  color: #0D6E6E;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name {
  font-weight: 500;
  color: #1A1A1A;
}

.price-input-wrapper {
  display: flex;
  align-items: center;
  background: #F9FAFB;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  overflow: hidden;
}

.price-unit {
  padding: 6px 0 6px 10px;
  color: #6B7280;
  font-size: 13px;
  font-weight: 500;
}

.price-input {
  flex: 1;
  padding: 8px 8px 8px 4px;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  outline: none;
}

.price-input::placeholder {
  color: #D1D5DB;
}

.price-input:focus {
  background: #FFFFFF;
}

.price-input-wrapper:focus-within {
  border-color: #0D6E6E;
  background: #FFFFFF;
}

.price-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
  color: #1A1A1A;
}

.price-change {
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
  font-weight: 500;
}

.price-change.positive {
  color: #EF4444;
}

.price-change.negative {
  color: #10B981;
}

.price-change:not(.positive):not(.negative) {
  color: #888888;
}

.unit-text {
  font-size: 13px;
  color: #6B7280;
}

/* 加载/空状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px;
  gap: 16px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  color: #666666;
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
}

.empty-state {
  padding: 60px 24px;
  text-align: center;
  color: #888888;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

/* ==================== 移动端布局 ==================== */
@media (max-width: 1024px) {
  .pc-maintenance {
    display: none;
  }
}

.price-maintenance-page {
  display: flex;
  flex-direction: column;
  max-width: 100%;
}

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
  gap: 12px;
}

.back-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #1A1A1A;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.back-btn:hover {
  background: #F5F5F5;
}

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 18px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.save-btn {
  padding: 8px 16px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 150ms;
}

.save-btn:hover:not(:disabled) {
  background: #0D8A8A;
}

.save-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.date-section {
  padding: 16px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
}

.date-nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.date-center {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.date-nav-btn-mobile {
  width: 36px;
  height: 36px;
  border: 1px solid #E5E5E5;
  background: #F9FAFB;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  transition: all 150ms;
  flex-shrink: 0;
}

.date-nav-btn-mobile:active {
  background: #E8F5F5;
  color: #0D6E6E;
  border-color: #0D6E6E;
}

.date-label {
  display: block;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
  color: #6B7280;
  margin-bottom: 8px;
}

.date-input-mobile {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  background: #FFFFFF;
  box-sizing: border-box;
}

.content {
  flex: 1;
  padding: 16px;
  padding-bottom: 100px;
}

.price-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.price-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
}

.card-header {
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #F3F4F6;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-header .drag-handle {
  color: #C0C4CC;
}

.card-header .drag-handle:hover {
  color: #0D6E6E;
  background: #F5F5F5;
}

.card-seq {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 600;
  color: #0D6E6E;
  min-width: 20px;
}

.drag-ghost-mobile {
  opacity: 0.4;
  background: #E8F5F5;
  border-color: #0D6E6E;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.product-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #1A1A1A;
}

.main-price-section {
  margin-bottom: 16px;
}

.main-price-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
  color: #6B7280;
}

.price-compare-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px;
  background: #F9FAFB;
  border-radius: 8px;
}

.compare-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: center;
}

.compare-label {
  font-family: 'Inter', sans-serif;
  font-size: 11px;
  color: #888888;
}

.compare-value {
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
}

.compare-value.positive {
  color: #EF4444;
}

.compare-value.negative {
  color: #10B981;
}

.unit-row {
  display: flex;
  align-items: center;
  gap: 4px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #888888;
}

.unit-value {
  color: #6B7280;
}

.price-input-wrapper {
  display: flex;
  align-items: center;
  background: #F9FAFB;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  overflow: hidden;
}

.price-unit {
  padding: 8px 0 8px 10px;
  color: #6B7280;
  font-weight: 500;
  font-size: 13px;
}

.price-input {
  flex: 1;
  padding: 8px 10px 8px 4px;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 15px;
  font-weight: 500;
  color: #1A1A1A;
  outline: none;
}

.price-input::placeholder {
  color: #D1D5DB;
  font-weight: 400;
}

.price-input-wrapper:focus-within {
  border-color: #0D6E6E;
  background: #FFFFFF;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
  gap: 16px;
  color: #666666;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

.empty-state {
  padding: 48px 24px;
  text-align: center;
  color: #888888;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

@media (max-width: 480px) {
  .content {
    padding: 12px;
  }

  .price-card {
    padding: 14px;
    border-radius: 10px;
  }

  .price-compare-row {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
