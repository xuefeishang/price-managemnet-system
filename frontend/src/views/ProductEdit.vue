<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { getProduct, createProduct, updateProduct } from '@/api/products'
import { getCategories } from '@/api/categories'
import { UNIT_OPTIONS } from '@/constants/units'
import { getDictOptions, getStatusLabel, loadAllDicts, getOriginOptions, getCustomerOptions, getCustomerName } from '@/composables/useDict'
import { eventBus } from '@/utils/eventBus'
import type { ProductCategory, ProductStatus } from '@/types'

const route = useRoute()
const router = useRouter()

const productId = route.params.id as string
const isEdit = !!productId

const loading = ref(false)
const saving = ref(false)

// 判断是否为PC布局
const isPCLayout = ref(typeof window !== 'undefined' ? window.innerWidth >= 1024 : false)
const pcLayoutRef = ref<HTMLElement | null>(null)
const mobileLayoutRef = ref<HTMLElement | null>(null)

const handleResize = () => {
  isPCLayout.value = window.innerWidth >= 1024
}

// 表单数据
const form = reactive({
  code: '',
  name: '',
  categoryId: '',
  status: 'ACTIVE' as ProductStatus,
  specs: '',
  description: '',
  remark: '',
  unit: '',
  showOnHome: false,
  currency: 'CNY',
  sortOrder: 0,
  budgetPrice: '' as string | number
})

// 计量单位选项（从共享常量引入，与后端/数据库保持一致）
const unitOptions = UNIT_OPTIONS

// 计价币种选项（从字典服务获取，computed 保证缓存刷新后联动）
const currencyOptions = computed(() => getDictOptions('currency'))

// 分类数据
const categories = ref<ProductCategory[]>([])

// 产地和客户数据（从字典缓存获取，computed 保证缓存刷新后联动）
const originOptions = computed(() => getOriginOptions())
const customerOpts = computed(() => getCustomerOptions())

// 选中的产地（单选，存 dictKey）和客户（多选，存 dictKey）
const selectedOriginKey = ref<string>('')
const selectedCustomerKeys = ref<string[]>([])
const showCustomerDropdown = ref(false)
const customerDropdownRef = ref<HTMLElement | null>(null)
// const formAreaRef = ref<HTMLElement | null>(null)
const activeSection = ref('basic')

// 表单分组导航
const formSections = [
  {
    id: 'basic',
    label: '基本信息',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/></svg>'
  },
  {
    id: 'relation',
    label: '关联信息',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>'
  },
  {
    id: 'extra',
    label: '其他信息',
    icon: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>'
  }
]

// 辅助函数
const getCustomerNameByKey = (key: string) => {
  return getCustomerName(key)
}

const removeCustomer = (key: string) => {
  selectedCustomerKeys.value = selectedCustomerKeys.value.filter(k => k !== key)
}

// 滚动到指定分区
const scrollToSection = (sectionId: string) => {
  activeSection.value = sectionId
  const container = isPCLayout.value ? pcLayoutRef.value : mobileLayoutRef.value
  const el = container?.querySelector(`#section-${sectionId}`) as HTMLElement | null
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const toggleCustomerDropdown = () => {
  showCustomerDropdown.value = !showCustomerDropdown.value
}

const toggleCustomerSelection = (key: string) => {
  const idx = selectedCustomerKeys.value.indexOf(key)
  if (idx >= 0) {
    selectedCustomerKeys.value.splice(idx, 1)
  } else {
    selectedCustomerKeys.value.push(key)
  }
}

// 点击外部关闭下拉
const handleClickOutside = (event: MouseEvent) => {
  if (customerDropdownRef.value && !customerDropdownRef.value.contains(event.target as Node)) {
    showCustomerDropdown.value = false
  }
}

// 加载产品
const loadProduct = async () => {
  if (!productId) return

  loading.value = true
  try {
    const response = await getProduct(parseInt(productId))
    const product = response.data
    Object.assign(form, {
      code: product.code?.toUpperCase() || '',
      name: product.name,
      categoryId: product.category?.id?.toString() || '',
      status: product.status,
      specs: product.specs || '',
      description: product.description || '',
      remark: product.remark || '',
      unit: product.unit || '',
      showOnHome: product.showOnHome ?? false,
      currency: product.currency || 'CNY',
      sortOrder: product.sortOrder ?? 0,
      budgetPrice: product.budgetPrice ?? ''
    })

    // 解析产地（单选，dictKey）和客户（多选，dictKey）
    if (product.originIds) {
      try {
        const keys = JSON.parse(product.originIds)
        selectedOriginKey.value = keys.length > 0 ? keys[0] : ''
      } catch (e) {
        selectedOriginKey.value = ''
      }
    }
    if (product.customerIds) {
      try {
        selectedCustomerKeys.value = JSON.parse(product.customerIds)
      } catch (e) {
        selectedCustomerKeys.value = []
      }
    }
  } catch (error) {
    console.error('Failed to load product:', error)
  } finally {
    loading.value = false
    await nextTick(() => handleScroll())
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

// 浮动按钮 & 侧边导航：滚动检测
const showFloatingBar = ref(false)
let scrollTimer: ReturnType<typeof setTimeout> | null = null

const handleScroll = () => {
  // 浮动按钮：距底部判断
  const scrollTop = window.scrollY || document.documentElement.scrollTop
  const scrollHeight = document.documentElement.scrollHeight
  const clientHeight = window.innerHeight
  const distanceToBottom = scrollHeight - scrollTop - clientHeight
  showFloatingBar.value = distanceToBottom > 80

  // 侧边导航：自动选中当前可见分区（节流）
  if (!scrollTimer) {
    scrollTimer = setTimeout(() => {
      scrollTimer = null
      const container = isPCLayout.value ? pcLayoutRef.value : mobileLayoutRef.value
      if (!container) return
      const sectionIds = formSections.map(s => s.id)
      for (let i = sectionIds.length - 1; i >= 0; i--) {
        const el = container.querySelector(`#section-${sectionIds[i]}`) as HTMLElement | null
        if (el) {
          const rect = el.getBoundingClientRect()
          if (rect.top <= 120) {
            activeSection.value = sectionIds[i]
            break
          }
        }
      }
    }, 80)
  }
}

// 保存
const handleSave = async () => {
  // 校验基本信息必输项
  const requiredFields: { key: 'code' | 'name' | 'categoryId' | 'specs' | 'unit'; label: string }[] = [
    { key: 'code', label: '产品编码' },
    { key: 'name', label: '产品名称' },
    { key: 'categoryId', label: '产品分类' },
    { key: 'specs', label: '产品规格' },
    { key: 'unit', label: '计量单位' },
  ]

  for (const field of requiredFields) {
    if (!String(form[field.key]).trim()) {
      showToast(`请输入${field.label}`)
      scrollToSection('basic')
      await nextTick()
      // 尝试聚焦到对应输入框
      const section = document.getElementById('section-basic')
      if (section) {
        const allInputs = section.querySelectorAll<HTMLInputElement | HTMLSelectElement>('input, select')
        for (const input of allInputs) {
          if (!input.value.trim()) {
            input.focus()
            input.scrollIntoView({ behavior: 'smooth', block: 'center' })
            break
          }
        }
      }
      return
    }
  }

  saving.value = true
  try {
    const productData = {
      code: form.code?.toUpperCase() || undefined,
      name: form.name,
      categoryId: form.categoryId ? parseInt(form.categoryId) : undefined,
      status: form.status as ProductStatus,
      specs: form.specs,
      description: form.description,
      remark: form.remark,
      unit: form.unit || undefined,
      showOnHome: form.showOnHome,
      currency: form.currency,
      sortOrder: form.sortOrder || 0,
      budgetPrice: form.budgetPrice ? parseFloat(parseFloat(String(form.budgetPrice)).toFixed(2)) : undefined,
      originIds: selectedOriginKey.value ? JSON.stringify([selectedOriginKey.value]) : undefined,
      customerIds: selectedCustomerKeys.value.length > 0 ? JSON.stringify(selectedCustomerKeys.value) : undefined
    }

    if (isEdit) {
      await updateProduct(parseInt(productId), productData)
    } else {
      await createProduct(productData)
    }

    showToast(isEdit ? '产品更新成功' : '产品创建成功')
    eventBus.emit('product-updated', productId ? parseInt(productId) : null)
    await router.push('/products')
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
  loadAllDicts()
  loadCategories()
  loadProduct()
  document.addEventListener('click', handleClickOutside)
  window.addEventListener('scroll', handleScroll, { passive: true })
  window.addEventListener('resize', handleResize)
  nextTick(() => handleScroll())
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('scroll', handleScroll)
  window.removeEventListener('resize', handleResize)
  if (scrollTimer) clearTimeout(scrollTimer)
})
</script>

<template>
  <div class="product-edit-page">
    <!-- ==================== PC布局 ==================== -->
    <div v-show="isPCLayout" ref="pcLayoutRef">
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
              <p class="page-subtitle">{{ isEdit ? '修改产品信息' : '创建新产品' }}</p>
            </div>
          </div>
        </div>

        <!-- 主体：左侧导航 + 右侧表单 -->
        <div class="pc-body" v-if="!loading">
          <!-- 左侧导航栏 -->
          <aside class="pc-sidebar">
            <nav class="sidebar-nav">
              <a
                v-for="section in formSections"
                :key="section.id"
                class="nav-item"
                :class="{ active: activeSection === section.id }"
                href="javascript:void(0)"
                @click="scrollToSection(section.id)"
              >
                <div class="nav-icon" v-html="section.icon"></div>
                <span class="nav-label">{{ section.label }}</span>
              </a>
            </nav>
          </aside>

          <!-- 右侧表单区域 -->
          <div class="pc-form-area" ref="formAreaRef">
            <!-- 基本信息 -->
            <div class="form-section" id="section-basic">
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
                      产品编码
                      <span class="required">*</span>
                    </label>
                    <input
                      v-model="form.code"
                      type="text"
                      class="form-input"
                      placeholder="请输入产品编码"
                      :readonly="isEdit"
                    />
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
                      产品分类
                      <span class="required">*</span>
                    </label>
                    <select v-model="form.categoryId" class="form-select">
                      <option value="">请选择分类</option>
                      <option v-for="category in categories" :key="category.id" :value="category.id">
                        {{ category.name }}
                      </option>
                    </select>
                  </div>

                  <div class="form-group">
                    <label class="form-label">
                      产品规格
                      <span class="required">*</span>
                    </label>
                    <input
                      v-model="form.specs"
                      type="text"
                      class="form-input"
                      placeholder="如：500g/袋"
                    />
                  </div>

                  <div class="form-group">
                    <label class="form-label">
                      计量单位
                      <span class="required">*</span>
                    </label>
                    <select v-model="form.unit" class="form-select">
                      <option value="">请选择计量单位</option>
                      <option v-for="opt in unitOptions" :key="opt.value" :value="opt.value">
                        {{ opt.label }}
                      </option>
                    </select>
                  </div>

                  <div class="form-group">
                    <label class="form-label">计价币种</label>
                    <select v-model="form.currency" class="form-select">
                      <option v-for="opt in currencyOptions" :key="opt.value" :value="opt.value">
                        {{ opt.label }}
                      </option>
                    </select>
                  </div>

                  <div class="form-group">
                    <label class="form-label">预算价格</label>
                    <input
                      v-model="form.budgetPrice"
                      type="number"
                      class="form-input"
                      placeholder="请输入预算价格"
                      step="0.01"
                    />
                  </div>

                  <div class="form-group">
                    <label class="form-label">显示状态</label>
                    <div class="status-toggle inline">
                      <button
                        class="status-btn"
                        :class="{ active: form.status === 'ACTIVE' }"
                        @click="form.status = 'ACTIVE'"
                      >
                        <span class="status-dot active"></span>
                        {{ getStatusLabel('ACTIVE') }}
                      </button>
                      <button
                        class="status-btn"
                        :class="{ active: form.status === 'INACTIVE' }"
                        @click="form.status = 'INACTIVE'"
                      >
                        <span class="status-dot inactive"></span>
                        {{ getStatusLabel('INACTIVE') }}
                      </button>
                    </div>
                  </div>

                  <div class="form-group">
                    <label class="form-label">首页展示</label>
                    <div class="status-toggle inline">
                      <button
                        class="status-btn"
                        :class="{ active: form.showOnHome }"
                        @click="form.showOnHome = true"
                      >
                        <span class="status-dot home"></span>
                        是
                      </button>
                      <button
                        class="status-btn"
                        :class="{ active: !form.showOnHome }"
                        @click="form.showOnHome = false"
                      >
                        <span class="status-dot home-inactive"></span>
                        否
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 关联信息 -->
            <div class="form-section" id="section-relation">
              <div class="form-card">
                <div class="card-header">
                  <div class="card-icon relation-icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                      <circle cx="9" cy="7" r="4"/>
                      <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                      <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
                    </svg>
                  </div>
                  <span class="card-title">关联信息</span>
                </div>

                <div class="form-grid two-col">
                  <div class="form-group">
                    <label class="form-label">产地</label>
                    <select v-model="selectedOriginKey" class="form-select">
                      <option value="">请选择产地</option>
                      <option v-for="opt in originOptions" :key="opt.value" :value="opt.value">
                        {{ opt.label }}
                      </option>
                    </select>
                  </div>

                  <div class="form-group" v-if="customerOpts.length > 0">
                    <label class="form-label">客户信息</label>
                    <div class="multi-select-dropdown pc" ref="customerDropdownRef">
                      <div class="multi-select-trigger" @click="toggleCustomerDropdown">
                        <span class="multi-select-placeholder" v-if="selectedCustomerKeys.length === 0">请选择客户（可多选）</span>
                        <span class="multi-select-tags" v-else>
                          <span
                            v-for="key in selectedCustomerKeys"
                            :key="key"
                            class="multi-select-tag"
                          >
                            {{ getCustomerNameByKey(key) }}
                            <span class="tag-remove" @click.stop="removeCustomer(key)">×</span>
                          </span>
                        </span>
                        <svg class="dropdown-arrow" :class="{ open: showCustomerDropdown }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                          <polyline points="6 9 12 15 18 9"/>
                        </svg>
                      </div>
                      <div class="multi-select-panel" v-if="showCustomerDropdown">
                        <label
                          v-for="opt in customerOpts"
                          :key="opt.value"
                          class="multi-select-option"
                          :class="{ selected: selectedCustomerKeys.includes(opt.value) }"
                        >
                          <input
                            type="checkbox"
                            :value="opt.value"
                            :checked="selectedCustomerKeys.includes(opt.value)"
                            @click.stop="toggleCustomerSelection(opt.value)"
                          />
                          <span>{{ opt.label }}</span>
                        </label>
                      </div>
                    </div>
                  </div>
                </div>

                <div class="form-group" style="margin-top: 16px;">
                  <label class="form-label">产品描述</label>
                  <textarea
                    v-model="form.description"
                    class="form-textarea"
                    placeholder="请输入产品描述"
                    rows="4"
                  ></textarea>
                </div>
              </div>
            </div>

            <!-- 其他信息 -->
            <div class="form-section" id="section-extra">
              <div class="form-card">
                <div class="card-header">
                  <div class="card-icon extra-icon">
                    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <circle cx="12" cy="12" r="3"/>
                      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
                    </svg>
                  </div>
                  <span class="card-title">其他信息</span>
                </div>

                <div class="form-grid">
                  <div class="form-group">
                    <label class="form-label">排序号</label>
                    <input
                      v-model.number="form.sortOrder"
                      type="number"
                      class="form-input"
                      placeholder="数值越小越靠前"
                      min="0"
                    />
                  </div>
                </div>

                <div class="form-group">
                  <label class="form-label">备注说明</label>
                    <textarea
                      v-model="form.remark"
                      class="form-textarea"
                      placeholder="请输入备注信息（可选）"
                      rows="4"
                    ></textarea>
                  </div>

              </div>
            </div>
          </div>
        </div>

        <!-- 底部操作栏（原始位置） -->
        <div class="pc-actions-bar" v-if="!loading">
          <button class="btn-cancel" @click="goBack">取消</button>
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

        <!-- 浮动操作栏（未滑到底部时显示） -->
        <transition name="float-fade">
          <div class="pc-float-bar" v-if="!loading && showFloatingBar">
            <button class="btn-cancel" @click="goBack">取消</button>
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
        </transition>

        <div class="loading-state" v-if="loading">
          <div class="loading-spinner"></div>
          <span>加载中...</span>
        </div>
      </div>
    </div>

    <!-- ==================== 移动端布局 ==================== -->
    <div v-show="!isPCLayout" ref="mobileLayoutRef">
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
              :readonly="isEdit"
            />
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
              产品分类
              <span class="required">*</span>
            </label>
            <select v-model="form.categoryId" class="form-select">
              <option value="">请选择分类</option>
              <option v-for="category in categories" :key="category.id" :value="category.id">
                {{ category.name }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">
              产品规格
              <span class="required">*</span>
            </label>
            <input
              v-model="form.specs"
              type="text"
              class="form-input"
              placeholder="如：500g/袋"
            />
          </div>

          <div class="form-group">
            <label class="form-label">
              计量单位
              <span class="required">*</span>
            </label>
            <select v-model="form.unit" class="form-select">
              <option value="">请选择计量单位</option>
              <option v-for="opt in unitOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">计价币种</label>
            <select v-model="form.currency" class="form-select">
              <option v-for="opt in currencyOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label class="form-label">预算价格</label>
            <input
              v-model="form.budgetPrice"
              type="number"
              class="form-input"
              placeholder="请输入预算价格"
              step="0.01"
            />
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
                {{ getStatusLabel('ACTIVE') }}
              </button>
              <button
                class="status-btn"
                :class="{ active: form.status === 'INACTIVE' }"
                @click="form.status = 'INACTIVE'"
              >
                <span class="status-dot inactive"></span>
                {{ getStatusLabel('INACTIVE') }}
              </button>
            </div>
          </div>

          <div class="form-group">
            <label class="form-label">首页展示</label>
            <div class="status-toggle">
              <button
                class="status-btn"
                :class="{ active: form.showOnHome }"
                @click="form.showOnHome = true"
              >
                <span class="status-dot home"></span>
                是
              </button>
              <button
                class="status-btn"
                :class="{ active: !form.showOnHome }"
                @click="form.showOnHome = false"
              >
                <span class="status-dot home-inactive"></span>
                否
              </button>
            </div>
          </div>
        </div>

        <!-- 关联信息卡片 -->
        <div class="form-card">
          <div class="card-header">
            <div class="card-icon relation-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
                <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
              </svg>
            </div>
            <span class="card-title">关联信息</span>
          </div>

          <!-- 产地单选 -->
          <div class="form-group" v-if="originOptions.length > 0">
            <label class="form-label">产地</label>
            <select v-model="selectedOriginKey" class="form-select">
              <option value="">请选择产地</option>
              <option v-for="opt in originOptions" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </option>
            </select>
          </div>

          <!-- 客户多选下拉 -->
          <div class="form-group" v-if="customerOpts.length > 0">
            <label class="form-label">客户信息</label>
            <div class="multi-select-dropdown mobile" ref="customerDropdownRef">
              <div class="multi-select-trigger" @click="toggleCustomerDropdown">
                <span class="multi-select-placeholder" v-if="selectedCustomerKeys.length === 0">请选择客户</span>
                <span class="multi-select-tags" v-else>
                  <span
                    v-for="key in selectedCustomerKeys"
                    :key="key"
                    class="multi-select-tag"
                  >
                    {{ getCustomerNameByKey(key) }}
                    <span class="tag-remove" @click.stop="removeCustomer(key)">×</span>
                  </span>
                </span>
                <svg class="dropdown-arrow" :class="{ open: showCustomerDropdown }" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polyline points="6 9 12 15 18 9"/>
                </svg>
              </div>
              <div class="multi-select-panel" v-if="showCustomerDropdown">
                <label
                  v-for="opt in customerOpts"
                  :key="opt.value"
                  class="multi-select-option"
                  :class="{ selected: selectedCustomerKeys.includes(opt.value) }"
                >
                  <input
                    type="checkbox"
                    :value="opt.value"
                    :checked="selectedCustomerKeys.includes(opt.value)"
                    @click.stop="toggleCustomerSelection(opt.value)"
                  />
                  <span>{{ opt.label }}</span>
                </label>
              </div>
            </div>
          </div>

          <!-- 产品描述 -->
          <div class="form-group">
            <label class="form-label">产品描述</label>
            <textarea
              v-model="form.description"
              class="form-textarea"
              placeholder="请输入产品描述"
              rows="3"
            ></textarea>
          </div>
        </div>

        <!-- 其他信息卡片 -->
        <div class="form-card">
          <div class="card-header">
            <div class="card-icon extra-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <circle cx="12" cy="12" r="3"/>
                <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
              </svg>
            </div>
            <span class="card-title">其他信息</span>
          </div>

          <div class="form-group">
            <label class="form-label">排序号</label>
            <input
              v-model.number="form.sortOrder"
              type="number"
              class="form-input"
              placeholder="数值越小越靠前"
              min="0"
            />
          </div>

          <div class="form-group">
            <label class="form-label">备注说明</label>
            <textarea
              v-model="form.remark"
              class="form-textarea"
              placeholder="请输入备注信息（可选）"
              rows="3"
            ></textarea>
          </div>
        </div>
      </main>

      <!-- 加载状态 -->
      <div class="loading-state" v-else>
        <div class="loading-spinner"></div>
        <span>加载中...</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Newsreader:wght@400;500;600&family=JetBrains+Mono:wght@500;600&display=swap');

.product-edit-page {
  background-color: #F5F5F5;
}

/* ==================== PC布局 ==================== */
.pc-edit {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: 100%;
}

.pc-header {
  padding-bottom: 24px;
  border-bottom: 1px solid #E5E5E5;
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
  font-size: 28px;
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

/* 主体分栏布局 */
.pc-body {
  flex: 1;
  display: flex;
  gap: 24px;
}

/* 左侧导航栏 */
.pc-sidebar {
  width: 180px;
  flex-shrink: 0;
  position: sticky;
  top: 24px;
  align-self: flex-start;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #666666;
  cursor: pointer;
  transition: all 150ms;
  text-decoration: none;
  border: 1px solid transparent;
}

.nav-item:hover {
  background: rgba(13, 110, 110, 0.06);
  color: #374151;
}

.nav-item.active {
  background: rgba(13, 110, 110, 0.08);
  color: #0D6E6E;
  border-color: rgba(13, 110, 110, 0.15);
  font-weight: 500;
}

.nav-icon {
  width: 16px;
  height: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.nav-label {
  white-space: nowrap;
}

/* 右侧表单区域 */
.pc-form-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

/* 分区滚动定位：避免被 Layout 的 sub-navbar(48px) 遮挡 */
.form-section {
  scroll-margin-top: 64px;
}

/* 卡片样式 */
.form-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 28px 32px;
  border: 1px solid #E5E5E5;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
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

.card-title {
  font-family: 'Inter', sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: #1A1A1A;
}

/* 表单网格 */
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 20px;
  margin-bottom: 16px;
}

.form-grid.two-col {
  grid-template-columns: 1fr 1fr;
}

.form-group {
  margin-bottom: 0;
}

.form-label {
  display: block;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.required {
  color: #EF4444;
  margin-left: 2px;
}

.form-input[readonly] {
  background: #F9FAFB;
  color: #6B7280;
  cursor: not-allowed;
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
}

.status-toggle.inline {
  margin-bottom: 0;
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

.status-dot.home {
  background: #0D6E6E;
}

.status-dot.home-inactive {
  background: #EF4444;
}

/* 移动端多选下拉 */
.multi-select-dropdown {
  position: relative;
  width: 100%;
}

.multi-select-dropdown.pc .multi-select-trigger {
  min-height: 40px;
}

.multi-select-dropdown.pc .multi-select-panel {
  max-height: 240px;
}

.multi-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  background: #FFFFFF;
  cursor: pointer;
  min-height: 42px;
  gap: 8px;
}

.multi-select-trigger:hover {
  border-color: #D1D5DB;
}

.multi-select-placeholder {
  color: #9CA3AF;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
}

.multi-select-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}

.multi-select-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background: rgba(13, 110, 110, 0.1);
  color: #0D6E6E;
  border-radius: 4px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
}

.tag-remove {
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: #0D6E6E;
}

.tag-remove:hover {
  color: #EF4444;
}

.dropdown-arrow {
  color: #6B7280;
  transition: transform 200ms;
  flex-shrink: 0;
}

.dropdown-arrow.open {
  transform: rotate(180deg);
}

.multi-select-panel {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  background: #FFFFFF;
  border: 1px solid #E5E5E5;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  max-height: 200px;
  overflow-y: auto;
  z-index: 100;
}

.multi-select-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  color: #1A1A1A;
}

.multi-select-option:hover {
  background: #F5F5F5;
}

.multi-select-option.selected {
  background: rgba(13, 110, 110, 0.05);
  color: #0D6E6E;
}

.multi-select-option input[type="checkbox"] {
  width: 16px;
  height: 16px;
  cursor: pointer;
  accent-color: #0D6E6E;
}

/* 移动端多选下拉 */
.multi-select-dropdown.mobile .multi-select-panel {
  max-height: 250px;
}

/* 底部操作栏（原始位置） */
.pc-actions-bar {
  margin-top: auto;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 20px 0 4px;
}

/* 浮动操作栏（未滑到底部时显示） */
.pc-float-bar {
  position: fixed;
  bottom: 32px;
  right: 32px;
  display: flex;
  gap: 12px;
  z-index: 100;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(8px);
  padding: 10px 14px;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12), 0 0 0 1px rgba(0, 0, 0, 0.04);
}

.float-fade-enter-active,
.float-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.float-fade-enter-from,
.float-fade-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

.btn-save {
  padding: 12px 32px;
  min-width: 120px;
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
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px 16px 100px;
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
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 8px;
}

.required {
  color: #EF4444;
  margin-left: 2px;
}

.form-input[readonly] {
  background: #F9FAFB;
  color: #6B7280;
  cursor: not-allowed;
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

@media (max-width: 480px) {
  .content {
    padding: 12px;
  }

  .form-card {
    padding: 16px;
    border-radius: 10px;
  }

  .form-label {
    font-size: 13px;
    margin-bottom: 6px;
  }
}
</style>
