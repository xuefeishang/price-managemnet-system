# API 调用手册

矿产品价格管理系统后端 API 接口文档，供外部系统对接调用。

---

## 目录

- [基础信息](#基础信息)
- [认证方式](#认证方式)
- [外部 API Key 签名认证](#外部-api-key-签名认证)
- [外部 API 管理接口](#外部-api-管理接口)
- [外部 API 业务接口](#外部-api-业务接口)
- [通用响应格式](#通用响应格式)
- [认证接口](#认证接口)
- [产品接口](#产品接口)
- [价格接口](#价格接口)
- [价格草稿接口](#价格草稿接口)
- [价格查询接口](#价格查询接口)
- [首页仪表盘接口](#首页仪表盘接口)
- [分类接口](#分类接口)
- [产地接口](#产地接口)
- [客户接口](#客户接口)
- [字典接口](#字典接口)
- [用户接口](#用户接口)
- [角色接口](#角色接口)
- [权限接口](#权限接口)
- [部门接口](#部门接口)
- [菜单接口](#菜单接口)
- [操作日志接口](#操作日志接口)
- [导入导出接口](#导入导出接口)
- [样式配置接口](#样式配置接口)
- [审批流程接口](#审批流程接口)
- [个人中心接口](#个人中心接口)
- [通知中心接口](#通知中心接口)
- [管理员通知接口](#管理员通知接口)
- [系统公告接口](#系统公告接口)
- [定时任务接口](#定时任务接口)
- [产品年度预算接口](#产品年度预算接口)
- [API 授权管理接口](#api-授权管理接口)
- [API 调用日志接口](#api-调用日志接口)

---

## 基础信息

| 项目 | 值 |
|------|-----|
| 基础URL | `http://<服务器地址>:8080/api` |
| 协议 | HTTP/HTTPS |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |
| 时间格式 | `yyyy-MM-dd HH:mm:ss` |
| 日期格式 | `yyyy-MM-dd` |

---

## 认证方式

内部后台接口使用 JWT Bearer Token 认证。登录成功后获取 `accessToken`，后续请求需在请求头中携带：

```
Authorization: Bearer <accessToken>
```

外部系统接口使用 API Key + HMAC-SHA256 签名认证，仅允许调用 `/api/external/v1/**`。外部接口不得携带 `Authorization`，也不得传输 `X-App-Secret`。

### 角色权限说明

| 角色 | 权限范围 |
|------|----------|
| ADMIN | 全部接口 |
| EDITOR | 查看、新增、编辑（无删除权限） |
| VIEWER | 仅查看 |

---

## 外部 API Key 签名认证

### 启用条件

外部 API 默认关闭。服务端必须配置：

| 环境变量 | 说明 |
|----------|------|
| `API_KEY_ENABLED=true` | 启用外部 API |
| `API_KEY_ENCRYPTION_KEY` | Base64 编码的 32 字节 AES-GCM 主密钥 |
| `API_KEY_ENCRYPTION_KEY_VERSION` | 主密钥版本，默认 `v1` |

启用后还受后台页面运行时开关控制：

- `API_KEY_ENABLED=false`：部署级关闭，外部接口始终不可用。
- `API_KEY_ENABLED=true` 且 API 授权管理页开关为“开启”：外部接口正常服务。
- `API_KEY_ENABLED=true` 但页面开关为“暂停”：所有 `/api/external/v1/**` 返回 `503`，认证结果为 `SERVICE_DISABLED`。

### 请求头

```http
X-App-Id: app_xxxxxxxxxxxxxxxx
X-Timestamp: 1779990000
X-Nonce: nonce_test_001
X-Signature: 7221cc7d6fc7d2f6cde1c20d7cdf62aa9669e0c965fe9e91902efac24d4e37cf
```

禁止请求头：

```http
X-App-Secret
Authorization
```

### Canonical String

```text
HTTP_METHOD_UPPERCASE
canonicalPath
canonicalQuery
timestamp
nonce
bodySha256Hex
```

规则：

- 使用 `\n` 连接 6 行。
- `canonicalPath` 使用请求 path，不含域名和 query。
- `canonicalQuery` 按 key 升序、value 升序排序后 URL 编码；无 query 时为空字符串。
- `bodySha256Hex` 对原始请求体字节计算 SHA-256。空 body 固定为 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`。
- `X-Timestamp` 使用 Unix 秒，默认允许服务器时间 ±300 秒。
- `X-Nonce` 在 TTL 内不能重复，默认 600 秒。
- 签名值为 `hex(hmac_sha256(appSecret, canonicalString))`。

### 测试向量

| 项目 | 值 |
|------|-----|
| secret | `sec_test_1234567890` |
| method | `GET` |
| path | `/api/external/v1/products` |
| query | `size=20&page=0` |
| canonical query | `page=0&size=20` |
| timestamp | `1779990000` |
| nonce | `nonce_test_001` |
| bodySha256Hex | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| signature | `7221cc7d6fc7d2f6cde1c20d7cdf62aa9669e0c965fe9e91902efac24d4e37cf` |

Canonical String：

```text
GET
/api/external/v1/products
page=0&size=20
1779990000
nonce_test_001
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
```

### JavaScript 签名示例

```javascript
async function sha256Hex(input) {
  const data = typeof input === 'string' ? new TextEncoder().encode(input) : input
  const digest = await crypto.subtle.digest('SHA-256', data)
  return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('')
}

async function hmacSha256Hex(secret, canonicalString) {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  )
  const sig = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(canonicalString))
  return [...new Uint8Array(sig)].map(b => b.toString(16).padStart(2, '0')).join('')
}
```

### 可复制调用示例

后台 `/api-keys` 新增密钥弹窗会基于 `sys_external_api_endpoint` 的结构化元数据生成 Node.js、Java 25、Postman、PowerShell、curl 示例。创建成功弹窗中的示例会临时带入真实 `App ID` 和一次性 `App Secret`，关闭后不再保留。

安全要求：

- `App Secret` 只应保存在外部系统服务端环境变量中。
- 不要把 `App Secret` 写入浏览器前端、Git 仓库、URL、日志或操作记录。
- 示例代码中的 body 字符串会参与 SHA-256 和 HMAC 签名，实际发送内容必须与签名时内容完全一致。
- Java 调用方优先复制 Java 25 示例，示例只使用 JDK 标准库 `HttpClient`、`Mac`、`MessageDigest`、`HexFormat`，不依赖第三方 SDK。
- Postman 调试时复制 Postman 示例，把 `base_url`、`app_id`、`app_secret` 填到 Environment，并把 Pre-request Script 粘贴到请求中。
- Windows 环境优先复制 PowerShell 示例；Linux/macOS 可使用 curl + openssl 示例。

权限端点元数据接口 `GET /api/api-keys/permissions/tree` 会返回：

| 字段 | 说明 |
|------|------|
| `queryExample` / `bodyExample` / `pathParamsExample` | 代码生成使用的示例参数 |
| `querySchema` / `bodySchema` / `pathParamsSchema` | 字段名、类型、是否必填、默认值和说明 |
| `successExample` / `failureExample` | 成功和失败响应示例 |
| `codeNotes` | 复制运行前的端点级注意事项 |

---

## 外部 API 管理接口

管理接口仍使用内部 JWT，且仅 `ADMIN` 可访问。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-keys` | 分页查询 API Key |
| POST | `/api/api-keys` | 创建 API Key，返回 Secret 一次 |
| GET | `/api/api-keys/{id}` | API Key 详情 |
| PUT | `/api/api-keys/{id}` | 更新名称、描述、环境、过期时间、白名单、限流和权限 |
| PUT | `/api/api-keys/{id}/enable` | 启用 |
| PUT | `/api/api-keys/{id}/disable` | 停用 |
| PUT | `/api/api-keys/{id}/revoke` | 吊销 |
| GET | `/api/api-keys/permissions/tree` | 获取外部 API 权限端点 |
| GET | `/api/api-call-logs` | 查询调用日志 |
| GET | `/api/api-call-logs/statistics` | 查询调用统计 |

### 创建 API Key

```http
POST /api/api-keys
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "name": "外部系统A",
  "description": "价格读取对接",
  "environment": "TESTING",
  "expireTime": "2026-12-31 23:59:59",
  "ipWhitelist": ["192.168.1.10", "10.0.0.0/24"],
  "rateLimitPerMinute": 60,
  "dailyLimit": 10000,
  "permissionCodes": ["product:read", "price:read"]
}
```

响应中的 `appSecret` 只展示一次，关闭后无法再次查看。

入参校验：

| 字段 | 规则 |
|------|------|
| `name` | 必填，不能为空白 |
| `environment` | 必填，取值来自字典 `api_key_environment` |
| `rateLimitPerMinute` | 必填，0 或正整数；0 表示不限制分钟请求次数 |
| `dailyLimit` | 必填，0 或正整数；0 表示不限制每日请求次数 |
| `expireTime` | 可选，格式 `yyyy-MM-dd HH:mm:ss` |
| `permissionCodes` | 管理页面要求至少选择一项；接口层会忽略不存在或未启用的权限编码 |

---

## 外部 API 业务接口

外部接口统一前缀：`/api/external/v1`。

| 权限编码 | 方法 | 路径 | 说明 |
|----------|------|------|------|
| `product:read` | GET | `/products`、`/products/{id}` | 产品读取 |
| `product:write` | POST/PUT | `/products`、`/products/{id}` | 产品写入 |
| `product:delete` | DELETE | `/products/{id}` | 产品删除 |
| `price:read` | GET | `/products/{productId}/price-history` | 价格历史 |
| `price:read` | GET | `/products/{productId}/current-price` | 当前价格 |
| `price:read` | GET | `/products/{productId}/price-by-date` | 指定日期价格 |
| `price:read` | GET | `/products/{productId}/price-trend` | 价格走势 |
| `price:read` | GET | `/prices/by-date`、`/prices/by-date-with-stats` | 按日期价格 |
| `price:write` | POST/PUT | `/products/{productId}/prices`、`/prices/{id}` | 价格写入 |
| `price-query:read` | GET | `/price-query` | 价格查询 |
| `price-query:export` | GET | `/price-query/export` | 价格查询导出 |
| `category:read` | GET | `/categories`、`/categories/{id}` | 分类读取 |
| `origin:read` | GET | `/origins`、`/origins/{id}` | 产地读取 |
| `customer:read` | GET | `/customers`、`/customers/{id}` | 客户读取 |
| `dict:read` | GET | `/dict?category=xxx`、`/dict/active`、`/dict/categories`、`/dict/{id}` | 字典读取 |
| `home:read` | GET | `/home/dashboard`、`/home/summary`、`/home/alerts`、`/home/trend`、`/home/product-order` | 首页数据 |

`GET /api/external/v1/dict` 必须指定 `category`，外部接口不提供一次性读取全部字典能力。

阶段一外部写入说明：

- `POST /products`、`POST /products/{productId}/prices`、`PUT /prices/{id}` 会复用现有产品/价格服务和审批流。
- 若审批流启用，外部写入产生的审批请求使用系统外部申请人占位 `0`，调用来源以 `sys_api_call_log.app_id` 和 `sys_api_key_operation_log` 追溯；外部应用到内部用户/部门的身份映射放入阶段二。
- 外部写入口会清理请求体中的 `id`、`version`、`createdTime`、`updatedTime` 等系统字段，不允许外部系统覆盖服务端生成字段。

---

## 通用响应格式

所有接口返回统一的 JSON 格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1779990000000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 业务态码，200=成功，4xx=客户端错误，5xx=服务端错误 |
| message | string | 响应消息，**默认"操作成功"**（不是"success"） |
| data | object | 响应数据，泛型 |
| timestamp | long | 服务端响应时间戳（**毫秒**，Long 类型） |

### 分页响应格式

```json
{
  "code": 200,
  "message": "获取成功",
  "data": {
    "content": [ ... ],
    "totalElements": 100,
    "totalPages": 10,
    "size": 10,
    "number": 0
  }
}
```

---

## 认证接口

### 获取验证码

```
GET /api/auth/captcha
```

**权限：** 无需认证

**响应：**
```json
{
  "code": 200,
  "message": "获取验证码成功",
  "data": {
    "captchaKey": "uuid-string",
    "captchaImage": "data:image/png;base64,..."
  }
}
```

### 用户登录

```
POST /api/auth/login
```

**权限：** 无需认证

**请求体：**
```json
{
  "username": "admin",
  "password": "password",
  "loginType": "USERNAME",
  "captchaKey": "uuid-string",
  "captchaCode": "1234"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名或工号 |
| password | string | 是 | 密码 |
| loginType | string | 否 | USERNAME 或 EMPLOYEE_ID |
| captchaKey | string | 否 | 验证码Key |
| captchaCode | string | 否 | 验证码内容 |

**响应：**
```json
{
  "code": 200,
  "message": "登录成功",
  "data": {
    "accessToken": "jwt-token-string",
    "refreshToken": "refresh-token-string",
    "tokenType": "Bearer",
    "user": {
      "id": 1,
      "username": "admin",
      "nickname": "管理员",
      "role": "ADMIN",
      "roles": ["ADMIN"]
    },
    "permissions": ["*:*:*"]
  }
}
```

### 刷新令牌

```
POST /api/auth/refresh-token
```

**权限：** 无需认证

**请求体：**
```json
{
  "refreshToken": "refresh-token-string"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "令牌刷新成功",
  "data": {
    "accessToken": "new-jwt-token",
    "refreshToken": "refresh-token-string",
    "tokenType": "Bearer",
    "expiresIn": 86400
  }
}
```

### 获取当前用户信息

```
GET /api/auth/profile
```

**权限：** 已登录用户

**响应：**
```json
{
  "code": 200,
  "message": "获取用户信息成功",
  "data": {
    "user": {
      "id": 1,
      "username": "admin",
      "employeeId": "000001",
      "nickname": "管理员",
      "role": "ADMIN",
      "roles": ["ADMIN"],
      "email": "admin@example.com",
      "phone": "13800000000",
      "department": "技术部"
    },
    "permissions": ["*:*:*"]
  }
}
```

### 更新个人信息

```
PUT /api/auth/profile
```

**权限：** 已登录用户

**请求体：**
```json
{
  "nickname": "新昵称",
  "email": "new@example.com",
  "phone": "13800000001"
}
```

### 修改密码

```
PUT /api/auth/password
```

**权限：** 已登录用户

> 该兼容接口委托 `/api/profile/password`。修改成功后会撤销当前用户全部 Refresh Token，前端必须清理本地 Token 并重新登录。新密码长度 8-32 位，必须包含字母和数字，不能包含空白字符，不能与旧密码相同。

**请求体：**
```json
{
  "oldPassword": "旧密码",
  "newPassword": "新密码",
  "confirmPassword": "确认新密码"
}
```

### 退出登录

```
POST /api/auth/logout
```

**权限：** 已登录用户

---

## 个人中心接口

所有接口均要求已登录，并且只能操作当前认证用户。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/profile` | 获取个人中心资料、角色和权限 |
| PUT | `/api/profile` | 更新昵称、邮箱、手机号 |
| GET | `/api/profile/security` | 获取最近登录、登录 IP、登录次数、密码更新时间和锁定状态 |
| PUT | `/api/profile/password` | 修改密码，成功后撤销全部 Refresh Token |
| GET | `/api/profile/operation-logs` | 查询我的操作记录，不接受 `userId` 参数 |
| GET | `/api/profile/sessions` | 查询当前有效会话；请求头可传 `X-Refresh-Token` 标识当前会话 |
| DELETE | `/api/profile/sessions/{id}` | 撤销指定其他设备会话 |
| DELETE | `/api/profile/sessions/others` | 退出其他设备 |
| DELETE | `/api/profile/sessions/all` | 退出全部设备并重新登录 |
| GET | `/api/profile/login-history` | 查询我的登录历史 |
| GET | `/api/profile/preferences` | 获取个人偏好 |
| PUT | `/api/profile/preferences` | 更新个人偏好 |

### 更新个人偏好

```json
{
  "tableDensity": "DEFAULT",
  "defaultHomePath": "/home",
  "themeMode": "SYSTEM",
  "pageSize": 20
}
```

### 会话管理说明

- 单设备撤销不能撤销当前会话。
- 修改密码和退出全部设备成功后，前端必须跳转登录页。
- 会话接口不会返回 Refresh Token 原文。

---

## 通知中心接口

用户侧接口均要求已登录，且只能操作当前认证用户自己的通知；管理侧接口仅 `ADMIN` 可用。

### 用户侧

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/notifications/my` | 查询我的通知，支持 `page`、`size`、`readStatus` |
| GET | `/api/notifications/unread-count` | 查询未读通知数 |
| GET | `/api/notifications/events` | SSE 轻事件流，推送 `connected`、`unreadCountChanged`、`newNotification`；前端断开后回退轮询 |
| POST | `/api/notifications/{messageId}/read` | 标记单条通知已读 |
| POST | `/api/notifications/read-all` | 标记全部通知已读 |
| POST | `/api/notifications/{messageId}/archive` | 归档单条通知 |
| GET | `/api/notifications/preferences` | 查询通知偏好 |
| PUT | `/api/notifications/preferences` | 更新通知偏好 |
| GET | `/api/notifications/mini-program/subscriptions` | 查询当前用户小程序订阅模板、openid 绑定、Provider 配置和授权次数 |
| POST | `/api/notifications/mini-program/subscriptions` | 上报当前用户点击微信订阅授权后的模板授权结果 |

小程序订阅授权上报示例：

```json
{
  "results": [
    {
      "notificationType": "PRICE_PUBLISHED",
      "templateId": "template-id-from-backend",
      "result": "accept"
    }
  ]
}
```

后端只使用当前认证用户 ID 和 `sys_user.wechat_openid`，客户端不得传入 userId。`templateId` 必须来自后端订阅状态接口返回的已配置模板；授权结果为 `accept` 时累计一次可用授权次数，`reject` / `ban` 会清空对应模板可用次数。

### 管理侧

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/notifications/dashboard` | 查询通知指标看板：今日消息、投递结果、Outbox 积压、重试数、最长待投递时间、渠道指标、高频类型 |
| GET | `/api/admin/notifications/providers/health` | 查询 Provider 注册/配置状态、待投递数、失败数、连续失败数和健康状态 |
| GET | `/api/admin/notifications/throttle-rules` | 查询聚合频控规则和当前窗口内触发情况 |
| GET | `/api/admin/notifications` | 分页查询全局通知消息 |
| GET | `/api/admin/notifications/{id}` | 查询通知详情 |
| GET | `/api/admin/notifications/{id}/recipients` | 查询通知收件人，响应包含 `userId`、`username`、`nickname`、`readStatus` 等字段，管理端收件人清单优先展示 `nickname` / `username` |
| GET | `/api/admin/notifications/{id}/deliveries` | 查询投递日志 |
| POST | `/api/admin/notifications/deliveries/{id}/retry` | 重试投递记录 |
| GET/PUT | `/api/admin/notifications/channels/MINI_PROGRAM/config` | 查询/保存小程序渠道配置；保存时 `templates=null` 表示不变，`templates=[]` 表示清空模板集合，模板项 `clearTemplateId=true` 表示清空该类型模板 ID |
| GET | `/api/admin/notifications/mini-program/coverage?roles=ADMIN,EDITOR` | 小程序发布前触达预估；`roles` 由后端显式拆分并校验 |
| GET | `/api/admin/notifications/mini-program/subscriptions/{userId}` | 查询订阅授权运维详情，包含模板状态、最近投递、失败原因、用户偏好和处理记录 |
| POST | `/api/admin/notifications/mini-program/subscriptions/{userId}/resolve` | 标记已处理、暂不提醒、记录备注或生成跟进标记 |
| POST | `/api/admin/notifications/channels/MINI_PROGRAM/test-token` | 远程校验微信 access_token 配置，不返回 token 明文 |
| POST | `/api/admin/notifications/channels/MINI_PROGRAM/test-delivery` | 对指定用户和通知类型创建受控测试投递 |

---

## 产品接口

### 获取产品列表

```
GET /api/products
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 0 | 页码（从0开始） |
| size | int | 否 | 10 | 每页数量 |
| keyword | string | 否 | - | 搜索关键字 |
| categoryId | long | 否 | - | 分类ID |
| status | string | 否 | - | 状态：ACTIVE / INACTIVE |
| sortBy | string | 否 | id | 排序字段 |
| sortDirection | string | 否 | asc | 排序方向：asc / desc |

**响应：** 分页产品列表

### 获取产品详情

```
GET /api/products/{id}
```

**权限：** ADMIN / EDITOR / VIEWER

### 创建产品

```
POST /api/products
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
{
  "name": "产品名称",
  "spec": "规格型号",
  "unit": "吨",
  "categoryId": 1,
  "originId": 1,
  "status": "ACTIVE",
  "sortOrder": 0
}
```

### 更新产品

```
PUT /api/products/{id}
```

**权限：** ADMIN / EDITOR

### 删除产品

```
DELETE /api/products/{id}
```

**权限：** ADMIN

### 批量更新排序

```
POST /api/products/batch-sort
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
[
  { "id": 1, "sortOrder": 0 },
  { "id": 2, "sortOrder": 1 }
]
```

---

## 价格接口

### 获取产品价格历史

```
GET /api/products/{productId}/price-history
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取产品当前价格

```
GET /api/products/{productId}/current-price
```

**权限：** ADMIN / EDITOR / VIEWER

### 按日期获取价格列表

```
GET /api/prices/by-date
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| date | date | 否 | 今天 | 日期，格式 yyyy-MM-dd |

### 按日期获取价格列表（带统计）

```
GET /api/prices/by-date-with-stats
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| date | date | 否 | 今天 | 日期 |

**响应：** 包含昨日价格和月均价

### 获取产品指定日期价格

```
GET /api/products/{productId}/price-by-date?date=2026-05-29
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取产品昨日价格

```
GET /api/products/{productId}/yesterday-price
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取产品月均价格

```
GET /api/products/{productId}/monthly-average-price
```

**权限：** ADMIN / EDITOR / VIEWER

### 添加产品价格

```
POST /api/products/{productId}/prices
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
{
  "price": 100.50,
  "priceDate": "2026-05-29",
  "remark": "备注"
}
```

### 更新价格

```
PUT /api/prices/{id}
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
{
  "price": 105.00,
  "priceDate": "2026-05-29",
  "remark": "更新备注"
}
```

### 清理重复价格数据

```
POST /api/prices/cleanup-duplicates
```

**权限：** ADMIN

### 获取产品价格走势

```
GET /api/products/{productId}/price-trend
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| days | int | 否 | 30 | 天数（30/180/365） |
| startDate | date | 否 | - | 精确开始日期；传入后优先于 `days` 计算的开始日期 |
| endDate | date | 否 | 当天 | 结束日期 |

查看某个自然年时，传入该年的 `startDate=YYYY-01-01` 与 `endDate=YYYY-12-31`；查看当前年时，`endDate` 使用当天。

### 获取产品价格录入年份

```
GET /api/products/{productId}/price-years
```

**权限：** ADMIN / EDITOR / VIEWER

返回该产品正式价格记录中实际存在的年份，按年份倒序排列；不返回无数据年份，也不限制历史年限。

---

## 价格草稿接口

价格维护页采用“保存草稿 / 发布全部草稿”双阶段。保存只写当前日期草稿；发布会发布全系统所有 `DRAFT` 草稿，不受当前页面日期限制。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/price-drafts/by-date?date=yyyy-MM-dd` | ADMIN / EDITOR | 查询指定日期活动价格草稿 |
| GET | `/api/price-drafts/publishable-summary` | ADMIN / EDITOR | 查询全系统待发布 DRAFT 草稿汇总 |
| POST | `/api/price-drafts/batch-save` | ADMIN / EDITOR | 批量保存当前日期价格草稿 |
| POST | `/api/price-drafts/publish-all` | ADMIN / EDITOR | 发布全系统所有 DRAFT 草稿，使价格正式生效 |
| POST | `/api/price-drafts/by-date/publish?date=yyyy-MM-dd` | ADMIN / EDITOR | 按日期发布 DRAFT 草稿；仅用于定时任务、补发或维护入口 |
| POST | `/api/price-drafts/{batchId}/publish` | ADMIN / EDITOR | 兼容/维护用单批次发布接口 |

### 待发布草稿汇总

```http
GET /api/price-drafts/publishable-summary
Authorization: Bearer <accessToken>
```

响应 `data` 示例：

```json
{
  "hasPublishableDrafts": true,
  "publishableBatchCount": 4,
  "publishableItemCount": 80,
  "publishableDateCount": 3,
  "effectiveDates": ["2026-06-12", "2026-06-13", "2026-06-14"],
  "publishableBatchIds": [101, 102, 103, 104]
}
```

### 发布全部草稿

```http
POST /api/price-drafts/publish-all
Authorization: Bearer <accessToken>
```

响应 `data` 会返回 `successCount`、`failCount`、`publishGroupId`、`publishLogIds` 和 `batchResults`。发布成功后生成一条发布组通知；无 DRAFT 草稿时返回业务错误“暂无可发布草稿”。

---

## 价格查询接口

### 查询价格列表

```
GET /api/price-query
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| date | date | 否 | - | 日期 |
| keyword | string | 否 | - | 搜索关键字 |
| categoryId | long | 否 | - | 分类ID |
| status | string | 否 | - | 状态 |
| page | int | 否 | 0 | 页码 |
| size | int | 否 | 20 | 每页数量 |
| sortBy | string | 否 | - | 排序字段 |
| sortDirection | string | 否 | asc | 排序方向 |

**响应指标字段：**

| 字段 | 说明 |
|------|------|
| `latestPrice`, `latestPriceDate` | 截至查询日期的最新有效价格及日期 |
| `previousPrice`, `previousPriceDate` | 最新有效价格之前的上期有效价格及日期，跳过无记录日期 |
| `previousChangeAmount`, `previousChangePercent` | 最新价格较上期有效价格的差额及差异率 |
| `budgetPrice`, `budgetChangeAmount`, `budgetChangePercent` | 最新有效价格日所属年度预算价及预算偏差 |
| `monthlyAveragePrice`, `previousMonthAveragePrice`, `monthOverMonthPercent` | 最新有效价格日所在月累计均价、上个自然月均价及环比 |
| `lastYearSamePeriodAveragePrice`, `yearOverYearPercent` | 以上年同月对应最新有效价格日为截止日的均价及同比 |

最新有效价格会排除在查询日已显式过期的价格。指标分组、名称、排序、启停和说明分别由 `price_metric_group`、`price_metric` 字典维护。`yesterdayPrice`、`changeAmount`、`changePercent` 为 deprecated 的 v1 历史兼容字段，分别始终等于 `budgetPrice`、`budgetChangeAmount`、`budgetChangePercent`；内部页面和导出不再使用这些字段。

### 导出价格查询结果

```
GET /api/price-query/export
```

**权限：** ADMIN / EDITOR / VIEWER

**响应：** Excel 文件下载

---

## 首页仪表盘接口

### 获取仪表盘数据

```
GET /api/home/dashboard
```

**权限：** 已登录用户

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| date | date | 否 | 昨天 | 日期 |

### 获取摘要统计

```
GET /api/home/summary
```

**权限：** 已登录用户

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| date | date | 否 | 昨天 | 日期 |

### 获取价格预警

```
GET /api/home/alerts
```

**权限：** 已登录用户

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| date | date | 否 | 昨天 | 日期 |

### 获取趋势分析

```
GET /api/home/trend
```

**权限：** 已登录用户

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| date | date | 否 | 昨天 | 日期 |
| days | int | 否 | 30 | 天数 |

### 获取首页产品排序

```
GET /api/home/product-order
```

**权限：** ADMIN / EDITOR / VIEWER

---

## 分类接口

### 获取分类列表

```
GET /api/categories
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 否 | 状态过滤，传 ACTIVE 获取启用项 |

### 获取分类详情

```
GET /api/categories/{id}
```

**权限：** ADMIN / EDITOR / VIEWER

### 创建分类

```
POST /api/categories
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
{
  "name": "分类名称",
  "status": "ACTIVE",
  "sortOrder": 0
}
```

### 更新分类

```
PUT /api/categories/{id}
```

**权限：** ADMIN / EDITOR

### 删除分类

```
DELETE /api/categories/{id}
```

**权限：** ADMIN

### 批量更新分类排序

```
POST /api/categories/batch-sort
```

**权限：** ADMIN / EDITOR

---

## 产地接口

### 获取产地列表

```
GET /api/origins
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 否 | 状态过滤 |

### 获取产地详情

```
GET /api/origins/{id}
```

**权限：** ADMIN / EDITOR / VIEWER

### 创建产地

```
POST /api/origins
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
{
  "name": "产地名称",
  "status": "ACTIVE"
}
```

### 更新产地

```
PUT /api/origins/{id}
```

**权限：** ADMIN / EDITOR

### 删除产地

```
DELETE /api/origins/{id}
```

**权限：** ADMIN

---

## 客户接口

### 获取客户列表

```
GET /api/customers
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 否 | 状态过滤 |

### 获取客户详情

```
GET /api/customers/{id}
```

**权限：** ADMIN / EDITOR / VIEWER

### 创建客户

```
POST /api/customers
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
{
  "name": "客户名称",
  "contact": "联系人",
  "phone": "联系电话",
  "status": "ACTIVE"
}
```

### 更新客户

```
PUT /api/customers/{id}
```

**权限：** ADMIN / EDITOR

### 删除客户

```
DELETE /api/customers/{id}
```

**权限：** ADMIN

---

## 字典接口

### 获取字典列表

```
GET /api/dict
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| category | string | 否 | 字典分类 |

### 获取启用的字典项

```
GET /api/dict/active?category=CATEGORY_NAME
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取字典分类列表

```
GET /api/dict/categories
```

**权限：** ADMIN / EDITOR / VIEWER

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| all | boolean | 否 | false | true=全部，false=仅启用 |

### 获取字典项详情

```
GET /api/dict/{id}
```

**权限：** ADMIN / EDITOR / VIEWER

### 创建字典项

```
POST /api/dict
```

**权限：** ADMIN / EDITOR

**请求体：**
```json
{
  "category": "分类编码",
  "dictKey": "字典键",
  "dictValue": "字典值",
  "sortOrder": 0,
  "status": "ACTIVE"
}
```

### 批量创建字典项

```
POST /api/dict/batch
```

**权限：** ADMIN

**请求体：** 字典项数组

### 更新字典项

```
PUT /api/dict/{id}
```

**权限：** ADMIN / EDITOR

### 删除字典项

```
DELETE /api/dict/{id}
```

**权限：** ADMIN

---

## 用户接口

### 获取用户列表

```
GET /api/users
```

**权限：** ADMIN

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 0 | 页码 |
| size | int | 否 | 20 | 每页数量 |
| keyword | string | 否 | - | 搜索关键字 |
| role | string | 否 | - | 角色过滤 |
| status | string | 否 | - | 状态过滤 |
| deptId | long | 否 | - | 部门ID |

### 获取所有用户（不分页）

```
GET /api/users/all
```

**权限：** ADMIN

### 获取用户详情

```
GET /api/users/{id}
```

**权限：** ADMIN

### 获取用户权限列表

```
GET /api/users/{id}/permissions
```

**权限：** ADMIN

### 获取用户角色列表

```
GET /api/users/{id}/roles
```

**权限：** ADMIN

### 批量获取用户角色映射

```
GET /api/users/roles-batch?ids=1,2,3
```

**权限：** ADMIN

### 创建用户

```
POST /api/users
```

**权限：** ADMIN

**请求体：**
```json
{
  "username": "用户名",
  "password": "密码",
  "employeeId": "工号",
  "nickname": "昵称",
  "email": "email@example.com",
  "phone": "13800000000",
  "role": "VIEWER",
  "deptId": 1,
  "status": "ACTIVE"
}
```

### 更新用户

```
PUT /api/users/{id}
```

**权限：** ADMIN

### 删除用户

```
DELETE /api/users/{id}
```

**权限：** ADMIN

### 重置用户密码

```
POST /api/users/{id}/reset-password
```

**权限：** ADMIN

**JSON 请求体：**

```json
{
  "newPassword": "Password123"
}
```

`newPassword` 可省略，此时使用服务端默认密码。密码禁止通过 URL 查询参数传输。

### 管理员原子编辑用户

```
PUT /api/users/{id}/admin-edit
```

一次事务内更新基础资料、状态和可选新密码。`deptId: null` 表示显式清空部门关联；
省略字段表示保持原值。

### 锁定用户

```
POST /api/users/{id}/lock
```

**权限：** ADMIN

### 解锁用户

```
POST /api/users/{id}/unlock
```

**权限：** ADMIN

---

## 角色接口

### 获取角色列表

```
GET /api/roles
```

**权限：** ADMIN

### 获取启用的角色列表

```
GET /api/roles/active
```

**权限：** 已登录用户

### 获取角色详情

```
GET /api/roles/{id}
```

**权限：** ADMIN

### 获取角色权限ID列表

```
GET /api/roles/{id}/permissions
```

**权限：** ADMIN

### 为角色分配权限

```
PUT /api/roles/{id}/permissions
```

**权限：** ADMIN

**请求体：** 权限ID数组 `[1, 2, 3]`

### 创建角色

```
POST /api/roles
```

**权限：** ADMIN

**请求体：**
```json
{
  "roleCode": "ROLE_CODE",
  "roleName": "角色名称",
  "description": "角色描述",
  "status": "ACTIVE"
}
```

### 更新角色

```
PUT /api/roles/{id}
```

**权限：** ADMIN

### 删除角色

```
DELETE /api/roles/{id}
```

**权限：** ADMIN

### 为用户分配角色

```
POST /api/roles/assign/{userId}
```

**权限：** ADMIN

**请求体：** 角色ID数组 `[1, 2]`

---

## 权限接口

### 获取所有权限列表

```
GET /api/permissions
```

**权限：** 已登录用户

### 获取权限树

```
GET /api/permissions/tree
```

**权限：** 已登录用户

---

## 部门接口

### 获取部门树

```
GET /api/departments/tree
```

**权限：** 已登录用户

### 获取部门列表

```
GET /api/departments
```

**权限：** 已登录用户

### 获取部门详情

```
GET /api/departments/{id}
```

**权限：** 已登录用户

### 创建部门

```
POST /api/departments
```

**权限：** ADMIN

**请求体：**
```json
{
  "deptCode": "DEPT001",
  "deptName": "部门名称",
  "parentId": null,
  "sortOrder": 0,
  "status": "ACTIVE"
}
```

### 更新部门

```
PUT /api/departments/{id}
```

**权限：** ADMIN

### 移动部门

```
PUT /api/departments/{id}/move?parentId=新父部门ID
```

**权限：** ADMIN

### 批量排序部门

```
PUT /api/departments/sort
```

**权限：** ADMIN

**请求体：** 部门ID数组，按顺序排列 `[1, 2, 3]`

### 删除部门

```
DELETE /api/departments/{id}
```

**权限：** ADMIN

---

## 菜单接口

### 获取菜单树

```
GET /api/menus/tree
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取可见菜单

```
GET /api/menus/visible?role=ADMIN
```

**权限：** 已登录用户

### 获取所有菜单

```
GET /api/menus
```

**权限：** ADMIN

### 获取菜单详情

```
GET /api/menus/{id}
```

**权限：** ADMIN

### 创建菜单

```
POST /api/menus
```

**权限：** ADMIN

**请求体：**
```json
{
  "title": "菜单标题",
  "path": "/path",
  "icon": "icon-name",
  "parentId": null,
  "sortOrder": 0,
  "visible": true,
  "roles": ["ADMIN", "EDITOR"]
}
```

### 更新菜单

```
PUT /api/menus/{id}
```

**权限：** ADMIN

### 删除菜单

```
DELETE /api/menus/{id}
```

**权限：** ADMIN

### 批量更新菜单排序

```
POST /api/menus/batch-sort
```

**权限：** ADMIN

### 初始化默认菜单

```
POST /api/menus/init
```

**权限：** ADMIN

---

## 操作日志接口

### 分页查询操作日志

```
GET /api/logs
```

**权限：** ADMIN

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 0 | 页码 |
| size | int | 否 | 10 | 每页数量 |
| username | string | 否 | - | 用户名 |
| operationType | string | 否 | - | 操作类型 |
| operationModule | string | 否 | - | 操作模块 |
| startTime | datetime | 否 | - | 开始时间 |
| endTime | datetime | 否 | - | 结束时间 |

### 获取最近操作日志

```
GET /api/logs/recent?limit=10
```

**权限：** ADMIN

### 按用户ID查询日志

```
GET /api/logs/user/{userId}
```

**权限：** ADMIN

### 按操作类型查询日志

```
GET /api/logs/type/{operationType}
```

**权限：** ADMIN

### 获取日志统计

```
GET /api/logs/statistics
```

**权限：** ADMIN

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| startTime | datetime | 否 | 30天前 | 开始时间 |
| endTime | datetime | 否 | 现在 | 结束时间 |

### 获取月度报表

```
GET /api/logs/reports/monthly?year=2026&month=5
```

**权限：** ADMIN

### 获取年度报表

```
GET /api/logs/reports/yearly?year=2026
```

**权限：** ADMIN

---

## 导入导出接口

### 导入产品

```
POST /api/import/products
```

**权限：** ADMIN / EDITOR

**请求：** multipart/form-data，文件字段名 `file`

### 导出产品

```
GET /api/import/products
```

**权限：** ADMIN / EDITOR / VIEWER

**响应：** Excel 文件下载

### 导入用户

```
POST /api/import/users
```

**权限：** ADMIN

**请求：** multipart/form-data，文件字段名 `file`

导入前会全量检查模板表头、字段格式、密码策略、文件内重复以及数据库中的用户名/工号重复。
只有全部检查通过才会在单一事务中写入；存在任一异常时不会导入任何用户。

**成功响应：**
```json
{
  "code": 200,
  "message": "用户导入成功，共 10 条",
  "data": {
    "valid": true,
    "imported": true,
    "totalRows": 10,
    "importedCount": 10,
    "errors": []
  }
}
```

**预检失败响应（HTTP 400）：**
```json
{
  "code": 400,
  "message": "用户导入预检未通过",
  "data": {
    "valid": false,
    "imported": false,
    "totalRows": 2,
    "importedCount": 0,
    "errors": [
      {
        "rowNumber": 3,
        "field": "username",
        "code": "DUPLICATE_USERNAME_IN_FILE",
        "message": "Excel 中存在重复用户名"
      }
    ]
  }
}
```

写入阶段发生并发唯一约束冲突时返回 HTTP 409。默认最大导入数据行数为 1000，可通过
`USER_IMPORT_MAX_ROWS` 调整。

### 导出用户

```
GET /api/import/users
```

**权限：** ADMIN

**响应：** Excel 文件下载

### 下载用户导入模板

```
GET /api/import/users/template
```

**权限：** ADMIN

**响应：** 仅包含标准表头的 Excel 模板文件下载

---

## 样式配置接口

### 获取样式配置

```
GET /api/style/config
```

**权限：** 已登录用户

### 获取预设主题

```
GET /api/style/themes
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取所有预设

```
GET /api/style/presets
```

**权限：** ADMIN / EDITOR / VIEWER

**响应：**
```json
{
  "code": 200,
  "data": {
    "colorSchemes": [...],
    "layoutStyles": [...],
    "fontPresets": [...]
  }
}
```

### 获取色彩方案

```
GET /api/style/color-schemes
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取布局方案

```
GET /api/style/layout-styles
```

**权限：** ADMIN / EDITOR / VIEWER

### 获取字号预设

```
GET /api/style/font-presets
```

**权限：** ADMIN / EDITOR / VIEWER

### 更新样式配置

```
PUT /api/style/config
```

**权限：** ADMIN

### 切换主题

```
PUT /api/style/theme/{themeKey}
```

**权限：** ADMIN

### 切换色彩方案

```
PUT /api/style/color-scheme/{schemeKey}
```

**权限：** ADMIN

### 切换布局方案

```
PUT /api/style/layout-style/{layoutKey}
```

**权限：** ADMIN

### 切换字号预设

```
PUT /api/style/font-preset/{presetKey}
```

**权限：** ADMIN

### 上传Logo

```
POST /api/style/logo
```

**权限：** ADMIN

**请求：** multipart/form-data，文件字段名 `file`

### 上传登录页Logo

```
POST /api/style/logo/login
```

**权限：** ADMIN

**请求：** multipart/form-data，文件字段名 `file`

### 上传导航栏Logo

```
POST /api/style/logo/nav
```

**权限：** ADMIN

**请求：** multipart/form-data，文件字段名 `file`

### 获取版本列表

```
GET /api/style/versions
```

**权限：** ADMIN

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 0 | 页码 |
| size | int | 否 | 10 | 每页数量 |

### 获取版本详情

```
GET /api/style/versions/{versionId}
```

**权限：** ADMIN

### 回滚到指定版本

```
POST /api/style/rollback/{versionId}
```

**权限：** ADMIN

---

## 审批流程接口

### 工作流管理

#### 获取所有工作流

```
GET /api/approvals/workflows
```

**权限：** ADMIN

#### 获取激活的工作流

```
GET /api/approvals/workflows/active
```

**权限：** 已登录用户

#### 获取工作流详情

```
GET /api/approvals/workflows/{id}
```

**权限：** ADMIN

#### 创建工作流

```
POST /api/approvals/workflows
```

**权限：** ADMIN

**请求体：**
```json
{
  "workflowName": "工作流名称",
  "businessType": "PRODUCT_CREATE",
  "description": "描述",
  "status": "ACTIVE"
}
```

#### 更新工作流

```
PUT /api/approvals/workflows/{id}
```

**权限：** ADMIN

#### 删除工作流

```
DELETE /api/approvals/workflows/{id}
```

**权限：** ADMIN

#### 激活工作流

```
PUT /api/approvals/workflows/{id}/activate
```

**权限：** ADMIN

#### 停用工作流

```
PUT /api/approvals/workflows/{id}/deactivate
```

**权限：** ADMIN

### 节点管理

#### 获取工作流节点

```
GET /api/approvals/workflows/{workflowId}/nodes
```

**权限：** ADMIN

#### 添加审批节点

```
POST /api/approvals/workflows/{workflowId}/nodes
```

**权限：** ADMIN

**请求体：**
```json
{
  "nodeName": "节点名称",
  "nodeType": "APPROVAL",
  "approverRole": "ADMIN",
  "sortOrder": 0
}
```

#### 更新审批节点

```
PUT /api/approvals/nodes/{id}
```

**权限：** ADMIN

#### 删除审批节点

```
DELETE /api/approvals/nodes/{id}
```

**权限：** ADMIN

### 审批请求管理

#### 分页查询审批请求

```
GET /api/approvals/requests
```

**权限：** ADMIN

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | int | 否 | 0 | 页码 |
| size | int | 否 | 10 | 每页数量 |
| status | string | 否 | - | 状态：PENDING/APPROVED/REJECTED |
| businessType | string | 否 | - | 业务类型 |
| applicantId | long | 否 | - | 申请人ID |

#### 获取待我审批的请求

```
GET /api/approvals/requests/pending
```

**权限：** ADMIN / EDITOR

#### 获取我提交的审批请求

```
GET /api/approvals/requests/my
```

**权限：** 已登录用户

#### 获取审批请求详情

```
GET /api/approvals/requests/{id}
```

**权限：** 已登录用户

#### 创建审批请求

```
POST /api/approvals/requests
```

**权限：** 已登录用户

**请求体：**
```json
{
  "workflowId": 1,
  "businessType": "PRODUCT_CREATE",
  "businessId": 123,
  "businessData": "{...}"
}
```

#### 审批通过

```
PUT /api/approvals/requests/{id}/approve?comment=审批意见
```

**权限：** ADMIN / EDITOR

#### 审批拒绝

```
PUT /api/approvals/requests/{id}/reject?comment=拒绝原因
```

**权限：** ADMIN / EDITOR

#### 撤回审批请求

```
PUT /api/approvals/requests/{id}/cancel
```

**权限：** 已登录用户（仅限申请人）

#### 获取审批记录

```
GET /api/approvals/requests/{id}/records
```

**权限：** 已登录用户

---

## 错误码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或Token失效 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 调用示例

### cURL 示例

```bash
# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# 获取产品列表
curl -X GET http://localhost:8080/api/products \
  -H "Authorization: Bearer <token>"

# 创建产品
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"产品A","unit":"吨","categoryId":1}'
```

### JavaScript 示例

```javascript
// 登录
const loginRes = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'password' })
});
const { data } = await loginRes.json();
const token = data.accessToken;

// 获取产品列表
const productsRes = await fetch('/api/products', {
  headers: { 'Authorization': `Bearer ${token}` }
});
const products = await productsRes.json();
```

---

## 个人中心接口

当前登录用户自身的资料、安全、会话、偏好、登录历史等接口。

> 与"认证接口"区别：认证接口面向登录态获取 token；个人中心接口面向已登录用户管理自身账号。

### 获取个人资料

```
GET /api/profile
```

**权限：** 已登录用户

### 更新个人资料

```
PUT /api/profile
```

**权限：** 已登录用户

### 获取安全设置（含密码策略、最后登录信息）

```
GET /api/profile/security
```

### 修改密码

```
PUT /api/profile/password
```

**请求体：**

```json
{
  "oldPassword": "原密码",
  "newPassword": "新密码"
}
```

### 获取当前会话列表

```
GET /api/profile/sessions
```

### 注销指定会话

```
DELETE /api/profile/sessions/{sessionId}
```

### 获取登录历史

```
GET /api/profile/login-history
```

**查询参数：** `page`(默认0), `size`(默认20), `startDate`, `endDate`

### 获取操作日志（当前用户）

```
GET /api/profile/operation-logs
```

### 获取用户偏好

```
GET /api/profile/preferences
```

### 更新用户偏好

```
PUT /api/profile/preferences
```

**请求体：**

```json
{
  "theme": "dark",
  "language": "zh-CN",
  "notifications": {
    "email": true,
    "sms": false
  }
}
```

---

## 通知中心接口

面向已登录用户的通知拉取、SSE 实时推送、偏好设置。

### 拉取我的通知列表

```
GET /api/notifications/my
```

**查询参数：** `page`(默认0), `size`(默认20), `unread`(true/false), `category`

### 标记单条已读

```
PUT /api/notifications/{id}/read
```

### 全部标记已读

```
PUT /api/notifications/read-all
```

### 获取通知事件流（SSE 实时推送）

```
GET /api/notifications/events
```

> 该接口为 Server-Sent Events 长连接。客户端使用 `EventSource` 订阅。空闲超时 60 秒会自动重连（前端组件已实现自动重连）。

### 获取我的通知偏好

```
GET /api/notifications/preferences
```

### 更新我的通知偏好

```
PUT /api/notifications/preferences
```

### 获取通知未读数

```
GET /api/notifications/unread-count
```

### 删除单条通知

```
DELETE /api/notifications/{id}
```

---

## 管理员通知接口

通知管理后台接口，仅 `ADMIN` 角色可访问。

> 路由前缀：`/api/admin/notifications`

### 仪表盘

```
GET /api/admin/notifications/dashboard
```

### 服务商健康检查

```
GET /api/admin/notifications/providers/health
```

### 节流规则

```
GET /api/admin/notifications/throttle-rules
PUT /api/admin/notifications/throttle-rules
```

### 通知列表（管理员视角）

```
GET /api/admin/notifications
```

### 单条详情

```
GET /api/admin/notifications/{id}
```

### 创建通知

```
POST /api/admin/notifications
```

### 取消通知

```
POST /api/admin/notifications/{id}/cancel
```

### 重发通知

```
POST /api/admin/notifications/{id}/resend
```

### 获取接收人列表

```
GET /api/admin/notifications/{id}/recipients
```

### 获取投递日志

```
GET /api/admin/notifications/{id}/deliveries
```

### 小程序订阅管理

```
POST /api/admin/notifications/mini-program/authorization-guides
POST /api/admin/notifications/mini-program/authorization-guides/{userId}
```

---

## 系统公告接口

管理员发布/取消系统公告。

> 路由前缀：`/api/admin/system-notices`

### 公告列表

```
GET /api/admin/system-notices
```

### 发布公告

```
POST /api/admin/system-notices
```

### 取消公告

```
POST /api/admin/system-notices/{id}/cancel
```

---

## 定时任务接口

调度任务管理与执行日志。

> 路由前缀：`/api/scheduled-tasks`

### 任务列表

```
GET /api/scheduled-tasks
```

### 任务详情

```
GET /api/scheduled-tasks/{id}
```

### 创建任务

```
POST /api/scheduled-tasks
```

**请求体：**

```json
{
  "name": "每日价格汇总",
  "cron": "0 0 1 * * ?",
  "handler": "priceSummaryJob",
  "enabled": true
}
```

### 更新任务

```
PUT /api/scheduled-tasks/{id}
```

### 启/停任务

```
PUT /api/scheduled-tasks/{id}/enable
PUT /api/scheduled-tasks/{id}/disable
```

### 立即执行一次

```
POST /api/scheduled-tasks/{id}/run
```

### 删除任务

```
DELETE /api/scheduled-tasks/{id}
```

### 执行日志

```
GET /api/scheduled-tasks/{id}/logs
```

---

## 产品年度预算接口

按年度存储产品预算价格，供价格走势参考。

> 路由前缀：`/api/product-budgets`

### 预算列表（按产品+年份）

```
GET /api/product-budgets
```

**查询参数：** `productId`, `year`, `page`, `size`

### 创建/更新年度预算

```
POST /api/product-budgets
```

**请求体：**

```json
{
  "productId": 1,
  "year": 2026,
  "budgetPrice": 5500.00,
  "currency": "CNY",
  "remark": "年度目标价"
}
```

### 删除预算

```
DELETE /api/product-budgets/{id}
```

---

## API 授权管理接口

外部 API Key 的增删改查、服务开关、权限树。仅 `ADMIN` 角色。

> 路由前缀：`/api/api-keys`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-keys` | 列表（分页） |
| POST | `/api/api-keys` | 创建（返回一次性 App Secret） |
| GET | `/api/api-keys/{id}` | 详情 |
| PUT | `/api/api-keys/{id}` | 更新名称/描述 |
| PUT | `/api/api-keys/{id}/enable` | 启用 |
| PUT | `/api/api-keys/{id}/disable` | 停用 |
| PUT | `/api/api-keys/{id}/revoke` | 吊销（不可逆） |
| GET | `/api/api-keys/permissions/tree` | 权限树（用于绑定） |
| GET | `/api/api-keys/service-status` | 服务总开关 |
| PUT | `/api/api-keys/service-status` | 切换服务总开关 |

> 注意：列表权限树实际接口路径为 `/api/api-keys/permissions/tree`（归属 `ApiKeyController`），而 `/api/permissions/tree` 归属 `PermissionController`，用于内部角色权限。

---

## API 调用日志接口

外部 API 调用流水记录，仅 `ADMIN` 角色。

> 路由前缀：`/api/api-call-logs`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-call-logs` | 日志列表（分页） |
| GET | `/api/api-call-logs/statistics` | 统计概览（按时间/状态/密钥） |

---

**最后更新：2026-06-14**
