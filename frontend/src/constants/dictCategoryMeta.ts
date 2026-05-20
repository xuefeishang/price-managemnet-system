/**
 * 字典分类元数据
 * 定义分类的治理规则和受保护分类列表
 */

export type DictCategoryDomain =
  | 'business_dict'
  | 'system_dict'
  | 'ui_config'
  | 'visual_mapping'
  | 'internal'

export interface DictCategoryMeta {
  category: string
  label: string
  domain: DictCategoryDomain
  editableInDictPage: boolean
  keyMutable: boolean
  valueMutable: boolean
  extraValueMode: 'text' | 'color' | 'icon' | 'json' | 'readonly'
  ownerPage?: 'dict-management' | 'style-settings' | 'category-visual-settings'
  description?: string
}

/**
 * 受保护分类列表
 * 这些分类不能在字典管理页面直接编辑，必须通过专业入口（如样式设置）管理
 */
export const PROTECTED_CATEGORIES = [
  'style',
  'theme',
  'color_scheme',
  'layout_style',
  'font_preset',
  'home_layout',
  'home_widget',
  'category_visual_config'
]

/**
 * 分类元数据定义
 */
export const DICT_CATEGORY_META: DictCategoryMeta[] = [
  // 业务字典
  {
    category: 'currency',
    label: '币种',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '货币类型及符号'
  },
  {
    category: 'unit',
    label: '计量单位',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '产品计量单位'
  },
  {
    category: 'origin',
    label: '产地',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: true,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '产品产地'
  },
  {
    category: 'customer',
    label: '客户',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: true,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '客户类型'
  },

  // 系统字典
  {
    category: 'common_status',
    label: '通用状态',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'color',
    ownerPage: 'dict-management',
    description: '启用/停用状态'
  },
  {
    category: 'user_role',
    label: '用户角色',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'icon',
    ownerPage: 'dict-management',
    description: '系统角色类型'
  },
  {
    category: 'dept_type',
    label: '部门类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'color',
    ownerPage: 'dict-management',
    description: '部门组织类型'
  },
  {
    category: 'operation_type',
    label: '操作类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '操作日志类型'
  },
  {
    category: 'operation_module',
    label: '操作模块',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '操作日志模块'
  },

  // 审批流程
  {
    category: 'approval_status',
    label: '审批状态',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'color',
    ownerPage: 'dict-management',
    description: '审批流程状态'
  },
  {
    category: 'workflow_type',
    label: '工作流类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '审批工作流类型'
  },
  {
    category: 'node_type',
    label: '审批节点类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '审批节点类型'
  },
  {
    category: 'approval_action',
    label: '审批操作',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '审批操作类型'
  },
  {
    category: 'change_type',
    label: '变更类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '数据变更类型'
  },
  {
    category: 'business_type',
    label: '业务类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    ownerPage: 'dict-management',
    description: '业务数据类型'
  },

  // UI 配置（受保护）
  {
    category: 'style',
    label: '样式配置',
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    ownerPage: 'style-settings',
    description: '全局样式配置，请前往样式设置管理'
  },
  {
    category: 'theme',
    label: '主题配置',
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    ownerPage: 'style-settings',
    description: '主题预设配置，请前往样式设置管理'
  },
  {
    category: 'color_scheme',
    label: '色彩方案',
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    ownerPage: 'style-settings',
    description: '色彩方案预设，请前往样式设置管理'
  },
  {
    category: 'layout_style',
    label: '布局方案',
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    ownerPage: 'style-settings',
    description: '布局方案预设，请前往样式设置管理'
  },
  {
    category: 'font_preset',
    label: '字号预设',
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    ownerPage: 'style-settings',
    description: '字号预设配置，请前往样式设置管理'
  },
  {
    category: 'home_layout',
    label: '首页布局',
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    ownerPage: 'style-settings',
    description: '首页布局配置，请前往样式设置管理'
  },
  {
    category: 'home_widget',
    label: '首页组件',
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    ownerPage: 'style-settings',
    description: '首页组件配置，请前往样式设置管理'
  },

  // 视觉映射（受保护）
  {
    category: 'category_visual_config',
    label: '分类视觉配置',
    domain: 'visual_mapping',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'json',
    ownerPage: 'category-visual-settings',
    description: '分类视觉映射配置，JSON 结构复杂'
  }
]

/**
 * 获取分类元数据
 */
export function getCategoryMeta(category: string): DictCategoryMeta | undefined {
  return DICT_CATEGORY_META.find(meta => meta.category === category)
}

/**
 * 判断是否为受保护分类
 */
export function isProtectedCategory(category: string): boolean {
  return PROTECTED_CATEGORIES.includes(category)
}

/**
 * 获取分类标签
 */
export function getCategoryLabel(category: string): string {
  const meta = getCategoryMeta(category)
  return meta?.label || category
}

/**
 * 获取所有业务字典分类
 */
export function getBusinessDictCategories(): string[] {
  return DICT_CATEGORY_META
    .filter(meta => meta.domain === 'business_dict')
    .map(meta => meta.category)
}

/**
 * 获取所有系统字典分类
 */
export function getSystemDictCategories(): string[] {
  return DICT_CATEGORY_META
    .filter(meta => meta.domain === 'system_dict')
    .map(meta => meta.category)
}

/**
 * 获取所有 UI 配置分类
 */
export function getUIConfigCategories(): string[] {
  return DICT_CATEGORY_META
    .filter(meta => meta.domain === 'ui_config')
    .map(meta => meta.category)
}

/**
 * 获取字典管理页面可编辑的分类
 */
export function getEditableCategories(): string[] {
  return DICT_CATEGORY_META
    .filter(meta => meta.editableInDictPage)
    .map(meta => meta.category)
}

/**
 * 获取分类的 extraValue 渲染模式
 * 如果分类未定义，默认返回 'text'
 */
export function getExtraValueMode(category: string): 'text' | 'color' | 'icon' | 'json' | 'readonly' {
  const meta = getCategoryMeta(category)
  return meta?.extraValueMode || 'text'
}

/**
 * 判断 extraValue 是否为颜色值
 */
export function isColorValue(value: string): boolean {
  if (!value) return false
  // 支持 #RGB, #RRGGBB, #RRGGBBAA 格式
  return /^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$/i.test(value)
}

/**
 * 判断 extraValue 是否为 JSON
 */
export function isJsonValue(value: string): boolean {
  if (!value) return false
  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

/**
 * 格式化 JSON 显示（缩进2空格）
 */
export function formatJsonDisplay(value: string): string {
  if (!value) return ''
  try {
    const parsed = JSON.parse(value)
    return JSON.stringify(parsed, null, 2)
  } catch {
    return value
  }
}
