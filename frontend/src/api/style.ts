import http from '@/utils/http'
import type { StyleConfig, StyleTheme } from '@/types/theme'

export const getStyleConfig = () => {
  return http.get<StyleConfig>('/api/style/config')
}

export const getPresetThemes = () => {
  return http.get<StyleTheme[]>('/api/style/themes')
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

export const uploadLogo = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<string>('/api/style/logo', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
