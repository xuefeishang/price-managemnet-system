import { ref, computed } from 'vue'
import { getDictByCategory } from '@/composables/useDict'
import type { CategoryVisualConfig } from '@/types'

// 分类视觉配置缓存（避免重复解析JSON）
const visualCacheById = new Map<number, CategoryVisualConfig>()
const visualCacheByCode = new Map<string, CategoryVisualConfig>()

// 分类ID到Code的映射缓存
const categoryCodeMap = new Map<number, string>()

// 默认视觉配置
const DEFAULT_VISUAL: CategoryVisualConfig = {
  categoryCode: 'DEFAULT',
  primaryColor: '#165DFF',
  secondaryColor: '#3C7EFF',
  textColor: '#1D2129',
  borderColor: '#165DFF',
  glowColor: 'rgba(22, 93, 255, 0.15)',
  icon: 'default',
  iconType: 'builtin',
  darkMode: {
    primaryColor: '#3C7EFF',
    textColor: '#F5F7FA',
    borderColor: '#165DFF',
    glowColor: 'rgba(60, 126, 255, 0.2)'
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
      if (config.categoryCode === categoryCode) {
        // 存入缓存
        visualCacheByCode.set(categoryCode, config)
        return config
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
        visualCacheById.set(categoryId, config)
        return config
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
    return {
      categoryId: 0,
      primaryColor: '#D4A574',
      secondaryColor: '#C4956A',
      textColor: '#8B5A2B',
      borderColor: '#165DFF',
      glowColor: 'rgba(212, 165, 116, 0.15)',
      icon: 'gold_ingot',
      iconType: 'builtin'
    }
  }

  // 白银
  if (nameLower.includes('银') || nameLower.includes('silver')) {
    return {
      categoryId: 0,
      primaryColor: '#A8B5C4',
      secondaryColor: '#9AA8B7',
      textColor: '#6B7B8A',
      borderColor: '#165DFF',
      glowColor: 'rgba(168, 181, 196, 0.15)',
      icon: 'silver_bar',
      iconType: 'builtin'
    }
  }

  // 铜
  if (nameLower.includes('铜') || nameLower.includes('copper')) {
    return {
      categoryId: 0,
      primaryColor: '#B87333',
      secondaryColor: '#A66628',
      textColor: '#8B4513',
      borderColor: '#165DFF',
      glowColor: 'rgba(184, 115, 51, 0.15)',
      icon: 'copper_coil',
      iconType: 'builtin'
    }
  }

  // 铁
  if (nameLower.includes('铁') || nameLower.includes('iron')) {
    return {
      categoryId: 0,
      primaryColor: '#8B4513',
      secondaryColor: '#7A3D11',
      textColor: '#5C3317',
      borderColor: '#165DFF',
      glowColor: 'rgba(139, 69, 19, 0.15)',
      icon: 'iron_ore',
      iconType: 'builtin'
    }
  }

  // 铝
  if (nameLower.includes('铝') || nameLower.includes('aluminum')) {
    return {
      categoryId: 0,
      primaryColor: '#C0C0C0',
      secondaryColor: '#B0B0B0',
      textColor: '#808080',
      borderColor: '#165DFF',
      glowColor: 'rgba(192, 192, 192, 0.12)',
      icon: 'aluminum_block',
      iconType: 'builtin'
    }
  }

  // 稀土
  if (nameLower.includes('稀土') || nameLower.includes('rare')) {
    return {
      categoryId: 0,
      primaryColor: '#8B5CF6',
      secondaryColor: '#7C3AED',
      textColor: '#6D28D9',
      borderColor: '#165DFF',
      glowColor: 'rgba(139, 92, 246, 0.15)',
      icon: 'rare_element',
      iconType: 'builtin'
    }
  }

  return DEFAULT_VISUAL
}

/**
 * 清除缓存
 */
export const clearCategoryVisualCache = () => {
  visualCacheById.clear()
  visualCacheByCode.clear()
  categoryCodeMap.clear()
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
    '--category-text': visual.textColor,
    '--category-border': visual.borderColor,
    '--category-glow': visual.glowColor
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
    '--category-text': visual.textColor,
    '--category-border': visual.borderColor,
    '--category-glow': visual.glowColor
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
