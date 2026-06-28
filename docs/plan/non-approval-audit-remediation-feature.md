# 非审批流代码审计整改方案

## Context

2026-06-23 对 PC 前端、uni-app 前端和后端进行整体审计后，发现若干与认证、配置、构建、字典规范和安全防护相关的偏差。本方案只覆盖审批流以外的整改项；审批流相关的对象级权限、待审批列表过滤等问题按用户决策暂缓，不纳入本轮计划。

本轮审计已完成以下验证：

| 验证项 | 结果 | 说明 |
|---|---|---|
| 后端测试 | 通过 | `mvn test`，143 tests，0 failures |
| PC 前端构建 | 通过 | `npm run build` |
| uni-app 类型检查 | 通过 | `npm run typecheck` |

这些问题大多不会在编译期暴露，主要属于运行契约、配置默认值、安全策略和长期维护一致性风险。

## 开发目标

本方案仅描述应用层整改：后端服务、前端调用、uni-app 调用、配置默认值、测试和项目文档同步。不描述人员分工或发布管理流程。

| 维度 | 目标 | 验收口径 |
|---|---|---|
| 认证安全 | 禁用、锁定、无角色用户不能通过 Refresh Token 继续续签 | 单测和接口验证覆盖正常、禁用、锁定、无角色、用户不存在分支 |
| 契约一致性 | PC、uni-app、后端 API 路径、方法、响应结构一致 | 登录、续签、退出、资料、改密调用不出现 405 或结构解析错误 |
| 配置默认值 | 外部 API 默认关闭，敏感配置无弱默认值 | `application.yml`、部署脚本和示例配置均使用安全默认或占位符 |
| 黑名单拦截 | `ip_blacklist` 表能参与请求拦截，代理头和缓存边界安全 | 命中、未命中、过期、伪造代理头、新增黑名单立即生效均有测试 |
| 前端安全 | 字典显示动态化，移除未受控 HTML 渲染点 | 单位、状态等显示来自字典；全仓无新增 `v-html` / `innerHTML` 风险点 |

## 范围与非目标

### 本次范围

- Refresh Token 续签时的用户状态校验。
- uni-app 认证接口与后端契约对齐。
- 外部 API Key 默认开关、密钥配置和文档一致性。
- IP 黑名单表的实际拦截闭环。
- Maven / Gradle 双构建入口统一。
- 前端字典动态化规范补齐。
- `v-html` 静态 SVG 渲染风险收口。
- 与以上整改相关的正式项目文档同步。

### 非目标

- 不处理审批流接口、审批列表、审批记录等权限模型，后续单独规划。
- 不重构整体权限体系。
- 不新增微信登录能力，除非产品确认小程序必须支持微信一键登录。
- 不改动业务表结构，除非 IP 黑名单拦截实现需要补最小索引或状态字段。
- 不进行生产密钥轮换操作；本方案只处理代码默认值和配置说明。

### 暂不实现项

| 项目 | 状态 | 原因 | 本方案处理方式 |
|---|---|---|---|
| 审批流设计与实现 | 延期 | 业务规则未最终确认 | 本方案不记录具体审批流漏洞和修复动作 |
| 生产密钥轮换 | 不在应用代码内执行 | 涉及真实凭据替换 | 本方案只处理默认值、占位符和配置校验 |
| 统一身份源 / SSO | 后续独立功能 | 当前系统已有本地认证 | 不阻塞本次认证安全补强 |
| 全量权限模型重构 | 后续独立功能 | 当前目标是关闭明确风险 | 仅处理本次风险项所需的最小权限和安全默认策略 |

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
| R-08 | P2 | 本地生产式环境文件和弱默认示例处理不足 | 工作区存在忽略文件 `.env.production`；文档和开发配置保留弱示例 | 本地泄露风险、误用弱密码风险 |
| R-09 | P1 | IP 黑名单可被伪造代理头绕过 | `IpAddressUtil.getClientIp` 无条件采信 `X-Forwarded-For` / `X-Real-IP`；黑名单 trusted sources 默认包含 loopback | 应用被直连时，攻击者可伪造 `X-Forwarded-For: 127.0.0.1` 绕过黑名单 |
| R-10 | P2 | 黑名单缓存没有感知 `expiresAt` | `CachedDecision` 只保存 `loadedAt`，命中缓存时不再校验记录过期时间 | 临近过期的黑名单可能在实际过期后继续拦截，最长接近缓存 TTL |
| R-11 | P2 | 未命中结果缓存导致新增黑名单延迟生效 | 未找到 active 记录时缓存 `notBlocked`，但新增/启停黑名单时没有统一清缓存链路 | 管理员手动新增黑名单后，同一 IP 可能在 TTL 内继续放行 |
| R-12 | P1 | 登录/续签 IP 限流仍可被伪造代理头绕过 | `RateLimiterAspect.getClientIp` 直接读取 `X-Forwarded-For` / `X-Real-IP`，未使用可信代理判断 | 攻击者可变换请求头绕过登录 5 次/分钟、Refresh Token 10 次/分钟限制 |
| R-13 | P1 | 外部 API Key IP 白名单在反向代理部署下可能误判 | `ApiKeyAuthenticationFilter` 使用默认 `IpAddressUtil.getClientIp(request)`，只取 `remoteAddr` | 经 Nginx/网关访问时可能只校验代理 IP，导致合法调用被拒或白名单被迫放宽到代理 IP |
| R-14 | P2 | 操作日志 IP 仍可被代理头伪造 | `OperationLogHelper` 自行读取多个代理头并写入 `operation_log.ip_address` | 操作日志来源 IP 不可信，和黑名单/安全事件中的 IP 解析结果不一致 |

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
- `docs/dev/workflow/deployment.md` 如涉及配置说明。

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

实现要点：

- 将 `api-key.enabled` 默认值统一为 `${API_KEY_ENABLED:false}`。
- 保持 `API_KEY_ENCRYPTION_KEY` 无默认值；只有开发 profile 可提供明确标注的开发兜底 key。
- 启用外部 API 时，缺少或格式错误的 `API_KEY_ENCRYPTION_KEY` 必须启动失败。
- 后台创建 API Key / 保存通知渠道密钥时继续调用 `requireValidEncryptionKey`。
- 文档统一表达：
  - 外部 API 签名入口默认关闭。
  - 创建 API Key 或保存密钥配置需要加密主密钥。
  - 非开发环境禁止使用开发示例 key。

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
  - 配置文件明确声明的可信来源。

验证方式：

- 单测或 MockMvc 覆盖命中、未命中、过期失效、白名单。
- 手工插入 active 黑名单 IP，验证请求被拒绝。
- 验证正常用户请求不增加明显延迟。

文档同步：

- `docs/dev/design/security.md`
- `docs/dev/design/database.md`
- `docs/dev/api/internal.md` 如增加管理端接口。

#### 变更后审计补强方案（2026-06-24）

本轮代码变更后的复核发现，IP 黑名单基础闭环已实现，但客户端 IP 可信边界和缓存时效性仍需补强。该补强属于 P1-01 的后续收口，不改变审批流范围。

##### A. 修复代理头伪造绕过

涉及文件：

- `backend/src/main/java/com/pricemanagement/util/IpAddressUtil.java`
- `backend/src/main/java/com/pricemanagement/config/properties/SecurityProperties.java`
- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/pricemanagement/service/IpBlacklistService.java`
- `backend/src/test/java/com/pricemanagement/config/IpBlacklistFilterTests.java`

修复设计：

- 将“可信代理来源”和“黑名单豁免来源”拆成两个概念：
  - `security.ip-blacklist.trusted-proxies`：允许采信 `X-Forwarded-For` / `X-Real-IP` 的反向代理 IP 或 CIDR。
  - `security.ip-blacklist.bypass-sources`：明确不参与黑名单拦截的来源，例如本机健康检查或内部可信来源。
- `IpAddressUtil.getClientIp` 增加重载方法：
  - 先读取 `request.getRemoteAddr()`。
  - 只有 `remoteAddr` 命中 `trusted-proxies` 时，才解析 `X-Forwarded-For` / `X-Real-IP`。
  - 否则忽略所有客户端自带代理头，直接使用 `remoteAddr`。
- 黑名单匹配只对解析后的真实客户端 IP 执行；`bypass-sources` 仅用于经过可信代理校验后的来源或明确可信的 `remoteAddr`。
- 默认配置不应把 loopback 当作可被请求头声明的可信客户端来源；loopback 只能作为 `remoteAddr` 或可信代理地址出现。

验收标准：

- 直连请求 `remoteAddr=203.0.113.10` 且 `X-Forwarded-For=127.0.0.1` 时，仍按 `203.0.113.10` 判定并拦截。
- 可信代理请求 `remoteAddr=10.0.0.2` 且 `X-Forwarded-For=203.0.113.10` 时，按 `203.0.113.10` 判定。
- 非可信代理请求即使带 `X-Real-IP`，也不采信该头。
- 单测覆盖直连伪造、可信代理、非可信代理、CIDR 匹配。

##### B. 修复黑名单过期缓存不一致

涉及文件：

- `backend/src/main/java/com/pricemanagement/service/IpBlacklistService.java`
- `backend/src/test/java/com/pricemanagement/config/IpBlacklistFilterTests.java`

修复设计：

- `CachedDecision` 增加 `expiresAt` 字段。
- `isFresh` 同时判断：
  - `loadedAt + cacheTtlSeconds` 未过期。
  - 若为 blocked decision 且记录存在 `expiresAt`，则 `now` 必须早于 `expiresAt`。
- 当缓存中的 blocked decision 已超过业务 `expiresAt` 时：
  - 不直接返回缓存。
  - 重新查库。
  - 若记录仍 active 但已过期，执行现有懒失效逻辑并放行。
- 对临近过期记录，缓存有效期应取 `min(cacheTtlSeconds, expiresAt - now)` 的语义，避免过期后继续拦截。

验收标准：

- 黑名单记录 `expiresAt=now+1s`，首次命中后等待超过 1 秒，再次请求应触发懒失效并放行。
- 未过期记录在 TTL 内仍可命中缓存并拦截。
- 过期失效后 `active=false`、`unbanAt`、`unbanReason` 正确写入。

##### C. 修复新增黑名单延迟生效

涉及文件：

- `backend/src/main/java/com/pricemanagement/service/IpBlacklistService.java`
- 后续如新增管理接口，则同步相关 controller/service。

修复设计：

- 将 negative decision 与 blocked decision 拆分 TTL：
  - blocked decision 使用 `cacheTtlSeconds`，但受 `expiresAt` 限制。
  - not blocked decision 默认不缓存，或使用 `negativeCacheTtlSeconds` 且默认 0。
- 若后续新增黑名单管理接口，新增、启用、禁用、删除黑名单时必须调用 `evict(ipAddress)`。
- 若短期仍通过数据库手工维护黑名单，文档必须明确：启用 not-blocked 缓存会造成延迟；推荐默认关闭 negative cache。

验收标准：

- 某 IP 第一次未命中后，立即插入 active 黑名单，再次请求应被拦截。
- 如显式启用 negative cache，则文档和配置中必须声明延迟窗口。
- 单测覆盖“先未命中、后新增 active 记录、再次命中拦截”。

#### 变更后审计补强方案（二）：统一客户端 IP 解析（2026-06-24）

本轮补强后，IP 黑名单已使用可信代理边界，但登录限流、外部 API Key 白名单、操作日志仍存在独立 IP 解析逻辑。为避免同一请求在不同模块得到不同客户端 IP，需要在应用层统一客户端 IP 解析入口。

##### A. 新增统一 `ClientIpResolver`

涉及文件：

- 新增 `backend/src/main/java/com/pricemanagement/service/ClientIpResolver.java`
- `backend/src/main/java/com/pricemanagement/util/IpAddressUtil.java`
- `backend/src/main/java/com/pricemanagement/config/properties/SecurityProperties.java`
- `backend/src/main/resources/application.yml`

修复设计：

- 新增 Spring Bean `ClientIpResolver`，封装可信代理解析规则：
  - 读取 `request.getRemoteAddr()`。
  - 只有 `remoteAddr` 命中通用配置 `security.client-ip.trusted-proxies` 时，才采信 `X-Forwarded-For` / `X-Real-IP`。
  - 其他模块不再直接读取代理头。
- 明确代理头解析顺序和回退规则：
  - 优先解析 `X-Forwarded-For` 的首个有效 IP。
  - `X-Forwarded-For` 不存在、为空、为 `unknown` 或首个值无效时，再解析 `X-Real-IP`。
  - `X-Real-IP` 不存在、为空、为 `unknown` 或格式无效时，回退 `remoteAddr`。
  - 多个 forwarded 值只取第一个有效客户端 IP，不使用后续代理链条值作为客户端 IP。
- 将可信代理配置从 IP 黑名单专用配置提升为通用安全配置：
  - `security.client-ip.forwarded-header-enabled`：是否允许解析代理头，默认 `true`。
  - `security.client-ip.trusted-proxies`：允许采信代理头的反向代理 IP/CIDR，默认仅 loopback。
  - `security.ip-blacklist.bypass-sources`：保留为黑名单专用豁免来源，不再承担代理头可信判断。
- 保留 `IpAddressUtil.matches` / `matchesAny` 作为纯工具方法。
- `IpAddressUtil.getClientIp(request)` 作为低层安全默认仅返回 `remoteAddr`，不再作为业务客户端 IP 获取入口。
- 所有业务模块获取客户端 IP 时必须注入并调用 `ClientIpResolver.resolve(request)`。

验收标准：

- 直连请求伪造 `X-Forwarded-For` 时，所有模块解析到的 IP 都是 `remoteAddr`。
- 可信代理请求携带 `X-Forwarded-For` 时，所有模块解析到的 IP 都是首个 forwarded IP。
- 可信代理请求的 `X-Forwarded-For` 首个值为空、`unknown` 或格式无效时，应回退到 `X-Real-IP`；若 `X-Real-IP` 也无效，则回退 `remoteAddr`。
- 单测覆盖空代理头、非可信代理、可信代理、CIDR 可信代理。
- 当 `security.client-ip.forwarded-header-enabled=false` 时，即使 `remoteAddr` 命中可信代理，也只返回 `remoteAddr`。
- 全仓扫描确认：除 `IpAddressUtil` / `ClientIpResolver` 外，业务代码不直接调用 `IpAddressUtil.getClientIp(request)` 获取业务客户端 IP。

##### B. 登录/续签限流改用统一 IP 解析

涉及文件：

- `backend/src/main/java/com/pricemanagement/config/RateLimiterAspect.java`
- `backend/src/main/java/com/pricemanagement/controller/AuthController.java`
- 新增或扩展 `backend/src/test/java/com/pricemanagement/config/RateLimiterAspectTests.java`

修复设计：

- `RateLimiterAspect` 注入 `ClientIpResolver`。
- `LimitType.IP` 的 key 使用 `clientIpResolver.resolve(request)`。
- 删除 `RateLimiterAspect` 内部手写读取 `X-Forwarded-For` / `X-Real-IP` 的逻辑。

验收标准：

- 登录请求 `remoteAddr=203.0.113.10` 且伪造 `X-Forwarded-For=1.1.1.1` 时，限流 key 使用 `203.0.113.10`。
- 可信代理 `remoteAddr=10.0.0.2` 转发 `X-Forwarded-For=203.0.113.10` 时，限流 key 使用 `203.0.113.10`。
- 登录和 refresh-token 的既有限流行为不变，只改变 IP 解析来源。

##### C. 外部 API Key IP 白名单改用统一 IP 解析

涉及文件：

- `backend/src/main/java/com/pricemanagement/config/ApiKeyAuthenticationFilter.java`
- 相关 API Key filter 单测。

修复设计：

- `ApiKeyAuthenticationFilter` 注入 `ClientIpResolver`。
- `assertIpAllowed` 与 `buildBaseLog` 都使用统一解析后的客户端 IP。
- API Key IP 白名单继续支持单 IP 和 CIDR，校验对象从 `remoteAddr` 修正为可信代理解析后的真实客户端 IP。

验收标准：

- 可信代理请求 `remoteAddr=10.0.0.2`、`X-Forwarded-For=203.0.113.10`、API Key 白名单为 `203.0.113.10` 时应通过。
- 非可信直连请求伪造 `X-Forwarded-For=203.0.113.10`，但 `remoteAddr` 不在白名单时应拒绝。
- API 调用日志中的 `ipAddress` 与白名单校验使用同一个解析结果。

##### D. 操作日志与登录历史改用统一 IP 解析

涉及文件：

- `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java`
- `backend/src/main/java/com/pricemanagement/service/LoginHistoryService.java`
- `backend/src/main/java/com/pricemanagement/service/RefreshTokenService.java`
- `backend/src/main/java/com/pricemanagement/controller/ApiKeyController.java`

修复设计：

- 对于 Spring 管理的 service/controller，直接注入 `ClientIpResolver`。
- `OperationLogHelper` 若当前是 Spring Bean，则注入 `ClientIpResolver`；若为静态/工具式调用，优先改为组件化调用，避免继续保留独立代理头解析逻辑。
- 登录历史、Refresh Token 绑定 IP、API Key 操作审计 IP、操作日志 IP 均使用同一个解析结果。

验收标准：

- 同一请求下，`security_event.source_ip`、`operation_log.ip_address`、登录历史 IP、Refresh Token IP、API Key 调用日志 IP 一致。
- 全仓扫描后，除 `IpAddressUtil` / `ClientIpResolver` 外，不再出现业务代码直接读取 `X-Forwarded-For` / `X-Real-IP`。

### P1-02：构建入口统一

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

### P2-02：环境文件与弱默认示例处理

涉及文件：

- `.gitignore`
- `.env.example`
- `.env.production`（本地忽略文件，不提交真实值）
- `backend/src/main/resources/application-dev.yml`
- `scripts/deploy.sh`
- `scripts/init-db.sh`
- 相关配置文档。

实现要点：

- 确认 `.env`、`.env.production` 不入库；当前 `git ls-files` 未跟踪，保持该状态。
- `.env.example` 中敏感值使用占位符，不放可直接复用的弱密码。
- `.env.production` 等真实环境文件不进入 Git，示例文件只保留占位符。
- `scripts/deploy.sh` 不应默认写入弱 `DEFAULT_USER_PASSWORD`。
- `application-dev.yml` 中开发默认口令仅限本地，并在注释中提示不可用于共享环境。

验证方式：

- `git status --ignored` 确认真实环境文件仍被忽略。
- `rg` 扫描不再出现新的生产式明文密钥。
- 配置文档中的示例均为占位符或生成命令。

文档同步：

- `README.md`
- `docs/dev/workflow/deployment.md`

## 开发验收原则

- P0 项必须全部完成并通过自动化验证。
- 不允许引入新的硬编码中文字典显示名。
- 不允许新增未受控的 `v-html`、`innerHTML` 或动态 HTML 渲染点。
- 不允许提交真实 `.env`、密钥、数据库口令或生产式配置。
- 后端只保留 Maven 作为真实构建入口，文档和脚本不再推荐旧 Gradle 入口。
- IP 黑名单补强项必须覆盖伪造代理头、`expiresAt` 缓存边界、not-blocked 缓存边界。

## 实施步骤

### 阶段 0：整改准备与冻结范围

- [x] 确认本轮不包含审批流，相关问题记录为延期项，不在本分支混入实现。
- [x] 确认本轮只在应用层落地：后端、PC 前端、uni-app、配置模板、测试和项目文档。
- [x] 确认真实环境文件不提交，示例配置只保留占位符。
- [x] 建立变更记录：风险项、影响文件、实现要点、验证方式。

### 阶段 1：P0 运行风险收口

- [x] 修复 Refresh Token 续签状态校验。
- [x] 修复 uni-app 认证 HTTP 方法和 profile 响应解析。
- [x] 删除或隐藏未实现的微信登录调用，除非另起功能方案。
- [x] 统一外部 API 默认开关为关闭。
- [x] 补齐 P0 单测和构建验证。

### 阶段 2：P1 安全与维护一致性

- [x] 实现 IP 黑名单过滤器与缓存。
- [x] 统一后端构建入口，删除 Gradle 旧入口。
- [x] 完成 PC 与 uni-app 字典动态化整改。
- [x] 补齐相关文档。

### 阶段 2.5：变更后审计补强

- [x] 修复 `X-Forwarded-For` / `X-Real-IP` 无条件采信导致的 IP 黑名单绕过风险。
- [x] 修复 blocked cache 不感知 `expiresAt` 导致的过期后继续拦截风险。
- [x] 修复 not-blocked cache 导致的新增黑名单延迟生效风险。
- [x] 补齐上述三类边界条件单测。
- [x] 更新安全、部署和配置文档中的代理头、黑名单缓存说明。

### 阶段 2.6：统一客户端 IP 解析补强

- [x] 新增 `ClientIpResolver`，统一可信代理头解析规则。
- [x] 将可信代理配置提升为 `security.client-ip.forwarded-header-enabled` 与 `security.client-ip.trusted-proxies`。
- [x] `RateLimiterAspect` 改用 `ClientIpResolver`，修复登录/续签限流绕过风险。
- [x] `ApiKeyAuthenticationFilter` 改用 `ClientIpResolver`，修复反向代理下 API Key IP 白名单误判。
- [x] `OperationLogHelper`、`LoginHistoryService`、`RefreshTokenService`、`ApiKeyController` 改用统一 IP 解析。
- [x] 补齐限流、API Key 白名单、操作日志/登录历史相关单测。
- [x] 全仓扫描确认业务代码不再直接读取 `X-Forwarded-For` / `X-Real-IP`，也不直接调用旧 `IpAddressUtil.getClientIp(request)` 获取业务客户端 IP。

### 阶段 3：P2 安全债与配置处理

- [x] 移除 `ProductEdit.vue` 的 `v-html`。
- [x] 清理弱默认示例和部署脚本弱默认值。
- [x] 确认真实环境文件不进入 Git 跟踪。
- [x] 补充安全编码规范。

### 阶段 4：应用层验证

- [x] 完成 `mvn test`、PC 前端构建、uni-app 类型检查。
- [x] 完成关键代码扫描：`v-html`、`innerHTML`、`UNIT_OPTIONS`、`wechat-login`、旧 Gradle 入口。
- [x] 完成阶段 2.5 的 IP 黑名单补强后重新运行后端测试。
- [x] 完成阶段 2.5 的代理头与缓存边界专项单测。
- [x] 完成阶段 2.6 的统一客户端 IP 解析补强后重新运行后端测试。

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
- 直连请求伪造 `X-Forwarded-For: 127.0.0.1` 不应绕过黑名单。
- 只有 `remoteAddr` 命中可信代理配置时，才采信 `X-Forwarded-For` / `X-Real-IP`。
- 黑名单 `expiresAt` 到期后，缓存不得继续返回 blocked decision。
- 某 IP 先未命中、随后新增 active 黑名单后，再次请求应立即被拦截。
- 登录和 refresh-token 的 IP 限流必须使用统一客户端 IP 解析结果。
- 外部 API Key IP 白名单必须在可信代理场景下校验真实客户端 IP，在非可信代理场景下忽略伪造代理头。
- 操作日志、登录历史、Refresh Token IP、API Key 调用日志中的 IP 应与统一解析结果一致。
- `security.client-ip.forwarded-header-enabled=false` 时，所有模块都忽略 `X-Forwarded-For` / `X-Real-IP`。

### 前端专项验证

- uni-app 登录、恢复会话、获取个人资料成功。
- uni-app 不再调用不存在的 `/api/auth/wechat-login`，或后端已实现该接口。
- PC 产品编辑页单位选项随字典变化。
- uni-app 状态显示和 toast 不硬编码中文标签。
- 全仓 `rg -n "v-html|innerHTML"` 无新增高风险点。

### 应用层验收验证

- 配置默认值符合预期：外部 API 默认关闭，IP 黑名单默认启用，观察模式可通过配置切换。
- 关键安全事件能在应用日志或 `security_event` 表中体现：刷新拒绝、API Key 拒绝、IP 黑名单命中。
- 移动端和 PC 端各完成一轮登录、资料、改密、产品编辑页冒烟回归。
- 新增或变更文档能让开发者判断：用 Maven 构建后端、外部 API 默认关闭、字典显示不得硬编码。
- 全仓扫描后，除统一 IP 解析工具外，业务代码不直接读取 `X-Forwarded-For` / `X-Real-IP`，也不直接调用旧 `IpAddressUtil.getClientIp(request)` 获取业务客户端 IP。

### 文档检查

- API 变更同步 `docs/dev/api/auth.md`、`docs/dev/api/internal.md`、`docs/dev/design/api-design.md`。
- 配置和部署变更同步 `README.md`、`docs/dev/workflow/deployment.md`。
- 字典规范同步 `docs/dev/coding-standards.md`、`docs/dev/design/specifications.md`。
- 数据库/安全拦截同步 `docs/dev/design/database.md`、`docs/dev/design/security.md`。

## 开发完成清单

- [x] 代码整改无审批流实现混入。
- [x] 自动化验证完成：后端测试、PC 构建、uni-app 类型检查。
- [x] 安全专项验证覆盖：禁用/锁定续签拒绝、API Key 默认关闭、黑名单命中。
- [x] 跨端契约验证覆盖：PC、uni-app 登录、资料、改密、错误路径。
- [x] 相关文档同步。
- [x] 残余技术风险已记录到 R-09、R-10、R-11。
- [x] 变更后审计补强项完成：可信代理头校验、缓存过期校验、negative cache 处理。
- [x] 统一客户端 IP 解析补强项完成：通用 `security.client-ip.*` 配置、限流、API Key 白名单、操作日志、登录历史、Refresh Token、API Key 操作审计均使用统一解析。
