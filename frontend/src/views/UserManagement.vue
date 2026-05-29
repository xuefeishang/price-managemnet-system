<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { showToast, showDialog } from 'vant'
import { getUsers, createUser, updateUser, deleteUser, lockUser, unlockUser, getUserRolesBatch, assignUserRoles, importUsers, exportUsers, downloadUserTemplate } from '@/api/users'
import { getActiveRoles } from '@/api/roles'
import { getDepartmentTree } from '@/api/departments'
import { usePermission, Permission } from '@/composables/usePermission'
import { getStatusLabel, getDictOptions, loadAllDicts } from '@/composables/useDict'
import type { User, Department, SysRole } from '@/types'

const userStore = useUserStore()
const { hasPermission } = usePermission()

// 用户列表
const users = ref<User[]>([])
// 部门树
const departmentTree = ref<Department[]>([])
// 角色列表（从sys_role表获取）
const roles = ref<SysRole[]>([])
// 用户角色映射（userId -> roleIds）
const userRolesMap = ref<Map<number, number[]>>(new Map())

const loading = ref(false)
const searchKeyword = ref('')
const roleFilter = ref('')
const statusFilter = ref('')
const deptFilter = ref('')
const showModal = ref(false)
const editingUser = ref<User | null>(null)
const showRoleModal = ref(false)
const editingUserId = ref<number | null>(null)
const selectedRoleIds = ref<number[]>([])
const importing = ref(false)
const exporting = ref(false)
const importResult = ref<{ successCount: number; skipCount: number; errors: string[] } | null>(null)
const showMoreMenu = ref(false)

// 防抖搜索
let searchTimer: ReturnType<typeof setTimeout> | null = null

// 分页
const currentPage = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)
const pageSizeOptions = [20, 30, 50, 100, 150]

const formData = ref({
  username: '',
  employeeId: '',
  nickname: '',
  email: '',
  phone: '',
  department: '',
  deptId: null as number | null,
  password: '',
  role: 'VIEWER' as 'ADMIN' | 'EDITOR' | 'VIEWER',
  status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE'
})

// 加载角色列表
const loadRoles = async () => {
  try {
    const response = await getActiveRoles()
    if (response.data) {
      roles.value = response.data
    }
  } catch (error: any) {
    console.error('加载角色失败:', error)
  }
}

// 加载所有用户的角色（批量接口）
const loadAllUserRoles = async () => {
  if (users.value.length === 0) return
  try {
    const userIds = users.value.map(u => u.id)
    const response = await getUserRolesBatch(userIds)
    if (response.data) {
      // 转换为 Map 格式
      for (const [userId, roleIds] of Object.entries(response.data)) {
        userRolesMap.value.set(Number(userId), roleIds)
      }
    }
  } catch (error: any) {
    console.error('加载用户角色失败:', error)
  }
}

// 获取用户角色标签（用于显示）
const getUserRoleTags = (userId: number): { id: number; name: string; code: string }[] => {
  const roleIds = userRolesMap.value.get(userId) || []
  return roleIds.map(rid => {
    const role = roles.value.find(r => r.id === rid)
    if (role) {
      return { id: role.id, name: role.roleName, code: role.roleCode }
    }
    return { id: rid, name: '', code: '' }
  }).filter(tag => tag.name)
}

// 加载部门树
const loadDepartments = async () => {
  try {
    const response = await getDepartmentTree()
    if (response.data) {
      departmentTree.value = response.data
    }
  } catch (error: any) {
    console.error('加载部门失败:', error)
  }
}

// 获取部门名称
const getDeptName = (deptId: number | null | undefined) => {
  if (!deptId) return '-'
  const findDept = (depts: Department[]): string => {
    for (const dept of depts) {
      if (dept.id === deptId) return dept.deptName
      if (dept.children) {
        const found = findDept(dept.children)
        if (found) return found
      }
    }
    return ''
  }
  return findDept(departmentTree.value) || '-'
}

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    const response = await getUsers({
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined,
      role: roleFilter.value || undefined,
      status: statusFilter.value || undefined,
      deptId: deptFilter.value ? Number(deptFilter.value) : undefined
    })
    if (response.data) {
      users.value = response.data.content || []
      totalElements.value = response.data.totalElements
      totalPages.value = response.data.totalPages
      // 加载当前页用户的角色
      await loadAllUserRoles()
    }
  } catch (error: any) {
    showToast(error.message || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

// 分页变化
const handlePageChange = (page: number) => {
  currentPage.value = page
  loadUsers()
}

const handlePageSizeChange = () => {
  currentPage.value = 0
  loadUsers()
}

// 筛选变化时重置页码
watch([searchKeyword, roleFilter, statusFilter, deptFilter], () => {
  currentPage.value = 0
})

// 防抖搜索
watch(searchKeyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    loadUsers()
  }, 300)
})

// 获取可见页码
const getVisiblePages = () => {
  const pages: (number | string)[] = []
  const total = totalPages.value
  const current = currentPage.value

  if (total <= 7) {
    for (let i = 0; i < total; i++) pages.push(i)
  } else {
    if (current < 3) {
      pages.push(0, 1, 2, 3, '...', total - 1)
    } else if (current > total - 4) {
      pages.push(0, '...', total - 4, total - 3, total - 2, total - 1)
    } else {
      pages.push(0, '...', current - 1, current, current + 1, '...', total - 1)
    }
  }
  return pages
}

// 扁平化部门列表（用于筛选下拉）
const flatDepartments = computed(() => {
  const result: { value: string; label: string }[] = [{ value: '', label: '全部部门' }]
  const flatten = (depts: Department[], prefix: string = '') => {
    for (const dept of depts) {
      result.push({ value: String(dept.id), label: prefix + dept.deptName })
      if (dept.children) {
        flatten(dept.children, prefix + '  ')
      }
    }
  }
  flatten(departmentTree.value)
  return result
})

// 重置表单
const resetForm = () => {
  formData.value = {
    username: '',
    employeeId: '',
    nickname: '',
    email: '',
    phone: '',
    department: '',
    deptId: null,
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
    employeeId: user.employeeId || '',
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || '',
    department: user.department || '',
    deptId: user.deptId || null,
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
        department: formData.value.department,
        deptId: formData.value.deptId ?? undefined,
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
        employeeId: formData.value.employeeId || undefined,
        nickname: formData.value.nickname,
        email: formData.value.email,
        phone: formData.value.phone,
        department: formData.value.department,
        deptId: formData.value.deptId ?? undefined,
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

// 锁定/解锁用户
const toggleLock = async (user: User) => {
  loading.value = true
  try {
    if (user.isLocked) {
      await unlockUser(user.id)
      showToast('用户已解锁')
    } else {
      await lockUser(user.id)
      showToast('用户已锁定')
    }
    await loadUsers()
  } catch (error: any) {
    showToast(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

// 获取状态名称
const getStatusName = (status: string) => {
  return getStatusLabel(status)
}

// 角色选项和状态选项（来自字典）
const roleOptions = computed(() => getDictOptions('user_role'))
const statusOptions = computed(() => getDictOptions('common_status'))

// 打开角色分配模态框
const handleAssignRoles = (user: User) => {
  editingUserId.value = user.id
  // 获取当前用户的角色ID列表
  const currentRoleIds = userRolesMap.value.get(user.id) || []
  selectedRoleIds.value = [...currentRoleIds]
  showRoleModal.value = true
}

// 切换角色选择
const toggleRoleSelection = (roleId: number) => {
  const index = selectedRoleIds.value.indexOf(roleId)
  if (index > -1) {
    selectedRoleIds.value.splice(index, 1)
  } else {
    selectedRoleIds.value.push(roleId)
  }
}

// 保存角色分配
const saveRoleAssignment = async () => {
  if (!editingUserId.value) return
  loading.value = true
  try {
    await assignUserRoles(editingUserId.value, selectedRoleIds.value)
    showToast('角色分配成功')
    // 更新本地缓存
    userRolesMap.value.set(editingUserId.value, selectedRoleIds.value)
    showRoleModal.value = false
  } catch (error: any) {
    showToast(error.message || '角色分配失败')
  } finally {
    loading.value = false
  }
}

// 获取角色标签样式（根据角色编码）
const getRoleClassByCode = (code: string) => {
  const map: Record<string, string> = {
    ADMIN: 'admin',
    EDITOR: 'editor',
    VIEWER: 'viewer'
  }
  return map[code] || 'viewer'
}

// 下载导入模板
const handleDownloadTemplate = async () => {
  try {
    await downloadUserTemplate()
    showToast('模板下载成功')
  } catch (error: any) {
    showToast('模板下载失败')
  }
}

// 导入用户
const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  const allowedTypes = ['.xlsx', '.xls']
  const fileExt = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
  if (!allowedTypes.includes(fileExt)) {
    showToast('请选择Excel文件(.xlsx或.xls)')
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    showToast('文件大小不能超过10MB')
    return
  }

  handleImport(file)
  target.value = ''
}

const handleImport = async (file: File) => {
  importing.value = true
  importResult.value = null
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await importUsers(formData)
    if (response.data) {
      importResult.value = response.data
      const msg = response.data.skipCount > 0
        ? `导入完成: 成功 ${response.data.successCount} 条, 跳过 ${response.data.skipCount} 条`
        : `导入成功，共 ${response.data.successCount} 条`
      showToast(msg)
      await loadUsers()
    }
  } catch (error: any) {
    showToast(error.message || '导入失败')
  } finally {
    importing.value = false
  }
}

// 导出用户
const handleExport = async () => {
  exporting.value = true
  try {
    await exportUsers()
    showToast('导出成功')
  } catch (error: any) {
    showToast('导出失败')
  } finally {
    exporting.value = false
  }
}

onMounted(() => {
  // 检查权限 - 已在路由守卫中检查，但作为额外保障
  if (!userStore.isAdmin) {
    window.location.href = '/#/home'
    return
  }
  loadAllDicts()
  loadRoles()
  loadDepartments()
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
      <div class="header-actions">
        <button class="btn btn-primary" @click="handleCreate" v-if="hasPermission(Permission.USER_CREATE)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新增
        </button>
        <div class="dropdown" v-if="hasPermission(Permission.USER_CREATE)">
          <button class="btn btn-outline" @click="showMoreMenu = !showMoreMenu">
            更多
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="6 9 12 15 18 9"/>
            </svg>
          </button>
          <div class="dropdown-menu" v-if="showMoreMenu" @click.self="showMoreMenu = false">
            <button class="dropdown-item" @click="handleExport" :disabled="exporting">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              {{ exporting ? '导出中...' : '导出用户' }}
            </button>
            <button class="dropdown-item" @click="handleDownloadTemplate">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                <polyline points="14 2 14 8 20 8"/>
                <line x1="12" y1="18" x2="12" y2="12"/>
                <line x1="9" y1="15" x2="15" y2="15"/>
              </svg>
              下载模板
            </button>
            <label class="dropdown-item" :class="{ disabled: importing }">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              {{ importing ? '导入中...' : '导入用户' }}
              <input type="file" accept=".xlsx,.xls" @change="handleFileChange" :disabled="importing" hidden />
            </label>
          </div>
        </div>
      </div>
    </div>

    <!-- 筛选区域 - 紧凑单行 -->
    <div class="filter-bar">
      <div class="filter-inline">
        <div class="search-input-wrapper">
          <svg class="search-icon" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/>
            <line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索登录名或姓名..."
            class="search-input"
          />
        </div>
        <select v-model="deptFilter" class="filter-select-sm" @change="loadUsers">
          <option v-for="opt in flatDepartments" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <select v-model="roleFilter" class="filter-select-sm" @change="loadUsers">
          <option value="">全部角色</option>
          <option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
        <select v-model="statusFilter" class="filter-select-sm" @change="loadUsers">
          <option value="">全部状态</option>
          <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
        </select>
      </div>
      <button class="btn-reset-sm" @click="searchKeyword = ''; deptFilter = ''; roleFilter = ''; statusFilter = ''; loadUsers()" title="重置">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="1 4 1 10 7 10"/>
          <path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/>
        </svg>
        重置
      </button>
    </div>

    <!-- 用户列表 -->
    <div class="content-card">
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>用户</th>
              <th class="hidden-mobile">工号</th>
              <th class="hidden-mobile">部门</th>
              <th>角色</th>
              <th>状态</th>
              <th class="hidden-mobile">创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading && users.length === 0">
              <td colspan="7" class="text-center py-10">
                <div class="loading-spinner small"></div>
                <span class="text-gray-500">加载中...</span>
              </td>
            </tr>
            <tr v-else-if="users.length === 0">
              <td colspan="7" class="text-center py-10">
                <div class="empty-state">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                  </svg>
                  <p>暂无用户数据</p>
                </div>
              </td>
            </tr>
            <tr v-else v-for="user in users" :key="user.id" class="table-row">
              <td>
                <div class="user-cell">
                  <div class="user-avatar-small" :class="user.role.toLowerCase()">
                    {{ user.nickname?.charAt(0) || 'U' }}
                  </div>
                  <div class="user-info-inline">
                    <span class="user-name-inline">{{ user.nickname }}</span>
                    <span class="user-username-inline">@{{ user.username }}</span>
                  </div>
                </div>
              </td>
              <td class="hidden-mobile">
                <span class="text-gray-600">{{ user.employeeId || '-' }}</span>
              </td>
              <td class="hidden-mobile">
                <span class="text-gray-600">{{ getDeptName(user.deptId) || user.department || '-' }}</span>
              </td>
              <td>
                <div class="roles-cell">
                  <span
                    v-for="roleTag in getUserRoleTags(user.id).slice(0, 2)"
                    :key="roleTag.id"
                    class="role-badge"
                    :class="getRoleClassByCode(roleTag.code)"
                  >
                    {{ roleTag.name }}
                  </span>
                  <span
                    v-if="getUserRoleTags(user.id).length > 2"
                    class="role-badge more"
                    :title="getUserRoleTags(user.id).slice(2).map(r => r.name).join(', ')"
                  >
                    +{{ getUserRoleTags(user.id).length - 2 }}
                  </span>
                  <span v-if="getUserRoleTags(user.id).length === 0" class="text-gray-400">-</span>
                </div>
              </td>
              <td>
                  <button
                    class="status-badge"
                    :class="{ active: user.status === 'ACTIVE', inactive: user.status === 'INACTIVE', locked: user.isLocked }"
                    @click="user.isLocked ? toggleLock(user) : toggleStatus(user)"
                  >
                    <span class="status-dot"></span>
                    {{ user.isLocked ? '已锁定' : getStatusName(user.status) }}
                    <svg v-if="user.isLocked" width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                  </button>
                </td>
              <td class="hidden-mobile">
                <span class="text-gray-500 text-sm">{{ user.createdTime }}</span>
              </td>
              <td>
                <div class="actions-cell">
                  <button class="action-btn" @click="handleAssignRoles(user)" v-if="hasPermission(Permission.USER_EDIT)" title="分配角色">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                      <circle cx="12" cy="7" r="4"/>
                    </svg>
                  </button>
                  <button class="action-btn" @click="handleEdit(user)" v-if="hasPermission(Permission.USER_EDIT)" title="编辑">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                    </svg>
                  </button>
                  <button
                    v-if="user.id !== 1 && hasPermission(Permission.USER_DELETE)"
                    class="action-btn danger"
                    @click="handleDelete(user)"
                    title="删除"
                  >
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="3 6 5 6 21 6"/>
                      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                    </svg>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div class="table-footer">
        <div class="pagination-info">
          <span>共 {{ totalElements }} 条记录</span>
          <select v-model="pageSize" @change="handlePageSizeChange" class="page-size-select">
            <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条/页</option>
          </select>
        </div>
        <div class="pagination-controls" v-if="totalPages > 1">
          <button class="page-btn" :disabled="currentPage === 0" @click="handlePageChange(currentPage - 1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <template v-for="p in getVisiblePages()" :key="p">
            <button v-if="p === '...'" class="page-btn ellipsis" disabled>...</button>
            <button v-else class="page-btn" :class="{ active: p === currentPage }" @click="handlePageChange(p as number)">
              {{ (p as number) + 1 }}
            </button>
          </template>
          <button class="page-btn" :disabled="currentPage >= totalPages - 1" @click="handlePageChange(currentPage + 1)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </button>
        </div>
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
              <label class="form-label">登录名 <span class="required">*</span></label>
              <input
                v-model="formData.username"
                type="text"
                class="input"
                :disabled="!!editingUser"
                placeholder="请输入登录名"
              />
            </div>
            <div class="form-group">
              <label class="form-label">工号</label>
              <input
                v-model="formData.employeeId"
                type="text"
                class="input"
                :disabled="!!editingUser"
                placeholder="留空自动生成6位工号"
                maxlength="6"
              />
            </div>
            <div class="form-group">
              <label class="form-label">姓名 <span class="required">*</span></label>
              <input
                v-model="formData.nickname"
                type="text"
                class="input"
                placeholder="请输入姓名"
              />
            </div>
            <div class="form-group">
              <label class="form-label">部门</label>
              <select v-model="formData.deptId" class="input">
                <option :value="null">请选择部门</option>
                <option v-for="opt in flatDepartments.filter(o => o.value)" :key="opt.value" :value="Number(opt.value)">
                  {{ opt.label }}
                </option>
              </select>
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
                <option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-label">状态</label>
              <select v-model="formData.status" class="input">
                <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
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

    <!-- 角色分配模态框 -->
    <div v-if="showRoleModal" class="modal-overlay" @click.self="showRoleModal = false">
      <div class="modal-content">
        <div class="modal-header">
          <h2 class="modal-title">分配角色</h2>
          <button class="modal-close" @click="showRoleModal = false">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
        </div>

        <div class="modal-body">
          <p class="form-hint">选择要分配给用户的角色（可多选）：</p>
          <div class="role-selection-grid">
            <div
              v-for="role in roles"
              :key="role.id"
              class="role-option"
              :class="{ selected: selectedRoleIds.includes(role.id) }"
              @click="toggleRoleSelection(role.id)"
            >
              <div class="role-checkbox">
                <svg v-if="selectedRoleIds.includes(role.id)" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </div>
              <div class="role-info">
                <span class="role-name">{{ role.roleName }}</span>
                <span class="role-code">{{ role.roleCode }}</span>
              </div>
              <span v-if="role.isSystem" class="system-badge">系统</span>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-outline" @click="showRoleModal = false" :disabled="loading">
            取消
          </button>
          <button class="btn btn-primary" @click="saveRoleAssignment" :disabled="loading">
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

.header-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

/* 下拉菜单 */
.dropdown {
  position: relative;
}

.dropdown-menu {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: white;
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
  min-width: 160px;
  z-index: 100;
  overflow: hidden;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
  padding: var(--spacing-sm) var(--spacing-md);
  border: none;
  background: transparent;
  font-size: 0.875rem;
  color: var(--gray-700);
  cursor: pointer;
  text-align: left;
  transition: background var(--transition-fast);
}

.dropdown-item:hover {
  background: var(--gray-50);
}

.dropdown-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dropdown-item svg {
  width: 16px;
  height: 16px;
  color: var(--gray-500);
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

/* 筛选栏 - 紧凑单行 */
.filter-bar {
  background: white;
  border-radius: var(--radius-lg);
  padding: var(--spacing-sm) var(--spacing-md);
  box-shadow: var(--shadow-sm);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  flex-wrap: wrap;
}

.filter-inline {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
  flex: 1;
}

.search-input-wrapper {
  position: relative;
  flex: 1;
  min-width: 200px;
}

.search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--gray-400);
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 6px 10px 6px 32px;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius);
  font-size: var(--font-size-body-sm);
  background: white;
  outline: none;
  transition: all var(--transition-fast);
}

.search-input:focus {
  border-color: var(--primary-color);
}

.search-input::placeholder {
  color: var(--gray-400);
}

.filter-select-sm {
  padding: 6px 10px;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius);
  font-size: var(--font-size-body-sm);
  background: white;
  outline: none;
  transition: all var(--transition-fast);
  min-width: 100px;
}

.filter-select-sm:focus {
  border-color: var(--primary-color);
}

.btn-reset-sm {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius);
  background: white;
  color: var(--gray-600);
  font-size: var(--font-size-body-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-reset-sm:hover {
  background: var(--gray-50);
  border-color: var(--gray-300);
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
  font-size: var(--font-size-base);
  color: var(--gray-700);
  border-bottom: 2px solid var(--gray-200);
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
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 600;
  font-size: 0.75rem;
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

.user-info-inline {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.user-name-inline {
  font-weight: 600;
  color: var(--gray-900);
  font-size: 0.875rem;
}

.user-username-inline {
  font-size: 0.75rem;
  color: var(--gray-400);
  font-family: 'SF Mono', 'Fira Code', monospace;
}

/* 角色标签 */
.role-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.6875rem;
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

.role-badge.more {
  background: var(--gray-100);
  color: var(--gray-600);
  cursor: help;
}

/* 状态徽章 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.status-badge.active {
  background: rgba(16, 185, 129, 0.1);
  color: var(--success-color);
}

.status-badge.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: var(--error-color);
}

.status-badge.locked {
  background: rgba(239, 68, 68, 0.15);
  color: var(--error-color);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

/* 分页 */
.table-footer {
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--gray-100);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pagination-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  color: var(--gray-500);
  font-size: 0.875rem;
}

.page-size-select {
  padding: 0.25rem 0.5rem;
  border: 1px solid var(--gray-300);
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  color: var(--gray-600);
  background: var(--bg-card);
  cursor: pointer;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.page-btn {
  min-width: 32px;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--gray-600);
  font-size: 0.75rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.page-btn:hover:not(:disabled):not(.active) {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.page-btn.active {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-btn.ellipsis {
  border: none;
  background: transparent;
}

.page-btn svg {
  width: 16px;
  height: 16px;
}

/* 角色单元格 */
.roles-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

/* 操作按钮 */
.actions-cell {
  display: flex;
  gap: 4px;
}

.action-btn {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius);
  border: none;
  cursor: pointer;
  transition: all var(--transition-fast);
  background: var(--gray-100);
  color: var(--gray-600);
}

.action-btn svg {
  width: 14px;
  height: 14px;
}

.action-btn:hover {
  background: var(--primary-color);
  color: white;
}

.action-btn.danger:hover {
  background: var(--error-color);
}

.text-gray-400 {
  color: var(--gray-400);
  font-size: 0.75rem;
}

/* 角色选择网格 */
.role-selection-grid {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.role-option {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  border: 2px solid var(--gray-200);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.role-option:hover {
  border-color: var(--gray-300);
  background: var(--gray-50);
}

.role-option.selected {
  border-color: var(--primary-color);
  background: rgba(99, 102, 241, 0.05);
}

.role-checkbox {
  width: 24px;
  height: 24px;
  border: 2px solid var(--gray-300);
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.role-option.selected .role-checkbox {
  border-color: var(--primary-color);
  background: var(--primary-color);
}

.role-checkbox svg {
  width: 16px;
  height: 16px;
  color: white;
}

.role-info {
  display: flex;
  flex-direction: column;
}

.role-name {
  font-weight: 600;
  color: var(--gray-900);
}

.role-code {
  font-size: 0.75rem;
  color: var(--gray-500);
}

.system-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.625rem;
  font-weight: 600;
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.form-hint {
  font-size: 0.875rem;
  color: var(--gray-600);
  margin-bottom: var(--spacing-md);
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

/* 响应式设计 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: space-between;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-inline {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input-wrapper {
    min-width: 100%;
  }

  .filter-select-sm {
    width: 100%;
    min-width: 100%;
  }

  .btn-reset-sm {
    width: 100%;
    justify-content: center;
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
