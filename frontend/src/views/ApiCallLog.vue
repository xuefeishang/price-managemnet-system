<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { showToast } from 'vant'
import { getApiCallLogStatistics, getApiCallLogs } from '@/api/apiKey'
import { getDictOptions, getDictValue, loadAllDicts } from '@/composables/useDict'
import type { ApiCallLog, ApiCallLogStatistics } from '@/types/apiKey'

const loading = ref(false)
const logs = ref<ApiCallLog[]>([])
const statistics = ref<ApiCallLogStatistics | null>(null)

const pagination = reactive({
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0
})

const filters = reactive({
  appId: '',
  authResult: '',
  statusCode: '',
  startTime: '',
  endTime: ''
})

const authResultOptions = computed(() => getDictOptions('api_auth_result'))
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

const loadLogs = async () => {
  loading.value = true
  try {
    const response = await getApiCallLogs({
      page: pagination.page,
      size: pagination.size,
      appId: filters.appId || undefined,
      authResult: filters.authResult || undefined,
      statusCode: filters.statusCode ? Number(filters.statusCode) : undefined,
      startTime: filters.startTime || undefined,
      endTime: filters.endTime || undefined
    })
    logs.value = response.data.content || []
    pagination.totalElements = response.data.totalElements || 0
    pagination.totalPages = response.data.totalPages || 0
    jumpPage.value = String((response.data.number ?? pagination.page) + 1)
  } catch (error: any) {
    showToast(error.message || '加载调用日志失败')
  } finally {
    loading.value = false
  }
}

const loadStatistics = async () => {
  const response = await getApiCallLogStatistics({
    startTime: filters.startTime || undefined,
    endTime: filters.endTime || undefined
  })
  statistics.value = response.data
}

const handleSearch = () => {
  pagination.page = 0
  loadLogs()
  loadStatistics()
}

const resetFilters = () => {
  filters.appId = ''
  filters.authResult = ''
  filters.statusCode = ''
  filters.startTime = ''
  filters.endTime = ''
  pagination.page = 0
  loadLogs()
  loadStatistics()
}

const handlePageChange = (page: number) => {
  const total = pagination.totalPages || 1
  pagination.page = Math.min(Math.max(page - 1, 0), total - 1)
  jumpPage.value = String(pagination.page + 1)
  loadLogs()
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

onMounted(async () => {
  await loadAllDicts()
  await Promise.all([loadLogs(), loadStatistics()])
})
</script>

<template>
  <div class="api-call-log-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">API调用日志</h1>
        <p class="page-subtitle">追踪外部系统调用结果、耗时和认证失败原因</p>
      </div>
    </div>

    <div class="stats-row" v-if="statistics">
      <div class="stat-card">
        <div class="stat-value">{{ statistics.totalCalls }}</div>
        <div class="stat-label">调用总数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ statistics.authResultCount.SUCCESS || 0 }}</div>
        <div class="stat-label">成功调用</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ (statistics.totalCalls || 0) - (statistics.authResultCount.SUCCESS || 0) }}</div>
        <div class="stat-label">异常调用</div>
      </div>
    </div>

    <div class="filter-bar">
      <input v-model="filters.appId" class="filter-input" placeholder="App ID" @keyup.enter="handleSearch" />
      <select v-model="filters.authResult" class="filter-select">
        <option value="">全部结果</option>
        <option v-for="opt in authResultOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
      </select>
      <input v-model="filters.statusCode" class="filter-input small" placeholder="状态码" @keyup.enter="handleSearch" />
      <input v-model="filters.startTime" class="filter-input" placeholder="开始时间 yyyy-MM-dd HH:mm:ss" />
      <input v-model="filters.endTime" class="filter-input" placeholder="结束时间 yyyy-MM-dd HH:mm:ss" />
      <button class="btn-outline" @click="resetFilters">重置</button>
      <button class="btn-primary" @click="handleSearch">搜索</button>
    </div>

    <div class="content-card">
      <div class="table-container">
        <table class="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>App ID</th>
              <th>方法</th>
              <th>路径</th>
              <th>权限</th>
              <th>结果</th>
              <th>状态码</th>
              <th>耗时</th>
              <th>IP</th>
              <th>错误摘要</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="10" class="empty-cell">加载中...</td>
            </tr>
            <tr v-else-if="logs.length === 0">
              <td colspan="10" class="empty-cell">暂无调用日志</td>
            </tr>
            <tr v-for="log in logs" v-else :key="log.id">
              <td>{{ formatTime(log.requestTime) }}</td>
              <td class="mono">{{ log.appId || '-' }}</td>
              <td>{{ log.method }}</td>
              <td class="mono">{{ log.endpoint }}</td>
              <td>{{ log.permissionCode ? getDictValue('api_permission', log.permissionCode) : '-' }}</td>
              <td>
                <span class="result-badge" :class="{ success: log.authResult === 'SUCCESS' }">
                  {{ getDictValue('api_auth_result', log.authResult) }}
                </span>
              </td>
              <td>{{ log.statusCode || '-' }}</td>
              <td>{{ log.responseTime ?? '-' }}ms</td>
              <td>{{ log.ipAddress || '-' }}</td>
              <td class="error-cell" :title="log.errorMessage">{{ log.errorMessage || '-' }}</td>
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
  </div>
</template>

<style scoped>
.api-call-log-page { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.page-title { margin: 0 0 4px; font-size: 1.5rem; color: var(--gray-900); }
.page-subtitle { margin: 0; color: var(--gray-500); font-size: 0.875rem; }
.stats-row { display: grid; grid-template-columns: repeat(3, minmax(160px, 1fr)); gap: var(--spacing-md); }
.stat-card, .filter-bar, .content-card { background: white; border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); }
.stat-card { padding: var(--spacing-lg); }
.stat-value { font-size: 1.75rem; font-weight: 700; color: var(--primary-color); }
.stat-label { color: var(--gray-500); font-size: 0.875rem; margin-top: 4px; }
.filter-bar { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md); flex-wrap: wrap; }
.filter-input, .filter-select { border: 1px solid var(--gray-300); border-radius: var(--radius); padding: 8px 10px; font-size: 0.875rem; background: white; }
.filter-input { min-width: 210px; }
.filter-input.small { min-width: 90px; width: 100px; }
.btn-primary, .btn-outline, .page-btn { cursor: pointer; border-radius: var(--radius); font-size: 0.875rem; padding: 8px 14px; }
.btn-primary { border: none; background: var(--primary-color); color: white; font-weight: 600; }
.btn-outline, .page-btn { border: 1px solid var(--gray-300); background: white; color: var(--gray-700); }
.table-container { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 11px 12px; border-bottom: 1px solid var(--gray-100); text-align: left; font-size: 0.875rem; white-space: nowrap; }
.data-table th { background: var(--gray-50); color: var(--gray-600); font-weight: 600; }
.mono { font-family: var(--font-mono), monospace; }
.empty-cell { text-align: center !important; color: var(--gray-500); padding: 32px !important; }
.result-badge { padding: 3px 8px; border-radius: var(--radius-sm); background: rgba(239,68,68,.12); color: #dc2626; font-size: 0.75rem; font-weight: 600; }
.result-badge.success { background: rgba(16,185,129,.12); color: #059669; }
.error-cell { max-width: 240px; overflow: hidden; text-overflow: ellipsis; }
.table-footer { display: flex; justify-content: space-between; align-items: center; padding: var(--spacing-md); color: var(--gray-500); }
.pagination { display: flex; gap: var(--spacing-sm); align-items: center; flex-wrap: wrap; }
.page-btn.number { min-width: 34px; padding-left: 10px; padding-right: 10px; }
.page-btn.active { background: var(--primary-color); border-color: var(--primary-color); color: white; }
.page-ellipsis { color: var(--gray-500); padding: 0 2px; }
.jump-control { display: inline-flex; align-items: center; gap: 6px; color: var(--gray-500); font-size: 0.8125rem; }
.jump-control input { width: 64px; border: 1px solid var(--gray-300); border-radius: var(--radius); padding: 7px 8px; font-size: 0.875rem; }
.page-btn:disabled { opacity: .5; cursor: not-allowed; }
@media (max-width: 768px) {
  .stats-row { grid-template-columns: 1fr; }
  .filter-input, .filter-select { width: 100%; }
  .table-footer { align-items: flex-start; flex-direction: column; }
}
</style>
