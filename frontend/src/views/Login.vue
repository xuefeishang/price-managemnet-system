<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useRouter } from 'vue-router'
import { useTheme } from '@/composables/useTheme'
import { useLayout } from '@/composables/useLayout'
import { getCaptcha } from '@/api/auth'
import type { CaptchaResponse } from '@/api/auth'

const userStore = useUserStore()
const router = useRouter()
const { themeConfig, loadThemeConfig } = useTheme()
const { isPCLayout } = useLayout()

// 副标题样式（动态配置）
const subtitleStyle = computed(() => {
  const fontMap: Record<string, string> = {
    heading: 'var(--font-heading)',
    body: 'var(--font-body)'
  }
  return {
    fontFamily: fontMap[themeConfig.value.subtitleFont || 'body'],
    fontWeight: themeConfig.value.subtitleFontWeight || '400',
    color: themeConfig.value.subtitleColor || 'rgba(255, 255, 255, 0.75)'
  }
})

// Logo尺寸样式（登录页放大1倍）
const logoSizeStyle = computed(() => {
  const sizeMap: Record<string, string> = {
    small: '48px',
    medium: '72px',
    large: '96px',
    xlarge: '128px'
  }
  // 优先使用登录页专用尺寸，否则使用通用尺寸
  const size = themeConfig.value.logoSizeLogin || themeConfig.value.logoSize
  return { height: sizeMap[size] || '72px' }
})

// Logo URL处理（优先使用登录页专用Logo，否则使用通用Logo）
const logoUrlFull = computed(() => {
  const url = themeConfig.value.logoUrlLogin || themeConfig.value.logoUrl
  if (!url) return ''
  return url.startsWith('http') ? url : `${import.meta.env.VITE_API_BASE_URL || ''}${url}`
})

const form = ref({
  username: '',
  password: '',
  captchaCode: ''
})

const loading = ref(false)
const errorMessage = ref('')
const showPassword = ref(false)
const rememberUsername = ref(false)

// 验证码相关
const captchaData = ref<CaptchaResponse | null>(null)
const captchaLoading = ref(false)
const captchaCountdown = ref(0)
let countdownTimer: ReturnType<typeof setInterval> | null = null

// 倒计时显示文本
const countdownText = computed(() => {
  if (captchaCountdown.value > 0) {
    return `${captchaCountdown.value}s`
  }
  return ''
})

// 获取验证码
const refreshCaptcha = async () => {
  if (captchaLoading.value) return

  captchaLoading.value = true
  try {
    const response = await getCaptcha()
    if (response.data) {
      captchaData.value = response.data
      // 开始60秒倒计时
      startCountdown()
    }
  } catch (error) {
    console.error('Failed to get captcha:', error)
    // 加载失败，3秒后自动重试
    setTimeout(() => {
      if (!captchaData.value) {
        refreshCaptcha()
      }
    }, 3000)
  } finally {
    captchaLoading.value = false
  }
}

// 开始倒计时
const startCountdown = () => {
  // 清除之前的计时器
  if (countdownTimer) {
    clearInterval(countdownTimer)
  }
  captchaCountdown.value = 60
  countdownTimer = setInterval(() => {
    captchaCountdown.value--
    if (captchaCountdown.value <= 0) {
      clearInterval(countdownTimer!)
      countdownTimer = null
      // 倒计时结束自动刷新
      refreshCaptcha()
    }
  }, 1000)
}

// 密码可见切换
const togglePassword = () => {
  showPassword.value = !showPassword.value
}

// 表单验证
const validateForm = () => {
  if (!form.value.username.trim()) {
    errorMessage.value = '请输入用户名'
    return false
  }
  if (!form.value.password) {
    errorMessage.value = '请输入密码'
    return false
  }
  if (form.value.password.length < 6) {
    errorMessage.value = '密码长度不能少于6位'
    return false
  }
  errorMessage.value = ''
  return true
}

const handleLogin = async () => {
  errorMessage.value = ''

  if (!validateForm()) {
    return
  }

  // 验证码校验
  if (!form.value.captchaCode) {
    errorMessage.value = '请输入验证码'
    return
  }

  loading.value = true
  try {
    const success = await userStore.loginAction({
      username: form.value.username,
      password: form.value.password,
      captchaKey: captchaData.value?.captchaKey,
      captchaCode: form.value.captchaCode
    })
    if (success) {
      // 保存用户名记忆状态
      if (rememberUsername.value) {
        localStorage.setItem('rememberUsername', form.value.username)
        localStorage.setItem('rememberUsernameFlag', 'true')
      } else {
        localStorage.removeItem('rememberUsername')
        localStorage.removeItem('rememberUsernameFlag')
      }
      router.push('/home')
    }
  } catch (error: any) {
    // 仅开发环境打印日志
    if (import.meta.env.DEV) {
      console.error('Login error:', error)
    }
    // 显示后端返回的具体错误信息
    errorMessage.value = error.message || '登录失败，请稍后重试'
    // 刷新验证码
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}

const handleKeyPress = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    handleLogin()
  }
}

// 清除错误消息
const clearError = () => {
  if (errorMessage.value) {
    errorMessage.value = ''
  }
}

onMounted(async () => {
  // 重置验证码状态（处理组件复用的情况）
  captchaData.value = null
  form.value.captchaCode = ''

  // 如果有 token，先验证是否有效
  if (userStore.isAuthenticated) {
    try {
      // 尝试获取用户信息验证 token 有效性
      await userStore.fetchProfile()
      // token 有效，使用 replace 跳转首页（不添加新 history 条目）
      router.replace('/home')
      return
    } catch (error) {
      // token 无效，清除本地状态（不调用API）
      console.log('Token invalid, clearing state')
      userStore.logoutAction(false)
    }
  }

  // 加载主题配置（包含系统名称）
  await loadThemeConfig()

  // 获取验证码
  await refreshCaptcha()

  // 恢复记住的用户名
  const savedUsername = localStorage.getItem('rememberUsername')
  if (savedUsername) {
    form.value.username = savedUsername
    rememberUsername.value = true
  }
  // 恢复勾选状态（即使没有保存用户名也恢复勾选状态）
  const savedRememberFlag = localStorage.getItem('rememberUsernameFlag')
  if (savedRememberFlag === 'true') {
    rememberUsername.value = true
  }
})

// 组件卸载时清理计时器
onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
})
</script>

<template>
  <div class="login-page">
    <!-- PC布局：左右分栏 -->
    <template v-if="isPCLayout">
      <div class="login-container-pc">
        <!-- 左侧品牌区域 -->
        <div class="brand-section">
          <div class="brand-content">
            <img
              v-if="logoUrlFull"
              :src="logoUrlFull"
              alt="Logo"
              class="brand-logo"
              :style="logoSizeStyle"
            />
            <h1 class="brand-title">{{ themeConfig.systemName }}</h1>
            <p class="brand-subtitle" :style="subtitleStyle">{{ themeConfig.subtitleText || '价格展示与管理平台' }}</p>
          </div>
        </div>

        <!-- 右侧表单区域 -->
        <div class="form-section-pc">
          <div class="form-wrapper">
            <div class="form-header">
              <h2>欢迎回来</h2>
              <p>请登录您的账户</p>
            </div>

            <div class="form-body">
              <form @submit.prevent="handleLogin">
              <!-- 错误提示 -->
              <div v-if="errorMessage" class="error-message">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="8" x2="12" y2="12"/>
                  <line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
                <span>{{ errorMessage }}</span>
              </div>

              <div class="form-group">
                <label class="form-label" for="username-pc">用户名</label>
                <input
                  id="username-pc"
                  v-model="form.username"
                  type="text"
                  name="username"
                  class="form-input"
                  placeholder="请输入用户名"
                  :disabled="loading"
                  autocomplete="username"
                  @input="clearError"
                  @keypress="handleKeyPress"
                />
              </div>

              <div class="form-group">
                <label class="form-label" for="password-pc">密码</label>
                <div class="password-input-wrapper">
                  <input
                    id="password-pc"
                    v-model="form.password"
                    :type="showPassword ? 'text' : 'password'"
                    name="password"
                    class="form-input password-input"
                    placeholder="请输入密码"
                    :disabled="loading"
                    autocomplete="current-password"
                    @input="clearError"
                    @keypress="handleKeyPress"
                  />
                  <button
                    type="button"
                    class="password-toggle"
                    @click="togglePassword"
                    :disabled="loading"
                  >
                    <svg v-if="!showPassword" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                      <circle cx="12" cy="12" r="3"/>
                    </svg>
                    <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                      <line x1="1" y1="1" x2="23" y2="23"/>
                    </svg>
                  </button>
                </div>
              </div>

              <!-- 验证码 -->
              <div class="form-group">
                <label class="form-label" for="captcha-pc">验证码</label>
                <div class="captcha-input-wrapper">
                  <input
                    id="captcha-pc"
                    v-model="form.captchaCode"
                    type="text"
                    name="captcha"
                    class="form-input captcha-input"
                    placeholder="请输入验证码"
                    :disabled="loading"
                    maxlength="4"
                    autocomplete="off"
                    @input="clearError"
                    @keypress="handleKeyPress"
                  />
                  <div class="captcha-image-wrapper" @click="refreshCaptcha">
                    <img
                      v-if="captchaData?.captchaImage"
                      :src="captchaData.captchaImage"
                      alt="验证码"
                      class="captcha-image"
                      :class="{ loading: captchaLoading }"
                    />
                    <div v-else class="captcha-placeholder">
                      <svg class="spinner" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20"/>
                      </svg>
                    </div>
                    <!-- 倒计时遮罩 -->
                    <div v-if="countdownText" class="captcha-countdown">{{ countdownText }}</div>
                  </div>
                </div>
              </div>

              <!-- 记住用户名 -->
              <div class="remember-row">
                <label class="remember-label">
                  <input
                    type="checkbox"
                    v-model="rememberUsername"
                    :disabled="loading"
                  />
                  <span>记住用户名</span>
                </label>
              </div>

              <button
                class="login-button"
                :class="{ loading: loading }"
                type="submit"
                :disabled="loading || !form.username || !form.password || !form.captchaCode"
              >
                <span v-if="!loading">登录</span>
                <span v-else class="loading-text">
                  <svg class="spinner" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20"/>
                  </svg>
                  登录中...
                </span>
              </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 移动端布局 -->
    <template v-else>
      <div class="login-content">
        <!-- 标题区域 -->
        <div class="title-section">
          <img
            v-if="logoUrlFull"
            :src="logoUrlFull"
            alt="Logo"
            class="main-logo"
            :style="logoSizeStyle"
          />
          <h1 class="main-title">{{ themeConfig.systemName }}</h1>
          <p class="subtitle" :style="subtitleStyle">{{ themeConfig.subtitleText || '价格展示与管理平台' }}</p>
        </div>

        <!-- 登录表单 -->
        <form class="form-container" @submit.prevent="handleLogin">
          <!-- 错误提示 -->
          <div v-if="errorMessage" class="error-message error-message-mobile">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="10"/>
              <line x1="12" y1="8" x2="12" y2="12"/>
              <line x1="12" y1="16" x2="12.01" y2="16"/>
            </svg>
            <span>{{ errorMessage }}</span>
          </div>

          <div class="form-group">
            <label class="form-label" for="username-mobile">用户名</label>
            <input
              id="username-mobile"
              v-model="form.username"
              type="text"
              name="username"
              class="form-input"
              placeholder="请输入用户名"
              :disabled="loading"
              autocomplete="username"
              @input="clearError"
              @keypress="handleKeyPress"
            />
          </div>

          <div class="form-group">
            <label class="form-label" for="password-mobile">密码</label>
            <div class="password-input-wrapper">
              <input
                id="password-mobile"
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
                name="password"
                class="form-input password-input"
                placeholder="请输入密码"
                :disabled="loading"
                autocomplete="current-password"
                @input="clearError"
                @keypress="handleKeyPress"
              />
              <button
                type="button"
                class="password-toggle"
                @click="togglePassword"
                :disabled="loading"
              >
                <svg v-if="!showPassword" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
                  <circle cx="12" cy="12" r="3"/>
                </svg>
                <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
                  <line x1="1" y1="1" x2="23" y2="23"/>
                </svg>
              </button>
            </div>
          </div>

          <!-- 验证码 -->
          <div class="form-group">
            <label class="form-label" for="captcha-mobile">验证码</label>
            <div class="captcha-input-wrapper">
              <input
                id="captcha-mobile"
                v-model="form.captchaCode"
                type="text"
                name="captcha"
                class="form-input captcha-input"
                placeholder="请输入验证码"
                :disabled="loading"
                maxlength="4"
                autocomplete="off"
                @input="clearError"
                @keypress="handleKeyPress"
              />
              <div class="captcha-image-wrapper" @click="refreshCaptcha">
                <img
                  v-if="captchaData?.captchaImage"
                  :src="captchaData.captchaImage"
                  alt="验证码"
                  class="captcha-image"
                  :class="{ loading: captchaLoading }"
                />
                <div v-else class="captcha-placeholder">
                  <span v-if="captchaLoading">加载中...</span>
                  <span v-else>点击获取</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 记住用户名 -->
          <div class="remember-row">
            <label class="remember-label">
              <input
                type="checkbox"
                v-model="rememberUsername"
                :disabled="loading"
              />
              <span>记住用户名</span>
            </label>
          </div>

          <button
            class="login-button"
            :class="{ loading: loading }"
            type="submit"
            :disabled="loading || !form.username || !form.password || !form.captchaCode"
          >
            <span v-if="!loading">登录</span>
            <span v-else class="loading-text">登录中...</span>
          </button>
        </form>
      </div>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.login-page {
  min-height: 100vh;
  background-color: #FAFAFA;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  box-sizing: border-box;
}

/* ==================== PC布局 ==================== */
.login-container-pc {
  display: flex;
  width: 100%;
  max-width: 1000px;
  min-height: 560px;
  height: auto;
  background: #FFFFFF;
  border-radius: 16px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
  overflow: hidden;
  margin: 20px;
}

/* 左侧品牌区域 */
.brand-section {
  flex: 1;
  min-width: 300px;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  position: relative;
  overflow: hidden;
}

.brand-section::before {
  content: '';
  position: absolute;
  inset: 0;
  background: url("data:image/svg+xml,%3Csvg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'%3E%3Cg fill='none' fill-rule='evenodd'%3E%3Cg fill='%23ffffff' fill-opacity='0.05'%3E%3Cpath d='M36 34v-4h-2v4h-4v2h4v4h2v-4h4v-2h-4zm0-30V0h-2v4h-4v2h4v4h2V6h4V4h-4zM6 34v-4H4v4H0v2h4v4h2v-4h4v-2H6zM6 4V0H4v4H0v2h4v4h2V6h4V4H6z'/%3E%3C/g%3E%3C/g%3E%3C/svg%3E");
  opacity: 0.5;
}

.brand-content {
  position: relative;
  z-index: 1;
  color: white;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.brand-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-3xl);
  font-weight: 600;
  color: #FFFFFF;
  line-height: 1.1;
  margin: 0 0 12px 0;
}

.brand-logo {
  object-fit: contain;
  margin-bottom: 20px;
}

.brand-subtitle {
  font-size: var(--font-size-sm);
  line-height: 1.4;
  margin: 0;
}

/* 右侧表单区域 */
.form-section-pc {
  flex: 1;
  min-width: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.form-wrapper {
  width: 100%;
  max-width: 360px;
}

.form-header {
  margin-bottom: 32px;
}

.form-header h2 {
  font-family: var(--font-body);
  font-size: var(--font-size-2xl);
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 8px;
}

.form-header p {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: #666666;
}

.form-body {
  margin-bottom: 24px;
}

.form-group {
  margin-bottom: 20px;
}

.form-label {
  display: block;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  height: 48px;
  padding: 12px 16px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: #1A1A1A;
  transition: border-color 150ms;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #0D6E6E;
}

.form-input.password-input {
  padding-right: 48px;
}

.form-input.captcha-input {
  width: 60%;
  flex: 1;
}

/* 验证码输入容器 */
.captcha-input-wrapper {
  display: flex;
  gap: 12px;
  align-items: center;
}

.captcha-image-wrapper {
  width: 120px;
  height: 48px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #F5F5F5;
  transition: border-color 150ms;
  position: relative;
}

.captcha-image-wrapper:hover {
  border-color: #0D6E6E;
}

.captcha-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.captcha-image.loading {
  opacity: 0.5;
}

.captcha-placeholder {
  font-size: var(--font-size-xs);
  color: #888888;
}

.captcha-countdown {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(0, 0, 0, 0.6);
  color: white;
  font-size: 0.625rem;
  padding: 1px 4px;
  border-radius: 4px;
  line-height: 1;
}

/* 错误提示 */
.error-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: rgba(255, 77, 79, 0.1);
  border: 1px solid rgba(255, 77, 79, 0.3);
  border-radius: 8px;
  color: #E03B3B;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  margin-bottom: 20px;
}

.error-message svg {
  flex-shrink: 0;
}

.error-message-mobile {
  margin-left: -24px;
  margin-right: -24px;
  margin-top: -24px;
  margin-bottom: 16px;
  border-radius: 12px 12px 0 0;
  border-left: none;
  border-right: none;
  border-top: none;
}

/* 密码输入框容器 */
.password-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.password-input-wrapper .form-input {
  width: 100%;
}

.password-toggle {
  position: absolute;
  right: 12px;
  background: none;
  border: none;
  cursor: pointer;
  color: #888888;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.password-toggle:hover {
  color: #0D6E6E;
}

/* 记住用户名 */
.remember-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  margin-bottom: 20px;
}

.remember-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: #666666;
}

.remember-label input[type="checkbox"] {
  width: 18px;
  height: 18px;
  accent-color: #0D6E6E;
  cursor: pointer;
}

/* 加载动画 */
.spinner {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.form-input::placeholder {
  color: #888888;
}

.form-input:disabled {
  background: #F5F5F5;
  cursor: not-allowed;
}

.login-button {
  width: 100%;
  height: 48px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-button:hover:not(:disabled) {
  background: #0D8A8A;
}

.login-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.loading-text {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ==================== 移动端布局 ==================== */
.login-content {
  width: 100%;
  max-width: 402px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  margin: auto;
}

.title-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px 24px 24px;
}

.main-title {
  font-family: var(--font-heading);
  font-size: 2.5rem;
  font-weight: 600;
  color: #1A1A1A;
  line-height: 1.05;
  margin: 0 0 12px 0;
  text-align: center;
}

.main-logo {
  object-fit: contain;
  margin-bottom: 20px;
}

.subtitle {
  font-size: var(--font-size-sm);
  line-height: 1.4;
  margin: 0;
  text-align: center;
}

.form-container {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  border: 1px solid #E5E5E5;
}

/* 响应式设计 */
@media (max-width: 1024px) {
  .login-container-pc {
    display: none;
  }
}

@media (min-width: 1024px) {
  .login-content {
    display: none;
  }
}

@media (max-width: 480px) {
  .login-content {
    padding: 16px;
  }

  .main-title {
    font-size: var(--font-size-3xl);
  }

  .title-section {
    padding: 32px 16px 16px;
  }

  .form-container {
    padding: 20px;
  }
}
</style>
