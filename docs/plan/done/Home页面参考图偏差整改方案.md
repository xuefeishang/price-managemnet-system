# Home页面参考图偏差整改方案

## Context

当前 Home 页面已经完成第一轮响应式工作台改造，但与用户提供的参考图仍存在三类关键偏差：

1. 首行经营摘要没有按参考图的信息结构实现，尤其是“产品总数、今日更新、覆盖品类”等卡片内容和视觉层级不一致。
2. 价格曲线仍复用现有趋势卡片组件，缺少参考图中“主价格曲线分析面板”的大图表结构。
3. 页面在部分浏览器宽度下仍出现页面级左右滚动条，说明响应式容器和固定宽度治理不足。

本整改方案聚焦这三项偏差，不另起一套设计语言，继续基于系统已有的 `style-settings`、主题 token、分类视觉、字典服务和分页接口能力完成整改。

## 执行进展

2026-05-26 已执行首轮整改：

- `/api/home/summary` 增加 `coveredCategoryCount`、`activeCategoryCount`、`changedProductCount`。
- `SummarySection.vue` 改为“产品总数、今日更新、覆盖品类、价格异动”四张经营摘要卡。
- 新增 `HomePriceCurvePanel.vue`，Home PC 核心区价格曲线改为独立主图表面板。
- Home 产品列表在中屏自动切换为卡片模式，表格不再使用固定 `1040px` 最小宽度撑开页面。
- `Layout.vue` 与 Home 主容器补充 `min-width: 0`、`max-width` 和页面级横向溢出防护。

## 问题复核

### 1. 首行摘要卡片未对齐参考图

当前实现：

- `frontend/src/components/home/SummarySection.vue` 渲染 4 张卡：
  - 产品总数
  - 今日更新
  - 平均变动
  - 上涨/下跌 split 卡
- `HomeSummary` 当前字段只有：
  - `totalProducts`
  - `priceUpdatedToday`
  - `avgPriceChange`
  - `risingCount`
  - `fallingCount`
  - `flatCount`

偏差：

- 参考图首行更像经营概览，卡片重点是“产品总数、今日更新、覆盖品类”等稳定业务指标。
- 当前第三、四卡偏行情波动，信息层级和参考图不一致。
- 卡片内容没有形成统一结构：图标、标题、主数字、辅助说明。
- “覆盖品类”当前需要由分类数据或后端汇总返回，不能在前端拍脑袋写死。

### 2. 价格曲线与参考图不一致

当前实现：

- Home 右侧价格曲线使用 `TrendAnalysisChart`，该组件原本偏“重点走势卡片集合”。
- 主图表通过单个 `ProductTrendItem` 传入，标题、副指标、时间范围、图例、最高/最低/最新标记都没有形成完整主面板。
- 图表高度虽然有固定值，但面板结构仍不像参考图中的主分析区。

偏差：

- 参考图价格曲线是核心分析面板，不是若干小趋势卡复用。
- 右上角应有时间范围控制，面板内应有当前产品、当前价格、涨跌、单位、更新时间等摘要。
- 图表需要稳定占据主要视觉面积，并支持空状态、加载态和选中产品变化。

### 3. 页面仍出现页面级左右滚动条

当前风险点：

- `.pc-home` 使用固定 `max-width` 和 `padding`，在布局容器被侧边栏挤压时可能与内部 `min-width` 冲突。
- 产品表 `.home-product-table` 设置 `min-width: 1040px`，虽然外层有 `overflow-x: auto`，但父级若没有 `min-width: 0`，仍可能把页面整体撑宽。
- `.core-workspace` 使用 `minmax(360px, 0.92fr) minmax(460px, 1.08fr)`，加上 gap 和 padding 后，在 1024-1280 区间容易超出可用宽度。
- 搜索区、筛选器、分页区存在固定宽度或不换行场景。

整改边界：

- 页面级横向滚动必须消除。
- 表格内部横向滚动允许存在，但只能发生在 `.product-table-shell` 内。
- 图表、卡片、筛选条不得撑破主内容容器。

## 整改目标

1. 首行摘要按参考图重构为稳定经营指标区，默认展示：
   - 产品总数
   - 今日更新
   - 覆盖品类
   - 价格异动
2. 价格曲线重构为独立主图表面板，不再直接用趋势卡片样式承载主图。
3. 页面在 `1600 / 1440 / 1280 / 1024 / 768 / 390` 宽度下都不能出现页面级左右滚动条。
4. 所有显示名称继续遵循字典和主数据治理，不硬编码产地、币种、状态、分类标签。
5. 新增配置继续进入 `style-settings`，预览与真实 Home 保持一致。

## 整改方案

### 一、首行摘要区整改

#### 目标结构

首行摘要区改为 `HomeSummaryStrip` 结构：

```text
┌──────────────┬──────────────┬──────────────┬──────────────┐
│ 产品总数      │ 今日更新      │ 覆盖品类      │ 价格异动      │
│ 128          │ 24           │ 8            │ 12           │
│ 启用产品      │ 今日有报价     │ 已启用品类     │ 上涨/下跌合计  │
└──────────────┴──────────────┴──────────────┴──────────────┘
```

#### 数据字段设计

扩展 `HomeSummary`：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| `totalProducts` | number | 后端 `/api/home/summary` | 启用产品总数 |
| `priceUpdatedToday` | number | 后端 `/api/home/summary` | 当前日期有价格记录的产品数 |
| `coveredCategoryCount` | number | 后端汇总或前端基于启用分类兜底 | 覆盖品类数 |
| `activeCategoryCount` | number | 后端汇总 | 启用品类总数，可作为辅助说明 |
| `changedProductCount` | number | 后端汇总 | 上涨 + 下跌数量 |
| `avgPriceChange` | number | 既有字段 | 可作为价格异动辅助信息 |
| `risingCount` | number | 既有字段 | 上涨数量 |
| `fallingCount` | number | 既有字段 | 下跌数量 |
| `flatCount` | number | 既有字段 | 持平数量 |

短期如果后端暂未返回 `coveredCategoryCount`：

- 前端可以用 `categories.filter(status === 'ACTIVE').length` 作为视觉兜底。
- 但方案完成态必须由 `/api/home/summary` 返回，避免 Home 页面重复散落统计逻辑。

#### 组件调整

建议将 `SummarySection.vue` 升级为固定卡片配置：

```ts
const summaryCards = computed(() => [
  {
    key: 'totalProducts',
    label: '产品总数',
    value: summary.totalProducts,
    helper: '启用产品',
    icon: 'box'
  },
  {
    key: 'priceUpdatedToday',
    label: '今日更新',
    value: summary.priceUpdatedToday,
    helper: '今日有报价',
    icon: 'refresh'
  },
  {
    key: 'coveredCategoryCount',
    label: '覆盖品类',
    value: summary.coveredCategoryCount,
    helper: `${summary.activeCategoryCount || summary.coveredCategoryCount} 个启用品类`,
    icon: 'layers'
  },
  {
    key: 'changedProductCount',
    label: '价格异动',
    value: summary.changedProductCount,
    helper: `${summary.risingCount} 涨 / ${summary.fallingCount} 跌`,
    icon: 'activity'
  }
])
```

注意：

- 卡片标题可以是固定业务文案，不属于编码值显示，不违反字典规范。
- 状态、币种、产地、分类名仍不得硬编码。
- 卡片主数字必须使用等宽数字字体 `--font-mono`。
- 图标应使用现有图标体系或 lucide 图标，不再手写复杂装饰图。

#### 响应式规则

| 宽度 | 摘要区布局 |
|------|------------|
| ≥1440 | 4 列 |
| 1200-1439 | 4 列或 2+2，取决于容器宽度 |
| 1024-1199 | 2 列 |
| <768 | 2 列 |
| <420 | 1 列或横向滑动，优先 1 列避免页面横滚 |

CSS 建议：

```css
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(220px, 100%), 1fr));
  gap: var(--spacing-md);
}

.summary-card {
  min-width: 0;
}
```

### 二、价格曲线主面板整改

#### 目标结构

新建或拆分 `HomePriceCurvePanel.vue`：

```text
┌─────────────────────────────────────────────────────────┐
│ 价格曲线                                  7日 30日 90日 │
│ 当前产品名称 / 产地 / 规格                               │
│ ¥104730 / 元/吨      较昨日 +320   更新时间 2026-05-26   │
│                                                         │
│                    ECharts 主折线图                     │
│                                                         │
│ 最低价 / 最高价 / 均价 / 最新价                         │
└─────────────────────────────────────────────────────────┘
```

#### 组件职责

`HomePriceCurvePanel.vue` 只负责主曲线，不再承担重点走势卡片职责。

Props：

| Prop | 说明 |
|------|------|
| `product` | 当前选中产品 |
| `trendItem` | 当前产品趋势点 |
| `rangeOptions` | 时间范围配置，来自 `home_layout` 或默认值 |
| `activeDays` | 当前时间范围 |
| `loading` | 趋势加载态 |

Emits：

| Emit | 说明 |
|------|------|
| `range-change` | 切换 7/30/90 等时间范围 |
| `open-detail` | 跳转产品详情，可选 |

#### 图表规范

ECharts 配置要求：

- `grid.left/right/top/bottom` 使用稳定值，避免 label 被裁切。
- `height` 默认 320px，中屏 280px，移动端 240px。
- `tooltip.confine = true`，防止 tooltip 撑出页面。
- `connectNulls = true`，缺失价格点不让曲线断成混乱形态。
- 当前产品使用分类视觉线色：
  - `getCategoryVisual(categoryId).chartLineColor`
  - 缺失时回退 `themeConfig.chartPrimaryColor`
- 标记点保持克制：
  - 最新价可显示；
  - 最高/最低默认不显示大气泡，只在 tooltip 或底部统计里呈现，避免遮挡曲线。

#### 与重点产品卡联动

交互关系：

- 点击左侧重点产品卡：更新 `selectedProductId`。
- 主曲线随 `selectedProductId` 更新。
- 切换时间范围：只刷新曲线数据，不重置产品列表分页。
- 产品列表行点击：同样更新主曲线产品。
- 若当前选中产品不在分页结果中，曲线仍保留当前产品，不强制清空。

### 三、自适应和横向滚动整改

#### 页面级原则

全局原则：

```css
.app-main,
.main-content,
.pc-home,
.core-workspace,
.home-product-table-section,
.product-table-toolbar {
  min-width: 0;
  max-width: 100%;
}
```

页面禁止：

- 在 `.pc-home`、`.main-content`、`.content-wrapper` 上出现 `width: 1440px` 之类固定宽度。
- 子元素使用 `min-width` 撑开页面。
- 表格 `min-width` 直接作用到页面级容器。

允许：

- `.product-table-shell` 内部横向滚动。
- 移动端产品卡列表自然纵向滚动。

#### 重点修复点

1. `.pc-home`

整改：

```css
.pc-home {
  width: 100%;
  max-width: min(1440px, 100%);
  min-width: 0;
  box-sizing: border-box;
}
```

2. `.core-workspace`

当前风险：

```css
grid-template-columns: minmax(360px, 0.92fr) minmax(460px, 1.08fr);
```

整改：

```css
.core-workspace {
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
}

@container home-main (max-width: 1180px) {
  .core-workspace {
    grid-template-columns: 1fr;
  }
}
```

如果暂不引入 container query，则用媒体查询：

```css
@media (max-width: 1280px) {
  .core-workspace {
    grid-template-columns: 1fr;
  }
}
```

3. 产品表格

整改：

```css
.home-product-table-section {
  min-width: 0;
  overflow: hidden;
}

.product-table-shell {
  max-width: 100%;
  min-width: 0;
  overflow-x: auto;
  overscroll-behavior-x: contain;
}

.home-product-table {
  width: max-content;
  min-width: 100%;
}
```

4. 筛选工具条

整改：

```css
.product-table-toolbar {
  min-width: 0;
  flex-wrap: wrap;
}

.table-filters {
  min-width: 0;
  flex-wrap: wrap;
}

.search-box-pc {
  width: clamp(180px, 24vw, 260px);
  min-width: 0;
}
```

5. 价格数字与单位

整改：

```css
.featured-price {
  min-width: 0;
  overflow-wrap: anywhere;
}

.featured-unit {
  white-space: normal;
}

.product-list-card-price,
.price-row {
  min-width: 0;
  flex-wrap: wrap;
}
```

#### 横向滚动验收脚本

建议增加 Playwright 检查：

```ts
const widths = [1600, 1440, 1280, 1024, 768, 390]

for (const width of widths) {
  await page.setViewportSize({ width, height: 900 })
  await page.goto('/home')
  const overflow = await page.evaluate(() => ({
    body: document.body.scrollWidth > document.body.clientWidth,
    html: document.documentElement.scrollWidth > document.documentElement.clientWidth
  }))
  expect(overflow.body || overflow.html).toBe(false)
}
```

额外检查：

- `.product-table-shell.scrollWidth > clientWidth` 允许。
- `document.body.scrollWidth > clientWidth` 不允许。

### 四、style-settings 配置补充

新增或确认以下配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `summary_mode` | `reference` | 摘要区模式，默认参考图经营摘要 |
| `summary_card_min_width` | `220` | 摘要卡最小宽度 |
| `curve_panel_height` | `320` | 主价格曲线默认高度 |
| `curve_show_stat_footer` | `true` | 曲线底部是否展示最高/最低/均价 |
| `page_horizontal_overflow_guard` | `true` | 开启页面级横滚防护样式 |

首期可先实现：

- `curve_panel_height`
- `summary_card_min_width`

其余作为后续治理项写入方案，避免一次性扩大实现面。

### 五、实施步骤

1. 扩展 `HomeSummary` 类型和后端 `/api/home/summary` 返回字段。
2. 修改 `SummarySection.vue`：
   - 将第三卡改为“覆盖品类”。
   - 将第四卡改为“价格异动”。
   - 统一卡片结构和响应式 grid。
3. 新建 `HomePriceCurvePanel.vue`：
   - 从 `TrendAnalysisChart.vue` 中剥离主曲线需求。
   - Home 右侧核心区改为使用主曲线面板。
4. 调整 Home 容器 CSS：
   - 所有关键容器补 `min-width: 0`。
   - 核心区中屏切为上下。
   - 表格横滚限制在 `.product-table-shell`。
5. 更新 `style-settings`：
   - 预览中首行摘要改为产品总数/今日更新/覆盖品类/价格异动。
   - 价格曲线预览改为单个主图表面板。
6. 增加断点验证：
   - 1600 / 1440 / 1280 / 1024 / 768 / 390。
   - 截图确认无重叠、无页面级横向滚动。
7. 更新文档：
   - `README.md`
   - `docs/dev/UI设计说明.md`
   - `docs/dev/开发指南.md`
   - `docs/dev/项目设计文档.md`

### 六、验收标准

必须满足：

- 首行摘要默认展示“产品总数、今日更新、覆盖品类、价格异动”。
- 每张摘要卡都有图标、标题、主数字、辅助说明，结构一致。
- 价格曲线是独立主面板，不再呈现为重点走势卡片样式。
- 点击重点产品卡和产品表格行后，价格曲线产品同步更新。
- 1280 宽度下核心区不挤压、不出现页面级左右滚动。
- 1024 宽度下核心区上下排列，表格内部可以横向滚动，但页面本身不能横向滚动。
- 390 移动宽度下无内容溢出，产品列表使用卡片。
- `npm run build` 通过。
- `mvn test` 通过。

### 七、完成度评分预期

完成本整改后，Home 页面评分可从当前约 8.5-9.0 提升到 9.5+：

| 维度 | 目标 |
|------|------|
| 参考图还原 | 首行摘要与主曲线结构对齐参考图 |
| 工程稳定性 | 无页面级横向滚动，断点行为可验证 |
| 系统一致性 | 继续使用 style-settings、主题 token、分类视觉和字典服务 |
| 业务可用性 | 摘要、重点产品、主曲线、产品列表形成联动工作台 |
