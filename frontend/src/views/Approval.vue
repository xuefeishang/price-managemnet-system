<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import {
  getPendingApprovals,
  getMyRequests,
  // getRequests,
  getWorkflows,
  getWorkflowNodes,
  approveRequest,
  rejectRequest,
  cancelRequest,
  createWorkflow,
  deleteWorkflow,
  addWorkflowNode,
  deleteWorkflowNode,
  type ApprovalRequest,
  type ApprovalWorkflow,
  type ApprovalNode,
  // type ApprovalPageResponse
} from '@/api/approval'
import { getDictValue, getDictOptions, loadAllDicts } from '@/composables/useDict'
import { usePermission, Permission } from '@/composables/usePermission'

// 加载状态
const loading = ref(false)
const workflowLoading = ref(false)

// 当前用户角色
const userRole = ref<string>('')
const { hasPermission } = usePermission()
const isAdmin = computed(() => userRole.value === 'ADMIN')

// 标签页
const activeTab = ref('pending')

// 待我审批列表
const pendingList = ref<ApprovalRequest[]>([])
const pendingPagination = ref({
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0
})

// 我的申请列表
const myRequestList = ref<ApprovalRequest[]>([])
const myRequestPagination = ref({
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0
})

// 工作流列表
const workflowList = ref<ApprovalWorkflow[]>([])
const selectedWorkflow = ref<ApprovalWorkflow | null>(null)
const workflowNodes = ref<ApprovalNode[]>([])

// 审批弹窗
const showApprovalDialog = ref(false)
const showRejectDialog = ref(false)
const currentRequest = ref<ApprovalRequest | null>(null)
const approvalComment = ref('')

// 工作流编辑弹窗
const showWorkflowDialog = ref(false)
const showNodeDialog = ref(false)
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

// 状态选项
// const statusOptions = [
//   { text: '全部', value: '' },
//   { text: '待审批', value: 'PENDING' },
//   { text: '已通过', value: 'APPROVED' },
//   { text: '已拒绝', value: 'REJECTED' },
//   { text: '已撤回', value: 'CANCELLED' }
// ]

// 业务类型选项
// const businessTypeOptions = [
//   { text: '全部', value: '' },
//   { text: '价格变更', value: 'PRICE' },
//   { text: '产品创建', value: 'PRODUCT' }
// ]

// 工作流类型选项（从字典服务获取）
const workflowTypeOptions = computed(() => getDictOptions('workflow_type').map(o => ({ text: o.label, value: o.value })))

// 节点类型选项（从字典服务获取）
const nodeTypeOptions = computed(() => getDictOptions('node_type').map(o => ({ text: o.label, value: o.value })))

// 角色选项（从字典服务获取）
const roleOptions = computed(() => getDictOptions('user_role').filter(o => o.value !== 'VIEWER').map(o => ({ text: o.label, value: o.value })))

// 筛选条件
// const filterStatus = ref('')
// const filterBusinessType = ref('')

// 加载待我审批
const loadPendingApprovals = async () => {
  loading.value = true
  try {
    const response = await getPendingApprovals(pendingPagination.value.page, pendingPagination.value.size)
    if (response.data) {
      pendingList.value = response.data.content || []
      pendingPagination.value.totalElements = response.data.totalElements || 0
      pendingPagination.value.totalPages = response.data.totalPages || 0
    }
  } catch (error: any) {
    showToast(error.message || '加载待审批请求失败')
  } finally {
    loading.value = false
  }
}

// 加载我的申请
const loadMyRequests = async () => {
  loading.value = true
  try {
    const response = await getMyRequests(myRequestPagination.value.page, myRequestPagination.value.size)
    if (response.data) {
      myRequestList.value = response.data.content || []
      myRequestPagination.value.totalElements = response.data.totalElements || 0
      myRequestPagination.value.totalPages = response.data.totalPages || 0
    }
  } catch (error: any) {
    showToast(error.message || '加载我的申请失败')
  } finally {
    loading.value = false
  }
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

// 切换标签页
const switchTab = (tab: string) => {
  activeTab.value = tab
  if (tab === 'pending') {
    loadPendingApprovals()
  } else if (tab === 'my') {
    loadMyRequests()
  } else if (tab === 'workflow' && isAdmin.value) {
    loadWorkflows()
  }
}

// 获取状态样式
const getStatusClass = (status: string) => {
  const map: Record<string, string> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'default'
  }
  return map[status] || 'default'
}

// 获取状态名称（从字典服务获取）
const getStatusName = (status: string) => {
  return getDictValue('approval_status', status)
}

// 获取业务类型名称（从字典服务获取）
const getBusinessTypeName = (type: string) => {
  return getDictValue('business_type', type)
}

// 格式化时间
const formatTime = (time: string) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

// 打开审批弹窗
const openApprovalDialog = (request: ApprovalRequest) => {
  currentRequest.value = request
  approvalComment.value = ''
  showApprovalDialog.value = true
}

// 打开拒绝弹窗
const openRejectDialog = (request: ApprovalRequest) => {
  currentRequest.value = request
  approvalComment.value = ''
  showRejectDialog.value = true
}

// 执行审批通过
const handleApprove = async () => {
  if (!currentRequest.value?.id) return
  try {
    await approveRequest(currentRequest.value.id, approvalComment.value)
    showToast('审批通过成功')
    showApprovalDialog.value = false
    loadPendingApprovals()
  } catch (error: any) {
    showToast(error.message || '审批失败')
  }
}

// 执行审批拒绝
const handleReject = async () => {
  if (!currentRequest.value?.id) return
  try {
    await rejectRequest(currentRequest.value.id, approvalComment.value)
    showToast('已拒绝')
    showRejectDialog.value = false
    loadPendingApprovals()
  } catch (error: any) {
    showToast(error.message || '操作失败')
  }
}

// 撤回申请
const handleCancel = async (request: ApprovalRequest) => {
  try {
    await showConfirmDialog({
      title: '确认撤回',
      message: '确定要撤回该审批申请吗？'
    })
    await cancelRequest(request.id!)
    showToast('已撤回')
    loadMyRequests()
  } catch (error: any) {
    if (error !== 'cancel') {
      showToast(error.message || '撤回失败')
    }
  }
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
  try {
    await createWorkflow(editingWorkflow.value as ApprovalWorkflow)
    showToast('保存成功')
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
    loadWorkflows()
  } catch (error: any) {
    if (error !== 'cancel') {
      showToast(error.message || '删除失败')
    }
  }
}

// 选择工作流查看节点
const selectWorkflow = (workflow: ApprovalWorkflow) => {
  selectedWorkflow.value = workflow
  loadWorkflowNodes(workflow.id!)
}

// 打开节点编辑弹窗
const openNodeDialog = () => {
  editingNode.value = {
    workflowId: selectedWorkflow.value?.id,
    nodeOrder: workflowNodes.value.length + 1,
    nodeType: 'APPROVER',
    approverRole: 'EDITOR',
    isRequired: true
  }
  showNodeDialog.value = true
}

// 保存节点
const handleSaveNode = async () => {
  try {
    await addWorkflowNode(selectedWorkflow.value!.id!, editingNode.value as ApprovalNode)
    showToast('保存成功')
    showNodeDialog.value = false
    if (selectedWorkflow.value?.id) {
      loadWorkflowNodes(selectedWorkflow.value.id)
    }
  } catch (error: any) {
    showToast(error.message || '保存失败')
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

// 分页变化
const handlePageChange = (type: 'pending' | 'my', page: number) => {
  if (type === 'pending') {
    pendingPagination.value.page = page - 1
    loadPendingApprovals()
  } else {
    myRequestPagination.value.page = page - 1
    loadMyRequests()
  }
}

onMounted(() => {
  loadAllDicts()
  // 获取用户角色
  const storedUser = localStorage.getItem('user')
  if (storedUser) {
    try {
      const user = JSON.parse(storedUser)
      userRole.value = user.role || ''
    } catch {
      userRole.value = ''
    }
  }
  loadPendingApprovals()
})
</script>

<template>
  <div class="approval-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1 class="page-title">审批管理</h1>
        <p class="page-subtitle">处理价格变更和产品创建审批流程</p>
      </div>
    </div>

    <!-- 标签页切换 -->
    <div class="tab-section">
      <div class="tab-buttons">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'pending' }"
          @click="switchTab('pending')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
          待我审批
          <span v-if="pendingPagination.totalElements > 0" class="badge">{{ pendingPagination.totalElements }}</span>
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'my' }"
          @click="switchTab('my')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
            <line x1="16" y1="13" x2="8" y2="13"/>
            <line x1="16" y1="17" x2="8" y2="17"/>
          </svg>
          我的申请
        </button>
        <button
          v-if="isAdmin"
          class="tab-btn"
          :class="{ active: activeTab === 'workflow' }"
          @click="switchTab('workflow')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="12 2 2 7 12 12 22 7 12 2"/>
            <polyline points="2 17 12 22 22 17"/>
            <polyline points="2 12 12 17 22 12"/>
          </svg>
          工作流配置
        </button>
      </div>
    </div>

    <!-- ==================== 待我审批 ==================== -->
    <div v-if="activeTab === 'pending'" class="tab-content">
      <div class="content-card">
        <div v-if="loading && pendingList.length === 0" class="loading-state">
          <div class="loading-spinner"></div>
          <p>加载中...</p>
        </div>
        <div v-else-if="pendingList.length === 0" class="empty-state">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M9 12l2 2 4-4"/>
            <circle cx="12" cy="12" r="10"/>
          </svg>
          <p>暂无待审批事项</p>
        </div>
        <div v-else class="request-list">
          <div v-for="request in pendingList" :key="request.id" class="request-card">
            <div class="request-header">
              <span class="request-type">{{ getBusinessTypeName(request.businessType) }}</span>
              <span class="request-status" :class="getStatusClass(request.status)">
                {{ getStatusName(request.status) }}
              </span>
            </div>
            <div class="request-body">
              <div class="request-info">
                <p><strong>申请人ID：</strong>{{ request.applicantId }}</p>
                <p><strong>业务ID：</strong>{{ request.businessId }}</p>
                <p><strong>申请时间：</strong>{{ formatTime(request.createdTime || '') }}</p>
              </div>
            </div>
            <div class="request-actions">
              <button class="btn btn-outline" @click="openRejectDialog(request)" v-if="hasPermission(Permission.APPROVAL_PROCESS)">拒绝</button>
              <button class="btn btn-primary" @click="openApprovalDialog(request)" v-if="hasPermission(Permission.APPROVAL_PROCESS)">通过</button>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="table-footer" v-if="pendingPagination.totalElements > 0">
          <span class="text-gray-500">
            共 {{ pendingPagination.totalElements }} 条记录，第 {{ pendingPagination.page + 1 }} / {{ pendingPagination.totalPages || 1 }} 页
          </span>
          <div class="pagination">
            <button
              class="page-btn"
              :disabled="pendingPagination.page === 0"
              @click="handlePageChange('pending', pendingPagination.page)"
            >
              上一页
            </button>
            <button
              class="page-btn"
              :disabled="pendingPagination.page >= pendingPagination.totalPages - 1"
              @click="handlePageChange('pending', pendingPagination.page + 2)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 我的申请 ==================== -->
    <div v-if="activeTab === 'my'" class="tab-content">
      <div class="content-card">
        <div v-if="loading && myRequestList.length === 0" class="loading-state">
          <div class="loading-spinner"></div>
          <p>加载中...</p>
        </div>
        <div v-else-if="myRequestList.length === 0" class="empty-state">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
            <polyline points="14 2 14 8 20 8"/>
          </svg>
          <p>您还没有提交过审批申请</p>
        </div>
        <div v-else class="request-list">
          <div v-for="request in myRequestList" :key="request.id" class="request-card">
            <div class="request-header">
              <span class="request-type">{{ getBusinessTypeName(request.businessType) }}</span>
              <span class="request-status" :class="getStatusClass(request.status)">
                {{ getStatusName(request.status) }}
              </span>
            </div>
            <div class="request-body">
              <div class="request-info">
                <p><strong>业务ID：</strong>{{ request.businessId }}</p>
                <p><strong>申请时间：</strong>{{ formatTime(request.createdTime || '') }}</p>
                <p><strong>更新时间：</strong>{{ formatTime(request.updatedTime || '') }}</p>
              </div>
            </div>
            <div class="request-actions">
              <button
                v-if="request.status === 'PENDING'"
                class="btn btn-outline"
                @click="handleCancel(request)"
              >
                撤回
              </button>
            </div>
          </div>
        </div>

        <!-- 分页 -->
        <div class="table-footer" v-if="myRequestPagination.totalElements > 0">
          <span class="text-gray-500">
            共 {{ myRequestPagination.totalElements }} 条记录，第 {{ myRequestPagination.page + 1 }} / {{ myRequestPagination.totalPages || 1 }} 页
          </span>
          <div class="pagination">
            <button
              class="page-btn"
              :disabled="myRequestPagination.page === 0"
              @click="handlePageChange('my', myRequestPagination.page)"
            >
              上一页
            </button>
            <button
              class="page-btn"
              :disabled="myRequestPagination.page >= myRequestPagination.totalPages - 1"
              @click="handlePageChange('my', myRequestPagination.page + 2)"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 工作流配置 (仅管理员) ==================== -->
    <div v-if="activeTab === 'workflow' && isAdmin" class="tab-content">
      <div class="workflow-layout">
        <!-- 工作流列表 -->
        <div class="workflow-sidebar">
          <div class="sidebar-header">
            <h3>工作流列表</h3>
            <button class="btn btn-primary btn-sm" @click="openWorkflowDialog()">新建</button>
          </div>
          <div v-if="workflowLoading" class="loading-state">
            <div class="loading-spinner small"></div>
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
                <span class="workflow-name">{{ workflow.workflowName }}</span>
                <span class="workflow-type">{{ getDictValue('workflow_type', workflow.workflowType) }}</span>
              </div>
              <div class="workflow-item-actions">
                <button class="icon-btn" @click.stop="openWorkflowDialog(workflow)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                  </svg>
                </button>
                <button class="icon-btn danger" @click.stop="handleDeleteWorkflow(workflow)">
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
            <div class="node-list">
              <div v-for="(node, index) in workflowNodes" :key="node.id" class="node-card">
                <div class="node-order">{{ index + 1 }}</div>
                <div class="node-info">
                  <p><strong>节点类型：</strong>{{ getDictValue('node_type', node.nodeType) }}</p>
                  <p><strong>审批角色：</strong>{{ node.approverRole }}</p>
                  <p><strong>是否必填：</strong>{{ node.isRequired ? '是' : '否' }}</p>
                </div>
                <button class="icon-btn danger" @click="handleDeleteNode(node)">
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="3 6 5 6 21 6"/>
                    <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                  </svg>
                </button>
              </div>
              <div v-if="workflowNodes.length === 0" class="empty-state small">
                <p>暂无节点配置</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 审批通过弹窗 ==================== -->
    <van-popup v-model:show="showApprovalDialog" position="bottom" round>
      <div class="dialog-content">
        <h3 class="dialog-title">审批通过</h3>
        <div class="dialog-body">
          <textarea
            v-model="approvalComment"
            class="input"
            rows="3"
            placeholder="请输入审批意见（可选）"
          ></textarea>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-outline" @click="showApprovalDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleApprove">确认通过</button>
        </div>
      </div>
    </van-popup>

    <!-- ==================== 审批拒绝弹窗 ==================== -->
    <van-popup v-model:show="showRejectDialog" position="bottom" round>
      <div class="dialog-content">
        <h3 class="dialog-title">审批拒绝</h3>
        <div class="dialog-body">
          <textarea
            v-model="approvalComment"
            class="input"
            rows="3"
            placeholder="请输入拒绝原因"
          ></textarea>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-outline" @click="showRejectDialog = false">取消</button>
          <button class="btn btn-danger" @click="handleReject">确认拒绝</button>
        </div>
      </div>
    </van-popup>

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

    <!-- ==================== 节点编辑弹窗 ==================== -->
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
              是否必填
            </label>
          </div>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-outline" @click="showNodeDialog = false">取消</button>
          <button class="btn btn-primary" @click="handleSaveNode">保存</button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.approval-page {
  padding: 16px;
  min-height: 100vh;
  background: #f5f5f5;
}

.page-header {
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

.tab-section {
  background: #fff;
  border-radius: 8px;
  padding: 4px;
  margin-bottom: 16px;
}

.tab-buttons {
  display: flex;
  gap: 4px;
}

.tab-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 14px;
  color: #666;
  cursor: pointer;
  transition: all 0.2s;
}

.tab-btn.active {
  background: #0D6E6E;
  color: #fff;
}

.tab-btn .badge {
  background: #EF4444;
  color: #fff;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 10px;
}

.tab-content {
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.content-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

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

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #f0f0f0;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: 12px;
}

.loading-spinner.small {
  width: 20px;
  height: 20px;
  border-width: 2px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 请求卡片 */
.request-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.request-card {
  border: 1px solid #eee;
  border-radius: 8px;
  padding: 12px;
  background: #fafafa;
}

.request-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.request-type {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.request-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}

.request-status.warning {
  background: #FFF7E6;
  color: #FF976A;
}

.request-status.success {
  background: #E6FFF2;
  color: #07C160;
}

.request-status.danger {
  background: #FFF0F0;
  color: #F56C6C;
}

.request-status.default {
  background: #F5F5F5;
  color: #999;
}

.request-info p {
  margin: 4px 0;
  font-size: 13px;
  color: #666;
}

.request-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

/* 按钮样式 */
.btn {
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

.btn-danger {
  background: #F56C6C;
  color: #fff;
}

.btn-outline {
  background: transparent;
  border: 1px solid #ddd;
  color: #666;
}

.btn-outline:hover {
  border-color: #0D6E6E;
  color: #0D6E6E;
}

/* 分页 */
.table-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f0f0;
}

.text-gray-500 {
  color: #999;
  font-size: 13px;
}

.pagination {
  display: flex;
  gap: 8px;
}

.page-btn {
  padding: 6px 12px;
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
}

.page-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* 工作流布局 */
.workflow-layout {
  display: flex;
  gap: 16px;
  min-height: 400px;
}

.workflow-sidebar {
  width: 280px;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  flex-shrink: 0;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.sidebar-header h3 {
  font-size: 14px;
  font-weight: 600;
  margin: 0;
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
  padding: 10px;
  border: 1px solid #eee;
  border-radius: 6px;
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
  gap: 2px;
}

.workflow-name {
  font-size: 13px;
  font-weight: 500;
  color: #333;
}

.workflow-type {
  font-size: 11px;
  color: #999;
}

.workflow-item-actions {
  display: flex;
  gap: 4px;
}

.icon-btn {
  padding: 4px;
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

.icon-btn.danger:hover {
  background: #FFF0F0;
  color: #F56C6C;
}

/* 节点配置 */
.node-config {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
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
  padding: 10px;
  border: 1px solid #eee;
  border-radius: 6px;
}

.node-order {
  width: 24px;
  height: 24px;
  background: #0D6E6E;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.node-info {
  flex: 1;
}

.node-info p {
  margin: 2px 0;
  font-size: 12px;
  color: #666;
}

.empty-state.small {
  padding: 20px;
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

textarea.input {
  resize: none;
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
