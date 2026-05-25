<script setup lang="ts">
import { computed } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import type { StyleConfig, StyleVersion } from '@/types/theme'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import { buildStyleConfigDiff, parseConfigSnapshot, type DiffItem } from '@/utils/styleConfigDiff'
import { buildStylePresetNameMap } from '@/utils/stylePresetNames'

const props = defineProps<{
  editingConfig: StyleConfig
  targetVersion: StyleVersion | null
}>()

const workbench = useStyleSettingsWorkbench()

// 当前版本号（从配置或时间生成）
const currentVersionNo = computed(() => {
  const now = new Date()
  const date = now.toISOString().slice(0, 10).replace(/-/g, '')
  return `v${date}-001`
})

// 目标版本号
const targetVersionNo = computed(() => {
  return props.targetVersion?.versionNo || '选择历史版本'
})

// 是否有目标版本
const hasTarget = computed(() => !!props.targetVersion)

// 解析目标版本配置
const targetConfig = computed<StyleConfig | null>(() => {
  if (!props.targetVersion?.configSnapshot) return null
  return parseConfigSnapshot(props.targetVersion.configSnapshot)
})

// 差异列表
const diffItems = computed<DiffItem[]>(() => {
  if (!targetConfig.value) return []
  const presetNames = buildStylePresetNameMap(
    workbench.colorSchemes.value,
    workbench.layoutStyles.value,
    workbench.fontPresets.value
  )
  return buildStyleConfigDiff(props.editingConfig, targetConfig.value, { presetNames })
})

// 是否有差异
const hasDiff = computed(() => diffItems.value.length > 0)

// Logo 变更提示
const logoChangeHint = computed(() => {
  if (!props.targetVersion?.configSnapshot) return null
  try {
    const snapshot = JSON.parse(props.targetVersion.configSnapshot)
    const assetRefs = snapshot.assetRefs
    if (assetRefs) {
      const logoChanges: string[] = []
      if (assetRefs.logoUrl?.hasValue) logoChanges.push('默认 Logo')
      if (assetRefs.logoUrlLogin?.hasValue) logoChanges.push('登录页 Logo')
      if (assetRefs.logoUrlNav?.hasValue) logoChanges.push('导航栏 Logo')
      if (logoChanges.length > 0) {
        return `注意：${logoChanges.join('、')} 在该版本有变更，但 Logo 不随版本回滚`
      }
    }
  } catch {
    // ignore
  }
  return null
})
</script>

<template>
  <PreviewFrame title="版本对比" hint="当前版本 vs 目标版本">
    <div class="version-compare">
      <div class="version-box current">
        <span class="version-label">当前版本</span>
        <span class="version-no">{{ currentVersionNo }}</span>
      </div>
      <div class="version-arrow">→</div>
      <div class="version-box target" :class="{ selected: hasTarget }">
        <span class="version-label">目标版本</span>
        <span class="version-no">{{ targetVersionNo }}</span>
      </div>
    </div>

    <!-- 字段级差异 -->
    <div v-if="hasTarget && hasDiff" class="diff-list">
      <div class="diff-header">
        <span class="diff-title">变更内容 ({{ diffItems.length }} 项)</span>
      </div>
      <div class="diff-items">
        <div v-for="diff in diffItems" :key="diff.field" class="diff-row">
          <span class="diff-label">{{ diff.label }}</span>
          <div class="diff-values">
            <div class="diff-value current">
              <span
                v-if="diff.type === 'color'"
                class="color-preview"
                :style="{ background: diff.currentValue }"
              ></span>
              <span class="value-text">{{ diff.currentDisplay }}</span>
            </div>
            <span class="diff-arrow">→</span>
            <div class="diff-value target">
              <span
                v-if="diff.type === 'color'"
                class="color-preview"
                :style="{ background: diff.targetValue }"
              ></span>
              <span class="value-text">{{ diff.targetDisplay }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 无差异 -->
    <div v-else-if="hasTarget && !hasDiff" class="diff-summary">
      <div class="no-diff">配置相同，无变更</div>
    </div>

    <!-- Logo 变更提示 -->
    <div v-if="logoChangeHint" class="logo-hint">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>
      <span>{{ logoChangeHint }}</span>
    </div>

    <!-- 未选择版本 -->
    <div v-if="!hasTarget" class="version-hint">
      <span>在左侧版本列表中选择历史版本，查看差异和回滚后效果</span>
    </div>
  </PreviewFrame>
</template>

<style scoped>
.version-compare {
  display: flex;
  align-items: center;
  gap: 12px;
}

.version-box {
  flex: 1;
  padding: 12px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.version-box.current {
  background: rgba(13, 110, 110, 0.1);
  border: 1px solid #0D6E6E;
}

.version-box.target {
  background: var(--bg-card, #FFFFFF);
  border: 1px solid var(--border-light, #E5E5E5);
}

.version-box.target.selected {
  border-color: #F59E0B;
  background: rgba(245, 158, 11, 0.1);
}

.version-label {
  font-size: 10px;
  color: var(--text-secondary, #888);
}

.version-no {
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.version-arrow {
  font-size: var(--font-size-lg);
  color: var(--text-secondary, #888);
}

/* 差异列表 */
.diff-list {
  margin-top: 16px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  overflow: hidden;
}

.diff-header {
  padding: 8px 12px;
  background: #F5F5F5;
  border-bottom: 1px solid #E5E5E5;
}

.diff-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: #1A1A1A;
}

.diff-items {
  max-height: 200px;
  overflow-y: auto;
}

.diff-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #F5F5F5;
}

.diff-row:last-child {
  border-bottom: none;
}

.diff-label {
  font-size: var(--font-size-xs);
  color: #666666;
  min-width: 80px;
}

.diff-values {
  display: flex;
  align-items: center;
  gap: 8px;
}

.diff-value {
  display: flex;
  align-items: center;
  gap: 4px;
}

.diff-value.current {
  color: #0D6E6E;
}

.diff-value.target {
  color: #F59E0B;
}

.color-preview {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1px solid rgba(0, 0, 0, 0.1);
}

.value-text {
  font-size: var(--font-size-xs);
  font-family: var(--font-mono);
}

.diff-arrow {
  font-size: 10px;
  color: #888888;
}

.no-diff {
  font-size: var(--font-size-xs);
  color: #888888;
  text-align: center;
  padding: 12px;
}

/* Logo 提示 */
.logo-hint {
  margin-top: 12px;
  padding: 8px 12px;
  background: #FEF3C7;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-size-xs);
  color: #92400E;
}

.diff-summary {
  margin-top: 12px;
  padding: 12px;
  background: var(--bg-secondary, #FAFAFA);
  border-radius: 6px;
}

.diff-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.version-hint {
  margin-top: 12px;
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
  text-align: center;
  padding: 12px;
  background: var(--bg-secondary, #FAFAFA);
  border-radius: 6px;
}
</style>
