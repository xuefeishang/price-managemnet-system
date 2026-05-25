<script setup lang="ts">
import { computed } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import type { StyleConfig } from '@/types/theme'

const props = defineProps<{
  editingConfig: StyleConfig
}>()

const logoUrlFull = computed(() => {
  if (!props.editingConfig.logoUrl) return ''
  if (props.editingConfig.logoUrl.startsWith('data:')) return props.editingConfig.logoUrl
  if (props.editingConfig.logoUrl.startsWith('http')) return props.editingConfig.logoUrl
  return window.location.origin + props.editingConfig.logoUrl
})
</script>

<template>
  <PreviewFrame title="系统整体效果" hint="导航 + 价格卡片 + 表格">
    <div class="system-overview">
      <!-- 迷你导航 -->
      <div class="mini-nav">
        <div class="mini-nav-brand">
          <img v-if="logoUrlFull" :src="logoUrlFull" alt="Logo" class="mini-nav-logo" style="width: 20px; height: 20px;" />
          <span class="mini-nav-name">{{ editingConfig.systemName }}</span>
        </div>
        <div class="mini-nav-items">
          <span class="mini-nav-item active">首页</span>
          <span class="mini-nav-item">产品</span>
        </div>
      </div>
      <!-- 迷你价格卡片 -->
      <div class="mini-cards">
        <div class="mini-card">
          <span class="mini-card-name">电铜</span>
          <span class="mini-card-price">¥68,500</span>
          <span class="mini-card-change" :style="{ color: editingConfig.priceRiseColor }">+2.5%</span>
        </div>
        <div class="mini-card">
          <span class="mini-card-name">铝锭</span>
          <span class="mini-card-price">¥18,200</span>
          <span class="mini-card-change" :style="{ color: editingConfig.priceFallColor }">-1.1%</span>
        </div>
      </div>
      <!-- 迷你表格 -->
      <div class="mini-table">
        <div class="mini-table-header">
          <span>产品</span>
          <span>价格</span>
          <span>涨跌</span>
        </div>
        <div class="mini-table-row">
          <span>电铜</span>
          <span class="mono">¥68,500</span>
          <span :style="{ color: editingConfig.priceRiseColor }">+2.5%</span>
        </div>
      </div>
    </div>
  </PreviewFrame>
</template>

<style scoped>
.system-overview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mini-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  background: var(--app-nav-bg, #FFFFFF);
  border-bottom: 1px solid var(--border-light, #E5E5E5);
}

.mini-nav-brand {
  display: flex;
  align-items: center;
  gap: 4px;
}

.mini-nav-logo {
  object-fit: contain;
}

.mini-nav-name {
  font-family: var(--font-heading);
  font-size: 10px;
  font-weight: 500;
  color: var(--app-nav-text, #1A1A1A);
}

.mini-nav-items {
  display: flex;
  gap: 8px;
}

.mini-nav-item {
  font-size: 9px;
  color: var(--app-nav-text, #1A1A1A);
  padding: 2px 4px;
  border-radius: 2px;
}

.mini-nav-item.active {
  background: rgba(13, 110, 110, 0.15);
}

.mini-cards {
  display: flex;
  gap: 8px;
}

.mini-card {
  flex: 1;
  padding: 8px;
  background: var(--bg-card, #FFFFFF);
  border-radius: 4px;
  border: 1px solid var(--border-light, #E5E5E5);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mini-card-name {
  font-size: 9px;
  color: var(--text-secondary, #888);
}

.mini-card-price {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.mini-card-change {
  font-family: var(--font-mono);
  font-size: 9px;
  font-weight: 600;
}

.mini-table {
  font-size: 9px;
}

.mini-table-header {
  display: flex;
  padding: 4px 8px;
  background: var(--bg-secondary, #FAFAFA);
  font-weight: 600;
}

.mini-table-header span,
.mini-table-row span {
  flex: 1;
}

.mini-table-row {
  display: flex;
  padding: 4px 8px;
  border-top: 1px solid var(--border-light, #F3F4F6);
}

.mono {
  font-family: var(--font-mono);
}
</style>