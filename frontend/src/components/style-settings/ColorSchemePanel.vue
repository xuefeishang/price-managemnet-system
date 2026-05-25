<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { showToast } from 'vant'
import { getColorSchemes } from '@/api/style'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import type { StylePreset } from '@/types/theme'
import { isSupportedHexColor, type PriceColorField } from '@/utils/styleColorValidation'

const workbench = useStyleSettingsWorkbench()

const colorSchemes = ref<StylePreset[]>([])
const loading = ref(false)
const hiddenDuplicateSchemeKeys = new Set(['scheme_classic'])

// 加载色彩方案列表
const loadColorSchemes = async () => {
  loading.value = true
  try {
    const res = await getColorSchemes()
    colorSchemes.value = res.data || []
  } catch (error) {
    console.error('Failed to load color schemes:', error)
  } finally {
    loading.value = false
  }
}

// 切换色彩方案（只更新草稿，需保存才生效）
const switchScheme = (schemeKey: string) => {
  workbench.applyColorScheme(schemeKey)
  showToast({ message: '色彩方案已进入草稿，请点击顶部保存配置', position: 'top', duration: 1500 })
}

// 获取方案预览颜色
const getSchemePreview = (scheme: StylePreset) => {
  const config = scheme.config as any
  return {
    riseColor: config?.priceRiseColor || '#EF4444',
    fallColor: config?.priceFallColor || '#10B981',
    primaryColor: config?.chartPrimaryColor || '#0D6E6E'
  }
}

// 当前激活的色彩方案
const activeKey = computed(() => workbench.activeColorSchemeKey.value)
const visibleColorSchemes = computed(() =>
  colorSchemes.value.filter(scheme => !hiddenDuplicateSchemeKeys.has(scheme.key))
)

const colorTextValues = ref<Record<PriceColorField, string>>({
  priceRiseColor: '',
  priceFallColor: '',
  priceFlatColor: ''
})

const colorErrors = ref<Record<PriceColorField, boolean>>({
  priceRiseColor: false,
  priceFallColor: false,
  priceFlatColor: false
})

const updateColorDraft = (
  key: PriceColorField,
  value: string
) => {
  if (!workbench.draftConfig.value || workbench.draftConfig.value[key] === value) return
  workbench.updateDraft({ [key]: value })
}

const handleColorPickerInput = (key: PriceColorField, value: string) => {
  colorTextValues.value[key] = value
  colorErrors.value[key] = false
  updateColorDraft(key, value)
}

const handleColorTextInput = (key: PriceColorField, value: string) => {
  colorTextValues.value[key] = value
  const normalizedValue = value.trim()
  const isValid = isSupportedHexColor(normalizedValue)
  colorErrors.value[key] = !isValid
  if (isValid) {
    updateColorDraft(key, normalizedValue)
  }
}

watch(
  () => [
    workbench.draftConfig.value?.priceRiseColor,
    workbench.draftConfig.value?.priceFallColor,
    workbench.draftConfig.value?.priceFlatColor
  ],
  () => {
    const config = workbench.draftConfig.value
    if (!config) return
    colorTextValues.value.priceRiseColor = config.priceRiseColor
    colorTextValues.value.priceFallColor = config.priceFallColor
    colorTextValues.value.priceFlatColor = config.priceFlatColor
    colorErrors.value.priceRiseColor = !isSupportedHexColor(config.priceRiseColor)
    colorErrors.value.priceFallColor = !isSupportedHexColor(config.priceFallColor)
    colorErrors.value.priceFlatColor = !isSupportedHexColor(config.priceFlatColor)
  },
  { immediate: true }
)

onMounted(() => {
  loadColorSchemes()
})
</script>

<template>
  <div class="color-scheme-panel">
    <section class="config-section">
      <h2 class="section-title">
        色彩方案
        <span class="section-status">当前：{{ activeKey || '默认' }}</span>
      </h2>
      <p class="section-hint">选择预设后进入草稿，点击顶部保存配置后生效</p>

      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
      </div>

      <div v-else class="scheme-grid">
        <div
          v-for="scheme in visibleColorSchemes"
          :key="scheme.key"
          class="scheme-card"
          :class="{ active: scheme.key === activeKey }"
          @click="switchScheme(scheme.key)"
        >
          <div class="scheme-preview">
            <div
              class="preview-color rise"
              :style="{ background: getSchemePreview(scheme).riseColor }"
            ></div>
            <div
              class="preview-color fall"
              :style="{ background: getSchemePreview(scheme).fallColor }"
            ></div>
            <div
              class="preview-color primary"
              :style="{ background: getSchemePreview(scheme).primaryColor }"
            ></div>
          </div>
          <div class="scheme-info">
            <span class="scheme-name">{{ scheme.name }}</span>
            <span v-if="scheme.isDefault" class="default-badge">默认</span>
          </div>
          <div v-if="scheme.description" class="scheme-desc">{{ scheme.description }}</div>
          <div v-if="scheme.key === activeKey" class="active-indicator">
            ✓ 当前使用
          </div>
        </div>
      </div>
    </section>

    <details class="config-section advanced-section" v-if="workbench.draftConfig.value" open>
      <summary>
        <span>高级微调</span>
        <small>价格涨跌色</small>
      </summary>
      <p class="section-hint">适合临时修正涨跌/持平颜色；修改后进入草稿，点击顶部保存配置后生效</p>

      <div class="color-config">
        <div class="color-row">
          <span class="color-label">上涨色</span>
          <div class="color-input-group">
            <input
              type="color"
              :value="workbench.draftConfig.value.priceRiseColor"
              @input="handleColorPickerInput('priceRiseColor', ($event.target as HTMLInputElement).value)"
              class="color-picker"
            />
            <div class="color-text-field">
              <input
                type="text"
                :value="colorTextValues.priceRiseColor"
                @input="handleColorTextInput('priceRiseColor', ($event.target as HTMLInputElement).value)"
                class="color-text"
                :class="{ invalid: colorErrors.priceRiseColor }"
              />
              <span v-if="colorErrors.priceRiseColor" class="color-error">请输入 #RGB 或 #RRGGBB</span>
            </div>
          </div>
          <span
            class="preview-badge up"
            :style="{ color: workbench.draftConfig.value.priceRiseColor, background: workbench.draftConfig.value.priceRiseColor + '1A' }"
          >
            ↑ +2.5%
          </span>
        </div>

        <div class="color-row">
          <span class="color-label">下跌色</span>
          <div class="color-input-group">
            <input
              type="color"
              :value="workbench.draftConfig.value.priceFallColor"
              @input="handleColorPickerInput('priceFallColor', ($event.target as HTMLInputElement).value)"
              class="color-picker"
            />
            <div class="color-text-field">
              <input
                type="text"
                :value="colorTextValues.priceFallColor"
                @input="handleColorTextInput('priceFallColor', ($event.target as HTMLInputElement).value)"
                class="color-text"
                :class="{ invalid: colorErrors.priceFallColor }"
              />
              <span v-if="colorErrors.priceFallColor" class="color-error">请输入 #RGB 或 #RRGGBB</span>
            </div>
          </div>
          <span
            class="preview-badge down"
            :style="{ color: workbench.draftConfig.value.priceFallColor, background: workbench.draftConfig.value.priceFallColor + '1A' }"
          >
            ↓ -1.2%
          </span>
        </div>

        <div class="color-row">
          <span class="color-label">持平色</span>
          <div class="color-input-group">
            <input
              type="color"
              :value="workbench.draftConfig.value.priceFlatColor"
              @input="handleColorPickerInput('priceFlatColor', ($event.target as HTMLInputElement).value)"
              class="color-picker"
            />
            <div class="color-text-field">
              <input
                type="text"
                :value="colorTextValues.priceFlatColor"
                @input="handleColorTextInput('priceFlatColor', ($event.target as HTMLInputElement).value)"
                class="color-text"
                :class="{ invalid: colorErrors.priceFlatColor }"
              />
              <span v-if="colorErrors.priceFlatColor" class="color-error">请输入 #RGB 或 #RRGGBB</span>
            </div>
          </div>
          <span
            class="preview-badge flat"
            :style="{ color: workbench.draftConfig.value.priceFlatColor, background: workbench.draftConfig.value.priceFlatColor + '1A' }"
          >
            — 0.0%
          </span>
        </div>
      </div>
    </details>
  </div>
</template>

<style scoped>
.color-scheme-panel {
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

.advanced-section summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
}

.advanced-section summary small {
  font-size: var(--font-size-xs);
  font-weight: 400;
  color: #666666;
  padding: 4px 10px;
  background: #F5F5F5;
  border-radius: 4px;
}

.advanced-section[open] summary {
  margin-bottom: 8px;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 40px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.scheme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.scheme-card {
  padding: 16px;
  border: 2px solid #E5E5E5;
  border-radius: 12px;
  cursor: pointer;
  transition: all 150ms;
  background: #FFFFFF;
}

.scheme-card:hover {
  border-color: #0D6E6E;
  box-shadow: 0 2px 8px rgba(13, 110, 110, 0.1);
}

.scheme-card.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.04);
}

.scheme-preview {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
}

.preview-color {
  width: 32px;
  height: 24px;
  border-radius: 4px;
}

.scheme-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.scheme-name {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #1A1A1A;
}

.default-badge {
  font-size: 10px;
  padding: 1px 4px;
  background: #E0F2F1;
  color: #0D6E6E;
  border-radius: 3px;
}

.scheme-desc {
  font-size: var(--font-size-xs);
  color: #888888;
  line-height: 1.4;
}

.active-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: var(--font-size-xs);
  color: #0D6E6E;
  font-weight: 500;
}

/* 颜色配置 */
.color-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.color-label {
  width: 80px;
  font-size: var(--font-size-sm);
  color: #1A1A1A;
}

.color-input-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-text-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.color-picker {
  width: 40px;
  height: 40px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  cursor: pointer;
  padding: 2px;
}

.color-text {
  width: 100px;
  height: 36px;
  padding: 0 8px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: var(--font-size-sm);
  font-family: var(--font-mono);
}

.color-text.invalid {
  border-color: #EF4444;
  background: #FEF2F2;
}

.color-error {
  font-size: 10px;
  color: #EF4444;
  line-height: 1.2;
}

.preview-badge {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: var(--font-size-sm);
  font-weight: 600;
}

</style>
