/**
 * 样式预览区块配置
 * 定义配置域与预览区块的映射关系
 */

export type StyleSection =
  | 'overview'
  | 'brand'
  | 'color'
  | 'typography'
  | 'layout'
  | 'home'
  | 'home-sort'
  | 'category'
  | 'version'

export type StylePreviewBlock =
  | 'systemOverview'
  | 'brandNav'
  | 'logoSize'
  | 'priceColorScenario'
  | 'chartPalette'
  | 'fontHierarchy'
  | 'tableTypography'
  | 'layoutMiniature'
  | 'cardSurface'
  | 'homeViewport'
  | 'categoryCard'
  | 'versionCompare'

/**
 * 配置域与预览区块映射
 * 每个配置域对应 1-2 个核心预览区块
 */
export const PREVIEW_BLOCKS_BY_SECTION: Record<StyleSection, StylePreviewBlock[]> = {
  overview: ['systemOverview'],
  brand: ['brandNav', 'logoSize'],
  color: ['priceColorScenario', 'chartPalette'],
  typography: ['fontHierarchy', 'tableTypography'],
  layout: ['layoutMiniature', 'cardSurface'],
  home: ['homeViewport'],
  'home-sort': ['homeViewport'],
  category: ['categoryCard'],
  version: ['versionCompare']
}

/**
 * 检查是否为有效的配置域
 */
export const isStyleSection = (value: string): value is StyleSection => {
  return value in PREVIEW_BLOCKS_BY_SECTION
}

/**
 * 获取配置域对应的预览区块列表
 */
export const getPreviewBlocksBySection = (section: string): StylePreviewBlock[] => {
  if (isStyleSection(section)) {
    return PREVIEW_BLOCKS_BY_SECTION[section]
  }
  return PREVIEW_BLOCKS_BY_SECTION.overview
}
