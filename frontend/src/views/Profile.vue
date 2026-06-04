<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import { useUserStore } from '@/store/useUserStore'
import {
  changeProfilePassword,
  getMyOperationLogs,
  getProfileDetail,
  getProfileLoginHistory,
  getProfileSecurity,
  getProfileSessions,
  revokeAllProfileSessions,
  revokeOtherProfileSessions,
  revokeProfileSession,
  updateProfileDetail
} from '@/api/profile'
import {
  getDictOptions,
  getDictValue,
  getOperationModuleLabel,
  getOperationTypeLabel,
  getRoleLabel,
  getStatusLabel,
  loadAllDicts
} from '@/composables/useDict'
import type {
  Profile,
  ProfileLoginHistory,
  ProfileSecurity,
  ProfileSession
} from '@/types/profile'
import type { OperationLog } from '@/api/logs'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

type TabKey = 'basic' | 'security' | 'logs' | 'loginHistory' | 'sessions'

const activeTab = ref<TabKey>('basic')
const loading = ref(false)
const profile = ref<Profile | null>(null)
const security = ref<ProfileSecurity | null>(null)
const sessions = ref<ProfileSession[]>([])
const operationLogs = ref<OperationLog[]>([])
const loginHistory = ref<ProfileLoginHistory[]>([])

const profileForm = ref({ nickname: '', email: '', phone: '' })
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const logPage = ref({ page: 0, size: 10, total: 0 })
const loginPage = ref({ page: 0, size: 10, total: 0 })
const logFilters = ref({ operationType: '', operationModule: '', keyword: '' })
const loginFilters = ref({ result: '' })

const tabs: { key: TabKey, label: string }[] = [
  { key: 'basic', label: '基本资料' },
  { key: 'security', label: '账号安全' },
  { key: 'logs', label: '操作记录' },
  { key: 'loginHistory', label: '登录历史' },
  { key: 'sessions', label: '会话管理' }
]

const operationTypeOptions = computed(() => getDictOptions('operation_type'))
const operationModuleOptions = computed(() => getDictOptions('operation_module'))

const avatarText = computed(() => (profile.value?.nickname || profile.value?.username || 'U').charAt(0))
const displayName = computed(() => profile.value?.nickname || userStore.user?.nickname || '-')
const username = computed(() => profile.value?.username || userStore.user?.username || '-')
const roleLabel = computed(() => {
  const role = profile.value?.role || userStore.user?.role
  return role ? getRoleLabel(role) : '-'
})
const statusLabel = computed(() => {
  const status = profile.value?.status || userStore.user?.status
  return status ? getStatusLabel(status) : '-'
})
const isAccountActive = computed(() => {
  const status = profile.value?.status || userStore.user?.status
  return status === 'ACTIVE' && !security.value?.locked && !profile.value?.locked
})
const activeSessionCount = computed(() => sessions.value.filter(item => !item.revoked).length)
const recentSessions = computed(() => sessions.value.slice().sort((a, b) => {
  return timeValue(b.lastUsedTime || b.createdTime) - timeValue(a.lastUsedTime || a.createdTime)
}).slice(0, 3))
const recentOperations = computed(() => operationLogs.value.slice(0, 3))
const failedLoginCount = computed(() => loginHistory.value.filter(item => item.result === 'FAILED').length)
const monthlyLoginCount = computed(() => {
  const now = new Date()
  return loginHistory.value.filter(item => {
    if (item.result !== 'SUCCESS' || !item.loginTime) return false
    const date = new Date(item.loginTime.replace(' ', 'T'))
    return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
  }).length
})
const topDeviceRatio = computed(() => {
  const names = loginHistory.value
    .filter(item => item.result === 'SUCCESS')
    .map(item => resolveDeviceName(item.userAgent))
    .filter(Boolean)
  if (names.length === 0) return null
  const counts = names.reduce<Record<string, number>>((acc, name) => {
    acc[name] = (acc[name] || 0) + 1
    return acc
  }, {})
  const max = Math.max(...Object.values(counts))
  return Math.round((max / names.length) * 100)
})
const securityScore = computed(() => {
  let score = 100
  if (!profile.value?.email) score -= 10
  if (!security.value?.passwordUpdatedTime) score -= 20
  if (security.value?.locked || profile.value?.locked) score -= 40
  score -= Math.min(failedLoginCount.value * 5, 25)
  return Math.max(0, score)
})
const passwordStrength = computed(() => {
  const password = passwordForm.value.newPassword
  let score = 0
  if (password.length >= 8) score += 1
  if (/[A-Za-z]/.test(password)) score += 1
  if (/\d/.test(password)) score += 1
  if (/[^A-Za-z0-9]/.test(password)) score += 1
  if (!password) return { text: '', className: '' }
  if (score <= 2) return { text: '弱', className: 'weak' }
  if (score === 3) return { text: '中', className: 'medium' }
  return { text: '强', className: 'strong' }
})
const timeValue = (value?: string) => {
  if (!value) return 0
  const date = new Date(value.replace(' ', 'T'))
  return Number.isNaN(date.getTime()) ? 0 : date.getTime()
}

const formatTime = (value?: string) => value ? value.replace('T', ' ') : '-'
const maskUserAgent = (value?: string) => {
  if (!value) return '-'
  return value.length > 86 ? `${value.slice(0, 86)}...` : value
}
const resolveDeviceName = (userAgent?: string) => {
  if (!userAgent) return ''
  const browser = userAgent.includes('Edg') ? 'Edge'
    : userAgent.includes('Chrome') ? 'Chrome'
      : userAgent.includes('Safari') ? 'Safari'
        : userAgent.includes('Firefox') ? 'Firefox'
          : '浏览器'
  const os = userAgent.includes('Windows') ? 'Windows'
    : userAgent.includes('iPhone') ? 'iPhone'
      : userAgent.includes('Mac') ? 'macOS'
        : userAgent.includes('Android') ? 'Android'
          : '设备'
  return `${os} · ${browser}`
}

const loadProfile = async () => {
  const response = await getProfileDetail()
  profile.value = response.data
  profileForm.value = {
    nickname: response.data.nickname || '',
    email: response.data.email || '',
    phone: response.data.phone || ''
  }
}
const loadSecurity = async () => {
  const response = await getProfileSecurity()
  security.value = response.data
}
const loadOperationLogs = async () => {
  const response = await getMyOperationLogs({
    page: logPage.value.page,
    size: logPage.value.size,
    operationType: logFilters.value.operationType || undefined,
    operationModule: logFilters.value.operationModule || undefined,
    keyword: logFilters.value.keyword || undefined
  })
  operationLogs.value = response.data.content || []
  logPage.value.total = response.data.totalElements || 0
}
const loadLoginHistory = async () => {
  const response = await getProfileLoginHistory({
    page: loginPage.value.page,
    size: loginPage.value.size,
    result: loginFilters.value.result || undefined
  })
  loginHistory.value = response.data.content || []
  loginPage.value.total = response.data.totalElements || 0
}
const loadSessions = async () => {
  const response = await getProfileSessions()
  sessions.value = response.data || []
}
const refreshAll = async () => {
  loading.value = true
  try {
    await Promise.all([
      loadProfile(),
      loadSecurity(),
      loadOperationLogs(),
      loadLoginHistory(),
      loadSessions()
    ])
  } finally {
    loading.value = false
  }
}

const switchTab = async (tab: TabKey) => {
  activeTab.value = tab
  if (tab === 'logs') await loadOperationLogs()
  if (tab === 'loginHistory') await loadLoginHistory()
  if (tab === 'sessions') await loadSessions()
}
const resolveRouteTab = (): TabKey | null => {
  const value = route.query.tab
  if (typeof value !== 'string') return null
  return tabs.some(tab => tab.key === value) ? value as TabKey : null
}
const syncRouteTab = async () => {
  const tab = resolveRouteTab()
  if (tab && tab !== activeTab.value) {
    await switchTab(tab)
  }
}
const goDashboard = async () => {
  await router.push('/home')
}

const saveProfile = async () => {
  if (!profileForm.value.nickname.trim()) {
    showToast('昵称不能为空')
    return
  }
  await updateProfileDetail({
    nickname: profileForm.value.nickname.trim(),
    email: profileForm.value.email || undefined,
    phone: profileForm.value.phone || undefined
  })
  await userStore.fetchProfile()
  await loadProfile()
  showToast('个人资料已更新')
}
const changePassword = async () => {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword || !passwordForm.value.confirmPassword) {
    showToast('请完整填写密码信息')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    showToast('两次输入的新密码不一致')
    return
  }
  if (passwordForm.value.newPassword.length < 8) {
    showToast('新密码至少8位')
    return
  }
  await showDialog({
    title: '修改密码',
    message: '修改成功后将退出所有设备，需要重新登录。',
    showCancelButton: true
  })
  await changeProfilePassword(passwordForm.value)
  showToast('密码已修改，请重新登录')
  await userStore.logoutAction(false)
  router.push('/login')
}
const revokeSession = async (session: ProfileSession) => {
  if (session.current) {
    showToast('不能在此处撤销当前会话')
    return
  }
  await showDialog({
    title: '撤销会话',
    message: `确定撤销 ${session.deviceName || '该设备'} 的登录会话吗？`,
    showCancelButton: true
  })
  await revokeProfileSession(session.id)
  await loadSessions()
  showToast('会话已撤销')
}
const revokeOthers = async () => {
  await showDialog({
    title: '退出其他设备',
    message: '将撤销除当前会话外的其他设备登录状态。',
    showCancelButton: true
  })
  await revokeOtherProfileSessions()
  await loadSessions()
  showToast('其他设备已退出')
}
const revokeAll = async () => {
  await showDialog({
    title: '退出全部设备',
    message: '将退出所有设备并返回登录页。',
    showCancelButton: true
  })
  await revokeAllProfileSessions()
  await userStore.logoutAction(false)
  router.push('/login')
}
const previousLogPage = async () => {
  if (logPage.value.page === 0) return
  logPage.value.page -= 1
  await loadOperationLogs()
}
const nextLogPage = async () => {
  if ((logPage.value.page + 1) * logPage.value.size >= logPage.value.total) return
  logPage.value.page += 1
  await loadOperationLogs()
}
const previousLoginPage = async () => {
  if (loginPage.value.page === 0) return
  loginPage.value.page -= 1
  await loadLoginHistory()
}
const nextLoginPage = async () => {
  if ((loginPage.value.page + 1) * loginPage.value.size >= loginPage.value.total) return
  loginPage.value.page += 1
  await loadLoginHistory()
}

onMounted(async () => {
  await loadAllDicts()
  await refreshAll()
  await syncRouteTab()
})

watch(() => route.query.tab, () => {
  syncRouteTab()
})
</script>

<template>
  <div class="profile-page" :class="{ loading }">
    <header class="profile-header profile-card">
      <div>
        <h1>用户中心</h1>
        <p>{{ displayName }}，您好！管理你的账户信息与安全设置。</p>
      </div>
      <div class="header-actions">
        <button class="icon-action" :disabled="loading" title="刷新" type="button" @click="refreshAll">
          <span class="refresh-icon" :class="{ spinning: loading }">↻</span>
        </button>
        <button class="secondary-action" type="button" @click="goDashboard">返回工作台</button>
      </div>
    </header>

    <section class="profile-primary-grid">
      <aside class="profile-summary-card profile-card">
        <div class="avatar">{{ avatarText }}</div>
        <h2>{{ displayName }}</h2>
        <p class="username">{{ username }}</p>
        <div class="role-badge">{{ roleLabel }}</div>

        <div class="summary-list">
          <div class="summary-row">
            <span>状态</span>
            <strong><i class="status-dot" :class="{ active: isAccountActive }"></i>{{ statusLabel }}</strong>
          </div>
          <div class="summary-row"><span>部门</span><strong>{{ profile?.department || '-' }}</strong></div>
          <div class="summary-row"><span>员工编号</span><strong>{{ profile?.employeeId || '-' }}</strong></div>
          <div class="summary-row"><span>最近登录</span><strong>{{ formatTime(profile?.lastLoginTime) }}</strong></div>
        </div>

        <div class="summary-metrics">
          <button class="metric security" type="button" @click="switchTab('security')">
            <strong>{{ securityScore }}</strong>
            <span>安全评分</span>
          </button>
          <button class="metric device" type="button" @click="switchTab('sessions')">
            <strong>{{ activeSessionCount }}</strong>
            <span>登录设备</span>
          </button>
          <button class="metric operation" type="button" @click="switchTab('logs')">
            <strong>{{ logPage.total }}</strong>
            <span>操作记录</span>
          </button>
        </div>
      </aside>

      <main class="profile-tab-card profile-card">
        <nav class="profile-tabs" aria-label="个人中心分区">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            :class="{ active: activeTab === tab.key }"
            type="button"
            @click="switchTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </nav>

        <section v-if="activeTab === 'basic'" class="tab-panel">
          <div class="panel-heading">
            <h2>基本资料</h2>
            <p>用户名、员工编号、角色和部门由管理员维护，个人中心仅开放展示信息维护。</p>
          </div>
          <div class="form-grid">
            <label><span>用户名</span><input :value="profile?.username || ''" disabled /></label>
            <label><span>邮箱</span><input v-model="profileForm.email" type="email" /></label>
            <label><span>昵称</span><input v-model="profileForm.nickname" /></label>
            <label><span>部门</span><input :value="profile?.department || ''" disabled /></label>
            <label><span>员工编号</span><input :value="profile?.employeeId || ''" disabled /></label>
            <label><span>手机号</span><input v-model="profileForm.phone" /></label>
          </div>
          <div class="panel-actions">
            <button class="primary-action" type="button" @click="saveProfile">保存资料</button>
            <button class="secondary-action" type="button" @click="loadProfile">重置</button>
          </div>
        </section>

        <section v-if="activeTab === 'security'" class="tab-panel">
          <div class="security-layout">
            <div class="security-status-card">
              <h3>账号状态</h3>
              <p>安全评分已在下方账号安全概览中展示，这里聚焦具体账号状态。</p>
              <div class="info-list">
                <div><span>最近登录时间</span><strong>{{ formatTime(security?.lastLoginTime) }}</strong></div>
                <div><span>最近登录 IP</span><strong>{{ security?.lastLoginIp || '-' }}</strong></div>
                <div><span>登录次数</span><strong>{{ security?.loginCount ?? 0 }}</strong></div>
                <div><span>密码更新时间</span><strong>{{ formatTime(security?.passwordUpdatedTime) }}</strong></div>
                <div><span>登录方式</span><strong>{{ security?.loginType || '-' }}</strong></div>
                <div><span>锁定状态</span><strong>{{ security?.locked ? '已锁定' : '正常' }}</strong></div>
              </div>
            </div>
            <div class="password-box">
              <h3>修改密码</h3>
              <p>新密码需满足系统密码策略。提交成功后将清理所有设备登录状态。</p>
              <div class="password-form">
                <label><span>当前密码</span><input v-model="passwordForm.oldPassword" type="password" /></label>
                <label>
                  <span>新密码</span>
                  <input v-model="passwordForm.newPassword" type="password" />
                  <small v-if="passwordStrength.text" :class="passwordStrength.className">强度：{{ passwordStrength.text }}</small>
                </label>
                <label><span>确认新密码</span><input v-model="passwordForm.confirmPassword" type="password" /></label>
              </div>
              <button class="danger-action" type="button" @click="changePassword">修改密码</button>
            </div>
          </div>
        </section>

        <section v-if="activeTab === 'logs'" class="tab-panel">
          <div class="panel-title-row">
            <div class="panel-heading">
              <h2>我的操作记录</h2>
              <p>只展示当前账号自己的操作审计。</p>
            </div>
            <div class="page-actions">
              <button :disabled="logPage.page === 0" type="button" @click="previousLogPage">上一页</button>
              <button :disabled="(logPage.page + 1) * logPage.size >= logPage.total" type="button" @click="nextLogPage">下一页</button>
            </div>
          </div>
          <div class="filters">
            <input v-model="logFilters.keyword" placeholder="关键字" />
            <select v-model="logFilters.operationType">
              <option value="">全部类型</option>
              <option v-for="option in operationTypeOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <select v-model="logFilters.operationModule">
              <option value="">全部模块</option>
              <option v-for="option in operationModuleOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <button type="button" @click="loadOperationLogs">查询</button>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>时间</th><th>模块</th><th>类型</th><th>描述</th><th>IP</th><th>状态</th></tr></thead>
              <tbody>
                <tr v-for="log in operationLogs" :key="log.id">
                  <td>{{ formatTime((log as any).operationTime || log.createdTime) }}</td>
                  <td>{{ getOperationModuleLabel(log.operationModule || '') }}</td>
                  <td>{{ getOperationTypeLabel(log.operationType || '') }}</td>
                  <td>{{ log.operationDesc || '-' }}</td>
                  <td>{{ log.ipAddress || '-' }}</td>
                  <td>{{ log.status || '-' }}</td>
                </tr>
                <tr v-if="operationLogs.length === 0"><td colspan="6" class="empty">暂无记录</td></tr>
              </tbody>
            </table>
          </div>
          <p class="page-summary">第 {{ logPage.page + 1 }} 页，共 {{ logPage.total }} 条</p>
        </section>

        <section v-if="activeTab === 'loginHistory'" class="tab-panel">
          <div class="panel-title-row">
            <div class="panel-heading">
              <h2>登录历史</h2>
              <p>查看最近登录成功与失败记录，帮助识别异常登录。</p>
            </div>
            <div class="page-actions">
              <button :disabled="loginPage.page === 0" type="button" @click="previousLoginPage">上一页</button>
              <button :disabled="(loginPage.page + 1) * loginPage.size >= loginPage.total" type="button" @click="nextLoginPage">下一页</button>
            </div>
          </div>
          <div class="filters">
            <select v-model="loginFilters.result">
              <option value="">全部结果</option>
              <option value="SUCCESS">{{ getDictValue('login_result', 'SUCCESS') }}</option>
              <option value="FAILED">{{ getDictValue('login_result', 'FAILED') }}</option>
            </select>
            <button type="button" @click="loadLoginHistory">查询</button>
          </div>
          <div class="table-wrap">
            <table>
              <thead><tr><th>时间</th><th>结果</th><th>IP</th><th>设备</th><th>失败原因</th></tr></thead>
              <tbody>
                <tr v-for="item in loginHistory" :key="item.id">
                  <td>{{ formatTime(item.loginTime) }}</td>
                  <td><span class="result-pill" :class="item.result.toLowerCase()">{{ getDictValue('login_result', item.result) }}</span></td>
                  <td>{{ item.ipAddress || '-' }}</td>
                  <td>{{ resolveDeviceName(item.userAgent) || maskUserAgent(item.userAgent) }}</td>
                  <td>{{ item.failureReason || '-' }}</td>
                </tr>
                <tr v-if="loginHistory.length === 0"><td colspan="5" class="empty">暂无记录</td></tr>
              </tbody>
            </table>
          </div>
          <p class="page-summary">第 {{ loginPage.page + 1 }} 页，共 {{ loginPage.total }} 条</p>
        </section>

        <section v-if="activeTab === 'sessions'" class="tab-panel">
          <div class="panel-title-row">
            <div class="panel-heading">
              <h2>会话管理</h2>
              <p>查看当前有效设备，撤销不再使用的登录会话。</p>
            </div>
            <div class="page-actions">
              <button type="button" @click="revokeOthers">退出其他设备</button>
              <button class="danger-outline" type="button" @click="revokeAll">退出全部设备</button>
            </div>
          </div>
          <div class="session-list">
            <article v-for="session in sessions" :key="session.id" class="session-item">
              <div class="device-icon">▣</div>
              <div>
                <h3>{{ session.deviceName || resolveDeviceName(session.userAgent) || '未知设备' }} <span v-if="session.current">当前设备</span></h3>
                <p>{{ session.ipAddress || '-' }} · 最近使用 {{ formatTime(session.lastUsedTime) }}</p>
                <p>{{ maskUserAgent(session.userAgent) }}</p>
              </div>
              <button :disabled="session.current" type="button" @click="revokeSession(session)">撤销</button>
            </article>
            <div v-if="sessions.length === 0" class="empty">暂无有效会话</div>
          </div>
        </section>

      </main>
    </section>

    <section class="profile-overview-grid">
      <article class="profile-card overview-card clickable" @click="switchTab('security')">
        <div class="overview-title"><span class="overview-icon security">◇</span><h3>账号安全概览</h3></div>
        <div class="strength-row">
          <div>
            <span>安全评分</span>
            <strong>{{ securityScore }}</strong>
          </div>
          <div class="strength-bars">
            <i v-for="index in 4" :key="index" :class="{ active: securityScore >= index * 25 }"></i>
          </div>
        </div>
        <div class="security-lines">
          <div><span>登录密码</span><strong>已设置</strong></div>
          <div><span>安全邮箱</span><strong>{{ profile?.email ? '已绑定' : '未绑定' }}</strong></div>
          <div><span>账号状态</span><strong>{{ security?.locked ? '已锁定' : '正常' }}</strong></div>
        </div>
      </article>

      <article class="profile-card overview-card">
        <div class="overview-title">
          <span class="overview-icon operation">○</span>
          <h3>最近操作</h3>
          <button type="button" @click="switchTab('logs')">查看全部</button>
        </div>
        <ul class="recent-list">
          <li v-for="log in recentOperations" :key="log.id">
            <i></i>
            <div>
              <strong>{{ log.operationDesc || getOperationTypeLabel(log.operationType || '') }}</strong>
              <span>{{ getOperationModuleLabel(log.operationModule || '') }}</span>
            </div>
            <time>{{ formatTime((log as any).operationTime || log.createdTime) }}</time>
          </li>
          <li v-if="recentOperations.length === 0" class="empty-line">暂无操作记录</li>
        </ul>
      </article>

      <article class="profile-card overview-card">
        <div class="overview-title">
          <span class="overview-icon device">▱</span>
          <h3>登录设备 / 会话</h3>
          <button type="button" @click="switchTab('sessions')">管理设备</button>
        </div>
        <div class="session-stats">
          <div><strong>{{ activeSessionCount }}</strong><span>当前在线</span></div>
          <div><strong>{{ monthlyLoginCount }}</strong><span>近期登录</span></div>
          <div><strong>{{ topDeviceRatio === null ? '-' : `${topDeviceRatio}%` }}</strong><span>常用设备</span></div>
        </div>
        <ul class="device-list">
          <li v-for="session in recentSessions" :key="session.id">
            <span class="device-mini">▣</span>
            <div>
              <strong>{{ session.deviceName || resolveDeviceName(session.userAgent) || '未知设备' }}</strong>
              <span>{{ session.ipAddress || '-' }} · {{ formatTime(session.lastUsedTime || session.createdTime) }}</span>
            </div>
            <em v-if="session.current">当前设备</em>
          </li>
          <li v-if="recentSessions.length === 0" class="empty-line">暂无有效会话</li>
        </ul>
      </article>
    </section>
  </div>
</template>

<style scoped>
.profile-page {
  --profile-bg: #F7F9FB;
  --profile-card: #FFFFFF;
  --profile-primary: #0D6E6E;
  --profile-primary-dark: #075E5E;
  --profile-success: #10B981;
  --profile-info: #2563EB;
  --profile-warning: #F97316;
  --profile-danger: #DC2626;
  --profile-text: #0F172A;
  --profile-muted: #64748B;
  --profile-border: #E2E8F0;
  min-height: calc(100vh - 72px);
  padding: 24px;
  background: var(--profile-bg);
  color: var(--profile-text);
}

.profile-card {
  background: var(--profile-card);
  border: 1px solid var(--profile-border);
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.profile-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  margin-bottom: 16px;
}

.profile-header h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.2;
  font-weight: 800;
  letter-spacing: 0;
}

.profile-header p,
.panel-heading p,
.username,
.page-summary {
  margin: 6px 0 0;
  color: var(--profile-muted);
}

.header-actions,
.panel-actions,
.page-actions,
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.profile-primary-grid {
  display: grid;
  grid-template-columns: minmax(280px, 320px) minmax(0, 1fr);
  gap: 16px;
}

.profile-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr) minmax(0, 1.05fr);
  gap: 16px;
  margin-top: 16px;
}

.profile-summary-card {
  padding: 24px 18px 18px;
  text-align: center;
}

.avatar {
  width: 84px;
  height: 84px;
  margin: 0 auto 14px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, var(--profile-primary) 0%, var(--profile-primary-dark) 100%);
  color: #fff;
  font-size: 42px;
  font-weight: 800;
}

.profile-summary-card h2 {
  margin: 0;
  font-size: 20px;
}

.role-badge {
  display: inline-flex;
  align-items: center;
  margin-top: 12px;
  padding: 4px 10px;
  border: 1px solid rgba(13, 110, 110, 0.22);
  border-radius: 999px;
  background: rgba(13, 110, 110, 0.08);
  color: var(--profile-primary);
  font-size: 13px;
  font-weight: 700;
}

.summary-list {
  margin-top: 18px;
  padding-top: 8px;
  border-top: 1px solid var(--profile-border);
}

.summary-row,
.security-lines div,
.info-grid div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  color: var(--profile-muted);
}

.summary-row strong,
.security-lines strong,
.info-grid strong {
  color: var(--profile-text);
  font-weight: 700;
  text-align: right;
}

.status-dot {
  width: 8px;
  height: 8px;
  display: inline-block;
  margin-right: 6px;
  border-radius: 999px;
  background: var(--profile-warning);
}

.status-dot.active {
  background: var(--profile-success);
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.metric {
  min-width: 0;
  padding: 10px 6px;
  border-radius: 8px;
  border: 1px solid var(--profile-border);
  background: #FAFBFC;
  cursor: pointer;
}

.metric strong {
  display: block;
  font-size: 18px;
}

.metric span {
  display: block;
  margin-top: 4px;
  color: var(--profile-muted);
  font-size: 12px;
}

.metric.security strong { color: var(--profile-primary); }
.metric.device strong { color: var(--profile-info); }
.metric.operation strong { color: var(--profile-warning); }

.profile-tab-card {
  min-width: 0;
  overflow: hidden;
}

.profile-tabs {
  display: flex;
  gap: 24px;
  overflow-x: auto;
  padding: 0 16px;
  border-bottom: 1px solid var(--profile-border);
}

.profile-tabs button {
  position: relative;
  min-height: 56px;
  padding: 0 2px;
  border: none;
  background: transparent;
  color: var(--profile-text);
  font-weight: 700;
  white-space: nowrap;
}

.profile-tabs button.active {
  color: var(--profile-primary);
}

.profile-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 3px;
  border-radius: 999px 999px 0 0;
  background: var(--profile-primary);
  content: '';
}

.tab-panel {
  padding: 20px 22px 22px;
}

.panel-heading {
  margin-bottom: 16px;
}

.panel-heading h2,
.overview-card h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.25;
}

.form-grid,
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 20px;
  margin-bottom: 18px;
}

label {
  display: grid;
  gap: 7px;
  color: var(--profile-text);
  font-weight: 600;
}

label span,
.info-grid span,
.info-list span {
  color: var(--profile-text);
  font-size: 14px;
}

input,
select {
  width: 100%;
  min-height: 40px;
  border: 1px solid #CBD5E1;
  border-radius: 7px;
  padding: 0 11px;
  background: #fff;
  color: var(--profile-text);
  outline: none;
}

input:focus,
select:focus {
  border-color: var(--profile-primary);
}

input:disabled {
  color: var(--profile-muted);
  background: #F8FAFC;
}

button {
  min-height: 38px;
  border: 1px solid var(--profile-border);
  border-radius: 7px;
  padding: 0 14px;
  background: #fff;
  color: var(--profile-text);
  cursor: pointer;
  font-weight: 700;
  transition: transform 0.16s ease, border-color 0.16s ease, background 0.16s ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
  border-color: var(--profile-primary);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.primary-action {
  border-color: var(--profile-primary);
  background: var(--profile-primary);
  color: #fff;
}

.primary-action:hover:not(:disabled) {
  background: var(--profile-primary-dark);
}

.secondary-action,
.icon-action {
  background: #fff;
}

.danger-action {
  border-color: var(--profile-danger);
  background: var(--profile-danger);
  color: #fff;
}

.danger-outline {
  border-color: rgba(220, 38, 38, 0.35);
  color: var(--profile-danger);
}

.icon-action {
  width: 40px;
  padding: 0;
  font-size: 22px;
}

.refresh-icon {
  display: inline-block;
}

.refresh-icon.spinning {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

small.weak { color: var(--profile-danger); }
small.medium { color: var(--profile-warning); }
small.strong { color: var(--profile-success); }

.security-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 0.86fr);
  gap: 18px;
  align-items: stretch;
}

.security-status-card,
.password-box {
  min-width: 0;
  padding: 16px;
  border: 1px solid var(--profile-border);
  border-radius: 8px;
  background: #FAFBFC;
}

.security-status-card h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.security-status-card p,
.password-box p {
  color: var(--profile-muted);
}

.security-status-card p {
  margin: 0 0 12px;
  line-height: 1.55;
}

.info-list {
  margin-top: 4px;
  border-top: 1px solid var(--profile-border);
}

.info-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid var(--profile-border);
}

.info-list div:last-child {
  border-bottom: none;
}

.info-list strong {
  color: var(--profile-text);
  text-align: right;
  word-break: break-word;
}

.password-box h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.password-box p {
  margin: 0 0 14px;
  line-height: 1.55;
}

.password-form {
  display: grid;
  gap: 14px;
  margin-bottom: 16px;
}

.panel-title-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.filters {
  margin-bottom: 14px;
}

.filters input,
.filters select {
  max-width: 220px;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--profile-border);
  border-radius: 8px;
}

table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
}

th,
td {
  padding: 11px 12px;
  border-bottom: 1px solid var(--profile-border);
  text-align: left;
  vertical-align: top;
}

th {
  background: #F8FAFC;
  color: #334155;
  font-weight: 800;
}

tr:last-child td {
  border-bottom: none;
}

.empty {
  padding: 28px;
  color: var(--profile-muted);
  text-align: center;
}

.result-pill {
  display: inline-flex;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.result-pill.success {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
}

.result-pill.failed {
  background: rgba(220, 38, 38, 0.10);
  color: var(--profile-danger);
}

.session-list {
  display: grid;
  gap: 10px;
}

.session-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 14px;
  border: 1px solid var(--profile-border);
  border-radius: 8px;
}

.device-icon,
.device-mini {
  display: grid;
  place-items: center;
  color: var(--profile-info);
}

.device-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: rgba(37, 99, 235, 0.08);
}

.session-item h3 {
  margin: 0;
  font-size: 15px;
}

.session-item h3 span,
.device-list em {
  margin-left: 8px;
  padding: 2px 7px;
  border-radius: 999px;
  color: #047857;
  background: rgba(16, 185, 129, 0.12);
  font-size: 12px;
  font-style: normal;
}

.session-item p {
  margin: 5px 0 0;
  color: var(--profile-muted);
  word-break: break-word;
}

.overview-card {
  padding: 16px;
  min-width: 0;
}

.overview-card.clickable {
  cursor: pointer;
}

.overview-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--profile-border);
}

.overview-title h3 {
  flex: 1;
}

.overview-title button {
  min-height: 30px;
  padding: 0 8px;
  border: none;
  color: var(--profile-primary);
  background: transparent;
}

.overview-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  font-weight: 800;
}

.overview-icon.security {
  color: var(--profile-primary);
  background: rgba(13, 110, 110, 0.09);
}

.overview-icon.operation {
  color: var(--profile-warning);
  background: rgba(249, 115, 22, 0.10);
}

.overview-icon.device {
  color: var(--profile-info);
  background: rgba(37, 99, 235, 0.10);
}

.strength-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 0 10px;
}

.strength-row span {
  color: var(--profile-muted);
}

.strength-row strong {
  display: block;
  margin-top: 4px;
  color: var(--profile-primary);
  font-size: 26px;
}

.strength-bars {
  display: flex;
  align-items: end;
  gap: 6px;
}

.strength-bars i {
  width: 28px;
  height: 6px;
  border-radius: 999px;
  background: #E2E8F0;
}

.strength-bars i.active {
  background: var(--profile-success);
}

.security-lines {
  border: 1px solid var(--profile-border);
  border-radius: 8px;
  overflow: hidden;
}

.security-lines div {
  padding: 12px;
}

.security-lines div + div {
  border-top: 1px solid var(--profile-border);
}

.recent-list,
.device-list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}

.recent-list li,
.device-list li {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 11px 0;
}

.recent-list li + li,
.device-list li + li {
  border-top: 1px solid var(--profile-border);
}

.recent-list i {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--profile-text);
}

.recent-list strong,
.device-list strong {
  display: block;
  overflow: hidden;
  color: var(--profile-text);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-list span,
.device-list span,
.recent-list time {
  color: var(--profile-muted);
  font-size: 13px;
}

.session-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 0;
}

.session-stats div {
  text-align: center;
}

.session-stats strong {
  display: block;
  color: var(--profile-info);
  font-size: 20px;
}

.session-stats div:nth-child(3) strong {
  color: var(--profile-warning);
}

.session-stats span {
  color: var(--profile-muted);
  font-size: 12px;
}

.empty-line {
  display: block !important;
  color: var(--profile-muted);
  text-align: center;
}

@media (max-width: 1024px) {
  .profile-primary-grid,
  .profile-overview-grid {
    grid-template-columns: 1fr;
  }

  .profile-summary-card {
    text-align: left;
  }

  .avatar {
    margin-left: 0;
  }
}

@media (max-width: 768px) {
  .profile-page {
    padding: 12px;
  }

  .profile-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .profile-header h1 {
    font-size: 24px;
  }

  .profile-tabs {
    gap: 18px;
  }

  .form-grid,
  .info-grid,
  .security-layout {
    grid-template-columns: 1fr;
  }

  .panel-title-row,
  .session-item {
    grid-template-columns: 1fr;
  }

  .filters input,
  .filters select {
    max-width: none;
  }

  .summary-metrics,
  .session-stats {
    grid-template-columns: 1fr;
  }
}
</style>
