import { ref, computed } from 'vue'
import { getDictByCategory } from '@/composables/useDict'
import type { CategoryVisualConfig } from '@/types'
import { getCategoryVisualPreset, buildCategoryVisualConfigFromPreset } from '@/constants/categoryVisualPresets'

// 分类视觉配置缓存（避免重复解析JSON）
const visualCacheById = new Map<number, CategoryVisualConfig>()
const visualCacheByCode = new Map<string, CategoryVisualConfig>()

// 分类ID到Code的映射缓存
const categoryCodeMap = new Map<number, string>()

// 默认视觉配置
const DEFAULT_VISUAL: CategoryVisualConfig = {
  categoryCode: 'DEFAULT',
  presetId: 'blue_ore',
  presetVersion: 1,
  customized: false,
  primaryColor: '#2563EB',
  secondaryColor: '#DBEAFE',
  textColor: '#1D4ED8',
  borderColor: '#BFDBFE',
  surfaceColor: '#EFF6FF',
  chartLineColor: '#2563EB',
  chartAreaColor: 'rgba(37, 99, 235, 0.12)',
  glowColor: 'rgba(37, 99, 235, 0.14)',
  icon: 'cube_ore',
  iconType: 'builtin',
  darkMode: {
    primaryColor: '#93C5FD',
    textColor: '#DBEAFE',
    borderColor: '#1D4ED8',
    surfaceColor: '#0B1F45',
    glowColor: 'rgba(147, 197, 253, 0.16)'
  }
}

const withVisualFallbacks = (config: CategoryVisualConfig): CategoryVisualConfig => {
  const presetBase = config.presetId
    ? buildCategoryVisualConfigFromPreset(
      { id: config.categoryId || 0, code: config.categoryCode || 'DEFAULT' },
      getCategoryVisualPreset(config.presetId)
    )
    : DEFAULT_VISUAL

  const merged = {
    ...presetBase,
    ...config
  }

  return {
    ...merged,
    secondaryColor: merged.secondaryColor || merged.surfaceColor || presetBase.secondaryColor || DEFAULT_VISUAL.secondaryColor,
    surfaceColor: merged.surfaceColor || merged.secondaryColor || presetBase.surfaceColor || DEFAULT_VISUAL.surfaceColor,
    chartLineColor: merged.chartLineColor || merged.primaryColor || presetBase.chartLineColor || DEFAULT_VISUAL.chartLineColor,
    chartAreaColor: merged.chartAreaColor || merged.glowColor || presetBase.chartAreaColor || DEFAULT_VISUAL.chartAreaColor,
    glowColor: merged.glowColor || presetBase.glowColor || DEFAULT_VISUAL.glowColor,
    icon: merged.icon || presetBase.icon || DEFAULT_VISUAL.icon,
    iconType: merged.iconType || 'builtin',
    darkMode: merged.darkMode || presetBase.darkMode || DEFAULT_VISUAL.darkMode
  }
}

/**
 * 注册分类ID到Code的映射
 * 在加载分类数据后调用
 */
export const registerCategoryCode = (categoryId: number, categoryCode: string) => {
  categoryCodeMap.set(categoryId, categoryCode)
}

/**
 * 批量注册分类映射
 */
export const registerCategoryCodes = (categories: Array<{ id: number; code: string }>) => {
  categories.forEach(c => categoryCodeMap.set(c.id, c.code))
}

/**
 * 解析分类视觉配置（按分类编码）
 * 从字典数据中解析指定分类的视觉配置
 */
export const parseCategoryVisualByCode = (categoryCode: string): CategoryVisualConfig | null => {
  // 检查缓存
  if (visualCacheByCode.has(categoryCode)) {
    return visualCacheByCode.get(categoryCode)!
  }

  const configs = getDictByCategory('category_visual_config')

  for (const dict of configs) {
    try {
      const config = JSON.parse(dict.extraValue || '{}') as CategoryVisualConfig
      if (config.categoryCode === categoryCode || dict.dictKey === categoryCode) {
        // 存入缓存
        const normalized = withVisualFallbacks(config)
        visualCacheByCode.set(categoryCode, normalized)
        return normalized
      }
    } catch (e) {
      console.warn(`Failed to parse category visual config: ${dict.dictKey}`, e)
    }
  }

  return null
}

/**
 * 解析分类视觉配置（按分类ID）
 * 优先通过ID查找Code，再用Code匹配字典配置
 */
export const parseCategoryVisualConfig = (categoryId: number): CategoryVisualConfig | null => {
  // 检查缓存
  if (visualCacheById.has(categoryId)) {
    return visualCacheById.get(categoryId)!
  }

  // 先通过ID获取Code
  const categoryCode = categoryCodeMap.get(categoryId)
  if (categoryCode) {
    // 用Code匹配
    const config = parseCategoryVisualByCode(categoryCode)
    if (config) {
      visualCacheById.set(categoryId, config)
      return config
    }
  }

  // 兼容旧逻辑：尝试直接匹配categoryId（部分配置可能使用categoryId）
  const configs = getDictByCategory('category_visual_config')
  for (const dict of configs) {
    try {
      const config = JSON.parse(dict.extraValue || '{}') as CategoryVisualConfig
      if (config.categoryId === categoryId) {
        const normalized = withVisualFallbacks(config)
        visualCacheById.set(categoryId, normalized)
        return normalized
      }
    } catch (e) {
      console.warn(`Failed to parse category visual config: ${dict.dictKey}`, e)
    }
  }

  return null
}

/**
 * 获取分类视觉配置（带默认值）
 * @param categoryId 分类ID
 * @param isDarkMode 是否深色模式
 */
export const getCategoryVisual = (
  categoryId: number | undefined,
  isDarkMode: boolean = false
): CategoryVisualConfig => {
  if (!categoryId) {
    return applyDarkMode(DEFAULT_VISUAL, isDarkMode)
  }

  const config = parseCategoryVisualConfig(categoryId)
  if (config) {
    return applyDarkMode(config, isDarkMode)
  }

  return applyDarkMode(DEFAULT_VISUAL, isDarkMode)
}

/**
 * 根据分类编码获取视觉配置
 * @param categoryCode 分类编码（如 BLACK_METAL, PRECIOUS_METAL）
 * @param isDarkMode 是否深色模式
 */
export const getCategoryVisualByCode = (
  categoryCode: string | undefined,
  isDarkMode: boolean = false
): CategoryVisualConfig => {
  if (!categoryCode) {
    return applyDarkMode(DEFAULT_VISUAL, isDarkMode)
  }

  const config = parseCategoryVisualByCode(categoryCode)
  if (config) {
    return applyDarkMode(config, isDarkMode)
  }

  return applyDarkMode(DEFAULT_VISUAL, isDarkMode)
}

/**
 * 应用深色主题配置
 */
const applyDarkMode = (config: CategoryVisualConfig, isDarkMode: boolean): CategoryVisualConfig => {
  if (!isDarkMode || !config.darkMode) {
    return config
  }

  return {
    ...config,
    primaryColor: config.darkMode.primaryColor,
    textColor: config.darkMode.textColor,
    borderColor: config.darkMode.borderColor,
    glowColor: config.darkMode.glowColor
  }
}

/**
 * 根据分类名称推断视觉配置（备用方案）
 */
export const inferVisualFromName = (name: string): CategoryVisualConfig => {
  const nameLower = name.toLowerCase()

  // 黄金
  if (nameLower.includes('金') || nameLower.includes('gold')) {
    return buildCategoryVisualConfigFromPreset({ id: 0, code: 'GOLD' }, getCategoryVisualPreset('gold_precious'))
  }

  // 白银
  if (nameLower.includes('银') || nameLower.includes('silver')) {
    return buildCategoryVisualConfigFromPreset({ id: 0, code: 'SILVER' }, getCategoryVisualPreset('silver_neutral'))
  }

  // 铜
  if (nameLower.includes('铜') || nameLower.includes('copper')) {
    return buildCategoryVisualConfigFromPreset({ id: 0, code: 'COPPER' }, getCategoryVisualPreset('green_energy'))
  }

  // 铁
  if (nameLower.includes('铁') || nameLower.includes('iron')) {
    return buildCategoryVisualConfigFromPreset({ id: 0, code: 'IRON' }, getCategoryVisualPreset('orange_index'))
  }

  // 铝
  if (nameLower.includes('铝') || nameLower.includes('aluminum')) {
    return buildCategoryVisualConfigFromPreset({ id: 0, code: 'ALUMINUM' }, getCategoryVisualPreset('blue_ore'))
  }

  // 稀土
  if (nameLower.includes('稀土') || nameLower.includes('rare')) {
    return buildCategoryVisualConfigFromPreset({ id: 0, code: 'RARE_EARTH' }, getCategoryVisualPreset('violet_alloy'))
  }

  return DEFAULT_VISUAL
}

/**
 * 清除缓存
 */
export const clearCategoryVisualCache = () => {
  visualCacheById.clear()
  visualCacheByCode.clear()
}

/**
 * 获取产品卡片的分类样式（按ID）
 * 返回可直接用于 :style 的对象
 */
export const getCategoryCardStyle = (
  categoryId: number | undefined,
  isDarkMode: boolean = false
): Record<string, string> => {
  const visual = getCategoryVisual(categoryId, isDarkMode)

  return {
    '--category-primary': visual.primaryColor,
    '--category-secondary': visual.secondaryColor,
    '--category-surface': visual.surfaceColor || visual.secondaryColor,
    '--category-text': visual.textColor,
    '--category-border': visual.borderColor,
    '--category-glow': visual.glowColor,
    '--category-chart-line': visual.chartLineColor || visual.primaryColor,
    '--category-chart-area': visual.chartAreaColor || visual.glowColor
  }
}

/**
 * 获取产品卡片的分类样式（按编码）
 * 返回可直接用于 :style 的对象
 */
export const getCategoryCardStyleByCode = (
  categoryCode: string | undefined,
  isDarkMode: boolean = false
): Record<string, string> => {
  const visual = getCategoryVisualByCode(categoryCode, isDarkMode)

  return {
    '--category-primary': visual.primaryColor,
    '--category-secondary': visual.secondaryColor,
    '--category-surface': visual.surfaceColor || visual.secondaryColor,
    '--category-text': visual.textColor,
    '--category-border': visual.borderColor,
    '--category-glow': visual.glowColor,
    '--category-chart-line': visual.chartLineColor || visual.primaryColor,
    '--category-chart-area': visual.chartAreaColor || visual.glowColor
  }
}

/**
 * 组合式 API
 */
export function useCategoryVisual() {
  const isDarkMode = ref(false)

  // 检测深色模式（可选）
  const checkDarkMode = () => {
    isDarkMode.value = window.matchMedia('(prefers-color-scheme: dark)').matches
  }

  return {
    getCategoryVisual,
    getCategoryVisualByCode,
    parseCategoryVisualConfig,
    parseCategoryVisualByCode,
    inferVisualFromName,
    getCategoryCardStyle,
    getCategoryCardStyleByCode,
    clearCategoryVisualCache,
    registerCategoryCode,
    registerCategoryCodes,
    isDarkMode: computed(() => isDarkMode.value),
    checkDarkMode
  }
}
