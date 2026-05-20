<script setup lang="ts">
import { ref, provide } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import { getVersionList, rollbackToVersion } from '@/api/style'
import type { StyleVersion } from '@/types/theme'

// 组件导入
import StyleSettingsShell from '@/components/style-settings/StyleSettingsShell.vue'
import StyleOverviewPanel from '@/components/style-settings/StyleOverviewPanel.vue'
import BrandSettingsPanel from '@/components/style-settings/BrandSettingsPanel.vue'
import ColorSchemePanel from '@/components/style-settings/ColorSchemePanel.vue'
import TypographyPanel from '@/components/style-settings/TypographyPanel.vue'
import LayoutStylePanel from '@/components/style-settings/LayoutStylePanel.vue'
import HomeExperiencePanel from '@/components/style-settings/HomeExperiencePanel.vue'
import CategoryVisualPanel from '@/components/style-settings/CategoryVisualPanel.vue'
import StylePreviewPanel from '@/components/style-settings/StylePreviewPanel.vue'

// 工作台状态
const workbench = useStyleSettingsWorkbench()

// 当前激活的配置域
const activeSection = ref('overview')

// 版本管理
const showVersionPanel = ref(false)
const versionList = ref<StyleVersion[]>([])
const loadingVersions = ref(false)

// 加载版本列表
const loadVersions = async () => {
  loadingVersions.value = true
  try {
    const res = await getVersionList(0, 20)
    versionList.value = res.data?.content || []
  } catch (error) {
    console.error('Failed to load versions:', error)
    showToast('加载版本列表失败')
  } finally {
    loadingVersions.value = false
  }
}

// 打开版本面板
const openVersionPanel = async () => {
  showVersionPanel.value = true
  await loadVersions()
}

// 关闭版本面板
const closeVersionPanel = () => {
  showVersionPanel.value = false
  versionList.value = []
}

// 格式化时间（带时分秒）
const formatTime = (time: string) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 回滚版本
const rollback = async (versionId: number, versionNo: string) => {
  try {
    await showConfirmDialog({
      title: '确认回滚',
      message: `确定要回滚到版本 ${versionNo} 吗？当前配置将被替换。`
    })

    await rollbackToVersion(versionId)
    await workbench.loadWorkbenchConfig(true)
    showToast({ message: '回滚成功', position: 'top', duration: 2000 })
    closeVersionPanel()
  } catch (error: any) {
    if (error.message !== 'cancel') {
      console.error('Rollback failed:', error)
      showToast('回滚失败')
    }
  }
}

// 恢复默认配置
const resetToDefault = async () => {
  try {
    await showConfirmDialog({
      title: '确认重置',
      message: '确定要重置为默认配置吗？'
    })
    await workbench.resetToDefault()
    showToast('已重置为默认配置')
  } catch {
    // 用户取消
  }
}

// 导出配置
const exportConfig = () => {
  if (!workbench.draftConfig.value) return

  const config = workbench.draftConfig.value
  const json = JSON.stringify(config, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `style-config-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
  showToast('配置已导出')
}

// 导航到指定配置域
const navigateTo = (section: string) => {
  activeSection.value = section
}

// 提供给子组件
provide('workbench', workbench)
provide('activeSection', activeSection)
</script>

<template>
  <StyleSettingsShell
    :active-section="activeSection"
    @update:active-section="activeSection = $event"
    @open-version="openVersionPanel"
  >
    <!-- 中央配置区 -->
    <template #config>
      <!-- 总览 -->
      <StyleOverviewPanel
        v-if="activeSection === 'overview'"
        @reset-default="resetToDefault"
        @open-version="openVersionPanel"
        @export-config="exportConfig"
        @navigate="navigateTo"
      />

      <!-- 品牌 -->
      <BrandSettingsPanel v-if="activeSection === 'brand'" />

      <!-- 色彩 -->
      <ColorSchemePanel v-if="activeSection === 'color'" />

      <!-- 排版 -->
      <TypographyPanel v-if="activeSection === 'typography'" />

      <!-- 布局 -->
      <LayoutStylePanel v-if="activeSection === 'layout'" />

      <!-- 首页体验 -->
      <HomeExperiencePanel v-if="activeSection === 'home'" />

      <!-- 分类视觉 -->
      <CategoryVisualPanel v-if="activeSection === 'category'" />

      <!-- 版本恢复 -->
      <div v-if="activeSection === 'version'" class="version-section">
        <div class="version-header">
          <h2 class="section-title">历史版本</h2>
          <button class="refresh-btn" @click="loadVersions">刷新</button>
        </div>

        <div v-if="loadingVersions" class="version-loading">加载中...</div>
        <div v-else-if="versionList.length === 0" class="version-empty">
          <p>暂无历史版本</p>
          <p class="version-hint">每次保存配置时会自动生成版本快照</p>
        </div>
        <div v-else class="version-list">
          <div
            v-for="version in versionList"
            :key="version.id"
            class="version-item"
          >
            <div class="version-header">
              <div class="version-info">
                <span class="version-no">{{ version.versionNo }}</span>
                <span class="version-time">{{ formatTime(version.createdTime) }}</span>
              </div>
              <button class="version-rollback-btn" @click="rollback(version.id, version.versionNo)">
                回滚
              </button>
            </div>
            <div class="version-summary">{{ version.changeSummary || '样式配置更新' }}</div>
            <div class="version-meta">
              <span class="version-user">{{ version.changedByName || '系统' }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 右侧预览区 -->
    <template #preview>
      <div v-if="!workbench.isLoaded.value" class="preview-loading">
        <span class="loading-text">加载中...</span>
      </div>
      <StylePreviewPanel v-else :editing-config="workbench.draftConfig.value!" />
    </template>
  </StyleSettingsShell>

  <!-- 版本历史弹窗（备用） -->
  <div v-if="showVersionPanel" class="version-panel-overlay" @click.self="closeVersionPanel">
    <div class="version-panel">
      <div class="version-panel-header">
        <h3 class="version-panel-title">历史版本</h3>
        <button class="version-panel-close" @click="closeVersionPanel">×</button>
      </div>
      <div class="version-panel-content">
        <div v-if="loadingVersions" class="version-loading">加载中...</div>
        <div v-else-if="versionList.length === 0" class="version-empty">暂无历史版本</div>
        <div v-else class="version-list">
          <div
            v-for="version in versionList"
            :key="version.id"
            class="version-item"
          >
            <div class="version-header">
              <div class="version-info">
                <span class="version-no">{{ version.versionNo }}</span>
                <span class="version-time">{{ formatTime(version.createdTime) }}</span>
              </div>
              <button class="version-rollback-btn" @click="rollback(version.id, version.versionNo)">
                回滚
              </button>
            </div>
            <div class="version-summary">{{ version.changeSummary || '样式配置更新' }}</div>
            <div class="version-meta">
              <span class="version-user">{{ version.changedByName || '系统' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 版本区域 */
.version-section {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 24px;
}

.version-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
}

.section-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0;
}

.refresh-btn {
  padding: 6px 16px;
  font-size: var(--font-size-xs);
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  background: #FFFFFF;
  color: #666666;
  cursor: pointer;
}

.refresh-btn:hover {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.version-loading,
.version-empty {
  text-align: center;
  padding: 40px;
  color: #888888;
}

.version-hint {
  font-size: var(--font-size-xs);
  color: #AAAAAA;
  margin-top: 8px;
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.version-item {
  padding: 16px;
  background: #FAFAFA;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
}

.version-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 8px;
  gap: 12px;
}

.version-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.version-no {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #0D6E6E;
}

.version-time {
  font-size: var(--font-size-xs);
  color: #888888;
}

.version-summary {
  font-size: var(--font-size-sm);
  color: #333333;
  margin-bottom: 8px;
}

.version-meta {
  font-size: var(--font-size-xs);
  color: #888888;
}

.version-rollback-btn {
  padding: 6px 16px;
  background: white;
  border: 1px solid #0D6E6E;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  color: #0D6E6E;
  cursor: pointer;
  transition: all 150ms;
  flex-shrink: 0;
}

.version-rollback-btn:hover {
  background: #0D6E6E;
  color: white;
}

/* 版本面板弹窗 */
.version-panel-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.version-panel {
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  background: white;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.version-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid #E5E5E5;
}

.version-panel-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0;
}

.version-panel-close {
  width: 32px;
  height: 32px;
  border: none;
  background: #F5F5F5;
  border-radius: 8px;
  font-size: 20px;
  color: #666666;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.version-panel-close:hover {
  background: #E5E5E5;
}

.version-panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

/* 预览加载状态 */
.preview-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
}

.loading-text {
  font-size: var(--font-size-sm);
  color: #888888;
}
</style>