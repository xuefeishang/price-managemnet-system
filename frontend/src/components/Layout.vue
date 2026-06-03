<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useMenuStore } from '@/store/useMenuStore'
import { useRouter, useRoute } from 'vue-router'
import { getRoleLabel, loadAllDicts } from '@/composables/useDict'
import { useTheme } from '@/composables/useTheme'
import { useLayout } from '@/composables/useLayout'
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
  userStore.logoutAction()
  // 跳转由 watch(() => userStore.isAuthenticated) 统一处理
}

const goProfile = () => {
  navigateTo('/profile')
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
})

watch(() => userStore.isAuthenticated, (isAuth) => {
  if (!isAuth) {
    router.push('/login')
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
          <div class="user-info">
            <button class="user-avatar avatar-button" @click="goProfile" title="进入个人中心" type="button">
              {{ userStore.user?.nickname?.charAt(0) || 'U' }}
            </button>
            <div class="user-details">
              <div class="user-name">{{ userStore.user?.nickname }}</div>
              <div class="user-role">{{ userRoleLabel }}</div>
            </div>
          </div>
          <button class="logout-btn" @click="handleLogout" title="退出登录">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
          </button>
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
  padding: 16px;
  border-top: 1px solid #F3F4F6;
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
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

.user-details {
  text-align: left;
}

.user-name {
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--app-nav-text);
}

.user-role {
  font-family: var(--font-body);
  font-size: var(--font-size-xs);
  color: var(--app-nav-text);
  opacity: 0.7;
}

.logout-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--app-nav-text);
  cursor: pointer;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 150ms;
}

.logout-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
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
