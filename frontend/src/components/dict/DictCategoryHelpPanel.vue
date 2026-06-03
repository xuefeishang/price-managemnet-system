<script setup lang="ts">
import { computed } from 'vue'
import { getDomainLabel, type DictCategoryMeta } from '@/constants/dictCategoryMeta'

const props = defineProps<{
  category: string
  meta?: DictCategoryMeta
  itemCount: number
  compact?: boolean
}>()

const domainLabel = computed(() => props.meta ? getDomainLabel(props.meta.domain) : '未登记分类')
const editableLabel = computed(() => props.meta?.editableInDictPage ? '可在本页维护' : '只读查看')
const ownerLabel = computed(() => {
  if (!props.meta?.ownerPage) return '字典管理'
  const map: Record<string, string> = {
    'dict-management': '字典管理',
    'style-settings': '样式设置',
    'category-visual-settings': '分类视觉设置'
  }
  return map[props.meta.ownerPage] || props.meta.ownerPage
})
</script>

<template>
  <section class="help-panel" :class="{ compact }">
    <div class="help-head">
      <div>
        <p class="eyebrow">{{ domainLabel }}</p>
        <h2>{{ meta?.label || category }}</h2>
      </div>
      <span class="count-pill">{{ itemCount }} 项</span>
    </div>

    <p class="category-code">{{ category }}</p>
    <p class="help-title">{{ meta?.helpTitle || '该分类尚未登记元数据，可先维护字典项，再补充分类说明。' }}</p>
    <p class="help-copy">{{ meta?.usage || '当前仅提供基础 key/value/extraValue 管理，请补充使用范围和字段规则后再用于核心业务。' }}</p>

    <div class="rule-grid">
      <div class="rule-item">
        <span>Key</span>
        <p>{{ meta?.keyRule || '保持唯一、稳定，推荐英文或编码。' }}</p>
      </div>
      <div class="rule-item">
        <span>显示值</span>
        <p>{{ meta?.valueRule || '填写用户可理解的展示名称。' }}</p>
      </div>
      <div class="rule-item">
        <span>扩展值</span>
        <p>{{ meta?.extraValueRule || '按业务需要填写；没有就留空。' }}</p>
      </div>
    </div>

    <div class="meta-line">
      <span>{{ editableLabel }}</span>
      <span>维护入口：{{ ownerLabel }}</span>
      <span v-if="meta">扩展模式：{{ meta.extraValueMode }}</span>
    </div>

    <div v-if="meta?.usedIn?.length" class="used-in">
      <span v-for="place in meta.usedIn" :key="place">{{ place }}</span>
    </div>

    <div v-if="meta?.editWarning" class="warning">
      {{ meta.editWarning }}
    </div>
  </section>
</template>

<style scoped>
.help-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
}

.help-panel.compact {
  padding: 14px;
}

.help-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.eyebrow {
  margin: 0 0 4px;
  font-size: var(--font-size-xs);
  color: #64748b;
}

h2 {
  margin: 0;
  color: #111827;
  font-size: var(--font-size-lg);
  font-weight: 650;
}

.count-pill {
  padding: 4px 8px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.category-code {
  display: inline-flex;
  margin: 10px 0 0;
  padding: 3px 8px;
  border-radius: 4px;
  background: #f3f4f6;
  color: #0d6e6e;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.help-title {
  margin: 14px 0 6px;
  color: #1f2937;
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.help-copy {
  margin: 0;
  color: #4b5563;
  font-size: var(--font-size-sm);
  line-height: 1.65;
}

.rule-grid {
  display: grid;
  gap: 8px;
  margin-top: 14px;
}

.rule-item {
  padding: 10px;
  border-radius: 6px;
  background: #f8fafc;
}

.rule-item span {
  display: block;
  margin-bottom: 4px;
  color: #0f766e;
  font-size: var(--font-size-xs);
  font-weight: 650;
}

.rule-item p {
  margin: 0;
  color: #475569;
  font-size: var(--font-size-xs);
  line-height: 1.55;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.meta-line span,
.used-in span {
  padding: 3px 8px;
  border-radius: 4px;
  background: #f3f4f6;
  color: #4b5563;
  font-size: var(--font-size-xs);
}

.used-in {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.warning {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #92400e;
  font-size: var(--font-size-xs);
  line-height: 1.5;
}
</style>
