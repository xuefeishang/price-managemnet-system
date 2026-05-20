<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast, showDialog } from 'vant'
import { getDicts, createDict, updateDict, deleteDict, getDictCategories } from '@/api/dict'
import { getStatusLabel, getDictOptions, CATEGORY_LABELS, loadAllDicts, refreshDictCache } from '@/composables/useDict'
import { useLayout } from '@/composables/useLayout'
import { isProtectedCategory, getCategoryMeta, getExtraValueMode, isColorValue, isJsonValue, formatJsonDisplay } from '@/constants/dictCategoryMeta'
import type { SysDict } from '@/types'

const dicts = ref<SysDict[]>([])
const categories = ref<string[]>([])
const loading = ref(false)
const selectedCategory = ref<string>('')
const togglingId = ref<number | null>(null)
const { isPCLayout } = useLayout()

// 显示系统配置开关（仅 ADMIN 可见）
const showSystemConfig = ref(false)

// 新增/编辑对话框
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
  id: null, category: '', dictKey: '', dictValue: '', extraValue: '',
  sortOrder: 0, status: 'ACTIVE', remark: ''
})
const isEditing = ref(false)
const newCategory = ref('')

// 获取分类中文标签
const getCategoryLabel = (category: string): string => {
  const meta = getCategoryMeta(category)
  return meta?.label || CATEGORY_LABELS[category] || category
}

// 可见分类列表（根据 showSystemConfig 过滤）
const visibleCategories = computed(() => {
  if (showSystemConfig.value) {
    return categories.value
  }
  return categories.value.filter(cat => !isProtectedCategory(cat))
})

// 状态筛选选项（使用全部状态项，含停用）
const statusFilterOptions = computed(() => getDictOptions('common_status'))

// 按分类分组显示
const groupedDicts = computed(() => {
  if (selectedCategory.value) {
    return { [selectedCategory.value]: dicts.value.filter(d => d.category === selectedCategory.value) }
  }
  const groups: Record<string, SysDict[]> = {}
  for (const dict of dicts.value) {
    // 默认隐藏受保护分类
    if (!showSystemConfig.value && isProtectedCategory(dict.category)) continue
    if (!groups[dict.category]) groups[dict.category] = []
    groups[dict.category].push(dict)
  }
  return groups
})


const loadDicts = async () => {
  loading.value = true
  try {
    const response = await getDicts(selectedCategory.value || undefined)
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
    // 管理页面需要看到所有分类（含仅有停用项的分类）
    const response = await getDictCategories(true)
    categories.value = response.data || []
  } catch (error) {
    console.error('Failed to load categories:', error)
  }
}

const handleToggleStatus = async (dict: SysDict) => {
  // 受保护分类不允许切换状态
  if (isProtectedCategory(dict.category)) {
    showToast('此分类由样式设置管理，请前往样式设置修改')
    return
  }

  if (togglingId.value === dict.id) return
  const newStatus = dict.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const actionText = getStatusLabel(newStatus)

  try {
    togglingId.value = dict.id
    await updateDict(dict.id, { status: newStatus } as Partial<SysDict>)
    dict.status = newStatus as any
    showToast(`字典项已${actionText}`)
    // 不刷新全局缓存，避免全屏刷新。页面刷新时自然更新。
  } catch (error) {
    console.error('Failed to toggle status:', error)
    showToast('操作失败')
  } finally {
    togglingId.value = null
  }
}

const handleCreate = (category?: string) => {
  // 受保护分类不允许创建
  if (category && isProtectedCategory(category)) {
    showToast('此分类由样式设置管理，请前往样式设置修改')
    return
  }

  isEditing.value = false
  editForm.value = {
    id: null,
    category: category || selectedCategory.value || '',
    dictKey: '', dictValue: '', extraValue: '',
    sortOrder: 0, status: 'ACTIVE', remark: ''
  }
  newCategory.value = ''
  validationErrors.value.clear()  // 清除验证错误
  showEditDialog.value = true
}

const handleEdit = (dict: SysDict) => {
  // 受保护分类不允许编辑
  if (isProtectedCategory(dict.category)) {
    showToast('此分类由样式设置管理，请前往样式设置修改')
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
  validationErrors.value.clear()  // 清除验证错误
  showEditDialog.value = true
}

// 表单验证错误字段
const validationErrors = ref<Set<string>>(new Set())

const handleSave = async () => {
  const form = editForm.value
  const category = form.category === '__new__' ? newCategory.value.trim() : form.category

  // 清除之前的验证错误
  validationErrors.value.clear()

  // 收集所有验证错误
  const errors: string[] = []

  if (!category) {
    errors.push('请选择或输入分类')
    validationErrors.value.add('category')
  }
  // 如果选择新建分类，检查新分类名称
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

  // 如果有验证错误，显示所有错误提示
  if (errors.length > 0) {
    showToast(errors.join('、'))
    return
  }

  try {
    if (isEditing.value && form.id) {
      await updateDict(form.id, {
        category,
        dictKey: form.dictKey,
        dictValue: form.dictValue,
        extraValue: form.extraValue,
        sortOrder: form.sortOrder,
        status: form.status as any,
        remark: form.remark
      } as Partial<SysDict>)
      showToast('更新成功')
    } else {
      await createDict({
        category,
        dictKey: form.dictKey,
        dictValue: form.dictValue,
        extraValue: form.extraValue,
        sortOrder: form.sortOrder,
        status: form.status as any,
        remark: form.remark
      } as Omit<SysDict, 'id' | 'createdTime' | 'updatedTime'>)
      showToast('创建成功')
    }
    showEditDialog.value = false
    loadDicts()
    loadCategories()
    // 异步刷新缓存，不阻塞UI
    refreshDictCache()
  } catch (error: any) {
    console.error('Failed to save dict:', error)
    showToast(error?.response?.data?.message || '保存失败')
  }
}

const handleDelete = async (dict: SysDict) => {
  // 受保护分类不允许删除
  if (isProtectedCategory(dict.category)) {
    showToast('此分类由样式设置管理，请前往样式设置修改')
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
      loadDicts()
      loadCategories()
      // 删除操作需要刷新缓存，确保其他页面不引用已删除的字典项
      refreshDictCache()
    } catch (error) {
      console.error('Failed to delete dict:', error)
      showToast('删除失败')
    }
  }).catch(() => {})
}

// 跳转到样式设置
const goToStyleSettings = () => {
  window.location.href = '/style-settings'
}

// 复制文本到剪贴板
const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制到剪贴板')
  } catch {
    showToast('复制失败')
  }
}

// 获取 extraValue 的渲染信息
const getExtraValueRender = (dict: SysDict) => {
  const mode = getExtraValueMode(dict.category)
  const value = dict.extraValue || ''

  if (!value) {
    return { type: 'empty', display: '', raw: '' }
  }

  // 根据模式渲染
  switch (mode) {
    case 'color':
      if (isColorValue(value)) {
        return { type: 'color', display: value, color: value, raw: value }
      }
      return { type: 'text', display: value, raw: value }

    case 'icon':
      return { type: 'icon', display: value, raw: value }

    case 'json':
      if (isJsonValue(value)) {
        return { type: 'json', display: formatJsonDisplay(value), raw: value }
      }
      return { type: 'text', display: value, raw: value }

    case 'readonly':
      // 受保护分类，只读显示
      if (isJsonValue(value)) {
        return { type: 'json', display: formatJsonDisplay(value), raw: value, readonly: true }
      }
      if (isColorValue(value)) {
        return { type: 'color', display: value, color: value, raw: value, readonly: true }
      }
      return { type: 'readonly', display: value, raw: value }

    default:
      return { type: 'text', display: value, raw: value }
  }
}

onMounted(() => {
  loadAllDicts()
  loadDicts()
  loadCategories()
})
</script>

<template>
  <div class="dict-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-dict">
        <div class="page-header-pc">
          <h1 class="page-title-pc">数据字典</h1>
          <div class="header-actions">
            <label class="system-config-toggle">
              <input type="checkbox" v-model="showSystemConfig" />
              <span>显示系统配置</span>
            </label>
            <select class="category-select" v-model="selectedCategory" @change="loadDicts()">
              <option value="">全部分类</option>
              <option v-for="cat in visibleCategories" :key="cat" :value="cat">
                {{ getCategoryLabel(cat) }} ({{ cat }})
              </option>
            </select>
            <button class="btn-primary-pc" @click="handleCreate()">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
              新建字典
            </button>
          </div>
        </div>

        <!-- 受保护分类提示 -->
        <div v-if="showSystemConfig" class="protected-notice">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="8" x2="12" y2="12"/>
            <line x1="12" y1="16" x2="12.01" y2="16"/>
          </svg>
          <span>以下分类由样式设置管理，仅可查看。如需修改请</span>
          <button class="link-btn" @click="goToStyleSettings">前往样式设置</button>
        </div>

        <!-- 按分类分组展示 -->
        <div v-if="!loading">
          <div v-for="(items, cat) in groupedDicts" :key="cat" class="category-section">
            <div class="category-header">
              <div class="category-info">
                <span class="category-icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                    <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                  </svg>
                </span>
                <h2 class="category-name">{{ getCategoryLabel(cat as string) }}</h2>
                <span class="category-code">{{ cat }}</span>
                <span class="category-count">{{ items.length }} 项</span>
                <span v-if="isProtectedCategory(cat as string)" class="protected-badge">受保护</span>
              </div>
              <button v-if="!isProtectedCategory(cat as string)" class="btn-add-pc" @click="handleCreate(cat as string)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/>
                  <line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
                新增
              </button>
            </div>

            <div class="dict-table">
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
                v-for="dict in items"
                :key="dict.id"
                class="table-row"
                :class="{ inactive: dict.status === 'INACTIVE', protected: isProtectedCategory(dict.category) }"
              >
                <div class="table-cell sort-col">{{ dict.sortOrder }}</div>
                <div class="table-cell key-col">
                  <code class="dict-key-code">{{ dict.dictKey }}</code>
                </div>
                <div class="table-cell value-col">
                  <span class="dict-value-text">{{ dict.dictValue }}</span>
                </div>
                <div class="table-cell extra-col">
                  <template v-if="dict.extraValue">
                    <!-- 颜色类型 -->
                    <div v-if="getExtraValueRender(dict).type === 'color'" class="extra-color">
                      <span class="color-swatch" :style="{ backgroundColor: getExtraValueRender(dict).color }"></span>
                      <code class="color-value">{{ getExtraValueRender(dict).display }}</code>
                    </div>
                    <!-- 图标类型 -->
                    <div v-else-if="getExtraValueRender(dict).type === 'icon'" class="extra-icon">
                      <span class="icon-preview">{{ getExtraValueRender(dict).display }}</span>
                    </div>
                    <!-- JSON 类型 -->
                    <div v-else-if="getExtraValueRender(dict).type === 'json'" class="extra-json">
                      <pre class="json-preview">{{ getExtraValueRender(dict).display }}</pre>
                      <button class="copy-btn" @click="copyToClipboard(getExtraValueRender(dict).raw)" title="复制JSON">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                        </svg>
                      </button>
                    </div>
                    <!-- 只读类型 -->
                    <div v-else-if="getExtraValueRender(dict).type === 'readonly'" class="extra-readonly">
                      <span class="readonly-text">{{ getExtraValueRender(dict).display }}</span>
                    </div>
                    <!-- 普通文本 -->
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
                    :title="dict.status === 'ACTIVE' ? '点击停用' : '点击启用'"
                  >
                    <div class="toggle-slider"></div>
                  </div>
                  <span class="status-text" :class="dict.status">
                    {{ getStatusLabel(dict.status) }}
                  </span>
                </div>
                <div class="table-cell remark-col">{{ dict.remark || '-' }}</div>
                <div class="table-cell actions-col" @click.stop>
                  <template v-if="!isProtectedCategory(dict.category)">
                    <button class="action-btn icon-only edit" @click="handleEdit(dict)" title="编辑">
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                        <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                      </svg>
                    </button>
                    <button class="action-btn icon-only delete" @click="handleDelete(dict)" title="删除">
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3 6 5 6 21 6"/>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                      </svg>
                    </button>
                  </template>
                  <span v-else class="readonly-hint">只读</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="Object.keys(groupedDicts).length === 0" class="empty-state-pc">
            <div class="empty-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
              </svg>
            </div>
            <p class="empty-text">暂无字典数据</p>
            <button class="empty-btn" @click="handleCreate()">创建第一个字典项</button>
          </div>
        </div>

        <div v-else class="loading-state-pc">
          <div class="loading-spinner"></div>
        </div>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <header class="navbar">
        <div class="navbar-left">
          <h1 class="navbar-title">数据字典</h1>
        </div>
        <button class="add-btn" @click="handleCreate()">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
        </button>
      </header>

      <main class="content">
        <!-- 系统配置开关 -->
        <label class="system-config-toggle mobile">
          <input type="checkbox" v-model="showSystemConfig" />
          <span>显示系统配置</span>
        </label>

        <!-- 受保护分类提示 -->
        <div v-if="showSystemConfig" class="protected-notice mobile">
          <span>以下分类由样式设置管理，仅可查看</span>
          <button class="link-btn" @click="goToStyleSettings">前往样式设置</button>
        </div>

        <!-- 分类筛选 -->
        <div class="category-tabs">
          <button
            class="tab-btn"
            :class="{ active: selectedCategory === '' }"
            @click="selectedCategory = ''; loadDicts()"
          >全部</button>
          <button
            v-for="cat in visibleCategories"
            :key="cat"
            class="tab-btn"
            :class="{ active: selectedCategory === cat, protected: isProtectedCategory(cat) }"
            @click="selectedCategory = cat; loadDicts()"
          >
            {{ getCategoryLabel(cat) }}
            <span v-if="isProtectedCategory(cat)" class="tab-protected-icon">🔒</span>
          </button>
        </div>

        <div class="dict-list" v-if="!loading">
          <div
            v-for="dict in dicts"
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
                  <span v-if="isProtectedCategory(dict.category)" class="card-protected-badge">受保护</span>
                </div>
              </div>
            </div>

            <div class="card-detail" v-if="dict.extraValue">
              <span class="detail-label">扩展值</span>
              <!-- 颜色类型 -->
              <template v-if="getExtraValueRender(dict).type === 'color'">
                <span class="color-swatch small" :style="{ backgroundColor: getExtraValueRender(dict).color }"></span>
                <code class="detail-extra">{{ getExtraValueRender(dict).display }}</code>
              </template>
              <!-- JSON 类型 -->
              <template v-else-if="getExtraValueRender(dict).type === 'json'">
                <code class="detail-extra json">JSON</code>
                <button class="copy-btn small" @click="copyToClipboard(getExtraValueRender(dict).raw)" title="复制">
                  <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                  </svg>
                </button>
              </template>
              <!-- 其他类型 -->
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
                  <button class="card-btn edit" @click="handleEdit(dict)">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button class="card-btn delete" @click="handleDelete(dict)">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </template>
              <template v-else>
                <span class="status-text" :class="dict.status">{{ getStatusLabel(dict.status) }}</span>
                <span class="readonly-hint mobile">只读</span>
              </template>
            </div>
          </div>

          <div v-if="dicts.length === 0" class="empty-state">
            <p class="empty-text">暂无字典数据</p>
          </div>
        </div>

        <div v-else class="loading-state">
          <div class="loading-spinner"></div>
        </div>
      </main>
    </template>

    <!-- 编辑对话框 -->
    <div v-if="showEditDialog" class="dialog-overlay" @click.self="showEditDialog = false">
      <div class="dialog-content">
        <div class="dialog-header">
          <h3>{{ isEditing ? '编辑字典项' : '新建字典项' }}</h3>
          <button class="dialog-close" @click="showEditDialog = false">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>
        <div class="dialog-body">
          <div class="form-group">
            <label class="form-label"><span class="required">*</span>分类</label>
            <select v-if="!isEditing" v-model="editForm.category"
                    class="form-select"
                    :class="{ 'is-error': validationErrors.has('category') }">
              <option value="">请选择分类</option>
              <option v-for="cat in categories" :key="cat" :value="cat">
                {{ getCategoryLabel(cat) }} ({{ cat }})
              </option>
              <option value="__new__">+ 新建分类</option>
            </select>
            <input v-else :value="editForm.category" class="form-input" disabled />
            <span v-if="validationErrors.has('category')" class="error-hint">请选择分类</span>
          </div>
          <div v-if="editForm.category === '__new__'" class="form-group">
            <label class="form-label"><span class="required">*</span>新分类名称</label>
            <input v-model="newCategory"
                   class="form-input"
                   :class="{ 'is-error': validationErrors.has('newCategory') }"
                   placeholder="输入新分类标识，如 payment_type" />
            <span v-if="validationErrors.has('newCategory')" class="error-hint">请输入新分类名称</span>
          </div>
          <div class="form-group">
            <label class="form-label"><span class="required">*</span>字典键 (Key)</label>
            <input v-model="editForm.dictKey"
                   class="form-input"
                   :class="{ 'is-error': validationErrors.has('dictKey') }"
                   placeholder="如 CNY、ACTIVE" />
            <span v-if="validationErrors.has('dictKey')" class="error-hint">请输入字典键</span>
          </div>
          <div class="form-group">
            <label class="form-label"><span class="required">*</span>显示值</label>
            <input v-model="editForm.dictValue"
                   class="form-input"
                   :class="{ 'is-error': validationErrors.has('dictValue') }"
                   placeholder="如 人民币、启用" />
            <span v-if="validationErrors.has('dictValue')" class="error-hint">请输入显示值</span>
          </div>
          <div class="form-group">
            <label class="form-label">扩展值</label>
            <input v-model="editForm.extraValue" class="form-input" placeholder="如 ¥、$、图标名（可选）" />
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
.dict-page { background-color: var(--bg-page, #FAFAFA); }

/* PC布局：填满主内容区域，与UserManagement一致 */
.pc-dict {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.page-header-pc { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-title-pc { font-family: var(--font-heading); font-size: var(--font-size-2xl); font-weight: 500; color: var(--text-primary, #1A1A1A); margin: 0; }
.header-actions { display: flex; gap: 12px; align-items: center; }

.category-select {
  padding: 8px 12px; border: 1px solid var(--border-color, #E5E5E5); border-radius: var(--radius, 8px);
  font-family: var(--font-body); font-size: var(--font-size-sm); color: var(--text-primary, #1A1A1A);
  background: var(--bg-card, #FFFFFF); cursor: pointer; min-width: 180px;
}
.category-select:focus { outline: none; border-color: var(--primary-color, #0D6E6E); }

.btn-primary-pc {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; background: var(--primary-color, #0D6E6E); color: var(--text-on-primary, #FFFFFF);
  border: none; border-radius: var(--radius, 8px); font-family: var(--font-body);
  font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; transition: all 150ms;
}
.btn-primary-pc:hover { background: var(--primary-hover, #0A5C5C); }

.btn-add-pc {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; background: rgba(13,110,110,0.08); color: var(--primary-color, #0D6E6E);
  border: none; border-radius: var(--radius-sm, 6px); font-family: var(--font-body);
  font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; transition: all 150ms;
}
.btn-add-pc:hover { background: rgba(13,110,110,0.15); }

.category-section { margin-bottom: 32px; }
.category-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px; padding: 0 4px;
}
.category-info { display: flex; align-items: center; gap: 10px; }
.category-icon { width: 32px; height: 32px; border-radius: var(--radius, 8px); background: rgba(13,110,110,0.1); color: var(--primary-color, #0D6E6E); display: flex; align-items: center; justify-content: center; }
.category-name { font-family: var(--font-body); font-size: var(--font-size-base); font-weight: 600; color: var(--text-primary, #1A1A1A); margin: 0; }
.category-code { font-family: var(--font-mono); font-size: var(--font-size-xs); color: var(--text-secondary, #888); background: var(--bg-secondary, #F3F4F6); padding: 2px 8px; border-radius: var(--radius-sm, 4px); }
.category-count { font-size: var(--font-size-xs); color: var(--text-secondary, #888); }

.dict-table { background: var(--bg-card, #FFFFFF); border-radius: var(--radius-lg, 12px); border: 1px solid var(--border-color, #E5E5E5); overflow-x: auto; }
.table-header { display: flex; align-items: center; background: var(--bg-secondary, #FAFAFA); border-bottom: 1px solid var(--border-color, #E5E5E5); padding: 0 20px; min-width: 800px; }
.table-row { display: flex; align-items: center; padding: 0 20px; border-bottom: 1px solid var(--border-light, #F3F4F6); transition: background-color 150ms; min-width: 800px; }
.table-row:last-child { border-bottom: none; }
.table-row:hover { background: var(--bg-hover, #FAFAFA); }
.table-row.inactive { background: var(--bg-inactive, #FDFCFB); }
.table-row.inactive .dict-value-text { color: var(--text-disabled, #9CA3AF); }

/* 表头单元格：使用CSS变量字体体系 */
.table-header .table-cell {
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.table-cell { padding: 14px 8px; font-family: var(--font-body); font-size: var(--font-size-sm); color: var(--text-primary, #1A1A1A); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table-cell.sort-col { width: 8%; text-align: center; }
.table-cell.key-col { width: 15%; }
.table-cell.value-col { width: 15%; }
.table-cell.extra-col { width: 20%; max-width: 200px; }
.table-cell.status-col { width: 15%; display: flex; align-items: center; gap: 8px; }
.table-cell.remark-col { width: 17%; }
.table-cell.actions-col { width: 10%; display: flex; gap: 6px; justify-content: flex-end; }

.dict-key-code { font-family: var(--font-mono); font-size: var(--font-size-xs); background: #F3F4F6; padding: 2px 6px; border-radius: 4px; color: #0D6E6E; }
.dict-value-text { font-weight: 500; }
.dict-extra-badge { font-family: var(--font-mono); font-size: var(--font-size-xs); background: #FFF7ED; color: #C2410C; padding: 2px 8px; border-radius: 4px; }
.dict-extra-empty { color: #D1D5DB; }

/* extraValue 智能渲染样式 */
.extra-color {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.color-swatch {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1px solid rgba(0,0,0,0.1);
  flex-shrink: 0;
}
.color-swatch.small {
  width: 14px;
  height: 14px;
}
.color-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: #6B7280;
}

.extra-icon {
  display: inline-flex;
  align-items: center;
}
.icon-preview {
  font-size: var(--font-size-sm);
  padding: 2px 8px;
  background: #EEF2FF;
  color: #4F46E5;
  border-radius: 4px;
}

.extra-json {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  max-width: 100%;
}
.json-preview {
  font-family: var(--font-mono);
  font-size: 10px;
  background: #F8FAFC;
  color: #475569;
  padding: 4px 8px;
  border-radius: 4px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 60px;
  overflow-y: auto;
  flex: 1;
  border: 1px solid #E2E8F0;
}

.extra-readonly {
  display: inline-flex;
  align-items: center;
}
.readonly-text {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: #9CA3AF;
  font-style: italic;
}

.copy-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: #F3F4F6;
  border-radius: 4px;
  cursor: pointer;
  color: #6B7280;
  transition: all 150ms;
  flex-shrink: 0;
}
.copy-btn:hover {
  background: #E5E7EB;
  color: #374151;
}
.copy-btn.small {
  width: 20px;
  height: 20px;
}

.detail-extra.json {
  background: #EEF2FF;
  color: #4F46E5;
}

.toggle-switch { width: 40px; height: 22px; border-radius: 11px; background: #E5E7EB; position: relative; cursor: pointer; transition: all 200ms; flex-shrink: 0; }
.toggle-switch.active { background: #0D6E6E; }
.toggle-switch.loading { opacity: 0.6; pointer-events: none; }
.toggle-slider { position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: #FFFFFF; box-shadow: 0 1px 3px rgba(0,0,0,0.2); transition: all 200ms; }
.toggle-switch.active .toggle-slider { left: 20px; }
.toggle-switch.small { width: 36px; height: 20px; }
.toggle-switch.small .toggle-slider { width: 16px; height: 16px; }
.toggle-switch.small.active .toggle-slider { left: 18px; }

.status-text { font-size: var(--font-size-xs); font-weight: 500; }
.status-text.ACTIVE { color: #0D6E6E; }
.status-text.INACTIVE { color: #9CA3AF; }

.action-btn { display: inline-flex; align-items: center; justify-content: center; border: none; border-radius: 8px; cursor: pointer; transition: all 150ms; }
.action-btn.icon-only { width: 32px; height: 32px; }
.action-btn.edit { background: rgba(13,110,110,0.06); color: #0D6E6E; }
.action-btn.edit:hover { background: rgba(13,110,110,0.14); transform: scale(1.05); }
.action-btn.delete { background: rgba(239,68,68,0.06); color: #EF4444; }
.action-btn.delete:hover { background: rgba(239,68,68,0.14); transform: scale(1.05); }

.empty-state-pc { display: flex; flex-direction: column; align-items: center; padding: 80px 24px; text-align: center; }
.empty-icon { width: 80px; height: 80px; border-radius: 20px; background: #F3F4F6; color: #9CA3AF; display: flex; align-items: center; justify-content: center; margin-bottom: 20px; }
.empty-text { font-family: var(--font-body); font-size: var(--font-size-sm); color: #9CA3AF; margin: 0 0 20px 0; }
.empty-btn { padding: 10px 20px; background: #0D6E6E; color: #FFFFFF; border: none; border-radius: 8px; font-family: var(--font-body); font-size: var(--font-size-xs); font-weight: 500; cursor: pointer; }
.loading-state-pc { display: flex; align-items: center; justify-content: center; padding: 120px; background: #FFFFFF; border-radius: 12px; border: 1px solid #E5E5E5; }
.loading-spinner { width: 32px; height: 32px; border: 3px solid #E5E5E5; border-top-color: #0D6E6E; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 移动端样式 ========== */
.navbar { height: 56px; background: #FFFFFF; border-bottom: 1px solid #E5E5E5; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; position: sticky; top: 0; z-index: 100; }
.navbar-title { font-family: var(--font-heading); font-size: var(--font-size-lg); font-weight: 500; color: #1A1A1A; margin: 0; }
.add-btn { width: 32px; height: 32px; border: none; background: #0D6E6E; color: #FFFFFF; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; }

.content { flex: 1; padding: 12px 16px; display: flex; flex-direction: column; gap: 12px; padding-bottom: 40px; }

.category-tabs { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; -webkit-overflow-scrolling: touch; }
.category-tabs::-webkit-scrollbar { display: none; }
.tab-btn { padding: 6px 14px; border: 1px solid #E5E5E5; border-radius: 20px; background: #FFFFFF; font-family: var(--font-body); font-size: var(--font-size-xs); color: #666; cursor: pointer; white-space: nowrap; transition: all 150ms; }
.tab-btn.active { background: #0D6E6E; color: #FFFFFF; border-color: #0D6E6E; }

.dict-list { display: flex; flex-direction: column; gap: 10px; }
.dict-card { background: #FFFFFF; border-radius: 12px; padding: 14px; border: 1px solid #E5E5E5; }
.dict-card.inactive { background: #FAFAFA; }
.dict-card.inactive .card-value { color: #9CA3AF; }

.card-main { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 8px; }
.card-info { flex: 1; }
.card-value { font-family: var(--font-body); font-size: var(--font-size-base); font-weight: 600; color: #1A1A1A; margin-bottom: 4px; }
.card-meta { display: flex; align-items: center; gap: 6px; font-size: var(--font-size-xs); color: #888; }
.card-key { font-family: var(--font-mono); background: #F3F4F6; padding: 1px 6px; border-radius: 3px; font-size: var(--font-size-xs); color: #0D6E6E; }
.card-category { font-size: var(--font-size-xs); }
.separator { color: #D1D5DB; }

.card-detail { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #FFF7ED; border-radius: 6px; margin-bottom: 8px; }
.detail-label { font-size: var(--font-size-xs); color: #888; }
.detail-extra { font-family: var(--font-mono); font-size: var(--font-size-xs); color: #C2410C; font-weight: 500; }

.card-control { display: flex; align-items: center; justify-content: space-between; }
.card-actions { display: flex; gap: 8px; }
.card-btn { display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; border: none; border-radius: 6px; cursor: pointer; transition: all 150ms; }
.card-btn.edit { background: rgba(13,110,110,0.08); color: #0D6E6E; }
.card-btn.delete { background: rgba(239,68,68,0.08); color: #EF4444; }

.empty-state { text-align: center; padding: 40px; }
.loading-state { display: flex; justify-content: center; padding: 60px; }

/* ========== 对话框样式 ========== */
.dialog-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: 20px; }
.dialog-content { background: #FFFFFF; border-radius: 16px; width: 100%; max-width: 480px; max-height: 90vh; overflow-y: auto; }
.dialog-header { display: flex; justify-content: space-between; align-items: center; padding: 20px 24px 0; }
.dialog-header h3 { font-family: var(--font-body); font-size: var(--font-size-lg); font-weight: 600; color: #1A1A1A; margin: 0; }
.dialog-close { width: 32px; height: 32px; border: none; background: #F3F4F6; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: #666; }

.dialog-body { padding: 20px 24px; }
.form-group { margin-bottom: 16px; }
.form-group.half { flex: 1; }
.form-label { display: block; font-family: var(--font-body); font-size: var(--font-size-xs); font-weight: 500; color: #374151; margin-bottom: 6px; }
.form-label .required { color: #EF4444; margin-right: 4px; font-weight: 600; }
.form-input, .form-select, .form-textarea {
  width: 100%; padding: 10px 12px; border: 1px solid #E5E5E5; border-radius: 8px;
  font-family: var(--font-body); font-size: var(--font-size-sm); color: #1A1A1A;
  background: #FFFFFF; box-sizing: border-box;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.form-input:focus, .form-select:focus, .form-textarea:focus { outline: none; border-color: #0D6E6E; }
.form-input:disabled { background: #F9FAFB; color: #9CA3AF; }
.form-input.is-error, .form-select.is-error { border-color: #EF4444; box-shadow: 0 0 0 2px rgba(239,68,68,0.1); }
.form-input.is-error:focus, .form-select.is-error:focus { border-color: #EF4444; box-shadow: 0 0 0 3px rgba(239,68,68,0.15); }
.error-hint { display: block; font-size: var(--font-size-xs); color: #EF4444; margin-top: 4px; }
.form-textarea { resize: vertical; }
.form-row { display: flex; gap: 12px; }

.dialog-footer { display: flex; gap: 12px; justify-content: flex-end; padding: 0 24px 20px; }
.btn-cancel { padding: 10px 20px; background: #F3F4F6; color: #374151; border: none; border-radius: 8px; font-family: var(--font-body); font-size: var(--font-size-sm); cursor: pointer; }
.btn-save { padding: 10px 20px; background: #0D6E6E; color: #FFFFFF; border: none; border-radius: 8px; font-family: var(--font-body); font-size: var(--font-size-sm); font-weight: 500; cursor: pointer; }
.btn-save:hover { background: #0A5C5C; }

/* ========== 受保护分类样式 ========== */
.system-config-toggle {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: var(--text-secondary, #666);
}
.system-config-toggle input {
  width: 16px;
  height: 16px;
  accent-color: var(--primary-color, #0D6E6E);
}
.system-config-toggle.mobile {
  padding: 8px 0;
  border-bottom: 1px solid var(--border-light, #F3F4F6);
  margin-bottom: 8px;
}

.protected-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #FEF3C7;
  border: 1px solid #FCD34D;
  border-radius: var(--radius, 8px);
  margin-bottom: 16px;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: #92400E;
}
.protected-notice.mobile {
  padding: 10px 12px;
  font-size: var(--font-size-xs);
  flex-wrap: wrap;
}
.protected-notice svg {
  flex-shrink: 0;
  color: #D97706;
}

.link-btn {
  background: none;
  border: none;
  color: var(--primary-color, #0D6E6E);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  text-decoration: underline;
  padding: 0;
}
.link-btn:hover {
  color: var(--primary-hover, #0A5C5C);
}

.protected-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  background: #FEE2E2;
  color: #DC2626;
  font-size: var(--font-size-xs);
  font-weight: 500;
  border-radius: var(--radius-sm, 4px);
  margin-left: 8px;
}

.readonly-hint {
  font-size: var(--font-size-xs);
  color: #9CA3AF;
  font-style: italic;
}
.readonly-hint.mobile {
  margin-left: auto;
}

.table-row.protected {
  background: #FFFBEB;
}
.table-row.protected:hover {
  background: #FEF3C7;
}

/* 移动端受保护分类标签 */
.tab-btn.protected {
  background: #FEF3C7;
  border-color: #FCD34D;
  color: #92400E;
}
.tab-btn.protected.active {
  background: #D97706;
  color: #FFFFFF;
  border-color: #D97706;
}
.tab-protected-icon {
  font-size: 10px;
  margin-left: 4px;
}

.card-protected-badge {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  background: #FEE2E2;
  color: #DC2626;
  font-size: 10px;
  font-weight: 500;
  border-radius: 3px;
  margin-left: 6px;
}

.dict-card.protected {
  background: #FFFBEB;
  border-color: #FCD34D;
}

@media (max-width: 1024px) {
  .pc-dict { display: none; }
}
</style>
