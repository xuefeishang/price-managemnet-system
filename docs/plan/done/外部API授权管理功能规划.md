# 外部 API 授权管理功能执行方案（两阶段 9.5+）

## Context

当前系统主要通过内部用户 JWT 调用 `/api/**` 接口。外部系统对接时如果继续复用用户账号，会产生以下风险：

- 无法区分内部用户操作与外部系统调用。
- 外部系统获得用户 Token 后权限过大，无法限制到具体接口。
- 凭证与用户账号耦合，无法独立启停、过期、轮换。
- 缺少请求签名、Nonce 防重放、IP 白名单、限流和独立审计。
- 缺少面向外部系统的调用统计、异常追踪和对接文档。

本方案将新增独立的 API Key + HMAC 签名认证机制。内部用户仍使用现有 JWT；外部系统只能调用独立隔离的 `/api/external/v1/**` 接口，不能直接调用现有内部 `/api/**` 业务接口。实施分为两个阶段：

- **阶段一：安全闭环上线**。交付可生产使用的 API Key 管理、签名认证、权限、限流、日志和基础页面。
- **阶段二：企业级增强**。在阶段一安全闭环和“可复制调用示例”增强基础上，交付密钥轮换、监控仪表盘、告警治理、调试控制台、示例资产治理、敏感操作审批和性能治理。

## 总体判断

该方案作为两阶段执行后可达到 9.5+，但评分成立的前提是严格执行以下修正：

- HMAC 验签必须使用服务端可恢复或可派生的签名密钥，不能只保存 BCrypt 哈希。
- 外部认证必须使用独立 `SecurityFilterChain`，仅匹配 `/api/external/**`，不进入内部 JWT 安全链路。
- 外部接口必须由 `ExternalApiController` 暴露，内部现有 Controller 和 `@PreAuthorize` 不做第一阶段改造。
- 数据库迁移必须匹配当前项目表结构，例如 `menu_item` 使用 `name` 字段而不是 `title`。
- 接口权限不能只放在字典里，必须有可执行的 `method + /api/external/v1/** path_pattern -> permission_code` 映射。
- 签名规范必须明确 query、body、编码、排序和空值规则，避免不同语言 SDK 算法不一致。
- 签名过滤器读取请求体时必须使用可重复读取的 request wrapper，否则 Controller 后续可能拿不到 body。

## Goals

1. 外部系统通过独立 App ID / App Secret 认证，不依赖内部用户账号。
2. 支持按外部接口和 HTTP 方法授权，权限粒度可落到 `GET /api/external/v1/products`、`POST /api/external/v1/products/{id}/prices`。
3. 支持 IP 白名单、分钟限流、日限额、Nonce 防重放和调用日志。
4. Secret 原文仅展示一次，数据库不保存明文 Secret。
5. 外部认证和现有 JWT 认证并存，互不破坏。
6. 管理端可创建、查看、编辑、启用、禁用、吊销 API Key。
7. 所有编码显示名称使用字典服务，前端禁止硬编码中文标签。
8. 阶段二支持密钥轮换、监控告警、调试控制台、示例资产治理和敏感操作审批。

## Non-Goals

- 不替代现有 JWT 登录体系。
- 不在本次引入 OAuth2/OIDC 或独立 API 网关。
- 不改变已有业务接口的响应结构。
- 阶段一不实现复杂 SLA、租户隔离、字段级权限或 API 网关插件。

## 架构决策

| 决策 | 方案 | 原因 |
|------|------|------|
| 认证方式 | JWT 与 API Key 双认证并存 | 内部用户和外部系统身份边界清晰 |
| 接口路径 | 外部系统只允许访问 `/api/external/v1/**` | 与现有内部 `/api/**` 功能物理隔离，降低回归风险 |
| Controller 边界 | 新增 `ExternalApiController`，内部调用现有 Service | 不改现有业务 Controller，不影响当前前端页面 |
| 安全链路 | 新增独立 `externalApiSecurityFilterChain`，仅匹配 `/api/external/**` | API Key 过滤器不参与内部 JWT 请求 |
| 方法级授权 | 外部 Controller 使用 `hasAuthority('API_xxx')` 或在 Controller 内调用权限服务 | 不改现有 `@PreAuthorize("hasRole...")` |
| 混合认证 | `/api/external/**` 禁止携带 `Authorization`；内部 `/api/**` 忽略 API Key 头并按需拒绝 | 避免身份歧义和审计归属不清 |
| Secret 存储 | `app_secret_cipher` 加密存储 + `app_secret_fingerprint` 校验标识 | HMAC 需要服务端签名密钥；仅 BCrypt 无法验签 |
| 密钥加密 | AES-GCM，主密钥来自环境变量 `API_KEY_ENCRYPTION_KEY`，并记录密钥版本 | 数据库泄露时避免 Secret 明文暴露，后续可平滑轮换主密钥 |
| 权限来源 | `sys_external_api_endpoint` 维护接口映射，`sys_api_key_permission` 维护授权 | 字典负责显示，授权表负责决策 |
| 限流 | Redis 原子计数限流，Redis 故障降级为单实例内存限流 | 分布式优先，同时保持服务可用 |
| 请求体处理 | 使用 `ContentCachingRequestWrapper` 或自定义 cached request wrapper | 签名计算读取 body 后，业务 Controller 仍可正常读取请求体 |
| 调用日志 | 阶段一同步记录最小调用日志并配置失败采样、字段截断和保留周期；阶段二再做异步批量落库 | 先保证安全审计闭环，再优化性能 |

## 两阶段交付边界

### 阶段一：安全闭环上线

目标：外部系统可以安全调用被授权的独立外部接口，管理员可以管理 API Key，系统可以审计每次调用，并且不改动现有内部业务接口行为。

阶段一必须包含：

- 数据库表、字典、菜单、数据字典文档。
- API Key 管理 CRUD。
- Secret 生成、一次性展示、加密存储、吊销。
- HMAC-SHA256 签名验证。
- Timestamp ±5 分钟窗口校验。
- Redis / 内存 Nonce 防重放。
- IP 白名单。
- 分钟限流和日限额。
- `method + /api/external/v1/** path_pattern` 接口权限校验。
- 独立外部 API SecurityFilterChain。
- 独立 External Controller，不改造现有业务 Controller。
- 签名过滤器请求体缓存包装。
- API 加密主密钥启动校验与版本字段。
- 调用日志和管理操作日志。
- 调用日志保留周期和认证失败日志限速。
- 签名算法测试向量。
- 管理端密钥列表、详情、创建/编辑弹窗、调用日志页。
- 单元测试、集成测试、前后端类型一致性检查。
- README、开发指南、项目设计文档、API 调用手册、UI 说明、完成总结、数据字典同步更新。

阶段一不做：

- 密钥平滑轮换。
- 监控仪表盘。
- 告警通知。
- 在线调试控制台保存凭证。
- SDK 包发布。
- 审批流接入。
- multipart 文件上传、导入导出类接口外部开放。
- 现有内部 Controller 的 `@PreAuthorize` 大规模改造。

### 阶段二：企业级增强

目标：完善企业级运维治理、开发者体验和安全生命周期管理。

阶段二基于阶段一及后续增强继续推进。已由 `docs/plan/api-key-copyable-examples-feature.md` 完成或规划的一次性可运行示例、端点结构化 schema、Node.js / Java 25 / Postman / PowerShell / curl 代码模板，不再作为阶段二的重复建设项；阶段二只做资产化、下载、版本治理和调试台复用。

阶段二包含：

- 密钥轮换：新旧密钥过渡期、旧密钥自动降级为 `DEPRECATED`，确认无调用后吊销。
- 监控仪表盘：调用量、成功率、响应时间、TOP 接口、TOP 调用方、异常趋势和服务开关状态。
- 告警配置：认证失败、限流触发、成功率下降、响应超时、密钥过期、服务暂停超时。
- 通知渠道：站内通知、Webhook，并复用现有钉钉/企业微信告警能力；邮件按现有告警基础能力评估后接入。
- 调试控制台：复用前端签名模板，本地输入 Secret 参与签名，不从服务端读取或保存 rawSecret。
- 示例资产治理：把阶段一增强中的可复制示例沉淀为可下载示例包、版本化测试向量和端点契约检查，不发布重型 SDK 包。
- 敏感操作审批：生产环境密钥创建、轮换、吊销、权限扩大、白名单放宽、延长过期时间接入现有审批模块。
- 日志归档与清理策略：按月归档、保留期清理、冷热数据分层查询。
- 性能治理与压测报告：签名验签、日志写入、统计查询、调试台调用链路均需有压力边界。

## 阶段一详细设计

### 1. 数据库设计

新增迁移文件：

```text
backend/src/main/resources/db/migration/V17__external_api_auth_phase1.sql
```

阶段一新增表：

#### sys_api_key

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(100) | 密钥名称 |
| app_id | VARCHAR(64) | 应用 ID，唯一，格式 `app_` + 随机值 |
| app_secret_cipher | TEXT | AES-GCM 加密后的 Secret |
| app_secret_key_version | VARCHAR(20) | 加密主密钥版本，默认 `v1` |
| app_secret_fingerprint | VARCHAR(64) | Secret SHA-256 指纹，用于审计和排查，不用于验签 |
| description | VARCHAR(500) | 描述 |
| status | VARCHAR(20) | 状态，字典 `api_key_status` |
| environment | VARCHAR(20) | 环境，字典 `api_key_environment` |
| expire_time | DATETIME | 过期时间，NULL 表示永不过期 |
| ip_whitelist | TEXT | JSON 数组，支持单 IP 和 CIDR |
| rate_limit_per_minute | INT | 每分钟请求上限 |
| daily_limit | INT | 每日请求上限 |
| created_by | BIGINT | 创建人 ID |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |
| last_used_time | DATETIME | 最后使用时间 |
| version | BIGINT | 乐观锁 |

索引：

- `uk_api_key_app_id(app_id)`
- `idx_api_key_status(status)`
- `idx_api_key_environment(environment)`
- `idx_api_key_expire_time(expire_time)`

#### sys_api_key_permission

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| api_key_id | BIGINT | API Key ID |
| permission_code | VARCHAR(100) | 权限编码 |
| created_time | DATETIME | 创建时间 |

约束：

- `uk_api_key_permission(api_key_id, permission_code)`
- 外键 `api_key_id -> sys_api_key.id ON DELETE CASCADE`

#### sys_external_api_endpoint

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| permission_code | VARCHAR(100) | 权限编码 |
| method | VARCHAR(10) | HTTP 方法 |
| path_pattern | VARCHAR(200) | Ant 风格路径，如 `/api/external/v1/products/**` |
| description | VARCHAR(200) | 接口说明 |
| status | VARCHAR(20) | ACTIVE / INACTIVE |
| sort_order | INT | 排序 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

说明：

- 授权决策以本表为准。
- 字典 `api_permission` 仅用于显示名称和下拉标签。
- 若一个请求匹配多个规则，选择路径更具体的规则。

#### sys_api_call_log

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| api_key_id | BIGINT | API Key ID，认证失败时可为空 |
| app_id | VARCHAR(64) | 请求头中的 App ID |
| endpoint | VARCHAR(200) | 请求路径，不含 query |
| query_string | VARCHAR(1000) | 原始 query，超长截断 |
| method | VARCHAR(10) | HTTP 方法 |
| permission_code | VARCHAR(100) | 匹配到的权限编码 |
| status_code | INT | 响应状态码 |
| response_time | INT | 响应耗时 ms |
| ip_address | VARCHAR(50) | 客户端 IP |
| request_time | DATETIME | 请求时间 |
| request_body_hash | VARCHAR(64) | 原始请求体 SHA-256 |
| nonce | VARCHAR(64) | Nonce，日志可脱敏存储 |
| auth_result | VARCHAR(30) | SUCCESS / INVALID_SIGNATURE / REPLAY / RATE_LIMITED / FORBIDDEN 等 |
| error_message | VARCHAR(500) | 错误摘要，不记录敏感数据 |
| created_time | DATETIME | 创建时间 |

索引：

- `idx_api_call_api_key_time(api_key_id, request_time)`
- `idx_api_call_app_time(app_id, request_time)`
- `idx_api_call_status(status_code)`
- `idx_api_call_auth_result(auth_result)`
- `idx_api_call_created_time(created_time)`

#### sys_api_key_operation_log

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| api_key_id | BIGINT | API Key ID |
| operation | VARCHAR(50) | 字典 `api_key_operation` |
| operator_id | BIGINT | 操作人 ID |
| operator_ip | VARCHAR(50) | 操作人 IP |
| detail | TEXT | 操作详情 JSON，敏感字段脱敏 |
| created_time | DATETIME | 创建时间 |

### 2. 字典设计

新增字典分类：

| 分类 | 说明 | 阶段 |
|------|------|------|
| api_key_status | 密钥状态 | 阶段一 |
| api_key_environment | 密钥环境 | 阶段一 |
| api_key_operation | 管理操作类型 | 阶段一 |
| api_auth_result | 认证结果 | 阶段一 |
| api_permission | API 权限显示名 | 阶段一 |
| api_alert_type | 告警类型 | 阶段二 |
| api_notify_channel | 通知渠道 | 阶段二 |

阶段一字典项：

| 分类 | 字典键 | 显示值 |
|------|--------|--------|
| api_key_status | ACTIVE | 生效中 |
| api_key_status | DISABLED | 已停用 |
| api_key_status | EXPIRED | 已过期 |
| api_key_status | REVOKED | 已吊销 |
| api_key_environment | PRODUCTION | 生产 |
| api_key_environment | TESTING | 测试 |
| api_key_environment | SANDBOX | 沙箱 |
| api_key_operation | CREATE | 创建 |
| api_key_operation | UPDATE | 更新 |
| api_key_operation | ENABLE | 启用 |
| api_key_operation | DISABLE | 停用 |
| api_key_operation | REVOKE | 吊销 |
| api_auth_result | SUCCESS | 成功 |
| api_auth_result | MISSING_HEADER | 缺少认证头 |
| api_auth_result | INVALID_APP_ID | 应用不存在 |
| api_auth_result | INVALID_SIGNATURE | 签名错误 |
| api_auth_result | EXPIRED_TIMESTAMP | 时间戳过期 |
| api_auth_result | REPLAY | 重放请求 |
| api_auth_result | IP_DENIED | IP 受限 |
| api_auth_result | RATE_LIMITED | 触发限流 |
| api_auth_result | FORBIDDEN | 权限不足 |

前端必须在 `frontend/src/composables/useDict.ts` 的 `CATEGORY_LABELS` 增加上述分类。页面中状态、环境、操作、认证结果、权限名称均使用 `getDictValue()` 或 `getDictOptions()`。

### 3. 外部 API 接口与权限映射

阶段一初始化以下最小权限集：

| 权限编码 | 方法 | 路径 | 说明 |
|----------|------|------|------|
| product:read | GET | `/api/external/v1/products`、`/api/external/v1/products/*` | 查看产品 |
| product:write | POST/PUT | `/api/external/v1/products`、`/api/external/v1/products/*` | 新增或编辑产品 |
| product:delete | DELETE | `/api/external/v1/products/*` | 删除产品 |
| price:read | GET | `/api/external/v1/products/*/price-history`、`/api/external/v1/products/*/current-price`、`/api/external/v1/products/*/price-by-date`、`/api/external/v1/products/*/price-trend`、`/api/external/v1/prices/by-date`、`/api/external/v1/prices/by-date-with-stats` | 查看价格 |
| price:write | POST/PUT | `/api/external/v1/products/*/prices`、`/api/external/v1/prices/*` | 维护价格 |
| price-query:read | GET | `/api/external/v1/price-query` | 价格查询 |
| price-query:export | GET | `/api/external/v1/price-query/export` | 价格导出 |
| category:read | GET | `/api/external/v1/categories/**` | 查看分类 |
| origin:read | GET | `/api/external/v1/origins/**` | 查看产地 |
| customer:read | GET | `/api/external/v1/customers/**` | 查看客户 |
| dict:read | GET | `/api/external/v1/dict/**` | 查看字典 |
| home:read | GET | `/api/external/v1/home/**` | 首页数据 |

说明：

- `*:read` 权限只覆盖 GET。
- 写权限不隐含删除权限。
- 父级通配权限如 `product:*` 可在服务层展开为同前缀权限，但数据库仍保存明确授权项，便于审计。
- 匹配优先级为：HTTP 方法完全匹配 -> path_pattern 更具体者优先 -> `sort_order` 更小者优先。
- 未匹配到 `sys_external_api_endpoint` 的接口默认拒绝外部 API 调用。
- 阶段一只开放 `/api/external/v1/**`，所有现有内部 `/api/**` 业务接口均不接受 API Key 作为认证方式。
- 阶段一不开放导入导出、multipart 文件上传、认证、用户、角色、菜单、审批配置、样式配置等管理类能力。
- 外部接口 DTO 可以比内部接口更窄，避免把内部字段、管理字段或敏感字段暴露给外部系统。
- `POST /api/external/v1/prices/cleanup-duplicates` 阶段一不提供；如确需开放，应单独规划并在阶段二纳入审批。
- 外部写入口会清理请求体中的 `id`、`version`、`createdTime`、`updatedTime` 等系统字段，避免外部系统覆盖服务端生成字段。
- 阶段一外部产品/价格写入复用现有审批流；如审批流启用，审批申请人使用系统外部申请人占位 `0`，真实调用来源通过 `sys_api_call_log.app_id` 追溯。外部应用与内部用户/部门映射进入阶段二。

### 4. 签名协议

请求头：

```http
X-App-Id: app_xxxxxxxxxxxxxxxx
X-Timestamp: 1779990000
X-Nonce: 16-64位随机字符串
X-Signature: hex(hmac_sha256(appSecret, canonicalString))
```

禁止请求头：

```http
X-App-Secret
```

任何外部请求都不得传输 Secret 原文。

Canonical String：

```text
HTTP_METHOD_UPPERCASE
canonicalPath
canonicalQuery
timestamp
nonce
bodySha256Hex
```

规则：

- 使用 `\n` 连接各行。
- `HTTP_METHOD_UPPERCASE` 为大写方法名。
- `canonicalPath` 使用原始 path，不包含域名，不包含 query。
- `canonicalQuery` 将 query 参数按 key 升序、再按 value 升序排列后 URL 编码；无 query 时为空字符串。
- `bodySha256Hex` 对原始请求体字节计算 SHA-256；GET/DELETE 无 body 时使用空字符串的 SHA-256。
- 不对 body 做脱敏后再签名。日志脱敏与签名完整性是两件事。
- 服务端使用常量时间比较签名。
- Timestamp 允许与服务器时间相差不超过 300 秒。
- Nonce 使用 Redis `SET NX EX 600`；Redis 不可用时降级到本机内存缓存，降级状态写入日志。

请求体缓存要求：

- `ApiKeyAuthenticationFilter` 必须在读取 body 前将请求包装为 `ContentCachingRequestWrapper` 或自定义 cached request wrapper。
- 对 `application/json`、`application/x-www-form-urlencoded`、空 body 请求计算 body hash。
- 阶段一不支持 multipart 外部签名调用；匹配到 multipart 请求时直接返回 415 或 403，并记录 `UNSUPPORTED_CONTENT_TYPE`。
- 过滤器向后续 filter chain 传递包装后的 request，避免 Controller `@RequestBody` 为空。

示例：

```text
GET
/api/external/v1/products
page=0&size=20
1779990000
nonce_abc123
e3b0c44298fc1c149afbf4c8996fb924...
```

测试向量：

| 项目 | 值 |
|------|-----|
| appSecret | `sec_test_1234567890` |
| method | `GET` |
| path | `/api/external/v1/products` |
| query | `size=20&page=0` |
| canonicalQuery | `page=0&size=20` |
| timestamp | `1779990000` |
| nonce | `nonce_test_001` |
| body | 空字符串 |
| bodySha256Hex | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |

上线前必须用 Java 后端工具类和 JavaScript 示例对同一测试向量生成一致签名，并把最终 `X-Signature` 写入 `docs/dev/API调用手册.md`。

### 5. Secret 生命周期

创建密钥：

1. 服务端生成 `appId` 和 `appSecret`。
2. `appSecret` 原文只在创建成功响应中返回一次。
3. 服务端使用 `API_KEY_ENCRYPTION_KEY` 进行 AES-GCM 加密，存入 `app_secret_cipher`。
4. 服务端记录当前主密钥版本到 `app_secret_key_version`。
5. 服务端计算 `SHA-256(appSecret)`，存入 `app_secret_fingerprint`，用于排查和确认，不用于验签。
6. 管理操作日志记录创建行为，但不记录 Secret 原文。

主密钥配置：

- `API_KEY_ENCRYPTION_KEY` 使用 Base64 编码的 256-bit key。
- `API_KEY_ENCRYPTION_KEY_VERSION` 默认为 `v1`。
- 生产环境缺失或格式不合法时应用必须启动失败。
- 开发环境允许使用示例 key，但必须在 `application.yml.example` 中标注不可用于生产。
- 阶段二轮换主密钥时，通过 `app_secret_key_version` 识别使用哪个旧 key 解密。

读取密钥：

- 列表和详情永远不返回 Secret 原文。
- 只返回 fingerprint 前 8 位、创建时间、最后使用时间等非敏感信息。

吊销密钥：

- 状态改为 `REVOKED`。
- 清除 Redis 缓存。
- 吊销后不可恢复，只能新建。

阶段二轮换：

- 新建 successor key。
- 旧 key 进入 `TRANSITION`，新旧同时有效一段时间。
- 过渡期结束旧 key 自动进入 `DEPRECATED` 或 `REVOKED`。

### 6. 后端模块

新增文件结构：

```text
backend/src/main/java/com/pricemanagement/
├── config/
│   └── ApiKeyAuthenticationFilter.java
├── config/properties/
│   └── ApiKeyProperties.java
├── controller/
│   ├── ApiKeyController.java
│   ├── ApiCallLogController.java
│   └── external/
│       ├── ExternalProductController.java
│       ├── ExternalPriceController.java
│       ├── ExternalPriceQueryController.java
│       ├── ExternalBasicDataController.java
│       └── ExternalHomeController.java
├── dto/
│   ├── ApiKeyCreateRequest.java
│   ├── ApiKeyCreateResponse.java
│   ├── ApiKeyDTO.java
│   ├── ApiKeyUpdateRequest.java
│   ├── ApiCallLogDTO.java
│   └── ExternalApiEndpointDTO.java
├── entity/
│   ├── ApiKey.java
│   ├── ApiKeyPermission.java
│   ├── ExternalApiEndpoint.java
│   ├── ApiCallLog.java
│   └── ApiKeyOperationLog.java
├── repository/
│   ├── ApiKeyRepository.java
│   ├── ApiKeyPermissionRepository.java
│   ├── ExternalApiEndpointRepository.java
│   ├── ApiCallLogRepository.java
│   └── ApiKeyOperationLogRepository.java
├── service/
│   ├── ApiKeyService.java
│   ├── ApiKeySecretService.java
│   ├── ApiSignatureService.java
│   ├── ExternalApiPermissionService.java
│   ├── ApiRateLimitService.java
│   ├── ApiNonceService.java
│   └── ApiCallLogService.java
└── util/
    ├── SignatureUtil.java
    └── IpWhitelistMatcher.java
```

外部 Controller 原则：

- `ExternalApiController` 只暴露 `/api/external/v1/**`。
- Controller 层做外部 DTO 入参校验和响应裁剪。
- 业务逻辑复用现有 Service，不复制核心业务规则。
- 不返回内部管理字段，例如内部用户信息、操作日志详情、审批配置、菜单配置。
- 外部接口字段和内部接口字段允许不完全一致，以外部契约稳定为优先。

### 7. 独立 Spring Security 链路

`ApiKeyAuthenticationFilter` 行为：

1. 只处理 `/api/external/**` 请求。
2. 若外部请求缺少 `X-App-Id`，返回 401。
3. 若外部请求携带 `Authorization`，返回 400 或 401，提示外部接口不接受 JWT。
4. 内部 `/api/**` 请求不进入此过滤器。
5. 验签、限流、权限全部通过后，创建 `UsernamePasswordAuthenticationToken`：
   - principal：`external:{appId}`
   - details：`apiKeyId`
   - authority：`ROLE_EXTERNAL_API` 和授权的 `API_{permission_code}`
6. 写入 `SecurityContextHolder`。
7. 下游 `ExternalApiController` 通过 `hasAuthority('API_xxx')` 或权限服务完成方法级授权。

新增独立安全链：

```java
@Bean
@Order(1)
SecurityFilterChain externalApiSecurityFilterChain(HttpSecurity http,
                                                   ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
    return http
            .securityMatcher("/api/external/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
}

@Bean
@Order(2)
SecurityFilterChain internalSecurityFilterChain(HttpSecurity http,
                                                JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
    return http
            .securityMatcher("/api/**", "/swagger-ui/**", "/v3/api-docs/**")
            // 保持现有 JWT 配置、PUBLIC_PATHS、异常处理、CORS 配置不变
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
}
```

内部安全链要求：

- 原有 JWT 认证、`SystemConstants.PUBLIC_PATHS`、Swagger 放行、异常处理逻辑保持不变。
- 内部 `/api/**` 不因 API Key 功能增加额外 body 包装、签名校验、Nonce 校验或限流。
- 如果内部请求误带 `X-App-Id`，建议在内部链路前置一个轻量检查直接返回 400，避免身份混用；也可选择忽略，但必须在测试中固定行为。
- `api-key.enabled=false` 时，`externalApiSecurityFilterChain` 可以返回 404/禁用响应或不注册外部接口，内部链路必须完全可用。

### 8. 外部 Controller 授权

外部 Controller 使用 API 权限，不使用内部角色：

```java
@GetMapping("/api/external/v1/products")
@PreAuthorize("hasAuthority('API_product:read')")
public Result<Page<ExternalProductDTO>> listProducts(...) {
    // 调用 ProductService，返回外部 DTO
}
```

```java
@PostMapping("/api/external/v1/products/{productId}/prices")
@PreAuthorize("hasAuthority('API_price:write')")
public Result<Void> createPrice(...) {
    // 调用 PriceService
}
```

阶段一新增或拆分的外部 Controller：

| Controller | 需要补充的外部权限 |
|------------|--------------------|
| `ExternalProductController` | `API_product:read`、`API_product:write`、`API_product:delete` |
| `ExternalPriceController` | `API_price:read`、`API_price:write` |
| `ExternalPriceQueryController` | `API_price-query:read`、`API_price-query:export` |
| `ExternalBasicDataController` | `API_category:read`、`API_origin:read`、`API_customer:read`、`API_dict:read` |
| `ExternalHomeController` | `API_home:read` |

要求：

- 不在 `ApiKeyAuthenticationFilter` 中授予内部用户角色。
- 不修改现有 `ProductController`、`PriceController`、`SysDictController` 等内部 Controller 的 `@PreAuthorize`。
- 每个开放外部接口必须同时存在 `sys_external_api_endpoint` 映射和外部 Controller 权限分支。
- 集成测试必须覆盖“内部 `/api/products` 不接受 API Key、外部 `/api/external/v1/products` 接受 API Key”的隔离行为。

### 9. 管理 API 设计

所有管理 API 仅 `ADMIN` 可访问，并使用现有 JWT。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-keys` | 分页查询 |
| POST | `/api/api-keys` | 创建，返回 Secret 一次 |
| GET | `/api/api-keys/{id}` | 详情 |
| PUT | `/api/api-keys/{id}` | 更新名称、描述、环境、过期时间、白名单、限流、权限 |
| PUT | `/api/api-keys/{id}/enable` | 启用 |
| PUT | `/api/api-keys/{id}/disable` | 停用 |
| PUT | `/api/api-keys/{id}/revoke` | 吊销 |
| GET | `/api/api-keys/permissions/tree` | 获取外部 API 权限树 |
| GET | `/api/api-call-logs` | 分页查询调用日志 |
| GET | `/api/api-call-logs/statistics` | 阶段一基础统计 |

分页参数统一使用 `page`、`size`，响应继续使用 `Result<T>`。

管理 API 不属于外部调用面：

- `/api/api-keys/**` 和 `/api/api-call-logs/**` 走内部 JWT 安全链。
- API Key 不能调用管理 API。
- 管理页面仍由现有后台前端访问，不暴露到 `/api/external/**`。

### 10. 调用日志保留与失败限速

阶段一即配置日志治理，避免上线后日志表失控。

配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `api-key.log.retention-days` | 180 | 调用日志保留天数 |
| `api-key.log.auth-failure-sample-rate` | 1.0 | 认证失败采样率，生产可按压力调整 |
| `api-key.log.max-error-message-length` | 500 | 错误摘要最大长度 |
| `api-key.log.max-query-length` | 1000 | query 最大记录长度 |

策略：

- 阶段一同步写入最小调用日志，日志写入失败只记录告警，不影响业务响应。
- 认证失败、重放、限流、权限拒绝记录最小日志。
- 同一 IP + App ID 的高频认证失败按采样率控制，避免恶意请求刷爆数据库。
- 阶段二增加异步批量落库和归档能力；阶段一已经实现每日清理保留期外日志。

### 11. 前端页面

阶段一新增路由：

```text
/api-keys
/api-keys/:id
/api-call-logs
```

菜单挂在「系统管理」下：

```text
系统管理
├── 用户管理
├── 部门管理
├── 菜单配置
├── 日志管理
├── 审批流配置
├── 样式设置
└── API授权管理
    ├── 密钥管理
    └── 调用日志
```

注意当前数据库 `menu_item` 使用 `name` 字段，迁移 SQL 必须写：

```sql
INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT 4, 'API授权管理', '/api-keys', 'key', 7, TRUE, '["ADMIN"]', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM menu_item WHERE path = '/api-keys');
```

阶段一页面：

- `ApiKeyList.vue`：筛选、分页、状态展示、创建入口、启停吊销。
- `ApiKeyDetail.vue`：基础信息、权限、白名单、限流、最近调用。
- `ApiKeyForm.vue`：创建/编辑弹窗，权限树勾选。
- `ApiKeySecretModal.vue`：创建成功后一次性展示 Secret。
- `ApiCallLog.vue`：调用日志查询，支持 App ID、结果、状态码、时间范围筛选。

前端硬编码要求：

- 状态显示使用 `getDictValue('api_key_status', row.status)`。
- 环境显示使用 `getDictValue('api_key_environment', row.environment)`。
- 操作类型使用 `getDictValue('api_key_operation', key)`。
- 认证结果使用 `getDictValue('api_auth_result', key)`。
- 权限名称使用 `getDictValue('api_permission', key)`。

## 阶段二详细设计

### 0. 阶段二前置基线

阶段二启动前必须先确认阶段一及“可复制调用示例”增强已经形成以下基线：

- `sys_external_api_endpoint` 已包含端点结构化示例字段、参数 schema、成功/失败示例和代码调用提示。
- `frontend/src/utils/externalApiCodeExamples.ts` 已统一生成 Node.js、Java 25、Postman、PowerShell、curl 示例。
- 创建成功弹窗只在前端内存中使用一次性 `appSecret`，不回传后端、不写入存储。
- `docs/dev/API调用手册.md` 已记录签名测试向量，前端模板与后端签名工具生成一致。
- `API_KEY_ENABLED` 部署级开关和运行时服务开关已经落地；阶段二监控和告警必须纳入这两个状态。

阶段二不再重复实现基础代码示例，而是在此基础上做“治理化”：

- 示例模板版本化。
- 端点契约一致性检查。
- 可下载示例包。
- 调试控制台复用同一套签名和请求构造逻辑。
- 文档和页面中的示例能力保持单一来源。

### 1. 数据库迁移

新增迁移文件：

```text
backend/src/main/resources/db/migration/V21__external_api_auth_phase2.sql
```

说明：

- 当前项目已经使用 `V18__external_api_endpoint_docs.sql`、`V19__external_api_endpoint_code_examples.sql`、`V20__external_api_runtime_service_switch.sql`，阶段二迁移不得再使用原方案中的 `V18__external_api_auth_phase2.sql`。
- 迁移只能新增字段、表、索引和字典项，不修改已执行历史迁移。

#### sys_api_key 扩展字段

| 字段 | 类型 | 说明 |
|------|------|------|
| predecessor_id | BIGINT | 前任密钥 ID，新密钥由轮换产生时填写 |
| successor_id | BIGINT | 后继密钥 ID，旧密钥进入过渡期时填写 |
| transition_start_time | DATETIME | 轮换过渡开始时间 |
| transition_end_time | DATETIME | 轮换过渡结束时间 |
| rotation_reason | VARCHAR(500) | 轮换原因 |
| last_rotation_time | DATETIME | 最近一次轮换时间 |

索引：

- `idx_api_key_predecessor(predecessor_id)`
- `idx_api_key_successor(successor_id)`
- `idx_api_key_transition_end(transition_end_time)`

状态扩展写入 `api_key_status` 字典：

| 状态 | 说明 |
|------|------|
| TRANSITION | 过渡期，旧密钥仍可调用，新密钥也可调用 |
| DEPRECATED | 已弃用，仅保留审计，不再允许调用 |

操作类型扩展写入 `api_key_operation` 字典：

| 操作 | 说明 |
|------|------|
| ROTATE | 发起轮换 |
| FINISH_ROTATION | 结束轮换 |
| APPROVE_CHANGE | 审批通过后生效 |
| REJECT_CHANGE | 审批拒绝 |

#### sys_api_alert_config

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| api_key_id | BIGINT | NULL 表示全局规则 |
| alert_type | VARCHAR(50) | 字典 `api_alert_type` |
| threshold | DECIMAL(10,2) | 阈值 |
| window_minutes | INT | 检测窗口 |
| notify_channels | TEXT | JSON 数组，字典 `api_notify_channel` |
| webhook_url_cipher | TEXT | Webhook URL 加密存储 |
| webhook_url_masked | VARCHAR(500) | 脱敏展示值 |
| enabled | BOOLEAN | 是否启用 |
| last_triggered_time | DATETIME | 最近触发时间 |
| cooldown_minutes | INT | 告警冷却时间 |
| created_by | BIGINT | 创建人 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |
| version | BIGINT | 乐观锁 |

索引：

- `idx_api_alert_key(api_key_id)`
- `idx_api_alert_type(alert_type)`
- `idx_api_alert_enabled(enabled)`

#### sys_api_alert_event

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| config_id | BIGINT | 告警配置 ID |
| api_key_id | BIGINT | 命中的 API Key，可为空 |
| alert_type | VARCHAR(50) | 告警类型 |
| metric_value | DECIMAL(12,4) | 实际指标值 |
| threshold | DECIMAL(10,2) | 阈值 |
| window_start_time | DATETIME | 检测窗口开始 |
| window_end_time | DATETIME | 检测窗口结束 |
| notify_channels | TEXT | 实际通知渠道 |
| notify_status | VARCHAR(30) | SUCCESS / FAILED / SKIPPED |
| message | VARCHAR(1000) | 告警摘要 |
| created_time | DATETIME | 创建时间 |

#### sys_api_example_bundle

用于管理可下载示例包版本，不保存真实 Secret。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| bundle_version | VARCHAR(30) | 示例包版本，如 `v1.0.0` |
| languages | TEXT | JSON 数组，如 `["node","java","powershell","curl","postman"]` |
| signature_vector | TEXT | 当前测试向量 JSON |
| endpoint_snapshot_hash | VARCHAR(64) | 端点 schema 快照 hash |
| status | VARCHAR(20) | ACTIVE / INACTIVE |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

### 2. 密钥轮换

轮换目标：

- 不要求外部调用方瞬时切换 Secret。
- 新 Secret 仍只展示一次。
- 旧 Secret 在过渡期内可用，过渡结束自动失效。
- 轮换链路可审计、可手动结束、可审批。

管理 API：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/api-keys/{id}/rotate` | 发起轮换，创建 successor，返回新 Secret 一次 |
| PUT | `/api/api-keys/{id}/finish-rotation` | 手动结束旧密钥过渡期 |
| GET | `/api/api-keys/{id}/rotation-chain` | 查看密钥轮换链路 |

轮换请求：

```json
{
  "transitionDays": 7,
  "reason": "供应商系统密钥定期轮换",
  "copyPermissions": true,
  "copyIpWhitelist": true,
  "copyLimits": true
}
```

轮换规则：

- 只有 `ACTIVE` 和 `TRANSITION` 状态可发起轮换；`REVOKED`、`DEPRECATED` 不允许轮换。
- 新密钥继承名称时追加“轮换版本”标识，避免列表中难以区分。
- 新密钥默认复制旧密钥的权限、IP 白名单、环境、限流和过期时间，也允许管理员在轮换请求中收窄权限。
- 旧密钥状态改为 `TRANSITION`，写入 `successor_id`、`transition_start_time`、`transition_end_time`。
- 新密钥写入 `predecessor_id` 和 `last_rotation_time`，状态为 `ACTIVE`。
- 旧密钥过渡期结束后自动改为 `DEPRECATED`；管理员确认无调用后可吊销。
- 过渡期内调用日志按实际 `api_key_id` 记录，仪表盘支持按轮换链合并查看。

定时任务：

- 每 10 分钟扫描过渡期结束的旧密钥并自动改为 `DEPRECATED`；确认无调用后由管理员或清理任务吊销。
- 每天扫描即将过期密钥，生成提醒事件。
- 每天扫描过渡期仍有旧密钥调用的情况，生成风险提醒。

### 3. 监控仪表盘

新增页面：

```text
/api-dashboard
```

菜单挂在「系统管理 / API授权管理」下：

```text
API授权管理
├── 密钥管理
├── 调用日志
├── 监控仪表盘
├── 告警配置
└── 调试控制台
```

指标：

- 今日调用量。
- 成功率。
- 平均响应时间。
- P95 响应时间。
- 认证失败次数。
- 限流触发次数。
- 服务状态：部署级开关、运行时开关、最近暂停/恢复时间。
- TOP10 调用方。
- TOP10 接口。
- TOP10 失败原因。
- 近 24 小时调用趋势。
- 按 API Key、权限编码、端点、认证结果、状态码、时间窗口筛选。

统计 API：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-dashboard/summary` | 摘要卡片 |
| GET | `/api/api-dashboard/trend` | 趋势 |
| GET | `/api/api-dashboard/top-keys` | TOP 调用方 |
| GET | `/api/api-dashboard/top-endpoints` | TOP 接口 |
| GET | `/api/api-dashboard/top-errors` | TOP 失败原因 |
| GET | `/api/api-dashboard/key/{id}/summary` | 单个 API Key 概览 |

数据来源：

- 阶段二先基于 `sys_api_call_log` 聚合查询，避免引入新的时序系统。
- 如果日志量达到慢查询阈值，再增加按小时汇总表 `sys_api_call_metric_hourly`。
- 统计接口不得反查或返回 Secret。

### 4. 告警配置与通知

告警类型：

| 类型 | 触发规则 |
|------|----------|
| SUCCESS_RATE | 窗口内成功率低于阈值 |
| RESPONSE_TIME | 平均响应时间高于阈值 |
| RATE_LIMIT | 限流触发次数高于阈值 |
| AUTH_FAIL | 认证失败次数高于阈值 |
| EXPIRE_WARNING | 密钥将在 N 天内过期 |
| SERVICE_DISABLED | 运行时服务暂停超过阈值 |
| OLD_KEY_USED | 轮换过渡期内旧密钥仍被调用 |

通知渠道：

| 渠道 | 说明 |
|------|------|
| IN_APP | 站内通知或告警事件列表 |
| WEBHOOK | 通用 Webhook |
| DINGTALK | 复用现有钉钉告警服务 |
| WECHAT | 复用现有企业微信告警服务 |

管理 API：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-alert-configs` | 分页查询告警规则 |
| POST | `/api/api-alert-configs` | 创建告警规则 |
| GET | `/api/api-alert-configs/{id}` | 告警规则详情 |
| PUT | `/api/api-alert-configs/{id}` | 更新告警规则 |
| PUT | `/api/api-alert-configs/{id}/enable` | 启用告警 |
| PUT | `/api/api-alert-configs/{id}/disable` | 停用告警 |
| DELETE | `/api/api-alert-configs/{id}` | 删除告警规则 |
| GET | `/api/api-alert-events` | 查询告警事件 |
| POST | `/api/api-alert-configs/{id}/test` | 发送测试通知 |

安全要求：

- `webhook_url` 必须加密存储或复用现有敏感配置处理方式，列表和详情只返回脱敏值。
- 告警事件中不得包含 `appSecret`、完整签名、完整请求体。
- 高频告警必须支持冷却时间，避免通知刷屏。
- 告警检测失败不得影响外部 API 调用。

### 5. 调试控制台

原则：

- 调试台不读取、不保存、不回显历史 Secret。
- 用户在本地输入 Secret，前端仅在当前内存中生成签名。
- 离开页面或刷新后清空 Secret。
- 请求路径默认只能选择 `sys_external_api_endpoint` 中已启用的 `/api/external/v1/**` 端点；手动输入也必须限制在 `/api/external/v1/**`。
- 调试请求仍走真实后端认证和日志。
- 调试台复用 `frontend/src/utils/externalApiCodeExamples.ts` 的路径变量替换、query 排序、body 序列化和签名规则，避免出现“示例能跑、调试台不能跑”的算法分叉。

页面能力：

- 选择 API Key，仅带出 `appId`、权限、白名单、限流等非敏感信息。
- 用户手动输入 `appSecret`，输入框支持临时显示/隐藏，离开页面清空。
- 选择端点后自动填充 path 参数、query 示例、body 示例。
- 展示 canonical string、body hash、签名、请求头和最终 URL，便于联调排错。
- 支持发送真实请求并展示响应状态码、响应头、响应体和调用日志 ID。
- 支持“一键复制 Node.js / Java 25 / PowerShell / curl / Postman 示例”，示例来自同一工具模块。

调试 API：

调试请求可以直接从浏览器调用外部真实接口；如因 CORS、下载流或审计需要走后端代理，则新增内部管理接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/api-debug/execute` | 由管理端代理发送外部 API 调试请求 |

代理约束：

- 仅 ADMIN 可用。
- `appSecret` 只参与本次请求，不落库、不写日志、不出现在异常栈。
- 代理只允许访问本系统 `/api/external/v1/**`，禁止任意 URL 转发。
- 调试请求仍记录 `sys_api_call_log`，并在日志中标识 `debug=true`。

### 6. 示例资产治理

阶段一增强已经完成页面内可复制示例。阶段二不发布完整 SDK 包，优先提供轻量、可审计、可版本化的示例资产。

新增目录：

```text
docs/examples/external-api/
├── README.md
├── node-example.mjs
├── java-25-example.java
├── powershell-example.ps1
├── curl-openssl-example.sh
├── postman-pre-request-script.js
└── signature-test-vector.json
```

资产要求：

- 示例文件只包含占位符，不包含真实 `appSecret`。
- `signature-test-vector.json` 与后端 `ApiSignatureUtilTests`、前端 `SIGNATURE_TEST_VECTOR` 一致。
- 示例包版本写入 `sys_api_example_bundle`，页面显示当前示例版本。
- 后端提供只读接口下载示例包或单语言示例，返回内容由前端模板或服务端模板生成，但不得要求用户上传真实 Secret。
- 端点 schema 变更后，必须重新计算 `endpoint_snapshot_hash`，提示管理员更新示例包。

管理 API：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-examples/bundles/current` | 获取当前示例包元数据 |
| GET | `/api/api-examples/download?language=node` | 下载指定语言占位符示例 |
| GET | `/api/api-examples/signature-vector` | 获取签名测试向量 |
| GET | `/api/api-examples/contract-check` | 检查端点 schema、文档、示例是否一致 |

### 7. 审批流接入

接入范围：

- 生产环境创建 API Key。
- 生产环境发起密钥轮换。
- 权限扩大。
- IP 白名单从有限制改为无限制，或新增宽泛网段。
- 吊销密钥。
- 延长过期时间。
- 提高分钟限流或日限额。

审批通过前：

- 变更写入审批请求，不直接生效。
- 审批通过后由 `ApiKeyService` 执行实际变更。
- 操作日志记录申请人、审批人、变更前后 diff。
- 被审批变更不得包含明文 Secret；密钥创建或轮换的 Secret 只能在审批通过并真正创建成功后展示一次。

审批识别建议：

- 复用现有审批模块，新增业务类型 `API_KEY_CHANGE`。
- 审批请求数据使用 JSON 记录目标密钥、变更类型、变更前后摘要。
- 审批通过回调必须校验乐观锁，避免审批期间密钥状态已经变化。

### 8. 日志归档与性能治理

日志策略：

- 阶段一保留同步最小日志和保留期清理。
- 阶段二增加按月归档任务，可将超过保留期但仍需审计的数据迁移到归档表。
- 调用日志页面默认查询热数据；需要查询归档数据时必须显式选择归档范围。
- 认证失败日志继续保留采样和截断策略。

可选归档表：

```text
sys_api_call_log_archive_yyyy_mm
```

性能目标：

- 签名认证链路 P95 额外耗时小于 50ms。
- 监控仪表盘默认 24 小时查询在 2 秒内返回。
- 调用日志分页查询在常用筛选条件下 1.5 秒内返回。
- 告警检测任务单次执行不阻塞外部 API 请求。
- 调试台单次请求遵循外部 API 同等限流与日志规则。

压测内容：

- 正确签名高并发请求。
- 错误签名和重放请求高频攻击模拟。
- 单个 App ID 限流触发。
- 多 App ID 并发调用隔离。
- 调用日志大量写入和仪表盘聚合查询。
- Redis 不可用时 Nonce / 限流内存降级。

## 迁移与一致性要求

### Entity 与数据库

每个新增 Entity 必须检查：

- `@Table(name = "...")` 与表名一致。
- `@Column(name = "...")` 与列名一致。
- `@JoinColumn(name = "...")` 外键列存在。
- 并发更新表包含 `@Version` 和 `version` 列。
- 不入库字段加 `@Transient`。

### 前后端类型

必须创建并保持一致：

```text
backend dto <-> frontend/src/types/apiKey.ts
backend controller path <-> frontend/src/api/apiKey.ts
```

重点字段：

- `appId`
- `status`
- `environment`
- `expireTime`
- `ipWhitelist`
- `rateLimitPerMinute`
- `dailyLimit`
- `permissionCodes`
- `createdTime`
- `updatedTime`
- `lastUsedTime`

外部接口类型要求：

```text
External DTO <-> docs/dev/API调用手册.md
External Controller path <-> sys_external_api_endpoint.path_pattern
```

外部 DTO 不直接复用内部管理 DTO。字段必须按对外契约显式定义，避免内部字段变化影响外部系统。

### 文档同步

阶段一完成后必须更新：

- `README.md`
- `docs/dev/开发指南.md`
- `docs/ops/IDEA部署指南.md`
- `docs/dev/项目设计文档.md`
- `docs/dev/API调用手册.md`
- `docs/dev/UI设计说明.md`
- `docs/archive/项目完成总结.md`
- `backend/src/main/resources/数据字典.md`

阶段二完成后再次同步上述文档，并补充密钥轮换、监控告警、调试控制台、示例资产治理、审批治理和日志归档说明。

## 实施步骤

### 阶段一实施步骤

1. 创建 `V17__external_api_auth_phase1.sql`。
2. 初始化阶段一字典和 `/api/external/v1/**` 的 `sys_external_api_endpoint` 默认权限映射。
3. 初始化「API授权管理」「密钥管理」「调用日志」菜单，使用当前 `menu_item.name` 字段。
4. 新增 Entity，并完成 ORM 注解与数据库一致性检查。
5. 新增 Repository。
6. 新增 `ApiKeyProperties`，配置 `api-key.encryption-key` 从 `API_KEY_ENCRYPTION_KEY` 读取。
7. 实现 `ApiKeyProperties` 启动校验：主密钥 Base64、长度、版本、生产环境 fail fast。
8. 实现 `ApiKeySecretService`：生成、加密、解密、fingerprint、key version。
9. 实现 request body 缓存包装，确保过滤器和 Controller 都可读取请求体。
10. 实现 `ApiSignatureService`：canonical string、body hash、HMAC、常量时间比较、测试向量。
11. 实现 `ApiNonceService`：Redis SETNX + 内存降级。
12. 实现 `ApiRateLimitService`：Redis 原子计数 + 内存降级。
13. 实现 `ExternalApiPermissionService`：仅匹配 `/api/external/v1/**`，路径匹配、具体度排序、未匹配默认拒绝。
14. 实现 `ApiKeyAuthenticationFilter` 并接入独立 `externalApiSecurityFilterChain`。
15. 新增 `ExternalProductController`、`ExternalPriceController`、`ExternalPriceQueryController`、`ExternalBasicDataController`、`ExternalHomeController`，不改造现有内部 Controller。
16. 实现 `ApiCallLogService`：最小同步日志、失败采样、字段截断、保留期清理配置。
17. 实现 `ApiKeyService` 和管理操作日志。
18. 实现 `ApiKeyController`、`ApiCallLogController`。
19. 前端新增 `types/apiKey.ts`、`api/apiKey.ts`。
20. 前端新增密钥管理、详情、表单、Secret 一次性展示、调用日志页面。
21. `useDict.ts` 增加新增字典分类标签。
22. 补充后端单元测试和集成测试。
23. 补充前端构建验证和硬编码扫描。
24. 更新阶段一相关文档。

### 阶段二实施步骤

1. 确认阶段一增强基线：`V19__external_api_endpoint_code_examples.sql`、`externalApiCodeExamples.ts`、签名测试向量和运行时服务开关已完成。
2. 创建 `V21__external_api_auth_phase2.sql`，新增轮换字段、告警配置表、告警事件表、示例包元数据表和阶段二字典。
3. 扩展 `ApiKey`、`ApiKeyDTO`、`ApiKeyService`，完成 Entity 注解与数据库字段一致性检查。
4. 实现密钥轮换服务、轮换链路查询、过渡期结束定时任务和旧密钥仍被调用提醒。
5. 实现 `ApiDashboardController` 和统计服务，覆盖摘要、趋势、TOP 调用方、TOP 接口、TOP 失败原因。
6. 实现 `ApiAlertConfigController`、告警检测服务、告警事件查询、通知测试和冷却策略，复用现有钉钉/企业微信告警能力。
7. 新增前端监控仪表盘页面，展示服务状态、核心指标、趋势图、TOP 列表和筛选条件。
8. 新增前端告警配置页面，所有告警类型、通知渠道显示名使用字典服务。
9. 新增调试控制台页面，复用 `externalApiCodeExamples.ts` 的签名和请求构造逻辑，Secret 只保存在页面内存。
10. 增加示例资产治理：`docs/examples/external-api/`、示例包下载接口、签名测试向量接口、端点契约检查接口。
11. 接入审批流，覆盖生产环境创建、轮换、吊销、权限扩大、白名单放宽、延长过期、提高限额等敏感操作。
12. 增加日志归档任务、可选归档表策略和归档查询入口。
13. 增加压测脚本和性能报告，覆盖签名认证、日志写入、统计查询、限流、Redis 降级和调试台。
14. 更新 README、开发指南、IDEA 部署指南、项目设计文档、API 调用手册、UI 说明、完成总结和数据字典。

## Verification

### 阶段一验收

安全：

- [ ] 无 `X-App-Id` 且无 JWT 的请求返回 401。
- [ ] `/api/external/**` 缺少 `X-App-Id` 的请求返回 401。
- [ ] `/api/external/**` 同时携带 JWT 和 API Key 认证头的请求被拒绝。
- [ ] 内部 `/api/**` 不进入 API Key 过滤器。
- [ ] 内部 `/api/products` 携带 API Key 但无 JWT 时不能访问。
- [ ] API Key 认证成功后可通过对应 External Controller 权限分支。
- [ ] API Key 不具备内部 `ROLE_ADMIN`、`ROLE_EDITOR`、`ROLE_VIEWER`。
- [ ] 正确签名通过，错误签名返回 401。
- [ ] 超出时间窗口返回 401。
- [ ] 相同 Nonce 第二次请求返回 401 或 409。
- [ ] 请求 body 任意改动后签名失败。
- [ ] 过滤器读取 body 后，Controller `@RequestBody` 仍可正常绑定。
- [ ] multipart / import 类能力在阶段一没有 `/api/external/v1/**` 对外入口。
- [ ] 数据库无 Secret 明文。
- [ ] `app_secret_key_version` 正确写入。
- [ ] 生产环境缺少 `API_KEY_ENCRYPTION_KEY` 或格式非法时应用启动失败。
- [ ] Secret 只在创建响应展示一次。
- [ ] IP 白名单生效。
- [ ] 分钟限流和日限额生效，超限返回 429。
- [ ] 无权限接口返回 403。

功能：

- [ ] ADMIN 可创建、编辑、启用、停用、吊销 API Key。
- [ ] 调用日志记录成功、失败、限流、权限拒绝。
- [ ] 管理操作日志记录创建、编辑、启停、吊销。
- [ ] API Key 变更后立即按数据库最新状态和权限生效。
- [ ] 过期密钥无法调用。
- [ ] 外部系统可通过签名调用被授权的 `/api/external/v1/products` 等接口。
- [ ] 未登记在 `sys_external_api_endpoint` 的接口默认拒绝外部 API 调用。
- [ ] 调用日志保留期配置生效。
- [ ] 高频认证失败不会无限制刷写数据库。

一致性：

- [ ] Controller 路径与前端 API 文件一致。
- [ ] DTO 字段与 TypeScript 类型一致。
- [ ] Entity 注解与数据库表结构一致。
- [ ] 新增字典存在于 `sys_dict` 和 `CATEGORY_LABELS`。
- [ ] 每个阶段一开放外部接口同时具备 `sys_external_api_endpoint` 映射和 External Controller 权限分支。
- [ ] 现有内部业务 Controller 未因阶段一实施修改 `@PreAuthorize`。
- [ ] 签名测试向量已写入 `docs/dev/API调用手册.md`，Java 与 JavaScript 示例生成一致签名。
- [ ] 前端状态、环境、操作、认证结果显示无硬编码中文映射。
- [ ] 数据字典文档同步更新。

构建与测试：

- [ ] 后端测试通过：`mvn clean test`。
- [ ] 前端构建通过：`npm run build`。
- [ ] 签名、Nonce、限流、权限服务有单元测试。
- [ ] API Key 完整认证流程有集成测试。
- [ ] 外部接口权限集成测试覆盖 read/write/delete 权限允许和拒绝。
- [ ] 隔离测试覆盖 `/api/external/**` 与内部 `/api/**` 安全链互不影响。
- [ ] body 缓存包装集成测试覆盖 POST/PUT JSON 请求。

性能：

- [ ] API Key 授权元数据实时查库，变更后无缓存滞后。
- [ ] 调用日志写入失败不阻塞成功请求。
- [ ] Redis 不可用时系统可降级并产生日志标识。

### 阶段二验收

- [ ] 密钥轮换过渡期新旧密钥均可用。
- [ ] 过渡期结束后旧密钥自动失效。
- [ ] 旧密钥过渡期仍被调用时可以产生提醒或告警事件。
- [ ] 轮换链路可查询，调用日志可按实际密钥和轮换链合并查看。
- [ ] 仪表盘指标与调用日志统计一致。
- [ ] 仪表盘展示部署级开关和运行时服务开关状态。
- [ ] 成功率、响应时间、限流、认证失败、过期提醒、服务暂停、旧密钥调用告警可触发。
- [ ] 站内通知、Webhook、钉钉或企业微信通知正常发送，并支持冷却时间。
- [ ] 调试控制台不保存 Secret，刷新后 Secret 清空。
- [ ] 调试控制台与可复制示例使用同一套签名规则，同一测试向量结果一致。
- [ ] 调试控制台只允许访问 `/api/external/v1/**`，不能代理访问任意 URL。
- [ ] 示例包下载内容只包含占位符，不包含真实 Secret。
- [ ] 示例包签名测试向量与后端测试、前端 `SIGNATURE_TEST_VECTOR` 一致。
- [ ] 端点 schema、API 调用手册和示例资产契约检查通过。
- [ ] 生产环境敏感操作按审批流生效。
- [ ] 审批通过前变更不生效，审批拒绝后不改变密钥状态和权限。
- [ ] 日志归档任务可执行，热数据和归档数据查询边界清晰。
- [ ] 压测报告满足预设性能指标。

## 风险与控制

| 风险 | 控制措施 |
|------|----------|
| Secret 加密主密钥泄露 | 使用环境变量配置，生产定期轮换，禁止提交到仓库 |
| Secret 主密钥格式错误导致无法解密 | 启动时校验 Base64、长度和版本；生产环境 fail fast |
| 签名算法跨语言不一致 | 使用明确 canonical string，并提供测试向量和示例资产 |
| API Key 影响现有内部接口 | 第一阶段使用 `/api/external/v1/**` 独立接口和独立 SecurityFilterChain，不改内部 Controller |
| API Key 通过认证后被权限拦截 | External Controller 使用 `hasAuthority('API_xxx')`，并用集成测试覆盖 |
| 签名过滤器读取 body 导致业务接口 body 为空 | 使用可重复读取 request wrapper，并测试 POST/PUT JSON |
| Redis 故障导致防重放能力下降 | 降级内存缓存并记录降级日志，运维告警提示 |
| 日志量快速增长 | 阶段一加索引、保留期、字段截断和失败采样，阶段二增加异步批量与归档 |
| 权限映射遗漏 | 默认只开放明确登记接口，未匹配接口默认拒绝 |
| 宽泛路径误授权 | 只匹配 `/api/external/v1/**`，具体路径优先，管理类和导入导出接口阶段一默认不开放 |
| 外部调用影响内部接口 | 限流按 appId 隔离，内部 JWT 不受外部限流影响 |
| 调试台泄露 Secret | Secret 仅当前页面内存使用，不保存、不上传、不回显历史值 |

## 9.5+ 评分依据

| 维度 | 达成方式 |
|------|----------|
| 安全性 | HMAC、Nonce、Timestamp、IP 白名单、Secret 加密、权限最小化、路径隔离 |
| 可执行性 | 两阶段拆分，阶段一可独立上线 |
| 项目一致性 | 保持现有 JWT 和内部 Controller 行为，新增独立外部安全链、菜单、字典和文档 |
| 可运维性 | 调用日志、操作日志、限流、实时授权生效、日志保留、阶段二监控告警 |
| 可扩展性 | `/api/external/v1` 版本化、权限映射表、轮换机制、示例资产治理、后续可迁移 API 网关 |
| 用户体验 | 管理页面、一次性 Secret 展示、调试台和 API 手册 |

---

*方案评分：9.5+*
*执行方式：两阶段交付*
*最后更新：2026-06-02*
