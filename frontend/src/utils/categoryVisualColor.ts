export interface CategoryVisualColorCheck {
  valid: boolean
  warnings: string[]
}

const expandHex = (hex: string): string => {
  const value = hex.replace('#', '').trim()
  if (value.length === 3) {
    return value.split('').map(char => char + char).join('')
  }
  return value.padEnd(6, '0').slice(0, 6)
}

export const hexToRgb = (hex: string): [number, number, number] => {
  const normalized = expandHex(hex)
  return [
    parseInt(normalized.slice(0, 2), 16),
    parseInt(normalized.slice(2, 4), 16),
    parseInt(normalized.slice(4, 6), 16)
  ]
}

const luminanceChannel = (value: number): number => {
  const normalized = value / 255
  return normalized <= 0.03928
    ? normalized / 12.92
    : Math.pow((normalized + 0.055) / 1.055, 2.4)
}

export const getContrastRatio = (foreground: string, background: string): number => {
  const [fr, fg, fb] = hexToRgb(foreground)
  const [br, bg, bb] = hexToRgb(background)
  const foregroundLuminance = 0.2126 * luminanceChannel(fr) + 0.7152 * luminanceChannel(fg) + 0.0722 * luminanceChannel(fb)
  const backgroundLuminance = 0.2126 * luminanceChannel(br) + 0.7152 * luminanceChannel(bg) + 0.0722 * luminanceChannel(bb)
  const lighter = Math.max(foregroundLuminance, backgroundLuminance)
  const darker = Math.min(foregroundLuminance, backgroundLuminance)
  return (lighter + 0.05) / (darker + 0.05)
}

export const getColorDistance = (colorA: string, colorB: string): number => {
  const [ar, ag, ab] = hexToRgb(colorA)
  const [br, bg, bb] = hexToRgb(colorB)
  return Math.sqrt(Math.pow(ar - br, 2) + Math.pow(ag - bg, 2) + Math.pow(ab - bb, 2))
}

export const rgbaFromHex = (hex: string, alpha: number): string => {
  const [r, g, b] = hexToRgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${alpha.toFixed(2)})`
}

export const checkCategoryVisualColors = (config: {
  primaryColor: string
  textColor: string
  surfaceColor?: string
  glowColor?: string
}, themePrimaryColor = '#0D6E6E'): CategoryVisualColorCheck => {
  const warnings: string[] = []
  const surfaceColor = config.surfaceColor || '#FFFFFF'

  if (getContrastRatio(config.textColor, surfaceColor) < 4.5) {
    warnings.push('文本色与浅底色对比度不足 4.5:1')
  }

  if (getContrastRatio(config.primaryColor, '#FFFFFF') < 3) {
    warnings.push('主色在白色卡片上识别度不足 3:1')
  }

  if (getColorDistance(config.primaryColor, themePrimaryColor) < 32) {
    warnings.push('分类主色与全局主题色过近，建议更换方案或降低边框强调')
  }

  const alphaMatch = config.glowColor?.match(/rgba\([^,]+,[^,]+,[^,]+,\s*([\d.]+)\)/)
  const alpha = alphaMatch ? Number(alphaMatch[1]) : 0.15
  if (alpha < 0.12 || alpha > 0.18) {
    warnings.push('光晕透明度建议控制在 12%-18%')
  }

  return {
    valid: warnings.length === 0,
    warnings
  }
}
