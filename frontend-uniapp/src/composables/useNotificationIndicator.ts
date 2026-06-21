import { ref } from 'vue'
import { getUnreadNotificationCount } from '@/api/notifications'
import { useUserStore } from '@/store/useUserStore'

const unreadCount = ref(0)
const initialized = ref(false)
const bubbleVisible = ref(false)
const bubbleText = ref('')
const activeHostPath = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null
let bubbleTimer: ReturnType<typeof setTimeout> | null = null
let refreshPromise: Promise<void> | null = null

const normalizeUnreadCount = (value: unknown) => {
  const count = Number(value || 0)
  if (!Number.isFinite(count) || count <= 0) return 0
  return Math.floor(count)
}

const syncCustomTabBarUnreadCount = (count: number) => {
  // #ifdef MP-WEIXIN
  const pages = getCurrentPages()
  for (let index = pages.length - 1; index >= 0; index -= 1) {
    const tabBar = (pages[index] as any)?.getTabBar?.()
    if (tabBar?.setUnreadCount) {
      tabBar.setUnreadCount(count)
      return
    }
  }
  // #endif
}

export const setNotificationUnreadCount = (count: number) => {
  const nextCount = normalizeUnreadCount(count)
  unreadCount.value = nextCount
  initialized.value = true
  syncCustomTabBarUnreadCount(nextCount)
}

export const showNotificationBubble = (message = '收到新的消息通知') => {
  bubbleText.value = message
  bubbleVisible.value = true
  if (bubbleTimer) clearTimeout(bubbleTimer)
  bubbleTimer = setTimeout(() => {
    bubbleVisible.value = false
  }, 3600)
}

export const refreshNotificationIndicator = async (showBubbleOnIncrease = true) => {
  if (refreshPromise) return refreshPromise

  refreshPromise = refreshNotificationIndicatorInternal(showBubbleOnIncrease)
  try {
    await refreshPromise
  } finally {
    refreshPromise = null
  }
}

const refreshNotificationIndicatorInternal = async (showBubbleOnIncrease: boolean) => {
  const userStore = useUserStore()
  if (!userStore.isAuthenticated) {
    setNotificationUnreadCount(0)
    initialized.value = false
    return
  }

  try {
    const response = await getUnreadNotificationCount()
    const nextCount = normalizeUnreadCount(response.data)
    if (showBubbleOnIncrease && initialized.value && nextCount > unreadCount.value) {
      showNotificationBubble()
    }
    setNotificationUnreadCount(nextCount)
  } catch {
    // 通知角标属于辅助信息，失败时保留已有状态并等待下次轮询。
  }
}

export const startNotificationPolling = () => {
  if (!initialized.value) refreshNotificationIndicator(false)
  if (pollTimer) return
  pollTimer = setInterval(() => refreshNotificationIndicator(true), 30000)
}

export const setActiveNotificationHost = (path: string) => {
  activeHostPath.value = path
}

export const useNotificationIndicator = () => ({
  unreadCount,
  bubbleVisible,
  bubbleText,
  activeHostPath,
  refreshNotificationIndicator,
  setNotificationUnreadCount,
  showNotificationBubble,
  startNotificationPolling,
  setActiveNotificationHost
})
