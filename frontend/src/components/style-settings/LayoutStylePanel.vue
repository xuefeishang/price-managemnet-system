<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { getLayoutStyles } from '@/api/style'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import type { StylePreset } from '@/types/theme'

const workbench = useStyleSettingsWorkbench()

const layoutStyles = ref<StylePreset[]>([])
const loading = ref(false)

// 加载布局方案列表
const loadLayoutStyles = async () => {
  loading.value = true
  try {
    const res = await getLayoutStyles()
    layoutStyles.value = res.data || []
  } catch (error) {
    console.error('Failed to load layout styles:', error)
  } finally {
    loading.value = false
  }
}

// 切换布局方案（只更新草稿，需保存才生效）
const switchLayout = (layoutKey: string) => {
  workbench.applyLayoutStyle(layoutKey)
  showToast({ message: '布局方案已进入草稿，请点击顶部保存配置', position: 'top', duration: 1500 })
}

// 获取布局图标
const getLayoutIcon = (key: string) => {
  switch (key) {
    case 'layout_top_nav':
      return 'M3 4h18v4H3V4zm0 6h18v10H3V10z'
    case 'layout_left_nav':
      return 'M3 4h6v16H3V4zm8 0h10v4H11V4zm0 6h10v10H11V10z'
    case 'layout_dashboard':
      return 'M3 3h8v8H3V3zm10 0h8v8h-8V3zM3 13h8v8H3v-8zm10 0h8v8h-8v-8z'
    case 'layout_minimal':
      return 'M5 8h14v2H5V8zm0 4h14v2H5v-2zm0 4h10v2H5v-2z'
    default:
      return 'M3 3h18v18H3V3z'
  }
}

// 获取布局预览
const getLayoutPreview = (layout: StylePreset) => {
  const config = layout.config as any
  return {
    navBg: config?.navBgColor || '#FFFFFF',
    navText: config?.navTextColor || '#1A1A1A',
    pageBg: config?.pageBgColor || '#FAFAFA'
  }
}

// 当前激活的布局方案
const activeKey = computed(() => workbench.activeLayoutStyleKey.value)

onMounted(() => {
  loadLayoutStyles()
})
</script>

<template>
  <div class="layout-style-panel">
    <section class="config-section">
      <h2 class="section-title">
        布局方案
        <span class="section-status">当前：{{ activeKey || '默认' }}</span>
      </h2>
      <p class="section-hint">选择页面布局风格后进入草稿，点击顶部保存配置后生效</p>

      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
      </div>

      <div v-else class="layout-grid">
        <div
          v-for="layout in layoutStyles"
          :key="layout.key"
          class="layout-card"
          :class="{ active: layout.key === activeKey }"
          @click="switchLayout(layout.key)"
        >
          <div class="layout-preview" :style="{ background: getLayoutPreview(layout).pageBg }">
            <div
              class="preview-nav"
              :style="{
                background: getLayoutPreview(layout).navBg,
                color: getLayoutPreview(layout).navText
              }"
            >
              <svg width="40" height="20" viewBox="0 0 40 20">
                <path :d="getLayoutIcon(layout.key)" fill="currentColor" opacity="0.3"/>
              </svg>
            </div>
            <div class="preview-content">
              <div class="preview-card"></div>
              <div class="preview-card"></div>
            </div>
          </div>
          <div class="layout-info">
            <span class="layout-name">{{ layout.name }}</span>
            <span v-if="layout.isDefault" class="default-badge">默认</span>
          </div>
          <div v-if="layout.description" class="layout-desc">{{ layout.description }}</div>
          <div v-if="layout.key === activeKey" class="active-indicator">
            ✓ 当前使用
          </div>
        </div>
      </div>
    </section>

    <!-- 页面密度配置 -->
    <section class="config-section">
      <h2 class="section-title">页面密度</h2>
      <p class="section-hint">控制表格行高、卡片间距、表单间距</p>

      <div class="density-options">
        <div class="density-option" @click="workbench.updateDraft({ fontSizePreset: 'compact' })">
          <span class="density-label">紧凑</span>
          <span class="density-desc">适合数据密集型后台</span>
        </div>
        <div class="density-option active">
          <span class="density-label">标准</span>
          <span class="density-desc">通用场景</span>
        </div>
        <div class="density-option" @click="workbench.updateDraft({ fontSizePreset: 'large' })">
          <span class="density-label">舒展</span>
          <span class="density-desc">适合演示或大屏</span>
        </div>
      </div>
    </section>

    <!-- 卡片样式配置 -->
    <section class="config-section">
      <h2 class="section-title">卡片样式</h2>

      <div class="card-style-config">
        <div class="style-row">
          <span class="style-label">圆角</span>
          <div class="style-value">
            <span class="value-text">12px</span>
          </div>
        </div>
        <div class="style-row">
          <span class="style-label">阴影</span>
          <div class="style-value">
            <span class="value-text">轻</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.layout-style-panel {
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

.layout-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 12px;
}

.layout-card {
  padding: 16px;
  border: 2px solid #E5E5E5;
  border-radius: 12px;
  cursor: pointer;
  transition: all 150ms;
  background: #FFFFFF;
}

.layout-card:hover {
  border-color: #0D6E6E;
  box-shadow: 0 2px 8px rgba(13, 110, 110, 0.1);
}

.layout-card.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.04);
}

.layout-preview {
  height: 80px;
  border-radius: 6px;
  margin-bottom: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.preview-nav {
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-content {
  flex: 1;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.preview-card {
  flex: 1;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
}

.layout-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.layout-name {
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

.layout-desc {
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

/* 页面密度 */
.density-options {
  display: flex;
  gap: 12px;
}

.density-option {
  flex: 1;
  padding: 16px;
  border: 2px solid #E5E5E5;
  border-radius: 8px;
  cursor: pointer;
  text-align: center;
  transition: all 150ms;
}

.density-option:hover {
  border-color: #0D6E6E;
}

.density-option.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.04);
}

.density-label {
  display: block;
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 4px;
}

.density-desc {
  font-size: var(--font-size-xs);
  color: #888888;
}

/* 卡片样式 */
.card-style-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.style-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #F5F5F5;
}

.style-row:last-child {
  border-bottom: none;
}

.style-label {
  font-size: var(--font-size-sm);
  color: #1A1A1A;
}

.style-value {
  font-size: var(--font-size-sm);
  color: #666666;
}
</style>
