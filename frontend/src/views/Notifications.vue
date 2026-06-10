<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showToast } from 'vant'
import {
  cancelSystemNotice,
  createSystemNotice,
  getAdminNotificationDeliveries,
  getAdminNotificationRecipients,
  getAdminNotifications,
  getMiniProgramCoverage,
  getMiniProgramSubscriptionDetail,
  getMiniProgramSubscriptions,
  getNotificationChannelConfig,
  getNotificationDashboard,
  getNotificationProviderHealth,
  getNotificationThrottleRules,
  getSystemNotices,
  publishSystemNotice,
  retryAdminNotificationDelivery,
  resolveMiniProgramSubscription,
  saveNotificationChannelConfig,
  sendMiniProgramAuthorizationGuide,
  sendMiniProgramAuthorizationGuides,
  testNotificationChannelConfig,
  testNotificationChannelDelivery,
  testNotificationChannelToken
} from '@/api/notifications'
import { getDictOptions, getDictValue, loadAllDicts } from '@/composables/useDict'
import { Permission, usePermission } from '@/composables/usePermission'
import type {
  AdminNotificationSummary,
  AdminMiniProgramSubscription,
  NotificationChannelConfig,
  NotificationChannelConfigUpdateRequest,
  NotificationDashboard,
  NotificationDeliveryLog,
  NotificationMiniProgramCoverage,
  NotificationProviderHealth,
  NotificationRecipient,
  NotificationThrottleRule,
  PageResponse,
  Role,
  SystemNotice,
  SystemNoticeCreateRequest,
  SystemNoticeStatus
} from '@/types'

const loading = ref(false)
const noticeLoading = ref(false)
const detailLoading = ref(false)
const notifications = ref<AdminNotificationSummary[]>([])
const recipients = ref<NotificationRecipient[]>([])
const deliveries = ref<NotificationDeliveryLog[]>([])
const notices = ref<SystemNotice[]>([])
const dashboard = ref<NotificationDashboard | null>(null)
const providerHealth = ref<NotificationProviderHealth[]>([])
const throttleRules = ref<NotificationThrottleRule[]>([])
const miniProgramConfig = ref<NotificationChannelConfig | null>(null)
const miniProgramCoverage = ref<NotificationMiniProgramCoverage | null>(null)
const subscriptions = ref<AdminMiniProgramSubscription[]>([])
const selectedSubscription = ref<AdminMiniProgramSubscription | null>(null)
const subscriptionDetailLoading = ref(false)
const resolutionForm = reactive({
  status: 'RESOLVED' as 'OPEN' | 'RESOLVED' | 'SNOOZED' | 'FOLLOW_UP',
  remark: '',
  remindAfter: ''
})
const selectedNotification = ref<AdminNotificationSummary | null>(null)
const showNoticeEditor = ref(false)
const { hasPermission } = usePermission()

type NotificationTab = 'overview' | 'publish' | 'audit' | 'channels' | 'subscriptions'

const activeTab = ref<NotificationTab>('overview')
const selectedChannel = ref('MINI_PROGRAM')
const channelEditing = ref(false)
const channelConfigLoading = ref(false)
const subscriptionLoading = ref(false)

const tabs: Array<{ key: NotificationTab; label: string }> = [
  { key: 'overview', label: '总览' },
  { key: 'publish', label: '消息发布' },
  { key: 'audit', label: '投递审计' },
  { key: 'channels', label: '渠道配置' },
  { key: 'subscriptions', label: '订阅授权' }
]

const filters = reactive({
  keyword: '',
  type: '',
  priority: '',
  businessType: '',
  channel: '',
  deliveryStatus: ''
})

const pagination = reactive({
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0
})

const noticePagination = reactive({
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
  status: '' as '' | SystemNoticeStatus
})

const subscriptionFilters = reactive({
  keyword: '',
  role: '',
  status: ''
})

const subscriptionPagination = reactive({
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0
})

const channelConfigForm = reactive<NotificationChannelConfigUpdateRequest>({
  enabled: false,
  appId: '',
  endpointUrl: '',
  secret: '',
  timeoutMs: 5000,
  defaultPage: '',
  templates: []
})

const deliveryFilters = reactive({
  keyword: '',
  channel: '',
  status: ''
})

const deliveryPagination = reactive({
  page: 0,
  size: 3
})

const noticeForm = reactive<SystemNoticeCreateRequest>({
  title: '',
  summary: '',
  content: '',
  targetRoles: ['ADMIN'],
  channels: ['IN_APP'],
  priority: 'NORMAL',
  scheduledPublishTime: '',
  expireTime: ''
})

const typeOptions = computed(() => getDictOptions('notification_type'))
const priorityOptions = computed(() => getDictOptions('notification_priority'))
const businessTypeOptions = computed(() => getDictOptions('notification_business_type'))
const channelOptions = computed(() => getDictOptions('notification_channel'))
const deliveryStatusOptions = computed(() => getDictOptions('notification_delivery_status'))
const noticeStatusOptions = computed(() => getDictOptions('system_notice_status'))
const roleOptions = computed(() => getDictOptions('user_role'))
const canRetryNotification = computed(() => hasPermission(Permission.NOTIFICATION_RETRY))
const canCreateNotice = computed(() => hasPermission(Permission.SYSTEM_NOTICE_CREATE))
const canCancelNotice = computed(() => hasPermission(Permission.SYSTEM_NOTICE_CANCEL))
const canManageChannelConfig = computed(() => hasPermission(Permission.SYSTEM_SETTING))
const canViewSubscriptions = computed(() => hasPermission(Permission.NOTIFICATION_SUBSCRIPTION_VIEW))
const canGuideSubscription = computed(() => hasPermission(Permission.NOTIFICATION_SUBSCRIPTION_GUIDE))
const canResolveSubscription = computed(() => hasPermission(Permission.NOTIFICATION_SUBSCRIPTION_RESOLVE))
const canTestToken = computed(() => hasPermission(Permission.NOTIFICATION_TEST_TOKEN))
const canTestDelivery = computed(() => hasPermission(Permission.NOTIFICATION_TEST_DELIVERY))
const subscriptionRowStatusOptions = computed(() => getDictOptions('notification_mini_subscription_row_status'))
const resolutionStatusOptions = computed(() => getDictOptions('notification_mini_resolution_status'))
const visibleTabs = computed(() => tabs.filter(tab => tab.key !== 'subscriptions' || canViewSubscriptions.value))
const miniProgramHealth = computed(() =>
  providerHealth.value.find(provider => provider.channel === 'MINI_PROGRAM') || null
)
const miniProgramSelected = computed(() => noticeForm.channels.includes('MINI_PROGRAM'))
const miniProgramHealthLabel = computed(() => {
  const health = miniProgramHealth.value
  return getDictValue('notification_provider_health_status', health?.healthStatus || 'NOT_CONFIGURED')
})
const miniProgramReady = computed(() => {
  const health = miniProgramHealth.value
  return Boolean(health?.registered && health.configured && health.healthStatus !== 'DOWN')
})
const selectedProviderHealth = computed(() =>
  providerHealth.value.find(provider => provider.channel === selectedChannel.value) || null
)
const selectedChannelConfig = computed(() =>
  selectedChannel.value === 'MINI_PROGRAM' ? miniProgramConfig.value : null
)
const selectedChannelDiagnostics = computed(() => selectedChannelConfig.value?.diagnostics || [])
const selectedChannelTemplates = computed(() => selectedChannelConfig.value?.templates || [])
const diagnosticPassCount = computed(() =>
  selectedChannelDiagnostics.value.filter(item => item.status === 'PASS').length
)
const subscriptionPageLabel = computed(() => {
  const totalPages = subscriptionPagination.totalPages || 1
  return `${subscriptionPagination.totalElements === 0 ? 0 : subscriptionPagination.page + 1} / ${totalPages}`
})
const subscriptionRangeLabel = computed(() => {
  if (subscriptionPagination.totalElements === 0) return '共 0 条'
  const start = subscriptionPagination.page * subscriptionPagination.size + 1
  const end = Math.min((subscriptionPagination.page + 1) * subscriptionPagination.size, subscriptionPagination.totalElements)
  return `${start}-${end} / ${subscriptionPagination.totalElements} 条`
})
const channelConfigRows = computed(() => {
  const baseChannels = ['IN_APP', 'WEBHOOK', 'MINI_PROGRAM', 'APP_PUSH', 'WECHAT_WORK']
  return baseChannels.map(channel => {
    const health = providerHealth.value.find(provider => provider.channel === channel)
    const configured = channel === 'MINI_PROGRAM'
      ? Boolean(miniProgramConfig.value?.configured)
      : channel === 'IN_APP' || Boolean(health?.configured)
    return {
      channel,
      configured,
      healthStatus: channel === 'MINI_PROGRAM'
        ? miniProgramConfig.value?.healthStatus || health?.healthStatus || 'NOT_CONFIGURED'
        : health?.healthStatus || (channel === 'IN_APP' ? 'OK' : 'NOT_CONFIGURED'),
      pendingCount: health?.pendingCount || 0,
      failedCount: health?.failedCount || 0,
      lastErrorCode: health?.lastErrorCode || health?.lastStatus || ''
    }
  })
})
const miniProgramImpact = computed(() => {
  const coverage = miniProgramCoverage.value
  return {
    targetCount: coverage?.targetCount || 0,
    openidBound: coverage?.openidBound || 0,
    authorized: coverage?.authorized || 0,
    reachable: coverage?.reachable || 0,
    inAppFallback: coverage?.inAppFallback || 0,
    rejectedOrBanned: coverage?.rejectedOrBanned || 0,
    lowBalance: coverage?.lowBalance || 0
  }
})
const auditMetrics = computed(() => ({
  total: dashboard.value?.todayDeliveryCount || 0,
  success: dashboard.value?.successDeliveryCount || successfulDeliveries.value,
  failed: dashboard.value?.failedDeliveryCount || failedDeliveries.value,
  skipped: dashboard.value?.skippedDeliveryCount || 0,
  pending: dashboard.value?.outboxPendingCount || 0
}))
const subscriptionMetrics = computed(() => {
  const total = miniProgramImpact.value.targetCount || subscriptionPagination.totalElements
  const openidBound = miniProgramImpact.value.openidBound
  const miniAuthorized = miniProgramImpact.value.authorized
  const totalForRate = total || 1
  return [
    { label: '用户总数', value: total, hint: '全部启用账号', tone: 'primary' },
    { label: 'OpenID 绑定', value: openidBound, hint: `${Math.round(openidBound / totalForRate * 100)}% 已绑定`, tone: 'primary' },
    { label: '小程序模板授权', value: miniAuthorized, hint: '按当前模板统计', tone: 'primary' },
    { label: 'App 设备绑定', value: 0, hint: '渠道预留', tone: 'muted' },
    { label: '拒绝/禁用', value: miniProgramImpact.value.rejectedOrBanned, hint: '需引导或排除', tone: 'danger' }
  ]
})
const dashboardCards = computed(() => {
  const data = dashboard.value
  if (!data) return []
  return [
    { label: '今日消息', value: data.todayMessageCount, tone: 'primary' },
    { label: '今日投递', value: data.todayDeliveryCount, tone: 'info' },
    { label: '失败投递', value: data.failedDeliveryCount, tone: data.failedDeliveryCount > 0 ? 'danger' : 'success' },
    { label: 'Outbox待处理', value: data.outboxPendingCount, tone: data.outboxPendingCount > 0 ? 'warning' : 'success' },
    { label: 'Outbox失败', value: data.outboxFailedCount, tone: data.outboxFailedCount > 0 ? 'danger' : 'success' },
    { label: 'Provider失败率', value: `${data.providerFailureRate}%`, tone: data.providerFailureRate > 0 ? 'warning' : 'success' }
  ]
})
const notificationPageLabel = computed(() => {
  const totalPages = pagination.totalPages || 1
  return `${pagination.totalElements === 0 ? 0 : pagination.page + 1} / ${totalPages}`
})
const notificationRangeLabel = computed(() => {
  if (pagination.totalElements === 0) return '共 0 条'
  const start = pagination.page * pagination.size + 1
  const end = Math.min((pagination.page + 1) * pagination.size, pagination.totalElements)
  return `${start}-${end} / ${pagination.totalElements} 条`
})
const noticePageLabel = computed(() => {
  const totalPages = noticePagination.totalPages || 1
  return `${noticePagination.totalElements === 0 ? 0 : noticePagination.page + 1} / ${totalPages}`
})
const successfulDeliveries = computed(() => deliveries.value.filter(delivery => delivery.status === 'SUCCESS').length)
const failedDeliveries = computed(() => deliveries.value.filter(delivery => delivery.status === 'FAILED').length)
const inAppSuccessDeliveries = computed(() =>
  deliveries.value.filter(delivery => delivery.channel === 'IN_APP' && delivery.status === 'SUCCESS').length
)
const externalFailedDeliveries = computed(() =>
  deliveries.value.filter(delivery => delivery.channel !== 'IN_APP' && delivery.status === 'FAILED').length
)
const highFrequencyTypes = computed(() => dashboard.value?.highFrequencyTypes || [])
const filteredDeliveries = computed(() => {
  const keyword = deliveryFilters.keyword.trim().toLowerCase()
  return deliveries.value.filter(delivery => {
    if (deliveryFilters.channel && delivery.channel !== deliveryFilters.channel) return false
    if (deliveryFilters.status && delivery.status !== deliveryFilters.status) return false
    if (!keyword) return true
    return [
      delivery.channel,
      getDictValue('notification_channel', delivery.channel),
      delivery.status,
      getDictValue('notification_delivery_status', delivery.status),
      delivery.provider,
      delivery.providerMessageId,
      delivery.errorCode,
      delivery.errorMessage
    ].some(value => String(value || '').toLowerCase().includes(keyword))
  })
})
const deliveryTotalPages = computed(() => Math.max(1, Math.ceil(filteredDeliveries.value.length / deliveryPagination.size)))
const pagedDeliveries = computed(() => {
  const start = deliveryPagination.page * deliveryPagination.size
  return filteredDeliveries.value.slice(start, start + deliveryPagination.size)
})
const deliveryPageLabel = computed(() =>
  `${filteredDeliveries.value.length === 0 ? 0 : deliveryPagination.page + 1} / ${deliveryTotalPages.value}`
)
const deliveryRangeLabel = computed(() => {
  if (filteredDeliveries.value.length === 0) return '共 0 条'
  const start = deliveryPagination.page * deliveryPagination.size + 1
  const end = Math.min((deliveryPagination.page + 1) * deliveryPagination.size, filteredDeliveries.value.length)
  return `${start}-${end} / ${filteredDeliveries.value.length} 条`
})

let detailRequestSeq = 0

const formatTime = (time?: string) => time ? time.replace('T', ' ').substring(0, 19) : '-'

const formatDuration = (seconds?: number) => {
  if (!seconds && seconds !== 0) return '-'
  if (seconds < 60) return `${seconds}秒`
  if (seconds < 3600) return `${Math.floor(seconds / 60)}分钟`
  return `${Math.floor(seconds / 3600)}小时${Math.floor((seconds % 3600) / 60)}分钟`
}

const loadObservability = async () => {
  const [dashboardResponse, providerResponse, throttleResponse] = await Promise.all([
    getNotificationDashboard(),
    getNotificationProviderHealth(),
    getNotificationThrottleRules()
  ])
  dashboard.value = dashboardResponse.data
  providerHealth.value = providerResponse.data || []
  throttleRules.value = throttleResponse.data || []
}

const syncChannelConfigForm = (config: NotificationChannelConfig | null) => {
  channelConfigForm.enabled = Boolean(config?.enabled)
  channelConfigForm.appId = ''
  channelConfigForm.endpointUrl = ''
  channelConfigForm.secret = ''
  channelConfigForm.timeoutMs = config?.timeoutMs || 5000
  channelConfigForm.defaultPage = config?.defaultPage || ''
  channelConfigForm.templates = (config?.templates || []).map(template => ({
    notificationType: template.notificationType,
    templateId: '',
    page: template.page || '',
    fields: { ...(template.fields || {}) }
  }))
}

const loadMiniProgramConfig = async () => {
  channelConfigLoading.value = true
  try {
    const response = await getNotificationChannelConfig('MINI_PROGRAM')
    miniProgramConfig.value = response.data
    syncChannelConfigForm(response.data)
  } finally {
    channelConfigLoading.value = false
  }
}

const loadMiniProgramCoverage = async () => {
  const response = await getMiniProgramCoverage({
    roles: noticeForm.targetRoles.join(','),
    notificationType: miniProgramSelected.value ? 'SYSTEM_NOTICE' : undefined
  })
  miniProgramCoverage.value = response.data
}

const loadMiniProgramSubscriptions = async () => {
  if (!canViewSubscriptions.value) {
    subscriptions.value = []
    subscriptionPagination.totalElements = 0
    subscriptionPagination.totalPages = 0
    return
  }
  subscriptionLoading.value = true
  try {
    const response = await getMiniProgramSubscriptions({
      page: subscriptionPagination.page,
      size: subscriptionPagination.size,
      keyword: subscriptionFilters.keyword || undefined,
      role: subscriptionFilters.role || undefined,
      status: subscriptionFilters.status || undefined
    })
    const pageData = response.data as PageResponse<AdminMiniProgramSubscription>
    subscriptions.value = pageData.content || []
    subscriptionPagination.totalElements = pageData.totalElements || 0
    subscriptionPagination.totalPages = pageData.totalPages || 0
  } finally {
    subscriptionLoading.value = false
  }
}

const parseList = (value?: string): string[] => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const channelText = (value?: string) => {
  const channels = parseList(value)
  return channels.length ? channels.map(channel => getDictValue('notification_channel', channel)).join(' / ') : '-'
}

const roleText = (value?: string) => {
  const roles = parseList(value)
  return roles.length ? roles.map(role => getDictValue('user_role', role)).join(' / ') : '-'
}

const recipientName = (recipient: NotificationRecipient) =>
  recipient.nickname || recipient.username || '未知收件人'

const loadNotifications = async () => {
  loading.value = true
  try {
    const response = await getAdminNotifications({
      page: pagination.page,
      size: pagination.size,
      keyword: filters.keyword || undefined,
      type: filters.type || undefined,
      priority: filters.priority || undefined,
      businessType: filters.businessType || undefined,
      channel: filters.channel || undefined,
      deliveryStatus: filters.deliveryStatus || undefined
    })
    const pageData = response.data as PageResponse<AdminNotificationSummary>
    notifications.value = pageData.content || []
    pagination.totalElements = pageData.totalElements || 0
    pagination.totalPages = pageData.totalPages || 0
    if (notifications.value.length === 0) {
      selectedNotification.value = null
      recipients.value = []
      deliveries.value = []
      return
    }
    const selectedStillVisible = selectedNotification.value
      ? notifications.value.some(notification => notification.id === selectedNotification.value?.id)
      : false
    if (!selectedStillVisible) {
      await selectNotification(notifications.value[0])
    }
  } finally {
    loading.value = false
  }
}

const loadNotices = async () => {
  noticeLoading.value = true
  try {
    const response = await getSystemNotices({
      page: noticePagination.page,
      size: noticePagination.size,
      status: noticePagination.status || undefined
    })
    const pageData = response.data as PageResponse<SystemNotice>
    notices.value = pageData.content || []
    noticePagination.totalElements = pageData.totalElements || 0
    noticePagination.totalPages = pageData.totalPages || 0
  } finally {
    noticeLoading.value = false
  }
}

const selectNotification = async (notification: AdminNotificationSummary) => {
  const requestSeq = ++detailRequestSeq
  selectedNotification.value = notification
  detailLoading.value = true
  try {
    const [recipientResponse, deliveryResponse] = await Promise.all([
      getAdminNotificationRecipients(notification.id, { page: 0, size: 20 }),
      getAdminNotificationDeliveries(notification.id)
    ])
    if (requestSeq !== detailRequestSeq) return
    recipients.value = recipientResponse.data.content || []
    deliveries.value = deliveryResponse.data || []
    deliveryPagination.page = 0
  } finally {
    if (requestSeq === detailRequestSeq) {
      detailLoading.value = false
    }
  }
}

const search = () => {
  pagination.page = 0
  detailRequestSeq += 1
  selectedNotification.value = null
  recipients.value = []
  deliveries.value = []
  detailLoading.value = false
  loadNotifications()
}

const changeNotificationPage = async (page: number) => {
  if (page < 0 || page >= pagination.totalPages || page === pagination.page) return
  pagination.page = page
  detailRequestSeq += 1
  selectedNotification.value = null
  recipients.value = []
  deliveries.value = []
  detailLoading.value = false
  await loadNotifications()
}

const changeNoticePage = async (page: number) => {
  if (page < 0 || page >= noticePagination.totalPages || page === noticePagination.page) return
  noticePagination.page = page
  await loadNotices()
}

const searchDeliveryLogs = () => {
  deliveryPagination.page = 0
}

const resetDeliveryFilters = () => {
  deliveryFilters.keyword = ''
  deliveryFilters.channel = ''
  deliveryFilters.status = ''
  deliveryPagination.page = 0
}

const changeDeliveryPage = (page: number) => {
  if (page < 0 || page >= deliveryTotalPages.value || page === deliveryPagination.page) return
  deliveryPagination.page = page
}

const refreshAll = async () => {
  await Promise.all([
    loadObservability(),
    loadNotifications(),
    loadNotices(),
    loadMiniProgramConfig(),
    loadMiniProgramCoverage(),
    loadMiniProgramSubscriptions()
  ])
}

const resetFilters = () => {
  filters.keyword = ''
  filters.type = ''
  filters.priority = ''
  filters.businessType = ''
  filters.channel = ''
  filters.deliveryStatus = ''
  search()
}

const retryDelivery = async (delivery: NotificationDeliveryLog) => {
  await retryAdminNotificationDelivery(delivery.id)
  showToast('已提交投递重试')
  if (selectedNotification.value) {
    await selectNotification(selectedNotification.value)
  }
}

const switchTab = (tab: NotificationTab) => {
  activeTab.value = tab
}

const saveChannelConfig = async () => {
  if (!canManageChannelConfig.value) {
    showToast('无权限保存渠道配置')
    return
  }
  if (selectedChannel.value !== 'MINI_PROGRAM') {
    showPlannedAction(`${getDictValue('notification_channel', selectedChannel.value)}配置`)
    return
  }
  const response = await saveNotificationChannelConfig(selectedChannel.value, channelConfigForm)
  miniProgramConfig.value = response.data
  syncChannelConfigForm(response.data)
  channelEditing.value = false
  showToast('渠道配置已保存')
  await Promise.all([loadObservability(), loadMiniProgramCoverage()])
}

const testChannelConfig = async () => {
  if (selectedChannel.value !== 'MINI_PROGRAM') {
    showPlannedAction(`${getDictValue('notification_channel', selectedChannel.value)}测试`)
    return
  }
  const response = await testNotificationChannelConfig(selectedChannel.value)
  showToast(response.data.passed ? '配置诊断通过' : `配置诊断 ${response.data.passedCount}/${response.data.totalCount} 通过`)
  await loadMiniProgramConfig()
}

const testChannelToken = async () => {
  const response = await testNotificationChannelToken(selectedChannel.value)
  showToast(response.data.passed ? '微信凭据远程校验通过' : response.data.diagnostics?.[0]?.message || '远程校验失败')
}

const showRecentFailures = async () => {
  filters.channel = selectedChannel.value
  filters.deliveryStatus = 'FAILED'
  activeTab.value = 'audit'
  await search()
}

const editChannelConfig = () => {
  if (!canManageChannelConfig.value) {
    showToast('无权限配置渠道')
    return
  }
  if (selectedChannel.value !== 'MINI_PROGRAM') {
    showPlannedAction(`${getDictValue('notification_channel', selectedChannel.value)}配置`)
    return
  }
  channelEditing.value = true
}

const sendAuthorizationGuide = async () => {
  const response = await sendMiniProgramAuthorizationGuides({
    targetRoles: subscriptionFilters.role ? [subscriptionFilters.role] : undefined,
    status: subscriptionFilters.status || undefined,
    keyword: subscriptionFilters.keyword || undefined
  })
  showToast(`已发送 ${response.data || 0} 个授权引导`)
}

const showPlannedAction = (name: string) => {
  showToast(`${name}功能待接入后端接口，当前为方案入口`)
}

const viewSubscriptionDetail = async (row: AdminMiniProgramSubscription) => {
  subscriptionDetailLoading.value = true
  try {
    const response = await getMiniProgramSubscriptionDetail(row.userId)
    selectedSubscription.value = response.data
    resolutionForm.status = response.data.resolution?.status || 'RESOLVED'
    resolutionForm.remark = response.data.resolution?.remark || ''
    resolutionForm.remindAfter = response.data.resolution?.remindAfter?.substring(0, 16) || ''
  } finally {
    subscriptionDetailLoading.value = false
  }
}

const saveSubscriptionResolution = async () => {
  if (!selectedSubscription.value) return
  const response = await resolveMiniProgramSubscription(selectedSubscription.value.userId, {
    status: resolutionForm.status,
    remark: resolutionForm.remark || undefined,
    remindAfter: resolutionForm.status === 'SNOOZED'
      ? normalizeDateTime(resolutionForm.remindAfter)
      : undefined
  })
  selectedSubscription.value = response.data
  showToast('处理状态已保存')
}

const sendSubscriptionTestDelivery = async () => {
  const row = selectedSubscription.value
  const notificationType = row?.templates?.[0]?.notificationType
  if (!row || !notificationType) {
    showToast('当前用户没有可测试的模板')
    return
  }
  if (!window.confirm('测试投递将真实调用微信接口并消耗该用户一次订阅授权，是否继续？')) return
  const response = await testNotificationChannelDelivery('MINI_PROGRAM', {
    userId: row.userId,
    notificationType
  })
  showToast(`测试投递已创建，投递ID ${response.data}`)
  await viewSubscriptionDetail(row)
}

const guideSelectedSubscription = async () => {
  if (!selectedSubscription.value) return
  const response = await sendMiniProgramAuthorizationGuide(selectedSubscription.value.userId)
  showToast(response.data ? '授权引导已发送' : '用户处于暂不提醒状态，未发送授权引导')
}

const searchSubscriptions = () => {
  subscriptionPagination.page = 0
  loadMiniProgramSubscriptions()
}

const changeSubscriptionPage = async (page: number) => {
  if (page < 0 || page >= subscriptionPagination.totalPages || page === subscriptionPagination.page) return
  subscriptionPagination.page = page
  await loadMiniProgramSubscriptions()
}

const resetNoticeForm = () => {
  noticeForm.title = ''
  noticeForm.summary = ''
  noticeForm.content = ''
  noticeForm.targetRoles = ['ADMIN']
  noticeForm.channels = ['IN_APP']
  noticeForm.priority = 'NORMAL'
  noticeForm.scheduledPublishTime = ''
  noticeForm.expireTime = ''
}

const normalizeDateTime = (value?: string) => {
  if (!value) return undefined
  return value.length === 16 ? `${value}:00` : value
}

const saveNotice = async () => {
  await createSystemNotice({
    ...noticeForm,
    scheduledPublishTime: normalizeDateTime(noticeForm.scheduledPublishTime),
    expireTime: normalizeDateTime(noticeForm.expireTime)
  })
  showToast('系统公告已创建')
  showNoticeEditor.value = false
  resetNoticeForm()
  await refreshAll()
}

const publishNotice = async (notice: SystemNotice) => {
  await publishSystemNotice(notice.id)
  showToast('系统公告已发布')
  await Promise.all([loadNotices(), loadNotifications()])
}

const cancelNotice = async (notice: SystemNotice) => {
  await cancelSystemNotice(notice.id)
  showToast('系统公告已撤回')
  await loadNotices()
}

const toggleRole = (role: Role) => {
  const exists = noticeForm.targetRoles.includes(role)
  noticeForm.targetRoles = exists
    ? noticeForm.targetRoles.filter(item => item !== role)
    : [...noticeForm.targetRoles, role]
  loadMiniProgramCoverage()
}

const toggleChannel = (channel: string) => {
  const exists = noticeForm.channels.includes(channel)
  noticeForm.channels = exists
    ? noticeForm.channels.filter(item => item !== channel)
    : [...noticeForm.channels, channel]
  loadMiniProgramCoverage()
}

const toneClass = (value?: string) => (value || 'unknown').toLowerCase().replace(/_/g, '-')

const subscriptionStatusText = (row: AdminMiniProgramSubscription) => {
  return getDictValue('notification_mini_subscription_row_status', row.status)
}

const subscriptionStatusClass = (row: AdminMiniProgramSubscription) => {
  if (row.status === 'NORMAL') return ''
  if (row.status === 'LOW_BALANCE') return 'warning-text'
  return 'danger'
}

const availableText = (status: string, count: number) => {
  if (status === 'ACCEPT') return `${count} 次`
  return getDictValue('notification_mini_subscription_status', status || 'UNKNOWN')
}

const templateFieldsText = (fields?: Record<string, string>) => {
  const entries = Object.entries(fields || {})
  return entries.length ? entries.map(([key, value]) => `${key} -> ${value}`).join(' / ') : '-'
}

onMounted(async () => {
  await loadAllDicts()
  await refreshAll()
})
</script>

<template>
  <div class="notifications-page">
    <header class="header-row">
      <div>
        <p class="header-breadcrumb">系统管理 / 通知管理</p>
        <h1 class="header-title">通知管理</h1>
      </div>
      <div class="header-actions">
        <button class="btn-outline" type="button" @click="refreshAll"><span class="btn-icon">↻</span>刷新</button>
        <button v-if="canCreateNotice" class="btn-primary" type="button" @click="switchTab('publish')"><span class="btn-icon">+</span>新增公告</button>
      </div>
    </header>

    <nav class="module-tabs" aria-label="通知管理页签">
      <button
        v-for="tab in visibleTabs"
        :key="tab.key"
        type="button"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </nav>

    <section v-if="activeTab === 'overview'" class="tab-panel overview-tab">
      <section class="observability-band">
        <article class="band-intro">
          <strong>通知运行态</strong>
          <span>最长待投递：{{ formatDuration(dashboard?.oldestPendingWaitSeconds) }}</span>
        </article>
        <article v-for="card in dashboardCards" :key="card.label" class="metric-card" :class="card.tone">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
        </article>
      </section>

      <section class="filter">
        <div class="filter-left">
          <input v-model="filters.keyword" class="form-input" placeholder="标题或摘要" @keyup.enter="search" />
          <select v-model="filters.type" class="form-input">
            <option value="">全部类型</option>
            <option v-for="option in typeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <select v-model="filters.priority" class="form-input">
            <option value="">全部优先级</option>
            <option v-for="option in priorityOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <select v-model="filters.businessType" class="form-input">
            <option value="">全部业务</option>
            <option v-for="option in businessTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
          <select v-model="filters.channel" class="form-input">
            <option value="">全部渠道</option>
            <option v-for="option in channelOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </div>
        <div class="filter-right">
          <button class="btn-outline" type="button" @click="resetFilters"><span class="btn-icon">×</span>重置</button>
          <button class="btn-primary" type="button" @click="search"><span class="btn-icon">⌕</span>搜索</button>
        </div>
      </section>

      <main class="overview-workbench">
        <section class="panel notification-stream">
          <div class="panel-header">
            <div>
              <h2>通知列表</h2>
              <small>{{ notificationRangeLabel }}</small>
            </div>
          </div>
          <div v-if="loading" class="state-cell">加载中...</div>
          <div v-else-if="notifications.length === 0" class="state-cell">暂无通知</div>
          <div v-else class="notification-list">
            <article
              v-for="notification in notifications"
              :key="notification.id"
              class="notification-item"
              :class="{ selected: selectedNotification?.id === notification.id }"
              role="button"
              tabindex="0"
              @click="selectNotification(notification)"
              @keydown.enter.prevent="selectNotification(notification)"
            >
              <div class="notification-item-head">
                <strong>{{ notification.title }}</strong>
                <span>{{ getDictValue('notification_type', notification.type) }}</span>
              </div>
              <p>{{ notification.summary || notification.content || '-' }}</p>
              <div class="notification-meta">
                <span>收件 {{ notification.recipientCount }}</span>
                <span>未读 {{ notification.unreadCount }}</span>
                <span :class="{ danger: notification.failedDeliveryCount > 0 }">失败 {{ notification.failedDeliveryCount }}</span>
              </div>
              <time>{{ formatTime(notification.createdTime) }}</time>
            </article>
          </div>
          <div class="pager compact-pager">
            <button class="btn-outline" type="button" :disabled="pagination.page <= 0" @click="changeNotificationPage(pagination.page - 1)"><span class="btn-icon">‹</span>上一页</button>
            <span>{{ notificationPageLabel }}</span>
            <button class="btn-outline" type="button" :disabled="pagination.page + 1 >= pagination.totalPages" @click="changeNotificationPage(pagination.page + 1)"><span class="btn-icon">›</span>下一页</button>
          </div>
        </section>

        <section class="panel detail-panel">
          <template v-if="detailLoading">
            <div class="state-cell">链路加载中...</div>
          </template>
          <template v-else-if="selectedNotification">
            <div class="panel-title">
              <div>
                <h2>{{ selectedNotification.title }}</h2>
                <p>{{ selectedNotification.content || selectedNotification.summary || '-' }}</p>
              </div>
              <span>{{ getDictValue('notification_type', selectedNotification.type) }}</span>
            </div>
            <div class="route-steps">
              <article><span>创建</span><strong>{{ formatTime(selectedNotification.createdTime) }}</strong></article>
              <article><span>收件</span><strong>{{ selectedNotification.recipientCount }}</strong></article>
              <article><span>站内成功</span><strong>{{ inAppSuccessDeliveries }}</strong></article>
              <article :class="{ danger: externalFailedDeliveries > 0 }"><span>外部失败</span><strong>{{ externalFailedDeliveries }}</strong></article>
            </div>
            <div class="summary-split">
              <article>
                <span>收件人</span>
                <strong>{{ selectedNotification.recipientCount }} 人</strong>
                <small>已读 {{ selectedNotification.recipientCount - selectedNotification.unreadCount }} / 未读 {{ selectedNotification.unreadCount }}</small>
              </article>
              <article :class="{ danger: failedDeliveries > 0 }">
                <span>投递状态</span>
                <strong>{{ failedDeliveries }} 失败</strong>
                <small>成功 {{ successfulDeliveries }} / 全部 {{ deliveries.length }}</small>
              </article>
            </div>
            <div class="section-title">投递日志</div>
            <div class="delivery-list compact">
              <article v-if="pagedDeliveries.length === 0" class="state-inline">暂无投递日志</article>
              <article v-for="delivery in pagedDeliveries" v-else :key="delivery.id" class="delivery-row" :class="toneClass(delivery.status)">
                <div class="delivery-copy">
                  <strong>{{ getDictValue('notification_channel', delivery.channel) }} · {{ getDictValue('notification_delivery_status', delivery.status) }}</strong>
                  <small>{{ delivery.errorCode || delivery.providerMessageId || delivery.provider || '-' }}</small>
                </div>
                <time>{{ formatTime(delivery.deliveredTime || delivery.createdTime) }}</time>
              </article>
            </div>
            <div class="pager compact-pager">
              <button class="btn-outline" type="button" :disabled="deliveryPagination.page <= 0" @click="changeDeliveryPage(deliveryPagination.page - 1)"><span class="btn-icon">‹</span>上一页</button>
              <span>{{ deliveryPageLabel }}</span>
              <button class="btn-outline" type="button" :disabled="deliveryPagination.page + 1 >= deliveryTotalPages" @click="changeDeliveryPage(deliveryPagination.page + 1)"><span class="btn-icon">›</span>下一页</button>
            </div>
            <div class="section-title">收件人</div>
            <div class="recipient-strip">
              <span v-if="recipients.length === 0" class="state-inline">暂无收件人</span>
              <span v-for="recipient in recipients" v-else :key="recipient.id" class="recipient-pill">{{ recipientName(recipient) }}</span>
            </div>
          </template>
          <div v-else class="state-cell">选择一条通知查看链路</div>
        </section>

        <aside class="side-ops">
          <section class="panel provider-panel">
            <div class="panel-header"><h2>Provider健康</h2></div>
            <div class="health-list">
              <article v-if="providerHealth.length === 0" class="state-inline">暂无健康数据</article>
              <article v-for="provider in providerHealth" v-else :key="provider.channel" class="health-row" :class="toneClass(provider.healthStatus)">
                <div>
                  <strong>{{ getDictValue('notification_channel', provider.channel) }}</strong>
                  <span>{{ getDictValue('notification_provider_health_status', provider.healthStatus) }}</span>
                </div>
                <p>待投递 {{ provider.pendingCount }} / 失败 {{ provider.failedCount }}</p>
                <small>{{ provider.lastErrorCode || provider.lastStatus || '-' }}</small>
              </article>
            </div>
          </section>
          <section class="panel throttle-panel">
            <div class="panel-header"><h2>聚合频控</h2></div>
            <div class="health-list">
              <article v-if="throttleRules.length === 0" class="state-inline">暂无频控规则</article>
              <article v-for="rule in throttleRules" v-else :key="rule.type" class="throttle-row" :class="{ active: rule.throttled }">
                <div>
                  <strong>{{ getDictValue('notification_type', rule.type) }}</strong>
                  <span>{{ getDictValue('common_status', rule.enabled ? 'ACTIVE' : 'INACTIVE') }}</span>
                </div>
                <p>{{ rule.windowMinutes }}分钟 / {{ rule.maxCount }}条，当前 {{ rule.currentCount }} 条</p>
              </article>
            </div>
          </section>
          <section class="panel channel-panel">
            <div class="panel-header">
              <h2>渠道投递指标</h2>
              <small>今日口径</small>
            </div>
            <div class="channel-list">
              <article v-if="!dashboard?.channelMetrics?.length" class="state-inline">暂无投递指标</article>
              <article v-for="metric in dashboard?.channelMetrics || []" v-else :key="metric.channel" class="channel-row">
                <strong>{{ getDictValue('notification_channel', metric.channel) }}</strong>
                <span>成功 {{ metric.successCount }} / 失败 {{ metric.failedCount }} / 失败率 {{ metric.failureRate }}%</span>
              </article>
            </div>
          </section>
          <section class="panel">
            <div class="panel-header"><h2>高频类型</h2></div>
            <div class="frequency-list">
              <span v-if="highFrequencyTypes.length === 0">暂无高频聚合</span>
              <span v-for="item in highFrequencyTypes" v-else :key="item.type">
                {{ getDictValue('notification_type', item.type) }} · {{ item.count }}
              </span>
            </div>
          </section>
        </aside>
      </main>
    </section>

    <section v-else-if="activeTab === 'publish'" class="tab-panel publish-tab">
      <div class="publish-layout">
        <section class="panel notice-form-panel">
          <div class="panel-header">
            <div>
              <h2>公告内容</h2>
              <small>系统公告会进入站内通知，外部渠道通过 Outbox 异步投递</small>
            </div>
            <button v-if="canCreateNotice" class="btn-primary" type="button" @click="saveNotice"><span class="btn-icon">✓</span>保存公告</button>
          </div>
          <div class="form-grid">
            <label>标题<input v-model="noticeForm.title" class="form-input" /></label>
            <label>摘要<input v-model="noticeForm.summary" class="form-input" /></label>
            <label>优先级
              <select v-model="noticeForm.priority" class="form-input">
                <option v-for="option in priorityOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </label>
            <label>计划发布时间<input v-model="noticeForm.scheduledPublishTime" class="form-input" type="datetime-local" /></label>
            <label>过期时间<input v-model="noticeForm.expireTime" class="form-input" type="datetime-local" /></label>
          </div>
          <label class="content-label">正文<textarea v-model="noticeForm.content" class="form-input" rows="9"></textarea></label>
        </section>

        <aside class="publish-side">
          <section class="panel">
            <div class="panel-header"><h2>目标角色</h2></div>
            <div class="check-card-list">
              <label v-for="option in roleOptions" :key="option.value" class="check-row">
                <input
                  type="checkbox"
                  :checked="noticeForm.targetRoles.includes(option.value as Role)"
                  @change="toggleRole(option.value as Role)"
                />
                <span>{{ option.label }}</span>
              </label>
            </div>
          </section>
          <section class="panel">
            <div class="panel-header">
              <h2>通知渠道</h2>
              <small>站内通知可靠兜底</small>
            </div>
            <div class="check-card-list">
              <label v-for="option in channelOptions" :key="option.value" class="channel-check-row">
                <input
                  type="checkbox"
                  :checked="noticeForm.channels.includes(option.value)"
                  @change="toggleChannel(option.value)"
                />
                <span>{{ option.label }}</span>
                <small v-if="option.value === 'IN_APP'">必选兜底</small>
                <small v-else-if="option.value === 'MINI_PROGRAM'">预计触达 {{ miniProgramImpact.reachable }} 人</small>
              </label>
            </div>
            <div
              v-if="miniProgramSelected"
              class="mini-program-advisory"
              :class="{ ready: miniProgramReady }"
            >
              <div>
                <strong>小程序订阅消息</strong>
                <span>{{ miniProgramHealthLabel }}</span>
              </div>
              <p>未授权或未配置时不阻断公告发布，站内通知会作为可靠兜底。</p>
              <small>{{ miniProgramHealth?.lastErrorCode || miniProgramHealth?.lastStatus || '等待 Provider 健康检查' }}</small>
            </div>
          </section>
          <section class="panel impact-panel">
            <div class="panel-header">
              <h2>发布前触达预估</h2>
              <button class="btn-outline" type="button" @click="switchTab('channels')"><span class="btn-icon">⚙</span>配置诊断</button>
            </div>
            <div class="impact-list">
              <div><span>目标收件人</span><strong>{{ miniProgramImpact.targetCount }}</strong></div>
              <div><span>已绑定 openid</span><strong>{{ miniProgramImpact.openidBound }}</strong></div>
              <div><span>已授权模板</span><strong>{{ miniProgramImpact.authorized }}</strong></div>
              <div><span>预计可触达</span><strong>{{ miniProgramImpact.reachable }}</strong></div>
              <div><span>站内兜底</span><strong>{{ miniProgramImpact.inAppFallback }}</strong></div>
            </div>
            <p class="warning-note">外部渠道配置缺失或授权覆盖为 0 时仍允许发布，站内通知会完整生成。</p>
          </section>
          <section class="panel flow-panel">
            <h2>发布后链路</h2>
            <p>保存公告 -> 创建站内通知 -> 外部 delivery 入 Outbox -> Provider 异步投递 -> 投递审计页重试。</p>
          </section>
          <section class="panel notice-board">
            <div class="panel-header">
              <div>
                <h2>公告草稿与发布</h2>
                <small>{{ noticePageLabel }}</small>
              </div>
              <select v-model="noticePagination.status" class="form-input slim-select" @change="loadNotices">
                <option value="">全部状态</option>
                <option v-for="option in noticeStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div class="notice-list">
              <div v-if="noticeLoading" class="state-inline">公告加载中...</div>
              <div v-else-if="notices.length === 0" class="state-inline">暂无系统公告</div>
              <article v-for="notice in notices" v-else :key="notice.id" class="notice-row">
                <div>
                  <strong>{{ notice.title }}</strong>
                  <span>{{ notice.summary || '-' }}</span>
                  <small>角色 {{ roleText(notice.targetRoles) }} / 渠道 {{ channelText(notice.channels) }}</small>
                </div>
                <div class="notice-state">
                  <span>{{ getDictValue('system_notice_status', notice.status) }}</span>
                  <small>{{ formatTime(notice.createdTime) }}</small>
                </div>
                <div class="row-actions">
                  <button v-if="canCreateNotice && notice.status === 'DRAFT'" class="btn-primary compact-button" type="button" @click="publishNotice(notice)"><span class="btn-icon">↗</span>发布</button>
                  <button v-if="canCancelNotice && notice.status === 'PUBLISHED'" class="btn-outline compact-button" type="button" @click="cancelNotice(notice)"><span class="btn-icon">×</span>撤回</button>
                </div>
              </article>
            </div>
            <div class="pager compact-pager">
              <button class="btn-outline" type="button" :disabled="noticePagination.page <= 0" @click="changeNoticePage(noticePagination.page - 1)"><span class="btn-icon">‹</span>上一页</button>
              <span>{{ noticePageLabel }}</span>
              <button class="btn-outline" type="button" :disabled="noticePagination.page + 1 >= noticePagination.totalPages" @click="changeNoticePage(noticePagination.page + 1)"><span class="btn-icon">›</span>下一页</button>
            </div>
          </section>
        </aside>
      </div>
    </section>

    <section v-else-if="activeTab === 'audit'" class="tab-panel audit-tab">
      <div class="metrics-row">
        <article class="metric-card"><span>今日投递</span><strong>{{ auditMetrics.total }}</strong><small>全部渠道</small></article>
        <article class="metric-card success"><span>成功</span><strong>{{ auditMetrics.success }}</strong><small>已完成</small></article>
        <article class="metric-card danger"><span>失败</span><strong>{{ auditMetrics.failed }}</strong><small>需处理</small></article>
        <article class="metric-card"><span>跳过</span><strong>{{ auditMetrics.skipped }}</strong><small>未配置/未授权</small></article>
        <article class="metric-card warning"><span>Outbox 待处理</span><strong>{{ auditMetrics.pending }}</strong><small>异步队列</small></article>
      </div>
      <div class="audit-layout">
        <section class="panel audit-table-panel">
          <div class="panel-header">
            <div>
              <h2>投递审计明细</h2>
              <small>{{ notificationRangeLabel }}</small>
            </div>
            <div class="audit-filters">
              <input v-model="filters.keyword" class="form-input" placeholder="通知/收件人/错误码" @keyup.enter="search" />
              <select v-model="filters.channel" class="form-input" @change="search">
                <option value="">全部渠道</option>
                <option v-for="option in channelOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <select v-model="filters.deliveryStatus" class="form-input" @change="search">
                <option value="">全部状态</option>
                <option v-for="option in deliveryStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
          </div>
          <div class="audit-table">
            <div class="audit-row header">
              <span>通知</span><span>收件/未读</span><span>失败</span><span>渠道</span><span>创建时间</span><span>操作</span>
            </div>
            <div v-if="loading" class="state-cell">加载中...</div>
            <div v-else-if="notifications.length === 0" class="state-cell">暂无审计记录</div>
            <div
              v-for="notification in notifications"
              v-else
              :key="notification.id"
              class="audit-row"
              :class="{ selected: selectedNotification?.id === notification.id }"
              @click="selectNotification(notification)"
            >
              <span>{{ notification.title }}</span>
              <span>{{ notification.recipientCount }} / {{ notification.unreadCount }}</span>
              <span :class="{ danger: notification.failedDeliveryCount > 0 }">{{ notification.failedDeliveryCount }}</span>
              <span>{{ channelText(notification.channels) }}</span>
              <span>{{ formatTime(notification.createdTime) }}</span>
              <button class="btn-outline" type="button" @click.stop="selectNotification(notification)"><span class="btn-icon">›</span>查看</button>
            </div>
          </div>
        </section>
        <aside class="audit-side">
          <section class="panel">
            <div class="panel-header">
              <h2>当前投递日志</h2>
              <small>{{ deliveryRangeLabel }}</small>
            </div>
            <div class="delivery-filter compact-filter">
              <input v-model="deliveryFilters.keyword" class="form-input" placeholder="错误码或 Provider" @keyup.enter="searchDeliveryLogs" />
              <select v-model="deliveryFilters.status" class="form-input" @change="searchDeliveryLogs">
                <option value="">全部状态</option>
                <option v-for="option in deliveryStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <button class="btn-outline compact-button" type="button" @click="resetDeliveryFilters"><span class="btn-icon">×</span>重置</button>
            </div>
            <div class="delivery-list">
              <article v-if="pagedDeliveries.length === 0" class="state-inline">请选择通知查看投递日志</article>
              <article v-for="delivery in pagedDeliveries" v-else :key="delivery.id" class="delivery-row" :class="toneClass(delivery.status)">
                <div class="delivery-copy">
                  <strong>{{ getDictValue('notification_channel', delivery.channel) }} · {{ getDictValue('notification_delivery_status', delivery.status) }}</strong>
                  <small>{{ delivery.errorCode || delivery.providerMessageId || delivery.provider || '-' }}</small>
                  <time>{{ formatTime(delivery.deliveredTime || delivery.createdTime) }}</time>
                </div>
                <button
                  v-if="canRetryNotification && delivery.status === 'FAILED' && delivery.channel !== 'IN_APP'"
                  class="btn-outline"
                  type="button"
                  @click.stop="retryDelivery(delivery)"
                >
                  <span class="btn-icon">↻</span>重试
                </button>
              </article>
            </div>
            <div class="pager compact-pager">
              <button class="btn-outline" type="button" :disabled="deliveryPagination.page <= 0" @click="changeDeliveryPage(deliveryPagination.page - 1)"><span class="btn-icon">‹</span>上一页</button>
              <span>{{ deliveryPageLabel }}</span>
              <button class="btn-outline" type="button" :disabled="deliveryPagination.page + 1 >= deliveryTotalPages" @click="changeDeliveryPage(deliveryPagination.page + 1)"><span class="btn-icon">›</span>下一页</button>
            </div>
          </section>
          <section class="panel dark-panel">
            <h2>Outbox 队列</h2>
            <div><span>PENDING</span><strong>{{ dashboard?.outboxPendingCount || 0 }}</strong></div>
            <div><span>PROCESSING</span><strong>{{ dashboard?.outboxProcessingCount || 0 }}</strong></div>
            <div><span>FAILED</span><strong>{{ dashboard?.outboxFailedCount || 0 }}</strong></div>
            <div><span>RETRY</span><strong>{{ dashboard?.outboxRetryCount || 0 }}</strong></div>
          </section>
        </aside>
      </div>
    </section>

    <section v-else-if="activeTab === 'channels'" class="tab-panel channels-tab">
      <div class="channel-config-layout">
        <aside class="panel channel-selector">
          <div class="panel-header"><h2>渠道列表</h2></div>
          <button
            v-for="channel in channelConfigRows"
            :key="channel.channel"
            type="button"
            class="channel-config-item"
            :class="{ active: selectedChannel === channel.channel }"
            @click="selectedChannel = channel.channel"
          >
            <strong>{{ getDictValue('notification_channel', channel.channel) }}</strong>
            <span>{{ getDictValue('notification_provider_health_status', channel.healthStatus) }} · 失败 {{ channel.failedCount }}</span>
          </button>
        </aside>
        <section class="panel provider-config-detail">
          <div class="config-detail-header">
            <div>
              <h2>{{ getDictValue('notification_channel', selectedChannel) }}</h2>
              <p>运行时优先读取 PC 维护配置，环境配置仅作为初始化兜底。</p>
              <small v-if="channelEditing">当前为编辑态：AppSecret 只允许更新，不会回显。</small>
            </div>
            <div class="config-actions">
              <button v-if="canManageChannelConfig" class="btn-outline" type="button" @click="editChannelConfig"><span class="btn-icon">✎</span>编辑</button>
              <button v-if="canManageChannelConfig" class="btn-primary" type="button" @click="saveChannelConfig"><span class="btn-icon">✓</span>保存</button>
              <button class="btn-outline" type="button" @click="testChannelConfig"><span class="btn-icon">⚡</span>本地诊断</button>
              <button v-if="canTestToken" class="btn-outline" type="button" @click="testChannelToken"><span class="btn-icon">↗</span>远程校验</button>
              <button class="btn-outline" type="button" @click="showRecentFailures"><span class="btn-icon">!</span>最近失败</button>
            </div>
          </div>
          <div class="config-section">
            <div class="panel-header">
              <h2>基础配置</h2>
              <div class="row-actions">
                <button v-if="canManageChannelConfig" class="btn-outline compact-button" type="button" @click="editChannelConfig"><span class="btn-icon">⚙</span>配置</button>
                <span class="status-pill" :class="toneClass(selectedProviderHealth?.healthStatus || 'NOT_CONFIGURED')">
                  {{ getDictValue('notification_provider_health_status', selectedProviderHealth?.healthStatus || 'NOT_CONFIGURED') }}
                </span>
              </div>
            </div>
            <div class="config-grid">
              <article><span>Provider 启用</span><strong>{{ selectedChannelConfig?.enabled ? '已启用' : '未启用' }}</strong><small>关闭后外部投递记录为 SKIPPED</small></article>
              <article><span>AppID / Endpoint</span><strong>{{ selectedChannelConfig?.appIdMasked || selectedChannelConfig?.endpointUrlMasked || '-' }}</strong><small>非敏感参数脱敏展示</small></article>
              <article><span>接口超时</span><strong>{{ selectedChannelConfig?.timeoutMs || '-' }} ms</strong><small>建议限制 1000-30000ms</small></article>
              <article class="secret-card"><span>密钥状态</span><strong>{{ selectedChannelConfig?.secretConfigured ? '已托管' : '未配置' }}</strong><small>来源：{{ selectedChannelConfig?.secretSource || '-' }}</small></article>
              <article><span>默认跳转页</span><strong>{{ selectedChannelConfig?.defaultPage || '-' }}</strong><small>模板可覆盖</small></article>
              <article><span>配置来源</span><strong>{{ selectedChannelConfig?.source || '-' }}</strong><small>数据库配置优先，环境变量兜底</small></article>
            </div>
            <div v-if="channelEditing && selectedChannel === 'MINI_PROGRAM'" class="config-edit-grid">
              <label>
                <span>启用 Provider</span>
                <input v-model="channelConfigForm.enabled" type="checkbox" />
              </label>
              <label>
                <span>AppID</span>
                <input v-model="channelConfigForm.appId" class="form-input" placeholder="留空则保留/使用环境配置" />
              </label>
              <label>
                <span>AppSecret</span>
                <input v-model="channelConfigForm.secret" class="form-input" type="password" placeholder="只写入，不回显" />
              </label>
              <label>
                <span>接口超时 ms</span>
                <input v-model.number="channelConfigForm.timeoutMs" class="form-input" min="1000" max="30000" type="number" />
              </label>
              <label>
                <span>默认跳转页</span>
                <input v-model="channelConfigForm.defaultPage" class="form-input" placeholder="pages/notifications/index" />
              </label>
              <label>
                <span>发送接口地址</span>
                <input v-model="channelConfigForm.endpointUrl" class="form-input" placeholder="留空使用微信默认接口" />
              </label>
            </div>
          </div>
          <div class="config-section">
            <div class="panel-header">
              <h2>模板映射</h2>
              <div class="row-actions">
                <button v-if="canManageChannelConfig" class="btn-outline compact-button" type="button" @click="editChannelConfig"><span class="btn-icon">✎</span>编辑映射</button>
              </div>
            </div>
            <div class="template-table">
              <div class="template-row header"><span>通知类型</span><span>模板名称</span><span>模板ID</span><span>字段映射</span><span>状态</span></div>
              <div v-if="channelConfigLoading" class="state-inline">配置加载中...</div>
              <div
                v-for="template in selectedChannelTemplates"
                v-else
                :key="template.notificationType"
                class="template-row"
                :class="{ muted: !template.configured }"
              >
                <span>{{ template.notificationType }}</span>
                <span>{{ getDictValue('notification_type', template.notificationType) }}</span>
                <span>{{ template.templateIdMasked }}</span>
                <span>{{ templateFieldsText(template.fields) }}</span>
                <strong>{{ template.configured ? '完整' : '未配置' }}</strong>
              </div>
              <div v-if="!channelConfigLoading && !selectedChannelTemplates.length" class="state-inline">暂无模板映射</div>
            </div>
            <div v-if="channelEditing && selectedChannel === 'MINI_PROGRAM'" class="template-edit-list">
              <article v-for="(template, index) in channelConfigForm.templates" :key="template.notificationType" class="template-edit-row">
                <label>
                  <span>通知类型</span>
                  <input v-model="template.notificationType" class="form-input" />
                </label>
                <label>
                  <span>模板ID</span>
                  <input v-model="template.templateId" class="form-input" placeholder="留空保留当前模板ID" />
                </label>
                <label>
                  <span>跳转页</span>
                  <input v-model="template.page" class="form-input" />
                </label>
                <button v-if="canManageChannelConfig" class="btn-outline compact-button" type="button" @click="channelConfigForm.templates?.splice(index, 1)"><span class="btn-icon">×</span>删除</button>
              </article>
              <button v-if="canManageChannelConfig" class="btn-primary compact-button" type="button" @click="channelConfigForm.templates?.push({ notificationType: '', templateId: '', page: '', fields: {} })"><span class="btn-icon">+</span>新增模板</button>
            </div>
          </div>
        </section>
        <aside class="diagnostics-panel">
          <section class="panel">
            <div class="panel-header">
              <h2>配置诊断</h2>
              <span class="status-pill" :class="diagnosticPassCount === selectedChannelDiagnostics.length ? 'ok' : 'warning'">
                {{ diagnosticPassCount }}/{{ selectedChannelDiagnostics.length }} 通过
              </span>
            </div>
            <div class="diagnostic-list">
              <span
                v-for="item in selectedChannelDiagnostics"
                :key="item.key"
                :class="{ warning: item.status !== 'PASS' }"
                :title="item.message"
              >
                {{ item.label }}
              </span>
              <span v-if="!selectedChannelDiagnostics.length" class="warning">请选择支持诊断的渠道</span>
            </div>
          </section>
          <section class="panel dark-panel">
            <h2>安全边界</h2>
            <p>AppSecret、Webhook Secret 等密钥只允许更新和状态查看，不允许明文回显、复制或导出。</p>
          </section>
          <section class="panel">
            <div class="panel-header"><h2>发布前影响</h2></div>
            <div class="impact-list">
              <div><span>目标用户</span><strong>{{ miniProgramImpact.targetCount }}</strong></div>
              <div><span>openid 绑定</span><strong>{{ miniProgramImpact.openidBound }}</strong></div>
              <div><span>模板授权</span><strong>{{ miniProgramImpact.authorized }}</strong></div>
              <div><span>预计触达</span><strong>{{ miniProgramImpact.reachable }}</strong></div>
            </div>
          </section>
        </aside>
      </div>
    </section>

    <section v-else class="tab-panel subscriptions-tab">
      <div class="metrics-row">
        <article v-for="metric in subscriptionMetrics" :key="metric.label" class="metric-card" :class="metric.tone">
          <span>{{ metric.label }}</span>
          <strong>{{ metric.value }}</strong>
          <small>{{ metric.hint }}</small>
        </article>
      </div>
      <div class="subscription-layout">
        <section class="panel subscription-table-panel">
          <div class="panel-header">
            <div>
              <h2>用户接收资格</h2>
              <small>{{ subscriptionRangeLabel }}，按渠道授权、绑定状态和授权余量评估可触达人群。</small>
            </div>
            <button v-if="canGuideSubscription" class="btn-primary" type="button" @click="sendAuthorizationGuide"><span class="btn-icon">↗</span>发送授权引导</button>
          </div>
          <div class="audit-filters">
            <input v-model="subscriptionFilters.keyword" class="form-input" placeholder="搜索用户/昵称/手机号" @keyup.enter="searchSubscriptions" />
            <select v-model="subscriptionFilters.role" class="form-input" @change="searchSubscriptions">
              <option value="">全部角色</option>
              <option v-for="option in roleOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <select v-model="subscriptionFilters.status" class="form-input" @change="searchSubscriptions">
              <option value="">全部状态</option>
              <option v-for="option in subscriptionRowStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <button class="btn-outline" type="button" @click="searchSubscriptions"><span class="btn-icon">⌕</span>查询</button>
          </div>
          <div class="subscription-table">
            <div class="subscription-row header"><span>用户</span><span>角色</span><span>OpenID/设备</span><span>价格模板</span><span>公告模板</span><span>状态</span><span>操作</span></div>
            <div v-if="subscriptionLoading" class="state-inline">订阅授权加载中...</div>
            <div
              v-for="row in subscriptions"
              v-else
              :key="row.userId"
              class="subscription-row"
            >
              <span>{{ row.nickname || row.username }}</span>
              <span>{{ getDictValue('user_role', row.role) }}</span>
              <span>{{ row.openidBound ? row.openidMasked : '未绑定' }}</span>
              <span>{{ availableText(row.priceStatus, row.priceAvailableCount) }}</span>
              <span>{{ availableText(row.noticeStatus, row.noticeAvailableCount) }}</span>
              <strong :class="subscriptionStatusClass(row)">
                {{ subscriptionStatusText(row) }}
                <small v-if="row.resolution?.status && row.resolution.status !== 'OPEN'" class="resolution-status">
                  {{ getDictValue('notification_mini_resolution_status', row.resolution.status) }}
                </small>
              </strong>
              <button
                class="btn-outline compact-button"
                type="button"
                @click="viewSubscriptionDetail(row)"
              >
                <span class="btn-icon">›</span>详情
              </button>
            </div>
            <div v-if="!subscriptionLoading && !subscriptions.length" class="state-inline">暂无订阅授权数据</div>
          </div>
          <div class="pager compact-pager">
            <button class="btn-outline" type="button" :disabled="subscriptionPagination.page <= 0" @click="changeSubscriptionPage(subscriptionPagination.page - 1)"><span class="btn-icon">‹</span>上一页</button>
            <span>{{ subscriptionPageLabel }}</span>
            <button class="btn-outline" type="button" :disabled="subscriptionPagination.page + 1 >= subscriptionPagination.totalPages" @click="changeSubscriptionPage(subscriptionPagination.page + 1)"><span class="btn-icon">›</span>下一页</button>
          </div>
        </section>
        <aside class="subscription-side">
          <section class="panel">
            <div class="panel-header"><h2>渠道覆盖</h2></div>
            <div class="coverage-list">
              <div><span>小程序订阅</span><strong>{{ miniProgramImpact.authorized }} / {{ miniProgramImpact.targetCount }}</strong></div>
              <div><span>OpenID 绑定</span><strong>{{ miniProgramImpact.openidBound }} / {{ miniProgramImpact.targetCount }}</strong></div>
              <div><span>低余量</span><strong>{{ miniProgramImpact.lowBalance }}</strong></div>
            </div>
          </section>
          <section class="panel">
            <h2>授权引导策略</h2>
            <p>小程序订阅消息必须由用户在小程序端明确触发授权，PC 端只能发起引导任务，不能代替用户授权。</p>
          </section>
          <section class="panel">
            <h2>权限与安全边界</h2>
            <p>订阅状态可见，OpenID 脱敏展示；敏感凭据不进入前端响应。</p>
          </section>
        </aside>
      </div>
    </section>

    <div v-if="selectedSubscription" class="drawer-backdrop" @click.self="selectedSubscription = null">
      <aside class="subscription-drawer">
        <div class="panel-header">
          <div>
            <h2>{{ selectedSubscription.nickname || selectedSubscription.username }}</h2>
            <small>{{ getDictValue('user_role', selectedSubscription.role) }} / {{ selectedSubscription.openidMasked }}</small>
          </div>
          <button class="btn-outline compact-button" type="button" @click="selectedSubscription = null">关闭</button>
        </div>
        <div v-if="subscriptionDetailLoading" class="state-inline">详情加载中...</div>
        <template v-else>
          <section class="drawer-section">
            <div class="panel-header">
              <h3>模板授权</h3>
              <div class="row-actions">
                <button v-if="canGuideSubscription" class="btn-outline compact-button" type="button" @click="guideSelectedSubscription">发送授权引导</button>
                <button v-if="canTestDelivery" class="btn-outline compact-button" type="button" @click="sendSubscriptionTestDelivery">测试投递</button>
              </div>
            </div>
            <div v-for="template in selectedSubscription.templates" :key="template.notificationType" class="drawer-row">
              <span>{{ getDictValue('notification_type', template.notificationType) }}</span>
              <strong>{{ getDictValue('notification_mini_subscription_status', template.status) }} / {{ template.availableCount }} 次</strong>
              <small>{{ formatTime(template.lastAuthorizedTime) }}</small>
            </div>
          </section>
          <section class="drawer-section">
            <h3>异常处理</h3>
            <select v-model="resolutionForm.status" class="form-input">
              <option v-for="option in resolutionStatusOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <input v-if="resolutionForm.status === 'SNOOZED'" v-model="resolutionForm.remindAfter" class="form-input" type="datetime-local" />
            <textarea v-model="resolutionForm.remark" class="form-input" maxlength="500" placeholder="处理备注"></textarea>
            <button v-if="canResolveSubscription" class="btn-primary" type="button" @click="saveSubscriptionResolution">保存处理结果</button>
          </section>
          <section class="drawer-section">
            <h3>最近投递</h3>
            <div v-for="delivery in selectedSubscription.recentDeliveries || []" :key="delivery.id" class="drawer-row">
              <span>{{ getDictValue('notification_delivery_status', delivery.status) }}</span>
              <strong>{{ delivery.errorCode || delivery.providerMessageId || '-' }}</strong>
              <small>{{ delivery.errorMessage || formatTime(delivery.createdTime) }}</small>
            </div>
            <div v-if="!selectedSubscription.recentDeliveries?.length" class="state-inline">暂无小程序投递记录</div>
          </section>
          <section class="drawer-section">
            <h3>用户偏好</h3>
            <div v-for="preference in selectedSubscription.preferences || []" :key="preference.id" class="drawer-row">
              <span>{{ getDictValue('notification_type', preference.notificationType) }} / {{ getDictValue('notification_channel', preference.channel) }}</span>
              <strong>{{ preference.enabled ? '启用' : '停用' }}</strong>
              <small>{{ preference.quietStartTime || '-' }} - {{ preference.quietEndTime || '-' }}</small>
            </div>
            <div v-if="!selectedSubscription.preferences?.length" class="state-inline">使用默认通知偏好</div>
          </section>
        </template>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.notifications-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: calc(100vh - 120px);
}

.drawer-backdrop {
  background: rgba(15, 23, 42, 0.45);
  inset: 0;
  position: fixed;
  z-index: 1000;
}

.subscription-drawer {
  background: var(--bg-card, #fff);
  box-shadow: -12px 0 36px rgba(15, 23, 42, 0.18);
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  margin-left: auto;
  max-width: 560px;
  overflow-y: auto;
  padding: 22px;
  width: min(92vw, 560px);
}

.drawer-section {
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  display: grid;
  gap: 10px;
  padding: 14px;
}

.drawer-section h3 {
  margin: 0;
}

.drawer-row {
  align-items: start;
  border-bottom: 1px solid var(--border-color);
  display: grid;
  gap: 4px;
  grid-template-columns: 1fr auto;
  padding: 8px 0;
}

.drawer-row small {
  grid-column: 1 / -1;
}

.btn-primary,
.btn-outline {
  align-items: center;
  border-radius: var(--radius);
  cursor: pointer;
  display: inline-flex;
  font-size: var(--font-size-sm);
  font-weight: 600;
  justify-content: center;
  min-height: 36px;
  padding: 0 var(--spacing-md);
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
  white-space: nowrap;
}

.btn-primary:active,
.btn-outline:active {
  transform: scale(0.98);
}

.btn-primary:disabled,
.btn-outline:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.btn-primary {
  background: var(--primary-color);
  border: 1px solid var(--primary-color);
  color: white;
}

.btn-outline {
  background: white;
  border: 1px solid var(--gray-300);
  color: var(--gray-700);
}

.btn-outline:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--primary-color) 32%, var(--gray-300));
  color: var(--primary-color);
}

.btn-icon {
  align-items: center;
  display: inline-flex;
  flex: 0 0 18px;
  font-size: 13px;
  height: 18px;
  justify-content: center;
  line-height: 1;
  margin-right: 6px;
  width: 18px;
}

.form-input {
  background: white;
  border: 1px solid var(--gray-300);
  border-radius: var(--radius);
  color: var(--gray-800);
  font-size: var(--font-size-sm);
  min-height: 36px;
  padding: 8px 10px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.form-input:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary-color) 12%, transparent);
  outline: none;
}

.header-row {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-md);
  min-height: 70px;
}

.header-breadcrumb {
  color: var(--gray-500);
  font-size: var(--font-size-sm);
  margin: 0 0 4px;
}

.header-title {
  color: var(--gray-900);
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.2;
  margin: 0;
}

.header-actions,
.filter-right,
.row-actions,
.pager {
  align-items: center;
  display: flex;
  gap: var(--spacing-sm);
}

.module-tabs {
  align-items: center;
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius-md);
  display: flex;
  gap: 4px;
  min-height: 48px;
  overflow-x: auto;
  padding: 6px;
}

.module-tabs button {
  background: transparent;
  border: 0;
  border-radius: var(--radius);
  color: var(--gray-600);
  cursor: pointer;
  flex: 0 0 132px;
  font-size: var(--font-size-sm);
  font-weight: 700;
  height: 36px;
  letter-spacing: 0;
  transition: background 0.18s ease, color 0.18s ease;
}

.module-tabs button.active {
  background: var(--primary-color);
  color: white;
}

.tab-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.observability-band {
  align-items: stretch;
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius-md);
  display: grid;
  gap: var(--spacing-md);
  grid-template-columns: 196px repeat(6, minmax(0, 1fr));
  min-height: 96px;
  padding: var(--spacing-md);
}

.band-intro {
  background: color-mix(in srgb, var(--primary-color) 10%, white);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
}

.band-intro strong {
  color: var(--primary-color);
  font-size: var(--font-size-md);
}

.band-intro span {
  color: var(--gray-600);
  font-size: var(--font-size-sm);
}

.metric-card {
  background: white;
  border: 1px solid transparent;
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
  min-width: 0;
  padding: 10px 12px;
}

.metric-card span {
  color: var(--gray-500);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.metric-card strong {
  color: var(--gray-900);
  font-size: 28px;
  font-weight: 800;
  line-height: 1;
}

.metric-card.success strong {
  color: var(--success-color);
}

.metric-card.warning strong {
  color: var(--warning-color);
}

.metric-card.danger strong {
  color: var(--error-color);
}

.metric-card.info strong {
  color: var(--info-color);
}

.metric-card.primary strong {
  color: var(--primary-color);
}

.metric-card.muted strong,
.metric-card.muted span,
.metric-card.muted small {
  color: var(--gray-400);
}

.metrics-row {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  min-height: 100px;
}

.metrics-row .metric-card {
  border-color: var(--gray-200);
  padding: 14px;
}

.filter {
  align-items: center;
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius-md);
  display: flex;
  gap: var(--spacing-md);
  justify-content: space-between;
  min-height: 44px;
  padding: 6px 12px;
}

.filter-left {
  align-items: center;
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}

.filter-left .form-input {
  min-height: 32px;
  min-width: 118px;
}

.filter-left input.form-input {
  min-width: 230px;
}

.filter .btn-primary,
.filter .btn-outline {
  min-height: 32px;
}

.panel {
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius-md);
  min-width: 0;
}

.panel,
.panel p {
  color: var(--gray-600);
  font-size: var(--font-size-sm);
  line-height: 1.55;
}

.panel p {
  margin: 0;
}

.panel-header,
.panel-title {
  align-items: center;
  display: flex;
  gap: var(--spacing-sm);
  justify-content: space-between;
}

.panel-header h2,
.panel-title h2,
.quick-actions h2 {
  color: var(--gray-900);
  font-size: var(--font-size-md);
  font-weight: 700;
  line-height: 1.35;
  margin: 0;
}

.panel-header small,
.panel-title p,
.quick-actions p,
small {
  color: var(--gray-500);
  font-size: var(--font-size-xs);
}

.panel-title p {
  font-size: var(--font-size-sm);
  line-height: 1.45;
  margin: 6px 0 0;
}

.panel-title > span {
  background: color-mix(in srgb, var(--primary-color) 10%, white);
  border-radius: 999px;
  color: var(--primary-color);
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  font-weight: 700;
  padding: 5px 9px;
}

.workbench {
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(300px, 350px) minmax(420px, 1fr) minmax(250px, 270px);
  grid-template-rows: minmax(456px, auto) auto;
  align-items: stretch;
}

.overview-workbench {
  align-items: stretch;
  display: grid;
  gap: 14px;
  grid-template-columns: minmax(300px, 350px) minmax(420px, 1fr) minmax(250px, 270px);
  min-height: 460px;
}

.state-cell {
  color: var(--gray-500);
  padding: var(--spacing-xl);
  text-align: center;
}

.state-inline {
  color: var(--gray-500);
  font-size: var(--font-size-sm);
  padding: var(--spacing-sm);
  text-align: center;
}

.notification-stream,
.detail-panel,
.side-ops {
  min-height: 456px;
}

.notification-stream {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}

.notification-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}

.notification-item {
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: var(--radius);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 102px;
  padding: 12px;
  transition: background 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.notification-item:hover,
.notification-item:focus {
  border-color: color-mix(in srgb, var(--primary-color) 26%, var(--gray-200));
  outline: none;
}

.notification-item.selected {
  background: color-mix(in srgb, var(--primary-color) 9%, white);
  border-color: color-mix(in srgb, var(--primary-color) 34%, var(--gray-200));
  box-shadow: inset 3px 0 0 var(--primary-color);
}

.notification-item-head,
.notification-meta,
.delivery-row,
.health-row div,
.throttle-row div,
.notice-row,
.notice-state {
  align-items: center;
  display: flex;
  gap: var(--spacing-sm);
}

.notification-item-head,
.delivery-row,
.health-row div,
.throttle-row div,
.notice-row {
  justify-content: space-between;
}

.notification-item-head strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
}

.notification-item-head span {
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.notification-item p {
  color: var(--gray-600);
  font-size: var(--font-size-sm);
  line-height: 1.45;
  margin: 0;
}

.notification-meta {
  color: var(--gray-600);
  flex-wrap: wrap;
  font-size: var(--font-size-xs);
  font-weight: 600;
}

.notification-meta .danger,
.danger strong,
.danger span {
  color: var(--error-color);
}

.notification-item time,
.delivery-row time {
  color: var(--gray-500);
  font-size: var(--font-size-xs);
}

.pager {
  color: var(--gray-500);
  font-size: var(--font-size-sm);
  justify-content: center;
}

.compact-pager {
  border-top: 1px solid var(--gray-100);
  padding-top: 8px;
}

.pager .btn-outline {
  min-height: 30px;
  padding: 0 12px;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
  padding: var(--spacing-md);
}

.route-steps {
  background: var(--gray-50);
  border-radius: var(--radius);
  display: grid;
  gap: var(--spacing-sm);
  grid-template-columns: repeat(4, minmax(0, 1fr));
  padding: 12px;
}

.route-steps article,
.summary-split article {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.route-steps span,
.summary-split span,
.section-title {
  color: var(--gray-500);
  font-size: var(--font-size-xs);
  font-weight: 700;
}

.route-steps strong {
  color: var(--gray-800);
  font-size: var(--font-size-xs);
  overflow-wrap: anywhere;
}

.summary-split {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.summary-split article {
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  padding: 12px;
}

.summary-split strong {
  color: var(--gray-900);
  font-size: 22px;
  line-height: 1;
}

.section-title {
  margin-top: 2px;
}

.recipient-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.recipient-pill {
  background: var(--gray-50);
  border: 1px solid var(--gray-100);
  border-radius: 999px;
  color: var(--gray-600);
  font-size: var(--font-size-xs);
  padding: 5px 9px;
}

.delivery-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 190px;
}

.delivery-row {
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  padding: 10px;
}

.delivery-row.failed {
  border-color: color-mix(in srgb, var(--error-color) 36%, var(--gray-100));
}

.delivery-copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}

.delivery-copy strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
}

.delivery-section-title {
  align-items: center;
  display: flex;
  justify-content: space-between;
}

.delivery-section-title small {
  font-weight: 500;
}

.delivery-filter {
  align-items: center;
  display: grid;
  gap: 8px;
  grid-template-columns: minmax(130px, 1fr) minmax(104px, 0.56fr) minmax(104px, 0.56fr) auto;
}

.delivery-filter .form-input,
.delivery-filter .btn-outline {
  min-height: 30px;
}

.delivery-filter .form-input {
  min-width: 0;
  padding: 6px 9px;
}

.delivery-pager {
  margin-top: auto;
}

.health-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.side-ops {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.side-ops .panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.health-row,
.throttle-row {
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  display: grid;
  gap: var(--spacing-xs);
  padding: var(--spacing-sm);
}

.health-row div,
.throttle-row div {
  align-items: center;
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.health-row strong,
.throttle-row strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
  min-width: 0;
}

.health-row span,
.throttle-row span {
  background: var(--gray-100);
  border-radius: 999px;
  color: var(--gray-600);
  flex-shrink: 0;
  font-size: var(--font-size-xs);
  padding: 3px 8px;
}

.health-row p,
.throttle-row p {
  color: var(--gray-600);
  font-size: var(--font-size-sm);
  margin: 0;
}

.health-row small,
.throttle-row small {
  overflow-wrap: anywhere;
}

.health-row.ok span,
.delivery-row.success .delivery-copy strong {
  background: rgba(16, 185, 129, 0.12);
  color: var(--success-color);
}

.health-row.degraded span,
.health-row.not-configured span,
.throttle-row.active span {
  background: rgba(245, 158, 11, 0.14);
  color: var(--warning-color);
}

.health-row.down span,
.delivery-row.failed .delivery-copy strong {
  background: rgba(239, 68, 68, 0.12);
  color: var(--error-color);
}

.channel-list,
.notice-list,
.frequency-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.channel-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.channel-row strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
}

.channel-row span {
  color: var(--gray-600);
  font-size: var(--font-size-xs);
}

.notice-board,
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
}

.notice-board {
  grid-column: 1 / span 2;
}

.quick-actions {
  grid-column: 3;
}

.slim-select {
  min-height: 32px;
  width: 130px;
}

.notice-row {
  border-bottom: 1px solid var(--gray-100);
  display: grid;
  gap: var(--spacing-md);
  grid-template-columns: minmax(0, 1fr) minmax(150px, auto) auto;
  padding: 9px 0;
}

.notice-row:first-child {
  padding-top: 0;
}

.notice-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.notice-row strong,
.notice-row span {
  display: block;
}

.notice-row strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
}

.notice-row span,
.notice-state small {
  color: var(--gray-600);
  font-size: var(--font-size-xs);
}

.notice-state {
  align-items: flex-start;
  flex-direction: column;
  gap: 3px;
}

.quick-actions p {
  margin: 0;
}

.frequency-list span {
  background: color-mix(in srgb, var(--primary-color) 8%, white);
  border-radius: var(--radius-sm);
  color: var(--primary-color);
  font-size: var(--font-size-xs);
  font-weight: 700;
  padding: 5px 8px;
}

.editor-mask {
  align-items: center;
  background: rgba(15, 23, 42, 0.35);
  display: flex;
  inset: 0;
  justify-content: center;
  position: fixed;
  z-index: 30;
}

.editor-panel {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
  max-height: 90vh;
  max-width: 760px;
  overflow-y: auto;
  padding: var(--spacing-lg);
  width: min(92vw, 760px);
}

.form-grid {
  display: grid;
  gap: var(--spacing-md);
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
}

label,
.content-label {
  color: var(--gray-600);
  display: flex;
  flex-direction: column;
  font-size: var(--font-size-sm);
  gap: var(--spacing-xs);
}

.check-group {
  align-items: center;
  flex-wrap: wrap;
  display: flex;
  gap: var(--spacing-sm);
}

.check-group span {
  color: var(--gray-600);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.check-group label {
  align-items: center;
  flex-direction: row;
}

.mini-program-advisory {
  border: 1px solid color-mix(in srgb, var(--warning-color) 32%, var(--gray-200));
  border-radius: var(--radius);
  background: color-mix(in srgb, var(--warning-color) 8%, white);
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: var(--spacing-sm) var(--spacing-md);
}

.mini-program-advisory.ready {
  border-color: color-mix(in srgb, var(--success-color) 28%, var(--gray-200));
  background: color-mix(in srgb, var(--success-color) 7%, white);
}

.mini-program-advisory div {
  align-items: center;
  display: flex;
  gap: var(--spacing-sm);
  justify-content: space-between;
}

.mini-program-advisory strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
}

.mini-program-advisory span {
  border-radius: 999px;
  background: white;
  color: var(--gray-700);
  font-size: var(--font-size-xs);
  font-weight: 700;
  padding: 3px 8px;
}

.mini-program-advisory p,
.mini-program-advisory small {
  color: var(--gray-600);
  font-size: var(--font-size-sm);
  line-height: 1.5;
  margin: 0;
  overflow-wrap: anywhere;
}

.publish-layout,
.audit-layout,
.subscription-layout {
  align-items: start;
  display: grid;
  gap: 16px;
  grid-template-columns: minmax(620px, 1fr) minmax(320px, 0.48fr);
}

.notice-form-panel,
.publish-side .panel,
.audit-table-panel,
.audit-side .panel,
.subscription-table-panel,
.subscription-side .panel,
.provider-config-detail,
.channel-selector,
.diagnostics-panel .panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
}

.notice-form-panel {
  min-height: 620px;
}

.publish-side,
.audit-side,
.subscription-side,
.diagnostics-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.content-label textarea {
  min-height: 210px;
  resize: vertical;
}

.check-card-list {
  display: grid;
  gap: 10px;
}

.check-row,
.channel-check-row {
  align-items: center;
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  cursor: pointer;
  display: grid;
  gap: 10px;
  grid-template-columns: auto minmax(0, 1fr) auto;
  min-height: 42px;
  padding: 10px 12px;
}

.check-row span,
.channel-check-row span {
  color: var(--gray-800);
  font-weight: 700;
}

.channel-check-row small {
  text-align: right;
}

.impact-list,
.coverage-list {
  display: grid;
  gap: 8px;
}

.impact-list div,
.coverage-list div,
.dark-panel div {
  align-items: center;
  border-bottom: 1px solid var(--gray-100);
  display: flex;
  justify-content: space-between;
  min-height: 34px;
}

.impact-list div:last-child,
.coverage-list div:last-child,
.dark-panel div:last-child {
  border-bottom: 0;
}

.impact-list span,
.coverage-list span,
.dark-panel span {
  color: var(--gray-500);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.impact-list strong,
.coverage-list strong,
.dark-panel strong {
  color: var(--gray-900);
  font-size: var(--font-size-lg);
}

.warning-note,
.warning-text {
  color: var(--warning-color);
}

.audit-filters {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.audit-filters .form-input {
  min-height: 32px;
  min-width: 138px;
}

.audit-filters input.form-input {
  min-width: 220px;
}

.audit-table,
.subscription-table,
.template-table {
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  overflow: hidden;
}

.audit-row,
.subscription-row,
.template-row {
  align-items: center;
  border-bottom: 1px solid var(--gray-100);
  color: var(--gray-700);
  display: grid;
  font-size: var(--font-size-sm);
  gap: 10px;
  min-height: 48px;
  padding: 8px 12px;
}

.audit-row {
  cursor: pointer;
  grid-template-columns: minmax(180px, 1fr) 96px 64px minmax(140px, 0.8fr) 152px 92px;
}

.subscription-row {
  grid-template-columns: minmax(90px, 0.7fr) 82px minmax(140px, 1fr) 86px 86px 72px 94px;
}

.template-row {
  grid-template-columns: minmax(130px, 0.8fr) minmax(120px, 0.8fr) minmax(130px, 1fr) minmax(180px, 1.2fr) 70px;
}

.audit-row.header,
.subscription-row.header,
.template-row.header {
  background: var(--gray-50);
  color: var(--gray-500);
  cursor: default;
  font-size: var(--font-size-xs);
  font-weight: 800;
  min-height: 38px;
}

.audit-row:last-child,
.subscription-row:last-child,
.template-row:last-child {
  border-bottom: 0;
}

.audit-row:not(.header):hover,
.subscription-row:not(.header):hover {
  background: color-mix(in srgb, var(--primary-color) 5%, white);
}

.audit-row.selected {
  background: color-mix(in srgb, var(--primary-color) 8%, white);
  box-shadow: inset 3px 0 0 var(--primary-color);
}

.audit-row > span,
.subscription-row > span,
.template-row > span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resolution-status {
  display: block;
  margin-top: 3px;
  color: var(--gray-500);
  font-size: 11px;
  font-weight: 500;
}

.compact-filter {
  grid-template-columns: minmax(140px, 1fr) minmax(120px, 0.72fr);
}

.dark-panel {
  background: #12201f;
  border-color: #12201f;
  color: color-mix(in srgb, white 82%, transparent);
}

.dark-panel h2,
.dark-panel p,
.dark-panel span,
.dark-panel strong {
  color: inherit;
}

.dark-panel div {
  border-bottom-color: rgba(255, 255, 255, 0.14);
}

.channel-config-layout {
  align-items: start;
  display: grid;
  gap: 16px;
  grid-template-columns: 280px minmax(560px, 1fr) minmax(280px, 0.42fr);
}

.channel-config-item {
  background: white;
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 62px;
  padding: 12px;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.channel-config-item strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
}

.channel-config-item span {
  color: var(--gray-500);
  font-size: var(--font-size-xs);
}

.channel-config-item.active {
  background: color-mix(in srgb, var(--primary-color) 7%, white);
  border-color: color-mix(in srgb, var(--primary-color) 36%, var(--gray-200));
  box-shadow: inset 3px 0 0 var(--primary-color);
}

.config-detail-header {
  align-items: flex-start;
  border-bottom: 1px solid var(--gray-100);
  display: flex;
  gap: 16px;
  justify-content: space-between;
  padding-bottom: 14px;
}

.config-detail-header h2 {
  color: var(--gray-900);
  font-size: var(--font-size-lg);
  margin: 0 0 6px;
}

.config-detail-header p {
  margin: 0;
}

.config-actions {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.config-section {
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}

.config-grid {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.config-edit-grid,
.template-edit-list {
  display: grid;
  gap: 10px;
}

.config-edit-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.config-edit-grid label {
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  padding: 10px;
}

.config-edit-grid label:first-child {
  align-items: center;
  flex-direction: row;
}

.template-edit-row {
  align-items: end;
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  display: grid;
  gap: 10px;
  grid-template-columns: minmax(120px, 0.7fr) minmax(220px, 1.2fr) minmax(180px, 1fr) auto;
  padding: 10px;
}

.config-grid article {
  background: var(--gray-50);
  border: 1px solid var(--gray-100);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 92px;
  padding: 12px;
}

.config-grid span,
.config-grid small {
  color: var(--gray-500);
  font-size: var(--font-size-xs);
}

.config-grid strong {
  color: var(--gray-900);
  font-size: var(--font-size-sm);
  overflow-wrap: anywhere;
}

.secret-card {
  background: color-mix(in srgb, var(--warning-color) 7%, white) !important;
}

.status-pill {
  background: var(--gray-100);
  border-radius: 999px;
  color: var(--gray-600);
  font-size: var(--font-size-xs);
  font-weight: 800;
  padding: 4px 9px;
  white-space: nowrap;
}

.status-pill.ok,
.status-pill.success {
  background: rgba(16, 185, 129, 0.12);
  color: var(--success-color);
}

.status-pill.warning,
.status-pill.degraded,
.status-pill.not-configured {
  background: rgba(245, 158, 11, 0.14);
  color: var(--warning-color);
}

.status-pill.down,
.status-pill.failed {
  background: rgba(239, 68, 68, 0.12);
  color: var(--error-color);
}

.compact-button {
  min-height: 30px;
  padding: 0 10px;
}

.template-row.muted {
  color: var(--gray-400);
}

.diagnostic-list {
  display: grid;
  gap: 8px;
}

.diagnostic-list span {
  background: var(--gray-50);
  border: 1px solid var(--gray-100);
  border-radius: var(--radius-sm);
  color: var(--success-color);
  font-size: var(--font-size-sm);
  font-weight: 700;
  padding: 8px 10px;
}

.diagnostic-list span.warning {
  color: var(--warning-color);
}

.danger {
  color: var(--error-color) !important;
}

@media (max-width: 1280px) {
  .observability-band {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .band-intro {
    grid-column: 1 / -1;
  }

  .workbench {
    grid-template-columns: minmax(280px, 0.46fr) minmax(380px, 1fr);
    grid-template-rows: auto;
  }

  .side-ops {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    min-height: auto;
  }

  .notice-board {
    grid-column: 1 / -1;
  }

  .quick-actions {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1024px) {
  .filter {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-left .form-input,
  .filter-left input.form-input {
    flex: 1;
    min-width: 180px;
  }

  .workbench,
  .side-ops {
    grid-template-columns: 1fr;
  }

  .notification-stream,
  .detail-panel,
  .side-ops {
    min-height: auto;
  }
}

@media (max-width: 760px) {
  .header-row,
  .panel-header,
  .panel-title,
  .notice-row {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-actions,
  .filter-right,
  .row-actions {
    flex-wrap: wrap;
  }

  .observability-band,
  .route-steps,
  .summary-split,
  .delivery-filter {
    grid-template-columns: 1fr;
  }

  .notice-row {
    display: flex;
  }
}
</style>
