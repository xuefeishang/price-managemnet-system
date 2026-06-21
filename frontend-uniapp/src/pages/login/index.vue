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

        <view class="remember-row" @click="toggleRememberCredentials">
          <view class="remember-check" :class="{ checked: rememberCredentials }">
            <text v-if="rememberCredentials">✓</text>
          </view>
          <view class="remember-copy">
            <text class="remember-title">记住账号和密码</text>
            <text class="remember-hint">仅保存在当前设备，并按服务器环境区分</text>
          </view>
        </view>

        <view class="error-msg" v-if="errorMsg">
          <text>{{ errorMsg }}</text>
        </view>

        <button class="login-btn" :loading="loading" @click="handleLogin">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </view>

      <!-- 网络模式切换入口 -->
      <view class="network-switch-section">
        <text class="network-status" @click="openNetworkSettings">
          当前环境：{{ currentNetworkLabel }}
        </text>
        <text class="network-hint" @click="openNetworkSettings">{{ networkSwitchHint }}</text>
      </view>
    </view>

    <!-- 网络设置弹窗 -->
    <view v-if="showNetworkSettings" class="settings-mask" @click="closeNetworkSettings">
      <view class="settings-panel" @click.stop>
        <text class="settings-title">网络设置</text>

        <!-- 网络模式选择 -->
        <view class="network-options">
          <view
            v-for="option in networkOptions"
            :key="option.value"
            class="network-option"
            :class="{ active: selectedNetworkMode === option.value }"
            @click="selectNetworkMode(option.value)"
          >
            <view class="option-header">
              <text class="option-title">{{ option.label }}</text>
              <view v-if="selectedNetworkMode === option.value" class="option-check">
                <text>✓</text>
              </view>
            </view>
            <text class="option-desc">{{ option.description }}</text>
          </view>
        </view>

        <!-- 当前服务器地址显示 -->
        <view class="current-address">
          <text class="address-label">{{ currentAddressLabel }}</text>
          <text class="address-value">{{ currentServerAddress }}</text>
        </view>

        <!-- 小程序自定义地址 -->
        <view v-if="customServerSelected" class="custom-section">
          <view class="settings-field">
            <text class="settings-label">网址</text>
            <input
              v-model="manualServerForm.siteUrl"
              class="settings-input"
              type="text"
              placeholder="例如 https://example.com"
              placeholder-class="placeholder"
            />
          </view>

          <view class="settings-field">
            <text class="settings-label">端口</text>
            <input
              v-model="manualServerForm.port"
              class="settings-input"
              type="number"
              placeholder="例如 32080"
              placeholder-class="placeholder"
            />
          </view>
        </view>

        <!-- 高级设置：手动输入 IP 和端口 -->
        <view v-else-if="manualServerEnabled" class="advanced-section">
          <text class="advanced-toggle" @click="showAdvanced = !showAdvanced">
            {{ showAdvanced ? '收起高级设置' : '展开高级设置（手动输入）' }}
          </text>

          <view v-if="showAdvanced" class="advanced-fields">
            <view class="settings-field">
              <text class="settings-label">域名或 IP 地址</text>
              <input
                v-model="manualServerForm.ip"
                class="settings-input"
                type="text"
                placeholder="例如 example.com"
                placeholder-class="placeholder"
              />
            </view>

            <view class="settings-field">
              <text class="settings-label">端口号</text>
              <input
                v-model="manualServerForm.port"
                class="settings-input"
                type="number"
                placeholder="HTTPS 默认 443"
                placeholder-class="placeholder"
              />
            </view>

            <button class="manual-save-btn" @click="handleSaveManualServer">保存手动配置</button>
          </view>
        </view>

        <view class="settings-actions">
          <button class="settings-btn secondary" @click="closeNetworkSettings">关闭</button>
          <button class="settings-btn primary" :loading="detecting" @click="handleApplyNetwork">
            {{ detecting ? '检测中...' : '应用' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { onShareAppMessage } from '@dcloudio/uni-app'
import { useUserStore } from '@/store/useUserStore'
import { useTheme } from '@/composables/useTheme'
import {
  getServerConfig,
  isValidServerConfig,
  saveServerConfig,
  getNetworkMode,
  setNetworkMode,
  switchNetworkMode,
  initNetworkDetection,
  getInternalBaseUrl,
  getExternalBaseUrl,
  getApiBaseUrl,
  isMiniProgramEnvironmentSwitchEnabled,
  type ServerConfig,
  type NetworkMode
} from '@/utils/serverConfig'
import { getMiniappEntryShareMessage, showMiniappEntryShareMenu } from '@/utils/share'

const userStore = useUserStore()
const { themeConfig, loadThemeConfig } = useTheme()

const form = ref({ username: '', password: '' })
const showPassword = ref(false)
const rememberCredentials = ref(false)
const loading = ref(false)
const errorMsg = ref('')
const REMEMBERED_CREDENTIALS_KEY = 'rememberedLoginCredentials'

interface RememberedCredentials {
  username: string
  password: string
}

type RememberedCredentialsByServer = Record<string, RememberedCredentials>

// 网络设置相关
const showNetworkSettings = ref(false)
const showAdvanced = ref(false)
const detecting = ref(false)
const selectedNetworkMode = ref<NetworkMode>('auto')
const manualServerForm = ref<ServerConfig>({ ip: '', port: '' })
const networkStateVersion = ref(0)
const miniProgramEnvironmentSwitchEnabled = isMiniProgramEnvironmentSwitchEnabled()
const manualServerEnabled = import.meta.env.DEV && !miniProgramEnvironmentSwitchEnabled
let miniProgramRealDevice = false
// #ifdef MP-WEIXIN
miniProgramRealDevice = uni.getSystemInfoSync().platform !== 'devtools'
// #endif

const logoUrl = computed(() => themeConfig.value.logoUrlLogin || themeConfig.value.logoUrl)
const logoSizeClass = computed(() => `logo-${themeConfig.value.logoSizeLogin || themeConfig.value.logoSize || 'medium'}`)
const customServerSelected = computed(() => miniProgramEnvironmentSwitchEnabled && selectedNetworkMode.value === 'custom')
const networkSwitchHint = computed(() => miniProgramEnvironmentSwitchEnabled ? '点击切换正式地址/自定义' : '点击切换测试/正式环境')

// 网络选项配置
const networkOptions = computed<Array<{ value: NetworkMode; label: string; description: string }>>(() => {
  if (miniProgramEnvironmentSwitchEnabled) {
    return [
      {
        value: 'external',
        label: '正式地址',
        description: '使用系统默认正式服务'
      },
      {
        value: 'custom',
        label: '自定义',
        description: '手动填写网址和端口'
      }
    ]
  }

  return [
    {
      value: 'auto',
      label: '自动检测',
      description: '系统自动检测网络环境，优先使用内网'
    },
    {
      value: 'internal',
      label: '内网模式',
      description: `公司 WiFi 内网访问 (${getInternalBaseUrl()})`
    },
    {
      value: 'external',
      label: '外网模式',
      description: `外网远程访问 (${getExternalBaseUrl()})`
    }
  ]
})

// 当前网络模式标签
const currentNetworkLabel = computed(() => {
  networkStateVersion.value
  const mode = getNetworkMode()
  const labels: Record<NetworkMode, string> = {
    'auto': '自动检测',
    'internal': '内网',
    'external': miniProgramEnvironmentSwitchEnabled ? '正式地址' : '外网',
    'dev': '开发',
    'custom': '自定义'
  }
  return labels[mode] || '自动检测'
})

const currentAddressLabel = computed(() => {
  networkStateVersion.value
  if (!miniProgramEnvironmentSwitchEnabled) return '当前服务器地址'
  return getNetworkMode() === 'custom' ? '当前自定义地址' : '当前连接'
})

// 当前服务器地址
const currentServerAddress = computed(() => {
  networkStateVersion.value
  if (miniProgramEnvironmentSwitchEnabled && getNetworkMode() !== 'custom') {
    return '正式地址'
  }
  return getApiBaseUrl()
})

const getRememberedCredentialsByServer = (): RememberedCredentialsByServer => {
  try {
    const stored = uni.getStorageSync(REMEMBERED_CREDENTIALS_KEY)
    if (!stored) return {}
    return typeof stored === 'string'
      ? JSON.parse(stored) as RememberedCredentialsByServer
      : stored as RememberedCredentialsByServer
  } catch {
    return {}
  }
}

const restoreRememberedCredentials = () => {
  const credentials = getRememberedCredentialsByServer()[getApiBaseUrl()]
  if (!credentials?.username || !credentials?.password) {
    rememberCredentials.value = false
    return
  }
  form.value.username = credentials.username
  form.value.password = credentials.password
  rememberCredentials.value = true
}

const removeRememberedCredentials = () => {
  const credentialsByServer = getRememberedCredentialsByServer()
  delete credentialsByServer[getApiBaseUrl()]
  if (Object.keys(credentialsByServer).length === 0) {
    uni.removeStorageSync(REMEMBERED_CREDENTIALS_KEY)
    return
  }
  uni.setStorageSync(REMEMBERED_CREDENTIALS_KEY, credentialsByServer)
}

const saveRememberedCredentials = () => {
  const credentialsByServer = getRememberedCredentialsByServer()
  credentialsByServer[getApiBaseUrl()] = {
    username: form.value.username.trim(),
    password: form.value.password
  }
  uni.setStorageSync(REMEMBERED_CREDENTIALS_KEY, credentialsByServer)
}

const toggleRememberCredentials = () => {
  rememberCredentials.value = !rememberCredentials.value
  if (!rememberCredentials.value) {
    removeRememberedCredentials()
  }
}

const openNetworkSettings = () => {
  selectedNetworkMode.value = getNetworkMode()
  manualServerForm.value = miniProgramEnvironmentSwitchEnabled && getNetworkMode() !== 'custom'
    ? { ip: '', port: '', siteUrl: '' }
    : getServerConfig()
  showAdvanced.value = false
  showNetworkSettings.value = true
}

const closeNetworkSettings = () => {
  showNetworkSettings.value = false
}

const selectNetworkMode = (mode: NetworkMode) => {
  if (miniProgramRealDevice && mode === 'dev') {
    uni.showToast({
      title: '真机请选择正式地址或自定义',
      icon: 'none'
    })
    return
  }
  if (miniProgramEnvironmentSwitchEnabled && mode === 'custom' && getNetworkMode() !== 'custom') {
    manualServerForm.value = { ip: '', port: '', siteUrl: '' }
  }
  selectedNetworkMode.value = mode
}

const handleApplyNetwork = async () => {
  if (miniProgramRealDevice && selectedNetworkMode.value === 'dev') {
    selectedNetworkMode.value = 'external'
    uni.showToast({
      title: '真机请选择正式地址或自定义',
      icon: 'none'
    })
    return
  }

  if (customServerSelected.value && !isValidServerConfig(manualServerForm.value)) {
    uni.showToast({
      title: '请输入正确的网址和端口',
      icon: 'none'
    })
    return
  }

  detecting.value = true

  try {
    if (customServerSelected.value) {
      setNetworkMode('custom')
      saveServerConfig(manualServerForm.value)
    } else {
      await switchNetworkMode(selectedNetworkMode.value)
    }
    networkStateVersion.value += 1
    form.value = { username: '', password: '' }
    restoreRememberedCredentials()
    closeNetworkSettings()
    uni.showToast({
      title: `已切换到${currentNetworkLabel.value}`,
      icon: 'success'
    })
  } catch (error) {
    uni.showToast({
      title: '网络切换失败',
      icon: 'none'
    })
  } finally {
    detecting.value = false
  }
}

const handleSaveManualServer = () => {
  if (!isValidServerConfig(manualServerForm.value)) {
    uni.showToast({ title: '请输入正确的域名/IP和端口', icon: 'none' })
    return
  }

  saveServerConfig(manualServerForm.value)
  setNetworkMode(miniProgramEnvironmentSwitchEnabled ? 'custom' : 'external')
  networkStateVersion.value += 1
  form.value = { username: '', password: '' }
  restoreRememberedCredentials()
  showAdvanced.value = false
  closeNetworkSettings()
  uni.showToast({ title: '已保存手动配置', icon: 'success' })
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
      if (rememberCredentials.value) {
        saveRememberedCredentials()
      } else {
        removeRememberedCredentials()
      }
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

onShareAppMessage(() => getMiniappEntryShareMessage(themeConfig.value.systemName))

onMounted(async () => {
  // 127.0.0.1 在真机上指向手机自身，不能沿用开发者工具保存的测试环境。
  if (miniProgramRealDevice && getNetworkMode() === 'dev') {
    await switchNetworkMode('external')
    networkStateVersion.value += 1
  }

  // 先确定请求环境，避免主题配置和登录请求落到不同服务器。
  await initNetworkDetection()
  networkStateVersion.value += 1
  restoreRememberedCredentials()
  showMiniappEntryShareMenu(['shareAppMessage'])

  // 加载主题配置（获取Logo）
  await loadThemeConfig()

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
  gap: 48rpx;
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

.remember-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 2rpx 0 20rpx;
  padding: 8rpx 2rpx;
}

.remember-check {
  width: 34rpx;
  height: 34rpx;
  flex: 0 0 34rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #CBD5E1;
  border-radius: 8rpx;
  background: #FFFFFF;
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1;
}

.remember-check.checked {
  border-color: #0D6E6E;
  background: #0D6E6E;
}

.remember-copy,
.remember-title,
.remember-hint {
  display: block;
}

.remember-copy {
  min-width: 0;
}

.remember-title {
  color: #334155;
  font-size: 26rpx;
  font-weight: 600;
}

.remember-hint {
  margin-top: 4rpx;
  color: #94A3B8;
  font-size: 20rpx;
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

/* 网络切换区域 */
.network-switch-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.network-status {
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
}

.network-hint {
  color: rgba(255, 255, 255, 0.56);
  font-size: 24rpx;
  text-decoration: underline;
}

/* 设置弹窗 */
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
  max-height: 80vh;
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 36rpx 32rpx 32rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.settings-title {
  display: block;
  color: #1A1A1A;
  font-size: 34rpx;
  font-weight: 700;
}

/* 网络选项 */
.network-options {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.network-option {
  padding: 20rpx 24rpx;
  border: 2rpx solid #E5EDF0;
  border-radius: 16rpx;
  background: #F8FAFC;
}

.network-option.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.06);
}

.option-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.option-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.option-check {
  width: 36rpx;
  height: 36rpx;
  background: #0D6E6E;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.option-check text {
  color: #FFFFFF;
  font-size: 20rpx;
}

.option-desc {
  font-size: 24rpx;
  color: #64748B;
}

/* 当前地址显示 */
.current-address {
  padding: 16rpx 24rpx;
  background: #F1F5F9;
  border-radius: 12rpx;
}

.address-label {
  font-size: 24rpx;
  color: #64748B;
  margin-bottom: 8rpx;
}

.address-value {
  font-size: 28rpx;
  color: #1A1A1A;
  font-weight: 500;
}

/* 高级设置 */
.custom-section {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  border-top: 1rpx solid #E5EDF0;
  padding-top: 16rpx;
}

.advanced-section {
  border-top: 1rpx solid #E5EDF0;
  padding-top: 16rpx;
}

.advanced-toggle {
  font-size: 26rpx;
  color: #0D6E6E;
}

.advanced-fields {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.settings-field {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.settings-label {
  font-size: 26rpx;
  color: #334155;
  font-weight: 600;
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

.manual-save-btn {
  width: 100%;
  height: 72rpx;
  background: #F1F5F9;
  color: #475569;
  font-size: 28rpx;
  border-radius: 14rpx;
  border: none;
}

/* 操作按钮 */
.settings-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 8rpx;
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
