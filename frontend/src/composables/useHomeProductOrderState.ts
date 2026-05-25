import { computed, ref } from 'vue'
import { showToast } from 'vant'
import { batchUpdateProductSort } from '@/api/products'
import { batchUpdateCategorySort } from '@/api/categories'
import { getHomeProductOrder } from '@/api/home'
import { getOriginName } from '@/composables/useDict'
import { eventBus } from '@/utils/eventBus'
import type { HomeProductOrderGroup, HomeProductOrderItem } from '@/api/home'

type DragScope = 'category' | 'product'

interface DraftOrderGroup extends HomeProductOrderGroup {
  products: HomeProductOrderItem[]
}

const groups = ref<DraftOrderGroup[]>([])
const selectedGroupKey = ref('')
const loading = ref(false)
const saving = ref(false)
const hasUnsavedChanges = ref(false)
const dragState = ref<{ scope: DragScope; key: string; index: number } | null>(null)

const getGroupKey = (group: HomeProductOrderGroup) =>
  group.category ? `category-${group.category.id}` : group.virtualKey || 'uncategorized'

const getGroupId = (group: HomeProductOrderGroup) => group.category?.id

const compareProducts = (a: HomeProductOrderItem, b: HomeProductOrderItem) =>
  (a.sortOrder ?? 0) - (b.sortOrder ?? 0) ||
  a.name.localeCompare(b.name, 'zh-CN') ||
  a.id - b.id

const normalizeGroups = (items: HomeProductOrderGroup[]): DraftOrderGroup[] =>
  items.map(group => ({
    ...group,
    products: [...(group.products || [])].sort(compareProducts)
  }))

const reorderToInsertIndex = <T,>(items: T[], sourceIndex: number, targetIndex: number): T[] | null => {
  if (sourceIndex < 0 || targetIndex < 0) return null
  if (sourceIndex >= items.length || targetIndex > items.length) return null
  const insertIndex = sourceIndex < targetIndex ? targetIndex - 1 : targetIndex
  if (sourceIndex === insertIndex) return null

  const next = [...items]
  const [moved] = next.splice(sourceIndex, 1)
  next.splice(insertIndex, 0, moved)
  return next
}

const parseOriginIds = (originIds?: string) => {
  if (!originIds) return []
  try {
    const parsed = JSON.parse(originIds)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch {
    return []
  }
}

export function useHomeProductOrderState() {
  const selectedGroup = computed(() =>
    groups.value.find(group => getGroupKey(group) === selectedGroupKey.value) || groups.value[0]
  )

  const featuredCount = computed(() =>
    groups.value.reduce(
      (total, group) => total + group.products.filter(product => product.showOnHome).length,
      0
    )
  )

  const orderStatusText = computed(() => {
    if (saving.value) return '保存中'
    if (hasUnsavedChanges.value) return '已修改'
    return '已保存'
  })

  const markDirty = () => {
    hasUnsavedChanges.value = true
  }

  const loadOrder = async () => {
    loading.value = true
    try {
      const res = await getHomeProductOrder()
      groups.value = normalizeGroups(res.data || [])
      selectedGroupKey.value = groups.value[0] ? getGroupKey(groups.value[0]) : ''
      hasUnsavedChanges.value = false
    } catch (error) {
      console.error('Failed to load home product order:', error)
      showToast({ message: '加载首页排序失败', position: 'top', duration: 2000 })
    } finally {
      loading.value = false
    }
  }

  const selectGroup = (group: DraftOrderGroup) => {
    selectedGroupKey.value = getGroupKey(group)
  }

  const moveGroup = (index: number, direction: -1 | 1) => {
    const nextIndex = index + direction
    if (nextIndex < 0 || nextIndex >= groups.value.length) return
    const next = [...groups.value]
    const current = next[index]
    next[index] = next[nextIndex]
    next[nextIndex] = current
    groups.value = next
    markDirty()
  }

  const updateGroupProducts = (groupKey: string, products: HomeProductOrderItem[]) => {
    groups.value = groups.value.map(group =>
      getGroupKey(group) === groupKey ? { ...group, products } : group
    )
    markDirty()
  }

  const moveProduct = (index: number, direction: -1 | 1) => {
    const group = selectedGroup.value
    if (!group) return
    const nextIndex = index + direction
    if (nextIndex < 0 || nextIndex >= group.products.length) return

    const nextProducts = [...group.products]
    const current = nextProducts[index]
    nextProducts[index] = nextProducts[nextIndex]
    nextProducts[nextIndex] = current
    updateGroupProducts(getGroupKey(group), nextProducts)
  }

  const onDragStart = (scope: DragScope, key: string, index: number) => {
    dragState.value = { scope, key, index }
  }

  const onDragOver = (event: DragEvent) => {
    event.preventDefault()
  }

  const onDropGroupAt = (targetIndex: number) => {
    const source = dragState.value
    if (!source || source.scope !== 'category') return
    const next = reorderToInsertIndex(groups.value, source.index, targetIndex)
    if (next) {
      groups.value = next
      markDirty()
    }
    dragState.value = null
  }

  const onDropProductAt = (targetIndex: number) => {
    const source = dragState.value
    const group = selectedGroup.value
    if (!source || source.scope !== 'product' || !group || source.key !== getGroupKey(group)) return
    const nextProducts = reorderToInsertIndex(group.products, source.index, targetIndex)
    if (nextProducts) updateGroupProducts(getGroupKey(group), nextProducts)
    dragState.value = null
  }

  const getProductMeta = (product: HomeProductOrderItem) => {
    const originNames = parseOriginIds(product.originIds)
      .map(key => getOriginName(key))
      .filter(Boolean)
      .join(' / ')
    const specs = product.specs || product.code || '未填写规格'
    return originNames ? `${specs} · 产地 ${originNames}` : specs
  }

  const saveOrder = async () => {
    if (!hasUnsavedChanges.value || saving.value) return

    saving.value = true
    try {
      const categoryItems = groups.value
        .filter(group => getGroupId(group) != null)
        .map((group, index) => ({
          id: getGroupId(group)!,
          sortOrder: index + 1
        }))

      await batchUpdateCategorySort(categoryItems)

      const productItems = groups.value.flatMap(group =>
        group.products.map((product, index) => ({
          id: product.id,
          sortOrder: index + 1
        }))
      )

      if (productItems.length > 0) {
        await batchUpdateProductSort(productItems)
      }

      await loadOrder()
      eventBus.emit('category-sort-updated')
      eventBus.emit('product-sort-updated')
    } catch (error) {
      console.error('Failed to save home product order:', error)
      showToast({ message: '保存首页排序失败，草稿已保留', position: 'top', duration: 2200 })
      throw error
    } finally {
      saving.value = false
    }
  }

  return {
    groups,
    selectedGroupKey,
    selectedGroup,
    featuredCount,
    loading,
    saving,
    hasUnsavedChanges,
    orderStatusText,
    getGroupKey,
    loadOrder,
    selectGroup,
    moveGroup,
    moveProduct,
    onDragStart,
    onDragOver,
    onDropGroupAt,
    onDropProductAt,
    getProductMeta,
    saveOrder
  }
}
