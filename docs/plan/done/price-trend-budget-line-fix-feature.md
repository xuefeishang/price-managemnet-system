# 价格趋势与预算线修复方案

## Context

用户反馈：产品价格已发布后，`Home` 与 `PriceQuery` 页面没有稳定显示价格曲线；`BudgetManagement` 页面已有预算数据，但右侧没有价格曲线；折线图中预算线缺失。铂金只是当前复现样本，修复必须面向所有既有产品和未来新增产品生效。后续排查需要同时覆盖 PC 端与小程序端，避免同一接口语义在多端表现不一致。

当前审计结论：

- `BudgetManagement` 右侧图表没有调用价格走势接口，只基于当前预算值绘制静态预算参考线，因此不会出现价格曲线。
- `PriceQuery` 的趋势图只使用 `currentPrice != null` 的点作为有效点，预算点无法独立触发图表展示。
- 后端 `PriceService.getPriceTrend` 只返回存在正式价格记录的日期；没有价格记录的日期不会返回预算点，预算线无法形成完整时间轴。
- `Home` 小折线使用 `symbol: 'none'`，若产品只有 1 个正式价格点，没有可连接线段，也不会显示点，视觉上像没有曲线。
- 小程序 `price-trend-chart` 同样过滤 `currentPrice == null` 的点，预算线不能独立显示；`mini-trend-chart` 只绘制价格数组，不支持预算线。

## 修复目标

- 任意产品发布后的正式价格在 `Home`、`PriceQuery`、`BudgetManagement` 和小程序产品详情/小趋势图中都能展示走势。
- 年度预算线不依赖价格点；只要任意产品在对应年份有预算，就能显示预算线。
- 只有单个价格点时也要可见。
- PC 与小程序共用 `/api/products/{productId}/price-trend` 的统一语义。
- 不把草稿价格混入正式趋势查询；价格线只使用已发布正式价格。
- 新增产品无需额外前端配置或硬编码映射；只要存在正式价格或年度预算，趋势接口和各端图表自动支持。

## 实施方案

### 1. 数据实证

实施前先用铂金作为复现样本确认数据链路，同时追加通用产品覆盖检查，避免修成单品特例：

- 查询 `product.name = '铂金'` 的产品 ID。
- 查询 `price` 表中该产品的 `effective_date/current_price/budget_price/created_time`。
- 查询 `product_annual_budget` 表中该产品的 `budget_year/budget_price`。
- 查询 `price_draft_batch` 与 `price_draft_item`，确认发布批次状态为 `PUBLISHED`，明细有 `published_price_id`。
- 抽查至少 3 类产品：有价格有预算、只有预算无价格、只有价格无预算。
- 检查未来新增产品链路：新增产品后，只通过 `product.id` 查询正式价格与年度预算，不依赖产品名称、分类名称或前端枚举。

### 2. 后端趋势接口

改造 `PriceService.getPriceTrend`：

- 根据 `startDate/endDate` 或 `days + endDate` 生成完整日期轴。
- 对每个日期返回 `PriceTrendDTO(date, currentPrice, budgetPrice)`。
- `currentPrice` 按传入的 `productId` 从正式 `price` 表查询；当天无价格则为 `null`。
- `budgetPrice` 按传入的 `productId` 从 `product_annual_budget` 查询，按日期年份匹配。
- 同一天多条价格仍保留 `createdTime` 最新记录。
- 跨年范围按不同年份预算切换。
- 禁止在后端为某个产品名称写特殊分支；接口行为只由 `productId`、日期范围、正式价格、年度预算决定。

保持接口路径与 TypeScript 类型不变：

```text
GET /api/products/{productId}/price-trend?days=&startDate=&endDate=
```

返回结构仍为：

```json
[
  {
    "date": "2026-06-13",
    "currentPrice": 123.45,
    "budgetPrice": 120.00
  }
]
```

### 3. PC 端

`PriceQuery`：

- 图表数据源改为完整 `trendData`，不再只使用 `validTrendPoints`。
- 价格统计、最低价、最高价、均价仍只统计 `currentPrice != null` 的价格点。
- 图表显示条件改为：存在价格点或预算点。
- 价格线允许 `null` 点；预算线按 `budgetPrice` 连续展示。
- 普通滚动区间默认传页面 `selectedDate` 作为 `endDate`，保证右侧趋势与当前查询日期一致。

`Home`：

- 小折线支持单价格点显示圆点。
- `generateChartOption` 支持接口返回的 `budgetPrice`，增加预算虚线。
- tooltip 同时显示价格和预算。
- `HomePriceCurvePanel` 同步支持预算线和单点显示。

`ProductDetail`：

- 产品详情页趋势图同样使用完整 `trendData`。
- 只有单个正式价格点时显示圆点。
- 无价格但有预算时展示预算线，不再误判为“走势数据不足”。

`BudgetManagement`：

- 右侧图表调用 `getPriceTrend(productId, 365, endDate, startDate)`。
- `startDate = ${selectedYear}-01-01`，`endDate = ${selectedYear}-12-31`。
- 展示年度价格走势与年度预算线。
- 无价格但有预算时显示预算线；价格和预算都没有时显示空状态。
- 产品切换、分页切换和未来新增产品进入列表后，均使用当前行 `product.id` 拉取趋势，不依赖产品名称。
- 折线图视觉样式与 `PriceQuery` 保持一致：同款网格边距、tooltip、金额轴、价格实线、预算虚线、单点圆点和主题色来源。

`External API`：

- `/api/external/v1/products/{productId}/price-trend` 与 PC/小程序内部接口保持同一查询语义。
- 支持 `days/startDate/endDate`，避免外部调用仍只能获取滚动区间。

### 4. 小程序端

`frontend-uniapp/src/components/price-trend-chart/index.vue`：

- 不再过滤掉 `currentPrice == null` 的预算点。
- 绘图条件改为存在价格点或预算点。
- 价格统计仍只基于价格点。
- 预算线可独立绘制。
- 预算值以趋势接口返回的 `budgetPrice` 为准；接口无预算值时显示为空，不使用当前预算价兜底。

`frontend-uniapp/src/components/mini-trend-chart/index.vue`：

- 支持接口返回的 `budgetPrice`。
- 单个价格点显示圆点。
- 预算线独立绘制，不被价格点数量阻断。

小程序产品详情页：

- 保持现有产品详情传入预算价。
- 优先使用趋势接口返回的年度预算点，避免历史产品预算字段与年度预算不一致。

## 测试与验收

后端测试：

- 只有预算无价格时，趋势接口返回预算点。
- 只有一天价格时，趋势接口返回该价格点并返回对应预算。
- 多条同日价格时，只保留最新创建记录。
- 跨年日期范围内预算按年份切换。
- 任意已发布产品价格能被趋势接口命中。
- 新增产品在创建预算或发布价格后，不需要修改前端配置即可被趋势接口和图表识别。
- 不同产品之间价格与预算不能串线；A 产品预算不得出现在 B 产品趋势中。
- 外部 API 与内部 API 对相同产品和日期范围返回一致的趋势结构。

PC 验收：

- `PriceQuery` 选中任意有数据产品后，单价格点可见，预算线可见。
- `Home` 任意产品只有单日价格时，小图可见点；多日价格时形成线。
- `BudgetManagement` 选中任意产品后，右侧显示该产品年度价格走势和预算线。
- `BudgetManagement` 右侧折线图样式与 `PriceQuery` 右侧趋势图保持一致。
- `ProductDetail` 在单日价格或预算-only 场景下也能展示趋势图。
- 新建一个测试产品并录入年度预算/发布价格后，三个 PC 页面无需改代码即可展示对应趋势。

小程序验收：

- 产品详情趋势图：无价格但有预算时显示预算线。
- 产品详情趋势图：单日价格显示价格点。
- 小趋势图：价格线和预算线互不阻断。
- 新增产品同步出现在小程序后，趋势图按接口数据展示，不需要小程序端维护产品白名单。

执行命令：

```bash
mvn test
cd frontend && npm run build
```

小程序端如无统一构建脚本，至少对相关 Vue/TypeScript 文件做静态检查，并在微信开发者工具中手工验证产品详情趋势图。

## Assumptions

- 草稿价格不参与 `Home`、`PriceQuery`、`BudgetManagement` 的正式价格趋势展示。
- 预算线以 `product_annual_budget` 为准，不依赖历史 `price.budget_price` 快照。
- PC 与小程序端共用同一个后端趋势接口语义。
- 铂金仅作为当前问题复现与回归样本，不作为任何代码分支或配置条件。
- 本方案仅规划价格趋势与预算线修复；不包含首页产品分页、自适应布局或通知体系调整。
