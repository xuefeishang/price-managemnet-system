<script setup lang="ts">
/**
 * 预览框架组件
 * 统一预览区块的标题、边框、空状态
 */

defineProps<{
  title: string
  hint?: string
}>()
</script>

<template>
  <div class="preview-frame">
    <div class="frame-header">
      <span class="frame-title">{{ title }}</span>
      <span v-if="hint" class="frame-hint">{{ hint }}</span>
    </div>
    <div class="frame-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.preview-frame {
  background: var(--bg-card, #FFFFFF);
  border: 1px solid var(--border-light, #E5E5E5);
  border-radius: 8px;
  overflow: hidden;
}

.frame-header {
  padding: 10px 12px;
  background: var(--bg-secondary, #FAFAFA);
  border-bottom: 1px solid var(--border-light, #E5E5E5);
}

.frame-title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.frame-hint {
  display: block;
  font-size: 10px;
  color: var(--text-secondary, #888);
  margin-top: 2px;
}

.frame-content {
  padding: 12px;
}
</style>
