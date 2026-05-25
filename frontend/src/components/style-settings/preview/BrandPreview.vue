<script setup lang="ts">
import { computed } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import type { StyleConfig } from '@/types/theme'

const props = defineProps<{
  editingConfig: StyleConfig
}>()

// 导航栏 Logo URL
const logoUrlFull = computed(() => {
  if (!props.editingConfig.logoUrl) return ''
  if (props.editingConfig.logoUrl.startsWith('data:')) return props.editingConfig.logoUrl
  if (props.editingConfig.logoUrl.startsWith('http')) return props.editingConfig.logoUrl
  return window.location.origin + props.editingConfig.logoUrl
})

// 登录页 Logo URL（优先使用登录页专用）
const logoUrlLoginFull = computed(() => {
  const url = props.editingConfig.logoUrlLogin || props.editingConfig.logoUrl
  if (!url) return ''
  if (url.startsWith('data:')) return url
  if (url.startsWith('http')) return url
  return window.location.origin + url
})

const logoSizeOptions = [
  { value: 'small', label: '小', size: '24px' },
  { value: 'medium', label: '中', size: '36px' },
  { value: 'large', label: '大', size: '48px' },
  { value: 'xlarge', label: '特大', size: '64px' }
]

// 登录页 Logo 尺寸（放大1倍）
const logoSizeLoginOptions = [
  { value: 'small', size: '48px' },
  { value: 'medium', size: '72px' },
  { value: 'large', size: '96px' },
  { value: 'xlarge', size: '128px' }
]

const currentLogoSize = computed(() => {
  const opt = logoSizeOptions.find(o => o.value === props.editingConfig.logoSize)
  return opt?.size || '36px'
})

// 登录页 Logo 尺寸
const currentLogoSizeLogin = computed(() => {
  const size = props.editingConfig.logoSizeLogin || props.editingConfig.logoSize
  const opt = logoSizeLoginOptions.find(o => o.value === size)
  return opt?.size || '72px'
})

// 副标题样式
const subtitleStyle = computed(() => {
  const fontMap: Record<string, string> = {
    heading: 'var(--font-heading)',
    body: 'var(--font-body)'
  }
  return {
    fontFamily: fontMap[props.editingConfig.subtitleFont || 'body'],
    fontWeight: props.editingConfig.subtitleFontWeight || '400',
    color: props.editingConfig.subtitleColor || 'rgba(255, 255, 255, 0.75)'
  }
})
</script>

<template>
  <!-- 登录页品牌预览 -->
  <PreviewFrame title="登录页品牌预览" hint="Logo + 系统名称 + 副标题">
    <div class="login-preview">
      <div class="login-brand">
        <img v-if="logoUrlLoginFull" :src="logoUrlLoginFull" alt="Logo" class="login-logo" :style="{ height: currentLogoSizeLogin }" />
        <div v-else class="login-logo-placeholder">
          <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,0.5)" stroke-width="1.5">
            <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
            <circle cx="8.5" cy="8.5" r="1.5"/>
            <polyline points="21 15 16 10 5 21"/>
          </svg>
          <span>暂无Logo</span>
        </div>
        <h1 class="login-title">{{ editingConfig.systemName }}</h1>
        <p class="login-subtitle" :style="subtitleStyle">
          {{ editingConfig.subtitleText || '价格展示与管理平台' }}
        </p>
      </div>
    </div>
  </PreviewFrame>

  <!-- 导航栏品牌区 -->
  <PreviewFrame title="导航栏品牌区" hint="Logo + 系统名称">
    <div class="nav-preview">
      <div class="nav-brand">
        <img v-if="logoUrlFull" :src="logoUrlFull" alt="Logo" class="nav-logo" :style="{ width: currentLogoSize, height: currentLogoSize }" />
        <span class="nav-name">{{ editingConfig.systemName }}</span>
      </div>
      <div class="nav-items">
        <span class="nav-item active">首页</span>
        <span class="nav-item">产品</span>
        <span class="nav-item">设置</span>
      </div>
    </div>
  </PreviewFrame>

  <!-- Logo 尺寸对比 -->
  <PreviewFrame title="Logo 尺寸对比" hint="当前选中高亮">
    <div class="logo-size-grid">
      <div
        v-for="opt in logoSizeOptions"
        :key="opt.value"
        class="logo-size-item"
        :class="{ active: editingConfig.logoSize === opt.value }"
      >
        <img v-if="logoUrlFull" :src="logoUrlFull" alt="Logo" :style="{ width: opt.size, height: opt.size }" />
        <div v-else class="logo-placeholder" :style="{ width: opt.size, height: opt.size }"></div>
        <span class="size-label">{{ opt.label }} ({{ opt.size }})</span>
      </div>
    </div>
  </PreviewFrame>
</template>

<style scoped>
/* 登录页预览 */
.login-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  border-radius: 6px;
  background: linear-gradient(135deg, #1E3A5F 0%, #0D6E6E 100%);
  min-height: 180px;
}

.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.login-logo {
  object-fit: contain;
}

.login-logo-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 10px;
}

.login-title {
  font-family: var(--font-heading);
  font-size: 18px;
  font-weight: 600;
  color: #FFFFFF;
  margin: 0;
  text-align: center;
}

.login-subtitle {
  font-size: 12px;
  margin: 0;
  text-align: center;
}

/* 导航栏预览 */
.nav-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: 6px;
  background: var(--app-nav-bg);
  color: var(--app-nav-text);
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-logo {
  object-fit: contain;
}

.nav-name {
  font-family: var(--font-heading);
  font-size: var(--font-size-lg);
  font-weight: 500;
}

.nav-items {
  display: flex;
  gap: 12px;
}

.nav-item {
  font-size: var(--font-size-xs);
  padding: 4px 8px;
  border-radius: 4px;
}

.nav-item.active {
  background: rgba(13, 110, 110, 0.15);
}

.logo-size-grid {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.logo-size-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 8px;
  border-radius: 6px;
  border: 2px solid transparent;
  transition: all 150ms;
}

.logo-size-item.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.05);
}

.logo-size-item img {
  object-fit: contain;
}

.logo-placeholder {
  background: #E5E5E5;
  border-radius: 4px;
}

.size-label {
  font-size: 10px;
  color: var(--text-secondary, #888);
}
</style>
