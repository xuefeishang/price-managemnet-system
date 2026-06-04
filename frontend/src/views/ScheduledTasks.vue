<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import {
  createScheduledTask,
  disableScheduledTask,
  enableScheduledTask,
  getScheduledTaskLogs,
  getScheduledTasks,
  runScheduledTaskOnce,
  updateScheduledTask
} from '@/api/scheduledTasks'
import type { PageResponse, ScheduledTask, ScheduledTaskLog } from '@/types'
import { getDictOptions, getDictValue, loadAllDicts } from '@/composables/useDict'

const loading = ref(false)
const saving = ref(false)
const tasks = ref<ScheduledTask[]>([])
const logs = ref<ScheduledTaskLog[]>([])
const selectedTask = ref<ScheduledTask | null>(null)
const showEditor = ref(false)
const showLogs = ref(false)
const scheduledTaskTypeOptions = computed(() => getDictOptions('scheduled_task_type'))

const defaultPricePublishConfig = {
  dateOffsetDays: -1,
  publishOnlyCompleteDraft: false,
  notifyChannels: ['IN_APP'],
  recipientRoles: ['ADMIN', 'EDITOR', 'VIEWER'],
  systemUserId: 0,
  skipIfNoDraft: true
}

const form = ref<ScheduledTask>({
  taskCode: '',
  taskName: '',
  taskType: 'PRICE_PUBLISH',
  cronExpression: '0 0 9 * * ?',
  timezone: 'Asia/Shanghai',
  enabled: false,
  configJson: JSON.stringify(defaultPricePublishConfig, null, 2),
  remark: ''
})

const priceConfig = computed({
  get() {
    try {
      return { ...defaultPricePublishConfig, ...(form.value.configJson ? JSON.parse(form.value.configJson) : {}) }
    } catch {
      return defaultPricePublishConfig
    }
  },
  set(value) {
    form.value.configJson = JSON.stringify(value, null, 2)
  }
})

const loadTasks = async () => {
  loading.value = true
  try {
    const response = await getScheduledTasks({ page: 0, size: 100 })
    const pageData = response.data as PageResponse<ScheduledTask>
    tasks.value = pageData.content || []
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  selectedTask.value = null
  form.value = {
    taskCode: 'PRICE_AUTO_PUBLISH',
    taskName: '价格自动发布',
    taskType: 'PRICE_PUBLISH',
    cronExpression: '0 0 9 * * ?',
    timezone: 'Asia/Shanghai',
    enabled: false,
    configJson: JSON.stringify(defaultPricePublishConfig, null, 2),
    remark: '默认停用，需管理员确认后启用'
  }
  showEditor.value = true
}

const openEdit = (task: ScheduledTask) => {
  selectedTask.value = task
  form.value = { ...task }
  showEditor.value = true
}

const saveTask = async () => {
  saving.value = true
  try {
    if (form.value.id) {
      await updateScheduledTask(form.value.id, form.value)
      showToast('定时任务已更新')
    } else {
      await createScheduledTask(form.value)
      showToast('定时任务已创建')
    }
    showEditor.value = false
    await loadTasks()
  } finally {
    saving.value = false
  }
}

const toggleTask = async (task: ScheduledTask) => {
  if (!task.id) return
  if (task.enabled) {
    await disableScheduledTask(task.id)
    showToast('定时任务已停用')
  } else {
    await enableScheduledTask(task.id)
    showToast('定时任务已启用')
  }
  await loadTasks()
}

const runOnce = async (task: ScheduledTask) => {
  if (!task.id) return
  await runScheduledTaskOnce(task.id)
  showToast('手动执行完成')
  await loadTasks()
}

const openLogs = async (task: ScheduledTask) => {
  if (!task.id) return
  selectedTask.value = task
  const response = await getScheduledTaskLogs(task.id, { page: 0, size: 20 })
  const pageData = response.data as PageResponse<ScheduledTaskLog>
  logs.value = pageData.content || []
  showLogs.value = true
}

const updatePriceConfig = (key: keyof typeof defaultPricePublishConfig, value: any) => {
  priceConfig.value = { ...priceConfig.value, [key]: value }
}

onMounted(async () => {
  await loadAllDicts()
  await loadTasks()
})
</script>

<template>
  <div class="scheduled-page">
    <header class="page-header">
      <div>
        <h1>定时任务</h1>
        <p>管理系统内可配置的自动任务</p>
      </div>
      <button class="primary-btn" type="button" @click="openCreate">新增任务</button>
    </header>

    <section class="task-table">
      <table>
        <thead>
          <tr>
            <th>任务名称</th>
            <th>任务类型</th>
            <th>Cron</th>
            <th>启用</th>
            <th>最近状态</th>
            <th>下次执行</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="7" class="state-cell">正在加载...</td>
          </tr>
          <tr v-else-if="tasks.length === 0">
            <td colspan="7" class="state-cell">暂无定时任务</td>
          </tr>
          <tr v-for="task in tasks" v-else :key="task.id">
            <td>
              <strong>{{ task.taskName }}</strong>
              <span>{{ task.taskCode }}</span>
            </td>
            <td>{{ getDictValue('scheduled_task_type', task.taskType) }}</td>
            <td>{{ task.cronExpression }}</td>
            <td>{{ getDictValue('common_status', task.enabled ? 'ACTIVE' : 'INACTIVE') }}</td>
            <td>{{ task.lastRunStatus ? getDictValue('scheduled_task_run_status', task.lastRunStatus) : '-' }}</td>
            <td>{{ task.nextRunTime || '-' }}</td>
            <td class="actions">
              <button type="button" @click="openEdit(task)">编辑</button>
              <button type="button" @click="toggleTask(task)">{{ task.enabled ? '停用' : '启用' }}</button>
              <button type="button" @click="runOnce(task)">执行</button>
              <button type="button" @click="openLogs(task)">日志</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <section v-if="showEditor" class="editor-panel">
      <div class="panel-header">
        <h2>{{ selectedTask ? '编辑任务' : '新增任务' }}</h2>
        <button type="button" @click="showEditor = false">关闭</button>
      </div>
      <div class="form-grid">
        <label>任务编码<input v-model="form.taskCode" /></label>
        <label>任务名称<input v-model="form.taskName" /></label>
        <label>任务类型
          <select v-model="form.taskType">
            <option v-for="option in scheduledTaskTypeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <label>Cron<input v-model="form.cronExpression" /></label>
        <label>时区<input v-model="form.timezone" /></label>
        <label class="check-label"><input v-model="form.enabled" type="checkbox" /> 启用</label>
      </div>

      <div v-if="form.taskType === 'PRICE_PUBLISH'" class="form-grid">
        <label>日期偏移
          <input
            type="number"
            :value="priceConfig.dateOffsetDays"
            @input="updatePriceConfig('dateOffsetDays', Number(($event.target as HTMLInputElement).value))"
          />
        </label>
        <label class="check-label">
          <input
            type="checkbox"
            :checked="priceConfig.publishOnlyCompleteDraft"
            @change="updatePriceConfig('publishOnlyCompleteDraft', ($event.target as HTMLInputElement).checked)"
          />
          仅完整草稿发布
        </label>
        <label class="check-label">
          <input
            type="checkbox"
            :checked="priceConfig.skipIfNoDraft"
            @change="updatePriceConfig('skipIfNoDraft', ($event.target as HTMLInputElement).checked)"
          />
          无草稿时跳过
        </label>
      </div>

      <label class="json-label">任务参数 JSON<textarea v-model="form.configJson" rows="8"></textarea></label>
      <label class="json-label">备注<textarea v-model="form.remark" rows="3"></textarea></label>
      <button class="primary-btn" type="button" :disabled="saving" @click="saveTask">{{ saving ? '保存中...' : '保存' }}</button>
    </section>

    <section v-if="showLogs" class="editor-panel">
      <div class="panel-header">
        <h2>执行日志</h2>
        <button type="button" @click="showLogs = false">关闭</button>
      </div>
      <table>
        <thead>
          <tr>
            <th>触发方式</th>
            <th>状态</th>
            <th>开始时间</th>
            <th>耗时</th>
            <th>摘要</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td>{{ getDictValue('scheduled_task_trigger_type', log.triggerType) }}</td>
            <td>{{ getDictValue('scheduled_task_run_status', log.status) }}</td>
            <td>{{ log.startedTime || '-' }}</td>
            <td>{{ log.durationMs ?? '-' }}</td>
            <td>{{ log.message || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.scheduled-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header,
.task-table,
.editor-panel {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 18px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

h1,
h2,
p {
  margin: 0;
}

p,
td span {
  color: var(--text-secondary);
  font-size: 13px;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 12px;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  vertical-align: top;
}

.state-cell {
  text-align: center;
  color: var(--text-secondary);
}

.actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

button,
.primary-btn {
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-primary);
  min-height: 36px;
  padding: 0 12px;
  cursor: pointer;
}

.primary-btn {
  border-color: var(--primary-color);
  background: var(--primary-color);
  color: #fff;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--text-secondary);
  font-size: 13px;
}

.check-label {
  flex-direction: row;
  align-items: center;
}

input,
select,
textarea {
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-primary);
  min-height: 36px;
  padding: 8px 10px;
  font: inherit;
}

.json-label {
  margin-bottom: 12px;
}
</style>
