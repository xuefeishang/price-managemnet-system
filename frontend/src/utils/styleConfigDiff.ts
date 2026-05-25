/**
 * 样式配置差异对比工具
 * 用于版本对比预览，展示字段级差异
 */

import type { StyleConfig } from '@/types/theme'

/**
 * 差异项
 */
export interface DiffItem {
  field: string           // 字段名
  label: string           // 中文标签
  currentValue: any       // 当前值
  targetValue: any        // 目标值
  currentDisplay: string  // 当前值显示文本
  targetDisplay: string   // 目标值显示文本
  type: 'color' | 'font' | 'text' | 'select'  // 值类型
}

export interface StyleConfigDiffOptions {
  presetNames?: Record<string, string>
}

/**
 * 字段标签映射
 */
const FIELD_LABELS: Record<string, string> = {
  systemName: '系统名称',
  priceRiseColor: '上涨色',
  priceFallColor: '下跌色',
  priceFlatColor: '持平色',
  chartPrimaryColor: '图表主色',
  chartBudgetColor: '预算色',
  headingFont: '标题字体',
  bodyFont: '正文字体',
  numberFont: '数字字体',
  logoSize: 'Logo 尺寸',
  activeColorScheme: '色彩方案',
  activeLayoutStyle: '布局方案',
  fontSizePreset: '字号预设',
  subtitleText: '登录页副标题',
  fontSizeXs: '字号 XS',
  fontSizeSm: '字号 SM',
  fontSizeBase: '字号 Base',
  fontSizeLg: '字号 LG',
  fontSizeXl: '字号 XL',
  fontSize2xl: '字号 2XL',
  fontSize3xl: '字号 3XL',
  subtitleFont: '副标题字体',
  subtitleFontWeight: '副标题字重',
  subtitleColor: '副标题颜色',
  logoSizeLogin: '登录页 Logo 尺寸',
  logoSizeNav: '导航栏 Logo 尺寸'
}

/**
 * 添加差异项
 */
function addDiff(
  diffs: DiffItem[],
  field: string,
  current: any,
  target: any,
  type: 'color' | 'font' | 'text' | 'select',
  presetNames: Record<string, string> = {}
): void {
  if (current !== target) {
    diffs.push({
      field,
      label: FIELD_LABELS[field] || field,
      currentValue: current,
      targetValue: target,
      currentDisplay: type === 'select'
        ? (presetNames[current] || current || '默认')
        : (current || '默认'),
      targetDisplay: type === 'select'
        ? (presetNames[target] || target || '默认')
        : (target || '默认'),
      type
    })
  }
}

/**
 * 构建样式配置差异列表
 */
export function buildStyleConfigDiff(
  currentConfig: StyleConfig,
  targetConfig: StyleConfig | null,
  options: StyleConfigDiffOptions = {}
): DiffItem[] {
  if (!targetConfig) return []

  const diffs: DiffItem[] = []
  const presetNames = options.presetNames || {}

  // 颜色字段
  addDiff(diffs, 'priceRiseColor', currentConfig.priceRiseColor, targetConfig.priceRiseColor, 'color')
  addDiff(diffs, 'priceFallColor', currentConfig.priceFallColor, targetConfig.priceFallColor, 'color')
  addDiff(diffs, 'priceFlatColor', currentConfig.priceFlatColor, targetConfig.priceFlatColor, 'color')
  addDiff(diffs, 'chartPrimaryColor', currentConfig.chartPrimaryColor, targetConfig.chartPrimaryColor, 'color')
  addDiff(diffs, 'chartBudgetColor', currentConfig.chartBudgetColor, targetConfig.chartBudgetColor, 'color')

  // 字体字段
  addDiff(diffs, 'headingFont', currentConfig.headingFont, targetConfig.headingFont, 'font')
  addDiff(diffs, 'bodyFont', currentConfig.bodyFont, targetConfig.bodyFont, 'font')
  addDiff(diffs, 'numberFont', currentConfig.numberFont, targetConfig.numberFont, 'font')

  // 方案字段
  addDiff(diffs, 'activeColorScheme', currentConfig.activeColorScheme, targetConfig.activeColorScheme, 'select', presetNames)
  addDiff(diffs, 'activeLayoutStyle', currentConfig.activeLayoutStyle, targetConfig.activeLayoutStyle, 'select', presetNames)
  addDiff(diffs, 'fontSizePreset', currentConfig.fontSizePreset, targetConfig.fontSizePreset, 'select', presetNames)

  // 文本字段
  addDiff(diffs, 'systemName', currentConfig.systemName, targetConfig.systemName, 'text')
  addDiff(diffs, 'logoSize', currentConfig.logoSize, targetConfig.logoSize, 'text')
  addDiff(diffs, 'subtitleText', currentConfig.subtitleText, targetConfig.subtitleText, 'text')
  addDiff(diffs, 'subtitleFont', currentConfig.subtitleFont, targetConfig.subtitleFont, 'text')
  addDiff(diffs, 'subtitleFontWeight', currentConfig.subtitleFontWeight, targetConfig.subtitleFontWeight, 'text')
  addDiff(diffs, 'subtitleColor', currentConfig.subtitleColor, targetConfig.subtitleColor, 'text')
  addDiff(diffs, 'logoSizeLogin', currentConfig.logoSizeLogin, targetConfig.logoSizeLogin, 'text')
  addDiff(diffs, 'logoSizeNav', currentConfig.logoSizeNav, targetConfig.logoSizeNav, 'text')

  // 字号字段
  addDiff(diffs, 'fontSizeXs', currentConfig.fontSizeXs, targetConfig.fontSizeXs, 'text')
  addDiff(diffs, 'fontSizeSm', currentConfig.fontSizeSm, targetConfig.fontSizeSm, 'text')
  addDiff(diffs, 'fontSizeBase', currentConfig.fontSizeBase, targetConfig.fontSizeBase, 'text')
  addDiff(diffs, 'fontSizeLg', currentConfig.fontSizeLg, targetConfig.fontSizeLg, 'text')
  addDiff(diffs, 'fontSizeXl', currentConfig.fontSizeXl, targetConfig.fontSizeXl, 'text')
  addDiff(diffs, 'fontSize2xl', currentConfig.fontSize2xl, targetConfig.fontSize2xl, 'text')
  addDiff(diffs, 'fontSize3xl', currentConfig.fontSize3xl, targetConfig.fontSize3xl, 'text')

  return diffs
}

/**
 * 解析版本快照 JSON
 */
export function parseConfigSnapshot(snapshotJson: string): StyleConfig | null {
  if (!snapshotJson) return null
  try {
    const parsed = JSON.parse(snapshotJson)
    // 兼容新旧快照格式
    return {
      systemName: parsed.systemName || '',
      priceRiseColor: parsed.priceRiseColor || '#EF4444',
      priceFallColor: parsed.priceFallColor || '#10B981',
      priceFlatColor: parsed.priceFlatColor || '#9CA3AF',
      chartPrimaryColor: parsed.chartPrimaryColor || '#0D6E6E',
      chartBudgetColor: parsed.chartBudgetColor || '#F59E0B',
      chartColors: parsed.chartColors || [],
      headingFont: parsed.headingFont || 'Newsreader',
      bodyFont: parsed.bodyFont || 'Inter',
      numberFont: parsed.numberFont || 'JetBrains Mono',
      logoUrl: parsed.logoUrl || '',
      logoSize: parsed.logoSize || 'medium',
      activeTheme: parsed.activeTheme || '',
      activeColorScheme: parsed.activeColorScheme || '',
      activeLayoutStyle: parsed.activeLayoutStyle || 'layout_top_nav',
      fontSizePreset: parsed.fontSizePreset || 'standard',
      fontSizeXs: parsed.fontSizeXs || '0.75rem',
      fontSizeSm: parsed.fontSizeSm || '0.875rem',
      fontSizeBase: parsed.fontSizeBase || '1rem',
      fontSizeLg: parsed.fontSizeLg || '1.125rem',
      fontSizeXl: parsed.fontSizeXl || '1.25rem',
      fontSize2xl: parsed.fontSize2xl || '1.5rem',
      fontSize3xl: parsed.fontSize3xl || '1.875rem',
      logoUrlLogin: parsed.logoUrlLogin || '',
      logoUrlNav: parsed.logoUrlNav || '',
      logoSizeLogin: parsed.logoSizeLogin || '',
      logoSizeNav: parsed.logoSizeNav || '',
      subtitleText: parsed.subtitleText || '价格展示与管理平台',
      subtitleFont: parsed.subtitleFont || 'body',
      subtitleFontWeight: parsed.subtitleFontWeight || '400',
      subtitleColor: parsed.subtitleColor || 'rgba(255, 255, 255, 0.75)'
    }
  } catch {
    return null
  }
}
