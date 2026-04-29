<script setup lang="ts">
import { ref, computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'
import { useRouter, useRoute } from 'vue-router'
import { getRoleLabel } from '@/composables/useDict'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const isMobileMenuOpen = ref(false)

const menuItems = [
  { path: '/home', label: '首页', icon: 'home' },
  { path: '/products', label: '价格维护', icon: 'price' },
  { path: '/categories', label: '基础运维', icon: 'category' },
  { path: '/users', label: '用户管理', icon: 'users', adminOnly: true },
  { path: '/import', label: '导入导出', icon: 'import' },
  { path: '/profile', label: '个人中心', icon: 'profile' }
]

const filteredMenuItems = computed(() => {
  return menuItems.filter(item => {
    if (item.adminOnly && userStore.user?.role !== 'ADMIN') {
      return false
    }
    return true
  })
})

const isActive = (path: string) => {
  return route.path === path
}

const navigateTo = (path: string) => {
  router.push(path)
  isMobileMenuOpen.value = false
}

const handleLogout = () => {
  userStore.logoutAction()
  router.push('/login')
}
</script>

<template>
  <div class="nav-bar">
    <div class="nav-container">
      <!-- 左侧Logo和品牌 -->
      <div class="nav-left">
        <button class="menu-toggle" @click="isMobileMenuOpen = !isMobileMenuOpen">
          <svg v-if="!isMobileMenuOpen" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/>
            <line x1="3" y1="12" x2="21" y2="12"/>
            <line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
          <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/>
            <line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </button>

        <div class="brand" @click="navigateTo('/home')">
          <div class="brand-icon">
            <svg viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="4" y="4" width="24" height="24" rx="5" fill="url(#navGradient)"/>
              <path d="M10 16L13 19L22 10" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              <defs>
                <linearGradient id="navGradient" x1="4" y1="4" x2="28" y2="28" gradientUnits="userSpaceOnUse">
                  <stop stop-color="#6366f1"/>
                  <stop offset="1" stop-color="#8b5cf6"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <span class="brand-text">价格管理系统</span>
        </div>
      </div>

      <!-- 右侧菜单和用户信息 -->
      <div class="nav-right">
        <!-- 桌面端菜单 -->
        <nav class="nav-menu desktop-menu">
          <button
            v-for="item in filteredMenuItems"
            :key="item.path"
            class="nav-item"
            :class="{ active: isActive(item.path) }"
            @click="navigateTo(item.path)"
          >
            <span class="nav-label">{{ item.label }}</span>
          </button>
        </nav>

        <!-- 用户信息 -->
        <div class="user-section">
          <div class="user-info">
            <div class="user-avatar">
              {{ userStore.user?.nickname?.charAt(0) || 'U' }}
            </div>
            <div class="user-details hidden-mobile">
              <div class="user-name">{{ userStore.user?.nickname }}</div>
              <div class="user-role" :class="userStore.user?.role?.toLowerCase()">
                {{ getRoleLabel(userStore.user?.role || '') }}
              </div>
            </div>
          </div>
          <button class="logout-btn" @click="handleLogout" title="退出登录">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
              <polyline points="16 17 21 12 16 7"/>
              <line x1="21" y1="12" x2="9" y2="12"/>
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- 移动端菜单 -->
    <div class="mobile-menu" :class="{ open: isMobileMenuOpen }">
      <nav class="mobile-nav">
        <button
          v-for="item in filteredMenuItems"
          :key="item.path"
          class="mobile-nav-item"
          :class="{ active: isActive(item.path) }"
          @click="navigateTo(item.path)"
        >
          <span class="nav-icon">
            <component :is="getMenuIcon(item.icon)" />
          </span>
          <span class="nav-label">{{ item.label }}</span>
          <svg v-if="isActive(item.path)" class="active-indicator" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8.59 16.59L13.17 12 8.59 7.41 10 6l6 6-6 6-1.41-1.41z"/>
          </svg>
        </button>
      </nav>
      <div class="mobile-user-info">
        <div class="mobile-user-details">
          <div class="user-avatar large">
            {{ userStore.user?.nickname?.charAt(0) || 'U' }}
          </div>
          <div>
            <div class="user-name">{{ userStore.user?.nickname }}</div>
            <div class="user-role" :class="userStore.user?.role?.toLowerCase()">
              {{ getRoleLabel(userStore.user?.role || '') }}
            </div>
          </div>
        </div>
        <button class="mobile-logout-btn" @click="handleLogout">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          退出登录
        </button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { h } from 'vue'

// 菜单图标组件
export const getMenuIcon = (icon: string) => {
  const icons = {
    home: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z' }),
      h('polyline', { points: '9 22 9 12 15 12 15 22' })
    ]),
    price: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('line', { x1: '12', y1: '1', x2: '12', y2: '23' }),
      h('path', { d: 'M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6' })
    ]),
    category: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z' })
    ]),
    users: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2' }),
      h('circle', { cx: '9', cy: '7', r: '4' }),
      h('path', { d: 'M23 21v-2a4 4 0 0 0-3-3.87' }),
      h('path', { d: 'M16 3.13a4 4 0 0 1 0 7.75' })
    ]),
    import: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4' }),
      h('polyline', { points: '7 10 12 15 17 10' }),
      h('line', { x1: '12', y1: '15', x2: '12', y2: '3' })
    ]),
    profile: () => h('svg', { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', 'stroke-width': '2' }, [
      h('path', { d: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2' }),
      h('circle', { cx: '12', cy: '7', r: '4' })
    ])
  }
  return icons[icon as keyof typeof icons] || icons.home
}
</script>

<style scoped>
.nav-bar {
  position: sticky;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--gray-200);
  transition: all var(--transition);
}

.nav-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--spacing-lg);
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.nav-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.menu-toggle {
  display: none;
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  color: var(--gray-700);
  cursor: pointer;
  padding: 8px;
  border-radius: var(--radius);
  transition: all var(--transition-fast);
}

.menu-toggle:hover {
  background: var(--gray-100);
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  cursor: pointer;
  user-select: none;
}

.brand-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--transition);
}

.brand:hover .brand-icon {
  transform: scale(1.05);
}

.brand-text {
  font-size: 1.125rem;
  font-weight: 700;
  background: var(--gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

.nav-menu {
  display: flex;
  gap: var(--spacing-xs);
}

.nav-item {
  position: relative;
  padding: 0.5rem 1rem;
  background: transparent;
  color: var(--gray-600);
  border: none;
  border-radius: var(--radius);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.nav-item:hover {
  color: var(--gray-900);
  background: var(--gray-100);
}

.nav-item.active {
  color: var(--primary-color);
  background: rgba(99, 102, 241, 0.1);
}

.nav-item.active::after {
  content: '';
  position: absolute;
  bottom: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 24px;
  height: 3px;
  background: var(--gradient-primary);
  border-radius: 9999px;
}

.user-section {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.user-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 0.375rem 0.75rem;
  background: var(--gray-100);
  border-radius: var(--radius-md);
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--gradient-primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 0.875rem;
}

.user-avatar.large {
  width: 48px;
  height: 48px;
  font-size: 1.25rem;
}

.user-details {
  text-align: left;
}

.user-name {
  font-size: 0.875rem;
  font-weight: 600;
  color: var(--gray-900);
}

.user-role {
  font-size: 0.75rem;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 9999px;
}

.user-role.admin {
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.user-role.editor {
  background: rgba(245, 158, 11, 0.1);
  color: var(--warning-color);
}

.user-role.viewer {
  background: rgba(16, 185, 129, 0.1);
  color: var(--success-color);
}

.logout-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: var(--gray-500);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--radius);
  transition: all var(--transition-fast);
}

.logout-btn:hover {
  color: var(--error-color);
  background: rgba(239, 68, 68, 0.1);
}

/* 移动端菜单 */
.mobile-menu {
  display: none;
  position: fixed;
  top: 64px;
  left: 0;
  right: 0;
  bottom: 0;
  background: white;
  transform: translateX(-100%);
  transition: transform var(--transition);
  z-index: 999;
  overflow-y: auto;
}

.mobile-menu.open {
  transform: translateX(0);
}

.mobile-nav {
  padding: var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.mobile-nav-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--gray-700);
  font-size: 1rem;
  text-align: left;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.mobile-nav-item:hover {
  background: var(--gray-100);
}

.mobile-nav-item.active {
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.nav-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-label {
  flex: 1;
  font-weight: 500;
}

.active-indicator {
  width: 20px;
  height: 20px;
}

.mobile-user-info {
  margin-top: auto;
  padding: var(--spacing-md);
  border-top: 1px solid var(--gray-200);
}

.mobile-user-details {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-md);
}

.mobile-logout-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: rgba(239, 68, 68, 0.1);
  color: var(--error-color);
  border: none;
  border-radius: var(--radius-md);
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.mobile-logout-btn:hover {
  background: rgba(239, 68, 68, 0.15);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .nav-container {
    height: 56px;
    padding: 0 var(--spacing-md);
  }

  .menu-toggle {
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .desktop-menu {
    display: none;
  }

  .brand-text {
    font-size: 1rem;
  }

  .mobile-menu {
    display: flex;
    flex-direction: column;
  }

  .user-info {
    background: transparent;
    padding: 0;
  }

  .user-details {
    display: none;
  }
}
</style>
