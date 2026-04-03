/**
 * 权限控制Composable
 * 提供细粒度的按钮级权限控制
 */
import { computed } from 'vue'
import { useUserStore } from '@/store/useUserStore'

// 权限定义
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

  // 日志相关
  LOG_VIEW: 'log:view',
  LOG_EXPORT: 'log:export',

  // 系统相关
  SYSTEM_SETTING: 'system:setting',
} as const

export type PermissionKey = keyof typeof Permission

// 角色-权限映射
const rolePermissions: Record<string, string[]> = {
  ADMIN: [
    // 产品
    Permission.PRODUCT_VIEW,
    Permission.PRODUCT_CREATE,
    Permission.PRODUCT_EDIT,
    Permission.PRODUCT_DELETE,
    Permission.PRODUCT_IMPORT,
    Permission.PRODUCT_EXPORT,
    // 分类
    Permission.CATEGORY_VIEW,
    Permission.CATEGORY_CREATE,
    Permission.CATEGORY_EDIT,
    Permission.CATEGORY_DELETE,
    // 价格
    Permission.PRICE_VIEW,
    Permission.PRICE_EDIT,
    Permission.PRICE_APPROVE,
    // 用户
    Permission.USER_VIEW,
    Permission.USER_CREATE,
    Permission.USER_EDIT,
    Permission.USER_DELETE,
    Permission.USER_PASSWORD_RESET,
    // 审批
    Permission.APPROVAL_VIEW,
    Permission.APPROVAL_CREATE,
    Permission.APPROVAL_PROCESS,
    // 日志
    Permission.LOG_VIEW,
    Permission.LOG_EXPORT,
    // 系统
    Permission.SYSTEM_SETTING,
  ],
  EDITOR: [
    // 产品
    Permission.PRODUCT_VIEW,
    Permission.PRODUCT_CREATE,
    Permission.PRODUCT_EDIT,
    Permission.PRODUCT_IMPORT,
    Permission.PRODUCT_EXPORT,
    // 分类
    Permission.CATEGORY_VIEW,
    Permission.CATEGORY_CREATE,
    Permission.CATEGORY_EDIT,
    // 价格
    Permission.PRICE_VIEW,
    Permission.PRICE_EDIT,
    // 审批
    Permission.APPROVAL_VIEW,
    Permission.APPROVAL_CREATE,
    Permission.APPROVAL_PROCESS,
    // 日志
    Permission.LOG_VIEW,
  ],
  VIEWER: [
    // 产品
    Permission.PRODUCT_VIEW,
    Permission.PRODUCT_EXPORT,
    // 分类
    Permission.CATEGORY_VIEW,
    // 价格
    Permission.PRICE_VIEW,
    // 审批
    Permission.APPROVAL_VIEW,
  ],
}

export function usePermission() {
  const userStore = useUserStore()

  /**
   * 检查当前用户是否拥有指定权限
   */
  const hasPermission = (permission: string): boolean => {
    if (!userStore.user?.role) return false
    const permissions = rolePermissions[userStore.user.role] || []
    return permissions.includes(permission)
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
  const isAdmin = computed(() => userStore.user?.role === 'ADMIN')

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
  const permissions = computed(() => {
    if (!userStore.user?.role) return []
    return rolePermissions[userStore.user.role] || []
  })

  /**
   * 检查是否可以执行某操作（基于操作类型）
   */
  const canDo = (action: 'create' | 'edit' | 'delete' | 'view' | 'import' | 'export', resource: 'product' | 'category' | 'user' | 'price' | 'log'): boolean => {
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
