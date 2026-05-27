import { ref, reactive, computed } from 'vue'
import { getStyleConfig, updateStyleConfig, switchTheme as apiSwitchTheme } from '@/api/style'
import type { StyleConfig } from '@/types/theme'
import { PRESET_THEMES } from '@/types/theme'
import { resolveLayoutTokens, applyLayoutTokensToCSS } from '@/utils/layoutTokenResolver'
import { updateFavicon } from '@/utils/favicon'

const themeConfig = reactive<StyleConfig>({
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
  // 新增字段
  activeColorScheme: 'scheme_teal_classic',
  activeLayoutStyle: 'layout_top_nav',
  fontSizePreset: 'standard',
  // 字体大小默认值
  fontSizeXs: '0.8125rem',
  fontSizeSm: '0.875rem',
  fontSizeBase: '1rem',
  fontSizeLg: '1.125rem',
  fontSizeXl: '1.25rem',
  fontSize2xl: '1.5rem',
  fontSize3xl: '2rem',
  // 双Logo配置
  logoUrlLogin: '',
  logoUrlNav: '',
  logoSizeLogin: '',
  logoSizeNav: '',
  // 副标题配置
  subtitleText: '价格展示与管理平台',
  subtitleFont: 'body',
  subtitleFontWeight: '400',
  subtitleColor: 'rgba(255, 255, 255, 0.75)'
})

const isLoaded = ref(false)
const isLoading = ref(false)

// 强制重新加载主题配置（用于需要获取最新数据的场景）
const forceReloadThemeConfig = async () => {
  isLoaded.value = false
  await loadThemeConfig()
}

const applyThemeToCSS = () => {
  const root = document.documentElement

  root.style.setProperty('--price-rise-color', themeConfig.priceRiseColor)
  root.style.setProperty('--price-fall-color', themeConfig.priceFallColor)
  root.style.setProperty('--price-flat-color', themeConfig.priceFlatColor)
  root.style.setProperty('--chart-primary-color', themeConfig.chartPrimaryColor)
  root.style.setProperty('--chart-budget-color', themeConfig.chartBudgetColor)

  // 确保至少有 9 个图表配色
  const defaultColors = ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
  const colors = themeConfig.chartColors.length >= 9
    ? themeConfig.chartColors
    : [...themeConfig.chartColors, ...defaultColors.slice(themeConfig.chartColors.length)]

  colors.slice(0, 9).forEach((color, index) => {
    root.style.setProperty(`--chart-color-${index + 1}`, color)
  })

  root.style.setProperty('--font-heading', `'${themeConfig.headingFont}', Georgia, serif`)
  root.style.setProperty('--font-body', `'${themeConfig.bodyFont}', sans-serif`)
  root.style.setProperty('--font-mono', `'${themeConfig.numberFont}', monospace`)

  // 字体大小变量
  root.style.setProperty('--font-size-xs', themeConfig.fontSizeXs)
  root.style.setProperty('--font-size-sm', themeConfig.fontSizeSm)
  root.style.setProperty('--font-size-base', themeConfig.fontSizeBase)
  root.style.setProperty('--font-size-lg', themeConfig.fontSizeLg)
  root.style.setProperty('--font-size-xl', themeConfig.fontSizeXl)
  root.style.setProperty('--font-size-2xl', themeConfig.fontSize2xl)
  root.style.setProperty('--font-size-3xl', themeConfig.fontSize3xl)

  // 布局相关 CSS 变量（根据 activeLayoutStyle 设置）
  applyLayoutVariables()
  updateFavicon(themeConfig.logoUrlNav || themeConfig.logoUrl)
}

/**
 * 应用布局 CSS 变量（使用统一 resolver）
 */
const applyLayoutVariables = () => {
  const tokens = resolveLayoutTokens(themeConfig.activeLayoutStyle || 'layout_top_nav')
  applyLayoutTokensToCSS(tokens)
}

const loadThemeConfig = async () => {
  if (isLoading.value || isLoaded.value) return
  isLoading.value = true
  try {
    const response = await getStyleConfig()
    if (response.data) {
      const config = response.data as StyleConfig & { chartColors?: string | string[] }
      themeConfig.systemName = config.systemName || '价格管理系统'
      themeConfig.priceRiseColor = config.priceRiseColor || '#EF4444'
      themeConfig.priceFallColor = config.priceFallColor || '#10B981'
      themeConfig.priceFlatColor = config.priceFlatColor || '#9CA3AF'
      themeConfig.chartPrimaryColor = config.chartPrimaryColor || '#0D6E6E'
      themeConfig.chartBudgetColor = config.chartBudgetColor || '#F59E0B'
      // Handle both string (comma-separated from API) and array (local type)
      const chartColorsRaw = config.chartColors
      if (chartColorsRaw) {
        themeConfig.chartColors = typeof chartColorsRaw === 'string'
          ? chartColorsRaw.split(',')
          : chartColorsRaw
      } else {
        themeConfig.chartColors = ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
      }
      themeConfig.headingFont = config.headingFont || 'Newsreader'
      themeConfig.bodyFont = config.bodyFont || 'Inter'
      themeConfig.numberFont = config.numberFont || 'JetBrains Mono'
      themeConfig.logoUrl = config.logoUrl || ''
      themeConfig.logoSize = config.logoSize || 'medium'
      themeConfig.activeTheme = config.activeTheme || 'theme_red_green'

      // 新增字段
      themeConfig.activeColorScheme = config.activeColorScheme || 'scheme_teal_classic'
      themeConfig.activeLayoutStyle = config.activeLayoutStyle || 'layout_top_nav'
      themeConfig.fontSizePreset = config.fontSizePreset || 'standard'

      // 字体大小配置
      themeConfig.fontSizeXs = config.fontSizeXs || '0.8125rem'
      themeConfig.fontSizeSm = config.fontSizeSm || '0.875rem'
      themeConfig.fontSizeBase = config.fontSizeBase || '1rem'
      themeConfig.fontSizeLg = config.fontSizeLg || '1.125rem'
      themeConfig.fontSizeXl = config.fontSizeXl || '1.25rem'
      themeConfig.fontSize2xl = config.fontSize2xl || '1.5rem'
      themeConfig.fontSize3xl = config.fontSize3xl || '2rem'

      // 双Logo配置
      themeConfig.logoUrlLogin = config.logoUrlLogin || ''
      themeConfig.logoUrlNav = config.logoUrlNav || ''
      themeConfig.logoSizeLogin = config.logoSizeLogin || ''
      themeConfig.logoSizeNav = config.logoSizeNav || ''

      // 副标题配置
      themeConfig.subtitleText = config.subtitleText || '价格展示与管理平台'
      themeConfig.subtitleFont = config.subtitleFont || 'body'
      themeConfig.subtitleFontWeight = config.subtitleFontWeight || '400'
      themeConfig.subtitleColor = config.subtitleColor || 'rgba(255, 255, 255, 0.75)'

      applyThemeToCSS()
      isLoaded.value = true
    }
  } catch (error) {
    console.error('Failed to load theme config:', error)
  } finally {
    isLoading.value = false
  }
}

const saveThemeConfig = async (config: Partial<StyleConfig>) => {
  try {
    await updateStyleConfig(config)
    if (config.systemName !== undefined) themeConfig.systemName = config.systemName
    if (config.priceRiseColor) themeConfig.priceRiseColor = config.priceRiseColor
    if (config.priceFallColor) themeConfig.priceFallColor = config.priceFallColor
    if (config.priceFlatColor) themeConfig.priceFlatColor = config.priceFlatColor
    if (config.chartPrimaryColor) themeConfig.chartPrimaryColor = config.chartPrimaryColor
    if (config.chartBudgetColor) themeConfig.chartBudgetColor = config.chartBudgetColor
    if (config.chartColors) themeConfig.chartColors = config.chartColors
    if (config.headingFont) themeConfig.headingFont = config.headingFont
    if (config.bodyFont) themeConfig.bodyFont = config.bodyFont
    if (config.numberFont) themeConfig.numberFont = config.numberFont
    if (config.logoUrl) themeConfig.logoUrl = config.logoUrl
    if (config.logoSize) themeConfig.logoSize = config.logoSize
    if (config.activeTheme) themeConfig.activeTheme = config.activeTheme
    // 新增字段
    if (config.activeColorScheme) themeConfig.activeColorScheme = config.activeColorScheme
    if (config.activeLayoutStyle) themeConfig.activeLayoutStyle = config.activeLayoutStyle
    if (config.fontSizePreset) themeConfig.fontSizePreset = config.fontSizePreset
    // 字体大小配置
    if (config.fontSizeXs) themeConfig.fontSizeXs = config.fontSizeXs
    if (config.fontSizeSm) themeConfig.fontSizeSm = config.fontSizeSm
    if (config.fontSizeBase) themeConfig.fontSizeBase = config.fontSizeBase
    if (config.fontSizeLg) themeConfig.fontSizeLg = config.fontSizeLg
    if (config.fontSizeXl) themeConfig.fontSizeXl = config.fontSizeXl
    if (config.fontSize2xl) themeConfig.fontSize2xl = config.fontSize2xl
    if (config.fontSize3xl) themeConfig.fontSize3xl = config.fontSize3xl
    // 双Logo配置
    if (config.logoUrlLogin !== undefined) themeConfig.logoUrlLogin = config.logoUrlLogin
    if (config.logoUrlNav !== undefined) themeConfig.logoUrlNav = config.logoUrlNav
    if (config.logoSizeLogin !== undefined) themeConfig.logoSizeLogin = config.logoSizeLogin
    if (config.logoSizeNav !== undefined) themeConfig.logoSizeNav = config.logoSizeNav
    // 副标题配置
    if (config.subtitleText !== undefined) themeConfig.subtitleText = config.subtitleText
    if (config.subtitleFont !== undefined) themeConfig.subtitleFont = config.subtitleFont
    if (config.subtitleFontWeight !== undefined) themeConfig.subtitleFontWeight = config.subtitleFontWeight
    if (config.subtitleColor !== undefined) themeConfig.subtitleColor = config.subtitleColor
    applyThemeToCSS()
  } catch (error) {
    console.error('Failed to save theme config:', error)
    throw error
  }
}

const switchTheme = async (themeKey: string) => {
  try {
    await apiSwitchTheme(themeKey)
    const theme = PRESET_THEMES.find(t => t.key === themeKey)
    if (theme) {
      themeConfig.priceRiseColor = theme.colors.priceRise
      themeConfig.priceFallColor = theme.colors.priceFall
      themeConfig.priceFlatColor = theme.colors.priceFlat || '#9CA3AF'
      themeConfig.chartPrimaryColor = theme.colors.chartPrimary
      themeConfig.chartColors = theme.colors.chartColors
      themeConfig.activeTheme = themeKey
      themeConfig.activeColorScheme = themeKey
      applyThemeToCSS()
    }
  } catch (error) {
    console.error('Failed to switch theme:', error)
    throw error
  }
}

/**
 * 切换色彩方案
 */
const switchColorScheme = async (schemeKey: string) => {
  try {
    // 调用 API 切换色彩方案
    const { switchColorScheme: apiSwitchColorScheme } = await import('@/api/style')
    await apiSwitchColorScheme(schemeKey)
    themeConfig.activeColorScheme = schemeKey
    // 重新加载配置以应用新的颜色
    await forceReloadThemeConfig()
  } catch (error) {
    console.error('Failed to switch color scheme:', error)
    throw error
  }
}

/**
 * 切换布局方案
 */
const switchLayoutStyle = async (layoutKey: string) => {
  try {
    // 调用 API 切换布局方案
    const { switchLayoutStyle: apiSwitchLayoutStyle } = await import('@/api/style')
    await apiSwitchLayoutStyle(layoutKey)
    themeConfig.activeLayoutStyle = layoutKey
    applyLayoutVariables()
  } catch (error) {
    console.error('Failed to switch layout style:', error)
    throw error
  }
}

/**
 * 切换字号预设
 */
const switchFontPreset = async (presetKey: string) => {
  try {
    // 调用 API 切换字号预设
    const { switchFontPreset: apiSwitchFontPreset } = await import('@/api/style')
    await apiSwitchFontPreset(presetKey)
    themeConfig.fontSizePreset = presetKey
    // 重新加载配置以应用新的字号
    await forceReloadThemeConfig()
  } catch (error) {
    console.error('Failed to switch font preset:', error)
    throw error
  }
}

const getPriceColor = (direction: 'up' | 'down' | 'flat'): string => {
  if (direction === 'up') return themeConfig.priceRiseColor
  if (direction === 'down') return themeConfig.priceFallColor
  return themeConfig.priceFlatColor
}

const getPriceChangeClass = (change: number | null | undefined): string => {
  if (change === null || change === undefined) return ''
  if (change > 0) return 'up'
  if (change < 0) return 'down'
  return 'flat'
}

export function useTheme() {
  return {
    themeConfig: computed(() => themeConfig),
    isLoaded: computed(() => isLoaded.value),
    isLoading: computed(() => isLoading.value),
    loadThemeConfig,
    forceReloadThemeConfig,
    saveThemeConfig,
    switchTheme,
    switchColorScheme,
    switchLayoutStyle,
    switchFontPreset,
    getPriceColor,
    getPriceChangeClass,
    applyThemeToCSS
  }
}
