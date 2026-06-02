<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getApiCallLogs, getApiKey } from '@/api/apiKey'
import { getDictValue, loadAllDicts } from '@/composables/useDict'
import type { ApiCallLog, ApiKey } from '@/types/apiKey'

const route = useRoute()
const router = useRouter()
const apiKey = ref<ApiKey | null>(null)
const recentLogs = ref<ApiCallLog[]>([])
const loading = ref(false)

const id = computed(() => Number(route.params.id))

const loadDetail = async () => {
  if (!Number.isFinite(id.value)) return
  loading.value = true
  try {
    const response = await getApiKey(id.value)
    apiKey.value = response.data
    const logResponse = await getApiCallLogs({ page: 0, size: 10, appId: response.data.appId })
    recentLogs.value = logResponse.data.content || []
  } catch (error: any) {
    showToast(error.message || '加载密钥详情失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (time?: string) => time ? time.replace('T', ' ').substring(0, 19) : '-'

onMounted(async () => {
  await loadAllDicts()
  await loadDetail()
})
</script>

<template>
  <div class="api-key-detail-page">
    <div class="page-header">
      <div>
        <button class="back-btn" @click="router.push('/api-keys')">返回</button>
        <h1 class="page-title">{{ apiKey?.name || 'API密钥详情' }}</h1>
      </div>
    </div>

    <div v-if="loading" class="content-card empty">加载中...</div>

    <template v-else-if="apiKey">
      <div class="detail-grid">
        <section class="content-card">
          <h2>基础信息</h2>
          <dl>
            <dt>App ID</dt>
            <dd class="mono">{{ apiKey.appId }}</dd>
            <dt>Secret指纹</dt>
            <dd>{{ apiKey.appSecretFingerprint }}</dd>
            <dt>密钥版本</dt>
            <dd>{{ apiKey.appSecretKeyVersion }}</dd>
            <dt>状态</dt>
            <dd>{{ getDictValue('api_key_status', apiKey.status) }}</dd>
            <dt>环境</dt>
            <dd>{{ getDictValue('api_key_environment', apiKey.environment) }}</dd>
            <dt>过期时间</dt>
            <dd>{{ formatTime(apiKey.expireTime) }}</dd>
            <dt>最后调用</dt>
            <dd>{{ formatTime(apiKey.lastUsedTime) }}</dd>
          </dl>
        </section>

        <section class="content-card">
          <h2>访问控制</h2>
          <dl>
            <dt>分钟限流</dt>
            <dd>{{ apiKey.rateLimitPerMinute }}</dd>
            <dt>日限额</dt>
            <dd>{{ apiKey.dailyLimit }}</dd>
            <dt>IP白名单</dt>
            <dd>
              <span v-if="apiKey.ipWhitelist.length === 0">不限</span>
              <span v-for="ip in apiKey.ipWhitelist" v-else :key="ip" class="chip">{{ ip }}</span>
            </dd>
          </dl>
        </section>
      </div>

      <section class="content-card">
        <h2>授权权限</h2>
        <div class="chip-list">
          <span v-for="code in apiKey.permissionCodes" :key="code" class="chip">
            {{ getDictValue('api_permission', code) }}
          </span>
          <span v-if="apiKey.permissionCodes.length === 0" class="muted">未授权任何外部接口</span>
        </div>
      </section>

      <section class="content-card">
        <h2>最近调用</h2>
        <table class="data-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>方法</th>
              <th>路径</th>
              <th>结果</th>
              <th>状态码</th>
              <th>耗时</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="recentLogs.length === 0">
              <td colspan="6" class="empty-cell">暂无调用记录</td>
            </tr>
            <tr v-for="log in recentLogs" :key="log.id">
              <td>{{ formatTime(log.requestTime) }}</td>
              <td>{{ log.method }}</td>
              <td class="mono">{{ log.endpoint }}</td>
              <td>{{ getDictValue('api_auth_result', log.authResult) }}</td>
              <td>{{ log.statusCode || '-' }}</td>
              <td>{{ log.responseTime ?? '-' }}ms</td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>
  </div>
</template>

<style scoped>
.api-key-detail-page { display: flex; flex-direction: column; gap: var(--spacing-lg); }
.page-title { margin: 6px 0 0; font-size: 1.5rem; color: var(--gray-900); }
.back-btn { border: none; background: transparent; padding: 0; color: var(--primary-color); cursor: pointer; }
.detail-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: var(--spacing-lg); }
.content-card { background: white; border-radius: var(--radius-lg); box-shadow: var(--shadow-sm); padding: var(--spacing-lg); overflow-x: auto; }
.content-card.empty, .empty-cell { color: var(--gray-500); text-align: center; }
h2 { margin: 0 0 var(--spacing-md); font-size: 1rem; color: var(--gray-800); }
dl { display: grid; grid-template-columns: 110px 1fr; gap: 10px 16px; margin: 0; }
dt { color: var(--gray-500); }
dd { margin: 0; color: var(--gray-800); }
.mono { font-family: var(--font-mono), monospace; }
.chip-list { display: flex; gap: 8px; flex-wrap: wrap; }
.chip { display: inline-flex; align-items: center; padding: 3px 8px; border-radius: var(--radius-sm); background: var(--primary-bg); color: var(--primary-color); margin: 0 6px 6px 0; font-size: 0.8125rem; }
.muted { color: var(--gray-500); }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 10px 12px; border-bottom: 1px solid var(--gray-100); text-align: left; font-size: 0.875rem; white-space: nowrap; }
.data-table th { background: var(--gray-50); color: var(--gray-600); }
@media (max-width: 768px) {
  .detail-grid { grid-template-columns: 1fr; }
  dl { grid-template-columns: 1fr; }
}
</style>
