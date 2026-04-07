<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { showToast, showDialog } from 'vant'
import { getUsers, createUser, updateUser, deleteUser } from '@/api/users'
import { usePermission, Permission } from '@/composables/usePermission'
import type { User } from '@/types'

const userStore = useUserStore()
const { hasPermission } = usePermission()

// 用户列表
const users = ref<User[]>([])

const loading = ref(false)
const searchKeyword = ref('')
const roleFilter = ref('')
const statusFilter = ref('')
const showModal = ref(false)
const editingUser = ref<User | null>(null)

const formData = ref({
  username: '',
  nickname: '',
  email: '',
  phone: '',
  password: '',
  role: 'VIEWER' as 'ADMIN' | 'EDITOR' | 'VIEWER',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE'
})

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    const response = await getUsers()
    if (response.data) {
      users.value = response.data
    }
  } catch (error: any) {
    showToast(error.message || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 过滤后的用户列表
const filteredUsers = computed(() => {
  return users.value.filter(user => {
    const matchKeyword = !searchKeyword.value ||
      user.username?.includes(searchKeyword.value) ||
      user.nickname?.includes(searchKeyword.value)
    const matchRole = !roleFilter.value || user.role === roleFilter.value
    const matchStatus = !statusFilter.value || user.status === statusFilter.value
    return matchKeyword && matchRole && matchStatus
  })
})

// 重置表单
const resetForm = () => {
  formData.value = {
    username: '',
    nickname: '',
    email: '',
    phone: '',
    password: '',
    role: 'VIEWER',
    status: 'ACTIVE'
  }
  editingUser.value = null
}

// 打开新增模态框
const handleCreate = () => {
  resetForm()
  showModal.value = true
}

// 打开编辑模态框
const handleEdit = (user: User) => {
  editingUser.value = user
  formData.value = {
    username: user.username || '',
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || '',
    password: '',
    role: user.role,
    status: user.status
  }
  showModal.value = true
}

// 保存用户
const handleSave = async () => {
  if (!formData.value.username || !formData.value.nickname || !formData.value.role) {
    showToast('请填写必填项')
    return
  }

  if (!editingUser.value && !formData.value.password) {
    showToast('请设置初始密码')
    return
  }

  loading.value = true
  try {
    if (editingUser.value) {
      // 更新用户
      await updateUser(editingUser.value.id, {
        nickname: formData.value.nickname,
        email: formData.value.email,
        phone: formData.value.phone,
        role: formData.value.role,
        status: formData.value.status
      })
      showToast('更新成功')
      await loadUsers()
    } else {
      // 新增用户
      await createUser({
        username: formData.value.username,
        password: formData.value.password,
        nickname: formData.value.nickname,
        email: formData.value.email,
        phone: formData.value.phone,
        role: formData.value.role
      })
      showToast('创建成功')
      await loadUsers()
    }

    showModal.value = false
  } catch (error: any) {
    showToast(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

// 删除用户
const handleDelete = (user: User) => {
  showDialog({
    title: '确认删除',
    message: `确定要删除用户"${user.nickname}"吗？此操作不可恢复。`,
  }).then(async () => {
    loading.value = true
    try {
      await deleteUser(user.id)
      showToast('删除成功')
      await loadUsers()
    } catch (error: any) {
      showToast(error.message || '删除失败')
    } finally {
      loading.value = false
    }
  }).catch(() => {})
}

// 切换用户状态
const toggleStatus = async (user: User) => {
  loading.value = true
  try {
    const newStatus = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    await updateUser(user.id, { status: newStatus })
    showToast('状态更新成功')
    await loadUsers()
  } catch (error: any) {
    showToast(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

// 获取角色标签样式
const getRoleClass = (role: string) => {
  const map: Record<string, string> = {
    ADMIN: 'admin',
    EDITOR: 'editor',
    VIEWER: 'viewer'
  }
  return map[role] || 'viewer'
}

// 获取角色名称
const getRoleName = (role: string) => {
  const map: Record<string, string> = {
    ADMIN: '管理员',
    EDITOR: '编辑者',
    VIEWER: '查看者'
  }
  return map[role] || role
}

// 获取状态名称
const getStatusName = (status: string) => {
  return status === 'ACTIVE' ? '启用' : '禁用'
}

onMounted(() => {
  // 检查权限 - 已在路由守卫中检查，但作为额外保障
  if (!userStore.isAdmin) {
    window.location.href = '/#/home'
    return
  }
  loadUsers()
})
</script>

<template>
  <div class="user-management-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">用户角色管理</h1>
        <p class="page-subtitle">管理系统用户和角色权限</p>
      </div>
      <button class="btn btn-primary" @click="handleCreate" v-if="hasPermission(Permission.USER_CREATE)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新增用户
      </button>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-section">
      <div class="filter-grid">
        <div class="filter-item">
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索用户名或昵称..."
            class="input"
          />
        </div>
        <div class="filter-item">
          <select v-model="roleFilter" class="input">
            <option value="">全部角色</option>
            <option value="ADMIN">管理员</option>
            <option value="EDITOR">编辑者</option>
            <option value="VIEWER">查看者</option>
          </select>
        </div>
        <div class="filter-item">
          <select v-model="statusFilter" class="input">
            <option value="">全部状态</option>
            <option value="ACTIVE">启用</option>
            <option value="INACTIVE">禁用</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 用户列表 -->
    <div class="content-card">
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>用户</th>
              <th class="hidden-mobile">邮箱</th>
              <th>角色</th>
              <th>状态</th>
              <th class="hidden-mobile">创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading && users.length === 0">
              <td colspan="6" class="text-center py-10">
                <div class="loading-spinner small"></div>
                <span class="text-gray-500">加载中...</span>
              </td>
            </tr>
            <tr v-else-if="filteredUsers.length === 0">
              <td colspan="6" class="text-center py-10">
                <div class="empty-state">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                  </svg>
                  <p>暂无用户数据</p>
                </div>
              </td>
            </tr>
            <tr v-else v-for="user in filteredUsers" :key="user.id" class="table-row">
              <td>
                <div class="user-cell">
                  <div class="user-avatar-small" :class="user.role.toLowerCase()">
                    {{ user.nickname.charAt(0) }}
                  </div>
                  <div class="user-info-cell">
                    <div class="user-name-cell">{{ user.nickname }}</div>
                    <div class="user-username-cell">{{ user.username }}</div>
                  </div>
                </div>
              </td>
              <td class="hidden-mobile">
                <span class="text-gray-600">{{ user.email || '-' }}</span>
              </td>
              <td>
                <span class="role-badge" :class="getRoleClass(user.role)">
                  {{ getRoleName(user.role) }}
                </span>
              </td>
              <td>
                <button
                  class="status-toggle"
                  :class="user.status.toLowerCase()"
                  @click="toggleStatus(user)"
                >
                  <span class="status-dot"></span>
                  {{ getStatusName(user.status) }}
                </button>
              </td>
              <td class="hidden-mobile">
                <span class="text-gray-500 text-sm">{{ user.createdTime }}</span>
              </td>
              <td>
                <div class="actions-cell">
                  <button class="action-btn edit" @click="handleEdit(user)" v-if="hasPermission(Permission.USER_EDIT)">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                    编辑
                  </button>
                  <button
                    v-if="user.id !== 1 && hasPermission(Permission.USER_DELETE)"
                    class="action-btn delete"
                    @click="handleDelete(user)"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                    删除
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 统计信息 -->
      <div class="table-footer">
        <span class="text-gray-500">
          共 {{ filteredUsers.length }} 条记录
        </span>
      </div>
    </div>

    <!-- 新增/编辑模态框 -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2 class="modal-title">{{ editingUser ? '编辑用户' : '新增用户' }}</h2>
          <button class="modal-close" @click="showModal = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        <div class="modal-body">
          <div class="form-grid">
            <div class="form-group">
              <label class="form-label">用户名 <span class="required">*</span></label>
              <input
                v-model="formData.username"
                type="text"
                class="input"
                :disabled="!!editingUser"
                placeholder="请输入用户名"
              />
            </div>
            <div class="form-group">
              <label class="form-label">昵称 <span class="required">*</span></label>
              <input
                v-model="formData.nickname"
                type="text"
                class="input"
                placeholder="请输入昵称"
              />
            </div>
            <div class="form-group">
              <label class="form-label">邮箱</label>
              <input
                v-model="formData.email"
                type="email"
                class="input"
                placeholder="请输入邮箱"
              />
            </div>
            <div class="form-group">
              <label class="form-label">手机号</label>
              <input
                v-model="formData.phone"
                type="tel"
                class="input"
                placeholder="请输入手机号"
              />
            </div>
            <div class="form-group" v-if="!editingUser">
              <label class="form-label">初始密码 <span class="required">*</span></label>
              <input
                v-model="formData.password"
                type="password"
                class="input"
                placeholder="请设置初始密码"
              />
            </div>
            <div class="form-group">
              <label class="form-label">角色 <span class="required">*</span></label>
              <select v-model="formData.role" class="input">
                <option value="ADMIN">管理员</option>
                <option value="EDITOR">编辑者</option>
                <option value="VIEWER">查看者</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">状态</label>
              <select v-model="formData.status" class="input">
                <option value="ACTIVE">启用</option>
                <option value="INACTIVE">禁用</option>
              </select>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-outline" @click="showModal = false" :disabled="loading">
            取消
          </button>
          <button class="btn btn-primary" @click="handleSave" :disabled="loading">
            {{ loading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-management-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* 页面头部 */
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

/* 筛选区域 */
.filter-section {
  background: white;
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-md);
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-md);
}

.filter-item {
  display: flex;
}

/* 内容卡片 */
.content-card {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

/* 表格 */
.table-container {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table thead {
  background: var(--gray-50);
}

.data-table th {
  padding: var(--spacing-md);
  text-align: left;
  font-weight: 600;
  font-size: 0.8125rem;
  color: var(--gray-600);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--gray-200);
}

.data-table td {
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--gray-100);
}

.table-row {
  transition: background-color var(--transition-fast);
}

.table-row:hover {
  background-color: var(--gray-50);
}

/* 用户单元格 */
.user-cell {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.user-avatar-small {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 0.875rem;
}

.user-avatar-small.admin {
  background: var(--gradient-primary);
}

.user-avatar-small.editor {
  background: var(--gradient-warning);
}

.user-avatar-small.viewer {
  background: var(--gradient-success);
}

.user-info-cell {
  display: flex;
  flex-direction: column;
}

.user-name-cell {
  font-weight: 600;
  color: var(--gray-900);
  font-size: 0.875rem;
}

.user-username-cell {
  font-size: 0.75rem;
  color: var(--gray-500);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

/* 角色标签 */
.role-badge {
  display: inline-block;
  padding: 0.25rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.role-badge.admin {
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.role-badge.editor {
  background: rgba(245, 158, 11, 0.1);
  color: var(--warning-color);
}

.role-badge.viewer {
  background: rgba(16, 185, 129, 0.1);
  color: var(--success-color);
}

/* 状态切换 */
.status-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.375rem 0.75rem;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.status-toggle.active {
  background: rgba(16, 185, 129, 0.1);
  color: var(--success-color);
}

.status-toggle.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: var(--error-color);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

/* 操作按钮 */
.actions-cell {
  display: flex;
  gap: var(--spacing-xs);
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.375rem 0.625rem;
  border-radius: var(--radius);
  font-size: 0.75rem;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-btn.edit {
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.action-btn.edit:hover {
  background: rgba(99, 102, 241, 0.15);
}

.action-btn.delete {
  background: rgba(239, 68, 68, 0.1);
  color: var(--error-color);
}

.action-btn.delete:hover {
  background: rgba(239, 68, 68, 0.15);
}

/* 表格底部 */
.table-footer {
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--gray-100);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 加载和空状态 */
.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--gray-200);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto var(--spacing-md);
}

.loading-spinner.small {
  width: 24px;
  height: 24px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--gray-500);
}

.empty-state svg {
  width: 64px;
  height: 64px;
  margin-bottom: var(--spacing-md);
  color: var(--gray-300);
}

.text-center {
  text-align: center;
}

.text-gray-500 {
  color: var(--gray-500);
}

.text-gray-600 {
  color: var(--gray-600);
}

.text-sm {
  font-size: 0.8125rem;
}

.py-10 {
  padding: 2.5rem 0;
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
  animation: fadeIn 0.2s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.modal-content {
  background: white;
  border-radius: var(--radius-xl);
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  overflow: auto;
  box-shadow: var(--shadow-xl);
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
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
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--gray-500);
  cursor: pointer;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.modal-close:hover {
  background: var(--gray-100);
  color: var(--gray-700);
}

.modal-body {
  padding: var(--spacing-xl);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-sm);
  padding: var(--spacing-lg) var(--spacing-xl);
  border-top: 1px solid var(--gray-200);
}

/* 表单 */
.form-grid {
  display: grid;
  gap: var(--spacing-lg);
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

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 0.375rem;
  padding: 0.625rem 1.25rem;
  border-radius: var(--radius);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  border: none;
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

.btn-outline:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }

  .table-container {
    overflow-x: scroll;
  }

  .modal-overlay {
    padding: var(--spacing-md);
    align-items: flex-end;
  }

  .modal-content {
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    max-height: 80vh;
  }

  .modal-header,
  .modal-body,
  .modal-footer {
    padding-left: var(--spacing-lg);
    padding-right: var(--spacing-lg);
  }
}
</style>
