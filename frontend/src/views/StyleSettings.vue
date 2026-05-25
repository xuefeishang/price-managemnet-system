<script setup lang="ts">
import { ref, provide, onMounted, onUnmounted, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import { useHomePreviewState } from '@/composables/useHomePreviewState'
import { useHomeProductOrderState } from '@/composables/useHomeProductOrderState'
import { useCategoryPreviewState } from '@/composables/useCategoryPreviewState'
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
import HomeSortPanel from '@/components/style-settings/HomeSortPanel.vue'
import CategoryVisualPanel from '@/components/style-settings/CategoryVisualPanel.vue'
import StylePreviewPanel from '@/components/style-settings/StylePreviewPanel.vue'

// 工作台状态
const workbench = useStyleSettingsWorkbench()
const route = useRoute()
const router = useRouter()

// 首页和分类状态
const homeState = useHomePreviewState()
const productOrderState = useHomeProductOrderState()
const categoryState = useCategoryPreviewState()

// 当前激活的配置域
const activeSection = ref('overview')
const validSections = new Set(['overview', 'brand', 'color', 'typography', 'layout', 'home', 'home-sort', 'category', 'version'])

const setActiveSection = (section: string) => {
  if (!validSections.has(section)) return
  activeSection.value = section
}

// 版本管理
const versionList = ref<StyleVersion[]>([])
const loadingVersions = ref(false)
const selectedVersion = ref<StyleVersion | null>(null)

// 是否有未保存更改
const hasUnsavedChanges = () =>
  workbench.saveStatus.value === 'dirty' ||
  homeState.hasUnsavedChanges.value ||
  productOrderState.hasUnsavedChanges.value ||
  categoryState.hasUnsavedChanges.value

// 路由离开保护
onBeforeRouteLeave((_to, _from, next) => {
  if (hasUnsavedChanges()) {
    showConfirmDialog({
      title: '未保存的更改',
      message: '您有未保存的更改，是否放弃修改并离开？'
    }).then(() => {
      // 用户确认放弃
      workbench.discardChanges()
      homeState.loadConfig()
      productOrderState.loadOrder()
      categoryState.discardChanges()
      next()
    }).catch(() => {
      // 用户取消
      next(false)
    })
  } else {
    next()
  }
})

// 浏览器刷新/关闭保护
const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (hasUnsavedChanges()) {
    e.preventDefault()
    e.returnValue = '您有未保存的更改，确定要离开吗？'
  }
}

onMounted(() => {
  const section = route.query.section
  if (typeof section === 'string') {
    setActiveSection(section)
  }
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onUnmounted(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

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

// 选择版本查看
const selectVersion = (version: StyleVersion) => {
  selectedVersion.value = version
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
    selectedVersion.value = null
    await loadVersions()
  } catch (error: any) {
    if (error.message !== 'cancel') {
      console.error('Rollback failed:', error)
      showToast('回滚失败')
    }
  }
}

// 导航到指定配置域
const navigateTo = (section: string) => {
  setActiveSection(section)
}

// 提供给子组件
provide('workbench', workbench)
provide('activeSection', activeSection)

watch(() => route.query.section, (section) => {
  if (typeof section === 'string') {
    setActiveSection(section)
  }
})

watch(activeSection, (section) => {
  if (route.query.section === section) return
  router.replace({
    query: {
      ...route.query,
      section
    }
  })
})

watch(activeSection, (section) => {
  if (section === 'version') {
    loadVersions()
  }
}, { immediate: true })
</script>

<template>
  <StyleSettingsShell
    :active-section="activeSection"
    @update:active-section="setActiveSection"
  >
    <!-- 中央配置区 -->
    <template #config>
      <!-- 总览 -->
      <StyleOverviewPanel
        v-if="activeSection === 'overview'"
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

      <!-- 首页排序 -->
      <HomeSortPanel v-if="activeSection === 'home-sort'" />

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
            :class="{ selected: selectedVersion?.id === version.id }"
            @click="selectVersion(version)"
          >
            <div class="version-header">
              <div class="version-info">
                <span class="version-no">{{ version.versionNo }}</span>
                <span class="version-time">{{ formatTime(version.createdTime) }}</span>
              </div>
              <div class="version-actions">
                <button class="version-view-btn" @click.stop="selectVersion(version)">
                  查看
                </button>
                <button class="version-rollback-btn" @click.stop="rollback(version.id, version.versionNo)">
                  回滚
                </button>
              </div>
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
      <StylePreviewPanel
        v-else
        :editing-config="workbench.draftConfig.value!"
        :active-section="activeSection"
        :target-version="selectedVersion"
      />
    </template>
  </StyleSettingsShell>
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
  cursor: pointer;
  transition: all 150ms;
}

.version-item:hover {
  border-color: #0D6E6E;
}

.version-item.selected {
  border-color: #F59E0B;
  background: rgba(245, 158, 11, 0.05);
}

.version-actions {
  display: flex;
  gap: 8px;
}

.version-view-btn {
  padding: 6px 12px;
  background: white;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: var(--font-size-xs);
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
  flex-shrink: 0;
}

.version-view-btn:hover {
  border-color: #F59E0B;
  color: #F59E0B;
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
