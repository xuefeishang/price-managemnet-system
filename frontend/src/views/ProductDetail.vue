<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getProduct, getProductPriceHistory, getCurrentPrice } from '@/api/products'
import { usePermission, Permission } from '@/composables/usePermission'
import type { Product, PriceHistory, Price } from '@/types'

// 注册 ECharts 组件
use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const product = ref<Product | null>(null)
const currentPrice = ref<Price | null>(null)
const priceHistory = ref<PriceHistory[]>([])
const loading = ref(false)

// 判断是否为PC布局
const isPCLayout = computed(() => {
  if (typeof window !== 'undefined') {
    return window.innerWidth >= 1024
  }
  return false
})

// 图表配置
const chartOptions = computed(() => {
  // 按时间排序
  const sortedHistory = [...priceHistory.value].sort((a, b) =>
    new Date(a.changedTime).getTime() - new Date(b.changedTime).getTime()
  )

  const dates = sortedHistory.map(h => new Date(h.changedTime).toLocaleDateString('zh-CN'))
  const prices = sortedHistory.map(h => h.newPrice ? Number(h.newPrice) : null)

  return {
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const data = params[0]
        if (data.value != null) {
          return `${data.name}<br/>价格: ¥${data.value}`
        }
        return ''
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: {
        fontSize: 11,
        color: '#666'
      }
    },
    yAxis: {
      type: 'value',
      name: '价格(元)',
      nameTextStyle: {
        fontSize: 11,
        color: '#666'
      },
      axisLabel: {
        fontSize: 11,
        color: '#666'
      },
      splitLine: {
        lineStyle: {
          color: '#eee',
          type: 'dashed'
        }
      }
    },
    series: [{
      type: 'line',
      data: prices,
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: {
        color: '#0D6E6E',
        width: 2
      },
      itemStyle: {
        color: '#0D6E6E'
      },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(13, 110, 110, 0.3)' },
            { offset: 1, color: 'rgba(13, 110, 110, 0.05)' }
          ]
        }
      }
    }]
  }
})

const loadProduct = async () => {
  const id = route.params.id as string
  if (!id) {
    router.push('/home')
    return
  }

  loading.value = true
  try {
    const productResponse = await getProduct(parseInt(id))
    product.value = productResponse.data

    // 获取当前价格
    const priceResponse = await getCurrentPrice(parseInt(id))
    currentPrice.value = priceResponse.data || null

    // 获取价格历史
    const historyResponse = await getProductPriceHistory(parseInt(id))
    priceHistory.value = historyResponse.data || []
  } catch (error) {
    console.error('Failed to load product:', error)
  } finally {
    loading.value = false
  }
}

const editProduct = () => {
  router.push(`/product-edit/${product.value?.id}`)
}

const goBack = () => {
  router.push('/products')
}

// 底部标签栏导航（移动端）
const activeTab = ref('products')

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

onMounted(() => {
  loadProduct()
})
</script>

<template>
  <div class="product-detail-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-detail" v-if="!loading && product">
        <div class="page-header-pc">
          <button class="back-btn-pc" @click="goBack">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
            返回
          </button>
          <h1 class="page-title-pc">产品详情</h1>
          <button class="btn-edit-pc" @click="editProduct" v-if="hasPermission(Permission.PRODUCT_EDIT)">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
            编辑产品
          </button>
        </div>

        <div class="detail-grid-pc">
          <!-- 左侧产品信息 -->
          <div class="detail-main-pc">
            <div class="product-card-pc">
              <h2 class="product-name-pc">{{ product.name }}</h2>
              <span class="product-code-pc">{{ product.code }}</span>
              <div class="product-status-pc" :class="product.status?.toLowerCase()">
                {{ product.status === 'ACTIVE' ? '展示' : '隐藏' }}
              </div>
            </div>

            <div class="info-card-pc">
              <div class="info-label-pc">价格信息</div>
              <div class="price-row-pc" v-if="currentPrice?.originalPrice">
                <span>原价</span>
                <span class="price-original">¥{{ currentPrice.originalPrice }}</span>
              </div>
              <div class="price-row-pc" v-if="currentPrice?.currentPrice">
                <span>现价</span>
                <span class="price-current">¥{{ currentPrice.currentPrice }}</span>
              </div>
              <div class="price-row-pc" v-if="currentPrice?.costPrice">
                <span>成本价</span>
                <span>¥{{ currentPrice.costPrice }}</span>
              </div>
            </div>

            <div class="info-card-pc">
              <div class="info-label-pc">产品信息</div>
              <div class="info-row-pc">
                <span class="info-key">分类</span>
                <span class="info-value">{{ product.category?.name || '未分类' }}</span>
              </div>
              <div class="info-row-pc">
                <span class="info-key">规格</span>
                <span class="info-value">{{ product.specs || '-' }}</span>
              </div>
              <div class="info-row-pc">
                <span class="info-key">描述</span>
                <span class="info-value">{{ product.description || '-' }}</span>
              </div>
            </div>
          </div>

          <!-- 右侧价格历史 -->
          <div class="detail-sidebar-pc">
            <!-- 价格走势图 -->
            <div class="chart-card-pc" v-if="priceHistory.length > 1">
              <h3 class="chart-title-pc">价格趋势</h3>
              <v-chart class="price-chart" :option="chartOptions" autoresize />
            </div>

            <!-- 价格历史记录 -->
            <div class="history-card-pc" v-if="priceHistory.length > 0">
              <h3 class="history-title-pc">价格历史记录</h3>
              <div class="history-list-pc">
                <div v-for="history in priceHistory.slice().reverse().slice(0, 10)" :key="history.id" class="history-item-pc">
                  <div class="history-header">
                    <span class="history-type" :class="history.changeType?.toLowerCase()">
                      {{ history.changeType === 'CREATE' ? '创建' : history.changeType === 'UPDATE' ? '更新' : '删除' }}
                    </span>
                    <span class="history-time">{{ new Date(history.changedTime).toLocaleString() }}</span>
                  </div>
                  <div class="history-price" v-if="history.newPrice">
                    <span v-if="history.oldPrice" class="old-price">¥{{ history.oldPrice }}</span>
                    <span class="arrow">→</span>
                    <span class="new-price">¥{{ history.newPrice }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 移动端布局 ==================== -->
    <template v-else>
      <!-- 顶部导航栏 -->
      <header class="navbar">
        <div class="navbar-left">
          <button class="back-btn" @click="goBack">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </button>
          <h1 class="navbar-title">产品详情</h1>
        </div>
        <div class="navbar-right">
          <button class="nav-icon-btn" @click="editProduct" v-if="hasPermission(Permission.PRODUCT_EDIT)">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
              <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
            </svg>
          </button>
        </div>
      </header>

      <!-- 主内容区 -->
      <main class="content" v-if="!loading && product">
        <!-- 产品卡片 -->
        <div class="product-card">
          <div class="product-header">
            <div class="product-basic">
              <h2 class="product-name">{{ product.name }}</h2>
              <span class="product-code">{{ product.code }}</span>
            </div>
            <div class="product-status" :class="product.status?.toLowerCase()">
              {{ product.status === 'ACTIVE' ? '展示' : '隐藏' }}
            </div>
          </div>

          <div class="price-section" v-if="currentPrice?.originalPrice || currentPrice?.currentPrice">
            <div class="price-row" v-if="currentPrice?.originalPrice">
              <span class="price-label">原价</span>
              <span class="price-value original">¥{{ currentPrice.originalPrice }}</span>
            </div>
            <div class="price-row" v-if="currentPrice?.currentPrice">
              <span class="price-label">现价</span>
              <span class="price-value current">¥{{ currentPrice.currentPrice }}</span>
            </div>
            <div class="price-row" v-if="currentPrice?.costPrice">
              <span class="price-label">成本价</span>
              <span class="price-value cost">¥{{ currentPrice.costPrice }}</span>
            </div>
          </div>
        </div>

        <!-- 产品信息 -->
        <div class="info-card">
          <div class="card-label">产品信息</div>
          <div class="info-row">
            <span class="info-label">分类</span>
            <span class="info-value">{{ product.category?.name || '未分类' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">规格</span>
            <span class="info-value">{{ product.specs || '-' }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">描述</span>
            <span class="info-value">{{ product.description || '-' }}</span>
          </div>
        </div>

        <!-- 价格历史 -->
        <div class="history-card" v-if="priceHistory.length > 0">
          <div class="card-label">价格历史记录</div>
          <!-- 价格走势图（移动端） -->
          <div class="chart-wrapper-mobile" v-if="priceHistory.length > 1">
            <v-chart class="price-chart-mobile" :option="chartOptions" autoresize />
          </div>
          <div class="history-list">
            <div v-for="history in priceHistory.slice().reverse().slice(0, 10)" :key="history.id" class="history-item">
              <div class="history-main">
                <span class="history-type" :class="history.changeType?.toLowerCase()">
                  {{ history.changeType === 'CREATE' ? '创建' : history.changeType === 'UPDATE' ? '更新' : '删除' }}
                </span>
                <span class="history-time">{{ new Date(history.changedTime).toLocaleString() }}</span>
              </div>
              <div class="history-price" v-if="history.newPrice">
                <span v-if="history.oldPrice" class="old-price">¥{{ history.oldPrice }}</span>
                <span class="arrow">→</span>
                <span class="new-price">¥{{ history.newPrice }}</span>
              </div>
            </div>
          </div>
        </div>
      </main>

      <!-- 加载状态 -->
      <main class="loading-state" v-else-if="loading">
        <div class="loading-spinner"></div>
      </main>

      <!-- 底部标签栏 -->
      <footer class="tab-bar">
        <button class="tab-item" @click="switchTab('home')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          </svg>
          <span class="tab-label">首页</span>
        </button>
        <button class="tab-item active" @click="switchTab('products')">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M16.5 9.4l-9-5.19"/>
            <path d="M21 16V8l-7-4-7 4v8l7 4 7-4z"/>
          </svg>
          <span class="tab-label">产品</span>
        </button>
        <button class="tab-item" @click="switchTab('import')">
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

.product-detail-page {
  min-height: 100vh;
  background-color: #FAFAFA;
}

/* ==================== PC布局 ==================== */
.pc-detail {
  padding: 32px;
}

/* 价格图表 */
.chart-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
  margin-bottom: 24px;
}

.chart-title-pc {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  color: #888888;
  letter-spacing: 2px;
  text-transform: uppercase;
  margin: 0 0 16px 0;
}

.price-chart {
  width: 100%;
  height: 280px;
}

/* 移动端图表 */
.chart-wrapper-mobile {
  margin-bottom: 16px;
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
}

.price-chart-mobile {
  width: 100%;
  height: 200px;
}

.page-header-pc {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 32px;
}

.back-btn-pc {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  cursor: pointer;
}

.back-btn-pc:hover {
  background: #F5F5F5;
}

.page-title-pc {
  flex: 1;
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.btn-edit-pc {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-edit-pc:hover {
  background: #0D8A8A;
}

.detail-grid-pc {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 24px;
}

.detail-main-pc {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.product-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.product-name-pc {
  font-family: 'Inter', sans-serif;
  font-size: 24px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 8px 0;
}

.product-code-pc {
  font-family: 'JetBrains Mono', monospace;
  font-size: 14px;
  color: #888888;
  display: block;
  margin-bottom: 16px;
}

.product-status-pc {
  display: inline-block;
  padding: 6px 12px;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
}

.product-status-pc.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.product-status-pc.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.info-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.info-label-pc {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  color: #888888;
  letter-spacing: 2px;
  text-transform: uppercase;
  margin-bottom: 16px;
}

.price-row-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #F5F5F5;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
}

.price-row-pc:last-child {
  border-bottom: none;
}

.price-original {
  text-decoration: line-through;
  color: #888888;
}

.price-current {
  font-size: 24px;
  font-weight: 600;
  color: #0D6E6E;
}

.info-row-pc {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #F5F5F5;
}

.info-row-pc:last-child {
  border-bottom: none;
}

.info-key {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
}

.info-value {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  max-width: 60%;
  text-align: right;
}

.detail-sidebar-pc {
  position: sticky;
  top: 96px;
  height: fit-content;
}

.history-card-pc {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.history-title-pc {
  font-family: 'Inter', sans-serif;
  font-size: 16px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0 0 16px 0;
}

.history-list-pc {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item-pc {
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.history-type {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.history-type.create {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.history-type.update {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.history-type.delete {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.history-time {
  font-size: 12px;
  color: #888888;
}

.history-price {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.old-price {
  color: #888888;
  text-decoration: line-through;
}

.arrow {
  color: #888888;
}

.new-price {
  color: #0D6E6E;
  font-weight: 600;
}

/* ==================== 移动端布局 ==================== */
.navbar {
  height: 56px;
  background: #FFFFFF;
  border-bottom: 1px solid #E5E5E5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #1A1A1A;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.back-btn:hover {
  background: #F5F5F5;
}

.navbar-title {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 20px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  color: #0D6E6E;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.content {
  flex: 1;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-bottom: 100px;
}

.product-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.product-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.product-basic {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-name {
  font-family: 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #1A1A1A;
  margin: 0;
}

.product-code {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #888888;
}

.product-status {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.product-status.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.product-status.inactive {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.price-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price-label {
  font-size: 14px;
  color: #666666;
}

.price-value {
  font-size: 14px;
  font-weight: 500;
}

.price-value.original {
  color: #888888;
  text-decoration: line-through;
}

.price-value.current {
  color: #0D6E6E;
  font-size: 18px;
  font-weight: 600;
}

.info-card,
.history-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.card-label {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  color: #888888;
  letter-spacing: 2px;
  text-transform: uppercase;
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #F5F5F5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 14px;
  color: #666666;
}

.info-value {
  font-size: 14px;
  color: #1A1A1A;
  max-width: 60%;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  padding: 12px;
  background: #FAFAFA;
  border-radius: 8px;
}

.history-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.history-type {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.history-type.create {
  background: rgba(16, 185, 129, 0.1);
  color: #10B981;
}

.history-type.update {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.history-type.delete {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}

.history-time {
  font-size: 12px;
  color: #888888;
}

.history-price {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.old-price {
  color: #888888;
  text-decoration: line-through;
}

.new-price {
  color: #0D6E6E;
  font-weight: 600;
}

.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
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
  .pc-detail {
    display: none;
  }
}
</style>
