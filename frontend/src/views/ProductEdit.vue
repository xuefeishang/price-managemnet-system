<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getProduct, createProduct, updateProduct, getCurrentPrice, addProductPrice } from '@/api/products'
import { getCategories } from '@/api/categories'
import { getOrigins } from '@/api/origins'
import { getCustomers } from '@/api/customers'
import { usePermission, Permission } from '@/composables/usePermission'
import type { ProductCategory, Price, Origin, Customer } from '@/types'

const route = useRoute()
const router = useRouter()
const { hasPermission } = usePermission()

const productId = route.params.id as string
const isEdit = !!productId

const loading = ref(false)
const saving = ref(false)
const currentPriceId = ref<number | null>(null)

// 判断是否为PC布局
const isPCLayout = computed(() => {
  if (typeof window !== 'undefined') {
    return window.innerWidth >= 1024
  }
  return false
})

// 表单数据
const form = reactive({
  name: '',
  code: '',
  categoryId: '',
  status: 'ACTIVE',
  specs: '',
  description: '',
  remark: '',
  originalPrice: undefined as number | undefined,
  price: undefined as number | undefined,
  costPrice: undefined as number | undefined
})

// 分类数据
const categories = ref<ProductCategory[]>([])

// 产地和客户数据
const origins = ref<Origin[]>([])
const customers = ref<Customer[]>([])

// 选中的产地和客户（多选）
const selectedOriginIds = ref<number[]>([])
const selectedCustomerIds = ref<number[]>([])

// 加载产品
const loadProduct = async () => {
  if (!productId) return

  loading.value = true
  try {
    const response = await getProduct(parseInt(productId))
    const product = response.data
    Object.assign(form, {
      name: product.name,
      code: product.code,
      categoryId: product.category?.id?.toString() || '',
      status: product.status,
      specs: product.specs || '',
      description: product.description || '',
      remark: product.remark || ''
    })

    // 解析产地和客户
    if (product.originIds) {
      try {
        selectedOriginIds.value = JSON.parse(product.originIds)
      } catch (e) {
        selectedOriginIds.value = []
      }
    }
    if (product.customerIds) {
      try {
        selectedCustomerIds.value = JSON.parse(product.customerIds)
      } catch (e) {
        selectedCustomerIds.value = []
      }
    }

    // 获取当前价格
    const priceResponse = await getCurrentPrice(parseInt(productId))
    if (priceResponse.data) {
      const price = priceResponse.data as Price
      currentPriceId.value = price.id
      form.originalPrice = price.originalPrice
      form.price = price.currentPrice
      form.costPrice = price.costPrice
    }
  } catch (error) {
    console.error('Failed to load product:', error)
  } finally {
    loading.value = false
  }
}

// 加载分类
const loadCategories = async () => {
  try {
    const response = await getCategories()
    categories.value = response.data || []
  } catch (error) {
    console.error('Failed to load categories:', error)
  }
}

// 加载产地和客户
const loadOriginsAndCustomers = async () => {
  try {
    const [originsRes, customersRes] = await Promise.all([
      getOrigins('ACTIVE'),
      getCustomers('ACTIVE')
    ])
    origins.value = originsRes.data || []
    customers.value = customersRes.data || []
  } catch (error) {
    console.error('Failed to load origins and customers:', error)
  }
}

// 保存
const handleSave = async () => {
  if (!form.name.trim()) {
    showToast('请输入产品名称')
    return
  }
  if (!form.code.trim()) {
    showToast('请输入产品编码')
    return
  }

  saving.value = true
  try {
    const productData = {
      name: form.name,
      code: form.code,
      categoryId: form.categoryId ? parseInt(form.categoryId) : null,
      status: form.status,
      specs: form.specs,
      description: form.description,
      remark: form.remark,
      originIds: JSON.stringify(selectedOriginIds.value),
      customerIds: JSON.stringify(selectedCustomerIds.value)
    }

    let savedProductId: number

    if (isEdit) {
      const response = await updateProduct(parseInt(productId), productData)
      savedProductId = parseInt(productId)
    } else {
      const response = await createProduct(productData)
      savedProductId = response.data.id
    }

    // 如果填写了价格信息，保存价格
    if (form.price !== undefined && form.price !== null) {
      const priceData: Partial<Price> = {
        currentPrice: form.price,
        originalPrice: form.originalPrice,
        costPrice: form.costPrice
      }
      await addProductPrice(savedProductId, priceData as Price)
    }

    showToast(isEdit ? '产品更新成功' : '产品创建成功')
    router.push('/products')
  } catch (error: any) {
    console.error('Failed to save product:', error)
    showToast(error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 返回
const goBack = () => {
  router.push('/products')
}

onMounted(() => {
  loadCategories()
  loadOriginsAndCustomers()
  loadProduct()
})
</script>

<template>
  <div class="product-edit-page">
    <!-- ==================== PC布局 ==================== -->
    <template v-if="isPCLayout">
      <div class="pc-edit">
        <!-- 页面标题区 -->
        <div class="pc-header">
          <div class="header-content">
            <button class="back-button" @click="goBack">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="15 18 9 12 15 6"/>
              </svg>
            </button>
            <div class="header-text">
              <h1 class="page-title-pc">{{ isEdit ? '编辑产品' : '新建产品' }}</h1>
              <p class="page-subtitle">{{ isEdit ? '修改产品信息与价格' : '创建新产品并设置初始价格' }}</p>
            </div>
          </div>
        </div>

        <!-- 表单内容 -->
        <div class="pc-content" v-if="!loading">
          <!-- 基本信息卡片 -->
          <div class="form-card">
            <div class="card-header">
              <div class="card-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
                </svg>
              </div>
              <span class="card-title">基本信息</span>
            </div>

            <div class="form-grid">
              <div class="form-group">
                <label class="form-label">
                  产品名称
                  <span class="required">*</span>
                </label>
                <input
                  v-model="form.name"
                  type="text"
                  class="form-input"
                  placeholder="请输入产品名称"
                />
              </div>

              <div class="form-group">
                <label class="form-label">
                  产品编码
                  <span class="required">*</span>
                </label>
                <input
                  v-model="form.code"
                  type="text"
                  class="form-input"
                  placeholder="请输入产品编码"
                />
              </div>

              <div class="form-group">
                <label class="form-label">产品分类</label>
                <select v-model="form.categoryId" class="form-select">
                  <option value="">请选择分类</option>
                  <option v-for="category in categories" :key="category.id" :value="category.id">
                    {{ category.name }}
                  </option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label">产品规格</label>
                <input
                  v-model="form.specs"
                  type="text"
                  class="form-input"
                  placeholder="如：500g/袋"
                />
              </div>
            </div>

            <!-- 产地多选 -->
            <div class="form-row-full" v-if="origins.length > 0">
              <div class="form-group">
                <label class="form-label">产地</label>
                <div class="checkbox-group">
                  <label
                    v-for="origin in origins"
                    :key="origin.id"
                    class="checkbox-label"
                  >
                    <input
                      type="checkbox"
                      :value="origin.id"
                      v-model="selectedOriginIds"
                    />
                    <span class="checkbox-text">{{ origin.name }}</span>
                  </label>
                </div>
              </div>
            </div>

            <!-- 客户多选 -->
            <div class="form-row-full" v-if="customers.length > 0">
              <div class="form-group">
                <label class="form-label">客户</label>
                <div class="checkbox-group">
                  <label
                    v-for="customer in customers"
                    :key="customer.id"
                    class="checkbox-label"
                  >
                    <input
                      type="checkbox"
                      :value="customer.id"
                      v-model="selectedCustomerIds"
                    />
                    <span class="checkbox-text">{{ customer.name }}</span>
                  </label>
                </div>
              </div>
            </div>

            <div class="form-row-full">
              <div class="form-group">
                <label class="form-label">产品描述</label>
                <textarea
                  v-model="form.description"
                  class="form-textarea"
                  placeholder="请输入产品描述"
                  rows="2"
                ></textarea>
              </div>
            </div>

            <div class="form-row-full">
              <div class="form-group">
                <label class="form-label">显示状态</label>
                <div class="status-toggle">
                  <button
                    class="status-btn"
                    :class="{ active: form.status === 'ACTIVE' }"
                    @click="form.status = 'ACTIVE'"
                  >
                    <span class="status-dot active"></span>
                    启用
                  </button>
                  <button
                    class="status-btn"
                    :class="{ active: form.status === 'INACTIVE' }"
                    @click="form.status = 'INACTIVE'"
                  >
                    <span class="status-dot inactive"></span>
                    停用
                  </button>
                </div>
                <span class="status-hint">控制产品是否在首页价格列表中显示</span>
              </div>
            </div>
          </div>

          <!-- 价格设置卡片 -->
          <div class="form-card">
            <div class="card-header">
              <div class="card-icon price-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <line x1="12" y1="1" x2="12" y2="23"/>
                  <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
                </svg>
              </div>
              <span class="card-title">价格设置</span>
            </div>

            <div class="price-grid">
              <div class="price-main">
                <label class="form-label">当前售价</label>
                <div class="price-input-large">
                  <span class="price-unit">¥</span>
                  <input
                    v-model.number="form.price"
                    type="number"
                    class="price-input"
                    placeholder="0.00"
                  />
                </div>
              </div>

              <div class="price-secondary">
                <div class="form-group">
                  <label class="form-label">原价（划线价）</label>
                  <div class="price-input-wrapper">
                    <span class="price-unit">¥</span>
                    <input
                      v-model.number="form.originalPrice"
                      type="number"
                      class="form-input"
                      placeholder="0.00"
                    />
                  </div>
                </div>

                <div class="form-group">
                  <label class="form-label">成本价</label>
                  <div class="price-input-wrapper">
                    <span class="price-unit">¥</span>
                    <input
                      v-model.number="form.costPrice"
                      type="number"
                      class="form-input"
                      placeholder="0.00"
                    />
                  </div>
                </div>
              </div>
            </div>

            <div class="price-hint">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
              <span>价格信息为可选填写项，可在创建产品后单独添加</span>
            </div>
          </div>

          <!-- 备注卡片 -->
          <div class="form-card">
            <div class="card-header">
              <div class="card-icon remark-icon">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
              </div>
              <span class="card-title">备注说明</span>
            </div>

            <div class="form-group">
              <textarea
                v-model="form.remark"
                class="form-textarea"
                placeholder="请输入备注信息（可选）"
                rows="3"
              ></textarea>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="form-actions">
            <button class="btn-cancel" @click="goBack">
              取消
            </button>
            <button class="btn-save" @click="handleSave" :disabled="saving">
              <svg v-if="!saving" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/>
                <polyline points="17 21 17 13 7 13 7 21"/>
                <polyline points="7 3 7 8 15 8"/>
              </svg>
              <span v-if="saving" class="btn-spinner"></span>
              {{ saving ? '保存中...' : '保存产品' }}
            </button>
          </div>
        </div>

        <div class="loading-state" v-else>
          <div class="loading-spinner"></div>
          <span>加载中...</span>
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
          <h1 class="navbar-title">{{ isEdit ? '编辑产品' : '新建产品' }}</h1>
        </div>
        <button class="save-btn" @click="handleSave" :disabled="saving">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </header>

      <!-- 主内容区 -->
      <main class="content" v-if="!loading">
        <!-- 产品信息卡片 -->
        <div class="form-card">
          <div class="card-header">
            <div class="card-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
              </svg>
            </div>
            <span class="card-title">基本信息</span>
          </div>

          <div class="form-group">
            <label class="form-label">
              产品名称
              <span class="required">*</span>
            </label>
            <input
              v-model="form.name"
              type="text"
              class="form-input"
              placeholder="请输入产品名称"
            />
          </div>

          <div class="form-group">
            <label class="form-label">
              产品编码
              <span class="required">*</span>
            </label>
            <input
              v-model="form.code"
              type="text"
              class="form-input"
              placeholder="请输入产品编码"
            />
          </div>

          <div class="form-group">
            <label class="form-label">产品分类</label>
            <select v-model="form.categoryId" class="form-select">
              <option value="">请选择分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">产品规格</label>
            <input
              v-model="form.specs"
              type="text"
              class="form-input"
              placeholder="如：500g/袋"
            />
          </div>

          <!-- 产地多选 -->
          <div class="form-group" v-if="origins.length > 0">
            <label class="form-label">产地</label>
            <div class="checkbox-group">
              <label
                v-for="origin in origins"
                :key="origin.id"
                class="checkbox-label"
              >
                <input
                  type="checkbox"
                  :value="origin.id"
                  v-model="selectedOriginIds"
                />
                <span class="checkbox-text">{{ origin.name }}</span>
              </label>
            </div>
          </div>

          <!-- 客户多选 -->
          <div class="form-group" v-if="customers.length > 0">
            <label class="form-label">客户</label>
            <div class="checkbox-group">
              <label
                v-for="customer in customers"
                :key="customer.id"
                class="checkbox-label"
              >
                <input
                  type="checkbox"
                  :value="customer.id"
                  v-model="selectedCustomerIds"
                />
                <span class="checkbox-text">{{ customer.name }}</span>
              </label>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">产品描述</label>
            <textarea
              v-model="form.description"
              class="form-textarea"
              placeholder="请输入产品描述"
              rows="3"
            ></textarea>
          </div>

          <div class="form-group">
            <label class="form-label">显示状态</label>
            <div class="status-toggle">
              <button
                class="status-btn"
                :class="{ active: form.status === 'ACTIVE' }"
                @click="form.status = 'ACTIVE'"
              >
                <span class="status-dot active"></span>
                展示
              </button>
              <button
                class="status-btn"
                :class="{ active: form.status === 'INACTIVE' }"
                @click="form.status = 'INACTIVE'"
              >
                <span class="status-dot inactive"></span>
                隐藏
              </button>
            </div>
          </div>
        </div>

        <!-- 价格信息卡片 -->
        <div class="form-card">
          <div class="card-header">
            <div class="card-icon price-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="12" y1="1" x2="12" y2="23"/>
                <path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"/>
              </svg>
            </div>
            <span class="card-title">价格设置</span>
          </div>

          <div class="form-group">
            <label class="form-label">当前售价</label>
            <div class="price-input-wrapper">
              <span class="price-unit">¥</span>
              <input
                v-model.number="form.price"
                type="number"
                class="price-input"
                placeholder="0.00"
              />
            </div>
          </div>

          <div class="form-row">
            <div class="form-group">
              <label class="form-label">原价</label>
              <div class="price-input-wrapper small">
                <span class="price-unit">¥</span>
                <input
                  v-model.number="form.originalPrice"
                  type="number"
                  class="form-input"
                  placeholder="0.00"
                />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">成本价</label>
              <div class="price-input-wrapper small">
                <span class="price-unit">¥</span>
                <input
                  v-model.number="form.costPrice"
                  type="number"
                  class="form-input"
                  placeholder="0.00"
                />
              </div>
            </div>
          </div>
        </div>

        <!-- 备注卡片 -->
        <div class="form-card">
          <div class="card-header">
            <div class="card-icon remark-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
              </svg>
            </div>
            <span class="card-title">备注说明</span>
          </div>

          <div class="form-group">
            <textarea
              v-model="form.remark"
              class="form-textarea"
              placeholder="请输入备注信息（可选）"
              rows="4"
            ></textarea>
          </div>
        </div>
      </main>

      <!-- 加载状态 -->
      <div class="loading-state" v-else>
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
    </template>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.product-edit-page {
  min-height: 100vh;
  background-color: #F5F5F5;
}

/* ==================== PC布局 ==================== */
.pc-edit {
  padding: 32px;
  max-width: 900px;
  margin: 0 auto;
}

.pc-header {
  margin-bottom: 24px;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.back-button {
  width: 40px;
  height: 40px;
  border: 1px solid #E5E5E5;
  background: #FFFFFF;
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666666;
  transition: all 150ms;
}

.back-button:hover {
  background: #F5F5F5;
  color: #1A1A1A;
}

.header-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.page-title-pc {
  font-family: 'Newsreader', Georgia, serif;
  font-size: 24px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.page-subtitle {
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #888888;
  margin: 0;
}

.pc-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 卡片样式 */
.form-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 24px;
  border: 1px solid #E5E5E5;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #F3F4F6;
}

.card-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon.price-icon {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.card-icon.remark-icon {
  background: rgba(107, 114, 128, 0.1);
  color: #6B7280;
}

.card-title {
  font-family: 'Inter', sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: #1A1A1A;
}

/* 表单网格 */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.form-row-full {
  margin-bottom: 16px;
}

.form-row-full:last-child {
  margin-bottom: 0;
}

.form-group {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

.required {
  color: #EF4444;
  margin-left: 2px;
}

.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  transition: all 150ms;
  box-sizing: border-box;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: #0D6E6E;
  box-shadow: 0 0 0 3px rgba(13, 110, 110, 0.1);
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: #9CA3AF;
}

.form-select {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%236B7280' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 36px;
}

.form-textarea {
  resize: vertical;
  min-height: 60px;
}

/* 状态切换 */
.status-toggle {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.status-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.status-btn:hover {
  border-color: #D1D5DB;
  background: #F9FAFB;
}

.status-btn.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.05);
  color: #0D6E6E;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.active {
  background: #22C55E;
}

.status-dot.inactive {
  background: #EF4444;
}

/* 多选框组 */
.checkbox-group {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: #0D6E6E;
}

.checkbox-text {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
}

.status-hint {
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #9CA3AF;
}

/* 价格网格 */
.price-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 16px;
}

.price-main {
  padding: 20px;
  background: linear-gradient(135deg, #0D6E6E 0%, #0A5555 100%);
  border-radius: 10px;
}

.price-main .form-label {
  color: rgba(255, 255, 255, 0.8);
  font-size: 13px;
}

.price-input-large {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border-radius: 8px;
  overflow: hidden;
}

.price-unit {
  padding: 12px 0 12px 14px;
  color: #0D6E6E;
  font-weight: 600;
  font-size: 18px;
}

.price-input {
  flex: 1;
  padding: 12px 14px 12px 4px;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 28px;
  font-weight: 600;
  color: #1A1A1A;
}

.price-input:focus {
  outline: none;
}

.price-input::placeholder {
  color: #D1D5DB;
  font-weight: 400;
}

.price-secondary {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.price-secondary .form-group {
  margin-bottom: 0;
}

.price-input-wrapper {
  display: flex;
  align-items: center;
  background: #F9FAFB;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  overflow: hidden;
}

.price-input-wrapper .price-unit {
  padding: 10px 0 10px 12px;
  color: #6B7280;
  font-size: 14px;
  font-weight: 500;
}

.price-input-wrapper .form-input {
  padding: 10px 12px 10px 4px;
  border: none;
  background: transparent;
  font-size: 15px;
}

.price-input-wrapper .form-input:focus {
  box-shadow: none;
}

.price-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  background: #F9FAFB;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 12px;
  color: #6B7280;
}

/* 操作按钮 */
.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 8px;
}

.btn-save {
  padding: 12px 24px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: all 150ms;
}

.btn-save:hover:not(:disabled) {
  background: #0D8A8A;
}

.btn-save:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-cancel {
  padding: 12px 24px;
  background: #FFFFFF;
  color: #666666;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.btn-cancel:hover {
  background: #F5F5F5;
  border-color: #D1D5DB;
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #FFFFFF;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 加载状态 */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px;
  gap: 16px;
  color: #666666;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid #E5E5E5;
  border-top-color: #0D6E6E;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ==================== 移动端布局 ==================== */
@media (max-width: 1024px) {
  .pc-edit {
    display: none;
  }
}

.product-edit-page {
  display: flex;
  flex-direction: column;
  max-width: 100%;
}

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
  font-size: 18px;
  font-weight: 500;
  color: #1A1A1A;
  margin: 0;
}

.save-btn {
  padding: 8px 16px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 150ms;
}

.save-btn:hover:not(:disabled) {
  background: #0D8A8A;
}

.save-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.content {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 100px;
}

.form-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 20px;
  border: 1px solid #E5E5E5;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #F3F4F6;
}

.card-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
  display: flex;
  align-items: center;
  justify-content: center;
}

.card-icon.price-icon {
  background: rgba(245, 158, 11, 0.1);
  color: #F59E0B;
}

.card-icon.remark-icon {
  background: rgba(107, 114, 128, 0.1);
  color: #6B7280;
}

.card-title {
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 600;
  color: #1A1A1A;
}

.form-group {
  margin-bottom: 14px;
}

.form-group:last-child {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}

.required {
  color: #EF4444;
  margin-left: 2px;
}

.form-input,
.form-select,
.form-textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
  transition: all 150ms;
  box-sizing: border-box;
}

.form-input:focus,
.form-select:focus,
.form-textarea:focus {
  outline: none;
  border-color: #0D6E6E;
  box-shadow: 0 0 0 3px rgba(13, 110, 110, 0.1);
}

.form-input::placeholder,
.form-textarea::placeholder {
  color: #9CA3AF;
}

.form-select {
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%236B7280' stroke-width='2'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  padding-right: 36px;
}

.form-textarea {
  resize: vertical;
  min-height: 80px;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

/* 状态切换 */
.status-toggle {
  display: flex;
  gap: 8px;
}

.status-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
}

.status-btn.active {
  border-color: #0D6E6E;
  background: rgba(13, 110, 110, 0.05);
  color: #0D6E6E;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.status-dot.active {
  background: #22C55E;
}

.status-dot.inactive {
  background: #EF4444;
}

/* 价格输入 */
.price-input-wrapper {
  display: flex;
  align-items: center;
  background: #F9FAFB;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  overflow: hidden;
}

.price-input-wrapper.small {
  width: 100%;
}

.price-unit {
  padding: 10px 0 10px 12px;
  color: #6B7280;
  font-weight: 500;
  font-size: 14px;
}

.price-input {
  flex: 1;
  padding: 10px 12px;
  border: none;
  background: transparent;
  font-family: 'Inter', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #1A1A1A;
}

.price-input:focus {
  outline: none;
}

.price-input::placeholder {
  color: #D1D5DB;
  font-weight: 400;
}

@media (max-width: 480px) {
  .content {
    padding: 12px;
  }

  .form-card {
    padding: 16px;
    border-radius: 10px;
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 14px;
  }
}
</style>
