<script setup lang="ts">
import { computed } from 'vue'
import { showToast } from 'vant'
import { formatJsonDisplay, isColorValue, type DictCategoryMeta } from '@/constants/dictCategoryMeta'
import type { SysDict } from '@/types'

const props = defineProps<{
  category: string
  items: SysDict[]
  meta?: DictCategoryMeta
  compact?: boolean
}>()

type PreviewItem = Pick<SysDict, 'category' | 'dictKey' | 'dictValue' | 'extraValue' | 'status'>

const previewItems = computed<PreviewItem[]>(() => {
  const activeItems = props.items.filter(item => item.status !== 'INACTIVE')
  if (activeItems.length > 0) return activeItems.slice(0, 6)
  return (props.meta?.examples || []).slice(0, 6).map(item => ({
    category: props.category,
    dictKey: item.key,
    dictValue: item.value,
    extraValue: item.extraValue || '',
    status: 'ACTIVE'
  }))
})

const previewType = computed(() => props.meta?.previewType || 'text')

const copyJson = async (value: string) => {
  try {
    await navigator.clipboard.writeText(value)
    showToast('已复制 JSON')
  } catch {
    showToast('复制失败')
  }
}
</script>

<template>
  <section class="preview-panel" :class="{ compact }">
    <div class="preview-head">
      <div>
        <p class="eyebrow">效果展示</p>
        <h3>{{ meta?.label || category }}</h3>
      </div>
      <span>{{ previewItems.length }} 条示例</span>
    </div>

    <div v-if="previewItems.length === 0" class="empty-preview">
      暂无可展示项
    </div>

    <div v-else class="preview-body">
      <div v-if="previewType === 'select'" class="select-preview">
        <label>下拉选项</label>
        <select>
          <option v-for="item in previewItems" :key="item.dictKey" :value="item.dictKey">
            {{ item.dictValue }}
          </option>
        </select>
      </div>

      <div v-else-if="previewType === 'color'" class="chip-list">
        <span
          v-for="item in previewItems"
          :key="item.dictKey"
          class="color-chip"
          :style="isColorValue(item.extraValue || '') ? { borderColor: item.extraValue, color: item.extraValue } : undefined"
        >
          <i :style="{ backgroundColor: isColorValue(item.extraValue || '') ? item.extraValue : '#d1d5db' }"></i>
          {{ item.dictValue }}
        </span>
      </div>

      <div v-else-if="previewType === 'icon'" class="chip-list">
        <span v-for="item in previewItems" :key="item.dictKey" class="icon-chip">
          <b>{{ item.extraValue || item.dictKey.slice(0, 1) }}</b>
          {{ item.dictValue }}
        </span>
      </div>

      <div v-else-if="previewType === 'json'" class="json-list">
        <div v-for="item in previewItems" :key="item.dictKey" class="json-card">
          <div>
            <strong>{{ item.dictValue }}</strong>
            <code>{{ item.dictKey }}</code>
          </div>
          <pre>{{ formatJsonDisplay(item.extraValue || '{}') }}</pre>
          <button @click="copyJson(item.extraValue || '{}')">复制</button>
        </div>
      </div>

      <div v-else-if="previewType === 'readonly'" class="readonly-preview">
        <span v-for="item in previewItems" :key="item.dictKey">{{ item.dictValue }}</span>
      </div>

      <div v-else class="chip-list">
        <span v-for="item in previewItems" :key="item.dictKey" class="text-chip">
          {{ item.dictValue }}
        </span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.preview-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
}

.preview-panel.compact {
  padding: 14px;
}

.preview-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #64748b;
  font-size: var(--font-size-xs);
}

h3 {
  margin: 0;
  color: #111827;
  font-size: var(--font-size-base);
  font-weight: 650;
}

.preview-head span {
  color: #64748b;
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.empty-preview {
  padding: 24px;
  border-radius: 6px;
  background: #f8fafc;
  color: #94a3b8;
  text-align: center;
  font-size: var(--font-size-sm);
}

.select-preview {
  display: grid;
  gap: 8px;
}

.select-preview label {
  color: #64748b;
  font-size: var(--font-size-xs);
}

.select-preview select {
  width: 100%;
  min-height: 40px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  padding: 0 10px;
  color: #111827;
  background: #ffffff;
}

.chip-list,
.readonly-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.color-chip,
.icon-chip,
.text-chip,
.readonly-preview span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 30px;
  padding: 5px 10px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  color: #374151;
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.color-chip i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.icon-chip b {
  color: #0d6e6e;
  font-weight: 700;
}

.readonly-preview span {
  background: #f3f4f6;
  color: #6b7280;
}

.json-list {
  display: grid;
  gap: 10px;
}

.json-card {
  display: grid;
  gap: 8px;
  padding: 10px;
  border-radius: 6px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.json-card div {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.json-card strong {
  color: #1f2937;
  font-size: var(--font-size-xs);
}

.json-card code {
  color: #0d6e6e;
  font-family: var(--font-mono);
  font-size: 11px;
}

.json-card pre {
  max-height: 120px;
  overflow: auto;
  margin: 0;
  color: #475569;
  font-family: var(--font-mono);
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.json-card button {
  justify-self: start;
  border: 0;
  border-radius: 4px;
  background: #e0f2fe;
  color: #0369a1;
  padding: 4px 8px;
  cursor: pointer;
  font-size: var(--font-size-xs);
}
</style>
