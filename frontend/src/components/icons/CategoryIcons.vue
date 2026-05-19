<script setup lang="ts">
/**
 * 分类图标组件
 * 提供6种矿产品分类SVG图标：金锭、银条、铜线圈、铁矿石、铝块、稀土元素
 * 支持动态颜色和尺寸
 */
const props = withDefaults(defineProps<{
  icon: string
  size?: number
  color?: string
}>(), {
  size: 24,
  color: 'currentColor'
})

const iconSize = props.size
const iconColor = props.color
</script>

<template>
  <!-- 黄金类 - 金锭 -->
  <svg v-if="icon === 'gold_ingot'" :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none">
    <!-- 金锭主体 -->
    <rect x="3" y="10" width="18" height="8" rx="2" :fill="iconColor" opacity="0.9"/>
    <!-- 金锭顶部斜面 -->
    <path d="M5 10L8 5H16L19 10" :stroke="iconColor" stroke-width="2" fill="none"/>
    <!-- 高光 -->
    <rect x="6" y="12" width="5" height="3" rx="1" fill="#FFF" opacity="0.35"/>
    <rect x="13" y="12" width="3" height="2" rx="0.5" fill="#FFF" opacity="0.25"/>
  </svg>

  <!-- 白银类 - 银条 -->
  <svg v-else-if="icon === 'silver_bar'" :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none">
    <!-- 银条主体 -->
    <rect x="4" y="7" width="16" height="11" rx="1.5" :fill="iconColor"/>
    <!-- 顶部高光条 -->
    <rect x="6" y="9" width="12" height="2" rx="0.5" fill="#FFF" opacity="0.45"/>
    <!-- 底部纹理 -->
    <rect x="6" y="13" width="8" height="3" rx="0.5" fill="#FFF" opacity="0.2"/>
    <!-- 边框高光 -->
    <rect x="4" y="7" width="16" height="11" rx="1.5" :stroke="iconColor" stroke-width="0.5" fill="none" opacity="0.5"/>
  </svg>

  <!-- 铜类 - 铜线圈 -->
  <svg v-else-if="icon === 'copper_coil'" :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none">
    <!-- 外圈 -->
    <circle cx="12" cy="12" r="8" :stroke="iconColor" stroke-width="2.5" fill="none"/>
    <!-- 中圈 -->
    <circle cx="12" cy="12" r="5" :stroke="iconColor" stroke-width="2" fill="none" opacity="0.7"/>
    <!-- 内圈 -->
    <circle cx="12" cy="12" r="2" :fill="iconColor"/>
    <!-- 连接线（表示线圈绕制） -->
    <path d="M12 4V7M12 17V20M4 12H7M17 12H20" :stroke="iconColor" stroke-width="1.5" opacity="0.5"/>
  </svg>

  <!-- 铁矿石 -->
  <svg v-else-if="icon === 'iron_ore'" :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none">
    <!-- 不规则矿石形状 -->
    <path d="M4 15L7 6L15 5L20 12L17 19L8 19L4 15Z" :fill="iconColor"/>
    <!-- 矿石纹理 -->
    <path d="M8 9L12 7L16 10" :stroke="iconColor" stroke-width="1.5" opacity="0.5" fill="none"/>
    <!-- 高光点 -->
    <circle cx="10" cy="11" r="2" fill="#FFF" opacity="0.25"/>
    <circle cx="14" cy="14" r="1.5" fill="#FFF" opacity="0.2"/>
  </svg>

  <!-- 铝块 -->
  <svg v-else-if="icon === 'aluminum_block'" :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none">
    <!-- 铝块主体 -->
    <rect x="5" y="5" width="14" height="14" rx="2" :fill="iconColor"/>
    <!-- 内部凹陷效果 -->
    <rect x="7" y="7" width="10" height="10" rx="1" fill="#FFF" opacity="0.12"/>
    <!-- 中线纹理 -->
    <line x1="7" y1="12" x2="17" y2="12" :stroke="iconColor" stroke-width="1" opacity="0.4"/>
    <!-- 边框高光 -->
    <rect x="5" y="5" width="14" height="14" rx="2" :stroke="iconColor" stroke-width="0.5" fill="none" opacity="0.3"/>
  </svg>

  <!-- 稀土元素 -->
  <svg v-else-if="icon === 'rare_element'" :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none">
    <!-- 外圈（原子轨道） -->
    <circle cx="12" cy="12" r="9" :stroke="iconColor" stroke-width="1.5" fill="none"/>
    <!-- 电子轨道线 -->
    <path d="M12 3V6M12 18V21M3 12H6M18 12H21" :stroke="iconColor" stroke-width="1.5" opacity="0.6"/>
    <!-- 中间光晕 -->
    <circle cx="12" cy="12" r="5" :fill="iconColor" opacity="0.25"/>
    <!-- 核心 -->
    <circle cx="12" cy="12" r="2.5" :fill="iconColor"/>
    <!-- 电子点 -->
    <circle cx="12" cy="4" r="1" :fill="iconColor"/>
    <circle cx="12" cy="20" r="1" :fill="iconColor"/>
    <circle cx="4" cy="12" r="1" :fill="iconColor"/>
    <circle cx="20" cy="12" r="1" :fill="iconColor"/>
  </svg>

  <!-- 默认图标 -->
  <svg v-else :width="iconSize" :height="iconSize" viewBox="0 0 24 24" fill="none">
    <rect x="4" y="4" width="16" height="16" rx="3" :stroke="iconColor" stroke-width="2" fill="none"/>
    <circle cx="12" cy="12" r="4" :fill="iconColor" opacity="0.4"/>
  </svg>
</template>
