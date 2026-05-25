<script setup lang="ts">
import { computed } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import type { StyleConfig } from '@/types/theme'

const props = defineProps<{
  editingConfig: StyleConfig
}>()

const fontSamples = computed(() => [
  {
    label: '标题字体',
    family: props.editingConfig.headingFont,
    sample: '价格管理系统',
    css: `'${props.editingConfig.headingFont}', Georgia, serif`
  },
  {
    label: '正文字体',
    family: props.editingConfig.bodyFont,
    sample: '产品价格列表',
    css: `'${props.editingConfig.bodyFont}', sans-serif`
  },
  {
    label: '数字字体',
    family: props.editingConfig.numberFont,
    sample: '¥68,500.00',
    css: `'${props.editingConfig.numberFont}', monospace`
  }
])

const fontSizeLevels = computed(() => [
  { key: 'xs', label: '辅助信息', value: props.editingConfig.fontSizeXs },
  { key: 'sm', label: '表格内容', value: props.editingConfig.fontSizeSm },
  { key: 'base', label: '正文表头', value: props.editingConfig.fontSizeBase },
  { key: 'lg', label: '小节标题', value: props.editingConfig.fontSizeLg },
  { key: 'xl', label: '页面副标题', value: props.editingConfig.fontSizeXl },
  { key: '2xl', label: '页面主标题', value: props.editingConfig.fontSize2xl },
  { key: '3xl', label: '特大标题', value: props.editingConfig.fontSize3xl }
])
</script>

<template>
  <!-- 字体预览 -->
  <PreviewFrame title="字体预览" hint="标题/正文/数字字体">
    <div class="font-samples">
      <div
        v-for="font in fontSamples"
        :key="font.label"
        class="font-sample"
      >
        <span class="font-label">{{ font.label }}</span>
        <span class="font-family">{{ font.family }}</span>
        <span class="font-text" :style="{ fontFamily: font.css }">{{ font.sample }}</span>
      </div>
    </div>
  </PreviewFrame>

  <!-- 字号比例 -->
  <PreviewFrame title="字号比例" hint="7级字号">
    <div class="font-size-levels">
      <div
        v-for="level in fontSizeLevels"
        :key="level.key"
        class="font-size-level"
      >
        <span class="level-label">{{ level.label }}</span>
        <span class="level-value">{{ level.value }}</span>
        <span class="level-sample" :style="{ fontSize: level.value }">示例文字</span>
      </div>
    </div>
  </PreviewFrame>

  <!-- 表格预览 -->
  <PreviewFrame title="表格排版" hint="长文本压力测试">
    <div class="table-preview">
      <div class="table-header">
        <span>产品</span>
        <span>价格</span>
        <span>涨跌</span>
      </div>
      <div class="table-row">
        <span>华东地区高纯阴极铜现货报价</span>
        <span class="mono">¥68,500.00</span>
        <span class="price-rise" :style="{ color: editingConfig.priceRiseColor }">+12.35%</span>
      </div>
      <div class="table-row">
        <span>铝锭</span>
        <span class="mono">¥18,200</span>
        <span class="price-fall" :style="{ color: editingConfig.priceFallColor }">-0.8%</span>
      </div>
    </div>
  </PreviewFrame>
</template>

<style scoped>
.font-samples {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.font-sample {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.font-label {
  font-size: 10px;
  color: var(--text-secondary, #888);
}

.font-family {
  font-size: var(--font-size-xs);
  color: #0D6E6E;
  font-family: var(--font-mono);
}

.font-text {
  font-size: var(--font-size-lg);
  color: var(--text-primary, #1A1A1A);
}

.font-size-levels {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.font-size-level {
  display: flex;
  align-items: center;
  gap: 12px;
}

.level-label {
  width: 80px;
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
}

.level-value {
  width: 60px;
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  color: #0D6E6E;
}

.level-sample {
  color: var(--text-primary, #1A1A1A);
}

.table-preview {
  overflow: hidden;
}

.table-header {
  display: flex;
  padding: 8px 12px;
  background: var(--bg-secondary, #FAFAFA);
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.table-header span {
  flex: 1;
}

.table-row {
  display: flex;
  padding: 8px 12px;
  border-top: 1px solid var(--border-light, #F3F4F6);
  font-size: var(--font-size-xs);
}

.table-row span {
  flex: 1;
}

.mono {
  font-family: var(--font-mono);
}

.price-rise,
.price-fall {
  font-weight: 600;
}
</style>