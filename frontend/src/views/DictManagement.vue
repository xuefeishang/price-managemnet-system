<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { showToast, showDialog } from 'vant'
import { getDicts, createDict, updateDict, deleteDict, getDictCategories } from '@/api/dict'
import { getStatusLabel, getDictOptions, CATEGORY_LABELS, loadAllDicts, refreshDictCache } from '@/composables/useDict'
import { useLayout } from '@/composables/useLayout'
import DictCategoryHelpPanel from '@/components/dict/DictCategoryHelpPanel.vue'
import DictCategoryPreview from '@/components/dict/DictCategoryPreview.vue'
import {
  formatJsonDisplay,
  getCategoryMeta,
  getDomainLabel,
  getExtraValueMode,
  getUnregisteredCategories,
  getVisibleCategoryMetas,
  isColorValue,
  isJsonValue,
  isProtectedCategory,
  type DictCategoryDomain,
  type DictCategoryMeta
} from '@/constants/dictCategoryMeta'
import type { SysDict } from '@/types'

type DomainFilter = DictCategoryDomain
type CategoryNavItem = {
  category: string
  label: string
  meta?: DictCategoryMeta
}

const dicts = ref<SysDict[]>([])
const allDicts = ref<SysDict[]>([])
const categories = ref<string[]>([])
const loading = ref(false)
const selectedCategory = ref<string>('')
const selectedDomain = ref<DomainFilter>('business_dict')
const togglingId = ref<number | null>(null)
const showSystemConfig = ref(false)
const previewExpanded = ref(false)
const categoryNavExpanded = ref(false)
const { isPCLayout } = useLayout()

const showEditDialog = ref(false)
const editForm = ref<{
  id: number | null
  category: string
  dictKey: string
  dictValue: string
  extraValue: string
  sortOrder: number
  status: string
  remark: string
}>({
  id: null,
  category: '',
  dictKey: '',
  dictValue: '',
  extraValue: '',
  sortOrder: 0,
  status: 'ACTIVE',
  remark: ''
})
const isEditing = ref(false)
const newCategory = ref('')
const validationErrors = ref<Set<string>>(new Set())

const getCategoryLabel = (category: string): string => {
  return getCategoryMeta(category)?.label || CATEGORY_LABELS[category] || category
}

const visibleMetaCategories = computed(() => getVisibleCategoryMetas(categories.value, showSystemConfig.value))

const unregisteredCategories = computed(() => {
  const items = getUnregisteredCategories(categories.value)
  return showSystemConfig.value ? items : items.filter(category => !isProtectedCategory(category))
})

const domainTabs = computed(() => {
  const domains = new Set<DomainFilter>()
  visibleMetaCategories.value.forEach(meta => domains.add(meta.domain))
  if (unregisteredCategories.value.length > 0) domains.add('business_dict')
  const order: DomainFilter[] = ['business_dict', 'system_dict', 'ui_config', 'visual_mapping']
  return order
    .filter(domain => domains.has(domain))
    .map(domain => ({
      key: domain,
      label: getDomainLabel(domain),
      count: visibleMetaCategories.value.filter(meta => meta.domain === domain).length +
        (domain === 'business_dict' ? unregisteredCategories.value.length : 0)
    }))
})

const categoryTabs = computed(() => {
  const items: CategoryNavItem[] = visibleMetaCategories.value
    .filter(meta => meta.domain === selectedDomain.value)
    .map(meta => ({ category: meta.category, label: meta.label, meta }))

  if (selectedDomain.value === 'business_dict') {
    items.push(...unregisteredCategories.value.map(category => ({ category, label: getCategoryLabel(category), meta: undefined as DictCategoryMeta | undefined })))
  }

  return items
})

const domainCategoryGroups = computed(() => {
  return domainTabs.value.map(domain => {
    const items: CategoryNavItem[] = visibleMetaCategories.value
      .filter(meta => meta.domain === domain.key)
      .map(meta => ({ category: meta.category, label: meta.label, meta }))

    if (domain.key === 'business_dict') {
      items.push(...unregisteredCategories.value.map(category => ({ category, label: getCategoryLabel(category), meta: undefined as DictCategoryMeta | undefined })))
    }

    return { ...domain, items }
  }).filter(group => group.items.length > 0)
})

const visibleCategories = computed(() => categoryTabs.value.map(item => item.category))

const editableCategories = computed(() => {
  return categories.value.filter(category => {
    const meta = getCategoryMeta(category)
    return !isProtectedCategory(category) && (meta?.editableInDictPage ?? true)
  })
})

const categoryItemCounts = computed(() => {
  return allDicts.value.reduce<Record<string, number>>((acc, item) => {
    acc[item.category] = (acc[item.category] || 0) + 1
    return acc
  }, {})
})

const currentCategoryMeta = computed(() => getCategoryMeta(selectedCategory.value))
const editingCategory = computed(() => editForm.value.category === '__new__' ? newCategory.value : editForm.value.category)
const editingCategoryMeta = computed(() => getCategoryMeta(editingCategory.value))
const currentCategoryItems = computed(() => dicts.value.filter(item => item.category === selectedCategory.value))
const currentItemCount = computed(() => categoryItemCounts.value[selectedCategory.value] || currentCategoryItems.value.length)
const isCurrentProtected = computed(() => isProtectedCategory(selectedCategory.value))
const statusFilterOptions = computed(() => getDictOptions('common_status'))
const currentExtraLabel = computed(() => {
  const mode = getExtraValueMode(editingCategory.value)
  const map: Record<string, string> = {
    text: '扩展值',
    color: '颜色值',
    icon: '图标标识',
    json: 'JSON 配置',
    readonly: '只读配置'
  }
  return map[mode] || '扩展值'
})
const currentExtraPlaceholder = computed(() => editingCategoryMeta.value?.extraValueRule || '可选，按分类规则填写')
const isEditKeyDisabled = computed(() => isEditing.value && editingCategoryMeta.value?.keyMutable === false)

const selectDomain = async (domain: DomainFilter) => {
  selectedDomain.value = domain
  selectedCategory.value = categoryTabs.value[0]?.category || ''
  categoryNavExpanded.value = false
  previewExpanded.value = false
  await loadDicts()
}

const selectCategory = async (category: string) => {
  selectedCategory.value = category
  const ownerDomain = getCategoryMeta(category)?.domain || (unregisteredCategories.value.includes(category) ? 'business_dict' : selectedDomain.value)
  selectedDomain.value = ownerDomain
  previewExpanded.value = false
  await loadDicts()
}

const ensureSelection = async () => {
  if (selectedCategory.value && visibleCategories.value.includes(selectedCategory.value)) return
  if (!domainTabs.value.some(tab => tab.key === selectedDomain.value)) {
    selectedDomain.value = domainTabs.value[0]?.key || 'business_dict'
  }
  selectedCategory.value = categoryTabs.value[0]?.category || ''
  await loadDicts()
}

const loadAllManagementDicts = async () => {
  const response = await getDicts()
  allDicts.value = response.data || []
}

const loadDicts = async () => {
  if (!selectedCategory.value) {
    dicts.value = []
    return
  }
  loading.value = true
  try {
    const response = await getDicts(selectedCategory.value)
    dicts.value = response.data || []
  } catch (error) {
    console.error('Failed to load dicts:', error)
    showToast('加载字典失败')
  } finally {
    loading.value = false
  }
}

const loadCategories = async () => {
  try {
    const response = await getDictCategories(true)
    categories.value = response.data || []
  } catch (error) {
    console.error('Failed to load categories:', error)
  }
}

const refreshPageData = async () => {
  await Promise.all([loadCategories(), loadAllManagementDicts()])
  await ensureSelection()
}

const handleToggleStatus = async (dict: SysDict) => {
  if (isProtectedCategory(dict.category)) {
    showToast('此分类由专用页面管理，仅支持查看')
    return
  }

  if (togglingId.value === dict.id) return
  const newStatus = dict.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const actionText = getStatusLabel(newStatus)

  try {
    togglingId.value = dict.id
    await updateDict(dict.id, { status: newStatus } as Partial<SysDict>)
    dict.status = newStatus as any
    const cached = allDicts.value.find(item => item.id === dict.id)
    if (cached) cached.status = newStatus as any
    showToast(`字典项已${actionText}`)
  } catch (error) {
    console.error('Failed to toggle status:', error)
    showToast('操作失败')
  } finally {
    togglingId.value = null
  }
}

const handleCreate = (category?: string) => {
  const targetCategory = category || selectedCategory.value
  if (targetCategory && isProtectedCategory(targetCategory)) {
    showToast('此分类由专用页面管理，仅支持查看')
    return
  }

  isEditing.value = false
  editForm.value = {
    id: null,
    category: targetCategory || '',
    dictKey: '',
    dictValue: '',
    extraValue: '',
    sortOrder: 0,
    status: 'ACTIVE',
    remark: ''
  }
  newCategory.value = ''
  validationErrors.value.clear()
  showEditDialog.value = true
}

const handleEdit = (dict: SysDict) => {
  if (isProtectedCategory(dict.category)) {
    showToast('此分类由专用页面管理，仅支持查看')
    return
  }

  isEditing.value = true
  editForm.value = {
    id: dict.id,
    category: dict.category,
    dictKey: dict.dictKey,
    dictValue: dict.dictValue,
    extraValue: dict.extraValue || '',
    sortOrder: dict.sortOrder,
    status: dict.status,
    remark: dict.remark || ''
  }
  newCategory.value = ''
  validationErrors.value.clear()
  showEditDialog.value = true
}

const validateExtraValue = (category: string, value: string, errors: string[]) => {
  if (!value.trim()) return
  const mode = getExtraValueMode(category)
  if (mode === 'color' && !isColorValue(value.trim())) {
    errors.push('颜色值需为 #RGB、#RRGGBB 或 #RRGGBBAA')
    validationErrors.value.add('extraValue')
  }
  if (mode === 'json' && !isJsonValue(value.trim())) {
    errors.push('扩展值需为合法 JSON')
    validationErrors.value.add('extraValue')
  }
}

const handleSave = async () => {
  const form = editForm.value
  const category = form.category === '__new__' ? newCategory.value.trim() : form.category
  validationErrors.value.clear()

  const errors: string[] = []
  if (!category) {
    errors.push('请选择或输入分类')
    validationErrors.value.add('category')
  }
  if (form.category === '__new__' && !newCategory.value.trim()) {
    errors.push('请输入新分类名称')
    validationErrors.value.add('newCategory')
  }
  if (!form.dictKey.trim()) {
    errors.push('请输入字典键')
    validationErrors.value.add('dictKey')
  }
  if (!form.dictValue.trim()) {
    errors.push('请输入显示值')
    validationErrors.value.add('dictValue')
  }
  if (category) validateExtraValue(category, form.extraValue, errors)

  if (errors.length > 0) {
    showToast(errors.join('、'))
    return
  }

  try {
    const payload = {
      category,
      dictKey: form.dictKey.trim(),
      dictValue: form.dictValue.trim(),
      extraValue: form.extraValue.trim(),
      sortOrder: form.sortOrder,
      status: form.status as any,
      remark: form.remark.trim()
    }

    if (isEditing.value && form.id) {
      await updateDict(form.id, payload as Partial<SysDict>)
      showToast('更新成功')
    } else {
      await createDict(payload as Omit<SysDict, 'id' | 'createdTime' | 'updatedTime'>)
      showToast('创建成功')
      if (!categories.value.includes(category)) {
        selectedDomain.value = getCategoryMeta(category)?.domain || 'business_dict'
        selectedCategory.value = category
      }
    }

    showEditDialog.value = false
    await refreshPageData()
    refreshDictCache()
  } catch (error: any) {
    console.error('Failed to save dict:', error)
    showToast(error?.response?.data?.message || '保存失败')
  }
}

const handleDelete = async (dict: SysDict) => {
  if (isProtectedCategory(dict.category)) {
    showToast('此分类由专用页面管理，仅支持查看')
    return
  }

  showDialog({
    title: '确认删除',
    message: `确定要删除字典项"${dict.dictValue}"(${dict.category}.${dict.dictKey})吗？删除后不可恢复。`,
    confirmButtonText: '删除',
    confirmButtonColor: '#EF4444',
    showCancelButton: true
  }).then(async () => {
    try {
      await deleteDict(dict.id)
      showToast('删除成功')
      await refreshPageData()
      refreshDictCache()
    } catch (error) {
      console.error('Failed to delete dict:', error)
      showToast('删除失败')
    }
  }).catch(() => {})
}

const goToOwnerPage = () => {
  const owner = currentCategoryMeta.value?.ownerPage
  if (owner === 'style-settings') window.location.href = '/style-settings'
  if (owner === 'category-visual-settings') window.location.href = '/style-settings?section=category'
}

const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制到剪贴板')
  } catch {
    showToast('复制失败')
  }
}

const getExtraValueRender = (dict: SysDict) => {
  const mode = getExtraValueMode(dict.category)
  const value = dict.extraValue || ''

  if (!value) return { type: 'empty', display: '', raw: '' }
  if (mode === 'color') {
    return isColorValue(value)
      ? { type: 'color', display: value, color: value, raw: value }
      : { type: 'text', display: value, raw: value }
  }
  if (mode === 'icon') return { type: 'icon', display: value, raw: value }
  if (mode === 'json') {
    return isJsonValue(value)
      ? { type: 'json', display: formatJsonDisplay(value), raw: value }
      : { type: 'text', display: value, raw: value }
  }
  if (mode === 'readonly') {
    if (isJsonValue(value)) return { type: 'json', display: formatJsonDisplay(value), raw: value, readonly: true }
    if (isColorValue(value)) return { type: 'color', display: value, color: value, raw: value, readonly: true }
    return { type: 'readonly', display: value, raw: value }
  }
  return { type: 'text', display: value, raw: value }
}

watch(showSystemConfig, () => {
  ensureSelection()
})

onMounted(async () => {
  await loadAllDicts()
  await refreshPageData()
})
</script>

<template>
  <div class="dict-page">
    <template v-if="isPCLayout">
      <div class="pc-dict">
        <div class="page-header-pc">
          <div>
            <h1 class="page-title-pc">数据字典</h1>
            <p class="page-subtitle-pc">按业务领域和分类维护编码值，查看每个分类的使用说明与界面效果。</p>
          </div>
          <div class="header-actions">
            <label class="system-config-toggle">
              <input type="checkbox" v-model="showSystemConfig" />
              <span>显示系统配置</span>
            </label>
            <button class="btn-primary-pc" :disabled="!selectedCategory || isCurrentProtected" @click="handleCreate()">
              <span>+</span>
              新建字典
            </button>
          </div>
        </div>

        <section v-if="domainCategoryGroups.length" class="category-top-nav">
          <div class="top-domain-tabs">
            <button
              v-for="group in domainCategoryGroups"
              :key="group.key"
              class="top-domain-tab"
              :class="{ active: selectedDomain === group.key }"
              @click="selectDomain(group.key)"
            >
              <span>{{ group.label }}</span>
              <em>{{ group.count }}</em>
            </button>
          </div>
          <div class="top-category-row" :class="{ expanded: categoryNavExpanded }">
            <button
              v-for="item in categoryTabs"
              :key="item.category"
              class="top-category-chip"
              :class="{ active: selectedCategory === item.category, protected: isProtectedCategory(item.category) }"
              @click="selectCategory(item.category)"
            >
              <span>{{ item.label }}</span>
              <em>{{ categoryItemCounts[item.category] || 0 }}</em>
            </button>
          </div>
          <button
            v-if="categoryTabs.length > 8"
            class="category-expand-btn"
            @click="categoryNavExpanded = !categoryNavExpanded"
          >
            {{ categoryNavExpanded ? '收起分类' : '展开分类' }}
          </button>
        </section>

        <div v-if="showSystemConfig && isCurrentProtected" class="protected-notice">
          <span>当前分类由 {{ currentCategoryMeta?.ownerPage === 'style-settings' ? '样式设置' : '专用配置页面' }} 管理，仅可查看。</span>
          <button class="link-btn" @click="goToOwnerPage">前往维护入口</button>
        </div>

        <div v-if="!selectedCategory" class="empty-state-pc">
          <p class="empty-text">暂无可展示分类</p>
        </div>

        <div v-else class="dict-workbench">
          <section class="dict-main">
            <div class="category-header">
              <div class="category-info">
                <h2 class="category-name">{{ getCategoryLabel(selectedCategory) }}</h2>
                <span class="category-code">{{ selectedCategory }}</span>
                <span class="category-count">{{ currentItemCount }} 项</span>
                <span v-if="isCurrentProtected" class="protected-badge">只读</span>
              </div>
              <button v-if="!isCurrentProtected" class="btn-add-pc" @click="handleCreate(selectedCategory)">新增</button>
            </div>

            <div v-if="!loading" class="dict-table">
              <div class="table-header">
                <div class="table-cell sort-col">排序</div>
                <div class="table-cell key-col">字典键</div>
                <div class="table-cell value-col">显示值</div>
                <div class="table-cell extra-col">扩展值</div>
                <div class="table-cell status-col">状态</div>
                <div class="table-cell remark-col">备注</div>
                <div class="table-cell actions-col">操作</div>
              </div>

              <div
                v-for="dict in currentCategoryItems"
                :key="dict.id"
                class="table-row"
                :class="{ inactive: dict.status === 'INACTIVE', protected: isProtectedCategory(dict.category) }"
              >
                <div class="table-cell sort-col">{{ dict.sortOrder }}</div>
                <div class="table-cell key-col"><code class="dict-key-code">{{ dict.dictKey }}</code></div>
                <div class="table-cell value-col"><span class="dict-value-text">{{ dict.dictValue }}</span></div>
                <div class="table-cell extra-col">
                  <template v-if="dict.extraValue">
                    <div v-if="getExtraValueRender(dict).type === 'color'" class="extra-color">
                      <span class="color-swatch" :style="{ backgroundColor: getExtraValueRender(dict).color }"></span>
                      <code class="color-value">{{ getExtraValueRender(dict).display }}</code>
                    </div>
                    <div v-else-if="getExtraValueRender(dict).type === 'icon'" class="extra-icon">
                      <span class="icon-preview">{{ getExtraValueRender(dict).display }}</span>
                    </div>
                    <div v-else-if="getExtraValueRender(dict).type === 'json'" class="extra-json">
                      <pre class="json-preview">{{ getExtraValueRender(dict).display }}</pre>
                      <button class="copy-btn" @click="copyToClipboard(getExtraValueRender(dict).raw)" title="复制JSON">复制</button>
                    </div>
                    <span v-else-if="getExtraValueRender(dict).type === 'readonly'" class="readonly-text">{{ getExtraValueRender(dict).display }}</span>
                    <span v-else class="dict-extra-badge">{{ getExtraValueRender(dict).display }}</span>
                  </template>
                  <span v-else class="dict-extra-empty">-</span>
                </div>
                <div class="table-cell status-col">
                  <div
                    v-if="!isProtectedCategory(dict.category)"
                    class="toggle-switch"
                    :class="{ active: dict.status === 'ACTIVE', loading: togglingId === dict.id }"
                    @click="handleToggleStatus(dict)"
                  >
                    <div class="toggle-slider"></div>
                  </div>
                  <span class="status-text" :class="dict.status">{{ getStatusLabel(dict.status) }}</span>
                </div>
                <div class="table-cell remark-col">{{ dict.remark || '-' }}</div>
                <div class="table-cell actions-col" @click.stop>
                  <template v-if="!isProtectedCategory(dict.category)">
                    <button class="action-btn" @click="handleEdit(dict)" title="编辑">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                      </svg>
                    </button>
                    <button class="action-btn danger" @click="handleDelete(dict)" title="删除">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3 6 5 6 21 6"/>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                      </svg>
                    </button>
                  </template>
                  <span v-else class="readonly-hint">只读</span>
                </div>
              </div>

              <div v-if="currentCategoryItems.length === 0" class="empty-row">
                <p>当前分类暂无字典项</p>
                <button v-if="!isCurrentProtected" class="empty-btn" @click="handleCreate(selectedCategory)">创建字典项</button>
              </div>
            </div>

            <div v-else class="loading-state-pc">
              <div class="loading-spinner"></div>
            </div>
          </section>

          <aside class="dict-sidebar">
            <DictCategoryHelpPanel
              :category="selectedCategory"
              :meta="currentCategoryMeta"
              :item-count="currentItemCount"
            />
            <DictCategoryPreview
              :category="selectedCategory"
              :meta="currentCategoryMeta"
              :items="currentCategoryItems"
            />
          </aside>
        </div>
      </div>
    </template>

    <template v-else>
      <header class="navbar">
        <div class="navbar-left">
          <h1 class="navbar-title">数据字典</h1>
        </div>
        <button class="add-btn" :disabled="!selectedCategory || isCurrentProtected" @click="handleCreate()">+</button>
      </header>

      <main class="content">
        <label class="system-config-toggle mobile">
          <input type="checkbox" v-model="showSystemConfig" />
          <span>显示系统配置</span>
        </label>

        <label class="mobile-category-picker">
          <span>当前分类</span>
          <select v-model="selectedCategory" @change="selectCategory(selectedCategory)">
            <optgroup v-for="group in domainCategoryGroups" :key="group.key" :label="`${group.label} (${group.count})`">
              <option v-for="item in group.items" :key="item.category" :value="item.category">
                {{ item.label }} · {{ item.category }} · {{ categoryItemCounts[item.category] || 0 }}项
              </option>
            </optgroup>
          </select>
        </label>

        <DictCategoryHelpPanel
          v-if="selectedCategory"
          :category="selectedCategory"
          :meta="currentCategoryMeta"
          :item-count="currentItemCount"
          compact
        />

        <button v-if="selectedCategory" class="preview-toggle" @click="previewExpanded = !previewExpanded">
          {{ previewExpanded ? '收起效果展示' : '展开效果展示' }}
        </button>

        <DictCategoryPreview
          v-if="selectedCategory && previewExpanded"
          :category="selectedCategory"
          :meta="currentCategoryMeta"
          :items="currentCategoryItems"
          compact
        />

        <div class="dict-list" v-if="!loading">
          <div
            v-for="dict in currentCategoryItems"
            :key="dict.id"
            class="dict-card"
            :class="{ inactive: dict.status === 'INACTIVE', protected: isProtectedCategory(dict.category) }"
          >
            <div class="card-main">
              <div class="card-info">
                <div class="card-value">{{ dict.dictValue }}</div>
                <div class="card-meta">
                  <code class="card-key">{{ dict.dictKey }}</code>
                  <span class="separator">·</span>
                  <span class="card-category">{{ dict.category }}</span>
                  <span v-if="isProtectedCategory(dict.category)" class="card-protected-badge">只读</span>
                </div>
              </div>
            </div>

            <div class="card-detail" v-if="dict.extraValue">
              <span class="detail-label">扩展值</span>
              <template v-if="getExtraValueRender(dict).type === 'color'">
                <span class="color-swatch small" :style="{ backgroundColor: getExtraValueRender(dict).color }"></span>
                <code class="detail-extra">{{ getExtraValueRender(dict).display }}</code>
              </template>
              <template v-else-if="getExtraValueRender(dict).type === 'json'">
                <code class="detail-extra json">JSON</code>
                <button class="copy-btn small" @click="copyToClipboard(getExtraValueRender(dict).raw)" title="复制">复制</button>
              </template>
              <template v-else>
                <span class="detail-extra">{{ getExtraValueRender(dict).display }}</span>
              </template>
            </div>

            <div class="card-control">
              <template v-if="!isProtectedCategory(dict.category)">
                <div
                  class="toggle-switch small"
                  :class="{ active: dict.status === 'ACTIVE', loading: togglingId === dict.id }"
                  @click="handleToggleStatus(dict)"
                >
                  <div class="toggle-slider"></div>
                </div>
                <div class="card-actions">
                  <button class="card-btn edit" @click="handleEdit(dict)">编辑</button>
                  <button class="card-btn delete" @click="handleDelete(dict)">删</button>
                </div>
              </template>
              <template v-else>
                <span class="status-text" :class="dict.status">{{ getStatusLabel(dict.status) }}</span>
                <span class="readonly-hint mobile">只读</span>
              </template>
            </div>
          </div>

          <div v-if="currentCategoryItems.length === 0" class="empty-state">
            <p class="empty-text">当前分类暂无字典项</p>
          </div>
        </div>

        <div v-else class="loading-state">
          <div class="loading-spinner"></div>
        </div>
      </main>
    </template>

    <div v-if="showEditDialog" class="dialog-overlay" @click.self="showEditDialog = false">
      <div class="dialog-content">
        <div class="dialog-header">
          <h3>{{ isEditing ? '编辑字典项' : '新建字典项' }}</h3>
          <button class="dialog-close" @click="showEditDialog = false">×</button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label"><span class="required">*</span>分类</label>
            <select v-if="!isEditing" v-model="editForm.category" class="form-select" :class="{ 'is-error': validationErrors.has('category') }">
              <option value="">请选择分类</option>
              <option v-for="cat in editableCategories" :key="cat" :value="cat">
                {{ getCategoryLabel(cat) }} ({{ cat }})
              </option>
              <option value="__new__">+ 新建分类</option>
            </select>
            <input v-else :value="editForm.category" class="form-input" disabled />
            <span v-if="validationErrors.has('category')" class="error-hint">请选择分类</span>
          </div>
          <div v-if="editForm.category === '__new__'" class="form-group">
            <label class="form-label"><span class="required">*</span>新分类名称</label>
            <input v-model="newCategory" class="form-input" :class="{ 'is-error': validationErrors.has('newCategory') }" placeholder="输入新分类标识，如 payment_type" />
            <span v-if="validationErrors.has('newCategory')" class="error-hint">请输入新分类名称</span>
          </div>
          <div class="form-group">
            <label class="form-label"><span class="required">*</span>字典键 (Key)</label>
            <input
              v-model="editForm.dictKey"
              class="form-input"
              :class="{ 'is-error': validationErrors.has('dictKey') }"
              :disabled="isEditKeyDisabled"
              placeholder="如 CNY、ACTIVE"
            />
            <span v-if="isEditKeyDisabled" class="field-hint">该分类 Key 已被业务引用，编辑时锁定。</span>
            <span v-if="validationErrors.has('dictKey')" class="error-hint">请输入字典键</span>
          </div>
          <div class="form-group">
            <label class="form-label"><span class="required">*</span>显示值</label>
            <input v-model="editForm.dictValue" class="form-input" :class="{ 'is-error': validationErrors.has('dictValue') }" placeholder="填写用户可见名称" />
            <span v-if="validationErrors.has('dictValue')" class="error-hint">请输入显示值</span>
          </div>
          <div class="form-group">
            <label class="form-label">{{ currentExtraLabel }}</label>
            <textarea
              v-if="getExtraValueMode(editingCategory) === 'json'"
              v-model="editForm.extraValue"
              class="form-textarea"
              :class="{ 'is-error': validationErrors.has('extraValue') }"
              rows="4"
              :placeholder="currentExtraPlaceholder"
            ></textarea>
            <input
              v-else
              v-model="editForm.extraValue"
              class="form-input"
              :class="{ 'is-error': validationErrors.has('extraValue') }"
              :placeholder="currentExtraPlaceholder"
            />
          </div>
          <div class="form-row">
            <div class="form-group half">
              <label class="form-label">排序</label>
              <input v-model.number="editForm.sortOrder" type="number" class="form-input" />
            </div>
            <div class="form-group half">
              <label class="form-label">状态</label>
              <select v-model="editForm.status" class="form-select">
                <option v-for="opt in statusFilterOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label class="form-label">备注</label>
            <textarea v-model="editForm.remark" class="form-textarea" rows="2" placeholder="备注说明（可选）"></textarea>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showEditDialog = false">取消</button>
          <button class="btn-save" @click="handleSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dict-page { background: var(--bg-page, #fafafa); }

.pc-dict {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header-pc {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.page-title-pc {
  margin: 0;
  color: var(--text-primary, #1a1a1a);
  font-family: var(--font-heading);
  font-size: var(--font-size-2xl);
  font-weight: 500;
}

.page-subtitle-pc {
  margin: 6px 0 0;
  color: #64748b;
  font-size: var(--font-size-sm);
}

.header-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.btn-primary-pc,
.btn-add-pc,
.empty-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: 8px;
  background: var(--primary-color, #0d6e6e);
  color: #fff;
  cursor: pointer;
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.btn-primary-pc { padding: 9px 16px; }
.btn-add-pc { padding: 7px 12px; }
.btn-primary-pc:disabled,
.add-btn:disabled {
  opacity: .45;
  cursor: not-allowed;
}

.domain-tabs,
.category-tabs-pc {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.domain-tab,
.category-tab-pc,
.tab-btn {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  transition: border-color .15s, background .15s, color .15s;
}

.domain-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 38px;
  padding: 0 14px;
  font-size: var(--font-size-sm);
}

.domain-tab span {
  color: #94a3b8;
  font-size: var(--font-size-xs);
}

.domain-tab.active,
.category-tab-pc.active,
.tab-btn.active {
  border-color: #0d6e6e;
  background: #ecfdf5;
  color: #0f766e;
}

.category-top-nav {
  display: grid;
  gap: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  padding: 12px;
}

.top-domain-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.top-domain-tab,
.top-category-chip,
.category-expand-btn {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  transition: border-color .15s, background .15s, color .15s;
}

.top-domain-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 12px;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.top-domain-tab em,
.top-category-chip em {
  min-width: 22px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
  text-align: center;
}

.top-domain-tab.active {
  border-color: #0d6e6e;
  background: #ecfdf5;
  color: #0f766e;
}

.top-category-row {
  display: flex;
  max-height: 42px;
  flex-wrap: wrap;
  gap: 8px;
  overflow: hidden;
}

.top-category-row.expanded {
  max-height: none;
}

.top-category-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 34px;
  padding: 0 10px;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.top-category-chip.active {
  border-color: #0d6e6e;
  background: #0d6e6e;
  color: #fff;
}

.top-category-chip.active em {
  background: rgba(255, 255, 255, .18);
  color: #fff;
}

.top-category-chip.protected {
  border-color: #fde68a;
  background: #fffbeb;
}

.top-category-chip.active.protected {
  border-color: #d97706;
  background: #d97706;
  color: #fff;
}

.category-expand-btn {
  justify-self: start;
  min-height: 30px;
  padding: 0 10px;
  color: #0f766e;
  font-size: var(--font-size-xs);
  font-weight: 650;
}

.category-tab-pc {
  display: grid;
  grid-template-columns: auto auto auto;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 12px;
  white-space: nowrap;
}

.category-tab-pc code {
  color: #94a3b8;
  font-family: var(--font-mono);
  font-size: 11px;
}

.category-tab-pc em {
  min-width: 20px;
  padding: 2px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
  text-align: center;
}

.category-tab-pc.protected {
  border-color: #fde68a;
  background: #fffbeb;
}

.dict-workbench {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 340px);
  gap: 16px;
  align-items: start;
}

.category-nav-panel {
  position: sticky;
  top: 16px;
  max-height: calc(100vh - 132px);
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.category-nav-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #eef2f7;
  padding: 12px 14px;
  color: #334155;
  font-size: var(--font-size-sm);
  font-weight: 650;
}

.category-nav-head strong {
  min-width: 24px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: var(--font-size-xs);
  line-height: 22px;
  text-align: center;
}

.category-nav-groups {
  display: grid;
  gap: 10px;
  max-height: calc(100vh - 182px);
  overflow-y: auto;
  padding: 10px;
}

.category-nav-domain,
.category-nav-item {
  width: 100%;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.category-nav-domain {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 7px 8px;
  color: #475569;
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.domain-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.collapse-icon {
  width: 0;
  height: 0;
  border-top: 4px solid transparent;
  border-bottom: 4px solid transparent;
  border-left: 5px solid currentColor;
  transform: rotate(90deg);
  transition: transform .15s;
}

.category-nav-domain.collapsed .collapse-icon {
  transform: rotate(0deg);
}

.category-nav-domain em {
  color: #94a3b8;
  font-style: normal;
  font-weight: 600;
}

.category-nav-domain.active {
  color: #0f766e;
}

.category-nav-items {
  display: grid;
  gap: 4px;
}

.category-nav-item {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  margin-top: 4px;
  padding: 9px 8px 9px 12px;
  border-radius: 6px;
  color: #334155;
  transition: background .15s, color .15s;
}

.category-nav-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 9px;
  bottom: 9px;
  width: 3px;
  border-radius: 999px;
  background: transparent;
}

.category-nav-item:hover {
  background: #f8fafc;
}

.category-nav-item.active {
  background: #ecfdf5;
  color: #0f766e;
}

.category-nav-item.active::before {
  background: #0d6e6e;
}

.category-nav-item.protected {
  background: #fffbeb;
}

.category-nav-item.active.protected {
  border: 1px solid #fde68a;
}

.nav-item-label {
  overflow: hidden;
  font-size: var(--font-size-xs);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-nav-item em {
  grid-column: 2 / 3;
  min-width: 24px;
  padding: 2px 6px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  font-style: normal;
  text-align: center;
}

.dict-sidebar {
  display: grid;
  gap: 12px;
  position: sticky;
  top: 16px;
}

.dict-main {
  min-width: 0;
}

.category-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}

.category-info {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.category-name {
  margin: 0;
  color: #111827;
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.category-code,
.dict-key-code {
  padding: 2px 6px;
  border-radius: 4px;
  background: #f3f4f6;
  color: #0d6e6e;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.category-count,
.protected-badge {
  color: #64748b;
  font-size: var(--font-size-xs);
}

.protected-badge {
  padding: 2px 8px;
  border-radius: 4px;
  background: #fee2e2;
  color: #dc2626;
}

.dict-table {
  overflow-x: auto;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.table-header,
.table-row {
  display: flex;
  align-items: center;
  min-width: 820px;
  padding: 0 18px;
}

.table-header {
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}

.table-row {
  border-bottom: 1px solid #f1f5f9;
}

.table-row:last-child { border-bottom: 0; }
.table-row:hover { background: #f8fafc; }
.table-row.inactive .dict-value-text { color: #9ca3af; }
.table-row.protected { background: #fffbeb; }

.table-cell {
  overflow: hidden;
  padding: 14px 8px;
  color: #1f2937;
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-header .table-cell {
  color: #475569;
  font-weight: 650;
}

.sort-col { width: 8%; text-align: center; }
.key-col { width: 16%; }
.value-col { width: 16%; }
.extra-col { width: 23%; }
.status-col {
  display: flex;
  width: 15%;
  align-items: center;
  gap: 8px;
}
.remark-col { width: 14%; }
.actions-col {
  display: flex;
  width: 8%;
  justify-content: flex-end;
  gap: 6px;
}

.dict-value-text { font-weight: 600; }
.dict-extra-badge,
.icon-preview {
  padding: 2px 8px;
  border-radius: 4px;
  background: #fff7ed;
  color: #c2410c;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}
.dict-extra-empty { color: #d1d5db; }

.extra-color,
.extra-icon,
.extra-json {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.color-swatch {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  border: 1px solid rgba(0, 0, 0, .12);
  border-radius: 4px;
}

.color-swatch.small {
  width: 14px;
  height: 14px;
}

.color-value,
.readonly-text {
  color: #6b7280;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.json-preview {
  max-height: 60px;
  margin: 0;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  background: #f8fafc;
  color: #475569;
  font-family: var(--font-mono);
  font-size: 10px;
  line-height: 1.4;
  padding: 4px 6px;
  white-space: pre-wrap;
  word-break: break-all;
}

.copy-btn {
  border: 0;
  border-radius: 4px;
  background: #f3f4f6;
  color: #475569;
  cursor: pointer;
  font-size: 11px;
  padding: 4px 6px;
}

.copy-btn.small { padding: 3px 5px; }

.toggle-switch {
  position: relative;
  width: 40px;
  height: 22px;
  flex-shrink: 0;
  border-radius: 11px;
  background: #e5e7eb;
  cursor: pointer;
  transition: background .2s;
}

.toggle-switch.active { background: #0d6e6e; }
.toggle-switch.loading { opacity: .6; pointer-events: none; }

.toggle-slider {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, .2);
  transition: left .2s;
}

.toggle-switch.active .toggle-slider { left: 20px; }
.toggle-switch.small { width: 36px; height: 20px; }
.toggle-switch.small .toggle-slider { width: 16px; height: 16px; }
.toggle-switch.small.active .toggle-slider { left: 18px; }

.status-text {
  font-size: var(--font-size-xs);
  font-weight: 600;
}
.status-text.ACTIVE { color: #0d6e6e; }
.status-text.INACTIVE { color: #9ca3af; }

.action-btn {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: var(--radius, 8px);
  background: transparent;
  color: var(--gray-600, #475569);
  cursor: pointer;
  transition: all var(--transition-fast, .15s);
}
.action-btn svg {
  width: 14px;
  height: 14px;
}
.action-btn:hover {
  background: var(--primary-color, #0d6e6e);
  color: #fff;
}
.action-btn.danger:hover {
  background: var(--error-color, #ef4444);
}
.readonly-hint { color: #9ca3af; font-size: var(--font-size-xs); }

.empty-row,
.empty-state-pc {
  padding: 48px 20px;
  color: #94a3b8;
  text-align: center;
}

.empty-btn { padding: 8px 14px; }
.loading-state-pc,
.loading-state {
  display: flex;
  justify-content: center;
  padding: 60px;
}
.loading-spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #e5e7eb;
  border-top-color: #0d6e6e;
  border-radius: 50%;
  animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  height: 56px;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e5e5e5;
  background: #fff;
  padding: 0 16px;
}

.navbar-title {
  margin: 0;
  color: #1a1a1a;
  font-family: var(--font-heading);
  font-size: var(--font-size-lg);
  font-weight: 500;
}

.add-btn {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: #0d6e6e;
  color: #fff;
  cursor: pointer;
  font-size: 20px;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 16px 40px;
}

.mobile-domain,
.category-tabs {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.mobile-domain::-webkit-scrollbar,
.category-tabs::-webkit-scrollbar,
.category-tabs-pc::-webkit-scrollbar,
.domain-tabs::-webkit-scrollbar {
  display: none;
}

.tab-btn {
  min-height: 32px;
  padding: 0 12px;
  white-space: nowrap;
  font-size: var(--font-size-xs);
}

.category-tabs {
  display: flex;
  gap: 8px;
}

.preview-toggle {
  width: 100%;
  min-height: 36px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #fff;
  color: #0f766e;
  cursor: pointer;
  font-size: var(--font-size-sm);
}

.dict-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dict-card {
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  background: #fff;
  padding: 14px;
}

.dict-card.inactive { background: #fafafa; }
.dict-card.protected { border-color: #fde68a; background: #fffbeb; }
.card-main { margin-bottom: 8px; }
.card-value {
  margin-bottom: 4px;
  color: #1a1a1a;
  font-size: var(--font-size-base);
  font-weight: 650;
}
.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  color: #888;
  font-size: var(--font-size-xs);
}
.card-key {
  border-radius: 3px;
  background: #f3f4f6;
  color: #0d6e6e;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  padding: 1px 6px;
}
.separator { color: #d1d5db; }
.card-protected-badge {
  border-radius: 3px;
  background: #fee2e2;
  color: #dc2626;
  font-size: 10px;
  padding: 1px 6px;
}
.card-detail {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  border-radius: 6px;
  background: #fff7ed;
  padding: 6px 10px;
}
.detail-label { color: #888; font-size: var(--font-size-xs); }
.detail-extra {
  color: #c2410c;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  font-weight: 600;
}
.detail-extra.json { color: #4f46e5; }
.card-control {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-actions { display: flex; gap: 8px; }
.card-btn {
  min-width: 32px;
  height: 32px;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
}
.card-btn.edit { background: rgba(13, 110, 110, .08); color: #0d6e6e; }
.card-btn.delete { background: rgba(239, 68, 68, .08); color: #ef4444; }
.empty-state { padding: 32px; text-align: center; }
.empty-text { color: #9ca3af; font-size: var(--font-size-sm); }

.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, .5);
  padding: 20px;
}
.dialog-content {
  width: 100%;
  max-width: 500px;
  max-height: 90vh;
  overflow-y: auto;
  border-radius: 12px;
  background: #fff;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px 0;
}
.dialog-header h3 {
  margin: 0;
  color: #1a1a1a;
  font-size: var(--font-size-lg);
  font-weight: 650;
}
.dialog-close {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: #f3f4f6;
  color: #666;
  cursor: pointer;
  font-size: 20px;
}
.dialog-body { padding: 20px 24px; }
.form-group { margin-bottom: 16px; }
.form-group.half { flex: 1; }
.form-label {
  display: block;
  margin-bottom: 6px;
  color: #374151;
  font-size: var(--font-size-xs);
  font-weight: 600;
}
.required { color: #ef4444; margin-right: 4px; }
.form-input,
.form-select,
.form-textarea {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid #e5e5e5;
  border-radius: 8px;
  background: #fff;
  color: #1a1a1a;
  font-size: var(--font-size-sm);
  padding: 10px 12px;
}
.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  border-color: #0d6e6e;
  outline: none;
}
.form-input:disabled {
  background: #f9fafb;
  color: #9ca3af;
}
.form-input.is-error,
.form-select.is-error,
.form-textarea.is-error {
  border-color: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, .1);
}
.form-textarea { resize: vertical; }
.form-row { display: flex; gap: 12px; }
.error-hint,
.field-hint {
  display: block;
  margin-top: 4px;
  font-size: var(--font-size-xs);
}
.error-hint { color: #ef4444; }
.field-hint { color: #64748b; }
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 0 24px 20px;
}
.btn-cancel,
.btn-save {
  border: 0;
  border-radius: 8px;
  cursor: pointer;
  font-size: var(--font-size-sm);
  padding: 10px 20px;
}
.btn-cancel { background: #f3f4f6; color: #374151; }
.btn-save { background: #0d6e6e; color: #fff; font-weight: 600; }

.system-config-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #666;
  cursor: pointer;
  font-size: var(--font-size-sm);
}
.system-config-toggle input {
  width: 16px;
  height: 16px;
  accent-color: #0d6e6e;
}
.system-config-toggle.mobile {
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}

.mobile-category-picker {
  display: grid;
  gap: 6px;
}

.mobile-category-picker span {
  color: #64748b;
  font-size: var(--font-size-xs);
  font-weight: 650;
}

.mobile-category-picker select {
  width: 100%;
  min-height: 40px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #1f2937;
  font-size: var(--font-size-sm);
  padding: 0 12px;
}

.protected-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
  color: #92400e;
  font-size: var(--font-size-sm);
  padding: 12px 16px;
}
.link-btn {
  border: 0;
  background: transparent;
  color: #0d6e6e;
  cursor: pointer;
  font-size: var(--font-size-sm);
  font-weight: 600;
  padding: 0;
}

@media (max-width: 1180px) {
  .dict-workbench {
    grid-template-columns: 1fr;
  }
  .dict-sidebar {
    position: static;
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 1080px) {
  .dict-sidebar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 1024px) {
  .pc-dict { display: none; }
}
</style>
