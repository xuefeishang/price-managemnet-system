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
}

export interface StyleTheme {
  themeKey: string
  themeName: string
  description: string
  colors?: Record<string, string>
  fonts?: Record<string, string>
  isActive: boolean
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
    { value: 'Newsreader', label: 'Newsreader' },
    { value: 'Noto Serif SC', label: '思源宋体' },
    { value: 'Georgia', label: 'Georgia' },
    { value: 'Times New Roman', label: 'Times New Roman' }
  ],
  body: [
    { value: 'Inter', label: 'Inter' },
    { value: 'Noto Sans SC', label: '思源黑体' },
    { value: 'Roboto', label: 'Roboto' },
    { value: 'Microsoft YaHei', label: '微软雅黑' }
  ],
  number: [
    { value: 'JetBrains Mono', label: 'JetBrains Mono' },
    { value: 'SF Mono', label: 'SF Mono' },
    { value: 'Source Code Pro', label: 'Source Code Pro' },
    { value: 'Consolas', label: 'Consolas' }
  ]
}

export const AVAILABLE_LOGO_SIZES = [
  { value: 'small', label: '小', size: '24px' },
  { value: 'medium', label: '中', size: '36px' },
  { value: 'large', label: '大', size: '48px' },
  { value: 'xlarge', label: '特大', size: '64px' }
]
