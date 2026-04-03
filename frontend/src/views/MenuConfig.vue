<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { getAllMenus, createMenu, updateMenu, deleteMenu } from '@/api/menu'
import type { MenuItem, Role } from '@/types'

// 响应式布局
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const handleResize = () => {
  windowWidth.value = window.innerWidth
}
const isPCLayout = computed(() => windowWidth.value >= 768)

const loading = ref(false)
const menus = ref<MenuItem[]>([])
const editingMenu = ref<MenuItem | null>(null)
const isEditing = ref(false)
const showEditDialog = ref(false)

// 表单数据
const formData = ref({
  name: '',
  path: '',
  icon: '',
  sortOrder: 0,
  visible: true,
  roles: [] as Role[],
  parentId: null as number | null
})

const roleOptions: { value: Role; label: string }[] = [
  { value: 'ADMIN', label: '管理员' },
  { value: 'EDITOR', label: '编辑者' },
  { value: 'VIEWER', label: '查看者' }
]

const iconOptions = [
  { value: 'home', label: '首页' },
  { value: 'product', label: '产品' },
  { value: 'price', label: '价格' },
  { value: 'category', label: '分类' },
  { value: 'users', label: '用户' },
  { value: 'settings', label: '设置' },
  { value: 'import', label: '导入' },
  { value: 'menu', label: '菜单' }
]

// 扁平化菜单用于选择父级
const flatMenus = computed(() => {
  const result: { id: number; name: string; level: number }[] = []
  const flatten = (items: MenuItem[], level: number) => {
    items.forEach(item => {
      result.push({ id: item.id, name: item.name, level })
      if (item.children?.length) {
        flatten(item.children, level + 1)
      }
    })
  }
  flatten(menus.value, 0)
  return result
})

// 加载菜单
const loadMenus = async () => {
  loading.value = true
  try {
    const response = await getAllMenus()
    menus.value = response.data || []
  } catch (error) {
    console.error('Failed to load menus:', error)
    showToast('加载菜单失败')
  } finally {
    loading.value = false
  }
}

// 打开新增对话框
const openAddDialog = (parentId: number | null = null) => {
  isEditing.value = false
  editingMenu.value = null
  formData.value = {
    name: '',
    path: '',
    icon: '',
    sortOrder: 0,
    visible: true,
    roles: [],
    parentId
  }
  showEditDialog.value = true
}

// 打开编辑对话框
const openEditDialog = (menu: MenuItem) => {
  isEditing.value = true
  editingMenu.value = menu
  formData.value = {
    name: menu.name,
    path: menu.path || '',
    icon: menu.icon || '',
    sortOrder: menu.sortOrder,
    visible: menu.visible,
    roles: menu.roles || [],
    parentId: menu.parentId
  }
  showEditDialog.value = true
}

// 保存菜单
const handleSave = async () => {
  if (!formData.value.name) {
    showToast('请输入菜单名称')
    return
  }

  try {
    if (isEditing.value && editingMenu.value) {
      await updateMenu(editingMenu.value.id, formData.value)
      showToast('更新成功')
    } else {
      await createMenu(formData.value as MenuItem)
      showToast('创建成功')
    }
    showEditDialog.value = false
    loadMenus()
  } catch (error) {
    console.error('Failed to save menu:', error)
    showToast('保存失败')
  }
}

// 删除菜单
const handleDelete = async (menu: MenuItem) => {
  if (menu.children?.length) {
    showToast('请先删除子菜单')
    return
  }

  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除菜单"${menu.name}"吗？`
    })
    await deleteMenu(menu.id)
    showToast('删除成功')
    loadMenus()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('Failed to delete menu:', error)
      showToast('删除失败')
    }
  }
}

// 切换启用状态
const toggleVisible = async (menu: MenuItem) => {
  try {
    await updateMenu(menu.id, { ...menu, visible: !menu.visible })
    loadMenus()
  } catch (error) {
    console.error('Failed to toggle visibility:', error)
    showToast('操作失败')
  }
}

// 获取级别名称
const getLevelName = (level: number) => {
  return level === 0 ? '一级菜单' : '二级菜单'
}

onMounted(() => {
  loadMenus()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="menu-config-page">
    <!-- PC布局 -->
    <div v-if="windowWidth >= 1024" class="pc-container">
      <div class="pc-header">
        <div class="header-content">
          <button class="back-button" @click="$router.push('/home')">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <div class="header-text">
            <h1 class="page-title-pc">菜单配置</h1>
            <p class="page-subtitle">管理系统菜单，可添加、编辑、删除和排序</p>
          </div>
        </div>
        <button class="btn-primary" @click="openAddDialog(null)">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
          新增菜单
        </button>
      </div>

      <!-- 菜单树表格 -->
      <div class="menu-table" v-if="!loading">
        <div class="table-header">
          <div class="th name">菜单名称</div>
          <div class="th path">路径</div>
          <div class="th icon">图标</div>
          <div class="th order">排序</div>
          <div class="th visible">可见</div>
          <div class="th roles">角色</div>
          <div class="th actions">操作</div>
        </div>

        <template v-for="menu in menus" :key="menu.id">
          <!-- 一级菜单 -->
          <div class="table-row parent">
            <div class="td name">
              <span class="menu-name">{{ menu.name }}</span>
            </div>
            <div class="td path">{{ menu.path || '-' }}</div>
            <div class="td icon">{{ menu.icon || '-' }}</div>
            <div class="td order">{{ menu.sortOrder }}</div>
            <div class="td visible">
              <switch :checked="menu.visible" @change="toggleVisible(menu)" />
            </div>
            <div class="td roles">
              <span v-for="role in menu.roles" :key="role" class="role-tag">{{ role }}</span>
            </div>
            <div class="td actions">
              <button class="btn-icon" title="添加子菜单" @click="openAddDialog(menu.id)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="5" x2="12" y2="19"/>
                  <line x1="5" y1="12" x2="19" y2="12"/>
                </svg>
              </button>
              <button class="btn-icon" title="编辑" @click="openEditDialog(menu)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button class="btn-icon danger" title="删除" @click="handleDelete(menu)">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 二级菜单 -->
          <template v-if="menu.children?.length" v-for="child in menu.children" :key="child.id">
            <div class="table-row child">
              <div class="td name">
                <span class="child-indicator"></span>
                <span class="menu-name">{{ child.name }}</span>
              </div>
              <div class="td path">{{ child.path || '-' }}</div>
              <div class="td icon">{{ child.icon || '-' }}</div>
              <div class="td order">{{ child.sortOrder }}</div>
              <div class="td visible">
                <switch :checked="child.visible" @change="toggleVisible(child)" />
              </div>
              <div class="td roles">
                <span v-for="role in child.roles" :key="role" class="role-tag">{{ role }}</span>
              </div>
              <div class="td actions">
                <button class="btn-icon" title="编辑" @click="openEditDialog(child)">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
                <button class="btn-icon danger" title="删除" @click="handleDelete(child)">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              </div>
            </div>
          </template>
        </template>

        <div v-if="menus.length === 0" class="empty-state">
          暂无菜单数据
        </div>
      </div>

      <div v-else class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
    </div>

    <!-- 移动端布局 -->
    <template v-else>
      <header class="navbar">
        <div class="navbar-left">
          <button class="back-btn" @click="$router.push('/home')">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <h1 class="navbar-title">菜单配置</h1>
        </div>
        <button class="save-btn" @click="openAddDialog(null)">新增</button>
      </header>

      <main class="content" v-if="!loading">
        <div class="menu-list">
          <div v-for="menu in menus" :key="menu.id" class="menu-card">
            <div class="card-header">
              <span class="menu-name">{{ menu.name }}</span>
              <switch :checked="menu.visible" @change="toggleVisible(menu)" />
            </div>
            <div class="card-info">
              <span>路径: {{ menu.path || '-' }}</span>
              <span>排序: {{ menu.sortOrder }}</span>
            </div>
            <div class="card-actions">
              <button class="btn-small" @click="openAddDialog(menu.id)">添加子菜单</button>
              <button class="btn-small" @click="openEditDialog(menu)">编辑</button>
              <button class="btn-small danger" @click="handleDelete(menu)">删除</button>
            </div>

            <!-- 子菜单 -->
            <div v-if="menu.children?.length" class="sub-menu-list">
              <div v-for="child in menu.children" :key="child.id" class="sub-menu-card">
                <div class="card-header">
                  <span class="menu-name">{{ child.name }}</span>
                  <switch :checked="child.visible" @change="toggleVisible(child)" />
                </div>
                <div class="card-info">
                  <span>路径: {{ child.path || '-' }}</span>
                </div>
                <div class="card-actions">
                  <button class="btn-small" @click="openEditDialog(child)">编辑</button>
                  <button class="btn-small danger" @click="handleDelete(child)">删除</button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="menus.length === 0" class="empty-state">
            暂无菜单数据
          </div>
        </div>
      </main>

      <div v-else class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
    </template>

    <!-- 编辑对话框 -->
    <van-popup
      v-model:show="showEditDialog"
      :position="isPCLayout ? 'center' : 'bottom'"
      :round="!isPCLayout"
      :style="isPCLayout ? 'width: 520px; max-height: 85vh; border-radius: 12px;' : 'height: 80%;'"
      @click-overlay="showEditDialog = false"
    >
      <div class="edit-dialog" :class="{ 'pc-dialog': isPCLayout }">
        <div class="dialog-header">
          <span>{{ isEditing ? '编辑菜单' : '新增菜单' }}</span>
        </div>
        <div class="dialog-content">
          <div class="form-item">
            <label>菜单名称 *</label>
            <input v-model="formData.name" placeholder="请输入菜单名称" />
          </div>
          <div class="form-item">
            <label>路径</label>
            <input v-model="formData.path" placeholder="如: /products" />
          </div>
          <div class="form-item">
            <label>图标</label>
            <select v-model="formData.icon">
              <option value="">请选择图标</option>
              <option v-for="icon in iconOptions" :key="icon.value" :value="icon.value">
                {{ icon.label }}
              </option>
            </select>
          </div>
          <div class="form-item">
            <label>排序</label>
            <input type="number" v-model.number="formData.sortOrder" placeholder="数字越小越靠前" />
          </div>
          <div class="form-item">
            <label>父级菜单</label>
            <select v-model="formData.parentId">
              <option :value="null">无（作为一级菜单）</option>
              <option v-for="m in flatMenus" :key="m.id" :value="m.id">
                {{ '　'.repeat(m.level) }}{{ m.name }}
              </option>
            </select>
          </div>
          <div class="form-item">
            <label>可见角色</label>
            <div class="role-checkboxes">
              <label v-for="role in roleOptions" :key="role.value" class="checkbox-label">
                <input type="checkbox" :value="role.value" v-model="formData.roles" />
                {{ role.label }}
              </label>
            </div>
          </div>
          <div class="form-item form-item-inline">
            <label>启用</label>
            <div class="switch-wrapper">
              <input type="checkbox" class="switch-input" :checked="formData.visible" @change="formData.visible = ($event.target as HTMLInputElement).checked" />
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="btn-cancel" @click="showEditDialog = false">取消</button>
          <button class="btn-confirm" @click="handleSave">保存</button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.menu-config-page {
  min-height: 100vh;
  background-color: #F5F5F5;
}

/* ==================== PC布局 ==================== */
.pc-container {
  padding: 32px;
  max-width: 1200px;
  margin: 0 auto;
}

.pc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding: 20px 24px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-button {
  width: 40px;
  height: 40px;
  border: 1px solid #E5E5E5;
  background: #FFFFFF;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  transition: all 150ms;
}

.back-button:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.page-subtitle {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #888888;
  margin: 0;
}

.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-primary:hover {
  background: #0D8A8A;
}

/* 菜单表格 */
.menu-table {
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: center;
  background: #FAFAFA;
  border-bottom: 1px solid #E5E5E5;
  padding: 0 20px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 600;
  color: #666666;
}

.th, .td {
  padding: 14px 8px;
}

.th.name, .td.name {
  flex: 2;
  min-width: 150px;
}

.th.path, .td.path {
  flex: 1.5;
  min-width: 120px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #666666;
}

.th.icon, .td.icon {
  flex: 1;
  min-width: 80px;
}

.th.order, .td.order {
  flex: 0.6;
  min-width: 60px;
  text-align: center;
}

.th.visible, .td.visible {
  flex: 0.8;
  min-width: 70px;
  text-align: center;
}

.th.roles, .td.roles {
  flex: 1.2;
  min-width: 120px;
}

.th.actions, .td.actions {
  flex: 1.2;
  min-width: 120px;
  text-align: center;
}

.table-row {
  display: flex;
  align-items: center;
  padding: 0 20px;
  border-bottom: 1px solid #F3F4F6;
  transition: background-color 150ms;
}

.table-row:last-child {
  border-bottom: none;
}

.table-row:hover {
  background: #FAFAFA;
}

.table-row.child {
  background: #FAFAFA;
}

.menu-name {
  font-weight: 500;
  color: #1A1A1A;
}

.child-indicator {
  display: inline-block;
  width: 20px;
  height: 1px;
  background: #D1D5DB;
  margin-right: 8px;
  vertical-align: middle;
}

.role-tag {
  display: inline-block;
  padding: 2px 6px;
  background: #E5E5E5;
  border-radius: 4px;
  font-size: 11px;
  font-family: 'Inter', sans-serif;
  color: #666666;
  margin-right: 4px;
}

.btn-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #666666;
  cursor: pointer;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 150ms;
}

.btn-icon:hover {
  background: #E5E5E5;
  color: #1A1A1A;
}

.btn-icon.danger:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

/* 加载/空状态 */
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px;
  gap: 16px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
  color: #666666;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ==================== 移动端布局 ==================== */
@media (max-width: 1024px) {
  .pc-container {
    display: none;
  }
}

.navbar {
  height: 56px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #1A1A1A;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.back-btn:hover {
  background: #F5F5F5;
}

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 18px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.save-btn {
  padding: 8px 16px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.content {
  flex: 1;
  padding: 16px;
}

.menu-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.menu-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.menu-name {
  font-weight: 600;
  color: #1A1A1A;
  font-size: 15px;
}

.card-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #666666;
  margin-bottom: 12px;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.btn-small {
  padding: 6px 12px;
  background: #F5F5F5;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-small:hover {
  background: #E5E5E5;
}

.btn-small.danger {
  color: #EF4444;
}

.btn-small.danger:hover {
  background: rgba(239, 68, 68, 0.1);
}

.sub-menu-list {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #F3F4F6;
}

.sub-menu-card {
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
  margin-bottom: 8px;
}

.sub-menu-card:last-child {
  margin-bottom: 0;
}

/* 编辑对话框 */
.edit-dialog {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
}

/* PC端对话框 */
.edit-dialog.pc-dialog {
  height: auto;
  max-height: 85vh;
}

.edit-dialog.pc-dialog .dialog-content {
  max-width: 100%;
}

.edit-dialog.pc-dialog .form-item {
  margin-bottom: 16px;
}

.edit-dialog.pc-dialog .dialog-header {
  padding: 20px 24px;
}

.edit-dialog.pc-dialog .dialog-content {
  padding: 24px;
}

.edit-dialog.pc-dialog .dialog-footer {
  padding: 16px 24px;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #E5E5E5;
  font-family: 'Newsreader', Georgia, serif;
  font-size: 18px;
  font-weight: 500;
}

.dialog-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}

.form-item {
  margin-bottom: 20px;
}

.form-item label {
  display: block;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: #666666;
  margin-bottom: 8px;
}

/* 行内表单项（开关和可见角色） */
.form-item-inline {
  display: flex;
  align-items: center;
  gap: 16px;
}

.form-item-inline label {
  margin-bottom: 0;
  flex-shrink: 0;
}

.switch-wrapper {
  display: flex;
  align-items: center;
}

.form-item input,
.form-item select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  background: #FFFFFF;
  box-sizing: border-box;
}

.form-item input:focus,
.form-item select:focus {
  outline: none;
  border-color: #0D6E6E;
}

.switch-input {
  width: 40px;
  height: 22px;
  appearance: none;
  background: #DCDFE6;
  border-radius: 11px;
  cursor: pointer;
  transition: background 0.2s;
  position: relative;
  padding: 0;
  border: none;
}

.switch-input::before {
  content: '';
  position: absolute;
  width: 16px;
  height: 16px;
  background: white;
  border-radius: 50%;
  top: 3px;
  left: 3px;
  transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.15);
}

.switch-input:checked {
  background: #0D6E6E;
}

.switch-input:checked::before {
  transform: translateX(18px);
}

.role-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  cursor: pointer;
}

.dialog-footer {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid #E5E5E5;
}

.btn-cancel,
.btn-confirm {
  flex: 1;
  padding: 12px;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-cancel {
  background: #F5F5F5;
  border: none;
  color: #666666;
}

.btn-cancel:hover {
  background: #E5E5E5;
}

.btn-confirm {
  background: #0D6E6E;
  border: none;
  color: #FFFFFF;
}

.btn-confirm:hover {
  background: #0D8A8A;
}
</style>
