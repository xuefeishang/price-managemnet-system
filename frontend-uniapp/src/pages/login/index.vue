<template>
  <view class="login-page">
    <!-- Logo 和标题 -->
    <view class="login-header">
      <view class="logo">
        <image
          v-if="themeConfig.logoUrl"
          :src="themeConfig.logoUrl"
          class="logo-image"
          mode="aspectFit"
        />
        <text v-else class="logo-text">P</text>
      </view>
      <text class="title">{{ themeConfig.systemName || '矿产品价格管理系统' }}</text>
      <text class="subtitle">企业价格展示与管理平台</text>
    </view>

    <!-- 账号密码登录 -->
    <view class="login-form" v-if="loginMode === 'account'">
      <view class="form-item">
        <input
          v-model="form.username"
          class="input"
          type="text"
          placeholder="请输入用户名"
          placeholder-class="placeholder"
        />
      </view>
      <view class="form-item">
        <input
          v-model="form.password"
          class="input"
          :password="!showPassword"
          placeholder="请输入密码"
          placeholder-class="placeholder"
        />
        <text class="password-toggle" @click="showPassword = !showPassword">
          {{ showPassword ? '隐藏' : '显示' }}
        </text>
      </view>

      <button class="login-btn" :loading="loading" @click="handleLogin">
        登录
      </button>

      <!-- #ifdef MP-WEIXIN -->
      <view class="divider">
        <text>或</text>
      </view>
      <button class="wechat-btn" open-type="getPhoneNumber" @getphonenumber="handleWechatLogin">
        微信一键登录
      </button>
      <!-- #endif -->
    </view>

    <!-- 错误提示 -->
    <view class="error-msg" v-if="errorMsg">
      <text>{{ errorMsg }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useTheme } from '@/composables/useTheme'

const userStore = useUserStore()
const { themeConfig, loadThemeConfig } = useTheme()

const loginMode = ref<'account' | 'wechat'>('account')
const form = ref({ username: '', password: '' })
const showPassword = ref(false)
const loading = ref(false)
const errorMsg = ref('')

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
    errorMsg.value = '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 微信登录
const handleWechatLogin = async (e: any) => {
  if (e.detail.code) {
    loading.value = true
    try {
      const success = await userStore.wechatLoginAction(e.detail.code)
      if (success) {
        uni.switchTab({ url: '/pages/home/index' })
      } else {
        errorMsg.value = '微信登录失败'
      }
    } catch (error) {
      errorMsg.value = '微信登录失败，请稍后重试'
    } finally {
      loading.value = false
    }
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
  padding: 80rpx 40rpx;
  box-sizing: border-box;
}

.login-header {
  text-align: center;
  margin-bottom: 80rpx;
}

.logo {
  width: 120rpx;
  height: 120rpx;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 24rpx;
  overflow: hidden;
}

.logo-image {
  width: 100%;
  height: 100%;
}

.logo-text {
  font-size: 60rpx;
  font-weight: bold;
  color: #FFFFFF;
}

.title {
  display: block;
  font-size: 44rpx;
  font-weight: 600;
  color: #FFFFFF;
  margin-bottom: 16rpx;
}

.subtitle {
  display: block;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.8);
}

.login-form {
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 48rpx 32rpx;
}

.form-item {
  margin-bottom: 32rpx;
  position: relative;
}

.input {
  width: 100%;
  height: 96rpx;
  background: #F5F5F5;
  border-radius: 12rpx;
  padding: 0 32rpx;
  font-size: 32rpx;
  box-sizing: border-box;
}

.placeholder {
  color: #999999;
}

.password-toggle {
  position: absolute;
  right: 32rpx;
  top: 50%;
  transform: translateY(-50%);
  font-size: 28rpx;
  color: #0D6E6E;
}

.login-btn {
  width: 100%;
  height: 96rpx;
  background: #0D6E6E;
  color: #FFFFFF;
  font-size: 32rpx;
  font-weight: 500;
  border-radius: 12rpx;
  border: none;
  margin-top: 16rpx;
}

.login-btn:active {
  background: #0A5555;
}

.divider {
  display: flex;
  align-items: center;
  margin: 40rpx 0;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #E5E5E5;
}

.divider text {
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #999999;
}

.wechat-btn {
  width: 100%;
  height: 96rpx;
  background: #07C160;
  color: #FFFFFF;
  font-size: 32rpx;
  font-weight: 500;
  border-radius: 12rpx;
  border: none;
}

.wechat-btn:active {
  background: #06AD56;
}

.error-msg {
  margin-top: 32rpx;
  padding: 24rpx;
  background: rgba(255, 77, 79, 0.1);
  border-radius: 12rpx;
  text-align: center;
}

.error-msg text {
  font-size: 28rpx;
  color: #E03B3B;
}
</style>