# 首页功能恢复与 Style Settings 对齐方案

## Context

`style-settings` 已经具备三组会影响首页的管理能力：

1. **首页体验**：`home_layout` 控制卡片列数、重点产品数、趋势图、预警区；`home_widget` 控制经营摘要、核心指标、趋势分析、产品列表、风险预警的启用与顺序。
2. **分类视觉**：`category_visual_config` 控制每个产品分类的 icon、主色、浅底、边框、趋势图色、光晕等。
3. **全局样式**：颜色、字体、布局 token 通过 CSS 变量影响全站。

当前真实首页没有完整消费这些配置，并且部分首页能力已经出现功能回归：价格涨跌提醒、经营摘要、趋势分析、样式设置中预制的首页提示/组件，都没有在首页稳定展示。配置页像“工作台”，首页像“另一套实现”。

本方案目标是先恢复首页业务可见能力，再让首页成为 `style-settings` 的真实落地页：配置什么，首页就展示什么；分类视觉怎么配，首页产品卡片就怎么呈现。

## 评分目标

- 当前方案成熟度目标：**9.7 / 10**
- 实施优先级：先恢复失效首页功能，再做配置对齐，最后同步预览
- 核心验收标准：
  - 价格涨跌提醒 / 风险预警在首页重新可见
  - 经营摘要、趋势分析、样式设置预制组件在首页真实展示
  - `home_widget` 能控制首页区块显隐与顺序
  - 首页默认按分类分组展示产品
  - 分类筛选无结果不回退全部产品
  - 重点产品、普通产品、移动端产品卡片共用分类视觉规则

## 当前评估

### 0. 首页核心能力已出现功能回归

这部分按 P0 处理，优先级高于视觉优化和组件化拆分。

| 失效能力 | 现象 | 主要排查点 | 修复目标 |
|----------|------|------------|----------|
| 价格涨跌提醒 / 风险预警 | 首页没有展示价格上涨、下跌、异常提醒 | `Home.vue` 是否消费 `dashboard.alerts` 或 `/api/home/alerts`；`risk_alerts` / `price_alerts` key 是否不一致；`home_layout.showAlerts` 是否误拦截 | 首页恢复提醒区，空数据展示明确空状态 |
| 经营摘要 | 首页没有展示经营概要 | `Home.vue` 是否使用 `dashboard.summary` 或 `/api/home/summary`；`summary_stats` / `core_metrics` 是否启用 | 首页展示总产品、今日更新、上涨、下跌、平均涨跌幅等摘要 |
| 趋势分析 | 首页没有稳定展示趋势分析 | `Home.vue` 是否使用 `dashboard.trendAnalysis` 或 `/api/home/trend`；`trend_chart` 与 `showTrendChart` 是否同时生效 | 首页展示趋势区，时间范围来自 `chart_range` |
| 样式设置预制提示 / 组件 | 样式设置中预设的首页组件没有落到首页 | `enabledWidgets` / `getWidgetConfig()` 未成为首页渲染主入口 | 首页区块显隐、排序、标题与配置一致 |
| 分类筛选 | 分类功能看似无效 | 无结果时回退全部产品 | 选中分类无产品时展示空状态，不回退 |

P0 判断原则：

- 后端已有接口能返回的数据，首页必须先恢复展示。
- 如果真实接口无数据，首页也要展示空状态，不能让用户误判功能消失。
- 不用等组件化完成后再恢复提醒、摘要、趋势；先用最小改动接回，再重构。

### 1. 首页体验配置未完全落地

相关文件：

- `frontend/src/components/style-settings/HomeExperiencePanel.vue`
- `frontend/src/composables/useHomePreviewState.ts`
- `frontend/src/composables/useHomeConfig.ts`
- `frontend/src/views/Home.vue`
- `frontend/src/api/home.ts`
- `backend/src/main/java/com/pricemanagement/controller/HomeController.java`
- `backend/src/main/java/com/pricemanagement/service/HomeDashboardService.java`

现状：

- `HomeExperiencePanel` 可以配置 PC/移动端列数、重点产品数量、趋势图、预警区、首页组件启用/排序。
- `Home.vue` 只读取了 `layoutConfig.cardColumns`、`cardColumnsMobile`、`featuredProductCount`。
- `Home.vue` 没有按 `enabledWidgets` / `getWidgetConfig()` 控制区块显隐和顺序。
- `frontend/src/api/home.ts` 与后端 `/api/home/dashboard` 已提供 `summary`、`alerts`、`featuredProducts`、`trendAnalysis`，但 `Home.vue` 当前没有使用该聚合接口。

影响：

- “首页组件”在样式设置里可配置，但真实首页响应有限。
- 经营摘要、趋势分析、风险预警缺少正式展示。
- 首页数据请求分散，容易与后端仪表盘逻辑不一致。

### 2. 分类视觉与产品卡片匹配不完整

相关文件：

- `frontend/src/components/style-settings/CategoryVisualPanel.vue`
- `frontend/src/composables/useCategoryPreviewState.ts`
- `frontend/src/composables/useCategoryVisual.ts`
- `frontend/src/components/style-settings/preview/CategoryPreview.vue`
- `frontend/src/views/Home.vue`

现状：

- 普通产品列表卡片部分使用了 `getCategoryCardStyle()`、`getCategoryVisual()` 和 `has-category`。
- 重点关注指标区没有完整使用分类 icon、分类边框、分类背景、分类主色价格、分类趋势线。
- 移动端重点产品卡片没有完整套用分类视觉。
- 趋势小图已使用分类 `chartLineColor`，但卡片整体视觉不统一。

影响：

- `style-settings -> 分类视觉` 修改后，首页只有部分区域响应。
- 重点产品卡片与分类视觉预览不一致，削弱配置可信度。

### 3. 分类筛选逻辑存在误导

相关文件：

- `frontend/src/views/Home.vue`
- `frontend/src/components/CategoryFilterPanel.vue`

现状：

`Home.vue` 的 `filteredProducts` 在有分类筛选但无结果时会回退全部产品：

```ts
if (filtered.length === 0 && !searchQueryDebounced.value) return active
```

影响：

- 用户认为分类筛选没有生效。
- 无法准确区分“该分类无产品”和“未选择分类”。

### 4. 产品列表默认不是分类展示

现状：

- 首页产品列表默认平铺。
- 分类筛选只是一个下拉面板，不是默认信息架构。
- 用户期望“产品列表默认就是分类展示”。

目标：

- 默认按分类分组展示产品。
- 每个分类分组使用分类视觉作为标题和卡片视觉。
- 分类筛选只控制分组显隐。
- 搜索在分组内过滤产品，不破坏分类结构。

## 首页目标信息架构

桌面端目标顺序由 `home_widget.order` 决定，默认顺序如下：

```text
首页 Header
  日期选择 / 刷新 / 价格维护

经营摘要 summary_stats
  产品总数 | 今日更新 | 上涨 | 下跌 | 平均涨跌幅

核心指标 core_metrics
  重点产品卡片（分类视觉 + 趋势小图 + 涨跌标签）

趋势分析 trend_chart
  聚合趋势线 / 重点产品趋势线 / chart_range 时间范围

分类产品列表 product_list
  分类筛选工具栏
  分类分组 A
    分类标题（icon + 名称 + 数量 + 分类色条）
    产品卡片网格
  分类分组 B
    产品卡片网格
  未分类
    产品卡片网格

风险预警 risk_alerts
  单日涨跌 / 连续涨跌预警列表
```

移动端目标：

```text
顶部栏
日期/刷新
横向摘要卡
重点产品横向滑动
趋势分析折叠/紧凑图
分类 chips
按分类分组的产品列表
风险预警
底部 Tab
```

## 分阶段实施

### Phase 0：首页失效功能恢复（P0，立即执行）

范围：

- 恢复价格涨跌提醒 / 风险预警展示。
- 恢复经营摘要展示。
- 恢复趋势分析展示。
- 恢复样式设置中预制首页组件的显隐、顺序、提示文案落地。
- 统一或兼容 `risk_alerts` / `price_alerts` 组件 key。

实施要点：

1. 在现有 `Home.vue` 中先接入 `getHomeDashboard()`。
   - 优先使用 `dashboard.summary`、`dashboard.alerts`、`dashboard.trendAnalysis`。
   - 若聚合接口缺字段，再临时降级调用 `getHomeSummary()`、`getPriceAlerts()`、`getTrendAnalysis()`。
2. 用 `useHomeConfig()` 的配置控制首页区块。
   - `summary_stats` / `core_metrics` 控制摘要和核心指标。
   - `trend_chart` + `layoutConfig.showTrendChart` 控制趋势分析。
   - `risk_alerts` + `layoutConfig.showAlerts` 控制涨跌提醒。
   - `product_list` 控制产品列表。
3. 兼容旧 key。
   - 读取时 `risk_alerts` 和 `price_alerts` 都识别为提醒组件。
   - 保存和文档统一为 `risk_alerts`。
4. 所有恢复区块都必须有空状态。
   - 无提醒：展示“暂无风险预警”语义。
   - 无趋势：展示趋势空状态。
   - 无摘要：展示加载失败或暂无数据状态。

完成后收益：

- 用户马上能重新看到价格涨跌提醒、经营概要、趋势分析。
- 后续 Phase 1/2 的重构不会挡住当前首页可用性。
- “样式设置里有、首页没有”的核心断裂先被修复。

不包含：

- 大规模视觉重做。
- 卡片组件完全抽象。
- 后端 DTO 增强，除非接口完全无法支撑当前展示。

### Phase 1：真实首页配置落地（必须）

范围：

- 首页区块按 `home_widget` 显隐和排序。
- 在 Phase 0 恢复基础上，把经营摘要、趋势分析、风险预警纳入统一 section renderer。
- 产品列表默认分类分组。
- 分类筛选逻辑修复。

完成后收益：

- 用户能直接看到 `style-settings -> 首页体验` 的配置生效。
- 首页信息架构可用。
- 分类功能恢复可信。

不包含：

- 大规模视觉重做。
- 复杂图表交互增强。
- style-settings 预览完全复用真实首页组件。

### Phase 2：分类视觉统一与组件化（必须）

范围：

- 抽出 `CategoryProductCard.vue`。
- 重点产品、普通产品、移动端产品项统一视觉 token。
- 分类分组标题使用分类视觉。
- 趋势小图使用分类 `chartLineColor` / `chartAreaColor`。

完成后收益：

- 分类视觉配置在首页全量一致生效。
- 后续卡片样式维护成本下降。

### Phase 3：style-settings 预览对齐（推荐）

范围：

- `HomePreview.vue` 按 widget 顺序展示模拟首页。
- `CategoryPreview.vue` 与真实卡片共享 token 或共享轻量展示组件。
- 预览与真实首页的语义一致。

完成后收益：

- 配置页右侧预览可信度提升。
- 后续验收更直观。

### Phase 4：数据聚合优化（可选）

范围：

- 评估 `/api/home/dashboard` 是否需要补充分类字段。
- 若 DTO 缺字段，再做后端增强。
- 若现有 DTO 足够，则保持前端多接口组合，避免后端变更。

完成后收益：

- 降低首页请求数。
- 数据口径统一。

## API / DTO 对齐评估

### 已存在接口

| 功能 | 后端 | 前端 | 当前状态 | 结论 |
|------|------|------|----------|------|
| 首页聚合数据 | `GET /api/home/dashboard` | `getHomeDashboard` | 已存在，Home.vue 未使用 | Phase 1 优先接入 |
| 首页摘要 | `GET /api/home/summary` | `getHomeSummary` | 已存在 | 可单独刷新 |
| 首页预警 | `GET /api/home/alerts` | `getPriceAlerts` | 已存在 | 可单独刷新 |
| 趋势分析 | `GET /api/home/trend` | `getTrendAnalysis` | 已存在 | 替代硬编码趋势逻辑 |
| 产品列表 | `GET /api/products` | `getProducts` | 已存在 | 分类产品列表仍需要 |
| 分类列表 | `GET /api/categories` | `getCategories` | 已存在 | 分组与视觉映射需要 |

P0 结论：

- `Home.vue` 未使用聚合接口是当前首页功能失效的首要风险点。
- 价格涨跌提醒优先从 `dashboard.alerts` 恢复；如果聚合接口暂时没有数据，再调用 `getPriceAlerts`。
- 趋势分析优先从 `dashboard.trendAnalysis` 恢复；如果聚合接口暂时没有数据，再调用 `getTrendAnalysis`。
- 经营摘要优先从 `dashboard.summary` 恢复；如果聚合接口暂时没有数据，再调用 `getHomeSummary`。

### DTO 缺口检查

| DTO | 当前字段 | 首页需要 | 是否缺口 | 处理建议 |
|-----|----------|----------|----------|----------|
| `HomeSummaryDTO` | totalProducts, priceUpdatedToday, risingCount, fallingCount, flatCount, avgPriceChange | 经营摘要 | 无明显缺口 | 直接使用 |
| `ProductMetricDTO` | productId, productName, specs, currentPrice, priceDirection, formattedChange, currencySymbol, unit | 核心指标 | 缺 `categoryId/categoryCode/categoryName` | Phase 1 可用产品列表补齐，Phase 4 再评估后端增强 |
| `TrendAnalysisDTO` | days, dates, productTrends, avgTrend, rangeLabel | 趋势分析 | 缺产品名称映射 | Phase 1 可只展示 avgTrend；多线趋势用 products 补名称 |
| `PriceAlertDTO` | productId, productName, productSpecs, alertType, alertMessage, severity, currentValue, threshold, changePercent | 风险预警 | 缺分类视觉字段 | Phase 1 可通过 productId 匹配产品列表补分类 |
| `Product` | categoryId, category | 分类分组/视觉 | 可用但需兼容两种来源 | 统一 helper：`product.categoryId || product.category?.id` |

后端增强建议：

- Phase 1 不强制改后端，避免扩大范围。
- Phase 4 可为 `ProductMetricDTO` / `PriceAlertDTO` 增加：
  - `categoryId`
  - `categoryCode`
  - `categoryName`
- 若增强 DTO，必须同步 `frontend/src/api/home.ts` 类型。

## 字典 / 配置一致性

### 首页组件 key

| 组件 | 推荐 key | 当前风险 |
|------|----------|----------|
| 经营摘要 | `summary_stats` | 前后端一致 |
| 核心指标 | `core_metrics` | 前后端一致 |
| 趋势分析 | `trend_chart` | 前后端一致 |
| 产品列表 | `product_list` | 前后端一致 |
| 风险预警 | `risk_alerts` | 前端旧默认存在 `price_alerts` |

处理策略：

- 标准 key 使用 `risk_alerts`。
- 读取时兼容 `price_alerts`。
- 保存时优先保存已有后端字典项 `risk_alerts`。
- 文档统一为 `risk_alerts`。

### 图表范围

当前 `Home.vue` 硬编码：

- 7日
- 30日
- 90日

应改为读取 `chart_range` 字典：

- `dictKey`：范围 key
- `dictValue`：显示标签
- `extraValue`：天数

## 前端组件契约

### `useHomeDashboard.ts`

新增文件：

- `frontend/src/composables/useHomeDashboard.ts`

职责：

- 聚合首页真实数据。
- 隔离 `Home.vue` 中的数据请求和派生逻辑。
- 输出分类分组结构。

建议接口：

```ts
export interface HomeCategoryGroup {
  category: ProductCategory | null
  categoryId?: number
  categoryCode?: string
  categoryName: string
  visual: CategoryVisualConfig
  products: Product[]
}

export function useHomeDashboard() {
  return {
    loading,
    error,
    selectedDate,
    trendDays,
    dashboard,
    summary,
    alerts,
    products,
    categoryGroups,
    filteredCategoryGroups,
    selectedCategoryIds,
    searchQuery,
    priceMap,
    previousPriceMap,
    chartOptionsMap,
    loadData,
    refreshTrend,
    clearCategoryFilter,
    setCategoryFilter
  }
}
```

### `CategoryProductCard.vue`

新增文件：

- `frontend/src/components/home/CategoryProductCard.vue`

输入：

```ts
defineProps<{
  product: Product
  price?: Price | null
  previousPrice?: Price | null
  chartOption?: any
  variant?: 'compact' | 'featured' | 'list'
  showTrendChart?: boolean
}>()
```

视觉规则：

- `borderColor`: `--category-border`
- 背景：`--category-surface` 与 `--bg-card` 混合
- 标题/价格强调：`--category-primary`
- icon：`getCategoryVisual(categoryId).icon`
- hover glow：`--category-glow`
- 小趋势图：`--category-chart-line` / `--category-chart-area`

### `CategoryProductSection.vue`

职责：

- 展示分类筛选栏。
- 默认按分类分组展示。
- 管理分组空状态。

关键行为：

- 未选择分类：展示所有分类。
- 选择分类：只展示选中分类。
- 搜索：保留分类分组，仅过滤组内产品。
- 选中分类但无产品：展示“该分类暂无产品”，不回退全部产品。

## UI 验收标准

### 经营摘要

- 位于首页顶部或按 `home_widget.order` 位置展示。
- 5 个指标一屏可扫读。
- 数字使用 `--font-mono`。
- 上涨/下跌颜色使用全局价格颜色变量。
- 移动端可横向滚动或 2 列排列，不挤压文字。

### 核心指标

- 重点产品卡片视觉明显强于普通卡，但不脱离分类视觉。
- 每张卡必须包含：
  - 分类 icon
  - 产品名称
  - 规格
  - 当前价格
  - 涨跌标签
  - 可选趋势小图
- 同一分类的重点卡和普通卡视觉基因一致。

### 趋势分析

- 仅在 `showTrendChart=true` 且 `trend_chart.enabled=true` 时展示。
- 时间范围来自 `chart_range` 字典。
- 至少展示平均趋势线。
- 图表空数据时显示轻量空状态，而不是空白区域。

### 分类产品列表

- 默认按分类分组。
- 分类标题包含 icon、分类名、产品数量。
- 分类标题使用分类主色或色条。
- 分组内产品网格使用 `cardColumns`。
- 未分类产品排在最后。
- 搜索后仍保留分组结构。

### 风险预警

- 仅在 `showAlerts=true` 且 `risk_alerts.enabled=true` 时展示。
- severity 映射为 info / warning / danger 的视觉状态。
- 无预警时展示“暂无风险预警”，不隐藏整个区块造成误解。

## 实现方案

### Phase 1 详细步骤：真实首页配置落地

0. 先完成 Phase 0 的失效功能恢复。
   - 首页必须先能看到涨跌提醒、经营摘要、趋势分析。
   - 不能把 P0 修复延后到组件化完成之后。
1. 在 `useHomeConfig.ts` 中统一 widget key：
   - 标准化 `risk_alerts`
   - 兼容 `price_alerts`
   - 暴露 `isWidgetEnabled(key)` / `orderedEnabledWidgets`
2. 新增 `useHomeDashboard.ts`：
   - 接入 `getHomeDashboard`
   - 继续加载产品、分类、价格映射
   - 构造 `categoryGroups`
   - 构造 `filteredCategoryGroups`
3. 改造 `Home.vue`：
   - 移除大部分数据请求和派生逻辑
   - 按 `orderedEnabledWidgets` 渲染区块
   - 保留页面 Header、日期、刷新、价格维护入口
4. 新增/复用区块组件：
   - `SummaryStatsSection.vue`
   - `CoreMetricsSection.vue`
   - `TrendAnalysisSection.vue`
   - `CategoryProductSection.vue`
   - `RiskAlertsSection.vue`
5. 修复分类筛选：
   - 移除无结果回退全部产品逻辑
   - 空分类显示空状态

### Phase 2 详细步骤：分类视觉统一

1. 新增 `CategoryProductCard.vue`。
2. 把普通产品卡片迁入该组件。
3. 把重点产品卡片迁入该组件，使用 `variant="featured"`。
4. 移动端产品项使用 `variant="list"` 或同组件响应式样式。
5. 把 `getProductCategoryId()` 统一放入工具函数或组件内部。
6. 确保分类视觉缓存：
   - 首页加载分类后调用 `registerCategoryCodes`
   - 保存分类视觉后清理缓存

### Phase 3 详细步骤：预览对齐

1. 更新 `HomePreview.vue`：
   - 读取 `useHomePreviewState().enabledWidgets`
   - 按顺序展示模拟区块
   - 展示经营摘要、趋势分析、风险预警的开关状态
2. 更新 `CategoryPreview.vue`：
   - 与 `CategoryProductCard` 共享 token 规则
   - 微调助手变动时预览立即变化
3. 验证 `style-settings?section=home/category` 右侧预览和真实首页语义一致。

### Phase 4 详细步骤：后端增强评估

仅当 Phase 1 发现前端补齐分类信息成本过高时执行：

1. `ProductMetricDTO` 增加分类字段。
2. `PriceAlertDTO` 增加分类字段。
3. `HomeDashboardService` 在构造 DTO 时补齐分类信息。
4. 同步 `frontend/src/api/home.ts` 类型。
5. 更新 `docs/dev/项目设计文档.md` API DTO 说明。

## 数据流

```text
style-settings
  ├─ home_layout / home_widget -> useHomeConfig -> Home.vue section renderer
  ├─ category_visual_config -> useCategoryVisual -> CategoryProductCard
  └─ style config CSS vars -> theme/useTheme -> Home.vue global tokens

Home.vue
  ├─ useHomeConfig
  ├─ useHomeDashboard
  ├─ SummaryStatsSection
  ├─ CoreMetricsSection
  ├─ TrendAnalysisSection
  ├─ CategoryProductSection
  └─ RiskAlertsSection
```

## 风险与边界

### 风险1：一次性改造 Home.vue 过大

控制方式：

- 先抽 `useHomeDashboard.ts`，不立即删除旧逻辑。
- 逐个 section 替换。
- 每替换一个区块跑一次构建。

### 风险2：`/api/home/dashboard` 缺分类信息

控制方式：

- Phase 1 用产品列表补分类。
- Phase 4 再考虑 DTO 增强。

### 风险3：`risk_alerts` / `price_alerts` key 不一致

控制方式：

- 读取兼容两个 key。
- 保存使用后端已有 `risk_alerts`。
- 文档统一。

### 风险4：分类视觉缓存导致首页更新滞后

控制方式：

- 分类视觉保存后继续调用 `clearCategoryVisualCache()`。
- 首页加载分类时调用 `registerCategoryCodes()`。
- 首页刷新时重新解析分类视觉。

## 前后端一致性检查

### API 路径

| 功能 | 后端 | 前端 | 状态 |
|------|------|------|------|
| 首页聚合数据 | `GET /api/home/dashboard` | `frontend/src/api/home.ts#getHomeDashboard` | 已存在，待接入 |
| 首页摘要 | `GET /api/home/summary` | `getHomeSummary` | 已存在 |
| 首页预警 | `GET /api/home/alerts` | `getPriceAlerts` | 已存在 |
| 趋势分析 | `GET /api/home/trend` | `getTrendAnalysis` | 已存在 |
| 产品列表 | `GET /api/products` | `getProducts` | 已存在 |
| 分类列表 | `GET /api/categories` | `getCategories` | 已存在 |

### 数据结构一致性

- `Product.categoryId` 与 `Product.category.id` 均可能存在，前端统一使用 `product.categoryId || product.category?.id`。
- `Product.category.code` 用于注册 `categoryId -> categoryCode` 映射。
- `category_visual_config.extraValue` 通过 `categoryCode` 或 `categoryId` 都能匹配。
- 若后端增强 DTO，必须同步 TypeScript 接口。

### 数据库一致性

本方案默认不涉及数据库表结构变更。

需要检查初始化数据：

- `DataInitializer.java` 中 `home_widget` 是否包含标准 key。
- `init.sql` 中 `home_widget` / `home_layout` / `chart_range` 是否与代码一致。
- 如只调整字典初始化数据，不需要新增表或字段，但需要同步数据字典文档。

## 关键参考文件

- `frontend/src/views/Home.vue`
- `frontend/src/api/home.ts`
- `frontend/src/composables/useHomeConfig.ts`
- `frontend/src/composables/useHomePreviewState.ts`
- `frontend/src/composables/useCategoryVisual.ts`
- `frontend/src/components/CategoryFilterPanel.vue`
- `frontend/src/components/home/MetricsCardGrid.vue`
- `frontend/src/components/home/SummarySection.vue`
- `frontend/src/components/home/RiskAlertsPanel.vue`
- `frontend/src/components/home/TrendAnalysisChart.vue`
- `frontend/src/components/style-settings/HomeExperiencePanel.vue`
- `frontend/src/components/style-settings/CategoryVisualPanel.vue`
- `frontend/src/components/style-settings/preview/HomePreview.vue`
- `frontend/src/components/style-settings/preview/CategoryPreview.vue`
- `backend/src/main/java/com/pricemanagement/controller/HomeController.java`
- `backend/src/main/java/com/pricemanagement/service/HomeDashboardService.java`
- `backend/src/main/java/com/pricemanagement/config/DataInitializer.java`
- `backend/src/main/resources/init.sql`

## 执行记录

- 已执行 Phase 0：首页重新接入 `/api/home/dashboard`，并在聚合接口失败时降级使用 `/api/home/summary`、`/api/home/alerts`、`/api/home/trend`。
- 已恢复经营摘要、趋势分析、价格涨跌/风险预警在首页的真实展示。
- 已将 `price_alerts` 兼容到标准 `risk_alerts`，并统一预览默认组件 key。
- 已让首页区块按 `home_widget` 启用状态和排序渲染。
- 已修复分类筛选无结果回退全部产品的问题，并将首页产品列表默认改为分类分组展示。
- 已让重点产品卡片和移动端重点产品卡片使用分类视觉样式。
- 已为风险预警组件补充空状态，避免无提醒时整块消失。
- 已将趋势分析升级为管理视图：整体平均趋势作为参考，重点产品按 1 对 1 卡片展示价格走势，并在折线图标注最新价、最高价、最低价。
- 最新决策：样式设置中的“趋势分析”更名为“重点走势”，由 `trend_chart` 控制显隐和排序；首页不展示大折线图，只展示最多 4 个小折线图卡片。重点走势产品集合必须动态跟随当前重点关注指标，重点关注指标切换为其他产品时，重点走势同步切换。
- 已新增 `style-settings?section=home-sort` 首页排序页签，用于控制首页组件上下顺序与首页产品顺序。
- 已让 Home 页面按产品 `sortOrder` 稳定展示产品列表、分类分组内产品、重点关注指标与重点走势；首页产品顺序保存后通过 `product-sort-updated` 事件刷新首页数据。

## Verification

### 构建验证

```bash
cd frontend
npm run build
```

预期：

- `vue-tsc` 通过
- `vite build` 通过
- 允许保留现有 chunk size / dynamic import 警告

### 功能验证

0. 首页 P0 恢复
   - 首页能看到价格涨跌提醒 / 风险预警区块。
   - 首页能看到经营摘要区块。
   - 首页能看到趋势分析区块。
   - 无提醒、无趋势、无摘要时，有明确空状态，不是整块消失。
   - `risk_alerts` 和历史 `price_alerts` 不会导致提醒组件丢失。

1. `style-settings?section=home`
   - 关闭“经营摘要”，首页不展示经营摘要。
   - 关闭“趋势分析”，首页不展示趋势分析。
   - 关闭“显示趋势图”，首页趋势图相关 UI 隐藏。
   - 关闭“显示预警区”，首页风险预警隐藏或显示配置关闭态。
   - 调整组件顺序，首页区块顺序同步变化。

1.1. `style-settings?section=home-sort`
   - 调整首页组件顺序后，右侧首页缩略图顺序同步变化；点击顶部保存配置后真实首页同步变化。
   - 调整首页产品顺序并保存后，首页产品列表与分类分组内顺序同步变化。
   - 重点关注指标按“首页展示”产品的当前顺序取前 N 个，重点走势同步跟随前 4 个重点产品。

2. `style-settings?section=category`
   - 修改某分类主色、浅底、边框、趋势线。
   - 首页对应分类分组标题、产品卡片、趋势线同步使用新视觉。
   - 重点产品卡片与普通产品卡片视觉一致。

3. 首页产品列表
   - 默认按分类分组展示。
   - 选择单个分类，只展示该分类分组。
   - 选择无产品分类时，显示空状态，不回退全部产品。
   - 搜索产品时，保留分类分组结构，仅过滤组内产品。
   - 未分类产品出现在“未分类”分组。

4. 移动端首页
   - 分类分组可扫描。
   - 重点产品与普通产品分类视觉一致。
   - 分类筛选不遮挡主要操作。
   - 底部导航不遮挡列表内容。

### 视觉验收

- 首页不出现大块空白区。
- 卡片文字在 320px 宽移动端不溢出。
- 分类色彩不破坏价格涨跌色语义。
- 同一分类在重点卡、普通卡、移动端列表中的 icon 和主色一致。
- 经营摘要、趋势分析、风险预警各自有空状态。

### 回归验证

- 产品详情跳转正常。
- 价格维护入口正常。
- 日期切换后摘要、趋势、预警、产品价格同步刷新。
- 字典缓存刷新后分类视觉仍能按 `categoryCode` 匹配。
- 用户无编辑权限时不展示价格维护入口。

## 完成定义

满足以下条件才算完成：

- 首页已恢复价格涨跌提醒 / 风险预警、经营摘要、趋势分析展示。
- 样式设置中的首页预制组件能在真实首页落地，而不只停留在配置页。
- `Home.vue` 不再手写区块固定顺序，而是按 `home_widget` 渲染。
- 产品列表默认分类分组。
- 分类筛选不再无结果回退全部产品。
- 所有首页产品卡片统一走分类视觉 token。
- 首页真实展示经营摘要、趋势分析、风险预警。
- `style-settings` 首页预览与真实首页语义一致。
- 构建通过，文档同步。
