# PC端前后台第一性原理整改完善方案

制定日期：2026-06-30  
关联报告：[PC端前后台对抗式审查报告](PC端前后台对抗式审查报告.md)  
目标：在不破坏现有 PC 端功能、外部 API、已有角色使用路径的前提下，修复审查报告中的数据契约、权限边界、审计日志、前端一致性与构建性能问题。

## Context

本方案不直接以“逐条打补丁”为出发点，而是从系统成立所依赖的基本事实倒推整改边界：

1. 后端是权限事实源：前端路由和按钮隐藏只能改善体验，不能定义安全边界。
2. 数据库是数据事实源：接口参数必须准确映射查询条件，不能让调用方拿到语义相反的数据。
3. API 契约是前后台共同语言：每个参数、响应结构、错误码都应可测试、可文档化、可回归。
4. 操作日志是追责事实源：数据变更如果不能被审计，就等于系统无法解释自身历史。
5. 前端是工作台：前端整改要优先保持用户当前流程稳定，在此基础上提升一致性、自适应和安全性。

因此本次完善的核心原则是：先修复事实源错误，再增强防护与体验；先做兼容式修复，再做结构性优化；每一步都有回归验证，避免“修安全把业务修坏”。

## 不破坏现有功能的保护边界

- 不删除现有 API 路径，不修改前端已使用的基础 URL。
- 不改变 `Result<T>` 响应包装结构。
- 不改变现有 `ACTIVE` / `INACTIVE` / 角色编码等协议值。
- 内部 API 与外部 API 分开治理：内部管理端可更快收紧，外部 API 涉及第三方调用方时必须先兼容观测、再灰度严格化。
- 对权限收紧采用“先定级、再补测试、再上线”的方式；若接口确实被普通用户依赖，则新增脱敏/当前用户专用接口，不直接粗暴封禁。
- 操作日志补齐不得记录密码、token、API secret、微信密钥等敏感参数。
- 操作日志失败不得抛出到主业务响应；日志组件必须捕获自身异常，或在事务提交后异步写入。
- 前端自适应分页保留手动分页能力，避免用户已有操作习惯被强制替换。
- 构建性能优化不得改变图表展示口径和页面路由。
- 配置收口必须先完成环境差异清单，确认生产、测试、本地三套环境都有显式配置后再移除代码默认值。

## 实现方案

## 修复状态标识

状态口径：

- 已修复并验证：已完成代码修补，并通过 `mvn test` 或 `npm run build` 验证。
- 兼容保留：为避免破坏既有外部调用，当前保留兼容行为，但已补观测与退出条件。
- 部分完成：本轮已修复高风险缺陷，剩余为范围更大的治理专项。
- 未实施专项：属于后续优化项，本轮未改动，不能视为已完成。

本轮结论：

| 范围 | 当前状态 | 说明 |
|------|----------|------|
| 基础资料 `status` 查询语义 | 已修复并验证 | 内部分类、产地、客户接口已按 `ACTIVE` / `INACTIVE` 真实过滤；非法内部 status 返回 400；已补单测 |
| 外部 API v1 非法 `status` | 兼容保留 | 为不破坏现有 API Key 调用，非法非空 status 仍按 v1 兼容返回全量，但已输出结构化 warn，并补兼容测试 |
| 服务端权限边界 | 已修复并验证 | 权限、部门、首页等关键接口已补服务端授权；管理接口已从 `PUBLIC_PATHS` 移除；已补权限边界测试 |
| 高风险变更操作日志 | 已修复并验证 | 基础资料、菜单、字典、审批相关变更已补日志；失败路径响应码已与业务返回对齐 |
| 前端历史残留 | 已修复并验证 | 已替换 `/#/home`、`localStorage.user`、高风险 `window.confirm` 残留；前端构建通过 |
| 权限矩阵文档、全量日志枚举、日志异常隔离专项 | 部分完成 | 已有关键测试兜底，但完整矩阵、全量 POST/PUT/PATCH/DELETE 枚举与日志失败注入仍属后续治理 |
| PC 表格自适应推广 | 未实施专项 | 本轮未改动表格自适应逻辑，仍按方案作为后续阶段推进 |
| 配置收口、Token 安全增强、构建性能专项 | 未实施专项 | 本轮只完成构建验证，未改动生产配置、Token 承载方式或 chunk 拆分 |

### 0. 最后对抗式审查补丁项：修复落地后的观测与边界漂移

对已落地代码进行对抗式复核后，新增以下必须修补项。这些问题大多不直接破坏主业务返回，但会破坏“事实源”可信度，必须在继续推进表格自适应、配置收口和构建性能专项前处理。

| 问题 | 风险 | 解决方法 | 验证方式 | 当前状态 |
|------|------|----------|----------|----------|
| 手工操作日志失败路径默认记录 `responseCode=500` | 业务 400/404 会被审计和告警误判为服务端错误 | 所有捕获异常并返回 `Result.error(400/404/500)` 的控制器日志必须调用带 `responseCode` 的 `OperationLogHelper.logError` 重载，保证日志响应码与业务响应一致 | 增加/更新日志测试，断言 400、404、500 分别正确入库 | 已修复并验证：基础资料、字典、审批失败日志已按 400/404/500 对齐，`BasicDataStatusContractTests` 覆盖 400/404 |
| 外部 API v1 非法 `status` 只静默兼容，没有观测 | 无法判断 30 天兼容窗口是否可退出，也无法定位仍在发送非法参数的调用方 | `ExternalBasicDataController` 对非法非空 `status` 输出结构化 warn，记录资源名、status、认证主体/appId、URI；后续可接入指标或 API 调用日志字段 | 单测覆盖非法 status 仍兼容返回全量；人工检查日志字段包含 resource/status/principal | 已修复并验证：已补结构化 warn 与分类/产地/客户兼容测试；严格 400 切换仍按兼容窗口推进 |
| `PUBLIC_PATHS` 与方法级权限注解冲突 | 安全边界依赖第二道门，后续误删方法注解会暴露权限/部门/菜单接口 | 从 `SystemConstants.PUBLIC_PATHS` 移除已收紧的 `/api/menus/tree`、`/api/menus/visible`、`/api/departments/tree`、`/api/departments`、`/api/permissions`、`/api/permissions/tree`，只保留真正公开接口 | 增加权限测试或至少编译验证；对照 PC 路由确认不会误伤登录后调用 | 已修复并验证：`SecurityBoundaryContractTests` 断言管理路径不在公开白名单 |
| 权限矩阵和日志行为测试不足 | 现有测试只覆盖 status，不能防止权限/日志回归 | 补充轻量级反射或 MockMvc 测试：权限注解存在性、基础资料日志 responseCode、外部 status 兼容分支 | `mvn test` 必须通过 | 已修复关键缺口：已补权限边界、外部兼容、基础资料日志测试；完整角色矩阵仍作为后续治理 |
| `@OperationLog` 切面对 `Result.error` 无感知 | 捕获异常并正常返回错误结果的方法若挂注解，会被记为成功 | 规范要求：捕获异常并返回 `Result.error` 的接口不得直接使用 `@OperationLog`，必须手工记录成功/失败；仅不吞异常的方法可使用注解 | 代码审查检查新加注解位置，必要时补单测 | 已修复本轮涉及接口：捕获异常并返回错误的基础资料、字典、审批接口采用手工日志；后续新增接口继续按此规则审查 |

### 1. 数据契约修复：status 参数必须表达真实查询条件（状态：已修复并验证，外部 v1 严格化仍处兼容窗口）

第一性原理：调用方传入过滤条件，是在请求一组满足谓词的数据；后端不能把 `INACTIVE` 悄悄解释为 `ACTIVE`。

当前缺陷必须区分内外接口两种不同表现：

- 内部 PC 管理端接口：`ProductCategoryController`、`OriginController`、`CustomerController` 在传入合法 `status` 后都固定返回 ACTIVE 数据，因此 `status=INACTIVE` 会误返回启用数据。
- 外部 API v1 基础资料接口：`ExternalBasicDataController` 仅对 `status=ACTIVE` 做过滤，其它值包括 `INACTIVE` 和非法值都会返回全量数据。

测试用例必须按这两种现状分别设计，避免把外部 API 的“误返回全量”误写成“误返回 ACTIVE”。

整改范围：

- `ProductCategoryController`
- `OriginController`
- `CustomerController`
- `ExternalBasicDataController` 同类基础资料接口
- 对应 Service / Repository / 前端 API 类型

方案：

- Service 层新增或复用 `findByStatusOrderBySortOrderAsc(CommonStatus status)` 查询能力。
- Controller 使用解析后的 `CommonStatus` 变量，不再只判断“是否合法”。
- 非法 status 返回 400，错误信息使用统一业务错误，不再吞异常后回退全量。
- 前端 API 将 `status?: CommonStatus` 类型化，避免任意字符串传入。
- 内外接口采用不同落地节奏：
  - 内部 PC 管理端接口：直接严格校验非法 status 并返回 400。
  - 外部 API：先记录非法 status 调用告警与调用方 appId，保持一个版本周期的兼容窗口；窗口结束后再切换为 400，或通过 `/api/external/v2` 做版本化严格契约。
  - 外部 API 兼容窗口建议默认 30 天；连续 14 天无非法 status 调用后，才允许切换到严格 400。
  - 若兼容窗口内仍有活跃 appId 发送非法 status，应先通知调用方整改；必要时保留 v1 兼容行为并在 v2 启用严格契约。

兼容策略：

- `status` 为空时保持现有全量查询语义不变。
- `status=ACTIVE` 返回结果与当前行为一致。
- 仅修正 `status=INACTIVE` 与非法 status 的错误行为。
- 外部 API 切换前必须输出调用方影响清单：近 30 天是否存在非法 status、对应 appId、调用次数、最后调用时间。

### 2. 权限边界补齐：前端路由权限必须有后端等价约束（状态：核心边界已修复并验证，完整权限矩阵待后续补齐）

第一性原理：任何可被网络直接访问的资源，都必须由服务端独立判断授权。

整改范围：

- `PermissionController`
- `DepartmentController`
- `HomeController`
- 与 PC 路由元信息相关的管理接口

方案：

- 为首页读接口补齐 `hasAnyRole('ADMIN','EDITOR','VIEWER')`，与现有产品、价格等读接口保持一致。
- 权限树接口默认收紧为 ADMIN；如普通用户需要按钮权限，改走当前用户 profile/permission 摘要，不暴露完整权限树。
- 部门接口分级处理：
  - 用户管理/部门管理用途：ADMIN。
  - 业务展示用途：提供脱敏部门树，只返回 `id/name/parentId` 等必要字段。
- 建立“路由 meta 与后端注解对照表”，后续新增页面时同步检查。
- 权限矩阵必须作为阶段性交付物，至少包含：接口路径、HTTP 方法、当前注解、目标注解、PC 调用页面、ADMIN/EDITOR/VIEWER/API Key 预期结果、是否需要脱敏替代接口。
- 复用现有权限事实源：当前登录响应、刷新 token 响应、profile 相关服务已经返回当前用户权限摘要，前端 `useUserStore.hasPermission()` 已以该权限集合为准。整改时优先复用这条链路，不新增第二套“当前用户权限接口”，避免权限来源分裂。

权限矩阵模板：

| 接口 | 方法 | 当前权限 | 目标权限 | 调用页面/模块 | ADMIN | EDITOR | VIEWER | API Key | 是否需脱敏接口 | 测试用例 |
|------|------|----------|----------|---------------|-------|--------|--------|---------|----------------|----------|
| `/api/permissions/tree` | GET | 仅登录 | ADMIN | 角色管理 | 200 | 403 | 403 | 不适用 | 否 | `viewerCannotReadPermissionTree` |
| `/api/departments/tree` | GET | 仅登录 | 待矩阵确认 | 用户管理/部门管理 | 待定 | 待定 | 待定 | 不适用 | 视业务而定 | `departmentTreeAccessMatrix` |

兼容策略：

- 先通过代码检索确认当前 PC 页面真实调用方。
- 对已被非管理员页面依赖的读接口，优先新增脱敏接口，不直接改变原接口响应。
- 权限变更配套 MockMvc 测试，明确 ADMIN/EDITOR/VIEWER 的访问矩阵。
- 权限收紧上线后观察 403 指标；若某角色 403 异常升高，先按矩阵判断是预期拦截还是误伤。

### 3. 操作日志补齐：所有变更都有可追溯事实（状态：高风险接口已修复并验证，全量枚举与异常隔离专项待后续补齐）

第一性原理：系统状态发生改变，就必须能回答“谁在什么时候因为什么改了什么”。

整改范围：

- 基础资料：分类、产地、客户
- 菜单配置
- 数据字典
- 审批流与审批节点
- 个人资料、安全会话、偏好设置
- 其他报告中列出的变更接口

方案：

- 优先使用 `@OperationLog` 统一记录模块、类型、描述。
- 对需要记录业务对象名、失败原因的接口继续使用 `OperationLogHelper`，但形成统一标准。
- 敏感接口设置 `logParams=false`，必要时只记录对象 ID 与动作结果。
- 给操作日志补齐后端单测或集成验证，至少覆盖成功、失败两类路径。
- 明确日志实现约束：
  - `@OperationLog` 切面必须捕获并吞掉日志写入异常，同时输出服务端错误日志。
  - 对主业务强一致要求低的日志，优先 after-commit 或异步写入，避免主事务回滚。
  - 日志脱敏规则必须覆盖 password、token、secret、authorization、refreshToken、appSecret、apiKey 等字段名。
  - 若改造为 after-commit 或异步写入，必须在同步请求线程中先采集 `username`、`userId`、`requestUrl`、`ipAddress`、`userAgent`、`requestParams` 等快照，异步线程不得再依赖 `SecurityContextHolder` 或 `RequestContextHolder` 读取上下文。
  - 当前 `OperationLogHelper` 与 `OperationLogService` 已经捕获日志保存异常，实施时应优先补覆盖与测试，不应为了“异步化”而引入不必要的上下文丢失风险。

兼容策略：

- 不改变接口业务返回。
- 日志写入失败不影响主业务事务，除非已有日志组件明确要求强一致。
- 先补高风险模块，再补普通模块。
- 增加“日志失败注入”测试：模拟日志 Repository 抛异常，主接口仍返回原业务结果。

### 4. 前端权限与历史残留清理（状态：本轮发现残留已修复并通过构建）

第一性原理：前端状态只能有一个可信来源；同一类跳转只能有一种机制。

整改范围：

- `UserManagement.vue` 中 `/#/home` 跳转残留
- `Approval.vue` 中 `localStorage.user` 残留
- 前端状态展示硬编码

方案：

- 页面内跳转统一使用 `router.push` / `router.replace`。
- 当前用户统一来自 `useUserStore()`；如缺字段则扩展 profile 响应，而不是读取历史 localStorage。
- 状态展示统一使用 `useDict`，命令式按钮文案可保留固定文本。
- 状态颜色优先使用字典 `extraValue` 或设计 token。

兼容策略：

- 不改变现有路由 path。
- 不改变 Pinia store 对外方法名。
- 修改前先确认审批页申请人 ID/角色来源，避免审批流程断链。

### 5. PC 表格自适应能力抽取（状态：未实施专项）

第一性原理：PC 工作台的表格展示应由真实可用空间决定，而不是由固定数字猜测。

整改范围：

- 用户管理
- 操作日志
- API 调用日志
- 后续可扩展到审批列表、通知列表

方案：

- 抽取 `useAdaptivePageSize` composable，输入容器 ref、表头选择器、行选择器、最小/最大条数、默认条数。
- 在 PC 视口默认使用“自适应”；移动端保持现有分页或卡片布局。
- 筛选、搜索、窗口尺寸变化、分页大小切换后重新校准。
- 保留手动 page size 选项，增加“自适应”选项作为默认。

兼容策略：

- 首批只接入一个页面验证，例如用户管理。
- 通过视觉和接口请求验证 page/size 同步。
- 没有可测行高时回退当前固定 page size。

### 6. 生产配置收口（状态：未实施专项）

第一性原理：默认配置应该安全、可迁移；环境差异应该由环境声明，而不是硬编码在代码中。

整改范围：

- CORS 默认来源
- Redis 默认主机
- 生产域名/IP
- Token 存储与安全头

方案：

- CORS 代码默认只保留 localhost；生产来源必须通过 `CORS_ALLOWED_ORIGINS` 配置。
- Redis 默认值改为 localhost 或空值校验；生产内网地址迁移到部署配置。
- 增加启动时配置校验，对生产 profile 下缺失关键安全配置直接失败或告警。
- Token 存储分阶段治理：先缩短 access token、强化 refresh token 失效；后续迁移到 HttpOnly Cookie。
- 增加部署前配置差异检查清单，至少覆盖：`CORS_ALLOWED_ORIGINS`、`REDIS_HOST`、`REDIS_PASSWORD`、`JWT_SECRET`、`DEFAULT_USER_PASSWORD`、`API_KEY_ENABLED`、`API_KEY_ENCRYPTION_KEY`、通知渠道密钥。

兼容策略：

- 生产配置变更先更新 `.env.example`、部署文档、运维清单。
- 不在同一次改动中同时切换 Token 承载方式和业务权限，降低回滚复杂度。
- 生产默认值移除前，先在 staging 使用生产等价配置启动一次并完成登录、首页、外部 API、通知配置 smoke test。

### 7. 构建性能治理

第一性原理：用户首次打开页面只应下载完成当前任务所需的代码。

整改范围：

- ECharts 相关 chunk
- 路由懒加载与图表懒加载
- 构建预算

方案：

- 检查图表注册方式，按页面需要注册 renderer/chart/component。
- 对非首屏图表做可见区懒加载。
- 建立 bundle budget：单 chunk 超过 500 kB 必须说明原因或拆分。
- 保持现有页面路由懒加载。

兼容策略：

- 不改变图表数据计算逻辑。
- 优先拆加载边界，不改业务组件内部算法。

## 关键参考文件

- `docs/plan/PC端前后台对抗式审查报告.md`
- `backend/src/main/java/com/pricemanagement/controller/ProductCategoryController.java`
- `backend/src/main/java/com/pricemanagement/controller/OriginController.java`
- `backend/src/main/java/com/pricemanagement/controller/CustomerController.java`
- `backend/src/main/java/com/pricemanagement/controller/external/ExternalBasicDataController.java`
- `backend/src/main/java/com/pricemanagement/controller/PermissionController.java`
- `backend/src/main/java/com/pricemanagement/controller/DepartmentController.java`
- `backend/src/main/java/com/pricemanagement/controller/HomeController.java`
- `backend/src/main/java/com/pricemanagement/annotation/OperationLog.java`
- `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java`
- `frontend/src/router/index.ts`
- `frontend/src/store/useUserStore.ts`
- `frontend/src/composables/useDict.ts`
- `frontend/src/views/UserManagement.vue`
- `frontend/src/views/Approval.vue`
- `frontend/src/views/Products.vue`
- `frontend/src/views/Notifications.vue`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/pricemanagement/config/SecurityConfig.java`

## 实现步骤

### 阶段一：事实源修复（状态：已修复并验证，外部 v1 严格化待兼容窗口结束）

1. 修复基础资料 `status` 查询语义。
2. 补齐相关单测：`ACTIVE`、`INACTIVE`、非法 status。
3. 核对外部 API 同类基础资料接口，保持内外接口语义一致。
4. 对外部 API 输出非法 status 调用影响清单，决定兼容窗口或版本化切换。
5. 内部与外部接口分别建立回归断言：
   - 内部接口断言 `status=INACTIVE` 不再返回 ACTIVE 数据。
   - 外部接口断言兼容窗口内非法 status 产生告警，严格模式下非法 status 返回 400。

交付标准：

- 不传 status 时仍返回原全量结果。
- `status=ACTIVE` 与现状一致。
- `status=INACTIVE` 返回停用数据。
- 内部 API 非法 status 返回明确 400。
- 外部 API 严格化前完成调用方影响评估；若仍处兼容窗口，必须有告警日志和切换日期。
- 外部 API 兼容窗口具备明确退出条件：默认 30 天窗口，连续 14 天无非法 status 调用，或全部活跃调用方确认完成整改。

### 阶段二：服务端权限闭环（状态：核心接口已修复并验证，完整矩阵与上线观测待后续）

1. 建立 PC 路由与后端接口权限矩阵。
2. 为首页、权限、部门等接口补齐服务端授权。
3. 对存在普通用户依赖的接口拆出脱敏接口。
4. 增加权限回归测试。
5. 上线后观察 403 指标并与权限矩阵核对。

交付标准：

- VIEWER 无法读取完整权限树。
- 非管理员无法访问管理域部门详情，除非走脱敏接口。
- 首页接口对三类登录角色可用。
- 权限矩阵随代码一同提交，后续新增路由/API 必须更新。

### 阶段三：操作日志治理（状态：高风险接口已修复并验证，全量治理待后续）

1. 列出所有 POST/PUT/PATCH/DELETE 接口。
2. 标注已记录、需补充、需脱敏三种状态。
3. 先补菜单、字典、审批流、基础资料。
4. 再补个人资料、偏好、会话类变更。
5. 增加日志异常隔离测试和敏感字段脱敏测试。

交付标准：

- 高风险变更在 `operation_log` 中可查询。
- 敏感字段不入库。
- 日志失败不破坏主流程。
- 服务端能观测日志写入失败次数，并可定位失败模块。

### 阶段四：前端兼容清理（状态：本轮发现项已修复并验证）

1. 替换 `/#/home` 跳转为 router 跳转。
2. 替换 `localStorage.user` 为 `useUserStore()`。
3. 整理状态展示硬编码，优先接入 `useDict`。
4. 将通知高风险 `window.confirm` 替换为统一确认弹窗。

交付标准：

- 路由 history 模式无 hash 残留。
- 审批流程仍能正确识别当前用户。
- 状态展示随字典变更同步。

### 阶段五：PC 表格自适应推广（状态：未实施专项）

1. 从 `Products.vue` 提炼自适应分页经验。
2. 新增 `useAdaptivePageSize`。
3. 用户管理先接入并验证。
4. 再推广到操作日志、API 调用日志。

交付标准：

- 1366x768、1920x1080、2560x1440 下 page size 自动调整。
- 请求参数 `size` 与实际展示条数一致。
- 手动分页仍可用。

### 阶段六：配置与性能专项（状态：未实施专项，仅完成当前构建验证）

1. 收口 CORS 与 Redis 默认配置。
2. 更新 `.env.example` 与部署文档。
3. 建立构建产物预算。
4. 拆分或懒加载 ECharts 大 chunk。
5. 输出生产/测试/本地配置差异检查清单。
6. 建立上线后观测指标。

交付标准：

- 生产环境关键来源全部来自环境变量。
- `npm run build` 通过，chunk 超限有解释或拆分。
- 页面首屏不因非当前任务图表被迫加载。
- staging 通过生产等价配置 smoke test。

## Verification

后端验证：

- `mvn test`：已通过，165 个测试全部成功。
- 新增权限矩阵测试：已补关键边界测试，覆盖公开白名单与权限/部门接口注解；ADMIN/EDITOR/VIEWER 全量 MockMvc 矩阵待后续补齐。
- 新增 status 查询测试：已完成，覆盖分类、产地、客户、外部基础资料接口。
- 操作日志验证：基础资料、菜单、字典、审批等高风险接口已补日志；全量 POST/PUT/PATCH/DELETE 枚举待后续补齐。
- 操作日志异常隔离测试：待后续补齐，当前日志组件已有异常捕获机制。
- 外部 API 兼容验证：已完成非法 status 兼容告警测试；严格模式返回 400 待兼容窗口结束后实施。

前端验证：

- `npm run build`：已通过。
- PC 端手动走查：待上线/联调阶段执行。
- 用户管理自适应分页在三档桌面分辨率下截图确认：未实施专项。
- 字典值修改后，相关状态展示无需改代码即可变化：待字典展示专项继续治理。

兼容性验证：

- 原有前端 API 路径不变。
- 原有角色 ADMIN/EDITOR/VIEWER 的主要菜单可达性不退化。
- 外部 API Key 调用基础资料接口语义与内部接口一致。
- 生产部署前使用当前 `.env.production` 对照新配置清单，确认没有隐式默认值缺失。
- 对近 30 天外部 API 调用日志做一次兼容性扫描，确认严格化不会误伤活跃调用方。

上线观测：

- 400 指标：按接口统计非法 status、参数校验失败数量。
- 403 指标：按角色、接口统计权限拒绝数量，和权限矩阵预期比对。
- 操作日志指标：日志写入成功数、失败数、脱敏命中数。
- 前端性能指标：关键路由 chunk 加载耗时、图表 chunk 加载耗时、首屏可交互时间。
- 配置指标：启动时输出关键配置来源校验结果，不输出敏感值。

## 风险与回滚

- 权限收紧风险：如果发现普通用户依赖原接口，回滚策略不是长期放开，而是临时恢复原注解并补脱敏接口。
- 日志补齐风险：如果日志切面影响敏感参数，应立即设置 `logParams=false` 并补脱敏规则。
- 自适应分页风险：如果页面高度测量异常，回退固定 page size。
- 配置收口风险：生产环境切换前先在 staging 验证 CORS、Redis、JWT、外部 API。
- 外部 API 严格化风险：若活跃调用方仍发送非法 status，先延长兼容窗口并通知调用方，避免直接中断集成。
- 观测缺失风险：若上线后无法拿到 400/403/日志失败指标，不继续推进下一阶段收紧。

## 建议优先级

1. `status` 查询语义修复。
2. 后端权限闭环。
3. 操作日志补齐。
4. 前端历史残留与字典展示清理。
5. 表格自适应复用。
6. 配置收口与 Token 安全增强。
7. 构建性能专项。

## 阶段推进闸门

- 进入阶段二前：阶段一测试通过，外部 API 调用方影响清单完成。
- 进入阶段三前：权限矩阵完成，权限测试通过，上线后 403 指标无异常误伤。
- 进入阶段四前：高风险变更接口操作日志覆盖完成，日志异常隔离测试通过。
- 进入阶段六前：前端清理和表格自适应至少完成一个 PC 管理页试点。
- 生产切换前：配置差异清单、staging smoke test、上线观测面板三项全部完成。
