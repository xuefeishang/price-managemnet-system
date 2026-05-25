<script setup lang="ts">
import { computed } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import type { StyleConfig } from '@/types/theme'
import { resolveLayoutTokensFromConfig } from '@/utils/layoutTokenResolver'

const props = defineProps<{
  editingConfig: StyleConfig
}>()

const layoutStyle = computed(() => props.editingConfig.activeLayoutStyle || 'layout_top_nav')

const layoutTokens = computed(() => resolveLayoutTokensFromConfig(props.editingConfig))
</script>

<template>
  <!-- PC 视口缩略图 -->
  <PreviewFrame title="PC 视口缩略图" hint="导航位置 + 页面背景">
    <div class="layout-miniature" :class="layoutStyle">
      <div class="mini-nav-bar" :style="{ background: layoutTokens.navBg }">
        <span class="mini-nav-dot"></span>
      </div>
      <div class="mini-content" :style="{ background: layoutTokens.pageBg }">
        <div class="mini-card-block"></div>
        <div class="mini-card-block"></div>
      </div>
    </div>
    <div class="layout-info">
      <span class="layout-info-item">导航位置: {{ layoutTokens.navPosition }}</span>
    </div>
  </PreviewFrame>

  <!-- 卡片样式 -->
  <PreviewFrame title="卡片样式" hint="圆角 + 阴影">
    <div class="card-surface-preview">
      <div class="card-sample">
        <span class="card-sample-label">当前圆角</span>
        <div class="card-sample-box" :style="{ borderRadius: layoutTokens.cardRadius }">
          {{ layoutTokens.cardRadius }}
        </div>
      </div>
      <div class="card-sample">
        <span class="card-sample-label">当前阴影</span>
        <div class="card-sample-box shadow" :style="{ boxShadow: layoutTokens.cardShadow }">
          {{ layoutTokens.cardShadowLabel }}
        </div>
      </div>
    </div>
  </PreviewFrame>
</template>

<style scoped>
.layout-miniature {
  display: flex;
  height: 100px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--border-light, #E5E5E5);
  background: var(--bg-card, #FFFFFF);
}

.layout-miniature.layout_left_nav,
.layout-miniature.layout_dashboard {
  flex-direction: row;
}

.layout-miniature.layout_top_nav,
.layout-miniature.layout_minimal {
  flex-direction: column;
}

.mini-nav-bar {
  width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.layout-miniature.layout_top_nav .mini-nav-bar,
.layout-miniature.layout_minimal .mini-nav-bar {
  width: 100%;
  height: 20px;
}

.mini-nav-dot {
  width: 8px;
  height: 8px;
  background: #0D6E6E;
  border-radius: 2px;
}

.mini-content {
  flex: 1;
  display: flex;
  gap: 8px;
  padding: 8px;
}

.mini-card-block {
  flex: 1;
  background: var(--bg-card, #FFFFFF);
  border-radius: 4px;
  border: 1px solid var(--border-light, #E5E5E5);
}

.layout-info {
  margin-top: 8px;
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
}

.card-surface-preview {
  display: flex;
  gap: 12px;
}

.card-sample {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
}

.card-sample-label {
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
}

.card-sample-box {
  width: 80px;
  height: 60px;
  background: var(--bg-card, #FFFFFF);
  border: 1px solid var(--border-light, #E5E5E5);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: var(--text-secondary, #888);
}
</style>