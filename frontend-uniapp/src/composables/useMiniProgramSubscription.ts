import { computed, ref } from 'vue'
import {
  getMiniProgramSubscriptions,
  updateMiniProgramSubscriptions
} from '@/api/notifications'
import { getDictValue } from '@/composables/useDict'
import type { NotificationMiniProgramSubscription } from '@/types'

const defaultSubscription = (): NotificationMiniProgramSubscription => ({
  enabled: false,
  configured: false,
  openidBound: false,
  templates: []
})

const miniSubscription = ref<NotificationMiniProgramSubscription>(defaultSubscription())
const subscriptionLoading = ref(false)

const canRequestSubscribe = computed(() => {
  return miniSubscription.value.enabled
    && miniSubscription.value.configured
    && miniSubscription.value.openidBound
    && miniSubscription.value.templates.length > 0
})

const authorizedTemplateCount = computed(() => {
  return miniSubscription.value.templates.filter(item => item.authorized).length
})

const subscribeDescription = computed(() => {
  if (!miniSubscription.value.enabled) return '订阅消息功能未启用'
  if (!miniSubscription.value.configured) return '订阅消息模板尚未配置'
  if (!miniSubscription.value.openidBound) return '请先使用微信登录绑定小程序身份'
  if (miniSubscription.value.templates.length === 0) return '暂无可订阅的消息模板'
  if (authorizedTemplateCount.value > 0) {
    return `已授权 ${authorizedTemplateCount.value} 类消息，可继续补充订阅授权`
  }
  return '授权后可收到价格发布和系统公告提醒'
})

const loadMiniSubscriptions = async () => {
  subscriptionLoading.value = true
  try {
    const response = await getMiniProgramSubscriptions()
    miniSubscription.value = response.data || defaultSubscription()
  } catch {
    miniSubscription.value = defaultSubscription()
  } finally {
    subscriptionLoading.value = false
  }
}

const requestMiniProgramSubscribe = async () => {
  if (!canRequestSubscribe.value) {
    uni.showToast({ title: subscribeDescription.value, icon: 'none' })
    return false
  }

  const tmplIds = miniSubscription.value.templates.map(item => item.templateId)

  // #ifdef MP-WEIXIN
  try {
    const result = await uni.requestSubscribeMessage({ tmplIds })
    const responseMap = result as unknown as Record<string, string>
    const results = miniSubscription.value.templates.map(template => ({
      notificationType: template.notificationType,
      templateId: template.templateId,
      result: responseMap[template.templateId] || 'unknown'
    }))
    const response = await updateMiniProgramSubscriptions({ results })
    miniSubscription.value = response.data || miniSubscription.value
    uni.showToast({ title: '订阅状态已更新', icon: 'success' })
    return true
  } catch {
    uni.showToast({ title: '订阅授权未完成', icon: 'none' })
    return false
  }
  // #endif

  // #ifndef MP-WEIXIN
  uni.showToast({ title: '请在微信小程序中订阅消息', icon: 'none' })
  return false
  // #endif
}

const subscriptionTemplateLabel = (notificationType?: string) => {
  return getDictValue('notification_type', notificationType)
}

const subscriptionStatusLabel = (status?: string) => {
  return getDictValue('notification_mini_subscription_status', status)
}

export const useMiniProgramSubscription = () => ({
  miniSubscription,
  subscriptionLoading,
  canRequestSubscribe,
  authorizedTemplateCount,
  subscribeDescription,
  loadMiniSubscriptions,
  requestMiniProgramSubscribe,
  subscriptionTemplateLabel,
  subscriptionStatusLabel
})
