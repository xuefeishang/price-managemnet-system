<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { getCustomers, updateCustomer, deleteCustomer } from '@/api/customers'
import { usePermission, Permission } from '@/composables/usePermission'
import type { Customer } from '@/types'

const router = useRouter()
const { hasPermission } = usePermission()

const customers = ref<Customer[]>([])
const loading = ref(false)
const togglingId = ref<number | null>(null)
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

const isPCLayout = computed(() => windowWidth.value >= 1024)

const loadCustomers = async () => {
  loading.value = true
  try {
    const response = await getCustomers()
    customers.value = response.data || []
  } catch (error) {
    console.error('Failed to load customers:', error)
    showToast('加载客户失败')
  } finally {
    loading.value = false
  }
}

const handleToggleStatus = async (customer: Customer) => {
  if (togglingId.value === customer.id) return

  const newStatus = customer.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  const actionText = newStatus === 'ACTIVE' ? '启用' : '停用'

  try {
    togglingId.value = customer.id
    await updateCustomer(customer.id, { status: newStatus } as Partial<Customer>)
    customer.status = newStatus
    showToast(`客户已${actionText}`)
  } catch (error) {
    console.error('Failed to toggle status:', error)
    showToast('操作失败')
  } finally {
    togglingId.value = null
  }
}

const handleEdit = (customer: Customer) => {
  router.push(`/customer-edit/${customer.id}`)
}

const handleCreate = () => {
  router.push('/customer-edit')
}

const handleDelete = async (customer: Customer) => {
  showDialog({
    title: '确认删除',
    message: `确定要删除客户"${customer.name}"吗？删除后不可恢复。`,
    confirmButtonText: '删除',
    confirmButtonColor: '#EF4444',
    showCancelButton: true
  }).then(async () => {
    try {
      await deleteCustomer(customer.id)
      showToast('删除成功')
      loadCustomers()
    } catch (error) {
      console.error('Failed to delete customer:', error)
      showToast('删除失败')
    }
  }).catch(() => {
  })
}

const activeTab = ref('products')

const switchTab = (tab: string) => {
  activeTab.value = tab
  switch (tab) {
    case 'home':
      router.push('/home')
      break
    case 'products':
      router.push('/products')
      break
    case 'import':
      router.push('/import')
      break
    case 'profile':
      router.push('/profile')
      break
  }
}

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadCustomers()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="customers-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-customers">
        <div class="page-header-pc">
          <h1 class="page-title-pc">客户管理</h1>
          <button class="btn-primary-pc" @click="handleCreate">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"/>
              <line x1="5" y1="12" x2="19" y2="12"/>
            </svg>
            新建客户
          </button>
        </div>

        <div class="customer-table-pc" v-if="!loading">
          <div class="table-header">
            <div class="table-cell name">客户名称</div>
            <div class="table-cell code">客户编码</div>
            <div class="table-cell contact">联系人</div>
            <div class="table-cell phone">联系电话</div>
            <div class="table-cell status">状态</div>
            <div class="table-cell actions">操作</div>
          </div>

          <div
            v-for="customer in customers"
            :key="customer.id"
            class="table-row"
            :class="{ inactive: customer.status === 'INACTIVE' }"
          >
            <div class="table-cell name">
              <div class="customer-name-cell">
                <div class="customer-icon" :class="customer.status">
                  <svg v-if="customer.status === 'ACTIVE'" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                  </svg>
                  <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                    <circle cx="9" cy="7" r="4"/>
                    <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                    <line x1="9" y1="14" x2="15" y2="14"/>
                  </svg>
                </div>
                <span class="customer-name">{{ customer.name }}</span>
              </div>
            </div>
            <div class="table-cell code">{{ customer.code }}</div>
            <div class="table-cell contact">{{ customer.contact || '-' }}</div>
            <div class="table-cell phone">{{ customer.phone || '-' }}</div>
            <div class="table-cell status">
              <div
                class="toggle-switch"
                :class="{ active: customer.status === 'ACTIVE', loading: togglingId === customer.id }"
                @click="handleToggleStatus(customer)"
                :title="customer.status === 'ACTIVE' ? '点击停用' : '点击启用'"
              >
                <div class="toggle-slider"></div>
              </div>
              <span class="status-text" :class="customer.status">
                {{ customer.status === 'ACTIVE' ? '启用' : '停用' }}
              </span>
            </div>
            <div class="table-cell actions" @click.stop>
              <button class="action-btn edit" @click="handleEdit(customer)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                编辑
              </button>
              <button class="action-btn delete" @click="handleDelete(customer)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
                删除
              </button>
            </div>
          </div>

          <div v-if="customers.length === 0" class="empty-state-pc">
            <div class="empty-icon">
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <p class="empty-text">暂无客户数据</p>
            <button class="empty-btn" @click="handleCreate">
              创建第一个客户
            </button>
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
          <h1 class="navbar-title">客户管理</h1>
        </div>
        <button class="add-btn" @click="handleCreate">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="12" y1="5" x2="12" y2="19"/>
            <line x1="5" y1="12" x2="19" y2="12"/>
          </svg>
        </button>
      </header>

      <main class="content">
        <div class="customer-list" v-if="!loading && customers.length > 0">
          <div
            v-for="customer in customers"
            :key="customer.id"
            class="customer-card"
            :class="{ inactive: customer.status === 'INACTIVE' }"
          >
            <div class="card-main">
              <div class="customer-icon" :class="customer.status">
                <svg v-if="customer.status === 'ACTIVE'" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                </svg>
                <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                  <line x1="9" y1="14" x2="15" y2="14"/>
                </svg>
              </div>
              <div class="card-info">
                <div class="card-name">{{ customer.name }}</div>
                <div class="card-meta">
                  <span class="card-code">{{ customer.code }}</span>
                  <span class="separator">·</span>
                  <span>{{ customer.contact || '-' }}</span>
                </div>
              </div>
            </div>

            <div class="card-control">
              <div
                class="toggle-switch small"
                :class="{ active: customer.status === 'ACTIVE', loading: togglingId === customer.id }"
                @click="handleToggleStatus(customer)"
              >
                <div class="toggle-slider"></div>
              </div>
              <div class="card-actions">
                <button class="card-btn edit" @click="handleEdit(customer)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
                <button class="card-btn delete" @click="handleDelete(customer)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              </div>
            </div>

            <div class="card-remark" v-if="customer.phone">
              {{ customer.phone }}
            </div>
          </div>
        </div>

        <div v-else-if="!loading" class="empty-state">
          <div class="empty-icon">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
              <circle cx="9" cy="7" r="4"/>
              <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
              <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
          </div>
          <p class="empty-text">暂无客户数据</p>
          <button class="empty-btn" @click="handleCreate">
            创建第一个客户
          </button>
        </div>

        <div v-else class="loading-state">
          <div class="loading-spinner"></div>
        </div>
      </main>

      <footer class="tab-bar">
        <button class="tab-item" :class="{ active: activeTab === 'home' }" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'products' }" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'import' }" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" :class="{ active: activeTab === 'profile' }" @click="switchTab('profile')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span class="tab-label">我的</span>
        </button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.customers-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

.pc-customers {
  padding: 32px;
}

.page-header-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.customer-table-pc {
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

.table-row.inactive {
  background: #FDFCFB;
}

.table-cell {
  padding: 16px 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
}

.table-cell.name {
  flex: 2;
  min-width: 160px;
}

.table-cell.code {
  flex: 1;
  min-width: 120px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 13px;
  color: #666666;
}

.table-cell.contact {
  flex: 1;
  min-width: 100px;
  color: #666666;
}

.table-cell.phone {
  flex: 1;
  min-width: 120px;
  color: #666666;
}

.table-cell.status {
  flex: 1;
  min-width: 140px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.table-cell.actions {
  flex: 1;
  min-width: 140px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.customer-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.customer-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.customer-icon.ACTIVE {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.customer-icon.INACTIVE {
  background: #E5E7EB;
  color: #9CA3AF;
}

.customer-name {
  font-weight: 500;
}

.toggle-switch {
  width: 40px;
  height: 22px;
  border-radius: 11px;
  background: #E5E7EB;
  position: relative;
  cursor: pointer;
  transition: all 200ms;
  flex-shrink: 0;
}

.toggle-switch.active {
  background: #0D6E6E;
}

.toggle-switch.loading {
  opacity: 0.6;
  pointer-events: none;
}

.toggle-slider {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #FFFFFF;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
  transition: all 200ms;
}

.toggle-switch.active .toggle-slider {
  left: 20px;
}

.toggle-switch.small {
  width: 36px;
  height: 20px;
}

.toggle-switch.small .toggle-slider {
  width: 16px;
  height: 16px;
}

.toggle-switch.small.active .toggle-slider {
  left: 18px;
}

.status-text {
  font-size: 13px;
  font-weight: 500;
}

.status-text.ACTIVE {
  color: #0D6E6E;
}

.status-text.INACTIVE {
  color: #9CA3AF;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border: none;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.action-btn.edit {
  background: rgba(13, 110, 110, 0.08);
  color: #0D6E6E;
}

.action-btn.edit:hover {
  background: rgba(13, 110, 110, 0.15);
}

.action-btn.delete {
  background: rgba(239, 68, 68, 0.08);
  color: #EF4444;
}

.action-btn.delete:hover {
  background: rgba(239, 68, 68, 0.15);
}

.empty-state-pc {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 24px;
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: #F3F4F6;
  color: #9CA3AF;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-text {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #9CA3AF;
  margin: 0 0 20px 0;
}

.empty-btn {
  padding: 10px 20px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.loading-state-pc {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
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

@media (max-width: 1024px) {
  .pc-customers {
    display: none;
  }
}

.customers-page {
  display: flex;
  flex-direction: column;
  max-width: 100%;
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
}

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 18px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.add-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: #0D6E6E;
  color: #FFFFFF;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 100px;
}

.customer-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.customer-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
}

.card-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.customer-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 4px;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 11px;
  color: #888888;
}

.card-code {
  font-family: 'JetBrains Mono', monospace;
}

.separator {
  color: #D1D5DB;
}

.card-control {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.card-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 150ms;
}

.card-btn.edit {
  background: rgba(13, 110, 110, 0.08);
  color: #0D6E6E;
}

.card-btn.edit:hover {
  background: rgba(13, 110, 110, 0.15);
}

.card-btn.delete {
  background: rgba(239, 68, 68, 0.08);
  color: #EF4444;
}

.card-btn.delete:hover {
  background: rgba(239, 68, 68, 0.15);
}

.card-remark {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #888888;
  padding: 10px 12px;
  background: #F9FAFB;
  border-radius: 6px;
  margin-top: 12px;
  line-height: 1.4;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 24px;
  text-align: center;
}

.empty-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: #F3F4F6;
  color: #9CA3AF;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}

.empty-text {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #9CA3AF;
  margin: 0 0 16px 0;
}

.empty-btn {
  padding: 10px 20px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px;
}

.tab-bar {
  height: 64px;
  background: #FFFFFF;
  border-top: 1px solid #E5E5E5;
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 0 20px;
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  max-width: 100%;
  z-index: 100;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  transition: all 150ms;
  color: #AAAAAA;
}

.tab-item.active {
  color: #0D6E6E;
}

.tab-label {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 500;
}
</style>
