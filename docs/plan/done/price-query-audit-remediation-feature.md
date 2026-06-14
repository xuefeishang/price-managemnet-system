# 价格查询指标与自适应分页审计修复方案

> 状态：已完成，归档于 2026-06-13。

> 说明：本文件按用户指定路径存放于 `docs/plan/`。项目永久规范要求 Plan 默认放在根目录 `plan/`，本计划完成后应归档到 `docs/plan/done/`，并避免与既有 `daily-price-query-feature.md` 重复维护。

## Context

本计划用于修复 `/price-query` 价格指标洞察和自适应分页改版后的代码审计问题。

当前页面已实现：

- 按价格现状、短期及预算偏差、月度趋势展示 14 项价格指标。
- 使用 `price_metric_group`、`price_metric` 字典维护指标名称和说明。
- `/api/price-query` 返回最新价格、上期有效价格、预算偏差、环比及同比字段。
- 分页支持自适应、手动条数和响应式降级。

审计发现当前实现仍存在以下风险：

| 编号 | 优先级 | 问题 | 影响 |
|------|--------|------|------|
| PQ-01 | P0 | 同比截止日期使用查询日期，而非最新有效价格日 | 查询日之后无报价时，本期与上年同期统计区间长度不一致，同比失真 |
| PQ-02 | P0 | `yesterdayPrice` 实际返回预算价格 | DTO 字段语义误导内部与外部 API 调用方，导出出现重复预算列 |
| PQ-03 | P1 | 指标分组、顺序和启停仍在前端硬编码 | 字典管理中的分组、排序、状态修改不会真实影响页面 |
| PQ-04 | P1 | `loadRows()` 缺少请求竞态保护 | 日期、筛选、分页和尺寸变化并发请求时，旧响应可能覆盖新状态 |
| PQ-05 | P1 | 桌面自适应分页最小值固定为 10 | 低高度窗口无法按真实可用高度降级，产生不必要的表格内部滚动 |
| PQ-06 | P1 | 新增指标逻辑缺少专项测试 | 现有构建和通用测试无法防止指标口径、JPQL 和竞态回归 |
| PQ-07 | P2 | “最新有效价格”查询未明确校验 `expiryDate` | 若业务启用显式到期日，已过期价格可能仍被当作有效价格 |
| PQ-08 | P2 | 字典编辑能力与实际可配置范围不明确 | 管理员可能误以为可修改指标公式或任意调整结构 |

## 修复目标

1. 统一指标统计基准日，保证最新价格、同比、环比和说明口径一致。
2. 消除 `yesterdayPrice` 字段语义误导，同时保持已发布外部 API 的兼容性。
3. 让指标字典真正驱动展示分组、顺序和启停状态，但不允许字典修改后端计算公式。
4. 防止旧请求覆盖最新筛选、分页和日期状态。
5. 恢复基于真实可用高度的自适应分页，并保留合理响应式降级。
6. 为关键口径、边界和兼容行为建立自动化测试。

## 已确定关键决策

### 决策 D1：兼容字段保持当前已发布语义，不做静默改义

`PriceQueryRowDTO` 已同时用于内部接口和 `/api/external/v1/price-query`。为避免现有调用方在无版本升级的情况下获得不同含义的数据，确定采用以下策略：

| 字段 | 本轮语义 | 后续治理 |
|------|----------|----------|
| `yesterdayPrice` | 保持预算价格别名语义，始终等于修复后的 `budgetPrice` | 标记为 deprecated；外部 API 文档明确其为历史兼容字段 |
| `changeAmount` | 保持较预算差异额别名语义，始终等于 `budgetChangeAmount` | 标记为 deprecated；新调用方使用 `budgetChangeAmount` |
| `changePercent` | 保持较预算差异率别名语义，始终等于 `budgetChangePercent` | 标记为 deprecated；新调用方使用 `budgetChangePercent` |
| `previousPrice` / `previousPriceDate` | 上期有效价格及日期 | 作为唯一明确的上期价格契约 |
| `previousChangeAmount` / `previousChangePercent` | 较上期差异额及差异率 | 作为唯一明确的较上期变化契约 |
| `budgetPrice` / `budgetChangeAmount` / `budgetChangePercent` | 指标基准日预算及偏差 | 作为唯一明确的预算指标契约 |

实施要求：

1. 内部前端、导出和新代码禁止继续读取 `yesterdayPrice`、`changeAmount`、`changePercent`。
2. 在 Java DTO 注释、TypeScript 类型注释和 API 手册中标记三个兼容字段为 deprecated。
3. 本轮不删除兼容字段，不改变其 JSON 字段名、类型和预算别名角色；跨年场景中的数值允许随 D2 预算口径修复而变化，并必须在发布说明中声明为指标纠错。
4. 后续如发布 `/api/external/v2/price-query`，删除兼容字段，仅保留明确字段。

### 决策 D2：预算指标按 `metricBaseDate` 所属年度取值

预算差异用于解释“最新有效价格相对其所属预算周期的偏离”，因此预算指标必须与最新有效价格使用同一时间基准：

- `budgetPrice`：读取 `metricBaseDate` 所属年度预算。
- `budgetChangeAmount`、`budgetChangePercent`：使用最新有效价格与 `metricBaseDate` 年度预算计算。
- 无最新有效价格、即无 `metricBaseDate` 时，以上预算指标均返回 `null`，不生成无价格基准的预算偏差。

查询日期预算属于另一类历史快照信息，不参与价格指标偏差计算。本轮确定不新增 `queryDateBudgetPrice`，因为当前价格查询页和附件指标均没有独立展示查询日预算的需求。后续如出现明确需求，应另立功能并新增语义明确的 `queryDateBudgetPrice`，禁止复用 `budgetPrice`。

跨年标准示例：

```text
queryDate       = 2026-01-05
metricBaseDate  = 2025-12-31
latestPrice     = 1,050
2025 年预算      = 1,000
2026 年预算      = 1,200

budgetPrice         = 1,000
budgetChangeAmount  = 50
budgetChangePercent = 5%
```

不得使用 2026 年预算计算 2025-12-31 最新价格的预算偏差。

## 范围边界

### 本轮必须完成

- 修复 PQ-01 至 PQ-06。
- 明确并验证 PQ-07 的业务规则；若 `expiryDate` 参与有效性判断，则同步修复查询。
- 更新内部 API、外部 API、导出、前端类型和相关文档。
- 保持现有 `/api/price-query`、`/api/external/v1/price-query` 路径和权限不变。

### 本轮不做

- 不允许通过字典动态执行公式或 SQL。
- 不引入通用指标计算引擎。
- 不修改年度预算的唯一来源 `product_annual_budget`。
- 不重构其它页面的分页系统；可抽取设计建议，但本轮只修复价格查询页。
- 不删除已发布兼容字段，除非完成外部 API 版本升级。

## 实现方案

### 1. 统一指标统计基准日

#### 目标口径

区分两个日期：

| 日期 | 定义 | 使用范围 |
|------|------|----------|
| 查询日期 `queryDate` | 用户选择的历史快照日期 | 允许查询的价格上限、页面快照语义；本轮不返回独立查询日预算 |
| 指标基准日 `metricBaseDate` | 截至查询日期的最新有效价格日期 | 预算价格与预算偏差、本月累计均价、上年同期均价、同比截止日、指标说明 |

#### 后端处理顺序

调整 `PriceQueryService.buildRows()`：

1. 先批量查询截至 `queryDate` 的最新有效价格。
2. 为每个产品提取 `latestPriceDate`，作为产品级 `metricBaseDate`。
3. 按不同 `metricBaseDate` 批量或分组计算：
   - 本月累计平均价格：`metricBaseDate` 所在月第一天至 `metricBaseDate`。
   - 上月平均价格：`metricBaseDate` 上一个完整自然月。
   - 上年同期平均价格：上年同月第一天至上年对应日。
4. 年度预算按产品的 `metricBaseDate` 所属年度读取，确保预算偏差与最新有效价格处于同一预算周期。
5. 本轮不计算或返回查询日期预算，禁止将其与指标预算混用。
6. 无最新有效价格的产品，价格及预算偏差指标全部返回 `null`。

#### 闰年边界

当 `metricBaseDate` 为 2 月 29 日时，上年同期截止日使用上年 2 月最后一天，避免 `minusYears(1)` 或日期构造异常。

建议增加日期辅助方法：

```java
private LocalDate samePeriodEndLastYear(LocalDate baseDate)
```

#### 性能策略

禁止按产品逐条查询平均价。优先将产品按 `metricBaseDate` 分组，每个不同基准日执行一次批量平均价查询。正常日常报价场景中，多数产品基准日一致，可保持较低查询次数。

### 2. 修复 `yesterdayPrice` 兼容语义

#### 兼容原则

`PriceQueryRowDTO` 同时被内部和外部价格查询接口复用，不能直接删除或静默改变已发布字段。

#### 确定方案

1. `yesterdayPrice`、`changeAmount`、`changePercent` 保持预算相关别名语义、字段名和类型，避免破坏外部 API v1；其跨年数值允许随 D2 指标纠错变化。
2. 将上述三个字段标记为 deprecated，禁止内部前端、导出和新代码继续读取。
3. 页面预算列和预算偏差只使用：
   - `budgetPrice`
   - `budgetChangeAmount`
   - `budgetChangePercent`
4. 上期价格及变化只使用：
   - `previousPrice`
   - `previousPriceDate`
   - `previousChangeAmount`
   - `previousChangePercent`
5. 在 API 手册中记录旧字段当前语义、弃用状态和替代字段。
6. 后续外部 API v2 删除兼容字段，本轮不创建 v2。

#### 导出修复

调整 `PriceQueryExportExcelData`：

- 删除重复的“预算价格 / 预算价”列。
- 导出字段与当前页面和 Excel 指标口径对齐。
- 至少包含：
  - 最新价格及日期
  - 上期有效价格及日期
  - 较上期差异额和差异率
  - 预算价格、较预算差异额和差异率
  - 本月累计均价、上月均价、环比
  - 上年同期均价、同比

导出只使用明确字段，不导出 deprecated 兼容字段。

### 3. 指标字典驱动展示结构

#### 字典职责

`price_metric_group`：

- `dictKey`：稳定分组编码。
- `dictValue`：分组展示名称。
- `sortOrder`：分组顺序。
- `status`：是否展示该分组。

`price_metric`：

- `dictKey`：稳定指标编码。
- `dictValue`：指标展示名称。
- `sortOrder`：指标顺序。
- `status`：是否展示该指标。
- `extraValue`：展示元数据 JSON。

建议 `extraValue` 结构：

```json
{
  "group": "PRICE_STATUS",
  "valueType": "price",
  "description": "当前产品最新有效价格",
  "rule": "按价格日期倒序取最新有效记录",
  "note": "不是按录入时间取值"
}
```

#### 前端实现

在 `PriceQuery.vue` 中保留不可配置的安全字段映射：

```ts
const PRICE_METRIC_VALUE_ACCESSORS = {
  LATEST_PRICE: row => row.latestPrice,
  LATEST_PRICE_DATE: row => row.latestPriceDate,
  // ...
}
```

页面构建流程：

1. 读取启用的 `price_metric_group`，按 `sortOrder` 排序。
2. 读取启用的 `price_metric`，解析 `extraValue.group` 和 `valueType`。
3. 只渲染存在于安全字段映射中的稳定 Key。
4. 按字典分组和排序生成页面结构。
5. JSON 无效、分组不存在或 Key 未注册时跳过该项，并在开发环境输出警告。

#### 字典管理治理

- `dictKey` 禁止修改和删除。
- `extraValue.group` 必须校验为已存在的指标分组。
- `valueType` 只允许 `price`、`change`、`percent`、`date`。
- 指标公式仍由后端代码计算，字典中的 `rule` 仅作为说明。
- 若不准备支持启停、排序和换组，应将这些分类改为受保护只读分类，不能继续宣称其可驱动结构。

### 4. 列表请求竞态保护

为 `loadRows()` 增加请求序列号：

```ts
const rowRequestSeq = ref(0)

const loadRows = async (...) => {
  const seq = ++rowRequestSeq.value
  const response = await getPriceQueryRows(...)
  if (seq !== rowRequestSeq.value) return
  // 更新 rows、分页和 selectedRow
}
```

要求：

- 只有最新请求可以更新 `rows`、分页、选中行和 loading 状态。
- 旧请求失败时不得弹出覆盖当前状态的错误提示。
- 自适应条数变化触发的新请求必须复用同一竞态保护。
- 如当前 HTTP 层支持 `AbortController`，可进一步取消旧请求；序列号仍作为最终保护。

### 5. 自适应分页修复

#### 目标行为

- 宽屏左右分栏：按表格真实可用高度计算条数。
- 桌面上下堆叠：使用稳定的 10 条降级值，避免内容高度自锁。
- 移动端：使用 5 条降级值。
- 手动选择条数后停止自动调整；用户可随时切回“自适应”。

#### 宽屏计算规则

禁止用“桌面最少 10 条”覆盖真实高度结果。宽屏应使用：

```text
floor((tableShellHeight - headerHeight - safetyGap) / actualRowHeight)
```

建议：

- 最小值：5。
- 最大值：12。
- `safetyGap`：2-4px，避免边框和小数像素造成最后一行被截断。
- 无数据或加载中时使用固定行高回退值，不能使用状态行高度。
- 计算后仅在条数实际变化时重新请求。

#### 防抖与循环保护

- `ResizeObserver` 只观察影响可用高度的外层列表面板。
- 避免同时观察会被行数变化直接撑高的表格内容节点。
- 尺寸变化使用防抖。
- 同一尺寸计算结果不得重复请求。

### 6. `expiryDate` 有效性规则确认

实施前确认业务定义：

1. 若价格在下一条正式价格出现前持续有效，且 `expiryDate` 为空表示无限期：
   - 最新有效价格条件应包含 `expiryDate IS NULL OR expiryDate >= queryDate`。
2. 若价格记录仅代表报价日期，不使用到期语义：
   - API 和页面文案应使用“最近报价”而非“最新有效价格”。

确认后统一修改：

- `PriceRepository.findLatestPriceBeforeDate`
- `PriceRepository.findLatestPricesBeforeDate`
- `findPreviousEffectivePricesBeforeDate`
- 产品趋势和价格查询相关文档
- 对应测试

## 前后端与数据库一致性

### API 一致性

需要同步检查：

| 层级 | 文件 | 检查内容 |
|------|------|----------|
| 后端 DTO | `PriceQueryRowDTO.java` | 旧兼容字段与新增明确字段语义 |
| 内部接口 | `PriceQueryController.java` | 响应结构不破坏现有页面 |
| 外部接口 | `ExternalPriceQueryController.java` | 已发布字段兼容和文档说明 |
| 前端类型 | `frontend/src/types/index.ts` | 与 DTO 字段及可空性一致 |
| 前端页面 | `PriceQuery.vue` | 不再使用语义错误的兼容字段 |
| 导出 DTO | `PriceQueryExportExcelData.java` | 字段与页面指标一致且无重复列 |

兼容字段契约必须满足：

- `/api/price-query` 与 `/api/external/v1/price-query` 中三个 deprecated 字段保持预算别名语义、字段名和类型，并分别与明确预算字段保持值相等。
- 新增明确字段在内部和外部接口中保持相同字段名、类型、可空性和计算口径。
- 前端页面与导出只消费明确字段。
- 本轮不得新增或复用字段承载查询日期预算；未来如新增 `queryDateBudgetPrice`，必须另立功能并同步 DTO、前端类型、API 手册和测试。

### 数据库与字典一致性

本计划默认不新增数据库表或字段。

若调整指标字典 `extraValue`：

- 新增后续 Flyway migration，不能修改已执行的 `V45__price_metric_dict.sql`。
- 同步更新 `init.sql`。
- 同步更新 `backend/src/main/resources/数据字典.md`。
- 保持 `sys_dict(category, dict_key)` 唯一约束不变。

## 关键参考文件

### 后端

- `backend/src/main/java/com/pricemanagement/service/PriceQueryService.java`
- `backend/src/main/java/com/pricemanagement/repository/PriceRepository.java`
- `backend/src/main/java/com/pricemanagement/dto/PriceQueryRowDTO.java`
- `backend/src/main/java/com/pricemanagement/dto/PriceQueryExportExcelData.java`
- `backend/src/main/java/com/pricemanagement/controller/PriceQueryController.java`
- `backend/src/main/java/com/pricemanagement/controller/external/ExternalPriceQueryController.java`
- `backend/src/main/resources/db/migration/V45__price_metric_dict.sql`
- `backend/src/main/resources/init.sql`

### 前端

- `frontend/src/views/PriceQuery.vue`
- `frontend/src/types/index.ts`
- `frontend/src/api/priceQuery.ts`
- `frontend/src/composables/useDict.ts`
- `frontend/src/constants/dictCategoryMeta.ts`

### 文档

- `docs/dev/API调用手册.md`
- `docs/dev/开发指南.md`
- `docs/dev/项目设计文档.md`
- `docs/dev/UI设计说明.md`
- `backend/src/main/resources/数据字典.md`

## 实现步骤

### 阶段 0：实施前准备

1. 按项目规范备份所有待修改文件到 `backup/price-query-audit-remediation-{timestamp}/`。
2. 创建备份说明，记录变更目标、文件清单、数据库影响和恢复方式。
3. 确认 `expiryDate` 的业务定义。
4. 检查外部 `/api/external/v1/price-query` 调用方和日志，但不以检查结果改变 D1：v1 兼容字段本轮保持预算别名语义、字段名和类型。

### 阶段 1：P0 指标口径和兼容字段

1. 调整 `PriceQueryService`，先获取最新有效价格，再按产品级基准日计算月度指标。
2. 修复同比截止日期和闰年边界。
3. 按 `metricBaseDate` 所属年度读取预算并计算预算偏差。
4. 保持 `yesterdayPrice`、`changeAmount`、`changePercent` 当前语义并标记 deprecated，前端和导出切换至明确字段。
5. 修复导出重复预算列，补齐价格指标导出字段。
6. 更新内部和外部 API 文档。

### 阶段 2：P1 字典驱动和请求竞态

1. 增加前端稳定指标字段映射。
2. 使用启用字典项、分组和 `sortOrder` 动态构建指标区。
3. 增加字典 JSON 和分组校验。
4. 为 `loadRows()` 增加请求序列号或取消机制。
5. 验证字典停用、排序、改名和换组行为。

### 阶段 3：P1 自适应分页

1. 恢复宽屏真实高度计算，最小值允许降至 5。
2. 保留桌面堆叠 10 条、移动端 5 条降级。
3. 调整 `ResizeObserver` 观察目标，避免内容高度循环。
4. 验证手动条数和切回自适应。

### 阶段 4：测试和文档

1. 增加后端服务与仓储测试。
2. 增加前端指标构建和请求竞态测试；若项目尚无前端测试基础，至少抽取纯函数并测试。
3. 执行前端构建、后端测试和 Flyway 校验。
4. 更新项目文档并将本计划移入 `docs/plan/done/`。

## 测试方案

### 后端单元与集成测试

新增 `PriceQueryServiceTests`，覆盖：

| 场景 | 预期 |
|------|------|
| 查询日有报价 | 指标基准日等于查询日 |
| 查询日无报价，最近报价早于查询日 | 同比、本月累计均价截至最近报价日 |
| 查询日与指标基准日跨年 | 指标预算只使用基准日所属年度预算，不返回查询日期预算 |
| 无上期价格 | 上期价格和较上期指标为空 |
| 上期价格为 0 | 差异额存在，差异率为空 |
| 无预算 | 预算及预算偏差为空 |
| 上月无价格 | 环比为空 |
| 上年同期无价格 | 同比为空 |
| 基准日为闰年 2 月 29 日 | 上年同期正确截止到 2 月最后一天 |
| 多产品基准日不同 | 每个产品使用自己的指标基准日 |
| 显式到期价格 | 按确认后的有效性规则返回或排除 |
| 外部 API v1 兼容字段 | deprecated 字段保持预算别名语义，并分别等于明确预算字段 |
| 内部页面和导出 | 不读取或导出 deprecated 兼容字段 |

新增仓储测试，覆盖：

- 最新有效价格查询。
- 上期有效价格查询。
- 多产品批量查询。
- `expiryDate` 边界。

### 前端测试

至少覆盖：

- 字典分组、排序、停用和改名后指标区正确生成。
- 未注册指标 Key 被安全跳过。
- 非法 `extraValue` 不导致页面异常。
- 较旧列表请求后返回时不覆盖最新请求。
- 宽屏可用高度分别容纳 5、7、10、12 条时计算正确。
- 1180px 和 768px 响应式边界正确。
- 手动条数模式不被 ResizeObserver 改写。
- 可重新切回自适应模式。

### 手工验证

1. 打开 `/price-query`，选择一个查询日之后无新报价的产品，核对同比区间。
2. 在 `/dict-management` 中修改指标名称、排序、状态和分组，刷新价格查询页验证。
3. 快速切换日期、分类、搜索词和窗口大小，确认列表不会回跳到旧结果。
4. 调整浏览器高度，确认宽屏能在 5-12 条之间真实自适应。
5. 检查内部 API、外部 API 和导出 Excel 字段语义。
6. 使用跨年样例核对最新价格采用其所属年度预算，而不是查询日期年度预算。

## 实施结果

- 后端指标统计、预算年度和同比区间已统一按每个产品的 `metricBaseDate` 计算。
- 最新有效价格查询已纳入 `expiryDate` 边界。
- v1 兼容字段保留预算别名语义并标记 deprecated，内部页面与导出已停止使用。
- 指标分组、名称、排序、启停和换组已由字典动态驱动，新增 `V46__price_metric_display_metadata.sql` 补充 `valueType`。
- 列表请求已加入竞态保护；宽屏自适应分页范围调整为 5-12 条。
- 已新增 `PriceQueryServiceTests`，前端未引入新测试框架，以类型检查和生产构建验证纯前端改动。

## Verification

完成标准：

- [x] PQ-01 至 PQ-06 全部修复。
- [x] PQ-07 已明确规则并完成代码或文档修正。
- [x] `yesterdayPrice`、`changeAmount`、`changePercent` 保持预算别名语义、字段名和类型并标记 deprecated，分别与明确预算字段保持值相等。
- [x] 内部前端和导出只使用明确字段，不再消费 deprecated 兼容字段。
- [x] `budgetPrice` 与预算偏差按 `metricBaseDate` 所属年度计算，跨年标准样例验证通过。
- [x] 本轮未新增、未复用任何字段承载查询日期预算。
- [x] 字典停用、排序和换组能够真实影响指标展示。
- [x] 快速连续请求不会出现旧响应覆盖。
- [x] 宽屏分页按真实高度在 5-12 条之间变化；堆叠桌面为 10 条；移动端为 5 条。
- [x] 导出无重复预算列，指标字段与页面一致。
- [x] `mvn test` 通过，且新增价格查询专项测试。
- [x] `npm run build` 通过。
- [x] `git diff --check` 通过。
- [ ] Flyway 在空库和升级库验证通过；如无新迁移则确认 V45 不被修改。
- [x] README、开发指南、项目设计文档、API 调用手册、UI 设计说明和数据字典按实际变更同步。

## 风险与回滚

| 风险 | 控制措施 |
|------|----------|
| 兼容字段名称继续存在语义误导 | 保持 v1 预算别名角色但明确标记 deprecated；内部和新调用方只使用明确字段；后续 v2 删除 |
| 查询日期与指标基准日跨年导致预算理解差异 | 文档明确指标预算按 `metricBaseDate`；本轮不返回查询日期预算 |
| 产品级基准日导致查询次数增加 | 按基准日分组批量查询，增加查询次数监控 |
| 字典错误配置导致指标缺失 | 使用稳定 Key 白名单、JSON 校验和开发环境告警 |
| ResizeObserver 再次产生请求循环 | 只观察稳定外层容器，增加防抖和同值短路 |
| 指标计算口径变更导致历史数据变化 | 在发布说明中记录口径修复，并使用固定样例做新旧结果对照 |

回滚时恢复备份文件；若新增字典修复 migration，只能新增反向 migration，禁止删除或改写已执行 migration。
