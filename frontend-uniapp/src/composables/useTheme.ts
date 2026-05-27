/**
 * 主题配置 composable（小程序版）
 */
import { ref } from 'vue'
import { get } from '@/api/request'

interface StyleConfig {
  systemName: string
  logoUrl: string
  logoSize: string
}

const themeConfig = ref<StyleConfig>({
  systemName: '价格管理系统',
  logoUrl: '',
  logoSize: 'medium'
})

const isLoaded = ref(false)

const loadThemeConfig = async () => {
  if (isLoaded.value) return
  try {
    const res = await get('/api/style/config')
    if (res.code === 200 && res.data) {
      let logoUrl = res.data.logoUrl || ''
      // 将相对路径转为完整URL
      const baseUrl = import.meta.env.VITE_API_BASE_URL
      if (logoUrl && !logoUrl.startsWith('http')) {
        logoUrl = baseUrl + logoUrl
      }
      themeConfig.value = {
        systemName: res.data.systemName || '价格管理系统',
        logoUrl,
        logoSize: res.data.logoSize || 'medium'
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