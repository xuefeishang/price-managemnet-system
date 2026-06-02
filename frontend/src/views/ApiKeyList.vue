<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import {
  createApiKey,
  disableApiKey,
  enableApiKey,
  getApiKeys,
  getApiPermissions,
  getExternalApiServiceStatus,
  revokeApiKey,
  updateExternalApiServiceStatus,
  updateApiKey
} from '@/api/apiKey'
import { getDictOptions, getDictValue, loadAllDicts } from '@/composables/useDict'
import type { ApiKey, ApiKeyCreateRequest, ExternalApiEndpoint } from '@/types/apiKey'
import {
  buildCodeExample,
  endpointSchemaFields,
  validateSignatureTestVector,
  type CodeExampleLanguage
} from '@/utils/externalApiCodeExamples'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const apiKeys = ref<ApiKey[]>([])
const endpoints = ref<ExternalApiEndpoint[]>([])
const serviceStatus = ref({
  deploymentEnabled: false,
  runtimeEnabled: true,
  available: false,
  message: '外部 API 服务状态加载中'
})
const serviceStatusLoading = ref(false)
const showForm = ref(false)
const editingId = ref<number | null>(null)
const createdSecret = ref('')
const createdAppId = ref('')
const createdPermissionCodes = ref<string[]>([])
const formErrors = reactive<Record<string, string>>({})
const selectedPermissionCode = ref('')
const permissionSearch = ref('')
const showSelectedPermissionsOnly = ref(false)
const exampleBaseUrl = ref('http://localhost:8080')
const selectedCodeLanguage = ref<CodeExampleLanguage>('node')
const createdCodeLanguage = ref<CodeExampleLanguage>('powershell')
const createdExampleEndpointId = ref<number | null>(null)
const expireTimeInputRef = ref<HTMLInputElement | null>(null)

const codeLanguageOptions: Array<{ value: CodeExampleLanguage; label: string }> = [
  { value: 'node', label: 'Node.js' },
  { value: 'java', label: 'Java 25' },
  { value: 'postman', label: 'Postman' },
  { value: 'powershell', label: 'PowerShell' },
  { value: 'curl', label: 'curl' }
]

const pagination = reactive({
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0
})

const filters = reactive({
  keyword: '',
  status: '',
  environment: ''
})

const form = reactive<ApiKeyCreateRequest>({
  name: '',
  description: '',
  environment: 'TESTING',
  expireTime: '',
  ipWhitelist: [],
  rateLimitPerMinute: 60,
  dailyLimit: 10000,
  permissionCodes: []
})

const ipInput = ref('')

const statusOptions = computed(() => getDictOptions('api_key_status'))
const environmentOptions = computed(() => getDictOptions('api_key_environment'))
const jumpPage = ref('1')

const paginationItems = computed<Array<number | string>>(() => {
  const total = pagination.totalPages || 0
  const current = pagination.page
  if (total <= 7) {
    return Array.from({ length: total }, (_, index) => index)
  }

  const pages = new Set<number>([0, total - 1])
  for (let page = current - 1; page <= current + 1; page += 1) {
    if (page > 0 && page < total - 1) {
      pages.add(page)
    }
  }

  const sorted = Array.from(pages).sort((a, b) => a - b)
  const items: Array<number | string> = []
  sorted.forEach((page, index) => {
    const previous = sorted[index - 1]
    if (index > 0 && page - previous > 1) {
      items.push(`ellipsis-${previous}-${page}`)
    }
    items.push(page)
  })
  return items
})

const permissionGroups = computed(() => {
  const map = new Map<string, ExternalApiEndpoint[]>()
  endpoints.value.forEach(endpoint => {
    const list = map.get(endpoint.permissionCode) || []
    list.push(endpoint)
    map.set(endpoint.permissionCode, list)
  })
  return Array.from(map.entries()).map(([permissionCode, list]) => ({ permissionCode, endpoints: list }))
})

const filteredPermissionGroups = computed(() => {
  const keyword = permissionSearch.value.trim().toLowerCase()
  return permissionGroups.value.filter(group => {
    if (showSelectedPermissionsOnly.value && !form.permissionCodes.includes(group.permissionCode)) {
      return false
    }
    if (!keyword) {
      return true
    }
    const label = permissionLabel(group.permissionCode).toLowerCase()
    return label.includes(keyword)
      || group.permissionCode.toLowerCase().includes(keyword)
      || group.endpoints.some(endpoint =>
        endpoint.method.toLowerCase().includes(keyword)
        || endpoint.pathPattern.toLowerCase().includes(keyword)
        || (endpoint.description || '').toLowerCase().includes(keyword)
      )
  })
})

const selectedPermissionGroup = computed(() => {
  return permissionGroups.value.find(group => group.permissionCode === selectedPermissionCode.value)
    || filteredPermissionGroups.value[0]
    || permissionGroups.value[0]
})

const selectedEndpoints = computed(() => selectedPermissionGroup.value?.endpoints || [])

const createdExampleEndpoints = computed(() => {
  const codes = new Set(createdPermissionCodes.value)
  return endpoints.value.filter(endpoint => codes.has(endpoint.permissionCode))
})

const createdExampleEndpoint = computed(() => {
  return createdExampleEndpoints.value.find(endpoint => endpoint.id === createdExampleEndpointId.value)
    || chooseDefaultExampleEndpoint(createdExampleEndpoints.value)
})

const loadApiKeys = async () => {
  loading.value = true
  try {
    const response = await getApiKeys({
      page: pagination.page,
      size: pagination.size,
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      environment: filters.environment || undefined
    })
    apiKeys.value = response.data.content || []
    pagination.totalElements = response.data.totalElements || 0
    pagination.totalPages = response.data.totalPages || 0
    jumpPage.value = String((response.data.number ?? pagination.page) + 1)
  } catch (error: any) {
    showToast(error.message || '加载API密钥失败')
  } finally {
    loading.value = false
  }
}

const loadPermissions = async () => {
  const response = await getApiPermissions()
  endpoints.value = response.data || []
  if (!selectedPermissionCode.value && permissionGroups.value.length > 0) {
    selectedPermissionCode.value = permissionGroups.value[0].permissionCode
  }
}

const loadServiceStatus = async () => {
  const response = await getExternalApiServiceStatus()
  serviceStatus.value = response.data
}

const clearFormErrors = () => {
  Object.keys(formErrors).forEach(key => {
    delete formErrors[key]
  })
}

const resetForm = () => {
  clearFormErrors()
  editingId.value = null
  form.name = ''
  form.description = ''
  form.environment = 'TESTING'
  form.expireTime = ''
  form.ipWhitelist = []
  form.rateLimitPerMinute = 60
  form.dailyLimit = 10000
  form.permissionCodes = []
  selectedPermissionCode.value = permissionGroups.value[0]?.permissionCode || ''
  permissionSearch.value = ''
  showSelectedPermissionsOnly.value = false
  ipInput.value = ''
}

const toggleExternalApiService = async () => {
  if (!serviceStatus.value.deploymentEnabled) {
    showToast('部署配置未启用外部 API')
    return
  }
  const nextEnabled = !serviceStatus.value.runtimeEnabled
  try {
    await showDialog({
      title: nextEnabled ? '开启外部 API' : '暂停外部 API',
      message: nextEnabled
        ? '确认恢复外部 API 对外服务？恢复后外部系统可继续调用已授权接口。'
        : '确认暂停外部 API 对外服务？暂停后所有外部调用会立即返回服务暂停。',
      showCancelButton: true
    })
  } catch {
    return
  }
  serviceStatusLoading.value = true
  try {
    const response = await updateExternalApiServiceStatus(nextEnabled)
    serviceStatus.value = response.data
    showToast(nextEnabled ? '外部 API 已开启' : '外部 API 已暂停')
  } catch (error: any) {
    showToast(error.message || '更新外部 API 服务状态失败')
  } finally {
    serviceStatusLoading.value = false
  }
}

const openCreate = () => {
  resetForm()
  showForm.value = true
}

const openEdit = (row: ApiKey) => {
  editingId.value = row.id
  form.name = row.name
  form.description = row.description || ''
  form.environment = row.environment
  form.expireTime = toDateTimeInput(row.expireTime)
  form.ipWhitelist = [...(row.ipWhitelist || [])]
  form.rateLimitPerMinute = row.rateLimitPerMinute
  form.dailyLimit = row.dailyLimit
  form.permissionCodes = [...(row.permissionCodes || [])]
  ipInput.value = form.ipWhitelist.join('\n')
  showForm.value = true
}

const setFormError = (field: string, message: string) => {
  formErrors[field] = message
}

const isNonNegativeInteger = (value: unknown) => {
  return Number.isInteger(value) && Number(value) >= 0
}

const isValidDateTime = (value: string) => {
  if (!value) return true
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) {
    return false
  }
  const parsed = new Date(value)
  return !Number.isNaN(parsed.getTime())
}

const toDateTimeInput = (value?: string) => {
  if (!value) {
    return ''
  }
  return value.replace(' ', 'T').substring(0, 16)
}

const toApiDateTime = (value?: string) => {
  if (!value) {
    return undefined
  }
  return `${value.replace('T', ' ')}:00`
}

const clearExpireTime = () => {
  form.expireTime = ''
  delete formErrors.expireTime
}

const openExpireTimePicker = () => {
  delete formErrors.expireTime
  const input = expireTimeInputRef.value
  if (!input) {
    return
  }
  input.focus()
  if (typeof input.showPicker === 'function') {
    try {
      input.showPicker()
    } catch {
      // Some browsers only allow showPicker during direct user gestures.
    }
  }
}

const formatLimitText = (row: ApiKey) => {
  const minute = row.rateLimitPerMinute === 0 ? '分钟不限' : `${row.rateLimitPerMinute}/min`
  const daily = row.dailyLimit === 0 ? '日不限' : `${row.dailyLimit}/day`
  return `${minute} · ${daily}`
}

const validateForm = () => {
  clearFormErrors()

  if (!form.name.trim()) {
    setFormError('name', '请输入密钥名称')
  }
  if (!form.environment) {
    setFormError('environment', '请选择环境')
  }
  if (!isNonNegativeInteger(form.rateLimitPerMinute)) {
    setFormError('rateLimitPerMinute', '请输入0或正整数')
  }
  if (!isNonNegativeInteger(form.dailyLimit)) {
    setFormError('dailyLimit', '请输入0或正整数')
  }
  if (!isValidDateTime(form.expireTime || '')) {
    setFormError('expireTime', '请选择有效的过期时间')
  }
  if (form.permissionCodes.length === 0) {
    setFormError('permissionCodes', '请至少选择一项接口权限')
  }

  const messages = Object.values(formErrors)
  if (messages.length > 0) {
    showToast(messages[0])
    return false
  }
  return true
}

const submitForm = async () => {
  if (!validateForm()) {
    return
  }
  saving.value = true
  try {
    const payload: ApiKeyCreateRequest = {
      ...form,
      expireTime: toApiDateTime(form.expireTime),
      ipWhitelist: ipInput.value.split(/\r?\n/).map(item => item.trim()).filter(Boolean),
      permissionCodes: form.permissionCodes
    }
    if (editingId.value) {
      await updateApiKey(editingId.value, payload)
      showToast('更新成功')
    } else {
      const response = await createApiKey(payload)
      createdAppId.value = response.data.apiKey.appId
      createdSecret.value = response.data.appSecret
      createdPermissionCodes.value = [...payload.permissionCodes]
      createdExampleEndpointId.value = chooseDefaultExampleEndpoint(createdExampleEndpoints.value)?.id || null
      showToast('创建成功')
    }
    showForm.value = false
    await loadApiKeys()
  } catch (error: any) {
    showToast(error.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const actionOperationMap = {
  enable: 'ENABLE',
  disable: 'DISABLE',
  revoke: 'REVOKE'
} as const

const actionText = (action: keyof typeof actionOperationMap) => {
  return getDictValue('api_key_operation', actionOperationMap[action])
}

const handleStatus = async (row: ApiKey, action: keyof typeof actionOperationMap) => {
  const actionMap = {
    enable: { api: enableApiKey },
    disable: { api: disableApiKey },
    revoke: { api: revokeApiKey }
  }
  const target = actionMap[action]
  const text = actionText(action)
  try {
    await showDialog({
      title: `${text}确认`,
      message: `确认${text} ${row.name}？`,
      showCancelButton: true
    })
    await target.api(row.id)
    showToast(`${text}成功`)
    await loadApiKeys()
  } catch {
    // 用户取消
  }
}

const handleSearch = () => {
  pagination.page = 0
  loadApiKeys()
}

const resetFilters = () => {
  filters.keyword = ''
  filters.status = ''
  filters.environment = ''
  pagination.page = 0
  loadApiKeys()
}

const handlePageChange = (page: number) => {
  const total = pagination.totalPages || 1
  pagination.page = Math.min(Math.max(page - 1, 0), total - 1)
  jumpPage.value = String(pagination.page + 1)
  loadApiKeys()
}

const submitJumpPage = () => {
  const page = Number(jumpPage.value)
  if (!Number.isFinite(page) || page < 1 || page > Math.max(pagination.totalPages, 1)) {
    jumpPage.value = String(pagination.page + 1)
    return
  }
  handlePageChange(page)
}

const formatTime = (time?: string) => time ? time.replace('T', ' ').substring(0, 19) : '-'

const permissionLabel = (code: string) => getDictValue('api_permission', code)

const selectPermission = (code: string) => {
  selectedPermissionCode.value = code
}

const isPermissionSelected = (code: string) => form.permissionCodes.includes(code)

const togglePermission = (code: string, checked: boolean) => {
  if (checked && !form.permissionCodes.includes(code)) {
    form.permissionCodes.push(code)
  }
  if (!checked) {
    form.permissionCodes = form.permissionCodes.filter(item => item !== code)
  }
  delete formErrors.permissionCodes
}

const eventChecked = (event: Event) => {
  return (event.target as HTMLInputElement).checked
}

const endpointRequestExample = (endpoint: ExternalApiEndpoint) => {
  return endpoint.requestExample || `${endpoint.method} ${endpoint.pathPattern}`
}

const endpointErrorCodes = (endpoint: ExternalApiEndpoint) => {
  return endpoint.errorCodes || '401 签名失败；403 权限不足；429 触发限流'
}

const endpointUsageNotes = (endpoint: ExternalApiEndpoint) => {
  return endpoint.usageNotes || '调用时必须携带 API Key 签名请求头。'
}

const endpointSuccessExample = (endpoint: ExternalApiEndpoint) => {
  return endpoint.successExample || endpoint.responseExample || '{"code":200,"data":{}}'
}

const endpointFailureExample = (endpoint: ExternalApiEndpoint) => {
  return endpoint.failureExample || '{"code":401,"message":"API 签名验证失败"}'
}

const endpointCodeNotes = (endpoint: ExternalApiEndpoint) => {
  return endpoint.codeNotes || endpoint.usageNotes || '生产中请把 App Secret 保存在服务端环境变量中。'
}

const schemaFields = (value?: string) => endpointSchemaFields(value)

const hasSchema = (value?: string) => schemaFields(value).length > 0

const buildEndpointCode = (endpoint: ExternalApiEndpoint, language: CodeExampleLanguage, realSecret = false) => {
  return buildCodeExample(language, {
    endpoint,
    baseUrl: exampleBaseUrl.value,
    appId: realSecret ? createdAppId.value : 'APP_ID',
    appSecret: realSecret ? createdSecret.value : 'APP_SECRET',
    usePlaceholders: !realSecret
  })
}

const currentCreatedCode = computed(() => {
  if (!createdExampleEndpoint.value || !createdSecret.value) {
    return ''
  }
  return buildEndpointCode(createdExampleEndpoint.value, createdCodeLanguage.value, true)
})

const chooseDefaultExampleEndpoint = (list: ExternalApiEndpoint[]) => {
  return list.find(endpoint => endpoint.method === 'GET' && !endpoint.pathPattern.includes('export'))
    || list.find(endpoint => endpoint.method !== 'DELETE')
    || list[0]
}

const copyText = async (text: string) => {
  if (!text) {
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制')
  } catch {
    showToast('复制失败，请手动选择复制')
  }
}

const closeCreatedSecret = () => {
  createdSecret.value = ''
  createdAppId.value = ''
  createdPermissionCodes.value = []
  createdExampleEndpointId.value = null
}

const signatureVectorState = computed(() => validateSignatureTestVector())

onMounted(async () => {
  await loadAllDicts()
  await Promise.all([loadApiKeys(), loadPermissions(), loadServiceStatus()])
})
</script>

<template>
  <div class="api-key-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">API授权管理</h1>
        <p class="page-subtitle">管理外部系统调用凭证、权限和访问边界</p>
      </div>
      <button class="btn-primary" @click="openCreate">新增密钥</button>
    </div>

    <div class="service-status-card" :class="{ paused: !serviceStatus.available, locked: !serviceStatus.deploymentEnabled }">
      <div>
        <span class="service-kicker">外部 API 服务状态</span>
        <strong>{{ serviceStatus.message }}</strong>
        <p>
          部署级开关：{{ serviceStatus.deploymentEnabled ? '已启用' : '未启用' }}；
          运行时开关：{{ serviceStatus.runtimeEnabled ? '开启' : '暂停' }}
        </p>
      </div>
      <button
        class="switch-btn"
        :class="{ on: serviceStatus.runtimeEnabled && serviceStatus.deploymentEnabled }"
        :disabled="serviceStatusLoading || !serviceStatus.deploymentEnabled"
        @click="toggleExternalApiService"
      >
        <span></span>
        {{ serviceStatus.runtimeEnabled && serviceStatus.deploymentEnabled ? '暂停服务' : '开启服务' }}
      </button>
    </div>

    <div class="filter-bar">
      <input v-model="filters.keyword" class="filter-input" placeholder="密钥名称或App ID" @keyup.enter="handleSearch" />
      <select v-model="filters.status" class="filter-select">
        <option value="">全部状态</option>
        <option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <select v-model="filters.environment" class="filter-select">
        <option value="">全部环境</option>
        <option v-for="opt in environmentOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <button class="btn-outline" @click="resetFilters">重置</button>
      <button class="btn-primary compact" @click="handleSearch">搜索</button>
    </div>

    <div class="content-card">
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>密钥名称</th>
              <th>App ID</th>
              <th>状态</th>
              <th>环境</th>
              <th>权限</th>
              <th>最后调用</th>
              <th>限流</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="8" class="empty-cell">加载中...</td>
            </tr>
            <tr v-else-if="apiKeys.length === 0">
              <td colspan="8" class="empty-cell">暂无API密钥</td>
            </tr>
            <tr v-for="row in apiKeys" v-else :key="row.id">
              <td>
                <button class="link-btn strong" @click="router.push(`/api-keys/${row.id}`)">{{ row.name }}</button>
                <div class="subtle">{{ row.appSecretFingerprint }}</div>
              </td>
              <td><span class="mono">{{ row.appId }}</span></td>
              <td><span class="status-badge" :class="row.status.toLowerCase()">{{ getDictValue('api_key_status', row.status) }}</span></td>
              <td>{{ getDictValue('api_key_environment', row.environment) }}</td>
              <td>
                <span class="permission-count">{{ row.permissionCodes.length }}</span>
              </td>
              <td>{{ formatTime(row.lastUsedTime) }}</td>
              <td>{{ formatLimitText(row) }}</td>
              <td>
                <div class="actions">
                  <button class="link-btn" @click="openEdit(row)">编辑</button>
                  <button v-if="row.status !== 'ACTIVE'" class="link-btn" @click="handleStatus(row, 'enable')">{{ actionText('enable') }}</button>
                  <button v-if="row.status === 'ACTIVE'" class="link-btn" @click="handleStatus(row, 'disable')">{{ actionText('disable') }}</button>
                  <button class="link-btn danger" @click="handleStatus(row, 'revoke')">{{ actionText('revoke') }}</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="pagination.totalElements > 0" class="table-footer">
        <span>共 {{ pagination.totalElements }} 条，第 {{ pagination.page + 1 }} / {{ pagination.totalPages || 1 }} 页</span>
        <div class="pagination">
          <button class="page-btn" :disabled="pagination.page === 0" @click="handlePageChange(pagination.page)">上一页</button>
          <template v-for="item in paginationItems" :key="item">
            <button
              v-if="typeof item === 'number'"
              class="page-btn number"
              :class="{ active: item === pagination.page }"
              @click="handlePageChange(item + 1)"
            >
              {{ item + 1 }}
            </button>
            <span v-else class="page-ellipsis">...</span>
          </template>
          <button class="page-btn" :disabled="pagination.page >= pagination.totalPages - 1" @click="handlePageChange(pagination.page + 2)">下一页</button>
          <label class="jump-control">
            跳至
            <input v-model="jumpPage" type="number" min="1" :max="Math.max(pagination.totalPages, 1)" @change="submitJumpPage" />
          </label>
        </div>
      </div>
    </div>

    <div v-if="showForm" class="modal-mask">
      <div class="modal-panel">
        <div class="modal-header">
          <h2>{{ editingId ? '编辑密钥' : '新增密钥' }}</h2>
          <button class="icon-btn" @click="showForm = false">×</button>
        </div>
        <div class="form-grid">
          <label class="name-field">
            <span class="form-label">密钥名称 <span class="required">*</span></span>
            <input v-model="form.name" class="form-input" :class="{ invalid: formErrors.name }" @input="delete formErrors.name" />
            <span v-if="formErrors.name" class="field-error">{{ formErrors.name }}</span>
          </label>
          <label>
            <span class="form-label">环境 <span class="required">*</span></span>
            <select v-model="form.environment" class="form-input" :class="{ invalid: formErrors.environment }" @change="delete formErrors.environment">
              <option v-for="opt in environmentOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
            <span v-if="formErrors.environment" class="field-error">{{ formErrors.environment }}</span>
          </label>
          <label>
            <span class="form-label">分钟限流 <span class="required">*</span></span>
            <input
              v-model.number="form.rateLimitPerMinute"
              type="number"
              min="0"
              step="1"
              class="form-input"
              :class="{ invalid: formErrors.rateLimitPerMinute }"
              @input="delete formErrors.rateLimitPerMinute"
            />
            <span class="field-hint">填 0 表示不限制分钟请求次数</span>
            <span v-if="formErrors.rateLimitPerMinute" class="field-error">{{ formErrors.rateLimitPerMinute }}</span>
          </label>
          <label>
            <span class="form-label">日限额 <span class="required">*</span></span>
            <input
              v-model.number="form.dailyLimit"
              type="number"
              min="0"
              step="1"
              class="form-input"
              :class="{ invalid: formErrors.dailyLimit }"
              @input="delete formErrors.dailyLimit"
            />
            <span class="field-hint">填 0 表示不限制每日请求次数</span>
            <span v-if="formErrors.dailyLimit" class="field-error">{{ formErrors.dailyLimit }}</span>
          </label>
          <label>
            <span class="form-label">过期时间</span>
            <div class="datetime-control">
              <input
                ref="expireTimeInputRef"
                v-model="form.expireTime"
                type="datetime-local"
                class="form-input"
                :class="{ invalid: formErrors.expireTime }"
                @input="delete formErrors.expireTime"
                @click="openExpireTimePicker"
                @keydown.enter.prevent="openExpireTimePicker"
              />
              <button
                type="button"
                class="btn-outline slim never-expire-btn"
                :class="{ active: !form.expireTime }"
                @click="clearExpireTime"
              >
                永不过期
              </button>
            </div>
            <span v-if="formErrors.expireTime" class="field-error">{{ formErrors.expireTime }}</span>
          </label>
          <label class="wide">
            <span>描述</span>
            <textarea v-model="form.description" class="form-input" rows="2"></textarea>
          </label>
          <label class="wide">
            <span>IP白名单</span>
            <textarea v-model="ipInput" class="form-input" rows="3" placeholder="每行一个IP或CIDR，留空表示不限"></textarea>
          </label>
        </div>

        <div class="permission-section">
          <div class="permission-title">
            <div>
              <span class="form-label">接口权限 <span class="required">*</span></span>
              <span class="permission-summary">已选 {{ form.permissionCodes.length }} / {{ permissionGroups.length }}</span>
            </div>
            <span v-if="formErrors.permissionCodes" class="field-error inline">{{ formErrors.permissionCodes }}</span>
          </div>

          <div class="permission-workbench">
            <aside class="permission-sidebar">
              <div class="permission-tools">
                <input v-model="permissionSearch" class="form-input" placeholder="搜索权限、路径或方法" />
                <label class="selected-filter">
                  <input v-model="showSelectedPermissionsOnly" type="checkbox" />
                  <span>只看已选</span>
                </label>
              </div>

              <button
                v-for="group in filteredPermissionGroups"
                :key="group.permissionCode"
                type="button"
                class="permission-row"
                :class="{ active: selectedPermissionGroup?.permissionCode === group.permissionCode, checked: isPermissionSelected(group.permissionCode) }"
                @click="selectPermission(group.permissionCode)"
              >
                <input
                  :checked="isPermissionSelected(group.permissionCode)"
                  type="checkbox"
                  @click.stop
                  @change="togglePermission(group.permissionCode, eventChecked($event))"
                />
                <span class="permission-row-main">
                  <strong>{{ permissionLabel(group.permissionCode) }}</strong>
                  <code>{{ group.permissionCode }}</code>
                </span>
                <span class="endpoint-count">{{ group.endpoints.length }}</span>
              </button>

              <div v-if="filteredPermissionGroups.length === 0" class="permission-empty">没有匹配的接口权限</div>
            </aside>

            <section class="permission-detail" v-if="selectedPermissionGroup">
              <div class="detail-header">
                <div>
                  <h3>{{ permissionLabel(selectedPermissionGroup.permissionCode) }}</h3>
                  <code>{{ selectedPermissionGroup.permissionCode }}</code>
                </div>
                <label class="detail-toggle">
                  <input
                    :checked="isPermissionSelected(selectedPermissionGroup.permissionCode)"
                    type="checkbox"
                    @change="togglePermission(selectedPermissionGroup.permissionCode, eventChecked($event))"
                  />
                  <span>授权此 API</span>
                </label>
              </div>

              <div class="signature-panel">
                <div>
                  <span>认证头</span>
                  <code>X-App-Id / X-Timestamp / X-Nonce / X-Signature</code>
                </div>
                <div>
                  <span>签名结构</span>
                  <code>METHOD + path + query + timestamp + nonce + bodySha256</code>
                </div>
                <div>
                  <span>安全提示</span>
                  <code>禁止传输 X-App-Secret；Secret 仅创建时展示一次</code>
                </div>
              </div>

              <div class="endpoint-detail-list">
                <article v-for="endpoint in selectedEndpoints" :key="endpoint.id" class="endpoint-detail-item">
                  <div class="endpoint-detail-head">
                    <span class="method-badge" :class="endpoint.method.toLowerCase()">{{ endpoint.method }}</span>
                    <code>{{ endpoint.pathPattern }}</code>
                  </div>
                  <p>{{ endpoint.description || '暂无接口说明' }}</p>
                  <div class="doc-grid">
                    <div>
                      <span>请求示例</span>
                      <pre>{{ endpointRequestExample(endpoint) }}</pre>
                    </div>
                    <div>
                      <span>响应示例</span>
                      <pre>{{ endpointSuccessExample(endpoint) }}</pre>
                    </div>
                    <div>
                      <span>失败示例</span>
                      <pre>{{ endpointFailureExample(endpoint) }}</pre>
                    </div>
                    <div>
                      <span>错误码</span>
                      <pre>{{ endpointErrorCodes(endpoint) }}</pre>
                    </div>
                    <div>
                      <span>使用提示</span>
                      <pre>{{ endpointUsageNotes(endpoint) }}</pre>
                    </div>
                  </div>

                  <div class="schema-grid">
                    <div>
                      <span>路径参数</span>
                      <table v-if="hasSchema(endpoint.pathParamsSchema)" class="schema-table">
                        <tbody>
                          <tr v-for="field in schemaFields(endpoint.pathParamsSchema)" :key="field.name">
                            <td><code>{{ field.name }}</code></td>
                            <td>{{ field.type }}</td>
                            <td>{{ field.required ? '必填' : '可选' }}</td>
                            <td>{{ field.description || '-' }}</td>
                          </tr>
                        </tbody>
                      </table>
                      <pre v-else>{{ endpoint.pathParamsExample || '{}' }}</pre>
                    </div>
                    <div>
                      <span>Query 参数</span>
                      <table v-if="hasSchema(endpoint.querySchema)" class="schema-table">
                        <tbody>
                          <tr v-for="field in schemaFields(endpoint.querySchema)" :key="field.name">
                            <td><code>{{ field.name }}</code></td>
                            <td>{{ field.type }}</td>
                            <td>{{ field.required ? '必填' : '可选' }}</td>
                            <td>{{ field.description || '-' }}</td>
                          </tr>
                        </tbody>
                      </table>
                      <pre v-else>{{ endpoint.queryExample || '{}' }}</pre>
                    </div>
                    <div>
                      <span>Body 参数</span>
                      <table v-if="hasSchema(endpoint.bodySchema)" class="schema-table">
                        <tbody>
                          <tr v-for="field in schemaFields(endpoint.bodySchema)" :key="field.name">
                            <td><code>{{ field.name }}</code></td>
                            <td>{{ field.type }}</td>
                            <td>{{ field.required ? '必填' : '可选' }}</td>
                            <td>{{ field.description || '-' }}</td>
                          </tr>
                        </tbody>
                      </table>
                      <pre v-else>{{ endpoint.bodyExample || '{}' }}</pre>
                    </div>
                  </div>

                  <div class="code-example">
                    <div class="code-toolbar">
                      <div class="code-tabs">
                        <button
                          v-for="option in codeLanguageOptions"
                          :key="option.value"
                          type="button"
                          :class="{ active: selectedCodeLanguage === option.value }"
                          @click="selectedCodeLanguage = option.value"
                        >
                          {{ option.label }}
                        </button>
                      </div>
                      <button type="button" class="btn-outline slim" @click="copyText(buildEndpointCode(endpoint, selectedCodeLanguage))">复制示例</button>
                    </div>
                    <pre>{{ buildEndpointCode(endpoint, selectedCodeLanguage) }}</pre>
                    <p class="code-note">{{ endpointCodeNotes(endpoint) }}</p>
                  </div>
                </article>
              </div>
            </section>
          </div>
        </div>

        <div class="modal-actions">
          <button class="btn-outline" @click="showForm = false">取消</button>
          <button class="btn-primary compact" :disabled="saving" @click="submitForm">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </div>
    </div>

    <div v-if="createdSecret" class="modal-mask">
      <div class="secret-panel">
        <h2>密钥已创建</h2>
        <p>App Secret 只展示一次，关闭后无法再次查看。</p>
        <div class="secret-box">
          <div><span>App ID</span><code>{{ createdAppId }}</code></div>
          <div><span>App Secret</span><code>{{ createdSecret }}</code></div>
        </div>
        <div class="runtime-example">
          <div class="runtime-controls">
            <label>
              <span>调用地址</span>
              <input v-model="exampleBaseUrl" class="form-input" />
            </label>
            <label>
              <span>示例接口</span>
              <select v-model.number="createdExampleEndpointId" class="form-input">
                <option v-for="endpoint in createdExampleEndpoints" :key="endpoint.id" :value="endpoint.id">
                  {{ endpoint.method }} {{ endpoint.pathPattern }}
                </option>
              </select>
            </label>
          </div>
          <div class="code-toolbar">
            <div class="code-tabs">
              <button
                v-for="option in codeLanguageOptions"
                :key="option.value"
                type="button"
                :class="{ active: createdCodeLanguage === option.value }"
                @click="createdCodeLanguage = option.value"
              >
                {{ option.label }}
              </button>
            </div>
            <button type="button" class="btn-outline slim" @click="copyText(currentCreatedCode)">复制可运行示例</button>
          </div>
          <pre>{{ currentCreatedCode }}</pre>
          <p class="code-note">生产环境请把 App Secret 放在服务端环境变量中，不要提交到 Git，也不要放在浏览器前端代码里。</p>
          <p class="code-note">签名向量校验：{{ signatureVectorState.canonicalQueryMatches ? '已通过' : '未通过' }}</p>
        </div>
        <button class="btn-primary compact" @click="closeCreatedSecret">我已保存</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.api-key-page { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--spacing-md); }
.page-title { margin: 0 0 4px; font-size: 1.5rem; color: var(--gray-900); }
.page-subtitle { margin: 0; color: var(--gray-500); font-size: 0.875rem; }
.service-status-card { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); background: #ecfdf5; border: 1px solid rgba(16,185,129,.28); border-radius: var(--radius-lg); padding: var(--spacing-md); box-shadow: var(--shadow-sm); }
.service-status-card.paused { background: #fff7ed; border-color: rgba(245,158,11,.32); }
.service-status-card.locked { background: var(--gray-50); border-color: var(--gray-200); }
.service-status-card strong { display: block; color: var(--gray-900); font-size: 1rem; margin-top: 3px; }
.service-status-card p { margin: 6px 0 0; color: var(--gray-600); font-size: 0.8125rem; }
.service-kicker { color: var(--gray-500); font-size: 0.75rem; font-weight: 600; }
.switch-btn { display: inline-flex; align-items: center; gap: 8px; border: 1px solid var(--gray-300); border-radius: 999px; background: white; color: var(--gray-700); padding: 7px 12px 7px 8px; cursor: pointer; font-size: 0.8125rem; white-space: nowrap; }
.switch-btn span { width: 28px; height: 16px; border-radius: 999px; background: var(--gray-300); position: relative; transition: background .2s ease; }
.switch-btn span::after { content: ""; position: absolute; top: 2px; left: 2px; width: 12px; height: 12px; border-radius: 50%; background: white; transition: transform .2s ease; box-shadow: var(--shadow-sm); }
.switch-btn.on { color: #047857; border-color: rgba(16,185,129,.4); }
.switch-btn.on span { background: #10b981; }
.switch-btn.on span::after { transform: translateX(12px); }
.switch-btn:disabled { opacity: .55; cursor: not-allowed; }
.filter-bar, .content-card { background: white; border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.filter-bar { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md); flex-wrap: wrap; }
.filter-input, .filter-select, .form-input { border: 1px solid var(--gray-300); border-radius: var(--radius); padding: 8px 10px; font-size: 0.875rem; background: white; }
.form-input.invalid { border-color: #dc2626; background: #fef2f2; }
.form-input.invalid:focus { outline: 2px solid rgba(220, 38, 38, .18); outline-offset: 1px; }
.filter-input { min-width: 240px; }
.btn-primary, .btn-outline, .page-btn, .link-btn { cursor: pointer; border-radius: var(--radius); font-size: 0.875rem; }
.btn-primary { border: none; background: var(--primary-color); color: white; padding: 9px 16px; font-weight: 600; }
.btn-primary.compact { padding: 8px 14px; }
.btn-outline, .page-btn { border: 1px solid var(--gray-300); background: white; color: var(--gray-700); padding: 8px 14px; }
.btn-outline.slim { padding: 8px 10px; white-space: nowrap; }
.table-container { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 12px; border-bottom: 1px solid var(--gray-100); text-align: left; font-size: 0.875rem; white-space: nowrap; }
.data-table th { background: var(--gray-50); color: var(--gray-600); font-weight: 600; }
.empty-cell { text-align: center !important; color: var(--gray-500); padding: 32px !important; }
.mono { font-family: var(--font-mono), monospace; }
.subtle { color: var(--gray-500); font-size: 0.75rem; margin-top: 4px; }
.status-badge { padding: 3px 8px; border-radius: var(--radius-sm); font-size: 0.75rem; font-weight: 600; background: var(--gray-100); color: var(--gray-700); }
.status-badge.active { background: rgba(16,185,129,.12); color: #059669; }
.status-badge.disabled, .status-badge.revoked, .status-badge.expired { background: rgba(239,68,68,.12); color: #dc2626; }
.permission-count { display: inline-flex; min-width: 24px; justify-content: center; border-radius: var(--radius-sm); background: var(--primary-bg); color: var(--primary-color); padding: 2px 8px; }
.actions { display: flex; gap: 8px; }
.link-btn { border: none; background: transparent; color: var(--primary-color); padding: 0; }
.link-btn.strong { font-weight: 600; }
.link-btn.danger { color: #dc2626; }
.table-footer { display: flex; justify-content: space-between; align-items: center; padding: var(--spacing-md); color: var(--gray-500); }
.pagination { display: flex; gap: var(--spacing-sm); align-items: center; flex-wrap: wrap; }
.page-btn.number { min-width: 34px; padding-left: 10px; padding-right: 10px; }
.page-btn.active { background: var(--primary-color); border-color: var(--primary-color); color: white; }
.page-ellipsis { color: var(--gray-500); padding: 0 2px; }
.jump-control { display: inline-flex; align-items: center; gap: 6px; color: var(--gray-500); font-size: 0.8125rem; }
.jump-control input { width: 64px; border: 1px solid var(--gray-300); border-radius: var(--radius); padding: 7px 8px; font-size: 0.875rem; }
.page-btn:disabled { opacity: .5; cursor: not-allowed; }
.modal-mask { position: fixed; inset: 0; background: rgba(15, 23, 42, .45); z-index: 1000; display: flex; align-items: center; justify-content: center; padding: var(--spacing-md); }
.modal-panel, .secret-panel { background: white; border-radius: var(--radius-lg); box-shadow: var(--shadow-md); width: min(1080px, 100%); max-height: 90vh; overflow-y: auto; padding: var(--spacing-lg); }
.secret-panel { width: min(900px, 100%); }
.modal-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--spacing-md); }
.modal-header h2, .secret-panel h2 { margin: 0; font-size: 1.125rem; }
.icon-btn { border: none; background: transparent; font-size: 1.5rem; cursor: pointer; color: var(--gray-500); }
.form-grid { display: grid; grid-template-columns: minmax(220px, 1.15fr) minmax(180px, .85fr) minmax(260px, 1fr); gap: var(--spacing-md); align-items: start; }
.form-grid label { display: flex; flex-direction: column; gap: 6px; color: var(--gray-700); font-size: 0.8125rem; }
.form-label { color: var(--gray-700); font-weight: 600; }
.required { color: #dc2626; font-weight: 700; }
.field-error { color: #dc2626; font-size: 0.75rem; line-height: 1.35; }
.field-hint { color: var(--gray-500); font-size: 0.75rem; line-height: 1.35; }
.field-error.inline { margin-left: 8px; }
.form-grid .name-field { grid-column: span 2; }
.form-grid .wide { grid-column: 1 / -1; }
.datetime-control { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; align-items: center; }
.datetime-control .form-input { min-width: 0; }
.never-expire-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; }
.never-expire-btn.active { background: #ecfdf5; border-color: rgba(16,185,129,.45); color: #047857; font-weight: 700; box-shadow: 0 0 0 2px rgba(16,185,129,.12); }
.never-expire-btn.active::before { content: "✓"; display: inline-grid; place-items: center; width: 16px; height: 16px; border-radius: 999px; background: #10b981; color: white; font-size: 0.6875rem; line-height: 1; }
.permission-section { margin-top: var(--spacing-lg); }
.permission-title { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-bottom: var(--spacing-sm); }
.permission-summary { margin-left: 10px; color: var(--gray-500); font-size: 0.75rem; }
.permission-workbench { display: grid; grid-template-columns: 320px minmax(0, 1fr); border: 1px solid var(--gray-200); border-radius: var(--radius); min-height: 460px; overflow: hidden; }
.permission-sidebar { background: var(--gray-50); border-right: 1px solid var(--gray-200); padding: var(--spacing-sm); overflow-y: auto; max-height: 560px; }
.permission-tools { display: flex; flex-direction: column; gap: 8px; margin-bottom: var(--spacing-sm); }
.selected-filter { display: inline-flex; align-items: center; gap: 6px; color: var(--gray-600); font-size: 0.8125rem; }
.permission-row { width: 100%; display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 10px; border: 1px solid transparent; border-radius: var(--radius); background: transparent; padding: 10px; text-align: left; cursor: pointer; }
.permission-row:hover { background: white; border-color: var(--gray-200); }
.permission-row.active { background: white; border-color: var(--primary-color); box-shadow: 0 0 0 1px rgba(37, 99, 235, .08); }
.permission-row.checked .endpoint-count { background: var(--primary-color); color: white; }
.permission-row-main { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.permission-row-main strong { color: var(--gray-800); font-size: 0.875rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.permission-row-main code, .detail-header code { color: var(--gray-500); font-family: var(--font-mono), monospace; font-size: 0.75rem; word-break: break-all; }
.endpoint-count { justify-self: end; min-width: 28px; text-align: center; border-radius: var(--radius-sm); background: var(--gray-200); color: var(--gray-600); padding: 2px 6px; font-size: 0.75rem; }
.permission-empty { color: var(--gray-500); font-size: 0.8125rem; padding: var(--spacing-md); text-align: center; }
.permission-detail { padding: var(--spacing-md); overflow-y: auto; max-height: 560px; }
.detail-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--spacing-sm); margin-bottom: var(--spacing-md); }
.detail-header h3 { margin: 0 0 4px; font-size: 1rem; color: var(--gray-900); }
.detail-toggle { display: inline-flex; align-items: center; gap: 6px; border: 1px solid var(--gray-300); border-radius: var(--radius); padding: 8px 10px; color: var(--gray-700); font-size: 0.8125rem; white-space: nowrap; }
.signature-panel { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-bottom: var(--spacing-md); }
.signature-panel div { border: 1px solid var(--gray-200); border-radius: var(--radius); padding: 10px; background: var(--gray-50); }
.signature-panel span, .doc-grid span, .schema-grid span, .runtime-controls span { display: block; color: var(--gray-500); font-size: 0.75rem; margin-bottom: 5px; }
.signature-panel code { font-family: var(--font-mono), monospace; font-size: 0.75rem; color: var(--gray-800); word-break: break-word; }
.endpoint-detail-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.endpoint-detail-item { border: 1px solid var(--gray-200); border-radius: var(--radius); padding: var(--spacing-sm); }
.endpoint-detail-item p { margin: 8px 0 10px; color: var(--gray-600); font-size: 0.8125rem; }
.endpoint-detail-head { display: flex; gap: 8px; align-items: center; min-width: 0; }
.endpoint-detail-head code { font-family: var(--font-mono), monospace; font-size: 0.8125rem; color: var(--gray-800); word-break: break-all; }
.method-badge { min-width: 56px; text-align: center; border-radius: var(--radius-sm); padding: 3px 6px; font-family: var(--font-mono), monospace; font-size: 0.75rem; font-weight: 700; background: var(--gray-100); color: var(--gray-700); }
.method-badge.get { background: rgba(16,185,129,.12); color: #047857; }
.method-badge.post { background: rgba(37,99,235,.12); color: #1d4ed8; }
.method-badge.put { background: rgba(245,158,11,.16); color: #b45309; }
.method-badge.delete { background: rgba(239,68,68,.12); color: #dc2626; }
.doc-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.doc-grid div { background: var(--gray-50); border-radius: var(--radius-sm); padding: 8px; }
.doc-grid pre, .schema-grid pre, .code-example pre, .runtime-example pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: var(--font-mono), monospace; font-size: 0.75rem; color: var(--gray-800); line-height: 1.45; }
.schema-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; margin-top: 8px; }
.schema-grid > div { background: white; border: 1px solid var(--gray-200); border-radius: var(--radius-sm); padding: 8px; overflow-x: auto; }
.schema-table { width: 100%; border-collapse: collapse; font-size: 0.75rem; color: var(--gray-700); }
.schema-table td { border-top: 1px solid var(--gray-100); padding: 6px 5px; vertical-align: top; }
.schema-table tr:first-child td { border-top: none; }
.schema-table code { font-family: var(--font-mono), monospace; color: var(--gray-900); }
.code-example, .runtime-example { margin-top: var(--spacing-sm); border: 1px solid var(--gray-200); border-radius: var(--radius); padding: var(--spacing-sm); background: var(--gray-50); }
.code-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; }
.code-tabs { display: inline-flex; border: 1px solid var(--gray-300); border-radius: var(--radius); overflow: hidden; background: white; }
.code-tabs button { border: none; border-left: 1px solid var(--gray-200); background: transparent; color: var(--gray-600); padding: 7px 10px; cursor: pointer; font-size: 0.8125rem; }
.code-tabs button:first-child { border-left: none; }
.code-tabs button.active { background: var(--primary-color); color: white; }
.code-example pre, .runtime-example pre { max-height: 320px; overflow: auto; background: #111827; color: #e5e7eb; border-radius: var(--radius-sm); padding: 10px; }
.code-note { margin: 8px 0 0; color: var(--gray-600); font-size: 0.75rem; line-height: 1.5; }
.modal-actions { display: flex; justify-content: flex-end; gap: var(--spacing-sm); margin-top: var(--spacing-lg); }
.secret-panel p { color: var(--gray-600); }
.secret-box { display: flex; flex-direction: column; gap: var(--spacing-sm); margin: var(--spacing-md) 0; }
.secret-box div { display: flex; flex-direction: column; gap: 6px; background: var(--gray-50); border-radius: var(--radius); padding: var(--spacing-sm); }
.secret-box span { color: var(--gray-500); font-size: 0.75rem; }
.secret-box code { word-break: break-all; font-family: var(--font-mono), monospace; }
.runtime-controls { display: grid; grid-template-columns: 1fr 1.4fr; gap: var(--spacing-sm); margin-bottom: var(--spacing-sm); }
.runtime-controls label { display: flex; flex-direction: column; gap: 4px; }
@media (max-width: 768px) {
  .page-header, .table-footer, .service-status-card { align-items: flex-start; flex-direction: column; }
  .filter-input, .filter-select { width: 100%; }
  .form-grid { grid-template-columns: 1fr; }
  .form-grid .name-field { grid-column: 1; }
  .datetime-control { grid-template-columns: 1fr; }
  .datetime-control .form-input, .datetime-control .btn-outline { width: 100%; }
  .permission-workbench { grid-template-columns: 1fr; }
  .permission-sidebar { border-right: none; border-bottom: 1px solid var(--gray-200); max-height: 300px; }
  .permission-detail { max-height: none; }
  .signature-panel, .doc-grid, .schema-grid, .runtime-controls { grid-template-columns: 1fr; }
  .detail-header { flex-direction: column; }
}
</style>
