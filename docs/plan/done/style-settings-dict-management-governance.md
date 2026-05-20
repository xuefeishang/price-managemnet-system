# Style Settings 与 Dict Management 配置治理及页面规整计划

**创建日期：2026-05-20**  
**适用范围：** `style-settings` 样式设置页、`dict-management` 数据字典页、`useTheme`、`useDict`、`/api/style`、`/api/dict`  
**目标：** 明确两个管理入口的职责边界，规整样式设置页面的信息架构，降低配置混乱、误改风险和后续扩展成本。

---

## Plan 规范索引

| 规范章节 | 本文对应位置 |
|----------|--------------|
| Context | 一、背景与核心判断 |
| 实现方案 | 二、两个管理入口的专业定义；三、配置分类治理模型；四、Style Settings 页面规整方案；五、API 与数据结构建议；六、Dict Management 规整建议 |
| 关键参考文件 | 十二、关键参考文件 |
| 实现步骤 | 七、实施计划 |
| 开发执行细则 | 十三、开发实施规则；十四、详细开发步骤；十五、开发注意事项；十六、执行检查表；十七、里程碑与依赖关系；十八、回滚与灰度策略；二十、两阶段落地划分；二十二、扣分项优化后的执行与验收细则 |
| Verification | 八、验证清单 |

---

## 一、背景与核心判断

当前项目中有两个可配置入口：

1. **Style Settings（样式设置）**
   - 当前页面：`frontend/src/views/StyleSettings.vue`
   - 当前接口：`/api/style/config`、`/api/style/themes`、`/api/style/logo`
   - 当前存储：底层仍复用 `sys_dict` 的 `style`、`theme` 分类
   - 实际职责：系统名称、Logo、主题颜色、图表配色、字体、字号等全局体验配置

2. **Dict Management（字典管理）**
   - 当前页面：`frontend/src/views/DictManagement.vue`
   - 当前接口：`/api/dict`
   - 当前存储：`sys_dict`
   - 实际职责：下拉选项、编码显示名、角色命名、状态命名、币种符号、单位、产地、客户等业务/系统枚举

目前最大问题不是“数据都存在 `sys_dict`”本身，而是**治理边界不清**：

- `style`、`theme`、`home_layout`、`home_widget`、`category_visual_config` 等体验配置暴露在通用字典页面中，容易被当普通字典误改。
- `StyleSettings.vue` 把系统名称、预设主题、单色配置、图表配色、字体、字号、Logo、预览全部堆在一页，缺少层级、分组和保存边界。
- `useDict.ts` 的 `CATEGORY_LABELS` 同时承载业务字典分类和系统体验配置分类，导致“字典服务”越来越像“万能配置服务”。
- `/api/style` 已经是正确方向：它把样式配置包成专业 API。但底层存储仍被 `/api/dict` 直接暴露，治理上还没有隔离。

**建议结论：**

- 当前系统处于开发阶段，可以直接采用“两阶段落地”路线。
- 阶段一合并治理隔离与存储分离：Dict Management 回归业务字典，Style Settings 迁移到 `sys_style_config`、`sys_style_preset` 专用表。
- 阶段二只做最小服务增强：版本快照、手动回滚、缓存稳定化。
- 灰度发布、配置继承、WebSocket 实时同步只作为后续增强，不纳入当前必做范围。

---

## 二、两个管理入口的专业定义

### 2.1 Style Settings 的定义

**定位：全局品牌与体验配置中心。**

Style Settings 管理的是“系统看起来如何、读起来如何、品牌如何呈现”。它面向系统管理员和产品负责人，不应该变成底层键值表编辑器。

应纳入 Style Settings 的内容：

| 配置域 | 示例 | 管理方式 | 存储建议 |
|--------|------|----------|----------|
| 品牌识别 | 系统名称、Logo、Logo 尺寸 | 表单 + 文件上传 | `style` 或专用配置表 |
| 色彩方案 | 涨价色、跌价色、持平色、主色、图表色板 | 预设优先，允许高级自定义 | `style` + `theme`，后续可拆 `color_scheme` |
| 字体系统 | 标题字体、正文字体、数字字体 | 下拉选择 | `style` |
| 字号系统 | 紧凑、标准、大字体、无障碍 | 预设优先，高级自定义 | `style` |
| 布局风格 | 顶部导航、左侧导航、驾驶舱布局 | 预设选择 | 建议新增 `layout_style` |
| 首页组件 | 组件显隐、排序、首页展示密度 | 可视化配置 | `home_widget`，但通过专业页面/API 管理 |
| 预览与发布 | 实时预览、保存、重置、恢复默认 | 页面能力 | 不直接作为字典 |

不应纳入 Style Settings 的内容：

- 用户角色编码本身，如 `ADMIN`、`EDITOR`、`VIEWER`
- 业务状态显示名，如 `ACTIVE`、`INACTIVE`
- 产地、客户、币种、单位等业务选项
- 操作日志类型、审批状态、工作流类型等业务枚举

### 2.2 Dict Management 的定义

**定位：业务编码与显示名称管理中心。**

Dict Management 管理的是“系统中的编码值如何显示、哪些选项可选”。它服务于业务表单、筛选器、标签、权限名称等，不应负责复杂视觉主题。

应纳入 Dict Management 的内容：

| 字典域 | 示例 | 说明 |
|--------|------|------|
| 基础枚举 | `common_status`、`currency`、`unit` | 表单选项和标签显示 |
| 权限/身份显示 | `user_role`、`dept_type` | 显示名称可调整，但编码应谨慎修改 |
| 审批与流程枚举 | `approval_status`、`workflow_type`、`node_type` | 业务流程显示层 |
| 运维枚举 | `operation_type`、`operation_module`、`sync_status` | 日志和系统运维显示 |
| 主数据轻量项 | `origin`、`customer` | 当前作为字典管理，后续规模变大时可独立成表 |
| 菜单图标选项 | `menu_icon` | 如果只是图标候选项，可留在字典 |

应从 Dict Management 默认视图中隔离的内容：

| 分类 | 建议 | 原因 |
|------|------|------|
| `style` | 默认隐藏，只读或跳转 Style Settings | 直接编辑会绕过样式校验 |
| `theme` | 默认隐藏，只读或跳转 Style Settings | `extraValue` 是 JSON，误改风险高 |
| `home_layout` | 归入 Style Settings 的布局配置 | 更像体验配置，不是普通字典 |
| `home_widget` | 归入 Style Settings 的首页配置 | 涉及布局、显隐、排序 |
| `category_visual_config` | 建议新建“分类视觉配置”专门入口或作为 Style Settings 高级项 | JSON 结构复杂，不适合普通字典表单 |

---

## 三、配置分类治理模型

建议给所有 `sys_dict.category` 增加治理分层，前端先用常量实现，后端后续可持久化。

### 3.1 分类分层

| 层级 | category 示例 | 可见位置 | 编辑方式 |
|------|---------------|----------|----------|
| `business_dict` | `currency`、`unit`、`origin`、`customer` | Dict Management | 通用表单编辑 |
| `system_dict` | `common_status`、`user_role`、`operation_type` | Dict Management | 允许改显示名，限制改 key |
| `ui_config` | `style`、`theme`、`home_layout`、`home_widget` | Style Settings | 专业表单/API |
| `visual_mapping` | `category_visual_config` | Style Settings 高级项或独立页面 | JSON schema 表单 |
| `internal` | 技术内部分类 | 默认隐藏 | 开发/DB 维护 |

### 3.2 前端分类元数据建议

新增文件：

```text
frontend/src/constants/dictCategoryMeta.ts
```

建议结构：

```typescript
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
```

短期可以从 `useDict.ts` 中迁移 `CATEGORY_LABELS`，避免 `useDict` 同时承担“缓存服务”和“分类治理配置”的职责。

### 3.3 Dict Management 页面显示规则

默认只展示：

- `business_dict`
- `system_dict`

对 `ui_config`、`visual_mapping`：

- 默认隐藏在“高级/系统配置”开关之后。
- 开启后以只读方式展示，给出“前往样式设置”入口。
- ADMIN 可以查看，但不建议在通用字典表单中编辑。

### 3.4 后端保护规则

短期：

- `SysDictService.updateDict()` 对受保护分类增加校验。
- 非 ADMIN 禁止修改 `style`、`theme`、`home_layout`、`home_widget`、`category_visual_config`。
- 受保护分类优先通过专业 service 修改，如 `StyleConfigService`。

中期：

- 新增 `DictCategoryPolicy` 常量或配置类。
- `/api/dict/categories` 支持 `domain`、`includeProtected` 查询参数。
- `/api/dict` 返回分类元数据，前端不再硬编码全部治理规则。

---

## 四、Style Settings 页面规整方案

### 4.1 页面目标

把 Style Settings 从“长表单堆叠页”调整为“配置工作台”：

- 一屏能看懂当前主题状态。
- 每个配置域边界清楚。
- 普通管理员优先使用预设，不被高级字段淹没。
- 高风险 JSON/自定义配置不暴露为普通输入框。
- 保存行为清晰，避免无意保存半成品。

### 4.2 推荐信息架构

页面分为五个一级区：

1. **概览**
   - 当前系统名称、Logo、当前色彩方案、当前字号方案、当前布局方案
   - 最近更新时间
   - “保存更改”“重置更改”“恢复默认”操作

2. **品牌**
   - 系统显示名称
   - Logo 上传
   - Logo 尺寸
   - 导航栏品牌预览

3. **色彩**
   - 色彩方案预设卡片
   - 涨跌色语义预览
   - 图表色板预览
   - 高级自定义颜色折叠区

4. **排版**
   - 字体组合：标题/正文/数字
   - 字号预设：紧凑/标准/大字体/无障碍
   - 文本密度预览：表格、卡片、价格数字
   - 高级字号自定义折叠区

5. **布局**
   - 页面布局模式：顶部导航、左侧导航、深矿蓝驾驶舱、极简卡片
   - 首页组件显隐与顺序
   - PC/移动端适配说明
   - 布局缩略预览

页面右侧保留一个**实时预览面板**：

- 导航栏预览
- 价格卡片预览
- 表格行预览
- 图表色板预览
- 移动端窄屏预览切换

### 4.3 推荐页面布局

PC 端：

```text
┌─────────────────────────────────────────────────────────────┐
│ 全局样式设置                         [重置] [保存配置]      │
├───────────────┬───────────────────────────────┬─────────────┤
│ 配置导航       │ 当前配置区                      │ 实时预览    │
│ - 概览         │ ┌───────────────────────────┐ │             │
│ - 品牌         │ │ 品牌 / 色彩 / 排版 / 布局  │ │ 导航预览    │
│ - 色彩         │ │                           │ │ 价格预览    │
│ - 排版         │ │                           │ │ 表格预览    │
│ - 布局         │ └───────────────────────────┘ │ 图表预览    │
└───────────────┴───────────────────────────────┴─────────────┘
```

移动端：

```text
┌───────────────────────────────┐
│ 全局样式设置       [保存]      │
├───────────────────────────────┤
│ 横向分段控件：概览 品牌 色彩... │
├───────────────────────────────┤
│ 当前配置区                     │
├───────────────────────────────┤
│ 预览折叠面板                   │
└───────────────────────────────┘
```

### 4.4 UI 细节建议

**顶部操作栏**

- 使用固定操作栏，右侧放“保存配置”“重置本次修改”。
- 当 `editingConfig` 与服务端配置不一致时显示“有未保存更改”。
- 保存成功后显示当前生效状态，不强制刷新整页。

**配置导航**

- 使用左侧垂直导航或 tab，不要所有配置纵向堆叠。
- 每个导航项显示状态：已配置、默认、需检查。

**预设优先**

- 色彩、字号、布局都优先以“预设卡片”呈现。
- 自定义字段放在“高级设置”折叠区。
- 高级区加校验提示：颜色格式、字号格式、色板数量。

**预览真实化**

- 预览内容应模拟系统真实场景：
  - “电铜 ¥68,500 +2.5%”
  - “铁矿石 ¥812 -1.1%”
  - 表格列：产品、价格、涨跌、单位、更新时间
- 不要只展示孤立色块，要展示颜色在价格、标签、图表中的效果。

**避免页面嵌套卡片过多**

- 每个配置域是一个主区域。
- 预设项可以用轻量卡片。
- 不要在大卡片里再塞多层卡片，避免视觉拥挤。

**交互控件选择**

- 色彩：色板 + 色值输入 + 复制按钮
- 字号：预设卡片 + 高级输入
- 布局：缩略图卡片 + 说明
- Logo：上传按钮 + 当前 Logo 预览 + 尺寸 segmented control
- 组件显隐：switch
- 组件排序：后续可做拖拽，首版可用上移/下移按钮

### 4.5 `StyleSettings.vue` 代码规整建议

当前 `StyleSettings.vue` 已经过长，建议拆分：

```text
frontend/src/views/StyleSettings.vue
frontend/src/components/style-settings/StyleSettingsShell.vue
frontend/src/components/style-settings/StyleOverviewPanel.vue
frontend/src/components/style-settings/BrandSettingsPanel.vue
frontend/src/components/style-settings/ColorSchemePanel.vue
frontend/src/components/style-settings/TypographyPanel.vue
frontend/src/components/style-settings/LayoutStylePanel.vue
frontend/src/components/style-settings/StylePreviewPanel.vue
frontend/src/components/style-settings/AdvancedTokenPanel.vue
frontend/src/composables/useStyleSettingsForm.ts
frontend/src/constants/stylePresets.ts
```

拆分原则：

- `StyleSettings.vue` 只负责页面装配和数据加载保存。
- 表单状态、dirty 判断、reset、validation 放到 `useStyleSettingsForm.ts`。
- 预设数据从 `types/theme.ts` 迁移到 `constants/stylePresets.ts`。
- `types/theme.ts` 只保留类型定义，减少类型文件混入业务常量。
- 所有 CSS 变量写入仍由 `useTheme.ts` 统一执行。

---

## 五、API 与数据结构建议

### 5.1 短期 API 方案

保留现有 API：

| API | 用途 | 调整建议 |
|-----|------|----------|
| `GET /api/style/config` | 获取当前样式配置 | 扩展布局字段 |
| `PUT /api/style/config` | 保存样式配置 | 增加字段校验 |
| `GET /api/style/themes` | 获取主题预设 | 后续改名为 color schemes |
| `PUT /api/style/theme/{themeKey}` | 切换主题 | 兼容保留 |
| `POST /api/style/logo` | 上传 Logo | 保留 |

新增 API：

| API | 用途 |
|-----|------|
| `GET /api/style/presets` | 获取色彩、字号、布局全部预设 |
| `PUT /api/style/color-scheme/{schemeKey}` | 切换色彩方案 |
| `PUT /api/style/layout/{layoutKey}` | 切换布局方案 |
| `PUT /api/style/reset` | 恢复默认样式配置 |

### 5.2 DTO 扩展建议

`StyleConfigDTO` 增加：

```java
private String activeColorScheme;
private String activeLayoutStyle;
private String fontSizePreset;
private String layoutDensity;
private String homeWidgetConfig;
```

前端 `StyleConfig` 同步增加：

```typescript
activeColorScheme?: string
activeLayoutStyle?: string
fontSizePreset?: string
layoutDensity?: 'compact' | 'comfortable'
homeWidgetConfig?: HomeWidgetConfig[]
```

### 5.3 存储策略

**本轮推荐：直接完成存储分离，不再把新增样式配置写入 `sys_dict`。**

阶段一落地后的存储边界：

| 存储对象 | 职责 | 写入入口 | 备注 |
|----------|------|----------|------|
| `sys_dict` | 业务字典、系统字典 | `/api/dict`、Dict Management | 只保留角色、状态、币种、单位、产地、客户等编码显示名 |
| `sys_style_config` | 当前生效样式配置 | `/api/style/config`、Style Settings | 存系统名称、Logo、当前色彩、字号、布局、首页配置 |
| `sys_style_preset` | 样式预设 | `/api/style/presets`、初始化 SQL | 存色彩方案、布局方案、字号方案等可选预设 |
| `sys_style_version` | 样式版本快照 | `/api/style/versions`、保存/回滚流程 | 阶段二新增 |

旧 `sys_dict` 中的 `style`、`theme`、`color_scheme`、`layout_style`、`font_preset` 等数据只作为迁移来源和开发期兼容兜底，不再作为新增样式配置的主存储。迁移完成后：

- Dict Management 默认不展示这些旧样式分类。
- `/api/dict` 写接口禁止修改这些旧样式分类。
- `/api/style` 读取优先使用 `sys_style_*`，仅在新表无数据或迁移未完成时兼容读取旧 `sys_dict`。
- 不删除旧行、不修改旧行状态，避免影响回滚和 `CommonStatus` 枚举解析。

### 5.4 `extraValue` JSON 管理原则

`extra_value` 的治理原则调整为：新样式配置不再依赖 `sys_dict.extra_value`，复杂样式 JSON 进入 `sys_style_config.config_value` 或 `sys_style_preset.config_json`。

| 场景 | 处理规则 |
|------|----------|
| 旧 `theme.extra_value` | 仅作为迁移来源和兼容读取来源 |
| 普通业务字典 `extra_value` | 仍可存简短扩展值，如颜色、图标、备注编码 |
| 样式预设 JSON | 存入 `sys_style_preset.config_json` |
| 当前样式配置 JSON | 存入 `sys_style_config.config_value` |
| Dict Management 编辑 JSON | 默认禁止；只读格式化展示或跳转专业页面 |

所有进入 `sys_style_*` 的 JSON 必须经过 DTO/schema 校验，不允许前端直接提交任意 JSON 后透传入库。`SysDict.extraValue` 当前长度限制仍需保留在风险清单中，但它不再是阶段一扩展样式能力的主瓶颈。

---

## 六、Dict Management 规整建议

### 6.1 页面目标

Dict Management 应更像“字典运维台”，不是所有配置的裸编辑器。

推荐能力：

- 分类搜索和分组
- 业务字典/系统字典切换
- 受保护分类隐藏或只读
- JSON 扩展值智能展示
- 修改 key 时高风险确认
- 修改后刷新 `useDict` 缓存

### 6.2 页面结构建议

```text
┌─────────────────────────────────────────────────────────────┐
│ 数据字典                         [显示系统配置] [新建字典]   │
├───────────────┬─────────────────────────────────────────────┤
│ 分类侧栏       │ 字典项表格                                   │
│ - 业务字典     │ 分类说明 / 风险提示                           │
│ - 系统字典     │ key value extra status actions                │
│ - 视觉配置只读 │                                             │
└───────────────┴─────────────────────────────────────────────┘
```

### 6.3 关键交互规则

- `dictKey` 对已存在记录默认只读，点击“修改编码”后进入危险操作流程。
- 受保护分类展示 banner：“此分类由样式设置管理，请前往 Style Settings 修改。”
- `extraValue` 根据分类元数据渲染：
  - color：色块 + 色值
  - icon：图标名 + 图标预览
  - json：格式化只读 + 复制
  - text：普通文本
- 删除系统字典项时必须二次确认，并提示影响范围。

---

## 七、实施计划

本章是“阶段一：基础分离”的内部任务拆解，不再代表独立上线阶段。真正的上线节奏以“二十、两阶段落地划分”为准。

### Step 0：确认边界与冻结规则（0.5 天）

产出：

- 确认本文档的职责边界。
- 明确 `style`、`theme`、`home_layout`、`home_widget`、`category_visual_config` 是否从字典页面默认隐藏。
- 确认本轮直接采用两阶段落地路线：阶段一完成专用表分离，阶段二完成最小版本回滚。

验收：

- 产品/开发对“Style Settings 管体验，Dict Management 管编码显示”达成一致。
- 后续新增配置能判断应该进哪个入口。

### Step 1：配置分类元数据治理（0.5 天）

前端任务：

- 新增 `frontend/src/constants/dictCategoryMeta.ts`。
- 将 `CATEGORY_LABELS` 从 `useDict.ts` 迁移或引用到分类元数据。
- 增加 `getCategoryMeta(category)`、`isProtectedCategory(category)` 工具函数。

后端任务：

- 新增 `DictCategoryPolicy` 常量类，先不建表。
- 在 `SysDictService` 中对受保护分类增加编辑限制。

验收：

- 字典页面能识别业务字典、系统字典、体验配置。
- 受保护分类不会被普通编辑流程误改。

### Step 2：Dict Management 保护性改造（1 天）

前端任务：

- 默认隐藏 `ui_config` 与 `visual_mapping` 分类。
- 增加“显示系统配置”开关。
- 受保护分类只读展示，并提供跳转 Style Settings 的按钮。
- 已有字典项编辑时默认锁定 `category` 与 `dictKey`。
- `extraValue` 按元数据展示。

后端任务：

- `/api/dict/categories` 可增加 `includeProtected` 或 `domain` 参数。
- 更新权限校验，EDITOR 不允许修改受保护分类。

验收：

- 普通字典维护不再看到样式底层配置。
- 管理员仍可排查底层数据，但不会直接误编辑。
- `useDict` 缓存刷新逻辑保持正常。

### Step 3：Style Settings 页面信息架构重构（2 天）

前端任务：

- 拆分 `StyleSettings.vue` 为 shell + panel 组件。
- 新增左侧配置导航/顶部 tabs。
- 建立统一 `editingConfig` 表单 composable。
- 增加 dirty 状态、reset 当前修改、恢复默认。
- 右侧预览面板组件化。
- 色彩、排版、品牌、布局分区显示。

建议文件：

```text
frontend/src/components/style-settings/BrandSettingsPanel.vue
frontend/src/components/style-settings/ColorSchemePanel.vue
frontend/src/components/style-settings/TypographyPanel.vue
frontend/src/components/style-settings/LayoutStylePanel.vue
frontend/src/components/style-settings/StylePreviewPanel.vue
frontend/src/composables/useStyleSettingsForm.ts
frontend/src/constants/stylePresets.ts
```

验收：

- 页面不再是单一长表单。
- PC 端配置区与预览区清晰分离。
- 移动端使用分段控件切换配置区，预览可折叠。
- 保存、重置、恢复默认语义清楚。

### Step 4：色彩/布局/字号预设体系扩展（1.5 天）

前端任务：

- 将 `PRESET_THEMES` 升级为 `COLOR_SCHEMES`。
- 新增 `LAYOUT_STYLES`、`FONT_SIZE_PRESETS` 的统一预设模型。
- `useTheme.applyThemeToCSS()` 增加布局 CSS 变量：
  - `--app-nav-bg`
  - `--app-nav-text`
  - `--app-page-bg`
  - `--app-card-bg`
  - `--app-card-radius`
  - `--app-card-shadow`
  - `--app-density`

后端任务：

- `StyleConfigDTO` 增加 `activeColorScheme`、`activeLayoutStyle`、`fontSizePreset`。
- `StyleConfigService` 增加切换色彩方案、切换布局方案方法。
- `init.sql` 增加 `color_scheme`、`layout_style` 初始化数据。

验收：

- 色彩方案和布局方案可独立选择。
- 旧的 `activeTheme` 兼容保留，不影响现有配置。
- 刷新页面后配置保持。

### Step 5：首页与全局布局联动（2 天）

前端任务：

- `Layout.vue` 根据 `activeLayoutStyle` 切换顶部/左侧/驾驶舱布局变量。
- `Home.vue` 根据布局配置切换驾驶舱密度、卡片样式和组件顺序。
- 图表组件使用动态 `chartColors`。
- 保持移动端优先采用稳定布局，避免复杂布局在小屏溢出。

验收：

- 选择“深矿蓝 + 驾驶舱布局”后首页与导航有明显变化。
- 普通业务页面不出现文本溢出、横向滚动或颜色冲突。
- 字典管理、用户管理、产品管理页面仍保持可读和可操作。

### Step 6：文档与数据字典同步（0.5 天）

按项目规范更新：

- `README.md`：补充样式设置与字典管理职责说明。
- `docs/dev/开发指南.md`：补充配置分类治理规则。
- `docs/dev/项目设计文档.md`：补充 `/api/style` 扩展、字典分类元数据。
- `docs/dev/UI设计说明.md`：补充 Style Settings 新信息架构。
- `backend/src/main/resources/数据字典.md`：若变更表结构或新增字典分类，更新数据字典。
- `docs/archive/项目完成总结.md`：更新功能完成情况。

验收：

- 代码、API、数据库、文档一致。
- 新增分类和字段在文档中可追溯。

---

## 八、验证清单

### 8.1 前后端一致性

- `/api/style/config` 返回字段与 `frontend/src/types/theme.ts` 一致。
- `/api/style/presets` 返回结构与前端预设类型一致。
- `/api/dict/categories` 的过滤参数与前端调用一致。
- `chartColors` 字符串/数组转换逻辑统一，避免前后端类型漂移。

### 8.2 后端与数据库一致性

- 若 `sys_dict.extra_value` 改为 `TEXT`，同步检查：
  - `SysDict.extraValue @Column`
  - `init.sql`
  - `数据字典.md`
  - 相关 Repository 查询
- 新增 `color_scheme`、`layout_style` 分类后，初始化 SQL 要可重复执行。
- 受保护分类编辑限制要覆盖 create/update/delete/batchCreate。

### 8.3 前端 UI 验证

- PC 端：1366、1440、1920 宽度无横向滚动。
- 移动端：375、390、430 宽度文本不溢出。
- Style Settings 保存前后预览一致。
- Dict Management 默认不显示样式底层配置。
- 字典修改后下拉选项、角色显示、状态显示正常刷新。

### 8.4 回归场景

- 登录后系统名称和 Logo 正常显示。
- 产品列表涨跌颜色正确。
- 首页图表色板正确。
- 用户角色显示来自字典服务。
- 状态下拉仍来自 `common_status`。
- 样式设置保存失败时不污染本地预览状态。

---

## 九、风险与处理

| 风险 | 表现 | 处理 |
|------|------|------|
| 字典与样式仍共用 `sys_dict` | 底层分类容易互相影响 | 用分类元数据和受保护分类先隔离 |
| `extra_value` 长度不足 | 复杂 JSON 保存失败或截断 | 本轮避免复杂 JSON；必要时改 TEXT 并同步文档 |
| 页面拆分引入状态不同步 | 预览与保存值不一致 | 用单一 `useStyleSettingsForm` 管理状态 |
| 旧 `theme` 与新 `color_scheme` 并存 | 迁移期概念重复 | 保留兼容字段，UI 只展示新概念 |
| 移动端布局复杂 | 小屏溢出 | 移动端优先固定稳定布局，仅应用色彩/字号 |

---

## 十、推荐优先级

**P0：必须先做**

- 明确分类边界。
- Dict Management 默认隐藏/只读样式配置分类。
- Style Settings 拆分为品牌、色彩、排版、布局、预览。

**P1：本轮建议做**

- 色彩方案与布局方案分离。
- `StyleSettings.vue` 组件化。
- `useDict.ts` 的分类标签迁移到分类元数据。
- 增加 dirty 状态和恢复默认。

**P2：后续增强**

- 专用配置表。
- 分类视觉配置专门页面。
- 首页组件拖拽排序。
- 样式配置版本历史与回滚。

---

## 十一、最终定义一句话

**Dict Management 管“编码值是什么意思、哪些选项可选”；Style Settings 管“系统如何呈现、如何阅读、如何形成品牌体验”。**

这个边界建立后，后续无论新增角色名称、下拉选项、主题色、Logo、首页布局，都能明确进入哪个入口，页面也不会继续膨胀成难以维护的万能配置页。

---

## 十二、关键参考文件

前端：

| 文件 | 作用 |
|------|------|
| `frontend/src/views/StyleSettings.vue` | 当前样式设置页面，后续拆分的主对象 |
| `frontend/src/views/DictManagement.vue` | 当前字典管理页面，后续增加分类治理与保护规则 |
| `frontend/src/composables/useTheme.ts` | 样式配置加载、保存、CSS 变量应用 |
| `frontend/src/composables/useDict.ts` | 字典缓存、显示名获取、下拉选项获取 |
| `frontend/src/types/theme.ts` | 样式配置类型与现有预设定义 |
| `frontend/src/api/style.ts` | 样式设置 API 封装 |
| `frontend/src/api/dict.ts` | 字典管理 API 封装 |
| `frontend/src/style/variables.css` | 全局 CSS 变量基础 |

后端：

| 文件 | 作用 |
|------|------|
| `backend/src/main/java/com/pricemanagement/controller/StyleConfigController.java` | 样式设置接口 |
| `backend/src/main/java/com/pricemanagement/service/StyleConfigService.java` | 样式配置业务逻辑，当前读取/写入 `sys_dict` |
| `backend/src/main/java/com/pricemanagement/dto/StyleConfigDTO.java` | 样式配置传输对象 |
| `backend/src/main/java/com/pricemanagement/controller/SysDictController.java` | 字典管理接口 |
| `backend/src/main/java/com/pricemanagement/service/SysDictService.java` | 字典管理业务逻辑 |
| `backend/src/main/java/com/pricemanagement/entity/SysDict.java` | `sys_dict` ORM 实体 |
| `backend/src/main/resources/init.sql` | 初始化表结构和字典数据 |
| `backend/src/main/resources/数据字典.md` | 数据库字典文档 |

项目文档：

| 文件 | 作用 |
|------|------|
| `docs/dev/项目设计规范.md` | 字典与动态配置现有规范 |
| `docs/dev/项目设计文档.md` | API、数据库、功能模块设计 |
| `docs/dev/UI设计说明.md` | UI 设计规范与页面清单 |
| `docs/plan/dashboard-blue-theme-upgrade.md` | 当前蓝色主题/布局升级计划，可作为阶段 4/5 的输入 |

---

## 十三、开发实施规则

本章作为后续开发的强制执行规则。实现过程中如果发现规则与现有代码冲突，应先在本计划中补充决策记录，再修改代码。

### 13.1 配置归属判定规则

新增任何可配置项前，必须先回答以下问题：

| 问题 | 进入 Dict Management | 进入 Style Settings |
|------|----------------------|---------------------|
| 它是不是业务编码的显示名？ | 是 | 否 |
| 它是不是下拉框、筛选项、标签选项？ | 是 | 否 |
| 它是否影响颜色、字体、Logo、布局、密度、首页组件？ | 否 | 是 |
| 它是否需要专业校验或预览？ | 通常否 | 是 |
| 它是否是复杂 JSON 配置？ | 默认否 | 是，或独立配置页 |

判定结果：

- 业务枚举、显示名称、普通选项进入 `Dict Management`。
- 品牌、视觉、布局、首页组件进入 `Style Settings`。
- 复杂视觉映射如 `category_visual_config` 不允许继续使用普通字典表单裸编辑，应进入 Style Settings 高级区或后续独立页面。

### 13.2 受保护分类规则

以下分类定义为受保护分类：

```text
style
theme
color_scheme
layout_style
font_preset
home_layout
home_widget
category_visual_config
```

开发规则：

- Dict Management 默认不展示受保护分类。
- 打开“显示系统配置”后，受保护分类只能只读展示。
- 受保护分类的新增、编辑、删除必须通过专业 API 或专业页面完成。
- 后端 `SysDictService` 必须拒绝普通 `/api/dict` 写接口修改受保护分类。
- 允许 ADMIN 通过专业接口修改受保护分类，但仍必须经过 DTO 校验。

### 13.3 前端职责规则

| 模块 | 应负责 | 不应负责 |
|------|--------|----------|
| `useDict.ts` | 字典缓存、显示名获取、下拉选项获取 | 样式 token、主题切换、布局切换 |
| `useTheme.ts` | 样式配置加载、CSS 变量写入、主题状态 | 业务字典显示名 |
| `DictManagement.vue` | 普通字典维护、受保护分类只读查看 | 样式配置表单 |
| `StyleSettings.vue` | 品牌、色彩、排版、布局、预览 | 角色/状态/币种等业务字典维护 |
| `types/theme.ts` | 样式相关类型 | 大量预设常量堆叠 |
| `constants/stylePresets.ts` | 色彩、布局、字号预设 | 接口请求逻辑 |
| `constants/dictCategoryMeta.ts` | 字典分类元数据和治理规则 | 字典缓存请求 |

硬性要求：

- 前端不得硬编码角色、状态、币种、单位的中文显示名，继续通过 `useDict` 获取。
- 前端不得直接从 `useDict` 读取 `style` 分类来应用主题，继续通过 `useTheme` 和 `/api/style`。
- Style Settings 内部允许展示“色彩方案、布局方案”等预设名称，但这些预设最终应来自 `stylePresets.ts` 或 `/api/style/presets`，不要散落在组件模板中。

### 13.4 后端职责规则

| 模块 | 应负责 | 不应负责 |
|------|--------|----------|
| `SysDictController` | 普通字典 CRUD | 直接修改样式配置 |
| `SysDictService` | 字典唯一性、排序、状态、受保护分类拦截 | 解析复杂样式 JSON 并应用 CSS 语义 |
| `StyleConfigController` | 样式配置读写、预设切换、Logo 上传 | 暴露裸 `SysDict` 结构 |
| `StyleConfigService` | 样式 DTO 校验、配置落库、缓存清理 | 业务字典维护 |
| `StyleConfigDTO` | 当前生效样式配置 | 字典项通用字段 |

硬性要求：

- `/api/style` 返回的是业务化 DTO，不返回 `SysDict` 实体。
- `/api/dict` 返回普通字典项，但对受保护分类的写操作必须拦截。
- 所有样式写操作必须清理 `style` 缓存；影响字典缓存的配置迁移也要同步清理 `dict` 缓存。
- 所有数据变更接口继续按项目规范添加或保留操作日志。

### 13.5 数据库与初始化规则

对仍保留在 `sys_dict` 的业务字典，必须遵守：

- 新增字典分类必须写入 `init.sql`，并保证重复执行安全。
- 新增受保护分类必须同步更新 `dictCategoryMeta.ts` 和后端 `DictCategoryPolicy`。
- `extra_value` 存 JSON 时必须有 DTO/schema 校验，不能只依赖前端输入。
- 如果单项配置可能超过 500 字符，必须先评估是否将 `extra_value` 改为 `TEXT` 或拆专用表。
- 如果修改 `SysDict.extraValue` 字段类型，必须同步更新 Entity、`init.sql`、`数据字典.md`、`docs/dev/项目设计文档.md`。

对样式配置，阶段一后不得继续新增到 `sys_dict`，应写入 `sys_style_config` 或 `sys_style_preset`。

### 13.6 API 契约规则

开发顺序必须先定契约再改页面：

1. 先更新后端 DTO 字段。
2. 再更新前端 TypeScript 类型。
3. 再更新 API 封装。
4. 最后改页面和组件。

特别注意：

- `chartColors` 当前存在前端数组、后端字符串的差异。改造时应统一 API 契约，推荐后端 DTO 使用 `List<String>`，前端保持 `string[]`。
- 迁移期如必须兼容字符串，兼容逻辑只能放在 API/composable 层，不要散落到多个组件。
- 新增 `activeColorScheme`、`activeLayoutStyle` 时，保留 `activeTheme` 作为兼容字段，UI 不再主推旧概念。

### 13.7 UI 开发规则

Style Settings 页面必须按“工作台”而不是“长表单”实现：

- 页面必须有一级配置导航：概览、品牌、色彩、排版、布局。
- 保存按钮必须固定在页面顶部或底部操作区，不能只出现在长页面底部。
- 必须显示未保存状态。
- 预览必须使用真实业务样例，而不只是色块。
- 高级配置默认折叠。
- 不允许出现文本溢出、按钮文字挤压、PC 横向滚动。
- 不允许用普通文本按钮代替明显可图标化的操作，如上传、重置、删除、上移、下移。

Dict Management 页面必须按“字典运维台”实现：

- 默认只展示普通业务字典和系统字典。
- 受保护分类要有醒目的只读说明。
- 已存在字典项默认不允许直接改 `category` 和 `dictKey`。
- 删除、修改编码、停用系统字典项都必须有确认提示。

---

## 十四、详细开发步骤

本章把“七、实施计划”拆成可执行任务。建议按顺序实现，不要先做页面大改再补治理规则。

### 14.1 Step 1：建立分类治理常量

目标：前后端先知道哪些分类属于普通字典，哪些属于样式配置。

前端改动：

| 文件 | 操作 |
|------|------|
| `frontend/src/constants/dictCategoryMeta.ts` | 新增分类元数据、受保护分类列表、工具函数 |
| `frontend/src/composables/useDict.ts` | 引用分类元数据，逐步迁移 `CATEGORY_LABELS` |
| `frontend/src/views/DictManagement.vue` | 使用分类元数据展示标签和控制编辑权限 |

后端改动：

| 文件 | 操作 |
|------|------|
| `backend/src/main/java/com/pricemanagement/config/DictCategoryPolicy.java` | 新增受保护分类常量 |
| `backend/src/main/java/com/pricemanagement/service/SysDictService.java` | 写接口增加分类保护校验 |

验收：

- 普通字典分类标签正常显示。
- `style`、`theme` 等分类能被识别为受保护分类。
- 通过普通 `/api/dict` 修改受保护分类会失败并返回明确错误。

### 14.2 Step 2：保护 Dict Management

目标：防止用户从字典页误改样式底层配置。

前端改动：

| 文件 | 操作 |
|------|------|
| `DictManagement.vue` | 分类筛选默认排除受保护分类 |
| `DictManagement.vue` | 增加“显示系统配置”开关 |
| `DictManagement.vue` | 受保护分类只读展示，隐藏编辑/删除/启停按钮 |
| `DictManagement.vue` | 新增“前往样式设置”入口 |
| `DictManagement.vue` | 编辑已有字典时锁定 `category`、`dictKey` |

后端改动：

| 文件 | 操作 |
|------|------|
| `SysDictController.java` | 可选增加 `includeProtected`、`domain` 参数 |
| `SysDictService.java` | create/update/delete/batchCreate 全部应用保护规则 |

验收：

- 默认进入字典页看不到 `style`、`theme`、`home_widget`。
- 开启系统配置查看后，只能查看不能编辑。
- 普通业务字典增删改查不受影响。

### 14.3 Step 3：拆分 Style Settings 页面

目标：把当前长组件拆成可维护的配置工作台。

新增文件：

```text
frontend/src/components/style-settings/BrandSettingsPanel.vue
frontend/src/components/style-settings/ColorSchemePanel.vue
frontend/src/components/style-settings/TypographyPanel.vue
frontend/src/components/style-settings/LayoutStylePanel.vue
frontend/src/components/style-settings/StylePreviewPanel.vue
frontend/src/composables/useStyleSettingsForm.ts
frontend/src/constants/stylePresets.ts
```

改造顺序：

1. 先抽 `useStyleSettingsForm.ts`，保持页面 UI 不变。
2. 再抽 `StylePreviewPanel.vue`，保证预览结果一致。
3. 再抽品牌、色彩、排版 panel。
4. 最后改页面布局为配置导航 + 当前配置区 + 预览区。

验收：

- 拆分过程中每一步页面都能运行。
- 保存配置与当前行为一致。
- 重置默认、Logo 上传、主题切换不退化。
- `StyleSettings.vue` 只保留装配逻辑，不再承载大量表单细节。

### 14.4 Step 4：扩展色彩、排版、布局预设

目标：把当前 `theme` 概念升级为更清晰的色彩方案、字号方案、布局方案。

前端改动：

| 文件 | 操作 |
|------|------|
| `frontend/src/constants/stylePresets.ts` | 新增 `COLOR_SCHEMES`、`LAYOUT_STYLES`、`FONT_SIZE_PRESETS` |
| `frontend/src/types/theme.ts` | 新增 `ColorScheme`、`LayoutStyle`、`StylePresetResponse` 类型 |
| `frontend/src/composables/useTheme.ts` | 增加 `activeColorScheme`、`activeLayoutStyle`、布局 CSS 变量 |
| `frontend/src/api/style.ts` | 增加 presets、switch color scheme、switch layout API |

后端改动：

| 文件 | 操作 |
|------|------|
| `StyleConfigDTO.java` | 增加 `activeColorScheme`、`activeLayoutStyle`、`fontSizePreset` |
| `StyleConfigService.java` | 增加读取/保存/切换新字段 |
| `StyleConfigController.java` | 增加预设读取、色彩切换、布局切换接口 |
| `init.sql` | 初始化 `color_scheme`、`layout_style`、`font_preset` |

验收：

- 色彩方案切换只影响颜色，不改变布局。
- 布局方案切换只影响布局变量，不改变涨跌色。
- 刷新页面后配置持久化。
- 旧 `activeTheme` 配置仍能被兼容读取。

### 14.5 Step 5：全局应用样式配置

目标：让 Style Settings 的配置真正影响系统主要界面。

前端改动：

| 文件 | 操作 |
|------|------|
| `frontend/src/components/Layout.vue` | 读取布局 CSS 变量，支持顶部/左侧布局 |
| `frontend/src/views/Home.vue` | 支持驾驶舱布局变量和首页组件配置 |
| 图表组件 | 使用动态 chart colors |
| 业务列表页 | 使用统一背景、卡片、表格、字号变量 |

验收：

- 首页、产品列表、用户管理、字典管理、样式设置页面视觉一致。
- PC 和移动端都无明显布局破损。
- 图表颜色、价格涨跌色、表格字号均来自统一变量。

### 14.6 Step 6：补齐文档与数据字典

目标：满足项目“功能变更必须更新文档”的规范。

必须更新：

| 文档 | 更新内容 |
|------|----------|
| `README.md` | 功能列表补充样式设置/字典管理边界 |
| `docs/dev/开发指南.md` | 新增配置归属判定规则、受保护分类规则 |
| `docs/dev/项目设计文档.md` | 更新 API、DTO、字典分类、样式配置设计 |
| `docs/dev/UI设计说明.md` | 更新 Style Settings 页面信息架构 |
| `backend/src/main/resources/数据字典.md` | 新增分类或字段变更说明 |
| `docs/archive/项目完成总结.md` | 更新阶段完成状态 |

验收：

- 文档描述与代码一致。
- 新增 API、字段、分类都能在文档中找到。
- 数据库结构、Entity 注解、数据字典一致。

---

## 十五、开发注意事项

### 15.1 不要一次性大爆改

本次改造涉及页面、composable、API、后端 service、初始化 SQL 和文档，必须分阶段提交。推荐提交粒度：

1. 分类元数据和后端保护。
2. Dict Management 只读保护。
3. Style Settings 组件拆分，不改变行为。
4. 色彩/布局/字号预设扩展。
5. 全局布局联动。
6. 文档同步。

### 15.2 先保护，再美化

优先级必须是：

1. 防止误改受保护分类。
2. 明确两个入口边界。
3. 再重构 Style Settings UI。
4. 最后做深矿蓝、布局切换等视觉增强。

不要先做大视觉改造，否则底层配置仍可能被字典页误改。

### 15.3 保持兼容

- 不删除旧 `theme` 分类。
- 不立即删除 `activeTheme` 字段。
- 不破坏现有 `/api/style/theme/{themeKey}`。
- 新字段缺失时必须有默认值。
- 老数据升级后仍能进入系统并显示默认样式。

### 15.4 处理缓存

需要特别关注三类缓存：

| 操作 | 应清理缓存 |
|------|------------|
| 普通字典增删改 | `dict` |
| 样式配置保存 | `style` |
| 样式配置仍写入 `sys_dict` 时 | `style` + 必要时 `dict` |

前端保存样式后：

- 更新 `themeConfig`。
- 立即调用 `applyThemeToCSS()`。
- 不依赖整页刷新生效。

前端修改字典后：

- 调用 `refreshDictCache()`。
- 页面本地列表同步刷新。

### 15.5 JSON 配置必须可校验

以下内容不能继续用普通文本框自由输入：

- `theme.extraValue`
- `color_scheme.extraValue`
- `layout_style.extraValue`
- `home_widget.extraValue`
- `category_visual_config.extraValue`

处理方式：

- 简单配置做成结构化表单。
- 复杂配置做 JSON schema 校验。
- 只读展示时格式化 JSON，提供复制，不提供直接编辑。

### 15.6 样式页面要避免新的混乱

Style Settings 拆分后，仍要避免把所有高级功能堆回页面：

- 常用配置默认可见。
- 高级配置折叠。
- 危险操作二次确认。
- 布局配置和首页组件配置分开。
- 色彩方案和涨跌规则分开说明。
- 预览面板只展示关键场景，不做成另一个完整首页。

### 15.7 字典页面要保护业务稳定性

以下操作必须谨慎：

- 修改 `common_status` 的 key。
- 修改 `user_role` 的 key。
- 删除被产品、用户、审批、日志引用的字典项。
- 停用仍被页面默认筛选使用的字典项。

建议首版只锁定 key，不做引用计数；后续再增加“引用检查”能力。

### 15.8 数据库字段一致性

如果本轮只新增字典分类，不改表结构：

- `init.sql` 增加初始化数据即可。
- `数据字典.md` 补充分类用途。

如果修改 `sys_dict.extra_value` 长度或类型：

- `SysDict.java` 的 `@Column(name = "extra_value", length = 500)` 必须同步。
- `init.sql` 表结构必须同步。
- `backend/src/main/resources/数据字典.md` 必须同步。
- 本地启动需观察 JPA/Hibernate 是否有字段类型不一致警告。

---

## 十六、执行检查表

### 16.1 开发前检查

- [ ] 已确认新增配置归属 Style Settings 还是 Dict Management。
- [ ] 已确认是否涉及受保护分类。
- [ ] 已确认 API DTO 和前端类型是否需要新增字段。
- [ ] 已确认是否需要更新 `init.sql`。
- [ ] 已确认是否需要更新数据字典文档。

### 16.2 开发中检查

- [ ] 后端 DTO、Service、Controller 字段一致。
- [ ] 前端 `types`、`api`、`composable`、页面字段一致。
- [ ] 普通字典仍通过 `useDict` 获取显示名。
- [ ] 样式配置仍通过 `useTheme` 获取和应用。
- [ ] 受保护分类无法通过普通字典表单编辑。
- [ ] 保存样式后 CSS 变量立即生效。
- [ ] 修改字典后缓存刷新。

### 16.3 提测前检查

- [ ] PC 端 1366/1440/1920 宽度无横向滚动。
- [ ] 移动端 375/390/430 宽度文本不溢出。
- [ ] Style Settings 保存、重置、恢复默认、Logo 上传正常。
- [ ] Dict Management 普通字典新增、编辑、停用、删除正常。
- [ ] 受保护分类默认隐藏，显示后只读。
- [ ] 首页、产品列表、用户管理、字典管理无明显视觉回归。
- [ ] 后端接口权限符合 ADMIN/EDITOR/VIEWER 预期。

### 16.4 合并前检查

- [ ] `README.md` 已按功能变化更新。
- [ ] `docs/dev/开发指南.md` 已更新开发规则。
- [ ] `docs/dev/项目设计文档.md` 已更新 API/数据库/模块设计。
- [ ] `docs/dev/UI设计说明.md` 已更新页面说明。
- [ ] `backend/src/main/resources/数据字典.md` 已更新字典分类或字段变更。
- [ ] `docs/archive/项目完成总结.md` 已更新完成情况。
- [ ] 新增 SQL 可重复执行。
- [ ] Entity 注解与数据库结构一致。

---

## 十七、里程碑与依赖关系

### 17.1 推荐里程碑

M1-M6 属于“阶段一：基础分离”的内部里程碑；M7 属于“阶段二：最小服务增强”的内部里程碑。

| 里程碑 | 内容 | 预计工时 | 可独立验收 |
|--------|------|----------|------------|
| M1：配置边界保护 | 分类元数据、受保护分类、后端写保护 | 1 天 | 是 |
| M2：字典页规整 | Dict Management 默认隐藏/只读系统配置 | 1 天 | 是 |
| M3：样式页拆分 | Style Settings 组件化，不改变业务行为 | 2 天 | 是 |
| M4：预设体系扩展 | 色彩、字号、布局预设与 API 契约 | 1.5 天 | 是 |
| M5：全局样式联动 | Layout/Home/图表/业务页面应用新变量 | 2 天 | 是 |
| M6：文档同步 | README、开发指南、设计文档、数据字典 | 0.5 天 | 是 |
| M7：版本回滚 | 样式版本快照、历史列表、手动回滚、缓存稳定化 | 3-4 天 | 是 |

### 17.2 依赖关系

```text
M1 配置边界保护
  ↓
M2 字典页规整
  ↓
M3 样式页拆分
  ↓
M4 预设体系扩展
  ↓
M5 全局样式联动
  ↓
M6 文档同步
  ↓
M7 版本回滚
```

关键依赖说明：

- M1 必须先于 M2，否则字典页无法可靠判断哪些分类要隐藏或只读。
- M2 必须先于 M4/M5，否则新增 `color_scheme`、`layout_style` 后仍会暴露在普通字典编辑里。
- M3 应先于 M4，否则新预设会继续堆进旧长组件，页面会更乱。
- M4 必须先于 M5，否则 Layout/Home 没有稳定配置来源。
- M6 必须在阶段一合并前完成，符合项目功能变更流程。
- M7 依赖阶段一完成后再做，不改变 Dict Management 与 Style Settings 的入口边界。

### 17.3 可并行任务

在不冲突的前提下，可并行：

| 主线任务 | 可并行任务 | 注意 |
|----------|------------|------|
| M1 后端保护 | 前端分类元数据定义 | 分类列表必须最终一致 |
| M3 样式页拆分 | 预览面板 UI 设计 | 不提前引入新 API |
| M4 DTO/API 扩展 | 前端 `stylePresets.ts` 类型定义 | API 字段名必须统一 |
| M5 首页联动 | 文档草稿更新 | 最终以实际代码为准 |

不建议并行：

- 同时多人改 `StyleSettings.vue`。
- 同时多人改 `useTheme.ts`。
- 同时多人改 `StyleConfigDTO` 和 `frontend/src/types/theme.ts` 但不先对齐字段。

### 17.4 每个里程碑的 Definition of Done

M1 完成标准：

- 前后端都有受保护分类定义。
- 后端普通字典写接口无法修改受保护分类。
- 有明确错误提示。

M2 完成标准：

- 字典页默认只展示业务/系统字典。
- 系统配置只读可查。
- 普通字典 CRUD 正常。

M3 完成标准：

- `StyleSettings.vue` 明显瘦身。
- 品牌、色彩、排版、布局、预览至少拆出独立模块。
- 保存行为与旧版一致。

M4 完成标准：

- 色彩方案、布局方案、字号方案结构清晰。
- 旧配置兼容。
- API 和前端类型一致。

M5 完成标准：

- 首页和主要业务页面使用统一样式变量。
- 选择不同色彩/布局后可观察到预期变化。
- 移动端无明显破损。

M6 完成标准：

- 项目规范要求的文档全部更新。
- 数据字典与数据库结构一致。

M7 完成标准：

- 保存样式配置会生成版本快照。
- ADMIN 可查看历史版本并手动回滚。
- 回滚后 CSS 变量立即刷新，缓存被正确清理。

---

## 十八、回滚与灰度策略

### 18.1 前端回滚策略

Style Settings 改造必须保留默认配置兜底：

- `useTheme.ts` 中保留完整默认 `themeConfig`。
- API 获取失败时继续使用默认主题，不阻塞应用启动。
- 新增字段缺失时使用默认值。
- `activeColorScheme` 缺失时回退到 `activeTheme`。
- `activeLayoutStyle` 缺失时回退到现有布局。

如果新 Style Settings 页面出现严重问题：

1. 暂时隐藏布局配置和高级配置入口。
2. 保留品牌、基础色彩、字号、Logo 的最小可用表单。
3. 禁用新增的 layout 切换能力。
4. 保持 `/api/style/config` 旧字段可保存。

### 18.2 后端回滚策略

后端新增字段必须做到向后兼容：

- DTO 新字段可为空。
- Service 读取不到新字典项时返回默认值。
- 初始化 SQL 使用 `WHERE NOT EXISTS` 或类似方式避免重复插入。
- 不删除旧 `theme`、`style.active_theme` 数据。

如果新预设接口异常：

- 前端可以继续使用本地 `stylePresets.ts` 预设。
- `/api/style/config` 仍能返回当前生效配置。
- 不影响普通 `/api/dict` 读取。

### 18.3 数据回滚策略

如果新增 `color_scheme`、`layout_style`、`font_preset` 后需要回滚：

- 可以保留新增字典分类，不影响旧功能。
- 将 `style.active_color_scheme`、`style.active_layout_style` 标记为未使用或删除。
- 保留 `style.active_theme` 作为旧逻辑入口。
- 如已修改 `extra_value` 字段类型为 `TEXT`，不建议回滚字段类型，避免截断已有数据。

### 18.4 灰度开关建议

首版可以增加前端常量或后端配置控制新能力是否启用：

```typescript
export const STYLE_SETTINGS_FEATURE_FLAGS = {
  enableLayoutStyle: false,
  enableProtectedDictView: true,
  enableAdvancedStyleTokens: false,
}
```

灰度顺序：

1. 先启用受保护分类只读。
2. 再启用 Style Settings 新布局。
3. 再启用色彩方案/字号方案。
4. 最后启用布局方案和首页组件配置。

### 18.5 故障处理优先级

如果上线后出现问题，按以下顺序处理：

1. 样式无法加载：回退默认 `themeConfig`。
2. 字典无法加载：保留 key 原值显示，避免页面空白。
3. 受保护分类无法编辑：优先使用专业 Style Settings 修复。
4. 新布局破损：关闭 `enableLayoutStyle`，回退现有布局。
5. JSON 配置解析失败：忽略该配置项并记录错误，不阻塞应用。

---

## 十九、待解决问题清单

本章记录当前方案中尚未完全解决的问题，需要在实施过程中持续跟进。

### 19.1 `extra_value` 长度限制

| 问题 | 当前状态 | 风险 | 解决路径 |
|------|----------|------|----------|
| `sys_dict.extra_value` 为 `VARCHAR(500)` | 仅作为旧数据兼容和普通字典扩展字段 | 旧样式 JSON 迁移前可能溢出，普通字典误存复杂 JSON 也可能溢出 | 阶段一：样式 JSON 迁入 `sys_style_*`；普通字典禁止复杂 JSON |

**短期约束：**
- 不再新增 `color_scheme.extraValue`、`layout_style.extraValue`、`font_preset.extraValue` 作为样式主数据
- 旧 `theme.extraValue`、`color_scheme.extraValue`、`layout_style.extraValue` 只作为迁移来源
- `home_widget.extraValue` 如涉及多个组件排序，必须迁入 `sys_style_config.config_value`
- `category_visual_config.extraValue` 已存在复杂 JSON，建议本轮不通过字典页编辑

**阶段一方案：**
```sql
CREATE TABLE sys_style_preset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  preset_type VARCHAR(50) NOT NULL,  -- 'color_scheme', 'layout_style', 'font_preset'
  preset_key VARCHAR(100) NOT NULL,
  preset_name VARCHAR(200) NOT NULL,
  config_json TEXT NOT NULL,
  sort_order INT DEFAULT 0,
  status VARCHAR(20) DEFAULT 'ACTIVE',
  created_time DATETIME,
  updated_time DATETIME,
  UNIQUE KEY uk_type_key (preset_type, preset_key)
);
```

除非普通业务字典确实需要更长扩展字段，否则本轮不建议修改 `sys_dict.extra_value` 字段类型，避免扩大数据库变更面。

### 19.2 `chartColors` 类型兼容

| 问题 | 当前状态 | 风险 |
|------|----------|------|
| 后端存储为逗号分隔字符串，前端使用为 `string[]` | `useTheme.ts` 有转换逻辑 | 转换逻辑散落多处，易漂移 |

**解决规则：**
- API 层统一处理：`StyleConfigService` 返回 DTO 时将字符串转 `List<String>`
- 前端 `api/style.ts` 接收后直接使用，不再二次转换
- 禁止在组件层做 `split(',')` 或 `join(',')`

```typescript
// ❌ 禁止在组件中
const colors = config.chartColors.split(',')

// ✅ 正确：API 层已处理
const colors = config.chartColors  // 已是 string[]
```

### 19.3 受保护分类元数据同步

| 问题 | 当前状态 | 风险 |
|------|----------|------|
| 受保护分类列表前后端各硬编码一份 | 短期可接受 | 维护时易不同步 |

**短期方案：**
- 前端 `dictCategoryMeta.ts` 定义 `PROTECTED_CATEGORIES`
- 后端 `DictCategoryPolicy.java` 定义同名常量
- 新增受保护分类时，两边同步修改

**中期方案：**
- 后端 `/api/dict/categories` 返回分类元数据，含 `isProtected` 字段
- 前端从 API 获取，不再硬编码

```java
// 后端返回结构
public class DictCategoryMetaDTO {
  private String category;
  private String label;
  private String domain;
  private Boolean isProtected;
  private Boolean keyMutable;
  private Boolean valueMutable;
}
```

### 19.4 系统配置查看权限

| 问题 | 当前状态 | 建议 |
|------|----------|------|
| "显示系统配置"开关权限未明确 | 文档未说明 | 明确权限边界 |

**权限规则：**
| 角色 | 默认可见 | 开启后可见 | 可编辑 |
|------|----------|------------|--------|
| ADMIN | 业务+系统字典 | 全部含受保护 | 受保护分类只读，其他可编辑 |
| EDITOR | 业务+系统字典 | 业务+系统字典 | 业务字典可编辑 |
| VIEWER | 业务字典 | 业务字典 | 无 |

**前端实现：**
```typescript
const canViewSystemConfig = computed(() => userRole.value === 'ADMIN')
const canEditProtectedCategory = computed(() => false) // 即使 ADMIN 也只能通过 Style Settings 编辑
```

### 19.5 首页组件拖拽排序边界

| 问题 | 当前状态 | 边界 |
|------|----------|------|
| 首页组件拖拽排序列为 P2 | 与 M5 边界模糊 | 需明确 |

**边界划分：**
- M5（全局样式联动）：只做布局 CSS 变量应用，如 `--app-nav-bg`、`--app-card-radius`
- 首页组件显隐：M5 可做简单的 switch 开关
- 首页组件拖拽排序：P2 独立任务，不在本计划范围

---

## 二十、两阶段落地划分

本章将 Style Settings 与 Dict Management 的分离过程收敛为两个可落地阶段。考虑当前系统仍处于开发阶段，可以直接把原“阶段一：治理隔离”和“阶段二：存储分离”合并为第一阶段，一次性完成职责边界、页面入口和存储层分离；第二阶段只保留真正有近期价值的最小服务增强，先做版本快照与回滚，暂不把灰度发布、配置继承、WebSocket 实时同步列为本轮必做。

### 20.1 阶段总览

| 阶段 | 名称 | 核心目标 | 存储状态 | 工时 | 风险 | 可独立上线 |
|------|------|----------|----------|------|------|------------|
| 阶段一 | 基础分离 | 明确职责边界，完成页面/API/存储分离 | 字典留在 `sys_dict`，样式迁移到 `sys_style_*` | 10-12 天 | 中 | 是 |
| 阶段二 | 最小服务增强 | 支持样式版本快照、手动回滚、缓存稳定化 | 独立样式表 + 版本表 + 缓存 | 3-4 天 | 中 | 是 |

---

### 20.2 阶段一：基础分离（治理隔离 + 存储分离）

#### 20.2.1 业务目标

**一句话定义：** 在当前开发阶段直接完成 Style Settings 与 Dict Management 的完整基础分离：职责分离、页面分离、API 分离、存储分离同步落地。

**解决的问题：**
| 问题 | 现状 | 解决方式 |
|------|------|----------|
| 样式配置暴露在字典页 | `style`、`theme` 等分类在 Dict Management 可编辑 | 默认隐藏，只读展示 |
| 职责边界不清 | 两个入口都能改样式配置 | 明确归属，专业入口管理 |
| JSON 配置误改风险 | `extraValue` 为 JSON，普通表单易改错 | 受保护分类禁止普通编辑 |
| 新增配置归属不明 | 无判定规则 | 建立配置归属判定表 |
| 存储层仍耦合 | 样式配置复用 `sys_dict` | 新增 `sys_style_config`、`sys_style_preset` |
| `extra_value` 长度限制 | 复杂 JSON 可能超过 500 字符 | 样式配置使用 TEXT 字段 |

#### 20.2.2 功能范围

**包含功能：**

| 功能模块 | 具体内容 | 交付物 |
|----------|----------|--------|
| 分类元数据 | 定义 5 层分类：business_dict、system_dict、ui_config、visual_mapping、internal | `dictCategoryMeta.ts`、`DictCategoryPolicy.java` |
| 受保护分类 | 定义哪些分类禁止普通编辑 | `PROTECTED_CATEGORIES` 常量 |
| Dict Management 保护 | 默认隐藏受保护分类，开启后只读 | `DictManagement.vue` 改造 |
| Style Settings 拆分 | 拆成品牌、色彩、排版、布局、预览 5 区 | `StyleSettings*.vue` 组件族 |
| 样式专用存储 | 新增 `sys_style_config`、`sys_style_preset` | DDL、Entity、Repository、Service |
| 数据迁移 | 从 `sys_dict` 的 `style/theme` 迁移到 `sys_style_*` | 可重复执行迁移脚本 |
| 色彩/布局预设 | 7 套色彩方案 + 4 套布局方案 | `sys_style_preset` 数据 |
| 全局样式联动 | Layout/Home 应用 CSS 变量 | `useTheme.ts` 扩展 |
| API 扩展 | 色彩切换、布局切换接口 | 继续使用 `/api/style/*`，不额外引入 v2 路径 |

**不包含功能（阶段二）：**
- 版本快照与历史回滚
- 灰度发布、配置继承、WebSocket 实时同步
- A/B 测试、审批流、配置差异对比

#### 20.2.3 技术实现

**前端改动：**

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `constants/dictCategoryMeta.ts` | 新增 | 分类元数据定义 |
| `composables/useDict.ts` | 修改 | 引用分类元数据 |
| `views/DictManagement.vue` | 修改 | 默认隐藏受保护分类，只读展示 |
| `components/style-settings/*.vue` | 新增 | 拆分后的配置面板组件 |
| `composables/useStyleSettingsForm.ts` | 新增 | 表单状态管理 |
| `constants/stylePresets.ts` | 新增 | 色彩/布局/字号预设 |
| `composables/useTheme.ts` | 修改 | 新增布局 CSS 变量 |
| `api/style.ts` | 修改 | 新增预设切换 API |
| `components/Layout.vue` | 修改 | 支持布局变量 |
| `views/Home.vue` | 修改 | 支持仪表盘布局 |

**后端改动：**

| 文件 | 改动类型 | 说明 |
|------|----------|------|
| `config/DictCategoryPolicy.java` | 新增 | 受保护分类常量 |
| `service/SysDictService.java` | 修改 | 写接口增加分类保护校验 |
| `entity/SysStyleConfig.java` | 新增 | 映射 `sys_style_config` |
| `entity/SysStylePreset.java` | 新增 | 映射 `sys_style_preset` |
| `repository/StyleConfigRepository.java` | 新增 | 样式配置数据访问 |
| `repository/StylePresetRepository.java` | 新增 | 样式预设数据访问 |
| `dto/StyleConfigDTO.java` | 修改 | 新增 `activeColorScheme`、`activeLayoutStyle` |
| `service/StyleConfigService.java` | 修改 | 改为读取专用表，新增色彩/布局切换方法 |
| `service/StylePresetService.java` | 新增 | 管理色彩、布局、字号预设 |
| `controller/StyleConfigController.java` | 修改 | 新增预设切换接口 |
| `resources/init.sql` | 修改 | 新增样式专用表和初始化数据 |

**新增表结构：**

```sql
CREATE TABLE IF NOT EXISTS sys_style_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '样式配置ID',
  config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
  config_value TEXT COMMENT '配置值',
  config_type VARCHAR(50) DEFAULT 'string' COMMENT '类型：string/json/color/font/size/url',
  description VARCHAR(500) COMMENT '说明',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式配置表';

CREATE TABLE IF NOT EXISTS sys_style_preset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '样式预设ID',
  preset_type VARCHAR(50) NOT NULL COMMENT '预设类型：color_scheme/layout_style/font_preset',
  preset_key VARCHAR(100) NOT NULL COMMENT '预设键',
  preset_name VARCHAR(200) NOT NULL COMMENT '预设名称',
  preset_description VARCHAR(500) COMMENT '预设说明',
  config_json TEXT NOT NULL COMMENT '配置JSON',
  is_default BOOLEAN DEFAULT FALSE COMMENT '是否默认',
  sort_order INT DEFAULT 0 COMMENT '排序',
  status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_style_preset_type_key (preset_type, preset_key),
  INDEX idx_style_preset_type (preset_type),
  INDEX idx_style_preset_status (status),
  INDEX idx_style_preset_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式预设表';
```

**迁移规则：**

```sql
-- 迁移当前生效样式配置：注意 config_value 必须取 extra_value
INSERT INTO sys_style_config (config_key, config_value, config_type, description)
SELECT
  dict_key,
  extra_value,
  CASE
    WHEN dict_key LIKE '%color%' OR dict_key LIKE '%Color%' THEN 'color'
    WHEN dict_key LIKE '%font%' OR dict_key LIKE '%Font%' THEN 'font'
    WHEN dict_key LIKE '%size%' OR dict_key LIKE '%Size%' THEN 'size'
    WHEN dict_key LIKE '%logo%' OR dict_key LIKE '%Logo%' THEN 'url'
    ELSE 'string'
  END,
  COALESCE(remark, dict_value)
FROM sys_dict
WHERE category = 'style'
  AND status = 'ACTIVE'
  AND extra_value IS NOT NULL
ON DUPLICATE KEY UPDATE
  config_value = VALUES(config_value),
  config_type = VALUES(config_type),
  description = VALUES(description);

-- 迁移色彩、布局、字号预设
INSERT INTO sys_style_preset (preset_type, preset_key, preset_name, preset_description, config_json, sort_order, status)
SELECT
  CASE
    WHEN category IN ('theme', 'color_scheme') THEN 'color_scheme'
    WHEN category = 'layout_style' THEN 'layout_style'
    WHEN category = 'font_preset' THEN 'font_preset'
  END,
  dict_key,
  dict_value,
  remark,
  extra_value,
  sort_order,
  status
FROM sys_dict
WHERE category IN ('theme', 'color_scheme', 'layout_style', 'font_preset')
  AND status = 'ACTIVE'
  AND extra_value IS NOT NULL
ON DUPLICATE KEY UPDATE
  preset_name = VALUES(preset_name),
  preset_description = VALUES(preset_description),
  config_json = VALUES(config_json),
  sort_order = VALUES(sort_order),
  status = VALUES(status);
```

迁移注意：

- 不再将 `sys_dict.status` 改为 `MIGRATED`，避免 `CommonStatus` 枚举解析失败。
- 旧 `style/theme/color_scheme/layout_style/font_preset` 字典数据保留，用于开发期回滚和兼容读取。
- `StyleConfigService` 读取新表失败时，可以短期回退读取旧 `sys_dict`，但页面入口只允许 Style Settings 修改样式。

#### 20.2.4 验收标准

**功能验收：**

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| 分类元数据生效 | 检查 `dictCategoryMeta.ts` 导出 | 5 层分类定义完整 |
| 受保护分类识别 | 调用 `isProtectedCategory('style')` | 返回 true |
| Dict Management 隐藏 | 打开字典管理页面 | 默认不显示 `style`、`theme` |
| Dict Management 只读 | 开启"显示系统配置" | 受保护分类无编辑按钮 |
| 后端保护生效 | 通过 `/api/dict` 修改 `style` 分类 | 返回 403 或业务错误 |
| Style Settings 拆分 | 打开样式设置页面 | 显示 5 个配置区 |
| 样式专用表创建 | 检查数据库 | 存在 `sys_style_config`、`sys_style_preset` |
| 样式配置迁移 | 查询 `sys_style_config` | `price_rise_color` 等配置值为颜色值而非显示名 |
| 样式预设迁移 | 查询 `sys_style_preset` | 色彩、布局、字号预设迁移完整 |
| 色彩方案切换 | 选择不同色彩方案 | 涨跌颜色立即变化 |
| 布局方案切换 | 选择不同布局方案 | 导航/首页布局变化 |
| 配置持久化 | 刷新页面 | 配置保持 |
| 旧配置兼容 | 新表为空但旧字典存在 | 系统正常启动，使用兼容读取或默认配置 |

**UI 验收：**

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| PC 端布局 | 1366/1440/1920 宽度 | 无横向滚动 |
| 移动端布局 | 375/390/430 宽度 | 文本不溢出 |
| 预览真实性 | 检查预览面板 | 显示价格、表格等真实场景 |

**回归验收：**

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| 登录正常 | 登录系统 | 系统名称、Logo 正确 |
| 产品列表正常 | 打开产品列表 | 涨跌颜色正确 |
| 首页图表正常 | 打开首页 | 图表色板正确 |
| 字典维护正常 | 编辑普通字典 | 增删改查正常 |
| 角色显示正常 | 查看用户列表 | 角色名称来自字典 |

#### 20.2.5 交付物清单

```
阶段一交付物：
├── 前端代码
│   ├── frontend/src/constants/dictCategoryMeta.ts
│   ├── frontend/src/constants/stylePresets.ts
│   ├── frontend/src/composables/useStyleSettingsForm.ts
│   ├── frontend/src/components/style-settings/
│   │   ├── BrandSettingsPanel.vue
│   │   ├── ColorSchemePanel.vue
│   │   ├── TypographyPanel.vue
│   │   ├── LayoutStylePanel.vue
│   │   └── StylePreviewPanel.vue
│   └── 修改文件若干
├── 后端代码
│   ├── backend/.../config/DictCategoryPolicy.java
│   ├── backend/.../entity/SysStyleConfig.java
│   ├── backend/.../entity/SysStylePreset.java
│   ├── backend/.../repository/StyleConfigRepository.java
│   ├── backend/.../repository/StylePresetRepository.java
│   ├── backend/.../service/StylePresetService.java
│   └── 修改文件若干
├── 数据库
│   ├── init.sql 新增 sys_style_config、sys_style_preset
│   └── init.sql 新增样式配置和预设初始化数据
└── 文档
    ├── README.md 更新
    ├── docs/dev/开发指南.md 更新
    ├── docs/dev/项目设计文档.md 更新
    ├── docs/dev/UI设计说明.md 更新
    └── docs/archive/项目完成总结.md 更新
```

---

### 20.3 阶段二：最小服务增强

#### 20.3.1 业务目标

**一句话定义：** 在基础分离完成后，为 Style Settings 增加最小可用的版本快照、手动回滚和缓存稳定化能力；不在本阶段引入灰度发布、配置继承和 WebSocket 实时同步。

**解决的问题：**
| 问题 | 阶段一状态 | 阶段二解决 |
|------|------------|------------|
| 配置错误难恢复 | 只有当前配置 | 每次保存生成版本快照 |
| 无手动回滚 | 需要重新手工配置 | 支持回滚到历史版本 |
| 缓存策略不够明确 | `style`、`dict` 缓存边界混用 | 独立样式缓存与主动失效 |
| 变更原因不可追踪 | 只有更新时间 | 记录变更摘要、变更人 |

#### 20.3.2 功能范围

**包含功能：**

| 功能模块 | 具体内容 | 交付物 |
|----------|----------|--------|
| 版本快照 | 保存样式配置时生成配置快照 | `sys_style_version` |
| 版本列表 | 查看历史版本 | `/api/style/versions` |
| 版本回滚 | 回滚到指定历史版本 | `/api/style/rollback/{versionId}` |
| 缓存稳定化 | 样式配置独立缓存、保存后主动清理 | `StyleCacheService` 或现有缓存增强 |
| 操作日志 | 记录保存、回滚、重置等关键操作 | `@OperationLog` |

**不包含功能（后续扩展）：**
- 灰度发布
- 配置继承
- WebSocket 实时推送
- A/B 测试
- 样式配置审批流

#### 20.3.3 技术实现

**新增表结构：**

```sql
CREATE TABLE IF NOT EXISTS sys_style_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  version_no VARCHAR(50) NOT NULL COMMENT '版本号，如 v20260520_001',
  config_snapshot TEXT NOT NULL COMMENT '配置快照JSON',
  change_summary VARCHAR(500) COMMENT '变更说明',
  changed_by BIGINT COMMENT '变更人ID',
  created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  UNIQUE KEY uk_style_version_no (version_no),
  INDEX idx_style_version_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式版本历史表';
```

**后端改造：**

| 文件 | 改动 |
|------|------|
| `entity/SysStyleVersion.java` | 新增，映射 `sys_style_version` |
| `repository/StyleVersionRepository.java` | 新增 |
| `service/StyleVersionService.java` | 新增，保存快照、查询版本、回滚 |
| `service/StyleConfigService.java` | 保存配置前后生成版本快照，回滚后写回当前配置 |
| `controller/StyleConfigController.java` | 增加版本列表、版本详情、回滚接口 |
| `cache/StyleCacheService.java` | 可选新增，或增强现有缓存清理逻辑 |

**前端改造：**

| 文件 | 改动 |
|------|------|
| `api/style.ts` | 新增版本列表、版本详情、回滚 API |
| `components/style-settings/StyleVersionPanel.vue` | 可选新增，展示历史版本 |
| `views/StyleSettings.vue` | 增加“历史版本/回滚”入口 |
| `composables/useTheme.ts` | 回滚成功后重新加载并应用 CSS 变量 |

**新增接口：**

```text
GET  /api/style/versions
GET  /api/style/versions/{versionId}
POST /api/style/rollback/{versionId}
```

权限建议：

- `GET /api/style/versions`：ADMIN 可用。
- `GET /api/style/versions/{versionId}`：ADMIN 可用。
- `POST /api/style/rollback/{versionId}`：仅 ADMIN 可用，并记录操作日志。

#### 20.3.4 验收标准

**数据验收：**

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| 版本表创建成功 | `SHOW TABLES LIKE 'sys_style_version'` | 返回 1 张表 |
| 保存生成版本 | 修改并保存样式配置 | `sys_style_version` 新增记录 |
| 快照完整 | 查看 `config_snapshot` | 包含当前样式配置关键字段 |
| 变更人记录 | 查看 `changed_by` | 有当前用户 ID 或系统用户 |

**功能验收：**

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| 版本列表 | 打开历史版本 | 返回版本列表 |
| 版本详情 | 查看某一版本 | 能看到快照和变更说明 |
| 手动回滚 | 回滚到上一版本 | 当前配置恢复，CSS 变量刷新 |
| 回滚再生成版本 | 执行回滚 | 生成一条新的回滚版本记录 |
| 缓存清理 | 保存/回滚配置 | 页面刷新后立即显示最新配置 |

**回滚验收：**

| 验收项 | 验收方法 | 通过标准 |
|--------|----------|----------|
| 回滚权限 | EDITOR 尝试回滚 | 返回 403 |
| 回滚前确认 | 前端点击回滚 | 有确认提示 |
| 回滚失败兜底 | 模拟失败 | 当前配置不被破坏 |

#### 20.3.5 交付物清单

```
阶段二交付物：
├── 数据库
│   └── init.sql 新增 sys_style_version
├── 后端代码
│   ├── backend/.../entity/SysStyleVersion.java
│   ├── backend/.../repository/StyleVersionRepository.java
│   ├── backend/.../service/StyleVersionService.java
│   └── 修改文件若干
├── 前端代码
│   ├── frontend/src/api/style.ts（新增版本 API）
│   └── frontend/src/components/style-settings/StyleVersionPanel.vue（可选）
└── 文档
    ├── backend/src/main/resources/数据字典.md 更新
    └── docs/dev/项目设计文档.md 更新
```

---

### 20.4 后续增强：暂不纳入两阶段必做范围

两阶段完成后，Style Settings 已经具备清晰职责、专用存储、专业 API 和最小版本回滚能力。以下能力只有在出现明确业务需求后再立项，不进入当前落地计划。

| 能力 | 触发条件 | 说明 |
|------|----------|------|
| 灰度发布 | 多品牌、多租户、按部门试运行样式 | 可新增 `sys_style_grayscale` |
| 配置继承 | 组织层级需要不同样式覆盖 | 可新增 `sys_style_inherit` |
| WebSocket 实时同步 | 多端同时在线且要求实时刷新主题 | 可新增 `/ws/style/sync` |
| 配置审批 | 样式变更需要审批后发布 | 可接入审批流程 |
| A/B 测试 | 需要统计不同样式方案效果 | 需要数据埋点和效果分析 |

后续增强原则：

- 不影响 `Dict Management`，字典侧继续只管理业务枚举。
- 不改变 `sys_style_config` 与 `sys_style_preset` 的基础职责。
- 新增能力必须独立开关，不能阻塞基础样式配置读写。

---

### 20.5 阶段间依赖与触发条件

#### 20.5.1 依赖关系

```
阶段一（基础分离）
    │
    │ 必须完成：职责边界、页面保护、专用表、数据迁移、样式 API
    │
    ▼
阶段二（最小服务增强）
```

**阶段一 → 阶段二 触发条件：**

| 条件 | 当前评估 | 触发阈值 | 建议 |
|------|----------|----------|------|
| 基础分离完成 | 待完成 | `sys_style_*` 已稳定读写 | 必须满足 |
| Style Settings 可用 | 待完成 | 保存/重置/切换/预览均通过 | 必须满足 |
| Dict Management 已隔离 | 待完成 | 不再编辑样式配置 | 必须满足 |
| 需要回滚保障 | 开发阶段建议需要 | 进入联调或多人配置前 | 建议满足后进入阶段二 |

#### 20.5.2 各阶段独立上线能力

| 阶段 | 可独立上线 | 依赖前置 | 说明 |
|------|------------|----------|------|
| 阶段一 | 是 | 无 | 完成后两个管理入口已经基础分离 |
| 阶段二 | 是 | 阶段一 | 增加版本快照和手动回滚，不改变入口边界 |

**上线策略：**
- 阶段一在开发环境完成后即可作为新的基础架构继续开发
- 阶段二建议在进入频繁样式调整前完成，避免配置误改无法回退
- 灰度、继承、WebSocket 作为后续增强，单独评估，不绑定本计划上线

---

### 20.6 完全分离后的最终状态

#### 20.6.1 Dict Management 最终职责

```
┌─────────────────────────────────────────────────────────────┐
│ Dict Management（数据字典管理）                               │
├─────────────────────────────────────────────────────────────┤
│ 存储：sys_dict（仅业务枚举）                                  │
│ 接口：/api/dict                                              │
│ 前端服务：useDict.ts                                         │
├─────────────────────────────────────────────────────────────┤
│ 管理内容：                                                   │
│   - 业务枚举：currency, unit, origin, customer              │
│   - 系统枚举：common_status, user_role, operation_type      │
│   - 流程枚举：approval_status, workflow_type                │
├─────────────────────────────────────────────────────────────┤
│ 不再包含（已迁移到 Style Settings）：                         │
│   - style、theme、color_scheme、layout_style                │
│   - font_preset、home_widget、home_layout                   │
│   - category_visual_config                                  │
└─────────────────────────────────────────────────────────────┘
```

#### 20.6.2 Style Settings 最终职责

```
┌─────────────────────────────────────────────────────────────┐
│ Style Settings（样式设置）                                    │
├─────────────────────────────────────────────────────────────┤
│ 存储：sys_style_config, sys_style_preset, sys_style_version │
│ 接口：/api/style                                             │
│ 前端服务：useTheme.ts                                         │
├─────────────────────────────────────────────────────────────┤
│ 管理内容：                                                   │
│   - 品牌识别：系统名称、Logo                                  │
│   - 色彩方案：7+ 套预设 + 自定义                              │
│   - 布局方案：4+ 套预设 + 自定义                              │
│   - 字体系统：字体族 + 字号方案                               │
│   - 首页配置：组件显隐 + 排序                                 │
├─────────────────────────────────────────────────────────────┤
│ 阶段二能力：                                                  │
│   - 版本管理与回滚                                           │
│   - 缓存稳定化                                               │
└─────────────────────────────────────────────────────────────┘
```

#### 20.6.3 两系统关系

```
Dict Management                    Style Settings
       │                                 │
       │                                 │
       ▼                                 ▼
  sys_dict                        sys_style_*
       │                                 │
       │                                 │
       └─────────────┬───────────────────┘
                     │
                     ▼
              useDict.ts              useTheme.ts
                     │                                 │
                     └─────────────┬───────────────────┘
                                   │
                                   ▼
                            业务页面组件
```

#### 20.6.4 无交叉调用规则

| 规则 | 说明 |
|------|------|
| `useDict.ts` 永不调用 `/api/style` | 字典服务不依赖样式服务 |
| `useTheme.ts` 永不调用 `/api/dict` | 样式服务不依赖字典服务 |
| Dict Management 页面永不展示样式配置 | 页面职责清晰 |
| Style Settings 页面永不展示业务字典 | 页面职责清晰 |
| `sys_dict` 永不存储样式配置 | 存储层隔离 |
| `sys_style_*` 永不存储业务枚举 | 存储层隔离 |

---

## 二十一、与 dashboard-blue-theme-upgrade.md 的协同

### 21.1 两份计划的关系

| 计划 | 职责 | 解决什么问题 |
|------|------|--------------|
| 本计划 | 治理边界 | 谁管什么、怎么防误改、怎么演进 |
| dashboard-blue-theme-upgrade.md | 视觉内容 | 7套色彩、4套布局具体是什么、怎么实现 |

### 21.2 协同实施顺序

```
阶段一：基础分离（本计划 + dashboard-blue-theme-upgrade.md）
  │
  ├─ 1. 建立分类元数据与后端受保护分类拦截
  ├─ 2. Dict Management 默认隐藏/只读样式配置
  ├─ 3. 新增 sys_style_config、sys_style_preset
  ├─ 4. 从 sys_dict 迁移 style/theme 相关数据
  ├─ 5. 新增色彩方案、布局方案、字号方案
  ├─ 6. 拆分 StyleSettings.vue 为配置工作台
  ├─ 7. Layout/Home/图表应用新样式变量
  └─ 8. 文档与数据字典同步
  │
  ▼
阶段二：最小服务增强（本计划）
  │
  ├─ 1. 新增 sys_style_version
  ├─ 2. 保存配置生成版本快照
  ├─ 3. 提供版本列表/详情/回滚 API
  ├─ 4. Style Settings 增加历史版本入口
  └─ 5. 样式缓存稳定化
```

### 21.3 合并后的总工时

| 阶段 | 来源 | 工时 |
|------|------|------|
| 阶段一：基础分离 | 本计划 M1-M6 + dashboard 色彩/布局内容 | 10-12 天 |
| 阶段二：最小服务增强 | 版本快照、手动回滚、缓存稳定化 | 3-4 天 |
| **总计** | | **13-16 天** |

### 21.4 关键里程碑

| 里程碑 | 完成标志 | 可验收 |
|--------|----------|--------|
| MS1：边界保护完成 | Dict Management 不再暴露样式配置 | 是 |
| MS2：存储分离完成 | 样式配置读取 `sys_style_*`，字典只管业务枚举 | 是 |
| MS3：预设体系完成 | 7 套色彩 + 4 套布局可选 | 是 |
| MS4：样式页面重构完成 | Style Settings 为配置工作台 | 是 |
| MS5：布局联动完成 | 切换布局后导航/首页变化 | 是 |
| MS6：深矿蓝仪表盘完成 | 选择深矿蓝 + 仪表盘布局，能还原目标视觉 | 是 |
| MS7：版本回滚完成 | 保存生成快照，可手动回滚 | 是 |
| MS8：文档同步完成 | 所有文档与代码一致 | 是 |

---

## 二十二、扣分项优化后的执行与验收细则

本章用于补齐此前方案的扣分点：阶段粒度偏粗、迁移校验不够细、版本策略不够明确、前后端数据库一致性检查缺少逐项验收口径。执行时建议把本章作为开发任务拆分和提测 checklist。

### 22.1 扣分项修正总表

| 原扣分点 | 风险 | 本次优化后的落地方式 | 验收证据 |
|----------|------|----------------------|----------|
| 阶段一范围较大 | 容易一次性大改，难定位问题 | 拆成 P1.1-P1.8 八个任务包，每包可独立验收 | 每个任务包有代码清单、验收项、回滚点 |
| 存储策略表述曾有冲突 | 可能继续把样式写入 `sys_dict` | 明确阶段一直接使用 `sys_style_config`、`sys_style_preset` | 查询新表有数据，新增样式不写 `sys_dict` |
| 迁移规则不够细 | 颜色值可能迁成中文显示名，旧状态可能改坏 | 增加迁移前、迁移后、重复执行、回滚 SQL 检查 | SQL 查询截图或执行记录 |
| 版本回滚策略不够明确 | 快照时机、保留策略、回滚行为可能各做各的 | 明确保存成功后生成快照，回滚生成新版本，默认保留最近 100 条 | 版本表记录与回滚日志 |
| 一致性检查偏原则化 | Entity、init.sql、数据字典、前端类型容易漏一处 | 增加逐项文件级一致性检查表 | PR checklist 全部勾选 |
| UI 验收不够可操作 | “页面规整”主观判断强 | 增加 PC/移动端、预览、保存边界、只读状态验收标准 | 截图、操作录屏或测试记录 |

### 22.2 阶段一任务包拆分

阶段一建议按以下任务包执行。每个任务包都应尽量保持可单独提交、可单独验收，避免多人同时改同一个大文件。

| 任务包 | 目标 | 主要文件 | 输出物 | 验收标准 | 回滚点 |
|--------|------|----------|--------|----------|--------|
| P1.1 分类元数据 | 建立前后端统一的分类治理规则 | `frontend/src/constants/dictCategoryMeta.ts`、`backend/.../config/DictCategoryPolicy.java` | 分类域、受保护分类、可编辑规则 | `style/theme/home_layout/home_widget/category_visual_config` 均被识别为受保护 | 删除元数据文件并恢复旧分类列表 |
| P1.2 字典页保护 | Dict Management 不再编辑样式配置 | `frontend/src/views/DictManagement.vue`、`backend/.../service/SysDictService.java` | 默认隐藏、只读查看、后端写拦截 | 普通字典可 CRUD；样式分类通过 `/api/dict` 修改失败 | 关闭前端保护开关，保留后端拦截 |
| P1.3 专用表建模 | 建立样式配置专用存储 | `init.sql`、`SysStyleConfig.java`、`SysStylePreset.java`、Repository | `sys_style_config`、`sys_style_preset` | JPA 启动无映射错误；表字段与 Entity 一致 | 保留表但切回旧读取逻辑 |
| P1.4 数据迁移 | 将旧样式数据迁入新表 | `init.sql`、迁移 SQL、`StyleConfigService.java` | 幂等迁移脚本、兼容读取逻辑 | 重复执行不重复插入；颜色值不迁成中文标签 | 清空新表，继续读取旧 `sys_dict` |
| P1.5 样式 API 改造 | `/api/style` 读写新表 | `StyleConfigController.java`、`StyleConfigService.java`、`StyleConfigDTO.java`、`frontend/src/api/style.ts` | 配置读写、预设读取、重置接口 | 保存后刷新页面配置保持；旧字段兼容 | Service 内切回旧 `sys_dict` adapter |
| P1.6 样式页工作台 | 规整 Style Settings 页面结构 | `StyleSettings.vue`、`components/style-settings/*`、`useStyleSettingsForm.ts` | 概览、品牌、色彩、排版、布局、预览 | 页面不再是单长表单；保存/重置边界清晰 | 保留旧组件入口，回退路由引用 |
| P1.7 全局联动 | 让主题变量影响真实页面 | `useTheme.ts`、首页、产品列表、图表组件、布局组件 | CSS 变量、图表色板、布局变量 | 首页、产品列表、图表颜色随方案切换 | 关闭布局方案，仅保留颜色变量 |
| P1.8 文档同步 | 保持项目文档一致 | `README.md`、`docs/dev/*`、`docs/archive/项目完成总结.md`、`数据字典.md` | 功能、API、表结构、UI 文档更新 | AGENTS.md 要求的文档均已同步 | 无代码回滚，仅修正文档 |

### 22.3 阶段一逐步执行顺序

建议执行顺序：

```text
P1.1 分类元数据
  ↓
P1.2 字典页保护
  ↓
P1.3 专用表建模
  ↓
P1.4 数据迁移
  ↓
P1.5 样式 API 改造
  ↓
P1.6 样式页工作台
  ↓
P1.7 全局联动
  ↓
P1.8 文档同步
```

每个任务包完成后至少做一次本地验证。P1.3-P1.5 属于风险最高区域，必须在进入 P1.6 页面大改前先确认 API 和数据库稳定。

### 22.4 迁移执行与 SQL 验收清单

迁移目标：旧 `sys_dict` 样式相关数据保留不动，新表成为 Style Settings 的主数据源。

**迁移前检查：**

```sql
SELECT category, COUNT(*) AS total
FROM sys_dict
WHERE category IN ('style', 'theme', 'color_scheme', 'layout_style', 'font_preset', 'home_widget', 'home_layout')
GROUP BY category;
```

验收标准：

- 能明确哪些旧分类需要迁移。
- 若某分类不存在，不视为失败，但要确认默认预设是否由初始化 SQL 补齐。

**配置值迁移检查：**

```sql
SELECT config_key, config_value, value_type, status
FROM sys_style_config
WHERE config_key IN (
  'system_name',
  'logo_url',
  'active_theme',
  'active_color_scheme',
  'price_rise_color',
  'price_fall_color',
  'chart_colors',
  'active_layout_style',
  'font_size_preset'
);
```

验收标准：

- `price_rise_color`、`price_fall_color` 的 `config_value` 必须是 `#EF4444` 这类颜色值，不能是“上涨色”“下跌色”等中文显示名。
- `chart_colors` 如果是 JSON，必须能被后端解析为数组；如果是逗号分隔字符串，必须只在 Service 层统一转换。
- `status` 只使用项目已有状态值，如 `ACTIVE`、`INACTIVE`。

**预设迁移检查：**

```sql
SELECT preset_type, preset_key, preset_name, status
FROM sys_style_preset
WHERE preset_type IN ('color_scheme', 'layout_style', 'font_preset')
ORDER BY preset_type, sort_order;
```

验收标准：

- 色彩方案至少包含 `dashboard-blue-theme-upgrade.md` 要求的 7 套方案。
- 布局方案至少包含 4 套方案。
- 字号方案至少包含紧凑、标准、大字体、无障碍。
- `config_json` 不为空，并能通过后端 schema 校验。

**旧字典状态检查：**

```sql
SELECT DISTINCT status
FROM sys_dict;
```

验收标准：

- 不出现 `MIGRATED`、`DEPRECATED` 等 `CommonStatus` 不支持的状态。
- 旧样式分类保留 `ACTIVE` 或原状态，用于开发期兼容读取。

**重复执行检查：**

```sql
SELECT preset_type, preset_key, COUNT(*) AS duplicated
FROM sys_style_preset
GROUP BY preset_type, preset_key
HAVING COUNT(*) > 1;
```

验收标准：

- 返回空结果。
- 迁移 SQL 必须使用唯一键加 `ON DUPLICATE KEY UPDATE` 或 `WHERE NOT EXISTS`，保证重复执行安全。

**兼容兜底检查：**

| 场景 | 操作 | 通过标准 |
|------|------|----------|
| 新表为空，旧字典存在 | 临时清空开发库 `sys_style_config`、`sys_style_preset` 后启动 | `/api/style/config` 返回旧配置或默认配置，不报 500 |
| 新表存在，旧字典存在 | 正常启动 | `/api/style/config` 优先返回新表配置 |
| 旧 JSON 无法解析 | 构造异常旧数据 | 使用默认值并记录错误，不阻塞启动 |

### 22.5 阶段二版本策略细化

阶段二的版本管理只做“最小可用回滚”，不引入审批、灰度和实时同步。

| 策略项 | 规则 | 验收标准 |
|--------|------|----------|
| 快照时机 | `PUT /api/style/config` 保存成功后生成“保存后快照” | 保存成功一次，`sys_style_version` 增加一条 |
| 快照内容 | 保存完整 `StyleConfigDTO` JSON、当前预设 key、系统名称、Logo、布局、首页组件配置 | 任意版本详情可完整还原当前样式 |
| 版本号 | 使用 `vyyyyMMdd_HHmmss` 或 `vyyyyMMdd_序号` | 同一天多次保存不冲突 |
| 变更摘要 | 前端可传 `changeSummary`，不传则后端生成“样式配置更新” | 版本列表能看懂变更来源 |
| 变更人 | 记录当前用户 ID；系统初始化可用系统用户 | `changed_by` 非空或有明确系统标识 |
| 保留策略 | 默认保留最近 100 条；超过后删除最旧非保护版本 | 连续保存 101 次后历史记录数量不无限增长 |
| 回滚行为 | 回滚是一次新的写入：把历史快照写回 `sys_style_config`，再生成“回滚版本” | 回滚后版本数 +1，摘要包含被回滚版本号 |
| 权限 | 只有 ADMIN 可查看版本和回滚 | EDITOR/VIEWER 返回 403 |
| 缓存 | 保存、重置、回滚后主动清理样式缓存 | 刷新页面立即看到最新配置 |

推荐的回滚事务边界：

```text
读取目标版本快照
  ↓
校验 JSON schema 与必填字段
  ↓
开启事务
  ↓
写回 sys_style_config
  ↓
写入一条 rollback 类型 sys_style_version
  ↓
提交事务
  ↓
清理样式缓存
```

失败处理：

- 快照解析失败：拒绝回滚，当前配置保持不变。
- 写回失败：事务回滚，当前配置保持不变。
- 缓存清理失败：记录错误并提示刷新，数据库配置仍以已提交结果为准。

### 22.6 文件级一致性检查表

每次涉及 Style Settings 或 Dict Management 功能变更，必须按下表检查。

| 检查域 | 必查文件 | 检查内容 | 通过标准 |
|--------|----------|----------|----------|
| 后端 Entity | `SysStyleConfig.java`、`SysStylePreset.java`、`SysStyleVersion.java`、`SysDict.java` | `@Table`、`@Column`、字段类型、长度、可空性 | 与 `init.sql` 表结构一致 |
| 数据库初始化 | `backend/src/main/resources/init.sql` | 表结构、唯一键、索引、初始化数据、迁移 SQL 幂等性 | 新库可直接初始化，旧库可重复执行 |
| 数据字典文档 | `backend/src/main/resources/数据字典.md` | 新表字段、说明、索引、关系 | 与实际 SQL 一致 |
| 后端 DTO | `StyleConfigDTO.java`、版本 DTO | 字段名、类型、默认值 | 与前端 `StyleConfig` 对齐 |
| 后端 API | `StyleConfigController.java`、`SysDictController.java` | 路径、权限、请求响应结构 | 与前端 API 调用一致 |
| 前端 API | `frontend/src/api/style.ts`、`frontend/src/api/dict.ts` | URL、方法、参数、返回类型 | 与 Controller 一致 |
| 前端类型 | `frontend/src/types/theme.ts`、`frontend/src/types/dict.ts` | 可选字段、数组/字符串类型、枚举值 | 与 DTO 一致 |
| 前端 composable | `useTheme.ts`、`useDict.ts` | 缓存边界、加载时机、错误兜底 | `useTheme` 不调用 `/api/dict`，`useDict` 不调用 `/api/style` |
| 页面 | `StyleSettings.vue`、`DictManagement.vue` | 页面职责、只读/隐藏规则、保存边界 | 无交叉管理 |
| 项目文档 | `README.md`、`docs/dev/开发指南.md`、`docs/dev/项目设计文档.md`、`docs/dev/UI设计说明.md`、`docs/archive/项目完成总结.md` | 功能列表、API、表结构、UI 说明 | 与代码行为一致 |

### 22.7 UI 验收细则

**Style Settings 验收：**

| 验收项 | 操作 | 通过标准 |
|--------|------|----------|
| 信息架构 | 打开页面 | 能看到概览、品牌、色彩、排版、布局、预览，不再是无分组长表单 |
| 保存边界 | 修改品牌但不保存，切换到色彩区 | 页面提示有未保存更改，配置不丢失 |
| 重置本次修改 | 修改多个字段后点击重置 | 回到最近一次服务端配置 |
| 恢复默认 | 点击恢复默认并确认 | 回到系统默认主题，并写入服务端 |
| 预览真实性 | 切换色彩方案 | 价格涨跌、表格、图表色板同步变化 |
| 高级配置 | 打开高级设置 | 有格式校验，不允许保存非法颜色或非法 JSON |
| 移动端 | 375/390/430 宽度查看 | 无横向滚动，按钮文字不溢出 |
| PC 端 | 1366/1440/1920 宽度查看 | 预览面板和配置区不重叠 |

**Dict Management 验收：**

| 验收项 | 操作 | 通过标准 |
|--------|------|----------|
| 默认视图 | 打开字典管理 | 不显示 `style`、`theme`、`color_scheme` 等样式分类 |
| 系统配置查看 | ADMIN 开启“显示系统配置” | 可看到受保护分类，但编辑/删除按钮不可用 |
| 受保护写拦截 | 直接调用 `/api/dict` 修改 `style` | 后端拒绝，返回明确错误 |
| 普通字典编辑 | 修改 `currency` 或 `unit` | 保存成功，`useDict` 缓存刷新 |
| key 风险控制 | 尝试修改系统字典 key | 需要二次确认或直接禁止 |

### 22.8 提测与上线验收顺序

提测时按以下顺序验收，前一项失败不进入后一项：

1. 数据库初始化：新库启动、旧库迁移、重复执行 SQL。
2. 后端启动：JPA 映射无错误，`/api/style/config`、`/api/dict/categories` 正常。
3. 字典保护：Dict Management 不再编辑样式配置。
4. 样式读写：Style Settings 保存、重置、刷新保持。
5. 全局应用：首页、产品列表、图表、导航颜色与布局联动。
6. 响应式 UI：PC 和移动端布局无破损。
7. 阶段二版本：保存生成快照，ADMIN 可回滚，非 ADMIN 被拒绝。
8. 文档同步：项目文档、数据字典、设计文档全部更新。

### 22.9 最终可执行评分

按本章补齐后，本计划可执行性建议评分为 **9/10**。

仍保留 1 分风险，原因是：

- Style Settings 页面拆分和全局布局联动涉及前端多个组件，实际工作量可能受现有代码耦合度影响。
- 旧 `sys_dict` 样式数据的真实内容需要实施时用 SQL 再确认，迁移脚本可能需要根据现有数据微调。
- 版本快照的 JSON schema 需要在开发中落实为具体校验代码，否则会退化成“能存但不一定能回滚”。

扣分点已经转化为明确任务包和验收项，后续只要按 P1.1-P1.8、阶段二版本策略、SQL 验收清单推进，就可以逐步落地。

---

## 阶段一完成状态（2026-05-20）

### 已完成任务包

| 任务包 | 完成状态 | 完成日期 | 关键交付物 |
|--------|----------|----------|------------|
| P1.1 分类元数据 | ✅ 完成 | 2026-05-20 | `dictCategoryMeta.ts`、受保护分类定义 |
| P1.2 字典页保护 | ✅ 完成 | 2026-05-20 | 后端写拦截、前端隐藏样式分类 |
| P1.3 专用表建模 | ✅ 完成 | 2026-05-20 | `SysStyleConfig.java`、`SysStylePreset.java`、V7迁移 |
| P1.4 数据迁移 | ✅ 完成 | 2026-05-20 | V7 SQL初始化数据，Logo存Base64 |
| P1.5 样式 API 改造 | ✅ 完成 | 2026-05-20 | 色彩/布局/字号切换API、预设API |
| P1.6 样式页工作台 | ✅ 完成 | 2026-05-20 | 色彩/布局预设选择、组件拆分 |
| P1.7 全局联动 | ✅ 完成 | 2026-05-20 | CSS变量应用、Layout.vue布局切换 |
| P1.8 文档同步 | ✅ 完成 | 2026-05-20 | README、项目完成总结更新 |

### 关键实现细节

**Logo存储方案变更：**
- 原方案：文件系统存储 `/app/data/logos/`
- 实际方案：Base64编码存数据库 `sys_style_config.config_value`
- 格式：`data:image/png;base64,xxx`
- 限制：1.5MB（编码后约2MB）
- 优点：跨平台兼容、无文件路径问题

**CSS变量应用：**
```css
--app-nav-bg: 导航背景色
--app-nav-text: 导航文字色
--app-page-bg: 页面背景色
--app-card-radius: 卡片圆角
```

**预设数据：**
- 色彩方案：7套（青绿经典、经典红绿、美股绿红、商务蓝橙、高贵紫金、深矿蓝、暖色系）
- 布局方案：4套（经典顶部导航、左侧导航、深矿蓝仪表盘、极简卡片式）
- 字号预设：4套（紧凑、标准、大字体、特大字体）

---

## 阶段二完成状态（2026-05-20）

### 已完成任务

| 功能 | 完成状态 | 完成日期 | 关键交付物 |
|------|----------|----------|------------|
| 版本快照表 | ✅ 完成 | 2026-05-20 | `sys_style_version`、V8迁移 |
| 保存生成快照 | ✅ 完成 | 2026-05-20 | `StyleConfigService.updateStyleConfig()` |
| 版本列表API | ✅ 完成 | 2026-05-20 | `GET /api/style/versions` |
| 版本详情API | ✅ 完成 | 2026-05-20 | `GET /api/style/versions/{id}` |
| 回滚API | ✅ 完成 | 2026-05-20 | `POST /api/style/rollback/{id}` |
| 前端版本入口 | ✅ 完成 | 2026-05-20 | StyleSettings"历史版本"按钮 |
| 缓存稳定化 | ✅ 完成 | 2026-05-20 | `@CacheEvict` 注解、保存/回滚清理缓存 |

### 关键实现细节

**版本号格式：** `vyyyyMMdd_HHmmss`（如 `v20260520_143052`）

**保留策略：** 最近100条版本记录，超出自动清理最旧版本

**回滚行为：**
1. 读取目标版本快照
2. 校验 JSON schema
3. 写回 `sys_style_config`
4. 生成"回滚版本"记录
5. 清理缓存

**权限控制：** 仅 ADMIN 可查看版本列表和执行回滚

---

## 后续增强规划（暂不纳入开发）

### 版本策略

| 策略项 | 规则 |
|--------|------|
| 快照时机 | `PUT /api/style/config` 成功后 |
| 版本号格式 | `vyyyyMMdd_HHmmss` |
| 保留策略 | 最近100条 |
| 回滚行为 | 写回config + 生成新版本 |
| 权限 | 仅ADMIN |

---

## 后续增强规划

以下功能暂不纳入阶段二，待业务需求明确后立项：

| 能力 | 触发条件 | 说明 |
|------|----------|------|
| 灰度发布 | 多品牌/多租户 | `sys_style_grayscale` |
| 配置继承 | 组织层级覆盖 | `sys_style_inherit` |
| WebSocket同步 | 多端实时刷新 | `/ws/style/sync` |
| 配置审批 | 变更需审批 | 接入审批流程 |
| A/B测试 | 样式效果统计 | 数据埋点分析 |

---

## 后续增强规划（暂不纳入开发）

以下功能暂不纳入阶段二，待业务需求明确后立项：

| 能力 | 触发条件 | 说明 |
|------|----------|------|
| 灰度发布 | 多品牌/多租户 | `sys_style_grayscale` |
| 配置继承 | 组织层级覆盖 | `sys_style_inherit` |
| WebSocket同步 | 多端实时刷新 | `/ws/style/sync` |
| 配置审批 | 变更需审批 | 接入审批流程 |
| A/B测试 | 样式效果统计 | 数据埋点分析 |

---

## 已修复问题（2026-05-20）

### 代码审查改进项

| 问题 | 修复内容 | 状态 |
|------|----------|------|
| cleanupOldVersions() 清理逻辑错误 | 使用原生 SQL `deleteOldVersions(keepCount)` 按时间排序删除 | ✅ 已修复 |
| 回滚操作未记录用户 ID | 使用 `SecurityUtils.getCurrentUserId()` 获取当前用户 | ✅ 已修复 |
| getConfigSnapshot() 返回 null | JSON 解析失败时抛出 `IllegalStateException` | ✅ 已修复 |
| 回滚操作未记录日志 | 添加 `OperationLogHelper` 记录成功/失败日志 | ✅ 已修复 |

### 修复详情

**1. cleanupOldVersions() 清理逻辑修复**

原问题：`deleteByIdLessThanEqual(thresholdId)` 会误删 ID 较小但时间较新的记录。

修复方案：
```java
// Repository 新增方法
@Modifying
@Query(value = "DELETE FROM sys_style_version WHERE id NOT IN " +
        "(SELECT id FROM (SELECT id FROM sys_style_version ORDER BY created_time DESC LIMIT :keepCount) AS tmp)",
        nativeQuery = true)
int deleteOldVersions(int keepCount);

// Service 调用
int deleted = versionRepository.deleteOldVersions(MAX_VERSIONS);
log.info("已清理 {} 条旧版本, 保留最近 {} 条", deleted, MAX_VERSIONS);
```

**2. 回滚操作人 ID 获取**

修复方案：
```java
Long changedBy = SecurityUtils.getCurrentUserId();
styleConfigService.rollbackToVersion(versionId, changedBy);
log.info("样式配置回滚成功: versionId={}, changedBy={}", versionId, changedBy);
```

**3. getConfigSnapshot() null 处理**

修复方案：
```java
try {
    return objectMapper.readValue(version.getConfigSnapshot(), StyleConfigDTO.class);
} catch (JsonProcessingException e) {
    log.error("解析配置快照失败: versionId={}", versionId, e);
    throw new IllegalStateException("版本快照损坏，无法解析: " + versionId, e);
}
```

**4. 回滚操作日志**

修复方案：
```java
// 成功日志
operationLogHelper.logSuccess("样式设置", OperationLog.OperationType.UPDATE,
        "回滚样式配置到版本: " + versionId, "versionId=" + versionId);

// 失败日志
operationLogHelper.logError("样式设置", OperationLog.OperationType.UPDATE,
        "回滚样式配置失败", "versionId=" + versionId, e.getMessage());
```

---

## Dict Management 待完善项完成状态（2026-05-20）

根据治理方案，Dict Management 页面需要4项改进：

| 改进项 | 完成状态 | 完成日期 | 实现说明 |
|--------|----------|----------|----------|
| "显示系统配置"开关 | ✅ 完成 | 阶段一 | `showSystemConfig` ref，checkbox 控制 |
| 受保护分类只读展示 | ✅ 完成 | 阶段一 | `isProtectedCategory()` 判断，隐藏编辑/删除按钮 |
| 跳转Style Settings按钮 | ✅ 完成 | 阶段一 | `goToStyleSettings()` 函数，`protected-notice` 区域 |
| extraValue智能渲染 | ✅ 完成 | 2026-05-20 | 根据分类元数据渲染 color/icon/json/text |

### extraValue 智能渲染实现详情

**新增工具函数（`dictCategoryMeta.ts`）：**
- `getExtraValueMode(category)` - 获取渲染模式
- `isColorValue(value)` - 判断是否为颜色值（#RGB/#RRGGBB/#RRGGBBAA）
- `isJsonValue(value)` - 判断是否为 JSON
- `formatJsonDisplay(value)` - 格式化 JSON 显示（缩进2空格）

**渲染类型：**
| 模式 | 渲染效果 | 适用分类 |
|------|----------|----------|
| `color` | 色块 + 色值代码 | common_status, dept_type, approval_status |
| `icon` | 图标名称预览 | user_role |
| `json` | 格式化 JSON + 复制按钮 | category_visual_config, 受保护分类的 JSON |
| `readonly` | 只读文本（灰色斜体） | style, theme 等受保护分类 |
| `text` | 普通文本徽章 | currency, unit, origin 等 |

**前端组件改动：**
- PC端表格：`extra-col` 列使用 `getExtraValueRender(dict)` 智能渲染
- 移动端卡片：`card-detail` 区域使用智能渲染
- 新增 CSS：`.extra-color`, `.color-swatch`, `.extra-json`, `.json-preview`, `.copy-btn`

### 系统改善效果

**Dict Management 改善：**

| 改善点 | 改善前 | 改善后 |
|--------|--------|--------|
| 样式配置暴露 | `style`、`theme`等分类可随意编辑 | 默认隐藏，开启后只读 |
| extraValue显示 | 纯文本显示，JSON难以阅读 | 智能渲染：颜色色块、JSON格式化+复制 |
| 误改风险 | 高（任何人可改样式底层配置） | 低（受保护分类需通过Style Settings修改） |
| 职责边界 | 模糊（两入口都能改样式） | 清晰（Dict管编码，Style管体验） |

**extraValue智能渲染效果示例：**

| 分类 | extraValue示例 | 渲染效果 |
|------|----------------|----------|
| common_status | `#0D6E6E` | 🟩 `#0D6E6E`（色块+色值） |
| user_role | `shield` | `shield`（图标名预览） |
| category_visual_config | `{"color":"#xxx"}` | 格式化JSON + 复制按钮 |
| style（受保护） | 任意值 | 灰色只读文本 |

**系统整体改善：**

1. **降低配置错误风险** - 样式配置不再被字典页误改
2. **提升运维效率** - JSON配置可复制，颜色值直观显示
3. **职责清晰** - Dict Management专注业务枚举，Style Settings专注品牌体验
4. **权限可控** - ADMIN可查看系统配置，但修改需走专业入口

---

*文档创建日期：2026-05-20*
*最后更新：2026-05-20 — Dict Management 待完善项全部完成、extraValue智能渲染实现、系统改善效果说明*
