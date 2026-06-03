# 字典管理分类页签与使用说明功能规划

> 实施状态：已按本方案完成前端升级。落地文件包括 `frontend/src/views/DictManagement.vue`、`frontend/src/constants/dictCategoryMeta.ts`、`frontend/src/components/dict/DictCategoryHelpPanel.vue`、`frontend/src/components/dict/DictCategoryPreview.vue`。本次未修改后端接口和数据库结构。

## Context

当前 `frontend/src/views/DictManagement.vue` 已具备字典项的查询、新增、编辑、删除、启停和受保护分类只读提示能力，但页面仍偏“数据表维护”：

- PC 端通过下拉框筛选分类，无法像移动端一样直接看到分类页签，分类之间切换成本较高。
- 每个分类仅显示分类名称、编码、数量和受保护标识，缺少“这个分类在哪里使用、能改什么、扩展值怎么填、改错会影响什么”的上下文说明。
- `frontend/src/constants/dictCategoryMeta.ts` 已有分类元数据，但目前只有 `label`、`domain`、`editableInDictPage`、`extraValueMode`、`ownerPage`、`description` 等基础字段，无法支撑更完整的帮助说明和效果展示。
- 当前表格已经能按 `extraValueMode` 渲染颜色、图标、JSON、只读文本，但没有把这些渲染结果组织成面向管理员的“效果预览”。
- 项目规范要求所有编码值显示名称从字典服务动态获取，字典管理页本身更应该帮助管理员理解字典值对系统页面的影响。

本方案目标是将 `dict-management` 页面升级为 **分类页签 + 分类说明 + 效果展示 + 字典项维护** 的工作台，让 ADMIN / EDITOR 在维护前先明确分类用途、字段规则和变更影响。

## Goals

1. PC 端新增分类页签，替换或弱化现有分类下拉筛选，让常用分类可直接切换。
2. 移动端保留横向页签，并增加分类说明入口和轻量效果展示。
3. 每个分类都有明确的使用帮助：用途、使用页面、字段说明、扩展值规则、可编辑范围、风险提示。
4. 每个分类提供效果展示：状态标签、颜色预览、图标预览、下拉选项预览、JSON 摘要或业务场景样例。
5. 分类说明来源于前端静态元数据扩展，不新增数据库表，不改变后端字典接口。
6. 受保护分类继续只读，并明确显示归属入口，例如样式设置或分类视觉配置。
7. 保持字典值展示严格使用字典数据，不在业务页面新增硬编码中文映射。
8. 页面布局适配 PC 和移动端，不引入横向溢出和文字重叠。

## 9.5+ 达成原则

本方案按“后台工具可直接落地”的标准优化，不只描述功能，还明确最终布局、组件边界、交付阶段和验收门槛。

| 维度 | 9.5+ 要求 | 本方案落点 |
|------|-----------|------------|
| 布局确定性 | 开发者不需要二次猜测主布局 | 固定 PC 首选布局为“顶部页签 + 左说明预览 + 右表格”，窄屏才降级 |
| 操作效率 | 维护人员 3 步内完成分类定位和新增/编辑 | 分组页签、分类页签、当前分类主按钮贴近表格 |
| 风险可见 | 用户操作前知道字典项影响范围 | 分类说明面板展示使用位置、字段规则和风险提示 |
| 展示可信 | 预览不是装饰，而是来源于真实字典项 | `DictCategoryPreview` 优先使用当前分类真实启用项 |
| 工程可维护 | 避免 `DictManagement.vue` 继续膨胀 | 拆出 HelpPanel、Preview、Tabs、TableToolbar 等组件 |
| 项目一致性 | 不破坏现有 API、缓存和受保护分类规则 | 不改后端接口和表结构，复用 `useDict` 与 `dictCategoryMeta` |
| 验收可量化 | 有明确断点、尺寸、状态和回归范围 | 增加布局验收标准、手工验收矩阵和 MVP 完成线 |

### 最终决策

本次升级的最终推荐形态固定为：

```text
PC：顶部操作区 + 一级分组页签 + 二级分类页签 + 左侧说明/预览 + 右侧表格
移动端：顶部操作区 + 横向分类页签 + 说明摘要 + 预览折叠 + 字典卡片列表
```

除非实际验证发现表格在 `1366px` 宽度下不可用，否则 PC 宽屏不采用“纯上说明下表格”作为默认形态；该形态只作为 `1024px - 1439px` 的降级布局。

## Non-Goals

- 本次不新增字典分类的后端元数据表。
- 本次不调整 `sys_dict` 表结构。
- 本次不改变 `/api/dict`、`/api/dict/categories`、`/api/dict/active` 等接口协议。
- 本次不做字典项批量导入导出。
- 本次不把受保护分类改为可编辑；仍通过对应专业页面维护。

## 现状参考文件

| 文件 | 当前职责 | 本次用途 |
|------|----------|----------|
| `frontend/src/views/DictManagement.vue` | 字典管理页面 | 增加分类页签、说明面板、效果展示、响应式布局 |
| `frontend/src/constants/dictCategoryMeta.ts` | 分类治理元数据 | 扩展为分类帮助和展示配置的来源 |
| `frontend/src/composables/useDict.ts` | 字典缓存和显示值获取 | 继续作为字典展示和选项获取入口 |
| `frontend/src/api/dict.ts` | 前端字典 API | 保持接口不变 |
| `backend/src/main/java/com/pricemanagement/controller/SysDictController.java` | 后端字典接口 | 仅做一致性核对，不计划修改 |
| `backend/src/main/java/com/pricemanagement/entity/SysDict.java` | 字典实体 | 仅做 ORM 与表结构一致性核对 |
| `backend/src/main/resources/db/migration/V1__baseline_init_schema.sql` | `sys_dict` 基线表结构 | 仅做数据库一致性核对 |

## 信息架构

### 科学布局原则

本页面不是展示型页面，而是高频后台维护工具。布局必须优先服务“快速定位分类、理解影响、完成维护、确认效果”四个连续动作。

| 原则 | 落地要求 |
|------|----------|
| 操作主线清晰 | 页面从上到下按“筛选分类 → 阅读说明 → 维护字典项 → 查看效果”组织，不把帮助信息散落在弹窗里 |
| 认知负担可控 | 同一时刻只聚焦一个分类；“全部”只做总览，不展开所有分类表格 |
| 主次面积稳定 | 表格是主操作区，说明和预览是辅助区；辅助区不能挤压表格到不可用 |
| 高频操作靠近数据 | 新增、编辑、启停、删除必须贴近当前分类和当前行，避免用户跨区域寻找按钮 |
| 风险先提示后操作 | 受保护分类、不可修改 key、JSON/颜色扩展值等风险在操作前可见 |
| 响应式不是压缩 | 移动端不强行保留左右分栏，而是改为“页签 → 说明摘要 → 预览折叠 → 卡片列表” |

### 推荐区域比例

PC 端按主内容区宽度分三档处理：

| 宽度 | 布局 | 说明 |
|------|------|------|
| `>= 1440px` | 左侧说明预览 `360px` + 右侧表格自适应 | 最佳工作台形态，说明和表格同时可见 |
| `1024px - 1439px` | 上方说明预览横排 + 下方表格 | 避免说明侧栏挤压表格列宽 |
| `< 1024px` | 移动布局 | 页签横向滚动，说明摘要和预览折叠 |

PC 默认布局建议：

```text
Header 高度约 48-56px
分组页签高度约 40px
分类页签高度约 44-88px，可换行
主工作区：
  左侧说明/预览：固定 340-380px
  右侧表格：min-width 720px，自适应填充
```

表格区域应优先保持横向可读：

- 排序列固定窄列。
- 字典键、显示值、状态、操作列优先可见。
- 扩展值和备注允许截断、悬浮或展开查看。
- 不允许因为左侧说明面板导致表格操作列被挤出可视区。

### 操作路径

页面必须支持三条主要路径：

```text
路径 A：维护已有分类
进入页面 → 选择分组 → 选择分类 → 查看说明/预览 → 编辑或启停字典项 → 预览同步变化

路径 B：新增业务字典项
进入页面 → 选择可编辑分类 → 点击当前分类新增 → 表单按分类规则提示 → 保存 → 表格和预览刷新

路径 C：查看受保护分类
开启显示系统配置 → 选择受保护分类 → 查看只读说明和效果 → 跳转归属页面维护
```

所有路径都要避免让用户先打开编辑弹窗才知道字段规则。

### PC 端布局

推荐采用“上方概览 + 分类页签 + 主工作区”的后台管理布局：

```text
数据字典
┌────────────────────────────────────────────────────────────┐
│ 操作区：显示系统配置开关 / 搜索分类 / 新建字典               │
└────────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────────┐
│ 分类页签：全部 / 业务字典 / 系统字典 / 审批流程 / UI配置...   │
│ 二级分类：币种 / 计量单位 / 产地 / 客户 / 通用状态 ...        │
└────────────────────────────────────────────────────────────┘
┌──────────────────────────────┬─────────────────────────────┐
│ 分类说明与效果展示             │ 字典项表格                   │
│ - 用途                         │ - 排序                       │
│ - 使用页面                     │ - 字典键                     │
│ - 字段规则                     │ - 显示值                     │
│ - 效果预览                     │ - 扩展值                     │
│ - 风险提示                     │ - 状态 / 操作                │
└──────────────────────────────┴─────────────────────────────┘
```

PC 端建议保留“全部分类”视图，但默认进入第一个可见业务分类，避免首屏同时堆叠所有分类导致说明面板无法聚焦。

### 最终组件树

建议将页面拆成以下组件，保持职责单一：

```text
DictManagement.vue
├── DictPageHeader
│   ├── 分类搜索
│   ├── 显示系统配置开关
│   └── 当前分类主操作按钮
├── DictDomainTabs
│   └── 业务字典 / 系统字典 / UI配置 / 视觉映射
├── DictCategoryTabs
│   └── 当前分组下的分类 chip
├── DictWorkbench
│   ├── DictCategorySidePanel
│   │   ├── DictCategoryHelpPanel
│   │   └── DictCategoryPreview
│   └── DictItemsPanel
│       ├── DictTableToolbar
│       └── DictItemsTable
└── DictEditDialog
```

组件职责：

| 组件 | 职责 | 数据来源 |
|------|------|----------|
| `DictPageHeader` | 标题、搜索、系统配置开关、主操作按钮 | `selectedCategory`、`showSystemConfig` |
| `DictDomainTabs` | 一级分组切换 | `DictCategoryDomain`、`DICT_CATEGORY_META` |
| `DictCategoryTabs` | 二级分类切换和数量展示 | `visibleCategories`、`dicts` |
| `DictCategoryHelpPanel` | 当前分类用途、规则、风险和归属入口 | `DictCategoryMeta` |
| `DictCategoryPreview` | 当前分类真实效果展示 | `SysDict[]`、`previewType` |
| `DictItemsTable` | 字典项维护主表格 | `SysDict[]` |
| `DictEditDialog` | 新增/编辑表单和分类规则提示 | `DictCategoryMeta`、`SysDict` |

如果开发周期紧，可以先只拆 `DictCategoryHelpPanel` 和 `DictCategoryPreview`，但 `DictManagement.vue` 中必须预留上述结构的 class 和状态边界，避免后续再大拆。

### PC 端操作区细化

PC 端头部操作区建议按优先级从左到右排列：

```text
标题与说明
├── 数据字典
└── 管理系统编码值、显示名称和扩展配置

右侧操作
├── 分类搜索
├── 显示系统配置开关
└── 新建字典
```

细节要求：

- “新建字典”默认基于当前选中分类创建；当前为“全部”时打开弹窗后必须先选分类。
- 当前分类为受保护分类时，主按钮变为禁用或替换为“前往归属页面”。
- 分类搜索只搜索分类，不搜索表格行；表格行搜索如后续需要应独立实现，避免筛选语义混乱。
- 系统配置开关切换后，应保持用户当前分组上下文；如果当前分类不可见，再自动切回第一个可见分类。

### 移动端布局

移动端保留当前横向滚动分类页签，升级为：

```text
顶部栏：数据字典 / 新建
系统配置开关
分类页签
当前分类说明卡
效果展示折叠区
字典项卡片列表
```

移动端说明卡默认展示摘要，效果展示可折叠，避免挤占列表操作空间。

### 移动端操作细化

- 顶部只保留标题和新增按钮，避免头部过高。
- 分类页签使用横向滚动 chip，当前分类必须始终有清晰高亮。
- 当前分类说明默认展示 2-3 行摘要，提供“查看规则”展开。
- 效果展示默认折叠，折叠标题显示“效果展示”和当前启用项数量。
- 字典项卡片内操作按钮固定在右侧或底部，不与长文本混排。
- 长 JSON、长备注、长扩展值默认截断，点击后用弹窗或展开区查看。

### 布局验收标准

| 检查项 | 标准 |
|--------|------|
| 默认进入页面 | 用户 3 秒内能识别当前分类、分类用途、可执行操作 |
| 切换分类 | 页签、说明、预览、表格同步变化，无残留旧分类内容 |
| PC 表格可用性 | 右侧操作列在常见 1366px 宽度下可见或可通过表格自身横向滚动访问 |
| 说明面板 | 不超过主工作区宽度的 30%-35%，不能喧宾夺主 |
| 预览区 | 使用真实字典项生成，最多展示 3-5 个代表项，不形成第二张大表 |
| 移动端 | 无页面级横向滚动；页签横向滚动仅限页签容器 |
| 空数据 | 空分类仍展示规则说明、示例预览和新增入口 |
| 受保护分类 | 操作入口只读，维护入口明确 |

## 分类页签设计

### 分组页签

按 `DictCategoryDomain` 组织一级分组：

| 分组 | domain | 展示规则 |
|------|--------|----------|
| 全部 | virtual | 展示当前可见分类的统计入口，不作为默认编辑态 |
| 业务字典 | `business_dict` | 默认展示，包含币种、计量单位、产地、客户 |
| 系统字典 | `system_dict` | 包含状态、角色、审批、操作日志等 |
| UI配置 | `ui_config` | 仅在“显示系统配置”开启后展示，默认只读 |
| 视觉映射 | `visual_mapping` | 仅在“显示系统配置”开启后展示，默认只读 |
| 内部 | `internal` | 预留，默认不显示或只在系统配置开启后显示 |

### 分类页签

二级分类页签来自后端 `getDictCategories(true)` 与前端 `DICT_CATEGORY_META` 的交集和补充：

- 后端存在、元数据存在：按元数据显示。
- 后端存在、元数据不存在：归入“未登记分类”，标签使用 `CATEGORY_LABELS[category] || category`，并显示“缺少分类说明”的治理提示。
- 元数据存在、后端暂无数据：可显示为空分类页签，允许创建首个字典项；受保护分类不允许创建。

### 选中状态

- `selectedCategory` 仍作为当前分类状态。
- 切换分类时调用 `loadDicts()`，请求 `/api/dict?category=xxx`。
- “全部”视图可继续请求 `/api/dict`，但说明和效果展示切换为“字典总览”。
- URL 可选增强：使用 query `?category=currency` 保持刷新后定位；若实现，需要同步路由解析和切换。

## 分类说明元数据扩展

在 `frontend/src/constants/dictCategoryMeta.ts` 扩展 `DictCategoryMeta`，建议新增字段：

```ts
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

  helpTitle?: string
  usage?: string
  usedIn?: string[]
  keyRule?: string
  valueRule?: string
  extraValueRule?: string
  editWarning?: string
  examples?: Array<{
    key: string
    value: string
    extra?: string
    note?: string
  }>
  previewType?: 'select' | 'badge' | 'color' | 'icon' | 'json' | 'readonly' | 'text'
}
```

说明：

- `usage`：一句话说明该分类解决什么问题。
- `usedIn`：列出主要使用页面或模块，例如“产品编辑页”“价格维护页”“操作日志页”。
- `keyRule`：说明 `dictKey` 是否可改、推荐编码格式和业务含义。
- `valueRule`：说明 `dictValue` 展示在哪里。
- `extraValueRule`：说明 `extraValue` 的格式，例如颜色、图标名、JSON 或货币符号。
- `editWarning`：说明停用、删除或改 key 的影响。
- `examples`：用于帮助面板和空数据预览，不替代真实字典数据。
- `previewType`：决定效果展示组件的默认形态。

## 分类说明内容建议

首批至少补齐当前 `DICT_CATEGORY_META` 已登记分类：

| 分类 | 帮助重点 | 效果展示 |
|------|----------|----------|
| `currency` | 用于价格币种和货币符号；`dictKey` 为币种编码，`extraValue` 为符号 | 价格文本：`¥ 1,250 / 吨`、币种下拉 |
| `unit` | 用于产品报价单位；`dictKey` 与 `dictValue` 通常一致 | 单位下拉、价格单位后缀 |
| `origin` | 用于产品产地；可新增地区编码 | 产地下拉、产品详情标签 |
| `customer` | 用于客户选择；`extraValue` 可承载联系信息 JSON，后续建议规范化 | 客户下拉、联系人摘要 |
| `common_status` | 启用/停用等通用状态；`extraValue` 为颜色 | 状态标签和开关旁状态文案 |
| `user_role` | 用户角色展示；`extraValue` 为图标名 | 用户角色标签 |
| `dept_type` | 部门类型；`extraValue` 为颜色 | 部门类型色块标签 |
| `operation_type` | 操作日志动作类型 | 操作日志筛选和列表标签 |
| `operation_module` | 操作日志所属模块 | 日志模块标签 |
| `approval_status` | 审批流程状态；`extraValue` 为颜色 | 审批状态标签 |
| `workflow_type` | 审批工作流类型 | 审批配置下拉 |
| `node_type` | 审批节点类型 | 节点类型标签 |
| `approval_action` | 审批动作 | 审批历史动作标签 |
| `change_type` | 数据变更类型 | 审批/日志变更类型标签 |
| `business_type` | 审批或日志归属业务 | 业务类型标签 |
| `style` / `theme` / `color_scheme` / `layout_style` / `font_preset` | 受保护，提示前往样式设置 | 只读配置摘要 |
| `home_layout` / `home_widget` | 受保护，提示前往样式设置 | 首页布局摘要 |
| `category_visual_config` / `category_visual_custom_combo` | 受保护，JSON 较复杂，提示前往分类视觉设置 | 分类视觉色卡和 JSON 摘要 |

后续新增分类时，必须同步补齐 `DictCategoryMeta` 帮助字段。若未补齐，页面应显示“该分类暂无使用说明，请在分类元数据中补充”。

### 重点分类预览样例

预览必须尽量贴近实际业务页面，不做泛泛的装饰卡片：

| 分类 | 预览标题 | 预览内容 | 数据规则 |
|------|----------|----------|----------|
| `currency` | 价格展示效果 | 显示 `{extraValue} 1,250.00 / 吨` 和一个币种下拉外观 | 符号来自 `extraValue`，币种名称来自 `dictValue` |
| `unit` | 报价单位效果 | 显示“价格：1,250.00 {dictValue}”和单位选项 chip | 单位文本来自 `dictValue` |
| `origin` | 产品产地效果 | 显示产品详情中的“产地”标签和产地下拉样式 | 标签来自启用项，最多 5 个 |
| `customer` | 客户选择效果 | 显示客户选择行：客户名、联系人、电话摘要 | 联系人从 `extraValue` JSON 解析，解析失败时只显示客户名 |
| `common_status` | 状态标签效果 | 显示启用/停用 badge 和开关旁文案 | 颜色优先使用 `extraValue`，文案来自 `dictValue` |
| `user_role` | 用户角色效果 | 显示角色 badge 和图标名 | 图标名来自 `extraValue`，不硬编码角色中文 |
| `approval_status` | 审批状态效果 | 显示审批列表中的状态标签 | 颜色来自 `extraValue`，标签来自 `dictValue` |
| `operation_type` | 日志筛选效果 | 显示操作日志筛选 chip 和日志行动作标签 | 选项来自启用项 |
| `category_visual_config` | 分类视觉效果 | 显示色卡、图标名和 JSON 字段摘要 | JSON 只展示关键字段，不完整铺开 |

### 预览失败降级

- 颜色值非法：显示灰色默认色块，并标注“颜色格式异常”。
- JSON 解析失败：显示“JSON 格式异常”，保留复制原始值按钮。
- 当前分类无启用项：使用 `meta.examples`，并明确标注“示例效果”。
- 当前分类无元数据：只展示基础键值列表和“请补充分类说明”提示。

## 效果展示设计

### 组件拆分

建议从 `DictManagement.vue` 拆出两个小组件，减少页面继续膨胀：

| 组件 | 建议路径 | 职责 |
|------|----------|------|
| `DictCategoryHelpPanel.vue` | `frontend/src/components/dict/DictCategoryHelpPanel.vue` | 展示当前分类说明、规则、使用页面、风险提示 |
| `DictCategoryPreview.vue` | `frontend/src/components/dict/DictCategoryPreview.vue` | 根据分类和字典项渲染效果展示 |

如果本次希望最小改动，也可以先在 `DictManagement.vue` 内实现，后续再拆组件；但建议一次拆出，避免单文件复杂度继续升高。

### 预览规则

`DictCategoryPreview` 输入：

```ts
interface Props {
  category: string
  items: SysDict[]
  meta?: DictCategoryMeta
}
```

渲染策略：

- `previewType === 'select'`：渲染一个只读下拉样式，选项来自当前分类启用项。
- `previewType === 'badge'`：渲染若干标签，颜色优先取 `extraValue`。
- `previewType === 'color'`：渲染色块 + 标签 + 编码。
- `previewType === 'icon'`：渲染图标名 badge；如果项目已有图标映射，可展示图标，否则展示图标名。
- `previewType === 'json'`：渲染 JSON 摘要，展示关键字段数量和复制按钮，不展开长 JSON。
- `previewType === 'readonly'`：渲染只读说明和归属入口按钮。
- 默认 `text`：渲染键值示例列表。

预览数据优先使用真实 `items` 的前 3-5 个启用项；若当前分类暂无数据，使用 `meta.examples` 显示“示例效果”，并明确标注为示例。

### 受保护分类

受保护分类预览必须显示：

- “只读”标识。
- 归属页面，例如 `style-settings` 或 `category-visual-settings`。
- 跳转按钮：复用现有 `goToStyleSettings`，分类视觉配置如已有路由则跳转，否则显示“请在样式设置中维护”。
- 不展示新增、编辑、删除、启停操作。

## 字典项表格调整

PC 端表格保留现有列：

- 排序
- 字典键
- 显示值
- 扩展值
- 状态
- 备注
- 操作

优化点：

- 当前分类页签下只展示该分类表格，不再一页堆叠所有分类 section。
- “全部”视图可以展示分类总览卡，而不是展开所有表格。
- 表格上方显示当前分类标题、编码、条数、受保护状态。
- 扩展值列标题可根据 `extraValueMode` 动态提示，例如“扩展值（颜色）”“扩展值（JSON）”。
- 编辑弹窗中 `extraValue` 输入区根据 `extraValueMode` 显示占位和帮助文案。

## 表单规则调整

编辑/新增弹窗继续使用现有接口，但根据元数据增强提示：

- `keyMutable === false` 且编辑已有项时，`dictKey` 禁止修改或显示强警告。
- `extraValueMode === 'color'` 时，提供颜色输入框 + 文本输入，校验 `#RGB`、`#RRGGBB`、`#RRGGBBAA`。
- `extraValueMode === 'json'` 时，提供格式化和校验按钮，保存前校验 JSON 格式。
- `extraValueMode === 'readonly'` 时，不允许编辑扩展值。
- 新增未登记分类时，保留原有“新建分类”能力，但提交前提示需要补充分类元数据。

## 前后端与数据库一致性检查

本功能按前端体验增强设计，默认不修改数据库和后端接口。实施时仍需执行以下检查：

| 检查项 | 预期 |
--------|------|
| 前端 API 调用 | 继续使用 `GET /api/dict`、`GET /api/dict/categories?all=true`、`POST /api/dict`、`PUT /api/dict/{id}`、`DELETE /api/dict/{id}` |
| 请求/响应结构 | 继续使用 `SysDict`，字段为 `id`、`category`、`dictKey`、`dictValue`、`extraValue`、`sortOrder`、`status`、`remark`、`createdTime`、`updatedTime` |
| TypeScript 类型 | 如 `SysDict` 无变化，不改类型；仅扩展 `DictCategoryMeta` |
| Entity 与数据库 | `SysDict` 的 `@Table`、`@Column` 与 `sys_dict` 保持一致；本次不新增字段 |
| init.sql / migration | 不需要新增迁移；若补充初始化字典项，则必须同步 migration 和数据字典说明 |
| 数据字典文档 | 不改表结构；只需在项目设计文档或 UI 设计说明中记录页面说明能力 |

## 文档更新范围

功能开发完成后，按 AGENTS.md 变更流程同步以下文档：

| 文档 | 更新内容 |
|------|----------|
| `README.md` | 功能列表中补充“字典管理支持分类页签、使用说明和效果展示” |
| `docs/dev/开发指南.md` | 补充新增字典分类时必须维护 `dictCategoryMeta.ts` 帮助字段 |
| `docs/dev/项目设计文档.md` | 更新数据字典管理模块设计，说明分类元数据和页面预览机制 |
| `docs/dev/UI设计说明.md` | 更新字典管理页面 PC / 移动端布局和交互说明 |
| `docs/archive/项目完成总结.md` | 完成情况中补充该功能状态 |

本次规划阶段只新增本 plan，不修改上述长期文档。

## MVP 完成线

为保证一次交付足够稳，本功能按“必须完成 / 可延后”拆分：

### 必须完成

| 项目 | 完成标准 |
|------|----------|
| PC 分类页签 | 一级分组和二级分类可切换，当前分类高亮，数量正确 |
| 当前分类聚焦 | 默认进入第一个可见业务分类，“全部”只做总览 |
| 分类说明 | 所有已登记分类至少有用途、使用位置、字段规则、风险提示 |
| 效果展示 | 重点分类按真实字典项显示预览，空数据使用示例 |
| 受保护分类 | 只读、无误操作入口、有归属页面提示 |
| 表格维护 | 当前分类表格 CRUD 和启停保持原有能力 |
| 移动端 | 页签、说明摘要、预览折叠、卡片列表可用 |
| 验证 | 前端构建通过，PC 和移动端布局验收通过 |

### 可延后

| 项目 | 延后原因 |
|------|----------|
| URL query 分类定位 | 体验增强，不影响核心维护链路 |
| 分类搜索 | 分类数量目前可控，后续分类增多再强化 |
| 表格行搜索 | 与分类搜索语义不同，避免本次范围膨胀 |
| 完整图标渲染 | 若缺少统一图标映射，先展示图标名 |
| 未登记分类治理清单 | 可以先用提示兜底，后续做治理报表 |
| 后端分类元数据表 | 当前前端静态元数据足够，避免无必要数据库变更 |

## 质量门槛

以下任一项未满足，不建议标记功能完成：

1. 1366px PC 宽度下，当前分类说明和字典表格同时可用，表格操作列不丢失。
2. 移动端无页面级横向滚动，分类页签横向滚动不影响列表操作。
3. 切换分类后，说明、预览、表格和新增按钮的状态必须同步。
4. 受保护分类没有任何新增、编辑、删除、启停入口。
5. 所有状态、角色、审批、日志等编码显示仍来自字典项，不新增硬编码中文映射。
6. JSON 和颜色扩展值异常时有可理解的错误提示，不导致页面撑开或崩溃。
7. 新增、编辑、删除后，当前页面和字典缓存刷新行为与现有逻辑一致。
8. 新增分类如缺少元数据，页面明确提示补充说明，不静默展示空白帮助。

## 实现步骤

### Step 1：扩展分类元数据

1. 在 `frontend/src/constants/dictCategoryMeta.ts` 扩展 `DictCategoryMeta` 类型。
2. 为已有分类补充 `usage`、`usedIn`、`keyRule`、`valueRule`、`extraValueRule`、`editWarning`、`examples`、`previewType`。
3. 增加工具函数：
   - `getDomainLabel(domain)`
   - `getCategoriesByDomain(domain)`
   - `getVisibleCategoryMetas(categories, showSystemConfig)`
   - `getUnregisteredCategories(categories)`

### Step 2：重构页面状态

1. 保留 `selectedCategory`，新增 `selectedDomain`。
2. 计算 `visibleCategoryMetas`、`domainTabs`、`currentCategoryMeta`、`currentCategoryItems`。
3. 切换一级分组时自动选择该分组第一个可见分类。
4. 切换“显示系统配置”时，如果当前分类被隐藏，自动回到业务字典第一个分类。

### Step 3：搭建布局骨架并先验收

1. 在 `DictManagement.vue` 中先搭出页面骨架：头部操作区、分组页签、分类页签、说明预览区、表格区。
2. 先使用 mock 或现有计算数据填充分类名称和数量，不急于完善所有说明文案。
3. 按 `>=1440px`、`1024px-1439px`、`<1024px` 三档实现响应式布局。
4. 在 1366px 宽度下确认表格操作列可见或可通过表格自身横向滚动访问。
5. 验证“全部”视图不展开所有分类表格，只显示分类总览。
6. 骨架验收通过后再进入具体组件填充，避免后期返工。

### Step 4：实现 PC 分类页签

1. 将 PC 端头部下拉替换为分类页签区。
2. 一级分组使用紧凑 segmented tabs。
3. 二级分类使用可横向滚动或换行的 tab chips，显示分类名称、编码和数量。
4. 当前分类 chip 必须显示清晰 active 状态；受保护分类显示只读标识。
5. 保留搜索分类能力可选；如实现，搜索分类名称、编码和说明。

### Step 5：实现分类说明面板

1. 新增 `DictCategoryHelpPanel.vue`。
2. 展示分类名称、编码、domain、可编辑状态、扩展值模式。
3. 展示用途、使用页面、字段规则、风险提示。
4. 帮助信息按“用途 → 使用位置 → 字段规则 → 风险提示”的固定顺序展示。
5. 对未登记分类显示治理提示。
6. 对受保护分类显示归属页面和跳转按钮。

### Step 6：实现效果展示

1. 新增 `DictCategoryPreview.vue`。
2. 按 `previewType` 和 `extraValueMode` 渲染预览。
3. 真实数据为空时使用 `meta.examples`。
4. JSON 预览限制高度，提供复制按钮，避免撑开布局。
5. 颜色、状态、角色等预览不得硬编码中文标签，必须来自当前 `items`。
6. 预览最多展示 3-5 个代表项，更多项只显示数量，避免变成第二张表。

### Step 7：调整字典项表格和弹窗

1. PC 端当前分类只显示一张表格。
2. “全部”视图改为分类总览卡或分类统计列表。
3. 编辑弹窗按 `extraValueMode` 给出输入提示和校验。
4. `keyMutable === false` 的已有项禁止直接改 key 或二次确认。
5. 受保护分类继续禁止新增、编辑、删除、启停。
6. 表格列按“操作可达性”优化：字典键、显示值、状态、操作优先可见；备注和长扩展值允许截断展开。

### Step 8：移动端适配

1. 保留移动端横向分类页签。
2. 在列表前增加当前分类说明摘要。
3. 效果展示默认折叠，点击展开。
4. 卡片中的扩展值预览继续沿用颜色、图标、JSON 渲染。
5. 移动端完成后检查无页面级横向滚动，只有页签容器允许横向滚动。

### Step 9：验证与文档

1. 执行前端类型检查和构建。
2. 启动前端页面，检查 PC 和移动端布局。
3. 核对字典 CRUD 后缓存刷新和其他页面显示是否正常。
4. 按布局验收标准逐项截图或记录结果。
5. 按文档更新范围补齐长期文档。

## Verification

### 自动化检查

建议执行：

```bash
cd frontend
npm run type-check
npm run build
```

如项目没有 `type-check` 脚本，则执行：

```bash
cd frontend
npm run build
```

后端本次默认不改，如实施中改动后端或数据库，必须执行：

```bash
cd backend
mvn test
```

### 手工验收

| 场景 | 验收标准 |
|------|----------|
| PC 端进入字典管理 | 默认显示分类页签、当前分类说明、效果展示和字典项表格 |
| 切换分类 | 表格、说明、预览同步变化，请求参数正确 |
| 切换显示系统配置 | UI 配置和视觉映射分类出现，且标识为只读 |
| 受保护分类 | 无新增、编辑、删除、启停入口；显示归属页面提示 |
| 颜色扩展值分类 | 预览色块正确，非法颜色保存前提示 |
| JSON 扩展值分类 | JSON 摘要不撑破布局，非法 JSON 保存前提示 |
| 未登记分类 | 可查看数据，同时提示补充分类元数据 |
| 移动端 | 页签可滚动，说明不遮挡操作，卡片无横向溢出 |
| 字典缓存 | 新增、编辑、删除后页面刷新，其他页面再次加载能取到最新字典值 |

### 回归范围

- 产品编辑/详情中的币种、单位、产地显示。
- 客户相关页面的客户字典显示。
- 用户管理中的角色和状态显示。
- 审批页面中的审批状态、动作、工作流类型显示。
- 操作日志页面中的操作类型和操作模块显示。
- 样式设置和分类视觉配置的受保护分类不被字典管理页误改。

## 风险与处理

| 风险 | 处理 |
|------|------|
| `DictManagement.vue` 继续膨胀 | 拆出 `DictCategoryHelpPanel` 和 `DictCategoryPreview` |
| 分类元数据与实际字典分类不一致 | 页面显示“未登记分类”治理提示，开发指南要求新增分类同步维护元数据 |
| 帮助文案硬编码业务显示值 | 帮助文案只描述规则；具体选项和预览标签来自真实字典项 |
| JSON 预览撑开表格 | 限高、摘要、复制按钮，不在表格中完整展开 |
| 受保护分类误操作 | 继续使用 `isProtectedCategory` 作为操作入口的统一判断 |
| 移动端空间不足 | 说明摘要 + 折叠预览，列表操作优先 |

## 建议交付切分

### 第一阶段：可用版本

- 扩展分类元数据。
- PC / 移动端分类页签统一。
- 当前分类说明面板。
- 基础效果展示。
- 受保护分类只读说明。

### 第二阶段：增强版本

- URL query 保持分类定位。
- 分类搜索。
- 编辑弹窗按 `extraValueMode` 强校验和格式化。
- 未登记分类治理清单。

### 第三阶段：文档与治理

- 更新长期项目文档。
- 在开发指南中固化“新增字典分类必须补齐分类元数据说明”的规则。
- 如后续分类说明需要后台可配置，再评估新增字典分类元数据表。
