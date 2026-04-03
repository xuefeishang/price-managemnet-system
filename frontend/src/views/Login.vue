<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

const form = ref({
  username: '',
  password: ''
})

const loading = ref(false)
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const errorMessage = ref('')
const showPassword = ref(false)
const rememberUsername = ref(false)

// 密码可见切换
const togglePassword = () => {
  showPassword.value = !showPassword.value
}

// 判断是否为PC布局
const isPCLayout = computed(() => {
  return windowWidth.value >= 1024
})

// 监听窗口大小变化
const handleResize = () => {
  windowWidth.value = window.innerWidth
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

  loading.value = true
  try {
    const success = await userStore.loginAction(form.value)
    if (success) {
      // 保存用户名记忆
      if (rememberUsername.value) {
        localStorage.setItem('rememberUsername', form.value.username)
      } else {
        localStorage.removeItem('rememberUsername')
      }
      router.push('/home')
    } else {
      errorMessage.value = '用户名或密码错误'
    }
  } catch (error: any) {
    console.error('Login error:', error)
    errorMessage.value = error?.response?.data?.message || '登录失败，请稍后重试'
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

onMounted(() => {
  if (userStore.isAuthenticated) {
    router.push('/home')
    return
  }

  // 恢复记住的用户名
  const savedUsername = localStorage.getItem('rememberUsername')
  if (savedUsername) {
    form.value.username = savedUsername
    rememberUsername.value = true
  }

  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
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
            <h1 class="brand-title">价格管理系统</h1>
            <p class="brand-subtitle">企业价格展示与管理平台</p>
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
                <label class="form-label">用户名</label>
                <input
                  v-model="form.username"
                  type="text"
                  class="form-input"
                  placeholder="请输入用户名"
                  :disabled="loading"
                  autocomplete="username"
                  @input="clearError"
                  @keypress="handleKeyPress"
                />
              </div>

              <div class="form-group">
                <label class="form-label">密码</label>
                <div class="password-input-wrapper">
                  <input
                    v-model="form.password"
                    :type="showPassword ? 'text' : 'password'"
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
                @click="handleLogin"
                :disabled="loading || !form.username || !form.password"
              >
                <span v-if="!loading">登录</span>
                <span v-else class="loading-text">
                  <svg class="spinner" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" stroke-dasharray="60" stroke-dashoffset="20"/>
                  </svg>
                  登录中...
                </span>
              </button>
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
          <h1 class="main-title">价格管理系统</h1>
          <p class="subtitle">企业价格展示与管理平台</p>
        </div>

        <!-- 登录表单 -->
        <div class="form-container">
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
            <label class="form-label">用户名</label>
            <input
              v-model="form.username"
              type="text"
              class="form-input"
              placeholder="请输入用户名"
              :disabled="loading"
              autocomplete="username"
              @input="clearError"
              @keypress="handleKeyPress"
            />
          </div>

          <div class="form-group">
            <label class="form-label">密码</label>
            <div class="password-input-wrapper">
              <input
                v-model="form.password"
                :type="showPassword ? 'text' : 'password'"
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
            @click="handleLogin"
            :disabled="loading || !form.username || !form.password"
          >
            <span v-if="!loading">登录</span>
            <span v-else class="loading-text">登录中...</span>
          </button>
        </div>
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
}

.brand-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 32px;
  font-weight: 500;
  color: #FFFFFF;
  line-height: 1.1;
  margin-bottom: 16px;
}

.brand-subtitle {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.9);
  line-height: 1.4;
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
  font-family: 'Inter', sans-serif;
  font-size: 24px;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 8px;
}

.form-header p {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
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
  font-family: 'Inter', sans-serif;
  font-size: 14px;
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
  font-family: 'Inter', sans-serif;
  font-size: 14px;
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
  font-family: 'Inter', sans-serif;
  font-size: 14px;
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
  font-family: 'Inter', sans-serif;
  font-size: 14px;
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
  font-family: 'Inter', sans-serif;
  font-size: 16px;
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
  font-family: 'Newsreader', Georgia, serif;
  font-size: 40px;
  font-weight: 500;
  color: #1A1A1A;
  line-height: 1.05;
  margin: 0;
  text-align: center;
}

.subtitle {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
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
    font-size: 32px;
  }

  .title-section {
    padding: 32px 16px 16px;
  }

  .form-container {
    padding: 20px;
  }
}
</style>
