<script setup lang="ts">
import { ref, computed } from 'vue'
import { uploadLogo } from '@/api/style'
import { showToast } from 'vant'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import { AVAILABLE_LOGO_SIZES } from '@/types/theme'

const workbench = useStyleSettingsWorkbench()

const uploading = ref(false)

// Logo 完整 URL
const logoUrlFull = computed(() => {
  const url = workbench.logoUrl.value
  if (!url) return ''
  if (url.startsWith('data:')) return url
  if (url.startsWith('http')) return url
  return window.location.origin + url
})

// 更新系统名称
const updateSystemName = (value: string) => {
  workbench.applyAndPersist({ systemName: value })
}

// 更新 Logo 尺寸
const updateLogoSize = (size: string) => {
  workbench.applyAndPersist({ logoSize: size })
}

// 上传 Logo
const handleLogoUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (file.size > 1536 * 1024) {
    showToast('Logo 文件大小不能超过 1.5MB')
    return
  }

  uploading.value = true
  try {
    await uploadLogo(file)
    await workbench.loadWorkbenchConfig(true)
    showToast('Logo 上传成功')
  } catch (error) {
    console.error('Failed to upload logo:', error)
    showToast('Logo 上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}
</script>

<template>
  <div class="brand-settings-panel">
    <!-- 系统名称 -->
    <section class="config-section">
      <h2 class="section-title">
        系统名称
        <span class="section-status">当前：{{ workbench.currentSystemName.value }}</span>
      </h2>

      <div class="form-group">
        <label class="form-label">系统显示名称</label>
        <input
          type="text"
          class="form-input"
          :value="workbench.draftConfig?.value?.systemName"
          @change="updateSystemName(($event.target as HTMLInputElement).value)"
          placeholder="输入系统显示名称"
          maxlength="50"
        />
        <p class="form-hint">将显示在登录页面和导航栏</p>
      </div>
    </section>

    <!-- Logo 设置 -->
    <section class="config-section">
      <h2 class="section-title">
        Logo
        <span class="section-status">{{ logoUrlFull ? '已上传' : '未上传' }}</span>
      </h2>

      <div class="logo-config">
        <div class="logo-preview">
          <img
            v-if="logoUrlFull"
            :src="logoUrlFull"
            alt="Logo"
            class="preview-image"
          />
          <div v-else class="logo-placeholder">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5">
              <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
              <circle cx="8.5" cy="8.5" r="1.5"/>
              <polyline points="21 15 16 10 5 21"/>
            </svg>
            <span>暂无 Logo</span>
          </div>
        </div>

        <div class="logo-actions">
          <label class="upload-btn" :class="{ disabled: uploading }">
            <input
              type="file"
              accept="image/*"
              @change="handleLogoUpload"
              hidden
              :disabled="uploading"
            />
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            {{ uploading ? '上传中...' : '上传 Logo' }}
          </label>
          <p class="logo-hint">支持 PNG/JPG/SVG，最大 1.5MB</p>
        </div>
      </div>

      <!-- Logo 尺寸 -->
      <div class="logo-size-config">
        <span class="size-label">Logo 尺寸：</span>
        <div class="size-options">
          <button
            v-for="size in AVAILABLE_LOGO_SIZES"
            :key="size.value"
            class="size-btn"
            :class="{ active: workbench.logoSize.value === size.value }"
            @click="updateLogoSize(size.value)"
          >
            {{ size.label }}
          </button>
        </div>
      </div>
    </section>

    <!-- 导航栏品牌预览 -->
    <section class="config-section">
      <h2 class="section-title">导航栏品牌预览</h2>

      <div class="nav-preview">
        <div class="preview-nav">
          <img v-if="logoUrlFull" :src="logoUrlFull" alt="Logo" class="nav-logo" :style="{ height: workbench.logoSize.value === 'small' ? '24px' : workbench.logoSize.value === 'medium' ? '36px' : workbench.logoSize.value === 'large' ? '48px' : '64px' }" />
          <span class="nav-title">{{ workbench.currentSystemName.value }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.brand-settings-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-section {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
}

.section-status {
  font-size: var(--font-size-xs);
  color: #666666;
  padding: 4px 12px;
  background: #F5F5F5;
  border-radius: 4px;
}

.form-group {
  max-width: 400px;
}

.form-label {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
  margin-bottom: 8px;
}

.form-input {
  width: 100%;
  height: 44px;
  padding: 0 16px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-size: var(--font-size-sm);
  color: #1A1A1A;
  background: #FFFFFF;
  transition: border-color 150ms;
  box-sizing: border-box;
}

.form-input:focus {
  outline: none;
  border-color: #0D6E6E;
}

.form-hint {
  font-size: var(--font-size-xs);
  color: #888888;
  margin: 8px 0 0 0;
}

/* Logo 配置 */
.logo-config {
  display: flex;
  align-items: center;
  gap: 24px;
  margin-bottom: 20px;
}

.logo-preview {
  width: 80px;
  height: 80px;
  border: 1px dashed #E5E5E5;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #FAFAFA;
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.logo-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #888888;
  font-size: 10px;
}

.logo-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: #0D6E6E;
  color: #FFFFFF;
  border-radius: 8px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: background 150ms;
}

.upload-btn:hover:not(.disabled) {
  background: #0A5555;
}

.upload-btn.disabled {
  background: #9CA3AF;
  cursor: not-allowed;
}

.logo-hint {
  font-size: var(--font-size-xs);
  color: #888888;
}

/* Logo 尺寸 */
.logo-size-config {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #F0F0F0;
}

.size-label {
  font-size: var(--font-size-sm);
  color: #333333;
}

.size-options {
  display: flex;
  gap: 8px;
}

.size-btn {
  padding: 6px 16px;
  background: #F5F5F5;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.size-btn:hover {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.size-btn.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

/* 导航栏预览 */
.nav-preview {
  padding: 16px;
  background: #FAFAFA;
  border-radius: 8px;
}

.preview-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #FFFFFF;
  border-radius: 8px;
  border: 1px solid #E5E5E5;
}

.nav-logo {
  object-fit: contain;
}

.nav-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: #1A1A1A;
}
</style>