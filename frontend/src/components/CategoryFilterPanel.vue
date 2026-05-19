<script setup lang="ts">
/**
 * 分类筛选面板组件
 * 支持折叠分类按钮、多选筛选、已选数量徽章
 */
import { ref, onMounted } from 'vue'
import { getCategories } from '@/api/categories'
import { getCategoryVisual, registerCategoryCodes } from '@/composables/useCategoryVisual'
import CategoryIcons from '@/components/icons/CategoryIcons.vue'
import type { ProductCategory } from '@/types'

const props = withDefaults(defineProps<{
  selectedIds: number[]
  multiSelect?: boolean
}>(), {
  multiSelect: true
})

const emit = defineEmits<{
  (e: 'select', ids: number[]): void
  (e: 'clear'): void
}>()

const categories = ref<ProductCategory[]>([])
const expanded = ref(false)
const loading = ref(false)

// 加载分类列表
const loadCategories = async () => {
  loading.value = true
  try {
    const res = await getCategories('ACTIVE')
    categories.value = res.data || []
    // 注册分类ID到Code的映射
    registerCategoryCodes(categories.value.map(c => ({ id: c.id, code: c.code })))
  } catch (e) {
    console.error('Failed to load categories:', e)
  } finally {
    loading.value = false
  }
}

// 切换分类选择
const toggleCategory = (id: number) => {
  if (props.multiSelect) {
    const newIds = props.selectedIds.includes(id)
      ? props.selectedIds.filter(i => i !== id)
      : [...props.selectedIds, id]
    emit('select', newIds)
  } else {
    emit('select', [id])
    expanded.value = false
  }
}

// 判断是否选中
const isSelected = (id: number) => props.selectedIds.includes(id)

// 清除筛选
const handleClear = () => {
  emit('clear')
  expanded.value = false
}

// 全选
const handleSelectAll = () => {
  const allIds = categories.value.map(c => c.id)
  emit('select', allIds)
}

// 反选
const handleInvert = () => {
  const allIds = new Set(categories.value.map(c => c.id))
  const newIds = props.selectedIds.filter(id => !allIds.has(id))
  categories.value.forEach(c => {
    if (!props.selectedIds.includes(c.id)) {
      newIds.push(c.id)
    }
  })
  emit('select', newIds)
}

// 点击外部关闭
const handleClickOutside = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.category-filter')) {
    expanded.value = false
  }
}

onMounted(() => {
  loadCategories()
  document.addEventListener('click', handleClickOutside)
})

// 组件卸载时移除事件监听
import { onUnmounted } from 'vue'
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="category-filter" @click.stop>
    <!-- 触发按钮 -->
    <button class="filter-trigger" @click="expanded = !expanded" :class="{ active: selectedIds.length > 0 }">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
      </svg>
      <span class="trigger-text">分类</span>
      <span v-if="selectedIds.length > 0" class="filter-count">{{ selectedIds.length }}</span>
    </button>

    <!-- 下拉面板 -->
    <transition name="slide-down">
      <div v-if="expanded" class="filter-panel">
        <div class="panel-header">
          <span class="panel-title">选择分类</span>
          <div class="panel-actions">
            <button v-if="multiSelect" class="action-btn" @click="handleSelectAll">全选</button>
            <button v-if="multiSelect" class="action-btn" @click="handleInvert">反选</button>
            <button v-if="selectedIds.length > 0" class="action-btn clear" @click="handleClear">
              清除
            </button>
          </div>
        </div>

        <div class="category-list" v-if="!loading">
          <div
            v-for="cat in categories"
            :key="cat.id"
            class="category-item"
            :class="{ selected: isSelected(cat.id) }"
            :style="isSelected(cat.id) ? { borderColor: getCategoryVisual(cat.id).primaryColor } : {}"
            @click="toggleCategory(cat.id)"
          >
            <CategoryIcons
              :icon="getCategoryVisual(cat.id).icon"
              :size="20"
              :color="isSelected(cat.id) ? getCategoryVisual(cat.id).primaryColor : '#86909C'"
            />
            <span
              class="category-name"
              :style="isSelected(cat.id) ? { color: getCategoryVisual(cat.id).primaryColor } : {}"
            >
              {{ cat.name }}
            </span>
            <span v-if="isSelected(cat.id)" class="check-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                <polyline points="20 6 9 17 4 12"/>
              </svg>
            </span>
          </div>
        </div>

        <div v-else class="category-loading">
          <span>加载中...</span>
        </div>

        <div v-if="categories.length === 0 && !loading" class="category-empty">
          <span>暂无分类</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.category-filter {
  position: relative;
  display: inline-block;
}

.filter-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.filter-trigger:hover {
  border-color: var(--dashboard-primary);
  color: var(--dashboard-primary);
}

.filter-trigger.active {
  border-color: var(--dashboard-primary);
  background: rgba(22, 93, 255, 0.05);
  color: var(--dashboard-primary);
}

.trigger-text {
  font-weight: 500;
}

.filter-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  background: var(--dashboard-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  border-radius: 9px;
}

.filter-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  min-width: 240px;
  max-width: 320px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  z-index: 100;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  background: var(--gray-50);
}

.panel-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}

.panel-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 4px 8px;
  font-size: 12px;
  color: var(--text-secondary);
  background: transparent;
  border: none;
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--gray-100);
  color: var(--text-primary);
}

.action-btn.clear {
  color: var(--error-color);
}

.action-btn.clear:hover {
  background: var(--error-bg);
}

.category-list {
  max-height: 300px;
  overflow-y: auto;
  padding: 8px;
}

.category-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.category-item:hover {
  background: var(--gray-50);
}

.category-item.selected {
  background: rgba(22, 93, 255, 0.05);
  border-color: var(--dashboard-primary);
}

.category-name {
  flex: 1;
  font-size: var(--font-size-sm);
  color: var(--text-primary);
}

.check-icon {
  color: var(--dashboard-primary);
}

.category-loading,
.category-empty {
  padding: 24px;
  text-align: center;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

/* 动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.2s ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

/* 移动端适配 */
@media (max-width: 768px) {
  .filter-panel {
    position: fixed;
    top: auto;
    bottom: 0;
    left: 0;
    right: 0;
    max-width: 100%;
    border-radius: var(--radius-lg) var(--radius-lg) 0 0;
    max-height: 70vh;
  }

  .slide-down-enter-from,
  .slide-down-leave-to {
    transform: translateY(100%);
  }
}
</style>
