<script setup lang="ts">
import { onMounted } from 'vue'
import PreviewFrame from './PreviewFrame.vue'
import type { StyleConfig } from '@/types/theme'
import { useCategoryPreviewState } from '@/composables/useCategoryPreviewState'
import CategoryIcons from '@/components/icons/CategoryIcons.vue'

defineProps<{
  editingConfig: StyleConfig
}>()

const categoryState = useCategoryPreviewState()

onMounted(() => {
  categoryState.loadCategories()
})
</script>

<template>
  <PreviewFrame title="分类视觉预览" hint="跟随当前编辑分类">
    <div class="category-card-preview" v-if="categoryState.currentVisualConfig.value">
      <div class="combo-board">
        <div class="combo-board-header">
          <div>
            <strong>{{ categoryState.selectedComboId.value === categoryState.customComboId ? '我的组合' : '整体组合效果' }}</strong>
            <small>多分类并排预览</small>
          </div>
          <span>{{ categoryState.selectedComboPreview.value.filter(item => !item.skipped).length }} 个将更新</span>
        </div>

        <div class="combo-card-grid">
          <div
            v-for="item in categoryState.selectedComboPreview.value.slice(0, 6)"
            :key="item.category.id"
            class="combo-product-card"
            :class="{ skipped: item.skipped }"
            :style="{
              '--preview-primary': item.primaryColor,
              '--preview-surface': item.surfaceColor || '#F8FAFC',
              borderColor: item.borderColor
            }"
          >
            <div class="combo-product-top">
              <span class="combo-icon" :style="{ background: item.surfaceColor || '#F8FAFC' }">
                <CategoryIcons :icon="item.icon" :size="15" :color="item.primaryColor" />
              </span>
              <span>
                <strong>{{ item.category.name }}</strong>
                <small>{{ item.skipped ? item.reason : item.presetName }}</small>
              </span>
            </div>
            <div class="combo-product-price">
              <strong>¥5,220</strong>
              <i></i>
            </div>
          </div>
        </div>
      </div>

      <div
        class="category-card light"
        :class="{ 'combo-preview': categoryState.currentComboPreviewItem.value }"
        :style="{
          '--category-primary': categoryState.currentComboPreviewItem.value?.primaryColor || categoryState.currentVisualConfig.value.primaryColor,
          '--category-surface': categoryState.currentComboPreviewItem.value?.surfaceColor || categoryState.currentVisualConfig.value.surfaceColor || categoryState.currentVisualConfig.value.secondaryColor,
          '--category-text': categoryState.currentVisualConfig.value.textColor,
          '--category-chart-line': categoryState.currentComboPreviewItem.value?.primaryColor || categoryState.currentVisualConfig.value.chartLineColor || categoryState.currentVisualConfig.value.primaryColor,
          borderColor: categoryState.currentComboPreviewItem.value?.borderColor || categoryState.currentVisualConfig.value.borderColor,
          boxShadow: `0 0 16px ${categoryState.currentVisualConfig.value.glowColor}`
        }"
      >
        <div class="category-card-header">
          <span class="category-icon" :style="{ background: categoryState.currentComboPreviewItem.value?.surfaceColor || categoryState.currentVisualConfig.value.surfaceColor || categoryState.currentVisualConfig.value.secondaryColor }">
            <CategoryIcons
              :icon="categoryState.currentComboPreviewItem.value?.icon || categoryState.currentVisualConfig.value.icon"
              :size="18"
              :color="categoryState.currentComboPreviewItem.value?.primaryColor || categoryState.currentVisualConfig.value.primaryColor"
            />
          </span>
          <span class="category-title">
            <strong>{{ categoryState.selectedCategory.value?.name || '分类名称' }}</strong>
            <small>{{ categoryState.currentComboPreviewItem.value?.presetName || categoryState.currentPreset.value?.name || '旧版配置' }}</small>
          </span>
        </div>
        <div class="category-card-price">
          <span class="price-value" :style="{ color: categoryState.currentComboPreviewItem.value?.primaryColor || categoryState.currentVisualConfig.value.primaryColor }">¥68,500</span>
          <span class="price-change" :style="{ color: editingConfig.priceRiseColor }">+2.5%</span>
        </div>
        <div class="preview-line"></div>
        <div class="combo-preview-tag" v-if="categoryState.currentComboPreviewItem.value?.skipped">
          <small>{{ categoryState.currentComboPreviewItem.value.reason || '已有配置' }}</small>
        </div>
      </div>

      <div class="combo-mini-preview">
        <div class="mini-header">
          <strong>影响明细</strong>
          <span>{{ categoryState.selectedComboPreview.value.length }} 个分类</span>
        </div>
        <div class="mini-list">
          <div
            v-for="item in categoryState.selectedComboPreview.value.slice(0, 4)"
            :key="item.category.id"
            class="mini-item"
            :class="{ skipped: item.skipped }"
          >
            <span :style="{ background: item.primaryColor }"></span>
            <strong>{{ item.category.name }}</strong>
            <small>{{ item.skipped ? item.reason : item.presetName }}</small>
          </div>
        </div>
      </div>
    </div>
    <div class="category-hint" v-if="categoryState.currentVisualConfig.value">
      <span>{{ categoryState.selectedCategory.value?.name || '当前分类' }}</span>
      <span>{{ categoryState.currentVisualConfig.value?.presetVersion ? `v${categoryState.currentVisualConfig.value.presetVersion}` : 'custom' }}</span>
    </div>
  </PreviewFrame>
</template>

<style scoped>
.category-card-preview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.combo-board {
  padding: 12px;
  border-radius: 10px;
  background: #F8FAFC;
  border: 1px solid var(--border-color, #E5E5E5);
}

.combo-board-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.combo-board-header div {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.combo-board-header strong {
  color: var(--text-primary, #1A1A1A);
  font-size: var(--font-size-sm);
}

.combo-board-header small,
.combo-board-header span {
  color: var(--text-secondary, #888);
  font-size: var(--font-size-xs);
}

.combo-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.combo-product-card {
  min-width: 0;
  padding: 9px;
  border: 1px solid;
  border-radius: 8px;
  background: linear-gradient(135deg, #FFFFFF 0%, var(--preview-surface) 100%);
}

.combo-product-card.skipped {
  opacity: 0.55;
  filter: grayscale(0.2);
}

.combo-product-top {
  display: flex;
  align-items: center;
  gap: 7px;
}

.combo-icon {
  display: inline-flex;
  width: 26px;
  height: 26px;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  flex-shrink: 0;
}

.combo-product-top span:last-child {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.combo-product-top strong {
  color: var(--preview-primary);
  font-size: 11px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.combo-product-top small {
  color: var(--text-secondary, #888);
  font-size: 9px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.combo-product-price {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin-top: 8px;
}

.combo-product-price strong {
  color: var(--preview-primary);
  font-family: var(--font-mono);
  font-size: 12px;
}

.combo-product-price i {
  width: 34px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, var(--preview-primary));
}

.category-card {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid transparent;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.category-card.light {
  background: linear-gradient(135deg, var(--bg-card, #FFFFFF) 0%, color-mix(in srgb, var(--category-surface) 70%, var(--bg-card, #FFFFFF)) 100%);
}

.category-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.category-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.category-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.category-title strong {
  color: var(--category-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.category-title small {
  color: var(--text-secondary, #888);
  font-size: var(--font-size-xs);
}

.category-card-price {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.price-value {
  font-family: var(--font-mono);
  font-size: var(--font-size-base);
  font-weight: 600;
}

.price-change {
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.preview-line {
  height: 18px;
  border-radius: 999px;
  background: linear-gradient(120deg, transparent 12%, var(--category-chart-line) 36%, transparent 37%, var(--category-chart-line) 66%, transparent 67%);
  opacity: 0.55;
}

.combo-preview-tag {
  margin-top: 8px;
  padding: 4px 8px;
  border-radius: 4px;
  background: rgba(245, 158, 11, 0.1);
  text-align: center;
}

.combo-preview-tag small {
  color: #F59E0B;
  font-size: 10px;
}

.combo-mini-preview {
  padding: 12px;
  border-radius: 8px;
  background: var(--bg-secondary, #FAFAFA);
  border: 1px solid var(--border-color, #E5E5E5);
}

.mini-header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  font-size: var(--font-size-xs);
}

.mini-header strong {
  color: var(--text-primary, #1A1A1A);
}

.mini-header span {
  color: var(--text-secondary, #888);
}

.mini-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.mini-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  font-size: 10px;
}

.mini-item.skipped {
  opacity: 0.55;
}

.mini-item span {
  width: 10px;
  height: 10px;
  border-radius: 999px;
}

.mini-item strong {
  color: var(--text-primary, #1A1A1A);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mini-item small {
  color: var(--text-secondary, #888);
  white-space: nowrap;
}

.category-hint {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: var(--font-size-xs);
  color: var(--text-secondary, #888);
}
</style>
