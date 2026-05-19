<script setup lang="ts">
import { useLayout } from '@/composables/useLayout'
import { computed } from 'vue'

interface Props {
  /** 是否显示筛选栏插槽 */
  showFilter?: boolean
  /** 页面内边距（仅移动端生效） */
  mobilePadding?: 'none' | 'sm' | 'md' | 'lg'
}

const props = withDefaults(defineProps<Props>(), {
  showFilter: true,
  mobilePadding: 'md'
})

const { isPCLayout } = useLayout()

const containerClass = computed(() => [
  'min-h-screen bg-gray-50',
  isPCLayout.value ? 'flex flex-col gap-lg' : 'flex flex-col',
  !isPCLayout.value && props.mobilePadding !== 'none'
    ? `p-${props.mobilePadding}` : ''
])
</script>

<template>
  <div :class="containerClass">
    <!-- 页面头部 -->
    <header v-if="$slots.header" class="header-row">
      <slot name="header" />
    </header>

    <!-- 筛选栏 -->
    <div v-if="$slots.filter && showFilter" class="filter">
      <slot name="filter" />
    </div>

    <!-- 主内容 -->
    <main class="flex-1 flex flex-col gap-md">
      <slot />
    </main>

    <!-- 底部 -->
    <footer v-if="$slots.footer">
      <slot name="footer" />
    </footer>
  </div>
</template>
