/**
 * 字典分类元数据
 * 定义分类治理规则、使用帮助和效果预览配置。
 */

export type DictCategoryDomain =
  | 'business_dict'
  | 'system_dict'
  | 'ui_config'
  | 'visual_mapping'
  | 'internal'

export type ExtraValueMode = 'text' | 'color' | 'icon' | 'json' | 'readonly'
export type DictPreviewType = 'select' | 'badge' | 'color' | 'icon' | 'json' | 'readonly' | 'text'

export interface DictExample {
  key: string
  value: string
  extraValue?: string
}

export interface DictCategoryMeta {
  category: string
  label: string
  domain: DictCategoryDomain
  editableInDictPage: boolean
  keyMutable: boolean
  valueMutable: boolean
  extraValueMode: ExtraValueMode
  previewType: DictPreviewType
  ownerPage?: 'dict-management' | 'style-settings' | 'category-visual-settings'
  description?: string
  helpTitle: string
  usage: string
  usedIn: string[]
  keyRule: string
  valueRule: string
  extraValueRule: string
  editWarning?: string
  examples: DictExample[]
}

export const DOMAIN_LABELS: Record<DictCategoryDomain, string> = {
  business_dict: '业务字典',
  system_dict: '系统字典',
  ui_config: '界面配置',
  visual_mapping: '视觉映射',
  internal: '内部分类'
}

export const PROTECTED_CATEGORIES = [
  'style',
  'theme',
  'color_scheme',
  'layout_style',
  'font_preset',
  'home_layout',
  'home_widget',
  'category_visual_config',
  'category_visual_custom_combo'
]

const stableKeyWarning = '该分类已被业务页面引用，修改 Key 可能导致历史数据无法正确显示；建议只调整显示值、排序或状态。'
const protectedWarning = '该分类由专用页面维护，字典管理仅提供查看和理解入口。'

const meta = (item: DictCategoryMeta): DictCategoryMeta => item

export const DICT_CATEGORY_META: DictCategoryMeta[] = [
  meta({
    category: 'currency',
    label: '币种',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    previewType: 'select',
    ownerPage: 'dict-management',
    description: '货币类型及符号',
    helpTitle: '用于价格录入、价格展示和导入导出的币种口径。',
    usage: '产品价格、预算价和历史报价会读取该分类，将 dictValue 作为币种名称，将 extraValue 作为符号或短标识展示。',
    usedIn: ['产品列表', '价格维护', '价格查询', 'Excel 导入导出'],
    keyRule: '使用稳定大写编码，如 CNY、USD；上线后不建议修改。',
    valueRule: '填写用户可识别的币种名称。',
    extraValueRule: '填写符号或短标识，如 ¥、$；没有符号可留空。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'CNY', value: '人民币', extraValue: '¥' }, { key: 'USD', value: '美元', extraValue: '$' }]
  }),
  meta({
    category: 'unit',
    label: '计量单位',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    previewType: 'select',
    ownerPage: 'dict-management',
    description: '产品计量单位',
    helpTitle: '用于统一产品规格、报价单位和报表单位。',
    usage: '产品档案、价格维护和导入导出会读取该分类，避免同一单位出现多种写法。',
    usedIn: ['产品列表', '产品编辑', '价格维护', 'Excel 导入导出'],
    keyRule: '使用稳定单位编码，如 TON、KG；不要用显示名称作为 Key。',
    valueRule: '填写前台展示的单位名称。',
    extraValueRule: '可填写换算说明或简称；普通场景可留空。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'TON', value: '吨' }, { key: 'KG', value: '千克' }]
  }),
  meta({
    category: 'origin',
    label: '产地',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: true,
    valueMutable: true,
    extraValueMode: 'text',
    previewType: 'badge',
    ownerPage: 'dict-management',
    description: '产品产地',
    helpTitle: '用于产品来源地展示和筛选。',
    usage: '产品信息、首页产品卡片和价格查询页会根据产品 originIds 解析产地名称。',
    usedIn: ['产品列表', '首页产品卡片', '价格查询', '价格维护'],
    keyRule: '建议使用拼音或区域编码，保持短小且唯一。',
    valueRule: '填写真实产地名称，避免使用“未设置”等占位值。',
    extraValueRule: '可填写区域、国家或备注；没有就留空。',
    examples: [{ key: 'AU', value: '澳大利亚' }, { key: 'BR', value: '巴西' }]
  }),
  meta({
    category: 'customer',
    label: '客户',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: true,
    valueMutable: true,
    extraValueMode: 'text',
    previewType: 'select',
    ownerPage: 'dict-management',
    description: '客户类型',
    helpTitle: '用于沉淀客户分类或常用客户标识。',
    usage: '后续客户筛选、报价备注和报表维度可复用该分类，当前作为业务扩展字典保留。',
    usedIn: ['客户管理', '报价备注', '报表扩展'],
    keyRule: '建议使用客户简称、拼音或内部编码。',
    valueRule: '填写客户或客户类型展示名称。',
    extraValueRule: '可填写联系人、地区或扩展备注；普通文本即可。',
    examples: [{ key: 'VIP', value: '重点客户' }, { key: 'RETAIL', value: '零售客户' }]
  }),
  meta({
    category: 'price_metric_group',
    label: '价格指标分组',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    previewType: 'badge',
    ownerPage: 'dict-management',
    description: '价格查询页指标分组',
    helpTitle: '用于统一价格指标洞察区域的分组名称。',
    usage: '价格查询页按该分类展示价格现状、短期及预算偏差、月度趋势三个分组。',
    usedIn: ['价格查询', '价格指标洞察'],
    keyRule: '使用稳定业务编码，禁止修改既有 Key。',
    valueRule: '填写分组展示名称。',
    extraValueRule: '可填写分组用途说明。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'PRICE_STATUS', value: '价格现状' }, { key: 'MONTHLY_TREND', value: '月度趋势' }]
  }),
  meta({
    category: 'price_metric',
    label: '价格指标',
    domain: 'business_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'json',
    previewType: 'json',
    ownerPage: 'dict-management',
    description: '价格查询页指标名称与口径说明',
    helpTitle: '用于统一价格指标名称、解释、计算规则和备注。',
    usage: '价格查询页读取 dictValue 展示指标名称，读取 extraValue JSON 作为指标说明。',
    usedIn: ['价格查询', '价格指标洞察'],
    keyRule: '必须与前后端价格指标字段映射保持一致，禁止修改既有 Key。',
    valueRule: '填写指标展示名称，可按业务口径调整文案。',
    extraValueRule: 'JSON 格式：group、valueType、description、rule、note；valueType 仅支持 price、change、percent、date。',
    editWarning: stableKeyWarning,
    examples: [{
      key: 'LATEST_PRICE',
      value: '最新价格',
      extraValue: '{"group":"PRICE_STATUS","valueType":"price","description":"当前产品最新有效价格","rule":"按价格日期倒序取最新有效记录"}'
    }]
  }),
  meta({
    category: 'common_status',
    label: '通用状态',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'color',
    previewType: 'color',
    ownerPage: 'dict-management',
    description: '启用/停用状态',
    helpTitle: '用于统一系统内启用、停用等状态展示。',
    usage: '列表开关、状态标签和筛选条件会读取该分类，extraValue 用作状态色。',
    usedIn: ['用户管理', '字典管理', '产品管理', '审批管理'],
    keyRule: '使用系统协议值，如 ACTIVE、INACTIVE；禁止随意新增同义 Key。',
    valueRule: '填写状态显示名称。',
    extraValueRule: '填写十六进制颜色值，如 #0D6E6E。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'ACTIVE', value: '启用', extraValue: '#0D6E6E' }, { key: 'INACTIVE', value: '停用', extraValue: '#9CA3AF' }]
  }),
  meta({
    category: 'user_role',
    label: '用户角色',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'icon',
    previewType: 'icon',
    ownerPage: 'dict-management',
    description: '系统角色类型',
    helpTitle: '用于用户角色名称、权限提示和角色筛选。',
    usage: '用户管理和权限展示会读取该分类，Key 与后端权限角色保持一致。',
    usedIn: ['用户管理', '菜单权限', '登录态展示'],
    keyRule: '必须与后端角色编码一致，如 ADMIN、EDITOR、VIEWER。',
    valueRule: '填写角色显示名称。',
    extraValueRule: '可填写图标名或短标识，用于界面辅助展示。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'ADMIN', value: '管理员', extraValue: 'shield' }, { key: 'EDITOR', value: '编辑者', extraValue: 'edit' }]
  }),
  meta({
    category: 'dept_type',
    label: '部门类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'color',
    previewType: 'color',
    ownerPage: 'dict-management',
    description: '部门组织类型',
    helpTitle: '用于组织结构中区分总部、子公司和部门。',
    usage: '组织架构页面读取该分类决定部门类型名称和标识颜色。',
    usedIn: ['组织管理', '用户资料', '部门筛选'],
    keyRule: '使用稳定类型编码，避免与真实部门名称混用。',
    valueRule: '填写组织类型展示名称。',
    extraValueRule: '填写十六进制颜色值，用于类型标签。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'HEADQUARTER', value: '总部', extraValue: '#0D6E6E' }, { key: 'DEPARTMENT', value: '部门', extraValue: '#3B82F6' }]
  }),
  meta({
    category: 'operation_type',
    label: '操作类型',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    previewType: 'badge',
    ownerPage: 'dict-management',
    description: '操作日志类型',
    helpTitle: '用于操作日志的行为类型展示和筛选。',
    usage: '后端操作日志写入固定 Key，前端日志页面通过字典解析显示名称。',
    usedIn: ['日志管理', '审计追踪', '安全分析'],
    keyRule: '必须与 OperationLog 操作类型一致，如 CREATE、UPDATE、DELETE。',
    valueRule: '填写清晰的动作名称。',
    extraValueRule: '可填写分组或说明；通常留空。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'CREATE', value: '新增' }, { key: 'UPDATE', value: '更新' }]
  }),
  meta({
    category: 'operation_module',
    label: '操作模块',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'text',
    previewType: 'badge',
    ownerPage: 'dict-management',
    description: '操作日志模块',
    helpTitle: '用于操作日志中标识业务模块。',
    usage: '日志管理按模块展示和筛选操作记录，Key 与后端记录的模块编码一致。',
    usedIn: ['日志管理', '审计追踪'],
    keyRule: '使用稳定模块编码，保持与后端注解记录一致。',
    valueRule: '填写模块中文名称。',
    extraValueRule: '可填写模块分组；通常留空。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'PRODUCT', value: '产品管理' }, { key: 'USER', value: '用户管理' }]
  }),
  ...[
    ['approval_status', '审批状态', '审批流程状态', '审批列表、审批详情和变更记录会读取该分类，extraValue 用作状态色。', 'color', 'color'],
    ['workflow_type', '工作流类型', '审批工作流类型', '用于区分不同业务对象的审批流程。', 'text', 'select'],
    ['node_type', '审批节点类型', '审批节点类型', '用于审批流配置中标识节点职责。', 'text', 'badge'],
    ['approval_action', '审批操作', '审批操作类型', '用于审批历史中展示审批人动作。', 'text', 'badge'],
    ['change_type', '变更类型', '数据变更类型', '用于变更记录中展示新增、修改、删除等类型。', 'text', 'badge'],
    ['business_type', '业务类型', '业务数据类型', '用于审批或变更记录识别业务对象。', 'text', 'select']
  ].map(([category, label, description, usage, extraValueMode, previewType]) => meta({
    category,
    label,
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: extraValueMode as ExtraValueMode,
    previewType: previewType as DictPreviewType,
    ownerPage: 'dict-management',
    description,
    helpTitle: `${label}用于保持流程、日志和页面筛选口径一致。`,
    usage,
    usedIn: ['审批管理', '变更记录', '日志管理'],
    keyRule: '使用后端协议编码，新增或修改前需确认接口和历史数据引用。',
    valueRule: '填写前端展示名称，可按业务口径调整文案。',
    extraValueRule: extraValueMode === 'color' ? '填写十六进制颜色值，用于状态标签。' : '可填写辅助分组或说明；没有就留空。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'PENDING', value: '待处理', extraValue: extraValueMode === 'color' ? '#F59E0B' : undefined }, { key: 'APPROVED', value: '已通过', extraValue: extraValueMode === 'color' ? '#10B981' : undefined }]
  })),
  meta({
    category: 'notification_mini_subscription_row_status',
    label: '小程序订阅用户行状态',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'color',
    previewType: 'color',
    ownerPage: 'dict-management',
    description: '小程序订阅授权列表的聚合行状态',
    helpTitle: '用于订阅授权列表、筛选项和用户详情中的聚合接收资格展示。',
    usage: '通知管理订阅授权页读取该分类展示 NORMAL、LOW_BALANCE、UNBOUND、REJECTED 等行状态。',
    usedIn: ['通知管理', '小程序订阅授权'],
    keyRule: '必须与后端 AdminMiniProgramSubscriptionDTO.status 保持一致。',
    valueRule: '填写管理员可识别的状态名称。',
    extraValueRule: '填写十六进制颜色值，用于状态标签。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'NORMAL', value: '正常', extraValue: '#10B981' }, { key: 'LOW_BALANCE', value: '低余量', extraValue: '#F59E0B' }]
  }),
  meta({
    category: 'notification_mini_resolution_status',
    label: '小程序订阅异常处理状态',
    domain: 'system_dict',
    editableInDictPage: true,
    keyMutable: false,
    valueMutable: true,
    extraValueMode: 'color',
    previewType: 'color',
    ownerPage: 'dict-management',
    description: '小程序订阅异常的运维处理状态',
    helpTitle: '用于订阅授权详情中的异常处理和列表处理状态展示。',
    usage: '通知管理订阅授权页读取该分类展示 OPEN、RESOLVED、SNOOZED、FOLLOW_UP 状态。',
    usedIn: ['通知管理', '小程序订阅授权'],
    keyRule: '必须与后端 NotificationMiniProgramResolution.ResolveStatus 保持一致。',
    valueRule: '填写管理员可识别的处理状态名称。',
    extraValueRule: '填写十六进制颜色值，用于状态标签。',
    editWarning: stableKeyWarning,
    examples: [{ key: 'OPEN', value: '待处理', extraValue: '#F59E0B' }, { key: 'RESOLVED', value: '已处理', extraValue: '#10B981' }]
  }),
  ...[
    ['style', '样式配置', '全局样式配置，请前往样式设置管理'],
    ['theme', '主题配置', '主题预设配置，请前往样式设置管理'],
    ['color_scheme', '色彩方案', '色彩方案预设，请前往样式设置管理'],
    ['layout_style', '布局方案', '布局方案预设，请前往样式设置管理'],
    ['font_preset', '字号预设', '字号预设配置，请前往样式设置管理'],
    ['home_layout', '首页布局', '首页布局配置，请前往样式设置管理'],
    ['home_widget', '首页组件', '首页组件配置，请前往样式设置管理']
  ].map(([category, label, description]) => meta({
    category,
    label,
    domain: 'ui_config',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'readonly',
    previewType: 'readonly',
    ownerPage: 'style-settings',
    description,
    helpTitle: `${label}由样式设置页面统一维护。`,
    usage: '该分类承载界面配置，不建议在字典管理中直接编辑，避免样式配置和专业页面状态不一致。',
    usedIn: ['样式设置', '首页', '全局界面'],
    keyRule: '由专用页面生成和维护。',
    valueRule: '由专用页面维护展示名称。',
    extraValueRule: '只读查看，具体结构以专用页面为准。',
    editWarning: protectedWarning,
    examples: [{ key: category.toUpperCase(), value: label }]
  })),
  meta({
    category: 'category_visual_config',
    label: '分类视觉配置',
    domain: 'visual_mapping',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'json',
    previewType: 'json',
    ownerPage: 'category-visual-settings',
    description: '分类视觉映射配置，JSON 结构复杂',
    helpTitle: '用于产品分类圆点、颜色和组合样式映射。',
    usage: '首页、产品列表和价格查询会读取该分类的 JSON 配置展示分类视觉效果。',
    usedIn: ['首页', '产品列表', '价格查询', '分类视觉设置'],
    keyRule: '由分类视觉设置页面生成，不在字典管理中手动修改。',
    valueRule: '由专用页面维护。',
    extraValueRule: 'JSON 结构，只读预览；修改需前往分类视觉设置。',
    editWarning: protectedWarning,
    examples: [{ key: 'IRON_ORE', value: '铁矿石', extraValue: '{"color":"#0D6E6E","shape":"dot"}' }]
  }),
  meta({
    category: 'category_visual_custom_combo',
    label: '分类视觉自定义组合',
    domain: 'visual_mapping',
    editableInDictPage: false,
    keyMutable: false,
    valueMutable: false,
    extraValueMode: 'json',
    previewType: 'json',
    ownerPage: 'category-visual-settings',
    description: '分类视觉“我的组合”配置，仅允许 1 组',
    helpTitle: '用于保存分类视觉设置中的自定义组合。',
    usage: '分类视觉设置页面读取该分类恢复用户保存的自定义视觉方案。',
    usedIn: ['分类视觉设置', '首页', '产品列表'],
    keyRule: '由专用页面固定维护。',
    valueRule: '由专用页面维护。',
    extraValueRule: 'JSON 结构，只读预览；修改需前往分类视觉设置。',
    editWarning: protectedWarning,
    examples: [{ key: 'CUSTOM', value: '我的组合', extraValue: '{"items":[]}' }]
  })
]

export function getCategoryMeta(category: string): DictCategoryMeta | undefined {
  return DICT_CATEGORY_META.find(meta => meta.category === category)
}

export function isProtectedCategory(category: string): boolean {
  return PROTECTED_CATEGORIES.includes(category)
}

export function getCategoryLabel(category: string): string {
  return getCategoryMeta(category)?.label || category
}

export function getDomainLabel(domain: DictCategoryDomain): string {
  return DOMAIN_LABELS[domain]
}

export function getCategoriesByDomain(domain: DictCategoryDomain): string[] {
  return DICT_CATEGORY_META
    .filter(meta => meta.domain === domain)
    .map(meta => meta.category)
}

export function getVisibleCategoryMetas(categories: string[], showSystemConfig: boolean): DictCategoryMeta[] {
  const categorySet = new Set(categories)
  const registered = DICT_CATEGORY_META.filter(meta => categorySet.has(meta.category))
  return registered.filter(meta => showSystemConfig || !isProtectedCategory(meta.category))
}

export function getUnregisteredCategories(categories: string[]): string[] {
  const registered = new Set(DICT_CATEGORY_META.map(meta => meta.category))
  return categories.filter(category => !registered.has(category))
}

export function getBusinessDictCategories(): string[] {
  return getCategoriesByDomain('business_dict')
}

export function getSystemDictCategories(): string[] {
  return getCategoriesByDomain('system_dict')
}

export function getUIConfigCategories(): string[] {
  return getCategoriesByDomain('ui_config')
}

export function getEditableCategories(): string[] {
  return DICT_CATEGORY_META
    .filter(meta => meta.editableInDictPage)
    .map(meta => meta.category)
}

export function getExtraValueMode(category: string): ExtraValueMode {
  return getCategoryMeta(category)?.extraValueMode || 'text'
}

export function isColorValue(value: string): boolean {
  if (!value) return false
  return /^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$/i.test(value)
}

export function isJsonValue(value: string): boolean {
  if (!value) return false
  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

export function formatJsonDisplay(value: string): string {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
