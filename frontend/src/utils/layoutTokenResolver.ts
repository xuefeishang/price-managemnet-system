/**
 * 布局 Token 解析器（唯一来源）
 * 用于预览和全局 CSS 变量应用
 */

import type { StyleConfig } from '@/types/theme'

export interface LayoutTokens {
  navPosition: 'top' | 'left' | 'top-minimal'
  navPositionLabel: string
  navBg: string
  navText: string
  pageBg: string
  cardBg: string
  cardRadius: string
  cardShadow: string
  cardShadowLabel: string
}

/**
 * 根据布局方案解析 token（唯一来源）
 * 预览和真实 CSS 变量使用同一 resolver
 */
export function resolveLayoutTokens(layoutStyle: string): LayoutTokens {
  // 默认值
  let navPosition: 'top' | 'left' | 'top-minimal' = 'top'
  let navPositionLabel = '顶部'
  let navBg = '#FFFFFF'
  let navText = '#1A1A1A'
  let pageBg = '#FAFAFA'
  let cardBg = '#FFFFFF'
  let cardShadow = '0 1px 3px rgba(0,0,0,0.1)'
  let cardShadowLabel = '轻'
  let cardRadius = '12px'

  switch (layoutStyle) {
    case 'layout_left_nav':
      navPosition = 'left'
      navPositionLabel = '左侧'
      break
    case 'layout_dashboard':
      navPosition = 'left'
      navPositionLabel = '左侧'
      navBg = '#1E3A5F'
      navText = '#FFFFFF'
      pageBg = '#F5F5F5'
      cardRadius = '8px'
      break
    case 'layout_minimal':
      navPosition = 'top-minimal'
      navPositionLabel = '顶部极简'
      navBg = 'transparent'
      cardShadow = '0 4px 6px rgba(0,0,0,0.1)'
      cardShadowLabel = '中'
      cardRadius = '16px'
      break
  }

  return {
    navPosition,
    navPositionLabel,
    navBg,
    navText,
    pageBg,
    cardBg,
    cardRadius,
    cardShadow,
    cardShadowLabel
  }
}

/**
 * 根据完整配置解析 token
 */
export function resolveLayoutTokensFromConfig(config: StyleConfig): LayoutTokens {
  return resolveLayoutTokens(config.activeLayoutStyle || 'layout_top_nav')
}

/**
 * 将 token 应用到 CSS 变量
 */
export function applyLayoutTokensToCSS(tokens: LayoutTokens): void {
  const root = document.documentElement
  root.style.setProperty('--app-nav-position', tokens.navPosition)
  root.style.setProperty('--app-nav-bg', tokens.navBg)
  root.style.setProperty('--app-nav-text', tokens.navText)
  root.style.setProperty('--app-page-bg', tokens.pageBg)
  root.style.setProperty('--app-card-bg', tokens.cardBg)
  root.style.setProperty('--app-card-shadow', tokens.cardShadow)
  root.style.setProperty('--app-card-radius', tokens.cardRadius)
}