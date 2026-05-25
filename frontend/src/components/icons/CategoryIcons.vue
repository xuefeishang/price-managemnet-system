<script setup lang="ts">
/**
 * 分类图标组件
 * 统一采用工业几何线性图标：圆角端点、轻填充、弱高光。
 */
withDefaults(defineProps<{
  icon: string
  size?: number
  color?: string
}>(), {
  size: 24,
  color: 'currentColor'
})
</script>

<template>
  <svg
    v-if="icon === 'gold_ingot'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M7.4 8.4L9.1 5.2H14.9L16.6 8.4" :stroke="color" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M5.2 9.4H18.8L20.2 17.6H3.8L5.2 9.4Z" :fill="color" opacity="0.14"/>
    <path d="M5.2 9.4H18.8L20.2 17.6H3.8L5.2 9.4Z" :stroke="color" stroke-width="1.9" stroke-linejoin="round"/>
    <path d="M8 13H12.6M14.9 13H16.2" :stroke="color" stroke-width="1.7" stroke-linecap="round" opacity="0.55"/>
  </svg>

  <svg
    v-else-if="icon === 'silver_bar'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M5.4 7.2H18.6L20 16.8H4L5.4 7.2Z" :fill="color" opacity="0.12"/>
    <path d="M5.4 7.2H18.6L20 16.8H4L5.4 7.2Z" :stroke="color" stroke-width="1.9" stroke-linejoin="round"/>
    <path d="M7.2 10.4H16.8M8.2 13.5H14.4" :stroke="color" stroke-width="1.6" stroke-linecap="round" opacity="0.58"/>
  </svg>

  <svg
    v-else-if="icon === 'copper_coil'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="8" :fill="color" opacity="0.1"/>
    <circle cx="12" cy="12" r="8" :stroke="color" stroke-width="1.9"/>
    <circle cx="12" cy="12" r="4.9" :stroke="color" stroke-width="1.8" opacity="0.75"/>
    <circle cx="12" cy="12" r="2" :fill="color" opacity="0.22"/>
    <path d="M12 4V6.2M12 17.8V20M4 12H6.2M17.8 12H20" :stroke="color" stroke-width="1.5" stroke-linecap="round" opacity="0.55"/>
  </svg>

  <svg
    v-else-if="icon === 'iron_ore'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M4.6 14.8L7.4 6.8L14.2 5.2L19.6 10.8L17.2 18.8H8.3L4.6 14.8Z" :fill="color" opacity="0.13"/>
    <path d="M4.6 14.8L7.4 6.8L14.2 5.2L19.6 10.8L17.2 18.8H8.3L4.6 14.8Z" :stroke="color" stroke-width="1.9" stroke-linejoin="round"/>
    <path d="M8.7 10.1L12.3 8.4L15.9 10.8M9.1 15.3L12.8 16.4L16 14.2" :stroke="color" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>
  </svg>

  <svg
    v-else-if="icon === 'aluminum_block'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M6 7.2L12 4L18 7.2V16.8L12 20L6 16.8V7.2Z" :fill="color" opacity="0.12"/>
    <path d="M6 7.2L12 4L18 7.2V16.8L12 20L6 16.8V7.2Z" :stroke="color" stroke-width="1.9" stroke-linejoin="round"/>
    <path d="M12 10.4L18 7.2M12 10.4V20M12 10.4L6 7.2" :stroke="color" stroke-width="1.5" stroke-linecap="round" opacity="0.58"/>
    <path d="M8.5 15.1L12 16.9L15.5 15.1" :stroke="color" stroke-width="1.4" stroke-linecap="round" opacity="0.45"/>
  </svg>

  <svg
    v-else-if="icon === 'rare_element'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <circle cx="12" cy="12" r="3" :fill="color" opacity="0.18"/>
    <circle cx="12" cy="12" r="2" :stroke="color" stroke-width="1.7"/>
    <ellipse cx="12" cy="12" rx="8.4" ry="3.5" :stroke="color" stroke-width="1.5" opacity="0.72"/>
    <ellipse cx="12" cy="12" rx="8.4" ry="3.5" :stroke="color" stroke-width="1.5" opacity="0.72" transform="rotate(60 12 12)"/>
    <ellipse cx="12" cy="12" rx="8.4" ry="3.5" :stroke="color" stroke-width="1.5" opacity="0.72" transform="rotate(120 12 12)"/>
    <circle cx="19.3" cy="12" r="1.1" :fill="color"/>
  </svg>

  <svg
    v-else-if="icon === 'cube_ore'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M12 3.8L19.3 7.9V16.1L12 20.2L4.7 16.1V7.9L12 3.8Z" :fill="color" opacity="0.1"/>
    <path d="M12 3.8L19.3 7.9V16.1L12 20.2L4.7 16.1V7.9L12 3.8Z" :stroke="color" stroke-width="1.9" stroke-linejoin="round"/>
    <path d="M12 12L19.3 7.9M12 12V20.2M12 12L4.7 7.9" :stroke="color" stroke-width="1.55" stroke-linecap="round"/>
    <path d="M8.2 6.7L12 8.8L15.8 6.7" :stroke="color" stroke-width="1.35" stroke-linecap="round" opacity="0.5"/>
  </svg>

  <svg
    v-else-if="icon === 'bolt_metal'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M13.2 3.2L5.8 13.1H11L10.1 20.8L18.6 9.8H13L13.2 3.2Z" :fill="color" opacity="0.12"/>
    <path d="M13.2 3.2L5.8 13.1H11L10.1 20.8L18.6 9.8H13L13.2 3.2Z" :stroke="color" stroke-width="1.9" stroke-linejoin="round"/>
    <path d="M11.5 7.4L9.2 11H12.7L12.1 15.2" :stroke="color" stroke-width="1.35" stroke-linecap="round" opacity="0.58"/>
  </svg>

  <svg
    v-else-if="icon === 'cart_trade'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M4 5.5H6.2L8 15.1H18.1L19.7 8.2H7" :stroke="color" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M8 15.1H18.1L19.1 10.8H8.9" :fill="color" opacity="0.1"/>
    <circle cx="10.1" cy="19" r="1.35" :stroke="color" stroke-width="1.7"/>
    <circle cx="17" cy="19" r="1.35" :stroke="color" stroke-width="1.7"/>
    <path d="M11 11.5H15.8M13.4 9.1V13.9" :stroke="color" stroke-width="1.45" stroke-linecap="round" opacity="0.58"/>
  </svg>

  <svg
    v-else-if="icon === 'bar_index'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M5 19.2H20" :stroke="color" stroke-width="1.6" stroke-linecap="round" opacity="0.5"/>
    <path d="M6.2 18.8V11.2M12 18.8V5.5M17.8 18.8V8.7" :stroke="color" stroke-width="2" stroke-linecap="round"/>
    <path d="M5.2 10.2L10.3 7.7L13.8 10.2L19.2 6.8" :stroke="color" stroke-width="1.55" stroke-linecap="round" stroke-linejoin="round" opacity="0.68"/>
    <circle cx="19.2" cy="6.8" r="1.1" :fill="color"/>
  </svg>

  <svg
    v-else-if="icon === 'flask'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M9 3.8H15M10.2 3.8V9.3L5.7 17.6C4.9 19.2 6 21 7.8 21H16.2C18 21 19.1 19.2 18.3 17.6L13.8 9.3V3.8" :stroke="color" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M8.1 16.2H15.9L17 18.4C17.3 19 16.9 19.7 16.2 19.7H7.8C7.1 19.7 6.7 19 7 18.4L8.1 16.2Z" :fill="color" opacity="0.13"/>
    <path d="M8.2 16.2H15.8" :stroke="color" stroke-width="1.5" stroke-linecap="round" opacity="0.58"/>
    <circle cx="10.2" cy="18.1" r="0.9" :fill="color" opacity="0.65"/>
    <circle cx="14" cy="18.3" r="0.75" :fill="color" opacity="0.45"/>
  </svg>

  <svg
    v-else-if="icon === 'recycle_steel'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M8.1 7.7C9.2 5.9 10.7 5 12.7 5C15.8 5 18.2 7.4 18.2 10.5" :stroke="color" stroke-width="1.9" stroke-linecap="round"/>
    <path d="M18.2 6.3V10.5H14" :stroke="color" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M15.9 16.3C14.8 18.1 13.3 19 11.3 19C8.2 19 5.8 16.6 5.8 13.5" :stroke="color" stroke-width="1.9" stroke-linecap="round"/>
    <path d="M5.8 17.7V13.5H10" :stroke="color" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/>
    <path d="M9.2 10.2H14.8V13.8H9.2V10.2Z" :fill="color" opacity="0.12"/>
    <path d="M9.2 10.2H14.8V13.8H9.2V10.2Z" :stroke="color" stroke-width="1.35" stroke-linejoin="round" opacity="0.55"/>
  </svg>

  <svg
    v-else-if="icon === 'alloy_grid'"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M6 5H10V9H6V5ZM14 5H18V9H14V5ZM6 15H10V19H6V15ZM14 15H18V19H14V15Z" :fill="color" opacity="0.12"/>
    <path d="M6 5H10V9H6V5ZM14 5H18V9H14V5ZM6 15H10V19H6V15ZM14 15H18V19H14V15Z" :stroke="color" stroke-width="1.8" stroke-linejoin="round"/>
    <path d="M10 7H14M8 9V15M16 9V15M10 17H14" :stroke="color" stroke-width="1.45" stroke-linecap="round" opacity="0.62"/>
    <circle cx="12" cy="12" r="1.3" :fill="color" opacity="0.65"/>
  </svg>

  <svg
    v-else
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    aria-hidden="true"
  >
    <path d="M5 6.5L12 3L19 6.5V17.5L12 21L5 17.5V6.5Z" :fill="color" opacity="0.1"/>
    <path d="M5 6.5L12 3L19 6.5V17.5L12 21L5 17.5V6.5Z" :stroke="color" stroke-width="1.9" stroke-linejoin="round"/>
    <path d="M9 11.5H15M9 14.5H13" :stroke="color" stroke-width="1.5" stroke-linecap="round" opacity="0.58"/>
  </svg>
</template>
