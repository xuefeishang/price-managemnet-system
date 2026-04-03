
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/useUserStore'
import { showToast } from 'vant'

// 路由配置扩展
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    title?: string
    adminOnly?: boolean
    editorOnly?: boolean
    permission?: string  // 具体权限标识
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
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
          path: 'product-detail/:id',
          name: 'ProductDetail',
          component: () => import('../views/ProductDetail.vue'),
          meta: { title: '产品详情' }
        },
        {
          path: 'product-edit/:id?',
          name: 'ProductEdit',
          component: () => import('../views/ProductEdit.vue'),
          meta: { title: '产品维护', editorOnly: true }
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
          meta: { title: '分类编辑', editorOnly: true }
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
          meta: { title: '产地编辑', editorOnly: true }
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
          meta: { title: '客户编辑', editorOnly: true }
        },
        {
          path: 'users',
          name: 'UserManagement',
          component: () => import('../views/UserManagement.vue'),
          meta: { title: '用户管理', adminOnly: true }
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
        }
      ]
    }
  ]
})

// 路由守卫
router.beforeEach(async (to, from, next) => {
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
      // fetchProfile失败（401等），说明token无效，直接跳转登录页
      // 不需要显示额外提示，因为后端会返回合适的错误信息
      userStore.logoutAction()
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
