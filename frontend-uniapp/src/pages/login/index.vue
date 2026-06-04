<template>
  <view class="login-page">
    <view class="login-content">
      <view class="brand-section">
        <image
          v-if="logoUrl"
          :src="logoUrl"
          class="brand-logo"
          :class="logoSizeClass"
          mode="aspectFit"
        />
        <view v-else class="logo-placeholder">
          <text>P</text>
        </view>
        <text class="title">{{ themeConfig.systemName || '矿产品价格管理系统' }}</text>
        <text class="subtitle">{{ themeConfig.subtitleText || '企业价格展示与管理平台' }}</text>
      </view>

      <view class="login-panel">
        <view class="form-item">
          <view class="field-shell">
            <text class="field-label">账号</text>
            <input
              v-model="form.username"
              class="input"
              type="text"
              placeholder="请输入用户名"
              placeholder-class="placeholder"
              :adjust-position="true"
              :cursor-spacing="132"
            />
          </view>
        </view>
        <view class="form-item">
          <view class="field-shell password-wrapper">
            <text class="field-label">密码</text>
            <input
              v-model="form.password"
              class="input password-input"
              :password="!showPassword"
              placeholder="请输入密码"
              placeholder-class="placeholder"
              :adjust-position="true"
              :cursor-spacing="132"
            />
            <text class="password-toggle" @click="showPassword = !showPassword">
              {{ showPassword ? '隐藏' : '显示' }}
            </text>
          </view>
        </view>

        <view class="error-msg" v-if="errorMsg">
          <text>{{ errorMsg }}</text>
        </view>

        <button class="login-btn" :loading="loading" @click="handleLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </view>

      <text class="server-link" @click="openServerSettings">配置服务器地址</text>
    </view>

    <view v-if="showServerSettings" class="settings-mask" @click="closeServerSettings">
      <view class="settings-panel" @click.stop>
        <text class="settings-title">服务器设置</text>

        <view class="settings-field">
          <text class="settings-label">IP 地址</text>
          <input
            v-model="serverForm.ip"
            class="settings-input"
            type="text"
            placeholder="例如 127.0.0.1"
            placeholder-class="placeholder"
            :adjust-position="true"
            :cursor-spacing="132"
          />
        </view>

        <view class="settings-field">
          <text class="settings-label">端口号</text>
          <input
            v-model="serverForm.port"
            class="settings-input"
            type="number"
            placeholder="例如 8080"
            placeholder-class="placeholder"
            :adjust-position="true"
            :cursor-spacing="132"
          />
        </view>

        <view class="settings-actions">
          <button class="settings-btn secondary" @click="closeServerSettings">取消</button>
          <button class="settings-btn primary" @click="handleSaveServer">保存</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useTheme } from '@/composables/useTheme'
import {
  getServerConfig,
  isValidServerConfig,
  saveServerConfig,
  type ServerConfig
} from '@/utils/serverConfig'

const userStore = useUserStore()
const { themeConfig, loadThemeConfig } = useTheme()

const form = ref({ username: '', password: '' })
const showPassword = ref(false)
const loading = ref(false)
const errorMsg = ref('')
const showServerSettings = ref(false)
const serverForm = ref<ServerConfig>(getServerConfig())

const logoUrl = computed(() => themeConfig.value.logoUrlLogin || themeConfig.value.logoUrl)
const logoSizeClass = computed(() => `logo-${themeConfig.value.logoSizeLogin || themeConfig.value.logoSize || 'medium'}`)

const openServerSettings = () => {
  serverForm.value = getServerConfig()
  showServerSettings.value = true
}

const closeServerSettings = () => {
  showServerSettings.value = false
}

const handleSaveServer = () => {
  if (!isValidServerConfig(serverForm.value)) {
    uni.showToast({ title: '请输入正确的IP和端口', icon: 'none' })
    return
  }

  saveServerConfig(serverForm.value)
  showServerSettings.value = false
  uni.showToast({ title: '已保存', icon: 'success' })
}

// 账号密码登录
const handleLogin = async () => {
  errorMsg.value = ''

  if (!form.value.username.trim()) {
    errorMsg.value = '请输入用户名'
    return
  }
  if (!form.value.password) {
    errorMsg.value = '请输入密码'
    return
  }

  loading.value = true
  try {
    const success = await userStore.loginAction(form.value)
    if (success) {
      uni.switchTab({ url: '/pages/home/index' })
    } else {
      errorMsg.value = '用户名或密码错误'
    }
  } catch (error) {
    errorMsg.value = error instanceof Error && error.message ? error.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // 加载主题配置（获取Logo）
  loadThemeConfig()

  // 恢复会话
  userStore.restoreSession()

  // 已登录则跳转首页
  if (userStore.token) {
    uni.switchTab({ url: '/pages/home/index' })
  }
})
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  padding: env(safe-area-inset-top) 40rpx env(safe-area-inset-bottom);
  box-sizing: border-box;
  display: flex;
  align-items: stretch;
}

.login-content {
  min-height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 64rpx;
  padding: 40rpx 0 72rpx;
  box-sizing: border-box;
  transform: translateY(-44rpx);
}

.brand-section {
  text-align: center;
  padding: 0 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-logo {
  display: block;
  width: 280rpx;
  object-fit: contain;
  margin-bottom: 36rpx;
  transform: translateY(-16rpx);
}

.logo-small {
  width: 180rpx;
  height: 96rpx;
}

.logo-medium {
  width: 220rpx;
  height: 128rpx;
}

.logo-large {
  width: 260rpx;
  height: 164rpx;
}

.logo-xlarge {
  width: 300rpx;
  height: 200rpx;
}

.logo-placeholder {
  width: 128rpx;
  height: 128rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
}

.logo-placeholder text {
  font-size: 60rpx;
  font-weight: 700;
  color: #FFFFFF;
}

.title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #FFFFFF;
  line-height: 1.25;
  margin-bottom: 14rpx;
}

.subtitle {
  display: block;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.82);
}

.login-panel {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 28rpx 28rpx 32rpx;
  box-shadow: 0 24rpx 60rpx rgba(4, 55, 55, 0.18);
}

.server-link {
  display: block;
  align-self: center;
  margin-top: -44rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  text-decoration: underline;
}

.form-item {
  margin-bottom: 20rpx;
}

.field-shell {
  height: 92rpx;
  background: #F8FAFC;
  border: 1rpx solid #E5EDF0;
  border-radius: 16rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 22rpx;
}

.field-label {
  flex: 0 0 64rpx;
  color: #334155;
  font-size: 28rpx;
  font-weight: 600;
}

.password-wrapper {
  position: relative;
}

.input {
  flex: 1;
  min-width: 0;
  height: 88rpx;
  background: transparent;
  padding: 0;
  color: #1A1A1A;
  font-size: 30rpx;
  box-sizing: border-box;
}

.password-input {
  padding-right: 96rpx;
}

.placeholder {
  color: #94A3B8;
}

.password-toggle {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 26rpx;
  color: #0D6E6E;
}

.login-btn {
  width: 100%;
  height: 92rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 32rpx;
  font-weight: 700;
  border-radius: 14rpx;
  border: none;
  margin-top: 12rpx;
}

.login-btn:active {
  background: #0A5555;
}

.error-msg {
  margin: -4rpx 0 24rpx;
  padding: 20rpx 24rpx;
  background: #FFF7F7;
  border: 1rpx solid rgba(224, 59, 59, 0.18);
  border-radius: 14rpx;
  text-align: center;
}

.error-msg text {
  font-size: 28rpx;
  color: #E03B3B;
}

.settings-mask {
  position: fixed;
  inset: 0;
  z-index: 10;
  background: rgba(15, 23, 42, 0.36);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48rpx;
  box-sizing: border-box;
}

.settings-panel {
  width: 100%;
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 36rpx 32rpx 32rpx;
  box-sizing: border-box;
}

.settings-title {
  display: block;
  color: #1A1A1A;
  font-size: 34rpx;
  font-weight: 700;
  margin-bottom: 28rpx;
}

.settings-field {
  margin-bottom: 22rpx;
}

.settings-label {
  display: block;
  color: #334155;
  font-size: 26rpx;
  font-weight: 600;
  margin-bottom: 12rpx;
}

.settings-input {
  width: 100%;
  height: 88rpx;
  background: #F8FAFC;
  border: 1rpx solid #E5EDF0;
  border-radius: 14rpx;
  padding: 0 24rpx;
  color: #1A1A1A;
  font-size: 30rpx;
  box-sizing: border-box;
}

.settings-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 30rpx;
}

.settings-btn {
  flex: 1;
  height: 84rpx;
  border-radius: 14rpx;
  font-size: 30rpx;
  font-weight: 700;
  border: none;
}

.settings-btn.secondary {
  background: #F1F5F9;
  color: #475569;
}

.settings-btn.primary {
  background: #0D6E6E;
  color: #FFFFFF;
}
</style>
