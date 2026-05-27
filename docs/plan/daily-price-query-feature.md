# 日常价格查询与导出页面实施方案

## Context

当前系统已有“价格维护”页面，主要面向有维护权限的用户进行日常价格录入、查看与趋势分析。现在需要新增一个面向所有普通用户的只读查询页面，页面样式参考 `docs/UI/ProductsList.png`，用于日常价格查询，并将“导出数据”作为核心能力。

新增页面应解决以下问题：

- 普通用户可以快速按日期、产品名称、分类等条件查询日常价格。
- 普通用户可以导出当前查询条件下的价格数据，便于日常汇报、线下分析和留档。
- 页面视觉和交互与参考图保持一致：左侧为产品价格列表，右侧为选中产品趋势与价格摘要。
- 不影响现有“价格维护”页面的编辑流程，查询页保持只读。

## 实现方案

### 页面定位

新增页面建议命名为“价格查询”或“日常价格查询”，路由建议为：

```text
/price-query
```

菜单位置建议放在“产品管理”下：

```text
产品管理
  ├── 产品列表
  ├── 价格维护
  └── 价格查询
```

访问范围：

- `ADMIN`
- `EDITOR`
- `VIEWER`

页面仅提供查询、查看趋势、导出，不提供新增、修改、删除价格能力。

### 前端页面设计

参考 `docs/UI/ProductsList.png`，采用左右分栏布局。

左侧“产品价格列表”区域：

- 顶部工具栏：
  - 日期选择器，默认取业务默认查询日期。
  - 产品名称 / 关键字搜索。
  - 分类筛选。
  - 每页条数选择。
  - 导出按钮，视觉上作为主操作。
- 表格字段建议：
  - 产品名称
  - 类别
  - 规格
  - 当日售价
  - 昨日售价
  - 涨跌额
  - 涨跌幅
  - 单位
  - 币种
- 行点击后选中产品，并刷新右侧趋势图。
- 分页支持页码、上一页/下一页、跳转页。

右侧“价格趋势”区域：

- 展示选中产品名称、规格、当前价格。
- 提供 `7日 / 30日 / 90日 / 年度` 时间范围切换。
- 使用折线图展示价格走势，优先复用现有 ECharts 配置与安全自适应逻辑。
- 展示统计卡片：
  - 最低价
  - 最高价
  - 平均价
  - 预算价
  - 最新价
- 展示价格变化说明：
  - 较昨日
  - 较上周
  - 较上月

响应式要求：

- 桌面端保持左右分栏。
- 窄屏下改为上下布局：筛选区、价格列表、趋势区依次排列。
- 表格在移动端可横向滚动，避免字段挤压和文字重叠。

### 前端实现要点

建议新增：

```text
frontend/src/views/PriceQuery.vue
frontend/src/api/priceQuery.ts
```

需要复用或参考：

- `frontend/src/api/products.ts` 中已有产品、按日期价格、价格趋势接口封装。
- `frontend/src/composables/useDict.ts` 获取分类、币种、单位等显示名称，避免硬编码中文标签。
- `frontend/src/composables/useSafeChartAutoresize.ts` 保持图表响应式，并避免最小化窗口时的异常重绘问题。
- `frontend/src/router/index.ts` 增加新路由。
- 现有菜单初始化逻辑增加“价格查询”菜单。

导出交互：

- 点击“导出”时导出当前筛选条件下的全部匹配数据，而不是仅导出当前页。
- 导出前端传参与列表查询保持一致，包括日期、关键字、分类、状态等。
- 文件名建议：

```text
日常价格查询_yyyy-MM-dd.xlsx
```

### 后端 API 设计

现有接口已经具备部分查询能力：

- `GET /api/products`
- `GET /api/prices/by-date`
- `GET /api/prices/by-date-with-stats`
- `GET /api/products/{productId}/price-trend`

为了保证分页查询和导出结果一致，建议新增面向查询页的聚合接口：

```text
GET /api/price-query
GET /api/price-query/export
```

实现边界：

- Controller 层新增 `PriceQueryController`，只负责参数接收、权限校验、文件响应与统一返回。
- Service 层新增 `PriceQueryService`，负责组装产品信息、指定日期价格、昨日价格、预算价、均价和涨跌数据。
- Repository 层优先复用现有 `ProductRepository`、`PriceRepository` 查询能力；仅当现有查询无法支持分页聚合或导出全量筛选结果时，再新增明确命名的查询方法。
- 趋势图继续复用现有 `GET /api/products/{productId}/price-trend`，首版不重复新增趋势接口。
- 导出接口必须调用与列表接口相同的查询条件构建逻辑，避免页面结果和导出结果不一致。

`GET /api/price-query` 参数建议：

| 参数 | 类型 | 说明 |
|------|------|------|
| `date` | `LocalDate` | 查询日期 |
| `keyword` | `String` | 产品名称 / 规格关键字 |
| `categoryId` | `Long` | 分类 ID |
| `status` | `String` | 产品状态，默认只查启用 |
| `page` | `int` | 页码 |
| `size` | `int` | 每页条数 |
| `sortBy` | `String` | 排序字段 |
| `sortDirection` | `String` | 排序方向 |

返回 DTO 建议：

```text
PriceQueryRowDTO
```

字段建议：

- `productId`
- `productName`
- `categoryId`
- `categoryName`
- `specification`
- `unit`
- `currency`
- `effectiveDate`
- `currentPrice`
- `yesterdayPrice`
- `changeAmount`
- `changePercent`
- `budgetPrice`
- `monthlyAveragePrice`
- `latestPrice`
- `hasPrice`

字段约定：

| 字段 | 约定 |
|------|------|
| `currentPrice` | 查询日期当天价格；当天无报价时返回 `null` |
| `yesterdayPrice` | 查询日期前一日价格；无报价时返回 `null` |
| `changeAmount` | `currentPrice - yesterdayPrice`；任一值为空时返回 `null` |
| `changePercent` | 涨跌幅百分比；昨日价格为空或为 0 时返回 `null` |
| `budgetPrice` | 产品预算价或现有预算价来源；无配置时返回 `null` |
| `monthlyAveragePrice` | 查询日期所在月份均价；无有效价格时返回 `null` |
| `latestPrice` | 产品最近一次有效价格 |
| `hasPrice` | 查询日期当天是否存在有效价格 |

分页约定：

- `page` 从 0 开始，与现有后端分页习惯保持一致。
- 默认 `size` 为 20。
- 默认排序建议为 `categoryName ASC, productName ASC`。
- 当 `status` 未传时，默认只查询启用产品。

`GET /api/price-query/export`：

- 参数与列表查询保持一致。
- 不分页，导出全部匹配数据。
- 返回 Excel 文件流。
- 使用项目现有导出方式与响应头规范。
- 使用 `@OperationLog` 记录导出动作，操作类型为 `EXPORT`。
- 导出文件只包含页面可见字段和业务确认的统计字段，不导出内部 ID、权限字段、审计字段等后台管理信息。

导出字段首版固定为：

| 列名 | 字段 |
|------|------|
| 查询日期 | `effectiveDate` |
| 产品名称 | `productName` |
| 类别 | `categoryName` |
| 规格 | `specification` |
| 单位 | `unit` |
| 币种 | `currency` |
| 当日售价 | `currentPrice` |
| 昨日售价 | `yesterdayPrice` |
| 涨跌额 | `changeAmount` |
| 涨跌幅 | `changePercent` |
| 预算价 | `budgetPrice` |
| 月均价 | `monthlyAveragePrice` |
| 最近有效价 | `latestPrice` |
| 当日是否报价 | `hasPrice` |

导出限制：

- 单次导出建议设置最大行数保护，例如 10000 行。
- 超过最大行数时返回明确错误提示，引导用户缩小筛选范围。
- 前端导出按钮需要 loading 状态和防重复点击。

### 权限与菜单设计

普通用户需要使用导出功能，因此不能沿用仅管理员可用的导出权限。

建议新增权限标识：

```text
price:view
price:export
```

权限分配：

| 角色 | price:view | price:export |
|------|------------|--------------|
| ADMIN | 是 | 是 |
| EDITOR | 是 | 是 |
| VIEWER | 是 | 是 |

如果当前系统已有等价权限，可复用现有权限，但需要确保 `VIEWER` 用户可以访问页面并导出数据。

菜单初始化需要增加“价格查询”菜单项，角色范围覆盖普通用户。

落地原则：

- 如果当前项目通过启动初始化代码生成菜单和权限，则优先修改初始化代码。
- 如果当前项目通过 `init.sql` 固化菜单和权限，则同步更新 `init.sql`。
- 实现前必须先确认项目实际菜单来源，避免只改前端路由但普通用户菜单不可见。
- 前端权限常量需补充 `PRICE_VIEW`、`PRICE_EXPORT`，并保持与后端权限标识一致。

### 数据库与数据一致性

本功能原则上不需要新增业务表。

可能涉及的数据库变更：

- 菜单初始化数据新增“价格查询”菜单。
- 权限初始化数据新增 `price:view`、`price:export`。
- 角色权限绑定增加普通用户导出权限。

如果实际实现采用已有权限与菜单配置能力手工维护，则不需要修改表结构，但仍需同步初始化 SQL 或菜单初始化代码。

需要检查的一致性：

- 后端 DTO 字段与前端 TypeScript 类型一致。
- 列表查询参数与导出参数一致。
- 导出数据与页面筛选结果一致。
- 产品状态、分类、单位、币种等显示名称必须通过字典或后端字段获取，前端不硬编码中文映射。

### 页面交互细节

默认状态：

- 页面默认查询日期与现有首页/价格维护页面保持一致。
- 首次加载列表成功后，默认选中第一条有当日价格的产品；如果没有当日价格，则选中第一条产品。
- 右侧趋势图无选中产品时显示空状态，不发起趋势请求。

空状态：

- 列表无数据时展示“暂无符合条件的价格数据”。
- 产品存在但当日无报价时，价格字段展示 `--`，涨跌字段展示 `--`。
- 趋势数据为空时展示“暂无趋势数据”，保留时间范围切换但不显示空白图表。

错误与加载：

- 列表查询、趋势查询、导出分别维护 loading 状态。
- 筛选条件变化时重置到第一页。
- 导出过程中禁用导出按钮，避免重复请求。
- 导出失败时展示后端返回的错误信息；无明确错误信息时展示通用失败提示。
- 趋势图请求需要处理快速切换产品导致的旧请求覆盖问题，以最后一次选择为准。

视觉细节：

- 页面外观贴近 `docs/UI/ProductsList.png`，但查询页不展示编辑入口。
- 导出按钮放在筛选工具栏右侧，保持用户一眼可见。
- 表格行选中态要明显，右侧趋势标题与当前选中行保持一致。
- 日期控件不增加额外装饰边框，延续近期已优化的简洁风格。
- 左右按钮、分页按钮等固定尺寸，避免点击时抖动。

## 关键参考文件

视觉参考：

```text
docs/UI/ProductsList.png
```

前端参考：

```text
frontend/src/router/index.ts
frontend/src/api/products.ts
frontend/src/views/PriceMaintenance.vue
frontend/src/views/Home.vue
frontend/src/components/HomePriceCurvePanel.vue
frontend/src/composables/useDict.ts
frontend/src/composables/useSafeChartAutoresize.ts
frontend/src/composables/usePermission.ts
```

后端参考：

```text
backend/src/main/java/com/pricemanagement/controller/ProductController.java
backend/src/main/java/com/pricemanagement/controller/PriceController.java
backend/src/main/java/com/pricemanagement/service/ProductService.java
backend/src/main/java/com/pricemanagement/service/PriceService.java
backend/src/main/java/com/pricemanagement/dto/PriceWithStatsDTO.java
backend/src/main/java/com/pricemanagement/annotation/OperationLog.java
backend/src/main/resources/init.sql
```

文档参考：

```text
README.md
docs/dev/项目设计文档.md
docs/dev/UI设计说明.md
docs/ops/操作手册.md
docs/archive/项目完成总结.md
```

## 实现步骤

1. 梳理现有价格查询、趋势查询、导出相关接口，确认可以复用的 Service 与 DTO。
2. 确定“价格查询”页面路由、菜单名称、权限标识和角色范围。
3. 确认菜单和权限初始化来源，选择修改初始化代码或 `init.sql`，不要两边产生冲突。
4. 后端新增 `PriceQueryRowDTO`、`PriceQueryService` 与 `PriceQueryController`。
5. 后端实现 `GET /api/price-query`，确保分页、筛选、默认启用状态、空价格字段处理正确。
6. 后端实现 `GET /api/price-query/export`，复用列表查询条件构建逻辑，导出全部匹配数据。
7. 后端补充导出最大行数保护与 `@OperationLog`。
8. 更新菜单与权限初始化逻辑，确保 `VIEWER` 可访问页面并导出。
9. 前端新增 `priceQuery.ts` API 封装，定义查询参数、分页结果和导出方法。
10. 前端新增 `PriceQuery.vue` 页面，按参考图实现左右分栏、筛选、表格、趋势图和统计信息。
11. 前端处理空状态、loading、防重复导出、旧趋势请求覆盖、分页重置等交互细节。
12. 前端接入路由与菜单，确保普通用户登录后可见。
13. 完成响应式适配，检查窄屏下表格、筛选栏、趋势图不重叠。
14. 同步更新 README、设计文档、UI 说明、操作手册和项目完成总结。

## Verification

后端验证：

```text
cd backend
mvn test
```

或在本地依赖不完整时执行：

```text
cd backend
mvn -DskipTests package
```

前端验证：

```text
cd frontend
npm run build
```

手工验证：

- `ADMIN`、`EDITOR`、`VIEWER` 均可看到“价格查询”菜单。
- `VIEWER` 可以进入页面，但看不到任何新增、编辑、删除价格入口。
- 默认日期下列表可以正常加载价格数据。
- 搜索、分类筛选、分页切换后，表格数据正确刷新。
- 点击产品行后，右侧趋势图和统计信息正确刷新。
- 切换 `7日 / 30日 / 90日 / 年度` 后，趋势图正确刷新。
- 点击导出后生成 Excel 文件，文件内容与当前筛选条件一致，并包含全部匹配记录。
- 连续点击导出按钮不会触发重复下载或重复请求。
- 导出超过最大行数时，前端展示明确提示。
- 当日无报价、昨日无报价、昨日价格为 0、趋势为空等边界场景展示正常。
- 浏览器最小化后不会触发页面自动最大化，趋势图恢复显示正常。
- 窄屏下页面不出现文字重叠、按钮抖动、图表空白。

接口一致性验证：

- 列表接口与导出接口使用同一组筛选参数。
- 同一筛选条件下，导出文件行数应等于列表接口返回的 `totalElements`。
- 导出文件中的产品顺序与列表默认排序一致。
- 普通用户调用编辑、删除类价格接口仍应被拒绝。

## 风险与取舍

- 如果仅在前端组合现有产品接口和价格接口，可以减少后端改动，但导出“全部筛选结果”会不稳定，且容易出现页面结果与导出结果不一致。因此建议新增后端聚合查询与导出接口。
- 普通用户导出属于业务权限放开，需要确认导出内容是否包含敏感字段。首版建议只导出页面可见字段。
- 如果现有权限体系已经有可复用的导出权限，应优先复用；如果该权限只面向管理员，则需要新增 `price:export` 并显式授权给普通用户。
- 如果产品数量较大，导出接口必须避免一次性加载过多实体导致内存压力；实现时可采用分页查询后写入 Excel，或设置合理最大导出行数。
- 如果预算价、月均价在现有模型中来源不统一，首版可以先返回已有可靠字段，缺失字段以 `--` 展示和导出，不为了展示完整而临时硬编码计算口径。

## 实现前确认清单

- [ ] 页面菜单名称最终确定为“价格查询”或“日常价格查询”。
- [ ] 路由最终确定为 `/price-query`。
- [ ] 普通用户 `VIEWER` 是否允许导出全部筛选结果。
- [ ] 单次导出最大行数阈值。
- [ ] 导出字段是否按本方案首版字段固定。
- [ ] 菜单和权限初始化来源是启动代码还是 `init.sql`。
- [ ] 默认查询日期是否与首页、价格维护页面保持一致。
