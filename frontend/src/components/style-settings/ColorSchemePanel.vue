<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { getColorSchemes } from '@/api/style'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import type { StylePreset } from '@/types/theme'

const workbench = useStyleSettingsWorkbench()

const colorSchemes = ref<StylePreset[]>([])
const loading = ref(false)

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

// 切换色彩方案
const switchScheme = async (schemeKey: string) => {
  try {
    await workbench.applyColorScheme(schemeKey)
    showToast({ message: '色彩方案已切换', position: 'top', duration: 1500 })
  } catch (error) {
    showToast({ message: '切换失败，已恢复原设置', position: 'top', duration: 2000 })
  }
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
      <p class="section-hint">选择预设色彩方案，影响涨跌色、图表色板等</p>

      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
      </div>

      <div v-else class="scheme-grid">
        <div
          v-for="scheme in colorSchemes"
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

    <!-- 价格涨跌色配置 -->
    <section class="config-section" v-if="workbench.draftConfig.value">
      <h2 class="section-title">价格涨跌色</h2>
      <p class="section-hint">即时生效，自动保存</p>

      <div class="color-config">
        <div class="color-row">
          <span class="color-label">上涨色</span>
          <div class="color-input-group">
            <input
              type="color"
              v-model="workbench.draftConfig.value.priceRiseColor"
              @change="workbench.applyAndPersist({ priceRiseColor: workbench.draftConfig.value.priceRiseColor })"
              class="color-picker"
            />
            <input
              type="text"
              v-model="workbench.draftConfig.value.priceRiseColor"
              @change="workbench.applyAndPersist({ priceRiseColor: workbench.draftConfig.value.priceRiseColor })"
              class="color-text"
            />
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
              v-model="workbench.draftConfig.value.priceFallColor"
              @change="workbench.applyAndPersist({ priceFallColor: workbench.draftConfig.value.priceFallColor })"
              class="color-picker"
            />
            <input
              type="text"
              v-model="workbench.draftConfig.value.priceFallColor"
              @change="workbench.applyAndPersist({ priceFallColor: workbench.draftConfig.value.priceFallColor })"
              class="color-text"
            />
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
              v-model="workbench.draftConfig.value.priceFlatColor"
              @change="workbench.applyAndPersist({ priceFlatColor: workbench.draftConfig.value.priceFlatColor })"
              class="color-picker"
            />
            <input
              type="text"
              v-model="workbench.draftConfig.value.priceFlatColor"
              @change="workbench.applyAndPersist({ priceFlatColor: workbench.draftConfig.value.priceFlatColor })"
              class="color-text"
            />
          </div>
          <span
            class="preview-badge flat"
            :style="{ color: workbench.draftConfig.value.priceFlatColor, background: workbench.draftConfig.value.priceFlatColor + '1A' }"
          >
            — 0.0%
          </span>
        </div>
      </div>
    </section>

    <!-- 图表色板 -->
    <section class="config-section" v-if="workbench.draftConfig.value">
      <h2 class="section-title">
        图表色板
        <span class="section-status">9 色</span>
      </h2>

      <div class="chart-colors">
        <span
          v-for="(color, index) in workbench.draftConfig.value.chartColors"
          :key="index"
          class="chart-color-dot"
          :style="{ background: color }"
        ></span>
      </div>
    </section>
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

.preview-badge {
  padding: 4px 12px;
  border-radius: 6px;
  font-size: var(--font-size-sm);
  font-weight: 600;
}

/* 图表色板 */
.chart-colors {
  display: flex;
  gap: 8px;
}

.chart-color-dot {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
}
</style>