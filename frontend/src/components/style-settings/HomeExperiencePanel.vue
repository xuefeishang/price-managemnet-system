<script setup lang="ts">
import { onMounted } from 'vue'
import { useHomePreviewState } from '@/composables/useHomePreviewState'

// 使用共享状态
const homeState = useHomePreviewState()

onMounted(() => {
  homeState.loadConfig()
})
</script>

<template>
  <div class="home-experience-panel">
    <!-- 首页布局配置 -->
    <section class="config-section">
      <h2 class="section-title">
        首页布局
        <span class="section-status">当前：{{ homeState.layoutConfig.value.layoutMode === 'dashboard' ? '驾驶舱' : '简洁' }}</span>
      </h2>
      <p class="section-hint">修改后进入草稿，点击顶部保存配置后生效</p>

      <div class="layout-config">
        <!-- PC 卡片列数 -->
        <div class="config-row">
          <span class="config-label">PC 卡片列数</span>
          <div class="stepper">
            <button class="stepper-btn" @click="homeState.decrement('cardColumns', 2)">-</button>
            <span class="stepper-value">{{ homeState.layoutConfig.value.cardColumns }}</span>
            <button class="stepper-btn" @click="homeState.increment('cardColumns', 6)">+</button>
          </div>
        </div>

        <!-- 移动端卡片列数 -->
        <div class="config-row">
          <span class="config-label">移动端卡片列数</span>
          <div class="stepper">
            <button class="stepper-btn" @click="homeState.decrement('cardColumnsMobile', 1)">-</button>
            <span class="stepper-value">{{ homeState.layoutConfig.value.cardColumnsMobile }}</span>
            <button class="stepper-btn" @click="homeState.increment('cardColumnsMobile', 3)">+</button>
          </div>
        </div>

        <!-- 重点产品数量 -->
        <div class="config-row">
          <span class="config-label">重点产品数量</span>
          <div class="stepper">
            <button class="stepper-btn" @click="homeState.decrement('featuredProductCount', 0)">-</button>
            <span class="stepper-value">{{ homeState.layoutConfig.value.featuredProductCount }}</span>
            <button class="stepper-btn" @click="homeState.increment('featuredProductCount', 12)">+</button>
          </div>
        </div>

        <!-- 显示重点走势 -->
        <div class="config-row">
          <span class="config-label">显示重点走势</span>
          <div class="switch-control" :class="{ active: homeState.layoutConfig.value.showTrendChart }" @click="homeState.toggleSwitch('showTrendChart')">
            <span class="switch-slider"></span>
          </div>
        </div>

        <!-- 显示预警区 -->
        <div class="config-row">
          <span class="config-label">显示预警区</span>
          <div class="switch-control" :class="{ active: homeState.layoutConfig.value.showAlerts }" @click="homeState.toggleSwitch('showAlerts')">
            <span class="switch-slider"></span>
          </div>
        </div>
      </div>
    </section>

    <!-- 首页组件配置 -->
    <section class="config-section">
      <h2 class="section-title">
        首页组件
        <span class="section-status">已启用 {{ homeState.enabledCount.value }}/{{ homeState.widgets.value.length }}</span>
      </h2>
      <p class="section-hint">修改后进入草稿，点击顶部保存配置后生效</p>

      <div class="widget-list">
        <div
          v-for="widget in homeState.widgets.value"
          :key="widget.key"
          class="widget-item"
          :class="{ disabled: !widget.enabled }"
        >
          <div class="widget-info">
            <div class="widget-switch" :class="{ active: widget.enabled }" @click="homeState.toggleWidget(widget)">
              <span class="switch-slider"></span>
            </div>
            <span class="widget-name">{{ widget.name }}</span>
          </div>
          <div class="widget-actions">
            <button class="action-btn" @click="homeState.moveUp(widget)" :disabled="widget.order === 1">上移</button>
            <button class="action-btn" @click="homeState.moveDown(widget)" :disabled="widget.order === homeState.widgets.value.length">下移</button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-experience-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.config-section {
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 12px;
  padding: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--font-size-base);
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 20px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid #F0F0F0;
}

.section-status {
  font-size: var(--font-size-xs);
  color: #666666;
  padding: 4px 12px;
  background: #F5F5F5;
  border-radius: 4px;
}

/* 配置行 */
.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #F5F5F5;
}

.config-row:last-child {
  border-bottom: none;
}

.config-label {
  font-size: var(--font-size-sm);
  color: #1A1A1A;
}

/* Stepper */
.stepper {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stepper-btn {
  width: 32px;
  height: 32px;
  border: 1px solid #E5E5E5;
  border-radius: 6px;
  background: #FFFFFF;
  font-size: 16px;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.stepper-btn:hover:not(:disabled) {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.stepper-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.stepper-value {
  width: 40px;
  text-align: center;
  font-size: var(--font-size-sm);
  font-weight: 500;
  color: #1A1A1A;
}

/* Switch */
.switch-control {
  width: 44px;
  height: 24px;
  border-radius: 12px;
  background: #E5E5E5;
  cursor: pointer;
  position: relative;
  transition: background 150ms;
}

.switch-control.active {
  background: #0D6E6E;
}

.switch-slider {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 10px;
  background: #FFFFFF;
  transition: left 150ms;
}

.switch-control.active .switch-slider {
  left: 22px;
}

/* Widget 列表 */
.widget-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.widget-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #FAFAFA;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
}

.widget-item.disabled {
  opacity: 0.6;
}

.widget-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.widget-switch {
  width: 36px;
  height: 20px;
  border-radius: 10px;
  background: #E5E5E5;
  cursor: pointer;
  position: relative;
  transition: background 150ms;
}

.widget-switch.active {
  background: #0D6E6E;
}

.widget-switch .switch-slider {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 8px;
  background: #FFFFFF;
  transition: left 150ms;
}

.widget-switch.active .switch-slider {
  left: 18px;
}

.widget-name {
  font-size: var(--font-size-sm);
  color: #1A1A1A;
}

.widget-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  padding: 6px 12px;
  font-size: var(--font-size-xs);
  border: 1px solid #E5E5E5;
  border-radius: 4px;
  background: #FFFFFF;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.action-btn:hover:not(:disabled) {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
