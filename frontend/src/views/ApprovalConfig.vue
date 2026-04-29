<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import {
  getWorkflows,
  createWorkflow,
  updateWorkflow,
  deleteWorkflow,
  activateWorkflow,
  deactivateWorkflow,
  getWorkflowNodes,
  addWorkflowNode,
  updateWorkflowNode,
  deleteWorkflowNode,
  type ApprovalWorkflow,
  type ApprovalNode
} from '@/api/approval'

// 加载状态
// const loading = ref(false)
const workflowLoading = ref(false)

// 工作流列表
const workflowList = ref<ApprovalWorkflow[]>([])
const selectedWorkflow = ref<ApprovalWorkflow | null>(null)
const workflowNodes = ref<ApprovalNode[]>([])

// 工作流编辑弹窗
const showWorkflowDialog = ref(false)
const showNodeDialog = ref(false)
const showNodeEditDialog = ref(false)
const editingWorkflow = ref<Partial<ApprovalWorkflow>>({
  workflowName: '',
  workflowType: 'PRICE_CHANGE',
  approvalLevel: 1,
  isActive: true
})
const editingNode = ref<Partial<ApprovalNode>>({
  nodeOrder: 1,
  nodeType: 'APPROVER',
  approverRole: 'EDITOR',
  isRequired: true
})

// 工作流类型选项（从字典服务获取，computed 保证缓存刷新后联动）
import { getDictOptions, getDictValue } from '@/composables/useDict'
const workflowTypeOptions = computed(() => getDictOptions('workflow_type').map(o => ({ text: o.label, value: o.value })))

// 节点类型选项（从字典服务获取）
const nodeTypeOptions = computed(() => getDictOptions('node_type').map(o => ({ text: o.label, value: o.value })))

// 角色选项（从字典服务获取，审批角色不含VIEWER）
const roleOptions = computed(() => getDictOptions('user_role').filter(o => o.value !== 'VIEWER').map(o => ({ text: o.label, value: o.value })))

// 获取工作流类型名称
const getWorkflowTypeName = (type: string) => {
  return getDictValue('workflow_type', type)
}

// 加载工作流列表
const loadWorkflows = async () => {
  workflowLoading.value = true
  try {
    const response = await getWorkflows()
    if (response.data) {
      workflowList.value = response.data
    }
  } catch (error: any) {
    showToast(error.message || '加载工作流失败')
  } finally {
    workflowLoading.value = false
  }
}

// 加载工作流节点
const loadWorkflowNodes = async (workflowId: number) => {
  try {
    const response = await getWorkflowNodes(workflowId)
    if (response.data) {
      workflowNodes.value = response.data
    }
  } catch (error: any) {
    showToast(error.message || '加载节点失败')
  }
}

// 选择工作流查看节点
const selectWorkflow = (workflow: ApprovalWorkflow) => {
  selectedWorkflow.value = workflow
  loadWorkflowNodes(workflow.id!)
}

// 打开工作流编辑弹窗
const openWorkflowDialog = (workflow?: ApprovalWorkflow) => {
  if (workflow) {
    editingWorkflow.value = { ...workflow }
  } else {
    editingWorkflow.value = {
      workflowName: '',
      workflowType: 'PRICE_CHANGE',
      approvalLevel: 1,
      isActive: true
    }
  }
  showWorkflowDialog.value = true
}

// 保存工作流
const handleSaveWorkflow = async () => {
  if (!editingWorkflow.value.workflowName) {
    showToast('请输入工作流名称')
    return
  }
  try {
    if (editingWorkflow.value.id) {
      await updateWorkflow(editingWorkflow.value.id, editingWorkflow.value as ApprovalWorkflow)
      showToast('更新成功')
    } else {
      await createWorkflow(editingWorkflow.value as ApprovalWorkflow)
      showToast('创建成功')
    }
    showWorkflowDialog.value = false
    loadWorkflows()
  } catch (error: any) {
    showToast(error.message || '保存失败')
  }
}

// 删除工作流
const handleDeleteWorkflow = async (workflow: ApprovalWorkflow) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: `确定要删除工作流"${workflow.workflowName}"吗？`
    })
    await deleteWorkflow(workflow.id!)
    showToast('已删除')
    if (selectedWorkflow.value?.id === workflow.id) {
      selectedWorkflow.value = null
      workflowNodes.value = []
    }
    loadWorkflows()
  } catch (error: any) {
    if (error !== 'cancel') {
      showToast(error.message || '删除失败')
    }
  }
}

// 激活工作流
const handleActivate = async (workflow: ApprovalWorkflow) => {
  try {
    await activateWorkflow(workflow.id!)
    showToast('激活成功')
    loadWorkflows()
  } catch (error: any) {
    showToast(error.message || '激活失败')
  }
}

// 停用工作流
const handleDeactivate = async (workflow: ApprovalWorkflow) => {
  try {
    await showConfirmDialog({
      title: '确认停用',
      message: `确定要停用工作流"${workflow.workflowName}"吗？`
    })
    await deactivateWorkflow(workflow.id!)
    showToast('停用成功')
    loadWorkflows()
  } catch (error: any) {
    if (error !== 'cancel') {
      showToast(error.message || '停用失败')
    }
  }
}

// 打开节点添加弹窗
const openNodeDialog = () => {
  if (!selectedWorkflow.value) {
    showToast('请先选择工作流')
    return
  }
  editingNode.value = {
    workflowId: selectedWorkflow.value.id,
    nodeOrder: workflowNodes.value.length + 1,
    nodeType: 'APPROVER',
    approverRole: 'EDITOR',
    isRequired: true
  }
  showNodeDialog.value = true
}

// 打开节点编辑弹窗
const openNodeEditDialog = (node: ApprovalNode) => {
  editingNode.value = { ...node }
  showNodeEditDialog.value = true
}

// 保存节点
const handleSaveNode = async () => {
  try {
    await addWorkflowNode(selectedWorkflow.value!.id!, editingNode.value as ApprovalNode)
    showToast('添加成功')
    showNodeDialog.value = false
    if (selectedWorkflow.value?.id) {
      loadWorkflowNodes(selectedWorkflow.value.id)
    }
  } catch (error: any) {
    showToast(error.message || '添加失败')
  }
}

// 更新节点
const handleUpdateNode = async () => {
  try {
    await updateWorkflowNode(editingNode.value.id!, editingNode.value as ApprovalNode)
    showToast('更新成功')
    showNodeEditDialog.value = false
    if (selectedWorkflow.value?.id) {
      loadWorkflowNodes(selectedWorkflow.value.id)
    }
  } catch (error: any) {
    showToast(error.message || '更新失败')
  }
}

// 删除节点
const handleDeleteNode = async (node: ApprovalNode) => {
  try {
    await showConfirmDialog({
      title: '确认删除',
      message: '确定要删除该节点吗？'
    })
    await deleteWorkflowNode(node.id!)
    showToast('已删除')
    if (selectedWorkflow.value?.id) {
      loadWorkflowNodes(selectedWorkflow.value.id)
    }
  } catch (error: any) {
    if (error !== 'cancel') {
      showToast(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  loadWorkflows()
})
</script>

<template>
  <div class="approval-config-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">审批流配置</h1>
        <p class="page-subtitle">配置审批工作流及节点</p>
      </div>
      <button class="btn btn-primary" @click="openWorkflowDialog()">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <line x1="12" y1="5" x2="12" y2="19"/>
          <line x1="5" y1="12" x2="19" y2="12"/>
        </svg>
        新建工作流
      </button>
    </div>

    <!-- 内容区域 -->
    <div class="workflow-layout">
      <!-- 工作流列表 -->
      <div class="workflow-sidebar">
        <div class="sidebar-header">
          <h3>工作流列表</h3>
          <span class="count">{{ workflowList.length }}</span>
        </div>
        <div v-if="workflowLoading" class="loading-state">
          <div class="loading-spinner small"></div>
        </div>
        <div v-else-if="workflowList.length === 0" class="empty-state small">
          <p>暂无工作流</p>
        </div>
        <div v-else class="workflow-list">
          <div
            v-for="workflow in workflowList"
            :key="workflow.id"
            class="workflow-item"
            :class="{ active: selectedWorkflow?.id === workflow.id }"
            @click="selectWorkflow(workflow)"
          >
            <div class="workflow-item-info">
              <div class="workflow-name-row">
                <span class="workflow-name">{{ workflow.workflowName }}</span>
                <span class="status-badge" :class="{ active: workflow.isActive }">
                  {{ workflow.isActive ? '激活' : '停用' }}
                </span>
              </div>
              <span class="workflow-type">{{ getWorkflowTypeName(workflow.workflowType) }}</span>
            </div>
            <div class="workflow-item-actions">
              <button
                v-if="workflow.isActive"
                class="icon-btn"
                title="停用"
                @click.stop="handleDeactivate(workflow)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <circle cx="12" cy="12" r="10"/>
                  <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/>
                </svg>
              </button>
              <button
                v-else
                class="icon-btn success"
                title="激活"
                @click.stop="handleActivate(workflow)"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/>
                  <polyline points="22 4 12 14.01 9 11.01"/>
                </svg>
              </button>
              <button class="icon-btn" title="编辑" @click.stop="openWorkflowDialog(workflow)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </button>
              <button class="icon-btn danger" title="删除" @click.stop="handleDeleteWorkflow(workflow)">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="3 6 5 6 21 6"/>
                  <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 节点配置 -->
      <div class="node-config">
        <div v-if="!selectedWorkflow" class="empty-state">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <polygon points="12 2 2 7 12 12 22 7 12 2"/>
            <polyline points="2 17 12 22 22 17"/>
            <polyline points="2 12 12 17 22 12"/>
          </svg>
          <p>请选择工作流查看节点配置</p>
        </div>
        <div v-else>
          <div class="sidebar-header">
            <h3>{{ selectedWorkflow.workflowName }} - 节点配置</h3>
            <button class="btn btn-primary btn-sm" @click="openNodeDialog">添加节点</button>
          </div>
          <div v-if="workflowNodes.length === 0" class="empty-state small">
            <p>暂无节点配置，点击添加按钮创建</p>
          </div>
          <div v-else class="node-list">
            <div v-for="(node, index) in workflowNodes" :key="node.id" class="node-card">
              <div class="node-drag-handle">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="8" y1="6" x2="21" y2="6"/>
                  <line x1="8" y1="12" x2="21" y2="12"/>
                  <line x1="8" y1="18" x2="21" y2="18"/>
                  <line x1="3" y1="6" x2="3.01" y2="6"/>
                  <line x1="3" y1="12" x2="3.01" y2="12"/>
                  <line x1="3" y1="18" x2="3.01" y2="18"/>
                </svg>
              </div>
              <div class="node-order">{{ index + 1 }}</div>
              <div class="node-info">
                <div class="node-row">
                  <span class="node-type-badge" :class="node.nodeType.toLowerCase()">
                    {{ getDictValue('node_type', node.nodeType) }}
                  </span>
                  <strong>{{ getDictValue('user_role', node.approverRole) }}</strong>
                </div>
                <div class="node-row">
                  <span class="node-label">顺序：</span>
                  <span>{{ node.nodeOrder }}</span>
                  <span class="node-label ml-3">必须审批：</span>
                  <span>{{ node.isRequired ? '是' : '否' }}</span>
                </div>
              </div>
              <div class="node-actions">
                <button class="icon-btn" title="编辑" @click="openNodeEditDialog(node)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
                <button class="icon-btn danger" title="删除" @click="handleDeleteNode(node)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 工作流编辑弹窗 ==================== -->
    <van-popup v-model:show="showWorkflowDialog" position="bottom" round>
      <div class="dialog-content">
        <h3 class="dialog-title">{{ editingWorkflow.id ? '编辑工作流' : '新建工作流' }}</h3>
        <div class="dialog-body">
          <div class="form-group">
            <label>工作流名称</label>
            <input v-model="editingWorkflow.workflowName" class="input" placeholder="请输入工作流名称" />
          </div>
          <div class="form-group">
            <label>工作流类型</label>
            <select v-model="editingWorkflow.workflowType" class="input">
              <option v-for="opt in workflowTypeOptions" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>审批级别</label>
            <input v-model.number="editingWorkflow.approvalLevel" type="number" class="input" min="1" />
          </div>
          <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="editingWorkflow.isActive" />
              启用该工作流
            </label>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-outline" @click="showWorkflowDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveWorkflow">保存</button>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 节点添加弹窗 ==================== -->
    <van-popup v-model:show="showNodeDialog" position="bottom" round>
      <div class="dialog-content">
        <h3 class="dialog-title">添加节点</h3>
        <div class="dialog-body">
          <div class="form-group">
            <label>节点类型</label>
            <select v-model="editingNode.nodeType" class="input">
              <option v-for="opt in nodeTypeOptions" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>审批角色</label>
            <select v-model="editingNode.approverRole" class="input">
              <option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>节点顺序</label>
            <input v-model.number="editingNode.nodeOrder" type="number" class="input" min="1" />
          </div>
          <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="editingNode.isRequired" />
              是否必须审批（知会节点可关闭）
            </label>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-outline" @click="showNodeDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveNode">保存</button>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 节点编辑弹窗 ==================== -->
    <van-popup v-model:show="showNodeEditDialog" position="bottom" round>
      <div class="dialog-content">
        <h3 class="dialog-title">编辑节点</h3>
        <div class="dialog-body">
          <div class="form-group">
            <label>节点类型</label>
            <select v-model="editingNode.nodeType" class="input">
              <option v-for="opt in nodeTypeOptions" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>审批角色</label>
            <select v-model="editingNode.approverRole" class="input">
              <option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">
                {{ opt.text }}
              </option>
            </select>
          </div>
          <div class="form-group">
            <label>节点顺序</label>
            <input v-model.number="editingNode.nodeOrder" type="number" class="input" min="1" />
          </div>
          <div class="form-group">
            <label class="checkbox-label">
              <input type="checkbox" v-model="editingNode.isRequired" />
              是否必须审批
            </label>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-outline" @click="showNodeEditDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleUpdateNode">保存</button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.approval-config-page {
  padding: 16px;
  min-height: 100vh;
  background: #f5f5f5;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.page-subtitle {
  font-size: 13px;
  color: #999;
  margin: 4px 0 0 0;
}

/* 按钮样式 */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-sm {
  padding: 4px 10px;
  font-size: 12px;
}

.btn-primary {
  background: #0D6E6E;
  color: #fff;
}

.btn-primary:hover {
  background: #0A5A5A;
}

.btn-outline {
  background: transparent;
  border: 1px solid #ddd;
  color: #666;
}

/* 工作流布局 */
.workflow-layout {
  display: flex;
  gap: 16px;
  min-height: 500px;
}

.workflow-sidebar {
  width: 360px;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.sidebar-header h3 {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}

.count {
  background: #f0f0f0;
  color: #666;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.workflow-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.workflow-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border: 1px solid #eee;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.workflow-item:hover {
  border-color: #0D6E6E;
}

.workflow-item.active {
  border-color: #0D6E6E;
  background: #F0F9F9;
}

.workflow-item-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.workflow-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.workflow-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.status-badge {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  background: #f0f0f0;
  color: #999;
}

.status-badge.active {
  background: #E6FFF2;
  color: #07C160;
}

.workflow-type {
  font-size: 12px;
  color: #999;
}

.workflow-item-actions {
  display: flex;
  gap: 4px;
}

.icon-btn {
  padding: 6px;
  border: none;
  background: transparent;
  color: #999;
  cursor: pointer;
  border-radius: 4px;
}

.icon-btn:hover {
  background: #f0f0f0;
  color: #666;
}

.icon-btn.success:hover {
  background: #E6FFF2;
  color: #07C160;
}

.icon-btn.danger:hover {
  background: #FFF0F0;
  color: #F56C6C;
}

/* 节点配置 */
.node-config {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  min-width: 0;
}

.node-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.node-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #eee;
  border-radius: 8px;
}

.node-drag-handle {
  color: #ccc;
  cursor: grab;
}

.node-order {
  width: 28px;
  height: 28px;
  background: #0D6E6E;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.node-info {
  flex: 1;
  min-width: 0;
}

.node-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
}

.node-row:last-child {
  margin-bottom: 0;
}

.node-type-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  margin-right: 4px;
}

.node-type-badge.approver {
  background: #E6F4FF;
  color: #1890FF;
}

.node-type-badge.notifier {
  background: #FFF7E6;
  color: #FF976A;
}

.node-label {
  font-size: 12px;
  color: #999;
}

.ml-3 {
  margin-left: 12px;
}

.node-actions {
  display: flex;
  gap: 4px;
}

/* 加载和空状态 */
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
  color: #999;
}

.empty-state svg {
  margin-bottom: 12px;
  color: #ddd;
}

.empty-state.small {
  padding: 20px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #f0f0f0;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.loading-spinner.small {
  width: 20px;
  height: 20px;
  border-width: 2px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 弹窗样式 */
.dialog-content {
  padding: 20px;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  text-align: center;
}

.dialog-body {
  margin-bottom: 16px;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
}

.input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: #0D6E6E;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.checkbox-label input {
  width: 16px;
  height: 16px;
}

.dialog-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .workflow-layout {
    flex-direction: column;
  }

  .workflow-sidebar {
    width: 100%;
  }
}
</style>
