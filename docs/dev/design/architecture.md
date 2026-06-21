---
title: 架构与模块设计
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/项目设计文档.md
---

# 架构与模块设计

## 设计原则

### 禁止硬编码原则

本系统遵循**编码值动态化**原则：所有业务编码的显示名称必须从字典服务动态获取，严禁在代码中硬编码中文标签。

**原则要求：**

- 前端所有编码→中文的映射关系必须通过 `useDict` composable 获取
- 新增业务编码时，必须先在 `sys_dict` 表中配置对应的字典项
- 字典分类和键值对是系统的**数据契约**，修改字典即可改变全系统显示，无需改代码

**技术实现：**

- 后端：`sys_dict` 表存储所有编码映射，`SysDictService` 提供分类查询
- 前端：`useDict` composable 提供全局缓存和便捷方法（`getStatusLabel`、`getRoleLabel`、`getDictValue`、`getDictOptions` 等）
- 每个页面 `onMounted` 时调用 `loadAllDicts()` 加载缓存

## 项目概述

为制造业企业设计并实现一个前后端分离的价格展示与管理系统，主要面向企业内部员工使用。

## 技术选型

### 前端技术栈

| 技术 | 说明 |
|------|------|
| Vue 3 + TypeScript | 现代化前端框架 |
| Vite | 快速构建工具 |
| Vant UI | 移动端优先组件库 |
| UnoCSS | 原子化 CSS 引擎（与 Vant 共存） |
| Pinia | 状态管理 |
| Axios | HTTP 客户端 |
| Vue Router | 路由管理 |
| ECharts | 数据可视化图表 |

### 前端运行兼容设计

- 路由 history 由 `router/index.ts` 的 `createAppHistory()` 统一创建。该封装仍使用 HTML5 history，但在创建期间屏蔽 Vue Router 注册到 `document.visibilitychange` 的滚动位置保存监听，避免部分 Edge/Chrome 环境在浏览器最小化进入 hidden 状态时因 `history.replaceState` 触发窗口立即恢复。
- 首页 ECharts 图表通过 `useSafeChartAutoresize()` 控制 `vue-echarts` 的 `autoresize`，在页面隐藏、窗口外框尺寸极小或浏览器将窗口移动到负坐标的最小化状态下暂停图表自动 resize。
- 全局布局宽度通过 `useLayout()` 管理，最小化/隐藏状态下忽略 resize 事件，避免窗口恢复时错误切换响应式布局。

### 后端技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot 4.0.6 | 后端框架 |
| Java 25 | 编程语言 |
| Spring Data JPA | ORM 框架 |
| Spring Security | 安全框架 |
| Spring Cache + Redis | 缓存层（提升数据获取性能） |
| MySQL 8.4 | 关系数据库 |
| JWT + API Key HMAC | 内部用户认证与外部系统签名认证 |
| EasyExcel | Excel 处理 |

## 功能模块

### 1. 用户认证模块

- **登录/登出**：JWT Token 认证
- **动态权限系统**：登录时获取用户权限列表，前端动态判断
- **三种角色**：
  - `ADMIN`：管理员，拥有所有权限
  - `EDITOR`：编辑者，可以编辑产品价格
  - `VIEWER`：查看者，可查看价格数据并导出日常价格查询结果

### 2. 部门管理模块

- **树状组织架构**：总部/子公司/部门三级结构
- **拖拽调整层级**：支持拖拽移动部门
- **部门用户统计**：显示部门下用户数量
- **部门负责人设置**：可设置部门负责人
- **用户关联部门**：用户创建时可选择所属部门

### 2. 产品分类管理

- 分类列表展示
- 新增/编辑/删除分类
- 启用/禁用分类

### 3. 产品与价格管理（核心）

- 产品管理工作台：PC 端采用自适应分页产品列表联动详情侧栏，移动端采用单列产品卡片；支持名称、编码、规格搜索及分类、状态、排序筛选，详情侧栏聚合资料完整度、基础字段、当前价格与权限化管理入口
- 产品详情查看：响应式详情页将产品身份、近期价格、年度预算、趋势范围最低/最高/平均价及日、周、月环比和同比合并为统一概览区，避免分散和重复展示统计指标；近期价格表示截至所选日期有效的最后一次正式价格。概览区与实际价格曲线按产品分类编码读取 `category_visual_config`，以浅色分类背景、分类强调色和主题正文色保证识别度与可读性。基础资料复用产品管理字段与字典语义，币种缺失时按系统默认 `CNY` 展示。页头日期控件调用 `/api/products/{productId}/price-by-date` 查看截至指定日期有效的正式价格，并提供跳转 `/price-query` 的价格查询入口。趋势区统一提供 30天、180天、12个月范围，通过 `/api/products/{productId}/price-years` 获取真实录入年份，不限制历史年限；实际价格按正式价格生效区间连续返回并以平滑曲线展示，选择某年时调用 `/api/products/{productId}/price-trend?days={days}&startDate={yyyy-MM-dd}&endDate={yyyy-MM-dd}`，12 个月严格使用该自然年范围。
- 产品新增/编辑/删除
- 价格管理（原价、现价、成本价）
- 预算管理工作台：位于产品管理下，按产品 + 年份维护年度预算价格；年份控件支持直接输入 1900-2999 任意年份并前后切换，切换年份后加载该年预算，历史年度预算保持不变；保存调用 `/api/product-budgets` 批量更新。预算管理页是预算信息唯一维护入口，其它页面和外部产品接口只引用 `product_annual_budget` 返回的年度预算，不再从产品编辑、产品接口或价格维护写入产品旧预算字段；未维护年度预算时展示为空。右侧年度走势图复用 `/api/products/{productId}/price-trend?startDate={yyyy}-01-01&endDate={yyyy}-12-31`，样式与 `/price-query` 趋势图保持一致，正式价格与年度预算双线展示。
- 价格维护工作台：与首页产品列表样式保持一致，支持产品名称搜索、产品类别筛选、分页、每页条数切换和拖拽调整产品顺序；拖拽落位后调用 `/api/products/batch-sort` 更新产品 `sortOrder`，并通过 `product-sort-updated` 事件刷新首页排序；保留当日售价录入/修改，并展示预算价格、昨日售价、价格变化和月均价；预算价优先读取该日期所属年度的 `product_annual_budget`
- 首页价格走势：重点产品主曲线、重点走势卡片和产品列表迷你走势图复用 `/api/products/{productId}/price-trend`，同时展示正式价格实线和年度预算虚线；产品列表迷你走势图 tooltip 使用页面顶层 HTML 渲染，避免被表格单元格裁剪。
- 价格保存/发布：价格维护页先调用 `/api/price-drafts/batch-save` 保存当前日期草稿，草稿对正式价格查询不可见；PC 和小程序发布按钮读取 `/api/price-drafts/publishable-summary` 展示全系统待发布日期数、批次数和明细数，再调用 `/api/price-drafts/publish-all` 发布全系统所有 `DRAFT` 草稿，不受当前页面日期限制，写入正式 `price`、`price_history`、同步产品售价并生成一条发布组站内通知。发布结果返回发布组、日期数、批次数、全部 `publishLogIds` 和 `batchResults`；发布重试只处理未发布明细，已发布明细通过 `itemStatus=PUBLISHED` 与 `publishedPriceId` 跳过；按日期 `/api/price-drafts/by-date/publish?date=yyyy-MM-dd` 仅作为定时任务、补发或维护入口保留；单批次 `/api/price-drafts/{batchId}/publish` 仅作为兼容/维护接口保留。PC 端 Layout 在侧边栏底部使用用户卡片 + 更多菜单承载个人中心、修改密码、消息通知和退出登录；未读时更多按钮由三点图标切换为红色数字角标，采用带抖动的未读数轮询，并可进入通知抽屉筛选全部/未读、全部已读、归档、点击结构化跳转至价格查询。外部渠道通过 `notification_outbox` 异步投递，Provider 未配置或用户关闭偏好时记录为 SKIPPED；命中免打扰时延迟投递；worker 领取、发送、结果落库拆分事务，不影响发布事务。`PRICE_PUBLISH` 审批能力仅预留，当前不启用。
- 正式价格兼容维护：旧正式价格写入接口仅作为兼容/特殊维护入口，`ADMIN/EDITOR` 可调用，`VIEWER` 禁止写入；接口支持 `source=FORMAL_PRICE_MAINTENANCE` 审计来源，价格维护页不得绕过草稿接口直接调用。
- 通用定时任务：系统管理新增"定时任务"页面，使用 `sys_scheduled_task`、`sys_scheduled_task_log` 管理多种任务；价格自动发布作为 `PRICE_PUBLISH` 任务类型接入，默认停用，使用数据库行锁和执行日志唯一约束防重复。
- 日常价格查询：只读页面 `/price-query`，聚合产品产地、规格、近期价格、上期有效价格、年度预算、预算偏差及月度趋势指标；支持 VIEWER 按日期、关键字、分类分页查询。每个产品以查询日仍有效的最新正式价格日作为 `metricBaseDate`，月均、环比、同比和预算年度均按该日期计算，显式到期且查询日已失效的价格不作为最新有效价。指标展示由启用的 `price_metric_group`、`price_metric` 字典动态控制分组、名称、排序和启停，后端字段映射与计算公式保持受控；导出使用明确指标字段，不包含 deprecated 预算别名字段。
- 小程序角色化移动闭环：`frontend-uniapp` 以 PC 端能力为基础做移动任务化，VIEWER 底部导航为"首页 / 历史 / 我的"，ADMIN/EDITOR 增加"录入"；`pages/history/index` 提供只读历史价格查询，历史页和首页产品列表复用 `/api/price-query` 的 `latestPrice`、`previousPrice`、`previousChangeAmount` 展示"最新 / 上期 / 较上期"，与 PC 价格查询页上期有效价格口径一致；首页、历史和价格维护列表解析产品 `originIds`，按"产品名.产地"展示并支持搜索；`pages/price-maintenance/index` 承载轻量价格录入/补录，并复用 `/api/price-drafts/batch-save`、`/api/price-drafts/publishable-summary` 与 `/api/price-drafts/publish-all` 实现"保存当前日期草稿 → 发布全系统所有 DRAFT 草稿并通知"的双按钮流程；小程序通过 `/api/notifications/unread-count` 轮询共享未读状态，价格发布成功或检测到新通知时展示动态气泡，并在自定义底栏"我的"入口显示红色数字角标；小程序首页重点关注与产品列表复用 PC 首页的分类 `sortOrder` + 产品 `sortOrder` 稳定排序，并从币种字典读取产品货币符号；首页注册小程序入口分享，支持好友/微信群转发和右上角分享到朋友圈；登录页作为未登录测试与落地兜底，仅支持好友/微信群转发；我的页提供"分享小程序"按钮且仅触发好友/微信群转发；产品、分类、产地、客户、审批、字典、用户、菜单、日志、样式等完整运维保留在 PC 端，不作为小程序主导航入口
- 小程序登录页环境切换仅保留"正式地址 / 自定义"：正式地址不展示具体网址或端口，自定义支持填写网址和端口；历史本地测试、内网或自动模式在小程序端回落到正式地址。
- 小程序产品详情隐藏内部产品编码，产地和报价适用客户复用 `origin`、`customer` 数据字典解析中文名称。
- 小程序产品详情价格走势使用每日售价与每日预算价双线展示，并提供触摸选点查看具体日期和金额；12 个月后提供"查看年份"，选择后按自然年查询。
- 价格历史记录追踪
- Excel 批量导入导出

### 4. ERP 对接（预留）

- 产品数据同步接口
- 价格数据同步接口
- 同步日志记录

### 5. 操作日志管理（仅管理员）

- 操作日志查询与筛选（姓名/登录名、操作类型、时间范围）
- 日志统计分析
  - 趋势图（ECharts 折线图）
  - 操作类型分布（ECharts 饼图）
  - 模块分布（ECharts 柱状图）
  - 用户活跃度排行
- 月度报表（按日统计）
- 年度报表（月度趋势 + 用户年度排行）

### 6. 外部 API 授权管理（仅管理员）

- 隔离边界：外部系统只能访问 `/api/external/v1/**`，现有内部 `/api/**` 业务 Controller 不接受 API Key 认证。
- 独立安全链：`externalApiSecurityFilterChain` 使用 `@Order(1)` 只匹配 `/api/external/**`；内部 JWT 安全链使用 `@Order(2)`，保持原有行为。
- 签名认证：请求头 `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`，禁止传输 `X-App-Secret`；签名使用 HMAC-SHA256。
- 访问控制：支持启用、停用、吊销、过期时间、IP 白名单、分钟限流、日限额、Nonce 防重放和端点权限；分钟限流或日限额设置为 `0` 时表示对应维度不限制。
- 服务总开关：`API_KEY_ENABLED` 是部署级开关；API 授权管理页提供运行时服务开关，配置项为 `sys_style_config.external_api_service_enabled`，可即时暂停/恢复 `/api/external/v1/**`，暂停时认证结果为 `SERVICE_DISABLED`。
- 管理表单交互：创建/编辑 API Key 时，过期时间使用日期时间控件；接口权限采用左侧 API 目录 + 右侧端点详情结构，右侧展示请求/响应示例、参数 schema、错误码、使用提示、签名结构提示和 Node.js / Java 25 / Postman / PowerShell / curl 可复制代码。
- 创建成功闭环：创建 API Key 后的 Secret 一次性弹窗会基于真实 `appId` / `appSecret`、已授权端点和可配置 `baseUrl` 即时生成可运行示例；示例只在前端内存生成，不回传后端、不落库。
- 管理表单校验：创建/编辑 API Key 时，密钥名称、环境、分钟限流、日限额为后端必填校验项；分钟限流和日限额允许填写 `0` 表示不限制；前端还要求至少选择一项接口权限，并在字段下方展示明确错误提示。
- 审计：记录外部调用日志 `sys_api_call_log` 和管理操作日志 `sys_api_key_operation_log`。
- 默认关闭：`api-key.enabled=false` 时外部接口不可用，内部功能不受影响；后台创建 API Key 仍需可用的 Secret 加密主密钥。开发环境使用 `application-dev.yml` 兜底 key，生产环境必须通过 `API_KEY_ENCRYPTION_KEY` 注入独立随机 key。
- 阶段一外部写入复用现有产品/价格服务；审批流启用时，外部申请人使用系统占位 `0`，真实外部应用来源通过调用日志的 `app_id` 追溯。外部应用到内部用户/部门的身份映射在阶段二治理。
- 阶段一不启用 API Key 授权元数据缓存，管理端修改密钥、权限、状态后立即按数据库最新值生效；`api-key.cache-ttl-seconds` 为后续性能优化预留。

### 6.1 通知管理平台（ADMIN 治理 + 全员接收）

- 统一接入：价格发布、审批待办、定时任务失败、外部 API 告警、导入导出完成和系统公告均通过 `NotificationCreateCommand` 调用 `NotificationService.create(command)`，业务模块不得直接写 `notification_*` 表。
- 管理后台：系统管理下新增"通知管理"，仅 ADMIN 可见；页面采用三栏工作台，提供通知筛选、详情、收件人清单、投递日志筛选分页和失败重试，并提供系统公告列表、创建、立即发布、定时发布和撤回。公告板与高频类型摘要区纳入同一工作台网格，页面底部与右侧摘要区保持同一高度边界。
- 系统公告：公告数据存储在独立 `system_notice` 表，发布时按目标角色生成标准通知消息；定时任务自动发布到期公告并标记过期公告。公告撤回或过期后，同步归档对应通知消息的收件记录；Outbox worker 发送前再次校验消息过期时间和收件归档状态，避免撤回或过期公告继续外发。
- Webhook Provider：通过 `NOTIFICATION_WEBHOOK_*` 环境变量启用；Provider 调用使用 `delivery-{notification_delivery_log.id}` 作为幂等键，未配置写入 `SKIPPED/PROVIDER_NOT_CONFIGURED`，超时或非 2xx 写入 `FAILED`，成功写入 `SUCCESS` 和 provider message id。
- 偏好重算：Outbox 到期发送前重新读取用户偏好；渠道关闭则跳过，仍处于新免打扰窗口则继续延迟，`URGENT` 可绕过并记录原因。
- 三期增强：通知管理页提供指标看板、渠道投递指标、Provider 健康状态和聚合频控规则；用户侧新增 `/api/notifications/events` SSE 轻事件流，仅推送未读数变化和新通知事件，PC 端断开后自动回退轮询；uni-app 我的页提供消息入口、未读数、通知列表、全部已读、归档和价格查询跳转。
- 聚合频控：`notification_frequency_rule` 字典保存类型级规则，`extra_value` 使用 JSON 配置 `enabled`、`windowMinutes`、`maxCount`；普通高频通知触发聚合，`URGENT` 不参与聚合。聚合更新会增加 `notification_message.event_count`，重新唤醒收件人并为本次事件生成完整渠道投递记录。
- 业务事务边界：价格发布、审批、任务失败、API 告警和导入导出等附属通知统一在主事务提交后创建；通知创建失败只记录告警，不回滚核心业务。发布响应中的 `notificationMessageId` 为可空字段，不作为价格发布成功条件。
- 站内底座：`IN_APP` 是可靠消息底座，业务方只传外部渠道时 `NotificationService` 仍自动补齐站内渠道，确保用户侧站内列表可追溯。
- 权限边界：通知管理接口使用 `notification:view`、`notification:retry`、`system-notice:create`、`system-notice:cancel`、`system:setting` 进行按钮级 API 授权；渠道配置保存需要 `system:setting`，JWT 中携带权限码并由 `JwtAuthenticationFilter` 注入 Spring Security authorities。历史库由 `V39__system_setting_permission_backfill.sql` 补齐并向 ADMIN 授权该权限；前端按钮显隐直接读取动态权限集合，并在进入通知管理页时刷新管理员 access token，避免数据库授权与旧 JWT 不一致。
- 小程序配置运维：`MINI_PROGRAM` Provider 通过 `NotificationMiniProgramRuntimeConfigService` 解析运行配置，基础配置读取 `notification_channel_config`，模板配置优先读取 `notification_mini_program_template` 的 `ACTIVE` 版本，旧 `config_json` 模板仅用于兼容兜底；只有数据库不存在 `MINI_PROGRAM` 配置行时，才回退 `notification.mini-program.*` 环境配置。AppSecret 使用 `ApiKeySecretService` AES-GCM 加密存储，前端响应只返回"已托管/未配置"和脱敏 AppID/模板信息。
- 配置编辑回显：AppID 属于非敏感配置，可回写到受 `system:setting` 权限保护的编辑表单；AppSecret 不返回明文，只返回配置状态、来源和脱敏指纹。默认跳转页及模板跳转页统一使用 `notification_mini_program_page` 字典，并由后端校验启用状态。
- 渠道健康状态：`NOT_CONFIGURED` 仅表示没有有效启用项、凭据或模板；保存了部分配置但尚未满足投递条件时返回 `DEGRADED`；启用状态、AppID、AppSecret 和至少一个完整模板均满足时返回 `OK`。运行期连续失败仍按观测规则进入 `DOWN`。
- 小程序资格分页：`NotificationMiniProgramEligibilityService` 维护用户级资格快照；订阅授权、额度消费、用户创建/更新事务提交后触发单用户刷新，模板配置变化和每日低峰任务执行全量校准。管理列表带 `status` 筛选时通过 `sys_user` JOIN 快照表分页，当前页明细仍读取原始授权记录。Provider 投递使用原始授权表的条件更新预占授权次数，避免并发重复消费。

### 7. 审批流程管理（仅管理员）

- 审批工作流配置
  - 创建/编辑/删除工作流
  - 激活/停用工作流
  - 支持价格变更、产品创建两种工作流类型
- 审批节点配置
  - 节点类型：审批节点（APPROVER）、知会节点（NOTIFIER）
  - 审批角色：管理员（ADMIN）、编辑者（EDITOR）
  - 节点顺序配置
- 审批请求管理（编辑者可访问）
  - 待我审批列表
  - 我的申请列表
  - 审批通过/拒绝/撤回

### 8. 字典管理

- 数据字典 CRUD
  - 按领域分组导航管理字典项（币种、状态、角色等），分类与所属领域在同一导航中呈现
  - 字典项支持 key/value/extraValue 三层映射
  - 启用/停用字典项
  - 按分类聚焦维护，PC 端左侧为分类分组导航，中间展示当前分类表格，右侧展示说明和效果预览；移动端使用单个领域/分类选择器
- 前端字典服务（useDict composable）
  - 全局字典缓存，页面 onMounted 时调用 loadAllDicts() 加载
  - 便捷方法：getStatusLabel()、getRoleLabel()、getCurrencySymbol()、getDictValue()、getDictOptions() 等
  - 所有页面硬编码的编码标签已替换为从字典服务动态获取
- 分类治理与保护
  - 受保护分类（style、theme、color_scheme 等）默认隐藏
  - "显示系统配置"开关（仅 ADMIN 可见）
  - 受保护分类只读展示，编辑需前往样式设置或分类视觉配置入口
  - 分类元数据定义（`dictCategoryMeta.ts`）：domain、extraValueMode、previewType、editableInDictPage、ownerPage、helpTitle、usage、usedIn、字段填写规则、风险提示和示例
- 分类帮助与效果展示
  - `DictCategoryHelpPanel` 展示分类用途、使用页面、维护入口和字段规则
  - `DictCategoryPreview` 优先使用当前分类真实启用项展示下拉选项、状态色、图标、JSON 或只读配置效果
  - 本能力只扩展前端元数据和页面组件，不新增后端接口，不修改 `sys_dict` 表结构
- extraValue 智能渲染
  - 根据分类元数据渲染不同类型
  - color 模式：色块 + 色值代码（如 `#0D6E6E`）
  - json 模式：格式化 JSON + 复制按钮
  - icon 模式：图标名称预览
  - text 模式：普通文本徽章
- 字典分类列表

| 分类 | 说明 | 示例 |
|------|------|------|
| currency | 币种 | CNY→人民币, USD→美元 |
| common_status | 通用状态 | ACTIVE→启用, INACTIVE→停用 |
| user_role | 用户角色 | ADMIN→管理员, EDITOR→编辑者, VIEWER→查看者 |
| approval_status | 审批状态 | PENDING→待审批, APPROVED→已通过 |
| workflow_type | 工作流类型 | PRICE_CHANGE→价格变更 |
| node_type | 节点类型 | APPROVER→审批节点, NOTIFIER→知会节点 |
| business_type | 业务类型 | PRICE→价格, PRODUCT→产品 |
| approval_action | 审批操作 | APPROVE→通过, REJECT→拒绝 |
| change_type | 变更类型 | CREATE→创建, UPDATE→更新, DELETE→删除 |
| unit | 计量单位 | TON→吨, KG→千克 |
| operation_type | 操作类型 | LOGIN→登录, CREATE→创建 |
| operation_module | 操作模块 | 用户管理→用户管理 |
| menu_icon | 菜单图标 | home→首页 |
| home_layout | 首页布局配置 | card_columns→产品卡片列数, featured_product_count→重点产品数量, product_list_mode→产品列表模式, product_table_page_size→产品表每页条数 |
| home_widget | 首页小组件配置 | summary_stats→经营摘要, core_metrics→核心指标 |
| price_alert | 价格预警规则 | single_day_rise→单日涨幅>5%, consecutive_rise→连续上涨3日 |
| chart_range | 图表时间范围 | 30d→30天, 180d→180天, 1y→12个月 |
| price_metric_group | 价格指标分组 | PRICE_STATUS→价格现状, SHORT_TERM_BUDGET→短期及预算偏差, MONTHLY_TREND→月度趋势 |
| price_metric | 价格指标 | LATEST_PRICE→最新价格, PREVIOUS_PRICE→上期有效价格, YEAR_OVER_YEAR_PERCENT→月均价同比 |
| api_key_status | API 密钥状态 | ACTIVE→生效中, DISABLED→已停用 |
| api_key_environment | API 密钥环境 | PRODUCTION→生产, TESTING→测试 |
| api_key_operation | API 密钥操作 | CREATE→创建, REVOKE→吊销 |
| api_auth_result | 外部 API 认证结果 | SUCCESS→成功, INVALID_SIGNATURE→签名错误 |
| api_permission | 外部 API 权限 | product:read→产品读取, price:write→价格写入 |
| notification_type | 通知类型 | PRICE_PUBLISHED→价格发布, SYSTEM_NOTICE→系统公告 |
| notification_channel | 通知渠道 | IN_APP→站内通知, WEBHOOK→Webhook |
| notification_mini_program_page | 小程序通知跳转页 | pages/notifications/index→消息通知, pages/home/index→首页等已登记小程序页面 |
| notification_delivery_status | 投递状态 | PENDING→待投递, FAILED→失败 |
| notification_provider_health_status | Provider 健康状态 | OK→正常, DEGRADED→降级, DOWN→异常, NOT_CONFIGURED→未配置 |
| notification_frequency_rule | 通知聚合频控规则 | TASK_FAILED→任务失败聚合频控, API_LIMIT_WARNING→API 告警聚合频控 |
| system_notice_status | 系统公告状态 | DRAFT→草稿, SCHEDULED→待发布, PUBLISHED→已发布, CANCELLED→已撤回, EXPIRED→已过期 |

### 9. 全局样式管理（仅管理员）

- **响应式配置工作台**
  - PC 布局：顶部配置域 Tab | 配置区 | 右侧预览区(360px)，所有实时预览取消外层卡片框，不设置独立滚动条，并粘性跟随页面整体滚动
  - 移动端：横向滚动导航 + 配置区 + 预览抽屉
  - 三层状态模型：serverConfig / draftConfig / appliedConfig
  - 页面内变更只进入草稿，顶部"保存配置"统一持久化并正式生效
- **8 个配置域**
  - 总览：当前方案摘要、保存状态、健康检查；方案显示名优先使用接口预设缓存，静态编码仅作为数据协议
  - 品牌：系统名称与登录页副标题同排维护，文本输入实时进入草稿；Logo 上传、Logo 尺寸（small/medium/large/xlarge）、副标题样式
  - 色彩：色彩方案切换、高级涨跌色微调默认展开；图表色板在右侧预览区展示，颜色输入通过 `#RGB` / `#RRGGBB` 校验后进入草稿
  - 排版：字号预设切换（紧凑/标准/大字体/特大字体）、字体族配置
  - 布局：布局方案切换（顶部导航/左侧导航/驾驶舱/极简）、页面密度
  - 首页体验：产品卡片列数、重点产品数量（1-4）、产品列表模式、产品表每页条数；旧的布局模式、移动端卡片列数、重点走势/预警区独立开关不再参与首页渲染
  - 首页排序：首页组件显隐与上下顺序、产品列表分类顺序、分类内产品顺序；组件显隐统一由 `home_widget.enabled` 控制，分类顺序复用 `product_category.sort_order`，产品顺序复用 `product.sort_order`，影响产品列表、重点关注指标与重点走势
  - 分类视觉：按产品分类选择专业视觉方案（icon、主色、浅底、边框、趋势图色），微调助手与专家颜色默认展开且仅管理员可见
  - 版本恢复：历史版本列表、一键回滚；版本对比覆盖登录页副标题等文本差异
- **技术实现**
  - 后端：StyleConfigController、StyleConfigService、StyleVersionRepository
  - 前端：useStyleSettingsWorkbench composable 管理三层状态
  - CSS 变量动态化（--price-rise-color、--price-fall-color、--font-size-* 等）
  - 版本快照：每次保存自动生成版本，支持回滚；差异展示使用预设接口返回的动态名称

## 扩展性设计

### AI 功能扩展性

#### AI 服务抽象层

为支持多 AI 提供商切换和未来 AI 功能扩展，系统设计了 AI 服务抽象层：

```
┌─────────────────────────────────────────────────────────┐
│                    AI Service Layer                      │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
│  │ OpenAI     │  │ Claude     │  │ Local LLM  │       │
│  │ Service    │  │ Service    │  │ Service    │       │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘       │
│         └────────────────┼────────────────┘             │
│                          ▼                               │
│              ┌───────────────────────┐                   │
│              │    AIService          │                   │
│              │    (Interface)        │                   │
│              └───────────────────────┘                   │
└─────────────────────────────────────────────────────────┘
```

#### AI 服务接口定义

```java
public interface AIService {
    // 同步对话
    String chat(String prompt);
    // 带系统提示的对话
    String chat(String systemPrompt, String userPrompt);
    // 异步对话
    CompletableFuture<String> chatAsync(String prompt);
    // 流式对话
    Flux<String> chatStream(String prompt);
}
```

#### LangChain4j 集成

系统预留 LangChain4j 集成接口，支持：

- RAG 知识库问答
- 工具调用 (Tool Calling)
- 提示词模板管理
- 多模态支持

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
</dependency>
```

### API 服务扩展性

#### OpenAPI 文档集成

系统集成 SpringDoc OpenAPI，提供标准化 API 文档：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.4</version>
</dependency>
```

**效果：**

- 自动生成 OpenAPI 3.0 文档
- Swagger UI 可视化界面
- 支持 AI Agent 自动发现 API 能力
- API 版本管理支持

#### API 版本管理策略

| 版本策略 | 说明 | 适用场景 |
|----------|------|----------|
| URL 版本化 | `/api/v1/xxx` → `/api/v2/xxx` | 重大变更 |
| Header 版本 | `Accept: application/vnd.api.v2+json` | 渐进式变更 |

#### API 限流与监控

集成 Resilience4j + Micrometer 实现：

- API 限流
- 熔断降级
- Prometheus 监控
- 告警机制

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### MCP 服务扩展性

#### MCP 协议端点

系统实现 MCP (Model Context Protocol) Server，允许 AI Agent 调用系统功能：

```
┌─────────────┐      MCP Protocol      ┌─────────────────┐
│ AI Agent   │ ◄──────────────────► │ MCP Server      │
│ (Claude,   │                        │ /api/mcp/tools  │
│ Copilot)   │                        │ /api/mcp/invoke │
└─────────────┘                        └─────────────────┘
```

#### MCP 工具定义

```java
@RestController
@RequestMapping("/api/mcp")
public class MCPController {
    // 工具列表
    @GetMapping("/tools")
    public List<MCPTool> listTools();
    
    // 工具调用
    @PostMapping("/invoke")
    public MCPToolResult invokeTool(@RequestBody MCPToolInvocation invocation);
}

// 工具元数据
public class MCPTool {
    String name;           // 工具名称
    String description;    // 工具描述
    Object inputSchema;    // 输入参数 schema
}
```

#### MCP 工具示例

| 工具名称 | 描述 | 输入参数 | 输出 |
|----------|------|----------|------|
| price_query | 查询产品价格 | `{productName, date}` | 价格信息 |
| product_search | 搜索产品 | `{keyword}` | 产品列表 |
| log_analysis | 分析操作日志 | `{username, dateRange}` | 统计报告 |

### 依赖版本管理

#### BOM 管理策略

系统采用 Spring Boot BOM 统一管理传递依赖，减少版本冲突：

```xml
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM 已包含大部分依赖版本 -->
        
        <!-- 仅在需要覆盖 BOM 版本时显式声明 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-core</artifactId>
            <version>2.21.1</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### 版本升级原则

| 升级类型 | 触发条件 | 验证要求 |
|----------|----------|----------|
| 安全补丁 | CVE 漏洞披露 | 编译 + 单元测试 |
| 小版本升级 | 新功能需求 | 编译 + 集成测试 |
| 大版本升级 | 技术演进需求 | 完整回归测试 |

#### 前端依赖版本管理

```json
{
  "dependencies": {
    "vue": "^3.5.0",
    "vite": "^8.0.5",
    "axios": "^1.15.0",
    "vant": "^4.9.0",
    "echarts": "^5.5.0",
    "pinia": "^2.3.0"
  }
}
```

### 未来升级路线图

#### 2026 年计划

| 时间 | 升级项 | 理由 |
|------|--------|------|
| 2026-Q3 | Spring Boot 4.1.x | 安全更新 + 新特性 |
| 2026-Q3 | Vue 3.6.x | Composition API 增强 |
| 2026-Q4 | Java 21 LTS | 性能提升 + 虚拟线程 |

#### 2027 年计划

| 时间 | 升级项 | 理由 |
|------|--------|------|
| 2027-Q1 | Spring Boot 4.2.x | Jakarta EE 10 支持 |
| 2027-Q2 | GraphQL 支持 | API 灵活性增强 |
| 2027-Q3 | 向量数据库 | RAG 知识库支持 |
| 2027-Q4 | 微前端架构 | 前端模块解耦 |

### 数据库扩展设计

#### 向量搜索扩展

为支持 AI 知识库功能，预留向量数据库集成：

```sql
-- 预留表结构（未来实现）
CREATE TABLE IF NOT EXISTS document_embedding (
    id BIGINT PRIMARY KEY,
    document_id BIGINT,
    content_vector VECTOR(1536),
    created_time DATETIME
);
```

#### 审计日志扩展

系统操作审计日志支持 AI 辅助分析：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(50) | 操作人 |
| action | VARCHAR(50) | 操作类型 |
| resource | VARCHAR(100) | 资源类型 |
| resource_id | BIGINT | 资源 ID |
| changes | JSON | 变更内容 |
| ai_summary | TEXT | AI 摘要（未来扩展） |

---

## v1.6.8 文档一致性补充（2026-06-14）

本次补充对账 v1.5.0 ~ v1.6.7 期间实际已上线但本文档未独立列出的关键模块与表结构。原则：仅追加，不修改既有内容，避免与用户已编辑段落冲突。

### 新增独立模块索引

| 模块 | 状态 | 现有文档位置 | 本版本补充内容 |
|------|------|--------------|----------------|
| 价格草稿与批量发布 | 已上线 | 在 §3 "产品与价格管理" 中简述 | 见下方"模块 1：价格草稿与批量发布" |
| 通知中心（用户侧） | 已上线 | §6.1 仅描述管理端 | 见下方"模块 2：通知中心（用户侧）" |
| 定时任务 | 已上线 | 在 §3 中简述 | 见下方"模块 3：定时任务" |
| 产品年度预算 | 已上线 | 在 §3 中简述 | 见下方"模块 4：产品年度预算" |
| 样式管理 | 已上线 | §9 完整 | 无需补充 |
| 外部 API 授权 | 已上线 | §6 完整 | 无需补充 |

### 模块 1：价格草稿与批量发布

- **数据表**：`price_draft_batch`（批次）、`price_draft_item`（明细）、`price_publish_log`（发布日志）
- **核心流程**：录入 → 调用 `/api/price-drafts/batch-save` 保存草稿 → 发布前调用 `/api/price-drafts/publishable-summary` 概览 → 调用 `/api/price-drafts/publish-all` 一键发布全系统 `DRAFT` 草稿
- **核心服务**：`PriceDraftBatchService`、`PriceDraftItemService`、`PricePublishService`（带发布执行器、批次元数据、批次级幂等）
- **关键端点**：
  - `GET /api/price-drafts/by-date?date=yyyy-MM-dd`：按日期查询草稿
  - `GET /api/price-drafts/publishable-summary`：可发布摘要（日期数/批次数/明细数）
  - `POST /api/price-drafts/batch-save`：批量保存草稿
  - `POST /api/price-drafts/publish-all`：发布全系统所有 `DRAFT` 草稿（**推荐入口**）
  - `POST /api/price-drafts/{batchId}/publish`：单批次发布（兼容/维护入口）
  - `POST /api/price-drafts/by-date/publish`：按日期发布（定时任务/补发入口）
  - `POST /api/price-drafts/{batchId}/cancel`：取消批次
- **发布结果响应**：`{ batchResults, publishLogIds, totalDates, totalBatches }`
- **审计**：每条发布明细写入 `price_publish_log`，发布后写入正式 `price` 与 `price_history`，同步产品 `currentPrice` 并生成一条 `PRICE_PUBLISHED` 站内通知
- **幂等性**：发布重试只处理未发布明细，已发布明细通过 `itemStatus=PUBLISHED` 与 `publishedPriceId` 跳过

### 模块 2：通知中心（用户侧）

> 与 §6.1 "通知管理平台"（管理员后台）对应，本节描述用户侧的接收/读取/订阅体验。

- **核心端点**（用户侧）：
  - `GET /api/notifications/my?page&size&unread&category`：拉取我的通知
  - `GET /api/notifications/events`：**SSE 长连接**，实时推送未读数变化和新通知事件
  - `GET /api/notifications/unread-count`：未读数
  - `PUT /api/notifications/{id}/read`：标记单条已读
  - `PUT /api/notifications/read-all`：全部已读
  - `GET /api/notifications/preferences` / `PUT /api/notifications/preferences`：偏好设置
  - `DELETE /api/notifications/{id}`：删除单条
- **数据表**：`notification_message`（主表）、`notification_recipient`（收件人）、`notification_outbox`（可靠投递）、`notification_preference`（偏好）、`notification_delivery_log`（投递日志）、`notification_channel_config`（渠道配置）
- **站内底座**：`IN_APP` 是可靠消息底座，业务方只传外部渠道时 `NotificationService` 仍自动补齐站内渠道
- **小程序**：`NotificationMiniProgramTemplate`（模板）、`NotificationMiniProgramSubscription`（订阅）、`NotificationMiniProgramEligibility`（资格）、`NotificationMiniProgramResolution`（解析日志）
- **前端**：`Notifications.vue`（PC 5 页签工作台）、uniapp `pages/notifications/index.vue`（消息列表）
- **SSE 特性**：空闲 60 秒自动超时；前端 EventSource 组件实现自动重连；服务端异常通过 `GlobalExceptionHandler` 静默处理（v1.6.7 修复）

### 模块 3：定时任务

- **数据表**：`sys_scheduled_task`（任务定义）、`sys_scheduled_task_log`（执行日志）
- **核心端点**：
  - `GET /api/scheduled-tasks`：任务列表
  - `POST /api/scheduled-tasks`：创建任务（name/cron/handler/enabled）
  - `PUT /api/scheduled-tasks/{id}/enable` / `disable`：启停
  - `POST /api/scheduled-tasks/{id}/run`：立即执行一次
  - `GET /api/scheduled-tasks/{id}/logs`：执行日志
- **预置任务**：
  - `PRICE_PUBLISH`（价格自动发布）— 默认停用，使用数据库行锁和执行日志唯一约束防重复
  - 通知清理、订阅资格校准、过期公告清理等
- **前端**：`ScheduledTasks.vue`（PC 系统管理菜单下）

### 模块 4：产品年度预算

- **数据表**：`product_annual_budget`（V44 Flyway）
- **字段**：`id, product_id, year, budget_price, currency, remark, created_time, updated_time`
- **核心端点**：
  - `GET /api/product-budgets?productId&year`：列表
  - `POST /api/product-budgets`：创建/更新（按 productId+year 唯一）
  - `DELETE /api/product-budgets/{id}`：删除
- **使用场景**：
  - 预算管理工作台（产品管理下）
  - PC/小程序首页与详情页走势虚线
  - 价格查询页年度预算偏差指标
- **前端**：`BudgetManagement.vue`（产品管理下）
- **唯一入口**：预算管理页是预算信息唯一维护入口，其它页面和外部产品接口只引用 `product_annual_budget` 返回的年度预算

### 文档同步说明

- 本节为 v1.6.8 一致性补充，原有内容（§1 ~ §9）保持不变
- 后续若 §3 / §6.1 / §9 等章节的描述与本节有冲突，以实际 Flyway 迁移和 Controller 实现为准
- 真正的"模块章节"补全建议在 v2.0 大版本时统一重构

---

*版本：v2.0.0*
*最后更新：2026-06-15 — v2.0 文档拆分重构，原 v1.6.8 内容整合到 §功能模块*
