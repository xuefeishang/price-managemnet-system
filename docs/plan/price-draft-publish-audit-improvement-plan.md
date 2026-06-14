# 价格草稿全量发布审计改进方案

> 状态：待实施  
> 创建日期：2026-06-14  
> 范围：PC 端价格维护、小程序价格录入、价格草稿全量发布、发布通知跳转、发布结果反馈。

## Context

前一轮整改已将价格维护页的主发布动作从“按当前日期发布”调整为“发布全部待发布草稿”，并新增了：

- `GET /api/price-drafts/publishable-summary`
- `POST /api/price-drafts/publish-all`
- 发布结果汇总字段与批次级结果列表
- 发布组通知 `pricePublishedByGroup`
- PC 与小程序价格维护页的全局待发布摘要和“发布全部草稿”按钮

本次审计发现：主流程方向正确，但仍存在部分失败反馈、批次级事务语义、通知跳转上下文、待发布数量统计和失败统计等问题。用户已明确补充：通知可以跳转到价格查询页面，不需要跳转到某个产品详情页。因此本方案不设计产品详情跳转，只补齐价格查询页所需的查询上下文。

## 目标

1. 发布全部草稿时，用户能准确知道成功、部分失败或失败。
2. 全量发布的事务语义清晰，不出现“看似尽力发布、实际又可能整体回滚”的灰色状态。
3. 发布通知继续跳转价格查询页，但应能定位到本次发布相关日期。
4. 待发布摘要数量与实际草稿明细数量一致。
5. 失败批次的失败条数统计可信，便于前端提示和审计追踪。
6. PC 和小程序行为保持一致。

## 本轮边界

本轮整改采用“最小可靠闭环”，优先解决用户可感知和发布一致性问题。

| 类型 | 内容 |
|------|------|
| 必须实施 | 批次级独立事务、发布结果准确反馈、通知 `date/effectiveDates/publishGroupId` 协议、摘要按明细表统计、失败条数准确统计、关键测试补齐 |
| 暂不实施 | 新增发布组日志表、发布详情独立页面、通知跳产品详情、价格查询页多日期筛选 UI、审批流 |
| 可选增强 | 如果实现批次级独立事务时改动已经触及发布日志模型，可同步为 `PricePublishLog` 增加 `publish_group_id` 字段；否则先通过 DTO、通知 dedupeKey 和 message 追踪 |

验收口径：不要求新增数据库表也能达到 9.5 分，但必须保证用户侧提示准确、成功批次真实生效、失败批次可重试且可追踪。

## 跳转决策

通知跳转遵循以下决策：

| 项 | 决策 |
|----|------|
| 跳转目标 | PC 跳转 `/price-query`；小程序跳转当前承载价格查询能力的历史页 |
| 是否跳产品详情 | 不跳产品详情 |
| 单日期发布 | `linkParams` 携带 `date` |
| 多日期发布 | `linkParams` 至少携带一个默认查询日期，建议为本次发布中最新 `effectiveDate` |
| 扩展字段 | 可同时携带 `effectiveDates` 和 `publishGroupId`，用于后续支持多日期提示或发布组详情 |
| 前端行为 | 有 `date` 时进入价格查询页并定位日期；无 `date` 时进入价格查询页默认视图 |

推荐 `linkParams` 示例：

```json
{
  "date": "2026-06-14",
  "effectiveDates": ["2026-06-13", "2026-06-14"],
  "publishGroupId": "uuid"
}
```

最终通知协议：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `date` | string | 是 | 默认查询日期，取本次成功发布批次中最大的 `effectiveDate` |
| `effectiveDates` | string[] | 是 | 本次成功发布批次涉及的报价日期，按 `effectiveDate ASC` 去重 |
| `publishGroupId` | string | 是 | 本次全量发布 ID，用于通知去重和人工审计关联 |

日期选择规则：

1. 只从成功发布的批次中计算 `date` 和 `effectiveDates`。
2. `date` 取成功发布批次中最大的报价日期 `effectiveDate`，不是批次创建时间，也不是发布时间。
3. 若全量发布没有任何成功批次，不生成价格发布通知。
4. 单日期发布继续使用原有 `date` 协议，保持兼容。

PC 与小程序不得依赖 `publishGroupId` 做页面定位；它只用于审计关联。页面定位统一依赖 `date`。

## 接口与 DTO 最终协议

### `GET /api/price-drafts/publishable-summary`

返回字段保持现有结构，但 `publishableItemCount` 必须按 `price_draft_item` 实际明细数统计，不再直接累加 `PriceDraftBatch.savedItemCount`。

| 字段 | 类型 | 说明 |
|------|------|------|
| `hasPublishableDrafts` | boolean | 是否存在 `DRAFT` 草稿批次 |
| `publishableBatchCount` | number | `DRAFT` 草稿批次数 |
| `publishableItemCount` | number | `DRAFT` 草稿实际明细数 |
| `publishableDateCount` | number | 涉及报价日期数 |
| `effectiveDates` | string[] | 涉及报价日期，按 `effectiveDate ASC` 去重 |
| `publishableBatchIds` | number[] | 可发布批次 ID，按发布时间顺序 |

### `POST /api/price-drafts/publish-all`

`PricePublishResultDTO` 建议补齐以下字段。现有字段继续兼容，新增字段用于消除前端和审计歧义。

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `publishGroupId` | string | 是 | 本次全量发布 ID |
| `status` | `SUCCESS` / `PARTIAL` / `FAILED` | 是 | 全局发布状态 |
| `successCount` | number | 是 | 成功发布明细数 |
| `failCount` | number | 是 | 失败明细数 |
| `attemptedBatchCount` | number | 是 | 本次尝试处理的批次数 |
| `publishedBatchCount` | number | 是 | 完全发布成功的批次数 |
| `failedBatchCount` | number | 是 | 失败或部分失败的批次数 |
| `remainingDraftBatchCount` | number | 是 | 发布完成后仍为 `DRAFT` 的待发布批次数 |
| `attemptedDateCount` | number | 是 | 本次尝试处理的报价日期数 |
| `publishedDateCount` | number | 是 | 至少有成功批次的报价日期数 |
| `effectiveDates` | string[] | 是 | 成功发布涉及的报价日期 |
| `publishLogIds` | number[] | 是 | 本次产生的发布日志 ID |
| `batchResults` | array | 是 | 批次级发布结果 |
| `message` | string | 是 | 全局结果摘要 |

`batchResults` 字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `effectiveDate` | string | 是 | 批次报价日期 |
| `batchId` | number | 是 | 草稿批次 ID |
| `publishLogId` | number | 否 | 发布日志 ID；极端异常未落库时可为空 |
| `status` | `SUCCESS` / `PARTIAL` / `FAILED` | 是 | 批次发布状态 |
| `batchStatus` | `PUBLISHED` / `DRAFT` | 是 | 发布后草稿批次状态 |
| `successCount` | number | 是 | 该批次成功明细数 |
| `failCount` | number | 是 | 该批次失败明细数 |
| `message` | string | 是 | 失败原因或成功摘要 |

状态计算规则：

| 条件 | 全局 `status` |
|------|---------------|
| `successCount > 0 && failCount == 0 && remainingDraftBatchCount == 0` | `SUCCESS` |
| `successCount > 0 && (failCount > 0 || remainingDraftBatchCount > 0)` | `PARTIAL` |
| `successCount == 0` | `FAILED` |

前端只依据 `status` 展示成功、部分成功或失败，不再用 HTTP 成功状态推断业务成功。

## 审计问题

| 编号 | 优先级 | 问题 | 影响 |
|------|--------|------|------|
| PA-01 | P0 | 前端将 `PARTIAL` / `FAILED` 结果也提示为“发布完成” | 用户误以为全部草稿已生效，切换到未发布日期仍会看到可发布提示 |
| PA-02 | P0 | `publishAllDrafts` 使用单个大事务，但代码表现为批次级尽力发布 | 事务语义不清，失败批次缺少独立持久化日志，提交异常时可能整次回滚 |
| PA-03 | P1 | 全量发布通知只携带 `publishGroupId`，缺少价格查询页可用的 `date` | 点击通知能进入价格查询页，但无法定位本次发布日期 |
| PA-04 | P1 | 待发布摘要使用 `PriceDraftBatch.savedItemCount` 冗余字段 | 历史数据或异常场景下可能与实际草稿明细数不一致 |
| PA-05 | P1 | 整批失败时 `failCount` 固定为 1 | 一个包含多条明细的批次失败时，失败条数被低估 |
| PA-06 | P2 | 测试覆盖缺少部分失败、空批次、通知参数和前端结果分支 | 当前测试能覆盖成功主链路，但不能防止上述回归 |

## PA-01：发布结果反馈不区分部分失败

### 现状

PC 端发布后只提示：

```text
发布完成，成功 X 条
```

小程序发布后只提示：

```text
发布完成 X 条
```

两端没有根据后端 `status` 区分 `SUCCESS`、`PARTIAL`、`FAILED`。

### 风险

- 部分草稿失败时，用户仍看到“发布完成”。
- 未发布批次仍保持 `DRAFT`，切换到对应日期后仍会提示可发布，造成“刚才不是已经发布了吗”的体验问题。
- 运维排查需要从后台日志反推，前端没有给出失败批次和失败条数。

### 方案

PC 与小程序统一根据 `PricePublishResult.status` 展示：

| 状态 | 前端提示 |
|------|----------|
| `SUCCESS` | `发布成功，共 X 条` |
| `PARTIAL` | `部分发布成功：成功 X 条，失败 Y 条，仍有 N 个草稿批次待处理` |
| `FAILED` | `发布失败：Y 条未发布，请查看失败原因后重试` |

同时：

- 发布后无论成功或部分失败，都刷新当前日期价格、当前日期草稿、全局待发布摘要。
- `PARTIAL` 时保留通知角标刷新，但提示文案不得写“全部完成”。
- 可在 PC 端确认弹窗或结果区域展示 `batchResults` 的失败批次日期、批次 ID 和 message。
- 小程序可先用 toast/modal 展示摘要，详情页后续再增强。

## PA-02：全量发布事务语义需要明确

### 现状

`publishAllDrafts` 在一个事务内锁定所有 DRAFT 批次，并逐批调用 `publishLockedBatch`。单批次运行异常会被 catch 成失败结果，但失败批次没有独立发布日志；如果事务提交阶段或循环外发生异常，整次发布仍可能回滚。

### 决策

建议将全量发布定义为：

> 发布全部草稿是批次级尽力发布。每个草稿批次独立提交，成功批次立即生效，失败批次保留为 DRAFT 并产生可审计记录。

这个语义最贴合“把所有保存的草稿一起生效”的操作预期，也能避免一个异常批次阻塞其他日期或其他产品。

### 方案

后端调整为批次级独立事务：

1. `publishAllDrafts` 负责生成 `publishGroupId`、查询待发布批次 ID、汇总结果。
2. 新增批次发布执行器，例如 `PriceDraftBatchPublishExecutor`。
3. 执行器方法使用 `@Transactional(propagation = REQUIRES_NEW)`，按 `batchId` 加写锁并发布单批次。
4. 每个批次无论成功、部分失败或失败，都尽量产生一条 `PricePublishLog`。
5. 外层聚合 `batchResults`，并根据成功/失败数量计算全局状态。

注意：不要在同一个 Spring Bean 内通过自调用实现 `REQUIRES_NEW`，否则事务增强不会生效。应拆到独立 Bean，或通过代理调用。

可选增强：

- 新增发布组日志表，用于记录一次全量发布的总状态、发起人、日期数、批次数、成功条数、失败条数。
- 如果暂不加表，也应保证 `publishGroupId` 返回给前端，并写入通知去重键和日志 message，便于人工追踪。

### 并发规则

两个用户或两个端同时点击“发布全部”时，按以下规则处理：

1. 外层先查询当前所有 `DRAFT` 批次 ID，作为本次发布尝试集合。
2. 每个批次进入 `REQUIRES_NEW` 执行器后重新按 `batchId` 加写锁并读取最新状态。
3. 如果批次已变为 `PUBLISHED`，该批次记为 `SKIPPED` 或等价的成功跳过结果，不重复写价格。
4. 如果批次仍为 `DRAFT`，正常发布。
5. 如果批次为 `PUBLISHING`，返回批次失败结果，message 为“草稿批次正在发布，请稍后刷新查看结果”。
6. 如果进入接口时已经没有任何 `DRAFT` 批次，返回 400：“暂无可发布草稿”，前端提示同文案。

本轮可不在 DTO 中新增 `SKIPPED` 枚举，若保持现有 `PublishStatus`，已发布跳过批次可以不计入 `attemptedBatchCount`，或计入 `batchResults` 且 `message` 写明“已被其他发布请求处理”。实现时必须选择一种并在测试中固定，推荐不计入本次尝试结果，避免用户看到“成功发布 0 条”的噪音批次。

### 批次状态流转

| 场景 | 批次最终状态 | 明细状态 | 发布日志 |
|------|--------------|----------|----------|
| 全部明细成功 | `PUBLISHED` | 全部 `PUBLISHED` | `SUCCESS` |
| 部分明细失败 | `DRAFT` | 成功明细保留 `PUBLISHED`，失败明细保持 `DRAFT` | `PARTIAL` |
| 批次空明细 | `DRAFT` | 无 | `FAILED`，失败条数为 0，失败批次数为 1 |
| 批次级异常 | `DRAFT` | 未成功写入的明细保持原状态 | `FAILED`，失败条数按实际明细数计算 |

## PA-03：通知跳转价格查询页需要携带日期上下文

### 现状

全量发布通知 `pricePublishedByGroup` 设置：

```json
{
  "publishGroupId": "uuid"
}
```

PC 通知点击逻辑会跳转 `/price-query`，但主要读取 `linkParams.date`。小程序通知页也会读取 `date` 并写入 `notificationTargetHistoryDate` 后跳转历史页。

### 方案

后端 `pricePublishedByGroup` 增加查询日期参数：

- `date`：本次发布中最新的有效日期，作为默认落点。
- `effectiveDates`：本次发布涉及的所有日期，便于后续展示。
- `publishGroupId`：保留，用于审计关联。

PC 端：

- 继续跳转 `/price-query`。
- 如果 `linkParams.date` 存在，使用 `?date=YYYY-MM-DD`。
- 不跳产品详情页。

小程序：

- 继续跳转历史页或未来价格查询页。
- 如果 `linkParams.date` 存在，写入 `notificationTargetHistoryDate`。
- 不跳产品详情页。

### 验证

- 全量发布 2 个日期后，通知只有一条。
- 点击 PC 通知进入 `/price-query?date=最新发布日期`。
- 点击小程序通知进入历史页，并定位到最新发布日期。
- 通知内容仍包含“发布 X 个日期、Y 个草稿批次、Z 条价格”。

### 兼容性

- 旧通知只有 `date` 时，PC 和小程序按原逻辑跳转。
- 新通知有 `date/effectiveDates/publishGroupId` 时，PC 和小程序仍只用 `date` 做定位。
- 旧通知只有 `publishGroupId` 且没有 `date` 时，进入价格查询默认视图，不报错。
- 微信小程序订阅消息 page 不强制携带日期参数；站内通知点击时再通过 `linkParams.date` 定位。

## PA-04：待发布摘要应按明细表统计

### 现状

`publishable-summary` 使用 `PriceDraftBatch.savedItemCount` 汇总 `publishableItemCount`。

### 风险

`savedItemCount` 是批次冗余字段，一旦历史数据出现漂移，确认弹窗显示的条数就会与实际发布条数不一致。

### 方案

后端改为按草稿明细实际计数：

1. 在 `PriceDraftItemRepository` 增加按批次集合聚合统计的方法。
2. `buildPublishableSummary` 使用实际明细数汇总。
3. 对空明细 DRAFT 批次，摘要中可以计入批次数，但明细数为 0；发布时应返回失败批次详情。

建议 Repository 方法：

```java
@Query("SELECT i.batchId, COUNT(i) FROM PriceDraftItem i WHERE i.batchId IN :batchIds GROUP BY i.batchId")
List<Object[]> countItemsByBatchIds(@Param("batchIds") Collection<Long> batchIds);
```

如项目偏好类型安全，可定义 projection DTO 或 interface projection。

## PA-05：失败批次统计需要反映实际失败明细

### 现状

`failedBatchResult` 将整批失败的 `failCount` 固定为 1。

### 风险

一个包含 100 条明细的草稿批次整体失败时，全局结果只显示失败 1 条，误导用户和审计。

### 方案

失败批次结果至少应包含：

- `failedBatchCount`：失败批次数。
- `failCount`：失败明细数，优先取实际明细数量；取不到时再用 `savedItemCount`；仍取不到时兜底为 1。
- `message`：保留具体异常。

如果暂不扩展 DTO，可先将 `failCount` 从固定 1 改为实际明细数，同时在 `message` 中标明“批次发布失败”。

失败条数计算优先级：

1. 已读取到草稿明细列表时，使用明细列表数量。
2. 未读取明细但能查询 `countByBatchId` 时，使用实际 count。
3. 数据库查询也失败时，使用 `savedItemCount`。
4. `savedItemCount` 为空或小于 0 时，兜底为 1。

空批次特殊处理：`failCount = 0`，`failedBatchCount = 1`，message 为“草稿批次没有可发布明细”。这样避免把不存在的明细计为失败条数，同时仍能表达批次失败。

## 前端交互细则

### PC 价格维护页

发布按钮：

| 条件 | 状态 |
|------|------|
| `publishing || saving` | 禁用 |
| `hasChanges` | 禁用，并提示“请先保存当前修改” |
| `!hasPublishableDrafts` | 禁用或点击提示“暂无可发布草稿” |
| 其他 | 可点击 |

确认弹窗：

```text
将发布所有已保存但未发布的价格草稿，涉及 X 个日期、Y 个草稿批次、Z 条价格。发布后成功批次将立即生效，失败批次可修正后重试。
```

发布结果：

| 状态 | UI |
|------|----|
| `SUCCESS` | toast：`发布成功，共 X 条` |
| `PARTIAL` | modal 或醒目提示：`部分发布成功：成功 X 条，失败 Y 条，仍有 N 个草稿批次待处理`，并展示失败批次日期、批次 ID、原因 |
| `FAILED` | modal：`发布失败：Y 条未发布`，展示失败原因 |

刷新动作：

1. 刷新当前日期价格。
2. 刷新当前日期草稿。
3. 刷新全局待发布摘要。
4. 刷新通知角标。

### 小程序价格录入页

小程序与 PC 使用相同状态判断，但展示方式更轻量：

| 状态 | UI |
|------|----|
| `SUCCESS` | toast：`发布成功 X 条` |
| `PARTIAL` | modal：标题 `部分发布成功`，内容显示成功条数、失败条数、剩余批次数 |
| `FAILED` | modal：标题 `发布失败`，内容显示失败条数和首个失败原因 |

小程序发布后同样刷新页面数据、全局待发布摘要和通知角标。`PARTIAL` 和 `FAILED` 不显示“通知已生成”这类可能误导为全部成功的文案；仅当后端实际生成通知或 `successCount > 0` 时提示“已生成价格更新通知”。

## PA-06：补充测试覆盖

### 后端单元测试

在 `PricePublishServiceTests` 或拆分后的执行器测试中补充：

1. 全量发布中一个批次成功、一个批次失败，返回 `PARTIAL`。
2. 失败批次保留 `DRAFT`，成功批次为 `PUBLISHED`。
3. 失败批次产生可审计结果，`failCount` 等于实际明细数。
4. 空明细批次不阻塞其他批次发布。
5. 全量发布通知 `linkParams` 包含 `date`、`effectiveDates`、`publishGroupId`。
6. `publishable-summary` 使用实际明细数，而不是 `savedItemCount`。

### 前端验证

PC：

- mock `SUCCESS`、`PARTIAL`、`FAILED` 三种响应，检查 toast/modal 文案。
- 点击发布后刷新当前价格、当前草稿、全局摘要。
- 通知点击仍进入 `/price-query`，带 `date` 时定位日期。

小程序：

- mock `SUCCESS`、`PARTIAL`、`FAILED` 三种响应，检查 toast/modal 文案。
- 通知点击进入历史页，并通过 `notificationTargetHistoryDate` 定位日期。

## 实施步骤

1. 后端明确全量发布为批次级尽力发布，拆出独立事务执行器。
2. 后端完善失败批次日志与 `failCount` 统计。
3. 后端将 `publishable-summary` 的明细数改为实际明细统计。
4. 后端 `pricePublishedByGroup` 增加 `date` 和 `effectiveDates`。
5. 后端补齐 `PricePublishResultDTO` 字段：`failedBatchCount`、`remainingDraftBatchCount`、`effectiveDates`。
6. 后端补充并发发布、空批次、部分失败、通知 linkParams 测试。
7. PC 价格维护页按 `SUCCESS`、`PARTIAL`、`FAILED` 展示不同结果。
8. 小程序价格维护页同步 PC 的发布结果反馈。
9. PC 和小程序通知跳转保持价格查询/历史页目标，不跳产品详情页。
10. 执行后端测试、PC 构建、小程序类型检查。

## Verification

| 类型 | 验证项 |
|------|--------|
| 后端测试 | `mvn -Dtest=PricePublishServiceTests test` 或新增执行器测试通过 |
| PC 构建 | `npm run build` 通过 |
| 小程序类型 | `npm run typecheck` 通过 |
| 手工冒烟 | 昨天、今天各保存一个草稿，在今天页面点击发布，两个日期成功批次均生效 |
| 部分失败 | 人为制造一个失败批次，成功批次仍发布，前端提示 `PARTIAL` |
| 摘要准确性 | 修改 `savedItemCount` 与明细数不一致时，摘要以明细表统计为准 |
| 通知跳转 | 点击发布组通知进入价格查询页或小程序历史页，并定位到最新发布日期 |
| 并发发布 | 两个请求同时发布同一批 DRAFT，不重复写价格，第二个请求不误报全部成功 |
| 空批次 | 空明细 DRAFT 不阻塞其他批次，结果中体现失败批次但失败条数为 0 |

## 非目标

- 不设计跳转到产品详情页。
- 不重构价格查询页主体功能。
- 不调整首页“较上期”展示口径。
- 不处理小程序产品名称产地展示的既有整改内容。
- 不引入审批流或发布前复核流程。
