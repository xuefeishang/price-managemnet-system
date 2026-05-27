# Home页面响应式构图重构方案

## Context

当前 Home 页面已经具备首页组件显隐、排序、重点产品数量、PC/移动列数等基础配置能力，但整体仍偏“单一桌面尺寸下的卡片堆叠”。用户提供的参考图更接近成熟价格管理后台首页：左侧固定导航，右侧为清晰的数据工作台，页面由“欢迎与操作区、经营摘要区、核心价格与曲线区、产品数据表区”组成，信息层级稳定，组件在不同浏览器宽度下可以重新排布，而不是被强行压缩。

本方案目标不是照抄截图，而是吸收其构图原则，并结合现有 `style-settings` 的管理能力，将 Home 页面重构为可配置、可响应、可验证的工业级后台首页。

> 2026-05-26 补充：首行摘要、价格曲线主面板、页面级横向滚动三项偏差的专项整改，详见 [Home页面参考图偏差整改方案](Home页面参考图偏差整改方案.md)。

## 参考图视觉分析

参考图的布局特点如下：

1. **左侧导航稳定，右侧工作台流式伸缩**
   - 左侧导航约 240px，品牌、主菜单、用户信息垂直分区。
   - 右侧主体占满剩余宽度，卡片区域按容器宽度重新排布。
   - 视觉中心始终在价格数据，不让导航挤压核心内容。

2. **顶部不是传统大标题，而是操作工作区**
   - 左侧为“欢迎回来 + 副标题”，右侧是日期、刷新、价格维护。
   - 操作按钮与数据上下文同屏，减少业务跳转成本。
   - 顶部高度克制，没有营销式大留白。

3. **摘要卡片横向铺开，但内容结构固定**
   - 每张摘要卡由图标、标题、主数字、补充说明组成。
   - 主数字拥有独立视觉位置，不被说明文字挤压。
   - 卡片尺寸接近统一，适合在 3/2/1 列之间切换。

4. **核心价格区是左右主从结构**
   - 左侧“重点产品价格”用小卡片快速扫描多个产品。
   - 右侧“价格曲线”占更大面积，作为主分析区域。
   - 这是典型的“列表选择 + 详情图表”结构，适合价格系统。

5. **产品列表回归表格，而不是全部卡片化**
   - 下方产品列表以表格呈现，列包含产品、类别、品级、单位、币种、最新价格、较昨日、走势图、更新时间。
   - 表格比卡片更适合高密度业务扫描。
   - 小走势图作为稳定列，不参与文本混排。

6. **图表都有稳定容器**
   - 大图表占用固定高度区。
   - 小走势图在产品卡或表格列中有固定宽高。
   - 图表不会随着价格文字换行而被挤压。

7. **颜色用于识别，不主导页面**
   - 青绿作为主色，蓝、橙、紫用于产品或指标区分。
   - 页面主体是浅灰背景 + 白色卡片 + 细边框/阴影。
   - 色彩服务数据，不制造装饰噪音。

## 当前 Home 页面问题核查

基于 `frontend/src/views/Home.vue`、`frontend/src/components/Layout.vue`、`frontend/src/components/home/SummarySection.vue`、`frontend/src/components/home/TrendAnalysisChart.vue`、`frontend/src/components/style-settings/*` 的现状，用户提出的问题大部分成立，但需要分层判断。

| 问题 | 当前情况 | 结论 |
|------|----------|------|
| 刚性宽度 | `.pc-home` 使用 `max-width: 1400px`，左侧 `.pc-sidebar` 固定 240px，内容区依赖剩余宽度；产品网格列数由 JS 写入 `repeat(n, 1fr)` | 存在中屏拥挤风险 |
| 产品卡片结构不稳定 | 卡片底部价格与小图横向混排，价格为 `2.25rem`，单位紧贴价格；图表容器仅 80x40 | 存在挤压和换行风险 |
| 卡片高度统一不足 | 卡片 `min-height: 176px`，但标题、产地、规格、趋势徽标均按内容撑开 | 存在同排底部不齐风险 |
| 断点布局不足 | 现有逻辑主要是 `>=1400`、`>=1024`、移动端三档，且 PC 列数来自配置而非容器实际宽度 | 中间断点不够 |
| 左侧导航挤压内容 | 侧边栏固定 240px，仅在约 1030px 以下直接切换移动布局，没有 1280/1024 中间压缩形态 | 成立 |
| 信息密度失衡 | 卡片区留白与图表高度不成比例，产品列表仍偏卡片化 | 成立 |
| absolute 过多 | Home 内主要 absolute 用于分类图标，尚未大量用于主布局；但图标占位未参与布局，会与标题/徽标形成潜在冲突 | 局部成立 |
| 更像展示页 | 首页以卡片展示为主，表格扫描能力弱，价格曲线不是页面主分析中心 | 成立 |

## 重构目标

1. Home 页面从“卡片展示页”升级为“价格运营驾驶舱”。
2. 布局响应不依赖单一固定列数，而以容器宽度、最小卡宽和断点策略共同决定。
3. 产品价格卡内部建立稳定槽位：标题区、元信息区、价格区、变化区、图表区。
4. 图表全部进入独立容器，不与价格文字混排。
5. 产品列表在 PC 端使用数据表格，在窄屏降级为紧凑列表。
6. style-settings 不只管理“列数”，还管理“布局密度、首页模式、断点策略、组件显隐顺序、图表展示策略”。
7. 不引入新的硬编码字典标签，产地、币种、状态等显示仍走字典服务。
8. 首页视觉必须继承本系统的主题、字体、布局风格、分类视觉和字典治理能力，不能另起一套静态样式系统。

## 本系统设计治理原则

本系统的样式设计具有独特性：它不是单纯写死在页面 CSS 里，而是由 `style-settings`、主题配置、布局预设、字号预设、分类视觉、字典服务共同控制。Home 重构必须尊重这个架构。

### 1. 配置来源分层

Home 页面所有视觉和业务展示配置按以下优先级生效：

| 优先级 | 来源 | 说明 |
|--------|------|------|
| 1 | 运行时用户交互状态 | 当前选中的日期、曲线产品、筛选条件、分页、排序，只影响当前会话 |
| 2 | style-settings 草稿预览 | 管理员在样式设置页尚未保存的草稿，用于 HomePreview |
| 3 | 已保存 style-settings 配置 | `sys_style_config`、`home_layout`、`home_widget`、`category_visual_config` 等持久化配置 |
| 4 | 字典服务 | 产地、币种、状态、角色、单位等显示名称 |
| 5 | 前端默认值 | 只作为兜底，禁止变成主要配置来源 |

关键要求：
- Home 页面不能硬编码“人民币、启用、上涨、产地名称”等业务显示文案。
- Home 页面不能直接写死分类颜色，应通过 `useCategoryVisual()` 获取分类视觉变量。
- Home 页面不能绕过 `useTheme()` 写死涨跌色、图表主色、字体族。
- style-settings 的预览态必须和真实 Home 使用同一套 layout resolver，避免“预览好看，实际页面崩”。

### 2. 主题 token 接入

Home 重构后应继续使用现有 CSS 变量：

| 类型 | Token / 来源 | 用途 |
|------|--------------|------|
| 主色 | `--primary-color`、`--gradient-primary` | 主按钮、当前选中态、重点强调 |
| 背景 | `--bg-page`、`--bg-card`、`--app-page-bg` | 页面和卡片背景 |
| 字体 | `--font-heading`、`--font-body`、`--font-mono` | 标题、正文、价格数字 |
| 字号 | `--font-size-*` | 所有文字层级 |
| 涨跌 | `--price-rise-color`、`--price-fall-color`、`--price-flat-color` | 涨跌数字、趋势徽标 |
| 图表 | `--chart-primary-color`、`--chart-budget-color` | 主图表默认线色 |
| 分类视觉 | `--category-primary`、`--category-surface`、`--category-border`、`--category-glow` | 产品卡、表格标识、小走势图 |

参考图中的青绿、蓝、橙、紫不应作为页面硬编码色值迁入，而应映射到：
- 系统主色：青绿主题；
- 分类视觉：不同产品分类的 icon、边框、浅底、图表线；
- 图表色板：多产品对比时按 `themeConfig.chartColors` 或分类视觉分配。

### 3. 字典与业务显示治理

Home 页面涉及的所有编码显示都必须来自字典或主数据：

| 显示内容 | 数据来源 | 禁止做法 |
|----------|----------|----------|
| 产地 | `product.originIds` + `getOriginName()` | 写死“澳洲/巴西/中国”等中文 |
| 币种符号 | `getCurrencySymbol(currency)` | 前端手写 `CNY => ¥` 映射 |
| 状态 | `getStatusLabel(status)` | `ACTIVE ? 启用 : 停用` |
| 分类名 | `product_category.name` | 用字典替代分类主数据 |
| 单位 | 产品字段或后续单位字典 | 页面内写死单位清单 |
| 涨跌方向 | 价格计算结果 + 主题涨跌色 | 用固定红绿样式覆盖主题 |

### 4. 分类视觉参与规则

分类视觉是本系统区别于普通后台模板的重要能力，Home 重构必须让它成为价格卡、表格和图表的统一识别系统。

| 场景 | 分类视觉使用方式 |
|------|------------------|
| 重点产品价格卡 | icon、浅底、边框、迷你走势图颜色读取分类视觉 |
| 主价格曲线 | 当前选中产品使用其分类线色；多产品对比时按分类视觉优先、图表色板兜底 |
| 产品表格 | 产品名前的小色标或 icon 使用分类视觉；行背景保持克制，不大面积染色 |
| 产地胶囊 | 使用分类主色的低透明度版本，不抢占价格视觉 |
| 空状态/骨架屏 | 不使用分类色，保持中性 |

约束：
- 分类色只用于识别，不用于所有文字。
- 分类视觉配置缺失时回退到主题主色。
- 分类视觉不得降低文字对比度，继续遵守现有对比度校验规则。

### 5. 设计语言定义

本次 Home 的视觉目标可以定义为：

> 克制、清晰、数据优先的矿产品价格工作台。

具体风格：
- 卡片圆角保持 8-12px，不做夸张圆角。
- 阴影轻，主要依靠浅边框和层级间距。
- 价格数字最大，但不得牺牲单位、涨跌和图表稳定性。
- 页面密度由 style-settings 控制，默认 `balanced`，不做官网式大留白。
- 动效只用于 hover、选中、加载，不做大幅入场动画。

## 目标构图

### 桌面宽屏布局（≥1440）

```
┌─────────────────────────────────────────────────────────────┐
│ 欢迎回来 / 副标题                         日期 刷新 价格维护 │
├─────────────────────────────────────────────────────────────┤
│ 摘要卡片 1        摘要卡片 2        摘要卡片 3              │
├───────────────────────────────┬─────────────────────────────┤
│ 重点产品价格                  │ 价格曲线（单产品/多产品）   │
│ 4列小卡片                     │ 大图表 + 时间范围 + 货币    │
├───────────────────────────────┴─────────────────────────────┤
│ 产品列表：筛选工具条 + 数据表格 + 小走势图列                │
└─────────────────────────────────────────────────────────────┘
```

建议比例：
- 摘要区：3列或4列，最小卡宽 260px。
- 核心区：左 44%，右 56%；低于 1280 后上下堆叠。
- 产品卡：最小宽度 150px，最大 220px。
- 大图表：高度 300-360px。
- 表格行：48-56px，迷你走势图 96x28。

### 中屏布局（1200-1439）

```
摘要卡片：3列
核心区：左 40% / 右 60%，或根据容器宽度自动切为上下
重点产品小卡：3列
产品列表：表格保留，隐藏低优先级列
```

隐藏优先级建议：
1. 更新时间可缩短为日期时间紧凑格式。
2. 品级/规格与产品名合并为副信息。
3. 币种在价格前缀或列头体现。

### 平板/窄桌面布局（1024-1199）

```
左侧导航进入窄栏模式
摘要卡片：2列
核心区：上下结构
重点产品小卡：2或3列
产品列表：紧凑表格或横向滚动表格
```

关键要求：
- 不再强行保持四列。
- 左侧菜单从 240px 缩到 72px 图标栏，释放主内容宽度。
- 表格允许内部横向滚动，但页面主体不能整体横向滚动。

### 移动端布局（<768）

```
顶部移动导航
日期/刷新/维护压缩成工具行
摘要卡片：2列或横向滑动
重点产品：横向卡片轮播
价格曲线：单列全宽
产品列表：卡片列表，不显示完整表格
```

移动端不追求完整表格，只保留：
- 产品名 + 产地
- 最新价 + 单位
- 较昨日
- 更新时间
- 小走势图或趋势徽标

## 断点策略

建议引入统一的首页响应式策略，而不是在多个组件里分散判断。

| 容器宽度 | 布局名称 | 摘要列数 | 重点产品列数 | 趋势区 | 产品列表 |
|----------|----------|----------|--------------|--------|----------|
| ≥1440 | wide | 3-4 | 4 | 左右双栏 | 完整表格 |
| 1280-1439 | desktop | 3 | 3 | 左右双栏 | 隐藏低优先级列 |
| 1024-1279 | compact | 2 | 2-3 | 上下堆叠 | 紧凑表格 |
| 768-1023 | tablet | 2 | 2 | 上下堆叠 | 卡片/紧凑表格 |
| <768 | mobile | 1-2 | 横向滑动/1列 | 单列 | 卡片列表 |

实现建议：
- 使用 `ResizeObserver` 监听 Home 内容容器宽度，而不是只读 `window.innerWidth`。
- 新增 `useResponsiveHomeLayout.ts`，统一输出：
  - `layoutTier`
  - `summaryColumns`
  - `metricColumns`
  - `coreLayout`
  - `tableDensity`
  - `sidebarMode`
- CSS 使用 `grid-template-columns: repeat(auto-fit, minmax(var(--home-card-min-width), 1fr))` 作为底层兜底。
- JS 只决定“模式”，不直接硬写所有网格列数。

## 组件重构方案

### 1. HomeShell：首页壳层

职责：
- 顶部欢迎区与工具区。
- 控制页面最大宽度、内边距、内容栅格。
- 承接 style-settings 的密度、布局模式、组件顺序。

建议结构：

```
Home.vue
└─ HomeShell
   ├─ HomeHeroToolbar
   ├─ HomeSummaryGrid
   ├─ HomeCoreWorkspace
   │  ├─ FeaturedPriceCards
   │  └─ PriceCurvePanel
   ├─ HomeProductTable
   └─ RiskAlertsPanel
```

### 2. HomeHeroToolbar：欢迎与操作区

参考图做法：
- 左侧：欢迎回来，当前用户名。
- 副标题：实时掌握产品价格动态，科学分析市场趋势。
- 右侧：日期选择、刷新、价格维护。

响应规则：
- ≥1024：左右排列。
- <1024：标题在上，工具行在下。
- <640：日期与刷新保留，价格维护变为图标按钮或底部浮动按钮。

### 3. HomeSummaryGrid：经营摘要区

现有 `SummarySection.vue` 可保留业务数据，但布局需要改造：
- 从 `repeat(4, 1fr)` 改为 `auto-fit/minmax`。
- 摘要卡固定结构：icon slot、label、value、delta。
- 卡片最小高度固定，内部不靠内容撑高。

建议卡片信息：
- 产品总数
- 今日更新
- 覆盖品类
- 涨跌状态，或平均变动

### 4. HomeCoreWorkspace：核心工作区

这是本次重构的视觉核心。

#### 左侧：重点产品价格

从现有大卡片改成参考图里的紧凑价格卡：
- 产品名 + 产地/规格副信息。
- 最新价格大数字。
- 单位单独一行或使用弱化小字，不与价格强绑定。
- 较昨日独立区域。
- 迷你图表固定高度 56-72px。

卡片内部建议网格：

```
┌──────────────────────┐
│ icon 产品名           │
│ 产地/规格/单位        │
│ ¥970                 │
│ 较昨日 -10 (-1.02%)  │
│ [固定高度迷你走势图]  │
└──────────────────────┘
```

稳定规则：
- `.price-main` 使用 `font-size: clamp(24px, 2vw, 34px)`，但不要纯 viewport 缩放；可按容器模式给不同 token。
- `.price-unit` 不与价格同一 baseline 强绑定；窄卡时下移到副信息。
- `.mini-chart-frame` 固定 `height: 64px`。
- 卡片高度用 `grid-template-rows` 固定，不让图表被文字挤压。
- 产地超过宽度时 ellipsis，不换行撑高。

#### 右侧：价格曲线

新增或改造为主图表面板：
- 默认显示当前重点产品第一项或用户选择的产品。
- 支持单产品曲线，后续可扩展多产品对比。
- 顶部包含产品选择、7/30/90/180日范围、导出。
- 图表容器高度按断点变化：
  - wide: 340px
  - desktop: 300px
  - compact/tablet: 260px
  - mobile: 220px

#### 核心区交互闭环

核心区必须形成“左侧产品卡选择 → 右侧曲线更新 → 下方表格同步高亮”的闭环，而不是互不相关的展示块。

状态设计：

| 状态 | 说明 | 默认值 |
|------|------|--------|
| `selectedProductId` | 当前主曲线展示产品 | 第一个重点关注产品；无重点产品时取当前分页第一条 |
| `selectedRangeDays` | 主曲线时间范围 | `chart_range` 字典中排序最靠前且推荐的项，默认 30 |
| `selectedCurrency` | 曲线货币过滤 | 当前产品币种 |
| `highlightedRowId` | 表格高亮行 | 与 `selectedProductId` 同步 |
| `compareProductIds` | 后续多产品对比预留 | 空数组 |

交互规则：
- 点击重点产品卡：更新 `selectedProductId`，刷新右侧主曲线，表格对应行高亮并滚动到可见区域。
- 点击表格产品行：更新 `selectedProductId`，主曲线切换到该产品；点击行内“查看”才进入详情页。
- 点击主曲线产品下拉：更新选中产品，不改变表格筛选条件。
- 切换日期：摘要、重点卡、表格价格全部按新日期刷新；曲线区仍使用 `selectedRangeDays`。
- 切换时间范围：只影响主曲线和重点走势，不重置表格分页。
- 产品被筛选条件排除时：主曲线继续保留选中产品，但表格不强行高亮；显示“当前曲线产品不在筛选结果中”的弱提示。
- 当前选中产品无价格：主曲线显示空状态，重点卡/表格价格显示 `--`。

可访问性与键盘：
- 重点产品卡应使用 `button` 或可聚焦元素，支持 Enter/Space 选中。
- 当前选中卡使用 `aria-pressed="true"` 或明确的选中态。
- 表格行点击与行内操作按钮需要阻止事件冲突。

### 5. HomeProductTable：产品列表区

PC 端建议从卡片网格改为表格，贴近参考图和业务扫描需求。

#### 数据加载与分页

首页产品列表不能继续一次性拉取 100 条后在前端过滤。重构后应使用分页模型，优先复用现有 `/api/products` 的分页能力，并明确首页表格自己的查询状态。

建议状态：

| 状态 | 默认值 | 说明 |
|------|--------|------|
| `page` | `0` | 后端分页页码，保持现有接口从 0 开始 |
| `size` | `10` | 首页默认每页 10 条；style-settings 可配置 10/20/50 |
| `keyword` | 空 | 搜索产品名称、编码，输入防抖 300ms |
| `categoryId` | 空 | 类别筛选 |
| `originKey` | 空 | 产地筛选，值来自 `origin` 字典 |
| `currency` | 空 | 币种筛选，值来自字典服务 |
| `status` | `ACTIVE` | 首页默认只看启用产品，可在筛选中切换 |
| `priceChange` | 空 | 全部 / 上涨 / 下跌 / 持平 / 未维护 |
| `showOnHome` | 空 | 全部 / 重点关注 / 非重点 |
| `sort` | `categorySort,asc;sortOrder,asc` | 默认与首页排序一致 |

分页 UI：
- 表格底部显示“共 N 条 / 第 X 页 / 每页 N 条”。
- PC 端使用页码分页 + 每页数量选择。
- 1024 以下保留上一页/下一页和当前页信息，避免分页控件挤压。
- 移动端卡片列表支持“加载更多”，但内部仍映射到 `page + size`，避免一次性加载全部数据。
- 切换筛选条件时重置到 `page=0`。
- 切换日期时保留筛选条件，但刷新价格数据与涨跌数据。

接口策略：
- 短期：复用 `getProducts(params)` 获取分页产品，再用当前日期价格接口补齐价格、涨跌、走势图。
- 中期：新增首页专用接口 `GET /api/home/products`，一次返回分页产品行所需字段，减少前端拼装和多接口竞态。

建议首页专用接口参数：

```text
GET /api/home/products
page=0
size=10
keyword=
categoryId=
originKey=
currency=
status=ACTIVE
priceChange=
showOnHome=
date=2026-05-24
sort=categorySort,asc&sort=sortOrder,asc
```

建议响应结构：

```ts
interface HomeProductRow {
  productId: number
  code?: string
  name: string
  categoryId?: number
  categoryName?: string
  specs?: string
  originLabel?: string
  unit?: string
  currency: string
  currencySymbol: string
  latestPrice: number | null
  yesterdayPrice: number | null
  diff: number | null
  diffPercent: number | null
  trendDirection: 'up' | 'down' | 'flat' | 'none'
  trendPoints: Array<{ date: string; price: number | null }>
  showOnHome: boolean
  status: string
  updatedTime?: string
}
```

该接口返回 `PageResponse<HomeProductRow>`，与现有分页响应保持一致。

#### 筛选工具条

参考图中的产品列表顶部筛选区应成为 Home 表格的标准工具条：

- 搜索框：搜索产品名称/编码。
- 类别下拉：来自产品分类接口，支持“全部类别”。
- 产地下拉：来自 `origin` 字典，不硬编码中文。
- 单位下拉：来自产品已有单位聚合或字典配置。
- 币种下拉：来自字典服务。
- 涨跌筛选：全部 / 上涨 / 下跌 / 持平 / 未维护。
- 状态筛选：启用 / 停用 / 全部，默认启用。
- 高级筛选抽屉：移动端或筛选项过多时使用。
- 清除筛选：有筛选条件时显示，点击恢复默认查询。

筛选交互原则：
- 搜索输入防抖。
- 下拉筛选立即触发查询。
- 多筛选条件组合时，顶部显示已选条件 chip。
- 筛选无结果时展示“未找到匹配产品”，并提供“清除筛选”动作。

#### 排序

表格需要明确可排序列，而不是只按首页排序固定展示。

默认排序：
- `category.sortOrder ASC`
- `product.sortOrder ASC`
- `product.name ASC`

可排序列：
- 产品名称
- 类别
- 最新价格
- 较昨日
- 更新时间
- 首页排序

排序 UI：
- 表头点击切换升序/降序/取消。
- 当前排序列显示方向图标。
- 多字段默认排序只在未选择表头排序时生效。
- 用户选择排序后，当前页重置为第一页。

后端要求：
- 若沿用 `/api/products`，需要检查当前接口是否支持 `sort`、`keyword`、`categoryId` 等参数。
- 若不支持，应在 `ProductController` / `ProductRepository` 补齐筛选与排序能力，避免前端取全量后过滤。

#### 列显隐与列优先级

表格列：

| 列 | PC 完整 | 中屏 | 移动卡片 | 说明 |
|----|---------|------|----------|------|
| 选择框 | 显示 | 可隐藏 | 隐藏 | 预留批量操作 |
| 产品名称 | 显示 | 显示 | 显示 | 含重点星标、编码、产地 |
| 类别 | 显示 | 可合并到副信息 | 合并到副信息 | 来自分类主数据 |
| 品级/规格 | 显示 | 合并到名称副信息 | 合并到副信息 | 长文本省略 |
| 单位 | 显示 | 可隐藏 | 合并到价格后 | 不挤压价格 |
| 币种 | 显示 | 可合并到价格 | 合并到价格 | 通过字典符号展示 |
| 最新价格 | 显示 | 显示 | 显示 | 等宽字体 |
| 较昨日 | 显示 | 显示 | 显示 | 红涨绿跌 |
| 走势图 | 显示 | 显示 | 可选显示 | 固定宽高 |
| 更新时间 | 显示 | 可隐藏 | 可折叠 | 使用紧凑格式 |
| 操作 | 显示 | 图标化 | 更多菜单 | 查看/编辑 |

style-settings 可新增：
- 默认可见列配置。
- 是否显示表格选择框。
- 每页数量默认值。
- 移动端是否显示迷你走势图。

#### 加载、空状态与错误状态

- 首次加载：表格骨架屏，保持列宽和行高。
- 翻页加载：保留当前表格高度，行内显示 loading，不让页面跳动。
- 无数据：展示“暂无产品数据”。
- 筛选无结果：展示“未找到匹配产品”，显示已选筛选 chip 和清除按钮。
- 价格未维护：价格显示 `--`，趋势方向为 `none`，不可用涨跌颜色。
- 接口错误：表格区域显示错误提示和重试按钮，不影响上方摘要和核心图表。

#### 批量操作与权限

首页产品表格以查看和快速筛选为主，不替代完整产品管理页，但可以提供轻量操作。

权限规则：
- VIEWER：只能查看、筛选、分页、切换曲线产品。
- EDITOR：可进入价格维护，可编辑产品价格。
- ADMIN：可使用列设置、样式设置入口、批量操作预留能力。

操作项：
- 行点击：选中产品并更新主曲线。
- 查看详情：进入产品详情。
- 编辑产品：有 `PRODUCT_EDIT` 权限时显示。
- 价格维护：有价格维护权限时显示，带当前产品和日期上下文。
- 批量选择：默认可通过 style-settings 关闭；首期可只预留 UI，不做批量业务。

#### URL 与状态保持

Home 的业务状态应支持刷新后尽量恢复，避免用户调好筛选后丢失上下文。

建议进入 URL query：
- `date`
- `productId`
- `range`
- `keyword`
- `categoryId`
- `originKey`
- `currency`
- `priceChange`
- `page`
- `size`
- `sort`

不进入 URL：
- 表格列宽拖拽临时状态。
- 当前 hover 行。
- 抽屉展开状态。

规则：
- URL query 由用户交互防抖更新。
- 非法 query 值回退默认值，不报错。
- 权限不足导致不可见的筛选/操作不写入 URL。

响应规则：
- wide/desktop：完整表格。
- compact：隐藏类别或将类别合并到名称副信息，保留价格、涨跌、走势图。
- tablet/mobile：切换为产品列表卡片。

样式原则：
- 行高稳定。
- 迷你走势图固定宽高。
- 最新价格使用等宽字体。
- 涨跌颜色继续使用全局涨跌色变量。

## style-settings 配置扩展

现有 `HomeExperiencePanel.vue` 管理：
- PC 卡片列数
- 移动端卡片列数
- 重点产品数量
- 是否显示重点走势
- 是否显示预警区
- 首页组件显隐与排序

这些配置保留，但建议升级为“布局策略 + 视觉密度 + 组件排布”。

### 新增 home_layout 配置项

| dictKey | 中文名 | 默认值 | 说明 |
|--------|--------|--------|------|
| `responsive_strategy` | 响应式策略 | `auto` | `auto` 自动断点 / `manual` 使用管理员指定列数 |
| `home_density` | 首页密度 | `balanced` | `compact` 紧凑 / `balanced` 标准 / `comfortable` 舒展 |
| `core_workspace_layout` | 核心区布局 | `split` | `split` 左右双栏 / `stacked` 上下 / `auto` 自动 |
| `metric_card_min_width` | 重点卡最小宽度 | `156` | 用于 auto-fit 的最小卡宽 |
| `summary_card_min_width` | 摘要卡最小宽度 | `260` | 摘要卡响应式最小宽度 |
| `chart_panel_height` | 主图表高度 | `320` | PC 主图表默认高度 |
| `mini_chart_height` | 小走势图高度 | `64` | 产品卡与表格小图高度 |
| `product_list_mode` | 产品列表模式 | `table` | `table` 表格 / `cards` 卡片 / `auto` 自动 |
| `product_table_page_size` | 产品表每页条数 | `10` | 首页产品列表默认分页大小 |
| `product_table_columns` | 产品表默认列 | JSON | 记录管理员配置的列显隐与顺序 |
| `product_table_mobile_chart` | 移动端小走势图 | `true` | 移动端产品卡是否展示迷你走势 |
| `sidebar_collapse_width` | 侧栏折叠阈值 | `1280` | 低于该宽度进入窄侧栏 |

`product_table_columns` 建议 JSON：

```json
{
  "columns": [
    { "key": "selection", "visible": false, "order": 1, "pinned": "left", "minWidth": 44 },
    { "key": "product", "visible": true, "order": 2, "pinned": "left", "minWidth": 220 },
    { "key": "category", "visible": true, "order": 3, "priority": 3, "minWidth": 120 },
    { "key": "specs", "visible": true, "order": 4, "priority": 4, "minWidth": 120 },
    { "key": "unit", "visible": true, "order": 5, "priority": 5, "minWidth": 88 },
    { "key": "currency", "visible": true, "order": 6, "priority": 5, "minWidth": 80 },
    { "key": "latestPrice", "visible": true, "order": 7, "priority": 1, "minWidth": 120 },
    { "key": "priceChange", "visible": true, "order": 8, "priority": 1, "minWidth": 140 },
    { "key": "trend", "visible": true, "order": 9, "priority": 2, "minWidth": 120 },
    { "key": "updatedTime", "visible": true, "order": 10, "priority": 4, "minWidth": 150 },
    { "key": "actions", "visible": true, "order": 11, "pinned": "right", "minWidth": 88 }
  ]
}
```

列 key 使用英文协议值，显示名称通过前端固定表头文案或后续字典分类扩展；不可把列 key 当中文展示。

兼容规则：
- `card_columns` 和 `card_columns_mobile` 保留。
- 当 `responsive_strategy=manual` 时继续使用现有列数配置。
- 当 `responsive_strategy=auto` 时，列数由容器宽度与最小卡宽共同决定。

### 配置保存与治理模型

style-settings 当前已经使用草稿保存模式，本次 Home 配置扩展必须继续沿用该模型。

配置状态：

| 状态 | 含义 |
|------|------|
| `serverConfig` | 服务端已保存配置 |
| `draftConfig` | 当前正在编辑的配置 |
| `appliedConfig` | 当前预览正在使用的配置 |
| `hasUnsavedChanges` | 是否存在未保存变更 |

治理要求：
- HomeExperiencePanel 中的任何调整都只进入草稿。
- 顶部“保存配置”统一提交，不允许单项配置绕过保存。
- 保存成功后刷新字典缓存和 style config 缓存。
- 保存失败时保留草稿，并显示失败原因。
- 新增 JSON 配置必须做 schema 校验，非法 JSON 不允许保存。
- `home_layout`、`home_widget`、`category_visual_config` 仍属于受保护配置，普通字典管理页面默认隐藏。
- 首页真实运行态只读取已保存配置，不读取管理员未保存草稿。

### 配置分组建议

将 HomeExperiencePanel 调整为四组，而不是把所有配置堆在一个区域：

| 分组 | 配置 |
|------|------|
| 首页模式 | 响应式策略、首页密度、核心区布局、产品列表模式 |
| 组件编排 | 经营摘要、重点产品、主曲线、产品列表、风险预警的显隐与排序 |
| 表格能力 | 默认每页条数、列显隐与顺序、移动端小走势图、是否显示选择框 |
| 图表与卡片 | 重点产品数量、主图表高度、小走势图高度、卡片最小宽度 |

这样可以体现系统自己的“可管控首页”能力，而不是只提供几个 stepper。

### HomeExperiencePanel 调整

将“首页布局”拆成三组：

1. **布局策略**
   - 响应式策略：自动 / 手动。
   - 核心区布局：自动 / 左右 / 上下。
   - 产品列表模式：自动 / 表格 / 卡片。
   - 产品表分页大小：10 / 20 / 50。
   - 产品表列设置：勾选列显隐、拖拽列顺序。

2. **密度与尺寸**
   - 首页密度：紧凑 / 标准 / 舒展。
   - 摘要卡最小宽度。
   - 重点产品卡最小宽度。
   - 主图表高度。
   - 小走势图高度。

3. **内容数量与组件**
   - 重点产品数量。
   - 重点走势显示。
   - 预警区显示。
   - 组件显隐排序。

### HomePreview 调整

当前预览只有 PC/移动两种视口，建议扩展为：
- 宽屏 1440
- 桌面 1280
- 窄屏 1024
- 平板 768
- 手机 390

预览要展示：
- 摘要卡列数变化。
- 核心区左右/上下切换。
- 产品列表表格/卡片切换。
- 侧边栏宽栏/窄栏/抽屉切换。

这样管理员在 style-settings 中调参数时，可以直接看到真实断点效果，而不是只看到抽象列数。

### HomePreview 与真实 Home 同源要求

为了避免样式设置页预览和真实首页割裂，必须抽出同源 resolver：

```
useHomeLayoutResolver(config, containerWidth)
├─ resolveLayoutTier()
├─ resolveSummaryColumns()
├─ resolveCoreWorkspaceMode()
├─ resolveMetricGrid()
├─ resolveProductTableMode()
├─ resolveVisibleColumns()
└─ resolveChartHeights()
```

真实 Home 与 HomePreview 都调用该 resolver，只是数据源不同：

| 场景 | 配置来源 | 数据来源 |
|------|----------|----------|
| 真实 Home | 已保存配置 | 后端真实产品/价格/字典 |
| HomePreview | 草稿配置 | 预览 mock 数据 + 当前主题/分类视觉 |

预览 mock 数据必须覆盖压力场景：
- 长产品名。
- 长产地名。
- 长单位，如“元/吨度”。
- 超大价格，如“¥104730”。
- 无价格。
- 上涨、下跌、持平。
- 多分类视觉颜色。
- 表格筛选无结果。

预览不仅展示“好看状态”，还要展示“最容易崩的状态”。

## 导航布局改造

现有 `Layout.vue` 中：
- `.pc-sidebar` 固定 240px。
- `.pc-content-wrapper` 固定 `margin-left: 240px`。
- 小于约 1030px 直接隐藏 PC 布局。

建议新增三态导航：

| 模式 | 宽度 | 触发 |
|------|------|------|
| expanded | 240px | ≥1280 |
| collapsed | 72px | 1024-1279 |
| drawer | 280px 抽屉 | <1024 |

改造要点：
- collapsed 模式只显示图标，菜单文字进入 tooltip 或 hover 展开。
- `.pc-content-wrapper` 使用 CSS 变量 `--sidebar-width`，不要写死 240px。
- Home 页面按内容容器宽度响应，而不是按浏览器总宽度响应。

## 数据与接口一致性

本次方案主要是前端布局与字典配置扩展，不涉及产品、价格、分类、产地等业务接口变更。

如后续实施新增 `home_layout` 字典项，需要同步：
- `backend/src/main/java/com/pricemanagement/config/DataInitializer.java`
- `backend/src/main/resources/init.sql`
- `docs/dev/项目设计文档.md` 的数据字典配置项说明
- `docs/dev/开发指南.md` 的字典分类说明
- `docs/dev/UI设计说明.md` 的首页布局说明

不需要新增数据库表；若未来将 style-settings 从字典迁移到专用配置表，再单独设计迁移方案。

### 接口边界建议

为了兼顾系统管控能力与实现质量，建议分两层接口推进。

#### 首期可复用现有接口

| 数据 | 现有来源 |
|------|----------|
| 产品分页 | `GET /api/products` |
| 指定日期价格 | `GET /api/prices/by-date-with-stats` |
| 产品走势 | `GET /api/products/{id}/price-trend` |
| 分类 | `GET /api/categories` |
| 字典 | `useDict` / `/api/dict` |
| 样式配置 | `/api/style/*` + `home_layout` / `home_widget` |

但首期不能继续“拉 100 条产品后前端筛选”作为最终形态。如果现有 `/api/products` 缺少筛选、排序参数，应补齐后端能力。

#### 推荐新增首页聚合接口

当进入表格分页、复杂筛选、按价格涨跌筛选后，推荐新增：

```text
GET /api/home/products
GET /api/home/curve
GET /api/home/summary
```

职责：
- `/api/home/products`：首页产品表分页行，带最新价、较昨日、迷你走势。
- `/api/home/curve`：主曲线面板数据，支持产品、日期、range、多产品对比预留。
- `/api/home/summary`：摘要卡片数据，避免前端重复计算。

接口收益：
- 前端不用拼接多接口数据。
- 分页、筛选、排序由数据库完成。
- 价格涨跌筛选可以准确分页。
- Home 页面性能更稳定。

### 系统管控边界

| 能力 | 管控位置 | 页面实现要求 |
|------|----------|--------------|
| 首页组件显隐/排序 | style-settings `home_widget` | 页面只消费配置，不写死顺序 |
| 首页布局密度/断点策略 | style-settings `home_layout` | 通过 resolver 转成 CSS 变量和布局模式 |
| 主题色/涨跌色/字体 | style-settings 全局主题 | Home 不硬编码主题色 |
| 分类视觉 | 分类视觉配置 | 产品卡、表格、图表统一接入 |
| 产地/币种/状态显示 | 字典服务 | Home 不写映射表 |
| 产品排序 | 首页排序配置 + 产品 sortOrder | 表格默认排序与首页排序一致 |
| 权限 | `usePermission` | 按权限显示价格维护、编辑、列配置等操作 |

禁止事项：
- 禁止为 Home 单独创建一套不受 style-settings 控制的颜色和字体。
- 禁止在 Home 内硬编码业务枚举中文。
- 禁止绕过字典缓存直接写固定下拉选项。
- 禁止通过 absolute 定位完成主结构。
- 禁止为了视觉还原牺牲分页、筛选、排序的真实业务能力。

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `frontend/src/views/Home.vue` | 当前首页主体，需要拆分为 HomeShell、核心区、产品表格等组件 |
| `frontend/src/components/Layout.vue` | 左侧导航宽度与 PC/移动布局切换 |
| `frontend/src/components/home/SummarySection.vue` | 摘要卡片区改造 |
| `frontend/src/components/home/TrendAnalysisChart.vue` | 主图表和产品走势卡改造 |
| `frontend/src/components/style-settings/HomeExperiencePanel.vue` | 首页体验配置入口 |
| `frontend/src/components/style-settings/preview/HomePreview.vue` | 首页响应式预览 |
| `frontend/src/composables/useHomeConfig.ts` | 首页配置读取 |
| `frontend/src/composables/useHomePreviewState.ts` | style-settings 草稿状态与保存 |
| `backend/src/main/java/com/pricemanagement/config/DataInitializer.java` | 新增 home_layout 默认配置 |
| `backend/src/main/resources/init.sql` | 初始化 SQL 同步 |

## 实现步骤

### 实施优先级

为避免重构时目标发散，按以下优先级判断取舍：

| 优先级 | 内容 | 原因 |
|--------|------|------|
| P0 | 响应式布局稳定、卡片不变形、图表不被挤压 | 解决当前最核心体验问题 |
| P0 | style-settings 配置同源、预览与真实页面一致 | 保持系统管控能力 |
| P0 | 字典、主题、分类视觉接入 | 符合项目规范与系统特色 |
| P1 | 产品表分页、筛选、排序、列显隐 | 让首页成为真实工作台 |
| P1 | 主曲线与产品卡/表格联动 | 完成业务交互闭环 |
| P2 | 首页聚合接口、多产品对比、批量操作 | 性能和扩展增强 |

### Phase 1：建立响应式布局模型

1. 新增 `frontend/src/composables/useResponsiveHomeLayout.ts`。
2. 使用 `ResizeObserver` 获取 Home 内容容器宽度。
3. 输出统一布局模式：`wide / desktop / compact / tablet / mobile`。
4. 将 `gridCols` 从简单 `windowWidth` 判断改为容器驱动。
5. 保留旧配置，默认启用 `responsive_strategy=auto`。

### Phase 2：重构首页构图

1. 从 `Home.vue` 拆出：
   - `HomeHeroToolbar.vue`
   - `HomeSummaryGrid.vue`
   - `HomeCoreWorkspace.vue`
   - `FeaturedPriceCards.vue`
   - `PriceCurvePanel.vue`
   - `HomeProductTable.vue`
2. 保持现有数据加载逻辑，先不改接口。
3. 首页组件仍按 `home_widget` 顺序渲染，但 `core_metrics` 与 `trend_chart` 在默认模式下合并为 `HomeCoreWorkspace`。
4. 若管理员关闭 `trend_chart`，右侧曲线区隐藏，重点产品区全宽。

### Phase 3：工程化产品卡片

1. 产品价格卡使用 CSS Grid 固定内部行：
   - 标题
   - 元信息
   - 价格
   - 涨跌
   - 图表
2. 图表区使用固定高度容器。
3. 价格与单位解耦，窄卡时单位自动下移。
4. 分类图标从 absolute 装饰改为参与标题行布局，避免覆盖文字。
5. 产地胶囊保持名称右侧，但设置最大宽度和省略策略。

### Phase 4：建立主图表面板

1. 将当前重点走势能力升级为参考图中的主曲线面板。
2. 支持默认产品选择、时间范围切换、货币显示。
3. 图表高度由 `chart_panel_height` 和布局模式共同决定。
4. 小走势图只用于产品卡和表格，不承载主分析任务。

### Phase 5：产品列表表格化

1. PC 端新增 `HomeProductTable.vue`。
2. 使用现有产品、价格、分类、产地数据组装行。
3. 接入分页、筛选、排序状态，切换筛选时重置页码；后端组合执行 `keyword`、`categoryId`、`status`，避免关键词搜索绕过分类/状态过滤。
4. 小走势图列固定宽高。
5. 根据布局模式隐藏低优先级列。
6. 移动端保留卡片列表和加载更多，不强行渲染完整表格。
7. 评估是否新增 `/api/home/products` 聚合接口；若继续复用 `/api/products`，需补齐后端筛选排序参数。
8. 产品列表模式由 `home_layout.product_list_mode` 控制，支持 `table` / `cards` / `auto`；样式设置预览必须同步展示当前模式和 `product_table_page_size`。

### Phase 6：style-settings 配置升级

1. 扩展 `HomeLayoutConfig` 类型。
2. `useHomeConfig.ts` 读取新增字典项。
3. `useHomePreviewState.ts` 支持新增字段草稿、保存、回显。
4. `HomeExperiencePanel.vue` 改为分组配置。
5. `HomePreview.vue` 新增多视口预览。
6. `DataInitializer.java` 和 `init.sql` 初始化新增 `home_layout` 项。

### Phase 7：侧边栏三态响应

1. `Layout.vue` 引入 `sidebarMode`。
2. 1280 以下 PC 侧边栏进入 72px 窄栏。
3. 1024 以下切换抽屉菜单。
4. 内容区使用 `--sidebar-width` 控制偏移。
5. 验证 Home 内容容器宽度不会被 240px 固定侧栏持续挤压。

### Phase 8：文档同步

按 AGENTS.md 要求，实施完成后同步：
- `README.md`
- `docs/dev/开发指南.md`
- `docs/ops/IDEA部署指南.md`（如无部署变化，可注明无变更）
- `docs/dev/项目设计文档.md`
- `docs/archive/项目完成总结.md`
- `docs/dev/UI设计说明.md`
- 数据字典配置项说明

## 风险与控制

| 风险 | 表现 | 控制方式 |
|------|------|----------|
| 配置过多导致管理员难用 | 首页体验面板过于复杂 | 分组展示，高级配置折叠，提供“恢复推荐值” |
| 预览与真实页面不一致 | 保存后真实 Home 和预览不同 | 抽取同源 resolver，Preview 只换数据源 |
| 前端拼接多接口导致竞态 | 表格价格和产品错位 | 用 request token / abort controller；中期上聚合接口 |
| 分类视觉过度染色 | 页面变花，价格不突出 | 分类色只用于 icon、边框、小图线、弱背景 |
| 表格列太多 | 中屏拥挤或横向滚动过重 | 列优先级 + 自动隐藏 + 列设置 |
| URL query 状态污染 | 复制链接打开异常 | query schema 校验，非法值回退默认 |
| 旧配置不兼容 | 老数据缺少新增 home_layout 项 | 所有新增配置都有前端默认值和初始化补齐 |
| 权限遗漏 | 普通用户看到管理按钮 | 所有操作统一走 `usePermission` |

## Verification

### 功能验证

1. 首页在以下宽度均无横向页面滚动：
   - 1600
   - 1440
   - 1280
   - 1194
   - 1024
   - 768
   - 390
2. 重点产品价格卡在长产品名、长产地、长单位下不重叠。
3. `￥104730 / 元/吨`、`￥5140 / 元/吨度` 等长价格单位组合不挤压图表。
4. 小走势图容器高度稳定，不随文字换行压扁。
5. 同一行产品卡底部对齐。
6. 产品列表在 PC 端为表格，在移动端为卡片列表。
7. 产品列表分页、搜索、筛选、排序互相组合时结果正确，且不会一次性加载全量产品。
8. style-settings 修改布局策略后，HomePreview 与真实 Home 页面一致。

### 自动化验证

建议增加 Playwright 视觉检查：

1. 分别截取 1440、1280、1024、768、390 宽度下 Home 页面。
2. 检查 `.product-card`、`.price-row`、`.mini-chart-frame`、`.home-product-table` 是否存在重叠。
3. 检查 `document.documentElement.scrollWidth <= window.innerWidth + 1`。
4. 检查 ECharts canvas 非空，宽高满足对应断点最小值。

### 构建验证

```bash
cd frontend
npm run build
```

后端如新增字典初始化项：

```bash
cd backend
mvn test
```

## 验收标准

1. Home 页面在 1440、1280、1024、768、390 下均能保持稳定构图。
2. 首页首屏符合“摘要 + 核心价格工作区 + 产品列表”的信息层级。
3. 管理员可以在 style-settings 中控制布局策略、密度、组件显隐和顺序。
4. 卡片、图表、表格具备稳定尺寸，不因内容长短出现明显跳动。
5. 代码中主布局使用 grid/flex/container 响应，不依赖 absolute 完成结构排布。
6. 字典配置、前端类型、初始化数据、项目文档保持一致。

## 9.5+ 评分验收量表

若以 10 分评价本次 Home 重构，达到 9.5+ 需要满足以下量表。

| 维度 | 权重 | 9.5+ 标准 |
|------|------|-----------|
| 构图专业度 | 15% | 信息层级清晰，首屏一眼区分摘要、核心价格、主曲线、产品表格 |
| 响应式工程质量 | 20% | 1600/1440/1280/1024/768/390 均无挤压、重叠、页面横向滚动 |
| 系统管控融合 | 20% | 完整接入 style-settings、主题 token、字典、分类视觉、权限，不另起静态样式 |
| 业务闭环 | 15% | 重点卡、主曲线、表格、日期、筛选、分页、排序之间状态清晰且可恢复 |
| 数据密度与可读性 | 10% | 价格数字突出，单位/产地/涨跌/图表稳定可读，不牺牲表格扫描效率 |
| 可维护性 | 10% | Home 拆分组件清晰，布局 resolver 同源，配置 schema 可校验 |
| 可验证性 | 10% | 有 Playwright 断点截图、非空图表、无重叠、无横向滚动等自动检查 |

扣分项：
- 任一断点出现文字和图表重叠，最高 8 分。
- HomePreview 与真实 Home 布局不同，最高 8.5 分。
- 绕过字典或分类视觉硬编码显示，最高 8.5 分。
- 产品表仍然全量加载后前端过滤，最高 8 分。
- 主曲线、重点卡、表格没有联动关系，最高 8.8 分。
- style-settings 配置保存绕过草稿模型，最高 8.5 分。

达到 9.5+ 的定义：
- 它不仅像参考图一样专业，而且比参考图更符合本系统：可配置、可治理、可预览、可响应、可验证。
