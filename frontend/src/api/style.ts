import http from '@/utils/http'
import type { StyleConfig, StyleTheme, StylePreset, StyleVersion } from '@/types/theme'

export const getStyleConfig = () => {
  return http.get<StyleConfig>('/api/style/config')
}

export const getPresetThemes = () => {
  return http.get<StyleTheme[]>('/api/style/themes')
}

/**
 * 获取所有预设（色彩方案、布局方案、字号预设）
 */
export const getPresets = () => {
  return http.get<{
    colorSchemes: StylePreset[]
    layoutStyles: StylePreset[]
    fontPresets: StylePreset[]
  }>('/api/style/presets')
}

/**
 * 获取色彩方案列表
 */
export const getColorSchemes = () => {
  return http.get<StylePreset[]>('/api/style/color-schemes')
}

/**
 * 获取布局方案列表
 */
export const getLayoutStyles = () => {
  return http.get<StylePreset[]>('/api/style/layout-styles')
}

/**
 * 获取字号预设列表
 */
export const getFontPresets = () => {
  return http.get<StylePreset[]>('/api/style/font-presets')
}

export const updateStyleConfig = (config: Partial<StyleConfig>) => {
  // Convert chartColors array to comma-separated string for backend
  const payload = {
    ...config,
    chartColors: config.chartColors
      ? (Array.isArray(config.chartColors) ? config.chartColors.join(',') : config.chartColors)
      : undefined
  }
  return http.put('/api/style/config', payload)
}

export const switchTheme = (themeKey: string) => {
  return http.put(`/api/style/theme/${themeKey}`)
}

/**
 * 切换色彩方案
 */
export const switchColorScheme = (schemeKey: string) => {
  return http.put(`/api/style/color-scheme/${schemeKey}`)
}

/**
 * 切换布局方案
 */
export const switchLayoutStyle = (layoutKey: string) => {
  return http.put(`/api/style/layout-style/${layoutKey}`)
}

/**
 * 切换字号预设
 */
export const switchFontPreset = (presetKey: string) => {
  return http.put(`/api/style/font-preset/${presetKey}`)
}

export const uploadLogo = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<string>('/api/style/logo', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 上传登录页Logo
 */
export const uploadLogoLogin = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<string>('/api/style/logo/login', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 上传导航栏Logo
 */
export const uploadLogoNav = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<string>('/api/style/logo/nav', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// ==================== 版本管理 API ====================

/**
 * 获取版本列表
 */
export const getVersionList = (page: number = 0, size: number = 10) => {
  return http.get<{
    content: StyleVersion[]
    totalElements: number
    totalPages: number
    number: number
  }>('/api/style/versions', { params: { page, size } })
}

/**
 * 获取版本详情
 */
export const getVersionById = (versionId: number) => {
  return http.get<StyleVersion>(`/api/style/versions/${versionId}`)
}

/**
 * 回滚到指定版本
 */
export const rollbackToVersion = (versionId: number) => {
  return http.post(`/api/style/rollback/${versionId}`)
}
