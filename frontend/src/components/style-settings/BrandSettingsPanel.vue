<script setup lang="ts">
import { ref, computed } from 'vue'
import { showToast } from 'vant'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import { AVAILABLE_LOGO_SIZES } from '@/types/theme'

const workbench = useStyleSettingsWorkbench()

const uploadingLogin = ref(false)
const uploadingNav = ref(false)

const fileToDataUrl = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

// 是否使用相同Logo
const useSameLogo = computed(() => {
  return !workbench.logoUrlLogin.value && !workbench.logoUrlNav.value
})

// 登录页Logo完整URL
const logoUrlLoginFull = computed(() => {
  const url = workbench.logoUrlLogin.value || workbench.logoUrl.value
  if (!url) return ''
  if (url.startsWith('data:')) return url
  if (url.startsWith('http')) return url
  return window.location.origin + url
})

// 导航栏Logo完整URL
const logoUrlNavFull = computed(() => {
  const url = workbench.logoUrlNav.value || workbench.logoUrl.value
  if (!url) return ''
  if (url.startsWith('data:')) return url
  if (url.startsWith('http')) return url
  return window.location.origin + url
})

// 更新系统名称
const updateSystemName = (value: string) => {
  if (workbench.draftConfig.value?.systemName === value) return
  workbench.updateDraft({ systemName: value })
}

// 更新登录页副标题文案
const updateSubtitleText = (value: string) => {
  if (workbench.draftConfig.value?.subtitleText === value) return
  workbench.updateDraft({ subtitleText: value })
}

// 更新登录页Logo尺寸
const updateLogoSizeLogin = (size: string) => {
  workbench.updateDraft({ logoSizeLogin: size })
}

// 更新导航栏Logo尺寸
const updateLogoSizeNav = (size: string) => {
  workbench.updateDraft({ logoSizeNav: size })
}

// 上传登录页Logo
const handleLogoLoginUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (file.size > 1536 * 1024) {
    showToast('Logo 文件大小不能超过 1.5MB')
    return
  }

  uploadingLogin.value = true
  try {
    const logoUrl = await fileToDataUrl(file)
    workbench.updateDraft({ logoUrlLogin: logoUrl })
    showToast('登录页Logo已进入草稿，请点击顶部保存配置')
  } catch (error) {
    console.error('Failed to read login logo:', error)
    showToast('登录页Logo读取失败')
  } finally {
    uploadingLogin.value = false
    input.value = ''
  }
}

// 上传导航栏Logo
const handleLogoNavUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (file.size > 1536 * 1024) {
    showToast('Logo 文件大小不能超过 1.5MB')
    return
  }

  uploadingNav.value = true
  try {
    const logoUrl = await fileToDataUrl(file)
    workbench.updateDraft({ logoUrlNav: logoUrl })
    showToast('导航栏Logo已进入草稿，请点击顶部保存配置')
  } catch (error) {
    console.error('Failed to read nav logo:', error)
    showToast('导航栏Logo读取失败')
  } finally {
    uploadingNav.value = false
    input.value = ''
  }
}

// 清除登录页Logo
const clearLogoLogin = () => {
  workbench.updateDraft({ logoUrlLogin: '' })
}

// 清除导航栏Logo
const clearLogoNav = () => {
  workbench.updateDraft({ logoUrlNav: '' })
}

// 更新副标题字体
const updateSubtitleFont = (font: string) => {
  workbench.updateDraft({ subtitleFont: font })
}

// 更新副标题字重
const updateSubtitleFontWeight = (weight: string) => {
  workbench.updateDraft({ subtitleFontWeight: weight })
}

// 更新副标题颜色
const updateSubtitleColor = (color: string) => {
  workbench.updateDraft({ subtitleColor: color })
}

// 判断当前副标题颜色
const isSubtitleColor = (color: string) => {
  return (workbench.draftConfig?.value?.subtitleColor || 'rgba(255, 255, 255, 0.75)') === color
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

      <div class="brand-text-grid">
        <div class="form-group">
          <label class="form-label">系统显示名称</label>
          <input
            type="text"
            class="form-input"
            :value="workbench.draftConfig?.value?.systemName"
            @input="updateSystemName(($event.target as HTMLInputElement).value)"
            placeholder="输入系统显示名称"
            maxlength="50"
          />
          <p class="form-hint">将显示在登录页面和导航栏</p>
        </div>

        <div class="form-group">
          <label class="form-label">登录页副标题</label>
          <input
            type="text"
            class="form-input"
            :value="workbench.draftConfig?.value?.subtitleText"
            @input="updateSubtitleText(($event.target as HTMLInputElement).value)"
            placeholder="输入登录页副标题"
            maxlength="80"
          />
          <p class="form-hint">与系统名称平行维护，显示在登录页标题下方</p>
        </div>
      </div>
    </section>

    <!-- 双Logo设置 -->
    <section class="config-section">
      <h2 class="section-title">
        Logo 设置
        <span class="section-status">{{ useSameLogo ? '使用默认Logo' : '独立配置' }}</span>
      </h2>

      <div class="logo-grid">
        <!-- 登录页Logo -->
        <div class="logo-card">
          <div class="logo-card-header">
            <span class="logo-card-title">登录页 Logo</span>
            <span class="logo-card-hint">显示在登录页面</span>
          </div>

          <div class="logo-preview">
            <img
              v-if="logoUrlLoginFull"
              :src="logoUrlLoginFull"
              alt="登录页Logo"
              class="preview-image"
            />
            <div v-else class="logo-placeholder">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
              <span>{{ workbench.logoUrl.value ? '使用默认' : '暂无Logo' }}</span>
            </div>
          </div>

          <div class="logo-actions">
            <label class="upload-btn" :class="{ disabled: uploadingLogin }">
              <input
                type="file"
                accept="image/*"
                @change="handleLogoLoginUpload"
                hidden
                :disabled="uploadingLogin"
              />
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              {{ uploadingLogin ? '上传中...' : '上传' }}
            </label>
            <button
              v-if="workbench.logoUrlLogin.value"
              class="clear-btn"
              @click="clearLogoLogin"
            >
              清除
            </button>
          </div>

          <!-- 登录页Logo尺寸 -->
          <div class="logo-size-row">
            <span class="size-label">尺寸：</span>
            <div class="size-options">
              <button
                v-for="size in AVAILABLE_LOGO_SIZES"
                :key="size.value"
                class="size-btn"
                :class="{ active: (workbench.logoSizeLogin.value || workbench.logoSize.value) === size.value }"
                @click="updateLogoSizeLogin(size.value)"
              >
                {{ size.label }}
              </button>
            </div>
          </div>
        </div>

        <!-- 导航栏Logo -->
        <div class="logo-card">
          <div class="logo-card-header">
            <span class="logo-card-title">导航栏 Logo</span>
            <span class="logo-card-hint">显示在系统导航栏</span>
          </div>

          <div class="logo-preview">
            <img
              v-if="logoUrlNavFull"
              :src="logoUrlNavFull"
              alt="导航栏Logo"
              class="preview-image"
            />
            <div v-else class="logo-placeholder">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#ccc" stroke-width="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                <circle cx="8.5" cy="8.5" r="1.5"/>
                <polyline points="21 15 16 10 5 21"/>
              </svg>
              <span>{{ workbench.logoUrl.value ? '使用默认' : '暂无Logo' }}</span>
            </div>
          </div>

          <div class="logo-actions">
            <label class="upload-btn" :class="{ disabled: uploadingNav }">
              <input
                type="file"
                accept="image/*"
                @change="handleLogoNavUpload"
                hidden
                :disabled="uploadingNav"
              />
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              {{ uploadingNav ? '上传中...' : '上传' }}
            </label>
            <button
              v-if="workbench.logoUrlNav.value"
              class="clear-btn"
              @click="clearLogoNav"
            >
              清除
            </button>
          </div>

          <!-- 导航栏Logo尺寸 -->
          <div class="logo-size-row">
            <span class="size-label">尺寸：</span>
            <div class="size-options">
              <button
                v-for="size in AVAILABLE_LOGO_SIZES"
                :key="size.value"
                class="size-btn"
                :class="{ active: (workbench.logoSizeNav.value || workbench.logoSize.value) === size.value }"
                @click="updateLogoSizeNav(size.value)"
              >
                {{ size.label }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <p class="logo-tip">
        提示：未单独上传Logo时，将使用默认Logo。可以为登录页和导航栏分别设置不同的Logo。
      </p>
    </section>

    <!-- 副标题设置 -->
    <section class="config-section">
      <h2 class="section-title">
        副标题样式
        <span class="section-status">登录页副标题</span>
      </h2>

      <div class="subtitle-config">
        <!-- 字体选择 -->
        <div class="config-row">
          <span class="config-label">字体：</span>
          <div class="config-options">
            <button
              class="config-btn"
              :class="{ active: workbench.draftConfig?.value?.subtitleFont === 'heading' }"
              @click="updateSubtitleFont('heading')"
            >
              标题字体
            </button>
            <button
              class="config-btn"
              :class="{ active: workbench.draftConfig?.value?.subtitleFont === 'body' || !workbench.draftConfig?.value?.subtitleFont }"
              @click="updateSubtitleFont('body')"
            >
              正文字体
            </button>
          </div>
        </div>

        <!-- 字重选择 -->
        <div class="config-row">
          <span class="config-label">字重：</span>
          <div class="config-options">
            <button
              class="config-btn"
              :class="{ active: (workbench.draftConfig?.value?.subtitleFontWeight || '400') === '400' }"
              @click="updateSubtitleFontWeight('400')"
            >
              常规
            </button>
            <button
              class="config-btn"
              :class="{ active: workbench.draftConfig?.value?.subtitleFontWeight === '500' }"
              @click="updateSubtitleFontWeight('500')"
            >
              中等
            </button>
            <button
              class="config-btn"
              :class="{ active: workbench.draftConfig?.value?.subtitleFontWeight === '600' }"
              @click="updateSubtitleFontWeight('600')"
            >
              加粗
            </button>
          </div>
        </div>

        <!-- 颜色选择 -->
        <div class="config-row">
          <span class="config-label">颜色：</span>
          <div class="config-options color-options">
            <button
              class="color-btn"
              :class="{ active: isSubtitleColor('rgba(255, 255, 255, 0.75)') }"
              @click="updateSubtitleColor('rgba(255, 255, 255, 0.75)')"
              title="淡白（推荐）"
            >
              <span class="color-preview" style="background: rgba(255, 255, 255, 0.75)"></span>
              淡白
            </button>
            <button
              class="color-btn"
              :class="{ active: isSubtitleColor('rgba(255, 255, 255, 0.9)') }"
              @click="updateSubtitleColor('rgba(255, 255, 255, 0.9)')"
              title="亮白"
            >
              <span class="color-preview" style="background: rgba(255, 255, 255, 0.9)"></span>
              亮白
            </button>
            <button
              class="color-btn"
              :class="{ active: isSubtitleColor('rgba(255, 255, 255, 0.6)') }"
              @click="updateSubtitleColor('rgba(255, 255, 255, 0.6)')"
              title="灰白"
            >
              <span class="color-preview" style="background: rgba(255, 255, 255, 0.6)"></span>
              灰白
            </button>
          </div>
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

.brand-text-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
}

.form-group {
  min-width: 0;
  margin-bottom: 18px;
}

.form-group:last-child {
  margin-bottom: 0;
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

/* 双Logo网格 */
.logo-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

@media (max-width: 768px) {
  .brand-text-grid {
    grid-template-columns: 1fr;
  }

  .logo-grid {
    grid-template-columns: 1fr;
  }
}

.logo-card {
  padding: 16px;
  background: #FAFAFA;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
}

.logo-card-header {
  margin-bottom: 12px;
}

.logo-card-title {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 4px;
}

.logo-card-hint {
  font-size: var(--font-size-xs);
  color: #888888;
}

.logo-preview {
  width: 100%;
  height: 100px;
  border: 1px dashed #E5E5E5;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #F0F0F0;
  margin-bottom: 12px;
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
  gap: 8px;
  margin-bottom: 12px;
}

.upload-btn {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
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

.clear-btn {
  padding: 10px 16px;
  background: #FFFFFF;
  color: #666666;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: all 150ms;
}

.clear-btn:hover {
  border-color: #EF4444;
  color: #EF4444;
}

/* Logo尺寸 */
.logo-size-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid #E5E5E5;
}

.size-label {
  font-size: var(--font-size-xs);
  color: #666666;
  flex-shrink: 0;
}

.size-options {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.size-btn {
  padding: 4px 12px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 4px;
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

.logo-tip {
  font-size: var(--font-size-xs);
  color: #888888;
  margin: 16px 0 0 0;
  padding: 12px;
  background: #F5F5F5;
  border-radius: 8px;
}

/* 副标题配置 */
.subtitle-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.config-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.config-label {
  font-size: var(--font-size-sm);
  color: #333333;
  flex-shrink: 0;
  min-width: 60px;
}

.config-options {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.config-btn {
  padding: 6px 16px;
  background: #F5F5F5;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: var(--font-size-sm);
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.config-btn:hover {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.config-btn.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

.color-options {
  gap: 12px;
}

.color-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: var(--font-size-sm);
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.color-btn:hover {
  border-color: #0D6E6E;
}

.color-btn.active {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.color-preview {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border: 1px solid #E5E5E5;
}
</style>
