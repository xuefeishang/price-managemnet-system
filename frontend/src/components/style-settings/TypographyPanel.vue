<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { getFontPresets } from '@/api/style'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import type { StylePreset } from '@/types/theme'
import { FONT_SIZE_PRESETS, AVAILABLE_FONTS } from '@/types/theme'

const workbench = useStyleSettingsWorkbench()

const fontPresets = ref<StylePreset[]>([])
const loading = ref(false)

// 加载字号预设列表
const loadFontPresets = async () => {
  loading.value = true
  try {
    const res = await getFontPresets()
    fontPresets.value = res.data || []
  } catch (error) {
    console.error('Failed to load font presets:', error)
  } finally {
    loading.value = false
  }
}

// 切换字号预设
const switchPreset = async (presetKey: string) => {
  try {
    await workbench.applyFontPreset(presetKey)
    showToast({ message: '字号预设已切换', position: 'top', duration: 1500 })
  } catch (error) {
    showToast({ message: '切换失败，已恢复原设置', position: 'top', duration: 2000 })
  }
}

// 更新字体
const updateFont = async (key: 'headingFont' | 'bodyFont' | 'numberFont', value: string) => {
  if (!workbench.draftConfig.value) return
  workbench.draftConfig.value[key] = value
  await workbench.applyAndPersist({ [key]: value })
}

// rem 转 px
const getFontSizePx = (rem: string): string => {
  const match = rem.match(/^([\d.]+)rem$/)
  if (!match) return rem
  return `${parseFloat(match[1]) * 16}px`
}

// 当前激活的字号预设
const activeKey = computed(() => workbench.activeFontPresetKey.value)

onMounted(() => {
  loadFontPresets()
})
</script>

<template>
  <div class="typography-panel">
    <!-- 字号预设 -->
    <section class="config-section">
      <h2 class="section-title">
        字号预设
        <span class="section-status">当前：{{ activeKey || '标准' }}</span>
      </h2>
      <p class="section-hint">选择预设字号方案，影响全局文本显示</p>

      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
      </div>

      <div v-else class="preset-grid">
        <div
          v-for="preset in FONT_SIZE_PRESETS"
          :key="preset.key"
          class="preset-card"
          :class="{ active: preset.key === activeKey }"
          @click="switchPreset(preset.key)"
        >
          <span class="preset-name">{{ preset.name }}</span>
          <span class="preset-desc">{{ preset.description }}</span>
          <span v-if="preset.wcagCompliant" class="wcag-badge">WCAG ✓</span>
          <div v-if="preset.key === activeKey" class="active-indicator">
            ✓ 当前
          </div>
        </div>
      </div>
    </section>

    <!-- 字体族配置 -->
    <section class="config-section" v-if="workbench.draftConfig.value">
      <h2 class="section-title">字体族</h2>
      <p class="section-hint">即时生效，自动保存</p>

      <div class="font-config">
        <div class="font-row">
          <span class="font-label">标题字体</span>
          <select
            class="font-select"
            :value="workbench.draftConfig.value.headingFont"
            @change="updateFont('headingFont', ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="opt in AVAILABLE_FONTS.heading" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
          <div class="font-preview" :style="{ fontFamily: workbench.draftConfig.value.headingFont }">
            标题字体预览
          </div>
        </div>

        <div class="font-row">
          <span class="font-label">正文字体</span>
          <select
            class="font-select"
            :value="workbench.draftConfig.value.bodyFont"
            @change="updateFont('bodyFont', ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="opt in AVAILABLE_FONTS.body" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
          <div class="font-preview" :style="{ fontFamily: workbench.draftConfig.value.bodyFont }">
            正文字体预览 The quick brown fox
          </div>
        </div>

        <div class="font-row">
          <span class="font-label">数字字体</span>
          <select
            class="font-select"
            :value="workbench.draftConfig.value.numberFont"
            @change="updateFont('numberFont', ($event.target as HTMLSelectElement).value)"
          >
            <option v-for="opt in AVAILABLE_FONTS.number" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </option>
          </select>
          <div class="font-preview number" :style="{ fontFamily: workbench.draftConfig.value.numberFont }">
            ¥123,456.78
          </div>
        </div>
      </div>
    </section>

    <!-- 字号预览 -->
    <section class="config-section" v-if="workbench.draftConfig.value">
      <h2 class="section-title">字号预览</h2>

      <div class="size-preview">
        <div class="preview-item">
          <span class="preview-label">3xl</span>
          <span class="preview-text" :style="{ fontSize: workbench.draftConfig.value.fontSize3xl }">
            页面主标题
          </span>
          <span class="preview-value">{{ getFontSizePx(workbench.draftConfig.value.fontSize3xl) }}</span>
        </div>
        <div class="preview-item">
          <span class="preview-label">2xl</span>
          <span class="preview-text" :style="{ fontSize: workbench.draftConfig.value.fontSize2xl }">
            页面副标题
          </span>
          <span class="preview-value">{{ getFontSizePx(workbench.draftConfig.value.fontSize2xl) }}</span>
        </div>
        <div class="preview-item">
          <span class="preview-label">base</span>
          <span class="preview-text" :style="{ fontSize: workbench.draftConfig.value.fontSizeBase }">
            正文表头字号
          </span>
          <span class="preview-value">{{ getFontSizePx(workbench.draftConfig.value.fontSizeBase) }}</span>
        </div>
        <div class="preview-item">
          <span class="preview-label">sm</span>
          <span class="preview-text" :style="{ fontSize: workbench.draftConfig.value.fontSizeSm }">
            表格内容字号
          </span>
          <span class="preview-value">{{ getFontSizePx(workbench.draftConfig.value.fontSizeSm) }}</span>
        </div>
        <div class="preview-item">
          <span class="preview-label">xs</span>
          <span class="preview-text" :style="{ fontSize: workbench.draftConfig.value.fontSizeXs }">
            辅助信息字号
          </span>
          <span class="preview-value">{{ getFontSizePx(workbench.draftConfig.value.fontSizeXs) }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.typography-panel {
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
  margin: 0 0 8px 0;
}

.section-status {
  font-size: var(--font-size-xs);
  color: #666666;
  padding: 4px 12px;
  background: #F5F5F5;
  border-radius: 4px;
}

.section-hint {
  font-size: var(--font-size-xs);
  color: #888888;
  margin: 0 0 20px 0;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 20px;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 预设网格 */
.preset-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.preset-card {
  padding: 16px;
  border: 2px solid #E5E5E5;
  border-radius: 10px;
  cursor: pointer;
  text-align: center;
  transition: all 150ms;
}

.preset-card:hover {
  border-color: #0D6E6E;
}

.preset-card.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.05);
}

.preset-name {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 4px;
}

.preset-desc {
  display: block;
  font-size: var(--font-size-xs);
  color: #888888;
}

.wcag-badge {
  display: inline-block;
  margin-top: 8px;
  font-size: 10px;
  color: #10B981;
  font-weight: 500;
}

.active-indicator {
  margin-top: 8px;
  font-size: var(--font-size-xs);
  color: #0D6E6E;
  font-weight: 500;
}

/* 字体配置 */
.font-config {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.font-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.font-label {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
}

.font-select {
  height: 40px;
  padding: 0 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-size: var(--font-size-sm);
  color: #1A1A1A;
  background: #FFFFFF;
  cursor: pointer;
}

.font-select:focus {
  border-color: #0D6E6E;
  outline: none;
}

.font-preview {
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
  font-size: var(--font-size-sm);
}

.font-preview.number {
  font-size: var(--font-size-base);
  font-weight: 600;
}

/* 字号预览 */
.size-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: #FAFAFA;
  border-radius: 10px;
}

.preview-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.preview-label {
  width: 40px;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: #888888;
}

.preview-text {
  flex: 1;
  font-weight: 500;
}

.preview-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: #888888;
}

@media (max-width: 768px) {
  .preset-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>