/**
 * 主题配置 composable（小程序版）
 */
import { ref } from 'vue'
import { get } from '@/api/request'
import { getApiBaseUrl } from '@/utils/serverConfig'

interface StyleConfig {
  systemName: string
  subtitleText: string
  logoUrl: string
  logoUrlLogin: string
  logoSize: string
  logoSizeLogin: string
}

const themeConfig = ref<StyleConfig>({
  systemName: '价格管理系统',
  subtitleText: '价格展示与管理平台',
  logoUrl: '',
  logoUrlLogin: '',
  logoSize: 'medium',
  logoSizeLogin: ''
})

const isLoaded = ref(false)

const resolveAssetUrl = (url: string) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://') || url.startsWith('data:')) {
    return url
  }

  const baseUrl = getApiBaseUrl()
  return url.startsWith('/') ? `${baseUrl}${url}` : `${baseUrl}/${url}`
}

const loadThemeConfig = async () => {
  if (isLoaded.value) return
  try {
    const res = await get('/api/style/config')
    if (res.code === 200 && res.data) {
      const logoUrl = resolveAssetUrl(res.data.logoUrl || '')
      const logoUrlLogin = resolveAssetUrl(res.data.logoUrlLogin || '')

      themeConfig.value = {
        systemName: res.data.systemName || '价格管理系统',
        subtitleText: res.data.subtitleText || '价格展示与管理平台',
        logoUrl,
        logoUrlLogin,
        logoSize: res.data.logoSize || 'medium',
        logoSizeLogin: res.data.logoSizeLogin || ''
      }
      isLoaded.value = true
    }
  } catch (error) {
    console.error('加载主题配置失败:', error)
  }
}

export function useTheme() {
  return {
    themeConfig,
    isLoaded,
    loadThemeConfig
  }
}
