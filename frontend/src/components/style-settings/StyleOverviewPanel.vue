<script setup lang="ts">
import { computed } from 'vue'
import { useStyleSettingsWorkbench } from '@/composables/useStyleSettingsWorkbench'
import { resolveStylePresetName } from '@/utils/stylePresetNames'

const emit = defineEmits<{
  (e: 'navigate', section: string): void
}>()

const workbench = useStyleSettingsWorkbench()

// 当前方案摘要
const currentScheme = computed(() => {
  if (!workbench.isLoaded.value) return null
  const colorKey = workbench.activeColorSchemeKey.value
  const layoutKey = workbench.activeLayoutStyleKey.value
  const fontKey = workbench.activeFontPresetKey.value
  return {
    colorScheme: resolveStylePresetName(workbench.colorSchemes.value, colorKey, '默认'),
    layoutStyle: resolveStylePresetName(workbench.layoutStyles.value, layoutKey, '默认'),
    fontPreset: resolveStylePresetName(workbench.fontPresets.value, fontKey, '标准'),
    systemName: workbench.currentSystemName.value,
    hasLogo: !!workbench.logoUrl.value
  }
})

// 健康检查项
const healthChecks = computed(() => [
  { label: '色彩方案配置', status: 'ok' },
  { label: '布局方案配置', status: 'ok' },
  { label: '字体配置', status: 'ok' },
  { label: '首页组件配置', status: 'ok' },
  { label: '分类视觉配置', status: 'ok' }
])

const navigateTo = (section: string) => {
  emit('navigate', section)
}
</script>

<template>
  <div class="style-overview-panel">
    <!-- 当前生效方案 -->
    <section class="overview-section">
      <h2 class="section-title">当前生效方案</h2>
      <div class="scheme-cards" v-if="currentScheme">
        <div class="scheme-card" @click="navigateTo('color')">
          <span class="scheme-label">色彩方案</span>
          <span class="scheme-value">{{ currentScheme.colorScheme }}</span>
          <span class="scheme-arrow">→</span>
        </div>
        <div class="scheme-card" @click="navigateTo('typography')">
          <span class="scheme-label">字号方案</span>
          <span class="scheme-value">{{ currentScheme.fontPreset }}</span>
          <span class="scheme-arrow">→</span>
        </div>
        <div class="scheme-card" @click="navigateTo('layout')">
          <span class="scheme-label">布局方案</span>
          <span class="scheme-value">{{ currentScheme.layoutStyle }}</span>
          <span class="scheme-arrow">→</span>
        </div>
        <div class="scheme-card" @click="navigateTo('brand')">
          <span class="scheme-label">系统名称</span>
          <span class="scheme-value">{{ currentScheme.systemName }}</span>
          <span class="scheme-arrow">→</span>
        </div>
      </div>
    </section>

    <!-- 保存状态 -->
    <section class="overview-section">
      <h2 class="section-title">保存状态</h2>
      <div class="status-info">
        <div class="status-row">
          <span class="status-label">最近保存</span>
          <span class="status-value">
            {{ workbench.lastSavedAt.value ? new Date(workbench.lastSavedAt.value).toLocaleString('zh-CN') : '暂无记录' }}
          </span>
        </div>
        <div class="status-row">
          <span class="status-label">变更方式</span>
          <span class="status-value">先进入草稿</span>
        </div>
        <div class="status-row">
          <span class="status-label">生效方式</span>
          <span class="status-value">点击顶部保存配置</span>
        </div>
      </div>
    </section>

    <!-- 配置健康检查 -->
    <section class="overview-section">
      <h2 class="section-title">配置健康检查</h2>
      <div class="health-checks">
        <div
          v-for="(check, index) in healthChecks"
          :key="index"
          class="health-item"
          :class="check.status"
        >
          <span class="health-icon">{{ check.status === 'ok' ? '✓' : '!' }}</span>
          <span class="health-label">{{ check.label }}</span>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
.style-overview-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.overview-section {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 24px;
}

.section-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
}

/* 方案卡片 */
.scheme-cards {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.scheme-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  background: #FAFAFA;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 150ms;
}

.scheme-card:hover {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.05);
}

.scheme-label {
  font-size: var(--font-size-xs);
  color: #888888;
}

.scheme-value {
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
}

.scheme-arrow {
  align-self: flex-end;
  font-size: var(--font-size-xs);
  color: #0D6E6E;
}

/* 状态信息 */
.status-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #F5F5F5;
}

.status-row:last-child {
  border-bottom: none;
}

.status-label {
  font-size: var(--font-size-sm);
  color: #666666;
}

.status-value {
  font-size: var(--font-size-sm);
  color: #1A1A1A;
}

.status-ok {
  color: #10B981;
}

/* 健康检查 */
.health-checks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.health-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #FAFAFA;
  border-radius: 6px;
}

.health-item.ok .health-icon {
  color: #10B981;
}

.health-item.warning .health-icon {
  color: #F59E0B;
}

.health-item.error .health-icon {
  color: #EF4444;
}

.health-icon {
  font-size: 14px;
  font-weight: 600;
}

.health-label {
  font-size: var(--font-size-sm);
  color: #1A1A1A;
}

@media (max-width: 768px) {
  .scheme-cards {
    grid-template-columns: 1fr;
  }
}
</style>
