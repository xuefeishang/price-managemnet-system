<template>
  <view class="page">
    <!-- 顶部导航 -->
    <view class="navbar">
      <text class="navbar-title">客户管理</text>
      <view class="navbar-action" @click="handleCreate" v-if="userStore.canEdit">
        <text class="action-text">+ 新增</text>
      </view>
    </view>

    <!-- 列表 -->
    <scroll-view class="content" scroll-y>
      <view v-if="!loading && customers.length > 0" class="list">
        <view
          v-for="item in customers"
          :key="item.id"
          class="card"
          :class="{ inactive: item.status === 'INACTIVE' }"
        >
          <view class="card-main">
            <view class="card-icon" :class="item.status">
              <text>{{ item.name.charAt(0) }}</text>
            </view>
            <view class="card-info">
              <text class="card-name">{{ item.name }}</text>
              <text class="card-meta">{{ item.code || '-' }} · 排序: {{ item.sortOrder || 0 }}</text>
            </view>
          </view>
          <view class="card-contact" v-if="item.contact || item.phone">
            <text class="contact-text">联系人: {{ item.contact || '-' }}</text>
            <text class="contact-text">电话: {{ item.phone || '-' }}</text>
          </view>
          <view class="card-actions">
            <switch
              :checked="item.status === 'ACTIVE'"
              @change="handleToggleStatus(item)"
              color="#0D6E6E"
            />
            <view class="action-btns" v-if="userStore.canEdit">
              <text class="action-btn" @click="handleEdit(item)">编辑</text>
              <text class="action-btn delete" @click="handleDelete(item)">删除</text>
            </view>
          </view>
          <view class="card-remark" v-if="item.remark">
            <text>{{ item.remark }}</text>
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else-if="!loading" class="empty">
        <text class="empty-text">暂无客户数据</text>
        <button class="empty-btn" @click="handleCreate" v-if="userStore.canEdit">创建客户</button>
      </view>

      <!-- 加载中 -->
      <view v-else class="loading">
        <text>加载中...</text>
      </view>
    </scroll-view>

    <!-- 编辑弹窗 -->
    <view class="popup" v-if="showEditPopup" @click="showEditPopup = false">
      <view class="popup-content" @click.stop>
        <view class="popup-header">
          <text class="popup-title">{{ editingId ? '编辑客户' : '新增客户' }}</text>
          <text class="popup-close" @click="showEditPopup = false">×</text>
        </view>
        <scroll-view class="popup-body" scroll-y>
          <view class="form-item">
            <text class="form-label">客户名称 *</text>
            <input class="form-input" v-model="form.name" placeholder="请输入客户名称" />
          </view>
          <view class="form-item">
            <text class="form-label">客户编码</text>
            <input class="form-input" v-model="form.code" placeholder="请输入客户编码" />
          </view>
          <view class="form-item">
            <text class="form-label">联系人</text>
            <input class="form-input" v-model="form.contact" placeholder="请输入联系人" />
          </view>
          <view class="form-item">
            <text class="form-label">联系电话</text>
            <input class="form-input" v-model="form.phone" placeholder="请输入联系电话" />
          </view>
          <view class="form-item">
            <text class="form-label">地址</text>
            <input class="form-input" v-model="form.address" placeholder="请输入地址" />
          </view>
          <view class="form-item">
            <text class="form-label">排序</text>
            <input class="form-input" type="number" v-model="form.sortOrder" placeholder="0" />
          </view>
          <view class="form-item">
            <text class="form-label">备注</text>
            <textarea class="form-textarea" v-model="form.remark" placeholder="请输入备注" />
          </view>
        </scroll-view>
        <view class="popup-footer">
          <button class="btn-cancel" @click="showEditPopup = false">取消</button>
          <button class="btn-confirm" @click="handleSave">保存</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { getCustomers, createCustomer, updateCustomer, deleteCustomer } from '@/api/customers'
import { getDictValue, loadAllDicts } from '@/composables/useDict'
import type { Customer } from '@/types'

const userStore = useUserStore()
const customers = ref<Customer[]>([])
const loading = ref(false)
const showEditPopup = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  name: '',
  code: '',
  contact: '',
  phone: '',
  address: '',
  sortOrder: 0,
  remark: ''
})

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCustomers()
    if (res.code === 200) {
      customers.value = res.data || []
    }
  } catch (error) {
    console.error('加载客户失败:', error)
  } finally {
    loading.value = false
  }
}

const handleToggleStatus = async (item: Customer) => {
  if (!userStore.canEdit) return
  const newStatus = item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await updateCustomer(item.id, { status: newStatus })
    item.status = newStatus
    uni.showToast({ title: `已${getDictValue('common_status', newStatus)}`, icon: 'none' })
  } catch (error) {
    console.error('切换状态失败:', error)
  }
}

const handleCreate = () => {
  editingId.value = null
  form.value = { name: '', code: '', contact: '', phone: '', address: '', sortOrder: 0, remark: '' }
  showEditPopup.value = true
}

const handleEdit = (item: Customer) => {
  editingId.value = item.id
  form.value = {
    name: item.name,
    code: item.code || '',
    contact: item.contact || '',
    phone: item.phone || '',
    address: item.address || '',
    sortOrder: item.sortOrder || 0,
    remark: item.remark || ''
  }
  showEditPopup.value = true
}

const handleSave = async () => {
  if (!form.value.name.trim()) {
    uni.showToast({ title: '请输入客户名称', icon: 'none' })
    return
  }
  try {
    if (editingId.value) {
      await updateCustomer(editingId.value, form.value)
      uni.showToast({ title: '更新成功', icon: 'none' })
    } else {
      await createCustomer(form.value)
      uni.showToast({ title: '创建成功', icon: 'none' })
    }
    showEditPopup.value = false
    loadData()
  } catch (error) {
    console.error('保存失败:', error)
  }
}

const handleDelete = (item: Customer) => {
  uni.showModal({
    title: '确认删除',
    content: `确定要删除客户"${item.name}"吗？`,
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteCustomer(item.id)
          uni.showToast({ title: '删除成功', icon: 'none' })
          loadData()
        } catch (error) {
          console.error('删除失败:', error)
        }
      }
    }
  })
}

onMounted(async () => {
  userStore.restoreSession()
  await loadAllDicts()
  loadData()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #F5F5F5;
  display: flex;
  flex-direction: column;
}

.navbar {
  height: 88rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  border-bottom: 1px solid #E5E5E5;
}

.navbar-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.navbar-action {
  padding: 12rpx 24rpx;
  background: #0D6E6E;
  border-radius: 8rpx;
}

.action-text {
  font-size: 26rpx;
  color: #FFFFFF;
}

.content {
  flex: 1;
  padding: 24rpx 32rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 24rpx;
}

.card.inactive {
  opacity: 0.6;
}

.card-main {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 16rpx;
}

.card-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon.ACTIVE {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.card-icon.INACTIVE {
  background: #E5E7EB;
  color: #9CA3AF;
}

.card-icon text {
  font-size: 32rpx;
  font-weight: 600;
}

.card-info {
  flex: 1;
}

.card-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 8rpx;
}

.card-meta {
  display: block;
  font-size: 24rpx;
  color: #888888;
}

.card-contact {
  display: flex;
  gap: 32rpx;
  margin-bottom: 16rpx;
  padding: 16rpx;
  background: #F9FAFB;
  border-radius: 8rpx;
}

.contact-text {
  font-size: 24rpx;
  color: #666666;
}

.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.action-btns {
  display: flex;
  gap: 24rpx;
}

.action-btn {
  font-size: 26rpx;
  color: #0D6E6E;
}

.action-btn.delete {
  color: #EF4444;
}

.card-remark {
  margin-top: 16rpx;
  padding: 16rpx;
  background: #F9FAFB;
  border-radius: 8rpx;
}

.card-remark text {
  font-size: 24rpx;
  color: #888888;
}

.empty {
  padding: 160rpx 0;
  text-align: center;
}

.empty-text {
  display: block;
  font-size: 28rpx;
  color: #999999;
  margin-bottom: 32rpx;
}

.empty-btn {
  width: 240rpx;
  height: 72rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 28rpx;
  border-radius: 12rpx;
  border: none;
}

.loading {
  padding: 160rpx 0;
  text-align: center;
}

.loading text {
  font-size: 28rpx;
  color: #666666;
}

/* 弹窗样式 */
.popup {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.popup-content {
  width: 90%;
  max-width: 640rpx;
  max-height: 80vh;
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-bottom: 1px solid #E5E5E5;
}

.popup-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.popup-close {
  font-size: 48rpx;
  color: #999999;
  line-height: 1;
}

.popup-body {
  flex: 1;
  padding: 32rpx;
  overflow-y: auto;
}

.form-item {
  margin-bottom: 32rpx;
}

.form-label {
  display: block;
  font-size: 28rpx;
  color: #666666;
  margin-bottom: 16rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  padding: 0 24rpx;
  border: 1px solid #E5E5E5;
  border-radius: 8rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.form-textarea {
  width: 100%;
  height: 160rpx;
  padding: 16rpx 24rpx;
  border: 1px solid #E5E5E5;
  border-radius: 8rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.popup-footer {
  display: flex;
  gap: 24rpx;
  padding: 24rpx 32rpx;
  border-top: 1px solid #E5E5E5;
}

.btn-cancel, .btn-confirm {
  flex: 1;
  height: 80rpx;
  font-size: 30rpx;
  border-radius: 12rpx;
  border: none;
}

.btn-cancel {
  background: #F5F5F5;
  color: #666666;
}

.btn-confirm {
  background: #0D6E6E;
  color: #FFFFFF;
}
</style>
