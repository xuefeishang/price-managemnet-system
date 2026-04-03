<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  type?: 'no-data' | 'no-result' | 'error' | 'loading'
  title?: string
  description?: string
  actionText?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'no-data',
  title: '',
  description: '',
  actionText: ''
})

const emit = defineEmits<{
  action: []
}>()

const defaultMessages = computed(() => {
  switch (props.type) {
    case 'no-data':
      return {
        title: '暂无数据',
        description: '当前没有可显示的数据',
        showAction: false
      }
    case 'no-result':
      return {
        title: '搜索无结果',
        description: '没有找到匹配的结果，请尝试其他关键词',
        showAction: true,
        actionText: '清除搜索'
      }
    case 'error':
      return {
        title: '加载失败',
        description: '数据加载失败，请稍后重试',
        showAction: true,
        actionText: '重新加载'
      }
    case 'loading':
      return {
        title: '加载中',
        description: '正在加载数据...',
        showAction: false
      }
    default:
      return {
        title: '暂无数据',
        description: '',
        showAction: false
      }
  }
})

const displayTitle = computed(() => props.title || defaultMessages.value.title)
const displayDescription = computed(() => props.description || defaultMessages.value.description)
const displayActionText = computed(() => props.actionText || defaultMessages.value.actionText)

const handleAction = () => {
  emit('action')
}
</script>

<template>
  <div class="empty-state">
    <div class="empty-icon">
      <!-- 无数据 -->
      <svg v-if="type === 'no-data'" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
        <line x1="3" y1="9" x2="21" y2="9"/>
        <line x1="9" y1="21" x2="9" y2="9"/>
      </svg>

      <!-- 无搜索结果 -->
      <svg v-else-if="type === 'no-result'" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <circle cx="11" cy="11" r="8"/>
        <line x1="21" y1="21" x2="16.65" y2="16.65"/>
        <line x1="8" y1="8" x2="14" y2="14"/>
        <line x1="14" y1="8" x2="8" y2="14"/>
      </svg>

      <!-- 错误 -->
      <svg v-else-if="type === 'error'" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
        <circle cx="12" cy="12" r="10"/>
        <line x1="12" y1="8" x2="12" y2="12"/>
        <line x1="12" y1="16" x2="12.01" y2="16"/>
      </svg>

      <!-- 加载中 -->
      <svg v-else-if="type === 'loading'" width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" class="loading-icon">
        <circle cx="12" cy="12" r="10"/>
        <path d="M12 2a10 10 0 0 1 10 10"/>
      </svg>
    </div>

    <h3 class="empty-title">{{ displayTitle }}</h3>
    <p v-if="displayDescription" class="empty-description">{{ displayDescription }}</p>

    <button
      v-if="defaultMessages.showAction || actionText"
      class="empty-action"
      @click="handleAction"
    >
      {{ displayActionText }}
    </button>
  </div>
</template>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-icon {
  color: #D1D5DB;
  margin-bottom: 24px;
}

.empty-icon .loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.empty-title {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 8px 0;
}

.empty-description {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #888888;
  margin: 0 0 24px 0;
  max-width: 280px;
}

.empty-action {
  padding: 10px 24px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background 150ms;
}

.empty-action:hover {
  background: #0D8A8A;
}
</style>
