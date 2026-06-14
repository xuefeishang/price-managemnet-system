# 代码审计问题与整改方案

> 状态：待整改  
> 创建日期：2026-06-14  
> 范围：近期价格维护发布逻辑、首页价格对比、小程序首页/历史/录入产品展示，以及相关前后端接口一致性。

## Context

本次审计围绕近期变更展开：

- `/home` 产品列表将“较昨日”改为“较上期”，并对齐 `/price-query` 的“较上期差异额”计算口径。
- 小程序历史页文案从“昨日/较昨日”调整为“最新/较上期”。
- 小程序首页、历史、录入中的产品名称追加产地，展示为 `铁精粉.宽城`。
- `/price-maintenance` 发布逻辑应改为发布所有已保存但未发布的草稿，小程序保持同一逻辑。

审计结论：核心方向正确，但当前实现仍停留在“按选中日期发布”的能力，未满足“所有保存草稿一起生效”的业务语义。PC 和小程序价格维护页需要统一为“发布全系统所有 `DRAFT` 草稿”；按日期发布仅保留给定时任务、补发或管理型入口。首页与小程序部分取数也存在分页上限和回退口径风险。

## 最终决策

本方案采用以下不可再摇摆的发布模型：

> 价格维护页点击“发布” = 发布全系统所有 `DRAFT` 草稿，不受当前页面 `selectedDate` 限制。

边界定义：

| 维度 | 决策 |
|------|------|
| 发布范围 | 全系统所有 `status = DRAFT` 的价格草稿批次 |
| 日期范围 | 所有报价日期，不限当前选中日期 |
| 用户范围 | 所有用户保存的草稿；发布是系统级生效动作，不按创建人过滤 |
| 来源范围 | `MANUAL` 和 `SCHEDULED` 的 DRAFT 草稿均纳入，除非后续产品明确要求排除某类来源 |
| 不纳入范围 | `PENDING_APPROVAL`、`APPROVED`、`PUBLISHING`、`PUBLISHED`、`CANCELLED` 不由价格维护页一键发布处理 |
| 日期级发布 | 仅作为定时任务、按日期补发、管理型批次操作保留，不作为价格维护页主按钮语义 |
| 幂等要求 | 已发布明细继续跳过；无 DRAFT 草稿时明确提示“暂无可发布草稿” |
| 通知粒度 | 价格维护页一键发布生成一次发布组通知，内容汇总日期数、批次数和成功明细数 |

## Findings

| 编号 | 优先级 | 问题 | 影响 |
|------|--------|------|------|
| CA-01 | P0 | 价格维护页发布范围与“所有保存草稿一起生效”不一致 | PC 点击发布仍只发布当前选中日期的 DRAFT 草稿，其他日期历史草稿不会发布；切换回历史草稿日期后仍提示可发布 |
| CA-02 | P0 | 发布结果缺少批次级明细 | 多批次/跨日期发布时无法准确追踪每个批次的日志、状态和失败原因 |
| CA-03 | P1 | 全局发布的批次级异常与事务语义需要明确 | 空批次、状态竞态等异常可能中断整次发布，与“所有保存草稿一起生效”的业务预期不完全一致 |
| CA-04 | P1 | 通知仍按日期去重，不适合跨日期发布 | 全局发布多个日期时无法准确通知；同一天后续合法发布也可能被去重吞掉 |
| CA-05 | P1 | 首页价格对比存在回退口径混用 | `/price-query` 数据未覆盖的产品会回退到旧的 yesterday/inherited 逻辑，导致“较上期”口径不稳定 |
| CA-06 | P1 | 小程序首页只加载前 100 个产品和前 100 条价格查询数据 | 产品超过 100 个时，首页列表、搜索、统计和“较上期”计算可能缺失数据 |
| CA-07 | P2 | 小程序产地展示依赖字典加载时序 | 字典未完成加载或缺少配置时，产地可能显示编码值而非中文名称 |
| CA-08 | P2 | PC 首页搜索未纳入产地展示名 | 用户按产地搜索时，小程序可命中，PC 首页可能无法命中 |
| CA-09 | P2 | 编辑模型与全局发布模型边界需要产品化表达 | 用户编辑的是当前日期草稿，发布的是全系统所有 DRAFT 草稿，需要在 UI 和接口命名上讲清楚 |

## CA-01：价格维护页发布范围与业务语义不一致

### 问题

后端已提供 `/api/price-drafts/by-date/publish`，并在 `PricePublishService.publishByDate` 中按日期锁定并发布所有 `DRAFT` 批次。  
但用户期望的业务语义是：在价格维护页点击“发布”后，所有已保存但未发布的草稿一起生效，而不是只发布当前页面选中日期的草稿。

当前 PC 端 `handlePublish` 调用 `publishPriceDraftByDate(selectedDate.value)`，所以发布范围仍被 `selectedDate` 限制。若用户当天点击发布，只会发布当天日期的 DRAFT 草稿；之前日期已保存的草稿不会生效。切换到历史草稿日期后，因为该日期草稿仍是 DRAFT，页面仍会提示可发布。

另外，`/api/price-drafts/by-date` 仍只返回一个最新活动草稿，PC 与小程序页面也继续使用 `currentDraft` 控制按钮状态、保存批次和展示“已保存 X 条”。这会让页面看起来像是在发布单个当前草稿，而后端实际按日期发布多个 DRAFT 批次。

### 证据

- `backend/src/main/java/com/pricemanagement/service/PricePublishService.java`：`publishByDate` 通过 `findAllByDateAndStatusForUpdate` 获取同日期所有 DRAFT 批次，未覆盖其他日期 DRAFT 草稿。
- `backend/src/main/java/com/pricemanagement/service/PriceDraftService.java`：`getActiveDraftByDate` 只返回 `findFirstByEffectiveDateAndStatusInOrderByCreatedTimeDesc` 的最新活动批次。
- `frontend/src/views/PriceMaintenance.vue`：页面状态为 `currentDraft`，发布前校验 `!currentDraft.value?.id`，发布调用传入 `selectedDate.value`。
- `frontend-uniapp/src/pages/price-maintenance/index.vue`：同样使用 `currentDraft` 控制发布按钮和草稿条数展示，发布调用也传入 `selectedDate.value`。

### 影响

1. PC 端在当天页面点击发布时，只会发布当天草稿；之前日期已保存的草稿仍保持 DRAFT，价格不会生效。
2. 用户切换到之前草稿日期后仍会看到“可发布”提示，因为该日期草稿确实未被发布。
3. 页面展示“草稿已保存 10 条”，实际发布可能只覆盖当前选中日期，也可能包含同日期其他 DRAFT 批次，用户无法确认完整发布范围。
4. 最新活动批次不是 DRAFT 或接口返回为空时，即使系统中仍有 DRAFT 草稿，前端也可能禁用发布。

### 解决方案

价格维护页必须新增“全局待发布草稿”能力，将页面发布按钮从“按当前选中日期发布”改为“发布所有 DRAFT 草稿”。日期级发布接口只保留给定时任务、按日期补发或后续批次详情页使用，不再作为价格维护页主按钮调用目标。

第一阶段必须新增接口：

```text
GET /api/price-drafts/publishable-summary
POST /api/price-drafts/publish-all
```

后端实现要点：

- Repository 增加按状态锁定所有 DRAFT 批次的方法，例如 `findAllByStatusForUpdate(DRAFT)`，排序建议按 `effectiveDate ASC, createdTime ASC, id ASC`。
- Service 增加 `publishAllDrafts(publishType, userId)`，遍历所有 DRAFT 批次并复用单批次发布逻辑。
- 返回结构包含 `effectiveDate` 维度和 `batchId` 维度的汇总，避免只返回最后一个 `publishLogId`。
- 若没有任何 DRAFT 草稿，返回 400 或业务成功空结果需统一前端提示，建议沿用 400：“暂无可发布草稿”。

`GET /api/price-drafts/publishable-summary` 建议响应字段：

| 字段 | 说明 |
|------|------|
| `publishableBatchCount` | 全系统可发布 DRAFT 批次数 |
| `publishableItemCount` | 全系统可发布明细数 |
| `publishableDateCount` | 涉及报价日期数量 |
| `effectiveDates` | 涉及报价日期列表 |
| `publishableBatchIds` | 可发布批次 ID 列表 |
| `hasPublishableDrafts` | 是否存在可发布草稿 |

PC 和小程序第一阶段必须改为：

- 发布按钮基于全局 `hasPublishableDrafts && !hasChanges && !saving && !publishing`，而不是 `currentDraft` 或当前日期草稿。
- 发布确认弹窗明确提示“将发布所有已保存草稿”，并列出涉及日期数量、批次数和明细数。
- 发布完成后刷新当前页面日期的价格和草稿状态，同时刷新全局待发布摘要。
- 如果当前日期没有草稿但其他日期有草稿，也允许发布；按钮文案或提示应说明发布的是全部待发布草稿。

不得只切换 `publish-all` 而不做摘要接口。原因是发布按钮状态、确认弹窗和“暂无可发布草稿”提示都依赖全局摘要；缺少摘要会继续让用户误以为发布范围只与当前日期有关。

原日期级摘要接口仍有价值，但只解决“同日期多草稿”可见性问题：

```text
GET /api/price-drafts/by-date/summary?date=YYYY-MM-DD
```

建议响应字段：

| 字段 | 说明 |
|------|------|
| `effectiveDate` | 报价日期 |
| `activeDraft` | 当前用于编辑的草稿，保持兼容 |
| `publishableBatchCount` | 同日期可发布 DRAFT 批次数 |
| `publishableItemCount` | 同日期可发布明细数 |
| `publishableBatchIds` | 可发布批次 ID 列表 |
| `hasPublishableDrafts` | 是否存在可发布草稿 |
| `latestDraftStatus` | 最新活动草稿状态 |

日期级摘要后续仅用于管理型补发页面或批次详情页，不作为当前价格维护页主流程必需项。若实现该接口，对应页面应：

- 发布按钮基于 `hasPublishableDrafts && !hasChanges && !saving && !publishing`。
- 草稿状态区展示“本日期待发布 X 个草稿批次，共 Y 条”。
- 发布确认弹窗明确提示“将发布该日期所有已保存草稿”。
- 保存仍可使用 `activeDraft`，但发布不再依赖 `currentDraft.id`。

### 验证

- 创建昨天和今天两个日期的 DRAFT 草稿，在今天页面点击发布，两个日期草稿都变为 `PUBLISHED`，对应价格都写入价格表。
- 发布完成后切换到昨天日期，不再提示可发布；`/api/price-drafts/by-date?date=昨天` 不应返回 DRAFT 草稿。
- 同日期创建 2 个 DRAFT 批次，发布全部后两个批次都变为 `PUBLISHED`。
- 当前日期没有草稿、其他日期有草稿时，页面仍能通过全局摘要提示“有待发布草稿”，点击发布后全部生效。
- PC 与小程序展示数量、确认弹窗、发布结果一致。

## CA-02：全局发布结果缺少批次级明细

### 问题

`PricePublishResultDTO` 只有 `batchId`、`publishLogId`、`successCount`、`failCount`、`message` 等单批次字段。全局发布会跨日期、跨批次处理多个 DRAFT 草稿，如果仍只返回最后一个 `publishLogId`，前端、通知和审计都无法准确表达本次发布。

### 影响

- 操作人员无法从接口结果直接看到哪些日期、哪些批次成功，哪些失败。
- 通知和后续审计只能关联最后一条发布日志，跨日期发布追踪不完整。
- 前端如果要展示发布详情，只能解析 message 字符串，不稳定。

### 解决方案

扩展 DTO，保持现有字段兼容，新增结构化字段：

| 字段 | 说明 |
|------|------|
| `publishedBatchCount` | 成功完成发布的批次数 |
| `attemptedBatchCount` | 本次尝试发布的批次数 |
| `publishedDateCount` | 本次涉及且成功发布的报价日期数 |
| `attemptedDateCount` | 本次尝试发布的报价日期数 |
| `publishLogIds` | 所有发布日志 ID |
| `batchResults` | 每个批次的 `effectiveDate`、`batchId`、`publishLogId`、`status`、`successCount`、`failCount`、`message` |
| `publishGroupId` | 可选，串联同一次全局发布事件 |

建议新增发布组 ID，用于串联同一次全局发布中的多条批次日志和一条通知。

### 验证

- 后端单元测试断言跨日期多批次发布时返回全部 `publishLogIds`、日期数和批次数。
- 前端发布成功提示优先展示结构化总数，而不是依赖 message 文案。

## CA-03：全局发布的异常与事务语义需要明确

### 问题

`publishAllDrafts` 需要遍历全系统所有 DRAFT 批次。单条明细发布失败可以被 `publishLockedBatch` 捕获并记录为失败，但批次级异常，例如空明细、状态竞态、批次校验失败，如果直接抛出，会导致整次全局发布回滚。

### 影响

业务上“所有保存草稿一起生效”更接近“尽量处理所有可发布草稿，并清楚报告失败项”。如果一个异常批次阻断其他日期、其他批次的正常发布，用户会看到“发布失败”，但系统中大量正常草稿仍未生效。

### 解决方案

采用“全局尽力发布，批次级独立记录”的策略：

1. `publishAllDrafts` 遍历批次时捕获批次级异常，将该批次写入失败结果。
2. 空明细 DRAFT 批次不阻断其他批次，标记失败或自动取消，具体以业务确认结果为准。
3. 仅数据库不可恢复错误、权限错误等系统级异常回滚整次事务。
4. 增加测试覆盖：
   - 昨天空批次 + 今天正常批次。
   - 昨天批次部分明细失败 + 今天正常批次。
   - 并发点击全局发布时的锁与返回状态。

### 验证

- 跨日期混合批次场景下，正常批次能发布成功，异常批次在结果中可见。
- 所有批次失败时，接口返回 `FAILED`；部分成功时返回 `PARTIAL`。

## CA-04：通知应升级为发布组维度

### 问题

现有 `NotificationEventService.pricePublishedByDate` 的去重键为：

```text
PRICE_PUBLISHED:DATE:{effectiveDate}
```

它只适合单日期发布，不适合价格维护页的全局发布。全局发布可能一次涉及多个报价日期，此时用某一个日期作为通知去重键会丢失语义；同一天第一次发布后，如果用户又保存新的草稿并再次发布，第二次通知也可能被认为重复而不发送。

### 影响

- 查看者无法从通知中知道本次发布涉及几个日期、几个批次、多少条价格。
- 同一天后续合法发布可能没有通知。
- 发布日志、发布组和通知消息无法稳定关联，影响审计追踪。

### 解决方案

新增发布组通知方法，例如 `pricePublishedByGroup(...)`。通知去重键使用发布组维度：

```text
PRICE_PUBLISHED:GROUP:{publishGroupId}
```

如果第一阶段暂不落库发布组，也必须使用一次发布请求内生成的稳定事件 ID：

```text
PRICE_PUBLISHED:EVENT:{requestPublishEventId}
```

通知内容建议：

```text
价格已发布，共发布 {dateCount} 个日期、{batchCount} 个草稿批次、{successCount} 条价格。
```

如果仍需要防止重复点击，应在发布接口层用锁和状态控制，而不是用“日期”永久去重。

### 验证

- 一次全局发布只生成一条发布组通知，通知内容包含日期数、批次数和成功明细数。
- 同一天两次合法全局发布生成两条不同发布组通知。
- 通知可通过 `publishGroupId` 或事件 ID 关联到本次所有发布日志。

## CA-05：首页“较上期”存在回退口径混用

### 问题

PC 首页已优先使用 `/price-query` 的 `previousChangeAmount`，但当 `priceQueryRowMap` 没有覆盖某个产品时，会回退到 `previousPriceMap`、`inheritedPriceValueMap` 等旧逻辑。

### 影响

同一页面上部分产品使用 `/price-query` 的“上期有效价格”口径，部分产品使用旧的昨日或继承价格口径，用户看到的“较上期”并不完全一致。

### 解决方案

统一首页价格指标来源：

1. 后端提供按产品 ID 批量获取价格查询指标的接口，例如：

```text
POST /api/price-query/by-product-ids
```

2. 首页根据当前展示产品 ID 获取完整指标，而不是依赖固定分页缓存。
3. 若缺少 `/price-query` 指标，展示 `--`，不要静默回退到不同口径。
4. 如必须回退，需在 DTO 中明确返回 `metricSource`，前端可区分展示。

### 验证

- 构造当前页产品不在前 200 条 price-query 缓存中的场景，首页仍能展示正确“较上期”。
- 无上期价格时显示 `--`，不显示旧口径计算值。

## CA-06：小程序首页固定加载 100 条导致数据不完整

### 问题

小程序首页 `loadData` 中产品和 `/price-query` 都使用 `size: 100`。当有效产品超过 100 个时，首页展示、搜索和统计只能覆盖前 100 个产品。

### 影响

- 首页产品列表缺失。
- 用户按产品名或产地搜索时搜不到第 101 个之后的产品。
- 价格对比与上涨/下跌统计不完整。

### 解决方案

按目标体验二选一：

方案 A：实现分页/下拉加载更多。

- `products` 和 `price-query` 使用同一排序和分页参数。
- 滚动到底加载下一页。
- 搜索时走服务端分页搜索，避免只搜本地前 100 条。

方案 B：若首页设计上必须一次性展示全部首页产品，则后端提供轻量首页列表接口：

```text
GET /api/home/products-with-price-metrics?date=YYYY-MM-DD
```

该接口只返回首页所需字段，避免一次性拉取完整产品和价格对象。

### 验证

- 造数 150 个 ACTIVE 产品，小程序首页可看到或搜索到第 150 个产品。
- 第 101 个之后产品的“较上期”仍按 `/price-query` 口径计算。

## CA-07：小程序产地展示依赖字典加载时序

### 问题

`frontend-uniapp/src/utils/productDisplay.ts` 使用 `getDictValue('origin', key)` 将 `originIds` 转成中文产地。若字典尚未加载完成或字典缺少该 key，展示可能退化为编码值。

### 影响

用户可能看到 `铁精粉.KC` 或其他编码，而不是 `铁精粉.宽城`。在弱网或首屏并发加载时更容易出现。

### 解决方案

1. 页面初始化时先完成 `loadAllDicts()`，再渲染依赖产地的产品列表。
2. `getProductOriginLabel` 支持可选策略：
   - 正常模式：未知 key 返回空，避免把编码暴露给用户。
   - 调试模式：未知 key 返回编码，方便排查字典缺失。
3. 补充 origin 字典缺失时的兜底测试。

### 验证

- 清空本地字典缓存后首次进入小程序首页，产品名仍显示中文产地。
- 字典缺失某个 origin key 时，页面不展示难懂编码。

## CA-08：PC 首页搜索未纳入产地展示名

### 问题

小程序录入页搜索已将 `getProductDisplayName(product)`、产品名、规格和编码纳入搜索范围。PC 首页目前主要按产品名称等既有字段过滤，产地并不是稳定搜索条件。

### 影响

同一产品在小程序可以按“宽城”搜索到，在 PC 首页可能搜不到，跨端体验不一致。

### 解决方案

PC 首页引入统一的产品展示工具函数，搜索字段包含：

- `产品名.产地`
- 产品名
- 产地中文名
- 规格
- 编码

同时注意遵守字典规范：产地中文名仍通过 `useDict` 获取，不在组件中硬编码。

### 验证

- PC 首页输入产地名可筛出对应产品。
- 字典未加载完成时不会报错，加载完成后筛选结果刷新。

## CA-09：编辑模型与全局发布模型需要清晰表达

### 问题

当前保存逻辑倾向于写入当前日期的当前或最新活动草稿；发布逻辑按最终决策应发布全系统所有 DRAFT 草稿。系统实际上同时存在两种模型：

- 编辑模型：用户在当前选中日期编辑一个活动草稿。
- 发布模型：用户在价格维护页触发全局生效，发布所有日期、所有用户保存的 DRAFT 草稿。

### 影响

如果 UI 仍只显示当前日期草稿状态，用户会误以为发布只作用于当前日期；如果按钮又实际发布全局 DRAFT 草稿，会形成新的认知风险。多人协作、定时草稿和历史草稿同时存在时，这个风险会更明显。

### 解决方案

产品表达采用“双层状态”：

| 层级 | 页面展示 | 行为 |
|------|----------|------|
| 当前日期编辑状态 | 展示当前日期草稿状态、已保存条数、当前页面是否有未保存修改 | 只影响保存、切换日期、当前日期数据刷新 |
| 全局待发布状态 | 展示全系统待发布日期数、批次数、明细数 | 决定发布按钮是否可用，点击后发布所有 DRAFT 草稿 |

UI 文案建议：

- 当前日期草稿状态：`当前日期草稿：已保存 10 条`
- 全局待发布状态：`待发布：3 个日期 / 4 个草稿批次 / 80 条`
- 发布按钮文案：`发布全部草稿`
- 确认弹窗标题：`确认发布全部草稿`
- 确认弹窗内容：`将发布所有已保存但未发布的价格草稿，涉及 3 个日期、4 个草稿批次、80 条价格。`

接口命名也必须表达这个边界：

- 当前日期编辑继续使用 `/api/price-drafts/by-date`。
- 全局待发布使用 `/api/price-drafts/publishable-summary`。
- 全局发布使用 `/api/price-drafts/publish-all`。

同日期是否允许多个活动草稿不是本次修复的前置条件。若后续要治理，可另立专项，在不改变“价格维护页发布全部 DRAFT 草稿”这一主语义的前提下，决定是否约束同日期活动草稿数量。

## 推荐实施顺序

### 第一阶段：发布闭环修复，必须优先

1. 后端新增全局摘要：`GET /api/price-drafts/publishable-summary`。
2. 后端新增全局发布：`POST /api/price-drafts/publish-all`。
3. 后端扩展发布结果 DTO，返回发布组、日期数、批次数、全部 `publishLogIds` 和 `batchResults`。
4. 后端新增发布组通知，通知去重从日期维度改为发布组或发布事件维度。
5. PC 价格维护页切换发布按钮逻辑：按钮状态和确认弹窗读取全局摘要，发布调用 `publish-all`。
6. 小程序录入页同步 PC 逻辑，发布调用 `publish-all`。
7. 发布后刷新当前日期价格、当前日期草稿、全局待发布摘要和通知角标。
8. 增加后端测试和 PC/小程序手工冒烟，确认昨天和今天的 DRAFT 草稿可在今天页面一次发布完成。

### 第二阶段：口径和展示治理

1. 统一价格指标口径：完成 CA-05。
2. 完善小程序取数和展示：完成 CA-06、CA-07。
3. 对齐 PC 与小程序搜索体验：完成 CA-08。
4. 如业务需要，再单独治理同日期多活动草稿数量，不阻塞第一阶段发布闭环。

### 第三阶段：文档同步

更新 `README.md`、`docs/dev/开发指南.md`、`docs/dev/项目设计文档.md`、`docs/dev/UI设计说明.md`、`docs/archive/项目完成总结.md`。如涉及数据库约束或迁移，同步更新数据字典和迁移 SQL。

## Verification Matrix

| 类型 | 验证项 |
|------|--------|
| 后端单元测试 | `PricePublishServiceTests` 增加跨日期全部发布、同日期多批次、空批次、部分失败、重复发布、通知去重测试 |
| 后端接口测试 | `/api/price-drafts/publishable-summary`、`/api/price-drafts/publish-all`、`/api/price-drafts/by-date/publish` 返回结构和状态码 |
| 前端构建 | `cd frontend && npm run build` |
| 小程序类型检查 | `cd frontend-uniapp && npm run typecheck` |
| 手工冒烟 | PC 价格维护在今天页面发布昨天和今天的多个草稿；小程序同样发布全部待发布草稿；首页“较上期”与 `/price-query` 对照 |
| 数据一致性 | 草稿批次、草稿明细、价格表、发布日志、通知消息数量和状态一致 |

## Notes

- 本文档仅记录审计问题与解决方案，未修改业务代码。
- 当前工作区存在大量既有未提交变更，整改时应按问题分批提交，避免将无关文档、UI 和后端逻辑混在同一提交中。
