# 分类视觉预设系统开发方案

**创建日期：2026-05-23**  
**适用范围：** `frontend/src/components/style-settings/CategoryVisualPanel.vue`、`frontend/src/composables/useCategoryPreviewState.ts`、`frontend/src/composables/useCategoryVisual.ts`、`frontend/src/components/icons/CategoryIcons.vue`、`frontend/src/components/style-settings/preview/CategoryPreview.vue`、`frontend/src/views/Home.vue`、`frontend/src/types/index.ts`  
**目标：** 将 Style Settings 中“分类视觉”从手工调色工具升级为面向矿产品价格展示场景的“分类视觉方案选择器”，让用户可为每个已建立分类选择一套成熟视觉方案，首页产品卡片呈现接近附件所示的清爽、专业、分品类识别明确的效果。

---

## Context

项目定位是“价格管理和展示系统”。首页需要承载两类信息：

- 价格概览：少量重点产品，高识别度卡片，体现价格、涨跌、趋势。
- 产品列表：较多产品，快速扫描，产品类别需要通过 icon、颜色和轻量背景迅速区分。

用户提供的附件视觉特征可以概括为：

| 视觉要素 | 附件表现 | 对系统的启发 |
|----------|----------|--------------|
| 背景 | 浅灰蓝页面底，白色内容卡片 | 保持全局页面干净，不让分类色污染大面积背景 |
| 产品卡片 | 白底、细边框、柔和阴影、轻微色彩倾向 | 分类色只做识别，不做整块强填充 |
| 分类标识 | 小圆/圆角 icon 容器，颜色鲜明 | 每套方案必须包含 icon 与 icon 背景色 |
| 价格文字 | 主色强调，层级清楚 | 分类主色可用于产品名/icon，价格仍需尊重全局价格色规则 |
| 趋势图 | 与产品色系一致的线条和渐隐填充 | 分类色应能输出 chart line/fill token |
| 色彩关系 | 蓝、绿、紫、橙并存但都低饱和、浅底 | 预设应控制饱和度和明度，避免与 Style Settings 全局主题冲突 |

当前代码已经具备基础链路：

- `useCategoryPreviewState.ts` 管理分类视觉配置和草稿保存。
- `category_visual_config` 字典项保存分类视觉配置。
- `useCategoryVisual.ts` 在首页解析配置。
- `Home.vue` 已通过 `getCategoryVisual()` 和 `getCategoryCardStyle()` 消费分类视觉。
- `CategoryIcons.vue` 已有 6 个 SVG 图标。
- `CategoryVisualPanel.vue` 已有分类选择、颜色配置、图标配置、预设配色和预览。

但当前体验还不符合“视觉总监级可用”：

| 问题 | 当前表现 | 影响 |
|------|----------|------|
| 过度自定义 | 用户需要手工改主色、辅色、文本色、边框色、光晕 | 决策成本高，容易配出不专业方案 |
| 预设不完整 | 预设只是颜色块，缺少方案语义、icon、卡片效果、适用场景 | 用户不知道该选哪套 |
| 颜色冲突风险 | 旧预设里 `borderColor` 固定为全局蓝 `#165DFF` | 分类色和全局主色混用，识别不清 |
| 图标选择弱 | 图标以文本按钮呈现，缺少视觉预览 | 用户无法判断首页实际效果 |
| 图标数量不足 | 只有 6 种矿产品图标 | 附件中的化工、指数、循环/废钢等类别表达不足 |
| 预览不够贴近首页 | 预览卡片不像附件中的首页产品卡片 | Style Settings 里选完后，用户不确定真实首页效果 |

---

## 产品目标

### 目标

1. 已建立的每个产品分类都能独立选择一套视觉方案。
2. 用户选择的是“默认设计好的方案”，不是完全自由自定义。
3. 每套方案包含 icon、颜色体系、卡片边框、浅色背景、趋势图颜色和暗色模式适配。
4. 分类视觉与 Style Settings 的全局色彩体系协同，不抢全局主色，不破坏涨跌色、页面背景、卡片背景。
5. 首页展示效果接近附件：干净、轻盈、信息密度高，产品类别清晰但不过度装饰。

### 核心口径

**分类列表必须以业务分类 `product_category` 为源，视觉配置只是分类的附属表现配置。**

这意味着：

- 新建产品分类后，即使还没有视觉配置，也必须能在分类视觉面板出现。
- 删除或停用产品分类后，面板不应继续把残留视觉配置当成有效分类展示。
- `category_visual_config` 不再承担“分类主数据”职责，只保存视觉 token。
- 首页产品卡片仍以产品关联的 `categoryId/category.code` 作为匹配入口。

### 非目标

本阶段不做：

- 自定义上传 icon。
- 任意颜色编辑器作为主入口。
- 每个产品单独配置视觉，仍以“分类”为单位。
- 新增后端表结构。
- 首页整体重构为附件完全同款。

---

## 设计原则

### 1. 分类色是“识别色”，不是“主题色”

全局主题色仍由 Style Settings 的“色彩”模块控制。分类视觉只作用于：

- 分类 icon 线条/填充
- icon 背景浅色
- 产品名或分类名强调
- 卡片边框弱色
- 卡片背景极浅渐变
- 趋势图线条和面积渐变

不应用于：

- 全局按钮主色
- 页面背景
- 导航高亮
- 涨跌文字色
- 表单 focus 色

### 2. 预设优先，微调收口

主流程是选择预设。可保留“高级微调”但默认折叠，避免用户把页面调乱。

推荐交互：

```text
分类视觉
  1. 选择分类
  2. 选择视觉方案
  3. 查看首页卡片预览
  4. 保存

高级微调
  - 仅显示当前方案 token
  - 可恢复方案默认值
  - 不作为主入口
```

### 3. 方案要有命名和适用说明

不要只显示 `GOLD`、`COPPER`。每套方案应有中文名称、适用产品、视觉气质。

示例：

- 蓝晶矿脉：适合铝、铅锌、矿石类。
- 绿能金属：适合电铜、能源金属、活跃交易品。
- 紫钢合金：适合钢坯、钢材、稀有合金。
- 橙色指数：适合指数、铁精粉、波动提醒。

---

## 视觉方案库设计

### 方案库扩展评估：5 个选项卡 × 每个 10 套方案

用户希望方案库提供更多选择：在方案库中设置 5 个选项卡，每个选项卡下提供 10 套颜色方案，总计 50 套分类视觉方案。

从视觉总监与项目总监角度评估，这个方向是合理的，但必须做成“结构化方案库”，而不是简单堆叠 50 张颜色卡。

| 评估维度 | 结论 | 说明 |
|----------|------|------|
| 业务价值 | 高 | 价格系统面对矿石、金属、化工、指数、回收料等多类型产品，8 套基础方案覆盖面有限，50 套能更好支持多分类并存 |
| 视觉质量 | 中高，取决于约束 | 如果每套都按同一 token 体系和对比度规则设计，能提升专业度；如果只是随机配色，会造成首页碎片化 |
| 用户体验 | 中 | 选择更多会提升命中率，但也增加决策成本，必须通过选项卡、推荐方案、搜索/筛选降低负担 |
| 开发成本 | 中 | 前端常量与 UI 扩展成本可控，不需要新增后端表；主要成本在方案命名、色彩校验和展示组织 |
| 维护成本 | 中高 | 50 套方案需要版本管理、命名规范、适用说明和批量校验，否则后续难维护 |
| 与全局色彩冲突风险 | 可控 | 只要坚持分类色是“识别色”而非“主题色”，并执行色彩安全校验，风险可控 |

综合评分：**8.6 / 10**。

推荐采纳，但采用两阶段策略：

1. **第一阶段**保留 8 套核心推荐方案作为“精选/推荐”，保证主流程简单。
2. **第二阶段**扩展为 5 个选项卡、每个 10 套的完整方案库，作为“更多方案”能力。

不建议一开始把 50 套全部平铺到主视图。主视图应先展示“推荐方案”，完整方案库通过 tab 组织，用户有明确意图时再进入更多选择。

### 方案库选项卡信息架构

建议 5 个选项卡按产品业务语义和视觉气质划分，而不是按“红橙黄绿蓝”这种纯颜色分类。这样用户是在选择“产品气质”，不是做美术配色题。

| Tab Key | 选项卡名称 | 设计方向 | 适用产品 |
|---------|------------|----------|----------|
| `ore_metal` | 矿石金属 | 蓝、青、灰、冷金属色，稳定、专业 | 铝矿、铜矿、铅锌矿、铁矿、普通矿石 |
| `energy_active` | 能源活跃 | 绿、青绿、蓝绿，高流动性、交易感 | 电铜、能源金属、活跃交易品、材料 |
| `precious_index` | 贵金指数 | 金、橙、琥珀、深红，价值感与波动提醒 | 金、银、贵金属、指数、铁精粉指数 |
| `steel_alloy` | 钢铁合金 | 紫、靛、石墨、冷灰，结构感、工业感 | 钢坯、钢材、合金、稀土、钼钨镍 |
| `chemical_recycle` | 化工循环 | 青蓝、薄荷、灰绿、中性色，清洁、环保、辅助材料 | 硫酸、化工辅料、试剂、废钢、回收料 |

每个选项卡下提供 10 套方案，总计 50 套。每套方案仍必须包含完整 token：

- `id`
- `group`
- `version`
- `name`
- `description`
- `recommendedFor`
- `icon`
- `primaryColor`
- `secondaryColor`
- `surfaceColor`
- `textColor`
- `borderColor`
- `chartLineColor`
- `chartAreaColor`
- `glowColor`
- `darkMode`

新增 `group` 字段后，方案库 UI 可以直接按 tab 过滤：

```typescript
export type CategoryVisualPresetGroup =
  | 'ore_metal'
  | 'energy_active'
  | 'precious_index'
  | 'steel_alloy'
  | 'chemical_recycle'

export interface CategoryVisualPreset {
  id: string
  group: CategoryVisualPresetGroup
  version: number
  name: string
  description: string
  recommendedFor: string[]
  icon: string
  primaryColor: string
  secondaryColor: string
  textColor: string
  borderColor: string
  surfaceColor: string
  chartLineColor: string
  chartAreaColor: string
  glowColor: string
  darkMode: {
    primaryColor: string
    textColor: string
    borderColor: string
    surfaceColor: string
    glowColor: string
  }
}
```

### 50 套方案的设计原则

50 套方案不能只是“换主色”。每套都要有可解释的产品语义和稳定的视觉边界。

| 原则 | 约束 |
|------|------|
| 每个 tab 内要有层次 | 10 套方案应覆盖冷暖、轻重、活跃/稳健，不做 10 个近似蓝色 |
| 每套方案要能落到首页 | 必须同时检查概览卡、列表卡、mini chart，不只看色板 |
| icon 不必 50 个完全不同 | 可以复用 icon，但同一个 tab 内避免连续重复超过 3 套 |
| 命名必须业务化 | 使用“蓝晶矿脉”“绿能电解”“琥珀指数”这类名称，避免 `blue_01` |
| 推荐仍优先 | 系统按分类名称推荐 2-3 套，不让用户从 50 套里盲选 |
| 选项卡默认落点 | 进入方案库时默认定位到当前分类推荐 tab，而不是固定第一个 tab |
| 不允许强饱和大面积色 | 首页背景和卡片浅底必须继续克制，避免彩色拼贴 |

### 方案库 UI 设计调整

完整方案库建议改为：

```text
全部方案库
  [矿石金属] [能源活跃] [贵金指数] [钢铁合金] [化工循环]

  当前 Tab：矿石金属
  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
  │ 方案 1 │ │ 方案 2 │ │ 方案 3 │ │ 方案 4 │ │ 方案 5 │
  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘
  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐
  │ 方案 6 │ │ 方案 7 │ │ 方案 8 │ │ 方案 9 │ │ 方案10 │
  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘
```

交互细节：

- 推荐方案区仍固定展示 2-3 套，位于完整方案库上方。
- 完整方案库使用 5 个 tab，每个 tab 内 10 张方案卡。
- 当前分类若命中某个 tab 的关键词，默认打开该 tab。
- 方案卡显示名称、icon、适用说明、三枚色块、迷你趋势线。
- 卡片上标记“推荐”“已应用”“已微调”三种状态，但同一张卡不超过两个徽标。
- 移动端 tab 横向滚动，方案卡改为 2 列或 1 列，避免文字挤压。

### 50 套方案的验收门槛

在进入开发前，50 套方案需要先通过设计审查表。

| 检查项 | 标准 |
|--------|------|
| 数量 | 5 个 tab，每个 tab 正好 10 套 |
| 唯一性 | `id/name` 不重复，同 tab 内主色距离不低于 24 |
| 可读性 | 所有方案通过对比度和光晕透明度校验 |
| 业务语义 | 每套方案至少有 2 个推荐产品关键词 |
| 首页效果 | 任意 8 套同时出现在首页时不显得混乱 |
| 暗色适配 | 每套方案都有 darkMode token |
| 版本 | 所有方案 `version` 初始为 1 |

### 本次方案修订结论

建议将当前“8 套方案库”定位为 **精选基础库**，将用户提出的 “5 个选项卡 × 每个 10 套” 定位为 **完整方案库 V2**。

推荐最终产品形态（本轮体验修正版）：

```text
整体组合方案：5 套内置组合 + 1 套“我的组合”，用于批量生成分类视觉草稿
完整方案库：5 个选项卡 × 10 套，共 50 套，供当前分类单独选择
当前分类预览：与左侧选中分类同源，不再维护独立选中态
微调助手：仅管理员可见，默认折叠，用人可理解的强度控制替代裸颜色入口
```

“推荐方案”不再作为独立 UI 区块展示。推荐算法仍保留在底层，用于新分类默认方案、组合方案 fallback、默认打开方案分组等场景，避免页面同时出现“推荐方案”和“全部方案库”造成决策噪音。

### 分类视觉组合方案：比单套颜色更高一层的选择

在 50 套完整方案库之上，建议增加“组合方案”能力。组合方案不是单个颜色方案，而是一组已经搭配好的分类视觉映射。用户选择一个组合后，系统会自动为当前已有分类批量套用推荐方案。

这比让用户逐个分类、逐个颜色选择更高效，也更符合视觉设计总监的工作方式：先确定整体风格方向，再微调单个分类。

示例：

```text
组合方案：稳健矿业
  铝矿/铅锌矿 → 蓝晶矿脉
  电铜 → 绿能电解
  铁精粉/指数 → 橙色指数
  金 → 贵金暖金
  银 → 银灰金属
  废钢/回收料 → 石墨循环
```

#### 组合方案与单套方案的关系

| 层级 | 用户选择对象 | 作用范围 | 适合场景 |
|------|--------------|----------|----------|
| 推荐方案 | 当前分类的 2-3 套推荐 | 单个分类 | 用户只想快速设置一个分类 |
| 完整方案库 | 5 个 tab 下的 50 套方案 | 单个分类 | 用户想精细挑选某个分类风格 |
| 组合方案 | 一组分类到方案的映射 | 多个分类批量套用 | 新系统初始化、分类较多、希望整体风格统一 |

#### 第一批组合方案建议

| comboId | 中文名 | 视觉气质 | 推荐场景 |
|---------|--------|----------|----------|
| `steady_mining` | 稳健矿业 | 蓝、绿、灰为主，清爽稳重 | 默认推荐，适合大多数矿产品价格系统 |
| `active_trading` | 活跃交易 | 蓝绿、橙、紫对比更明确 | 交易频繁、价格波动较多的业务 |
| `precious_focus` | 贵金聚焦 | 金、银、琥珀、深紫形成价值感 | 贵金属、指数、高价值产品占比较高 |
| `industrial_alloy` | 工业合金 | 石墨灰、靛紫、钢蓝，工业感更强 | 钢铁、合金、稀有金属较多 |
| `clean_chemical` | 化工循环 | 青蓝、薄荷、灰绿，轻盈洁净 | 化工辅料、酸类、回收材料较多 |

#### 组合方案数据结构建议

建议新增：

```typescript
export interface CategoryVisualComboRule {
  keywords: string[]
  presetId: string
  fallbackPresetId?: string
}

export interface CategoryVisualCombo {
  id: string
  version: number
  name: string
  description: string
  tone: string
  recommendedFor: string[]
  rules: CategoryVisualComboRule[]
  fallbackPresetIds: string[]
}
```

应用逻辑：

1. 用户在当前分类视觉面板选择一个组合方案。
2. 系统扫描当前 `product_category` 中的分类名称和编码。
3. 根据组合方案中的 `rules.keywords` 匹配最合适的 `presetId`。
4. 没匹配到的分类从 `fallbackPresetIds` 中按顺序分配，避免多个分类全都使用同一套颜色。
5. 批量生成草稿，不立即保存。
6. 用户可以预览整体效果，再点击保存。

#### 组合方案交互设计

在分类视觉面板中，组合方案应放在单分类方案库之前：

```text
分类视觉

整体组合方案
  [稳健矿业] [活跃交易] [贵金聚焦] [工业合金] [化工循环] [我的组合]
  应用于全部分类 / 仅应用于未配置分类

当前分类
  [分类列表]

完整方案库
  [5 个选项卡 × 每个 10 套]
```

必须提供两个应用范围：

| 应用范围 | 行为 |
|----------|------|
| 应用于全部分类 | 覆盖所有当前分类草稿，适合初始化或整体换风格 |
| 仅应用于未配置分类 | 保留已有分类选择，只给新分类补齐视觉 |

设计上推荐默认选择“仅应用于未配置分类”，因为它更安全，不会意外覆盖用户已有配置。

交互规则：

- 点击组合卡片后直接更新当前分类视觉草稿，但不立即持久化。
- 选中组合后的整体预览必须放在右侧实时预览区，而不是挤在中间配置区。
- 中间配置区只显示轻量摘要：预计更新数量、保留数量、当前应用范围。
- 右侧实时预览展示多分类并排卡片、当前分类详情卡，以及每个分类将匹配到的方案、icon、主色、是否 fallback。
- 组合应用后仍不自动保存，必须点击 Style Settings 顶部“保存配置”才全局生效。
- 不设置常驻“放弃修改”按钮，避免用户误以为草稿必须主动撤销；未点击“保存配置”就不影响正式配置。
- 重新进入 Style Settings 或确认离开未保存页面时，分类视觉草稿必须从服务端配置强制重载，避免未保存的内存草稿被误认为已经生效。
- 默认应用范围为“仅未配置”，减少误覆盖。

#### 自定义组合方案：仅允许 1 组

除 5 套内置组合外，系统提供 1 套用户自定义组合，命名为“我的组合”。自定义组合不是多模板管理系统，只保存一组当前分类到视觉方案的映射，避免 Style Settings 复杂化。

| 能力 | 规则 |
|------|------|
| 数量 | 全系统只允许 1 组自定义组合 |
| 创建 | 可将当前全部分类视觉保存为“我的组合” |
| 覆盖 | 已存在时可用当前配置覆盖保存，覆盖前确认 |
| 应用 | 应用后只生成草稿，不自动保存分类视觉 |
| 存储 | 继续使用字典，建议 `sys_dict.category = category_visual_custom_combo`，`dictKey = my_combo` |
| 展示 | 与内置组合并列展示，但明确标识为自定义 |

自定义组合保存结构建议：

```json
{
  "version": 1,
  "name": "我的组合",
  "description": "由当前分类视觉保存",
  "updatedAt": "2026-05-23T10:20:00.000Z",
  "mappings": [
    {
      "categoryId": 1,
      "categoryCode": "COPPER",
      "presetId": "green_energy",
      "presetVersion": 1,
      "customized": false,
      "config": {
        "icon": "bolt_metal",
        "primaryColor": "#2E8B57",
        "surfaceColor": "#ECFDF3",
        "borderColor": "#B7E4C7",
        "chartLineColor": "#159947"
      }
    }
  ]
}
```

如果当前分类数量或编码变化，应用“我的组合”时按 `categoryCode` 优先匹配，匹配不到再按 `categoryId`，仍匹配不到则保留当前分类配置。

#### 组合方案的价值

| 价值 | 说明 |
|------|------|
| 降低决策成本 | 用户不用从 50 套里逐个挑 |
| 保持整体一致性 | 组合内的颜色已经按首页并排展示关系设计 |
| 适合新系统初始化 | 新建项目或新导入分类后，一键得到专业视觉 |
| 兼容高级用户 | 用户仍可对单个分类换方案或做管理员微调 |

#### 风险控制

- 组合方案只生成草稿，不自动保存。
- 覆盖全部分类前必须二次确认。
- `customized: true` 的分类默认不被覆盖，除非用户明确选择“覆盖已微调分类”。
- 应用组合后，需要显示变更摘要：多少分类被更新、多少分类保留、多少分类未匹配使用 fallback。
- 组合方案仍必须服从全部色彩硬指标。

### 方案数据结构

建议新增前端常量文件：

```text
frontend/src/constants/categoryVisualPresets.ts
```

推荐类型：

```typescript
export interface CategoryVisualPreset {
  id: string
  version: number
  name: string
  description: string
  recommendedFor: string[]
  icon: string
  primaryColor: string
  secondaryColor: string
  textColor: string
  borderColor: string
  surfaceColor: string
  chartLineColor: string
  chartAreaColor: string
  glowColor: string
  darkMode: {
    primaryColor: string
    textColor: string
    borderColor: string
    surfaceColor: string
    glowColor: string
  }
}
```

`CategoryVisualConfig` 建议扩展：

```typescript
export interface CategoryVisualConfig {
  categoryId?: number
  categoryCode?: string
  presetId?: string
  presetVersion?: number
  customized?: boolean
  primaryColor: string
  secondaryColor: string
  textColor: string
  borderColor: string
  surfaceColor?: string
  chartLineColor?: string
  chartAreaColor?: string
  glowColor: string
  icon: string
  iconType: 'builtin' | 'svg' | 'image'
  darkMode?: CategoryDarkModeConfig
}
```

兼容策略：

- 旧配置没有 `presetId` 时，继续按原字段渲染。
- 旧配置没有 `presetVersion` 时，视为用户自定义或旧版配置，不自动覆盖。
- 旧配置没有 `surfaceColor/chartLineColor/chartAreaColor` 时，用 `secondaryColor/primaryColor/glowColor` 回退。
- 保存新配置时写入完整字段，便于首页稳定渲染。

### 预设版本规则

预设常量必须包含 `version`。保存到分类配置时写入 `presetVersion`。

升级策略：

| 场景 | 处理方式 |
|------|----------|
| 预设常量升级，用户已保存旧版本 | 保持已保存 token，不自动覆盖首页效果 |
| 用户在面板中点击“更新到新版方案” | 用当前预设重新生成 token，并更新 `presetVersion` |
| 用户曾做高级微调 | 标记为 `customized: true`，不提示自动升级 |
| 无配置分类 | 按最新预设推荐生成草稿，但不保存 |

这样可以避免设计方案升级导致线上分类卡片突然变色。

### 第一批推荐预设

预设要覆盖附件中常见视觉：蓝、绿、紫、橙，以及贵金属/中性色。

| presetId | 中文名 | 主色 | 浅底 | icon | 适用分类 |
|----------|--------|------|------|------|----------|
| `blue_ore` | 蓝晶矿脉 | `#2563EB` | `#EFF6FF` | `cube_ore` | 铝铜矿、铅锌矿、普通矿石 |
| `green_energy` | 绿能金属 | `#2E8B57` | `#ECFDF3` | `bolt_metal` | 电铜、能源金属、活跃交易品 |
| `violet_alloy` | 紫钢合金 | `#6D28D9` | `#F5F0FF` | `alloy_grid` | 钢坯、钢材、合金、稀土 |
| `orange_index` | 橙色指数 | `#EA580C` | `#FFF4E8` | `bar_index` | 指数、铁精粉、价格波动类 |
| `cyan_chemical` | 青蓝试剂 | `#0891B2` | `#ECFEFF` | `flask` | 硫酸、化工辅料、试剂类 |
| `gold_precious` | 贵金暖金 | `#B7791F` | `#FFF7E6` | `gold_ingot` | 金、贵金属、高价值产品 |
| `silver_neutral` | 银灰金属 | `#64748B` | `#F1F5F9` | `silver_bar` | 银、通用金属、中性类别 |
| `graphite_recycle` | 石墨循环 | `#475569` | `#F8FAFC` | `recycle_steel` | 废钢、回收料、低饱和工业类 |

色彩约束：

- 主色避免使用全局默认主蓝 `#0D6E6E` 或 `#165DFF` 的完全同值。
- 主色饱和度控制在中等，避免荧光色。
- `surfaceColor` 必须接近白色，仅用于 6%-10% 的背景混合。
- `borderColor` 用主色的浅化版本，不再固定为系统蓝。
- `glowColor` 透明度建议 `0.12-0.18`。

### 色彩硬指标

预设入库前必须满足以下设计质量门槛：

| 指标 | 标准 |
|------|------|
| 文本对比度 | `textColor` 在 `surfaceColor` 上对比度 >= 4.5:1 |
| 主色可读性 | `primaryColor` 在 `bg-card` 上对比度 >= 3:1 |
| 浅底混合 | 首页卡片背景中分类色视觉占比不超过 10% |
| 边框强度 | `borderColor` 视觉权重低于 `primaryColor`，不可使用纯主色硬边框 |
| 光晕透明度 | `glowColor` alpha 控制在 0.12-0.18 |
| 全局主色距离 | 与全局 `primaryColor` 过近时，只允许用于 icon，不用于边框强调 |
| 涨跌语义 | 涨跌文字永远使用全局 `priceRiseColor/priceFallColor` |

实现建议：

- 新增 `getContrastRatio(foreground, background)`。
- 新增 `getColorDistance(colorA, colorB)`。
- 在开发期用单元函数或构建期脚本检查预设常量。
- 运行时遇到不合格旧配置时降级为安全渲染：弱边框、浅背景、保留 icon 主色。

---

## 图标系统设计

### 当前问题

`CategoryIcons.vue` 只有：

- `gold_ingot`
- `silver_bar`
- `copper_coil`
- `iron_ore`
- `aluminum_block`
- `rare_element`
- `default`

附件中还需要表达：

- 立方矿石
- 电能/闪电
- 购物车/交易
- 指数柱状图
- 试剂瓶
- 循环/废钢

### 扩展建议

在 `CategoryIcons.vue` 增加以下内置 SVG：

| icon | 用途 | 设计描述 |
|------|------|----------|
| `cube_ore` | 普通矿石、铝铜矿 | 立方体/矿块，类似附件蓝色方块 |
| `bolt_metal` | 电铜、能源金属 | 闪电符号，类似附件绿色电铜 |
| `cart_trade` | 精矿/交易品 | 小推车，类似附件紫色图标 |
| `bar_index` | 指数类 | 三根柱状条，类似附件橙色指数 |
| `flask` | 化工/硫酸 | 试剂瓶 |
| `recycle_steel` | 废钢/回收 | 循环箭头 |
| `alloy_grid` | 合金/钢材 | 方格/结构网 |

图标规范：

- SVG 不依赖外部库。
- 接收 `color` 和 `size`。
- 线条粗细统一在 `1.8-2.2`。
- 图标容器由卡片控制，图标本身不带背景。

---

## Style Settings 交互方案

### 信息架构

`CategoryVisualPanel.vue` 建议改为四个区域：

```text
分类视觉

1. 分类列表
   [全部已建立分类，以横向/网格 pill 展示]

2. 推荐方案
   [根据分类名称/编码自动推荐 2-3 套]
   [当前选中方案高亮]

3. 全部方案库
   [8 套方案卡片]
   [每张卡显示 icon、名称、适用说明、迷你卡片效果]

4. 当前分类预览
   [模拟首页价格概览卡]
   [模拟产品列表卡]
```

高级微调：

```text
高级微调（默认折叠）
  主色 / 浅底 / 边框 / 光晕 / 趋势线
  恢复当前方案默认值
```

### 高级微调边界

高级微调不是主流程，必须收口：

| 项 | 规则 |
|----|------|
| 默认状态 | 折叠，不干扰方案选择 |
| 可见权限 | 仅管理员可见；如当前前端无细粒度权限，则至少仅 `ADMIN` 角色展示 |
| 输入方式 | 优先色板/滑杆，文本 hex 作为辅助 |
| 校验 | 保存前执行色彩硬指标校验 |
| 恢复能力 | 必须提供“恢复当前方案默认值” |
| 自定义标记 | 用户修改任一 token 后写入 `customized: true` |
| 版本升级 | `customized: true` 时不提示自动升级预设 |

如果某个颜色输入不满足对比度或与全局色过近：

- 不阻断草稿编辑。
- 在面板内显示明确提示。
- 保存时必须阻断或自动降级为安全值，二选一；推荐第一版选择“阻断保存并提示”。

### 方案卡片形态

每个方案卡片应尽量接近附件里的产品卡：

```text
┌──────────────────────┐
│ [icon] 蓝晶矿脉       │
│ 适合矿石 / 铝 / 铅锌  │
│ ¥5,220      ─╱╲╱╲    │
│ [主色][浅底][边框]    │
└──────────────────────┘
```

选中状态：

- 边框使用方案主色。
- 右上角显示小型选中标识，避免只靠颜色。
- 卡片底部显示“已应用于当前分类”。

### 分类列表体验

分类列表必须来自产品分类主数据，推荐使用分类 API 获取 `ACTIVE` 分类。`category_visual_config` 只用于补充当前分类的视觉 token。

分类 pill 内容：

- 分类名
- 当前方案名称
- 小型 icon

示例：

```text
[铝铜矿  蓝晶矿脉] [电铜  绿能金属] [铁精粉  橙色指数]
```

### 推荐逻辑

前端可先实现轻量名称匹配，不新增后端接口：

| 匹配关键词 | 推荐方案 |
|------------|----------|
| 金、gold、贵金属 | `gold_precious` |
| 银、silver | `silver_neutral` |
| 铜、电铜、copper | `green_energy`、`blue_ore` |
| 铁、铁精粉、指数 | `orange_index`、`graphite_recycle` |
| 钢、钢坯、合金 | `violet_alloy`、`graphite_recycle` |
| 铝、矿、铅、锌 | `blue_ore`、`silver_neutral` |
| 硫酸、化工、酸、试剂 | `cyan_chemical` |
| 废、回收 | `graphite_recycle` |

若没有匹配：

- 推荐 `blue_ore`、`silver_neutral`、`green_energy`。

---

## 与全局色彩的协同规则

Style Settings 的“色彩”模块仍是全局视觉主控。分类视觉必须服从以下规则。

### Token 边界

| 来源 | 控制内容 |
|------|----------|
| 全局色彩 | 页面背景、导航、按钮、表单、卡片背景、涨跌色 |
| 分类视觉 | 分类 icon、分类名强调、产品卡片弱边框、浅色背景、趋势线 |

### 冲突处理

实现时新增一个色彩安全函数，建议放在：

```text
frontend/src/utils/categoryVisualColor.ts
```

职责：

1. 如果分类主色与全局主色过近，自动降低分类色在边框/背景里的权重。
2. 如果分类主色与涨色/跌色过近，不改变涨跌色，仅降低分类色在价格变化区域的使用。
3. 背景混合始终使用 `surfaceColor`，不直接用 `primaryColor` 大面积铺底。
4. 计算文本对比度，保证卡片标题、价格辅助文本可读。

简化规则：

```typescript
const isTooCloseToTheme = colorDistance(categoryPrimary, themePrimary) < threshold

cardBorder = isTooCloseToTheme ? preset.borderColorSoft : preset.borderColor
cardSurface = preset.surfaceColor
priceChangeColor = editingConfig.priceRiseColor / priceFallColor
```

建议阈值：

```typescript
const MIN_TEXT_CONTRAST = 4.5
const MIN_ACCENT_CONTRAST = 3
const THEME_COLOR_DISTANCE_THRESHOLD = 32
const MAX_SURFACE_MIX_PERCENT = 10
```

第一版可使用 RGB 欧氏距离作为 `colorDistance`，后续如需要更准，再升级到 LAB 色彩空间。

### 首页使用建议

首页卡片中：

- 产品名可用 `--category-primary`。
- icon 可用 `--category-primary`，容器背景用 `--category-surface`。
- 卡片边框用 `--category-border`。
- 卡片背景用 `color-mix(in srgb, var(--category-surface) 70%, var(--bg-card))`。
- 趋势图线条用 `--category-chart-line`。
- 涨跌百分比仍使用全局涨跌色，不用分类色。

### 首页图表接入细节

当前首页存在 `chartOptionsMap`，趋势图颜色若不接入会出现“卡片色变了、图表没变”的割裂。实现时需要明确以下更新点：

1. 生成图表 option 时读取产品分类视觉：

```typescript
const visual = getCategoryVisual(getProductCategoryId(product))
```

2. ECharts line 使用：

```typescript
lineStyle: { color: visual.chartLineColor || visual.primaryColor }
itemStyle: { color: visual.chartLineColor || visual.primaryColor }
areaStyle: { color: visual.chartAreaColor || visual.glowColor }
```

3. 当分类视觉保存后：

- 调用 `clearCategoryVisualCache()`。
- 重新计算首页产品卡片 style。
- 重新生成 `chartOptionsMap`。

4. 空数据 fallback：

- 没有趋势数据时仍显示分类 icon 与分类色。
- mini chart 区域显示极浅占位线，不显示错误状态。

5. 性能约束：

- 不在模板中重复 JSON parse。
- 分类视觉通过缓存读取。
- 保存或刷新字典后统一清缓存。

---

## 数据存储方案

继续使用现有 `sys_dict.category = category_visual_config`。

### 数据源职责

| 数据 | 来源 | 职责 |
|------|------|------|
| 产品分类主数据 | `product_category` / 分类 API | 分类名称、编码、状态、排序 |
| 分类视觉配置 | `sys_dict.category = category_visual_config` | 某分类对应的视觉 token |
| 首页匹配 | `product.categoryId` + `category.code` | 找到产品所属分类并应用视觉 |

`CategoryVisualPanel` 加载时建议流程：

1. 调用分类 API 获取 ACTIVE 产品分类。
2. 调用/读取 `category_visual_config` 获取视觉配置。
3. 用 `category.code` 或 `category.id` 将两者合并。
4. 对缺失视觉配置的分类生成推荐草稿。
5. 保存时只写视觉配置，不改分类主数据。

如果当前阶段为了复用只能先从 `category_visual_config` 读取分类，也必须在实现注释和文档中标记为过渡方案，并在下一阶段切到分类 API。

### 保存内容

每个分类字典项的 `extraValue` 保存紧凑 JSON。未微调方案只保存预设引用，运行时由 `categoryVisualPresets.ts` 补齐完整 token：

```json
{
  "categoryCode": "COPPER",
  "presetId": "green_energy",
  "presetVersion": 1,
  "customized": false,
  "icon": "bolt_metal",
  "iconType": "builtin"
}
```

管理员高级微调后才额外保存主色、浅底、边框、趋势图色、光晕等覆盖 token；`darkMode` 不写入字典项，使用预设库暗色 token 回退。

### 兼容旧数据

读取时：

1. 有 `presetId`：优先按已保存 token 渲染，不强制覆盖，避免预设升级导致用户配置突变。
2. 没有 `presetId` 但有旧颜色字段：按旧配置渲染，并在面板提示“旧版自定义配置，可选择方案升级”。
3. 没有配置：按分类名称自动推荐第一套方案作为草稿，不立即保存。
4. 配置对应分类已不存在或已停用：不在面板主列表展示，可在后台清理任务中处理。

保存时：

- 只在用户点击 Style Settings 顶部保存后持久化。
- 切换分类或选择方案只更新草稿状态。
- 保存后调用 `clearCategoryVisualCache()`，确保首页重新解析新配置。

---

## 关键参考文件

| 文件 | 当前职责 | 计划变更 |
|------|----------|----------|
| `frontend/src/components/style-settings/CategoryVisualPanel.vue` | 分类视觉配置面板 | 重构为分类选择 + 方案选择 + 首页式预览 |
| `frontend/src/composables/useCategoryPreviewState.ts` | 分类视觉草稿状态 | 增加 presetId、推荐方案、应用方案、恢复默认方案 |
| `frontend/src/composables/useCategoryVisual.ts` | 首页分类视觉解析 | 增加 surface/chart token 回退与缓存清理 |
| `frontend/src/components/icons/CategoryIcons.vue` | 分类 SVG icon | 增加附件风格需要的图标 |
| `frontend/src/components/style-settings/preview/CategoryPreview.vue` | 右侧分类预览 | 改为展示方案卡片与首页真实卡片效果 |
| `frontend/src/views/Home.vue` | 首页产品卡片消费分类视觉 | 消费新增 `surfaceColor/chartLineColor/chartAreaColor` |
| `frontend/src/types/index.ts` | 类型定义 | 扩展 `CategoryVisualConfig` |
| `docs/dev/UI设计说明.md` | UI 规范文档 | 完成后补充分类视觉预设说明 |

---

## 实现步骤

### Phase 1：方案常量与类型设计

1. 新增 `categoryVisualPresets.ts`。
2. 定义 `CategoryVisualPreset`。
3. 为预设增加 `group` 与 `version`。
4. 扩展 `CategoryVisualConfig`：`presetId`、`presetVersion`、`surfaceColor`、`chartLineColor`、`chartAreaColor`、`customized`。
5. 编写 `buildCategoryVisualConfigFromPreset(category, preset)`。
6. 编写 `getRecommendedPresets(categoryName, categoryCode)`。
7. 编写色彩校验工具：对比度、颜色距离、安全降级。
8. 第一阶段保留 8 套精选基础方案，第二阶段扩展为 5 个 `group`、每组 10 套的完整方案库。
9. 定义 `CategoryVisualCombo` 与 `CategoryVisualComboRule`。
10. 设计第一批 5 套组合方案：稳健矿业、活跃交易、贵金聚焦、工业合金、化工循环。

验收：

- TypeScript 类型通过。
- 旧配置不报错。
- 每套预设可独立输出完整 token。
- 预设 token 通过色彩硬指标校验。
- 方案库扩展到 50 套后，所有方案 `id/name` 唯一，且可按 `group` 稳定分组。
- 组合方案可以根据分类名称/编码批量生成分类视觉草稿。

### Phase 2：图标系统扩展

1. 扩展 `CategoryIcons.vue` 内置 SVG。
2. 保持 `icon/color/size` API 不变。
3. 移除分类预览里 emoji 字符展示，统一使用 `CategoryIcons`。

验收：

- 首页和 Style Settings 图标一致。
- 所有 icon 在 16/20/24/32 尺寸下清晰。

### Phase 3：分类视觉面板重构

1. 分类列表以产品分类 API 为源。
2. 合并已有 `category_visual_config`。
3. 分类列表显示“分类名 + 当前方案名 + icon”。
4. 新增“整体组合方案”区域，展示 5 套内置组合与 1 套“我的组合”。
5. 支持“应用于全部分类”和“仅应用于未配置分类”。
6. 删除独立“推荐方案”区块，推荐逻辑仅用于默认值和默认分组定位。
7. 全部方案区使用 5 个选项卡，每个选项卡展示 10 张方案卡片。
8. 点击单套方案后更新当前分类草稿配置。
9. 点击组合方案后直接批量生成多个分类草稿，不立即保存。
10. 右侧实时预览展示组合方案切换后的多分类整体效果。
11. 高级微调改为“微调助手”，默认折叠，仅 ADMIN 可见。
12. 保留“恢复方案默认值”。
13. 对不合格颜色阻断保存并提示原因。

验收：

- 用户不需要打开颜色选择器即可完成分类视觉设置。
- 用户可以通过组合方案一键为多个分类生成整体协调的视觉草稿。
- 切换分类后能看到该分类当前方案。
- 未保存状态能被 Style Settings 顶部状态栏识别。
- 完整方案库启用后，用户可以通过 5 个选项卡快速定位方案，移动端 tab 与方案卡不挤压。

### Phase 4：预览与首页消费升级

1. `CategoryPreview.vue` 改为更贴近附件的价格概览卡 + 产品列表卡。
2. `Home.vue` 新增 CSS 变量消费：
   - `--category-surface`
   - `--category-chart-line`
   - `--category-chart-area`
3. `chartOptionsMap` 生成时接入分类视觉 token。
4. 分类视觉保存后清缓存并重新生成图表 option。
5. 迷你趋势图颜色与分类方案一致。
6. 涨跌色仍使用全局 Style Settings 色彩配置。

验收：

- Style Settings 预览与首页实际卡片视觉一致。
- 分类色不会覆盖涨跌色。
- 页面整体仍保持浅色、专业、轻盈。

### Phase 5：保存、缓存与文档

1. 保存后清理分类视觉缓存。
2. 更新 `docs/dev/UI设计说明.md`。
3. 更新 `docs/dev/项目设计文档.md` 中 Style Settings 分类视觉配置说明。
4. 如 API 未变化，不更新后端接口文档；如字典结构说明变化，补充数据字典说明。

验收：

- 刷新后配置仍生效。
- 保存前首页不被污染，保存后全局生效。
- 文档与实际配置结构一致。

---

## Verification

### 功能验证

- 新建或已有多个分类时，分类视觉面板能列出所有分类。
- 新建分类但无视觉配置时，面板给出推荐草稿。
- 停用分类不会继续作为有效配置项展示。
- 每个分类可选择不同方案。
- 选择组合方案后，多个分类能按关键词自动套用不同预设，并生成未保存草稿。
- “仅应用于未配置分类”不会覆盖已有视觉配置。
- “应用于全部分类”会在覆盖前给出确认和变更摘要。
- 完整方案库启用后，5 个选项卡均可正常切换，每个选项卡展示 10 套方案。
- 当前分类能默认定位到推荐 tab，并保留 2-3 套推荐方案入口。
- 切换分类不丢失草稿。
- 保存后刷新页面，分类方案仍保留。
- 首页产品卡片能按分类显示不同 icon 和颜色。
- 没有分类的产品使用默认视觉，不报错。
- 保存后首页 mini chart 线条颜色随分类视觉变化。
- 高级微调不满足对比度时不能保存。

### 视觉验证

至少检查以下场景：

| 场景 | 验收点 |
------|--------|
| PC 首页 1440px | 卡片接近附件：白底、浅边框、小 icon、弱背景色 |
| PC Style Settings | 方案卡片可比较，当前选择清晰 |
| 组合方案预览 | 批量套用后，多分类并排展示仍保持统一气质 |
| 移动端 390px | 方案卡片不挤压，分类列表可横向/换行浏览 |
| 50 套方案库 | 5 个 tab 信息架构清楚，用户不会被平铺方案淹没 |
| 多分类并存 | 蓝、绿、紫、橙等色彩互不抢戏 |
| 全局主题色变化 | 分类视觉仍可读，不与按钮/导航冲突 |

### 构建验证

```bash
cd frontend
npm run build
```

### 回归验证

```bash
cd backend
mvn test
```

后端本阶段理论上不应改动；运行后端测试用于确认未误伤整体工程。

---

## 风险与决策点

### 决策点 1：是否保留高级微调

推荐：保留，但默认折叠。

理由：

- 满足少量高级用户微调诉求。
- 主流程仍是方案选择，避免视觉失控。

补充约束：

- 仅 ADMIN 可见。
- 保存前强制校验。
- 修改后标记为 `customized: true`。

### 决策点 2：预设是否允许后端配置

本阶段推荐：预设放前端常量。

理由：

- 这是设计系统级能力，稳定性高。
- 不需要新增后端表和管理页面。
- 后续如需要运营配置，可再迁移到字典或专表。

补充约束：

- 预设必须有 `version`。
- 保存的是 token 快照，不是只保存 `presetId`。
- 预设升级不自动改动已保存分类效果。

### 决策点 3：旧配置如何处理

推荐：兼容旧配置，不自动覆盖。

理由：

- 避免已有用户配置突然变化。
- 面板中提供“升级为推荐方案”即可。

补充约束：

- 旧配置进入面板时显示“旧版配置”标识。
- 用户选择新方案后才写入 `presetId/presetVersion`。
- 不存在或停用分类的旧配置不在主 UI 展示。

### 决策点 4：首页趋势图是否按分类色

推荐：趋势线按分类色，涨跌文字按全局涨跌色。

理由：

- 趋势线属于产品身份识别。
- 涨跌文字属于价格语义，必须保持全局一致。

补充约束：

- `lineStyle/itemStyle/areaStyle` 全部接入分类视觉 token。
- 保存分类视觉后必须重建 `chartOptionsMap`。
- 无趋势数据时使用安全占位，不显示空白破图。

### 决策点 5：是否扩展为 50 套完整方案库

推荐：采纳，但分阶段实现。

理由：

- 50 套方案可以覆盖更多矿产品、化工品、贵金属和指数场景，能明显提升系统的专业感。
- 直接平铺 50 套会增加决策成本，必须通过 5 个业务语义选项卡组织。
- 第一阶段的 8 套精选方案仍有价值，可作为默认推荐和低认知成本入口。

补充约束：

- 50 套方案必须按 `group` 分为 5 个 tab，每组 10 套。
- 推荐方案区独立存在，不被完整方案库替代。
- 所有方案必须经过色彩硬指标校验，不允许临时加色。
- 方案命名必须业务化，禁止 `blue_01`、`green_02` 这类工程命名直接展示给用户。
- 方案库扩展前应先完成设计审查表，确认每套方案的主色、浅底、边框、趋势图色和暗色模式 token。

### 决策点 6：是否增加“组合方案”批量套用能力

推荐：强烈建议增加，优先级高于单纯扩充更多颜色。

理由：

- 用户真正需要的是“这个系统看起来整体协调”，不是单独挑 50 次颜色。
- 组合方案可以把视觉总监的搭配判断预制进去，降低普通用户的设计负担。
- 对已有多分类系统，一键套用组合方案比逐类选择更高效。
- 组合方案不会削弱高级能力，因为用户仍可继续对单个分类改方案。

补充约束：

- 组合方案只批量生成草稿，不自动保存。
- 默认只应用于未配置分类，避免覆盖已有配置。
- 覆盖全部分类必须二次确认。
- 已 `customized: true` 的分类默认跳过。
- 套用后必须显示变更摘要。
- 组合方案本身也需要 `version`，便于后续升级。

---

## 建议验收标准

本功能完成后，应满足：

1. 用户可以在 30 秒内为一个分类选好专业视觉方案。
2. 不懂配色的用户也不会配出明显不协调的分类卡片。
3. 首页产品卡片能明显区分不同分类，但页面整体仍像一个系统，而不是彩色拼贴。
4. Style Settings 的“色彩”模块和“分类视觉”模块职责清楚，不互相覆盖。
5. 附件中的蓝、绿、紫、橙分类卡片气质能被复现到现有首页卡片体系中。
