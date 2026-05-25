<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import type { MenuNode } from './menuUtils'

const props = defineProps<{
  title?: string
  items: MenuNode[]
  activePath: string
}>()

const emit = defineEmits<{
  (e: 'navigate', path: string): void
}>()

const openMenuId = ref<number | null>(null)

const isActive = (menu: MenuNode): boolean => {
  if (menu.path && menu.path === props.activePath) return true
  return menu.children.some(child => isActive(child))
}

const navigate = (path: string | null) => {
  if (!path) return
  openMenuId.value = null
  emit('navigate', path)
}

const handleItemClick = (item: MenuNode) => {
  if (item.path) {
    navigate(item.path)
    return
  }
  if (item.children.length > 0) {
    openMenuId.value = openMenuId.value === item.id ? null : item.id
  }
}

const handleDocumentClick = (event: MouseEvent) => {
  const target = event.target as HTMLElement | null
  if (!target?.closest('.context-sub-nav')) {
    openMenuId.value = null
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    openMenuId.value = null
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="context-sub-nav">
    <div v-if="title" class="context-title">{{ title }}</div>
    <nav class="context-items" aria-label="上下文菜单">
      <div v-for="item in items" :key="item.id" class="context-item-wrap">
        <button
          class="context-item"
          :class="{ active: isActive(item), open: openMenuId === item.id }"
          type="button"
          @click.stop="handleItemClick(item)"
        >
          <span>{{ item.name }}</span>
          <svg v-if="item.children.length > 0" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </button>
        <div v-if="item.children.length > 0 && openMenuId === item.id" class="context-dropdown">
          <button
            v-if="item.path"
            class="dropdown-item"
            type="button"
            @click.stop="navigate(item.path)"
          >
            进入{{ item.name }}
          </button>
          <button
            v-for="child in item.children"
            :key="child.id"
            class="dropdown-item"
            :class="{ active: isActive(child) }"
            type="button"
            @click.stop="navigate(child.path)"
          >
            {{ child.name }}
          </button>
        </div>
      </div>
    </nav>
  </div>
</template>

<style scoped>
.context-sub-nav {
  height: 48px;
  background: var(--app-nav-bg);
  border-bottom: 1px solid #E5E5E5;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 24px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.context-title {
  max-width: 180px;
  padding-right: 24px;
  border-right: 1px solid #E5E5E5;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: #0D6E6E;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-items {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
}

.context-items::-webkit-scrollbar {
  display: none;
}

.context-item-wrap {
  position: relative;
  flex-shrink: 0;
}

.context-item {
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #666666;
  font-family: var(--font-body);
  font-size: var(--font-size-sm);
  font-weight: 500;
  line-height: 1.35;
  cursor: pointer;
  transition: all 150ms;
}

.context-item:hover,
.context-item.open {
  background: #F5F5F5;
  color: #1A1A1A;
}

.context-item.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}

.context-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  min-width: 160px;
  padding: 6px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.14);
  z-index: 80;
}

.dropdown-item {
  width: 100%;
  min-height: 36px;
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #555555;
  font-size: var(--font-size-sm);
  line-height: 1.35;
  text-align: left;
  cursor: pointer;
  transition: all 150ms;
}

.dropdown-item:hover,
.dropdown-item.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
}
</style>
