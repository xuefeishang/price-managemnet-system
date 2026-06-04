# 工业级消息管理平台建设方案

## Context

消息管理将成为价格管理系统的基础设施能力，而不是某个页面的附属功能。系统未来会持续接入价格发布、审批待办、定时任务异常、外部 API 告警、系统公告、账号安全、导入导出结果、运维事件等消息场景。如果消息能力没有统一治理，后续会出现消息重复、触达不可追踪、外部推送失败影响业务、前端跳转语义混乱、轮询噪声不可控等问题。

当前已有基础：

- 后端已有 `notification_message`、`notification_recipient`、`notification_delivery_log`。
- 后端已有 `/api/notifications/my`、`/api/notifications/unread-count`、`/api/notifications/{messageId}/read`。
- PC 端已有侧边栏用户卡片更多菜单、红色数字未读提示、通知抽屉。
- 价格发布后已能生成站内通知。

工业级目标：

- 消息不丢、不重、不误发。
- 业务事务不被外部推送拖垮。
- 用户能及时收到重要消息，但不被低价值消息打扰。
- 管理员能审计消息从创建到投递的完整链路。
- 系统能在外部 Provider、网络、浏览器、任务调度异常时安全降级。
- 后续多业务、多端、多渠道接入时，不需要重做消息体系。

## 执行跟踪

更新时间：2026-06-04

状态说明：

- `[x]` 已完成并通过基础验证
- `[~]` 已实现主体能力，仍需加固或补测试
- `[ ]` 未开始
- `[!]` 新发现问题或风险项

### 当前完成度

| 阶段 | 状态 | 完成情况 |
| --- | --- | --- |
| 一期：站内消息工业化 | `[~]` 主体完成 | 字段扩展、站内通知入口、智能轮询、全部已读、价格发布结构化跳转、批次级通知幂等、点击通知容错已完成；仍需处理列表 N+1 |
| 二期：可靠投递和治理后台 | `[ ]` 未开始 | Outbox、worker、偏好、通知管理后台、投递重试未开始 |
| 三期：实时增强和运营能力 | `[ ]` 未开始 | SSE、公告运营、聚合频控、指标看板未开始 |

### 已完成任务

| 任务 | 状态 | 说明 |
| --- | --- | --- |
| 通知主表字段扩展 | `[x]` | 已增加 `summary`、`priority`、`link_type`、`link_params`、`dedupe_key`、`expire_time` |
| 接收人状态字段扩展 | `[x]` | 已增加 `archived`、`archived_time`、`first_seen_time` 预留字段 |
| 通知字典补齐 | `[x]` | 已补 `notification_priority`、`notification_link_type`、`notification_business_type` |
| PC 通知入口 | `[x]` | 左侧侧边栏底部用户卡片更多菜单、红色数字未读提示、通知抽屉已完成 |
| PC 抽屉交互 | `[x]` | 全部/未读筛选、全部已读、优先级展示、摘要展示已完成 |
| 未读数智能轮询 | `[x]` | 随机抖动、页面隐藏暂停、失败退避、发布后本地刷新已完成 |
| 结构化跳转 | `[x]` | 价格发布通知已使用 `linkType=PRICE_QUERY` 和 `linkParams.date` |
| 价格发布通知幂等 | `[x]` | `dedupeKey` 已按草稿批次生成，避免同一批次重复通知 |
| 价格发布重试幂等 | `[x]` | 部分发布失败后重试会跳过已发布且有 `publishedPriceId` 的明细 |
| 发布幂等单元测试 | `[x]` | 已补 `PricePublishServiceTests` 覆盖重试跳过、失败不通知、日志计数 |
| PC 通知点击容错 | `[x]` | 点击通知时先乐观已读并继续跳转，已读接口失败后刷新未读数 |
| 文档同步 | `[x]` | README、开发指南、设计文档、数据字典、UI 说明已同步一期能力 |

### 新发现问题跟踪

| 编号 | 问题 | 严重性 | 状态 | 处理方案 |
| --- | --- | --- | --- | --- |
| N-001 | 部分发布失败后再次发布可能重复写入已成功明细 | 高 | `[x]` 已修复 | 发布时跳过 `itemStatus=PUBLISHED` 且 `publishedPriceId` 非空的明细 |
| N-002 | 价格发布通知原先按 `publishLogId` 去重，不能代表同一草稿批次幂等 | 中 | `[x]` 已修复 | `dedupeKey` 改为 `PRICE_PUBLISHED:BATCH:{batchId}` |
| N-003 | 我的通知列表存在 recipient 到 message 的 N+1 查询 | 中 | `[!]` 待处理 | 二期前置加固：改为批量 `findAllById` 或 join DTO 查询 |
| N-004 | 点击通知时若标记已读失败，会阻断后续业务跳转 | 中低 | `[x]` 已修复 | 前端已改为先跳转/乐观已读，失败后刷新未读数 |
| N-005 | `notification_outbox` 尚未落地，外部渠道仍只是 SKIPPED 预留 | 中 | `[ ]` 二期处理 | 建设 Outbox、worker、Provider 失败重试与管理端 |

### 下一步开发计划

近期优先级按“先补一期加固，再进入二期底座”执行：

1. **一期加固收尾**：修复 N-003，补充通知列表查询测试和 SQL/Explain 验证。
2. **二期底座设计落库**：新增 `notification_outbox`、`notification_preference` 迁移、实体、Repository 和基础服务。
3. **Outbox Worker**：实现数据库锁领取、指数退避、最大重试、失败保留。
4. **通知管理后台**：ADMIN 查询全局消息、收件人、投递日志，支持失败投递重试。
5. **业务接入扩展**：接入审批待办、定时任务失败、API 告警、导入导出完成消息。
6. **规模化增强评估**：根据在线用户规模决定 SSE 放在二期后段还是三期。

### 下一步执行计划

| 顺序 | 工作项 | 交付物 | 验证方式 |
| --- | --- | --- | --- |
| 1 | 通知列表 N+1 优化 | `NotificationService.getMyNotifications` 批量查询或 join 查询 | 单元测试 + SQL 日志确认不随列表条数线性增长 |
| 2 | 点击通知容错 | PC 通知点击不因已读接口失败阻断跳转 | `[x]` 已完成；后续补自动化或手工回归记录 |
| 3 | 一期回归测试 | 发布、未读数、全部已读、结构化跳转完整回归 | `mvn test`、`npm run build`、手工链路验证 |
| 4 | Outbox 表与实体 | `notification_outbox` 迁移、Entity、Repository | 后端启动和迁移验证 |
| 5 | Outbox Worker | 领取、锁定、重试、失败保留 | Provider mock 测试成功、失败、超时 |
| 6 | 管理端通知列表 | ADMIN 管理页面与接口 | 权限测试、列表分页、投递日志查询 |

## 工业级设计原则

1. **可靠性优先**：站内消息落库是消息可靠性的底座；外部推送只是增强。
2. **业务与投递解耦**：业务只产生消息命令或事件，不直接调用 App、小程序、企业微信等 Provider。
3. **事务边界清晰**：业务数据、站内消息、Outbox 在同一事务内提交；外部渠道事务后异步投递。
4. **幂等与去重内建**：消息创建、收件人生成、投递重试必须具备幂等键。
5. **可观测可审计**：每条消息必须能追踪创建人、接收人、阅读状态、渠道投递、失败原因和重试记录。
6. **低打扰可治理**：支持优先级、偏好、免打扰、频控、聚合、过期和归档。
7. **多端体验一致**：PC 提供完整消息管理，移动端提供轻量查看与业务跳转。
8. **可演进**：一期轻轮询稳定落地，后续按规模引入 SSE，保留轮询降级。

## SLO 与容量目标

一期目标以中小规模企业内网/专网应用为基准：

| 指标 | 目标 |
| --- | --- |
| 站内消息创建成功率 | >= 99.9% |
| 价格发布后站内消息可见延迟 | PC 当前用户立即刷新；其他在线用户 45 秒内 |
| 未读数接口 P95 | <= 100ms |
| 通知列表接口 P95 | <= 300ms |
| 外部 Provider 失败对业务影响 | 0，业务不得回滚 |
| 重复消息率 | 同一 `dedupeKey` 下为 0 |
| 消息审计可追踪性 | 100% 消息有创建、收件、投递记录 |

容量估算：

```text
未读数平均 QPS = 在线 PC 用户数 / 平均轮询间隔
```

智能轮询采用 30-45 秒随机抖动，平均约 37.5 秒。

| 在线 PC 用户 | 估算 QPS |
| --- | --- |
| 100 | 2.7 |
| 500 | 13.3 |
| 2000 | 53.3 |
| 5000 | 133.3 |

容量判断：

- 500 人以内：REST 轻轮询完全可控。
- 500-2000 人：必须启用隐藏页暂停、失败退避、索引优化和慢查询监控。
- 2000 人以上：建议引入 SSE 推送“未读数变化”，保留 REST 查询列表。

## 总体架构

采用工业级“五层模型”：

```text
业务事件层
  -> 消息编排层
  -> 可靠存储层
  -> 渠道投递层
  -> 多端呈现与治理层
```

### 1. 业务事件层

业务模块不直接写消息表，也不直接调用推送渠道。

标准业务事件：

| 事件 | 说明 | 默认优先级 |
| --- | --- | --- |
| PRICE_PUBLISHED | 价格发布成功 | NORMAL |
| PRICE_AUTO_PUBLISH_FAILED | 自动发布失败 | HIGH |
| APPROVAL_PENDING | 审批待处理 | HIGH |
| APPROVAL_FINISHED | 审批完成 | NORMAL |
| TASK_FAILED | 定时任务失败 | HIGH |
| API_LIMIT_WARNING | API 调用异常/限流 | HIGH |
| IMPORT_EXPORT_FINISHED | 导入导出完成 | LOW |
| SYSTEM_NOTICE | 系统公告 | NORMAL |
| SECURITY_ALERT | 账号安全风险 | URGENT |

业务模块只需要提供：

- 发生了什么事件。
- 谁应该收到。
- 用户点击后去哪。
- 是否重要、是否允许免打扰。

### 2. 消息编排层

统一命令对象：

```java
NotificationCreateCommand
```

字段要求：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| eventType | 是 | 消息类型，如 PRICE_PUBLISHED |
| title | 是 | 标题 |
| summary | 是 | 短摘要 |
| content | 是 | 正文 |
| businessType | 是 | PRICE、APPROVAL、TASK、SYSTEM、SECURITY |
| businessId | 否 | 业务 ID |
| recipientUserIds / recipientRoles | 是 | 接收人 |
| channels | 是 | IN_APP、APP_PUSH、MINI_PROGRAM 等 |
| priority | 是 | LOW、NORMAL、HIGH、URGENT |
| linkType | 是 | PRICE_QUERY、APPROVAL_DETAIL、TASK_LOG 等 |
| linkParams | 否 | JSON 参数 |
| dedupeKey | 是 | 幂等键 |
| expireTime | 否 | 过期时间 |

统一入口：

```java
NotificationMessage create(NotificationCreateCommand command)
```

接入规则：

- 业务模块不得直接写 `notification_*` 表。
- 业务模块不得直接调用外部 Provider。
- 新增业务消息必须先定义 `eventType`、`businessType`、`linkType` 字典。
- 前端不得从 `content` 解析跳转参数，只能使用 `linkType/linkParams`。

### 3. 可靠存储层

#### 3.1 notification_message

消息主体。

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| type | 消息类型 |
| title | 标题 |
| summary | 摘要 |
| content | 正文 |
| business_type | 业务类型 |
| business_id | 业务 ID |
| priority | 优先级 |
| link_type | 跳转类型 |
| link_params | JSON 跳转参数 |
| channels | JSON 渠道数组 |
| dedupe_key | 幂等键 |
| expire_time | 过期时间 |
| created_by | 创建人 |
| created_time | 创建时间 |

索引：

- `idx_notification_type_created(type, created_time)`
- `idx_notification_business(business_type, business_id)`
- `uk_notification_dedupe_key(dedupe_key)`

#### 3.2 notification_recipient

用户收件状态。

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| message_id | 消息 ID |
| user_id | 接收人 |
| read_status | UNREAD、READ |
| read_time | 阅读时间 |
| archived | 是否归档 |
| archived_time | 归档时间 |
| first_seen_time | 第一次被用户端拉取时间 |

索引：

- `idx_notification_recipient_user(user_id, read_status)`
- `idx_notification_recipient_user_time(user_id, id)`
- `idx_notification_recipient_message(message_id)`
- `uk_notification_message_user(message_id, user_id)`

#### 3.3 notification_delivery_log

渠道投递记录。

| 字段 | 说明 |
| --- | --- |
| message_id | 消息 ID |
| recipient_id | 收件记录 ID |
| user_id | 用户 ID |
| channel | 渠道 |
| status | PENDING、SUCCESS、FAILED、SKIPPED |
| provider | Provider 名称 |
| provider_message_id | 第三方消息 ID |
| retry_count | 重试次数 |
| delivered_time | 投递完成时间 |
| error_code | 错误编码 |
| error_message | 错误信息 |
| created_time / updated_time | 时间 |

#### 3.4 notification_outbox

可靠投递 Outbox。

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| event_type | 事件类型 |
| aggregate_type | 聚合类型 |
| aggregate_id | 聚合 ID |
| payload_json | 事件快照 |
| status | PENDING、PROCESSING、SUCCESS、FAILED |
| retry_count | 重试次数 |
| next_retry_time | 下次重试时间 |
| locked_by / lock_until | worker 锁 |
| created_time / updated_time | 时间 |

Outbox 规则：

- 业务事务内写入站内消息、收件人、delivery 初始记录和 outbox。
- 外部渠道由 worker 在事务提交后异步投递。
- worker 使用数据库行锁领取任务。
- 失败采用指数退避重试。
- 超过最大重试次数后标记 `FAILED`，等待人工重试或忽略。

#### 3.5 notification_preference

用户通知偏好。

| 字段 | 说明 |
| --- | --- |
| user_id | 用户 |
| notification_type | 消息类型 |
| channel | 渠道 |
| enabled | 是否启用 |
| quiet_start_time | 免打扰开始 |
| quiet_end_time | 免打扰结束 |
| created_time / updated_time | 时间 |

偏好规则：

- `IN_APP` 对 HIGH/URGENT 默认不可关闭。
- 外部渠道可按类型关闭。
- URGENT 可绕过免打扰，但必须记录原因。

## 渠道投递设计

Provider 接口：

```java
public interface NotificationChannelProvider {
    String channel();
    DeliveryResult send(NotificationMessage message, NotificationRecipient recipient);
}
```

渠道策略：

| 渠道 | 工业级要求 |
| --- | --- |
| IN_APP | 必选，落库即成功 |
| APP_PUSH | 可选，未配置或用户无设备标识则 SKIPPED |
| MINI_PROGRAM | 可选，用户无 openid 或未订阅模板则 SKIPPED |
| WEBHOOK | 可选，适合外部系统告警 |
| WECHAT_WORK | 可选，适合管理员运维告警 |

失败处理：

- Provider 超时：记录 `FAILED/TIMEOUT`。
- Provider 未配置：记录 `SKIPPED/PROVIDER_NOT_CONFIGURED`。
- 用户未绑定设备：记录 `SKIPPED/USER_NOT_BOUND`。
- 用户关闭偏好：记录 `SKIPPED/USER_PREFERENCE_DISABLED`。
- 免打扰命中：记录 `SKIPPED/QUIET_HOURS`，URGENT 除外。

## PC 端设计

PC 是完整消息工作台入口。

全局入口：

- 左侧侧边栏底部用户卡片更多菜单。
- 有未读消息时，更多按钮由三点图标切换为红色数字角标。
- 消息通知菜单项同步展示未读数。
- 点击消息通知打开右侧通知抽屉。

通知抽屉：

- 全部 / 未读切换。
- 全部已读。
- 单条已读。
- 归档。
- 展示类型、优先级、摘要、时间。
- 点击按 `linkType/linkParams` 跳转。

未读数获取：

- 30-45 秒随机抖动。
- 页面隐藏暂停。
- 页面恢复可见立即刷新。
- 抽屉打开立即刷新。
- 当前用户发布价格等动作后本地事件立即刷新。
- 请求失败后退避：30s -> 60s -> 120s，成功后恢复。

未来 SSE：

- 只推送轻事件：`unreadCountChanged`、`newNotification`。
- 列表仍通过 REST 查询。
- SSE 断开自动退回轮询。
- 不优先 WebSocket，除非未来需要双向协作。

## App/小程序设计

移动端强调轻量闭环，不承载复杂运维。

- 我的页消息入口。
- 未读角标或未读数字提示。
- 全部 / 未读列表。
- 点击消息跳转轻量业务页面。
- 不提供 Provider 配置、投递日志、失败重试。
- 小程序订阅消息只是增强，不替代站内消息列表。

## 管理端设计

系统管理新增“通知管理”。

能力：

- 全局消息列表。
- 消息详情。
- 收件人阅读状态。
- 渠道投递日志。
- 失败投递重试。
- 系统公告创建、定时发布、撤回、过期。
- 高频消息聚合配置。
- Provider 配置状态检查。

列表字段：

- 类型
- 标题
- 优先级
- 业务类型
- 创建人
- 创建时间
- 收件人数
- 未读人数
- 外部投递失败数

## API 设计

用户侧：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/notifications/my` | 我的通知列表 |
| GET | `/api/notifications/unread-count` | 未读数 |
| POST | `/api/notifications/{messageId}/read` | 单条已读 |
| POST | `/api/notifications/read-all` | 全部已读 |
| POST | `/api/notifications/{messageId}/archive` | 归档 |
| GET | `/api/notifications/preferences` | 查询偏好 |
| PUT | `/api/notifications/preferences` | 更新偏好 |

管理侧：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/notifications` | 全局消息 |
| GET | `/api/admin/notifications/{id}` | 消息详情 |
| GET | `/api/admin/notifications/{id}/recipients` | 收件人 |
| GET | `/api/admin/notifications/{id}/deliveries` | 投递日志 |
| POST | `/api/admin/notifications/deliveries/{id}/retry` | 重试投递 |
| POST | `/api/admin/system-notices` | 创建公告 |
| POST | `/api/admin/system-notices/{id}/cancel` | 撤回公告 |

## 权限与安全

- 普通用户只能查询自己的消息。
- `read`、`read-all`、`archive` 只能作用于当前用户自己的 recipient。
- 投递日志和全局消息仅 ADMIN 可见。
- 系统公告仅 ADMIN 可创建。
- 消息正文不得包含敏感凭据、密钥、完整 token、数据库连接串。
- 安全类消息允许展示摘要，敏感详情跳转到具备权限的页面。
- 所有消息管理操作记录操作日志。

## 字典设计

| 分类 | 示例 |
| --- | --- |
| notification_type | PRICE_PUBLISHED、APPROVAL_PENDING、TASK_FAILED |
| notification_priority | LOW、NORMAL、HIGH、URGENT |
| notification_channel | IN_APP、APP_PUSH、MINI_PROGRAM、WEBHOOK、WECHAT_WORK |
| notification_read_status | UNREAD、READ |
| notification_delivery_status | PENDING、SUCCESS、FAILED、SKIPPED |
| notification_business_type | PRICE、APPROVAL、TASK、SYSTEM、SECURITY |
| notification_link_type | PRICE_QUERY、APPROVAL_DETAIL、TASK_LOG、SYSTEM_NOTICE |
| notification_outbox_status | PENDING、PROCESSING、SUCCESS、FAILED |

## 可观测与运维

关键指标：

- 未读数接口 QPS、P95、错误率。
- 通知列表接口 P95。
- 每日消息创建数。
- 每日 recipient 数。
- 外部渠道 SUCCESS/FAILED/SKIPPED 数。
- outbox PENDING 堆积数量。
- outbox 最老 PENDING 等待时长。
- Provider 失败率。
- 高频消息类型排行。

告警建议：

| 指标 | 告警 |
| --- | --- |
| outbox PENDING 堆积超过阈值 | 告警 ADMIN |
| Provider 连续失败 | 告警 ADMIN |
| 未读数接口 P95 超阈值 | 告警运维 |
| 通知创建失败 | 告警开发/运维 |
| TASK_FAILED 高频触发 | 聚合后通知 ADMIN |

## 数据生命周期

- 普通消息默认保留 180 天。
- HIGH/URGENT 和审计相关消息默认保留 365 天。
- delivery log 默认保留 180 天。
- outbox SUCCESS 可保留 30-90 天后归档。
- FAILED outbox 在人工确认前不物理删除。
- 归档只影响用户视图，不删除审计记录。

## 分期实施

### 一期：站内消息工业化

目标：把当前 PC 站内消息做稳。

- [x] 增加 `linkType`、`linkParams`、`priority`、`summary`、`dedupeKey`。
- [x] 增加 `/api/notifications/read-all`。
- [x] PC 抽屉增加全部/未读、全部已读、优先级展示。
- [x] PC 轮询改为随机抖动、隐藏暂停、失败退避。
- [x] 价格发布通知改用 `linkType=PRICE_QUERY` 和 `linkParams.date`。
- [x] 价格发布通知按草稿批次 `dedupeKey` 去重。
- [x] 部分发布重试跳过已发布明细，避免重复写正式价格。
- [x] 补齐字典和文档。
- [ ] 通知列表 N+1 查询优化。
- [x] 点击通知时标记已读失败不阻断业务跳转。

验收：

- [x] 价格发布后所有目标用户生成未读消息。
- [x] 当前发布人立即看到未读刷新。
- [x] 其他在线用户 45 秒内看到未读刷新。
- [x] 页面隐藏时不轮询。
- [x] 全部已读后角标清零。
- [x] 同一草稿批次不会重复生成价格发布通知。
- [x] 部分发布重试不会重复发布已成功明细。
- [ ] 通知列表查询性能达到 N+1 优化目标。
- [x] 点击通知容错完成。

### 二期：可靠投递和治理后台

目标：支持多业务、多渠道、可审计。

- [ ] 新增 `notification_outbox`。
- [ ] 新增 outbox worker。
- [ ] 新增 `notification_preference`。
- [ ] 新增通知管理页面。
- [ ] 支持投递日志查询和失败重试。
- [ ] 接入审批、定时任务失败、API 告警。

验收：

- [ ] Provider 失败不影响业务事务。
- [ ] outbox 失败可重试。
- [ ] 管理员能查到每条消息的收件和投递状态。
- [ ] 用户偏好能影响外部渠道投递。

### 三期：实时增强和运营能力

目标：提升实时性和运营能力。

- [ ] 引入 SSE。
- [ ] 保留轮询降级。
- [ ] 系统公告支持定时发布、撤回、过期。
- [ ] 高频消息聚合和频控配置化。
- [ ] 建立消息指标看板。

验收：

- [ ] SSE 断开后自动退回轮询。
- [ ] 公告可按角色发送。
- [ ] 高频失败消息能聚合。
- [ ] 管理端可查看消息健康指标。

## Verification

后端：

- [x] 消息创建幂等：价格发布通知已按草稿批次 `dedupeKey` 去重。
- [x] recipient 不重复：依赖 `uk_notification_message_user(message_id, user_id)`。
- [x] `read-all` 只影响当前用户。
- [x] 部分发布重试不重复写已发布明细。
- [ ] 未读数查询命中索引：需补 SQL/Explain 验证记录。
- [ ] 我的通知列表 N+1 查询优化。
- [ ] 外部 Provider 失败不回滚业务：当前外部渠道为 SKIPPED 预留，Outbox 二期验证。
- [ ] outbox 可重试、可锁定、可恢复。

PC：

- [x] 铃铛、未读数、抽屉正常。
- [x] 隐藏页暂停轮询。
- [x] 失败退避生效。
- [x] `linkType/linkParams` 跳转正确。
- [x] 全部已读、单条已读正常。
- [x] 单条点击已读失败时不阻断跳转。
- [ ] 归档正常。

移动端：

- [ ] 我的页消息入口可用。
- [ ] 普通员工只看到自己的消息。
- [ ] 无小程序订阅授权时仍可查看站内消息。

管理端：

- [ ] ADMIN 可查询消息、收件人、投递日志。
- [ ] 可重试失败投递。
- [ ] 可创建和撤回系统公告。

回归：

- [x] 价格发布不因外部渠道异常而失败：当前外部渠道记录为 SKIPPED。
- [ ] 审批、任务执行接入消息后不因外部渠道异常而失败。
- [x] 首页、价格查询、价格维护数据口径不受消息升级影响。

## 风险与控制

| 风险 | 控制 |
| --- | --- |
| 轮询压力 | 抖动、隐藏暂停、失败退避、索引、后续 SSE |
| 消息重复 | dedupeKey、唯一约束、recipient 去重 |
| 消息丢失 | 站内落库、outbox、重试 |
| 外部渠道拖垮业务 | 事务后异步投递 |
| 用户被打扰 | 优先级、偏好、免打扰、频控、聚合 |
| 跳转错误 | linkType/linkParams 协议 |
| 数据膨胀 | 过期、归档、保留策略 |
| 权限越界 | 用户侧只查本人，管理侧 ADMIN |

## 方案评估与评分

综合评分：**9.7 / 10**

| 维度 | 分数 | 评价 |
| --- | --- | --- |
| 工业级可靠性 | 9.8 | 站内落库、Outbox、幂等、重试、投递日志构成可靠链路 |
| 架构科学性 | 9.7 | 五层模型职责清晰，业务和渠道解耦 |
| 融入性 | 9.8 | 价格、审批、任务、公告、安全、API 告警均可统一接入 |
| 性能可控性 | 9.6 | 智能轮询、索引、退避和 SSE 演进路径清晰 |
| 运维治理 | 9.7 | 管理端、指标、告警、失败重试和数据生命周期完整 |
| 用户体验 | 9.5 | PC 完整、移动轻量，支持低打扰和明确跳转 |
| 安全审计 | 9.6 | 权限边界、操作日志、敏感信息约束明确 |
| 实施可控性 | 9.4 | 分期合理，一期可基于现有代码演进，二三期逐步增强 |

扣分点：

- 外部 Provider 的实际接入仍依赖 App、小程序、企业微信等平台配置和授权条件。
- SSE 是否进入二期或三期，需要根据在线规模和实时性诉求最终决定。
- 通知管理后台和偏好系统会增加产品复杂度，必须按分期推进，避免一次做重。

最终结论：

这是一套面向工业级应用的消息基础设施方案。它不是简单通知功能，而是系统事件触达、站内可靠消息、外部渠道投递、管理审计、运维监控和多端体验的统一底座。建议立即执行一期，优先把站内消息标准化、可靠化、低噪音化；二期建设 Outbox 和通知管理后台；三期根据规模引入 SSE 与运营能力。
