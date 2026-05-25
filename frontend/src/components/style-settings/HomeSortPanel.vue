<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useHomePreviewState } from '@/composables/useHomePreviewState'
import { useHomeProductOrderState } from '@/composables/useHomeProductOrderState'
import { loadAllDicts } from '@/composables/useDict'

const homeState = useHomePreviewState()
const orderState = useHomeProductOrderState()
const widgetDragKey = ref('')
const orderDragKey = ref('')
const insertIndicator = ref<{ list: 'widgets' | 'categories' | 'products'; index: number } | null>(null)

const sortedWidgets = computed(() =>
  [...homeState.widgets.value].sort((a, b) => a.order - b.order)
)

const featuredCount = computed(() => {
  return Math.min(homeState.layoutConfig.value.featuredProductCount, orderState.featuredCount.value)
})

const onWidgetDragStart = (key: string) => {
  widgetDragKey.value = key
}

const getInsertIndex = (event: DragEvent, index: number) => {
  const target = event.currentTarget as HTMLElement | null
  if (!target) return index
  const rect = target.getBoundingClientRect()
  return event.clientY < rect.top + rect.height / 2 ? index : index + 1
}

const onWidgetDragOver = (event: DragEvent, index: number) => {
  event.preventDefault()
  insertIndicator.value = { list: 'widgets', index: getInsertIndex(event, index) }
}

const onWidgetDrop = (targetIndex: number) => {
  homeState.reorderWidgetToIndex(widgetDragKey.value, targetIndex)
  widgetDragKey.value = ''
  insertIndicator.value = null
}

const clearWidgetDrag = () => {
  widgetDragKey.value = ''
  insertIndicator.value = null
}

const onOrderDragStart = (scope: 'category' | 'product', key: string, index: number) => {
  orderDragKey.value = `${scope}-${key}-${index}`
  orderState.onDragStart(scope, key, index)
}

const onOrderDragOver = (event: DragEvent, list: 'categories' | 'products', index: number) => {
  orderState.onDragOver(event)
  insertIndicator.value = { list, index: getInsertIndex(event, index) }
}

const clearOrderDrag = () => {
  orderDragKey.value = ''
  insertIndicator.value = null
}

const onCategoryDrop = (index: number) => {
  orderState.onDropGroupAt(index)
  clearOrderDrag()
}

const onProductDrop = (index: number) => {
  orderState.onDropProductAt(index)
  clearOrderDrag()
}

const onListTailDragOver = (event: DragEvent, list: 'widgets' | 'categories' | 'products', index: number) => {
  event.preventDefault()
  insertIndicator.value = { list, index }
}

const hasInsertBefore = (list: 'widgets' | 'categories' | 'products', index: number) =>
  insertIndicator.value?.list === list && insertIndicator.value.index === index

const hasInsertAfter = (list: 'widgets' | 'categories' | 'products', index: number, length: number) =>
  index === length - 1 && insertIndicator.value?.list === list && insertIndicator.value.index === length

onMounted(async () => {
  await loadAllDicts()
  homeState.loadConfig()
  orderState.loadOrder()
})
</script>

<template>
  <div class="home-sort-panel">
    <section class="config-section">
      <h2 class="section-title">
        首页组件顺序
        <span class="section-status">已启用 {{ homeState.enabledCount.value }}/{{ homeState.widgets.value.length }}</span>
      </h2>
      <p class="section-hint">控制 Home 页面经营摘要、重点关注指标、重点走势、产品列表、风险预警等组件块的上下顺序，点击顶部保存配置后生效。</p>

      <div class="sort-list">
        <div
          v-for="(widget, index) in sortedWidgets"
          :key="widget.key"
          class="sort-item"
          :class="{
            disabled: !widget.enabled,
            dragging: widgetDragKey === widget.key,
            'insert-before': hasInsertBefore('widgets', index),
            'insert-after': hasInsertAfter('widgets', index, sortedWidgets.length)
          }"
          draggable="true"
          @dragstart="onWidgetDragStart(widget.key)"
          @dragover="onWidgetDragOver($event, index)"
          @drop="onWidgetDrop(insertIndicator?.index ?? index)"
          @dragend="clearWidgetDrag"
        >
          <div class="item-main">
            <span class="drag-handle" title="拖拽排序">⋮⋮</span>
            <span class="item-index">{{ index + 1 }}</span>
            <div class="item-text">
              <span class="item-name">{{ widget.name }}</span>
              <span class="item-meta">{{ widget.enabled ? '显示中' : '已关闭' }}</span>
            </div>
          </div>
        </div>
        <div
          v-if="widgetDragKey"
          class="drop-tail"
          :class="{ active: insertIndicator?.list === 'widgets' && insertIndicator.index === sortedWidgets.length }"
          @dragover="onListTailDragOver($event, 'widgets', sortedWidgets.length)"
          @drop="onWidgetDrop(sortedWidgets.length)"
        ></div>
      </div>
    </section>

    <section class="config-section product-order-section">
      <div class="section-header">
        <div>
          <h2 class="section-title inline">首页产品列表顺序</h2>
          <p class="section-hint">先调整分类顺序，再调整分类内产品顺序；重点关注指标和重点走势会按“首页展示”产品的当前顺序取前 {{ featuredCount }} 个。修改后点击顶部保存配置生效。</p>
        </div>
      </div>

      <div v-if="orderState.loading.value" class="loading-state">加载中...</div>
      <div v-else-if="orderState.groups.value.length === 0" class="empty-state">暂无可排序的启用分类或产品</div>
      <div v-else class="order-workbench">
        <aside class="category-order-pane" aria-label="分类顺序">
          <div class="pane-title">分类顺序</div>
          <div class="category-order-list">
            <button
              v-for="(group, index) in orderState.groups.value"
              :key="orderState.getGroupKey(group)"
              class="category-order-item"
              :class="{
                active: orderState.getGroupKey(group) === orderState.selectedGroupKey.value,
                dragging: orderDragKey === `category-${orderState.getGroupKey(group)}-${index}`,
                'insert-before': hasInsertBefore('categories', index),
                'insert-after': hasInsertAfter('categories', index, orderState.groups.value.length)
              }"
              type="button"
              draggable="true"
              :aria-current="orderState.getGroupKey(group) === orderState.selectedGroupKey.value ? 'true' : undefined"
              @click="orderState.selectGroup(group)"
              @dragstart="onOrderDragStart('category', orderState.getGroupKey(group), index)"
              @dragover="onOrderDragOver($event, 'categories', index)"
              @drop="onCategoryDrop(insertIndicator?.index ?? index)"
              @dragend="clearOrderDrag"
            >
              <span class="drag-handle" title="拖拽排序">⋮⋮</span>
              <span class="item-index">{{ index + 1 }}</span>
              <span class="category-text">
                <span class="item-name">{{ group.name }}</span>
                <span class="item-meta">{{ group.products.length }} 个启用产品</span>
              </span>
            </button>
            <div
              v-if="orderDragKey.startsWith('category-')"
              class="drop-tail compact"
              :class="{ active: insertIndicator?.list === 'categories' && insertIndicator.index === orderState.groups.value.length }"
              @dragover="onListTailDragOver($event, 'categories', orderState.groups.value.length)"
              @drop="onCategoryDrop(orderState.groups.value.length)"
            ></div>
          </div>
        </aside>

        <div class="product-order-pane" aria-label="分类内产品顺序">
          <div class="pane-title product-pane-title">
            <span>{{ orderState.selectedGroup.value?.name || '产品顺序' }}</span>
            <span class="pane-meta">{{ orderState.selectedGroup.value?.products.length || 0 }} 个启用产品</span>
          </div>

          <div v-if="!orderState.selectedGroup.value || orderState.selectedGroup.value.products.length === 0" class="empty-state pane-empty">
            当前分类暂无启用产品
          </div>

          <div v-else class="product-order-list">
            <div
              v-for="(product, index) in orderState.selectedGroup.value.products"
              :key="product.id"
              class="product-order-item"
              :class="{
                dragging: orderDragKey === `product-${orderState.getGroupKey(orderState.selectedGroup.value)}-${index}`,
                'insert-before': hasInsertBefore('products', index),
                'insert-after': hasInsertAfter('products', index, orderState.selectedGroup.value.products.length)
              }"
              draggable="true"
              @dragstart="onOrderDragStart('product', orderState.getGroupKey(orderState.selectedGroup.value), index)"
              @dragover="onOrderDragOver($event, 'products', index)"
              @drop="onProductDrop(insertIndicator?.index ?? index)"
              @dragend="clearOrderDrag"
            >
              <span class="drag-handle" title="拖拽排序">⋮⋮</span>
              <span class="item-index">{{ index + 1 }}</span>
              <div class="item-text">
                <span class="item-name">{{ product.name }}</span>
                <span class="item-meta">{{ orderState.getProductMeta(product) }}</span>
              </div>
              <span v-if="product.showOnHome" class="home-badge">首页展示</span>
            </div>
            <div
              v-if="orderDragKey.startsWith('product-')"
              class="drop-tail compact"
              :class="{ active: insertIndicator?.list === 'products' && insertIndicator.index === orderState.selectedGroup.value.products.length }"
              @dragover="onListTailDragOver($event, 'products', orderState.selectedGroup.value.products.length)"
              @drop="onProductDrop(orderState.selectedGroup.value.products.length)"
            ></div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-sort-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-section {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 12px 0;
}

.section-title.inline {
  display: block;
  margin-bottom: 8px;
}

.section-status {
  font-size: var(--font-size-xs);
  color: #666666;
  padding: 4px 12px;
  background: #F5F5F5;
  border-radius: 4px;
}

.section-hint {
  margin: 0 0 20px 0;
  color: #666666;
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}

.sort-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sort-item,
.product-order-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  background: #FAFAFA;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  cursor: grab;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease,
    background-color 180ms ease,
    opacity 180ms ease;
  position: relative;
  will-change: transform;
}

.sort-item:hover,
.product-order-item:hover {
  border-color: color-mix(in srgb, var(--primary-color, #0D6E6E) 28%, #E5E5E5);
  background: #FFFFFF;
}

.sort-item.disabled {
  opacity: 0.6;
}

.sort-item.dragging,
.category-order-item.dragging,
.product-order-item.dragging {
  opacity: 0.55;
  transform: scale(0.985);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.12);
}

.sort-item.insert-before::before,
.sort-item.insert-after::after,
.category-order-item.insert-before::before,
.category-order-item.insert-after::after,
.product-order-item.insert-before::before,
.product-order-item.insert-after::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  height: 3px;
  border-radius: 999px;
  background: var(--primary-color, #0D6E6E);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--primary-color, #0D6E6E) 12%, transparent);
  pointer-events: none;
  z-index: 2;
}

.sort-item.insert-before::before,
.category-order-item.insert-before::before,
.product-order-item.insert-before::before {
  top: -7px;
}

.sort-item.insert-after::after,
.category-order-item.insert-after::after,
.product-order-item.insert-after::after {
  bottom: -7px;
}

.drop-tail {
  height: 18px;
  border-radius: 8px;
  border: 1px dashed transparent;
  transition:
    height 160ms ease,
    border-color 160ms ease,
    background-color 160ms ease;
}

.drop-tail.compact {
  height: 14px;
}

.drop-tail.active {
  height: 26px;
  border-color: var(--primary-color, #0D6E6E);
  background:
    linear-gradient(
      to bottom,
      transparent 0,
      transparent calc(50% - 1px),
      var(--primary-color, #0D6E6E) calc(50% - 1px),
      var(--primary-color, #0D6E6E) calc(50% + 2px),
      transparent calc(50% + 2px),
      transparent 100%
    ),
    color-mix(in srgb, var(--primary-color, #0D6E6E) 6%, #FFFFFF);
}

.item-main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.item-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 10%, transparent);
  color: var(--primary-color, #0D6E6E);
  font-size: var(--font-size-xs);
  font-weight: 600;
  flex-shrink: 0;
}

.item-text,
.category-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.item-name {
  color: #1A1A1A;
  font-size: var(--font-size-sm);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  color: #888888;
  font-size: var(--font-size-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.order-workbench {
  display: grid;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  border: 1px solid #E5E5E5;
  border-radius: 10px;
  overflow: hidden;
  min-height: 360px;
}

.category-order-pane,
.product-order-pane {
  min-width: 0;
  background: #FFFFFF;
}

.category-order-pane {
  border-right: 1px solid #E5E5E5;
}

.product-order-pane {
  padding: 16px;
}

.pane-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 16px;
  color: #1A1A1A;
  font-size: var(--font-size-sm);
  font-weight: 600;
  border-bottom: 1px solid #E5E5E5;
}

.product-pane-title {
  height: auto;
  padding: 0 0 12px 0;
  margin-bottom: 12px;
}

.pane-meta {
  color: #888888;
  font-size: var(--font-size-xs);
  font-weight: 500;
}

.category-order-list,
.product-order-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.category-order-list {
  padding: 12px;
}

.category-order-item {
  display: grid;
  grid-template-columns: 18px 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-left: 3px solid transparent;
  border-radius: 8px;
  background: transparent;
  cursor: grab;
  text-align: left;
  transition:
    transform 180ms ease,
    border-color 180ms ease,
    box-shadow 180ms ease,
    background-color 180ms ease,
    opacity 180ms ease;
  position: relative;
  will-change: transform;
}

.category-order-item:hover {
  background: #FAFAFA;
  border-color: #E5E5E5;
}

.category-order-item.active {
  border-color: color-mix(in srgb, var(--primary-color, #0D6E6E) 30%, #E5E5E5);
  border-left-color: var(--primary-color, #0D6E6E);
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 8%, #FFFFFF);
}

.drag-handle {
  color: #B0B0B0;
  cursor: grab;
  font-size: var(--font-size-sm);
  line-height: 1;
  user-select: none;
}

.product-order-item {
  display: grid;
  grid-template-columns: 18px 28px minmax(0, 1fr) auto;
}

.home-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--primary-color, #0D6E6E);
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 10%, transparent);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.loading-state,
.empty-state {
  padding: 32px;
  text-align: center;
  color: #888888;
  background: #FAFAFA;
  border: 1px dashed #E5E5E5;
  border-radius: 8px;
  font-size: var(--font-size-sm);
}

.pane-empty {
  margin-top: 12px;
}

@media (max-width: 900px) {
  .config-section {
    padding: 16px;
  }

  .section-header {
    flex-direction: column;
    align-items: stretch;
  }

  .order-workbench {
    display: flex;
    flex-direction: column;
  }

  .category-order-pane {
    border-right: none;
    border-bottom: 1px solid #E5E5E5;
  }

  .category-order-list {
    max-height: 280px;
    overflow: auto;
  }

  .product-order-item {
    grid-template-columns: 18px 28px minmax(0, 1fr);
  }

  .product-order-item .home-badge {
    grid-column: 3;
    justify-self: start;
  }
}

@media (max-width: 640px) {
  .category-order-item {
    grid-template-columns: 18px 28px minmax(0, 1fr);
  }
}
</style>
