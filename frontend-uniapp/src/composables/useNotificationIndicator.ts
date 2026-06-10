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
    unreadCount.value = 0
    initialized.value = false
    return
  }

  try {
    const response = await getUnreadNotificationCount()
    const nextCount = Number(response.data || 0)
    if (showBubbleOnIncrease && initialized.value && nextCount > unreadCount.value) {
      showNotificationBubble()
    }
    unreadCount.value = nextCount
    initialized.value = true
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
  showNotificationBubble,
  startNotificationPolling,
  setActiveNotificationHost
})
