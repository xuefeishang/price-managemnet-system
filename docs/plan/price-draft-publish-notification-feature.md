# 价格保存-发布-通知功能方案

## Context

当前 `http://localhost:5173/price-maintenance` 的价格维护页面只有“保存”动作，前端通过 `addProductPrice` / `updatePrice` 调用后端 `/api/products/{productId}/prices`、`/api/prices/{id}`，后端会直接写入 `price` 表。`price` 表数据会立即被价格查询、首页、外部 API、uni-app 页面读取，因此无法满足“录入后暂存，但其他用户暂不可见”的业务要求。

本次需要把价格生命周期拆成三段：

1. 维护人员保存草稿：数据进入数据库，但不影响其他用户可见价格。
2. 维护人员手动发布，或系统定时任务自动发布：草稿价格正式写入生效价格。
3. 发布完成后触发通知：站内通知、App、小程序收到“价格已更新，可以查看”的消息。

核心设计原则：

- `price` 表继续代表“已发布、对外可见、生效价格”，尽量不破坏现有查询、首页、外部 API 逻辑。
- 新增草稿/发布批次表承载未发布数据，避免在 `price` 表里用状态字段与现有唯一约束、历史查询逻辑缠在一起。
- 发布动作必须是后端事务，价格生效、历史记录、发布记录、通知事件要么一起成功，要么一起回滚。
- 中文显示名称继续通过字典服务获取；前端新增状态、渠道、任务类型等编码展示时必须扩展字典分类。
- 定时任务使用独立通用任务配置表和任务管理页面，字典只负责状态、类型等编码显示，不承载 cron 和运行态数据。

## 技术定版

针对当前“开发环境无 Redis、生产 Docker 环境已部署 Redis”的实际情况，本方案采用以下定版策略：

1. **调度锁主方案：数据库行锁，不依赖 Redis。**  
   开发环境和生产环境都必须可运行，因此通用定时任务调度使用数据库事务 + 行级锁作为一期方案。Redis 可作为后续性能增强，但不得成为价格发布和定时任务的必需依赖。

2. **多实例防重复执行：基于任务行锁和执行窗口。**  
   调度器扫描到任务后，通过 `SELECT ... FOR UPDATE` 或 JPA `PESSIMISTIC_WRITE` 锁定 `sys_scheduled_task` 行，检查 `next_run_time <= now` 且最近执行窗口未处理，再创建 `sys_scheduled_task_log`。同一任务同一计划时间只允许一个实例执行。

3. **审批能力：本期不启用，预留独立 `PRICE_PUBLISH` 扩展点。**  
   本期价格发布不触发审批，点击发布后直接进入发布事务。为未来接入审批流，数据状态、字典和服务接口预留 `PRICE_PUBLISH` 语义；未来不复用 `PRICE_CHANGE`，`PRICE_CHANGE` 继续表示单条价格变更审批，`PRICE_PUBLISH` 表示整批价格发布审批。

4. **通知推送一期范围：站内通知完整可用，App/小程序外部推送可配置启用。**  
   一期必须落地 `IN_APP`。`APP_PUSH`、`MINI_PROGRAM` 先通过 Provider 接口预留，未配置推送凭证、用户未绑定设备或未订阅模板时，`notification_delivery_log.status=SKIPPED`，不影响发布成功。

5. **默认任务初始化：默认停用。**  
   迁移脚本初始化 `PRICE_AUTO_PUBLISH` 默认任务，但 `enabled=false`。由管理员在“定时任务”页面确认 cron、发布日期偏移和通知接收人后手动启用，避免生产升级后自动发布历史草稿。

6. **前端任务配置：`PRICE_PUBLISH` 使用结构化表单。**  
   管理员不需要手写 JSON；页面将结构化表单值序列化为 `config_json`。未知任务类型才降级为 JSON 编辑。

## 实现方案

### 1. 数据模型

新增 `price_draft_batch`：价格草稿批次。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| effective_date | DATE | 报价日期 |
| status | VARCHAR(20) | 草稿批次状态：DRAFT、PENDING_APPROVAL、APPROVED、REJECTED、PUBLISHING、PUBLISHED、CANCELLED |
| source_type | VARCHAR(20) | 来源：MANUAL、SCHEDULED |
| product_scope_snapshot | TEXT | 发布范围快照，JSON 数组，记录批次创建/首次保存时的产品 ID 范围 |
| item_count | INT | 批次产品范围数量 |
| saved_item_count | INT | 已保存草稿明细数量 |
| last_modified_by | BIGINT | 最近修改人 |
| published_time | DATETIME | 发布时间 |
| published_by | BIGINT | 发布人；定时任务可为系统用户或 0 |
| created_by | BIGINT | 创建人 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |
| remark | VARCHAR(500) | 备注 |

新增 `price_draft_item`：价格草稿明细。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| batch_id | BIGINT | 草稿批次 ID |
| product_id | BIGINT | 产品 ID |
| base_price_id | BIGINT | 编辑时基于的已发布价格 ID，用于并发提示 |
| base_price_version | BIGINT | 编辑时基于的已发布价格版本 |
| original_price | DECIMAL(15,4) | 原价 |
| current_price | DECIMAL(15,4) | 当前售价 |
| cost_price | DECIMAL(15,4) | 成本价 |
| budget_price | DECIMAL(15,4) | 预算价 |
| effective_date | DATE | 生效日期 |
| expiry_date | DATE | 失效日期 |
| unit | VARCHAR(50) | 单位 |
| price_spec | VARCHAR(200) | 规格说明 |
| item_status | VARCHAR(20) | 明细状态：DRAFT、PUBLISHED、SKIPPED |
| last_modified_by | BIGINT | 最近修改人 |
| published_price_id | BIGINT | 发布后对应 `price.id` |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

约束建议：

- `price_draft_batch` 增加普通索引：`idx_price_draft_batch_date_status(effective_date, status)`。
- MySQL 不适合直接做“同一天只能有一个未结束批次”的条件唯一索引，因此应用层在创建/保存时用事务锁保证同一天只能存在一个 `DRAFT/PENDING_APPROVAL/APPROVED/PUBLISHING` 活动批次。
- `price_draft_item` 增加唯一约束：`uk_price_draft_item_batch_product(batch_id, product_id)`。
- `price_draft_item.product_id` 外键关联 `product.id`，`batch_id` 外键关联 `price_draft_batch.id`。

草稿并发策略：

- 同一报价日期采用“一个活动批次，多人协作编辑”的模型，不按用户创建多个草稿批次。
- 批次保存时使用 `price_draft_batch.version` 乐观锁；如果版本冲突，后端返回 409，并提示前端刷新后再保存。
- 明细保存使用 `product_id` 粒度覆盖：同一产品最后一次保存为准，并记录 `last_modified_by`、`updated_time`。
- 前端保存前如果发现 `base_price_version` 与当前已发布 `price.version` 不一致，提示“该产品已被发布更新，请刷新后再编辑”，避免基于旧价覆盖。
- 页面要展示最近保存人和最近保存时间，降低多人录入时的误操作概率。

发布产品范围快照：

- 首次创建某日草稿批次时，后端读取当时所有 ACTIVE 产品 ID，写入 `product_scope_snapshot`。
- 完整性校验以 `product_scope_snapshot` 为准，而不是发布瞬间的 ACTIVE 产品集合，避免产品状态变化导致“是否完整”口径漂移。
- 如果批次创建后新增产品，该产品不强制进入当前批次；下一报价日自动纳入新批次。管理员可提供“刷新产品范围”操作作为后续增强。

新增 `price_publish_log`：发布日志。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| batch_id | BIGINT | 草稿批次 ID |
| effective_date | DATE | 发布日期 |
| publish_type | VARCHAR(20) | MANUAL、SCHEDULED |
| status | VARCHAR(20) | SUCCESS、FAILED、PARTIAL |
| total_count | INT | 草稿总数 |
| success_count | INT | 成功数 |
| fail_count | INT | 失败数 |
| message | TEXT | 发布结果 |
| created_by | BIGINT | 操作人 |
| created_time | DATETIME | 创建时间 |

新增通知表，作为系统内部通知机制基础能力。

`notification_message`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| type | VARCHAR(50) | 通知类型，如 PRICE_PUBLISHED |
| title | VARCHAR(100) | 标题 |
| content | TEXT | 内容 |
| business_type | VARCHAR(50) | PRICE |
| business_id | BIGINT | 可关联 batchId 或 publishLogId |
| channels | VARCHAR(200) | JSON 数组：IN_APP、APP_PUSH、MINI_PROGRAM |
| created_by | BIGINT | 创建人 |
| created_time | DATETIME | 创建时间 |

`notification_recipient`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| message_id | BIGINT | 通知消息 ID |
| user_id | BIGINT | 接收用户 |
| read_status | VARCHAR(20) | UNREAD、READ |
| read_time | DATETIME | 阅读时间 |

`notification_delivery_log`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| message_id | BIGINT | 通知消息 ID |
| recipient_id | BIGINT | `notification_recipient.id` |
| user_id | BIGINT | 接收用户 |
| channel | VARCHAR(50) | 投递渠道：IN_APP、APP_PUSH、MINI_PROGRAM |
| status | VARCHAR(20) | 投递状态：PENDING、SUCCESS、FAILED、SKIPPED |
| provider | VARCHAR(50) | 推送服务商，如 IN_APP、UNI_PUSH、WECHAT_MINI |
| provider_message_id | VARCHAR(100) | 第三方消息 ID |
| retry_count | INT | 重试次数 |
| delivered_time | DATETIME | 投递完成时间 |
| error_code | VARCHAR(100) | 错误编码 |
| error_message | VARCHAR(500) | 错误信息 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

通知投递模型说明：

- `notification_message` 表示一条业务通知。
- `notification_recipient` 表示某个用户是否已读。
- `notification_delivery_log` 表示某个用户在某个渠道上的投递结果。
- 这样可以表达“站内成功、App 失败、小程序未授权跳过”的真实状态，避免一个 `delivered_status` 混淆多个渠道。

新增通用定时任务表，作为系统级调度能力，价格自动发布只是其中一种任务。

`sys_scheduled_task`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| task_code | VARCHAR(80) | 任务编码，如 PRICE_AUTO_PUBLISH |
| task_name | VARCHAR(100) | 任务名称 |
| task_type | VARCHAR(50) | 任务类型，如 PRICE_PUBLISH、NOTIFICATION_RETRY、DATA_CLEANUP |
| cron_expression | VARCHAR(100) | cron 表达式 |
| timezone | VARCHAR(50) | 时区，如 Asia/Shanghai |
| enabled | BOOLEAN | 是否启用 |
| config_json | TEXT | 任务参数 JSON |
| lock_until | DATETIME | 任务锁过期时间，防止执行进程异常退出后永久锁定 |
| locked_by | VARCHAR(100) | 持有锁的实例标识 |
| last_scheduled_time | DATETIME | 最近一次计划触发时间 |
| last_run_time | DATETIME | 最近执行时间 |
| next_run_time | DATETIME | 下次预计执行时间 |
| last_run_status | VARCHAR(20) | 最近执行状态：SUCCESS、FAILED、SKIPPED |
| created_by | BIGINT | 创建人 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |
| version | BIGINT | 乐观锁 |
| remark | VARCHAR(500) | 备注 |

`sys_scheduled_task_log`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT | 主键 |
| task_id | BIGINT | 任务 ID |
| task_code | VARCHAR(80) | 任务编码冗余，便于审计查询 |
| scheduled_time | DATETIME | 本次计划触发时间 |
| trigger_type | VARCHAR(20) | 触发方式：SCHEDULED、MANUAL_TEST、MANUAL_RUN |
| status | VARCHAR(20) | 执行状态：RUNNING、SUCCESS、FAILED、SKIPPED |
| started_time | DATETIME | 开始时间 |
| finished_time | DATETIME | 结束时间 |
| duration_ms | BIGINT | 执行耗时 |
| business_type | VARCHAR(50) | 业务类型，如 PRICE_PUBLISH |
| business_id | BIGINT | 关联业务 ID，如 publishLogId |
| message | TEXT | 执行摘要 |
| error_stack | TEXT | 失败堆栈或错误详情 |
| created_time | DATETIME | 创建时间 |

调度表约束建议：

- `sys_scheduled_task.task_code` 唯一。
- `sys_scheduled_task_log` 增加唯一约束：`uk_task_scheduled_time(task_id, scheduled_time, trigger_type)`，保证同一任务同一计划时间不会重复执行。
- `lock_until` 使用短 TTL，例如 5 分钟；任务执行中按需续租，异常退出后其他实例可在锁过期后接管。

### 2. 字典设计

新增字典分类：

| 分类 | 说明 | 示例 Key |
| --- | --- | --- |
| price_draft_status | 价格草稿状态 | DRAFT、PENDING_APPROVAL、APPROVED、REJECTED、PUBLISHING、PUBLISHED、CANCELLED |
| price_publish_type | 发布类型 | MANUAL、SCHEDULED |
| price_publish_status | 发布结果 | SUCCESS、FAILED、PARTIAL |
| notification_type | 通知类型 | PRICE_PUBLISHED |
| notification_channel | 通知渠道 | IN_APP、APP_PUSH、MINI_PROGRAM |
| notification_read_status | 阅读状态 | UNREAD、READ |
| notification_delivery_status | 推送状态 | PENDING、SUCCESS、FAILED、SKIPPED |
| scheduled_task_type | 定时任务类型 | PRICE_PUBLISH、NOTIFICATION_RETRY、DATA_CLEANUP |
| scheduled_task_trigger_type | 定时任务触发方式 | SCHEDULED、MANUAL_TEST、MANUAL_RUN |
| scheduled_task_run_status | 定时任务执行状态 | RUNNING、SUCCESS、FAILED、SKIPPED |

价格自动发布不再放入字典项，而是在 `sys_scheduled_task` 中创建任务：

```json
{
  "taskCode": "PRICE_AUTO_PUBLISH",
  "taskName": "价格自动发布",
  "taskType": "PRICE_PUBLISH",
  "cronExpression": "0 0 9 * * ?",
  "timezone": "Asia/Shanghai",
  "enabled": false,
  "configJson": {
    "dateOffsetDays": -1,
    "publishOnlyCompleteDraft": false,
    "notifyChannels": ["IN_APP", "APP_PUSH", "MINI_PROGRAM"],
    "recipientRoles": ["ADMIN", "EDITOR", "VIEWER"]
  }
}
```

说明：

- `dateOffsetDays=-1` 表示每天 09:00 发布昨天报价。
- `publishOnlyCompleteDraft=true` 时，只有所有 ACTIVE 产品都有草稿价格才自动发布。
- 字典只维护 `taskType`、执行状态、触发方式等显示名称；cron、启停、最近执行时间、下次执行时间、任务参数全部由定时任务管理页面维护。
- 默认初始化任务 `enabled=false`，需要管理员确认配置后启用。

### 3. 后端接口

新增 `PriceDraftController`，路径建议统一放在 `/api/price-drafts`。

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/price-drafts/by-date?date=yyyy-MM-dd` | 查询某日草稿批次与明细 | ADMIN、EDITOR |
| POST | `/api/price-drafts/batch-save` | 批量保存草稿 | ADMIN、EDITOR |
| POST | `/api/price-drafts/{batchId}/publish` | 手动发布草稿 | ADMIN、EDITOR |
| POST | `/api/price-drafts/by-date/publish?date=yyyy-MM-dd` | 按日期发布草稿 | ADMIN、EDITOR |
| POST | `/api/price-drafts/{batchId}/cancel` | 取消草稿批次 | ADMIN、EDITOR |
| GET | `/api/price-publish-logs` | 查询发布日志 | ADMIN、EDITOR、VIEWER |

新增通用 `ScheduledTaskController`，路径建议统一放在 `/api/scheduled-tasks`。

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | `/api/scheduled-tasks` | 分页查询定时任务 | ADMIN |
| GET | `/api/scheduled-tasks/{id}` | 查询任务详情 | ADMIN |
| POST | `/api/scheduled-tasks` | 创建任务 | ADMIN |
| PUT | `/api/scheduled-tasks/{id}` | 更新任务配置 | ADMIN |
| POST | `/api/scheduled-tasks/{id}/enable` | 启用任务 | ADMIN |
| POST | `/api/scheduled-tasks/{id}/disable` | 停用任务 | ADMIN |
| POST | `/api/scheduled-tasks/{id}/run-once` | 手动执行一次 | ADMIN |
| POST | `/api/scheduled-tasks/{id}/test` | 校验 cron 和参数，不写业务数据 | ADMIN |
| GET | `/api/scheduled-tasks/{id}/logs` | 查询任务执行日志 | ADMIN |

`batch-save` 请求示例：

```json
{
  "effectiveDate": "2026-06-03",
  "batchId": 12,
  "batchVersion": 3,
  "items": [
    {
      "productId": 1,
      "basePriceId": 88,
      "basePriceVersion": 5,
      "currentPrice": 680.50,
      "budgetPrice": 700.00,
      "unit": "TON",
      "priceSpec": "主流粉矿"
    }
  ]
}
```

返回示例：

```json
{
  "batchId": 12,
  "effectiveDate": "2026-06-03",
  "status": "DRAFT",
  "version": 4,
  "itemCount": 20,
  "savedItemCount": 18,
  "savedCount": 18
}
```

发布响应示例：

```json
{
  "batchId": 12,
  "publishLogId": 33,
  "status": "SUCCESS",
  "batchStatus": "PUBLISHED",
  "successCount": 18,
  "failCount": 0,
  "notificationMessageId": 101
}
```

未来启用审批后的发布接口返回示例，本期不实现该分支：

```json
{
  "batchId": 12,
  "batchStatus": "PENDING_APPROVAL",
  "approvalRequestId": 56,
  "message": "价格发布已提交审批"
}
```

接口错误约定：

- 批次版本冲突：HTTP 409，提示刷新后重试。
- 批次状态不允许编辑或发布：HTTP 400，返回当前状态。
- 草稿不完整且要求完整发布：HTTP 400，返回缺失产品 ID 列表。
- 重复发布或正在发布：HTTP 409，返回已有 `publishLogId` 或当前 `PUBLISHING` 状态。

保留现有 `/api/products/{productId}/prices`、`/api/prices/{id}` 用于兼容已有调用，但 PC 和 uni-app 的价格维护页面改用草稿接口。外部 API 写价格能力要单独评估：默认仍直接走现有接口，若要求也纳入发布流程，再新增外部草稿写入接口，避免破坏外部调用方预期。

### 4. 发布服务

新增 `PricePublishService`：

1. 根据 `batchId` 或 `effectiveDate` 查询 DRAFT 批次。
2. 锁定批次，防止重复发布。可使用 `@Version` 乐观锁，或 Repository 查询时加 `PESSIMISTIC_WRITE`。
3. 校验状态，本期只允许 `DRAFT` 执行发布；`APPROVED` 为未来审批通过后的可发布状态。
4. 将批次置为 `PUBLISHING`，防止重复点击或定时任务并发触发。
5. 遍历草稿明细，复用 `PriceService.doSavePrice(product, price, oldPrice)` 写入正式 `price`。
6. 更新 `price_draft_item.item_status=PUBLISHED`、`published_price_id`。
7. 更新 `price_draft_batch.status=PUBLISHED`、`published_time`、`published_by`。
8. 写入 `price_publish_log`。
9. 调用 `NotificationService.createPricePublishedNotification(...)`。
10. 提交事务后异步执行 App/小程序推送，避免第三方推送失败回滚价格发布。

草稿批次状态机：

```text
DRAFT
  ├─ 保存草稿 -> DRAFT
  ├─ 取消 -> CANCELLED
  ├─ 本期发布 -> PUBLISHING -> PUBLISHED
  └─ 未来审批发布 -> PENDING_APPROVAL

PENDING_APPROVAL
  ├─ 审批通过 -> APPROVED
  ├─ 审批拒绝 -> REJECTED
  └─ 撤回 -> DRAFT

APPROVED
  └─ 执行发布 -> PUBLISHING -> PUBLISHED

PUBLISHING
  ├─ 发布成功 -> PUBLISHED
  └─ 发布失败 -> DRAFT 或 APPROVED，并写失败日志
```

状态边界：

- `PUBLISHED/CANCELLED/REJECTED` 为终态，不允许继续编辑。
- `PENDING_APPROVAL/APPROVED/PUBLISHING` 不允许保存草稿明细，避免审批内容和实际发布内容不一致；其中 `PENDING_APPROVAL/APPROVED` 为未来审批预留状态，本期不会主动进入。
- 发布失败回退到哪个状态取决于进入发布前状态：从 `DRAFT` 发布失败回 `DRAFT`，从 `APPROVED` 发布失败回 `APPROVED`。

注意：

- 发布后才同步 `product.selling_price`，草稿保存不得改产品当前售价。
- 发布后才写 `price_history`，草稿保存不写价格历史，避免历史记录对用户造成“已生效”的误解。
- `PriceQueryService`、首页、外部查询默认继续只读 `price`，自然只能看到已发布价格。
- 价格维护页面需要同时展示“已发布价格”和“草稿价格”：维护人员编辑时优先显示草稿；无草稿时显示已发布价格。

### 5. 定时自动发布

新增通用 `ScheduledTaskDispatcher`，价格自动发布由 `PriceAutoPublishTaskHandler` 执行。

实现方式：

- 使用 `@Scheduled(fixedDelay = 60000)` 每分钟扫描 `sys_scheduled_task.enabled=true` 的任务。
- 使用 Spring `CronExpression` 基于 `cron_expression`、`timezone`、`last_run_time` 判断是否命中。
- 命中后在事务内锁定 `sys_scheduled_task` 行，检查 `last_scheduled_time` 和 `sys_scheduled_task_log` 唯一约束，确认未执行后创建 `sys_scheduled_task_log`，状态先置为 RUNNING。
- 根据 `task_type` 分发到对应 Handler：`PRICE_PUBLISH` -> `PriceAutoPublishTaskHandler`。
- Handler 解析 `config_json`，计算目标报价日期：`targetDate = today + dateOffsetDays`。
- Handler 调用 `PricePublishService.publishByDate(targetDate, PublishType.SCHEDULED, systemUserId)`。
- 执行完成后更新 `sys_scheduled_task.last_run_time`、`next_run_time`、`last_run_status`，并更新执行日志。
- 多实例部署使用数据库行锁 + `lock_until` + 执行日志唯一约束，避免同一任务被多个后端实例重复执行。

锁策略：

- 一期不依赖 Redis 锁，开发环境和生产环境统一走数据库锁。
- 获取锁：事务内查询任务行并加 `PESSIMISTIC_WRITE`，如果 `lock_until` 为空或早于当前时间，则写入新的 `lock_until` 和 `locked_by`。
- 防重复：同一 `task_id + scheduled_time + trigger_type` 插入执行日志时依赖唯一约束兜底。
- 释放锁：执行完成后清空 `locked_by`，或将 `lock_until` 置为当前时间。
- 异常恢复：如果应用崩溃，锁到期后其他实例可重新扫描；若日志已是 SUCCESS，则跳过。

失败策略：

- 没有草稿：写发布日志 `FAILED`，不发价格更新通知，可发给 ADMIN/EDITOR 一条“自动发布未执行：无草稿”站内通知。
- 草稿不完整且 `publishOnlyCompleteDraft=true`：写发布日志 `FAILED`，通知维护人员补全。
- 第三方 App/小程序推送失败：价格发布保持成功，`notification_delivery_log.status=FAILED`，后台可重试。

任务管理页面通用能力：

- 列表展示：任务名称、任务类型、cron、时区、启停状态、最近执行状态、最近执行时间、下次执行时间。
- 新增/编辑：基础信息、cron 表达式、时区、启停、任务参数 JSON。
- cron 辅助：提供常用频率快捷选择，并展示下 5 次执行时间。
- 参数表单：按 `task_type` 切换参数编辑器。`PRICE_PUBLISH` 使用结构化表单维护 `dateOffsetDays`、`publishOnlyCompleteDraft`、通知渠道、接收角色；未知任务类型降级为 JSON 编辑。
- 操作按钮：启用、停用、手动执行一次、测试配置、查看执行日志。
- 执行日志抽屉：展示每次执行状态、耗时、业务结果、错误详情。

任务类型扩展设计：

- 定义 `ScheduledTaskHandler` 接口：`supports(taskType)`、`validate(configJson)`、`execute(task, triggerType)`。
- 每种任务类型只实现自己的 Handler，不改调度器。
- 后续可扩展 `NOTIFICATION_RETRY`、`API_LOG_ARCHIVE`、`CAPTCHA_CLEANUP`、`STYLE_CACHE_REFRESH` 等任务。

`PRICE_PUBLISH` 参数表单：

| 字段 | 类型 | 默认值 | 校验 |
| --- | --- | --- | --- |
| dateOffsetDays | number | -1 | -30 到 7 之间的整数 |
| publishOnlyCompleteDraft | boolean | false | 布尔值 |
| notifyChannels | string[] | `["IN_APP"]` | 至少包含 IN_APP；APP_PUSH、MINI_PROGRAM 只有 Provider 可用时允许启用 |
| recipientRoles | string[] | `["ADMIN","EDITOR","VIEWER"]` | 至少选择一个角色 |
| systemUserId | number | 0 | 系统任务执行人 ID，可配置为 0 或指定管理员 |
| skipIfNoDraft | boolean | true | 无草稿时记录 SKIPPED 或 FAILED |

### 6. 通知与多端消息

新增 `NotificationService`：

- `createMessage(type, title, content, businessType, businessId, channels, recipients)`
- `markRead(messageId, userId)`
- `listMyNotifications(userId, readStatus, page, size)`
- `dispatch(messageId)`：按渠道派发。
- `retryDelivery(deliveryLogId)`：重试单条渠道投递。
- `retryFailedDeliveries(messageId)`：重试某条消息下所有失败渠道。

新增 `NotificationController`：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/notifications/my` | 当前用户通知列表 |
| GET | `/api/notifications/unread-count` | 当前用户未读数 |
| POST | `/api/notifications/{id}/read` | 标记已读 |
| POST | `/api/notifications/read-all` | 全部已读 |
| GET | `/api/notifications/{id}/deliveries` | 查询通知渠道投递结果 |
| POST | `/api/notifications/deliveries/{deliveryLogId}/retry` | 重试单条渠道投递 |

渠道设计：

- `IN_APP`：落库即可，PC、App、小程序通过轮询 unread count 或通知列表展示。
- `APP_PUSH`：预留 `AppPushProvider`，一期可接 uni-push；如果当前没有推送证书或用户没有设备标识，记录为 SKIPPED。
- `MINI_PROGRAM`：预留 `MiniProgramSubscribeProvider`，需要用户订阅模板消息授权；未授权或没有 openid 时记录为 SKIPPED。
- `NOTIFICATION_RETRY`：后续可作为通用定时任务类型，定期扫描 `notification_delivery_log.status=FAILED` 且 `retry_count` 未超过上限的记录。

推送 Provider 一期接口：

- `NotificationChannelProvider.supports(channel)`
- `NotificationChannelProvider.isEnabled()`
- `NotificationChannelProvider.send(message, recipient)`
- `IN_APP` Provider 必须实现并默认启用。
- `APP_PUSH`、`MINI_PROGRAM` Provider 没有配置时返回 `SKIPPED`，不得抛出影响发布主流程的异常。

发布通知内容建议：

- 标题：`价格已更新`
- 内容：`2026-06-03 价格已发布，共更新 18 个产品，请查看最新价格。`
- 跳转参数：PC `/price-query?date=2026-06-03`；uni-app `/pages/history/index?date=2026-06-03` 或价格查询页。

接收人策略：

- 默认通知所有 ACTIVE 用户。
- 或由 `sys_scheduled_task.config_json.recipientRoles` / 手动发布弹窗选择接收角色控制。
- Viewer 只能收到“价格已更新，可以查看”，不能看到草稿详情。

### 7. 前端改造

PC `frontend/src/views/PriceMaintenance.vue`：

- 底部操作区拆成两个按钮：`保存`、`发布`。
- `保存` 调用 `/api/price-drafts/batch-save`，成功后页面状态变为“有未发布草稿”。
- `发布` 在存在草稿时可点击；点击前弹确认框，展示待发布日期、更新产品数。
- 发布成功后刷新草稿和已发布价格，提示“发布成功，已通知用户”。
- 当前编辑值优先来自草稿；无草稿时来自已发布价格；旁边可展示草稿状态标签。
- 保存按钮只在有修改时可用；发布按钮在存在 DRAFT 批次且无保存中状态时可用。
- 批次为 `PENDING_APPROVAL/APPROVED/PUBLISHING/PUBLISHED/REJECTED/CANCELLED` 时，编辑区应按状态禁用或只读。
- 多人协作时显示最近保存人、最近保存时间；保存遇到 409 时提示刷新，不自动覆盖。

uni-app `frontend-uniapp/src/pages/price-maintenance/index.vue`：

- 同步拆成“保存”和“发布”，复用同一后端草稿接口。
- 移动端底部固定操作区需要给两个按钮稳定宽度，避免文案切换造成布局跳动。
- 消息入口：可在首页/Profile 显示未读角标，进入通知列表。

新增前端 API：

- `frontend/src/api/priceDraft.ts`
- `frontend/src/api/notifications.ts`
- `frontend-uniapp/src/api/priceDraft.ts`
- `frontend-uniapp/src/api/notifications.ts`

PC 新增通用定时任务页面：

- 路由：`/scheduled-tasks`
- 页面文件：`frontend/src/views/ScheduledTasks.vue`
- API 文件：`frontend/src/api/scheduledTasks.ts`
- 类型定义：`ScheduledTask`、`ScheduledTaskLog`、`ScheduledTaskConfig`、`PricePublishTaskConfig`
- 菜单位置建议：`系统管理 -> 定时任务`，仅 ADMIN 可见。
- 所有任务类型、触发方式、执行状态通过字典服务显示。

类型定义：

- `PriceDraftBatch`
- `PriceDraftItem`
- `PriceDraftSaveRequest`
- `PricePublishResult`
- `NotificationMessage`
- `NotificationRecipient`
- `ScheduledTask`
- `ScheduledTaskLog`

所有状态标签显示必须通过 `useDict` / uni-app 对应字典缓存解析，不得硬编码中文状态名。

### 8. 审批能力预留

当前项目已有价格变更审批工作流，且 `PriceService.addProductPrice/updatePrice` 会在审批流启用时创建审批请求。

本期明确采用“保存草稿不审批，发布也不审批”的规则，发布按钮直接触发 `PricePublishService`。但为了未来可以接入审批流，本方案预留独立 `PRICE_PUBLISH` 审批能力：

- 保存草稿：不触发审批。
- 本期发布草稿：从 `DRAFT` 进入 `PUBLISHING`，发布成功后进入 `PUBLISHED`。
- 未来发布草稿：如果启用 `PRICE_PUBLISH` 审批能力，批次状态从 `DRAFT` 变为 `PENDING_APPROVAL`，并创建审批请求。
- 未来审批通过：批次状态变为 `APPROVED`，由审批完成事件或发布按钮继续调用 `PricePublishService` 执行发布。
- 未来审批拒绝：批次状态变为 `REJECTED`，保留草稿明细作为审计记录，不写入正式 `price`。

审批请求数据只引用 `batchId`，不要复制整批价格 JSON 到 `approval_request.request_data`，避免大批量价格导致字段过大。审批详情页需要查看价格明细时，通过 `batchId` 读取 `price_draft_item`。

审批预留实现：

- 本期可以初始化字典项 `workflow_type.PRICE_PUBLISH = 价格发布审批`，但不要求创建启用的审批流程。
- 如果需要创建 `approval_workflow.workflow_type = PRICE_PUBLISH`，必须默认 `is_active=false`，且本期不在前端暴露启用入口。
- 服务层预留 `PricePublishApprovalAdapter` 或等价接口，当前实现始终返回“不需要审批”。
- 未来启用审批时，发布按钮文案可切换为“提交发布审批”；审批通过后可自动发布或由管理员点击“发布已审批批次”。
- 现有 `PRICE_CHANGE` 不参与本功能，避免单条价格审批和批量发布审批语义混淆。

这样能兼容未来审批治理，又不影响当前“保存/发布”的主流程。

## 关键参考文件

- `frontend/src/views/PriceMaintenance.vue`：PC 价格维护页面，当前保存入口。
- `frontend/src/api/products.ts`：当前价格保存接口封装。
- `frontend-uniapp/src/pages/price-maintenance/index.vue`：App/小程序价格维护页面。
- `backend/src/main/java/com/pricemanagement/controller/PriceController.java`：当前价格接口。
- `backend/src/main/java/com/pricemanagement/service/PriceService.java`：当前价格保存、历史记录、产品售价同步逻辑。
- `backend/src/main/java/com/pricemanagement/repository/PriceRepository.java`：当前价格查询口径。
- `backend/src/main/java/com/pricemanagement/service/PriceQueryService.java`：日常价格查询只应读取已发布价格。
- `backend/src/main/java/com/pricemanagement/entity/Price.java`：正式价格实体。
- `backend/src/main/resources/init.sql`：初始化表结构和默认字典。
- `backend/src/main/resources/数据字典.md`：数据库数据字典。
- `frontend/src/constants/dictCategoryMeta.ts`：字典分类元数据。
- `frontend/src/composables/useDict.ts`：PC 字典显示服务。
- `backend/src/main/java/com/pricemanagement/service/AlertService.java`：现有运维告警能力，不建议直接当业务通知中心使用。

## 新旧模式冲突治理三期完善方案

本节用于处理价格保存/发布改造后暴露出的“旧模式直接写正式价格”和“新模式先保存草稿再发布”的边界冲突。治理目标不是一次性删除旧能力，而是在不影响现有功能的前提下逐步收口入口、明确读写边界，并最终统一编码和日志治理方式。

### 治理目标

1. 价格维护页只能走草稿保存与发布流程，避免继续调用旧的正式价格写入接口。
2. 价格查询、首页、外部 API、App/小程序价格展示只读取已发布价格，不读取草稿。
3. 旧接口在迁移期保留，但用途、权限、日志和文档边界必须清晰。
4. 操作日志、状态、任务类型、通知渠道等编码逐步统一为“后端存编码、前端查字典显示”。
5. 初始化脚本、迁移脚本、启动兜底初始化三者保持一致，避免新旧环境数据结构分叉。

### 一期：兼容层与边界确认

一期重点是让新模式稳定运行，同时保留旧接口作为兼容入口。

后端完善：

- 保留 `/api/products/{productId}/prices`、`/api/prices/{id}` 等旧正式价格写入接口，但在接口文档中标记为“正式价格维护接口”，不再作为批量价格维护页的默认入口。
- `PriceMaintenance.vue` 只允许调用 `/api/price-drafts/batch-save` 和 `/api/price-drafts/{batchId}/publish`。
- `PriceService` 继续作为正式价格落库服务，由 `PricePublishService` 在发布事务中复用。
- `PriceQueryService`、首页查询、外部 API 保持只读 `price` 表，禁止混入 `price_draft_*`。
- 为旧接口补充或确认 `@OperationLog`，日志描述明确“直接维护正式价格”，与“保存价格草稿/发布价格草稿”区分。

前端完善：

- 搜索前端代码，确认价格维护页没有残留 `addProductPrice`、`updatePrice` 调用。
- 价格维护页展示草稿状态时，只从 `price_draft_status` 字典取显示值。
- 无草稿、存在未保存改动、批次非 `DRAFT` 时，发布按钮必须禁用或给出明确提示。
- 价格查询页、首页、产品详情页不展示草稿数据，避免普通用户误以为草稿已经生效。

文档完善：

- 在 `docs/dev/项目设计文档.md` 的 API 部分标明两个入口的差异：草稿入口用于价格维护页，旧正式价格入口用于单条正式价格维护/兼容场景。
- 在 `docs/dev/开发指南.md` 增加约束：新增批量价格维护能力不得绕过 `PriceDraftService`。
- 在 `docs/dev/UI设计说明.md` 说明维护人员看到草稿，普通查看用户只看到已发布价格。

验收标准：

- 保存草稿后，`price`、`price_history`、`product.selling_price` 不变化。
- 发布后才写入 `price`、`price_history`，并更新符合条件的产品售价。
- Viewer、首页、价格查询、外部 API 在发布前均看不到草稿价。
- 操作日志中能区分“保存价格草稿”“发布价格草稿”“直接维护正式价格”。

### 二期：接口收口与约束增强

二期重点是降低误用旧接口的概率，把旧模式限制在明确场景内。

后端完善：

- 为旧正式价格写入接口增加用途注释和权限校验说明，必要时只允许 `ADMIN` 或具备特定权限的角色调用。
- 可新增请求来源或操作场景标识，例如 `source=FORMAL_PRICE_MAINTENANCE`，用于日志和审计识别。
- 如果后续权限系统支持细粒度权限，建议拆分：
  - `price:draft:save`
  - `price:draft:publish`
  - `price:formal:update`
- 在 `PricePublishService` 中集中处理正式价格写入，禁止其他批量发布逻辑直接操作 `price` Repository。
- 增加防重复发布保护：同一 `price_draft_batch` 已发布后再次发布必须拒绝或返回幂等结果。
- 增加冲突处理规则：草稿保存时基于 `batchVersion` 和 `basePriceVersion` 检查并发修改，冲突返回 409。

前端完善：

- 价格维护页不再导入旧价格写入 API，只保留草稿 API。
- 定时任务页面的任务类型下拉必须来自 `scheduled_task_type` 字典，不能写死单一任务类型。
- 操作日志筛选项补齐新增模块，确保“价格维护”“定时任务”“通知中心”可以按模块筛选。
- 发布失败、部分成功、通知外部渠道跳过等状态均通过字典显示。

数据与初始化完善：

- `V23__price_draft_publish_notification.sql`、`init.sql`、`DataInitializer` 中新增字典项保持一致。
- `operation_module` 新增模块项必须能匹配操作日志实际保存值。
- 默认 `PRICE_AUTO_PUBLISH` 任务继续保持 `enabled=false`，避免升级后自动发布历史草稿。

验收标准：

- 代码搜索确认价格维护页没有旧正式价格写入 API 调用。
- 旧接口仍可按权限用于兼容场景，但不会被新价格维护页调用。
- 操作日志页面能筛选新模块。
- 定时任务页面能展示多种任务类型，未实现的任务类型执行时记录 `SKIPPED`，不影响调度器。

### 三期：编码统一与长期治理

三期重点是把项目中“中文值直接入库”和“编码入库再查字典显示”的混用问题逐步统一。

后端完善：

- 操作日志模块建议逐步改为存编码，例如：
  - `PRICE_MAINTENANCE` → 价格维护
  - `SCHEDULED_TASK` → 定时任务
  - `NOTIFICATION_CENTER` → 通知中心
- `@OperationLog` 可扩展 `moduleCode`，短期兼容 `module` 中文值；新接口优先写 `moduleCode`。
- `OperationLog` 表可新增 `operation_module_code` 字段，保留原 `operation_module` 作为历史展示字段。
- 旧日志不强制立即迁移，可通过查询时兼容中文值和编码值；如需要迁移，再单独提供数据迁移脚本。
- 所有新状态、类型、渠道字段必须使用编码入库，中文只存在于 `sys_dict.dict_value`。

前端完善：

- 操作日志列表展示时优先使用 `getDictValue('operation_module', code)`，无法匹配时回退原值。
- 字典分类清单在 `useDict.ts`、`docs/dev/项目设计规范.md`、初始化脚本中保持同步。
- 新增页面如果展示编码值，必须先确认对应字典分类存在。

数据治理完善：

- 梳理 `sys_dict` 中同一分类下“英文编码 key”和“中文 key”混用的分类，形成迁移清单。
- 对高频分类优先治理：`operation_module`、`operation_type`、`workflow_type`、`scheduled_task_type`、`notification_channel`。
- 将 `DataInitializer` 定位为兜底补缺；数据库结构和正式种子数据以 Flyway 迁移和 `init.sql` 为准。
- 后续可以抽取统一种子清单，减少 `DataInitializer`、迁移脚本、`init.sql` 三处重复维护。

验收标准：

- 新增功能不再出现“后端存中文状态、前端再硬编码判断”的实现。
- 操作日志新数据可以稳定按编码筛选和字典显示。
- 旧日志仍能正常查询、展示和筛选，不因编码治理丢失可用性。
- 数据库初始化、增量迁移、启动兜底初始化三条路径产生的关键字典项一致。

### 当前完成状态矩阵

| 治理项 | 所属分期 | 当前状态 | 后续动作 |
| --- | --- | --- | --- |
| 价格维护页保存改为草稿接口 | 一期 | 已完成 | 上线前做页面回归，确认无旧保存 API 残留 |
| 发布按钮写正式价格并触发通知 | 一期 | 已完成 | 增加发布失败/部分成功的人工验收用例 |
| 普通查询只读 `price` 表 | 一期 | 已完成 | 回归首页、价格查询、外部 API、uni-app 展示口径 |
| 旧正式价格写入接口保留 | 一期 | 已完成 | 在 API 文档中标注兼容用途和使用边界 |
| 操作日志区分草稿保存/发布 | 一期 | 已完成 | 抽查 `operation_log` 中模块和描述是否可筛选 |
| 价格维护页不导入旧写入 API | 二期 | 已完成 | 增加代码搜索检查项：`addProductPrice`、`updatePrice` 不应出现在价格维护页 |
| 定时任务类型从字典读取 | 二期 | 已完成 | 新增任务类型时只改字典和 Handler，不改页面下拉 |
| 新增操作日志模块字典 | 二期 | 已完成 | 后续编码治理时迁移为稳定编码 |
| 旧接口权限收口 | 二期 | 待完善 | 明确是否仅 `ADMIN` 可直接维护正式价格 |
| 旧接口请求来源/审计标识 | 二期 | 待完善 | 增加 `source` 或独立 DTO 字段，用于审计区分 |
| 防重复发布幂等策略 | 二期 | 部分完成 | 明确重复发布返回 409 还是返回既有发布结果 |
| 操作日志模块编码化 | 三期 | 待完善 | 增加字段、字典编码、兼容查询和迁移脚本 |
| 字典种子单一来源治理 | 三期 | 待完善 | 抽取统一种子清单，减少三处重复维护 |

### 操作日志模块编码迁移方案

当前 `operation_log.operation_module` 存在中文模块名和字典编码不完全一致的问题。短期通过补充中文 key 字典保证筛选可用；长期建议新增编码字段并逐步迁移。

数据库变更建议：

1. 新增字段：

```sql
ALTER TABLE operation_log
ADD COLUMN operation_module_code VARCHAR(100) NULL COMMENT '操作模块编码',
ADD INDEX idx_operation_module_code (operation_module_code);
```

2. 补充字典项，统一使用稳定编码：

| dictKey | dictValue | 说明 |
| --- | --- | --- |
| PRICE_MAINTENANCE | 价格维护 | 草稿保存、发布、取消 |
| SCHEDULED_TASK | 定时任务 | 任务创建、更新、启停、手动执行 |
| NOTIFICATION_CENTER | 通知中心 | 通知已读、后续通知管理 |
| FORMAL_PRICE | 正式价格维护 | 旧接口直接维护正式价格 |

3. 回填历史日志编码：

```sql
UPDATE operation_log
SET operation_module_code = CASE operation_module
    WHEN '价格维护' THEN 'PRICE_MAINTENANCE'
    WHEN '定时任务' THEN 'SCHEDULED_TASK'
    WHEN '通知中心' THEN 'NOTIFICATION_CENTER'
    WHEN '价格管理' THEN 'FORMAL_PRICE'
    ELSE operation_module_code
END
WHERE operation_module_code IS NULL;
```

后端改造建议：

- `@OperationLog` 增加 `moduleCode` 属性，保留 `module` 用于兼容旧代码和兜底展示。
- `OperationLogAspect` 优先写 `moduleCode`，同时继续写 `module`，避免旧页面和旧报表失效。
- `OperationLogRepository` 查询模块时兼容两种条件：
  - 如果传入编码，匹配 `operation_module_code`
  - 如果传入中文历史值，匹配 `operation_module`
- 新增接口和新代码必须填写 `moduleCode`，禁止只填写中文 `module`。

前端改造建议：

- 操作日志筛选下拉的 `value` 使用编码，展示使用字典 `dictValue`。
- 日志列表展示优先使用 `operationModuleCode` 查字典；没有编码时回退 `operationModule` 原值。
- 迁移完成前保留中文 key 字典项，避免旧日志无法筛选。

兼容周期建议：

- 第 1 个版本：新增字段和兼容查询，新日志双写编码与中文名。
- 第 2 个版本：完成历史数据回填，前端默认用编码筛选。
- 第 3 个版本：保留 `operation_module` 展示字段，但新增代码不得依赖中文模块名做查询。

### 上线与回滚策略

上线前检查：

- 数据库先执行 `V23__price_draft_publish_notification.sql`，确认新增表、索引、外键、字典和默认任务创建成功。
- 确认 `PRICE_AUTO_PUBLISH.enabled=false`。
- 用管理员账号完成一次“保存草稿 -> 发布 -> 通知生成”的端到端验收。
- 用 Viewer 账号确认发布前看不到草稿，发布后才能看到新价格。
- 抽查 `price_history` 和 `product.selling_price`，确认只有发布动作会更新。

灰度建议：

- 第一阶段只开放手动保存/发布，不启用自动发布任务。
- 第二阶段开放定时任务页面，但默认任务保持停用，由管理员手动启用。
- 第三阶段再启用外部 App/小程序 Provider；未配置前只使用站内通知和 `SKIPPED` 投递日志。

故障回滚策略：

- 如果草稿保存异常：临时禁用价格维护页保存按钮或回滚前端页面到旧版本；已保存草稿保留在 `price_draft_*`，不影响正式价格。
- 如果发布异常：停用发布按钮和 `PRICE_AUTO_PUBLISH` 任务，保留旧正式价格；未发布草稿继续留存，修复后可再次发布。
- 如果通知异常：保留价格发布成功，只暂停外部 Provider 或通知入口；通知失败不得回滚价格发布。
- 如果定时任务异常：将 `sys_scheduled_task.enabled=false`，清理异常锁字段 `lock_until`、`locked_by` 后再恢复。
- 如果迁移脚本异常：在生产环境停止继续启动应用，修复迁移后重跑；不得手工删除已创建的价格正式数据。

数据回退原则：

- 草稿数据可以取消或保留，不直接影响用户可见价格。
- 已发布价格属于正式业务数据，原则上不自动删除；如确需回退价格，应通过新的正式价格发布或明确的价格回滚功能完成。
- 通知消息和投递日志作为审计记录保留，不因价格回滚而物理删除。

运行监控建议：

- 监控 `price_publish_log.status` 中 `FAILED/PARTIAL` 数量。
- 监控 `sys_scheduled_task_log.status=FAILED` 的任务执行。
- 监控 `notification_delivery_log.status=FAILED` 的外部推送失败。
- 每次启用新任务类型前，先用 `MANUAL_RUN` 手动执行并检查日志。

### 分期优先级建议

| 分期 | 优先级 | 建议时机 | 原因 |
| --- | --- | --- | --- |
| 一期 | P0 | 当前功能上线前完成 | 直接影响价格保存/发布边界，必须先稳住 |
| 二期 | P1 | 上线后第一轮迭代 | 降低误用旧接口和重复发布风险 |
| 三期 | P2 | 审批流或更多任务类型接入前 | 统一编码后，审批、通知、日志扩展会更顺 |

## 实现步骤

### 阶段一：数据库和后端基础

1. 新增迁移脚本，如 `V23__price_draft_publish_notification.sql`。
2. 创建 `price_draft_batch`、`price_draft_item`、`price_publish_log`、`notification_message`、`notification_recipient`、`notification_delivery_log`。
3. 创建 `sys_scheduled_task`、`sys_scheduled_task_log`。
4. 初始化新增字典分类、默认字典项、默认价格自动发布任务。
5. 新增 Entity、Repository、DTO。
6. 实现 `PriceDraftService` 的按日期查询、批量保存草稿。
7. 实现 `PricePublishService` 的手动发布事务。
8. 实现 `NotificationService` 的站内通知落库。
9. 实现 `ScheduledTaskService`、`ScheduledTaskDispatcher`、`PriceAutoPublishTaskHandler`。
10. 增加 `PriceDraftController`、`NotificationController`、`ScheduledTaskController`。

### 阶段二：PC 价格维护改造

1. 新增 `frontend/src/api/priceDraft.ts`、`frontend/src/api/notifications.ts`。
2. 改造 `PriceMaintenance.vue` 加载逻辑：同时读取已发布价格和草稿价格。
3. 将原保存改为批量保存草稿。
4. 新增发布按钮和发布确认弹窗。
5. 发布成功后刷新数据，并通过事件总线通知首页/价格查询页刷新。
6. 新增通知入口或未读数展示。

### 阶段三：定时发布

1. 在 `dictCategoryMeta.ts` 注册定时任务类型、触发方式、执行状态字典元数据。
2. 新增 PC `ScheduledTasks.vue` 通用定时任务管理页面。
3. 后端实现通用任务扫描、cron 命中判断、执行日志和任务锁。
4. 价格自动发布作为 `PRICE_PUBLISH` Handler 接入调度器。
5. 自动发布复用 `PricePublishService`，并写 `price_publish_log` 和 `sys_scheduled_task_log`。
6. 增加自动发布失败通知。

### 阶段四：App/小程序通知

1. uni-app 增加通知 API、未读数、通知列表。
2. 价格发布通知落地为站内消息，App/小程序轮询展示。
3. 实现 Provider 接口和 IN_APP Provider；uni-push / 小程序订阅消息 Provider 可按配置启用。
4. 在 `notification_delivery_log` 记录每个用户、每个渠道的投递结果。
5. 未配置外部推送时记录 SKIPPED，保证 App/小程序仍可通过站内通知列表看到消息。
6. 增加失败重试或后台重发入口。

### 阶段五：文档与数据字典

按 `AGENTS.md` 要求同步更新：

- `README.md`：补充价格保存/发布/通知功能。
- `docs/dev/开发指南.md`：补充草稿保存、发布、通知 API 和开发约束。
- `docs/ops/IDEA部署指南.md`：补充迁移脚本、定时任务配置、推送配置。
- `docs/dev/项目设计文档.md`：补充表结构、接口、功能模块设计。
- `docs/archive/项目完成总结.md`：更新功能完成情况。
- `docs/dev/UI设计说明.md`：补充双按钮、草稿状态、通知入口。
- `backend/src/main/resources/数据字典.md`：补充新增表和字段。

## 前后端与数据库一致性检查

接口一致性：

- `POST /api/price-drafts/batch-save` 请求字段要与 `PriceDraftSaveRequest`、前端 `PriceDraftSaveRequest` 类型一致。
- `POST /api/price-drafts/{batchId}/publish` 返回字段要与 `PricePublishResult` 一致。
- 通知接口返回分页结构要与现有 `PageResponse` 兼容。
- 定时任务接口请求/响应要与 `ScheduledTask`、`ScheduledTaskLog` 前端类型一致，`configJson` 对 `PRICE_PUBLISH` 必须能被结构化表单稳定读写。
- 草稿保存请求必须携带 `batchVersion`；后端返回新 `version`，前端本地同步更新。
- 本期发布接口只返回直接发布结果；未来启用审批后，前端再根据 `batchStatus` 处理 `PENDING_APPROVAL` 提示。

数据库一致性：

- Entity 的 `@Table`、`@Column`、`@JoinColumn` 必须与迁移脚本、`init.sql`、数据字典一致。
- `price_draft_item.product_id`、`price_draft_item.batch_id` 外键列必须真实存在。
- `price_draft_batch.product_scope_snapshot` 必须能存放完整产品 ID 数组；产品数量较多时使用 TEXT 或 JSON 类型。
- `notification_delivery_log.recipient_id` 必须关联 `notification_recipient.id`。
- `@Version` 如用于批次并发控制，表中必须有 `version` 字段。
- `sys_scheduled_task.version` 必须与 Entity `@Version` 一致。
- `sys_scheduled_task.lock_until`、`locked_by`、`last_scheduled_time` 和 `sys_scheduled_task_log.scheduled_time` 必须与数据库迁移和 Entity 一致。
- `price` 表不新增草稿状态，现有查询不需要大面积改动。

业务一致性：

- 价格查询、首页、外部读接口只读取已发布 `price`。
- 价格维护页面读取草稿和已发布价格，维护人员能看到未发布草稿。
- 保存草稿不写 `price_history`，发布才写。
- 保存草稿不更新 `product.selling_price`，发布才更新。
- 自动发布完整性校验以 `product_scope_snapshot` 为准。
- `PENDING_APPROVAL/APPROVED` 为未来审批预留状态；一旦未来启用审批，这些状态下不允许继续编辑草稿明细。
- 通知已读状态与渠道投递状态分离：已读看 `notification_recipient`，渠道发送看 `notification_delivery_log`。
- 默认价格自动发布任务必须初始化为停用。
- `PRICE_PUBLISH` 审批流默认停用，且不复用 `PRICE_CHANGE`。

## Verification

后端验证：

- 保存草稿后，`price_draft_batch`、`price_draft_item` 有数据，`price` 不变化。
- Viewer 查询价格、首页、外部 API 看不到草稿价格。
- 发布后，`price` 更新，`price_history` 写入，`product.selling_price` 在符合日期条件时更新。
- 同一批次重复发布被拒绝或幂等返回，不产生重复历史。
- 发布成功后生成 `notification_message`、`notification_recipient` 和各渠道 `notification_delivery_log`。
- 定时任务命中后能自动发布目标日期草稿。
- 定时任务管理页能创建、编辑、启停、手动执行、测试 cron，并查看执行日志。
- 同一任务在同一分钟内不会重复执行；多实例场景通过数据库行锁、`lock_until` 和执行日志唯一约束保护。
- 第三方推送失败不回滚价格发布。
- 未配置 App/小程序推送 Provider 时，外部渠道投递记录为 SKIPPED，站内通知仍成功。
- 批次版本冲突返回 409，不覆盖其他用户保存的草稿。
- 产品范围快照创建后，完整性校验不受后续产品启停影响。
- 本期发布不会进入 `PENDING_APPROVAL`；未来启用 `PRICE_PUBLISH` 审批后，发布先进入 `PENDING_APPROVAL`，审批通过后才写正式 `price`。

前端验证：

- PC 价格维护页面显示“保存”和“发布”两个按钮。
- 修改价格后点击保存，只显示“有未发布草稿”，其他用户刷新仍看旧价。
- 发布成功后，价格查询页和首页显示新价。
- 无草稿时发布按钮禁用或提示无可发布内容。
- 状态、渠道、发布结果显示均来自字典服务。
- 定时任务页面所有任务类型、触发方式、执行状态显示均来自字典服务；`PRICE_PUBLISH` 参数使用结构化表单，不要求用户手写 JSON。
- 保存遇到 409 冲突时，页面提示刷新并保留用户当前输入，避免直接丢失编辑内容。
- 未来审批中批次、已发布批次、已取消批次在价格维护页只读展示。

uni-app 验证：

- App/小程序价格维护页保存/发布流程与 PC 一致。
- 价格发布后能看到未读通知。
- 点击通知可跳转到对应价格查看页面。

回归验证：

- 现有产品列表、首页、价格查询、导出、价格趋势不受草稿数据影响。
- 本期发布立即生效；未来启用 `PRICE_PUBLISH` 审批后按审批策略执行。
- `mvn test` 或 `./gradlew.bat test` 后端测试通过。
- 前端 `npm run build` 通过。
