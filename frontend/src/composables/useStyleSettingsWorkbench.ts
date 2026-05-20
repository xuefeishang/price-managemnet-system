/**
 * 样式设置工作台状态管理
 * 实现三层状态模型：serverConfig / draftConfig / appliedConfig
 * 核心原则：选择即生效，后台自动保存，失败自动回滚
 */

import { ref, computed } from 'vue'
import { getStyleConfig, updateStyleConfig, switchColorScheme, switchLayoutStyle, switchFontPreset } from '@/api/style'
import type { StyleConfig } from '@/types/theme'

// 保存状态类型
export type SaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'failed'

// 工作台状态接口
export interface StyleWorkbenchState {
  serverConfig: StyleConfig | null    // 最近一次服务端确认的配置
  draftConfig: StyleConfig | null     // 当前页面正在展示和操作的配置
  appliedConfig: StyleConfig | null   // 已写入 CSS 变量的配置
  saveStatus: SaveStatus
  lastSavedAt: string | null
  lastError: string | null
}

// 默认配置
const DEFAULT_CONFIG: StyleConfig = {
  systemName: '价格管理系统',
  priceRiseColor: '#EF4444',
  priceFallColor: '#10B981',
  priceFlatColor: '#9CA3AF',
  chartPrimaryColor: '#0D6E6E',
  chartBudgetColor: '#F59E0B',
  chartColors: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B'],
  headingFont: 'Newsreader',
  bodyFont: 'Inter',
  numberFont: 'JetBrains Mono',
  logoUrl: '',
  logoSize: 'medium',
  activeTheme: 'theme_red_green',
  activeColorScheme: '',
  activeLayoutStyle: 'layout_top_nav',
  fontSizePreset: 'standard',
  fontSizeXs: '0.75rem',
  fontSizeSm: '0.875rem',
  fontSizeBase: '1rem',
  fontSizeLg: '1.125rem',
  fontSizeXl: '1.25rem',
  fontSize2xl: '1.5rem',
  fontSize3xl: '1.875rem'
}

// 全局状态
const serverConfig = ref<StyleConfig | null>(null)
const draftConfig = ref<StyleConfig | null>(null)
const appliedConfig = ref<StyleConfig | null>(null)
const saveStatus = ref<SaveStatus>('idle')
const lastSavedAt = ref<string | null>(null)
const lastError = ref<string | null>(null)
const isLoading = ref(false)

// 深拷贝
const clone = <T>(obj: T): T => JSON.parse(JSON.stringify(obj))

// 将配置应用到 CSS 变量
const applyThemeToCSS = (config: StyleConfig) => {
  const root = document.documentElement

  // 价格颜色
  root.style.setProperty('--price-rise-color', config.priceRiseColor)
  root.style.setProperty('--price-fall-color', config.priceFallColor)
  root.style.setProperty('--price-flat-color', config.priceFlatColor)

  // 图表颜色
  root.style.setProperty('--chart-primary-color', config.chartPrimaryColor)
  root.style.setProperty('--chart-budget-color', config.chartBudgetColor)

  // 图表配色数组
  const defaultColors = DEFAULT_CONFIG.chartColors
  const colors = config.chartColors?.length >= 9
    ? config.chartColors
    : [...(config.chartColors || []), ...defaultColors.slice(config.chartColors?.length || 0)]

  colors.slice(0, 9).forEach((color, index) => {
    root.style.setProperty(`--chart-color-${index + 1}`, color)
  })

  // 字体
  root.style.setProperty('--font-heading', `'${config.headingFont}', Georgia, serif`)
  root.style.setProperty('--font-body', `'${config.bodyFont}', sans-serif`)
  root.style.setProperty('--font-mono', `'${config.numberFont}', monospace`)

  // 字体大小
  root.style.setProperty('--font-size-xs', config.fontSizeXs)
  root.style.setProperty('--font-size-sm', config.fontSizeSm)
  root.style.setProperty('--font-size-base', config.fontSizeBase)
  root.style.setProperty('--font-size-lg', config.fontSizeLg)
  root.style.setProperty('--font-size-xl', config.fontSizeXl)
  root.style.setProperty('--font-size-2xl', config.fontSize2xl)
  root.style.setProperty('--font-size-3xl', config.fontSize3xl)

  // 布局变量
  applyLayoutVariables(config.activeLayoutStyle || 'layout_top_nav')
}

// 应用布局 CSS 变量
const applyLayoutVariables = (layoutStyle: string) => {
  const root = document.documentElement

  let navPosition = 'top'
  let navBgColor = '#FFFFFF'
  let navTextColor = '#1A1A1A'
  let pageBgColor = '#FAFAFA'
  let cardBgColor = '#FFFFFF'
  let cardShadow = '0 1px 3px rgba(0,0,0,0.1)'
  let borderRadius = '12px'

  switch (layoutStyle) {
    case 'layout_left_nav':
      navPosition = 'left'
      break
    case 'layout_dashboard':
      navPosition = 'left'
      navBgColor = '#1E3A5F'
      navTextColor = '#FFFFFF'
      pageBgColor = '#F5F5F5'
      borderRadius = '8px'
      break
    case 'layout_minimal':
      navPosition = 'top-minimal'
      navBgColor = 'transparent'
      cardShadow = '0 4px 6px rgba(0,0,0,0.1)'
      borderRadius = '16px'
      break
  }

  root.style.setProperty('--app-nav-position', navPosition)
  root.style.setProperty('--app-nav-bg', navBgColor)
  root.style.setProperty('--app-nav-text', navTextColor)
  root.style.setProperty('--app-page-bg', pageBgColor)
  root.style.setProperty('--app-card-bg', cardBgColor)
  root.style.setProperty('--app-card-shadow', cardShadow)
  root.style.setProperty('--app-card-radius', borderRadius)
}

/**
 * 从服务端加载配置
 * 这是页面回显的唯一数据源
 */
const loadWorkbenchConfig = async (_forceRefresh: boolean = false): Promise<void> => {
  // 强制刷新时跳过 isLoading 检查
  if (!_forceRefresh && isLoading.value) return
  isLoading.value = true

  try {
    const response = await getStyleConfig()
    if (response.data) {
      const config = response.data as StyleConfig & { chartColors?: string | string[] }

      // 处理 chartColors（可能是逗号分隔的字符串）
      const chartColors = config.chartColors
        ? (typeof config.chartColors === 'string' ? config.chartColors.split(',') : config.chartColors)
        : DEFAULT_CONFIG.chartColors

      const normalizedConfig: StyleConfig = {
        ...DEFAULT_CONFIG,
        ...config,
        chartColors
      }

      serverConfig.value = clone(normalizedConfig)
      draftConfig.value = clone(normalizedConfig)
      appliedConfig.value = clone(normalizedConfig)
      applyThemeToCSS(normalizedConfig)
      saveStatus.value = 'idle'
      lastError.value = null
    }
  } catch (error) {
    console.error('Failed to load workbench config:', error)
    lastError.value = '加载配置失败'
  } finally {
    isLoading.value = false
  }
}

/**
 * 应用配置变更并持久化
 * 核心方法：选择即生效，后台自动保存
 */
const applyAndPersist = async (
  patch: Partial<StyleConfig>
): Promise<void> => {
  if (!draftConfig.value) return

  const previous = clone(draftConfig.value)

  // 1. 更新 draftConfig
  Object.assign(draftConfig.value, patch)

  // 2. 立即应用到 CSS 变量
  applyThemeToCSS(draftConfig.value)
  appliedConfig.value = clone(draftConfig.value)

  // 3. 标记为脏状态
  saveStatus.value = 'dirty'

  // 4. 保存到服务端
  saveStatus.value = 'saving'

  try {
    await updateStyleConfig(draftConfig.value)
    serverConfig.value = clone(draftConfig.value)
    saveStatus.value = 'saved'
    lastSavedAt.value = new Date().toISOString()
    lastError.value = null
  } catch (error) {
    console.error('Failed to save config:', error)

    // 回滚到服务端配置
    draftConfig.value = previous
    applyThemeToCSS(previous)
    appliedConfig.value = clone(previous)
    saveStatus.value = 'failed'
    lastError.value = '保存失败，已恢复'
  }
}

/**
 * 切换色彩方案（立即生效）
 */
const applyColorScheme = async (schemeKey: string): Promise<void> => {
  if (!draftConfig.value) return

  const previousKey = draftConfig.value.activeColorScheme

  try {
    await switchColorScheme(schemeKey)
    draftConfig.value.activeColorScheme = schemeKey

    // 重新加载配置以获取新颜色
    await loadWorkbenchConfig(true)
    saveStatus.value = 'saved'
    lastSavedAt.value = new Date().toISOString()
  } catch (error) {
    console.error('Failed to switch color scheme:', error)
    draftConfig.value.activeColorScheme = previousKey
    saveStatus.value = 'failed'
    lastError.value = '切换色彩方案失败'
    throw error
  }
}

/**
 * 切换布局方案（立即生效）
 */
const applyLayoutStyle = async (layoutKey: string): Promise<void> => {
  if (!draftConfig.value) return

  const previousKey = draftConfig.value.activeLayoutStyle

  try {
    await switchLayoutStyle(layoutKey)
    draftConfig.value.activeLayoutStyle = layoutKey
    applyLayoutVariables(layoutKey)
    appliedConfig.value = clone(draftConfig.value)
    saveStatus.value = 'saved'
    lastSavedAt.value = new Date().toISOString()
  } catch (error) {
    console.error('Failed to switch layout style:', error)
    draftConfig.value.activeLayoutStyle = previousKey
    applyLayoutVariables(previousKey || 'layout_top_nav')
    saveStatus.value = 'failed'
    lastError.value = '切换布局方案失败'
    throw error
  }
}

/**
 * 切换字号预设（立即生效）
 */
const applyFontPreset = async (presetKey: string): Promise<void> => {
  if (!draftConfig.value) return

  const previousKey = draftConfig.value.fontSizePreset

  try {
    await switchFontPreset(presetKey)
    await loadWorkbenchConfig(true)
    saveStatus.value = 'saved'
    lastSavedAt.value = new Date().toISOString()
  } catch (error) {
    console.error('Failed to switch font preset:', error)
    draftConfig.value.fontSizePreset = previousKey
    saveStatus.value = 'failed'
    lastError.value = '切换字号预设失败'
    throw error
  }
}

/**
 * 恢复默认配置
 */
const resetToDefault = async (): Promise<void> => {
  await applyAndPersist(clone(DEFAULT_CONFIG))
}

/**
 * 清除错误状态
 */
const clearError = () => {
  lastError.value = null
  if (saveStatus.value === 'failed') {
    saveStatus.value = 'idle'
  }
}

/**
 * 计算属性：当前激活的色彩方案
 */
const activeColorSchemeKey = computed(() => draftConfig.value?.activeColorScheme || '')

/**
 * 计算属性：当前激活的布局方案
 */
const activeLayoutStyleKey = computed(() => draftConfig.value?.activeLayoutStyle || '')

/**
 * 计算属性：当前激活的字号预设
 */
const activeFontPresetKey = computed(() => draftConfig.value?.fontSizePreset || 'standard')

/**
 * 计算属性：当前系统名称
 */
const currentSystemName = computed(() => draftConfig.value?.systemName || '价格管理系统')

/**
 * 计算属性：Logo URL
 */
const logoUrl = computed(() => draftConfig.value?.logoUrl || '')

/**
 * 计算属性：Logo 尺寸
 */
const logoSize = computed(() => draftConfig.value?.logoSize || 'medium')

/**
 * 计算属性：是否已加载
 */
const isLoaded = computed(() => serverConfig.value !== null)

/**
 * 组合式 API
 */
export function useStyleSettingsWorkbench() {
  return {
    // 状态
    serverConfig: computed(() => serverConfig.value),
    draftConfig: computed(() => draftConfig.value),
    appliedConfig: computed(() => appliedConfig.value),
    saveStatus: computed(() => saveStatus.value),
    lastSavedAt: computed(() => lastSavedAt.value),
    lastError: computed(() => lastError.value),
    isLoading: computed(() => isLoading.value),
    isLoaded,

    // 计算属性（便捷访问）
    activeColorSchemeKey,
    activeLayoutStyleKey,
    activeFontPresetKey,
    currentSystemName,
    logoUrl,
    logoSize,

    // 方法
    loadWorkbenchConfig,
    applyAndPersist,
    applyColorScheme,
    applyLayoutStyle,
    applyFontPreset,
    resetToDefault,
    clearError,
    applyThemeToCSS
  }
}
