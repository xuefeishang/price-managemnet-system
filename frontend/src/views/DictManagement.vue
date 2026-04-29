<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { showToast, showDialog } from 'vant'
import { getDicts, createDict, updateDict, deleteDict, getDictCategories } from '@/api/dict'
import { getStatusLabel, getDictOptions, CATEGORY_LABELS, loadAllDicts, refreshDictCache } from '@/composables/useDict'
import type { SysDict } from '@/types'

const dicts = ref<SysDict[]>([])
const categories = ref<string[]>([])
const loading = ref(false)
const selectedCategory = ref<string>('')
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const togglingId = ref<number | null>(null)

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

const isPCLayout = computed(() => windowWidth.value >= 1024)

// 获取分类中文标签
const getCategoryLabel = (category: string): string => {
  return CATEGORY_LABELS[category] || category
}

// 状态筛选选项（使用全部状态项，含停用）
const statusFilterOptions = computed(() => getDictOptions('common_status'))

// 按分类分组显示
const groupedDicts = computed(() => {
  if (selectedCategory.value) {
    return { [selectedCategory.value]: dicts.value.filter(d => d.category === selectedCategory.value) }
  }
  const groups: Record<string, SysDict[]> = {}
  for (const dict of dicts.value) {
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
  if (togglingId.value === dict.id) return
  const newStatus = dict.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const actionText = getStatusLabel(newStatus)

  try {
    togglingId.value = dict.id
    await updateDict(dict.id, { status: newStatus } as Partial<SysDict>)
    dict.status = newStatus as any
    showToast(`字典项已${actionText}`)
    refreshDictCache()
  } catch (error) {
    console.error('Failed to toggle status:', error)
    showToast('操作失败')
  } finally {
    togglingId.value = null
  }
}

const handleCreate = (category?: string) => {
  isEditing.value = false
  editForm.value = {
    id: null,
    category: category || selectedCategory.value || '',
    dictKey: '', dictValue: '', extraValue: '',
    sortOrder: 0, status: 'ACTIVE', remark: ''
  }
  newCategory.value = ''
  showEditDialog.value = true
}

const handleEdit = (dict: SysDict) => {
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
  showEditDialog.value = true
}

const handleSave = async () => {
  const form = editForm.value
  const category = form.category === '__new__' ? newCategory.value.trim() : form.category

  if (!category) {
    showToast('请选择或输入分类')
    return
  }
  if (!form.dictKey.trim()) {
    showToast('请输入字典键')
    return
  }
  if (!form.dictValue.trim()) {
    showToast('请输入显示值')
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
    refreshDictCache()
  } catch (error: any) {
    console.error('Failed to save dict:', error)
    showToast(error?.response?.data?.message || '保存失败')
  }
}

const handleDelete = async (dict: SysDict) => {
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
      refreshDictCache()
    } catch (error) {
      console.error('Failed to delete dict:', error)
      showToast('删除失败')
    }
  }).catch(() => {})
}

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadAllDicts()
  loadDicts()
  loadCategories()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
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
            <select class="category-select" v-model="selectedCategory" @change="loadDicts()">
              <option value="">全部分类</option>
              <option v-for="cat in categories" :key="cat" :value="cat">
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
              </div>
              <button class="btn-add-pc" @click="handleCreate(cat as string)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/>
                  <line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
                新增
              </button>
            </div>

            <div class="dict-table">
              <div class="table-header">
                <div class="table-cell key-col">字典键</div>
                <div class="table-cell value-col">显示值</div>
                <div class="table-cell extra-col">扩展值</div>
                <div class="table-cell sort-col">排序</div>
                <div class="table-cell status-col">状态</div>
                <div class="table-cell remark-col">备注</div>
                <div class="table-cell actions-col">操作</div>
              </div>

              <div
                v-for="dict in items"
                :key="dict.id"
                class="table-row"
                :class="{ inactive: dict.status === 'INACTIVE' }"
              >
                <div class="table-cell key-col">
                  <code class="dict-key-code">{{ dict.dictKey }}</code>
                </div>
                <div class="table-cell value-col">
                  <span class="dict-value-text">{{ dict.dictValue }}</span>
                </div>
                <div class="table-cell extra-col">
                  <span v-if="dict.extraValue" class="dict-extra-badge">{{ dict.extraValue }}</span>
                  <span v-else class="dict-extra-empty">-</span>
                </div>
                <div class="table-cell sort-col">{{ dict.sortOrder }}</div>
                <div class="table-cell status-col">
                  <div
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
        <!-- 分类筛选 -->
        <div class="category-tabs">
          <button
            class="tab-btn"
            :class="{ active: selectedCategory === '' }"
            @click="selectedCategory = ''; loadDicts()"
          >全部</button>
          <button
            v-for="cat in categories"
            :key="cat"
            class="tab-btn"
            :class="{ active: selectedCategory === cat }"
            @click="selectedCategory = cat; loadDicts()"
          >{{ getCategoryLabel(cat) }}</button>
        </div>

        <div class="dict-list" v-if="!loading">
          <div
            v-for="dict in dicts"
            :key="dict.id"
            class="dict-card"
            :class="{ inactive: dict.status === 'INACTIVE' }"
          >
            <div class="card-main">
              <div class="card-info">
                <div class="card-value">{{ dict.dictValue }}</div>
                <div class="card-meta">
                  <code class="card-key">{{ dict.dictKey }}</code>
                  <span class="separator">·</span>
                  <span class="card-category">{{ dict.category }}</span>
                </div>
              </div>
            </div>

            <div class="card-detail" v-if="dict.extraValue">
              <span class="detail-label">扩展值</span>
              <span class="detail-extra">{{ dict.extraValue }}</span>
            </div>

            <div class="card-control">
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
            <label class="form-label">分类</label>
            <select v-if="!isEditing" v-model="editForm.category" class="form-select">
              <option value="">请选择分类</option>
              <option v-for="cat in categories" :key="cat" :value="cat">
                {{ getCategoryLabel(cat) }} ({{ cat }})
              </option>
              <option value="__new__">+ 新建分类</option>
            </select>
            <input v-else :value="editForm.category" class="form-input" disabled />
          </div>
          <div v-if="editForm.category === '__new__'" class="form-group">
            <label class="form-label">新分类名称</label>
            <input v-model="newCategory" class="form-input" placeholder="输入新分类标识，如 payment_type" />
          </div>
          <div class="form-group">
            <label class="form-label">字典键 (Key)</label>
            <input v-model="editForm.dictKey" class="form-input" placeholder="如 CNY、ACTIVE" />
          </div>
          <div class="form-group">
            <label class="form-label">显示值</label>
            <input v-model="editForm.dictValue" class="form-input" placeholder="如 人民币、启用" />
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
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.dict-page { min-height: 100vh; background-color: #FAFAFA; }

.pc-dict { padding: 32px; max-width: 1200px; margin: 0 auto; }

.page-header-pc { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-title-pc { font-family: 'Newsreader', Georgia, serif; font-size: 24px; font-weight: 500; color: #1A1A1A; margin: 0; }
.header-actions { display: flex; gap: 12px; align-items: center; }

.category-select {
  padding: 8px 12px; border: 1px solid #E5E5E5; border-radius: 8px;
  font-family: 'Inter', sans-serif; font-size: 13px; color: #1A1A1A;
  background: #FFFFFF; cursor: pointer; min-width: 180px;
}
.category-select:focus { outline: none; border-color: #0D6E6E; }

.btn-primary-pc {
  display: inline-flex; align-items: center; gap: 6px;
  padding: 8px 16px; background: #0D6E6E; color: #FFFFFF;
  border: none; border-radius: 8px; font-family: 'Inter', sans-serif;
  font-size: 13px; font-weight: 500; cursor: pointer; transition: all 150ms;
}
.btn-primary-pc:hover { background: #0A5C5C; }

.btn-add-pc {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; background: rgba(13,110,110,0.08); color: #0D6E6E;
  border: none; border-radius: 6px; font-family: 'Inter', sans-serif;
  font-size: 12px; font-weight: 500; cursor: pointer; transition: all 150ms;
}
.btn-add-pc:hover { background: rgba(13,110,110,0.15); }

.category-section { margin-bottom: 32px; }
.category-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px; padding: 0 4px;
}
.category-info { display: flex; align-items: center; gap: 10px; }
.category-icon { width: 32px; height: 32px; border-radius: 8px; background: rgba(13,110,110,0.1); color: #0D6E6E; display: flex; align-items: center; justify-content: center; }
.category-name { font-family: 'Inter', sans-serif; font-size: 16px; font-weight: 600; color: #1A1A1A; margin: 0; }
.category-code { font-family: 'JetBrains Mono', monospace; font-size: 12px; color: #888; background: #F3F4F6; padding: 2px 8px; border-radius: 4px; }
.category-count { font-size: 12px; color: #888; }

.dict-table { background: #FFFFFF; border-radius: 12px; border: 1px solid #E5E5E5; overflow: hidden; }
.table-header { display: flex; align-items: center; background: #FAFAFA; border-bottom: 1px solid #E5E5E5; padding: 0 20px; }
.table-row { display: flex; align-items: center; padding: 0 20px; border-bottom: 1px solid #F3F4F6; transition: background-color 150ms; }
.table-row:last-child { border-bottom: none; }
.table-row:hover { background: #FAFAFA; }
.table-row.inactive { background: #FDFCFB; }
.table-row.inactive .dict-value-text { color: #9CA3AF; }

.table-cell { padding: 14px 8px; font-family: 'Inter', sans-serif; font-size: 14px; color: #1A1A1A; }
.table-cell.key-col { flex: 1; min-width: 100px; }
.table-cell.value-col { flex: 1; min-width: 100px; }
.table-cell.extra-col { flex: 0.7; min-width: 80px; }
.table-cell.sort-col { flex: 0.4; min-width: 50px; color: #666; font-size: 13px; }
.table-cell.status-col { flex: 1; min-width: 130px; display: flex; align-items: center; gap: 8px; }
.table-cell.remark-col { flex: 1.2; min-width: 100px; color: #888; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.table-cell.actions-col { flex: 0; min-width: 80px; display: flex; gap: 6px; justify-content: flex-end; }

.dict-key-code { font-family: 'JetBrains Mono', monospace; font-size: 13px; background: #F3F4F6; padding: 2px 6px; border-radius: 4px; color: #0D6E6E; }
.dict-value-text { font-weight: 500; }
.dict-extra-badge { font-family: 'JetBrains Mono', monospace; font-size: 13px; background: #FFF7ED; color: #C2410C; padding: 2px 8px; border-radius: 4px; }
.dict-extra-empty { color: #D1D5DB; }

.toggle-switch { width: 40px; height: 22px; border-radius: 11px; background: #E5E7EB; position: relative; cursor: pointer; transition: all 200ms; flex-shrink: 0; }
.toggle-switch.active { background: #0D6E6E; }
.toggle-switch.loading { opacity: 0.6; pointer-events: none; }
.toggle-slider { position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: #FFFFFF; box-shadow: 0 1px 3px rgba(0,0,0,0.2); transition: all 200ms; }
.toggle-switch.active .toggle-slider { left: 20px; }
.toggle-switch.small { width: 36px; height: 20px; }
.toggle-switch.small .toggle-slider { width: 16px; height: 16px; }
.toggle-switch.small.active .toggle-slider { left: 18px; }

.status-text { font-size: 13px; font-weight: 500; }
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
.empty-text { font-family: 'Inter', sans-serif; font-size: 14px; color: #9CA3AF; margin: 0 0 20px 0; }
.empty-btn { padding: 10px 20px; background: #0D6E6E; color: #FFFFFF; border: none; border-radius: 8px; font-family: 'Inter', sans-serif; font-size: 13px; font-weight: 500; cursor: pointer; }
.loading-state-pc { display: flex; align-items: center; justify-content: center; padding: 120px; background: #FFFFFF; border-radius: 12px; border: 1px solid #E5E5E5; }
.loading-spinner { width: 32px; height: 32px; border: 3px solid #E5E5E5; border-top-color: #0D6E6E; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 移动端样式 ========== */
.navbar { height: 56px; background: #FFFFFF; border-bottom: 1px solid #E5E5E5; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; position: sticky; top: 0; z-index: 100; }
.navbar-title { font-family: 'Newsreader', Georgia, serif; font-size: 18px; font-weight: 500; color: #1A1A1A; margin: 0; }
.add-btn { width: 32px; height: 32px; border: none; background: #0D6E6E; color: #FFFFFF; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; }

.content { flex: 1; padding: 12px 16px; display: flex; flex-direction: column; gap: 12px; padding-bottom: 40px; }

.category-tabs { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; -webkit-overflow-scrolling: touch; }
.category-tabs::-webkit-scrollbar { display: none; }
.tab-btn { padding: 6px 14px; border: 1px solid #E5E5E5; border-radius: 20px; background: #FFFFFF; font-family: 'Inter', sans-serif; font-size: 12px; color: #666; cursor: pointer; white-space: nowrap; transition: all 150ms; }
.tab-btn.active { background: #0D6E6E; color: #FFFFFF; border-color: #0D6E6E; }

.dict-list { display: flex; flex-direction: column; gap: 10px; }
.dict-card { background: #FFFFFF; border-radius: 12px; padding: 14px; border: 1px solid #E5E5E5; }
.dict-card.inactive { background: #FAFAFA; }
.dict-card.inactive .card-value { color: #9CA3AF; }

.card-main { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 8px; }
.card-info { flex: 1; }
.card-value { font-family: 'Inter', sans-serif; font-size: 15px; font-weight: 600; color: #1A1A1A; margin-bottom: 4px; }
.card-meta { display: flex; align-items: center; gap: 6px; font-size: 11px; color: #888; }
.card-key { font-family: 'JetBrains Mono', monospace; background: #F3F4F6; padding: 1px 6px; border-radius: 3px; font-size: 11px; color: #0D6E6E; }
.card-category { font-size: 11px; }
.separator { color: #D1D5DB; }

.card-detail { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #FFF7ED; border-radius: 6px; margin-bottom: 8px; }
.detail-label { font-size: 11px; color: #888; }
.detail-extra { font-family: 'JetBrains Mono', monospace; font-size: 13px; color: #C2410C; font-weight: 500; }

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
.dialog-header h3 { font-family: 'Inter', sans-serif; font-size: 18px; font-weight: 600; color: #1A1A1A; margin: 0; }
.dialog-close { width: 32px; height: 32px; border: none; background: #F3F4F6; border-radius: 8px; cursor: pointer; display: flex; align-items: center; justify-content: center; color: #666; }

.dialog-body { padding: 20px 24px; }
.form-group { margin-bottom: 16px; }
.form-group.half { flex: 1; }
.form-label { display: block; font-family: 'Inter', sans-serif; font-size: 13px; font-weight: 500; color: #374151; margin-bottom: 6px; }
.form-input, .form-select, .form-textarea {
  width: 100%; padding: 10px 12px; border: 1px solid #E5E5E5; border-radius: 8px;
  font-family: 'Inter', sans-serif; font-size: 14px; color: #1A1A1A;
  background: #FFFFFF; box-sizing: border-box;
}
.form-input:focus, .form-select:focus, .form-textarea:focus { outline: none; border-color: #0D6E6E; }
.form-input:disabled { background: #F9FAFB; color: #9CA3AF; }
.form-textarea { resize: vertical; }
.form-row { display: flex; gap: 12px; }

.dialog-footer { display: flex; gap: 12px; justify-content: flex-end; padding: 0 24px 20px; }
.btn-cancel { padding: 10px 20px; background: #F3F4F6; color: #374151; border: none; border-radius: 8px; font-family: 'Inter', sans-serif; font-size: 14px; cursor: pointer; }
.btn-save { padding: 10px 20px; background: #0D6E6E; color: #FFFFFF; border: none; border-radius: 8px; font-family: 'Inter', sans-serif; font-size: 14px; font-weight: 500; cursor: pointer; }
.btn-save:hover { background: #0A5C5C; }

@media (max-width: 1024px) {
  .pc-dict { display: none; }
}
</style>
