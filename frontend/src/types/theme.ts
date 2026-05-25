export interface StyleConfig {
  systemName?: string
  priceRiseColor: string
  priceFallColor: string
  priceFlatColor: string
  chartPrimaryColor: string
  chartBudgetColor: string
  chartColors: string[]
  headingFont: string
  bodyFont: string
  numberFont: string
  logoUrl: string
  logoSize: string
  activeTheme: string
  // 新增字段
  activeColorScheme?: string
  activeLayoutStyle?: string
  fontSizePreset?: string
  // 字体大小配置
  fontSizeXs: string
  fontSizeSm: string
  fontSizeBase: string
  fontSizeLg: string
  fontSizeXl: string
  fontSize2xl: string
  fontSize3xl: string
  // 双Logo配置
  logoUrlLogin?: string
  logoUrlNav?: string
  logoSizeLogin?: string
  logoSizeNav?: string
  // 副标题配置
  subtitleText?: string       // 登录页副标题文案
  subtitleFont?: string       // heading/body
  subtitleFontWeight?: string // 400/500/600
  subtitleColor?: string      // 颜色值
}

export interface StyleTheme {
  themeKey: string
  themeName: string
  description: string
  colors?: Record<string, string>
  fonts?: Record<string, string>
  isActive: boolean
}

/**
 * 样式预设接口
 */
export interface StylePreset {
  key: string
  name: string
  description?: string
  active?: boolean
  isDefault?: boolean
  sortOrder?: number
  config?: Record<string, unknown>
}

export interface PresetTheme {
  key: string
  name: string
  description: string
  colors: {
    priceRise: string
    priceFall: string
    priceFlat?: string
    chartPrimary: string
    chartBudget: string
    chartColors: string[]
  }
}

export const PRESET_THEMES: PresetTheme[] = [
  {
    key: 'theme_red_green',
    name: '红涨绿跌',
    description: '传统配色，涨价显示红色，跌价显示绿色',
    colors: {
      priceRise: '#EF4444',
      priceFall: '#10B981',
      chartPrimary: '#0D6E6E',
      chartBudget: '#F59E0B',
      chartColors: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }
  },
  {
    key: 'theme_green_red',
    name: '绿涨红跌',
    description: '美股风格配色，涨价显示绿色，跌价显示红色',
    colors: {
      priceRise: '#10B981',
      priceFall: '#EF4444',
      chartPrimary: '#0D6E6E',
      chartBudget: '#F59E0B',
      chartColors: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }
  },
  {
    key: 'theme_blue_orange',
    name: '蓝涨橙跌',
    description: '商务风格配色',
    colors: {
      priceRise: '#3B82F6',
      priceFall: '#F97316',
      chartPrimary: '#0D6E6E',
      chartBudget: '#F59E0B',
      chartColors: ['#0D6E6E', '#3B82F6', '#F97316', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B', '#10B981']
    }
  },
  {
    key: 'theme_purple_gold',
    name: '紫涨金跌',
    description: '高贵风格配色',
    colors: {
      priceRise: '#8B5CF6',
      priceFall: '#EAB308',
      chartPrimary: '#8B5CF6',
      chartBudget: '#F59E0B',
      chartColors: ['#8B5CF6', '#EAB308', '#0D6E6E', '#EC4899', '#6366F1', '#14B8A6', '#64748B', '#10B981', '#F59E0B']
    }
  }
]

export const AVAILABLE_FONTS = {
  heading: [
    { value: 'Microsoft YaHei', label: '微软雅黑' },
    { value: 'SimSun', label: '宋体' },
    { value: 'SimHei', label: '黑体' },
    { value: 'KaiTi', label: '楷体' },
    { value: 'Noto Serif SC', label: '思源宋体' },
    { value: 'Noto Sans SC', label: '思源黑体' },
    { value: 'Newsreader', label: 'Newsreader' },
    { value: 'Georgia', label: 'Georgia' },
    { value: 'Times New Roman', label: 'Times New Roman' }
  ],
  body: [
    { value: 'Microsoft YaHei', label: '微软雅黑' },
    { value: 'SimSun', label: '宋体' },
    { value: 'SimHei', label: '黑体' },
    { value: 'Noto Sans SC', label: '思源黑体' },
    { value: 'Inter', label: 'Inter' },
    { value: 'Roboto', label: 'Roboto' },
    { value: 'PingFang SC', label: '苹方' }
  ],
  number: [
    { value: 'JetBrains Mono', label: 'JetBrains Mono' },
    { value: 'SF Mono', label: 'SF Mono' },
    { value: 'Source Code Pro', label: 'Source Code Pro' },
    { value: 'Consolas', label: 'Consolas' },
    { value: 'Monaco', label: 'Monaco' }
  ]
}

export const AVAILABLE_LOGO_SIZES = [
  { value: 'small', label: '小', size: '24px' },
  { value: 'medium', label: '中', size: '36px' },
  { value: 'large', label: '大', size: '48px' },
  { value: 'xlarge', label: '特大', size: '64px' }
]

// 字体大小预设方案
export interface FontSizePreset {
  key: string
  name: string
  description: string
  wcagCompliant: boolean
  sizes: {
    xs: string
    sm: string
    base: string
    lg: string
    xl: string
    '2xl': string
    '3xl': string
  }
}

export const FONT_SIZE_PRESETS: FontSizePreset[] = [
  {
    key: 'compact',
    name: '紧凑',
    description: '高密度但保持可读',
    wcagCompliant: true,
    sizes: {
      xs: '0.75rem',
      sm: '0.8125rem',
      base: '0.9375rem',
      lg: '1rem',
      xl: '1.125rem',
      '2xl': '1.375rem',
      '3xl': '1.75rem'
    }
  },
  {
    key: 'standard',
    name: '标准',
    description: '通用场景',
    wcagCompliant: true,
    sizes: {
      xs: '0.75rem',
      sm: '0.875rem',
      base: '1rem',
      lg: '1.125rem',
      xl: '1.25rem',
      '2xl': '1.5rem',
      '3xl': '2rem'
    }
  },
  {
    key: 'large',
    name: '大字体',
    description: '阅读友好',
    wcagCompliant: true,
    sizes: {
      xs: '0.875rem',
      sm: '1rem',
      base: '1.125rem',
      lg: '1.25rem',
      xl: '1.5rem',
      '2xl': '1.875rem',
      '3xl': '2.375rem'
    }
  },
  {
    key: 'xlarge',
    name: '特大字体',
    description: '演示/投影/无障碍',
    wcagCompliant: true,
    sizes: {
      xs: '1rem',
      sm: '1.125rem',
      base: '1.25rem',
      lg: '1.5rem',
      xl: '1.75rem',
      '2xl': '2.25rem',
      '3xl': '2.75rem'
    }
  }
]

// 字体大小字段定义
export const FONT_SIZE_FIELDS = [
  { key: 'fontSizeXs', label: '辅助信息', default: '0.8125rem' },
  { key: 'fontSizeSm', label: '表格内容', default: '0.875rem' },
  { key: 'fontSizeBase', label: '正文表头', default: '1rem' },
  { key: 'fontSizeLg', label: '小节标题', default: '1.125rem' },
  { key: 'fontSizeXl', label: '页面副标题', default: '1.25rem' },
  { key: 'fontSize2xl', label: '页面主标题', default: '1.5rem' },
  { key: 'fontSize3xl', label: '特大标题', default: '2rem' }
]

// ==================== 版本管理类型 ====================

/**
 * 样式版本
 */
export interface StyleVersion {
  id: number
  versionNo: string
  configSnapshot: string
  changeSummary?: string
  changedBy?: number
  changedByName?: string
  createdTime: string
}

/**
 * 版本列表分页响应
 */
export interface StyleVersionPage {
  content: StyleVersion[]
  totalElements: number
  totalPages: number
  number: number
}
