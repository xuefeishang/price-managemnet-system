<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { useCategoryPreviewState } from '@/composables/useCategoryPreviewState'
import { useUserStore } from '@/store/useUserStore'
import CategoryIcons from '@/components/icons/CategoryIcons.vue'
import {
  getCategoryVisualPreset,
  type CategoryVisualPreset,
  type CategoryVisualPresetGroup
} from '@/constants/categoryVisualPresets'
import { rgbaFromHex } from '@/utils/categoryVisualColor'
import type { CategoryVisualConfig } from '@/types'

const categoryState = useCategoryPreviewState()
const userStore = useUserStore()

const isAdmin = computed(() => userStore.isAdmin)

const selectedBuiltInCombo = computed(() =>
  categoryState.combos.find(combo => combo.id === categoryState.selectedComboId.value)
)

const selectedComboName = computed(() => {
  if (categoryState.selectedComboId.value === categoryState.customComboId) return '我的组合'
  return selectedBuiltInCombo.value?.name || categoryState.combos[0]?.name || '组合方案'
})

const customComboTime = computed(() => {
  const updatedAt = categoryState.customCombo.value?.updatedAt
  if (!updatedAt) return ''
  return new Date(updatedAt).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
})

const isPresetActive = (preset: CategoryVisualPreset): boolean => {
  return categoryState.currentVisualConfig.value?.presetId === preset.id
}

const getPresetName = (presetId?: string): string => {
  return getCategoryVisualPreset(presetId).name
}

const applyPreset = (preset: CategoryVisualPreset) => {
  categoryState.applyPreset(preset)
}

const selectCombo = (comboId: string) => {
  categoryState.selectCombo(comboId)

  if (comboId === categoryState.customComboId) {
    // 「我的组合」不应用预设，仅切换选中状态，展示用户当前配置
    return
  }

  // 内置组合：直接应用预设方案
  const summary = categoryState.applySelectedCombo('all')
  showToast(`已应用：更新 ${summary.updated} 个分类`)
}

const setPresetGroup = (group: CategoryVisualPresetGroup) => {
  categoryState.setActivePresetGroup(group)
}

const updateAdvancedColor = (patch: Partial<CategoryVisualConfig>) => {
  categoryState.updateVisualConfig(patch, true)
}

const mixHex = (color: string, percent: number, target = '#FFFFFF'): string => {
  const normalize = (hex: string) => hex.replace('#', '').padEnd(6, '0').slice(0, 6)
  const source = normalize(color)
  const dest = normalize(target)
  const weight = Math.max(0, Math.min(100, percent)) / 100
  const channel = (start: string, end: string) => {
    const next = Math.round(parseInt(start, 16) * weight + parseInt(end, 16) * (1 - weight))
    return next.toString(16).padStart(2, '0')
  }
  return `#${channel(source.slice(0, 2), dest.slice(0, 2))}${channel(source.slice(2, 4), dest.slice(2, 4))}${channel(source.slice(4, 6), dest.slice(4, 6))}`
}

const setSurfaceStrength = (value: number) => {
  const config = categoryState.currentVisualConfig.value
  if (!config) return
  updateAdvancedColor({
    surfaceColor: mixHex(config.primaryColor, value)
  })
}

const setBorderStrength = (value: number) => {
  const config = categoryState.currentVisualConfig.value
  if (!config) return
  updateAdvancedColor({
    borderColor: mixHex(config.primaryColor, value)
  })
}

const setGlowStrength = (value: number) => {
  const config = categoryState.currentVisualConfig.value
  if (!config) return
  updateAdvancedColor({
    glowColor: rgbaFromHex(config.primaryColor, value / 100)
  })
}

const setChartStrength = (value: number) => {
  const config = categoryState.currentVisualConfig.value
  if (!config) return
  updateAdvancedColor({
    chartLineColor: mixHex(config.primaryColor, value, '#111827')
  })
}

onMounted(() => {
  categoryState.loadCategories()
})
</script>

<template>
  <div class="category-visual-panel">
    <section class="config-section">
      <div class="section-header">
        <div>
          <h2 class="section-title">分类视觉</h2>
          <p class="section-hint">先确定整体组合，再按分类细调方案；点击顶部保存配置后首页正式生效</p>
        </div>
        <span class="section-status" v-if="categoryState.selectedCategory.value">
          当前：{{ categoryState.selectedCategory.value.name }}
        </span>
      </div>

      <div class="loading-state" v-if="categoryState.loading.value">加载分类中...</div>
      <div class="empty-state" v-else-if="categoryState.categories.value.length === 0">暂无可配置分类</div>
      <div class="category-grid" v-else>
        <button
          v-for="cat in categoryState.categories.value"
          :key="cat.id"
          type="button"
          class="category-pill"
          :class="{ active: categoryState.selectedCategory.value?.id === cat.id }"
          @click="categoryState.selectCategory(cat)"
        >
          <span class="pill-icon" :style="{ background: categoryState.visualConfigs.value[cat.id]?.surfaceColor || '#EFF6FF' }">
            <CategoryIcons
              :icon="categoryState.visualConfigs.value[cat.id]?.icon || 'cube_ore'"
              :size="18"
              :color="categoryState.visualConfigs.value[cat.id]?.primaryColor || '#2563EB'"
            />
          </span>
          <span class="pill-text">
            <span class="pill-name">{{ cat.name }}</span>
            <span class="pill-preset">{{ getPresetName(categoryState.visualConfigs.value[cat.id]?.presetId) }}</span>
          </span>
        </button>
      </div>
    </section>

    <template v-if="categoryState.currentVisualConfig.value">
      <section class="config-section">
        <div class="section-header">
          <div>
            <h2 class="section-title">整体组合方案</h2>
            <p class="section-hint">点击组合卡片会更新未配置分类的草稿；右侧实时预览展示整体效果，点击顶部保存配置后生效</p>
          </div>
        </div>

        <div class="combo-grid">
          <button
            v-for="combo in categoryState.combos"
            :key="combo.id"
            type="button"
            class="combo-card"
            :class="{ active: categoryState.selectedComboId.value === combo.id }"
            @click="selectCombo(combo.id)"
          >
            <span class="combo-tone">{{ combo.tone }}</span>
            <strong>{{ combo.name }}</strong>
            <small>{{ combo.description }}</small>
            <span class="combo-tags">{{ combo.recommendedFor.slice(0, 3).join(' / ') }}</span>
            <span class="focus-mark" v-if="categoryState.selectedComboId.value === combo.id">当前聚焦</span>
          </button>

          <button
            type="button"
            class="combo-card custom"
            :class="{ active: categoryState.selectedComboId.value === categoryState.customComboId }"
            @click="selectCombo(categoryState.customComboId)"
          >
            <span class="combo-tone">自定义</span>
            <strong>我的组合</strong>
            <small v-if="categoryState.customCombo.value">
              已有 {{ categoryState.customCombo.value.mappings.length }} 个分类映射
            </small>
            <small v-else>尚未创建</small>
            <span class="combo-tags">{{ customComboTime || '只保留 1 组' }}</span>
            <span class="focus-mark" v-if="categoryState.selectedComboId.value === categoryState.customComboId">当前聚焦</span>
          </button>
        </div>

        <div class="combo-actions">
          <div>
            <strong>{{ selectedComboName }}</strong>
            <span>
              已更新 {{ categoryState.lastComboSummary.value?.updated || 0 }} 个分类，
              点击顶部「保存配置」正式生效
            </span>
          </div>
        </div>

        <div class="combo-summary" v-if="categoryState.lastComboSummary.value">
          已生成草稿：更新 {{ categoryState.lastComboSummary.value.updated }} 个分类，
          保留 {{ categoryState.lastComboSummary.value.skipped }} 个分类，
          fallback {{ categoryState.lastComboSummary.value.fallback }} 个分类。请点击顶部“保存配置”正式生效。
        </div>
      </section>

      <section class="config-section">
        <div class="section-header">
          <div>
            <h2 class="section-title">全部方案库</h2>
            <p class="section-hint">为当前分类单独选择视觉方案，变更进入草稿</p>
          </div>
        </div>

        <div class="preset-tabs" role="tablist" aria-label="分类视觉方案分组">
          <button
            v-for="group in categoryState.presetGroups"
            :key="group.key"
            type="button"
            role="tab"
            :aria-selected="categoryState.activePresetGroup.value === group.key"
            :class="{ active: categoryState.activePresetGroup.value === group.key }"
            @click="setPresetGroup(group.key)"
          >
            <strong>{{ group.name }}</strong>
            <small>{{ group.description }}</small>
          </button>
        </div>

        <div class="preset-grid">
          <button
            v-for="preset in categoryState.activeGroupPresets.value"
            :key="preset.id"
            type="button"
            class="preset-card"
            :class="{ active: isPresetActive(preset) }"
            @click="applyPreset(preset)"
          >
            <span class="preset-icon" :style="{ color: preset.primaryColor, background: preset.surfaceColor }">
              <CategoryIcons :icon="preset.icon" :size="20" :color="preset.primaryColor" />
            </span>
            <strong>{{ preset.name }}</strong>
            <small>{{ preset.recommendedFor.slice(0, 3).join(' / ') }}</small>
            <span class="swatches">
              <i :style="{ background: preset.primaryColor }"></i>
              <i :style="{ background: preset.surfaceColor }"></i>
              <i :style="{ background: preset.borderColor }"></i>
            </span>
            <span class="selected-mark" v-if="isPresetActive(preset)">已应用</span>
          </button>
        </div>
      </section>

      <section class="config-section advanced-section" v-if="isAdmin">
        <details open>
          <summary>
            <span>微调助手</span>
          </summary>

          <p class="advanced-hint">适合做轻量修饰；想彻底换风格，优先回到方案库选择新方案。</p>

          <div class="validation-list" v-if="categoryState.currentValidationWarnings.value.length">
            <span v-for="warning in categoryState.currentValidationWarnings.value" :key="warning">{{ warning }}</span>
          </div>

          <div class="tuning-grid">
            <label class="range-field">
              <span>背景浓度</span>
              <input type="range" min="4" max="18" value="8" @input="setSurfaceStrength(Number(($event.target as HTMLInputElement).value))" />
            </label>
            <label class="range-field">
              <span>边框明显度</span>
              <input type="range" min="18" max="48" value="28" @input="setBorderStrength(Number(($event.target as HTMLInputElement).value))" />
            </label>
            <label class="range-field">
              <span>光晕强度</span>
              <input type="range" min="12" max="18" value="14" @input="setGlowStrength(Number(($event.target as HTMLInputElement).value))" />
            </label>
            <label class="range-field">
              <span>趋势线强度</span>
              <input type="range" min="60" max="100" value="82" @input="setChartStrength(Number(($event.target as HTMLInputElement).value))" />
            </label>
          </div>

          <details class="expert-colors" open>
            <summary>专家颜色</summary>
            <div class="advanced-grid">
              <label class="color-field">
                <span>主色</span>
                <input type="color" :value="categoryState.currentVisualConfig.value.primaryColor" @input="updateAdvancedColor({ primaryColor: ($event.target as HTMLInputElement).value })" />
              </label>
              <label class="color-field">
                <span>浅底</span>
                <input type="color" :value="categoryState.currentVisualConfig.value.surfaceColor" @input="updateAdvancedColor({ surfaceColor: ($event.target as HTMLInputElement).value })" />
              </label>
              <label class="color-field">
                <span>边框</span>
                <input type="color" :value="categoryState.currentVisualConfig.value.borderColor" @input="updateAdvancedColor({ borderColor: ($event.target as HTMLInputElement).value })" />
              </label>
              <label class="color-field">
                <span>趋势线</span>
                <input type="color" :value="categoryState.currentVisualConfig.value.chartLineColor" @input="updateAdvancedColor({ chartLineColor: ($event.target as HTMLInputElement).value })" />
              </label>
            </div>
          </details>
        </details>
      </section>
    </template>
  </div>
</template>

<style scoped>
.category-visual-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-section {
  background: var(--bg-card, #FFFFFF);
  border: 1px solid var(--border-color, #E5E5E5);
  border-radius: var(--app-card-radius, 12px);
  padding: 20px;
}

.section-header,
.combo-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary, #1A1A1A);
}

.section-hint,
.advanced-hint {
  margin: 6px 0 0;
  font-size: var(--font-size-sm);
  color: var(--text-secondary, #666666);
}

.section-status,
.combo-tone {
  padding: 5px 10px;
  border-radius: 6px;
  background: var(--bg-secondary, #F5F5F5);
  color: var(--text-secondary, #666666);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.loading-state,
.empty-state {
  padding: 24px;
  color: var(--text-secondary, #666666);
  text-align: center;
}

.category-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.category-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 150px;
  padding: 10px 12px;
  border: 1px solid var(--border-color, #E5E5E5);
  border-radius: 8px;
  background: var(--bg-secondary, #FAFAFA);
  cursor: pointer;
  transition: border-color 150ms ease, background 150ms ease;
}

.category-pill.active {
  border-color: var(--primary-color, #0D6E6E);
  background: var(--bg-card, #FFFFFF);
}

.pill-icon,
.preset-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex-shrink: 0;
}

.pill-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  text-align: left;
}

.pill-name {
  color: var(--text-primary, #1A1A1A);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.pill-preset {
  color: var(--text-secondary, #666666);
  font-size: var(--font-size-xs);
}

.combo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
  gap: 12px;
}

.combo-card,
.preset-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--border-color, #E5E5E5);
  border-radius: 8px;
  background: var(--bg-card, #FFFFFF);
  text-align: left;
  cursor: pointer;
  transition: border-color 150ms ease, transform 150ms ease, box-shadow 150ms ease;
}

.combo-card:hover,
.preset-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md, 0 8px 20px rgba(15, 23, 42, 0.08));
}

.combo-card.active,
.preset-card.active {
  border-color: var(--primary-color, #0D6E6E);
}

.combo-card.active {
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 7%, var(--bg-card, #FFFFFF));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary-color, #0D6E6E) 14%, transparent);
  transform: translateY(-1px);
}

.combo-card.custom {
  background: linear-gradient(135deg, var(--bg-card, #FFFFFF), var(--bg-secondary, #F8FAFC));
}

.combo-card.custom.active {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--primary-color, #0D6E6E) 8%, var(--bg-card, #FFFFFF)),
    var(--bg-secondary, #F8FAFC)
  );
}

.combo-card strong,
.preset-card strong {
  color: var(--text-primary, #1A1A1A);
  font-size: var(--font-size-base);
}

.combo-card small,
.combo-tags,
.preset-card small {
  color: var(--text-secondary, #666666);
  font-size: var(--font-size-xs);
  line-height: 1.5;
}

.combo-tags {
  margin-top: auto;
}

.focus-mark {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 3px 7px;
  border-radius: 999px;
  background: var(--primary-color, #0D6E6E);
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 600;
}

.combo-actions {
  align-items: center;
  margin-top: 16px;
  margin-bottom: 12px;
  padding: 12px;
  border-radius: 8px;
  background: var(--bg-secondary, #F8FAFC);
}

.combo-actions > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--text-secondary, #666666);
  font-size: var(--font-size-xs);
}

.combo-actions strong {
  color: var(--text-primary, #1A1A1A);
  font-size: var(--font-size-sm);
}

.combo-summary {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--primary-color, #0D6E6E) 8%, transparent);
  color: var(--text-secondary, #666666);
  font-size: var(--font-size-xs);
}

.preset-tabs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.preset-tabs button {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 72px;
  padding: 10px;
  border: 1px solid var(--border-color, #E5E5E5);
  border-radius: 8px;
  background: var(--bg-secondary, #FAFAFA);
  text-align: left;
  cursor: pointer;
}

.preset-tabs button.active {
  border-color: var(--primary-color, #0D6E6E);
  background: var(--bg-card, #FFFFFF);
}

.preset-tabs strong,
.preset-tabs small {
  color: var(--text-primary, #1A1A1A);
  font-size: var(--font-size-sm);
}

.preset-tabs small {
  color: var(--text-secondary, #666666);
  font-size: var(--font-size-xs);
  line-height: 1.4;
}

.preset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.swatches {
  display: flex;
  gap: 5px;
}

.swatches i {
  width: 22px;
  height: 10px;
  border-radius: 99px;
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.selected-mark {
  position: absolute;
  right: 12px;
  bottom: 10px;
  color: var(--primary-color, #0D6E6E);
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.advanced-section summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  color: var(--text-primary, #1A1A1A);
  font-weight: 600;
}

.validation-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 14px;
  color: var(--warning-color, #F59E0B);
  font-size: var(--font-size-xs);
}

.tuning-grid,
.advanced-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.range-field,
.color-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--border-color, #E5E5E5);
  border-radius: 8px;
}

.range-field {
  flex-direction: column;
  align-items: stretch;
}

.range-field span,
.color-field span {
  color: var(--text-secondary, #666666);
  font-size: var(--font-size-sm);
}

.color-field input {
  width: 38px;
  height: 30px;
  border: none;
  background: transparent;
  cursor: pointer;
}

.expert-colors {
  margin-top: 16px;
}

@media (max-width: 768px) {
  .section-header,
  .combo-actions {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .preset-tabs {
    width: 100%;
  }

  .preset-tabs {
    display: flex;
    overflow-x: auto;
    padding-bottom: 4px;
  }

  .preset-tabs button {
    min-width: 136px;
  }
}
</style>
