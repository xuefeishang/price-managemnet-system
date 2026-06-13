# 工业级消息管理平台建设方案

> **文档定位说明**
>
> 本文保留目标架构、设计决策和历史实施记录，不再作为当前完成状态的唯一事实源。
>
> - 当前代码事实与验收状态：见 [通知管理平台当前实现状态.md](通知管理平台当前实现状态.md)
> - 尚未闭环工作的实施计划：见 [notification-management-platform-closure-feature.md](notification-management-platform-closure-feature.md)
>
> 如本文后续历史章节与“当前实现状态”冲突，以当前实现状态文档和代码为准。

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

更新时间：2026-06-09

状态说明：

- `[x]` 已完成并通过基础验证
- `[~]` 已实现主体能力，仍需加固或补测试
- `[ ]` 未开始
- `[!]` 新发现问题或风险项

### 当前完成度

| 阶段 | 状态 | 完成情况 |
| --- | --- | --- |
| 一期：站内消息工业化 | `[x]` 已完成 | 字段扩展、站内通知入口、智能轮询、全部已读、归档、价格发布结构化跳转、批次级通知幂等、点击通知容错、列表 N+1 优化已完成 |
| 二期：治理后台和业务接入 | `[x]` 已完成 | PC 治理页面、系统公告、Webhook Provider MVP、审批待办、定时任务失败、外部 API 告警、导入导出完成接入、延迟投递偏好重算、菜单权限、V26 迁移和审计补丁已完成 |
| 三期：观测、多端和实时增强 | `[x]` 主体完成 | 移动端消息入口、指标看板、Provider 健康检查、SSE 轻事件、REST 轮询降级、聚合频控已完成；真实 MySQL 索引/并发压测仍需环境验证 |
| 四期：小程序订阅消息触达 | `[~]` 运维主体闭环，继续处理真实微信与生产等价环境验证 | PC `http://localhost:5173/notifications` 已补齐角色契约、异常处理、远程 token 校验、测试投递、详情抽屉、操作日志和资格快照数据库状态分页；真实微信模板状态仍需联调，因此暂不标记为 9.5+ |

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
| 通知列表 N+1 优化 | `[x]` | `getMyNotifications` 先分页查询 recipient，再按当前页 messageId 批量 `findAllById`，已消除逐条查询 |
| PC 通知归档 | `[x]` | 已新增 `/api/notifications/{messageId}/archive` 和抽屉归档按钮 |
| Outbox 投递底座 | `[x]` | 已新增 `notification_outbox`、Entity、Repository、worker、指数退避和最大重试 |
| 通知偏好底座 | `[x]` | 已新增 `notification_preference` 和用户偏好 API；站内通知不可关闭，外部渠道可关闭 |
| ADMIN 审计 API | `[x]` | 已新增全局消息筛选、详情、收件人、投递日志和投递重试接口；重试入口状态机已加固 |
| 三期观测看板 | `[x]` | 已新增 `/api/admin/notifications/dashboard`、Provider 健康状态和聚合频控规则接口，并在 PC 通知管理页展示 |
| 三期实时增强 | `[x]` | 已新增 `/api/notifications/events` SSE 轻事件；PC 端使用 fetch stream 携带 Bearer Token，断开后回退轮询 |
| 三期移动端入口 | `[x]` | uni-app 我的页新增消息入口和未读数，移动通知页支持全部/未读、全部已读、归档、价格查询跳转 |
| PC 信息发布管理台 | `[x]` | `http://localhost:5173/notifications` 已具备通知治理、系统公告创建/发布/撤回、目标角色和通知渠道选择能力，可作为小程序消息发布的唯一管理入口 |
| 小程序站内接收兜底 | `[x]` | 小程序已通过 `/api/notifications/unread-count`、`/api/notifications/my` 接收站内消息；无微信订阅授权时仍可查看消息 |
| 小程序订阅消息 Provider | `[~]` | 已新增微信订阅消息 Provider、模板配置、授权记录表和小程序授权入口；真实 AppID/AppSecret、模板审核和线上投递待联调 |
| 小程序配置运维升级 | `[~]` | 配置安全、保存语义、缓存失效、细粒度权限、状态字典、隔离测试投递、异常处理和资格快照数据库分页已完成；剩余真实微信与生产等价环境验证 |
| 站内通知底座门禁 | `[x]` | `NotificationService` 自动保留 `IN_APP` 渠道，即使业务方只传外部渠道，用户侧仍能在站内列表查看 |
| 文档同步 | `[x]` | README、开发指南、设计文档、数据字典、UI 说明已同步一期能力 |

### 新发现问题跟踪

| 编号 | 问题 | 严重性 | 状态 | 处理方案 |
| --- | --- | --- | --- | --- |
| N-001 | 部分发布失败后再次发布可能重复写入已成功明细 | 高 | `[x]` 已修复 | 发布时跳过 `itemStatus=PUBLISHED` 且 `publishedPriceId` 非空的明细 |
| N-002 | 价格发布通知原先按 `publishLogId` 去重，不能代表同一草稿批次幂等 | 中 | `[x]` 已修复 | `dedupeKey` 改为 `PRICE_PUBLISHED:BATCH:{batchId}` |
| N-003 | 我的通知列表存在 recipient 到 message 的 N+1 查询 | 中 | `[x]` 已修复 | 先分页查询 recipient，再按当前页 messageId 批量 `findAllById`；单元测试约束不再逐条 `findById` |
| N-004 | 点击通知时若标记已读失败，会阻断后续业务跳转 | 中低 | `[x]` 已修复 | 前端已改为先跳转/乐观已读，失败后刷新未读数 |
| N-005 | `notification_outbox` 尚未落地，外部渠道仍只是 SKIPPED 预留 | 中 | `[x]` 已修复 | 外部渠道先写 PENDING delivery + outbox，worker 异步处理；Provider 未配置时转 SKIPPED |
| N-006 | 投递重试接口可重试任意 delivery，可能把 `IN_APP/SUCCESS` 改坏或重复发送外部消息 | 高 | `[x]` 已修复 | 已增加重试状态机：仅失败的外部渠道投递可重试，`IN_APP`、`SUCCESS`、`PENDING`、`SKIPPED` 均拒绝 |
| N-007 | Outbox worker 的领取、外部发送和结果落库处于同一事务边界，进程中断时存在重复发送风险 | 高 | `[x]` 已修复 | 已拆分 claim、Provider send、apply result 三段；外部发送不再持有长事务 |
| N-008 | `notification_outbox` 缺少 `(aggregate_type, aggregate_id)` 唯一约束，服务层幂等无法抵御并发插入 | 中高 | `[x]` 已修复 | 迁移脚本、`init.sql` 和 Entity 已同步增加 `uk_notification_outbox_aggregate` |
| N-009 | 免打扰时间已保存但未参与外部渠道投递判断 | 中 | `[x]` 已修复 | 普通外部通知命中免打扰时延迟 outbox 至免打扰结束；URGENT 可绕过并写入原因 |
| N-010 | Provider 返回错误未统一截断，长错误可能导致状态落库失败 | 中 | `[x]` 已修复 | 所有失败、跳过、异常结果落库前统一截断到数据库字段长度 |
| N-011 | 系统公告撤回或过期后，已生成的用户通知仍会在用户侧列表和未读数中保留 | 高 | `[x]` 已修复 | 撤回公告和公告过期任务会同步归档对应 `notification_recipient`；用户侧通知查询和未读数过滤已过期 `notification_message.expire_time` |
| N-012 | 未读数统计未排除已归档通知，归档未读消息后红色角标可能仍然存在 | 高 | `[x]` 已修复 | 未读数查询、全部已读和用户通知列表统一排除 `archived=true` 和已过期消息；补充单元测试覆盖 |
| N-013 | 系统公告计划发布时间和过期时间前端提示为 `yyyy-MM-dd HH:mm:ss`，但后端 `LocalDateTime` 默认更偏向 ISO 格式，存在创建定时公告 400 风险 | 中 | `[x]` 已修复 | 前端改用 `datetime-local` 并提交 ISO 本地时间；后端增加计划发布时间必须早于过期时间校验 |
| N-014 | `system_notice` 数据字典/设计文档字段长度与 V26、`init.sql`、Entity 不一致 | 中 | `[x]` 已修复 | 新增 `V27__system_notice_field_capacity_alignment.sql` 扩字段；`init.sql`、Entity、数据字典和设计文档统一为 `VARCHAR(200/500)` 与 `TEXT` |
| N-015 | 已新增 `notification:retry`、`system-notice:create`、`system-notice:cancel` 权限码，但 Controller 仍只校验 `hasRole('ADMIN')` | 低 | `[x]` 已修复 | JWT 携带权限码并注入 Spring Security authorities；Controller 使用 `hasAuthority(...)`，前端按钮也按权限码显隐 |
| N-016 | 只传外部渠道时 `channels` 可能缺少 `IN_APP`，与站内通知必选底座不一致 | 中 | `[x]` 已修复 | `NotificationService.resolveChannels` 固定保留 `IN_APP`，并补单元测试覆盖 |
| N-017 | 小程序 `MINI_PROGRAM` 渠道需要真实调用微信订阅消息接口 | 中高 | `[x]` 已实现 | `WechatMiniProgramNotificationProvider` 已接入微信发送接口和 Outbox；真实微信成功回执作为独立联调验证项 |
| N-018 | 小程序需要在离线或非前台场景获得主动触达 | 中 | `[x]` 已实现代码链路 | 小程序显式订阅授权、后端授权记录和 Outbox 异步发送已接入；真机主动触达和点击跳转作为独立联调验证项 |
| N-019 | 管理端渠道配置 DTO 返回完整 `templateId`，与“页面显示脱敏模板 ID、配置最小化暴露”不一致 | 高 | `[x]` 已修复 | `GET /channels/{channel}/config` 只返回 `templateIdMasked`；前端编辑不再依赖普通查看接口回填完整模板 ID，留空按保留处理 |
| N-020 | 渠道配置保存时空字符串会覆盖数据库配置，和“留空则保留/使用环境配置”的页面语义冲突 | 高 | `[x]` 已修复 | 后端保存逻辑区分“未传/空字符串/显式清空”；空字符串按不变处理，新增 `clearAppId`、`clearEndpointUrl`、`clearDefaultPage`、`clearSecret` 显式清空字段；模板配置基于当前 DB/环境兜底合并 |
| N-021 | AppID/AppSecret 更新后 `WechatMiniProgramNotificationProvider` 的 access_token 缓存不会立即失效 | 高 | `[x]` 已修复 | token 缓存绑定 AppID、AppSecret、tokenUrl 的不可逆 SHA-256 指纹；保存配置后发布配置变更事件并清空 Provider token 缓存 |
| N-022 | 渠道配置编辑/保存/模板维护按钮前端未按后端 `system:setting` 权限控制 | 中 | `[x]` 已修复 | 前端使用 `Permission.SYSTEM_SETTING` 控制编辑、保存、配置、新增模板和删除模板；无权限用户只能查看脱敏配置和诊断状态 |
| N-023 | 小程序订阅授权行状态 `NORMAL/LOW_BALANCE/UNBOUND/REJECTED` 在前端硬编码中文显示 | 中 | `[x]` 已修复 | 新增字典分类 `notification_mini_subscription_row_status`，迁移脚本、`init.sql`、数据字典、`useDict` 元数据同步；列表和筛选使用 `getDictValue/getDictOptions` |
| N-024 | 订阅授权行状态固定按价格和公告两个模板判断，未来只配置部分模板或新增模板时会误判低余量 | 中 | `[x]` 已修复 | 行状态按运行时已配置模板集合动态计算，只统计当前配置模板，不再固定价格/公告两个模板 |
| N-025 | 订阅授权列表先加载全部 ACTIVE 用户再内存过滤分页，用户量扩大后不符合管理端分页规范 | 中 | `[x]` 已修复 | 新增用户级资格快照；带聚合行状态筛选通过 `sys_user` JOIN 快照表执行数据库分页，当前页再批量加载授权明细 |
| N-026 | 同一方案同时承担当前状态、目标设计和历史记录，导致状态冲突 | 中低 | `[x]` 已修复 | 当前实现状态与剩余闭环计划已拆分为独立文档；本文保留目标设计和历史记录，并声明事实源优先级 |

### 下一步开发计划

当前只按 [notification-management-platform-closure-feature.md](notification-management-platform-closure-feature.md) 推进剩余闭环工作：

1. 真实微信 token、授权、隔离测试投递、正式投递和点击跳转验证。
2. 真实 MySQL Explain 与并发 Outbox 验证。
3. 生产角色权限和敏感日志复核。
4. 确认“跟进标记”是否需要升级为真实人工跟进任务。

### 当前页面与方案匹配性评估

评估对象：PC `http://localhost:5173/notifications`。

评估结论：**运维主体已闭环，环境与规模验证未闭环**。5 页签通知控制台均已接入真实接口；渠道配置、订阅授权、资格快照数据库分页、远程 token 校验、隔离测试投递和异常处理可用。剩余缺口是真实微信联调证据、真实 MySQL 验证记录和生产环境复核。

| 页签 | 方案要求 | 当前实现 | 匹配度 | 主要缺口 |
| --- | --- | --- | --- | --- |
| 总览 | 运行态、通知列表、链路详情、Provider 健康、聚合频控 | 已接入 dashboard、Provider health、throttle rules、通知列表、收件人和投递日志 | 高 | 投递日志当前为前端分页，超大日志量时需后端分页 |
| 消息发布 | 系统公告创建、目标角色、渠道选择、发布/撤回、发布前触达预估 | 公告创建、发布、撤回、状态筛选和真实覆盖率 API 已接入 | 高 | 真实微信投递联调证据 |
| 投递审计 | 审计列表、错误定位、失败重试、Outbox 状态 | 通知审计、当前通知投递日志、失败重试和 Outbox 指标可用 | 高 | 日志筛选当前只针对已加载详情数据 |
| 渠道配置 | Provider 启用、非敏感配置维护、模板映射、密钥状态、诊断测试 | 配置安全、保存语义、token 缓存失效、权限、远程 token 校验和隔离测试投递已接入 | 高 | 真实微信模板与投递联调证据 |
| 订阅授权 | 用户授权覆盖、openid 绑定、模板余量、拒绝/低余量处理、授权引导 | 真实指标、列表、详情、引导、异常处理、暂不提醒和字典状态已接入 | 中高 | 带聚合行状态筛选的数据库分页 |

功能边界校准：

- `总览 / 消息发布 / 投递审计` 可以视为当前可用的管理闭环。
- `渠道配置 / 订阅授权` 已具备真实运维主体能力，但在真实微信和规模验证完成前不标记为 9.5+。
- 测试投递和异常处理已经接入真实后端接口；测试投递使用隔离链路，异常处理中的 `FOLLOW_UP` 仅为跟进标记。
- 当前状态以 [通知管理平台当前实现状态.md](通知管理平台当前实现状态.md) 为准。

### 四期整改工作跟踪清单

> 本清单同时保留已完成项和剩余项。真正尚未闭环的工作以独立闭环计划为准。

#### P0：渠道配置真实闭环

目标：让 `渠道配置` 页签从静态展示升级为可维护、可诊断、可审计的运维页面。

| 编号 | 工作项 | 交付物 | 验收标准 |
| --- | --- | --- | --- |
| C-01 | Provider 配置表 | `[x]` `notification_channel_config` 迁移、Entity、Repository、数据字典同步 | 当前按 `channel` 唯一维护配置，小程序订阅消息已接入 |
| C-02 | 配置读取服务 | `[x]` `NotificationMiniProgramRuntimeConfigService` | `MINI_PROGRAM` Provider 发送时优先读取数据库配置，缺失才回退 `application.yml` |
| C-03 | 配置摘要 API | `[x]` `GET /api/admin/notifications/channels/{channel}/config` | 渠道配置页展示启用状态、配置完整度、模板映射和诊断清单 |
| C-04 | 配置详情 API | `[x]` `GET /api/admin/notifications/channels/{channel}/config` | 返回 AppID 脱敏、超时、默认跳转页、模板映射和密钥状态；普通查看接口只返回 `templateIdMasked` |
| C-05 | 非敏感配置保存 API | `[x]` `PUT /api/admin/notifications/channels/{channel}/config` | 空字符串按不变处理，显式清空使用 `clearXxx`，模板配置按当前值合并 |
| C-06 | 操作日志 | `[x]` `@OperationLog` 覆盖配置保存、授权引导 | 配置变更能追踪操作人、时间、模块和结果 |
| C-07 | 权限控制 | `[x]` 使用 `notification:view` 查看、`system:setting` 修改 | 后端校验保存权限，前端渠道配置操作按权限码控制 |

#### P0：AppSecret 密钥托管

目标：支持 PC 更新 AppSecret，但系统任何前端响应、日志和导出都不出现明文。

| 编号 | 工作项 | 交付物 | 验收标准 |
| --- | --- | --- | --- |
| S-01 | 密钥托管字段 | `[x]` `notification_channel_config.secret_cipher` | AppSecret 使用 AES-GCM 密文保存，响应只返回状态 |
| S-02 | 加密服务 | `[x]` 复用 `ApiKeySecretService` / `API_KEY_ENCRYPTION_KEY` | 避免重复维护密钥体系，生产环境必须注入独立 32 字节 Base64 主密钥 |
| S-03 | 密钥更新 API | `[x]` `PUT /api/admin/notifications/channels/{channel}/config` | 请求只接收新密钥，响应只返回配置状态、来源、更新时间 |
| S-04 | access_token 缓存失效 | `[x]` 更新密钥后发布配置变更事件并清空小程序 token 缓存 | 新密钥保存后下一次请求立即使用新凭据 |
| S-05 | 安全审计 | `[x]` 日志/响应脱敏约束 | 普通日志、操作日志、投递日志不包含 AppSecret 明文 |
| S-06 | 配置指纹缓存 | `[x]` token 缓存绑定 AppID、密钥指纹和 tokenUrl | 防止切换 AppID/AppSecret 后复用旧 access_token；指纹不可反推出 AppSecret |

#### P0：配置诊断与联调工具

目标：让管理员知道“小程序订阅消息未配置”具体缺什么，并能在 PC 端完成安全联调。

| 编号 | 工作项 | 交付物 | 验收标准 |
| --- | --- | --- | --- |
| D-01 | 配置校验 API | `[x]` `POST /api/admin/notifications/channels/{channel}/test` | 返回缺项、严重性、修复建议和通过数 |
| D-02 | token 测试 API | `[x]` `POST /api/admin/notifications/channels/{channel}/test-token` | 远程请求微信 token 接口并返回脱敏诊断结果，不返回 access_token 明文 |
| D-03 | 模板校验 | `[~]` 模板 ID、字段映射、跳转页本地校验 | 已能定位缺失项；微信后台模板状态仍需真实接口/人工确认 |
| D-04 | 测试投递 API | `[x]` `POST /api/admin/notifications/channels/{channel}/test-delivery` | 在订阅详情选择目标用户后按已配置模板生成 `is_test=true` 的隔离 delivery/outbox，不进入站内列表、不参与聚合和偏好延迟 |
| D-05 | 最近失败跳转 | `[x]` 诊断面板跳转投递审计并带入渠道、失败状态筛选 | 管理员能从配置页定位失败投递日志 |
| D-06 | 诊断操作日志 | `[x]` 本地诊断、远程 token 校验和测试投递均增加 `@OperationLog` | 记录操作人、时间、渠道和结果，不记录密钥/token |

#### P0：发布前真实触达预估

目标：消息发布页勾选 `MINI_PROGRAM` 时展示真实可触达范围，而不是前端估算。

| 编号 | 工作项 | 交付物 | 验收标准 |
| --- | --- | --- | --- |
| R-01 | 覆盖率 API | `[x]` `GET /api/admin/notifications/mini-program/coverage` | 按目标角色、通知类型返回目标用户、openid 绑定、模板授权、预计触达、站内兜底 |
| R-02 | 前端接入 | `[x]` 消息发布页替换 `miniProgramImpact` 模拟值 | 改变目标角色或渠道后预估刷新 |
| R-03 | 配置缺失提示 | 发布前区分配置缺失、授权为 0、正常可触达 | 配置缺失不阻断站内发布，但明确提示小程序会跳过 |
| R-04 | 发布审计记录 | 公告发布时记录预估快照或可追溯统计口径 | 事后可解释“为什么某些用户未收到微信订阅消息” |
| R-05 | 角色参数契约 | `[x]` 前端传逗号字符串，后端接收 `String roles` 并显式拆分、校验枚举 | 不再依赖 Spring 对枚举列表的隐式拆分行为 |

#### P1：订阅授权运维闭环

目标：让 `订阅授权` 页签展示真实用户接收资格，并支持管理动作。

| 编号 | 工作项 | 交付物 | 验收标准 |
| --- | --- | --- | --- |
| A-01 | 授权分页 API | `[x]` `GET /api/admin/notifications/mini-program/subscriptions` | 支持角色、状态、关键词筛选和分页 |
| A-02 | 单用户详情 API | `[x]` `GET /api/admin/notifications/mini-program/subscriptions/{userId}` | 展示 openid 脱敏、模板余量和最近授权 |
| A-03 | 批量授权引导 | `[x]` `POST /api/admin/notifications/mini-program/authorization-guides` | 可按筛选条件发送站内引导消息 |
| A-04 | 单用户引导 | `[x]` `POST /api/admin/notifications/mini-program/authorization-guides/{userId}` | 对低余量/未绑定用户单独引导 |
| A-05 | 异常处理 | `[x]` `POST /api/admin/notifications/mini-program/subscriptions/{userId}/resolve` | 用户级记录支持标记已处理、暂不提醒、记录备注和生成跟进标记，未绑定 OpenID 用户也可处理 |
| A-06 | 权限控制 | `[x]` `notification:subscription:view/guide/resolve` | 后端和前端页签、按钮权限一致；无权限用户不能查看、发起引导或处理 |
| A-07 | 前端真实数据替换 | `[x]` 替换当前静态用户行和模拟指标 | 页面不再展示固定姓名和固定覆盖数 |
| A-08 | 行状态字典 | `[x]` 新增 `notification_mini_subscription_row_status` | `NORMAL/LOW_BALANCE/UNBOUND/REJECTED` 的列表、筛选和详情显示均走字典 |
| A-09 | 动态模板状态 | `[x]` 行状态按运行时模板集合动态计算 | 不再固定价格/公告两个模板，避免新增或停用模板时误判 |
| A-10 | 数据库分页 | `[x]` | 已建立 `notification_mini_program_eligibility` 资格快照，带聚合行状态筛选时通过数据库 JOIN 分页，当前页再批量查询授权明细 |

#### P1：文档与验收补齐

目标：保证实现过程符合项目规范，不让配置类、数据库、前端类型和文档漂移。

| 编号 | 工作项 | 交付物 | 验收标准 |
| --- | --- | --- | --- |
| DOC-01 | 数据字典同步 | 数据字典、`init.sql`、迁移脚本 | Provider 配置、密钥、订阅处理状态字段一致 |
| DOC-02 | API 文档同步 | `docs/dev/项目设计文档.md` 和 API 调用手册 | 新增接口路径、权限码、请求/响应结构完整 |
| DOC-03 | 运维文档同步 | `docs/ops/IDEA部署指南.md` / 操作手册 | 说明密钥主密钥、微信配置来源、生产配置步骤 |
| DOC-04 | UI 说明同步 | `docs/dev/UI设计说明.md` | 说明渠道配置和订阅授权的真实交互 |
| DOC-05 | 回归清单 | 前后端构建、核心 API、权限按钮、真实微信联调 | 完成后才能把四期状态从 `[~]` 改为 `[x]` |

### 历史执行跟踪清单

> 本清单用于记录各阶段曾经的执行顺序，不代表当前下一步工作。

| 顺序 | 工作项 | 交付物 | 验证方式 |
| --- | --- | --- | --- |
| 1 | 通知列表 N+1 优化 | `NotificationService.getMyNotifications` 分页 recipient 后批量查询 message | `[x]` 单元测试覆盖；SQL/Explain 待真实 MySQL 记录 |
| 2 | 点击通知容错 | PC 通知点击不因已读接口失败阻断跳转 | `[x]` 已完成 |
| 3 | 一期回归测试 | 发布、未读数、全部已读、归档、结构化跳转完整回归 | `[x]` `mvn test`、`npm run build` 已通过 |
| 4 | Outbox 表与实体 | `notification_outbox` 迁移、Entity、Repository | `[x]` 后端启动和测试通过 |
| 5 | Outbox Worker | 领取、锁定、重试、失败保留 | `[x]` 未配置 Provider 降级、Webhook 成功、失败和超时测试通过 |
| 6 | 管理端通知列表 | ADMIN 管理 API + PC 页面 | `[x]` 支持筛选、详情、收件人、投递日志和失败重试 |
| 7 | 重试入口状态机 | 禁止 `IN_APP/SUCCESS` 等不可重试投递进入重试 | `[x]` 单元测试覆盖非法重试；操作日志注解沿用 ADMIN 重试接口 |
| 8 | Outbox 事务拆分 | claim 单独提交、发送不持有长事务、结果单独落库 | `[x]` 单元测试覆盖 Provider 结果交接、成功落库、失败重试和批量任务隔离基础 |
| 9 | Outbox 唯一约束 | `(aggregate_type, aggregate_id)` 唯一约束和 Entity 注解 | `[x]` 迁移脚本、`init.sql`、Entity、数据字典已同步 |
| 10 | 免打扰生效 | 外部渠道命中免打扰时延迟投递，URGENT 例外 | `[x]` 单元测试覆盖跨天免打扰、URGENT 绕过和通知创建延迟入队 |
| 11 | Provider 幂等契约 | 明确 Provider 调用必须使用 `notification_delivery_log.id` 作为幂等键 | `[x]` Webhook Provider 使用 `delivery-{id}` 幂等键并覆盖未配置、成功、失败和超时路径 |
| 12 | Outbox 并发验证 | 真实 MySQL 或等价集成环境验证并发 enqueue 唯一约束 | `[~]` 迁移、Entity 与单元测试覆盖唯一约束语义；真实 MySQL 并发压测留到三期环境验证 |
| 13 | 迁移版本策略 | 若 V25 已被环境执行，则使用后续迁移补约束；未发布环境保持 V25 一致 | `[x]` 不改写 V25，二期统一追加 `V26__notification_management_phase2.sql` 并同步 `init.sql` |
| 14 | 延迟期间偏好重算 | 免打扰延迟到期后发送前重新读取偏好 | `[x]` Outbox 发送前重新读取偏好，支持关闭渠道跳过、免打扰再延迟、URGENT 绕过 |
| 15 | 公告撤回/过期可见性修复 | 用户侧通知列表、未读数和公告状态保持一致 | `[x]` 单元测试覆盖公告撤回归档通知；用户列表和未读数过滤过期消息 |
| 16 | 归档未读数口径修复 | 未读数、全部已读和用户列表统一排除归档通知 | `[x]` 单元测试覆盖未读数使用可见通知查询 |
| 17 | 公告时间格式修复 | 定时发布和过期时间提交格式与后端 DTO 一致 | `[x]` 前端 `datetime-local` 提交 ISO 本地时间，后端校验计划时间和过期时间顺序 |
| 18 | system_notice 文档/DDL 一致性修复 | V26/init.sql/Entity/数据字典/设计文档字段长度统一 | `[x]` 新增 V27 扩字段，`init.sql` 和 Entity 已同步最终结构 |
| 19 | 通知权限码策略落地 | 明确权限码仅展示或真正用于 API 授权 | `[x]` JWT、Controller 和前端按钮均使用通知权限码 |
| 20 | 小程序消息发布入口归一 | `http://localhost:5173/notifications` 作为系统公告和小程序消息发布的唯一管理台 | `[x]` 页面已保留目标角色、渠道选择、定时发布、发布和撤回能力；不在小程序端新增发布后台 |
| 21 | 小程序订阅模板配置 | 配置 `PRICE_PUBLISHED`、`SYSTEM_NOTICE` 等模板 ID、字段映射和跳转页面 | `[x]` 未配置模板时 `MINI_PROGRAM` delivery 记录 `SKIPPED/PROVIDER_NOT_CONFIGURED` |
| 22 | 小程序订阅授权采集 | 小程序前端在我的页调用订阅授权，并把授权结果上报后端 | `[x]` 用户拒绝或未授权时不报错，只保留站内消息 |
| 23 | 订阅授权数据模型 | 保存 userId、模板编码、模板 ID、授权状态、可用次数和最近授权时间 | `[x]` 与 `sys_user.wechat_openid` 关联，未绑定 openid 时外部投递跳过 |
| 24 | `MINI_PROGRAM` Provider | 微信订阅消息 Provider 使用 outbox worker 异步调用微信接口 | `[x]` 成功、拒收、未订阅、模板错误和 page 错误均进入投递状态机 |
| 25 | PC 发布页渠道提示 | 展示 Provider 状态、真实覆盖率和站内兜底提示 | `[x]` 已接入后端覆盖率 API |
| 26 | 小程序订阅消息验收 | 从 PC 发布 `IN_APP + MINI_PROGRAM`，已授权用户收到微信服务通知并能跳转消息页 | `[~]` 代码链路完成，待真实微信账号和真机验证 |

## 审计后完善与优化方案

### 阶段总览

| 阶段 | 名称 | 目标 | 评分 |
| --- | --- | --- | --- |
| 第一阶段 | 可靠性闭环 | 修复审计发现的高风险投递问题，确保不误发、不重复、不污染状态 | 9.7 / 10 |
| 第二阶段 | 治理后台和业务接入 | 把已有 API 变成可运营页面，并以最小真实 Provider 和核心业务事件形成运营闭环 | 9.6 / 10 |
| 第三阶段 | 观测、多端和实时增强 | 补齐移动端、指标看板、健康检查、聚合频控和按规模触发的 SSE 演进 | 9.5 / 10 |

综合评价：**9.6 / 10**。三阶段拆分后，第一阶段作为质量门禁，第二阶段只做治理和核心接入，第三阶段把观测、多端、聚合和 SSE 按规模推进，整体边界清晰且风险可控。

高分成立条件：

- 每个阶段必须有可执行验收项，不以后续阶段能力掩盖当前阶段缺口。
- 第一阶段已完成；二期 Webhook Provider 和更多业务事件已在可靠性门禁之上接入。
- 第二阶段 Provider 先以 `WEBHOOK` MVP 闭环，不一次性铺开所有渠道。
- 第三阶段 SSE 和聚合频控按在线规模、消息量和运维痛点触发，避免过早复杂化。

### 二期代码审计后的改善方案

本轮代码审计发现的问题不推翻二期主体架构，但会影响公告治理闭环、用户角标准确性和文档一致性。改善顺序按用户可见影响和发布风险排序。

#### P0：公告生命周期与用户通知可见性

目标：公告的 `DRAFT` / `SCHEDULED` / `PUBLISHED` / `CANCELLED` / `EXPIRED` 状态必须和用户侧通知列表、未读数保持一致。

改进要点：

- `system_notice.notification_message_id` 已保存发布后消息 ID，撤回公告时应通过该 ID 批量处理对应 `notification_recipient`，可选策略为归档、标记撤回或从用户列表过滤。
- 用户侧通知查询应过滤已过期消息：`notification_message.expire_time IS NULL OR expire_time > now`。
- 未读数、全部已读、用户列表必须使用同一可见性口径，避免列表看不到但角标仍显示。
- 公告过期定时任务不能只更新 `system_notice.status`，还应同步对应通知可见性。

验收：

- 已发布公告撤回后，目标用户通知列表不再显示该公告通知，未读数同步下降。
- 公告到达 `expire_time` 后，用户通知列表和未读数均不再包含该公告。
- 普通价格发布通知不受公告撤回/过期逻辑影响。

#### P0：归档与未读数口径统一

目标：归档是用户主动移出列表的动作，已归档通知不得继续贡献未读角标。

改进要点：

- 将 `countByUserIdAndReadStatus` 替换为包含 `archived=false` 的查询。
- `markAllReadByUserId` 增加 `archived=false` 条件，避免后台批量修改已归档记录。
- 前端归档未读通知后可以继续乐观更新角标，但最终以后端未读数为准。

验收：

- 归档未读通知后，用户未读数减少。
- 全部已读不会修改已归档通知的审计状态。

#### P1：系统公告时间格式一致

目标：创建定时公告和设置过期时间时，前后端时间协议稳定可用。

改进要点：

- 推荐前端使用 `datetime-local` 控件，提交 ISO `yyyy-MM-ddTHH:mm:ss`，和 Jackson `JavaTimeModule` 默认行为一致。
- 如果必须保留 `yyyy-MM-dd HH:mm:ss` 输入格式，则在 `SystemNoticeCreateRequest` 的 `scheduledPublishTime`、`expireTime` 上添加 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`。
- 表单保存前校验过期时间晚于当前时间、计划发布时间不晚于过期时间。

验收：

- 创建立即公告、定时公告、带过期时间公告均成功。
- 错误时间格式和过期时间早于当前时间时返回明确错误。

#### P1：数据库、Entity 与文档一致性

目标：`system_notice` 的表结构、Entity、`init.sql`、数据字典和设计文档完全一致。

改进要点：

- 当前 V26 / `init.sql` / Entity 口径为：`title VARCHAR(100)`、`summary VARCHAR(300)`、`target_roles VARCHAR(200)`、`channels VARCHAR(200)`。
- 当前数据字典和设计文档写为：`title VARCHAR(200)`、`summary VARCHAR(500)`、`target_roles TEXT`、`channels TEXT`。
- 若要扩大字段容量，不改写已执行的 V26，应追加 V27 修改字段长度，并同步 Entity、`init.sql` 和文档。
- 若接受当前容量，应把数据字典和设计文档改回 100/300/200/200。

验收：

- ORM 注解、V26/V27、`init.sql`、数据字典、设计文档字段名、类型、长度和索引名称一致。
- 后端启动时 Hibernate DDL 不出现与业务表结构不一致的提示。

#### P2：通知权限码策略落地

目标：新增权限码不成为“只插库不生效”的空权限。

改进要点：

- 若通知管理继续采用 ADMIN 粗粒度权限，文档应明确 `notification:view`、`notification:retry`、`system-notice:create`、`system-notice:cancel` 仅用于菜单和按钮展示，不作为 API 授权边界。
- 若进入按钮级 RBAC，则在对应 Controller 方法上使用 `hasAuthority('notification:retry')`、`hasAuthority('system-notice:create')`、`hasAuthority('system-notice:cancel')`，前端按钮也读取当前用户权限集合控制显隐。

验收：

- 权限说明、菜单显隐、按钮显隐和后端 API 授权边界一致。
- 非授权账号无法通过直接调用 API 绕过按钮限制。

### 第一阶段：可靠性止血

状态：`[x]` 已完成，`mvn test` 40 个测试通过。

目标：先消除重复发送、状态污染和状态无法落库等高风险问题。

- 重试接口只接受外部渠道且处于 `FAILED` 或明确可重试状态的 delivery。
- `IN_APP`、`SUCCESS`、已完成的 `SKIPPED` 默认不可重试，除非后续引入明确的人工重放语义。
- 建立投递状态机，所有状态转换由服务层统一控制。
- Outbox worker 拆分为 `claim -> send -> persist result` 三段，避免外部调用处于长事务内。
- Provider 调用必须携带幂等键，推荐使用 `notification_delivery_log.id`。
- `notification_outbox` 增加 `(aggregate_type, aggregate_id)` 唯一约束。
- Provider 返回的错误信息、跳过原因、异常信息统一截断后再写库。

验收标准：

- 重试 `IN_APP/SUCCESS` 返回明确业务错误，不改变原投递记录。
- 外部发送成功但结果落库失败时，不会无限制造不可追踪状态。
- 同一 delivery 并发 enqueue 只产生一条 outbox。
- 长错误信息不会导致 worker 整批回滚。

### 第二阶段：治理后台和业务接入

目标：让通知能力从“有底座和 API”变成管理员可运营、业务可接入的能力。

- PC 管理端建设通知列表、详情、收件人清单、投递日志、失败重试和筛选能力。
- 系统公告支持创建、定时发布、撤回、过期和按角色发送。
- 接入审批待办、定时任务失败、API 告警、导入导出完成等核心业务事件。
- 接入真实 Provider MVP，优先支持 `WEBHOOK` 并完成端到端验收，再根据配置条件接入 `WECHAT_WORK`。
- 将 Provider 幂等键固化为接口/实现契约，真实 Provider 必须使用 `notification_delivery_log.id` 或派生键避免重复触达。
- 在真实 MySQL 或等价集成环境补并发 enqueue 验证，确认唯一约束能兜住并发插入。
- 明确迁移版本策略：若 `V25` 已进入共享/生产环境，不再改写历史迁移，改用后续版本迁移补约束。
- 延迟投递到期后、调用 Provider 前重新读取用户偏好，避免用户在免打扰期间关闭渠道后仍被外部触达。
- Provider 配置使用环境变量或配置属性类管理，禁止硬编码敏感信息。
- 后端校验 `notificationType/channel/status` 必须来自字典或明确白名单。

验收标准：

- 管理员能定位一条消息从创建、收件、投递到重试的完整链路。
- 管理员能创建、定时发布、撤回和过期系统公告。
- 审批、任务、API 告警、导入导出等核心事件通过统一消息命令接入。
- 真实 Provider 未配置、超时、失败和成功都有明确投递记录。
- 真实 Provider 重复收到同一幂等键不会重复触达用户。
- 真实数据库并发 enqueue 验证通过，同一 delivery 只保留一条 outbox。
- 发布前已确认 Flyway history，迁移脚本不会破坏已部署环境。
- 延迟期间用户关闭渠道或调整免打扰后，最终投递按最新偏好执行。
- 第二阶段交付后，管理员无需查数据库即可完成消息运营和失败定位。

### 第三阶段：观测、多端和实时增强

目标：把通知能力从“可运营”提升为“可观察、多端一致、可按规模演进”的平台能力。

- 移动端补齐消息入口、未读角标、通知列表和业务跳转。
- 增加投递指标：成功数、失败数、跳过数、重试数、平均耗时、outbox 积压、最长待投递时间。
- 增加通知健康看板和 Provider 配置状态检查。
- 高频消息聚合和频控配置化，降低任务失败、API 告警等高频事件噪声。
- Provider 插件化增强：`WebhookNotificationProvider`、`WeChatWorkNotificationProvider` 等渠道可独立扩展和观测。
- 在线规模扩大后引入 SSE，仅推送轻事件，REST 继续负责列表查询；未达到规模阈值时保留轮询方案。

验收标准：

- 管理端可观察 outbox 积压、失败原因和渠道健康。
- PC、移动端都能完成消息查看和业务跳转闭环。
- 高频失败消息能聚合，避免对管理员造成通知轰炸。
- SSE 断开后自动退回轮询，列表查询仍由 REST 提供。
- 未启用 SSE 时，现有轮询仍满足容量目标和用户体验目标。

### 方案评价

| 方案 | 优点 | 缺点 | 评价 |
| --- | --- | --- | --- |
| 最小修复方案 | 改动小、交付快，可快速修复重试和错误截断 | 事务边界、唯一约束、免打扰和治理页面仍会留下隐患 | 只适合临时止血，不建议作为最终状态 |
| 三阶段工业化方案 | 先消除高风险，再做治理和业务接入，最后做观测、多端和实时增强，范围最清晰 | 周期比两阶段略长，需要阶段间严格验收 | 推荐采用 |
| 全量重构方案 | 架构最干净，可一次性抽象事件总线和完整 Provider 体系 | 当前代码已有可用底座，全量重构成本高、回归风险大 | 暂不推荐 |

推荐结论：

采用**三阶段工业化方案**。第一阶段完成可靠性闭环，确保“消息不重、不误发、失败可追踪”；第二阶段补齐治理后台、系统公告、Webhook Provider MVP 和核心业务接入；第三阶段建设移动端入口、指标看板、健康检查、聚合频控和 SSE。

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

用户收件记录与阅读状态。

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
- 领取任务必须单独提交，外部 Provider 发送不得处于长事务内。
- 发送结果必须单条独立落库，单条失败不得导致整批任务回滚。
- 同一 delivery 只能存在一条 outbox，数据库必须通过 `(aggregate_type, aggregate_id)` 唯一约束兜底。
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
| MINI_PROGRAM | 可选，微信小程序订阅消息；用户无 openid、未授权模板、模板未配置或微信接口拒收时 SKIPPED/FAILED，站内消息仍成功 |
| WEBHOOK | 可选，适合外部系统告警 |
| WECHAT_WORK | 可选，适合管理员运维告警 |

失败处理：

- Provider 超时：记录 `FAILED/TIMEOUT`。
- Provider 未配置：记录 `SKIPPED/PROVIDER_NOT_CONFIGURED`。
- 用户未绑定设备：记录 `SKIPPED/USER_NOT_BOUND`。
- 用户关闭偏好：记录 `SKIPPED/USER_PREFERENCE_DISABLED`。
- 免打扰命中：普通消息延迟到免打扰结束后继续投递，URGENT 可绕过但必须记录原因。
- Provider 返回错误：统一截断到数据库字段允许长度后写入。

### 小程序订阅消息 Provider 规划

`MINI_PROGRAM` 不再停留在“渠道字典预留”，四期要补齐真实微信订阅消息触达能力。设计边界如下：

- **统一发布入口**：PC `http://localhost:5173/notifications` 是系统公告和小程序消息发布的唯一管理台。管理员在该页面创建公告、选择目标角色、选择 `IN_APP + MINI_PROGRAM` 渠道并发布；小程序端只负责订阅授权和接收，不提供发布后台。
- **统一消息命令**：系统公告、价格发布、审批待办等业务仍统一通过 `NotificationCreateCommand` 创建消息，不允许 PC 页面或业务服务直接调用微信接口。
- **可靠兜底**：`NotificationService.resolveChannels` 继续强制保留 `IN_APP`。即使 `MINI_PROGRAM` 投递失败、跳过或用户未授权，小程序通知列表仍能看到站内消息。
- **异步投递**：`MINI_PROGRAM` delivery 和 outbox 在业务事务中生成，微信接口调用由 outbox worker 在事务提交后执行。微信失败不得回滚公告发布、价格发布或站内消息生成。
- **身份绑定**：Provider 使用 `notification_delivery_log.user_id -> sys_user.wechat_openid` 定位接收用户。用户未绑定 openid 时记录 `SKIPPED/USER_NOT_BOUND`。
- **模板授权**：小程序前端只能在用户明确操作后调用订阅授权；授权结果上报后端保存。未授权、拒绝、授权次数不足或模板不可用时记录 `SKIPPED/SUBSCRIBE_NOT_AUTHORIZED`。
- **模板映射**：后端配置 `notification_type -> templateId -> 字段映射 -> page`，不同消息分类必须使用匹配自身业务语义的订阅模板，不再使用一套全局字段映射硬套所有消息。当前已按你提供的信息配置 `PRICE_PUBLISHED` 报价变更模板和 `SYSTEM_NOTICE` 基础信息/公告模板；后续审批待办、任务失败等类型按同一结构扩展。
- **幂等键**：调用微信接口时使用 `delivery-{notification_delivery_log.id}` 作为本系统幂等键写入日志和排查上下文；微信接口本身不保证按该键去重，系统侧必须依赖 outbox 唯一约束避免重复发送。
- **审计可见**：PC 通知管理页继续展示 `MINI_PROGRAM` delivery 的 SUCCESS / FAILED / SKIPPED、Provider 返回错误、重试次数和投递时间。
- **配置降级**：未配置 AppID/AppSecret/templateId 时 Provider 健康状态显示 `NOT_CONFIGURED`，投递记录为 `SKIPPED/PROVIDER_NOT_CONFIGURED`。

#### 小程序订阅配置来源与职责边界

`WECHAT_MINI_*` 配置不是由本系统自动生成，也不应由普通消息发布页面明文维护。配置来源和职责如下：

| 配置项 | 来源 | 配置职责 | 页面可见性 |
| --- | --- | --- | --- |
| `WECHAT_MINI_NOTIFY_ENABLED` | 本系统部署配置 | 运维/管理员决定是否启用 `MINI_PROGRAM` Provider | PC `/notifications` 可显示启用/未启用状态 |
| `WECHAT_MINI_APP_ID` | 微信公众平台小程序后台 `mp.weixin.qq.com` 的开发设置/开发者 ID | 运维从微信后台复制到后端环境变量或 PC 渠道配置；项目默认不预置具体 AppID | 页面最多显示“已配置”和脱敏尾号，不展示完整值 |
| `WECHAT_MINI_APP_SECRET` | 微信公众平台小程序后台生成或重置 | 运维配置到后端环境变量或密钥管理系统 | 页面只能显示“已配置/未配置”，严禁明文展示 |
| `WECHAT_MINI_TEMPLATE_PRICE_PUBLISHED` | 微信公众平台小程序后台的订阅消息“我的模板” | 报价变更通知模板 ID，项目默认不预置正式模板 ID | 页面可显示“价格发布模板已配置/未配置”，可显示脱敏模板 ID |
| `WECHAT_MINI_TEMPLATE_SYSTEM_NOTICE` | 微信公众平台小程序后台的订阅消息“我的模板” | 基础信息/系统公告模板 ID，项目默认不预置正式模板 ID | 页面可显示“系统公告模板已配置/未配置”，可显示脱敏模板 ID |
| `WECHAT_MINI_PRICE_FIELD_TYPE` / `WECHAT_MINI_PRICE_FIELD_TIP` | 报价变更通知模板详情字段 | 默认映射为 `type -> phrase2`、`tip -> thing4`，对应“类型”“温馨提示” | 页面可显示字段映射诊断，不展示密钥 |
| `WECHAT_MINI_NOTICE_FIELD_CREATOR` / `WECHAT_MINI_NOTICE_FIELD_TIME` | 基础信息/系统公告模板详情字段 | 默认映射为 `creator -> thing1`、`time -> time2`，对应“创建人”“创建时间” | 页面可显示字段映射诊断，不展示密钥 |
| `WECHAT_MINI_PRICE_PAGE` / `WECHAT_MINI_NOTICE_PAGE` | 小程序页面路径 | 默认分别为 `pages/home/index`、`pages/notifications/index` | 页面可显示跳转页诊断 |

获取路径说明：

1. 登录微信公众平台 `https://mp.weixin.qq.com`，选择对应小程序。
2. 在小程序后台的开发设置/开发者 ID 中获取 `AppID`；`AppSecret` 由具备权限的管理员生成或重置，生成后必须按密钥处理。
3. 在小程序后台进入订阅消息，选择公共模板或申请模板，加入“我的模板”后获取模板 ID。
4. 将上述值配置到后端运行环境变量中，重启后端或按部署策略刷新配置。

PC `/notifications` 的职责是“发布消息 + 诊断配置状态”，不是微信后台替代品。页面上看到“小程序订阅消息未配置”通常表示 Provider 不具备真实投递条件，常见原因是 `WECHAT_MINI_NOTIFY_ENABLED=false`、`WECHAT_MINI_APP_SECRET` 未注入、AppID 缺失或模板缺失；其中“模板已配置”和“Provider 可投递”是两层状态。后续若继续升级，应在 `/notifications` 增加“小程序订阅配置诊断面板”，只展示缺项、脱敏 ID、模板配置状态、最近投递错误和授权覆盖情况，不允许在页面保存或展示 `AppSecret`。

#### 当前已配置模板映射

```yaml
notification:
  mini-program:
    enabled: ${WECHAT_MINI_NOTIFY_ENABLED:false}
    app-id: ${WECHAT_MINI_APP_ID:}
    app-secret: ${WECHAT_MINI_APP_SECRET:}
    templates:
      PRICE_PUBLISHED:
        template-id: ${WECHAT_MINI_TEMPLATE_PRICE_PUBLISHED:}
        page: ${WECHAT_MINI_PRICE_PAGE:pages/home/index}
        fields:
          type: ${WECHAT_MINI_PRICE_FIELD_TYPE:phrase2}
          tip: ${WECHAT_MINI_PRICE_FIELD_TIP:thing4}
      SYSTEM_NOTICE:
        template-id: ${WECHAT_MINI_TEMPLATE_SYSTEM_NOTICE:}
        page: ${WECHAT_MINI_NOTICE_PAGE:pages/notifications/index}
        fields:
          creator: ${WECHAT_MINI_NOTICE_FIELD_CREATOR:thing1}
          time: ${WECHAT_MINI_NOTICE_FIELD_TIME:time2}
```

安全要求：用户曾在对话中提供过 AppSecret，该密钥已视为暴露，不得写入仓库、文档、前端页面或普通日志。上线前必须在微信公众平台重置 AppSecret，并仅通过 `WECHAT_MINI_APP_SECRET` 或密钥管理系统注入运行环境。

#### 历史候选设计：PC 手动配置运维升级方案

> 本节记录实施前的候选模型和接口草案，包含未采用的 `notification_provider_config`、`notification_provider_secret` 和 `/provider-configs/**` 命名。当前实现采用 `notification_channel_config` 与 `/channels/{channel}/**`，不得把本节接口当作当前 API。

当前将 AppID、模板 ID 和字段映射写在 `application.yml` 的方式只适合快速联调，不适合作为长期工业化配置方案。最终形态应改为：

- `application.yml` 只保留初始化兜底和部署兜底，不作为主要配置维护入口。
- PC `/notifications` 新增“渠道配置 / 小程序订阅消息”运维页，管理员可维护 AppID、模板 ID、字段映射、跳转页、启用开关和测试投递。
- AppSecret 可在 PC 端执行“更新密钥”，但后端必须加密保存或写入受控密钥源，前端永远不回显明文。
- Provider 发送时优先读取数据库中的启用配置；数据库未配置时才回退到 `notification.mini-program.*` 环境配置。
- 所有配置变更、密钥更新、测试投递和配置校验必须记录操作日志。

##### 页面信息架构

PC 通知管理页建议从单页工作台升级为 5 个页签：

| 页签 | 目标 | 主要能力 |
| --- | --- | --- |
| 总览 | 看运行态 | 今日消息、今日投递、失败率、Outbox 积压、Provider 健康、高频类型 |
| 消息发布 | 发公告/触达 | 新增公告、目标角色、渠道选择、发布前触达预估、站内兜底提示 |
| 投递审计 | 查链路 | 通知列表、收件人、投递日志、错误码筛选、失败重试 |
| 渠道配置 | 运维 Provider | Webhook、小程序订阅消息、App Push/企业微信预留、配置完整度、连接测试 |
| 小程序订阅 | 查授权覆盖 | openid 绑定、模板授权次数、授权覆盖率、拒绝/禁用用户统计 |

##### 渠道配置页细节

`渠道配置` 页左侧为渠道列表，右侧为当前渠道详情。

左侧渠道列表字段：

| 字段 | 说明 |
| --- | --- |
| 渠道 | 站内通知、Webhook、小程序订阅消息、App Push 预留、企业微信预留 |
| 启用状态 | 读取 Provider 配置 |
| 配置完整度 | 完整 / 缺少密钥 / 缺少模板 / 字段映射不完整 |
| 健康状态 | OK / DEGRADED / DOWN / NOT_CONFIGURED |
| 最近错误 | 最近一次 delivery 错误码或 Provider 错误 |

小程序订阅消息详情区分 4 个区域：

1. **基础配置**

| 字段 | 控件 | 规则 |
| --- | --- | --- |
| Provider 启用 | 开关 | 关闭后只保留站内通知；外部投递记录为 SKIPPED |
| AppID | 输入框 | 非敏感，可由 PC 保存；支持脱敏展示和完整编辑 |
| AppSecret | 状态行 + 更新按钮 | 只显示已配置/未配置、更新时间、更新人；不显示、不复制、不导出明文 |
| 微信接口超时 | 数字输入 | 默认 5000ms，限制 1000-30000ms |
| access_token 状态 | 只读诊断 | 最近刷新时间、最近错误、剩余有效期 |
| 默认跳转页 | 输入框 | 小程序页面路径，发布时可被模板配置覆盖 |

2. **模板映射表**

| 列 | 说明 |
| --- | --- |
| 通知类型 | `notification_type` 字典项，如 `PRICE_PUBLISHED`、`SYSTEM_NOTICE` |
| 业务名称 | 通过字典服务显示，前端不得硬编码中文标签 |
| 模板名称 | 运维可维护，如“报价变更通知”“基础信息” |
| 模板编号 | 微信后台模板编号，如 `28968`、`62445` |
| 模板 ID | 微信“我的模板”中的模板 ID |
| 字段映射 | 语义字段到微信字段编号，如 `type -> phrase2`、`tip -> thing4` |
| 跳转页 | 点击订阅消息进入的小程序页面 |
| 启用状态 | 单模板启用/停用 |
| 配置状态 | 完整 / 缺少模板 ID / 缺少字段 / 跳转页异常 |
| 操作 | 编辑、禁用、复制诊断、发送测试 |

当前默认初始化数据：

| 通知类型 | 模板名称 | 模板编号 | 模板 ID | 字段映射 | 跳转页 |
| --- | --- | --- | --- | --- | --- |
| `PRICE_PUBLISHED` | 报价变更通知 | 微信后台模板编号 | 由微信后台复制到配置 | `type -> phrase2`，`tip -> thing4` | `pages/home/index` |
| `SYSTEM_NOTICE` | 基础信息 | 微信后台模板编号 | 由微信后台复制到配置 | `creator -> thing1`，`time -> time2` | `pages/notifications/index` |

3. **配置诊断**

诊断项必须按可修复问题展示：

| 诊断项 | 通过条件 | 缺失处理 |
| --- | --- | --- |
| Provider 已启用 | 小程序 Provider enabled=true | 提供启用入口 |
| AppID 已配置 | AppID 非空 | 跳转基础配置 |
| AppSecret 已配置 | 密钥源存在可用密钥 | 打开更新密钥弹窗 |
| 模板 ID 已配置 | 启用模板均有 templateId | 跳转模板行编辑 |
| 字段映射完整 | 启用模板字段映射非空且字段名合法 | 标出缺失字段 |
| 跳转页已配置 | page 非空且符合小程序路径格式 | 提供默认页修复 |
| openid 覆盖 | 目标角色中存在绑定 openid 用户 | 跳转授权覆盖页 |
| 模板授权覆盖 | 目标用户中存在可用授权次数 | 提示小程序端引导授权 |
| 最近投递错误 | 最近错误数低于阈值 | 打开投递审计筛选结果 |

4. **联调工具**

| 功能 | 行为 | 安全约束 |
| --- | --- | --- |
| 测试 access_token | 后端使用当前 AppID/AppSecret 调微信 token 接口 | 不返回 access_token 明文，只返回成功/失败和错误码 |
| 校验模板配置 | 检查本地配置完整性和字段映射合法性 | 不调用用户侧订阅发送 |
| 发送测试订阅 | 选择测试用户、通知类型和模板，生成测试 delivery/outbox 或直接走受控测试接口 | 必须记录操作日志，限制 ADMIN |
| 查看最近失败 | 带筛选跳转投递审计 | 不暴露密钥和完整外部响应敏感字段 |
| 刷新授权覆盖率 | 统计 openid 绑定、模板授权次数和可触达人数 | 只显示聚合结果，不泄露用户隐私字段 |

##### 发布前触达预估

管理员在“消息发布”页勾选 `MINI_PROGRAM` 时，应显示发布前影响预估：

| 指标 | 说明 |
| --- | --- |
| 目标收件人数 | 按目标角色和用户状态计算 |
| 已绑定 openid | `sys_user.wechat_openid` 非空人数 |
| 已授权对应模板 | `notification_mini_program_subscription` 中 ACCEPT 且 availableCount > 0 的人数 |
| 预计可触达 | 同时满足 openid 绑定和模板授权的人数 |
| 站内兜底 | 始终生成 `IN_APP`，人数等于目标收件人数 |

发布按钮不因小程序配置不完整而禁用，但必须显示明确提示：

- 小程序配置完整：提示“可触达已授权用户，站内通知同步生成”。
- 小程序配置缺失：提示“小程序订阅消息将跳过，站内通知仍会发布”。
- 授权覆盖为 0：提示“当前没有可用订阅授权，建议先引导用户在小程序授权”。

##### 后端数据模型建议

新增 Provider 配置表，保存非敏感配置：

```sql
CREATE TABLE notification_provider_config (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel VARCHAR(50) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  config_json TEXT,
  secret_configured BOOLEAN NOT NULL DEFAULT FALSE,
  secret_updated_time DATETIME NULL,
  secret_updated_by BIGINT NULL,
  created_time DATETIME NOT NULL,
  updated_time DATETIME NOT NULL,
  UNIQUE KEY uk_notification_provider_config (channel, provider)
);
```

`MINI_PROGRAM` 的 `config_json` 示例：

```json
{
  "appId": "WECHAT_MINI_APP_ID",
  "timeoutMs": 5000,
  "defaultPage": "pages/notifications/index",
  "templates": {
    "PRICE_PUBLISHED": {
      "templateName": "报价变更通知",
      "templateNo": "28968",
      "templateId": "WECHAT_MINI_TEMPLATE_PRICE_PUBLISHED",
      "page": "pages/home/index",
      "enabled": true,
      "fields": {
        "type": "phrase2",
        "tip": "thing4"
      }
    },
    "SYSTEM_NOTICE": {
      "templateName": "基础信息",
      "templateNo": "62445",
      "templateId": "WECHAT_MINI_TEMPLATE_SYSTEM_NOTICE",
      "page": "pages/notifications/index",
      "enabled": true,
      "fields": {
        "creator": "thing1",
        "time": "time2"
      }
    }
  }
}
```

密钥保存有两种方案：

| 方案 | 做法 | 适用阶段 |
| --- | --- | --- |
| A：环境变量托管 | AppSecret 仍来自 `WECHAT_MINI_APP_SECRET`，PC 只显示状态 | 最小改造、快速上线 |
| B：数据库加密托管 | PC 更新 AppSecret，后端用 `NOTIFICATION_SECRET_ENCRYPTION_KEY` 加密保存，仅运行时解密使用 | 推荐最终方案 |

推荐最终采用方案 B，并新增密钥表：

```sql
CREATE TABLE notification_provider_secret (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  channel VARCHAR(50) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  secret_key VARCHAR(100) NOT NULL,
  encrypted_value TEXT NOT NULL,
  encryption_key_version VARCHAR(50) NOT NULL,
  updated_by BIGINT NULL,
  updated_time DATETIME NOT NULL,
  UNIQUE KEY uk_notification_provider_secret (channel, provider, secret_key)
);
```

安全约束：

- `encrypted_value` 不得通过任何 DTO 返回前端。
- AppSecret 更新接口只接受新值，不返回旧值。
- 普通日志、操作日志、投递日志只记录“已更新/未配置/更新时间/操作人”，不得记录密钥值。
- 密钥加密主密钥 `NOTIFICATION_SECRET_ENCRYPTION_KEY` 必须来自环境变量或生产密钥管理系统，不得写入仓库。
- 密钥更新后应清空 access_token 缓存，下一次投递重新获取 token。

##### PC 页签按钮功能检查矩阵

当前 `/notifications` 已按 5 页签完成前端运维工作台升级。按钮能力必须区分“已接入真实接口”和“规划入口”，避免管理员误以为配置已经写入后端。

| 页签 | 按钮/动作 | 当前状态 | 后端/API 依赖 | 功能管理评价 |
| --- | --- | --- | --- | --- |
| 全局页头 | 刷新 | `[x]` 可用 | `GET /api/admin/notifications/dashboard`、`providers/health`、`throttle-rules`、通知列表、公告列表 | 满足运维刷新需求 |
| 全局页头 | 新增公告 | `[x]` 可用 | 前端切换到“消息发布”页签 | 满足统一发布入口需求 |
| 页签栏 | 总览/消息发布/投递审计/渠道配置/订阅授权 | `[x]` 可用 | 前端状态切换 | 满足模块边界和企业级信息架构需求 |
| 总览 | 搜索 | `[x]` 可用 | `GET /api/admin/notifications` | 满足按关键词、类型、优先级、业务、渠道筛选 |
| 总览 | 重置 | `[x]` 可用 | 前端清空条件后重查通知列表 | 满足筛选恢复需求 |
| 总览 | 通知上一页/下一页 | `[x]` 可用 | `GET /api/admin/notifications?page&size` | 满足管理列表分页 |
| 总览 | 投递日志上一页/下一页 | `[x]` 可用 | 当前详情日志前端分页 | 满足单条通知日志浏览；后续大数据量可升级后端分页 |
| 总览 | 点击通知卡片 | `[x]` 可用 | `GET /api/admin/notifications/{id}/recipients`、`deliveries` | 满足消息链路审计 |
| 消息发布 | 保存公告 | `[x]` 可用 | `POST /api/admin/system-notices` | 满足公告草稿/计划发布创建 |
| 消息发布 | 配置诊断 | `[x]` 可用 | 前端切换到“渠道配置”页签 | 满足发布前定位配置入口 |
| 消息发布 | 公告状态筛选 | `[x]` 可用 | `GET /api/admin/system-notices?status=` | 满足公告生命周期管理 |
| 消息发布 | 发布 | `[x]` 可用 | `POST /api/admin/system-notices/{id}/publish` | 满足统一发布和 Outbox 投递触发 |
| 消息发布 | 撤回 | `[x]` 可用 | `POST /api/admin/system-notices/{id}/cancel` | 满足公告撤回治理 |
| 消息发布 | 公告上一页/下一页 | `[x]` 可用 | `GET /api/admin/system-notices?page&size` | 满足公告列表分页 |
| 投递审计 | 审计筛选 | `[x]` 可用 | `GET /api/admin/notifications` | 满足按渠道/状态/错误定位消息 |
| 投递审计 | 查看 | `[x]` 可用 | `GET /api/admin/notifications/{id}/recipients`、`deliveries` | 满足选中消息查看当前投递日志 |
| 投递审计 | 日志筛选/重置 | `[x]` 可用 | 当前详情日志前端过滤 | 满足单条消息排障；后续可加后端日志分页与错误码筛选 |
| 投递审计 | 重试 | `[x]` 可用 | `POST /api/admin/notifications/deliveries/{id}/retry` | 满足失败外部投递重试；状态机禁止站内、成功、待处理和跳过重试 |
| 渠道配置 | 选择渠道 | `[x]` 可用 | 前端状态切换 + Provider 健康数据 | 满足多渠道管理入口 |
| 渠道配置 | 编辑/配置 | `[x]` 可进入编辑态 | `GET /api/admin/notifications/channels/{channel}/config` | 前端按权限显隐，无权限用户只读 |
| 渠道配置 | 保存 | `[x]` 已接入并完成质量整改 | `PUT /api/admin/notifications/channels/{channel}/config` | 留空保留、模板合并、模板 ID 最小暴露和 token 缓存失效已完成 |
| 渠道配置 | 测试 | `[~]` 已接入本地诊断、远程 token 校验、测试投递和操作日志 | `POST /test`、`/test-token`、`/test-delivery` | 微信后台模板状态仍需真实接口能力或人工确认 |
| 渠道配置 | 编辑映射 | `[~]` 基础表单可编辑 | `PUT /api/admin/notifications/channels/{channel}/config` | 权限和安全保存已完成；字段映射完整校验与动态通知类型选择仍可增强 |
| 渠道配置 | 新增模板 | `[~]` 基础前端入口 | `PUT /api/admin/notifications/channels/{channel}/config` | 权限和保存合并已完成；通知类型字典选择和字段映射校验仍可增强 |
| 订阅授权 | 发送授权引导 | `[x]` 已接入 | `POST /api/admin/notifications/mini-program/authorization-guides` | PC 只能发起站内引导任务，小程序端仍需用户点击后授权 |
| 订阅授权 | 查看 | `[x]` 详情抽屉已接入 | `GET /api/admin/notifications/mini-program/subscriptions/{userId}` | 展示模板维度、最近投递、失败原因和用户偏好 |
| 订阅授权 | 引导 | `[x]` 已接入单用户引导 | `POST /api/admin/notifications/mini-program/authorization-guides/{userId}` | 按权限控制并反馈暂不提醒状态 |
| 订阅授权 | 处理 | `[x]` 已接入用户级异常处理 | `POST /api/admin/notifications/mini-program/subscriptions/{userId}/resolve` | 支持已处理、暂不提醒、备注和跟进标记 |

按钮功能结论：

- 当前已经能正常工作的核心链路是：刷新、筛选、通知详情、公告创建、公告发布/撤回、投递审计、失败重试、页签切换、基础渠道配置保存、本地配置诊断、订阅授权列表、批量/单用户授权引导。
- 当前不应视为 9.5+ 闭环的链路是：微信后台模板真实状态校验、生产等价环境的真实投递联调记录，以及真实 MySQL Explain/并发验证记录。
- 前端必须对未接入按钮给出明确“待接入后端接口”的提示；已接入按钮必须保证权限、安全和保存语义正确，不得让管理员误以为配置已按预期生效。

##### 订阅授权页签补充设计

原方案主要描述了小程序端授权采集和发布前触达预估，但对 PC 端“订阅授权”页签的管理动作描述不足。补充如下：

| 模块 | 功能 | 设计要求 |
| --- | --- | --- |
| 授权指标 | 用户总数、OpenID 绑定、模板授权、拒绝/禁用、低余量 | 数据来自真实用户、openid 绑定和 `notification_mini_program_subscription` 聚合，不使用前端模拟值 |
| 用户资格列表 | 用户、角色、openid/设备、各模板可用次数、状态、最近授权时间 | OpenID 必须脱敏；状态显示走字典；默认按异常优先排序 |
| 查看详情 | 查看单用户订阅明细 | 展示模板维度授权次数、最近授权来源、最近投递结果、最近失败原因和用户偏好 |
| 授权引导 | 批量或单用户引导授权 | PC 端只能创建引导任务或站内提示，小程序端仍需用户点击后触发 `wx.requestSubscribeMessage` |
| 异常处理 | 拒绝、低余量、未绑定、模板失效处理 | 当前支持记录处理备注、标记暂不提醒和设置跟进标记；真实跟进任务尚未实现 |
| 覆盖统计 | 渠道覆盖对比 | 小程序、App、企业微信等渠道使用同一覆盖模型，便于未来扩展 |

建议新增管理端接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/notifications/mini-program/subscriptions` | 分页查询用户订阅授权资格，支持角色、状态、模板、关键词筛选 |
| GET | `/api/admin/notifications/mini-program/subscriptions/{userId}` | 查询单用户订阅详情，OpenID 脱敏返回 |
| POST | `/api/admin/notifications/mini-program/authorization-guides` | 批量发送授权引导任务或站内引导消息 |
| POST | `/api/admin/notifications/mini-program/authorization-guides/{userId}` | 对单个用户发送授权引导 |
| POST | `/api/admin/notifications/mini-program/subscriptions/{userId}/resolve` | 标记订阅异常已处理或设置跟进状态 |

权限建议：

| 权限码 | 用途 |
| --- | --- |
| `notification:subscription:view` | 查看订阅授权覆盖和用户资格 |
| `notification:subscription:guide` | 发起授权引导 |
| `notification:subscription:resolve` | 处理订阅异常 |

##### API 设计补充

管理端新增接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/notifications/provider-configs` | 查询所有外部 Provider 配置摘要 |
| GET | `/api/admin/notifications/provider-configs/{channel}` | 查询单个渠道配置详情，密钥只返回状态 |
| PUT | `/api/admin/notifications/provider-configs/{channel}` | 保存非敏感配置，如 enabled、AppID、模板、字段映射、跳转页 |
| PUT | `/api/admin/notifications/provider-configs/{channel}/secret` | 更新渠道密钥，如小程序 AppSecret；请求只进不出 |
| POST | `/api/admin/notifications/provider-configs/{channel}/validate` | 执行配置完整性校验 |
| POST | `/api/admin/notifications/provider-configs/{channel}/test-token` | 测试微信 access_token 获取，不返回 token 明文 |
| POST | `/api/admin/notifications/provider-configs/{channel}/test-delivery` | 发送测试订阅消息或生成测试投递 |
| GET | `/api/admin/notifications/mini-program/coverage` | 查询 openid 绑定、模板授权和预计触达覆盖率 |

权限建议：

| 权限码 | 用途 |
| --- | --- |
| `notification:view` | 查看通知管理、Provider 状态 |
| `notification:retry` | 重试失败投递 |
| `notification:provider-config:view` | 查看渠道配置详情 |
| `notification:provider-config:update` | 修改非敏感渠道配置 |
| `notification:provider-secret:update` | 更新 AppSecret/Webhook Secret 等密钥 |
| `notification:test-delivery` | 执行测试投递 |

##### 历史实施步骤

> 以下步骤为实施前草案，当前完成状态不以本清单为准。

1. 新增 `notification_provider_config`，先保存 `MINI_PROGRAM` 非敏感配置；启动时可从 `application.yml` 默认值初始化一条配置。
2. 新增 Provider 配置读取服务，发送时优先读取数据库配置，缺失时回退 `NotificationMiniProgramProperties`。
3. 新增 PC `渠道配置` 页签和后端配置 API，支持 AppID、启用开关、模板 ID、字段映射、跳转页维护。
4. 新增配置诊断 API，输出缺项、严重性、修复入口和最近错误。
5. 新增 `notification_provider_secret` 和密钥加密服务，支持 AppSecret 更新但不回显；第一阶段也可先只显示环境变量状态。
6. 新增测试 token、测试投递、授权覆盖率接口，并在 PC 页面展示发布前触达预估。
7. 将 `application.yml` 中的 AppID、模板 ID 默认值标记为“初始化兜底”，文档和页面均以数据库配置为准。
8. 补充操作日志、权限码、API 文档、部署说明和数据字典。

##### 历史验收标准

> 以下验收标准用于保留设计意图；当前验收结论见 [通知管理平台当前实现状态.md](通知管理平台当前实现状态.md)。

- ADMIN 可在 PC `/notifications -> 渠道配置 -> 小程序订阅消息` 完成 AppID、模板 ID、字段映射、跳转页和启用开关维护。
- ADMIN 可更新 AppSecret，但前端、接口响应、日志、导出文件均不出现 AppSecret 明文。
- 小程序 Provider 发送时优先使用 PC 维护的数据库配置，数据库缺失时才回退环境变量。
- 配置缺失时页面能指出具体缺项，而不是只显示笼统“未配置”。
- 发布公告选择 `MINI_PROGRAM` 前可看到预计触达人数和站内兜底人数。
- 测试 access_token、模板校验、测试投递均有操作日志和明确成功/失败结果。
- `mvn test`、前端构建、权限按钮显隐和 API 权限校验通过。

##### 历史审计提出的 9.5+ 质量整改方案

本节记录整改前 **7.4 / 10** 的历史审计基线及当时提出的整改方案。当前评估与剩余门禁见本文末尾评分章节和独立闭环计划，不得把本节表格理解为当前待办状态。

| 优先级 | 整改项 | 解决方案 | 验收标准 |
| --- | --- | --- | --- |
| P0 | 模板 ID 最小化暴露 | 普通配置查询 DTO 删除完整 `templateId`，只返回 `templateIdMasked`；编辑场景要么要求重新录入模板 ID，要么新增受 `system:setting` 保护的编辑详情接口 | 无配置权限用户抓包看不到完整模板 ID；前端仍可展示脱敏状态和配置完整度 |
| P0 | 留空保存语义 | 后端把空字符串视为“不修改”，显式清空使用 `clearXxx` 字段；模板列表按 DB 现值合并，避免前端未传导致回退环境变量覆盖 | 管理员只修改启用开关或超时，不会清空 AppID、Endpoint、默认页和模板配置 |
| P0 | AppSecret 更新立即生效 | access_token 缓存增加配置指纹，保存 AppID/AppSecret/tokenUrl 后失效；指纹只使用不可逆 hash 或已保存的 secretFingerprint | 更新 AppSecret 后不重启应用，下一次投递重新获取 token；缓存和日志不含 AppSecret 明文 |
| P0 | 权限按钮一致 | 前端按 `Permission.SYSTEM_SETTING` 控制编辑、保存、新增模板、删除模板；后续细粒度权限落地后替换为 `notification:provider-config:update` | 无权限用户只能查看脱敏配置和诊断状态，不能进入编辑态或提交保存 |
| P0 | 字典显示合规 | 新增 `notification_mini_subscription_row_status` 字典；订阅列表状态、筛选项、详情标题均走 `getDictValue/getDictOptions` | 前端无 `NORMAL -> 正常` 之类硬编码映射；迁移、`init.sql`、数据字典一致 |
| P1 | 动态模板状态 | `resolveRowStatus` 按运行时已配置模板集合计算，不固定价格/公告两个模板 | 只配置系统公告模板时，不因价格模板授权为 0 被误判低余量 |
| P1 | 订阅分页性能 | 用户角色、关键词、分页下推数据库；当前页再聚合订阅授权和 openid 状态 | 用户量扩大时接口仍按 page/size 返回，不全量加载 ACTIVE 用户 |
| P1 | 远程联调闭环 | 拆分本地诊断、token 测试、测试投递；所有可能触发外部请求的 POST 增加操作日志 | 管理员能区分本地缺项、微信 token 错误、模板错误、测试投递失败，不显示 token 明文 |
| P1 | 文档状态收敛 | 当前文档、设计文档、数据字典、UI 说明统一为“基础接口已接入，P0 质量整改待完成” | 不再出现“保存未接入”和“保存已完成”并存的矛盾描述 |

质量门禁：

- `mvn -q -DskipTests compile`、核心单元测试、`npm run build` 通过。
- 新增/调整的 Entity、Flyway migration、`init.sql`、`数据字典.md` 保持一致。
- AppSecret、access_token、完整密钥材料不进入 DTO、普通日志、操作日志、投递日志、前端控制台和导出文件。
- `/notifications` 页面按钮权限、状态字典、分页行为和保存语义通过手工回归。

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

当前 SSE：

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

小程序消息接收策略：

- 前台在线：继续使用站内未读数刷新和通知列表，当前 30 秒轮询可作为基础兜底；后续可按平台能力增加进入前台立即刷新、发布后本地刷新和失败退避。
- 后台/离线：依赖微信订阅消息触达已授权用户，点击订阅消息后进入小程序通知页或对应业务页，再通过 REST 拉取最新通知详情。
- 未授权用户：不弹错误、不阻断业务；只通过站内消息列表和未读角标接收。
- 授权入口：优先放在我的页消息入口、通知页顶部“接收小程序提醒”、价格维护发布成功提示等明确场景；不得在页面加载时静默请求授权。
- 订阅范围：默认只申请与当前业务相关的模板，如系统公告、价格发布；不一次性索取无关模板。
- 文案规则：订阅授权说明和小程序端状态名称走字典/配置，不在前端硬编码业务编码显示名。

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
- 渠道配置运维：支持 Webhook、小程序订阅消息等 Provider 的启用状态、非敏感参数、模板映射、测试连接和诊断维护。
- 小程序订阅消息渠道治理：选择 `MINI_PROGRAM` 渠道时展示 Provider 配置状态、模板状态、授权覆盖率、预计触达人数和“仅触达已授权用户，站内消息仍兜底”的说明。
- 密钥安全维护：AppSecret、Webhook Secret 等密钥只允许更新和状态查看，不允许明文回显、复制或导出。

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

通知管理页签建议：

| 页签 | 主要内容 |
| --- | --- |
| 总览 | 运行指标、Provider 健康、Outbox 积压、高频类型 |
| 消息发布 | 系统公告创建、渠道选择、发布前触达预估 |
| 投递审计 | 通知列表、收件人、投递日志、失败重试 |
| 渠道配置 | Provider 配置、模板映射、密钥状态、配置诊断、联调工具 |
| 小程序订阅 | openid 绑定、模板授权次数、授权覆盖率和拒绝/禁用统计 |

## API 设计（目标接口合集）

> 本节同时保留历史目标接口。当前已经实现的接口路径以 `docs/dev/项目设计文档.md` 和 Controller 为准；其中 `/provider-configs/**` 是未采用的历史候选命名，当前渠道配置接口使用 `/api/admin/notifications/channels/{channel}/**`。

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
| GET | `/api/notifications/mini-program/subscriptions` | 查询当前用户小程序订阅授权状态 |
| POST | `/api/notifications/mini-program/subscriptions` | 上报小程序订阅授权结果 |

管理侧：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/notifications` | 全局消息 |
| GET | `/api/admin/notifications/{id}` | 消息详情 |
| GET | `/api/admin/notifications/{id}/recipients` | 收件人 |
| GET | `/api/admin/notifications/{id}/deliveries` | 投递日志 |
| POST | `/api/admin/notifications/deliveries/{id}/retry` | 重试投递 |
| POST | `/api/admin/system-notices` | 创建公告 |
| POST | `/api/admin/system-notices/{id}/publish` | 发布公告 |
| POST | `/api/admin/system-notices/{id}/cancel` | 撤回公告 |
| GET | `/api/admin/notifications/providers/health` | Provider 健康状态，包含小程序订阅消息配置状态 |
| GET | `/api/admin/notifications/provider-configs` | 查询 Provider 配置摘要 |
| GET | `/api/admin/notifications/provider-configs/{channel}` | 查询单个 Provider 配置详情，密钥只返回状态 |
| PUT | `/api/admin/notifications/provider-configs/{channel}` | 保存非敏感配置 |
| PUT | `/api/admin/notifications/provider-configs/{channel}/secret` | 更新 Provider 密钥，禁止回显 |
| POST | `/api/admin/notifications/provider-configs/{channel}/validate` | 配置完整性校验 |
| POST | `/api/admin/notifications/provider-configs/{channel}/test-token` | 测试 token 或连接状态 |
| POST | `/api/admin/notifications/provider-configs/{channel}/test-delivery` | 发送测试投递 |
| GET | `/api/admin/notifications/mini-program/coverage` | 查询小程序 openid 绑定和模板授权覆盖率 |

小程序订阅配置迁移策略：

- 当前环境变量配置继续保留，用于部署兜底和首轮初始化。
- PC 运维配置运行时优先读取 `notification_channel_config` 和密钥托管记录。
- `application.yml` 中的默认 AppID、模板 ID、字段映射不得作为长期唯一配置源。

现有小程序订阅环境变量兜底：

| 配置项 | 环境变量 | 默认值处理 |
| --- | --- | --- |
| 小程序 AppID | `WECHAT_MINI_APP_ID` | 默认空，需由环境变量或 PC 渠道配置提供 |
| 小程序 AppSecret | `WECHAT_MINI_APP_SECRET` | 空则 Provider `NOT_CONFIGURED` |
| 价格发布模板 ID | `WECHAT_MINI_TEMPLATE_PRICE_PUBLISHED` | 默认空，需由环境变量或 PC 渠道配置提供 |
| 系统公告模板 ID | `WECHAT_MINI_TEMPLATE_SYSTEM_NOTICE` | 默认空，需由环境变量或 PC 渠道配置提供 |
| 价格发布字段映射 | `WECHAT_MINI_PRICE_FIELD_TYPE` / `WECHAT_MINI_PRICE_FIELD_TIP` | 默认 `phrase2` / `thing4` |
| 系统公告字段映射 | `WECHAT_MINI_NOTICE_FIELD_CREATOR` / `WECHAT_MINI_NOTICE_FIELD_TIME` | 默认 `thing1` / `time2` |
| 小程序跳转页 | `WECHAT_MINI_PRICE_PAGE` / `WECHAT_MINI_NOTICE_PAGE` | 默认 `pages/home/index` / `pages/notifications/index` |
| 微信接口超时 | `WECHAT_MINI_NOTIFY_TIMEOUT_MS` | 默认 5000ms |

配置类必须放在 `backend/src/main/java/com/pricemanagement/config/properties/`，使用 `@ConfigurationProperties` 读取，敏感值只允许来自环境变量或配置文件占位符。

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
- [x] 通知列表 N+1 查询优化。
- [x] 点击通知时标记已读失败不阻断业务跳转。

验收：

- [x] 价格发布后所有目标用户生成未读消息。
- [x] 当前发布人立即看到未读刷新。
- [x] 其他在线用户 45 秒内看到未读刷新。
- [x] 页面隐藏时不轮询。
- [x] 全部已读后角标清零。
- [x] 同一草稿批次不会重复生成价格发布通知。
- [x] 部分发布重试不会重复发布已成功明细。
- [x] 通知列表查询性能达到 N+1 优化目标。
- [x] 点击通知容错完成。

### 二期：治理后台和业务接入

目标：在完成可靠性闭环后，把通知能力变成管理员可运营、核心业务可接入的能力。

- [x] 新增 `notification_outbox`。
- [x] 新增 outbox worker。
- [x] 新增 `notification_preference`。
- [x] 完成可靠性闭环门禁：重试状态机、Outbox 事务拆分、唯一约束、免打扰生效、错误截断。
- [x] 新增通知管理页面（ADMIN 通知筛选、详情、收件人、投递日志和失败重试已完成）。
- [x] 支持投递日志查询和失败重试（查询、状态机和操作日志注解已完成）。
- [x] 固化 Provider 幂等契约，Webhook Provider 基于投递记录幂等键避免重复触达。
- [~] 使用真实 MySQL 或等价集成环境验证 outbox 并发 enqueue 唯一约束（唯一约束、迁移和单元测试已覆盖，真实环境压测留到三期验证）。
- [x] 明确 Flyway 迁移版本策略：不改写 V25，二期追加 V26 并同步 `init.sql`。
- [x] 延迟投递到期后重新读取用户偏好，避免延迟期间偏好变更后仍继续触达。
- [x] 系统公告支持创建、定时发布、撤回、过期和按角色发送。
- [x] 接入真实 Provider MVP，优先支持 `WEBHOOK`；`WECHAT_WORK` 保留扩展位。
- [x] 接入审批待办、定时任务失败、API 告警、导入导出完成消息。
- [x] 站内通知底座强制保留：业务方只传外部渠道时，系统仍自动补齐 `IN_APP`。

验收：

- [x] Provider 失败不影响业务事务。
- [x] outbox 失败可重试，重试状态机和事务边界已加固。
- [x] 管理员能查到每条消息的收件和投递状态。
- [x] 用户偏好能影响外部渠道投递，免打扰延迟和 URGENT 绕过已生效。
- [x] Provider 幂等键端到端验证通过。
- [~] 真实数据库并发 enqueue 验证通过（真实环境压测留到三期验证）。
- [x] 延迟期间偏好变更后，最终投递按最新偏好执行。
- [x] 管理员可在 PC 页面完成消息查询、投递日志查看和失败重试。
- [x] 系统公告可按角色发送并支持撤回、过期。
- [x] 核心业务事件通过统一消息入口接入，不直接写表或调用 Provider。
- [x] 任意通知创建命令最终至少包含站内通知渠道，外部渠道不可替代站内可靠消息列表。

### 三期：观测、多端和实时增强

目标：补齐移动端、可观测、聚合频控和实时增强，让通知能力具备平台级运营能力。

- [x] 移动端补齐消息入口、未读角标、通知列表和业务跳转。
- [x] 建立消息指标看板。
- [x] Provider 健康检查和配置状态可视化。
- [x] 高频消息聚合和频控配置化。
- [x] 引入 SSE，仅推送未读数变化等轻事件。
- [x] 保留 REST 轮询降级。
- [~] 真实 MySQL 索引命中和 outbox 并发 enqueue 压测待联调/生产等价环境补充记录。

验收：

- [x] 移动端用户可查看自己的消息并完成业务跳转。
- [x] 管理端可查看 outbox 积压、失败率、重试数和最长待投递时间。
- [x] Provider 配置缺失、连续失败和积压异常可被管理员发现。
- [x] SSE 断开后自动退回轮询。
- [x] 高频失败消息能聚合。
- [~] 真实 MySQL 环境完成 Explain 与并发 enqueue 验证记录。

### 四期：小程序订阅消息触达

目标：利用 PC `http://localhost:5173/notifications` 已完成的信息发布管理台，补齐微信小程序订阅消息的主动触达能力，同时保持站内消息可靠兜底。

- [x] 后端新增 `WechatMiniProgramNotificationProvider`，实现 `NotificationChannelProvider`，channel 固定为 `MINI_PROGRAM`。
- [x] 新增小程序订阅消息配置类，读取 AppID、AppSecret、按消息类型划分的模板 ID、模板字段名、接口超时等配置；未配置时 Provider 健康状态为 `NOT_CONFIGURED`。
- [x] 设计模板映射：`PRICE_PUBLISHED` 使用报价变更通知模板 `phrase2/thing4`，`SYSTEM_NOTICE` 使用基础信息/公告模板 `thing1/time2`；后续审批待办、任务失败按业务需要扩展。
- [x] 新增 `notification_mini_program_subscription` 授权记录表，记录 userId、openid、notificationType、templateId、授权状态、最近授权时间、可用次数和授权来源。
- [x] 小程序端在“我的”页提供“接收小程序提醒”授权动作，调用平台订阅授权并上报后端。
- [x] PC 通知管理页选择 `MINI_PROGRAM` 渠道时展示 Provider 和模板配置状态，提示触达范围依赖用户授权。
- [x] 系统公告从 PC 页面创建并发布后，统一生成 `IN_APP + MINI_PROGRAM` delivery；站内消息立即可见，小程序订阅消息由 outbox 异步发送。
- [x] 投递日志按微信接口结果记录 `SUCCESS`、`FAILED`、`SKIPPED` 和明确错误码，ADMIN 可在通知管理页审计和重试失败投递。
- [x] 未授权、未绑定 openid、模板未配置、用户关闭偏好、免打扰延迟等场景均不得影响公告发布和站内消息。
- [x] PC `渠道配置` 页签支持小程序 AppID、启用开关、模板 ID、字段映射、跳转页等非敏感配置维护；配置安全、保存语义和按钮权限已修复。
- [x] AppSecret 密钥托管支持 PC 更新但不明文回显；access_token 缓存绑定配置指纹并在配置变化后失效。
- [~] 小程序配置诊断和联调工具已包含本地诊断、远程 token 测试、隔离测试投递、最近失败跳转和授权覆盖率；真实微信模板与投递联调证据待补。
- [x] 发布前触达预估已接入真实覆盖率 API，角色参数契约由后端显式解析。
- [~] PC `订阅授权` 页签已接入真实列表、详情抽屉、授权引导、异常处理、暂不提醒、动态模板状态和字典；带聚合行状态筛选的数据库分页待补齐。
- [x] 运行时优先使用 PC 维护的数据库配置，`application.yml` 作为部署兜底。

验收：

- [x] ADMIN 在 PC `/notifications` 新增公告，选择 `IN_APP + MINI_PROGRAM` 后可进入现有通知创建/Outbox 流程，目标用户站内通知列表可见。
- [~] 已绑定 openid 且授权对应模板的小程序用户收到微信订阅消息：代码链路已接入，待真实微信模板审核与 AppSecret 联调验证。
- [~] 点击微信订阅消息进入小程序通知页或对应业务页，并通过 REST 拉取通知详情：默认跳转通知页，待微信真机联调确认。
- [x] 未授权用户不收到微信订阅消息，但站内通知列表可见且未读数正确。
- [x] Provider 未配置时投递记录为 `SKIPPED/PROVIDER_NOT_CONFIGURED`，PC 发布成功且站内消息可见。
- [x] 微信接口失败时投递记录为 `FAILED`，可在 PC 通知管理页查看错误并按状态机重试。
- [x] 小程序订阅授权请求只发生在用户点击明确按钮后，不在页面加载时静默触发。
- [x] ADMIN 可在 PC 页面维护小程序非敏感配置，留空保留、模板合并、显式清空和权限按钮已通过本地验证。
- [x] ADMIN 更新 AppSecret 后，接口响应和日志不包含明文密钥，Provider 下次投递使用新配置重新获取 access_token。
- [~] 小程序配置缺失时，PC 页面显示具体缺项和修复入口；本地诊断、远程 token 和隔离测试投递已接入，真实微信模板状态验证待联调。
- [x] 选择 `MINI_PROGRAM` 发布前展示预计可触达人数，配置缺失或授权覆盖为 0 时仍允许发布站内通知。

## Verification

后端：

- [x] 消息创建幂等：价格发布通知已按草稿批次 `dedupeKey` 去重。
- [x] recipient 不重复：依赖 `uk_notification_message_user(message_id, user_id)`。
- [x] `read-all` 只影响当前用户。
- [x] 部分发布重试不重复写已发布明细。
- [ ] 未读数查询命中索引：需补真实 MySQL SQL/Explain 验证记录。
- [x] 我的通知列表 N+1 查询优化。
- [x] 外部 Provider 失败不回滚业务。
- [x] outbox 可重试、可锁定、可恢复。
- [x] 重试接口拒绝 `IN_APP`、`SUCCESS` 等不可重试投递。
- [x] Outbox claim、send、result 三段事务拆分验证。
- [x] `notification_outbox` 通过唯一约束保证同一聚合对象只产生一条记录。
- [x] 免打扰普通消息延迟投递、URGENT 绕过。
- [x] Provider 长错误信息不会导致状态落库失败。
- [x] Provider 幂等契约端到端验证。
- [x] 站内通知必选底座验证：只传外部渠道时自动补齐 `IN_APP`。
- [~] 真实 MySQL 并发 enqueue 验证（唯一约束语义已覆盖，真实压测待联调/生产等价环境补充）。
- [x] 已部署环境的 Flyway 迁移版本策略确认。
- [x] 延迟投递发送前重新读取偏好。
- [x] 聚合频控：`notification_frequency_rule` 字典规则可触发同类通知聚合，`URGENT` 不参与聚合。
- [x] SSE 用户轻事件：新增通知、未读数变化通过 `/api/notifications/events` 推送。
- [x] `MINI_PROGRAM` Provider 未配置时返回 `SKIPPED/PROVIDER_NOT_CONFIGURED`，不影响站内消息和业务事务。
- [x] `MINI_PROGRAM` Provider 用户未绑定 `wechat_openid` 时返回 `SKIPPED/USER_NOT_BOUND`。
- [x] 小程序订阅授权不足或被拒绝时返回 `SKIPPED/SUBSCRIBE_NOT_AUTHORIZED`。
- [x] 微信接口超时、HTTP 非成功响应、微信错误码分别记录明确 `FAILED` 错误码并可重试；字段名通过配置适配模板。
- [x] PC `/notifications` 发布公告选择 `MINI_PROGRAM` 时，delivery/outbox 创建、worker 发送、投递日志落库完整闭环已接入现有消息管线；真实微信成功回执待联调验证。

PC：

- [x] 铃铛、未读数、抽屉正常。
- [x] 隐藏页暂停轮询。
- [x] 失败退避生效。
- [x] `linkType/linkParams` 跳转正确。
- [x] 全部已读、单条已读正常。
- [x] 单条点击已读失败时不阻断跳转。
- [x] 归档正常。

移动端：

- [x] 我的页消息入口可用。
- [x] 普通员工只看到自己的消息。
- [x] 无小程序订阅授权时仍可查看站内消息。
- [x] 用户点击明确授权入口后触发小程序订阅授权并上报后端。
- [~] 已授权用户可接收 PC `/notifications` 发布的系统公告订阅消息：待真实微信模板联调验证。
- [~] 点击订阅消息可进入小程序通知页或对应业务页：默认通知页，待真机验证。

管理端：

- [x] ADMIN 可查询消息、收件人、投递日志。
- [x] 可重试失败投递。
- [x] 可创建和撤回系统公告。
- [x] 撤回系统公告后，用户侧公告通知不可见且未读数同步下降。
- [x] 系统公告过期后，用户侧公告通知不可见且未读数同步下降。
- [x] 创建定时公告和带过期时间公告时，前后端时间格式兼容。
- [x] `system_notice` 的 Entity、迁移、`init.sql`、数据字典和设计文档字段长度一致。
- [x] 通知管理权限码策略明确：接口和按钮均按权限码控制。
- [x] ADMIN 可查看通知指标看板、Provider 健康状态、聚合频控规则和渠道投递指标。
- [x] ADMIN 在 PC `/notifications` 选择 `MINI_PROGRAM` 渠道时能看到 Provider/模板配置状态和授权触达提示。
- [x] ADMIN 可在通知详情中查看 `MINI_PROGRAM` 投递日志、失败原因和可重试状态。
- [x] ADMIN 可在 PC `渠道配置` 页签维护小程序订阅消息配置、查看诊断清单、更新密钥、执行远程 token 校验和隔离测试投递。

回归：

- [x] 价格发布不因外部渠道异常而失败：当前外部渠道记录为 SKIPPED。
- [x] 审批、任务执行接入消息后不因外部渠道异常而失败。
- [x] 首页、价格查询、价格维护数据口径不受消息升级影响。

## 风险与控制

| 风险 | 控制 |
| --- | --- |
| 轮询压力 | 抖动、隐藏暂停、失败退避、索引、SSE 轻事件和轮询降级 |
| 消息重复 | dedupeKey、唯一约束、recipient 去重 |
| 消息丢失 | 站内落库、outbox、重试 |
| 投递状态被误改 | 投递状态机、重试入口校验、操作日志 |
| 外部渠道重复发送 | Outbox 唯一约束、事务拆分、Provider 幂等键 |
| 外部渠道拖垮业务 | 事务后异步投递 |
| 用户被打扰 | 优先级、偏好、免打扰、频控、聚合 |
| 跳转错误 | linkType/linkParams 协议 |
| 数据膨胀 | 过期、归档、保留策略 |
| 权限越界 | 用户侧只查本人，管理侧 ADMIN |
| 公告撤回后仍触达用户 | 撤回/过期同步处理通知可见性，用户列表和未读数统一过滤过期消息 |
| 归档未读角标不一致 | 未读数、全部已读、列表查询统一排除 `archived=true` |
| 前后端时间格式不一致 | 公告时间字段统一 ISO 或 `@JsonFormat` 协议，并补接口测试 |
| 文档与表结构漂移 | 每次 DB 变更同步检查 Entity、迁移、`init.sql`、数据字典和设计文档 |
| 小程序订阅授权不可控 | 订阅消息只做外部触达增强，站内消息始终兜底；未授权记录 SKIPPED |
| 小程序模板配置缺失 | Provider 健康状态显示 NOT_CONFIGURED，PC 发布页提示，投递记录 SKIPPED |
| 小程序配置依赖静态文件导致运维困难 | AppID、模板 ID、字段映射、跳转页和启用开关迁入 PC 配置页，`application.yml` 仅作初始化兜底 |
| AppSecret 进入前端或日志 | PC 只允许更新密钥和查看状态，后端加密保存或读取环境变量，任何 DTO、日志、导出均不返回明文 |
| 配置错误难定位 | 配置诊断清单按启用开关、AppID、AppSecret、模板、字段、跳转页、openid 和授权覆盖逐项提示 |
| 微信接口失败或限流 | Outbox 异步投递、失败落库、指数退避和人工重试；不影响 PC 发布事务 |
| 重复发送订阅消息 | delivery/outbox 唯一约束、状态机和 worker 幂等上下文控制，禁止 SUCCESS 重试 |
| 用户被过度打扰 | 按通知类型申请模板授权，尊重用户偏好和免打扰，低价值消息不默认开启 `MINI_PROGRAM` |

## 方案评估与评分

评分口径：

- **目标方案评分：9.6 / 10**。按本方案完成可靠性加固、治理页面、真实 Provider、业务接入、小程序订阅配置运维和可观测能力后，可达到工业级消息平台要求。
- **整体当前实现评分：9.4 / 10**。第一阶段可靠性闭环、第二阶段主体能力、二期审计补丁和第三阶段主体能力已完成；当前主要剩余真实 MySQL 索引/并发压测记录、小程序订阅消息真实微信联调、外部平台 Provider 扩展和生产级告警自动化。
- **四期小程序订阅运维子域历史审计基线：7.4 / 10，当前实现评估：9.4 / 10，整改目标：9.6 / 10**。角色契约、异常处理、远程 token 校验、隔离测试投递、详情抽屉、操作日志和 N-025/A-10 资格快照数据库分页已闭环；真实微信模板、投递和生产等价环境验证仍未闭环，因此当前不能标记为 9.5+ 已达成。

| 维度 | 分数 | 评价 |
| --- | --- | --- |
| 工业级可靠性 | 目标 9.7 / 当前 9.4 | 重试状态机、Outbox 事务拆分、唯一约束、错误截断、免打扰延迟、Webhook 幂等键、公告生命周期可见性、站内通知必选底座和 token 缓存失效已完成；真实 MySQL 压测和小程序订阅消息真实投递记录仍需补强 |
| 架构科学性 | 目标 9.6 / 当前 9.5 | 五层模型职责清晰，业务和渠道解耦；Webhook Provider、状态机、治理后台、SSE 轻事件、聚合频控和观测服务已落地，小程序订阅消息可在现有 Provider/Outbox 架构上扩展 |
| 融入性 | 目标 9.6 / 当前 9.2 | 价格发布、审批、任务、公告、API 告警和导入导出事件已接入统一命令 |
| 性能可控性 | 目标 9.5 / 当前 9.3 | 智能轮询、N+1 优化、指标看板和 SSE 降级链路已完成；SQL/Explain 仍待真实 MySQL 记录 |
| 运维治理 | 目标 9.6 / 当前 9.4 | ADMIN PC 治理页、投递日志、安全重试、系统公告、Provider 健康、受控测试投递、订阅异常处理、细粒度权限和资格快照数据库分页已完成；真实微信模板校验和生产环境复核仍需闭环 |
| 用户体验 | 目标 9.5 / 当前 9.4 | PC 抽屉、管理端页面、结构化跳转、免打扰、归档未读数、公告过期可见性、移动端入口和聚合频控已完成；小程序离线主动触达代码已接入，待真机验证 |
| 安全审计 | 目标 9.6 / 当前 9.5 | 操作日志、字典校验、细粒度权限、配置按钮权限、AppSecret 密文托管、token 缓存失效、请求参数脱敏和 Provider 异常脱敏已完成；仍需生产等价环境验证和历史日志巡检 |
| 实施可控性 | 目标 9.5 / 当前 9.3 | 三阶段边界清晰，第三阶段主体已落地；真实数据库压测和生产告警自动化仍需按环境推进 |

扣分点：

- 小程序订阅消息的实际接入依赖微信小程序 AppID/AppSecret、模板审核、用户显式授权和 openid 绑定。
- 小程序订阅运维子域要达到 9.5+，仍必须完成真实微信联调、真实 MySQL 验证和生产环境复核，不能只通过文档评分调整完成。
- 真实 MySQL 索引命中、并发 enqueue 和生产告警自动化仍需要在联调或生产等价环境补充记录。
- 通知管理后台、偏好系统、观测面板和移动端入口会增加产品复杂度，后续应控制 Provider 扩展边界。
- 当前实现已完成 N-006 至 N-016 的可靠性加固、二期主体交付、审计补丁和第三阶段主体能力。

最终结论：

这是一套面向工业级应用的消息基础设施方案。它不是简单通知功能，而是系统事件触达、站内可靠消息、外部渠道投递、管理审计、运维监控和多端体验的统一底座。当前第一阶段可靠性闭环、第二阶段主体能力、二期审计补丁和第三阶段主体能力已完成；后续重点转为真实 MySQL/生产等价环境验证、小程序订阅消息 Provider 授权联调、外部平台 Provider 扩展和告警自动化。PC `http://localhost:5173/notifications` 将作为小程序消息发布的统一管理入口，小程序订阅消息只承担主动触达，不能替代站内通知可靠列表。
