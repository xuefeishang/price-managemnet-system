<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { useUserStore } from '@/store/useUserStore'
import { getRoleLabel } from '@/composables/useDict'
import { updateProfile, changePassword } from '@/api/auth'
import type { UpdateProfileRequest, ChangePasswordRequest } from '@/api/auth'

const userStore = useUserStore()
const router = useRouter()

// 响应式布局
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const isPCLayout = computed(() => windowWidth.value >= 1024)

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

// 标签页状态
const activeTab = ref('profile')
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
      break
  }
}

// 角色名称映射（从字典服务获取）
const getRoleName = (role?: string) => {
  return role ? getRoleLabel(role) : ''
}

// 获取角色颜色类
const getRoleClass = (role?: string) => {
  const map: Record<string, string> = {
    ADMIN: 'admin',
    EDITOR: 'editor',
    VIEWER: 'viewer'
  }
  return role ? map[role] : ''
}

// ==================== 个人信息编辑 ====================
const showEditProfile = ref(false)
const editForm = ref({
  nickname: '',
  email: '',
  phone: ''
})
const editLoading = ref(false)

const openEditProfile = () => {
  editForm.value = {
    nickname: userStore.user?.nickname || '',
    email: userStore.user?.email || '',
    phone: userStore.user?.phone || ''
  }
  showEditProfile.value = true
}

const handleSaveProfile = async () => {
  if (!editForm.value.nickname.trim()) {
    showToast('昵称不能为空')
    return
  }

  editLoading.value = true
  try {
    const data: UpdateProfileRequest = {
      nickname: editForm.value.nickname,
      email: editForm.value.email || undefined,
      phone: editForm.value.phone || undefined
    }
    await updateProfile(data)
    await userStore.fetchProfile()
    showEditProfile.value = false
    showToast('个人信息已更新')
  } catch (error: any) {
    showToast(error?.message || '更新失败')
  } finally {
    editLoading.value = false
  }
}

// ==================== 修改密码 ====================
const showChangePassword = ref(false)
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordLoading = ref(false)
const showPasswordFields = ref({
  old: false,
  new: false,
  confirm: false
})

// 密码强度检测
const passwordStrength = computed(() => {
  const pwd = passwordForm.value.newPassword
  if (!pwd) return { level: 0, text: '', color: '' }

  let strength = 0
  if (pwd.length >= 6) strength++
  if (pwd.length >= 8) strength++
  if (/[a-z]/.test(pwd) && /[A-Z]/.test(pwd)) strength++
  if (/\d/.test(pwd)) strength++
  if (/[!@#$%^&*(),.?":{}|<>]/.test(pwd)) strength++

  if (strength <= 2) return { level: 1, text: '弱', color: '#ff4d4f' }
  if (strength <= 3) return { level: 2, text: '中', color: '#faad14' }
  return { level: 3, text: '强', color: '#52c41a' }
})

const openChangePassword = () => {
  passwordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
  showChangePassword.value = true
}

const handleChangePassword = async () => {
  if (!passwordForm.value.oldPassword) {
    showToast('请输入旧密码')
    return
  }
  if (!passwordForm.value.newPassword) {
    showToast('请输入新密码')
    return
  }
  if (passwordForm.value.newPassword.length < 6) {
    showToast('新密码长度不能少于6位')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    showToast('两次输入的密码不一致')
    return
  }
  if (passwordForm.value.oldPassword === passwordForm.value.newPassword) {
    showToast('新密码不能与旧密码相同')
    return
  }

  passwordLoading.value = true
  try {
    const data: ChangePasswordRequest = {
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
      confirmPassword: passwordForm.value.confirmPassword
    }
    await changePassword(data)
    showChangePassword.value = false
    showDialog({
      title: '提示',
      message: '密码修改成功，请重新登录'
    }).then(() => {
      userStore.logoutAction()
      router.push('/login')
    })
  } catch (error: any) {
    showToast(error?.message || '密码修改失败')
  } finally {
    passwordLoading.value = false
  }
}

// ==================== 系统设置 ====================
const showSettings = ref(false)
const settings = ref({
  theme: 'light',
  autoRefresh: true,
  refreshInterval: 30
})

// 保存设置
const handleSaveSettings = () => {
  localStorage.setItem('userSettings', JSON.stringify(settings.value))
  showSettings.value = false
  showToast('设置已保存')
}

// 加载设置
const loadSettings = () => {
  const saved = localStorage.getItem('userSettings')
  if (saved) {
    try {
      settings.value = JSON.parse(saved)
    } catch (e) {
      // ignore
    }
  }
}

// ==================== 退出登录 ====================
const handleLogout = () => {
  showDialog({
    title: '提示',
    message: '确定要退出登录吗？',
    showCancelButton: true
  }).then(() => {
    userStore.logoutAction()
    router.push('/login')
  }).catch(() => {
    // 取消
  })
}

// ==================== 生命周期 ====================
onMounted(async () => {
  window.addEventListener('resize', handleResize)
  loadSettings()

  // 如果用户信息为空，先获取
  if (!userStore.user) {
    try {
      await userStore.fetchProfile()
    } catch (e) {
      // ignore
    }
  }
})
</script>

<template>
  <div class="profile-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-profile">
        <h1 class="page-title-pc">个人中心</h1>

        <div class="profile-grid-pc">
          <!-- 用户信息卡片 -->
          <div class="user-card-pc">
            <div class="user-avatar-pc">
              {{ userStore.user?.nickname?.charAt(0) || 'U' }}
            </div>
            <div class="user-name-pc">{{ userStore.user?.nickname }}</div>
            <div class="user-username-pc">@{{ userStore.user?.username }}</div>
            <div class="user-role-pc" :class="getRoleClass(userStore.user?.role)">
              {{ getRoleName(userStore.user?.role) }}
            </div>
          </div>

          <!-- 设置卡片 -->
          <div class="settings-card-pc">
            <h2 class="card-title-pc">账户设置</h2>
            <div class="settings-list-pc">
              <div class="setting-item-pc" @click="openEditProfile">
                <div class="setting-left">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                    <circle cx="12" cy="7" r="4"/>
                  </svg>
                  <span>个人信息</span>
                </div>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </div>
              <div class="setting-divider"></div>
              <div class="setting-item-pc" @click="openChangePassword">
                <div class="setting-left">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                  </svg>
                  <span>修改密码</span>
                </div>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </div>
              <div class="setting-divider"></div>
              <div class="setting-item-pc" @click="showSettings = true">
                <div class="setting-left">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="3"/>
                    <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
                  </svg>
                  <span>系统设置</span>
                </div>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </div>
            </div>
          </div>

          <!-- 账户信息卡片 -->
          <div class="info-card-pc">
            <h2 class="card-title-pc">账户信息</h2>
            <div class="info-list-pc">
              <div class="info-item-pc">
                <span class="info-label">用户名</span>
                <span class="info-value">{{ userStore.user?.username }}</span>
              </div>
              <div class="info-item-pc">
                <span class="info-label">昵称</span>
                <span class="info-value">{{ userStore.user?.nickname || '-' }}</span>
              </div>
              <div class="info-item-pc">
                <span class="info-label">邮箱</span>
                <span class="info-value">{{ userStore.user?.email || '-' }}</span>
              </div>
              <div class="info-item-pc">
                <span class="info-label">手机号</span>
                <span class="info-value">{{ userStore.user?.phone || '-' }}</span>
              </div>
              <div class="info-item-pc">
                <span class="info-label">角色</span>
                <span class="info-value">{{ getRoleName(userStore.user?.role) }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 退出登录 -->
        <button class="logout-btn-pc" @click="handleLogout">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          退出登录
        </button>
      </div>

      <!-- 编辑个人信息弹窗 -->
      <van-popup v-model:show="showEditProfile" position="center" :style="{ width: '400px', borderRadius: '12px' }">
        <div class="modal-content">
          <h3 class="modal-title">编辑个人信息</h3>
          <div class="modal-form">
            <div class="form-item">
              <label>昵称</label>
              <input v-model="editForm.nickname" type="text" placeholder="请输入昵称" />
            </div>
            <div class="form-item">
              <label>邮箱</label>
              <input v-model="editForm.email" type="email" placeholder="请输入邮箱（选填）" />
            </div>
            <div class="form-item">
              <label>手机号</label>
              <input v-model="editForm.phone" type="tel" placeholder="请输入手机号（选填）" />
            </div>
          </div>
          <div class="modal-actions">
            <button class="btn-cancel" @click="showEditProfile = false">取消</button>
            <button class="btn-confirm" @click="handleSaveProfile" :disabled="editLoading">
              {{ editLoading ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </van-popup>

      <!-- 修改密码弹窗 -->
      <van-popup v-model:show="showChangePassword" position="center" :style="{ width: '400px', borderRadius: '12px' }">
        <div class="modal-content">
          <h3 class="modal-title">修改密码</h3>
          <div class="modal-form">
            <div class="form-item">
              <label>旧密码</label>
              <div class="password-input-wrap">
                <input
                  v-model="passwordForm.oldPassword"
                  :type="showPasswordFields.old ? 'text' : 'password'"
                  placeholder="请输入旧密码"
                />
                <button class="eye-btn" @click="showPasswordFields.old = !showPasswordFields.old">
                  <svg v-if="!showPasswordFields.old" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
            </div>
            <div class="form-item">
              <label>新密码</label>
              <div class="password-input-wrap">
                <input
                  v-model="passwordForm.newPassword"
                  :type="showPasswordFields.new ? 'text' : 'password'"
                  placeholder="请输入新密码（6-20位）"
                />
                <button class="eye-btn" @click="showPasswordFields.new = !showPasswordFields.new">
                  <svg v-if="!showPasswordFields.new" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
              <!-- 密码强度指示 -->
              <div v-if="passwordForm.newPassword" class="password-strength">
                <div class="strength-bar">
                  <div
                    class="strength-fill"
                    :style="{ width: (passwordStrength.level / 3 * 100) + '%', backgroundColor: passwordStrength.color }"
                  ></div>
                </div>
                <span class="strength-text" :style="{ color: passwordStrength.color }">{{ passwordStrength.text }}</span>
              </div>
            </div>
            <div class="form-item">
              <label>确认密码</label>
              <div class="password-input-wrap">
                <input
                  v-model="passwordForm.confirmPassword"
                  :type="showPasswordFields.confirm ? 'text' : 'password'"
                  placeholder="请再次输入新密码"
                />
                <button class="eye-btn" @click="showPasswordFields.confirm = !showPasswordFields.confirm">
                  <svg v-if="!showPasswordFields.confirm" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
          <div class="modal-actions">
            <button class="btn-cancel" @click="showChangePassword = false">取消</button>
            <button class="btn-confirm" @click="handleChangePassword" :disabled="passwordLoading">
              {{ passwordLoading ? '修改中...' : '确认修改' }}
            </button>
          </div>
        </div>
      </van-popup>

      <!-- 系统设置弹窗 -->
      <van-popup v-model:show="showSettings" position="center" :style="{ width: '400px', borderRadius: '12px' }">
        <div class="modal-content">
          <h3 class="modal-title">系统设置</h3>
          <div class="modal-form">
            <div class="form-item">
              <label>显示模式</label>
              <div class="radio-group">
                <label class="radio-item">
                  <input type="radio" v-model="settings.theme" value="light" />
                  <span>浅色模式</span>
                </label>
                <label class="radio-item">
                  <input type="radio" v-model="settings.theme" value="dark" />
                  <span>深色模式</span>
                </label>
              </div>
            </div>
            <div class="form-item">
              <label>自动刷新数据</label>
              <div class="switch-wrap">
                <van-switch v-model="settings.autoRefresh" size="20" />
              </div>
            </div>
            <div class="form-item" v-if="settings.autoRefresh">
              <label>刷新间隔（秒）</label>
              <div class="stepper-wrap">
                <van-stepper v-model="settings.refreshInterval" min="10" max="300" step="10" />
              </div>
            </div>
          </div>
          <div class="modal-actions">
            <button class="btn-cancel" @click="showSettings = false">取消</button>
            <button class="btn-confirm" @click="handleSaveSettings">保存设置</button>
          </div>
        </div>
      </van-popup>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 主内容区 -->
      <main class="content">
        <!-- 用户卡片 -->
        <div class="user-card">
          <div class="user-avatar">
            {{ userStore.user?.nickname?.charAt(0) || 'U' }}
          </div>
          <div class="user-info">
            <span class="user-name">{{ userStore.user?.nickname }}</span>
            <span class="user-role-badge" :class="getRoleClass(userStore.user?.role)">
              {{ getRoleName(userStore.user?.role) }}
            </span>
          </div>
        </div>

        <!-- 菜单列表 -->
        <div class="menu-card">
          <div class="menu-item" @click="openEditProfile">
            <div class="menu-left">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
                <circle cx="12" cy="7" r="4"/>
              </svg>
              <span>个人信息</span>
            </div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </div>

          <div class="menu-divider"></div>

          <div class="menu-item" @click="openChangePassword">
            <div class="menu-left">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
              </svg>
              <span>修改密码</span>
            </div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </div>

          <div class="menu-divider"></div>

          <div class="menu-item" @click="showSettings = true">
            <div class="menu-left">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="3"/>
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
              </svg>
              <span>系统设置</span>
            </div>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="9 18 15 12 9 6"/>
            </svg>
          </div>
        </div>

        <!-- 退出登录 -->
        <button class="logout-btn" @click="handleLogout">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          退出登录
        </button>
      </main>

      <!-- 底部标签栏 -->
      <footer class="tab-bar">
        <button class="tab-item" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item active" @click="switchTab('profile')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span class="tab-label">我的</span>
        </button>
      </footer>

      <!-- 编辑个人信息弹窗 -->
      <van-popup v-model:show="showEditProfile" position="bottom" :style="{ height: 'auto' }">
        <div class="mobile-modal">
          <div class="mobile-modal-header">
            <span class="mobile-modal-title">编辑个人信息</span>
            <button class="close-btn" @click="showEditProfile = false">&times;</button>
          </div>
          <div class="mobile-modal-content">
            <div class="form-item">
              <label>昵称</label>
              <input v-model="editForm.nickname" type="text" placeholder="请输入昵称" />
            </div>
            <div class="form-item">
              <label>邮箱</label>
              <input v-model="editForm.email" type="email" placeholder="请输入邮箱（选填）" />
            </div>
            <div class="form-item">
              <label>手机号</label>
              <input v-model="editForm.phone" type="tel" placeholder="请输入手机号（选填）" />
            </div>
          </div>
          <div class="mobile-modal-footer">
            <button class="full-btn" @click="handleSaveProfile" :disabled="editLoading">
              {{ editLoading ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </van-popup>

      <!-- 修改密码弹窗 -->
      <van-popup v-model:show="showChangePassword" position="bottom" :style="{ height: 'auto' }">
        <div class="mobile-modal">
          <div class="mobile-modal-header">
            <span class="mobile-modal-title">修改密码</span>
            <button class="close-btn" @click="showChangePassword = false">&times;</button>
          </div>
          <div class="mobile-modal-content">
            <div class="form-item">
              <label>旧密码</label>
              <div class="password-input-wrap">
                <input
                  v-model="passwordForm.oldPassword"
                  :type="showPasswordFields.old ? 'text' : 'password'"
                  placeholder="请输入旧密码"
                />
                <button class="eye-btn" @click="showPasswordFields.old = !showPasswordFields.old">
                  <svg v-if="!showPasswordFields.old" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
            </div>
            <div class="form-item">
              <label>新密码</label>
              <div class="password-input-wrap">
                <input
                  v-model="passwordForm.newPassword"
                  :type="showPasswordFields.new ? 'text' : 'password'"
                  placeholder="请输入新密码（6-20位）"
                />
                <button class="eye-btn" @click="showPasswordFields.new = !showPasswordFields.new">
                  <svg v-if="!showPasswordFields.new" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
              <div v-if="passwordForm.newPassword" class="password-strength">
                <div class="strength-bar">
                  <div
                    class="strength-fill"
                    :style="{ width: (passwordStrength.level / 3 * 100) + '%', backgroundColor: passwordStrength.color }"
                  ></div>
                </div>
                <span class="strength-text" :style="{ color: passwordStrength.color }">{{ passwordStrength.text }}</span>
              </div>
            </div>
            <div class="form-item">
              <label>确认密码</label>
              <div class="password-input-wrap">
                <input
                  v-model="passwordForm.confirmPassword"
                  :type="showPasswordFields.confirm ? 'text' : 'password'"
                  placeholder="请再次输入新密码"
                />
                <button class="eye-btn" @click="showPasswordFields.confirm = !showPasswordFields.confirm">
                  <svg v-if="!showPasswordFields.confirm" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>
                  </svg>
                  <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
          <div class="mobile-modal-footer">
            <button class="full-btn" @click="handleChangePassword" :disabled="passwordLoading">
              {{ passwordLoading ? '修改中...' : '确认修改' }}
            </button>
          </div>
        </div>
      </van-popup>

      <!-- 系统设置弹窗 -->
      <van-popup v-model:show="showSettings" position="bottom" :style="{ height: 'auto' }">
        <div class="mobile-modal">
          <div class="mobile-modal-header">
            <span class="mobile-modal-title">系统设置</span>
            <button class="close-btn" @click="showSettings = false">&times;</button>
          </div>
          <div class="mobile-modal-content">
            <div class="form-item">
              <label>显示模式</label>
              <div class="radio-group">
                <label class="radio-item">
                  <input type="radio" v-model="settings.theme" value="light" />
                  <span>浅色模式</span>
                </label>
                <label class="radio-item">
                  <input type="radio" v-model="settings.theme" value="dark" />
                  <span>深色模式</span>
                </label>
              </div>
            </div>
            <div class="form-item">
              <label>自动刷新数据</label>
              <div class="switch-wrap">
                <van-switch v-model="settings.autoRefresh" size="20" />
              </div>
            </div>
            <div class="form-item" v-if="settings.autoRefresh">
              <label>刷新间隔（秒）</label>
              <div class="stepper-wrap">
                <van-stepper v-model="settings.refreshInterval" min="10" max="300" step="10" />
              </div>
            </div>
          </div>
          <div class="mobile-modal-footer">
            <button class="full-btn" @click="handleSaveSettings">保存设置</button>
          </div>
        </div>
      </van-popup>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.profile-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

/* ==================== PC布局 ==================== */
.pc-profile {
  padding: 32px;
  max-width: 900px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0 0 32px 0;
}

.profile-grid-pc {
  display: grid;
  grid-template-columns: 280px 1fr;
  grid-template-rows: auto auto;
  gap: 24px;
  margin-bottom: 32px;
}

.user-card-pc {
  grid-row: 1 / 3;
  background: #FFFFFF;
  border-radius: 12px;
  padding: 32px;
  border: 1px solid #E5E5E5;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.user-avatar-pc {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', sans-serif;
  font-size: 32px;
  font-weight: 600;
  margin-bottom: 16px;
}

.user-name-pc {
  font-family: 'Inter', sans-serif;
  font-size: 20px;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 4px;
}

.user-username-pc {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #888888;
  margin-bottom: 16px;
}

.user-role-pc {
  padding: 6px 16px;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
}

.user-role-pc.admin {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.user-role-pc.editor {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.user-role-pc.viewer {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.settings-card-pc,
.info-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.card-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 16px 0;
}

.settings-list-pc {
  display: flex;
  flex-direction: column;
}

.setting-item-pc {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  cursor: pointer;
}

.setting-item-pc:hover {
  background: #FAFAFA;
  margin: 0 -12px;
  padding-left: 12px;
  padding-right: 12px;
  border-radius: 8px;
}

.setting-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.setting-left svg {
  color: #666666;
}

.setting-left span {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
}

.settings-card-pc svg:last-child {
  color: #888888;
}

.setting-divider {
  height: 1px;
  background: #F5F5F5;
}

.info-list-pc {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
}

.info-label {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
}

.info-value {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  font-weight: 500;
}

.logout-btn-pc {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 16px;
  background: #FFFFFF;
  color: #E07B54;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.logout-btn-pc:hover {
  background: rgba(224, 123, 84, 0.1);
}

/* ==================== 移动端布局 ==================== */
.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-bottom: 100px;
}

.user-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #0D6E6E;
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Inter', sans-serif;
  font-size: 32px;
  font-weight: 600;
}

.user-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.user-name {
  font-family: 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #1A1A1A;
}

.user-role-badge {
  padding: 4px 12px;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
}

.user-role-badge.admin {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.user-role-badge.editor {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.user-role-badge.viewer {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.menu-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 0 16px;
  border: 1px solid #E5E5E5;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0;
  cursor: pointer;
}

.menu-item svg:first-child {
  color: #666666;
}

.menu-item svg:last-child {
  color: #888888;
}

.menu-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-left span {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
}

.menu-divider {
  height: 1px;
  background: #F5F5F5;
}

.logout-btn {
  width: 100%;
  padding: 14px;
  background: #FFFFFF;
  color: #E07B54;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: all 150ms;
}

.logout-btn:hover {
  background: rgba(224, 123, 84, 0.1);
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
  right: 0;
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

/* ==================== 弹窗样式 ==================== */
.modal-content {
  padding: 24px;
}

.modal-title {
  font-family: 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 20px 0;
  text-align: center;
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-item label {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #1A1A1A;
}

.form-item input[type="text"],
.form-item input[type="email"],
.form-item input[type="tel"],
.form-item input[type="password"] {
  width: 100%;
  height: 44px;
  padding: 0 16px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  box-sizing: border-box;
}

.form-item input:focus {
  outline: none;
  border-color: #0D6E6E;
}

.password-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.password-input-wrap input {
  width: 100%;
  padding-right: 44px;
}

.eye-btn {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  cursor: pointer;
  color: #888888;
  padding: 4px;
  display: flex;
  align-items: center;
}

.password-strength {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}

.strength-bar {
  flex: 1;
  height: 4px;
  background: #E5E5E5;
  border-radius: 2px;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  transition: all 300ms;
}

.strength-text {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  font-weight: 500;
}

.modal-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.btn-cancel,
.btn-confirm {
  flex: 1;
  height: 44px;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.btn-cancel {
  background: #FFFFFF;
  color: #666666;
  border: 1px solid #E5E5E5;
}

.btn-cancel:hover {
  background: #F5F5F5;
}

.btn-confirm {
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
}

.btn-confirm:hover:not(:disabled) {
  background: #0D8A8A;
}

.btn-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 单选框组 */
.radio-group {
  display: flex;
  gap: 24px;
}

.radio-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.radio-item input[type="radio"] {
  width: 18px;
  height: 18px;
  accent-color: #0D6E6E;
}

.radio-item span {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
}

/* 移动端弹窗 */
.mobile-modal {
  padding: 16px;
}

.mobile-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.mobile-modal-title {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
}

.close-btn {
  background: none;
  border: none;
  font-size: 24px;
  color: #888888;
  cursor: pointer;
  padding: 0;
  line-height: 1;
}

.mobile-modal-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.mobile-modal-footer {
  margin-top: 24px;
}

.full-btn {
  width: 100%;
  height: 44px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.full-btn:hover:not(:disabled) {
  background: #0D8A8A;
}

.full-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.switch-wrap,
.stepper-wrap {
  display: flex;
  align-items: center;
}

@media (max-width: 1024px) {
  .pc-profile {
    display: none;
  }
}

@media (max-width: 480px) {
  .tab-bar {
    width: 100%;
    left: 0;
    transform: none;
  }
}
</style>
