<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { showToast, showDialog } from 'vant'
import { getRoles, createRole, updateRole, deleteRole, getRolePermissionIds, assignRolePermissions } from '@/api/roles'
import { getPermissionTree } from '@/api/permissions'
import { usePermission, Permission } from '@/composables/usePermission'
import { getStatusLabel, getDictOptions, loadAllDicts } from '@/composables/useDict'
import type { SysRole, SysPermission } from '@/types'

const { hasPermission } = usePermission()

// 角色列表
const roles = ref<SysRole[]>([])
// 权限树
const permissions = ref<SysPermission[]>([])
const loading = ref(false)
const showModal = ref(false)
const showPermissionModal = ref(false)
const editingRole = ref<SysRole | null>(null)
const editingRoleId = ref<number | null>(null)
const selectedPermissionIds = ref<number[]>([])

const formData = ref({
  roleCode: '',
  roleName: '',
  description: '',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE'
})

const statusOptions = computed(() => getDictOptions('common_status'))

// 加载角色列表
const loadRoles = async () => {
  loading.value = true
  try {
    const response = await getRoles()
    if (response.data) {
      roles.value = response.data
    }
  } catch (error: any) {
    showToast(error.message || '加载角色列表失败')
  } finally {
    loading.value = false
  }
}

// 加载权限树
const loadPermissions = async () => {
  try {
    const response = await getPermissionTree()
    if (response.data) {
      permissions.value = response.data
    }
  } catch (error: any) {
    console.error('加载权限树失败:', error)
  }
}

// 获取角色的权限数量
const getRolePermissionCount = async (roleId: number): Promise<number> => {
  try {
    const response = await getRolePermissionIds(roleId)
    return response.data?.length || 0
  } catch {
    return 0
  }
}

// 角色权限数量映射
const rolePermissionCounts = ref<Map<number, number>>(new Map())

// 加载所有角色的权限数量
const loadRolePermissionCounts = async () => {
  for (const role of roles.value) {
    const count = await getRolePermissionCount(role.id)
    rolePermissionCounts.value.set(role.id, count)
  }
}

// 重置表单
const resetForm = () => {
  formData.value = {
    roleCode: '',
    roleName: '',
    description: '',
    status: 'ACTIVE'
  }
  editingRole.value = null
}

// 打开新增模态框
const handleCreate = () => {
  resetForm()
  showModal.value = true
}

// 打开编辑模态框
const handleEdit = (role: SysRole) => {
  editingRole.value = role
  formData.value = {
    roleCode: role.roleCode,
    roleName: role.roleName,
    description: role.description || '',
    status: role.status as 'ACTIVE' | 'INACTIVE'
  }
  showModal.value = true
}

// 保存角色
const handleSave = async () => {
  if (!formData.value.roleCode || !formData.value.roleName) {
    showToast('请填写必填项')
    return
  }

  loading.value = true
  try {
    if (editingRole.value) {
      await updateRole(editingRole.value.id, {
        roleName: formData.value.roleName,
        description: formData.value.description,
        status: formData.value.status
      })
      showToast('更新成功')
    } else {
      await createRole({
        roleCode: formData.value.roleCode,
        roleName: formData.value.roleName,
        description: formData.value.description,
        status: formData.value.status,
        isSystem: false,
        sortOrder: roles.value.length + 1
      })
      showToast('创建成功')
    }
    showModal.value = false
    await loadRoles()
    await loadRolePermissionCounts()
  } catch (error: any) {
    showToast(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

// 删除角色
const handleDelete = (role: SysRole) => {
  if (role.isSystem) {
    showToast('系统内置角色不能删除')
    return
  }
  showDialog({
    title: '确认删除',
    message: `确定要删除角色"${role.roleName}"吗？此操作不可恢复。`
  }).then(async () => {
    loading.value = true
    try {
      await deleteRole(role.id)
      showToast('删除成功')
      await loadRoles()
      await loadRolePermissionCounts()
    } catch (error: any) {
      showToast(error.message || '删除失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

// 打开权限配置模态框
const handleConfigPermissions = async (role: SysRole) => {
  editingRoleId.value = role.id
  // 加载当前角色的权限
  try {
    const response = await getRolePermissionIds(role.id)
    selectedPermissionIds.value = response.data || []
  } catch (error: any) {
    selectedPermissionIds.value = []
  }
  showPermissionModal.value = true
}

// 切换权限选择（支持父子联动）
const togglePermissionSelection = (permissionId: number, isParent: boolean = false, children?: SysPermission[]) => {
  const index = selectedPermissionIds.value.indexOf(permissionId)

  if (isParent && children && children.length > 0) {
    // 父权限切换：联动子权限
    const childIds = children.map(c => c.id)
    if (index > -1) {
      // 取消父权限时，同时取消所有子权限
      selectedPermissionIds.value = selectedPermissionIds.value.filter(id => id !== permissionId && !childIds.includes(id))
    } else {
      // 选中父权限时，同时选中所有子权限
      const newIds = new Set(selectedPermissionIds.value)
      newIds.add(permissionId)
      childIds.forEach(id => newIds.add(id))
      selectedPermissionIds.value = Array.from(newIds)
    }
  } else {
    // 单个权限切换
    if (index > -1) {
      selectedPermissionIds.value.splice(index, 1)
    } else {
      selectedPermissionIds.value.push(permissionId)
    }
  }
}

// 检查父权限是否半选（部分子权限选中）
const isParentIndeterminate = (_parentId: number, children?: SysPermission[]): boolean => {
  if (!children || children.length === 0) return false
  const childIds = children.map(c => c.id)
  const selectedChildCount = childIds.filter(id => selectedPermissionIds.value.includes(id)).length
  // 半选：部分选中但不是全选
  return selectedChildCount > 0 && selectedChildCount < children.length
}

// 全选/取消全选
const toggleSelectAll = () => {
  const allIds = getAllPermissionIds(permissions.value)
  if (selectedPermissionIds.value.length === allIds.length) {
    selectedPermissionIds.value = []
  } else {
    selectedPermissionIds.value = allIds
  }
}

// 获取所有权限ID
const getAllPermissionIds = (perms: SysPermission[]): number[] => {
  const ids: number[] = []
  for (const perm of perms) {
    ids.push(perm.id)
    if (perm.children) {
      ids.push(...getAllPermissionIds(perm.children))
    }
  }
  return ids
}

// 保存权限配置
const savePermissions = async () => {
  if (!editingRoleId.value) return
  loading.value = true
  try {
    await assignRolePermissions(editingRoleId.value, selectedPermissionIds.value)
    showToast('权限配置成功')
    rolePermissionCounts.value.set(editingRoleId.value, selectedPermissionIds.value.length)
    showPermissionModal.value = false
  } catch (error: any) {
    showToast(error.message || '权限配置失败')
  } finally {
    loading.value = false
  }
}

// 获取角色样式
const getRoleClass = (role: SysRole) => {
  if (role.roleCode === 'ADMIN') return 'admin'
  if (role.roleCode === 'EDITOR') return 'editor'
  if (role.roleCode === 'VIEWER') return 'viewer'
  return 'custom'
}

onMounted(async () => {
  loadAllDicts()
  loadPermissions()
  await loadRoles()
  await loadRolePermissionCounts()
})
</script>

<template>
  <div class="role-management-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">角色管理</h1>
        <p class="page-subtitle">管理系统角色和权限配置</p>
      </div>
      <button class="btn btn-primary" @click="handleCreate" v-if="hasPermission(Permission.USER_CREATE)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新增角色
      </button>
    </div>

    <!-- 角色列表 -->
    <div class="content-card">
      <div class="role-grid">
        <div v-if="loading && roles.length === 0" class="loading-state">
          <div class="loading-spinner"></div>
          <span>加载中...</span>
        </div>

        <div v-else-if="roles.length === 0" class="empty-state">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <p>暂无角色数据</p>
          <button class="btn btn-primary" @click="handleCreate">创建角色</button>
        </div>

        <div
          v-else
          v-for="role in roles"
          :key="role.id"
          class="role-card"
          :class="getRoleClass(role)"
        >
          <div class="role-header">
            <span class="role-badge" :class="getRoleClass(role)">
              {{ role.roleCode }}
            </span>
            <span v-if="role.isSystem" class="system-badge">系统</span>
          </div>
          <div class="role-body">
            <h3 class="role-name">{{ role.roleName }}</h3>
            <p class="role-desc">{{ role.description || '暂无描述' }}</p>
            <div class="role-stats">
              <span class="stat-item">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                </svg>
                {{ rolePermissionCounts.get(role.id) || 0 }} 个权限
              </span>
              <span class="stat-item" :class="role.status.toLowerCase()">
                {{ getStatusLabel(role.status) }}
              </span>
            </div>
          </div>
          <div class="role-actions">
            <button class="action-btn permission" @click="handleConfigPermissions(role)" title="配置权限">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
                <path d="M9 12l2 2 4-4"/>
              </svg>
              权限
            </button>
            <button class="action-btn edit" @click="handleEdit(role)" title="编辑">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
              编辑
            </button>
            <button
              v-if="!role.isSystem"
              class="action-btn delete"
              @click="handleDelete(role)"
              title="删除"
            >
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="3 6 5 6 21 6"/>
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
              </svg>
              删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 新增/编辑角色模态框 -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2 class="modal-title">{{ editingRole ? '编辑角色' : '新增角色' }}</h2>
          <button class="modal-close" @click="showModal = false">×</button>
        </div>

        <div class="modal-body">
          <div class="form-group">
            <label class="form-label">角色编码 <span class="required">*</span></label>
            <input
              v-model="formData.roleCode"
              type="text"
              class="input"
              :disabled="!!editingRole"
              placeholder="如：MANAGER、OPERATOR"
            />
          </div>
          <div class="form-group">
            <label class="form-label">角色名称 <span class="required">*</span></label>
            <input
              v-model="formData.roleName"
              type="text"
              class="input"
              placeholder="请输入角色名称"
            />
          </div>
          <div class="form-group">
            <label class="form-label">角色描述</label>
            <textarea
              v-model="formData.description"
              class="input textarea"
              placeholder="请输入角色描述"
              rows="3"
            ></textarea>
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

    <!-- 权限配置模态框 -->
    <div v-if="showPermissionModal" class="modal-overlay" @click.self="showPermissionModal = false">
      <div class="modal-content permission-modal">
        <div class="modal-header">
          <h2 class="modal-title">配置权限</h2>
          <button class="modal-close" @click="showPermissionModal = false">×</button>
        </div>

        <div class="modal-body">
          <div class="permission-header">
            <button class="btn btn-outline btn-sm" @click="toggleSelectAll">
              {{ selectedPermissionIds.length === getAllPermissionIds(permissions).length ? '取消全选' : '全选' }}
            </button>
            <span class="selected-count">已选择 {{ selectedPermissionIds.length }} 个权限</span>
          </div>

          <div class="permission-list">
            <div v-for="perm in permissions" :key="perm.id" class="permission-group">
              <div
                class="permission-item parent"
                :class="{ selected: selectedPermissionIds.includes(perm.id), indeterminate: isParentIndeterminate(perm.id, perm.children) }"
                @click="togglePermissionSelection(perm.id, true, perm.children)"
              >
                <div class="permission-checkbox">
                  <svg v-if="selectedPermissionIds.includes(perm.id)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
                <span class="permission-name">{{ perm.permissionName }}</span>
                <span class="permission-code">{{ perm.permissionCode }}</span>
              </div>
              <div v-if="perm.children" class="permission-children">
                <div
                  v-for="child in perm.children"
                  :key="child.id"
                  class="permission-item child"
                  :class="{ selected: selectedPermissionIds.includes(child.id) }"
                  @click="togglePermissionSelection(child.id)"
                >
                  <div class="permission-checkbox">
                    <svg v-if="selectedPermissionIds.includes(child.id)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                      <polyline points="20 6 9 17 4 12"/>
                    </svg>
                  </div>
                  <span class="permission-name">{{ child.permissionName }}</span>
                  <span class="permission-code">{{ child.permissionCode }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-outline" @click="showPermissionModal = false" :disabled="loading">取消</button>
          <button class="btn btn-primary" @click="savePermissions" :disabled="loading">
            {{ loading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.role-management-page {
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

.content-card {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  padding: var(--spacing-lg);
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--spacing-lg);
}

.role-card {
  border: 2px solid var(--gray-200);
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  transition: all var(--transition-fast);
}

.role-card:hover {
  border-color: var(--gray-300);
  box-shadow: var(--shadow-md);
}

.role-card.admin {
  border-color: rgba(99, 102, 241, 0.3);
}

.role-card.editor {
  border-color: rgba(245, 158, 11, 0.3);
}

.role-card.viewer {
  border-color: rgba(16, 185, 129, 0.3);
}

.role-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.role-badge {
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.role-badge.admin {
  background: rgba(99, 102, 241, 0.15);
  color: var(--primary-color);
}

.role-badge.editor {
  background: rgba(245, 158, 11, 0.15);
  color: var(--warning-color);
}

.role-badge.viewer {
  background: rgba(16, 185, 129, 0.15);
  color: var(--success-color);
}

.role-badge.custom {
  background: rgba(107, 114, 128, 0.15);
  color: var(--gray-600);
}

.system-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.625rem;
  font-weight: 600;
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.role-body {
  margin-bottom: var(--spacing-md);
}

.role-name {
  font-size: 1.125rem;
  font-weight: 700;
  color: var(--gray-900);
  margin-bottom: 0.25rem;
}

.role-desc {
  font-size: 0.875rem;
  color: var(--gray-500);
  margin-bottom: var(--spacing-md);
}

.role-stats {
  display: flex;
  gap: var(--spacing-md);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.75rem;
  color: var(--gray-600);
}

.stat-item svg {
  width: 16px;
  height: 16px;
}

.stat-item.active {
  color: var(--success-color);
}

.stat-item.inactive {
  color: var(--error-color);
}

.role-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius);
  font-size: 0.75rem;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

.action-btn.permission {
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.action-btn.edit {
  background: rgba(245, 158, 11, 0.1);
  color: var(--warning-color);
}

.action-btn.delete {
  background: rgba(239, 68, 68, 0.1);
  color: var(--error-color);
}

.action-btn:hover {
  opacity: 0.8;
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

.permission-modal {
  max-width: 600px;
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

.textarea {
  resize: vertical;
  min-height: 80px;
}

/* 权限配置 */
.permission-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--spacing-md);
}

.selected-count {
  font-size: 0.875rem;
  color: var(--gray-600);
}

.permission-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  max-height: 400px;
  overflow-y: auto;
}

.permission-group {
  border: 1px solid var(--gray-200);
  border-radius: var(--radius);
}

.permission-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.permission-item:hover {
  background: var(--gray-50);
}

.permission-item.selected {
  background: rgba(99, 102, 241, 0.05);
}

.permission-item.parent {
  background: var(--gray-50);
}

.permission-checkbox {
  width: 20px;
  height: 20px;
  border: 2px solid var(--gray-300);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
}

.permission-item.selected .permission-checkbox {
  border-color: var(--primary-color);
  background: var(--primary-color);
}

.permission-item.indeterminate .permission-checkbox {
  border-color: var(--primary-color);
  background: white;
  position: relative;
}

.permission-item.indeterminate .permission-checkbox::after {
  content: '';
  position: absolute;
  width: 10px;
  height: 3px;
  background: var(--primary-color);
  border-radius: 1px;
}

.permission-checkbox svg {
  width: 14px;
  height: 14px;
  color: white;
}

.permission-name {
  font-weight: 600;
  color: var(--gray-900);
}

.permission-code {
  font-size: 0.75rem;
  color: var(--gray-500);
}

.permission-children {
  border-top: 1px solid var(--gray-200);
}

.permission-item.child {
  padding-left: calc(var(--spacing-md) + 24px);
}

/* 按钮 */
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

.btn-sm {
  padding: 0.375rem 0.75rem;
  font-size: 0.75rem;
}

.btn-primary {
  background: var(--gradient-primary);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
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

.btn-outline:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .role-grid {
    grid-template-columns: 1fr;
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