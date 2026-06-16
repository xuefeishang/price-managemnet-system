const STORAGE_USER_KEY = 'user'
const STORAGE_TOKEN_KEY = 'token'
const NETWORK_MODE_KEY = 'api_network_mode'

const LOCAL_TEST_BASE_URL = 'http://127.0.0.1:8080'
const INTRANET_BASE_URL = 'http://10.7.5.175:32801'
const EXTERNAL_BASE_URL = 'https://price.jlmining.com:32080'

const BASE_TABS = [
  { key: 'home', pagePath: '/pages/home/index', text: '首页', icon: '🏠' },
  { key: 'history', pagePath: '/pages/history/index', text: '历史', icon: '📈' },
  { key: 'profile', pagePath: '/pages/profile/index', text: '我的', icon: '👤' }
]

const ENTRY_TAB = { key: 'entry', pagePath: '/pages/price-maintenance/index', text: '录入', icon: '✍️' }

const getStoredUser = () => {
  const rawUser = wx.getStorageSync(STORAGE_USER_KEY)
  if (!rawUser) return null
  if (typeof rawUser === 'object') return rawUser

  try {
    return JSON.parse(rawUser)
  } catch (error) {
    console.warn('[custom-tab-bar] parse user failed', error)
    return null
  }
}

const getCanEdit = () => {
  const role = getStoredUser()?.role
  return role === 'ADMIN' || role === 'EDITOR'
}

const buildTabList = () => {
  const tabs = BASE_TABS.slice()
  if (getCanEdit()) {
    tabs.splice(2, 0, ENTRY_TAB)
  }
  return tabs
}

const getCurrentPath = () => {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  return current?.route ? `/${current.route}` : ''
}

const getApiBaseUrl = () => {
  const mode = wx.getStorageSync(NETWORK_MODE_KEY) || 'external'
  if (mode === 'internal' || mode === 'auto') return INTRANET_BASE_URL
  if (mode === 'dev') return LOCAL_TEST_BASE_URL
  return EXTERNAL_BASE_URL
}

Component({
  data: {
    currentPath: '',
    activeHostPath: '',
    tabList: buildTabList(),
    unreadCount: 0,
    unreadText: '',
    bubbleVisible: false,
    bubbleText: '收到新的消息通知',
    initialized: false
  },

  lifetimes: {
    attached() {
      this.refreshTabState()
      this.startNotificationPolling()
    },

    detached() {
      this.stopNotificationPolling()
      this.clearBubbleTimer()
    }
  },

  pageLifetimes: {
    show() {
      this.refreshTabState()
      this.refreshUnreadCount(true)
    }
  },

  methods: {
    refreshTabState() {
      const currentPath = getCurrentPath()
      this.setData({
        currentPath,
        activeHostPath: currentPath,
        tabList: buildTabList()
      })
    },

    switchTab(event) {
      const pagePath = event.currentTarget.dataset.path
      if (!pagePath || pagePath === this.data.currentPath) return

      wx.switchTab({ url: pagePath })
    },

    goNotifications() {
      this.setData({ bubbleVisible: false })
      wx.navigateTo({ url: '/pages/notifications/index' })
    },

    startNotificationPolling() {
      if (!this.data.initialized) {
        this.refreshUnreadCount(false)
      }
      if (this.pollTimer) return

      this.pollTimer = setInterval(() => {
        this.refreshUnreadCount(true)
      }, 30000)
    },

    stopNotificationPolling() {
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
    },

    clearBubbleTimer() {
      if (this.bubbleTimer) {
        clearTimeout(this.bubbleTimer)
        this.bubbleTimer = null
      }
    },

    showNotificationBubble() {
      this.clearBubbleTimer()
      this.setData({
        bubbleText: '收到新的消息通知',
        bubbleVisible: true
      })
      this.bubbleTimer = setTimeout(() => {
        this.setData({ bubbleVisible: false })
      }, 3600)
    },

    refreshUnreadCount(showBubbleOnIncrease) {
      const token = wx.getStorageSync(STORAGE_TOKEN_KEY)
      if (!token) {
        this.setData({
          unreadCount: 0,
          unreadText: '',
          initialized: false
        })
        return
      }

      if (this.refreshingUnread) return
      this.refreshingUnread = true

      wx.request({
        url: `${getApiBaseUrl()}/api/notifications/unread-count`,
        method: 'GET',
        timeout: 30000,
        header: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json'
        },
        success: (response) => {
          const body = response.data || {}
          if (response.statusCode < 200 || response.statusCode >= 300 || body.code !== 200) return

          const nextCount = Number(body.data || 0)
          if (showBubbleOnIncrease && this.data.initialized && nextCount > this.data.unreadCount) {
            this.showNotificationBubble()
          }

          this.setData({
            unreadCount: nextCount,
            unreadText: nextCount > 99 ? '99+' : String(nextCount),
            initialized: true
          })
        },
        complete: () => {
          this.refreshingUnread = false
        }
      })
    }
  }
})
