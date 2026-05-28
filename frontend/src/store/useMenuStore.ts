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
  const loadedRole = ref<string | null>(null)
  let loadingPromise: Promise<void> | null = null
  let loadingRole: string | null = null

  // 加载可见菜单
  const loadVisibleMenus = async (options: { force?: boolean } = {}) => {
    const userStore = useUserStore()
    const role = userStore.user?.role || 'VIEWER'

    if (!options.force && loadedRole.value === role) {
      return
    }

    if (loadingPromise && loadingRole === role) {
      return loadingPromise
    }

    loadingRole = role
    const request = (async () => {
      try {
        const response = await getVisibleMenus(role)
        visibleMenus.value = response.data || []
        loadedRole.value = role
        version.value++
      } catch (error) {
        console.error('Failed to load visible menus:', error)
      }
    })()

    loadingPromise = request
    try {
      await request
    } finally {
      if (loadingPromise === request) {
        loadingPromise = null
        loadingRole = null
      }
    }
  }

  // 通知菜单已变更（MenuConfig 调用后触发 Layout 重载）
  const notifyMenuChanged = async () => {
    await loadVisibleMenus({ force: true })
  }

  return {
    visibleMenus,
    version,
    loadVisibleMenus,
    notifyMenuChanged
  }
})
