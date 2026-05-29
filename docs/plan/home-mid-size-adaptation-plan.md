# Home 中间尺寸适配实施方案

## Context

Home 页面当前已有三档体验：全尺寸桌面、中间尺寸 PC/平板宽度、H5 移动端。中间尺寸的问题不是单纯“变窄”，而是信息密度、图表承载方式和产品列表模式没有形成稳定规则：

- 第一层经营摘要仍展示 4 项，空间利用偏满。
- 重点产品价格区域没有 Mini 折线图，但仍可能展示右侧大折线图。
- 产品列表在中间尺寸下会被 `productListPresentation` 自动降级为卡片，导致它没有沿用全尺寸产品列表样式，也没有严格尊重 `style-settings?section=home` 中的“产品列表模式”。

本方案只改 Home 前端布局与交互，不改接口协议、数据库结构或首页配置存储结构。

## Goals

- 中间尺寸第一层只展示 2 项摘要：`产品总数`、`当日更新`。
- 中间尺寸重点产品价格卡片展示 Mini 折线图。
- 中间尺寸不展示任何大折线图，包括 `HomePriceCurvePanel` 和独立 `TrendAnalysisChart` 大图模块。
- 产品列表在全尺寸、中间尺寸、H5 都遵循 `style-settings?section=home` 的产品列表模式。
- 产品列表样式同源：中间尺寸沿用全尺寸表格/卡片模板与分类视觉，不再出现一套中间尺寸专属降级样式。

## Non-Goals

- 不调整后端分页、价格、趋势接口。
- 不改变 `home_layout` 字典配置结构。
- 不在本方案内重新设计首页所有组件顺序。
- 不改变“重点产品数量”的后台配置上限，仍由 `featuredProductCount` 控制，只有中间尺寸摘要项数量固定收敛为 2。

## 尺寸定义

H5 与 PC 的分界仍以现有 `useLayout()` 为准：`windowWidth < 1024` 为 H5。

PC 内部的“全尺寸 / 中间尺寸”不应只看 `window.innerWidth`，因为左侧菜单、二级导航和主内容 padding 会占用空间。建议以 Home 内容容器宽度为主，窗口宽度仅作为 fallback。

建议新增容器测量：

```ts
const homeRootRef = ref<HTMLElement | null>(null)
const homeContentWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)

const homeViewportTier = computed<'full' | 'middle' | 'h5'>(() => {
  if (!isPCLayout.value) return 'h5'
  return homeContentWidth.value >= 1040 ? 'full' : 'middle'
})

const isFullHomeLayout = computed(() => homeViewportTier.value === 'full')
const isMiddleHomeLayout = computed(() => homeViewportTier.value === 'middle')
```

实现要点：

- 在 `.pc-home` 上绑定 `ref="homeRootRef"`。
- 使用 `ResizeObserver` 监听 `.pc-home` 实际宽度，组件卸载时断开。
- 当 `ResizeObserver` 不可用时 fallback 到 `windowWidth`：`windowWidth >= 1280 ? 'full' : 'middle'`。
- `1040px` 是内容容器阈值，不是浏览器窗口阈值；实施后可根据真实截图微调到 `1080px`，但必须保持“容器宽度优先”的原则。

## 实现方案

### 1. 第一层摘要中间尺寸只保留 2 项

当前 `SummarySection.vue` 默认输出 4 项：

- 产品总数
- 当日更新
- 更新率
- 覆盖品类

中间尺寸明确只保留前两项：`产品总数`、`当日更新`。这不是根据配置排序动态取前两项，而是固定保留最关键的两个运营信号。

实施方式：

- 给 `SummarySection.vue` 增加可选 prop：

```ts
const props = defineProps<{
  summary: HomeSummary
  loading?: boolean
  compact?: boolean
}>()
```

- 用 `compact` 控制展示项：

```ts
const displayCards = computed(() =>
  props.compact
    ? summaryCards.value.filter(card => ['products', 'updated'].includes(card.key))
    : summaryCards.value
)
```

- 模板从 `summaryCards` 改为 `displayCards`。
- Home PC 和 H5 都通过同一入口传值：

```vue
<SummarySection
  v-if="section.key === 'summary_stats'"
  :summary="summaryForDisplay"
  :compact="isMiddleHomeLayout"
/>
```

验收标准：

- 中间尺寸只显示 `产品总数`、`当日更新`。
- 全尺寸仍显示 4 项。
- H5 仍按当前移动端规则显示，不因为 `compact` 误触发。

### 2. 中间尺寸重点产品价格展示 Mini 折线图，取消大折线图

中间尺寸下，“重点走势”不再由大图承载，而是由重点产品卡片内的 Mini 折线图承载。

实施方式：

- 在 `featured-price-card` 内增加 Mini 折线图区域：

```vue
<span
  v-if="isMiddleHomeLayout && chartOptionsMap.get(product.id)"
  class="featured-mini-chart"
>
  <v-chart
    class="mini-chart"
    :option="chartOptionsMap.get(product.id)"
    :autoresize="chartAutoresize"
  />
</span>
```

- 抽出大图显示条件，避免模板中散落判断：

```ts
const showCoreLargeCurve = computed(() =>
  isFullHomeLayout.value && isHomeSectionVisible('trend_chart')
)

const showStandaloneTrendChart = computed(() =>
  isFullHomeLayout.value && !isHomeSectionVisible('core_metrics')
)
```

- `core_metrics` 内的 `HomePriceCurvePanel` 只在 `showCoreLargeCurve` 时显示。
- 独立 `TrendAnalysisChart` 只在 `showStandaloneTrendChart` 时显示。
- 中间尺寸即使 `trend_chart` widget 开启，也不展示大图模块；如果 `core_metrics` 同时关闭，中间尺寸直接跳过 `trend_chart`，避免“大图回流”。
- 中间尺寸 `featured-price-grid` 固定 2 列，卡片高度允许略增，用于容纳价格、变化和 Mini 图。

验收标准：

- 中间尺寸重点产品卡片内有 Mini 折线图。
- 中间尺寸不出现 `HomePriceCurvePanel`。
- 中间尺寸不出现独立 `TrendAnalysisChart` 大图。
- 全尺寸仍保留“重点产品价格 + 大折线图”的组合。
- Mini 折线图颜色仍读取分类视觉配置，与 Home 全尺寸产品列表一致。

### 3. 产品列表模式全尺寸 / 中间尺寸 / H5 全部一致

当前 `productListPresentation` 把尺寸判断混进了模式判断，导致明确选择“表格”时，中间尺寸仍可能降级成卡片。

新规则以配置优先：

```ts
const productListPresentation = computed<'table' | 'cards'>(() => {
  const mode = layoutConfig.value.productListMode
  if (mode === 'table') return 'table'
  if (mode === 'cards') return 'cards'
  return homeViewportTier.value === 'h5' ? 'cards' : 'table'
})
```

规则解释：

- `表格`：全尺寸、中间尺寸、H5 都进入表格模式。
- `卡片`：全尺寸、中间尺寸、H5 都进入卡片模式。
- `自动`：全尺寸和中间尺寸用表格，H5 用卡片。

H5 的“一致”指模式语义一致，不要求桌面 8 列表格在 H5 上无差别硬挤。H5 表格模式必须满足：

- 使用同一套产品列表数据、筛选、分页、排序、选中逻辑。
- 使用同一套字段语义。
- 表格容器内部横向滚动，页面整体不得横向滚动。
- 允许移动端压缩列宽、固定最小表格宽度、缩小字号，但不能切回旧的分类分组卡片。

验收标准：

- `style-settings?section=home` 选择“表格”：全尺寸、中间尺寸、H5 都展示表格。
- `style-settings?section=home` 选择“卡片”：全尺寸、中间尺寸、H5 都展示卡片。
- `style-settings?section=home` 选择“自动”：全尺寸和中间尺寸展示表格，H5 展示卡片。
- 中间尺寸产品列表与全尺寸复用同一套表格/卡片模板和样式类。

### 4. 产品列表共享组件分阶段实施

为了降低一次改动风险，产品列表共享组件分两阶段推进。

第一阶段：行为修正，必须完成。

- 重写 `productListPresentation`。
- PC 中间尺寸直接复用现有 `home-product-table-section` 的表格/卡片分支。
- H5 `product_list` 分支也接入新的 `productListPresentation`，先复用同一批 helper 和样式类，保证模式一致。
- 为 H5 表格模式补充 `.product-table-shell` 内部横向滚动。

第二阶段：结构收敛，建议紧随第一阶段完成。

- 抽取共享组件：

```text
frontend/src/components/home/HomeProductList.vue
```

建议组件输入：

- `products`
- `categories`
- `loading`
- `presentation`
- `tablePage`
- `tableSize`
- `tableTotalElements`
- `tableTotalPages`
- `selectedProduct`
- `chartOptionsMap`
- `searchQuery`
- `selectedCategoryIds`
- 价格显示、分类视觉、产地显示相关 helper 或 composable 返回值

建议组件事件：

- `update:searchQuery`
- `category-change`
- `size-change`
- `sort-change`
- `page-change`
- `select-product`
- `view-product`

抽取原则：

- 不改变 API 请求位置，仍由 `Home.vue` 管理加载、分页、筛选和趋势数据。
- 先保留现有 CSS 类名，避免视觉回归。
- 抽组件后再逐步删除 H5 旧 `category-product-groups mobile` 产品列表主实现。

## 需求优化后规则汇总

| 场景 | 摘要 | 重点产品价格 | 大折线图 | 产品列表 |
|------|------|--------------|----------|----------|
| 全尺寸 | 4 项 | 重点卡片 | 显示 | 跟随表格/卡片/自动 |
| 中间尺寸 | 固定 2 项：产品总数、当日更新 | 重点卡片 + Mini 折线图 | 不显示 | 跟随表格/卡片/自动，自动为表格 |
| H5 | 保持当前摘要规则 | 保持移动端重点卡片 | 不受本次新增大图影响 | 跟随表格/卡片/自动，自动为卡片 |

## 关键参考文件

- `frontend/src/views/Home.vue`
  - `gridCols`
  - `featuredProductsForDisplay`
  - `productListPresentation`
  - `visibleHomeSections`
  - PC `core_metrics` / `trend_chart` / `product_list` 模板分支
  - H5 `product_list` 模板分支
- `frontend/src/components/home/SummarySection.vue`
- `frontend/src/components/home/HomePriceCurvePanel.vue`
- `frontend/src/components/home/TrendAnalysisChart.vue`
- `frontend/src/composables/useHomeConfig.ts`
- `frontend/src/composables/useHomePreviewState.ts`
- `frontend/src/components/style-settings/HomeExperiencePanel.vue`
- `frontend/src/components/style-settings/preview/HomePreview.vue`
- `docs/dev/UI设计说明.md`
- `AGENTS.md`

## 实现步骤

1. 在 `Home.vue` 增加 `.pc-home` 容器宽度测量：`homeRootRef`、`homeContentWidth`、`ResizeObserver`。
2. 增加 `homeViewportTier`、`isMiddleHomeLayout`、`isFullHomeLayout`。
3. 给 `SummarySection.vue` 增加 `compact` prop，中间尺寸固定展示 `产品总数`、`当日更新`。
4. 调整 `core_metrics`：
   - 中间尺寸卡片内增加 Mini 折线图。
   - 中间尺寸隐藏 `HomePriceCurvePanel`。
   - 中间尺寸隐藏独立 `TrendAnalysisChart` 大图。
   - 补充 `featured-mini-chart`、中间尺寸 2 列卡片、高度和文本省略样式。
5. 重写 `productListPresentation`，让产品列表模式以 `style-settings?section=home` 为准。
6. 调整 H5 `product_list` 分支，使其也按 `productListPresentation` 渲染表格或卡片。
7. 补充 H5 表格模式样式：表格容器内部横向滚动、最小宽度、紧凑字号、页面整体 `overflow-x: hidden`。
8. 更新 `HomePreview.vue`，让样式设置预览中的 PC/移动产品列表模式与真实 Home 规则一致。
9. 更新 `docs/dev/UI设计说明.md`，记录中间尺寸、Mini 图、大图隐藏和产品列表模式规则。
10. 第二阶段抽取 `HomeProductList.vue`，合并 PC/H5 产品列表模板，保留现有视觉类名。

## Verification

基础验证：

- 运行 `npm run build`。
- 运行浏览器或 Playwright 截图检查。

尺寸验证：

- 全尺寸：`1440px`
  - 摘要显示 4 项。
  - 重点产品价格 + 大折线图正常显示。
  - 产品列表按 `table/cards/auto` 显示。
- 中间尺寸：建议至少检查 `1180px` 和 `1024px`
  - 摘要只显示 `产品总数`、`当日更新`。
  - 重点产品卡片展示 Mini 折线图。
  - 不显示 `HomePriceCurvePanel`。
  - 不显示独立 `TrendAnalysisChart` 大图。
  - 产品列表按 `table/cards/auto` 显示，自动为表格。
  - 页面整体不出现横向滚动。
- H5：`390px`
  - 表格模式：产品列表显示表格，表格容器内部横向滚动。
  - 卡片模式：产品列表显示卡片。
  - 自动模式：产品列表显示卡片。
  - 页面整体不出现横向滚动。

配置验证：

- 分别在 `style-settings?section=home` 保存“表格 / 卡片 / 自动”。
- 刷新 Home 后确认全尺寸、中间尺寸、H5 的产品列表模式符合规则。
- 检查 `HomePreview.vue` 的 PC/移动预览与真实页面规则一致。

视觉验证：

- Mini 折线图不遮挡价格、产品名、产地、规格。
- 中间尺寸 2 列重点产品卡片内文本不溢出。
- 分类色、产地胶囊、产品名强调与全尺寸保持同源。
- H5 表格模式下按钮、筛选、分页不被横向滚动区域吞掉。

## 风险与注意事项

- 容器宽度阈值 `1040px` 需要通过截图确认，若 1180px 视口下内容区仍拥挤，可微调到 `1080px`。
- H5 表格模式体验天然更重，但这是用户明确选择“表格”时的结果；自动模式仍保护 H5 为卡片。
- 中间尺寸隐藏大图后，`trend_chart` widget 在中间尺寸不再产生独立视觉块，这一点需要同步写入 UI 说明，避免后续误判为组件未渲染。
- 抽取 `HomeProductList.vue` 前，不要同时重写数据加载逻辑；先统一展示，再收敛结构。
- `ResizeObserver` 需要在 `onUnmounted` 中断开，避免切换路由后残留监听。
