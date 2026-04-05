import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getVisibleMenus } from '@/api/menu'
import { useUserStore } from './useUserStore'
import type { MenuItem } from '@/types'

export const useMenuStore = defineStore('menu', () => {
  // 可见菜单（Layout 侧边栏/顶部栏使用）
  const visibleMenus = ref<MenuItem[]>([])
  // 版本号，每次菜单变更 +1，用于触发响应式更新
  const version = ref(0)

  // 加载可见菜单
  const loadVisibleMenus = async () => {
    try {
      const userStore = useUserStore()
      const response = await getVisibleMenus(userStore.user?.role || 'VIEWER')
      visibleMenus.value = response.data || []
      version.value++
    } catch (error) {
      console.error('Failed to load visible menus:', error)
    }
  }

  // 通知菜单已变更（MenuConfig 调用后触发 Layout 重载）
  const notifyMenuChanged = async () => {
    await loadVisibleMenus()
  }

  return {
    visibleMenus,
    version,
    loadVisibleMenus,
    notifyMenuChanged
  }
})
