<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, provide, onErrorCaptured } from 'vue'
import { showToast } from 'vant'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import { useHomePreviewState } from '@/composables/useHomePreviewState'
import { useHomeProductOrderState } from '@/composables/useHomeProductOrderState'
import { useCategoryPreviewState } from '@/composables/useCategoryPreviewState'
import { getInvalidPriceColorLabels } from '@/utils/styleColorValidation'
import { resolveStylePresetName } from '@/utils/stylePresetNames'

// Props
const props = defineProps<{
  activeSection?: string
}>()

// Emits
const emit = defineEmits<{
  (e: 'update:activeSection', value: string): void
}>()

// 工作台状态
const workbench = useStyleSettingsWorkbench()

// 首页和分类状态
const homeState = useHomePreviewState()
const productOrderState = useHomeProductOrderState()
const categoryState = useCategoryPreviewState()

// 一级导航配置
const sections = [
  { key: 'overview', label: '总览', icon: 'dashboard' },
  { key: 'brand', label: '品牌', icon: 'brand' },
  { key: 'color', label: '色彩', icon: 'color' },
  { key: 'typography', label: '排版', icon: 'font' },
  { key: 'layout', label: '布局', icon: 'layout' },
  { key: 'home', label: '首页体验', icon: 'home' },
  { key: 'home-sort', label: '首页排序', icon: 'sort' },
  { key: 'category', label: '分类视觉', icon: 'category' },
  { key: 'version', label: '版本恢复', icon: 'history' }
]

// 当前激活的导航 - 以 props 为唯一来源
const currentSection = computed(() => props.activeSection || 'overview')

// 移动端预览抽屉
const showPreviewDrawer = ref(false)

// 响应式检测
const isMobile = ref(false)
const checkMobile = () => {
  isMobile.value = window.innerWidth < 1024
}

const effectiveSaveStatus = computed(() => {
  if (workbench.saveStatus.value === 'saving' || productOrderState.saving.value) return 'saving'
  if (workbench.saveStatus.value === 'failed') return 'failed'
  if (hasUnsavedChanges.value) return 'dirty'
  if (workbench.saveStatus.value === 'saved') return 'saved'
  return workbench.saveStatus.value
})

// 保存状态显示
const saveStatusText = computed(() => {
  switch (effectiveSaveStatus.value) {
    case 'saving': return '正在保存...'
    case 'saved': return workbench.lastSavedAt.value
      ? `已保存 ${formatTime(workbench.lastSavedAt.value)}`
      : '已保存'
    case 'failed': return '保存失败'
    case 'dirty': return '有未保存的更改'
    default: return '当前配置'
  }
})

// 是否有未保存更改（包含样式、首页、分类）
const hasUnsavedChanges = computed(() =>
  workbench.saveStatus.value === 'dirty' ||
  homeState.hasUnsavedChanges.value ||
  productOrderState.hasUnsavedChanges.value ||
  categoryState.hasUnsavedChanges.value
)

// 保存配置（统一保存样式、首页、分类）
const handleSave = async () => {
  try {
    const invalidColorLabels = getInvalidPriceColorLabels(workbench.draftConfig.value)
    if (invalidColorLabels.length > 0) {
      showToast({
        message: `${invalidColorLabels.join('、')}格式不正确，请使用 #RGB 或 #RRGGBB`,
        position: 'top',
        duration: 2500
      })
      return
    }

    // 并行保存所有配置
    const savePromises: Promise<void>[] = []

    if (workbench.saveStatus.value === 'dirty') {
      savePromises.push(workbench.saveConfig())
    }
    if (homeState.hasUnsavedChanges.value) {
      savePromises.push(homeState.saveAll())
    }
    if (productOrderState.hasUnsavedChanges.value) {
      savePromises.push(productOrderState.saveOrder())
    }
    if (categoryState.hasUnsavedChanges.value) {
      savePromises.push(categoryState.saveAll())
    }

    await Promise.all(savePromises)
    showToast({ message: '配置已保存', position: 'top', duration: 1500 })
  } catch (error) {
    showToast({ message: '保存失败，请重试', position: 'top', duration: 2000 })
  }
}

// 格式化时间
const formatTime = (iso: string) => {
  const date = new Date(iso)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 切换导航
const switchSection = (key: string) => {
  emit('update:activeSection', key)
}

// 从预设缓存获取方案名称（动态来源）
const colorSchemeName = computed(() => {
  const key = workbench.activeColorSchemeKey.value
  return resolveStylePresetName(workbench.colorSchemes.value, key, '默认')
})

const fontPresetName = computed(() => {
  const key = workbench.activeFontPresetKey.value
  return resolveStylePresetName(workbench.fontPresets.value, key, '标准')
})

const layoutStyleName = computed(() => {
  const key = workbench.activeLayoutStyleKey.value
  return resolveStylePresetName(workbench.layoutStyles.value, key, '默认')
})

// 错误边界处理
onErrorCaptured((err, _instance, info) => {
  console.error('StyleSettings error:', err, info)
  showToast('配置加载失败，请刷新重试')
  return false // 阻止错误传播
})

// 提供给子组件
provide('workbench', workbench)
provide('currentSection', currentSection)

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
  workbench.loadWorkbenchConfig()
  homeState.loadConfig()
  productOrderState.loadOrder()
  categoryState.loadCategories(true)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<template>
  <div class="style-settings-shell">
    <!-- 顶部状态栏 -->
    <header class="status-bar">
      <div class="status-left">
        <h1 class="page-title">全局样式设置</h1>
        <div class="current-scheme" v-if="workbench.isLoaded.value">
          <span class="scheme-tag">{{ colorSchemeName }}</span>
          <span class="scheme-divider">/</span>
          <span class="scheme-tag">{{ fontPresetName }}</span>
          <span class="scheme-divider">/</span>
          <span class="scheme-tag">{{ layoutStyleName }}</span>
        </div>
      </div>
      <div class="status-right">
        <span class="save-status" :class="effectiveSaveStatus">
          {{ saveStatusText }}
        </span>
        <div class="quick-actions">
          <button
            class="action-btn primary"
            :class="{ saving: effectiveSaveStatus === 'saving' }"
            :disabled="!hasUnsavedChanges || effectiveSaveStatus === 'saving'"
            @click="handleSave"
          >
            {{ effectiveSaveStatus === 'saving' ? '保存中...' : '保存配置' }}
          </button>
        </div>
      </div>
    </header>

    <nav class="section-tabs" aria-label="样式配置域">
      <div class="section-tabs-scroll">
        <button
          v-for="section in sections"
          :key="section.key"
          class="section-tab"
          :class="{ active: currentSection === section.key }"
          type="button"
          @click="switchSection(section.key)"
        >
          {{ section.label }}
        </button>
      </div>
    </nav>

    <!-- PC 端两栏布局 -->
    <div class="workbench-layout" v-if="!isMobile">
      <!-- 中央配置区 -->
      <main class="config-panel">
        <slot name="config" :section="currentSection"></slot>
      </main>

      <!-- 右侧预览区 -->
      <aside class="preview-panel">
        <slot name="preview"></slot>
      </aside>
    </div>

    <!-- 移动端布局 -->
    <div class="mobile-layout" v-else>
      <!-- 配置区 -->
      <main class="mobile-config">
        <slot name="config" :section="currentSection"></slot>
      </main>

      <!-- 预览按钮 -->
      <button class="preview-toggle" @click="showPreviewDrawer = true">
        打开实时预览
      </button>

      <!-- 预览抽屉 -->
      <div v-if="showPreviewDrawer" class="preview-drawer-overlay" @click="showPreviewDrawer = false">
        <div class="preview-drawer" @click.stop>
          <div class="drawer-header">
            <h3>实时预览</h3>
            <button class="drawer-close" @click="showPreviewDrawer = false">×</button>
          </div>
          <div class="drawer-content">
            <slot name="preview"></slot>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
:global(.pc-main:has(.style-settings-shell)) {
  overflow-y: visible;
}

.style-settings-shell {
  min-height: 100vh;
  background-color: var(--bg-page, #FAFAFA);
}

/* 顶部状态栏 */
.status-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: var(--bg-card, #FFFFFF);
  border-bottom: 1px solid var(--border-color, #E5E5E5);
}

.status-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-title {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0;
}

.current-scheme {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-sm);
  color: #666666;
}

.scheme-tag {
  padding: 4px 12px;
  background: var(--bg-secondary, #F5F5F5);
  border-radius: 4px;
}

.scheme-divider {
  color: var(--border-color, #E5E5E5);
}

.status-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.save-status {
  font-size: var(--font-size-sm);
  color: #666666;
}

.save-status.dirty {
  color: #F59E0B;
}

.save-status.saving {
  color: var(--primary-color, #0D6E6E);
}

.save-status.saved {
  color: #10B981;
}

.save-status.failed {
  color: #EF4444;
}

.quick-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 8px 16px;
  font-size: var(--font-size-sm);
  border: 1px solid var(--border-color, #E5E5E5);
  border-radius: 6px;
  background: var(--bg-card, #FFFFFF);
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.action-btn:hover {
  border-color: var(--primary-color, #0D6E6E);
  color: var(--primary-color, #0D6E6E);
}

.action-btn.primary {
  background: var(--primary-color, #0D6E6E);
  border-color: var(--primary-color, #0D6E6E);
  color: #FFFFFF;
}

.action-btn.primary:hover {
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 85%, black);
}

.action-btn.primary:disabled {
  background: #9CA3AF;
  border-color: #9CA3AF;
  cursor: not-allowed;
}

.action-btn.primary.saving {
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 85%, black);
}

/* 配置域导航 */
.section-tabs {
  position: sticky;
  top: 65px;
  z-index: 90;
  padding: 10px 24px;
  background: var(--bg-card, #FFFFFF);
  border-bottom: 1px solid var(--border-color, #E5E5E5);
}

.section-tabs-scroll {
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 1600px;
  margin: 0 auto;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.section-tabs-scroll::-webkit-scrollbar {
  display: none;
}

.section-tab {
  flex-shrink: 0;
  min-height: 32px;
  padding: 7px 14px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #666666;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.section-tab:hover {
  background: var(--bg-secondary, #F5F5F5);
  color: #1A1A1A;
}

.section-tab.active {
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 10%, transparent);
  color: var(--primary-color, #0D6E6E);
}

/* PC 端两栏布局 */
.workbench-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 24px;
  padding: 24px;
  max-width: 1600px;
  margin: 0 auto;
  align-items: start;
}

/* 中央配置区 */
.config-panel {
  min-width: 0;
}

/* 右侧预览区 */
.preview-panel {
  position: sticky;
  top: 125px;
  align-self: start;
  padding-right: 2px;
  overflow: visible;
  z-index: 20;
}

/* 移动端布局 */
.mobile-layout {
  padding: 16px;
}

.mobile-config {
  background: var(--bg-card, #FFFFFF);
  border: 1px solid var(--border-color, #E5E5E5);
  border-radius: 12px;
  padding: 16px;
}

.preview-toggle {
  position: fixed;
  bottom: 80px;
  right: 16px;
  padding: 12px 24px;
  background: var(--primary-color, #0D6E6E);
  color: #FFFFFF;
  border: none;
  border-radius: 24px;
  font-size: var(--font-size-sm);
  cursor: pointer;
  box-shadow: 0 4px 12px color-mix(in srgb, var(--primary-color, #0D6E6E) 30%, transparent);
}

/* 预览抽屉 */
.preview-drawer-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}

.preview-drawer {
  width: 100%;
  background: var(--bg-card, #FFFFFF);
  border-radius: 16px 16px 0 0;
  overflow: visible;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #E5E5E5);
}

.drawer-header h3 {
  margin: 0;
  font-size: var(--font-size-base);
}

.drawer-close {
  width: 32px;
  height: 32px;
  border: none;
  background: var(--bg-secondary, #F5F5F5);
  border-radius: 8px;
  font-size: 20px;
  cursor: pointer;
}

.drawer-content {
  padding: 16px;
  overflow: visible;
}

/* 响应式 */
@media (max-width: 1280px) {
  .workbench-layout {
    grid-template-columns: minmax(0, 1fr) 320px;
  }
}

@media (max-width: 1024px) {
  .status-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .section-tabs {
    top: 123px;
    padding: 10px 16px;
  }

  .status-right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
