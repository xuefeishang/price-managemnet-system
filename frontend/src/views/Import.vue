<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { downloadTemplate, importProducts, exportProducts } from '@/api/import'

const router = useRouter()

const importing = ref(false)
const exporting = ref(false)
const importProgress = ref(0)
const selectedFileName = ref('')
const importResult = ref<{
  success: boolean
  message: string
  errorDetails?: string[]
} | null>(null)

// 响应式布局
const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const isPCLayout = computed(() => windowWidth.value >= 1024)

const handleResize = () => {
  windowWidth.value = window.innerWidth
}

const handleDownloadTemplate = async () => {
  try {
    await downloadTemplate()
    showToast('模板下载成功')
  } catch (error: any) {
    showToast('模板下载失败')
    console.error('Failed to download template:', error)
  }
}

const handleFileChange = (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    // 验证文件类型
    const allowedTypes = ['.xlsx', '.xls']
    const fileExt = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
    if (!allowedTypes.includes(fileExt)) {
      showToast('请选择Excel文件(.xlsx或.xls)')
      return
    }
    // 验证文件大小 (10MB)
    if (file.size > 10 * 1024 * 1024) {
      showToast('文件大小不能超过10MB')
      return
    }
    selectedFileName.value = file.name
    handleImport(file)
  }
}

const handleImport = async (file: File) => {
  importResult.value = null
  importing.value = true
  importProgress.value = 0

  // 模拟进度
  const progressInterval = setInterval(() => {
    if (importProgress.value < 90) {
      importProgress.value += Math.random() * 15
    }
  }, 300)

  try {
    const formData = new FormData()
    formData.append('file', file)
    await importProducts(formData)
    importProgress.value = 100

    importResult.value = {
      success: true,
      message: `文件 "${file.name}" 导入成功！`
    }
    showDialog({
      title: '导入成功',
      message: `文件 "${file.name}" 已成功导入。`
    })
  } catch (error: any) {
    importProgress.value = 0
    const errorMsg = error?.response?.data?.message || error?.message || '导入失败，请检查文件格式和数据'
    importResult.value = {
      success: false,
      message: `文件 "${file.name}" 导入失败`,
      errorDetails: [errorMsg]
    }
    showDialog({
      title: '导入失败',
      message: errorMsg
    })
  } finally {
    clearInterval(progressInterval)
    importing.value = false
  }
}

const handleExport = async () => {
  exporting.value = true
  try {
    await exportProducts()
    showToast('导出成功')
  } catch (error: any) {
    showToast(error?.response?.data?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

// 底部标签栏导航（移动端）
const activeTab = ref('import')

const switchTab = (tab: string) => {
  activeTab.value = tab
  switch (tab) {
    case 'home':
      router.push('/home')
      break
    case 'products':
      router.push('/products')
      break
    case 'import':
      router.push('/import')
      break
    case 'profile':
      router.push('/profile')
      break
  }
}
</script>

<template>
  <div class="import-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-import">
        <h1 class="page-title-pc">导入导出</h1>

        <div class="import-grid-pc">
          <!-- 导入卡片 -->
          <div class="card-pc">
            <div class="card-header-pc">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="17 8 12 3 7 8"/>
                <line x1="12" y1="3" x2="12" y2="15"/>
              </svg>
              <span>导入产品</span>
            </div>
            <p class="card-desc-pc">上传Excel文件批量导入产品和价格信息</p>
            <div class="card-actions-pc">
              <input
                type="file"
                ref="fileInput"
                accept=".xlsx,.xls"
                @change="handleFileChange"
                hidden
              />
              <button class="btn-primary-pc" @click="($refs.fileInput as HTMLInputElement).click()" :disabled="importing">
                {{ importing ? '导入中...' : '选择文件' }}
              </button>
              <button class="btn-secondary-pc" @click="handleDownloadTemplate">
                下载模板
              </button>
            </div>
          </div>

          <!-- 导出卡片 -->
          <div class="card-pc">
            <div class="card-header-pc">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
                <polyline points="7 10 12 15 17 10"/>
                <line x1="12" y1="15" x2="12" y2="3"/>
              </svg>
              <span>导出产品</span>
            </div>
            <p class="card-desc-pc">将所有产品和价格信息导出为Excel文件</p>
            <div class="card-actions-pc">
              <button class="btn-outline-pc" @click="handleExport" :disabled="exporting">
                {{ exporting ? '导出中...' : '导出数据' }}
              </button>
            </div>
          </div>
        </div>

        <!-- 注意事项 -->
        <div class="tips-card-pc">
          <h3 class="tips-title-pc">注意事项</h3>
          <ul class="tips-list-pc">
            <li><strong>模板格式：</strong>请严格按照模板格式填写，不要随意修改列格式</li>
            <li><strong>必填字段：</strong>产品编码和产品名称为必填字段</li>
            <li><strong>文件大小：</strong>单个文件大小不要超过10MB</li>
            <li><strong>导入时间：</strong>大量数据导入可能需要较长时间，请耐心等待</li>
          </ul>
        </div>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 顶部导航栏 -->
      <header class="navbar">
        <div class="navbar-left">
          <h1 class="navbar-title">导入导出</h1>
        </div>
      </header>

      <!-- 主内容区 -->
      <main class="content">
        <!-- 导入卡片 -->
        <div class="import-card">
          <div class="card-header">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="17 8 12 3 7 8"/>
              <line x1="12" y1="3" x2="12" y2="15"/>
            </svg>
            <span class="card-title">导入产品</span>
          </div>
          <p class="card-desc">上传Excel文件批量导入产品和价格信息</p>
          <input
            type="file"
            ref="fileInput"
            class="file-input"
            accept=".xlsx,.xls"
            @change="handleFileChange"
            hidden
          />
          <button
            class="primary-btn"
            @click="($refs.fileInput as HTMLInputElement).click()"
            :disabled="importing"
          >
            {{ importing ? '导入中...' : '选择文件' }}
          </button>
          <button
            class="template-btn"
            @click="handleDownloadTemplate"
          >
            下载模板
          </button>

          <!-- 导入进度 -->
          <div v-if="importing" class="import-progress">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: importProgress + '%' }"></div>
            </div>
            <span class="progress-text">{{ Math.round(importProgress) }}%</span>
          </div>

          <!-- 已选文件名 -->
          <div v-if="selectedFileName && !importing" class="selected-file">
            已选择: {{ selectedFileName }}
          </div>

          <!-- 导入结果 -->
          <div v-if="importResult" class="import-result" :class="importResult.success ? 'success' : 'error'">
            {{ importResult.message }}
          </div>
        </div>

        <!-- 导出卡片 -->
        <div class="export-card">
          <div class="card-header">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
              <polyline points="7 10 12 15 17 10"/>
              <line x1="12" y1="15" x2="12" y2="3"/>
            </svg>
            <span class="card-title">导出产品</span>
          </div>
          <p class="card-desc">将所有产品和价格信息导出为Excel文件</p>
          <button
            class="secondary-btn"
            @click="handleExport"
            :disabled="exporting"
          >
            {{ exporting ? '导出中...' : '导出数据' }}
          </button>
        </div>

        <!-- 注意事项 -->
        <div class="tips-card">
          <div class="tips-title">注意事项</div>
          <ul class="tips-list">
            <li>模板格式：请严格按照模板格式填写</li>
            <li>必填字段：产品编码和产品名称为必填字段</li>
            <li>文件大小：单个文件大小不要超过10MB</li>
            <li>导入时间：大量数据导入可能需要较长时间</li>
          </ul>
        </div>
      </main>

      <!-- 底部标签栏 -->
      <footer class="tab-bar">
        <button class="tab-item" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item active" @click="switchTab('import')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="17 8 12 3 7 8"/>
            <line x1="12" y1="3" x2="12" y2="15"/>
          </svg>
          <span class="tab-label">导入</span>
        </button>
        <button class="tab-item" @click="switchTab('profile')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/>
            <circle cx="12" cy="7" r="4"/>
          </svg>
          <span class="tab-label">我的</span>
        </button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.import-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

/* ==================== PC布局 ==================== */
.pc-import {
  padding: 32px;
  max-width: 900px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0 0 32px 0;
}

.import-grid-pc {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
  margin-bottom: 32px;
}

.card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 32px;
  border: 1px solid #E5E5E5;
}

.card-header-pc {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.card-header-pc svg {
  color: #0D6E6E;
}

.card-header-pc span {
  font-family: 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #1A1A1A;
}

.card-desc-pc {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  line-height: 1.5;
  margin: 0 0 24px 0;
}

.card-actions-pc {
  display: flex;
  gap: 12px;
}

.btn-primary-pc {
  padding: 12px 24px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary-pc:hover:not(:disabled) {
  background: #0D8A8A;
}

.btn-primary-pc:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary-pc {
  padding: 12px 24px;
  background: #FFFFFF;
  color: #0D6E6E;
  border: 1px solid #0D6E6E;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-secondary-pc:hover {
  background: rgba(13, 110, 110, 0.1);
}

.btn-outline-pc {
  padding: 12px 24px;
  background: #FFFFFF;
  color: #0D6E6E;
  border: 1px solid #0D6E6E;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-outline-pc:hover:not(:disabled) {
  background: rgba(13, 110, 110, 0.1);
}

.btn-outline-pc:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tips-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.tips-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 16px 0;
}

.tips-list-pc {
  margin: 0;
  padding-left: 20px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  line-height: 1.8;
}

.tips-list-pc li {
  margin-bottom: 8px;
}

.tips-list-pc strong {
  color: #1A1A1A;
}

/* ==================== 移动端布局 ==================== */
.navbar {
  height: 56px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
  display: flex;
  align-items: center;
  padding: 0 16px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 20px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 100px;
}

.import-card,
.export-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.card-header svg {
  color: #0D6E6E;
}

.card-title {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 500;
  color: #1A1A1A;
}

.card-desc {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  line-height: 1.4;
  margin: 0 0 16px 0;
}

.primary-btn {
  width: 100%;
  padding: 14px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.primary-btn:hover:not(:disabled) {
  background: #0D8A8A;
}

.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.secondary-btn {
  width: 100%;
  padding: 14px;
  background: #FFFFFF;
  color: #0D6E6E;
  border: 1px solid #0D6E6E;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.secondary-btn:hover:not(:disabled) {
  background: rgba(13, 110, 110, 0.1);
}

.secondary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 模板按钮 */
.template-btn {
  width: 100%;
  padding: 10px;
  margin-top: 8px;
  background: #FFFFFF;
  color: #0D6E6E;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.template-btn:hover {
  background: rgba(13, 110, 110, 0.05);
}

/* 导入进度 */
.import-progress {
  margin-top: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.progress-bar {
  flex: 1;
  height: 8px;
  background: #E5E5E5;
  border-radius: 4px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #0D6E6E, #0A5555);
  border-radius: 4px;
  transition: width 0.3s ease;
}

.progress-text {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #666666;
  min-width: 40px;
  text-align: right;
}

/* 已选文件名 */
.selected-file {
  margin-top: 12px;
  padding: 8px 12px;
  background: #F5F5F5;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #666666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 导入结果 */
.import-result {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  text-align: center;
}

.import-result.success {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.import-result.error {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

/* 注意事项卡片 */
.tips-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
}

.tips-title {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 12px;
}

.tips-list {
  margin: 0;
  padding-left: 18px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #666666;
  line-height: 1.8;
}

.tips-list li {
  margin-bottom: 4px;
}

.tab-bar {
  height: 64px;
  background: #FFFFFF;
  border-top: 1px solid #E5E5E5;
  display: flex;
  justify-content: space-around;
  align-items: center;
  padding: 0 20px;
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  max-width: 100%;
  z-index: 100;
}

.tab-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 8px 16px;
  border-radius: 8px;
  color: #AAAAAA;
}

.tab-item.active {
  color: #0D6E6E;
}

.tab-label {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 500;
}

@media (max-width: 1024px) {
  .pc-import {
    display: none;
  }
}
</style>
