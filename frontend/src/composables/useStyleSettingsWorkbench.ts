/**
 * 样式设置工作台状态管理
 * 实现三层状态模型：serverConfig / draftConfig / appliedConfig
 * 核心原则：配置变化只更新预览，点击保存后才持久化并创建版本快照
 */

import { ref, computed } from 'vue'
import { getStyleConfig, updateStyleConfig, getColorSchemes, getLayoutStyles, getFontPresets } from '@/api/style'
import type { StyleConfig, StylePreset } from '@/types/theme'
import { resolveLayoutTokens, applyLayoutTokensToCSS } from '@/utils/layoutTokenResolver'
import { FONT_SIZE_PRESETS, PRESET_THEMES } from '@/types/theme'
import { updateFavicon } from '@/utils/favicon'

// 保存状态类型
export type SaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'failed'

// 工作台状态接口
export interface StyleWorkbenchState {
  serverConfig: StyleConfig | null    // 最近一次服务端确认的配置
  draftConfig: StyleConfig | null     // 当前页面正在展示和操作的配置（预览数据源）
  appliedConfig: StyleConfig | null   // 已写入 CSS 变量的配置（全局生效）
  saveStatus: SaveStatus
  lastSavedAt: string | null
  lastError: string | null
}

// 预设缓存
const colorSchemesCache = ref<StylePreset[]>([])
const layoutStylesCache = ref<StylePreset[]>([])
const fontPresetsCache = ref<StylePreset[]>([])
const presetsLoaded = ref(false)

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
  updateFavicon(config.logoUrlNav || config.logoUrl)
}

// 应用布局 CSS 变量（使用统一 resolver）
const applyLayoutVariables = (layoutStyle: string) => {
  const tokens = resolveLayoutTokens(layoutStyle)
  applyLayoutTokensToCSS(tokens)
}

/**
 * 加载预设配置缓存
 */
const loadPresets = async (): Promise<void> => {
  if (presetsLoaded.value) return

  try {
    const [colorRes, layoutRes, fontRes] = await Promise.all([
      getColorSchemes(),
      getLayoutStyles(),
      getFontPresets()
    ])
    colorSchemesCache.value = colorRes.data || []
    layoutStylesCache.value = layoutRes.data || []
    fontPresetsCache.value = fontRes.data || []
    presetsLoaded.value = true
  } catch (error) {
    console.error('Failed to load presets:', error)
  }
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
    // 并行加载配置和预设
    const [response] = await Promise.all([
      getStyleConfig(),
      loadPresets()
    ])

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
 * 仅更新草稿配置（预览响应，不保存）
 * 用于配置面板的表单变化
 */
const updateDraft = (patch: Partial<StyleConfig>): void => {
  if (!draftConfig.value) return
  Object.assign(draftConfig.value, patch)
  saveStatus.value = 'dirty'
}

/**
 * 保存配置（持久化 + 创建快照 + 全局生效）
 * 用户点击保存按钮时调用
 */
const saveConfig = async (): Promise<void> => {
  if (!draftConfig.value || saveStatus.value === 'saving') return

  saveStatus.value = 'saving'
  lastError.value = null

  try {
    await updateStyleConfig(draftConfig.value)
    serverConfig.value = clone(draftConfig.value)
    appliedConfig.value = clone(draftConfig.value)
    applyThemeToCSS(draftConfig.value)
    saveStatus.value = 'saved'
    lastSavedAt.value = new Date().toISOString()
  } catch (error) {
    console.error('Failed to save config:', error)
    saveStatus.value = 'failed'
    lastError.value = '保存失败'
    throw error
  }
}

/**
 * 放弃修改（恢复到服务端配置）
 */
const discardChanges = (): void => {
  if (!serverConfig.value) return
  draftConfig.value = clone(serverConfig.value)
  saveStatus.value = 'idle'
  lastError.value = null
}

/**
 * 应用配置变更并持久化（已废弃）
 * @deprecated 请使用 updateDraft() + saveConfig() 替代
 */
const applyAndPersist = async (
  patch: Partial<StyleConfig>
): Promise<void> => {
  if (!draftConfig.value) return

  // 更新草稿
  Object.assign(draftConfig.value, patch)
  saveStatus.value = 'dirty'

  // 立即保存
  await saveConfig()
}

/**
 * 切换色彩方案（只更新草稿，不立即保存）
 * 从预设配置中合并颜色到 draftConfig
 */
const applyColorScheme = (schemeKey: string): void => {
  if (!draftConfig.value) return

  // 从缓存中查找预设配置
  const preset = colorSchemesCache.value.find(p => p.key === schemeKey)
  if (!preset?.config) {
    // 如果缓存中没有，尝试从 PRESET_THEMES 中查找
    const theme = PRESET_THEMES.find(t => t.key === schemeKey)
    if (theme) {
      draftConfig.value.activeColorScheme = schemeKey
      draftConfig.value.priceRiseColor = theme.colors.priceRise
      draftConfig.value.priceFallColor = theme.colors.priceFall
      draftConfig.value.priceFlatColor = theme.colors.priceFlat || draftConfig.value.priceFlatColor
      draftConfig.value.chartPrimaryColor = theme.colors.chartPrimary
      draftConfig.value.chartBudgetColor = theme.colors.chartBudget
      draftConfig.value.chartColors = theme.colors.chartColors
      saveStatus.value = 'dirty'
    }
    return
  }

  const config = preset.config as Record<string, unknown>
  draftConfig.value.activeColorScheme = schemeKey

  // 合并预设颜色配置
  if (config.priceRiseColor) draftConfig.value.priceRiseColor = config.priceRiseColor as string
  if (config.priceFallColor) draftConfig.value.priceFallColor = config.priceFallColor as string
  if (config.priceFlatColor) draftConfig.value.priceFlatColor = config.priceFlatColor as string
  if (config.chartPrimaryColor) draftConfig.value.chartPrimaryColor = config.chartPrimaryColor as string
  if (config.chartBudgetColor) draftConfig.value.chartBudgetColor = config.chartBudgetColor as string
  if (config.chartColors) {
    draftConfig.value.chartColors = Array.isArray(config.chartColors)
      ? config.chartColors as string[]
      : (config.chartColors as string).split(',')
  }

  saveStatus.value = 'dirty'
}

/**
 * 切换布局方案（只更新草稿，不立即保存）
 */
const applyLayoutStyle = (layoutKey: string): void => {
  if (!draftConfig.value) return

  draftConfig.value.activeLayoutStyle = layoutKey
  saveStatus.value = 'dirty'
}

/**
 * 切换字号预设（只更新草稿，不立即保存）
 * 从预设配置中合并字号到 draftConfig
 */
const applyFontPreset = (presetKey: string): void => {
  if (!draftConfig.value) return

  // 从缓存中查找预设配置
  const preset = fontPresetsCache.value.find(p => p.key === presetKey)
  if (preset?.config) {
    const config = preset.config as Record<string, unknown>
    draftConfig.value.fontSizePreset = presetKey
    const sizes = {
      xs: config.fontSizeXs || config.xs,
      sm: config.fontSizeSm || config.sm,
      base: config.fontSizeBase || config.base,
      lg: config.fontSizeLg || config.lg,
      xl: config.fontSizeXl || config.xl,
      '2xl': config.fontSize2xl || config['2xl'],
      '3xl': config.fontSize3xl || config['3xl']
    }
    if (sizes.xs) draftConfig.value.fontSizeXs = sizes.xs as string
    if (sizes.sm) draftConfig.value.fontSizeSm = sizes.sm as string
    if (sizes.base) draftConfig.value.fontSizeBase = sizes.base as string
    if (sizes.lg) draftConfig.value.fontSizeLg = sizes.lg as string
    if (sizes.xl) draftConfig.value.fontSizeXl = sizes.xl as string
    if (sizes['2xl']) draftConfig.value.fontSize2xl = sizes['2xl'] as string
    if (sizes['3xl']) draftConfig.value.fontSize3xl = sizes['3xl'] as string
    saveStatus.value = 'dirty'
    return
  }

  // 从 FONT_SIZE_PRESETS 中查找
  const fontPreset = FONT_SIZE_PRESETS.find(p => p.key === presetKey)
  if (fontPreset) {
    draftConfig.value.fontSizePreset = presetKey
    draftConfig.value.fontSizeXs = fontPreset.sizes.xs
    draftConfig.value.fontSizeSm = fontPreset.sizes.sm
    draftConfig.value.fontSizeBase = fontPreset.sizes.base
    draftConfig.value.fontSizeLg = fontPreset.sizes.lg
    draftConfig.value.fontSizeXl = fontPreset.sizes.xl
    draftConfig.value.fontSize2xl = fontPreset.sizes['2xl']
    draftConfig.value.fontSize3xl = fontPreset.sizes['3xl']
    saveStatus.value = 'dirty'
  }
}

/**
 * 恢复默认配置（只更新草稿，不立即保存）
 */
const resetToDefault = (): void => {
  draftConfig.value = clone(DEFAULT_CONFIG)
  saveStatus.value = 'dirty'
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
 * 计算属性：登录页Logo URL
 */
const logoUrlLogin = computed(() => draftConfig.value?.logoUrlLogin || '')

/**
 * 计算属性：导航栏Logo URL
 */
const logoUrlNav = computed(() => draftConfig.value?.logoUrlNav || '')

/**
 * 计算属性：登录页Logo尺寸
 */
const logoSizeLogin = computed(() => draftConfig.value?.logoSizeLogin || '')

/**
 * 计算属性：导航栏Logo尺寸
 */
const logoSizeNav = computed(() => draftConfig.value?.logoSizeNav || '')

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

    // 预设缓存
    colorSchemes: computed(() => colorSchemesCache.value),
    layoutStyles: computed(() => layoutStylesCache.value),
    fontPresets: computed(() => fontPresetsCache.value),

    // 计算属性（便捷访问）
    activeColorSchemeKey,
    activeLayoutStyleKey,
    activeFontPresetKey,
    currentSystemName,
    logoUrl,
    logoSize,
    logoUrlLogin,
    logoUrlNav,
    logoSizeLogin,
    logoSizeNav,

    // 方法
    loadWorkbenchConfig,
    updateDraft,
    saveConfig,
    discardChanges,
    applyAndPersist, // @deprecated - 请使用 updateDraft() + saveConfig()
    applyColorScheme,
    applyLayoutStyle,
    applyFontPreset,
    resetToDefault,
    clearError,
    applyThemeToCSS
  }
}
