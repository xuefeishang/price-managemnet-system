import type { CategoryVisualConfig, ProductCategory } from '@/types'
import { rgbaFromHex } from '@/utils/categoryVisualColor'

export type CategoryVisualPresetGroup =
  | 'ore_metal'
  | 'energy_active'
  | 'precious_index'
  | 'steel_alloy'
  | 'chemical_recycle'

export interface CategoryVisualPresetGroupMeta {
  key: CategoryVisualPresetGroup
  name: string
  description: string
}

export interface CategoryVisualPreset {
  id: string
  group: CategoryVisualPresetGroup
  version: number
  name: string
  description: string
  recommendedFor: string[]
  icon: string
  primaryColor: string
  secondaryColor: string
  textColor: string
  borderColor: string
  surfaceColor: string
  chartLineColor: string
  chartAreaColor: string
  glowColor: string
  darkMode: {
    primaryColor: string
    textColor: string
    borderColor: string
    surfaceColor: string
    glowColor: string
  }
}

export interface CategoryVisualComboRule {
  keywords: string[]
  presetId: string
  fallbackPresetId?: string
}

export interface CategoryVisualCombo {
  id: string
  version: number
  name: string
  description: string
  tone: string
  recommendedFor: string[]
  rules: CategoryVisualComboRule[]
  fallbackPresetIds: string[]
}

const createPreset = (
  preset: Omit<CategoryVisualPreset, 'version' | 'secondaryColor' | 'chartLineColor' | 'chartAreaColor' | 'glowColor' | 'darkMode'> & {
    secondaryColor?: string
    chartLineColor?: string
    chartAreaColor?: string
    glowColor?: string
    darkMode?: CategoryVisualPreset['darkMode']
  }
): CategoryVisualPreset => ({
  version: 1,
  secondaryColor: preset.secondaryColor || preset.surfaceColor,
  chartLineColor: preset.chartLineColor || preset.primaryColor,
  chartAreaColor: preset.chartAreaColor || rgbaFromHex(preset.primaryColor, 0.12),
  glowColor: preset.glowColor || rgbaFromHex(preset.primaryColor, 0.14),
  darkMode: preset.darkMode || {
    primaryColor: preset.primaryColor,
    textColor: '#F8FAFC',
    borderColor: preset.textColor,
    surfaceColor: '#0F172A',
    glowColor: rgbaFromHex(preset.primaryColor, 0.16)
  },
  ...preset
})

export const CATEGORY_VISUAL_PRESET_GROUPS: CategoryVisualPresetGroupMeta[] = [
  { key: 'ore_metal', name: '矿石金属', description: '稳定、清爽、冷静的基础矿产品识别体系。' },
  { key: 'energy_active', name: '能源活跃', description: '更强交易感与流动性，适合活跃品类。' },
  { key: 'precious_index', name: '贵金指数', description: '价值感、波动提示和高关注产品表达。' },
  { key: 'steel_alloy', name: '钢铁合金', description: '工业结构感，适合钢铁、合金和稀有金属。' },
  { key: 'chemical_recycle', name: '化工循环', description: '洁净、理性、低饱和，适合辅料与回收品。' }
]

export const CATEGORY_VISUAL_PRESETS: CategoryVisualPreset[] = [
  createPreset({
    id: 'blue_ore',
    group: 'ore_metal',
    name: '蓝晶矿脉',
    description: '清晰、稳定，适合矿石和基础金属的专业识别。',
    recommendedFor: ['铝', '矿', '铅', '锌', '铝铜矿'],
    icon: 'cube_ore',
    primaryColor: '#2563EB',
    textColor: '#1D4ED8',
    borderColor: '#BFDBFE',
    surfaceColor: '#EFF6FF',
    secondaryColor: '#DBEAFE'
  }),
  createPreset({
    id: 'slate_ore',
    group: 'ore_metal',
    name: '岩层钢蓝',
    description: '稳重冷静，适合铁矿和基础矿粉。',
    recommendedFor: ['铁矿', '矿粉', '岩', '矿石'],
    icon: 'iron_ore',
    primaryColor: '#3B6EA8',
    textColor: '#28537F',
    borderColor: '#C8DAEF',
    surfaceColor: '#F0F6FC'
  }),
  createPreset({
    id: 'aluminum_mist',
    group: 'ore_metal',
    name: '铝雾浅蓝',
    description: '轻盈、干净，适合铝、氧化铝和轻金属。',
    recommendedFor: ['铝', '氧化铝', '轻金属'],
    icon: 'aluminum_block',
    primaryColor: '#4F7FBF',
    textColor: '#355E96',
    borderColor: '#C9D9F0',
    surfaceColor: '#F3F7FD'
  }),
  createPreset({
    id: 'zinc_cloud',
    group: 'ore_metal',
    name: '锌云灰蓝',
    description: '低饱和、高可读，适合锌、铅锌和通用金属。',
    recommendedFor: ['锌', '铅锌', '铅', '通用'],
    icon: 'silver_bar',
    primaryColor: '#5B7C99',
    textColor: '#3F5F78',
    borderColor: '#CAD8E4',
    surfaceColor: '#F4F8FB'
  }),
  createPreset({
    id: 'copper_vein_blue',
    group: 'ore_metal',
    name: '铜脉湖蓝',
    description: '蓝绿之间的矿脉感，适合铜矿、铜精矿。',
    recommendedFor: ['铜矿', '铜精矿', '铜'],
    icon: 'cube_ore',
    primaryColor: '#0F7EA8',
    textColor: '#0E6688',
    borderColor: '#B8E0EE',
    surfaceColor: '#EFFBFF'
  }),
  createPreset({
    id: 'lead_silver',
    group: 'ore_metal',
    name: '铅银冷灰',
    description: '偏银灰的工业质感，适合铅、银铅矿。',
    recommendedFor: ['铅', '银铅', '冷灰'],
    icon: 'silver_bar',
    primaryColor: '#6B7F93',
    textColor: '#4B6075',
    borderColor: '#D0D9E2',
    surfaceColor: '#F6F8FA'
  }),
  createPreset({
    id: 'nickel_ice',
    group: 'ore_metal',
    name: '镍冰蓝灰',
    description: '带科技感的冷金属色，适合镍、钴和电池材料。',
    recommendedFor: ['镍', '钴', '电池', '材料'],
    icon: 'rare_element',
    primaryColor: '#487DA3',
    textColor: '#315E7E',
    borderColor: '#C8DCE8',
    surfaceColor: '#F1F8FB'
  }),
  createPreset({
    id: 'tin_mountain',
    group: 'ore_metal',
    name: '锡山蓝灰',
    description: '柔和、稳定，适合锡、锑和小金属。',
    recommendedFor: ['锡', '锑', '小金属'],
    icon: 'cube_ore',
    primaryColor: '#607A9B',
    textColor: '#435D7B',
    borderColor: '#D3DCE8',
    surfaceColor: '#F6F8FC'
  }),
  createPreset({
    id: 'moly_steelblue',
    group: 'ore_metal',
    name: '钼蓝钢影',
    description: '较深的钢蓝识别，适合钼、钨等高强度金属。',
    recommendedFor: ['钼', '钨', '高强度'],
    icon: 'rare_element',
    primaryColor: '#355C8C',
    textColor: '#27476D',
    borderColor: '#C4D3E6',
    surfaceColor: '#F2F6FB'
  }),
  createPreset({
    id: 'ore_neutral',
    group: 'ore_metal',
    name: '矿脉中性',
    description: '克制中性，可作为未分类矿产品的安全选择。',
    recommendedFor: ['矿', '其他', '默认'],
    icon: 'cube_ore',
    primaryColor: '#586B84',
    textColor: '#3F5064',
    borderColor: '#D5DCE5',
    surfaceColor: '#F8FAFC'
  }),

  createPreset({
    id: 'green_energy',
    group: 'energy_active',
    name: '绿能金属',
    description: '活跃、流通感强，适合电铜和能源金属。',
    recommendedFor: ['铜', '电铜', '能源', 'copper'],
    icon: 'bolt_metal',
    primaryColor: '#2E8B57',
    textColor: '#166534',
    borderColor: '#B7E4C7',
    surfaceColor: '#ECFDF3',
    secondaryColor: '#DDF8EA',
    chartLineColor: '#159947'
  }),
  createPreset({
    id: 'electro_mint',
    group: 'energy_active',
    name: '电解薄荷',
    description: '清新且具有电解质感，适合电铜、电解镍。',
    recommendedFor: ['电解', '电铜', '电镍'],
    icon: 'bolt_metal',
    primaryColor: '#10A37F',
    textColor: '#08715B',
    borderColor: '#B9EAD9',
    surfaceColor: '#EFFCF7'
  }),
  createPreset({
    id: 'battery_teal',
    group: 'energy_active',
    name: '电池青绿',
    description: '适合新能源材料和电池金属。',
    recommendedFor: ['电池', '锂', '新能源', '材料'],
    icon: 'bolt_metal',
    primaryColor: '#0E8F8A',
    textColor: '#0B6C69',
    borderColor: '#B9E4E1',
    surfaceColor: '#EFFBFA'
  }),
  createPreset({
    id: 'lithium_spring',
    group: 'energy_active',
    name: '锂盐春绿',
    description: '轻快、明亮，适合锂盐与新能源辅材。',
    recommendedFor: ['锂', '锂盐', '碳酸锂'],
    icon: 'rare_element',
    primaryColor: '#4F9D5D',
    textColor: '#2F753D',
    borderColor: '#CBE8CF',
    surfaceColor: '#F2FBF3'
  }),
  createPreset({
    id: 'cobalt_current',
    group: 'energy_active',
    name: '钴流蓝绿',
    description: '冷静但活跃，适合钴、镍钴材料。',
    recommendedFor: ['钴', '镍钴', '材料'],
    icon: 'bolt_metal',
    primaryColor: '#1680A1',
    textColor: '#0F637D',
    borderColor: '#BDE2EB',
    surfaceColor: '#F0FAFC'
  }),
  createPreset({
    id: 'trade_aqua',
    group: 'energy_active',
    name: '交易水青',
    description: '交易属性明显，适合活跃报价品类。',
    recommendedFor: ['交易', '报价', '活跃'],
    icon: 'cart_trade',
    primaryColor: '#1199B1',
    textColor: '#0B7588',
    borderColor: '#BCE8EF',
    surfaceColor: '#F0FCFF'
  }),
  createPreset({
    id: 'market_green',
    group: 'energy_active',
    name: '行情绿线',
    description: '适合需要强化趋势变化的行情类产品。',
    recommendedFor: ['行情', '趋势', '活跃'],
    icon: 'bar_index',
    primaryColor: '#3C8D3F',
    textColor: '#2B6B2E',
    borderColor: '#CBE4C8',
    surfaceColor: '#F3FBF1'
  }),
  createPreset({
    id: 'supply_leaf',
    group: 'energy_active',
    name: '供应叶绿',
    description: '稳中带活，适合供应链相关类别。',
    recommendedFor: ['供应', '采购', '库存'],
    icon: 'cart_trade',
    primaryColor: '#5A8F3D',
    textColor: '#416D2B',
    borderColor: '#D5E6C9',
    surfaceColor: '#F7FBF2'
  }),
  createPreset({
    id: 'active_cyan',
    group: 'energy_active',
    name: '活跃青峰',
    description: '更鲜明的青色识别，适合高频关注分类。',
    recommendedFor: ['高频', '重点', '关注'],
    icon: 'bolt_metal',
    primaryColor: '#0891B2',
    textColor: '#0E7490',
    borderColor: '#A5F3FC',
    surfaceColor: '#ECFEFF'
  }),
  createPreset({
    id: 'energy_balance',
    group: 'energy_active',
    name: '能量平衡',
    description: '低冲突绿色，适合多分类并列展示。',
    recommendedFor: ['能源', '平衡', '默认'],
    icon: 'bolt_metal',
    primaryColor: '#4B8B6F',
    textColor: '#346C54',
    borderColor: '#CBE2D7',
    surfaceColor: '#F3FAF6'
  }),

  createPreset({
    id: 'orange_index',
    group: 'precious_index',
    name: '橙色指数',
    description: '醒目但克制，适合指数、铁精粉和波动提醒。',
    recommendedFor: ['铁', '铁精粉', '指数', '波动'],
    icon: 'bar_index',
    primaryColor: '#EA580C',
    textColor: '#C2410C',
    borderColor: '#FED7AA',
    surfaceColor: '#FFF4E8',
    secondaryColor: '#FFEDD5',
    chartLineColor: '#F97316'
  }),
  createPreset({
    id: 'gold_precious',
    group: 'precious_index',
    name: '贵金暖金',
    description: '温润、高价值，适合金和贵金属。',
    recommendedFor: ['金', 'gold', '贵金属'],
    icon: 'gold_ingot',
    primaryColor: '#B7791F',
    textColor: '#92400E',
    borderColor: '#FDE68A',
    surfaceColor: '#FFF7E6',
    secondaryColor: '#FEF3C7',
    chartLineColor: '#D97706'
  }),
  createPreset({
    id: 'silver_neutral',
    group: 'precious_index',
    name: '银灰金属',
    description: '稳重、中性，适合银、通用金属和低波动品类。',
    recommendedFor: ['银', 'silver', '通用'],
    icon: 'silver_bar',
    primaryColor: '#64748B',
    textColor: '#475569',
    borderColor: '#CBD5E1',
    surfaceColor: '#F1F5F9',
    secondaryColor: '#E2E8F0'
  }),
  createPreset({
    id: 'amber_spot',
    group: 'precious_index',
    name: '琥珀现货',
    description: '温暖、清晰，适合现货指数与重点价格。',
    recommendedFor: ['现货', '重点', '指数'],
    icon: 'bar_index',
    primaryColor: '#C47A19',
    textColor: '#9A5B0D',
    borderColor: '#F4D19C',
    surfaceColor: '#FFF8ED'
  }),
  createPreset({
    id: 'platinum_gray',
    group: 'precious_index',
    name: '铂金冷灰',
    description: '高级、克制，适合铂、钯和稀贵金属。',
    recommendedFor: ['铂', '钯', '稀贵'],
    icon: 'silver_bar',
    primaryColor: '#6F7885',
    textColor: '#4F5965',
    borderColor: '#D5DAE0',
    surfaceColor: '#F8FAFC'
  }),
  createPreset({
    id: 'ruby_alert',
    group: 'precious_index',
    name: '红宝波动',
    description: '用于高波动提示，但仍保持浅底克制。',
    recommendedFor: ['波动', '预警', '风险'],
    icon: 'bar_index',
    primaryColor: '#C24155',
    textColor: '#9F2F42',
    borderColor: '#F3C7CF',
    surfaceColor: '#FFF3F5'
  }),
  createPreset({
    id: 'bronze_index',
    group: 'precious_index',
    name: '青铜指数',
    description: '偏工业价值感，适合有色指数。',
    recommendedFor: ['有色', '指数', '铜'],
    icon: 'bar_index',
    primaryColor: '#A16207',
    textColor: '#854D0E',
    borderColor: '#E9D5A6',
    surfaceColor: '#FFF9EA'
  }),
  createPreset({
    id: 'sunset_price',
    group: 'precious_index',
    name: '夕照价格',
    description: '橙红渐进感，适合重点价格概览。',
    recommendedFor: ['价格', '概览', '重点'],
    icon: 'bar_index',
    primaryColor: '#D65A31',
    textColor: '#A84221',
    borderColor: '#F7C9B8',
    surfaceColor: '#FFF5F1'
  }),
  createPreset({
    id: 'value_purple',
    group: 'precious_index',
    name: '价值紫晶',
    description: '高价值但不奢华，适合战略品类。',
    recommendedFor: ['战略', '价值', '稀有'],
    icon: 'rare_element',
    primaryColor: '#7C3AED',
    textColor: '#5B21B6',
    borderColor: '#DDD6FE',
    surfaceColor: '#F5F3FF'
  }),
  createPreset({
    id: 'index_neutral',
    group: 'precious_index',
    name: '指数中性',
    description: '适合大量指数并存时保持页面稳定。',
    recommendedFor: ['指数', '中性', '默认'],
    icon: 'bar_index',
    primaryColor: '#7A6A58',
    textColor: '#5F5143',
    borderColor: '#DED4C8',
    surfaceColor: '#FAF7F2'
  }),

  createPreset({
    id: 'violet_alloy',
    group: 'steel_alloy',
    name: '紫钢合金',
    description: '结构化、科技感，适合钢材、合金和稀有品类。',
    recommendedFor: ['钢', '合金', '稀土', '坯'],
    icon: 'alloy_grid',
    primaryColor: '#6D28D9',
    textColor: '#5B21B6',
    borderColor: '#DDD6FE',
    surfaceColor: '#F5F0FF',
    secondaryColor: '#EDE9FE',
    chartLineColor: '#7C3AED'
  }),
  createPreset({
    id: 'graphite_recycle',
    group: 'steel_alloy',
    name: '石墨循环',
    description: '低饱和工业感，适合废钢、回收料和深色矿产。',
    recommendedFor: ['废', '回收', '石墨', '煤'],
    icon: 'recycle_steel',
    primaryColor: '#475569',
    textColor: '#334155',
    borderColor: '#D1D5DB',
    surfaceColor: '#F8FAFC',
    secondaryColor: '#E5E7EB'
  }),
  createPreset({
    id: 'steel_indigo',
    group: 'steel_alloy',
    name: '钢坯靛蓝',
    description: '厚重、清晰，适合钢坯和钢材。',
    recommendedFor: ['钢坯', '钢材', '钢'],
    icon: 'alloy_grid',
    primaryColor: '#4F46A5',
    textColor: '#373084',
    borderColor: '#D6D4F0',
    surfaceColor: '#F5F4FF'
  }),
  createPreset({
    id: 'alloy_lilac',
    group: 'steel_alloy',
    name: '合金淡紫',
    description: '轻量科技感，适合合金和加工材。',
    recommendedFor: ['合金', '加工', '材料'],
    icon: 'alloy_grid',
    primaryColor: '#8B5CF6',
    textColor: '#6D28D9',
    borderColor: '#DDD6FE',
    surfaceColor: '#F7F3FF'
  }),
  createPreset({
    id: 'rare_earth',
    group: 'steel_alloy',
    name: '稀土紫灰',
    description: '稀有金属的低调科技表达。',
    recommendedFor: ['稀土', '稀有', '战略'],
    icon: 'rare_element',
    primaryColor: '#765D9A',
    textColor: '#574275',
    borderColor: '#DDD2EA',
    surfaceColor: '#F8F5FC'
  }),
  createPreset({
    id: 'tungsten_shadow',
    group: 'steel_alloy',
    name: '钨影石墨',
    description: '硬朗、低饱和，适合钨、钼等高强品类。',
    recommendedFor: ['钨', '钼', '硬质'],
    icon: 'alloy_grid',
    primaryColor: '#52616F',
    textColor: '#394855',
    borderColor: '#D3DAE0',
    surfaceColor: '#F7F9FA'
  }),
  createPreset({
    id: 'rebar_bluegray',
    group: 'steel_alloy',
    name: '螺纹蓝灰',
    description: '适合建材、螺纹钢和钢贸场景。',
    recommendedFor: ['螺纹', '建材', '钢贸'],
    icon: 'alloy_grid',
    primaryColor: '#516F8D',
    textColor: '#3A5470',
    borderColor: '#D0DCE7',
    surfaceColor: '#F5F8FB'
  }),
  createPreset({
    id: 'scrap_plum',
    group: 'steel_alloy',
    name: '废钢梅紫',
    description: '区别于普通灰色回收料，适合废钢重点类别。',
    recommendedFor: ['废钢', '回收', '再生'],
    icon: 'recycle_steel',
    primaryColor: '#7B4C7E',
    textColor: '#5E3861',
    borderColor: '#E0CEE2',
    surfaceColor: '#FAF5FB'
  }),
  createPreset({
    id: 'cold_roll',
    group: 'steel_alloy',
    name: '冷轧银蓝',
    description: '轻冷工业感，适合冷轧、板材。',
    recommendedFor: ['冷轧', '板材', '钢板'],
    icon: 'silver_bar',
    primaryColor: '#5D7D96',
    textColor: '#425F75',
    borderColor: '#D1DDE7',
    surfaceColor: '#F6F9FB'
  }),
  createPreset({
    id: 'alloy_neutral',
    group: 'steel_alloy',
    name: '合金中性',
    description: '适合钢铁合金分类较多时的安全方案。',
    recommendedFor: ['合金', '钢铁', '默认'],
    icon: 'alloy_grid',
    primaryColor: '#5F667A',
    textColor: '#454B5C',
    borderColor: '#D6D9E2',
    surfaceColor: '#F8F9FC'
  }),

  createPreset({
    id: 'cyan_chemical',
    group: 'chemical_recycle',
    name: '青蓝试剂',
    description: '清洁、理性，适合硫酸、化工辅料和试剂类。',
    recommendedFor: ['硫酸', '化工', '酸', '试剂'],
    icon: 'flask',
    primaryColor: '#0891B2',
    textColor: '#0E7490',
    borderColor: '#A5F3FC',
    surfaceColor: '#ECFEFF',
    secondaryColor: '#CFFAFE'
  }),
  createPreset({
    id: 'acid_blue',
    group: 'chemical_recycle',
    name: '酸液蓝',
    description: '适合酸类、液体辅料和化工报价。',
    recommendedFor: ['酸', '液体', '辅料'],
    icon: 'flask',
    primaryColor: '#0C83A5',
    textColor: '#0B6680',
    borderColor: '#B8E4EE',
    surfaceColor: '#F0FBFE'
  }),
  createPreset({
    id: 'reagent_mint',
    group: 'chemical_recycle',
    name: '试剂薄荷',
    description: '清洁低刺激，适合试剂、添加剂。',
    recommendedFor: ['试剂', '添加剂', '辅料'],
    icon: 'flask',
    primaryColor: '#1D9A8A',
    textColor: '#147266',
    borderColor: '#BEE8E1',
    surfaceColor: '#F0FCFA'
  }),
  createPreset({
    id: 'recycle_green',
    group: 'chemical_recycle',
    name: '循环灰绿',
    description: '环保、低饱和，适合回收和再生料。',
    recommendedFor: ['回收', '再生', '循环'],
    icon: 'recycle_steel',
    primaryColor: '#5F8B6A',
    textColor: '#456C4E',
    borderColor: '#D2E3D5',
    surfaceColor: '#F6FBF7'
  }),
  createPreset({
    id: 'sulfur_clean',
    group: 'chemical_recycle',
    name: '硫酸清青',
    description: '适合硫酸等高频化工分类。',
    recommendedFor: ['硫酸', '硫', '酸'],
    icon: 'flask',
    primaryColor: '#1397A3',
    textColor: '#0E717A',
    borderColor: '#BDE7EC',
    surfaceColor: '#F0FCFD'
  }),
  createPreset({
    id: 'solvent_gray',
    group: 'chemical_recycle',
    name: '溶剂灰蓝',
    description: '中性理性，适合溶剂和一般化工品。',
    recommendedFor: ['溶剂', '化工', '中性'],
    icon: 'flask',
    primaryColor: '#5B7C89',
    textColor: '#405F6A',
    borderColor: '#D0DEE4',
    surfaceColor: '#F6FAFC'
  }),
  createPreset({
    id: 'carbon_reuse',
    group: 'chemical_recycle',
    name: '碳素再用',
    description: '适合碳素、焦炭和再利用材料。',
    recommendedFor: ['碳', '焦炭', '再用'],
    icon: 'recycle_steel',
    primaryColor: '#58636F',
    textColor: '#424C56',
    borderColor: '#D7DCE1',
    surfaceColor: '#F8FAFB'
  }),
  createPreset({
    id: 'clean_lab',
    group: 'chemical_recycle',
    name: '清洁实验室',
    description: '轻盈实验室风格，适合质量较精细的辅料。',
    recommendedFor: ['实验', '质量', '辅料'],
    icon: 'flask',
    primaryColor: '#3E91A8',
    textColor: '#2A6F82',
    borderColor: '#C8E4EB',
    surfaceColor: '#F3FBFD'
  }),
  createPreset({
    id: 'waste_neutral',
    group: 'chemical_recycle',
    name: '回收中性',
    description: '避免回收类过度装饰，适合大量再生料。',
    recommendedFor: ['废', '回收', '再生'],
    icon: 'recycle_steel',
    primaryColor: '#68766F',
    textColor: '#4E5D55',
    borderColor: '#D8DED9',
    surfaceColor: '#F8FAF8'
  }),
  createPreset({
    id: 'chemical_balance',
    group: 'chemical_recycle',
    name: '化工平衡',
    description: '通用化工分类的安全默认方案。',
    recommendedFor: ['化工', '默认', '平衡'],
    icon: 'flask',
    primaryColor: '#4E8794',
    textColor: '#396775',
    borderColor: '#D0E3E7',
    surfaceColor: '#F5FBFC'
  })
]

export const DEFAULT_CATEGORY_PRESET_ID = 'blue_ore'

export const CATEGORY_VISUAL_COMBOS: CategoryVisualCombo[] = [
  {
    id: 'steady_mining',
    version: 1,
    name: '稳健矿业',
    description: '默认推荐组合，蓝、绿、灰为主，适合大多数矿产品价格系统。',
    tone: '清爽稳重',
    recommendedFor: ['矿石', '基础金属', '综合价格系统'],
    rules: [
      { keywords: ['金', 'gold', '贵金属'], presetId: 'gold_precious' },
      { keywords: ['银', 'silver'], presetId: 'silver_neutral' },
      { keywords: ['铜', '电铜', 'copper'], presetId: 'green_energy' },
      { keywords: ['铁精粉', '指数'], presetId: 'orange_index' },
      { keywords: ['钢', '合金'], presetId: 'violet_alloy' },
      { keywords: ['废', '回收', '再生'], presetId: 'graphite_recycle' },
      { keywords: ['硫酸', '化工', '酸'], presetId: 'cyan_chemical' },
      { keywords: ['铝', '矿', '铅', '锌'], presetId: 'blue_ore' }
    ],
    fallbackPresetIds: ['blue_ore', 'green_energy', 'silver_neutral', 'orange_index', 'violet_alloy']
  },
  {
    id: 'active_trading',
    version: 1,
    name: '活跃交易',
    description: '对比更清晰，适合波动频繁、交易属性强的分类体系。',
    tone: '活跃清晰',
    recommendedFor: ['交易', '行情', '高频报价'],
    rules: [
      { keywords: ['交易', '报价', '现货'], presetId: 'trade_aqua' },
      { keywords: ['铜', '电铜'], presetId: 'electro_mint' },
      { keywords: ['指数', '波动', '预警'], presetId: 'ruby_alert' },
      { keywords: ['锂', '电池', '新能源'], presetId: 'battery_teal' },
      { keywords: ['钢', '合金'], presetId: 'steel_indigo' },
      { keywords: ['金', '贵金属'], presetId: 'amber_spot' }
    ],
    fallbackPresetIds: ['trade_aqua', 'active_cyan', 'market_green', 'sunset_price', 'alloy_lilac']
  },
  {
    id: 'precious_focus',
    version: 1,
    name: '贵金聚焦',
    description: '金、银、琥珀和深紫形成价值感，适合高价值产品占比较高的系统。',
    tone: '价值聚焦',
    recommendedFor: ['贵金属', '指数', '高价值产品'],
    rules: [
      { keywords: ['金', 'gold'], presetId: 'gold_precious' },
      { keywords: ['银', 'silver'], presetId: 'platinum_gray' },
      { keywords: ['铂', '钯'], presetId: 'platinum_gray' },
      { keywords: ['指数', '现货'], presetId: 'amber_spot' },
      { keywords: ['稀有', '战略', '稀土'], presetId: 'value_purple' },
      { keywords: ['铜', '有色'], presetId: 'bronze_index' }
    ],
    fallbackPresetIds: ['gold_precious', 'silver_neutral', 'amber_spot', 'value_purple', 'bronze_index']
  },
  {
    id: 'industrial_alloy',
    version: 1,
    name: '工业合金',
    description: '石墨灰、靛紫、钢蓝为主，强调结构感和工业秩序。',
    tone: '工业结构',
    recommendedFor: ['钢铁', '合金', '稀有金属'],
    rules: [
      { keywords: ['钢坯', '钢材', '钢'], presetId: 'steel_indigo' },
      { keywords: ['合金'], presetId: 'alloy_lilac' },
      { keywords: ['稀土', '稀有'], presetId: 'rare_earth' },
      { keywords: ['钨', '钼'], presetId: 'tungsten_shadow' },
      { keywords: ['废钢', '回收'], presetId: 'scrap_plum' },
      { keywords: ['冷轧', '板材'], presetId: 'cold_roll' }
    ],
    fallbackPresetIds: ['violet_alloy', 'steel_indigo', 'graphite_recycle', 'cold_roll', 'alloy_neutral']
  },
  {
    id: 'clean_chemical',
    version: 1,
    name: '化工循环',
    description: '青蓝、薄荷、灰绿组合，适合化工辅料、酸类和回收材料。',
    tone: '洁净理性',
    recommendedFor: ['化工', '酸类', '回收材料'],
    rules: [
      { keywords: ['硫酸', '硫', '酸'], presetId: 'sulfur_clean' },
      { keywords: ['试剂', '添加剂'], presetId: 'reagent_mint' },
      { keywords: ['溶剂'], presetId: 'solvent_gray' },
      { keywords: ['回收', '再生', '循环'], presetId: 'recycle_green' },
      { keywords: ['碳', '焦炭'], presetId: 'carbon_reuse' },
      { keywords: ['化工'], presetId: 'cyan_chemical' }
    ],
    fallbackPresetIds: ['cyan_chemical', 'reagent_mint', 'recycle_green', 'chemical_balance', 'solvent_gray']
  }
]

export const getCategoryVisualPreset = (presetId?: string): CategoryVisualPreset => {
  return CATEGORY_VISUAL_PRESETS.find(preset => preset.id === presetId)
    || CATEGORY_VISUAL_PRESETS.find(preset => preset.id === DEFAULT_CATEGORY_PRESET_ID)
    || CATEGORY_VISUAL_PRESETS[0]
}

export const buildCategoryVisualConfigFromPreset = (
  category: Pick<ProductCategory, 'id' | 'code'>,
  preset: CategoryVisualPreset
): CategoryVisualConfig => ({
  categoryId: category.id,
  categoryCode: category.code,
  presetId: preset.id,
  presetVersion: preset.version,
  customized: false,
  primaryColor: preset.primaryColor,
  secondaryColor: preset.secondaryColor,
  textColor: preset.textColor,
  borderColor: preset.borderColor,
  surfaceColor: preset.surfaceColor,
  chartLineColor: preset.chartLineColor,
  chartAreaColor: preset.chartAreaColor,
  glowColor: preset.glowColor || rgbaFromHex(preset.primaryColor, 0.15),
  icon: preset.icon,
  iconType: 'builtin',
  darkMode: preset.darkMode
})

export const getRecommendedCategoryVisualPresets = (
  categoryName = '',
  categoryCode = ''
): CategoryVisualPreset[] => {
  const text = `${categoryName} ${categoryCode}`.toLowerCase()
  const matched = CATEGORY_VISUAL_PRESETS.filter(preset =>
    preset.recommendedFor.some(keyword => text.includes(keyword.toLowerCase()))
  )

  const fallbackIds = ['blue_ore', 'silver_neutral', 'green_energy']
  const fallback = fallbackIds.map(getCategoryVisualPreset)
  const unique = [...matched, ...fallback].filter((preset, index, list) =>
    list.findIndex(item => item.id === preset.id) === index
  )

  return unique.slice(0, 3)
}

export const getRecommendedPresetGroup = (categoryName = '', categoryCode = ''): CategoryVisualPresetGroup => {
  return getRecommendedCategoryVisualPresets(categoryName, categoryCode)[0]?.group || 'ore_metal'
}

export const getPresetByCombo = (
  combo: CategoryVisualCombo,
  categoryName = '',
  categoryCode = '',
  fallbackIndex = 0
): CategoryVisualPreset => {
  const text = `${categoryName} ${categoryCode}`.toLowerCase()
  const matchedRule = combo.rules.find(rule =>
    rule.keywords.some(keyword => text.includes(keyword.toLowerCase()))
  )

  if (matchedRule) {
    return getCategoryVisualPreset(matchedRule.presetId || matchedRule.fallbackPresetId)
  }

  const fallbackId = combo.fallbackPresetIds[fallbackIndex % combo.fallbackPresetIds.length]
  return getCategoryVisualPreset(fallbackId)
}
