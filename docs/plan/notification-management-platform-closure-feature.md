# Notification Management Platform Closure Feature

更新时间：2026-06-09

## 文档职责

本文位于 `docs/plan/`，仅负责规划通知管理平台尚未闭环的工作，不重复记录已完成能力。

- 当前代码事实与验收状态：见 [通知管理平台当前实现状态.md](通知管理平台当前实现状态.md)
- 目标设计与历史决策：见 [notification-management-platform-feature.md](notification-management-platform-feature.md)

当当前实现状态发生变化时，应先更新事实状态文档，再同步调整本文的范围、实施步骤和 Verification。

## Context

通知管理平台主体代码已经实现，最近一次本地自动化基线为后端 `mvn test` 64 项通过、PC 前端 `npm run build` 通过。但仍有以下工作不能由本地代码完成状态替代：

1. 微信订阅消息缺真实平台联调、模板有效性和跳转验证证据。
2. 真实 MySQL 缺索引命中和并发 Outbox 验证记录。
3. 生产角色授权和历史敏感日志仍缺上线环境复核记录。

此外，现有异常处理中的 `FOLLOW_UP` 仅表示跟进标记，不是实际人工跟进任务。本计划明确区分“必须闭环项”和“需产品决策项”。

## 目标与边界

### 必须闭环

- WX-01：完成真实微信 token、授权、测试投递、正式投递和点击跳转验证。
- DB-01：完成真实 MySQL Explain 和并发 Outbox 验证并保存证据。
- OPS-01：复核生产角色的通知细粒度权限分配，并巡检历史操作日志和投递日志是否存在敏感信息。
- DOC-01：验证完成后同步状态文档、设计文档、数据字典和部署说明。

### 需产品决策

- TASK-01：是否需要把“跟进标记”升级为真实人工跟进任务。

不在本轮范围：

- 企业微信、App Push 等新增 Provider。
- 通用工单系统或跨模块任务中心，除非 TASK-01 明确批准。

## 实现方案

### 1. 订阅资格快照与数据库分页

实施状态：`[x]` 已完成代码闭环。已新增资格快照表、增量刷新、模板配置变化重建、每日校准和数据库 JOIN 状态分页；真实 MySQL Explain 与 10 万用户规模验证归入 DB-01。

推荐方案：新增用户级可查询资格快照表，避免每次筛选都跨用户、模板配置和授权明细做动态全量聚合。

方案比较：

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 查询时 SQL 聚合 | 无冗余快照，实时一致 | SQL 复杂，动态模板集合、关键词、角色和状态分页组合后难稳定优化 | 适合作为小规模过渡方案 |
| 用户级资格快照 | 查询简单、索引稳定、适合管理端分页和统计 | 需要刷新机制，存在短暂最终一致 | 推荐 |
| 继续内存过滤 | 实现成本低 | 用户量增长后内存、响应时间和分页准确性不可控 | 禁止作为闭环方案 |

表：`notification_mini_program_eligibility`

| 字段 | 说明 |
| --- | --- |
| `user_id` | 用户 ID，唯一 |
| `row_status` | `NORMAL/LOW_BALANCE/UNBOUND/REJECTED` |
| `openid_bound` | 是否绑定 OpenID |
| `configured_template_count` | 当前配置模板数 |
| `authorized_template_count` | 已授权模板数 |
| `available_total` | 可用授权总次数 |
| `last_authorized_time` | 最近授权时间 |
| `config_fingerprint` | 生成快照时使用的模板配置指纹 |
| `created_time/updated_time` | 审计时间 |

索引：

- `uk_notification_mini_eligibility_user(user_id)`
- `idx_notification_mini_eligibility_status_user(row_status, user_id)`
- `idx_notification_mini_eligibility_authorized_time(last_authorized_time)`

刷新触发：

- 用户 OpenID 或状态变化后刷新单用户。
- 用户上报订阅授权后刷新单用户。
- 小程序模板配置变化事务提交后触发全量重建。
- 每日低峰定时校准，修复遗漏事件。

查询方式：

- `sys_user` JOIN `notification_mini_program_eligibility`，角色、关键词、行状态和分页一次下推数据库。
- 当前页再批量加载模板明细、异常处理记录和最近投递，不进行逐行查询。

一致性策略：

- 快照允许秒级最终一致，不参与真实投递资格判断。
- Provider 发送前仍读取原始授权记录，避免快照过期造成误发。
- 模板配置变化时更新全局配置指纹并异步重建；指纹不一致的旧行可展示“刷新中”。

验收：

- 带 `status` 筛选时不调用 `findByStatus(ACTIVE)` 全量加载用户。
- SQL Explain 命中状态分页索引。
- 10 万用户规模下，常用筛选 P95 小于 500ms，具体阈值可按部署资源校准。

### 2. 真实微信联调闭环

先执行能力确认，不假设微信一定提供模板状态查询 API：

1. 核对微信官方当前接口能力，确认是否存在可用于当前账号类型的模板状态查询接口。
2. 若存在，新增只返回脱敏结果的远程模板校验接口。
3. 若不存在，采用“本地配置校验 + 微信后台人工审核记录 + 隔离测试投递”作为模板有效性证据。

联调场景：

| 场景 | 预期 |
| --- | --- |
| 正确 AppID/AppSecret | token 测试成功，响应和日志无 token 明文 |
| 错误 AppSecret | 返回脱敏失败原因，操作日志可追踪 |
| 用户未绑定 OpenID | delivery 为 `SKIPPED/USER_NOT_BOUND` |
| 用户未授权模板 | delivery 为 `SKIPPED/SUBSCRIBE_NOT_AUTHORIZED` |
| 隔离测试投递 | `is_test=true`，真实调用微信，不进入用户站内列表 |
| 正式系统公告 | `IN_APP + MINI_PROGRAM` 均生成，站内始终兜底 |
| 点击订阅消息 | 进入配置页面并通过 REST 拉取对应业务数据 |

证据要求：

- 记录测试时间、环境、模板、目标测试用户、delivery ID、微信返回码和跳转结果。
- 截图和日志必须脱敏，不保存 AppSecret、access_token 或完整 OpenID。

### 3. 真实 MySQL 验证

必须记录：

- `/api/notifications/my` 分页 recipient + 批量 message 查询的 Explain。
- `/api/notifications/unread-count` 的索引命中。
- 带行状态筛选的订阅资格分页 Explain。
- 并发创建同一 Outbox aggregate 时唯一约束行为。
- Outbox worker 多实例 claim 时无重复领取证据。

建议数据规模：

- 用户 10 万。
- 通知消息 100 万。
- recipient 500 万。
- delivery/outbox 各 100 万。

输出物：

- `docs/archive/notification-management-mysql-verification.md`
- Explain 结果、测试命令、数据规模、P50/P95/P99 和结论。

### 4. 生产权限与敏感日志复核

权限复核：

- ADMIN 默认具备订阅查看、引导、异常处理、远程 token 校验和测试投递权限。
- EDITOR、VIEWER 等角色只获得业务确实需要的权限，不因父权限或历史角色数据意外继承高风险操作。
- 使用不同角色账号验证 PC 页签、按钮显隐和后端 API 拒绝行为一致。

日志巡检：

- 检查 `operation_log.request_params`、通知投递错误、应用日志和反向代理日志。
- 不得出现 AppSecret、access_token、完整 OpenID 或带敏感查询参数的 URL。
- 对迁移清理前的历史备份和导出文件执行同样检查，并记录处置结论。

输出物：

- `docs/archive/notification-management-production-security-verification.md`
- 记录角色权限矩阵、验证账号、API 结果、日志抽样范围和脱敏结论。

### 5. 跟进任务产品决策

当前实现只提供跟进标记。若业务只需要管理员筛选“需要跟进”的用户，保留现状并删除所有“生成任务”表述。

若确认需要真实任务，另立独立功能计划，至少包含：

- 任务 ID、关联用户、来源、负责人、截止时间、优先级、状态、备注和审计字段。
- 创建、转派、完成、关闭、逾期提醒和任务列表。
- `FOLLOW_UP` 异常处理操作创建任务并保存 taskId，要求幂等。
- 独立权限和操作日志。

在上述模型落地前，验收文案只能使用“设置跟进标记”。

### 6. 非阻断增强项

以下能力可继续优化，但不作为四期闭环门禁：

- 渠道模板编辑时提供更强的字段映射格式校验。
- 新增模板时使用通知类型字典下拉，并限制重复通知类型。
- 投递审计详情日志升级为后端分页。
- 将真实联调证据在 PC 运维页中结构化展示。

## 关键参考文件

- `backend/src/main/java/com/pricemanagement/service/AdminMiniProgramSubscriptionManagementService.java`
- `backend/src/main/java/com/pricemanagement/service/NotificationMiniProgramSubscriptionService.java`
- `backend/src/main/java/com/pricemanagement/service/notification/WechatMiniProgramNotificationProvider.java`
- `backend/src/main/java/com/pricemanagement/service/NotificationOutboxService.java`
- `frontend/src/views/Notifications.vue`
- `frontend-uniapp/src/pages/profile/index.vue`

## 实现步骤

1. `[x]` 新增资格快照表、Entity、Repository、迁移、`init.sql` 和数据字典。
2. `[x]` 实现单用户刷新、模板变化重建和定时校准。
3. `[x]` 将订阅授权状态筛选改为数据库 JOIN 分页，并补单元测试；规模测试并入 DB-01。
4. 在真实微信测试账号完成 token、授权、隔离测试投递、正式投递和跳转验证。
5. 在真实 MySQL 完成 Explain 与并发测试，归档证据。
6. 在生产等价环境完成角色权限复核和敏感日志巡检，归档证据。
7. 对 TASK-01 做产品决策；未批准则统一删除“生成跟进任务”表述。
8. 更新当前状态文档；只有全部必须闭环项通过后，四期才可标记 `[x]`。

## Verification

- 后端单元测试覆盖快照刷新、状态计算、数据库分页和模板变化重建。
- Repository 集成测试证明带状态筛选使用数据库分页。
- `mvn test`、PC `npm run build`、小程序构建通过。
- 真实微信联调记录完整且脱敏。
- 真实 MySQL Explain 和并发验证记录已归档。
- 生产角色权限矩阵和敏感日志巡检记录已归档。
- 文档不再同时出现同一能力“已完成”和“待补齐”的冲突状态。
