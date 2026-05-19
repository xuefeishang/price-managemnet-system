<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { useTheme } from '@/composables/useTheme'
import { uploadLogo, getStyleConfig } from '@/api/style'
import { PRESET_THEMES, AVAILABLE_FONTS, AVAILABLE_LOGO_SIZES, FONT_SIZE_PRESETS, FONT_SIZE_FIELDS } from '@/types/theme'
import type { StyleConfig, FontSizePreset } from '@/types/theme'

const { themeConfig, saveThemeConfig, forceReloadThemeConfig } = useTheme()
const saving = ref(false)

// 编辑中的配置（用于预览）
const editingConfig = ref<StyleConfig>({
  systemName: themeConfig.value.systemName || '价格管理系统',
  priceRiseColor: themeConfig.value.priceRiseColor,
  priceFallColor: themeConfig.value.priceFallColor,
  priceFlatColor: themeConfig.value.priceFlatColor,
  chartPrimaryColor: themeConfig.value.chartPrimaryColor,
  chartBudgetColor: themeConfig.value.chartBudgetColor,
  chartColors: [...themeConfig.value.chartColors],
  headingFont: themeConfig.value.headingFont,
  bodyFont: themeConfig.value.bodyFont,
  numberFont: themeConfig.value.numberFont,
  logoUrl: themeConfig.value.logoUrl,
  logoSize: themeConfig.value.logoSize || 'medium',
  activeTheme: themeConfig.value.activeTheme,
  // 字体大小
  fontSizeXs: themeConfig.value.fontSizeXs,
  fontSizeSm: themeConfig.value.fontSizeSm,
  fontSizeBase: themeConfig.value.fontSizeBase,
  fontSizeLg: themeConfig.value.fontSizeLg,
  fontSizeXl: themeConfig.value.fontSizeXl,
  fontSize2xl: themeConfig.value.fontSize2xl,
  fontSize3xl: themeConfig.value.fontSize3xl
})

// 字体大小预设选择
const selectedFontSizePreset = ref('standard')

// 应用字体大小预设
const applyFontSizePreset = (preset: FontSizePreset) => {
  selectedFontSizePreset.value = preset.key
  editingConfig.value.fontSizeXs = preset.sizes.xs
  editingConfig.value.fontSizeSm = preset.sizes.sm
  editingConfig.value.fontSizeBase = preset.sizes.base
  editingConfig.value.fontSizeLg = preset.sizes.lg
  editingConfig.value.fontSizeXl = preset.sizes.xl
  editingConfig.value.fontSize2xl = preset.sizes['2xl']
  editingConfig.value.fontSize3xl = preset.sizes['3xl']
}

// rem 转 px 显示
const toPx = (rem: string): string => {
  const match = rem.match(/^([\d.]+)rem$/)
  if (!match) return rem
  return `${parseFloat(match[1]) * 16}px`
}

// 字体大小字段类型安全的 getter/setter
const fontSizeKeys = ['fontSizeXs', 'fontSizeSm', 'fontSizeBase', 'fontSizeLg', 'fontSizeXl', 'fontSize2xl', 'fontSize3xl'] as const
type FontSizeKey = typeof fontSizeKeys[number]

const getFontSizeValue = (key: string): string => {
  return editingConfig.value[key as FontSizeKey] || ''
}

const setFontSizeValue = (key: string, value: string): void => {
  editingConfig.value[key as FontSizeKey] = value
}

// 实时预览用的涨跌颜色
const previewPriceRiseColor = computed(() => editingConfig.value.priceRiseColor)
const previewPriceFallColor = computed(() => editingConfig.value.priceFallColor)

// Logo 完整 URL
const logoUrlFull = computed(() => {
  if (!editingConfig.value.logoUrl) return ''
  if (editingConfig.value.logoUrl.startsWith('http')) return editingConfig.value.logoUrl
  return window.location.origin + editingConfig.value.logoUrl
})

// 主题选择
const selectedTheme = ref(themeConfig.value.activeTheme)

// 监听主题变化，同步编辑配置
const onThemeChange = (themeKey: string) => {
  selectedTheme.value = themeKey
  const theme = PRESET_THEMES.find(t => t.key === themeKey)
  if (theme) {
    editingConfig.value.priceRiseColor = theme.colors.priceRise
    editingConfig.value.priceFallColor = theme.colors.priceFall
    editingConfig.value.priceFlatColor = theme.colors.priceFlat || '#9CA3AF'
    editingConfig.value.chartPrimaryColor = theme.colors.chartPrimary
    editingConfig.value.chartBudgetColor = theme.colors.chartBudget
    editingConfig.value.chartColors = [...theme.colors.chartColors]
    editingConfig.value.activeTheme = themeKey
  }
}

// 自定义颜色
const onCustomColorChange = () => {
  selectedTheme.value = 'custom'
  editingConfig.value.activeTheme = 'custom'
}

// 保存配置
const saveConfig = async () => {
  saving.value = true
  try {
    await saveThemeConfig(editingConfig.value)
    showToast({
      message: '保存成功',
      position: 'top',
      duration: 2000
    })
  } catch (error) {
    console.error('Failed to save config:', error)
    showToast({
      message: '保存失败',
      position: 'top',
      duration: 2000
    })
  } finally {
    saving.value = false
  }
}

// 重置为默认
const resetConfig = async () => {
  try {
    await showConfirmDialog({
      title: '确认重置',
      message: '确定要重置为默认配置吗？'
    })
    editingConfig.value = {
      systemName: '价格管理系统',
      priceRiseColor: '#EF4444',
      priceFallColor: '#10B981',
      priceFlatColor: '#9CA3AF',
      chartPrimaryColor: '#0D6E6E',
      chartBudgetColor: '#F59E0B',
      chartColors: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B'],
      headingFont: 'Newsreader',
      bodyFont: 'Inter',
      numberFont: 'JetBrains Mono',
      logoUrl: '',
      logoSize: 'medium',
      activeTheme: 'theme_red_green',
      fontSizeXs: '0.75rem',
      fontSizeSm: '0.875rem',
      fontSizeBase: '1rem',
      fontSizeLg: '1.125rem',
      fontSizeXl: '1.25rem',
      fontSize2xl: '1.5rem',
      fontSize3xl: '1.875rem'
    }
    selectedTheme.value = 'theme_red_green'
    selectedFontSizePreset.value = 'standard'
    await saveConfig()
    showToast('已重置为默认配置')
  } catch {
    // 用户取消
  }
}

// 上传 Logo
const logoInputRef = ref<HTMLInputElement | null>(null)
const uploadingLogo = ref(false)

const triggerLogoUpload = () => {
  logoInputRef.value?.click()
}

const onLogoSelected = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件')
    return
  }

  uploadingLogo.value = true
  try {
    const response = await uploadLogo(file)
    if (response.data) {
      // 后端已自动保存 logo URL，强制重新加载配置
      await forceReloadThemeConfig()
      editingConfig.value.logoUrl = themeConfig.value.logoUrl
      showToast('Logo 上传成功')
    }
  } catch (error) {
    console.error('Failed to upload logo:', error)
    showToast('Logo 上传失败')
  } finally {
    uploadingLogo.value = false
    input.value = ''
  }
}

onMounted(async () => {
  // 直接从 API 获取最新配置，确保显示已保存的值
  const res = await getStyleConfig()
  const config = res.data as any
  editingConfig.value = {
    systemName: config.systemName || '价格管理系统',
    priceRiseColor: config.priceRiseColor || '#EF4444',
    priceFallColor: config.priceFallColor || '#10B981',
    priceFlatColor: config.priceFlatColor || '#9CA3AF',
    chartPrimaryColor: config.chartPrimaryColor || '#0D6E6E',
    chartBudgetColor: config.chartBudgetColor || '#F59E0B',
    chartColors: typeof config.chartColors === 'string' ? config.chartColors.split(',') : (config.chartColors || ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']),
    headingFont: config.headingFont || 'Newsreader',
    bodyFont: config.bodyFont || 'Inter',
    numberFont: config.numberFont || 'JetBrains Mono',
    logoUrl: config.logoUrl || '',
    logoSize: config.logoSize || 'medium',
    activeTheme: config.activeTheme || 'theme_red_green',
    // 字体大小
    fontSizeXs: config.fontSizeXs || '0.75rem',
    fontSizeSm: config.fontSizeSm || '0.875rem',
    fontSizeBase: config.fontSizeBase || '1rem',
    fontSizeLg: config.fontSizeLg || '1.125rem',
    fontSizeXl: config.fontSizeXl || '1.25rem',
    fontSize2xl: config.fontSize2xl || '1.5rem',
    fontSize3xl: config.fontSize3xl || '1.875rem'
  }
  selectedTheme.value = config.activeTheme || 'theme_red_green'
})
</script>

<template>
  <div class="style-settings-page">
    <div class="page-header">
      <h1 class="page-title">全局样式设置</h1>
      <p class="page-desc">统一管理系统颜色、字体和图像风格</p>
    </div>

    <div class="settings-content">
      <!-- 左侧设置表单 -->
      <div class="settings-form">
        <!-- 系统名称 -->
        <section class="settings-section">
          <h2 class="section-title">系统名称</h2>
          <div class="system-name-config">
            <div class="form-group">
              <label class="form-label">系统显示名称</label>
              <input
                type="text"
                v-model="editingConfig.systemName"
                class="system-name-input"
                placeholder="请输入系统名称"
                maxlength="50"
              />
              <p class="form-hint">将显示在登录页面和导航栏</p>
            </div>
          </div>
        </section>

        <!-- 主题选择 -->
        <section class="settings-section">
          <h2 class="section-title">预设主题</h2>
          <div class="theme-grid">
            <div
              v-for="theme in PRESET_THEMES"
              :key="theme.key"
              class="theme-card"
              :class="{ active: selectedTheme === theme.key }"
              @click="onThemeChange(theme.key)"
            >
              <div class="theme-colors">
                <span
                  class="color-dot rise"
                  :style="{ background: theme.colors.priceRise }"
                ></span>
                <span
                  class="color-dot fall"
                  :style="{ background: theme.colors.priceFall }"
                ></span>
              </div>
              <span class="theme-name">{{ theme.name }}</span>
              <span class="theme-desc">{{ theme.description }}</span>
            </div>
          </div>
        </section>

        <!-- 颜色配置 -->
        <section class="settings-section">
          <h2 class="section-title">颜色配置</h2>
          <div class="color-config">
            <div class="color-item">
              <label class="color-label">涨价颜色</label>
              <div class="color-input-group">
                <input
                  type="color"
                  v-model="editingConfig.priceRiseColor"
                  @change="onCustomColorChange"
                  class="color-picker"
                />
                <input
                  type="text"
                  v-model="editingConfig.priceRiseColor"
                  @change="onCustomColorChange"
                  class="color-text"
                  placeholder="#EF4444"
                />
              </div>
              <div class="color-preview">
                <span class="preview-label">预览：</span>
                <span
                  class="preview-badge up"
                  :style="{ color: editingConfig.priceRiseColor, background: editingConfig.priceRiseColor + '1A' }"
                >
                  ↑ 涨价
                </span>
              </div>
            </div>

            <div class="color-item">
              <label class="color-label">跌价颜色</label>
              <div class="color-input-group">
                <input
                  type="color"
                  v-model="editingConfig.priceFallColor"
                  @change="onCustomColorChange"
                  class="color-picker"
                />
                <input
                  type="text"
                  v-model="editingConfig.priceFallColor"
                  @change="onCustomColorChange"
                  class="color-text"
                  placeholder="#10B981"
                />
              </div>
              <div class="color-preview">
                <span class="preview-label">预览：</span>
                <span
                  class="preview-badge down"
                  :style="{ color: editingConfig.priceFallColor, background: editingConfig.priceFallColor + '1A' }"
                >
                  ↓ 跌价
                </span>
              </div>
            </div>

            <div class="color-item">
              <label class="color-label">图表主色</label>
              <div class="color-input-group">
                <input
                  type="color"
                  v-model="editingConfig.chartPrimaryColor"
                  @change="onCustomColorChange"
                  class="color-picker"
                />
                <input
                  type="text"
                  v-model="editingConfig.chartPrimaryColor"
                  @change="onCustomColorChange"
                  class="color-text"
                  placeholder="#0D6E6E"
                />
              </div>
            </div>
          </div>

          <!-- 图表配色预览 -->
          <div class="chart-colors-preview">
            <label class="color-label">图表配色</label>
            <div class="chart-colors">
              <span
                v-for="(color, index) in editingConfig.chartColors"
                :key="index"
                class="chart-color-dot"
                :style="{ background: color }"
              ></span>
            </div>
          </div>
        </section>

        <!-- 字体配置 -->
        <section class="settings-section">
          <h2 class="section-title">字体配置</h2>
          <div class="font-config">
            <div class="font-item">
              <label class="font-label">标题字体</label>
              <select v-model="editingConfig.headingFont" class="font-select">
                <option
                  v-for="font in AVAILABLE_FONTS.heading"
                  :key="font.value"
                  :value="font.value"
                >
                  {{ font.label }}
                </option>
              </select>
              <div class="font-preview heading" :style="{ fontFamily: editingConfig.headingFont }">
                标题字体预览
              </div>
            </div>

            <div class="font-item">
              <label class="font-label">正文字体</label>
              <select v-model="editingConfig.bodyFont" class="font-select">
                <option
                  v-for="font in AVAILABLE_FONTS.body"
                  :key="font.value"
                  :value="font.value"
                >
                  {{ font.label }}
                </option>
              </select>
              <div class="font-preview body" :style="{ fontFamily: editingConfig.bodyFont }">
                正文字体预览 The quick brown fox
              </div>
            </div>

            <div class="font-item">
              <label class="font-label">数字字体</label>
              <select v-model="editingConfig.numberFont" class="font-select">
                <option
                  v-for="font in AVAILABLE_FONTS.number"
                  :key="font.value"
                  :value="font.value"
                >
                  {{ font.label }}
                </option>
              </select>
              <div class="font-preview number" :style="{ fontFamily: editingConfig.numberFont }">
                123456789.00
              </div>
            </div>
          </div>
        </section>

        <!-- 字体大小配置 -->
        <section class="settings-section">
          <h2 class="section-title">字体大小</h2>

          <!-- 预设方案 -->
          <div class="font-preset-grid">
            <div
              v-for="preset in FONT_SIZE_PRESETS"
              :key="preset.key"
              class="font-preset-card"
              :class="{ active: selectedFontSizePreset === preset.key }"
              @click="applyFontSizePreset(preset)"
            >
              <div class="preset-name">{{ preset.name }}</div>
              <div class="preset-desc">{{ preset.description }}</div>
              <div v-if="preset.wcagCompliant" class="wcag-badge">WCAG ✓</div>
            </div>
          </div>

          <!-- 自定义配置 -->
          <div class="font-size-grid">
            <div v-for="field in FONT_SIZE_FIELDS" :key="field.key" class="font-size-item">
              <label class="font-size-label">{{ field.label }}</label>
              <div class="font-size-inputs">
                <input :value="getFontSizeValue(field.key)" @input="setFontSizeValue(field.key, ($event.target as HTMLInputElement).value)" class="font-size-input" :placeholder="field.default" />
                <span class="font-size-px">{{ toPx(getFontSizeValue(field.key)) }}</span>
              </div>
            </div>
          </div>

          <!-- 实时预览 -->
          <div class="font-preview-panel">
            <h3 class="preview-title">预览</h3>
            <div class="preview-content">
              <p class="preview-hero" :style="{ fontSize: editingConfig.fontSize3xl }">特大标题</p>
              <h2 class="preview-heading" :style="{ fontSize: editingConfig.fontSize2xl }">页面标题</h2>
              <h3 class="preview-title-text" :style="{ fontSize: editingConfig.fontSizeXl }">副标题</h3>
              <p class="preview-body" :style="{ fontSize: editingConfig.fontSizeBase }">正文内容示例，展示标准字体大小效果。</p>
              <span class="preview-caption" :style="{ fontSize: editingConfig.fontSizeXs }">辅助信息</span>
            </div>
          </div>
        </section>

        <!-- Logo 配置 -->
        <section class="settings-section">
          <h2 class="section-title">Logo 设置</h2>
          <div class="logo-config">
            <div class="logo-preview">
              <img
                v-if="logoUrlFull"
                :src="logoUrlFull"
                alt="Logo"
                class="logo-image"
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
              <input
                ref="logoInputRef"
                type="file"
                accept="image/*"
                @change="onLogoSelected"
                style="display: none"
              />
              <button
                class="btn-upload"
                @click="triggerLogoUpload"
                :disabled="uploadingLogo"
              >
                {{ uploadingLogo ? '上传中...' : '上传 Logo' }}
              </button>
              <span class="logo-tip">支持 PNG、JPG，最大 2MB</span>
            </div>
            <!-- Logo 尺寸选择 -->
            <div class="logo-size-selector">
              <span class="logo-size-label">Logo 尺寸：</span>
              <div class="logo-size-options">
                <button
                  v-for="size in AVAILABLE_LOGO_SIZES"
                  :key="size.value"
                  class="logo-size-btn"
                  :class="{ active: editingConfig.logoSize === size.value }"
                  @click="editingConfig.logoSize = size.value"
                >
                  {{ size.label }}
                </button>
              </div>
            </div>
          </div>
        </section>

        <!-- 保存按钮 -->
        <div class="actions">
          <button class="btn-reset" @click="resetConfig">
            重置为默认
          </button>
          <button
            class="btn-save"
            @click="saveConfig"
            :disabled="saving"
          >
            {{ saving ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </div>

      <!-- 右侧实时预览 -->
      <div class="preview-panel">
        <h2 class="preview-title">实时预览</h2>

        <!-- 价格涨跌预览 -->
        <div class="preview-card">
          <h3 class="preview-card-title">价格涨跌显示</h3>
          <div class="preview-prices">
            <div class="preview-price-item">
              <span class="preview-price-label">涨价</span>
              <span
                class="preview-price-value up"
                :style="{ color: previewPriceRiseColor }"
              >
                ↑ ¥12,345.00 (+2.5%)
              </span>
            </div>
            <div class="preview-price-item">
              <span class="preview-price-label">跌价</span>
              <span
                class="preview-price-value down"
                :style="{ color: previewPriceFallColor }"
              >
                ↓ ¥12,345.00 (-1.8%)
              </span>
            </div>
            <div class="preview-price-item">
              <span class="preview-price-label">持平</span>
              <span class="preview-price-value flat">
                — ¥12,345.00 (0.0%)
              </span>
            </div>
          </div>
        </div>

        <!-- 颜色预览 -->
        <div class="preview-card">
          <h3 class="preview-card-title">配色预览</h3>
          <div class="preview-colors">
            <div class="preview-color-row">
              <span class="preview-color-label">涨价色</span>
              <span
                class="preview-color-swatch"
                :style="{ background: previewPriceRiseColor }"
              ></span>
              <span class="preview-color-value">{{ previewPriceRiseColor }}</span>
            </div>
            <div class="preview-color-row">
              <span class="preview-color-label">跌价色</span>
              <span
                class="preview-color-swatch"
                :style="{ background: previewPriceFallColor }"
              ></span>
              <span class="preview-color-value">{{ previewPriceFallColor }}</span>
            </div>
            <div class="preview-color-row">
              <span class="preview-color-label">图表主色</span>
              <span
                class="preview-color-swatch"
                :style="{ background: editingConfig.chartPrimaryColor }"
              ></span>
              <span class="preview-color-value">{{ editingConfig.chartPrimaryColor }}</span>
            </div>
          </div>
        </div>

        <!-- 字体预览 -->
        <div class="preview-card">
          <h3 class="preview-card-title">字体预览</h3>
          <div class="preview-fonts">
            <div class="preview-font-item">
              <span class="preview-font-label">标题字体</span>
              <div
                class="preview-font-heading"
                :style="{ fontFamily: editingConfig.headingFont }"
              >
                {{ editingConfig.systemName || '价格管理系统' }}
              </div>
            </div>
            <div class="preview-font-item">
              <span class="preview-font-label">正文字体</span>
              <div
                class="preview-font-body"
                :style="{ fontFamily: editingConfig.bodyFont }"
              >
                这是正文字体预览，用于显示一般性内容文字。
              </div>
            </div>
            <div class="preview-font-item">
              <span class="preview-font-label">数字字体</span>
              <div
                class="preview-font-number"
                :style="{ fontFamily: editingConfig.numberFont }"
              >
                ¥123,456.78
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.style-settings-page {
  padding: 32px;
  
  background-color: #FAFAFA;
}

.page-header {
  margin-bottom: 32px;
}

.page-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-2xl);
  font-weight: 500;
  color: #1A1A1A;
  margin: 0 0 8px 0;
}

.page-desc {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: #666666;
  margin: 0;
}

.settings-content {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 24px;
}

.settings-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-section {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.section-title {
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
}

/* 系统名称配置 */
.system-name-config {
  max-width: 400px;
}

.system-name-input {
  width: 100%;
  height: 44px;
  padding: 0 16px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: #1A1A1A;
  outline: none;
  transition: border-color 150ms;
  box-sizing: border-box;
}

.system-name-input:focus {
  border-color: #0D6E6E;
}

.form-hint {
  font-size: var(--font-size-xs);
  color: #888888;
  margin-top: 8px;
  margin-bottom: 0;
}

/* 主题选择 */
.theme-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.theme-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px;
  border: 2px solid #E5E5E5;
  border-radius: 12px;
  cursor: pointer;
  transition: all 200ms ease;
}

.theme-card:hover {
  border-color: #0D6E6E;
}

.theme-card.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.05);
}

.theme-colors {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.color-dot {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.theme-name {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 4px;
}

.theme-desc {
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  color: #888888;
  text-align: center;
}

/* 颜色配置 */
.color-config {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.color-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.color-label {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
}

.color-input-group {
  display: flex;
  align-items: center;
  gap: 12px;
}

.color-picker {
  width: 48px;
  height: 48px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  cursor: pointer;
  padding: 0;
}

.color-picker::-webkit-color-swatch-wrapper {
  padding: 4px;
}

.color-picker::-webkit-color-swatch {
  border: none;
  border-radius: 4px;
}

.color-text {
  flex: 1;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  color: #1A1A1A;
  outline: none;
}

.color-text:focus {
  border-color: #0D6E6E;
}

.color-preview {
  display: flex;
  align-items: center;
  gap: 8px;
}

.preview-label {
  font-size: var(--font-size-xs);
  color: #888888;
}

.preview-badge {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.chart-colors-preview {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #F0F0F0;
}

.chart-colors {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.chart-color-dot {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

/* 字体配置 */
.font-config {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.font-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.font-label {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
}

.font-select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  color: #1A1A1A;
  background: white;
  cursor: pointer;
  outline: none;
}

.font-select:focus {
  border-color: #0D6E6E;
}

.font-preview {
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
  font-size: var(--font-size-sm);
}

.font-preview.heading {
  font-size: var(--font-size-lg);
  font-weight: 500;
}

.font-preview.body {
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.font-preview.number {
  font-size: var(--font-size-base);
  font-weight: 600;
}

/* 字体大小配置 */
.font-preset-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.font-preset-card {
  padding: 16px;
  border: 2px solid #E5E5E5;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  text-align: center;
}

.font-preset-card:hover {
  border-color: #0D6E6E;
}

.font-preset-card.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.05);
}

.preset-name {
  font-size: var(--font-size-base);
  font-weight: 600;
  margin-bottom: 4px;
}

.preset-desc {
  font-size: var(--font-size-xs);
  color: #888888;
}

.wcag-badge {
  margin-top: 8px;
  font-size: 0.6875rem;
  color: #10B981;
  font-weight: 500;
}

.font-size-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.font-size-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.font-size-label {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
}

.font-size-inputs {
  display: flex;
  align-items: center;
  gap: 12px;
}

.font-size-input {
  flex: 1;
  height: 40px;
  padding: 0 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-size: var(--font-size-sm);
  outline: none;
}

.font-size-input:focus {
  border-color: #0D6E6E;
}

.font-size-px {
  font-size: var(--font-size-xs);
  color: #888888;
  font-family: var(--font-mono);
}

.font-preview-panel {
  padding: 20px;
  background: #FAFAFA;
  border-radius: 12px;
}

.preview-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  margin-bottom: 16px;
}

.preview-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-hero {
  font-weight: 600;
  margin-bottom: 8px;
}

.preview-heading {
  font-weight: 600;
  margin-bottom: 8px;
}

.preview-title-text {
  font-weight: 500;
  margin-bottom: 8px;
}

.preview-body {
  margin-bottom: 8px;
}

.preview-caption {
  color: #888888;
}

/* Logo 配置 */
.logo-config {
  display: flex;
  align-items: center;
  gap: 24px;
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

.logo-image {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.logo-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: #888888;
  font-size: 0.625rem;
}

.logo-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn-upload {
  height: 36px;
  padding: 0 20px;
  background: #0D6E6E;
  color: white;
  border: none;
  border-radius: 8px;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition: background 200ms ease;
}

.btn-upload:hover:not(:disabled) {
  background: #0A5555;
}

.btn-upload:disabled {
  background: #9CA3AF;
  cursor: not-allowed;
}

.logo-tip {
  font-size: var(--font-size-xs);
  color: #888888;
}

.logo-size-selector {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-size-label {
  font-size: var(--font-size-sm);
  color: #333333;
}

.logo-size-options {
  display: flex;
  gap: 8px;
}

.logo-size-btn {
  padding: 6px 16px;
  background: #F5F5F5;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.logo-size-btn:hover {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.logo-size-btn.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: white;
}

/* 保存按钮 */
.actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.btn-reset {
  height: 44px;
  padding: 0 24px;
  background: white;
  color: #666666;
  border: 1px solid #E5E5E5;
  border-radius: 10px;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all 200ms ease;
}

.btn-reset:hover {
  background: #F5F5F5;
}

.btn-save {
  height: 44px;
  padding: 0 24px;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  color: white;
  border: none;
  border-radius: 10px;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all 200ms ease;
  box-shadow: 0 2px 8px rgba(13, 110, 110, 0.25);
}

.btn-save:hover:not(:disabled) {
  box-shadow: 0 4px 12px rgba(13, 110, 110, 0.35);
}

.btn-save:disabled {
  background: #9CA3AF;
  cursor: not-allowed;
  box-shadow: none;
}

/* 右侧预览面板 */
.preview-panel {
  position: sticky;
  top: 96px;
  height: fit-content;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.preview-title {
  font-family: var(--font-body);
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 4px 0;
}

.preview-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.preview-card-title {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 16px 0;
}

.preview-prices {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-price-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-price-label {
  width: 48px;
  font-size: var(--font-size-xs);
  color: #888888;
}

.preview-price-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.preview-price-value.up {
  color: var(--price-rise-color);
}

.preview-price-value.down {
  color: var(--price-fall-color);
}

.preview-price-value.flat {
  color: #9CA3AF;
}

.preview-colors {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-color-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-color-label {
  width: 64px;
  font-size: var(--font-size-xs);
  color: #888888;
}

.preview-color-swatch {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}

.preview-color-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: #1A1A1A;
}

.preview-fonts {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.preview-font-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-font-label {
  font-size: var(--font-size-xs);
  color: #888888;
}

.preview-font-heading {
  font-size: var(--font-size-lg);
  font-weight: 500;
}

.preview-font-body {
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.preview-font-number {
  font-size: var(--font-size-base);
  font-weight: 600;
}

@media (max-width: 1024px) {
  .style-settings-page {
    padding: 16px;
  }

  .settings-content {
    grid-template-columns: 1fr;
  }

  .preview-panel {
    position: static;
  }

  .theme-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
