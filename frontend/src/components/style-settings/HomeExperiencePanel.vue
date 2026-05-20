<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { getDictByCategory } from '@/composables/useDict'
import { updateDict } from '@/api/dict'

// 首页布局配置
interface HomeLayoutConfig {
  layoutMode: 'dashboard' | 'simple'
  cardColumns: number
  cardColumnsMobile: number
  showTrendChart: boolean
  showAlerts: boolean
  featuredProductCount: number
}

// 数值字段类型
type NumericField = 'cardColumns' | 'cardColumnsMobile' | 'featuredProductCount'

// 字段到字典键的映射
const fieldToDictKey: Record<NumericField, string> = {
  cardColumns: 'card_columns',
  cardColumnsMobile: 'card_columns_mobile',
  featuredProductCount: 'featured_product_count'
}

// 首页组件配置
interface HomeWidget {
  key: string
  name: string
  enabled: boolean
  order: number
  config?: Record<string, any>
}

const layoutConfig = ref<HomeLayoutConfig>({
  layoutMode: 'dashboard',
  cardColumns: 4,
  cardColumnsMobile: 2,
  showTrendChart: true,
  showAlerts: true,
  featuredProductCount: 6
})

const widgets = ref<HomeWidget[]>([])
const loading = ref(false)
const saving = ref(false)

// 预设组件列表
const defaultWidgets: HomeWidget[] = [
  { key: 'summary_stats', name: '经营摘要', enabled: true, order: 1 },
  { key: 'core_metrics', name: '核心指标', enabled: true, order: 2 },
  { key: 'trend_chart', name: '趋势分析', enabled: true, order: 3 },
  { key: 'product_list', name: '产品列表', enabled: true, order: 4 },
  { key: 'price_alerts', name: '价格预警', enabled: false, order: 5 }
]

// 加载配置（字典已在 Layout.vue 预加载）
const loadConfig = async () => {
  loading.value = true
  try {
    // 加载布局配置
    const layoutDicts = getDictByCategory('home_layout')
    layoutDicts.forEach(dict => {
      const extraVal = dict.extraValue || ''
      switch (dict.dictKey) {
        case 'card_columns':
          layoutConfig.value.cardColumns = parseInt(extraVal) || 4
          break
        case 'card_columns_mobile':
          layoutConfig.value.cardColumnsMobile = parseInt(extraVal) || 2
          break
        case 'featured_product_count':
          layoutConfig.value.featuredProductCount = parseInt(extraVal) || 6
          break
        case 'show_trend_chart':
          layoutConfig.value.showTrendChart = extraVal === 'true'
          break
        case 'show_alerts':
          layoutConfig.value.showAlerts = extraVal === 'true'
          break
      }
    })

    // 加载组件配置
    const widgetDicts = getDictByCategory('home_widget')
    if (widgetDicts.length > 0) {
      widgets.value = widgetDicts
        .filter(d => d.status === 'ACTIVE')
        .map(dict => {
          const config = dict.extraValue ? JSON.parse(dict.extraValue) : {}
          return {
            key: dict.dictKey,
            name: dict.dictValue,
            enabled: config.enabled ?? true,
            order: config.order ?? 0,
            config
          }
        })
        .sort((a, b) => a.order - b.order)
    } else {
      // 使用默认组件
      widgets.value = defaultWidgets
    }
  } catch (error) {
    console.error('Failed to load home config:', error)
    showToast('加载配置失败')
  } finally {
    loading.value = false
  }
}

// 保存布局配置
const saveLayoutConfig = async (key: string, value: string) => {
  saving.value = true
  try {
    const dicts = getDictByCategory('home_layout')
    const dict = dicts.find(d => d.dictKey === key)
    if (dict) {
      await updateDict(dict.id, {
        extraValue: value
      })
      showToast({ message: '已保存', position: 'top', duration: 1000 })
    }
  } catch (error) {
    console.error('Failed to save layout config:', error)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

// 保存组件配置
const saveWidgetConfig = async (widget: HomeWidget) => {
  saving.value = true
  try {
    const dicts = getDictByCategory('home_widget')
    const dict = dicts.find(d => d.dictKey === widget.key)
    if (dict) {
      const config = {
        enabled: widget.enabled,
        order: widget.order,
        ...widget.config
      }
      await updateDict(dict.id, {
        extraValue: JSON.stringify(config)
      })
      showToast({ message: '已保存', position: 'top', duration: 1000 })
    }
  } catch (error) {
    console.error('Failed to save widget config:', error)
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}

// 组件开关
const toggleWidget = async (widget: HomeWidget) => {
  widget.enabled = !widget.enabled
  await saveWidgetConfig(widget)
}

// 组件上移
const moveUp = async (widget: HomeWidget) => {
  const index = widgets.value.findIndex(w => w.key === widget.key)
  if (index > 0) {
    const prevWidget = widgets.value[index - 1]
    widget.order = prevWidget.order
    prevWidget.order = widget.order + 1
    widgets.value.sort((a, b) => a.order - b.order)
    await Promise.all([saveWidgetConfig(widget), saveWidgetConfig(prevWidget)])
  }
}

// 组件下移
const moveDown = async (widget: HomeWidget) => {
  const index = widgets.value.findIndex(w => w.key === widget.key)
  if (index < widgets.value.length - 1) {
    const nextWidget = widgets.value[index + 1]
    widget.order = nextWidget.order
    nextWidget.order = widget.order - 1
    widgets.value.sort((a, b) => a.order - b.order)
    await Promise.all([saveWidgetConfig(widget), saveWidgetConfig(nextWidget)])
  }
}

// Stepper 增减
const increment = async (field: NumericField, max: number) => {
  const current = layoutConfig.value[field]
  if (current < max) {
    layoutConfig.value[field] = current + 1
    await saveLayoutConfig(fieldToDictKey[field], String(current + 1))
  }
}

const decrement = async (field: NumericField, min: number) => {
  const current = layoutConfig.value[field]
  if (current > min) {
    layoutConfig.value[field] = current - 1
    await saveLayoutConfig(fieldToDictKey[field], String(current - 1))
  }
}

// Switch 切换
const toggleSwitch = async (field: 'showTrendChart' | 'showAlerts') => {
  layoutConfig.value[field] = !layoutConfig.value[field]
  await saveLayoutConfig(field === 'showTrendChart' ? 'show_trend_chart' : 'show_alerts',
    layoutConfig.value[field] ? 'true' : 'false')
}

// 已启用组件数量
const enabledCount = computed(() => widgets.value.filter(w => w.enabled).length)

onMounted(() => {
  loadConfig()
})
</script>

<template>
  <div class="home-experience-panel">
    <!-- 首页布局配置 -->
    <section class="config-section">
      <h2 class="section-title">
        首页布局
        <span class="section-status">当前：{{ layoutConfig.layoutMode === 'dashboard' ? '驾驶舱' : '简洁' }}</span>
      </h2>

      <div class="layout-config">
        <!-- PC 卡片列数 -->
        <div class="config-row">
          <span class="config-label">PC 卡片列数</span>
          <div class="stepper">
            <button class="stepper-btn" @click="decrement('cardColumns', 2)">-</button>
            <span class="stepper-value">{{ layoutConfig.cardColumns }}</span>
            <button class="stepper-btn" @click="increment('cardColumns', 6)">+</button>
          </div>
        </div>

        <!-- 移动端卡片列数 -->
        <div class="config-row">
          <span class="config-label">移动端卡片列数</span>
          <div class="stepper">
            <button class="stepper-btn" @click="decrement('cardColumnsMobile', 1)">-</button>
            <span class="stepper-value">{{ layoutConfig.cardColumnsMobile }}</span>
            <button class="stepper-btn" @click="increment('cardColumnsMobile', 3)">+</button>
          </div>
        </div>

        <!-- 重点产品数量 -->
        <div class="config-row">
          <span class="config-label">重点产品数量</span>
          <div class="stepper">
            <button class="stepper-btn" @click="decrement('featuredProductCount', 0)">-</button>
            <span class="stepper-value">{{ layoutConfig.featuredProductCount }}</span>
            <button class="stepper-btn" @click="increment('featuredProductCount', 12)">+</button>
          </div>
        </div>

        <!-- 显示趋势图 -->
        <div class="config-row">
          <span class="config-label">显示趋势图</span>
          <div class="switch-control" :class="{ active: layoutConfig.showTrendChart }" @click="toggleSwitch('showTrendChart')">
            <span class="switch-slider"></span>
          </div>
        </div>

        <!-- 显示预警区 -->
        <div class="config-row">
          <span class="config-label">显示预警区</span>
          <div class="switch-control" :class="{ active: layoutConfig.showAlerts }" @click="toggleSwitch('showAlerts')">
            <span class="switch-slider"></span>
          </div>
        </div>
      </div>
    </section>

    <!-- 首页组件配置 -->
    <section class="config-section">
      <h2 class="section-title">
        首页组件
        <span class="section-status">已启用 {{ enabledCount }}/{{ widgets.length }}</span>
      </h2>

      <div class="widget-list">
        <div
          v-for="widget in widgets"
          :key="widget.key"
          class="widget-item"
          :class="{ disabled: !widget.enabled }"
        >
          <div class="widget-info">
            <div class="widget-switch" :class="{ active: widget.enabled }" @click="toggleWidget(widget)">
              <span class="switch-slider"></span>
            </div>
            <span class="widget-name">{{ widget.name }}</span>
          </div>
          <div class="widget-actions">
            <button class="action-btn" @click="moveUp(widget)" :disabled="widget.order === 1">上移</button>
            <button class="action-btn" @click="moveDown(widget)" :disabled="widget.order === widgets.length">下移</button>
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