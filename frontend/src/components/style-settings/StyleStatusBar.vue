<script setup lang="ts">
import { computed } from 'vue'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'

const emit = defineEmits<{
  (e: 'reset-default'): void
  (e: 'open-version'): void
  (e: 'export-config'): void
}>()

const workbench = useStyleSettingsWorkbench()

// 保存状态显示
const statusClass = computed(() => workbench.saveStatus.value)
const statusText = computed(() => {
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

// 当前方案摘要
const schemeSummary = computed(() => {
  if (!workbench.isLoaded.value) return '加载中...'
  const color = workbench.activeColorSchemeKey.value || '默认'
  const font = workbench.activeFontPresetKey.value || '标准'
  return `${color} / ${font}`
})

// 格式化时间
const formatTime = (iso: string) => {
  const date = new Date(iso)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="style-status-bar">
    <div class="status-info">
      <h1 class="page-title">全局样式设置</h1>
      <div class="scheme-summary">{{ schemeSummary }}</div>
    </div>

    <div class="status-actions">
      <span class="save-status" :class="statusClass">
        <span class="status-icon" v-if="statusClass === 'saving'">⏳</span>
        <span class="status-icon" v-if="statusClass === 'saved'">✓</span>
        <span class="status-icon" v-if="statusClass === 'failed'">⚠</span>
        {{ statusText }}
      </span>

      <div class="action-buttons">
        <button class="btn-action" @click="emit('reset-default')">
          恢复默认
        </button>
        <button class="btn-action" @click="emit('export-config')">
          导出配置
        </button>
        <button class="btn-action primary" @click="emit('open-version')">
          历史版本
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.style-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
}

.status-info {
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

.scheme-summary {
  font-size: var(--font-size-sm);
  color: #666666;
  padding: 4px 12px;
  background: #F5F5F5;
  border-radius: 4px;
}

.status-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.save-status {
  display: flex;
  align-items: center;
  gap: 4px;
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

.status-icon {
  font-size: 14px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.btn-action {
  padding: 8px 16px;
  font-size: var(--font-size-sm);
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  background: #FFFFFF;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.btn-action:hover {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.btn-action.primary {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

.btn-action.primary:hover {
  background: #0A5555;
}

@media (max-width: 768px) {
  .style-status-bar {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .status-actions {
    width: 100%;
    justify-content: space-between;
  }
}
</style>