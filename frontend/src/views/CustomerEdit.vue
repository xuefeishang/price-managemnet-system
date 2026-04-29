<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCustomer, createCustomer, updateCustomer } from '@/api/customers'
import { getStatusLabel, loadAllDicts } from '@/composables/useDict'
import type { CustomerStatus } from '@/types'

const route = useRoute()
const router = useRouter()

const customerId = route.params.id as string
const isEdit = !!customerId

const loading = ref(false)
const saving = ref(false)

const isPCLayout = computed(() => {
  if (typeof window !== 'undefined') {
    return window.innerWidth >= 1024
  }
  return false
})

const form = reactive<{
  name: string
  code: string
  contact: string
  phone: string
  address: string
  status: CustomerStatus
  sortOrder: number
  remark: string
}>({
  name: '',
  code: '',
  contact: '',
  phone: '',
  address: '',
  status: 'ACTIVE',
  sortOrder: 0,
  remark: ''
})

const loadCustomer = async () => {
  if (!customerId) return

  loading.value = true
  try {
    const response = await getCustomer(parseInt(customerId))
    const customer = response.data
    Object.assign(form, {
      name: customer.name,
      code: customer.code,
      contact: customer.contact || '',
      phone: customer.phone || '',
      address: customer.address || '',
      status: customer.status,
      sortOrder: customer.sortOrder || 0,
      remark: customer.remark || ''
    })
  } catch (error) {
    console.error('Failed to load customer:', error)
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  if (!form.name.trim() || !form.code.trim()) {
    return
  }

  saving.value = true
  try {
    const customerData = {
      ...form,
      sortOrder: parseInt(form.sortOrder as any) || 0
    }

    if (isEdit) {
      await updateCustomer(parseInt(customerId), customerData)
    } else {
      await createCustomer(customerData)
    }

    router.push('/customers')
  } catch (error) {
    console.error('Failed to save customer:', error)
  } finally {
    saving.value = false
  }
}

const goBack = () => {
  router.push('/customers')
}

onMounted(() => {
  loadAllDicts()
  loadCustomer()
})
</script>

<template>
  <div class="customer-edit-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-edit">
        <div class="pc-header">
          <h1 class="page-title-pc">{{ isEdit ? '编辑客户' : '新建客户' }}</h1>
        </div>

        <div class="pc-content" v-if="!loading">
          <div class="pc-form-card">
            <div class="form-group">
              <label class="form-label">客户名称</label>
              <input
                v-model="form.name"
                type="text"
                class="form-input"
                placeholder="请输入客户名称"
              />
            </div>

            <div class="form-group">
              <label class="form-label">客户编码</label>
              <input
                v-model="form.code"
                type="text"
                class="form-input"
                placeholder="请输入客户编码"
              />
            </div>

            <div class="form-group">
              <label class="form-label">联系人</label>
              <input
                v-model="form.contact"
                type="text"
                class="form-input"
                placeholder="请输入联系人"
              />
            </div>

            <div class="form-group">
              <label class="form-label">联系电话</label>
              <input
                v-model="form.phone"
                type="text"
                class="form-input"
                placeholder="请输入联系电话"
              />
            </div>

            <div class="form-group">
              <label class="form-label">地址</label>
              <input
                v-model="form.address"
                type="text"
                class="form-input"
                placeholder="请输入地址"
              />
            </div>

            <div class="form-group">
              <label class="form-label">客户状态</label>
              <div class="status-toggle">
                <button
                  class="status-btn"
                  :class="{ active: form.status === 'ACTIVE' }"
                  @click="form.status = 'ACTIVE'"
                >
                  {{ getStatusLabel('ACTIVE') }}
                </button>
                <button
                  class="status-btn"
                  :class="{ active: form.status === 'INACTIVE' }"
                  @click="form.status = 'INACTIVE'"
                >
                  {{ getStatusLabel('INACTIVE') }}
                </button>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">排序</label>
              <input
                v-model.number="form.sortOrder"
                type="number"
                class="form-input"
                placeholder="请输入排序号"
              />
            </div>

            <div class="form-group">
              <label class="form-label">备注</label>
              <textarea
                v-model="form.remark"
                class="form-textarea"
                placeholder="请输入备注信息"
                rows="4"
              ></textarea>
            </div>
          </div>

          <div class="pc-actions">
            <button class="btn-cancel-pc" @click="goBack">取消</button>
            <button class="btn-save-pc" @click="handleSave" :disabled="saving">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>

        <main class="loading-state" v-else>
          <div class="loading-spinner"></div>
        </main>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <header class="navbar">
        <div class="navbar-left">
          <button class="back-btn" @click="goBack">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <h1 class="navbar-title">{{ isEdit ? '编辑客户' : '新建客户' }}</h1>
        </div>
        <button class="save-btn" @click="handleSave" :disabled="saving">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </header>

      <main class="content" v-if="!loading">
        <div class="form-card">
          <div class="form-group">
            <label class="form-label">客户名称</label>
            <input
              v-model="form.name"
              type="text"
              class="form-input"
              placeholder="请输入客户名称"
            />
          </div>

          <div class="form-group">
            <label class="form-label">客户编码</label>
            <input
              v-model="form.code"
              type="text"
              class="form-input"
              placeholder="请输入客户编码"
            />
          </div>

          <div class="form-group">
            <label class="form-label">联系人</label>
            <input
              v-model="form.contact"
              type="text"
              class="form-input"
              placeholder="请输入联系人"
            />
          </div>

          <div class="form-group">
            <label class="form-label">联系电话</label>
            <input
              v-model="form.phone"
              type="text"
              class="form-input"
              placeholder="请输入联系电话"
            />
          </div>

          <div class="form-group">
            <label class="form-label">地址</label>
            <input
              v-model="form.address"
              type="text"
              class="form-input"
              placeholder="请输入地址"
            />
          </div>

          <div class="form-group">
            <label class="form-label">客户状态</label>
            <div class="status-toggle">
              <button
                class="status-btn"
                :class="{ active: form.status === 'ACTIVE' }"
                @click="form.status = 'ACTIVE'"
              >
                {{ getStatusLabel('ACTIVE') }}
              </button>
              <button
                class="status-btn"
                :class="{ active: form.status === 'INACTIVE' }"
                @click="form.status = 'INACTIVE'"
              >
                {{ getStatusLabel('INACTIVE') }}
              </button>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">排序</label>
            <input
              v-model.number="form.sortOrder"
              type="number"
              class="form-input"
              placeholder="请输入排序号"
            />
          </div>
        </div>

        <div class="form-card">
          <div class="form-group">
            <label class="form-label">备注</label>
            <textarea
              v-model="form.remark"
              class="form-textarea"
              placeholder="请输入备注信息"
              rows="4"
            ></textarea>
          </div>
        </div>
      </main>

      <main class="loading-state" v-else>
        <div class="loading-spinner"></div>
      </main>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.customer-edit-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

.pc-edit {
  padding: 32px;
  max-width: 600px;
}

.pc-header {
  margin-bottom: 32px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.pc-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.pc-form-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.pc-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.customer-edit-page {
  display: flex;
  flex-direction: column;
  max-width: 402px;
  margin: 0 auto;
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
  font-size: 20px;
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

.save-btn:hover:not(:disabled) {
  background: #0D8A8A;
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.form-group {
  margin-bottom: 16px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
  margin-bottom: 4px;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  transition: border-color 150ms;
  box-sizing: border-box;
}

.form-input:focus,
.form-textarea:focus {
  outline: none;
  border-color: #0D6E6E;
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: #888888;
}

.form-textarea {
  resize: vertical;
  min-height: 100px;
}

.status-toggle {
  display: flex;
  gap: 8px;
}

.status-btn {
  flex: 1;
  padding: 10px 16px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.status-btn.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
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
  .pc-edit {
    display: none;
  }
}

@media (max-width: 480px) {
  .customer-edit-page {
    max-width: 100%;
  }
}
</style>
