/**
 * Vue Router 路由配置
 * 定义应用的所有路由规则、路由元信息、路由守卫
 *
 * 路由元信息说明：
 * - requiresAuth: 是否需要登录认证
 * - title: 页面标题（显示在浏览器标签）
 * - adminOnly: 仅管理员可访问
 * - editorOnly: 仅编辑者及以上角色可访问
 * - permission: 具体权限标识（用于细粒度权限控制）
 */
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/useUserStore'
import { showToast } from 'vant'

const createAppHistory = () => {
  const originalAddEventListener = document.addEventListener

  document.addEventListener = function patchedAddEventListener(
    this: Document,
    type: string,
    listener: EventListenerOrEventListenerObject,
    options?: boolean | AddEventListenerOptions
  ) {
    if (type === 'visibilitychange') return
    return originalAddEventListener.call(this, type, listener, options)
  } as typeof document.addEventListener

  try {
    return createWebHistory(import.meta.env.BASE_URL)
  } finally {
    document.addEventListener = originalAddEventListener
  }
}

// 路由配置扩展：定义路由元信息类型
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    title?: string
    adminOnly?: boolean
    editorOnly?: boolean
    permission?: string  // 具体权限标识
    activeMenu?: string  // 非菜单路由对应的菜单路径
  }
}

const router = createRouter({
  history: createAppHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('../views/Login.vue'),
      meta: { requiresAuth: false, title: '登录' }
    },
    {
      path: '/',
      name: 'Layout',
      component: () => import('../components/Layout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'home',
          name: 'Home',
          component: () => import('../views/Home.vue'),
          meta: { title: '首页' }
        },
        {
          path: 'products',
          name: 'Products',
          component: () => import('../views/Products.vue'),
          meta: { title: '产品管理' }
        },
        {
          path: 'price-maintenance',
          name: 'PriceMaintenance',
          component: () => import('../views/PriceMaintenance.vue'),
          meta: { title: '价格维护', editorOnly: true }
        },
        {
          path: 'price-query',
          name: 'PriceQuery',
          component: () => import('../views/PriceQuery.vue'),
          meta: { title: '价格查询' }
        },
        {
          path: 'product-detail/:id',
          name: 'ProductDetail',
          component: () => import('../views/ProductDetail.vue'),
          meta: { title: '产品详情', activeMenu: '/products' }
        },
        {
          path: 'product-edit/:id?',
          name: 'ProductEdit',
          component: () => import('../views/ProductEdit.vue'),
          meta: { title: '产品维护', editorOnly: true, activeMenu: '/products' }
        },
        {
          path: 'categories',
          name: 'Categories',
          component: () => import('../views/Categories.vue'),
          meta: { title: '基础运维', editorOnly: true }
        },
        {
          path: 'category-edit/:id?',
          name: 'CategoryEdit',
          component: () => import('../views/CategoryEdit.vue'),
          meta: { title: '分类编辑', editorOnly: true, activeMenu: '/categories' }
        },
        {
          path: 'origins',
          name: 'Origins',
          component: () => import('../views/Origins.vue'),
          meta: { title: '产地管理', editorOnly: true }
        },
        {
          path: 'origin-edit/:id?',
          name: 'OriginEdit',
          component: () => import('../views/OriginEdit.vue'),
          meta: { title: '产地编辑', editorOnly: true, activeMenu: '/origins' }
        },
        {
          path: 'customers',
          name: 'Customers',
          component: () => import('../views/Customers.vue'),
          meta: { title: '客户管理', editorOnly: true }
        },
        {
          path: 'customer-edit/:id?',
          name: 'CustomerEdit',
          component: () => import('../views/CustomerEdit.vue'),
          meta: { title: '客户编辑', editorOnly: true, activeMenu: '/customers' }
        },
        {
          path: 'users',
          name: 'UserManagement',
          component: () => import('../views/UserManagement.vue'),
          meta: { title: '用户管理', adminOnly: true }
        },
        {
          path: 'departments',
          name: 'DepartmentManagement',
          component: () => import('../views/DepartmentManagement.vue'),
          meta: { title: '部门管理', adminOnly: true }
        },
        {
          path: 'roles',
          name: 'RoleManagement',
          component: () => import('../views/RoleManagement.vue'),
          meta: { title: '角色管理', adminOnly: true }
        },
        {
          path: 'menu-config',
          name: 'MenuConfig',
          component: () => import('../views/MenuConfig.vue'),
          meta: { title: '菜单配置', adminOnly: true }
        },
        {
          path: 'import',
          name: 'Import',
          component: () => import('../views/Import.vue'),
          meta: { title: '导入导出', editorOnly: true }
        },
        {
          path: 'profile',
          name: 'Profile',
          component: () => import('../views/Profile.vue'),
          meta: { title: '个人管理' }
        },
        {
          path: 'operation-log',
          name: 'OperationLog',
          component: () => import('../views/OperationLog.vue'),
          meta: { title: '操作日志', adminOnly: true }
        },
        {
          path: 'api-keys',
          name: 'ApiKeyList',
          component: () => import('../views/ApiKeyList.vue'),
          meta: { title: 'API授权管理', adminOnly: true }
        },
        {
          path: 'api-keys/:id',
          name: 'ApiKeyDetail',
          component: () => import('../views/ApiKeyDetail.vue'),
          meta: { title: 'API密钥详情', adminOnly: true, activeMenu: '/api-keys' }
        },
        {
          path: 'api-call-logs',
          name: 'ApiCallLog',
          component: () => import('../views/ApiCallLog.vue'),
          meta: { title: 'API调用日志', adminOnly: true }
        },
        {
          path: 'approval',
          name: 'Approval',
          component: () => import('../views/Approval.vue'),
          meta: { title: '审批管理', editorOnly: true }
        },
        {
          path: 'approval-config',
          name: 'ApprovalConfig',
          component: () => import('../views/ApprovalConfig.vue'),
          meta: { title: '审批流配置', adminOnly: true }
        },
        {
          path: 'dict-management',
          name: 'DictManagement',
          component: () => import('../views/DictManagement.vue'),
          meta: { title: '数据字典', adminOnly: true }
        },
        {
          path: 'style-settings',
          name: 'StyleSettings',
          component: () => import('../views/StyleSettings.vue'),
          meta: { title: '样式设置', adminOnly: true }
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()

  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 价格管理系统`
  }

  // 不需要认证的页面直接放行
  if (!to.meta.requiresAuth) {
    return next()
  }

  // 检查是否已登录（只有token存在）
  if (!userStore.isAuthenticated || !userStore.token) {
    showToast('请先登录')
    return next('/login')
  }

  // 如果用户信息为空，尝试获取
  if (!userStore.user) {
    try {
      await userStore.fetchProfile()
    } catch (error: any) {
      // fetchProfile失败（401等），说明token无效
      // 清除本地状态后跳转登录页，避免无限循环（不调用API）
      userStore.logoutAction(false)
      showToast('登录已过期，请重新登录')
      return next('/login')
    }
  }

  // 检查管理员专属页面
  if (to.meta.adminOnly && !userStore.isAdmin) {
    showToast('您没有权限访问该页面')
    return next('/home')
  }

  // 检查编辑者专属页面
  if (to.meta.editorOnly && !userStore.canEdit) {
    showToast('您没有权限执行此操作')
    return next('/products')
  }

  next()
})

export default router
