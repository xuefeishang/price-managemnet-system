<script setup lang="ts">
import type { MenuNode } from './menuUtils'

const props = defineProps<{
  menus: MenuNode[]
  activePath: string
  expandedIds: Set<number>
  level?: number
}>()

const emit = defineEmits<{
  (e: 'navigate', path: string): void
  (e: 'toggle', id: number): void
}>()

const levelValue = props.level ?? 1

const isExpanded = (menu: MenuNode) => props.expandedIds.has(menu.id)
const isActive = (menu: MenuNode): boolean => {
  if (menu.path && menu.path === props.activePath) return true
  return menu.children.some(child => isActive(child))
}

const handleClick = (menu: MenuNode) => {
  if (menu.path) {
    emit('navigate', menu.path)
    return
  }
  if (menu.children.length > 0) {
    emit('toggle', menu.id)
  }
}

const handleNavigate = (path: string) => emit('navigate', path)
const handleToggle = (id: number) => emit('toggle', id)
</script>

<template>
  <div class="menu-tree" :class="`level-${levelValue}`">
    <template v-for="menu in menus" :key="menu.id">
      <button
        class="menu-item"
        :class="{
          active: isActive(menu),
          expanded: isExpanded(menu),
          'has-children': menu.children.length > 0
        }"
        :style="{ '--menu-level': String(Math.min(levelValue, 4)) }"
        type="button"
        @click="handleClick(menu)"
      >
        <span v-if="levelValue === 1" class="nav-icon">
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
        <span v-if="menu.children.length > 0" class="nav-arrow">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="9 18 15 12 9 6"/>
          </svg>
        </span>
      </button>

      <SidebarMenuTree
        v-if="menu.children.length > 0 && isExpanded(menu)"
        :menus="menu.children"
        :active-path="activePath"
        :expanded-ids="expandedIds"
        :level="levelValue + 1"
        @navigate="handleNavigate"
        @toggle="handleToggle"
      />
    </template>
  </div>
</template>

<style scoped>
.menu-tree {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-tree.level-1 {
  gap: 6px;
}

.menu-item {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  padding-left: calc(12px + (var(--menu-level) - 1) * 16px);
  margin: 0;
  border: none;
  border-radius: var(--app-card-radius);
  background: transparent;
  color: var(--app-nav-text);
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition: all 150ms;
}

.menu-item:hover {
  background: rgba(13, 110, 110, 0.1);
}

.menu-item.active {
  background: rgba(13, 110, 110, 0.15);
  color: #0D6E6E;
}

.menu-tree:not(.level-1) .menu-item {
  min-height: 36px;
  padding-top: 9px;
  padding-bottom: 9px;
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 1.35;
}

.menu-tree.level-3 .menu-item,
.menu-tree.level-4 .menu-item {
  color: color-mix(in srgb, var(--app-nav-text) 78%, transparent);
}

.nav-icon {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-arrow {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: transform 150ms;
}

.menu-item.expanded .nav-arrow {
  transform: rotate(90deg);
}
</style>
