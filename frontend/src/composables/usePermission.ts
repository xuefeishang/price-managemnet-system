/**
 * 权限控制Composable
 * 提供细粒度的按钮级权限控制
 *
 * 权限判断方式：
 * - 登录时从后端获取用户权限列表
 * - 存入 userStore.permissions
 * - hasPermission 从 store 中动态读取
 */
import { computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'

// 权限定义（与后端 permission_code 对齐）
export const Permission = {
  // 产品相关
  PRODUCT_VIEW: 'product:view',
  PRODUCT_CREATE: 'product:create',
  PRODUCT_EDIT: 'product:edit',
  PRODUCT_DELETE: 'product:delete',
  PRODUCT_IMPORT: 'product:import',
  PRODUCT_EXPORT: 'product:export',

  // 分类相关
  CATEGORY_VIEW: 'category:view',
  CATEGORY_CREATE: 'category:create',
  CATEGORY_EDIT: 'category:edit',
  CATEGORY_DELETE: 'category:delete',

  // 价格相关
  PRICE_VIEW: 'price:view',
  PRICE_EDIT: 'price:edit',
  PRICE_EXPORT: 'price:export',
  PRICE_APPROVE: 'price:approve',

  // 用户相关
  USER_VIEW: 'user:view',
  USER_CREATE: 'user:create',
  USER_EDIT: 'user:edit',
  USER_DELETE: 'user:delete',
  USER_PASSWORD_RESET: 'user:password:reset',

  // 审批相关
  APPROVAL_VIEW: 'approval:view',
  APPROVAL_CREATE: 'approval:create',
  APPROVAL_PROCESS: 'approval:process',

  // 部门相关
  DEPT_VIEW: 'dept:view',
  DEPT_EDIT: 'dept:edit',

  // 日志相关
  LOG_VIEW: 'log:view',
  LOG_EXPORT: 'log:export',

  // 系统相关
  SYSTEM_SETTING: 'system:setting',

  // 通知相关
  NOTIFICATION_VIEW: 'notification:view',
  NOTIFICATION_RETRY: 'notification:retry',
  NOTIFICATION_SUBSCRIPTION_VIEW: 'notification:subscription:view',
  NOTIFICATION_SUBSCRIPTION_GUIDE: 'notification:subscription:guide',
  NOTIFICATION_SUBSCRIPTION_RESOLVE: 'notification:subscription:resolve',
  NOTIFICATION_TEST_TOKEN: 'notification:test-token',
  NOTIFICATION_TEST_DELIVERY: 'notification:test-delivery',
  SYSTEM_NOTICE_CREATE: 'system-notice:create',
  SYSTEM_NOTICE_CANCEL: 'system-notice:cancel',
} as const

export type PermissionKey = keyof typeof Permission

export function usePermission() {
  const userStore = useUserStore()

  /**
   * 检查当前用户是否拥有指定权限（动态）
   */
  const hasPermission = (permission: string): boolean => {
    return userStore.hasPermission(permission)
  }

  /**
   * 检查当前用户是否拥有所有指定权限
   */
  const hasAllPermissions = (permissions: string[]): boolean => {
    return permissions.every(p => hasPermission(p))
  }

  /**
   * 检查当前用户是否拥有任意一个指定权限
   */
  const hasAnyPermission = (permissions: string[]): boolean => {
    return permissions.some(p => hasPermission(p))
  }

  /**
   * 检查是否是管理员
   */
  const isAdmin = computed(() => userStore.isAdmin)

  /**
   * 检查是否是编辑者
   */
  const isEditor = computed(() => userStore.user?.role === 'EDITOR')

  /**
   * 检查是否是查看者
   */
  const isViewer = computed(() => userStore.user?.role === 'VIEWER')

  /**
   * 获取当前用户的权限列表
   */
  const permissions = computed(() => Array.from(userStore.permissions))

  /**
   * 检查是否可以执行某操作（基于操作类型）
   */
  const canDo = (action: 'create' | 'edit' | 'delete' | 'view' | 'import' | 'export', resource: 'product' | 'category' | 'user' | 'price' | 'log' | 'dept'): boolean => {
    const actionMap: Record<string, string> = {
      'create': `${resource}:create`,
      'edit': `${resource}:edit`,
      'delete': `${resource}:delete`,
      'view': `${resource}:view`,
      'import': `${resource}:import`,
      'export': `${resource}:export`,
    }
    const permission = actionMap[action]
    if (!permission) return false
    return hasPermission(permission)
  }

  return {
    hasPermission,
    hasAllPermissions,
    hasAnyPermission,
    isAdmin,
    isEditor,
    isViewer,
    permissions,
    canDo,
    Permission,
  }
}

/**
 * 权限指令 v-permission
 * 用法: <button v-permission="'product:create'">创建</button>
 */
export const permissionDirective = {
  mounted(el: HTMLElement, binding: any) {
    const { hasPermission } = usePermission()
    const permission = binding.value

    if (!permission || !hasPermission(permission)) {
      el.style.display = 'none'
    }
  },
  updated(el: HTMLElement, binding: any) {
    const { hasPermission } = usePermission()
    const permission = binding.value

    if (!permission || !hasPermission(permission)) {
      el.style.display = 'none'
    } else {
      el.style.display = ''
    }
  },
}

export default usePermission
