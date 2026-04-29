<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { showToast } from 'vant'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getLogs, getLogStatistics, getMonthlyReport, getYearlyReport, type OperationLog, type LogStatistics, type MonthlyReport, type YearlyReport } from '@/api/logs'
import { getDictOptions, getDictValue, getOperationTypeLabel, loadAllDicts } from '@/composables/useDict'

use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, CanvasRenderer])

const loading = ref(false)
const logList = ref<OperationLog[]>([])
const statistics = ref<LogStatistics | null>(null)
const pagination = ref({
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0
})

// 当前标签页
const activeTab = ref('logs')

// 报表相关
const currentYear = new Date().getFullYear()
const currentMonth = new Date().getMonth() + 1
const reportYear = ref(currentYear)
const reportMonth = ref(currentMonth)
const monthlyReport = ref<MonthlyReport | null>(null)
const yearlyReport = ref<YearlyReport | null>(null)

// 快捷日期选择
const quickDateRange = ref('30') // 默认最近30天
const dateRangeOptions = [
  { text: '最近7天', value: '7' },
  { text: '最近30天', value: '30' },
  { text: '最近90天', value: '90' },
  { text: '自定义', value: 'custom' }
]

// 自定义日期范围
const customStartDate = ref('')
const customEndDate = ref('')

// 筛选条件
const filters = ref({
  username: '',
  operationType: '',
  operationModule: ''
})

// 操作类型选项（从字典获取）
const operationTypes = computed(() => {
  const opts = getDictOptions('operation_type')
  return [{ text: '全部', value: '' }, ...opts.map(o => ({ text: o.label, value: o.value }))]
})

// 操作模块选项（从字典获取）
const operationModules = computed(() => {
  const opts = getDictOptions('operation_module')
  return [{ text: '全部', value: '' }, ...opts.map(o => ({ text: o.label, value: o.value }))]
})

// 计算日期范围
const dateRange = computed(() => {
  if (quickDateRange.value === 'custom') {
    return {
      startTime: customStartDate.value ? customStartDate.value + ' 00:00:00' : '',
      endTime: customEndDate.value ? customEndDate.value + ' 23:59:59' : ''
    }
  }
  const days = parseInt(quickDateRange.value)
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - days)
  return {
    startTime: start.toISOString().replace('T', ' ').substring(0, 19),
    endTime: end.toISOString().replace('T', ' ').substring(0, 19)
  }
})

// 加载日志列表
const loadLogs = async () => {
  loading.value = true
  try {
    const response = await getLogs({
      page: pagination.value.page,
      size: pagination.value.size,
      username: filters.value.username || undefined,
      operationType: filters.value.operationType || undefined,
      operationModule: filters.value.operationModule || undefined,
      startTime: dateRange.value.startTime || undefined,
      endTime: dateRange.value.endTime || undefined
    })

    if (response.data) {
      logList.value = response.data.content || []
      pagination.value.totalElements = response.data.totalElements || 0
      pagination.value.totalPages = response.data.totalPages || 0
    }
  } catch (error: any) {
    showToast(error.message || '加载日志失败')
  } finally {
    loading.value = false
  }
}

// 加载统计数据
const loadStatistics = async () => {
  try {
    const response = await getLogStatistics(
      dateRange.value.startTime || undefined,
      dateRange.value.endTime || undefined
    )
    if (response.data) {
      statistics.value = response.data
    }
  } catch (error: any) {
    showToast(error.message || '加载统计失败')
  }
}

// 加载月度报表
const loadMonthlyReport = async () => {
  loading.value = true
  try {
    const response = await getMonthlyReport(reportYear.value, reportMonth.value)
    if (response.data) {
      monthlyReport.value = response.data
    }
  } catch (error: any) {
    showToast(error.message || '加载月度报表失败')
  } finally {
    loading.value = false
  }
}

// 加载年度报表
const loadYearlyReport = async () => {
  loading.value = true
  try {
    const response = await getYearlyReport(reportYear.value)
    if (response.data) {
      yearlyReport.value = response.data
    }
  } catch (error: any) {
    showToast(error.message || '加载年度报表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.value.page = 0
  loadLogs()
  loadStatistics()
}

// 重置筛选
const handleReset = () => {
  filters.value = { username: '', operationType: '', operationModule: '' }
  quickDateRange.value = '30'
  pagination.value.page = 0
  loadLogs()
  loadStatistics()
}

// 分页变化
const handlePageChange = (page: number) => {
  pagination.value.page = page - 1
  loadLogs()
}

// 切换标签页
const switchTab = (tab: string) => {
  activeTab.value = tab
  if (tab === 'statistics') {
    loadStatistics()
  } else if (tab === 'monthly') {
    loadMonthlyReport()
  } else if (tab === 'yearly') {
    loadYearlyReport()
  }
}

// 趋势图配置
const trendChartOptions = computed(() => {
  if (!statistics.value?.dailyCount) return {}
  const data = statistics.value.dailyCount
  const dates = Object.keys(data)
  const values = Object.values(data)

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => `${params[0].name}: ${params[0].value} 次`
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: { fontSize: 11, color: '#666' }
    },
    yAxis: {
      type: 'value',
      name: '操作次数',
      nameTextStyle: { fontSize: 11, color: '#666' },
      axisLabel: { fontSize: 11, color: '#666' },
      splitLine: { lineStyle: { color: '#eee', type: 'dashed' } }
    },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(13, 110, 110, 0.3)' },
            { offset: 1, color: 'rgba(13, 110, 110, 0.05)' }
          ]
        }
      },
      lineStyle: { color: '#0D6E6E', width: 2 },
      itemStyle: { color: '#0D6E6E' }
    }]
  }
})

// 操作类型饼图配置
const operationTypeChartOptions = computed(() => {
  if (!statistics.value?.operationTypeCount) return {}
  const data = statistics.value.operationTypeCount
  const pieData = Object.entries(data).map(([key, value]) => ({
    name: getOperationTypeLabel(key),
    value: value
  }))

  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: '5%', left: 'center' },
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      avoidLabelOverlap: false,
      label: { show: false },
      emphasis: {
        label: { show: true, fontSize: 14, fontWeight: 'bold' }
      },
      data: pieData,
      color: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }]
  }
})

// 模块分布柱状图配置
const moduleChartOptions = computed(() => {
  if (!statistics.value?.moduleCount) return {}
  const data = statistics.value.moduleCount
  const categories = Object.keys(data)
  const values = Object.values(data)

  return {
    tooltip: { trigger: 'axis', formatter: '{b}: {c} 次' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: { type: 'category', data: categories, axisLabel: { fontSize: 11, color: '#666', rotate: 30 } },
    yAxis: {
      type: 'value',
      axisLabel: { fontSize: 11, color: '#666' },
      splitLine: { lineStyle: { color: '#eee', type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      data: values,
      itemStyle: { color: '#0D6E6E', borderRadius: [4, 4, 0, 0] },
      barWidth: '50%'
    }]
  }
})

// 用户活跃排行柱状图配置
const userActivityChartOptions = computed(() => {
  if (!statistics.value?.userActivities) return {}
  const activities = statistics.value.userActivities.slice(0, 10)
  const users = activities.map(a => a.username)
  const values = activities.map(a => a.operationCount)

  return {
    tooltip: { trigger: 'axis', formatter: '{b}: {c} 次' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: users, axisLabel: { fontSize: 11, color: '#666' } },
    series: [{
      type: 'bar',
      data: values,
      itemStyle: { color: '#0D6E6E', borderRadius: [0, 4, 4, 0] },
      barWidth: '60%'
    }]
  }
})

// 年度报表趋势图
const yearlyTrendChartOptions = computed(() => {
  if (!yearlyReport.value?.monthlyCount) return {}
  const data = yearlyReport.value.monthlyCount
  const months = Object.keys(data)
  const values = Object.values(data)

  return {
    tooltip: { trigger: 'axis', formatter: '{b}: {c} 次' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11, color: '#666' } },
    yAxis: {
      type: 'value',
      name: '操作次数',
      nameTextStyle: { fontSize: 11, color: '#666' },
      axisLabel: { fontSize: 11, color: '#666' },
      splitLine: { lineStyle: { color: '#eee', type: 'dashed' } }
    },
    series: [{
      type: 'bar',
      data: values,
      itemStyle: { color: '#0D6E6E', borderRadius: [4, 4, 0, 0] },
      barWidth: '60%'
    }]
  }
})

// 获取状态样式
const getStatusClass = (status: string) => status === 'SUCCESS' ? 'success' : 'error' // SUCCESS/FAIL 是日志特有状态，不在通用字典中

// 获取状态名称
const getStatusName = (status: string) => getDictValue('common_status', status) || (status === 'SUCCESS' ? '成功' : '失败')

// 格式化时间
const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 获取操作类型标签样式
const getOperationTypeClass = (type: string) => {
  const map: Record<string, string> = {
    LOGIN: 'login', LOGOUT: 'logout', CREATE: 'create', UPDATE: 'update',
    DELETE: 'delete', EXPORT: 'export', IMPORT: 'import', QUERY: 'query'
  }
  return map[type] || 'default'
}

// 获取操作类型中文名
const getOperationTypeName = (type: string) => getOperationTypeLabel(type)

onMounted(() => {
  loadAllDicts()
  loadLogs()
  loadStatistics()
})
</script>

<template>
  <div class="operation-log-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">日志管理</h1>
        <p class="page-subtitle">查看系统用户操作日志和统计分析</p>
      </div>
    </div>

    <!-- 标签页切换 -->
    <div class="tab-section">
      <div class="tab-buttons">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'logs' }"
          @click="switchTab('logs')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          日志列表
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'statistics' }"
          @click="switchTab('statistics')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="20" x2="18" y2="10"/>
            <line x1="12" y1="20" x2="12" y2="4"/>
            <line x1="6" y1="20" x2="6" y2="14"/>
          </svg>
          统计分析
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'monthly' }"
          @click="switchTab('monthly')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="3" y="4" width="18" height="18" rx="2" ry="2"/>
            <line x1="16" y1="2" x2="16" y2="6"/>
            <line x1="8" y1="2" x2="8" y2="6"/>
            <line x1="3" y1="10" x2="21" y2="10"/>
          </svg>
          月度报表
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'yearly' }"
          @click="switchTab('yearly')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>
          </svg>
          年度报表
        </button>
      </div>
    </div>

    <!-- ==================== 日志列表 ==================== -->
    <div v-if="activeTab === 'logs'" class="tab-content">
      <!-- 筛选区域 -->
      <div class="filter-section">
        <div class="filter-row">
          <div class="filter-item">
            <input
              v-model="filters.username"
              type="text"
              placeholder="用户名"
              class="input"
              @keyup.enter="handleSearch"
            />
          </div>
          <div class="filter-item">
            <select v-model="filters.operationType" class="input">
              <option v-for="opt in operationTypes" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
          <div class="filter-item">
            <select v-model="filters.operationModule" class="input">
              <option v-for="opt in operationModules" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
        </div>
        <div class="filter-row">
          <div class="filter-item">
            <select v-model="quickDateRange" class="input">
              <option v-for="opt in dateRangeOptions" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
          <template v-if="quickDateRange === 'custom'">
            <div class="filter-item">
              <input v-model="customStartDate" type="date" class="input" />
            </div>
            <div class="filter-item">
              <input v-model="customEndDate" type="date" class="input" />
            </div>
          </template>
          <div class="filter-actions">
            <button class="btn btn-outline" @click="handleReset">重置</button>
            <button class="btn btn-primary" @click="handleSearch">搜索</button>
          </div>
        </div>
      </div>

      <!-- 日志列表 -->
      <div class="content-card">
        <div class="table-container">
          <table class="data-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>用户</th>
                <th>模块</th>
                <th>操作</th>
                <th>描述</th>
                <th>IP地址</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading && logList.length === 0">
                <td colspan="7" class="text-center py-10">
                  <div class="loading-spinner small"></div>
                </td>
              </tr>
              <tr v-else-if="logList.length === 0">
                <td colspan="7" class="text-center py-10 empty-state">
                  <p>暂无日志数据</p>
                </td>
              </tr>
              <tr v-else v-for="log in logList" :key="log.id" class="table-row">
                <td>
                  <span class="text-sm text-gray-600">{{ formatTime(log.createdTime) }}</span>
                </td>
                <td>
                  <span class="username-cell">{{ log.username }}</span>
                </td>
                <td>
                  <span class="module-badge">{{ log.operationModule }}</span>
                </td>
                <td>
                  <span class="type-badge" :class="getOperationTypeClass(log.operationType)">
                    {{ getOperationTypeName(log.operationType) }}
                  </span>
                </td>
                <td>
                  <span class="desc-cell" :title="log.operationDesc">{{ log.operationDesc }}</span>
                </td>
                <td>
                  <span class="text-sm text-gray-500">{{ log.ipAddress || '-' }}</span>
                </td>
                <td>
                  <span class="status-badge" :class="getStatusClass(log.status)">
                    {{ getStatusName(log.status) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 分页 -->
        <div class="table-footer" v-if="pagination.totalElements > 0">
          <span class="text-gray-500">
            共 {{ pagination.totalElements }} 条记录，第 {{ pagination.page + 1 }} / {{ pagination.totalPages || 1 }} 页
          </span>
          <div class="pagination">
            <button
              class="page-btn"
              :disabled="pagination.page === 0"
              @click="handlePageChange(pagination.page)"
            >
              上一页
            </button>
            <button
              class="page-btn"
              :disabled="pagination.page >= pagination.totalPages - 1"
              @click="handlePageChange(pagination.page + 2)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 统计分析 ==================== -->
    <div v-if="activeTab === 'statistics'" class="tab-content">
      <!-- 概览卡片 -->
      <div class="stats-overview" v-if="statistics">
        <div class="stat-card">
          <div class="stat-value">{{ statistics.totalOperations }}</div>
          <div class="stat-label">总操作次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ statistics.loginCount }}</div>
          <div class="stat-label">登录次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ statistics.activeUsers }}</div>
          <div class="stat-label">活跃用户数</div>
        </div>
      </div>

      <!-- 图表区域 -->
      <div class="charts-grid" v-if="statistics">
        <!-- 趋势图 -->
        <div class="chart-card">
          <h3 class="chart-title">操作趋势</h3>
          <v-chart class="chart" :option="trendChartOptions" autoresize />
        </div>

        <!-- 操作类型分布 -->
        <div class="chart-card">
          <h3 class="chart-title">操作类型分布</h3>
          <v-chart class="chart" :option="operationTypeChartOptions" autoresize />
        </div>

        <!-- 模块分布 -->
        <div class="chart-card">
          <h3 class="chart-title">模块分布</h3>
          <v-chart class="chart" :option="moduleChartOptions" autoresize />
        </div>

        <!-- 用户活跃排行 -->
        <div class="chart-card">
          <h3 class="chart-title">用户活跃排行 TOP10</h3>
          <v-chart class="chart" :option="userActivityChartOptions" autoresize />
        </div>
      </div>
    </div>

    <!-- ==================== 月度报表 ==================== -->
    <div v-if="activeTab === 'monthly'" class="tab-content">
      <div class="report-header">
        <h2 class="report-title">{{ reportYear }}年{{ reportMonth }}月 系统操作报告</h2>
        <div class="report-selector">
          <select v-model="reportMonth" class="input" @change="loadMonthlyReport">
            <option v-for="m in 12" :key="m" :value="m">{{ m }}月</option>
          </select>
          <select v-model="reportYear" class="input" @change="loadMonthlyReport">
            <option v-for="y in [2024, 2025, 2026]" :key="y" :value="y">{{ y }}年</option>
          </select>
        </div>
      </div>

      <template v-if="monthlyReport">
        <!-- 月度概览 -->
        <div class="stats-overview">
          <div class="stat-card">
            <div class="stat-value">{{ monthlyReport.statistics.totalOperations }}</div>
            <div class="stat-label">本月操作次数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ monthlyReport.statistics.loginCount }}</div>
            <div class="stat-label">本月登录次数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ monthlyReport.statistics.activeUsers }}</div>
            <div class="stat-label">活跃用户数</div>
          </div>
        </div>

        <!-- 日报明细表 -->
        <div class="content-card">
          <h3 class="card-title">日报明细</h3>
          <div class="daily-report-table">
            <table class="data-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>操作次数</th>
                  <th>登录</th>
                  <th>创建</th>
                  <th>更新</th>
                  <th>删除</th>
                  <th>导入/导出</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="daily in monthlyReport.dailyReports" :key="daily.date">
                  <td>{{ daily.date }}</td>
                  <td class="text-bold">{{ daily.count }}</td>
                  <td>{{ daily.typeStats['LOGIN'] || 0 }}</td>
                  <td>{{ daily.typeStats['CREATE'] || 0 }}</td>
                  <td>{{ daily.typeStats['UPDATE'] || 0 }}</td>
                  <td>{{ daily.typeStats['DELETE'] || 0 }}</td>
                  <td>{{ (daily.typeStats['IMPORT'] || 0) + (daily.typeStats['EXPORT'] || 0) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </div>

    <!-- ==================== 年度报表 ==================== -->
    <div v-if="activeTab === 'yearly'" class="tab-content">
      <div class="report-header">
        <h2 class="report-title">{{ reportYear }}年度 系统操作报告</h2>
        <div class="report-selector">
          <select v-model="reportYear" class="input" @change="loadYearlyReport">
            <option v-for="y in [2024, 2025, 2026]" :key="y" :value="y">{{ y }}年</option>
          </select>
        </div>
      </div>

      <template v-if="yearlyReport">
        <!-- 年度概览 -->
        <div class="stats-overview">
          <div class="stat-card">
            <div class="stat-value">{{ yearlyReport.statistics.totalOperations }}</div>
            <div class="stat-label">年度操作次数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ yearlyReport.statistics.loginCount }}</div>
            <div class="stat-label">年度登录次数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ yearlyReport.statistics.activeUsers }}</div>
            <div class="stat-label">活跃用户总数</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">{{ Math.round(yearlyReport.statistics.totalOperations / 365) }}</div>
            <div class="stat-label">日均操作次数</div>
          </div>
        </div>

        <!-- 月度趋势 -->
        <div class="chart-card full-width">
          <h3 class="chart-title">月度操作趋势</h3>
          <v-chart class="chart tall" :option="yearlyTrendChartOptions" autoresize />
        </div>

        <!-- 用户排名 -->
        <div class="content-card">
          <h3 class="card-title">年度活跃用户排行 TOP10</h3>
          <div class="ranking-table">
            <table class="data-table">
              <thead>
                <tr>
                  <th>排名</th>
                  <th>用户名</th>
                  <th>操作次数</th>
                  <th>占比</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in yearlyReport.userRankings" :key="user.rank">
                  <td>
                    <span class="rank-badge" :class="{ gold: user.rank === 1, silver: user.rank === 2, bronze: user.rank === 3 }">
                      {{ user.rank }}
                    </span>
                  </td>
                  <td class="text-bold">{{ user.username }}</td>
                  <td>{{ user.count }}</td>
                  <td>{{ ((user.count / yearlyReport.statistics.totalOperations) * 100).toFixed(1) }}%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.operation-log-page {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--spacing-md);
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--gray-900);
  margin-bottom: 0.25rem;
}

.page-subtitle {
  font-size: 0.875rem;
  color: var(--gray-500);
}

/* 标签页切换 */
.tab-section {
  background: white;
  border-radius: var(--radius-lg);
  padding: var(--spacing-md);
  box-shadow: var(--shadow-md);
}

.tab-buttons {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}

.tab-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: none;
  background: transparent;
  color: var(--gray-600);
  border-radius: var(--radius);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.tab-btn:hover {
  background: var(--gray-100);
}

.tab-btn.active {
  background: var(--primary-color);
  color: white;
}

/* 标签内容 */
.tab-content {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* 筛选区域 */
.filter-section {
  background: white;
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}

.filter-row {
  display: flex;
  gap: var(--spacing-md);
  flex-wrap: wrap;
  align-items: center;
}

.filter-item {
  min-width: 150px;
}

.filter-actions {
  display: flex;
  gap: var(--spacing-sm);
  margin-left: auto;
}

/* 内容卡片 */
.content-card {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  overflow: hidden;
}

.card-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--gray-800);
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--gray-100);
  margin: 0;
}

/* 表格 */
.table-container {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table thead {
  background: var(--gray-50);
}

.data-table th {
  padding: var(--spacing-md);
  text-align: left;
  font-weight: 600;
  font-size: 0.8125rem;
  color: var(--gray-600);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid var(--gray-200);
  white-space: nowrap;
}

.data-table td {
  padding: var(--spacing-md);
  border-bottom: 1px solid var(--gray-100);
  font-size: 0.875rem;
}

.table-row:hover {
  background-color: var(--gray-50);
}

.username-cell {
  font-weight: 600;
  color: var(--gray-800);
}

.module-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius);
  font-size: 0.75rem;
  background: var(--gray-100);
  color: var(--gray-700);
}

.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: var(--radius);
  font-size: 0.75rem;
  font-weight: 600;
}

.type-badge.login { background: rgba(59, 130, 246, 0.1); color: #3b82f6; }
.type-badge.logout { background: rgba(107, 114, 128, 0.1); color: #6b7280; }
.type-badge.create { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.type-badge.update { background: rgba(245, 158, 11, 0.1); color: #f59e0b; }
.type-badge.delete { background: rgba(239, 68, 68, 0.1); color: #ef4444; }
.type-badge.export, .type-badge.import { background: rgba(139, 92, 246, 0.1); color: #8b5cf6; }
.type-badge.query { background: rgba(14, 165, 233, 0.1); color: #0ea5e9; }
.type-badge.default { background: var(--gray-100); color: var(--gray-600); }

.desc-cell {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
  color: var(--gray-600);
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 9999px;
  font-size: 0.75rem;
  font-weight: 600;
}

.status-badge.success { background: rgba(16, 185, 129, 0.1); color: #10b981; }
.status-badge.error { background: rgba(239, 68, 68, 0.1); color: #ef4444; }

/* 分页 */
.table-footer {
  padding: var(--spacing-md) var(--spacing-lg);
  border-top: 1px solid var(--gray-100);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.pagination {
  display: flex;
  gap: var(--spacing-sm);
}

.page-btn {
  padding: 6px 12px;
  border: 1px solid var(--gray-300);
  background: white;
  border-radius: var(--radius);
  font-size: 0.875rem;
  cursor: pointer;
}

.page-btn:hover:not(:disabled) {
  background: var(--gray-50);
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 概览卡片 */
.stats-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--spacing-lg);
}

.stat-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  box-shadow: var(--shadow-md);
  text-align: center;
}

.stat-value {
  font-size: 2rem;
  font-weight: 700;
  color: var(--primary-color);
  margin-bottom: 4px;
}

.stat-label {
  font-size: 0.875rem;
  color: var(--gray-500);
}

/* 图表区域 */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-lg);
}

.chart-card {
  background: white;
  border-radius: var(--radius-lg);
  padding: var(--spacing-lg);
  box-shadow: var(--shadow-md);
}

.chart-card.full-width {
  grid-column: 1 / -1;
}

.chart-title {
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--gray-700);
  margin: 0 0 var(--spacing-md) 0;
}

.chart {
  width: 100%;
  height: 280px;
}

.chart.tall {
  height: 350px;
}

/* 报表头部 */
.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--spacing-md);
}

.report-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--gray-800);
  margin: 0;
}

.report-selector {
  display: flex;
  gap: var(--spacing-sm);
}

.report-selector .input {
  min-width: 100px;
}

/* 日报/排名表格 */
.daily-report-table,
.ranking-table {
  overflow-x: auto;
}

.text-bold {
  font-weight: 600;
  color: var(--gray-800);
}

.rank-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-size: 0.75rem;
  font-weight: 600;
  background: var(--gray-100);
  color: var(--gray-600);
}

.rank-badge.gold { background: linear-gradient(135deg, #ffd700, #ffb347); color: #8b6914; }
.rank-badge.silver { background: linear-gradient(135deg, #e8e8e8, #c0c0c0); color: #666; }
.rank-badge.bronze { background: linear-gradient(135deg, #cd7f32, #b87333); color: #fff; }

/* 通用样式 */
.text-center { text-align: center; }
.text-gray-500 { color: var(--gray-500); }
.text-gray-600 { color: var(--gray-600); }
.text-sm { font-size: 0.8125rem; }
.py-10 { padding: 2.5rem 0; }
.empty-state { color: var(--gray-500); }

/* 按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: var(--radius);
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--transition-fast);
  border: none;
}

.btn-primary {
  background: var(--primary-color);
  color: white;
}

.btn-primary:hover {
  background: #0a5555;
}

.btn-outline {
  background: transparent;
  color: var(--gray-700);
  border: 1px solid var(--gray-300);
}

.btn-outline:hover {
  background: var(--gray-50);
}

/* 输入框 */
.input {
  padding: 8px 12px;
  border: 1px solid var(--gray-300);
  border-radius: var(--radius);
  font-size: 0.875rem;
  background: white;
  outline: none;
  transition: border-color var(--transition-fast);
}

.input:focus {
  border-color: var(--primary-color);
}

/* 加载状态 */
.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--gray-200);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

.loading-spinner.small {
  width: 24px;
  height: 24px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 响应式 */
@media (max-width: 1024px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }

  .stats-overview {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-row {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-item {
    min-width: 100%;
  }

  .filter-actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .report-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .report-selector {
    width: 100%;
  }

  .report-selector .input {
    flex: 1;
  }
}
</style>
