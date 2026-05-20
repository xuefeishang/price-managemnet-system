<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { getDictByCategory } from '@/composables/useDict'
import { updateDict } from '@/api/dict'
import type { CategoryVisualConfig } from '@/types'

// 分类列表
interface Category {
  id: number
  name: string
  code: string
}

const categories = ref<Category[]>([])
const selectedCategory = ref<Category | null>(null)
const visualConfig = ref<CategoryVisualConfig | null>(null)
const loading = ref(false)
const saving = ref(false)

// 预置分类配色
const presetColors: Record<string, CategoryVisualConfig> = {
  'GOLD': {
    categoryCode: 'GOLD',
    primaryColor: '#D4A574',
    secondaryColor: '#C4956A',
    textColor: '#8B5A2B',
    borderColor: '#165DFF',
    glowColor: 'rgba(212, 165, 116, 0.15)',
    icon: 'gold_ingot',
    iconType: 'builtin'
  },
  'SILVER': {
    categoryCode: 'SILVER',
    primaryColor: '#A8B5C4',
    secondaryColor: '#9AA8B7',
    textColor: '#6B7B8A',
    borderColor: '#165DFF',
    glowColor: 'rgba(168, 181, 196, 0.15)',
    icon: 'silver_bar',
    iconType: 'builtin'
  },
  'COPPER': {
    categoryCode: 'COPPER',
    primaryColor: '#B87333',
    secondaryColor: '#A66628',
    textColor: '#8B4513',
    borderColor: '#165DFF',
    glowColor: 'rgba(184, 115, 51, 0.15)',
    icon: 'copper_coil',
    iconType: 'builtin'
  },
  'IRON': {
    categoryCode: 'IRON',
    primaryColor: '#8B4513',
    secondaryColor: '#7A3D11',
    textColor: '#5C3317',
    borderColor: '#165DFF',
    glowColor: 'rgba(139, 69, 19, 0.15)',
    icon: 'iron_ore',
    iconType: 'builtin'
  },
  'ALUMINUM': {
    categoryCode: 'ALUMINUM',
    primaryColor: '#C0C0C0',
    secondaryColor: '#B0B0B0',
    textColor: '#808080',
    borderColor: '#165DFF',
    glowColor: 'rgba(192, 192, 192, 0.12)',
    icon: 'aluminum_block',
    iconType: 'builtin'
  },
  'RARE_EARTH': {
    categoryCode: 'RARE_EARTH',
    primaryColor: '#8B5CF6',
    secondaryColor: '#7C3AED',
    textColor: '#6D28D9',
    borderColor: '#165DFF',
    glowColor: 'rgba(139, 92, 246, 0.15)',
    icon: 'rare_element',
    iconType: 'builtin'
  }
}

// 图标列表
const iconOptions = [
  { value: 'gold_ingot', label: '金锭' },
  { value: 'silver_bar', label: '银条' },
  { value: 'copper_coil', label: '铜线圈' },
  { value: 'iron_ore', label: '铁矿石' },
  { value: 'aluminum_block', label: '铝块' },
  { value: 'rare_element', label: '稀土' }
]

// 加载分类列表（字典已在 Layout.vue 预加载）
const loadCategories = async () => {
  loading.value = true
  try {
    const dicts = getDictByCategory('category_visual_config')
    categories.value = dicts
      .filter(d => d.status === 'ACTIVE')
      .map(d => {
        const config = d.extraValue ? JSON.parse(d.extraValue) : {}
        return {
          id: d.id,
          name: d.dictValue,
          code: config.categoryCode || d.dictKey
        }
      })

    // 默认选中第一个
    if (categories.value.length > 0 && !selectedCategory.value) {
      selectCategory(categories.value[0])
    }
  } catch (error) {
    console.error('Failed to load categories:', error)
    showToast('加载分类失败')
  } finally {
    loading.value = false
  }
}

// 选择分类
const selectCategory = (category: Category) => {
  selectedCategory.value = category
  loadVisualConfig(category)
}

// 加载视觉配置
const loadVisualConfig = (category: Category) => {
  const dicts = getDictByCategory('category_visual_config')
  const dict = dicts.find(d => d.id === category.id)
  if (dict && dict.extraValue) {
    try {
      visualConfig.value = JSON.parse(dict.extraValue)
    } catch {
      visualConfig.value = presetColors[category.code] || createDefaultConfig(category.code)
    }
  } else {
    visualConfig.value = presetColors[category.code] || createDefaultConfig(category.code)
  }
}

// 创建默认配置
const createDefaultConfig = (code: string): CategoryVisualConfig => ({
  categoryCode: code,
  primaryColor: '#165DFF',
  secondaryColor: '#3C7EFF',
  textColor: '#1D2129',
  borderColor: '#165DFF',
  glowColor: 'rgba(22, 93, 255, 0.15)',
  icon: 'default',
  iconType: 'builtin'
})

// 保存配置
const saveConfig = async () => {
  if (!selectedCategory.value || !visualConfig.value) return

  saving.value = true
  try {
    await updateDict(selectedCategory.value.id, {
      extraValue: JSON.stringify(visualConfig.value)
    })
    showToast({ message: '已保存', position: 'top', duration: 1000 })
  } catch (error) {
    console.error('Failed to save visual config:', error)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

// 颜色变更（统一保存）
const onColorChange = async () => {
  await saveConfig()
}

// 应用预设配色
const applyPreset = async (preset: CategoryVisualConfig) => {
  if (!visualConfig.value) return
  visualConfig.value = { ...preset, categoryCode: visualConfig.value.categoryCode }
  await saveConfig()
}

// 计算光晕透明度
const glowOpacity = computed(() => {
  if (!visualConfig.value?.glowColor) return 0.15
  const match = visualConfig.value.glowColor.match(/rgba\([^,]+,[^,]+,[^,]+,\s*([\d.]+)\)/)
  return match ? parseFloat(match[1]) : 0.15
})

// 更新光晕透明度
const updateGlowOpacity = (opacity: number) => {
  if (!visualConfig.value) return
  const rgb = visualConfig.value.primaryColor.match(/^#([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})$/)
  if (rgb) {
    const r = parseInt(rgb[1], 16)
    const g = parseInt(rgb[2], 16)
    const b = parseInt(rgb[3], 16)
    visualConfig.value.glowColor = `rgba(${r}, ${g}, ${b}, ${opacity.toFixed(2)})`
  }
}

onMounted(() => {
  loadCategories()
})
</script>

<template>
  <div class="category-visual-panel">
    <!-- 分类选择 -->
    <section class="config-section">
      <h2 class="section-title">分类选择</h2>
      <div class="category-tabs">
        <div
          v-for="cat in categories"
          :key="cat.id"
          class="category-tab"
          :class="{ active: selectedCategory?.id === cat.id }"
          @click="selectCategory(cat)"
        >
          {{ cat.name }}
        </div>
      </div>
    </section>

    <!-- 颜色配置 -->
    <section class="config-section" v-if="visualConfig">
      <h2 class="section-title">
        颜色配置
        <span class="section-status">当前：{{ selectedCategory?.name }}</span>
      </h2>

      <div class="color-config">
        <!-- 主色 -->
        <div class="color-row">
          <span class="color-label">主色</span>
          <div class="color-input-group">
            <input
              type="color"
              v-model="visualConfig.primaryColor"
              @change="onColorChange"
              class="color-picker"
            />
            <input
              type="text"
              v-model="visualConfig.primaryColor"
              @change="onColorChange"
              class="color-text"
            />
          </div>
        </div>

        <!-- 辅色 -->
        <div class="color-row">
          <span class="color-label">辅色</span>
          <div class="color-input-group">
            <input
              type="color"
              v-model="visualConfig.secondaryColor"
              @change="onColorChange"
              class="color-picker"
            />
            <input
              type="text"
              v-model="visualConfig.secondaryColor"
              @change="onColorChange"
              class="color-text"
            />
          </div>
        </div>

        <!-- 文本色 -->
        <div class="color-row">
          <span class="color-label">文本色</span>
          <div class="color-input-group">
            <input
              type="color"
              v-model="visualConfig.textColor"
              @change="onColorChange"
              class="color-picker"
            />
            <input
              type="text"
              v-model="visualConfig.textColor"
              @change="onColorChange"
              class="color-text"
            />
          </div>
        </div>

        <!-- 边框色 -->
        <div class="color-row">
          <span class="color-label">边框色</span>
          <div class="color-input-group">
            <input
              type="color"
              v-model="visualConfig.borderColor"
              @change="onColorChange"
              class="color-picker"
            />
            <input
              type="text"
              v-model="visualConfig.borderColor"
              @change="onColorChange"
              class="color-text"
            />
          </div>
        </div>

        <!-- 光晕色 -->
        <div class="color-row">
          <span class="color-label">光晕色</span>
          <div class="glow-config">
            <span
              class="glow-preview"
              :style="{ background: visualConfig.glowColor }"
            ></span>
            <input
              type="range"
              :value="glowOpacity"
              @input="updateGlowOpacity(parseFloat(($event.target as HTMLInputElement).value)); saveConfig()"
              min="0.05"
              max="0.5"
              step="0.05"
              class="opacity-slider"
            />
            <span class="opacity-value">{{ (glowOpacity * 100).toFixed(0) }}%</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 图标配置 -->
    <section class="config-section" v-if="visualConfig">
      <h2 class="section-title">图标配置</h2>
      <div class="icon-config">
        <div
          v-for="icon in iconOptions"
          :key="icon.value"
          class="icon-option"
          :class="{ active: visualConfig.icon === icon.value }"
          @click="visualConfig.icon = icon.value; saveConfig()"
        >
          <span class="icon-name">{{ icon.label }}</span>
        </div>
      </div>
    </section>

    <!-- 预设配色 -->
    <section class="config-section">
      <h2 class="section-title">预设配色</h2>
      <div class="preset-grid">
        <div
          v-for="(preset, key) in presetColors"
          :key="key"
          class="preset-card"
          @click="applyPreset(preset)"
        >
          <span
            class="preset-color"
            :style="{ background: preset.primaryColor }"
          ></span>
          <span class="preset-label">{{ key }}</span>
        </div>
      </div>
    </section>

    <!-- 预览卡片 -->
    <section class="config-section" v-if="visualConfig">
      <h2 class="section-title">预览效果</h2>
      <div
        class="preview-card"
        :style="{
          '--category-primary': visualConfig.primaryColor,
          '--category-secondary': visualConfig.secondaryColor,
          '--category-text': visualConfig.textColor,
          '--category-border': visualConfig.borderColor,
          '--category-glow': visualConfig.glowColor
        }"
      >
        <div class="preview-header">
          <span class="preview-name">{{ selectedCategory?.name || '分类名称' }}</span>
        </div>
        <div class="preview-body">
          <div class="preview-price">¥ 123,456.78</div>
          <div class="preview-change">+2.35%</div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.category-visual-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-section {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
}

.section-status {
  font-size: var(--font-size-xs);
  color: #666666;
  padding: 4px 12px;
  background: #F5F5F5;
  border-radius: 4px;
}

/* 分类选择 */
.category-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-tab {
  padding: 8px 16px;
  font-size: var(--font-size-sm);
  color: #666666;
  background: #FAFAFA;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  cursor: pointer;
  transition: all 150ms;
}

.category-tab:hover {
  border-color: #0D6E6E;
}

.category-tab.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

/* 颜色配置 */
.color-config {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.color-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.color-label {
  width: 80px;
  font-size: var(--font-size-sm);
  color: #1A1A1A;
}

.color-input-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-picker {
  width: 40px;
  height: 40px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  cursor: pointer;
  padding: 2px;
}

.color-text {
  width: 100px;
  height: 36px;
  padding: 0 8px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  font-size: var(--font-size-sm);
  font-family: var(--font-mono);
}

/* 光晕配置 */
.glow-config {
  display: flex;
  align-items: center;
  gap: 12px;
}

.glow-preview {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #E5E5E5;
}

.opacity-slider {
  width: 120px;
}

.opacity-value {
  font-size: var(--font-size-xs);
  color: #666666;
}

/* 图标配置 */
.icon-config {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.icon-option {
  padding: 8px 16px;
  font-size: var(--font-size-sm);
  color: #666666;
  background: #FAFAFA;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  cursor: pointer;
  transition: all 150ms;
}

.icon-option:hover {
  border-color: #0D6E6E;
}

.icon-option.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}

/* 预设配色 */
.preset-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.preset-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: #FAFAFA;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 150ms;
}

.preset-card:hover {
  border-color: #0D6E6E;
}

.preset-color {
  width: 48px;
  height: 48px;
  border-radius: 8px;
}

.preset-label {
  font-size: var(--font-size-xs);
  color: #666666;
}

/* 预览卡片 */
.preview-card {
  padding: 16px;
  background: #FFFFFF;
  border: 2px solid var(--category-border);
  border-radius: 12px;
  box-shadow: 0 0 20px var(--category-glow);
}

.preview-header {
  margin-bottom: 12px;
}

.preview-name {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: var(--category-text);
}

.preview-body {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.preview-price {
  font-size: var(--font-size-xl);
  font-weight: 600;
  font-family: var(--font-mono);
  color: var(--category-primary);
}

.preview-change {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #10B981;
}
</style>