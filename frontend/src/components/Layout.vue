<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useMenuStore } from '@/store/useMenuStore'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from 'vant'
import { getDictValue, getRoleLabel, loadAllDicts } from '@/composables/useDict'
import { useTheme } from '@/composables/useTheme'
import { useLayout } from '@/composables/useLayout'
import {
  archiveNotification,
  getMyNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead
} from '@/api/notifications'
import type { NotificationMessage, NotificationSseEvent } from '@/types'
import SidebarMenuTree from '@/components/layout/SidebarMenuTree.vue'
import ContextSubNav from '@/components/layout/ContextSubNav.vue'
import { findMenuByPath, normalizeMenuTree, type MenuMatch, type MenuNode } from '@/components/layout/menuUtils'

const userStore = useUserStore()
const menuStore = useMenuStore()
const router = useRouter()
const route = useRoute()
const { themeConfig, loadThemeConfig } = useTheme()
const { isPCLayout } = useLayout()

// Logo完整URL（优先使用导航栏专用Logo，否则使用通用Logo）
const logoUrlFull = computed(() => {
  const url = themeConfig.value.logoUrlNav || themeConfig.value.logoUrl
  if (!url) return ''
  // data URL (Base64) 直接返回
  if (url.startsWith('data:')) return url
  // http链接直接返回
  if (url.startsWith('http')) return url
  // 相对路径拼接origin
  return window.location.origin + url
})

// Logo尺寸样式（优先使用导航栏专用尺寸）
const logoSizeStyle = computed(() => {
  const sizeMap: Record<string, string> = {
    small: '24px',
    medium: '36px',
    large: '48px',
    xlarge: '64px'
  }
  const size = sizeMap[themeConfig.value.logoSizeNav || themeConfig.value.logoSize] || '36px'
  return {
    width: size,
    height: size
  }
})

const isMobileMenuOpen = ref(false)
const expandedMenuIds = ref<Set<number>>(new Set())
const pendingMenuPath = ref<string | null>(null)
const notificationDrawerOpen = ref(false)
const notifications = ref<NotificationMessage[]>([])
const notificationsLoading = ref(false)
const notificationReadFilter = ref<'ALL' | 'UNREAD'>('ALL')
const unreadNotificationCount = ref(0)
const userMenuOpen = ref(false)
const unreadCountInitialized = ref(false)
let notificationTimer: ReturnType<typeof setTimeout> | null = null
let notificationBackoffLevel = 0
let notificationStreamAbortController: AbortController | null = null

// 使用 menuStore 的菜单数据
const menus = computed(() => menuStore.visibleMenus)
const userRoleLabel = computed(() => userStore.user?.role ? getRoleLabel(userStore.user.role) : '')
const menuTree = computed(() => normalizeMenuTree(menus.value))
const routeMenuPath = computed(() => {
  return (route.meta.activeMenu as string | undefined) || route.path
})
const displayRoutePath = computed(() => pendingMenuPath.value || route.path)
const displayRouteMenuPath = computed(() => pendingMenuPath.value || routeMenuPath.value)
const currentMenuMatch = computed<MenuMatch | null>(() => {
  return findMenuByPath(menuTree.value, displayRoutePath.value) || findMenuByPath(menuTree.value, displayRouteMenuPath.value)
})
const activeMenuPath = computed(() => currentMenuMatch.value?.node.path || displayRouteMenuPath.value)
const activeMenuIds = computed(() => {
  const match = currentMenuMatch.value
  if (!match) return new Set<number>()
  return new Set([...match.ancestors.map(menu => menu.id), match.node.id])
})
const contextSubNav = computed<{ parent: MenuNode, items: MenuNode[] } | null>(() => {
  const match = currentMenuMatch.value
  if (!match) return null

  const chain = [...match.ancestors, match.node]
  const depth = chain.length

  if (depth <= 1) return null

  if (depth === 2) {
    return match.node.children.length > 0
      ? { parent: match.node, items: match.node.children }
      : null
  }

  const secondLevelMenu = chain[1]
  return secondLevelMenu?.children.length > 0
    ? { parent: secondLevelMenu, items: secondLevelMenu.children }
    : null
})

const syncExpandedMenuByRoute = () => {
  const match = currentMenuMatch.value
  if (!match) return
  const next = new Set(expandedMenuIds.value)
  match.ancestors.forEach(menu => next.add(menu.id))
  expandedMenuIds.value = next
}

// 加载动态菜单
const loadMenus = async () => {
  await menuStore.loadVisibleMenus()
  syncExpandedMenuByRoute()
}

const toggleMenu = (id: number) => {
  const next = new Set(expandedMenuIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  expandedMenuIds.value = next
}

// 导航
const navigateTo = async (path: string) => {
  if (path === route.path) {
    isMobileMenuOpen.value = false
    return
  }

  pendingMenuPath.value = path
  isMobileMenuOpen.value = false

  try {
    await router.push(path)
  } finally {
    if (pendingMenuPath.value === path) {
      pendingMenuPath.value = null
    }
  }
}

const handleLogout = () => {
  stopNotificationUpdates()
  closeUserMenu()
  userStore.logoutAction()
  // 跳转由 watch(() => userStore.isAuthenticated) 统一处理
}

const goProfile = () => {
  closeUserMenu()
  navigateTo('/profile')
}

const goChangePassword = () => {
  closeUserMenu()
  router.push({ path: '/profile', query: { tab: 'security' } })
}

const toggleUserMenu = () => {
  userMenuOpen.value = !userMenuOpen.value
}

const closeUserMenu = () => {
  userMenuOpen.value = false
}

const applyUnreadNotificationCount = (nextCount: number, showNewToast = false) => {
  if (showNewToast && unreadCountInitialized.value && nextCount > unreadNotificationCount.value && !notificationDrawerOpen.value) {
    showToast({
      message: '收到新的消息通知',
      position: 'bottom'
    })
  }
  unreadNotificationCount.value = nextCount
  unreadCountInitialized.value = true
}

const loadUnreadNotificationCount = async () => {
  if (!userStore.isAuthenticated || !userStore.token) return false
  try {
    const response = await getUnreadNotificationCount()
    const nextCount = response.data || 0
    applyUnreadNotificationCount(nextCount, true)
    notificationBackoffLevel = 0
    return true
  } catch (error) {
    notificationBackoffLevel = Math.min(notificationBackoffLevel + 1, 3)
    if (import.meta.env.DEV) {
      console.error('Failed to load notification unread count:', error)
    }
    return false
  }
}

const loadNotifications = async () => {
  if (!userStore.isAuthenticated || !userStore.token) return
  notificationsLoading.value = true
  try {
    const response = await getMyNotifications({
      page: 0,
      size: 20,
      readStatus: notificationReadFilter.value === 'UNREAD' ? 'UNREAD' : undefined
    })
    notifications.value = response.data?.content || []
    await loadUnreadNotificationCount()
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error('Failed to load notifications:', error)
    }
  } finally {
    notificationsLoading.value = false
  }
}

const openNotificationDrawer = async () => {
  closeUserMenu()
  notificationDrawerOpen.value = true
  await loadNotifications()
}

const closeNotificationDrawer = () => {
  notificationDrawerOpen.value = false
}

const setNotificationReadFilter = async (filter: 'ALL' | 'UNREAD') => {
  if (notificationReadFilter.value === filter) return
  notificationReadFilter.value = filter
  await loadNotifications()
}

const markAllNotificationsAsRead = async () => {
  if (unreadNotificationCount.value <= 0) return
  try {
    await markAllNotificationsRead()
    unreadNotificationCount.value = 0
    notifications.value = notifications.value.map(notification => ({
      ...notification,
      readStatus: 'READ'
    }))
    if (notificationReadFilter.value === 'UNREAD') {
      await loadNotifications()
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error('Failed to mark all notifications read:', error)
    }
  }
}

const parseNotificationLinkParams = (notification: NotificationMessage) => {
  if (!notification.linkParams) return {}
  try {
    return JSON.parse(notification.linkParams) as Record<string, string>
  } catch {
    return {}
  }
}

const getNotificationDate = (notification: NotificationMessage) => {
  const params = parseNotificationLinkParams(notification)
  if (params.date) return params.date
  const matched = notification.content?.match(/\d{4}-\d{2}-\d{2}/)
  return matched?.[0]
}

const getNotificationPriorityLabel = (priority?: string) => {
  if (!priority || priority === 'NORMAL') return ''
  return getDictValue('notification_priority', priority)
}

const handleNotificationClick = async (notification: NotificationMessage) => {
  if (notification.readStatus === 'UNREAD') {
    notification.readStatus = 'READ'
    unreadNotificationCount.value = Math.max(unreadNotificationCount.value - 1, 0)
    markNotificationRead(notification.messageId).catch(async (error) => {
      if (import.meta.env.DEV) {
        console.error('Failed to mark notification read:', error)
      }
      await loadUnreadNotificationCount()
      if (notificationDrawerOpen.value) {
        await loadNotifications()
      }
    })
  }

  closeNotificationDrawer()
  if (notification.linkType === 'PRICE_QUERY' || notification.type === 'PRICE_PUBLISHED') {
    const date = getNotificationDate(notification)
    router.push({ path: '/price-query', query: date ? { date } : undefined })
  }
}

const archiveNotificationItem = async (notification: NotificationMessage) => {
  try {
    await archiveNotification(notification.messageId)
    notifications.value = notifications.value.filter(item => item.id !== notification.id)
    if (notification.readStatus === 'UNREAD') {
      await loadUnreadNotificationCount()
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error('Failed to archive notification:', error)
    }
  }
}

const formatNotificationTime = (time?: string) => {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

const getNotificationPollingDelay = () => {
  const baseDelay = notificationBackoffLevel > 0
    ? Math.min(30000 * 2 ** notificationBackoffLevel, 120000)
    : 30000
  return baseDelay + Math.floor(Math.random() * 15000)
}

const scheduleNotificationPolling = () => {
  if (notificationTimer || document.visibilityState === 'hidden') return
  notificationTimer = setTimeout(async () => {
    notificationTimer = null
    await loadUnreadNotificationCount()
    scheduleNotificationPolling()
  }, getNotificationPollingDelay())
}

const refreshNotifications = async () => {
  await loadUnreadNotificationCount()
  if (notificationDrawerOpen.value) {
    await loadNotifications()
  }
}

const handleNotificationRealtimeEvent = async (event: NotificationSseEvent) => {
  if (typeof event.unreadCount === 'number') {
    applyUnreadNotificationCount(event.unreadCount, event.eventType === 'newNotification')
  } else {
    await loadUnreadNotificationCount()
  }
  if (event.eventType === 'newNotification' && notificationDrawerOpen.value) {
    await loadNotifications()
  }
}

const parseNotificationEventChunk = async (chunk: string) => {
  const dataLine = chunk
    .split(/\r?\n/)
    .find(line => line.startsWith('data:'))
  if (!dataLine) return
  try {
    const payload = JSON.parse(dataLine.slice(5).trim()) as NotificationSseEvent
    if (payload.eventType !== 'connected') {
      await handleNotificationRealtimeEvent(payload)
    }
  } catch (error) {
    if (import.meta.env.DEV) {
      console.error('Failed to parse notification realtime event:', error)
    }
  }
}

const stopNotificationRealtime = () => {
  if (notificationStreamAbortController) {
    notificationStreamAbortController.abort()
    notificationStreamAbortController = null
  }
}

const startNotificationRealtime = async () => {
  if (!userStore.isAuthenticated || !userStore.token || document.visibilityState === 'hidden') return
  if (notificationStreamAbortController) return

  const controller = new AbortController()
  notificationStreamAbortController = controller
  const baseURL = import.meta.env.VITE_API_BASE_URL || ''

  try {
    const response = await fetch(`${baseURL}/api/notifications/events`, {
      headers: {
        Authorization: `Bearer ${userStore.token}`
      },
      signal: controller.signal
    })
    if (!response.ok || !response.body) {
      throw new Error(`SSE connect failed: ${response.status}`)
    }

    stopNotificationPolling()
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (!controller.signal.aborted) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const chunks = buffer.split(/\r?\n\r?\n/)
      buffer = chunks.pop() || ''
      for (const chunk of chunks) {
        await parseNotificationEventChunk(chunk)
      }
    }
  } catch (error) {
    if (!controller.signal.aborted && import.meta.env.DEV) {
      console.error('Notification realtime disconnected:', error)
    }
  } finally {
    if (notificationStreamAbortController === controller) {
      notificationStreamAbortController = null
    }
    if (!controller.signal.aborted && userStore.isAuthenticated && document.visibilityState === 'visible') {
      scheduleNotificationPolling()
    }
  }
}

const startNotificationPolling = () => {
  stopNotificationPolling()
  if (document.visibilityState === 'hidden') return
  loadUnreadNotificationCount().finally(scheduleNotificationPolling)
}

const stopNotificationPolling = () => {
  if (notificationTimer) {
    clearTimeout(notificationTimer)
    notificationTimer = null
  }
}

const startNotificationUpdates = () => {
  stopNotificationPolling()
  if (document.visibilityState === 'hidden') return
  startNotificationPolling()
  startNotificationRealtime()
}

const stopNotificationUpdates = () => {
  stopNotificationPolling()
  stopNotificationRealtime()
}

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') {
    startNotificationUpdates()
  } else {
    stopNotificationUpdates()
  }
}

onMounted(async () => {
  if (!userStore.isAuthenticated) {
    router.replace('/login')
    return
  }

  // 确保用户信息加载完成后再加载数据
  if (!userStore.user) {
    await userStore.fetchProfile()
  }

  // 并行加载菜单、字典、主题配置，提升首屏性能
  await Promise.all([
    loadMenus(),
    loadAllDicts(),
    loadThemeConfig()
  ])

  startNotificationUpdates()
  document.addEventListener('click', closeUserMenu)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('price-notifications:refresh', refreshNotifications)
})

onUnmounted(() => {
  stopNotificationUpdates()
  document.removeEventListener('click', closeUserMenu)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('price-notifications:refresh', refreshNotifications)
})

watch(() => route.fullPath, () => {
  closeUserMenu()
})

watch(() => userStore.isAuthenticated, (isAuth) => {
  if (!isAuth) {
    stopNotificationUpdates()
    unreadCountInitialized.value = false
    unreadNotificationCount.value = 0
    router.push('/login')
  } else {
    startNotificationUpdates()
  }
})

watch(() => userStore.user?.role, () => {
  loadMenus()
})

// 监听菜单数据变更（MenuConfig 修改后触发），更新路由选中状态
watch(() => menuStore.version, () => {
  syncExpandedMenuByRoute()
})

watch(() => route.path, () => {
  syncExpandedMenuByRoute()
  refreshNotifications()
})
</script>

<template>
  <div class="layout-container" :class="{ 'pc-layout': isPCLayout }">
    <!-- ==================== PC布局：左侧菜单 ==================== -->
    <template v-if="isPCLayout">
      <!-- 左侧边栏 -->
      <aside class="pc-sidebar">
        <!-- Logo区域 -->
        <div class="sidebar-header">
          <div class="brand" @click="navigateTo('/home')">
            <div class="brand-icon">
              <img v-if="logoUrlFull" :src="logoUrlFull" alt="Logo" class="brand-logo" :style="logoSizeStyle" />
              <svg v-else viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
                <rect x="4" y="4" width="24" height="24" rx="5" fill="url(#navGradient)"/>
                <path d="M10 16L13 19L22 10" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <defs>
                  <linearGradient id="navGradient" x1="4" y1="4" x2="28" y2="28" gradientUnits="userSpaceOnUse">
                    <stop stop-color="#0D6E6E"/>
                    <stop offset="1" stop-color="#0A5555"/>
                  </linearGradient>
                </defs>
              </svg>
            </div>
            <span class="brand-text">{{ themeConfig.systemName }}</span>
          </div>
        </div>

        <!-- 完整菜单树 -->
        <nav class="sidebar-nav">
          <SidebarMenuTree
            :menus="menuTree"
            :active-path="activeMenuPath"
            :active-ids="activeMenuIds"
            :expanded-ids="expandedMenuIds"
            @navigate="navigateTo"
            @toggle="toggleMenu"
          />
        </nav>

        <!-- 用户信息 -->
        <div class="sidebar-footer">
          <div class="user-panel" @click.stop>
            <button class="user-card" type="button" @click="goProfile" title="进入个人中心">
              <span class="user-avatar">
                {{ userStore.user?.nickname?.charAt(0) || 'U' }}
              </span>
              <span class="user-details">
                <span class="user-name">{{ userStore.user?.nickname }}</span>
                <span class="user-role">{{ userRoleLabel }}</span>
              </span>
            </button>

            <button
              class="user-more-btn"
              :class="{ active: userMenuOpen, attention: unreadNotificationCount > 0 }"
              type="button"
              title="用户操作"
              @click="toggleUserMenu"
            >
              <span v-if="unreadNotificationCount > 0" class="menu-notice-count">
                {{ unreadNotificationCount > 99 ? '99+' : unreadNotificationCount }}
              </span>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <circle cx="12" cy="5" r="1.8" />
                <circle cx="12" cy="12" r="1.8" />
                <circle cx="12" cy="19" r="1.8" />
              </svg>
            </button>

            <div v-if="userMenuOpen" class="user-action-menu">
              <button type="button" class="user-action-item" @click="goProfile">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M20 21a8 8 0 0 0-16 0" />
                  <circle cx="12" cy="7" r="4" />
                </svg>
                <span>个人中心</span>
              </button>
              <button type="button" class="user-action-item" @click="goChangePassword">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <rect x="4" y="11" width="16" height="10" rx="2" />
                  <path d="M8 11V7a4 4 0 0 1 8 0v4" />
                </svg>
                <span>修改密码</span>
              </button>
              <button type="button" class="user-action-item" @click="openNotificationDrawer">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z" />
                </svg>
                <span>消息通知</span>
                <span v-if="unreadNotificationCount > 0" class="user-action-badge">
                  {{ unreadNotificationCount > 99 ? '99+' : unreadNotificationCount }}
                </span>
              </button>
              <button type="button" class="user-action-item danger" @click="handleLogout">
                <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
                  <polyline points="16 17 21 12 16 7" />
                  <line x1="21" y1="12" x2="9" y2="12" />
                </svg>
                <span>退出登录</span>
              </button>
            </div>
          </div>
        </div>
      </aside>

      <!-- 二级菜单区域 -->
      <div class="pc-content-wrapper">
        <ContextSubNav
          v-if="contextSubNav"
          :title="contextSubNav.parent.name"
          :items="contextSubNav.items"
          :active-path="activeMenuPath"
          @navigate="navigateTo"
        />

        <!-- 主内容区域 -->
        <main class="pc-main">
          <router-view v-slot="{ Component }">
            <component :is="Component" />
          </router-view>
        </main>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 顶部导航栏 -->
      <header class="mobile-navbar">
        <div class="mobile-navbar-left">
          <button class="menu-toggle" @click="isMobileMenuOpen = !isMobileMenuOpen">
            <svg v-if="!isMobileMenuOpen" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
            <svg v-else width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"/>
              <line x1="6" y1="6" x2="18" y2="18"/>
            </svg>
          </button>
          <span class="mobile-title">
            <img v-if="logoUrlFull" :src="logoUrlFull" alt="Logo" class="mobile-brand-logo" :style="logoSizeStyle" />
            <span v-else>{{ themeConfig.systemName }}</span>
          </span>
        </div>
        <button class="mobile-logout" @click="handleLogout">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
        </button>
      </header>

      <!-- 侧边菜单 -->
      <div v-if="isMobileMenuOpen" class="mobile-sidebar-overlay" @click="isMobileMenuOpen = false"></div>
      <aside class="mobile-sidebar" :class="{ open: isMobileMenuOpen }">
        <div class="mobile-sidebar-header">
          <div class="mobile-user-info">
            <button class="user-avatar avatar-button" @click="goProfile" title="进入个人中心" type="button">
              {{ userStore.user?.nickname?.charAt(0) || 'U' }}
            </button>
            <div class="user-details">
              <div class="user-name">{{ userStore.user?.nickname }}</div>
              <div class="user-role">{{ userRoleLabel }}</div>
            </div>
          </div>
        </div>
        <nav class="mobile-nav">
          <SidebarMenuTree
            :menus="menuTree"
            :active-path="activeMenuPath"
            :active-ids="activeMenuIds"
            :expanded-ids="expandedMenuIds"
            @navigate="navigateTo"
            @toggle="toggleMenu"
          />
        </nav>
      </aside>

      <!-- 主内容区域 -->
      <main class="mobile-main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </main>
    </template>

    <div v-if="notificationDrawerOpen" class="notification-overlay" @click="closeNotificationDrawer"></div>
    <aside v-if="notificationDrawerOpen" class="notification-drawer">
      <header class="notification-header">
        <div>
          <h2>通知消息</h2>
          <p>{{ unreadNotificationCount }} 条未读</p>
        </div>
        <div class="notification-header-actions">
          <button
            type="button"
            class="notification-read-all"
            :disabled="unreadNotificationCount <= 0"
            @click="markAllNotificationsAsRead"
          >
            全部已读
          </button>
          <button type="button" class="notification-close" @click="closeNotificationDrawer">×</button>
        </div>
      </header>

      <div class="notification-tabs">
        <button
          type="button"
          :class="{ active: notificationReadFilter === 'ALL' }"
          @click="setNotificationReadFilter('ALL')"
        >
          全部
        </button>
        <button
          type="button"
          :class="{ active: notificationReadFilter === 'UNREAD' }"
          @click="setNotificationReadFilter('UNREAD')"
        >
          未读
        </button>
      </div>

      <div v-if="notificationsLoading" class="notification-state">加载中...</div>
      <div v-else-if="notifications.length === 0" class="notification-state">暂无通知</div>
      <div v-else class="notification-list">
        <article
          v-for="notification in notifications"
          :key="notification.id"
          role="button"
          tabindex="0"
          class="notification-item"
          :class="{ unread: notification.readStatus === 'UNREAD' }"
          @click="handleNotificationClick(notification)"
          @keydown.enter.prevent="handleNotificationClick(notification)"
        >
          <span class="notification-dot"></span>
          <span class="notification-content">
            <span class="notification-title-row">
              <strong>{{ notification.title }}</strong>
              <em v-if="getNotificationPriorityLabel(notification.priority)">
                {{ getNotificationPriorityLabel(notification.priority) }}
              </em>
              <button
                class="notification-archive"
                type="button"
                title="归档"
                @click.stop="archiveNotificationItem(notification)"
              >
                归档
              </button>
            </span>
            <span>{{ notification.summary || notification.content || '暂无内容' }}</span>
            <small>{{ formatNotificationTime(notification.createdTime) }}</small>
          </span>
        </article>
      </div>
    </aside>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.layout-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #F5F5F5;
}

/* ==================== PC布局 ==================== */
.pc-layout {
  flex-direction: row;
}

/* 左侧边栏 */
.pc-sidebar {
  width: 240px;
  min-width: 240px;
  height: 100vh;
  background: var(--app-nav-bg);
  border-right: 1px solid #E5E5E5;
  display: flex;
  flex-direction: column;
  position: fixed;
  left: 0;
  top: 0;
  z-index: 100;
}

.sidebar-header {
  padding: 20px 16px;
  border-bottom: 1px solid #F3F4F6;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.brand-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: contain;
}

.mobile-brand-logo {
  max-height: 32px;
  max-width: 100%;
  object-fit: contain;
}

.brand-text {
  font-family: var(--font-heading);
  font-size: var(--font-size-lg);
  font-weight: 500;
  color: var(--app-nav-text);
}

.sidebar-nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

/* 用户信息 */
.sidebar-footer {
  padding: 16px 18px 20px;
  border-top: 1px solid #F3F4F6;
  position: relative;
}

.user-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-panel {
  position: relative;
  width: 100%;
}

.user-card {
  width: 100%;
  min-height: 54px;
  padding: 9px 48px 9px 10px;
  border: 1px solid rgba(226, 232, 240, 0.84);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
  color: var(--app-nav-text);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  transition: border-color 160ms ease, background 160ms ease, box-shadow 160ms ease;
}

.user-card:hover {
  border-color: rgba(13, 110, 110, 0.24);
  background: #FFFFFF;
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.09);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: var(--font-size-sm);
}

.avatar-button {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.avatar-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(13, 110, 110, 0.22);
}

.user-card .user-avatar {
  flex-shrink: 0;
}

.user-details {
  min-width: 0;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--app-nav-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-role {
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  color: var(--app-nav-text);
  opacity: 0.7;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-more-btn {
  position: absolute;
  top: 50%;
  right: 9px;
  transform: translateY(-50%);
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: #EEF2F7;
  color: #475569;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 160ms ease, color 160ms ease, box-shadow 160ms ease;
}

.user-more-btn:hover,
.user-more-btn.active {
  background: #E2E8F0;
  color: #0D6E6E;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.1);
}

.menu-notice-count {
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: #E03B3B;
  color: #FFFFFF;
  box-shadow: 0 0 0 3px rgba(224, 59, 59, 0.12);
  font-size: 11px;
  font-weight: 800;
  line-height: 22px;
  text-align: center;
}

.user-action-menu {
  position: absolute;
  right: 0;
  bottom: 66px;
  z-index: 510;
  width: 148px;
  padding: 8px;
  border: 1px solid rgba(226, 232, 240, 0.86);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(10px);
}

.user-action-menu::after {
  content: '';
  position: absolute;
  right: 18px;
  bottom: -6px;
  width: 10px;
  height: 10px;
  border-right: 1px solid rgba(226, 232, 240, 0.86);
  border-bottom: 1px solid rgba(226, 232, 240, 0.86);
  background: rgba(255, 255, 255, 0.96);
  transform: rotate(45deg);
}

.user-action-item {
  position: relative;
  width: 100%;
  min-height: 36px;
  padding: 0 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  transition: background 150ms ease, color 150ms ease;
}

.user-action-item:hover {
  background: #F8FAFC;
  color: #0D6E6E;
}

.user-action-item.danger:hover {
  background: rgba(239, 68, 68, 0.08);
  color: #DC2626;
}

.user-action-item svg {
  flex-shrink: 0;
}

.user-action-item span:not(.user-action-badge) {
  min-width: 0;
  flex: 1;
}

.user-action-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #E03B3B;
  color: #FFFFFF;
  font-size: 10px;
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}

.notification-overlay {
  position: fixed;
  inset: 0;
  z-index: 500;
  background: rgba(15, 23, 42, 0.22);
}

.notification-drawer {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 501;
  width: 360px;
  max-width: 92vw;
  height: 100vh;
  background: #FFFFFF;
  box-shadow: -16px 0 40px rgba(15, 23, 42, 0.16);
  display: flex;
  flex-direction: column;
}

.notification-header {
  padding: 22px 20px 18px;
  border-bottom: 1px solid #EEF2F7;
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.notification-header h2 {
  margin: 0;
  color: #1A1A1A;
  font-size: 18px;
  font-weight: 700;
}

.notification-header p {
  margin: 6px 0 0;
  color: #64748B;
  font-size: 13px;
}

.notification-header-actions {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.notification-read-all {
  height: 32px;
  padding: 0 10px;
  border: 1px solid #D8E1EA;
  border-radius: 8px;
  background: #FFFFFF;
  color: #0D6E6E;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.notification-read-all:disabled {
  cursor: not-allowed;
  color: #94A3B8;
  background: #F8FAFC;
}

.notification-close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: #F8FAFC;
  color: #64748B;
  cursor: pointer;
  font-size: 24px;
  line-height: 1;
}

.notification-tabs {
  display: flex;
  gap: 8px;
  padding: 12px 20px;
  border-bottom: 1px solid #EEF2F7;
}

.notification-tabs button {
  height: 30px;
  padding: 0 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #F8FAFC;
  color: #64748B;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.notification-tabs button.active {
  border-color: rgba(13, 110, 110, 0.22);
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.notification-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.notification-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  gap: 10px;
  padding: 14px 12px;
  border-radius: 10px;
  cursor: pointer;
  text-align: left;
  box-sizing: border-box;
}

.notification-item:hover {
  background: #F8FAFC;
}

.notification-item.unread {
  background: rgba(13, 110, 110, 0.06);
}

.notification-dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: transparent;
  flex-shrink: 0;
}

.notification-item.unread .notification-dot {
  background: #0D6E6E;
}

.notification-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.notification-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notification-content strong {
  color: #1A1A1A;
  font-size: 14px;
  flex: 1;
}

.notification-content em {
  flex-shrink: 0;
  padding: 2px 6px;
  border-radius: 999px;
  background: rgba(224, 59, 59, 0.1);
  color: #B42318;
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}

.notification-archive {
  flex-shrink: 0;
  min-height: calc(var(--spacing-md) + var(--spacing-xs));
  padding: 0 var(--spacing-sm);
  border: 1px solid var(--gray-200);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--gray-600);
  cursor: pointer;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.notification-archive:hover {
  border-color: color-mix(in srgb, var(--primary-color, #0D6E6E) 28%, var(--gray-200));
  color: var(--primary-color);
}

.notification-content span {
  color: #475569;
  font-size: 13px;
  line-height: 1.45;
}

.notification-content small {
  color: #94A3B8;
  font-size: 12px;
}

.notification-state {
  padding: 48px 20px;
  color: #94A3B8;
  text-align: center;
  font-size: 14px;
}

/* 内容区域 */
.pc-content-wrapper {
  margin-left: 240px;
  flex: 1;
  min-width: 0;
  max-width: calc(100vw - 240px);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--app-page-bg);
  overflow-x: clip;
}

/* 主内容区域 */
.pc-main {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  overflow-x: clip;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

/* ==================== 移动端布局 ==================== */
.mobile-navbar {
  height: 56px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.mobile-navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.menu-toggle {
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  color: #1A1A1A;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.menu-toggle:hover {
  background: #F5F5F5;
}

.mobile-title {
  font-family: var(--font-heading);
  font-size: var(--font-size-lg);
  font-weight: 500;
  color: #0D6E6E;
}

.mobile-logout {
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  color: #666666;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.mobile-logout:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

/* 侧边菜单 */
.mobile-sidebar-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 200;
}

.mobile-sidebar {
  position: fixed;
  top: 0;
  left: -280px;
  width: 280px;
  height: 100vh;
  background: #FFFFFF;
  z-index: 201;
  transition: left 300ms ease;
  display: flex;
  flex-direction: column;
}

.mobile-sidebar.open {
  left: 0;
}

.mobile-sidebar-header {
  padding: 20px 16px;
  border-bottom: 1px solid #F3F4F6;
}

.mobile-user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mobile-nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.mobile-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

/* 响应式 */
@media (max-width: 1030px) {
  .pc-sidebar,
  .pc-content-wrapper {
    display: none;
  }
}

/* 移动端菜单展开动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 200ms ease;
  overflow: hidden;
}

.slide-down-enter-from,
.slide-down-leave-to {
  max-height: 0;
  opacity: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.slide-down-enter-to,
.slide-down-leave-from {
  max-height: 300px;
  opacity: 1;
}
</style>
