<script setup lang="ts">
// Props

const props = defineProps<{
  activeSection: string
  isMobile?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:activeSection', value: string): void
}>()

// 一级导航配置
const sections = [
  { key: 'overview', label: '总览', icon: '📊' },
  { key: 'brand', label: '品牌', icon: '🏷️' },
  { key: 'color', label: '色彩', icon: '🎨' },
  { key: 'typography', label: '排版', icon: '📝' },
  { key: 'layout', label: '布局', icon: '📐' },
  { key: 'home', label: '首页体验', icon: '🏠' },
  { key: 'category', label: '分类视觉', icon: '🏷️' },
  { key: 'version', label: '版本恢复', icon: '⏪' }
]

// 切换导航
const switchSection = (key: string) => {
  emit('update:activeSection', key)
}
</script>

<template>
  <!-- PC 端垂直导航 -->
  <nav class="section-nav" v-if="!isMobile">
    <div
      v-for="section in sections"
      :key="section.key"
      class="nav-item"
      :class="{ active: activeSection === section.key }"
      @click="switchSection(section.key)"
    >
      <span class="nav-icon">{{ section.icon }}</span>
      <span class="nav-label">{{ section.label }}</span>
    </div>
  </nav>

  <!-- 移动端横向导航 -->
  <nav class="mobile-nav" v-else>
    <div class="nav-scroll">
      <div
        v-for="section in sections"
        :key="section.key"
        class="mobile-nav-item"
        :class="{ active: activeSection === section.key }"
        @click="switchSection(section.key)"
      >
        {{ section.label }}
      </div>
    </div>
  </nav>
</template>

<style scoped>
/* PC 端垂直导航 */
.section-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  height: fit-content;
  position: sticky;
  top: 96px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  font-size: var(--font-size-sm);
  color: #666666;
  border-radius: 8px;
  cursor: pointer;
  transition: all 150ms;
}

.nav-item:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.nav-item.active {
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
  font-weight: 500;
}

.nav-icon {
  font-size: 16px;
}

.nav-label {
  flex: 1;
}

/* 移动端横向导航 */
.mobile-nav {
  margin-bottom: 16px;
}

.nav-scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 8px;
  -webkit-overflow-scrolling: touch;
}

.nav-scroll::-webkit-scrollbar {
  display: none;
}

.mobile-nav-item {
  flex-shrink: 0;
  padding: 8px 16px;
  font-size: var(--font-size-sm);
  color: #666666;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 20px;
  cursor: pointer;
  transition: all 150ms;
}

.mobile-nav-item.active {
  background: #0D6E6E;
  border-color: #0D6E6E;
  color: #FFFFFF;
}
</style>