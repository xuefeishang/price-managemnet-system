<script setup lang="ts">
import { ref, computed, onMounted, provide, onErrorCaptured } from 'vue'
import { showToast } from 'vant'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'

// Props
defineProps<{
  activeSection?: string
}>()

// Emits
const emit = defineEmits<{
  (e: 'update:activeSection', value: string): void
  (e: 'open-version'): void
}>()

// 工作台状态
const workbench = useStyleSettingsWorkbench()

// 一级导航配置
const sections = [
  { key: 'overview', label: '总览', icon: 'dashboard' },
  { key: 'brand', label: '品牌', icon: 'brand' },
  { key: 'color', label: '色彩', icon: 'color' },
  { key: 'typography', label: '排版', icon: 'font' },
  { key: 'layout', label: '布局', icon: 'layout' },
  { key: 'home', label: '首页体验', icon: 'home' },
  { key: 'category', label: '分类视觉', icon: 'category' },
  { key: 'version', label: '版本恢复', icon: 'history' }
]

// 当前激活的导航
const currentSection = ref('overview')

// 移动端预览抽屉
const showPreviewDrawer = ref(false)

// 响应式检测
const isMobile = ref(false)
const checkMobile = () => {
  isMobile.value = window.innerWidth < 1024
}

// 保存状态显示
const saveStatusText = computed(() => {
  switch (workbench.saveStatus.value) {
    case 'saving': return '正在保存...'
    case 'saved': return workbench.lastSavedAt.value
      ? `已保存 ${formatTime(workbench.lastSavedAt.value)}`
      : '已保存'
    case 'failed': return '保存失败，已恢复'
    case 'dirty': return '正在应用...'
    default: return '当前配置'
  }
})

// 格式化时间
const formatTime = (iso: string) => {
  const date = new Date(iso)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 切换导航
const switchSection = (key: string) => {
  currentSection.value = key
  emit('update:activeSection', key)
}

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
})
</script>

<template>
  <div class="style-settings-shell">
    <!-- 顶部状态栏 -->
    <header class="status-bar">
      <div class="status-left">
        <h1 class="page-title">全局样式设置</h1>
        <div class="current-scheme" v-if="workbench.isLoaded.value">
          <span class="scheme-tag">{{ workbench.activeColorSchemeKey.value || '默认' }}</span>
          <span class="scheme-divider">/</span>
          <span class="scheme-tag">{{ workbench.activeFontPresetKey.value || '标准' }}</span>
        </div>
      </div>
      <div class="status-right">
        <span class="save-status" :class="workbench.saveStatus.value">
          {{ saveStatusText }}
        </span>
        <div class="quick-actions">
          <button class="action-btn" @click="workbench.resetToDefault()">恢复默认</button>
          <button class="action-btn primary" @click="$emit('open-version')">历史版本</button>
        </div>
      </div>
    </header>

    <!-- PC 端三栏布局 -->
    <div class="workbench-layout" v-if="!isMobile">
      <!-- 左侧导航 -->
      <nav class="section-nav">
        <div
          v-for="section in sections"
          :key="section.key"
          class="nav-item"
          :class="{ active: currentSection === section.key }"
          @click="switchSection(section.key)"
        >
          <span class="nav-label">{{ section.label }}</span>
        </div>
      </nav>

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
      <!-- 横向导航 -->
      <nav class="mobile-nav">
        <div class="nav-scroll">
          <div
            v-for="section in sections"
            :key="section.key"
            class="mobile-nav-item"
            :class="{ active: currentSection === section.key }"
            @click="switchSection(section.key)"
          >
            {{ section.label }}
          </div>
        </div>
      </nav>

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
.style-settings-shell {
  min-height: 100vh;
  background-color: #FAFAFA;
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
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
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
  background: #F5F5F5;
  border-radius: 4px;
}

.scheme-divider {
  color: #E5E5E5;
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

.save-status.saving {
  color: #0D6E6E;
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
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  background: #FFFFFF;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.action-btn:hover {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.action-btn.primary {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

.action-btn.primary:hover {
  background: #0A5555;
}

/* PC 端三栏布局 */
.workbench-layout {
  display: grid;
  grid-template-columns: 200px 1fr 360px;
  gap: 24px;
  padding: 24px;
  max-width: 1600px;
  margin: 0 auto;
}

/* 左侧导航 */
.section-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  height: fit-content;
  position: sticky;
  top: 96px;
}

.nav-item {
  padding: 12px 16px;
  font-size: var(--font-size-sm);
  color: #666666;
  border-radius: 8px;
  cursor: pointer;
  transition: all 150ms;
}

.nav-item:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.nav-item.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
  font-weight: 500;
}

/* 中央配置区 */
.config-panel {
  min-width: 0;
}

/* 右侧预览区 */
.preview-panel {
  position: sticky;
  top: 96px;
  height: fit-content;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
}

/* 移动端布局 */
.mobile-layout {
  padding: 16px;
}

.mobile-nav {
  margin-bottom: 16px;
}

.nav-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 8px;
  -webkit-overflow-scrolling: touch;
}

.nav-scroll::-webkit-scrollbar {
  display: none;
}

.mobile-nav-item {
  flex-shrink: 0;
  padding: 8px 16px;
  font-size: var(--font-size-sm);
  color: #666666;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 20px;
  cursor: pointer;
  transition: all 150ms;
}

.mobile-nav-item.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

.mobile-config {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 16px;
}

.preview-toggle {
  position: fixed;
  bottom: 80px;
  right: 16px;
  padding: 12px 24px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 24px;
  font-size: var(--font-size-sm);
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(13, 110, 110, 0.3);
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
  max-height: 80vh;
  background: #FFFFFF;
  border-radius: 16px 16px 0 0;
  overflow: hidden;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #E5E5E5;
}

.drawer-header h3 {
  margin: 0;
  font-size: var(--font-size-base);
}

.drawer-close {
  width: 32px;
  height: 32px;
  border: none;
  background: #F5F5F5;
  border-radius: 8px;
  font-size: 20px;
  cursor: pointer;
}

.drawer-content {
  padding: 16px;
  overflow-y: auto;
  max-height: calc(80vh - 60px);
}

/* 响应式 */
@media (max-width: 1280px) {
  .workbench-layout {
    grid-template-columns: 180px 1fr 320px;
  }
}

@media (max-width: 1024px) {
  .status-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .status-right {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
