<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useMenuStore } from '@/store/useMenuStore'
import { useRouter, useRoute } from 'vue-router'
import type { MenuItem } from '@/types'

const userStore = useUserStore()
const menuStore = useMenuStore()
const router = useRouter()
const route = useRoute()

const isMobileMenuOpen = ref(false)
const selectedParentMenu = ref<MenuItem | null>(null)
const expandedSidebarMenuId = ref<number | null>(null)
const expandedMobileGroups = ref<Set<number>>(new Set())

// 使用 menuStore 的菜单数据
const menus = computed(() => menuStore.visibleMenus)

// 响应式布局
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const isPCLayout = computed(() => windowWidth.value >= 1024)

// 根据当前路由自动选中对应的一级菜单
const updateSelectedMenuByRoute = () => {
  const currentPath = route.path

  // 查找当前路由对应的一级菜单
  const findParentMenu = (menuList: MenuItem[]): MenuItem | null => {
    for (const menu of menuList) {
      // 检查是否是当前菜单本身
      if (menu.path === currentPath) {
        return menu
      }
      // 获取子菜单（支持嵌套和扁平结构）
      const children = menu.children && menu.children.length > 0
        ? menu.children
        : menus.value.filter(m => m.parentId === menu.id)
      for (const child of children) {
        // 直接子菜单匹配
        if (child.path === currentPath) {
          return menu
        }
        // 三级子菜单匹配
        const grandchildren = child.children && child.children.length > 0
          ? child.children
          : menus.value.filter(m => m.parentId === child.id)
        if (grandchildren.some(g => g.path === currentPath)) {
          expandedChildMenu.value = child
          return menu
        }
      }
    }
    return null
  }

  const matchedMenu = findParentMenu(parentMenus.value)
  if (matchedMenu) {
    selectedParentMenu.value = matchedMenu
    expandedSidebarMenuId.value = matchedMenu.id
  }
}

// 加载动态菜单
const loadMenus = async () => {
  await menuStore.loadVisibleMenus()
  // 菜单加载完成后，根据当前路由更新选中状态
  updateSelectedMenuByRoute()
}

// 监听路由变化，更新选中的一级菜单
watch(() => route.path, () => {
  updateSelectedMenuByRoute()
})

// 一级菜单（parentId 为 null）
const parentMenus = computed(() => {
  return menus.value.filter(m => m.parentId === null).sort((a, b) => a.sortOrder - b.sortOrder)
})

// 根据选中的一级菜单获取二级菜单
const childMenus = computed(() => {
  if (!selectedParentMenu.value) return []
  // 优先从 children 属性获取（API 返回的嵌套结构）
  if (selectedParentMenu.value.children && selectedParentMenu.value.children.length > 0) {
    return selectedParentMenu.value.children.sort((a, b) => a.sortOrder - b.sortOrder)
  }
  // 如果选中的是子菜单（parentId 不为 null），显示它的兄弟菜单
  if (selectedParentMenu.value.parentId !== null) {
    return menus.value
      .filter(m => m.parentId === selectedParentMenu.value?.parentId)
      .sort((a, b) => a.sortOrder - b.sortOrder)
  }
  // 备用：通过 parentId 匹配（扁平结构）
  return menus.value
    .filter(m => m.parentId === selectedParentMenu.value?.id)
    .sort((a, b) => a.sortOrder - b.sortOrder)
})

// 判断一级菜单是否有子菜单
const hasChildren = (menu: MenuItem) => {
  return (menu.children && menu.children.length > 0) ||
         menus.value.some(m => m.parentId === menu.id)
}

// sub-navbar 展示的菜单项：展开了二级容器则显示三级子菜单，否则显示二级子菜单
const subNavItems = computed(() => {
  if (expandedChildMenu.value) {
    return grandChildMenus(expandedChildMenu.value)
  }
  return childMenus.value
})

// 切换移动端菜单组展开状态
const toggleMobileGroup = (menu: MenuItem) => {
  if (!menu.id) return
  if (expandedMobileGroups.value.has(menu.id)) {
    expandedMobileGroups.value.delete(menu.id)
  } else {
    expandedMobileGroups.value.add(menu.id)
  }
}

// 判断移动端菜单组是否展开
const isMobileGroupExpanded = (menu: MenuItem) => {
  return menu.id ? expandedMobileGroups.value.has(menu.id) : false
}

// 判断菜单是否活跃
const isActive = (menu: MenuItem) => {
  // 1. 如果菜单自己有 path，检查是否匹配当前路由
  if (menu.path) {
    return route.path === menu.path
  }
  // 2. 如果菜单有子菜单，检查当前路由是否匹配其子菜单
  if (hasChildren(menu)) {
    // 获取该菜单的子菜单
    const children = menu.children && menu.children.length > 0
      ? menu.children
      : menus.value.filter(m => m.parentId === menu.id)
    return children.some(child => child.path && route.path === child.path)
  }
  return false
}

const expandedChildMenu = ref<MenuItem | null>(null)

// 选中一级菜单
const selectParentMenu = (menu: MenuItem) => {
  // 判断是否有子菜单（支持嵌套结构和扁平结构）
  const hasSubMenus = hasChildren(menu)
  expandedChildMenu.value = null

  // 始终设置 selectedParentMenu（控制顶部导航栏）
  selectedParentMenu.value = menu

  if (hasSubMenus) {
    // 仅切换侧边栏展开状态
    if (expandedSidebarMenuId.value === menu.id) {
      expandedSidebarMenuId.value = null
    } else {
      expandedSidebarMenuId.value = menu.id
    }
  } else if (menu.path) {
    expandedSidebarMenuId.value = null
    navigateTo(menu.path)
  }
}

// 获取三级子菜单
const grandChildMenus = (child: MenuItem) => {
  if (child.children && child.children.length > 0) {
    return child.children.sort((a, b) => a.sortOrder - b.sortOrder)
  }
  return menus.value
    .filter(m => m.parentId === child.id)
    .sort((a, b) => a.sortOrder - b.sortOrder)
}

// 二级菜单点击处理
const handleChildClick = (child: MenuItem) => {
  if (child.path) {
    navigateTo(child.path)
  } else if (hasChildren(child)) {
    if (expandedChildMenu.value?.id === child.id) {
      expandedChildMenu.value = null
    } else {
      expandedChildMenu.value = child
    }
  }
}

// 导航
const navigateTo = (path: string) => {
  router.push(path)
  isMobileMenuOpen.value = false
}

const handleLogout = () => {
  userStore.logoutAction()
  router.push('/login')
}

// 响应式监听
const handleResize = () => {
  windowWidth.value = window.innerWidth
}

onMounted(async () => {
  if (!userStore.isAuthenticated) {
    router.push('/login')
    return
  }

  // 确保用户信息加载完成后再加载菜单
  if (!userStore.user) {
    await userStore.fetchProfile()
  }
  loadMenus()
  window.addEventListener('resize', handleResize)
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
  updateSelectedMenuByRoute()
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
              <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
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
            <span class="brand-text">价格管理系统</span>
          </div>
        </div>

        <!-- 一级菜单（含二级菜单展开） -->
        <nav class="sidebar-nav">
          <template v-for="menu in parentMenus" :key="menu.id">
            <!-- 父级菜单项 -->
            <div
              class="nav-item parent"
              :class="{
                active: isActive(menu) || selectedParentMenu?.id === menu.id,
                expanded: expandedSidebarMenuId === menu.id,
                'has-children': hasChildren(menu)
              }"
              @click="selectParentMenu(menu)"
            >
              <span class="nav-icon">
                <svg v-if="menu.icon === 'home'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
                  <polyline points="9 22 9 12 15 12 15 22"/>
                </svg>
                <svg v-else-if="menu.icon === 'product'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                </svg>
                <svg v-else-if="menu.icon === 'price'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="1" x2="12" y2="23"/>
                  <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
                </svg>
                <svg v-else-if="menu.icon === 'category'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                </svg>
                <svg v-else-if="menu.icon === 'users'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                  <circle cx="9" cy="7" r="4"/>
                  <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                  <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                </svg>
                <svg v-else-if="menu.icon === 'settings'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="3"/>
                  <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
                </svg>
                <svg v-else-if="menu.icon === 'import'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                  <polyline points="7 10 12 15 17 10"/>
                  <line x1="12" y1="15" x2="12" y2="3"/>
                </svg>
                <svg v-else-if="menu.icon === 'menu'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="4" y1="12" x2="20" y2="12"/>
                  <line x1="4" y1="6" x2="20" y2="6"/>
                  <line x1="4" y1="18" x2="20" y2="18"/>
                </svg>
                <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="12" y1="8" x2="12" y2="12"/>
                  <line x1="12" y1="16" x2="12.01" y2="16"/>
                </svg>
              </span>
              <span class="nav-label">{{ menu.name }}</span>
              <span v-if="hasChildren(menu)" class="nav-arrow">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="9 18 15 12 9 6"/>
                </svg>
              </span>
            </div>
            <!-- 二级菜单（展开显示） -->
            <div v-if="hasChildren(menu) && expandedSidebarMenuId === menu.id" class="nav-children">
              <template v-for="child in childMenus" :key="child.id">
                <div
                  class="nav-item child"
                  :class="{ active: isActive(child), 'has-children': hasChildren(child), expanded: expandedChildMenu?.id === child.id }"
                  @click.stop="handleChildClick(child)"
                >
                  <span class="nav-label">{{ child.name }}</span>
                  <span v-if="hasChildren(child)" class="nav-arrow">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="9 18 15 12 9 6"/>
                    </svg>
                  </span>
                </div>
                <!-- 三级菜单（展开显示） -->
                <div v-if="hasChildren(child) && expandedChildMenu?.id === child.id" class="nav-children grandchild">
                  <div
                    v-for="grand in grandChildMenus(child)"
                    :key="grand.id"
                    class="nav-item grandchild"
                    :class="{ active: isActive(grand) }"
                    @click.stop="navigateTo(grand.path || '/home')"
                  >
                    <span class="nav-label">{{ grand.name }}</span>
                  </div>
                </div>
              </template>
            </div>
          </template>
        </nav>

        <!-- 用户信息 -->
        <div class="sidebar-footer">
          <div class="user-info">
            <div class="user-avatar">
              {{ userStore.user?.nickname?.charAt(0) || 'U' }}
            </div>
            <div class="user-details">
              <div class="user-name">{{ userStore.user?.nickname }}</div>
              <div class="user-role">
                {{ userStore.user?.role === 'ADMIN' ? '管理员' :
                   userStore.user?.role === 'EDITOR' ? '编辑者' : '查看者' }}
              </div>
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
        <!-- 二级菜单栏 -->
        <div v-if="selectedParentMenu" class="sub-navbar">
          <div class="sub-nav-title">{{ expandedChildMenu ? expandedChildMenu.name : selectedParentMenu.name }}</div>
          <nav class="sub-nav-menu" v-if="subNavItems.length > 0">
            <button
              v-for="item in subNavItems"
              :key="item.id"
              class="sub-nav-item"
              :class="{ active: isActive(item) }"
              @click="navigateTo(item.path || '/home')"
            >
              {{ item.name }}
            </button>
          </nav>
        </div>

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
          <span class="mobile-title">价格管理系统</span>
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
            <div class="user-avatar">
              {{ userStore.user?.nickname?.charAt(0) || 'U' }}
            </div>
            <div class="user-details">
              <div class="user-name">{{ userStore.user?.nickname }}</div>
              <div class="user-role">
                {{ userStore.user?.role === 'ADMIN' ? '管理员' :
                   userStore.user?.role === 'EDITOR' ? '编辑者' : '查看者' }}
              </div>
            </div>
          </div>
        </div>
        <nav class="mobile-nav">
          <template v-for="menu in parentMenus" :key="menu.id">
            <!-- 有子菜单的一级菜单 -->
            <div v-if="hasChildren(menu)" class="mobile-nav-group">
              <div
                class="mobile-nav-parent"
                :class="{ expanded: isMobileGroupExpanded(menu) }"
                @click="toggleMobileGroup(menu)"
              >
                <span>{{ menu.name }}</span>
                <svg class="expand-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </div>
              <transition name="slide-down">
                <div v-if="isMobileGroupExpanded(menu)" class="mobile-nav-children">
                  <template v-for="child in (menu.children || menus.filter(m => m.parentId === menu.id))" :key="child.id">
                    <!-- 二级有 path：直接可点击 -->
                    <button
                      v-if="child.path"
                      class="mobile-nav-item child"
                      :class="{ active: route.path === child.path }"
                      @click="navigateTo(child.path)"
                    >
                      {{ child.name }}
                    </button>
                    <!-- 二级无 path 但有子菜单：展开三级 -->
                    <div v-else-if="hasChildren(child)" class="mobile-nav-subgroup">
                      <button
                        class="mobile-nav-item child group-label"
                        @click="toggleMobileGroup(child)"
                      >
                        {{ child.name }}
                        <svg class="expand-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polyline points="6 9 12 15 18 9"/>
                        </svg>
                      </button>
                      <transition name="slide-down">
                        <div v-if="isMobileGroupExpanded(child)" class="mobile-nav-grandchildren">
                          <button
                            v-for="grand in grandChildMenus(child)"
                            :key="grand.id"
                            class="mobile-nav-item grandchild"
                            :class="{ active: route.path === grand.path }"
                            @click="navigateTo(grand.path || '/home')"
                          >
                            {{ grand.name }}
                          </button>
                        </div>
                      </transition>
                    </div>
                  </template>
                </div>
              </transition>
            </div>
            <!-- 没有子菜单的一级菜单 -->
            <button
              v-else
              class="mobile-nav-item parent"
              :class="{ active: isActive(menu) }"
              @click="navigateTo(menu.path || '/home')"
            >
              {{ menu.name }}
            </button>
          </template>
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
  background: #FFFFFF;
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

.brand-text {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 18px;
  font-weight: 500;
  color: #0D6E6E;
}

/* 导航菜单 */
.sidebar-nav {
  flex: 1;
  padding: 12px 8px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 150ms;
  color: #666666;
}

.nav-item:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.nav-item.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.nav-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
}

.nav-label {
  flex: 1;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
}

.nav-arrow {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 150ms;
}

.nav-item.expanded .nav-arrow {
  transform: rotate(90deg);
}

/* 二级菜单 */
.nav-children {
  padding-left: 20px;
  margin-bottom: 4px;
}

.nav-item.child {
  padding: 10px 16px 10px 44px;
  font-size: 13px;
  color: #666666;
}

.nav-item.child:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.nav-item.child.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

/* 三级菜单 */
.nav-children.grandchild {
  padding-left: 12px;
}

.nav-item.grandchild {
  padding: 8px 16px 8px 64px;
  font-size: 12px;
  color: #888888;
}

.nav-item.grandchild:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.nav-item.grandchild.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
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
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
}

.user-details {
  text-align: left;
}

.user-name {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #1A1A1A;
}

.user-role {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #888888;
}

.logout-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #666666;
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
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

/* 二级菜单栏 */
.sub-navbar {
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 24px;
  height: 48px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.sub-nav-title {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 600;
  color: #0D6E6E;
  padding-right: 24px;
  border-right: 1px solid #E5E5E5;
}

.sub-nav-menu {
  display: flex;
  gap: 4px;
}

.sub-nav-item {
  padding: 8px 16px;
  background: transparent;
  border: none;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.sub-nav-item:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.sub-nav-item.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

/* 主内容区域 */
.pc-main {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
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
  font-family: 'Newsreader', Georgia, serif;
  font-size: 18px;
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

.mobile-nav-group {
  margin-bottom: 4px;
}

.mobile-nav-parent {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #666666;
  cursor: pointer;
  border-radius: 8px;
  transition: all 150ms;
}

.mobile-nav-parent:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.mobile-nav-parent.expanded {
  color: #0D6E6E;
}

.mobile-nav-parent .expand-arrow {
  transition: transform 200ms ease;
}

.mobile-nav-parent.expanded .expand-arrow {
  transform: rotate(180deg);
}

.mobile-nav-children {
  overflow: hidden;
}

.mobile-nav-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 12px 16px;
  background: transparent;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
  text-align: left;
}

.mobile-nav-item:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.mobile-nav-item.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.mobile-nav-item.child {
  padding-left: 32px;
  font-weight: 400;
}

.mobile-nav-item.child.group-label {
  justify-content: space-between;
  color: #555555;
  font-weight: 500;
}

.mobile-nav-subgroup {
  width: 100%;
}

.mobile-nav-grandchildren {
  overflow: hidden;
}

.mobile-nav-item.grandchild {
  padding-left: 48px;
  font-size: 13px;
  color: #888888;
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
