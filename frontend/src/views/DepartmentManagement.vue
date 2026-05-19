<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { showToast, showDialog } from 'vant'
import {
  getDepartmentTree,
  createDepartment,
  updateDepartment,
  moveDepartment,
  deleteDepartment
} from '@/api/departments'
import { getAllUsers } from '@/api/users'
import { usePermission, Permission } from '@/composables/usePermission'
import { getDictOptions, loadAllDicts, getDeptTypeOptions } from '@/composables/useDict'
import DeptTreeNode from '@/components/DeptTreeNode.vue'
import type { Department, User } from '@/types'

const { hasPermission } = usePermission()
const canEdit = computed(() => hasPermission(Permission.DEPT_EDIT))

// 部门树数据
const departmentTree = ref<Department[]>([])
// 用户列表（用于负责人选择）
const users = ref<User[]>([])
const loading = ref(false)
const expandedIds = ref<Set<number>>(new Set())
const selectedDept = ref<Department | null>(null)
const showModal = ref(false)
const editingDept = ref<Department | null>(null)

// 表单数据
const formData = ref({
  deptCode: '',
  deptName: '',
  deptType: 'DEPARTMENT' as 'HEADQUARTERS' | 'COMPANY' | 'DEPARTMENT',
  parentId: null as number | null,
  leaderId: null as number | null,
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE'
})

// 部门类型选项（从字典获取）
const deptTypeOptions = computed(() => getDeptTypeOptions())

const statusOptions = computed(() => getDictOptions('common_status'))

// 用户选项（用于负责人选择）
const userOptions = computed(() => {
  return users.value
    .filter(u => u.status === 'ACTIVE')
    .map(u => ({ value: u.id, label: u.nickname || u.username }))
})

// 加载用户列表
const loadUsers = async () => {
  try {
    const response = await getAllUsers()
    if (response.data) {
      users.value = response.data
    }
  } catch (error: any) {
    console.error('加载用户列表失败:', error)
  }
}

// 加载部门树
const loadDepartmentTree = async () => {
  loading.value = true
  try {
    const response = await getDepartmentTree()
    if (response.data) {
      departmentTree.value = response.data
      // 默认展开所有
      expandAll(response.data)
    }
  } catch (error: any) {
    showToast(error.message || '加载部门树失败')
  } finally {
    loading.value = false
  }
}

// 展开所有节点
const expandAll = (depts: Department[]) => {
  for (const dept of depts) {
    expandedIds.value.add(dept.id)
    if (dept.children && dept.children.length > 0) {
      expandAll(dept.children)
    }
  }
}

// 切换展开状态
const toggleExpand = (dept: Department) => {
  if (expandedIds.value.has(dept.id)) {
    expandedIds.value.delete(dept.id)
  } else {
    expandedIds.value.add(dept.id)
  }
}

// 选择部门
const selectDept = (dept: Department) => {
  selectedDept.value = dept
}

// 重置表单
const resetForm = () => {
  formData.value = {
    deptCode: '',
    deptName: '',
    deptType: 'DEPARTMENT',
    parentId: null,
    leaderId: null,
    status: 'ACTIVE'
  }
  editingDept.value = null
}

// 打开新增模态框
const handleCreate = (parentDept?: Department) => {
  resetForm()
  if (parentDept) {
    formData.value.parentId = parentDept.id
    // 根据父部门类型设置默认类型
    if (parentDept.deptType === 'HEADQUARTERS') {
      formData.value.deptType = 'COMPANY'
    } else if (parentDept.deptType === 'COMPANY') {
      formData.value.deptType = 'DEPARTMENT'
    }
  }
  showModal.value = true
}

// 打开编辑模态框
const handleEdit = (dept: Department) => {
  editingDept.value = dept
  formData.value = {
    deptCode: dept.deptCode,
    deptName: dept.deptName,
    deptType: dept.deptType,
    parentId: dept.parentId,
    leaderId: dept.leaderId || null,
    status: dept.status as 'ACTIVE' | 'INACTIVE'
  }
  showModal.value = true
}

// 保存部门
const handleSave = async () => {
  if (!formData.value.deptCode || !formData.value.deptName) {
    showToast('请填写必填项')
    return
  }

  loading.value = true
  try {
    if (editingDept.value) {
      await updateDepartment(editingDept.value.id, {
        deptName: formData.value.deptName,
        deptType: formData.value.deptType,
        leaderId: formData.value.leaderId ?? undefined,
        status: formData.value.status
      })
      showToast('更新成功')
    } else {
      await createDepartment({
        deptCode: formData.value.deptCode,
        deptName: formData.value.deptName,
        deptType: formData.value.deptType,
        parentId: formData.value.parentId,
        leaderId: formData.value.leaderId ?? undefined,
        status: formData.value.status
      })
      showToast('创建成功')
    }
    showModal.value = false
    await loadDepartmentTree()
  } catch (error: any) {
    showToast(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

// 删除部门
const handleDelete = (dept: Department) => {
  if (dept.children && dept.children.length > 0) {
    showToast('请先删除子部门')
    return
  }
  if (dept.userCount && dept.userCount > 0) {
    showToast('该部门下还有用户，请先转移用户')
    return
  }

  showDialog({
    title: '确认删除',
    message: `确定要删除部门"${dept.deptName}"吗？此操作不可恢复。`
  }).then(async () => {
    loading.value = true
    try {
      await deleteDepartment(dept.id)
      showToast('删除成功')
      selectedDept.value = null
      await loadDepartmentTree()
    } catch (error: any) {
      showToast(error.message || '删除失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

// 拖拽相关
const draggedDept = ref<Department | null>(null)
const dragOverDept = ref<Department | null>(null)

const handleDragStart = (dept: Department) => {
  draggedDept.value = dept
}

const handleDragOver = (e: DragEvent, dept: Department) => {
  e.preventDefault()
  dragOverDept.value = dept
}

const handleDrop = async (e: DragEvent, targetDept: Department | null) => {
  e.preventDefault()
  if (!draggedDept.value) return

  // 不能拖到自己或自己的子部门
  if (targetDept && (targetDept.id === draggedDept.value.id ||
      (targetDept.path && draggedDept.value.path &&
       targetDept.path.startsWith(draggedDept.value.path + '/')))) {
    showToast('不能移动到该位置')
    draggedDept.value = null
    dragOverDept.value = null
    return
  }

  try {
    await moveDepartment(draggedDept.value.id, targetDept?.id || null)
    showToast('移动成功')
    await loadDepartmentTree()
  } catch (error: any) {
    showToast(error.message || '移动失败')
  } finally {
    draggedDept.value = null
    dragOverDept.value = null
  }
}

const handleDropToRoot = (e: DragEvent) => {
  handleDrop(e, null)
}

onMounted(() => {
  loadAllDicts()
  loadUsers()
  loadDepartmentTree()
})
</script>

<template>
  <div class="department-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">部门管理</h1>
        <p class="page-subtitle">管理组织架构，支持拖拽调整层级</p>
      </div>
      <button class="btn btn-primary" @click="handleCreate()" v-if="hasPermission(Permission.DEPT_EDIT)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新增部门
      </button>
    </div>

    <!-- 部门树 -->
    <div class="tree-container" @drop="handleDropToRoot" @dragover.prevent>
      <div v-if="loading && departmentTree.length === 0" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>

      <div v-else-if="departmentTree.length === 0" class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 9l9-7 9 7v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
        <p>暂无部门数据</p>
        <button class="btn btn-primary" @click="handleCreate()">创建总部</button>
      </div>

      <template v-else>
        <DeptTreeNode
          v-for="dept in departmentTree"
          :key="dept.id"
          :dept="dept"
          :level="0"
          :selected-id="selectedDept?.id || null"
          :expanded-ids="expandedIds"
          :drag-over-id="dragOverDept?.id || null"
          :has-permission="canEdit"
          @select="selectDept"
          @toggle-expand="toggleExpand"
          @create="handleCreate"
          @edit="handleEdit"
          @delete="handleDelete"
          @drag-start="handleDragStart"
          @drag-over="handleDragOver"
          @drop="handleDrop"
        />
      </template>
    </div>

    <!-- 新增/编辑模态框 -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2 class="modal-title">{{ editingDept ? '编辑部门' : '新增部门' }}</h2>
          <button class="modal-close" @click="showModal = false">×</button>
        </div>

        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">部门编码 <span class="required">*</span></label>
            <input
              v-model="formData.deptCode"
              type="text"
              class="input"
              :disabled="!!editingDept"
              placeholder="如：HQ、SUB01、DEPT01"
            />
          </div>
          <div class="form-group">
            <label class="form-label">部门名称 <span class="required">*</span></label>
            <input
              v-model="formData.deptName"
              type="text"
              class="input"
              placeholder="请输入部门名称"
            />
          </div>
          <div class="form-group">
            <label class="form-label">部门类型</label>
            <select v-model="formData.deptType" class="input">
              <option v-for="opt in deptTypeOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">部门负责人</label>
            <select v-model="formData.leaderId" class="input">
              <option :value="null">请选择负责人</option>
              <option v-for="opt in userOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label class="form-label">状态</label>
            <select v-model="formData.status" class="input">
              <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-outline" @click="showModal = false" :disabled="loading">取消</button>
          <button class="btn btn-primary" @click="handleSave" :disabled="loading">
            {{ loading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.department-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--spacing-md);
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gray-900);
  margin-bottom: 0.25rem;
}

.page-subtitle {
  font-size: 0.875rem;
  color: var(--gray-500);
}

.tree-container {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: var(--spacing-md);
  min-height: 400px;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem;
  color: var(--gray-500);
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--gray-200);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 1rem;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state svg {
  width: 64px;
  height: 64px;
  margin-bottom: 1rem;
  color: var(--gray-300);
}

/* 模态框 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: var(--spacing-lg);
}

.modal-content {
  background: white;
  border-radius: var(--radius-xl);
  width: 100%;
  max-width: 480px;
  box-shadow: var(--shadow-xl);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg) var(--spacing-xl);
  border-bottom: 1px solid var(--gray-200);
}

.modal-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--gray-900);
}

.modal-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 1.5rem;
  color: var(--gray-500);
  cursor: pointer;
  border-radius: var(--radius);
}

.modal-close:hover {
  background: var(--gray-100);
}

.modal-body {
  padding: var(--spacing-xl);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg) var(--spacing-xl);
  border-top: 1px solid var(--gray-200);
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.form-label {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--gray-700);
}

.required {
  color: var(--error-color);
}

.input {
  padding: 0.625rem 0.875rem;
  border: 2px solid var(--gray-300);
  border-radius: var(--radius);
  font-size: 0.875rem;
  transition: all var(--transition-fast);
}

.input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.input:disabled {
  background: var(--gray-100);
  cursor: not-allowed;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 1rem;
  border-radius: var(--radius);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  border: none;
}

.btn svg {
  width: 16px;
  height: 16px;
}

.btn-primary {
  background: var(--gradient-primary);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-lg);
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-outline {
  background: transparent;
  color: var(--gray-700);
  border: 2px solid var(--gray-300);
}

.btn-outline:hover:not(:disabled) {
  border-color: var(--gray-400);
  background: var(--gray-50);
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .modal-overlay {
    padding: var(--spacing-md);
    align-items: flex-end;
  }

  .modal-content {
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  }
}
</style>
