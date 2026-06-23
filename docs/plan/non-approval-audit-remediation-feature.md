# 非审批流代码审计整改方案

## Context

2026-06-23 对 PC 前端、uni-app 前端和后端进行整体审计后，发现若干与认证、配置、构建、字典规范和安全防护相关的偏差。本方案只覆盖审批流以外的整改项；审批流相关的对象级权限、待审批列表过滤等问题按用户决策暂缓，不纳入本轮计划。

本轮审计已完成以下验证：

| 验证项 | 结果 | 说明 |
|---|---|---|
| 后端测试 | 通过 | `mvn test`，131 tests，0 failures |
| PC 前端构建 | 通过 | `npm run build` |
| uni-app 类型检查 | 通过 | `npm run typecheck` |

这些问题大多不会在编译期暴露，主要属于运行契约、配置默认值、安全策略和长期维护一致性风险。

## 企业级评分目标

整改完成后的目标评分为 **9.2/10**。评分不以“问题数量减少”为唯一标准，而以企业级管理系统所需的可控性、可审计性、可观测性和可持续治理能力为准。

| 维度 | 目标 | 验收口径 |
|---|---|---|
| 安全基线 | 高风险认证、密钥、拦截链路均有默认安全策略 | P0 全部关闭，P1 不存在无闭环安全设计 |
| 契约一致性 | PC、uni-app、后端 API 契约一致 | 登录、续签、退出、错误码、分页、字典接口均可自动或手工验证 |
| 配置治理 | 默认值安全，生产配置显式化 | 敏感配置无明文提交，生产开关必须通过环境变量声明 |
| 变更控制 | 每项整改可拆分、可回滚、可灰度 | 每个风险项都有影响面、回滚点、验证证据 |
| 运维可观测 | 关键安全行为可监控、可告警、可追踪 | 认证失败、黑名单命中、外部 API 拒绝、字典加载失败有日志或指标 |
| 文档与交接 | 方案能支撑多人协作和审计复盘 | 责任、阶段门禁、证据清单、交付清单完整 |

本次评分不包含审批流。审批流的业务规则、状态机、权限边界、历史数据迁移和审计责任应在用户确认业务方案后单独评分，避免把未定需求混入当前整改闭环。

## 范围与非目标

### 本次范围

- Refresh Token 续签时的用户状态校验。
- uni-app 认证接口与后端契约对齐。
- 外部 API Key 默认开关、密钥配置和文档一致性。
- IP 黑名单表的实际拦截闭环。
- Maven / Gradle 双构建入口分叉治理。
- 前端字典动态化规范补齐。
- `v-html` 静态 SVG 渲染风险收口。
- 与以上整改相关的正式项目文档同步。

### 非目标

- 不处理审批流接口、审批列表、审批记录等权限模型，后续单独规划。
- 不重构整体权限体系。
- 不新增微信登录能力，除非产品确认小程序必须支持微信一键登录。
- 不改动业务表结构，除非 IP 黑名单拦截实现需要补最小索引或状态字段。
- 不进行生产密钥轮换操作；本方案只给出代码与文档整改要求，密钥轮换需走运维安全流程。

### 风险接受与延期项

| 项目 | 状态 | 原因 | 控制措施 |
|---|---|---|---|
| 审批流设计与实现 | 延期 | 业务规则、组织职责和流程边界未最终确认 | 本方案不记录具体审批流漏洞和修复动作；后续单独立项并重新审计 |
| 生产密钥轮换 | 单独运维窗口 | 涉及线上凭据、调用方同步和回滚预案 | 本方案只治理代码默认值和配置说明，不直接替换生产密钥 |
| 统一身份源 / SSO | 后续增强 | 当前系统已有本地认证，SSO 需要企业组织架构和身份源选型 | 纳入企业级 Backlog，不阻塞本次高风险整改 |
| 全量权限模型重构 | 后续增强 | 当前目标是关闭明确风险，避免在审批流未定时重构权限域 | 仅处理本次风险项所需的最小权限和安全默认策略 |

## 治理与责任矩阵

| 角色 | 主要责任 | 输出物 |
|---|---|---|
| 项目负责人 | 确认整改优先级、范围排除项和上线窗口 | 范围确认、上线批准、延期项记录 |
| 后端负责人 | 认证、API Key、IP 黑名单、构建入口、配置治理整改 | 后端代码、单元/集成测试、配置变更说明 |
| 前端负责人 | PC 端字典动态化、`v-html` 收口、接口契约适配 | 前端代码、页面验证记录、构建产物 |
| uni-app 负责人 | 移动端登录/续签/退出契约对齐 | uni-app 代码、模拟器或真机验证记录 |
| 安全复核人 | 复核认证、密钥、XSS、拦截链路与日志脱敏 | 安全复核意见、残余风险说明 |
| 测试负责人 | 组织回归、契约、权限、异常路径验证 | 测试报告、缺陷闭环记录 |
| 运维负责人 | 环境变量、部署、监控、回滚和观测窗口 | 发布清单、回滚方案、监控记录 |
| 文档负责人 | 同步 README、开发文档、API 文档和计划状态 | 文档更新记录、交付清单 |

实际执行时可以一人承担多个角色，但每项职责必须有明确负责人，避免企业级系统中常见的“代码已改但上线、监控、文档无人兜底”问题。

## 业务影响与变更窗口分级

| 等级 | 说明 | 适用整改项 | 上线要求 |
|---|---|---|---|
| L0 | 仅文档、构建脚本或静态代码治理，不影响运行时行为 | 构建入口治理、弱默认示例治理、文档同步 | 常规发布即可 |
| L1 | 影响单一页面、单一端或低风险运行时逻辑 | 字典动态化、`v-html` 收口、uni-app 契约对齐 | 需要功能回归和冒烟验证 |
| L2 | 影响认证、安全开关或请求拦截链路 | Refresh Token 状态校验、API Key 默认开关、IP 黑名单拦截 | 需要预发验证、回滚点和观测窗口 |
| L3 | 影响核心业务流程、历史数据或组织职责 | 审批流、权限域重构、生产密钥轮换 | 本方案不执行，需单独立项 |

本方案内 P0、P1 项至少按 L2 管控：必须先在测试/预发环境验证，再进入生产发布；发布后保留 24 小时观测窗口。

## 风险清单

| 编号 | 优先级 | 风险 | 当前证据 | 影响 |
|---|---|---|---|---|
| R-01 | P0 | 用户被锁定或禁用后，已有 Refresh Token 仍可续签 Access Token | `AuthController` 登录检查锁定/禁用；`RefreshTokenService.refreshAccessToken` 未复用该检查 | 被停用账号可能继续访问直到 refresh token 失效 |
| R-02 | P0 | uni-app 认证接口与后端方法/结构不一致 | uni-app 用 POST 更新资料/改密，后端只暴露 PUT；uni-app 调用未实现的 `/api/auth/wechat-login` | 小程序个人资料、改密或微信登录相关能力运行失败 |
| R-03 | P0 | 外部 API 默认开关与文档相反 | `application.yml` 默认为 `API_KEY_ENABLED:true`，README/部署文档描述默认 false | 缺少 `API_KEY_ENCRYPTION_KEY` 时生产启动失败或外部入口意外开启 |
| R-04 | P1 | `ip_blacklist` 只有表和 Repository，没有请求拦截 | `IpBlacklistRepository` 未被过滤器/拦截器调用 | 黑名单策略不可生效，安全事件闭环断开 |
| R-05 | P1 | 后端存在 Maven 与 Gradle 两套互相矛盾的构建入口 | `pom.xml` 为真实工程；`build.gradle` 仍是旧脚手架依赖 | CI/新人误用 Gradle 会构建错误工程 |
| R-06 | P1 | 字典规范未完全落地 | `UNIT_OPTIONS` 模块加载时一次性回退硬编码；uni-app 基础资料启停提示硬编码中文 | 后台字典维护无法同步到部分页面 |
| R-07 | P2 | `v-html` 渲染 SVG 形成潜在 XSS 扩展点 | `ProductEdit.vue` 用 `v-html="section.icon"` | 当前静态安全边界尚可，但未来动态化易引入 XSS |
| R-08 | P2 | 本地生产式环境文件和弱默认示例治理不足 | 工作区存在忽略文件 `.env.production`；文档和开发配置保留弱示例 | 本地泄露风险、误用弱密码风险 |

## 整改原则

1. **后端先兜底**：认证、禁用、黑名单等安全能力必须在后端强制执行，不能依赖前端隐藏入口。
2. **契约单源化**：前端 API 封装必须匹配控制器路径、HTTP 方法和响应结构。
3. **默认安全**：外部入口默认关闭，生产密钥必须显式注入。
4. **构建入口唯一**：保留一套真实构建入口，避免 CI 和本地开发行为分叉。
5. **字典动态展示**：编码值显示名称走 `useDict`，仅允许协议值和 CSS class 绑定硬编码。
6. **文档随代码走**：涉及认证、配置、部署、API 或 UI 规范的改动，必须同步对应文档。

## 实施方案

### P0-01：Refresh Token 续签状态校验

涉及文件：

- `backend/src/main/java/com/pricemanagement/service/RefreshTokenService.java`
- `backend/src/main/java/com/pricemanagement/controller/AuthController.java`
- `backend/src/test/java/com/pricemanagement/service/RefreshTokenServiceTests.java`（如不存在则新增）

实现要点：

- 在 `refreshAccessToken` 读取用户后，检查：
  - 用户存在。
  - `status == ACTIVE`。
  - `isLocked != true`。
  - 用户至少拥有一个有效角色；若 `UserRole` 为空再回退 `User.role` 时，也需确认账号可用。
- 用户禁用、锁定或角色异常时：
  - 拒绝续签。
  - 撤销该 refresh token；若风险更高，可撤销该用户全部 refresh token。
  - 返回统一 401，不暴露过多状态细节。
- 后台锁定/禁用用户时，可补充撤销全部 refresh token，降低等待刷新时才失效的窗口。

验证方式：

- 单测覆盖 ACTIVE、INACTIVE、locked、用户不存在、角色为空等分支。
- 手工验证禁用用户后，旧 access token 到期再刷新会失败。
- 确认正常用户刷新不受影响。

文档同步：

- `docs/dev/api/auth.md`
- `docs/dev/design/security.md`
- `docs/dev/workflow/deployment.md` 如涉及运维说明。

### P0-02：uni-app 认证契约对齐

涉及文件：

- `frontend-uniapp/src/api/auth.ts`
- `frontend-uniapp/src/store/useUserStore.ts`
- `frontend-uniapp/src/pages/login/index.vue`
- `backend/src/main/java/com/pricemanagement/controller/AuthController.java`（仅在确认需要微信登录时改）

实现要点：

- `updateProfile` 从 `POST /api/auth/profile` 改为 `PUT /api/auth/profile`。
- `changePassword` 从 `POST /api/auth/password` 改为 `PUT /api/auth/password`。
- `fetchProfile` 解析后端 `{ user, permissions }` 包装结构，保存 `data.user`，不要把整个响应对象当作 User。
- `/api/auth/wechat-login` 二选一：
  - 若小程序暂不支持微信登录：删除前端 API 和 store 中的 `wechatLoginAction`，页面不显示入口。
  - 若必须支持：新增后端端点、DTO、微信 code 换 openid 服务、账号绑定/创建规则、限流和操作日志，单独扩展方案。

验证方式：

- `npm run typecheck`。
- 小程序/H5 端登录、恢复会话、获取个人信息、退出登录。
- 如保留改密入口，验证改密成功后本地会话清理和重新登录。

文档同步：

- `docs/dev/api/auth.md`
- `docs/dev/api/internal.md`
- `docs/dev/design/api-design.md`
- `docs/dev/design/ui.md` 如移除微信登录入口。

### P0-03：外部 API 默认开关与密钥配置统一

涉及文件：

- `backend/src/main/resources/application.yml`
- `backend/src/main/resources/application.yml.example`
- `.env.example`（若仍保留模板）
- `docker-compose.yml`
- `README.md`
- `docs/dev/workflow/deployment.md`
- `docs/ops/IDEA部署指南.md`
- `docs/ops/外部API生产部署检查清单.md`

实现要点：

- 将 `api-key.enabled` 默认值统一为 `${API_KEY_ENABLED:false}`。
- 保持 `API_KEY_ENCRYPTION_KEY` 无默认值；只有开发 profile 可提供明确标注的开发兜底 key。
- 启用外部 API 时，缺少或格式错误的 `API_KEY_ENCRYPTION_KEY` 必须启动失败。
- 后台创建 API Key / 保存通知渠道密钥时继续调用 `requireValidEncryptionKey`。
- 文档统一表达：
  - 外部 API 签名入口默认关闭。
  - 创建 API Key 或保存密钥配置需要加密主密钥。
  - 生产禁止使用开发示例 key。

验证方式：

- 未配置 `API_KEY_ENABLED` 时，应用可启动且 `/api/external/**` 返回关闭状态或 404/401，按设计确认。
- `API_KEY_ENABLED=true` 且缺 key 时启动失败。
- `API_KEY_ENABLED=true` 且合法 key 时外部 API 鉴权链路可用。

文档同步：

- `README.md`
- `docs/dev/workflow/deployment.md`
- `docs/dev/design/security.md`
- `docs/dev/api/external.md`
- `docs/ops/IDEA部署指南.md`
- `docs/ops/外部API生产部署检查清单.md`

### P1-01：IP 黑名单拦截闭环

涉及文件：

- `backend/src/main/java/com/pricemanagement/entity/IpBlacklist.java`
- `backend/src/main/java/com/pricemanagement/repository/IpBlacklistRepository.java`
- 新增 `backend/src/main/java/com/pricemanagement/config/IpBlacklistFilter.java`
- `backend/src/main/java/com/pricemanagement/config/SecurityConfig.java`
- 安全事件或操作日志相关服务。

实现要点：

- 新增 `OncePerRequestFilter`：
  - 提取真实客户端 IP，复用 `IpAddressUtil`。
  - 查询 active 黑名单。
  - `expiresAt` 已过期时可懒失效并放行。
  - 命中 active 黑名单时返回 403 或 429，并记录安全事件。
- 过滤器位置放在 JWT/API Key 鉴权之前，减少无效请求消耗。
- 增加缓存，避免每个请求查库；可先本地短 TTL，后续接 Redis。
- 明确白名单：
  - 本机健康检查。
  - 内部反向代理 IP。
  - 运维明确配置的可信来源。

验证方式：

- 单测或 MockMvc 覆盖命中、未命中、过期失效、白名单。
- 手工插入 active 黑名单 IP，验证请求被拒绝。
- 验证正常用户请求不增加明显延迟。

文档同步：

- `docs/dev/design/security.md`
- `docs/dev/design/database.md`
- `docs/dev/api/internal.md` 如增加管理端接口。

### P1-02：构建入口治理

涉及文件：

- `backend/build.gradle`
- `backend/settings.gradle`
- `backend/gradlew`
- `backend/gradlew.bat`
- `backend/gradle/`
- `README.md`
- `docs/dev/stack.md`
- `docs/dev/workflow/development.md`
- CI 配置（如有）。

实现选项：

| 选项 | 做法 | 推荐 |
|---|---|---|
| A | 删除 Gradle 相关文件，只保留 Maven | 推荐 |
| B | 完整同步 Gradle，使其与 Maven 等价 | 不推荐，维护双入口成本高 |

推荐实施：

- 删除或归档 Gradle Wrapper 和 `build.gradle/settings.gradle`。
- 文档统一使用 Maven：`mvn test`、`mvn package`。
- 如 CI 中仍有 Gradle 步骤，改为 Maven。

验证方式：

- `mvn test`。
- `mvn package -DskipTests`。
- 全仓搜索不再推荐 `gradlew` 作为后端构建入口。

文档同步：

- `README.md`
- `docs/dev/stack.md`
- `docs/dev/workflow/development.md`
- `docs/dev/workflow/deployment.md`

### P1-03：前端字典动态化补齐

涉及文件：

- `frontend/src/constants/units.ts`
- `frontend/src/views/ProductEdit.vue`
- `frontend-uniapp/src/pages-sub/basic/categories/index.vue`
- `frontend-uniapp/src/pages-sub/basic/customers/index.vue`
- `frontend-uniapp/src/pages-sub/basic/origins/index.vue`
- `frontend-uniapp/src/composables/useDict.ts`

实现要点：

- 移除 `UNIT_OPTIONS` 一次性常量用法，改为页面内：
  - `const unitOptions = computed(() => getDictOptions('unit'))`
  - 若需要兜底，放在 `getDictOptions` 内部或明确显示加载失败状态。
- uni-app 基础资料启停提示使用 `getDictValue('common_status', newStatus)`。
- 页面 `onMounted/onShow` 确认调用 `loadAllDicts()` 或对应分类加载方法。
- 保留协议值硬编码，例如请求参数中的 `ACTIVE`、`PENDING`，但禁止硬编码中文显示名。

验证方式：

- PC：修改/新增字典 `unit` 后，产品编辑页选项同步变化。
- uni-app：基础资料启停 toast 使用字典显示。
- `npm run build`。
- `npm run typecheck`。

文档同步：

- `docs/dev/coding-standards.md`
- `docs/dev/design/specifications.md`
- `docs/dev/design/ui.md`

### P2-01：`v-html` SVG 渲染收口

涉及文件：

- `frontend/src/views/ProductEdit.vue`

实现要点：

- 将 `formSections.icon` 从 SVG 字符串改为受控 key，例如 `basic`、`relation`、`extra`。
- 模板中用静态组件、条件渲染或 lucide 图标映射，不再使用 `v-html`。
- 如确实需要渲染富文本，必须引入白名单 sanitizer，并限制来源。

验证方式：

- 产品新建/编辑页 PC 侧边导航图标正常显示。
- 全仓 `rg -n "v-html|innerHTML"` 确认无新增风险点。
- `npm run build`。

文档同步：

- `docs/dev/coding-standards.md`
- `docs/dev/design/security.md`

### P2-02：环境文件与弱默认示例治理

涉及文件：

- `.gitignore`
- `.env.example`
- `.env.production`（本地忽略文件，不提交真实值）
- `backend/src/main/resources/application-dev.yml`
- `scripts/deploy.sh`
- `scripts/init-db.sh`
- 运维文档。

实现要点：

- 确认 `.env`、`.env.production` 不入库；当前 `git ls-files` 未跟踪，保持该状态。
- `.env.example` 中敏感值使用占位符，不放可直接复用的弱密码。
- 生产式 `.env.production` 本地文件不在普通工作区长期保存，推荐迁移到安全渠道或部署机权限受控目录。
- `scripts/deploy.sh` 不应默认写入弱 `DEFAULT_USER_PASSWORD`。
- `application-dev.yml` 中开发默认口令仅限本地，并在注释中提示不可用于共享环境。

验证方式：

- `git status --ignored` 确认真实环境文件仍被忽略。
- `rg` 扫描不再出现新的生产式明文密钥。
- 部署文档中的示例均为占位符或生成命令。

文档同步：

- `README.md`
- `docs/dev/workflow/deployment.md`
- `docs/ops/操作手册.md`
- `docs/ops/IDEA部署指南.md`

## 上线门禁与阶段验收

企业级管理系统的整改不能只以“代码合并”为完成标准，必须分阶段设置门禁。

| 阶段 | 门禁 | 必须证据 | 不通过处理 |
|---|---|---|---|
| 开发完成 | P0/P1/P2 代码自测完成，无审批流改动混入 | 单测、构建、类型检查、关键 diff 说明 | 不进入测试 |
| 测试环境 | 认证、配置、黑名单、跨端契约全部通过 | 测试记录、接口响应截图或日志、缺陷关闭记录 | 修复后重新验证 |
| 预发环境 | 使用接近生产的环境变量和部署方式验证 | 配置清单、启动日志、外部 API 开关验证、黑名单命中日志 | 不进入生产 |
| 生产发布 | 有回滚点、负责人、观测指标和变更窗口 | 发布单、回滚命令、监控面板或日志查询语句 | 延期发布 |
| 观测结束 | 24 小时内无新增 P0/P1 异常 | 观测记录、异常汇总、残余风险确认 | 进入复盘或补丁流程 |

### 合并前门禁

- P0 项必须全部完成并验证通过。
- 不允许引入新的硬编码中文字典显示名。
- 不允许新增未受控的 `v-html`、`innerHTML` 或动态 HTML 渲染点。
- 不允许提交真实 `.env`、密钥、数据库口令或生产式配置。
- 不允许继续保留会误导 CI/新人的旧构建入口，除非 README 和 CI 明确标注“不可用/废弃”且有删除计划。

### 发布前门禁

- 后端配置必须明确列出 `API_KEY_ENABLED`、`API_KEY_ENCRYPTION_KEY`、`JWT_SECRET`、`DB_PASSWORD`、`REDIS_PASSWORD` 的来源。
- IP 黑名单建议先以“观察模式”上线：记录命中但不拦截；确认无误后再切换为强制拦截。
- 认证相关变更发布前，应准备一组可回归账号：正常账号、禁用账号、锁定账号、无角色账号。
- uni-app 发布前，应完成至少一次登录、恢复会话、获取资料、改密失败/成功路径验证。

## 监控指标与告警

| 指标 | 建议来源 | 告警阈值 | 处置动作 |
|---|---|---|---|
| Refresh Token 续签失败数 | 后端认证日志 / 应用指标 | 发布后 1 小时内异常升高 | 检查用户状态校验、token 存量和前端刷新逻辑 |
| 禁用/锁定用户续签拒绝数 | 后端安全日志 | 出现即记录，不一定告警 | 确认是否为预期安全拦截 |
| 外部 API 401/403 数 | 外部 API 日志 | 持续升高或调用方集中失败 | 检查 API Key 开关、密钥配置和调用方配置 |
| IP 黑名单命中数 | 过滤器日志 / 安全事件表 | 短时间集中命中 | 复核来源 IP、账号行为和是否需要扩展封禁 |
| uni-app 认证 405/401 数 | 网关或后端访问日志 | 发布后出现 405 | 检查 HTTP 方法、路径和移动端版本 |
| 字典加载失败数 | 前端错误监控 / 后端字典接口日志 | 连续失败或影响核心页面 | 检查字典接口、缓存、权限和网络 |
| 前端运行时 XSS 风险点 | 代码扫描 / PR 检查 | 新增即阻断 | 要求改为受控组件或 sanitizer 白名单 |

若当前项目尚未接入集中监控，至少在发布单中提供可执行的日志查询命令和人工巡检时间点；企业级成熟阶段再接入 Prometheus、ELK、Grafana 或等效平台。

## 验收证据清单

每项整改完成后应保留以下证据，便于内部审计、交接和后续复盘：

- 代码证据：关键 diff、影响文件列表、是否涉及数据库或配置。
- 测试证据：`mvn test`、`npm run build`、`npm run typecheck` 输出摘要。
- 安全证据：禁用/锁定用户续签被拒绝、API Key 默认关闭、黑名单命中记录。
- 契约证据：PC 与 uni-app 调用路径、HTTP 方法、响应结构验证记录。
- 配置证据：生产环境变量清单，敏感值必须脱敏。
- 文档证据：README、API、部署、安全、编码规范同步记录。
- 发布证据：发布时间、发布人、回滚点、观测人、24 小时观测结论。

## 后续增强 Backlog（非审批流）

以下内容不阻塞本次整改，但建议进入企业级能力建设路线图：

| 优先级 | 增强项 | 价值 | 触发条件 |
|---|---|---|---|
| B1 | OpenAPI / 契约测试 | 自动发现前后端路径、方法、字段偏差 | API 继续扩展或多端接入增加 |
| B1 | CI 安全扫描 | 阻断密钥提交、危险 HTML、依赖漏洞 | 接入正式 CI/CD 后 |
| B1 | 统一配置中心或密钥管理 | 降低生产密钥散落和弱默认风险 | 多环境、多实例部署后 |
| B2 | SSO / MFA | 满足企业统一身份、离职禁用和高风险操作二次确认 | 明确企业身份源后 |
| B2 | 安全事件中心 | 统一记录登录失败、黑名单命中、API Key 拒绝等事件 | 需要审计报表或安全运营时 |
| B2 | 前端错误监控 | 发现字典加载失败、移动端契约失败、页面运行时错误 | 用户规模扩大或移动端正式上线 |
| B3 | 灰度发布和特性开关 | 降低认证、拦截、配置类变更的发布风险 | 生产环境并发和组织规模扩大后 |
| B3 | 权限模型治理 | 建立资源、动作、数据范围的统一模型 | 审批流和组织职责确认后单独设计 |

以上 Backlog 明确排除审批流具体实现，只保留企业级系统通用能力建设方向。

## 实施步骤

### 阶段 0：整改准备与冻结范围

- [ ] 确认本轮不包含审批流，相关问题记录为延期项，不在本分支混入实现。
- [ ] 指定项目、后端、前端、uni-app、测试、运维、文档责任人。
- [ ] 确认测试环境和预发环境可用，准备正常、禁用、锁定、无角色测试账号。
- [ ] 确认真实环境文件不提交，生产敏感配置只以脱敏形式进入发布单。
- [ ] 建立变更记录：风险项、负责人、预计完成时间、验证方式、回滚方式。

### 阶段 1：P0 运行风险收口

- [ ] 修复 Refresh Token 续签状态校验。
- [ ] 修复 uni-app 认证 HTTP 方法和 profile 响应解析。
- [ ] 删除或隐藏未实现的微信登录调用，除非另起功能方案。
- [ ] 统一外部 API 默认开关为关闭。
- [ ] 补齐 P0 单测和构建验证。

### 阶段 2：P1 安全与维护一致性

- [ ] 实现 IP 黑名单过滤器与缓存。
- [ ] 治理后端构建入口，优先删除 Gradle 旧入口。
- [ ] 完成 PC 与 uni-app 字典动态化整改。
- [ ] 补齐相关文档。

### 阶段 3：P2 安全债与运维治理

- [ ] 移除 `ProductEdit.vue` 的 `v-html`。
- [ ] 清理弱默认示例和部署脚本弱默认值。
- [ ] 对真实环境文件进行本地权限和分发渠道检查。
- [ ] 补充安全编码规范。

### 阶段 4：预发、发布与观测

- [ ] 在测试环境完成自动化验证和专项验证。
- [ ] 在预发环境使用接近生产的环境变量完成启动、登录、续签、黑名单、外部 API 开关验证。
- [ ] 发布前确认回滚点、回滚命令、发布负责人和观测负责人。
- [ ] 生产发布后观察 24 小时，重点关注认证失败、外部 API 拒绝、黑名单命中和 uni-app 认证异常。
- [ ] 观测结束后更新交付清单，将残余风险转入 Backlog 或单独计划。

## Verification

### 自动化验证

```bash
cd backend
mvn test

cd ../frontend
npm run build

cd ../frontend-uniapp
npm run typecheck
```

### 后端专项验证

- 禁用用户后，旧 refresh token 续签失败。
- 锁定用户后，旧 refresh token 续签失败。
- 正常用户 refresh token 续签成功。
- 无角色或角色异常用户 refresh token 续签失败。
- 未配置 `API_KEY_ENABLED` 时外部 API 默认关闭。
- `API_KEY_ENABLED=true` 且缺少加密 key 时启动失败。
- `API_KEY_ENABLED=false` 时外部 API 不要求密钥且不会加载无效密钥配置。
- 插入 active IP 黑名单后，对应 IP 请求被拦截。
- IP 黑名单观察模式下只记录命中，不影响正常请求。

### 前端专项验证

- uni-app 登录、恢复会话、获取个人资料成功。
- uni-app 不再调用不存在的 `/api/auth/wechat-login`，或后端已实现该接口。
- PC 产品编辑页单位选项随字典变化。
- uni-app 状态显示和 toast 不硬编码中文标签。
- 全仓 `rg -n "v-html|innerHTML"` 无新增高风险点。

### 企业级门禁验证

- 发布单中包含影响等级、负责人、变更窗口、回滚点和观测负责人。
- 预发环境启动日志中敏感配置来源明确，敏感值脱敏。
- 关键安全事件日志可检索：刷新拒绝、API Key 拒绝、IP 黑名单命中。
- 移动端和 PC 端各完成一轮冒烟回归。
- 新增或变更文档能让新人判断：用 Maven 构建后端、外部 API 默认关闭、字典显示不得硬编码。

### 文档检查

- API 变更同步 `docs/dev/api/auth.md`、`docs/dev/api/internal.md`、`docs/dev/design/api-design.md`。
- 配置和部署变更同步 `README.md`、`docs/dev/workflow/deployment.md`、`docs/ops/IDEA部署指南.md`。
- 字典规范同步 `docs/dev/coding-standards.md`、`docs/dev/design/specifications.md`。
- 数据库/安全拦截同步 `docs/dev/design/database.md`、`docs/dev/design/security.md`。

## 回滚策略

| 变更 | 回滚方式 | 回滚触发条件 |
|---|---|---|
| Refresh Token 校验 | 回退服务层校验提交；保留测试用于重新评估 | 正常用户大面积无法续签，且无法快速定位 |
| uni-app 契约修复 | 回退前端 API 封装；后端接口不变 | 新版本移动端核心认证流程不可用 |
| 外部 API 默认关闭 | 显式设置 `API_KEY_ENABLED=true` 可恢复开启 | 已确认外部调用方依赖默认开启且无法立即改配置 |
| IP 黑名单过滤器 | 配置开关禁用过滤器，或回退过滤器注册 | 误拦截内部网关、反向代理或大量正常用户 |
| 删除 Gradle 入口 | 如确需恢复，从 Git 历史恢复，但必须同步依赖 | 现有 CI 仍依赖 Gradle 且短期无法改造 |
| 字典动态化 | 回退页面改动；保留字典数据不影响后端 | 核心页面因字典接口异常无法加载 |
| 移除 `v-html` | 回退静态图标组件改动即可 | 图标渲染严重异常且影响核心表单使用 |

回滚不是失败处理的唯一手段。若问题影响面可控，优先使用配置开关、观察模式或小补丁；若涉及认证、拦截、外部 API 默认开关等 L2 变更，生产回滚必须由项目负责人和运维负责人共同确认。

## 交付清单

- [ ] 代码整改提交，且无审批流实现混入。
- [ ] 自动化验证结果：后端测试、PC 构建、uni-app 类型检查。
- [ ] 安全专项验证记录：禁用/锁定续签拒绝、API Key 默认关闭、黑名单命中。
- [ ] 跨端契约验证记录：PC、uni-app 登录、资料、改密、错误路径。
- [ ] 关键功能手工回归记录。
- [ ] 相关文档同步。
- [ ] 生产配置脱敏清单和运维变更说明。
- [ ] 回滚命令、回滚触发条件和负责人。
- [ ] 24 小时观测记录。
- [ ] 残余风险和后续 Backlog 归档。

## 评分复核

若按本方案完整执行并通过门禁，预计评分可由原先的 **7.2/10** 提升至 **9.2/10**。

| 维度 | 原主要短板 | 升级后状态 | 评分贡献 |
|---|---|---|---|
| 技术风险覆盖 | 已列出主要问题，但偏实现清单 | P0/P1/P2 有影响面、回滚和验证 | 高 |
| 企业治理 | 缺少责任矩阵和变更门禁 | 补齐角色、阶段门禁、发布观测 | 高 |
| 安全运营 | 缺少监控指标和告警口径 | 补齐认证、API Key、黑名单、XSS 观测项 | 高 |
| 交付审计 | 缺少证据留存要求 | 补齐代码、测试、配置、文档、发布证据 | 中高 |
| 长期演进 | 缺少企业级后续路线 | 补齐 SSO、配置中心、CI 安全扫描、契约测试等 Backlog | 中 |

剩余无法给到满分的原因：审批流仍处于业务未定状态，SSO/MFA、集中监控、配置中心、自动化契约测试等能力属于后续企业级建设，需要在本轮风险收口后分阶段落地。
