<script setup lang="ts">
/**
 * 样式预览面板
 * 根据 activeSection 动态渲染对应的预览组件
 */

import { computed, ref, watch } from 'vue'
import type { StyleConfig, StyleVersion } from '@/types/theme'
import { getPreviewBlocksBySection, type StylePreviewBlock } from '@/constants/stylePreviewBlocks'

// 预览组件
import SystemOverviewPreview from './preview/SystemOverviewPreview.vue'
import BrandPreview from './preview/BrandPreview.vue'
import ColorPreview from './preview/ColorPreview.vue'
import TypographyPreview from './preview/TypographyPreview.vue'
import LayoutPreview from './preview/LayoutPreview.vue'
import HomePreview from './preview/HomePreview.vue'
import CategoryPreview from './preview/CategoryPreview.vue'
import VersionComparePreview from './preview/VersionComparePreview.vue'

const props = defineProps<{
  editingConfig: StyleConfig
  activeSection?: string
  targetVersion?: StyleVersion | null
}>()

// 当前激活的预览区块
const previewBlocks = computed(() => {
  return getPreviewBlocksBySection(props.activeSection || 'overview')
})

// 切换过渡状态
const isTransitioning = ref(false)
let transitionTimer: ReturnType<typeof setTimeout> | null = null

watch(() => props.activeSection, () => {
  isTransitioning.value = true
  if (transitionTimer) clearTimeout(transitionTimer)
  transitionTimer = setTimeout(() => {
    isTransitioning.value = false
  }, 150)
})

// 区块渲染判断
const showBlock = (block: StylePreviewBlock): boolean => {
  return previewBlocks.value.includes(block)
}
</script>

<template>
  <div class="style-preview-panel" :class="{ 'is-transitioning': isTransitioning }">
    <div class="panel-header">
      <h3 class="panel-title">实时预览</h3>
    </div>

    <div class="preview-content">
      <!-- 系统总览 -->
      <SystemOverviewPreview
        v-if="showBlock('systemOverview')"
        :editing-config="editingConfig"
      />

      <!-- 品牌预览 -->
      <BrandPreview
        v-if="showBlock('brandNav')"
        :editing-config="editingConfig"
      />

      <!-- 色彩预览 -->
      <ColorPreview
        v-if="showBlock('priceColorScenario')"
        :editing-config="editingConfig"
      />

      <!-- 排版预览 -->
      <TypographyPreview
        v-if="showBlock('fontHierarchy')"
        :editing-config="editingConfig"
      />

      <!-- 布局预览 -->
      <LayoutPreview
        v-if="showBlock('layoutMiniature')"
        :editing-config="editingConfig"
      />

      <!-- 首页体验预览 -->
      <HomePreview
        v-if="showBlock('homeViewport')"
        :editing-config="editingConfig"
      />

      <!-- 分类视觉预览 -->
      <CategoryPreview
        v-if="showBlock('categoryCard')"
        :editing-config="editingConfig"
      />

      <!-- 版本对比预览 -->
      <VersionComparePreview
        v-if="showBlock('versionCompare')"
        :editing-config="editingConfig"
        :target-version="targetVersion ?? null"
      />
    </div>
  </div>
</template>

<style scoped>
.style-preview-panel {
  padding: 0;
  background: transparent;
  border: none;
  border-radius: 0;
  transition: opacity 150ms ease;
}

.style-preview-panel.is-transitioning {
  opacity: 0.7;
}

.panel-header {
  margin-bottom: 10px;
}

.panel-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-secondary, #666666);
  margin: 0;
}

.preview-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
</style>
