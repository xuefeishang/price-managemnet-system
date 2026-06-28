---
title: 认证 & 外部 API Key 签名
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/API调用手册.md
---

# 认证 & 外部 API Key 签名

本文档说明矿产品价格管理系统后端的两种认证机制：

- **JWT Bearer Token** —— 内部后台接口（前端、运营后台）
- **API Key + HMAC-SHA256** —— 外部系统对接（仅 `/api/external/v1/**`）

并给出公开路径清单、Refresh Token 机制与端点分类。

---

## 目录

- [JWT Bearer Token](#jwt-bearer-token)
- [公开路径](#公开路径)
- [Refresh Token 机制](#refresh-token-机制)
- [公开 / 受保护端点分类](#公开--受保护端点分类)
- [外部 API Key 签名认证](#外部-api-key-签名认证)
  - [启用条件](#启用条件)
  - [请求头](#请求头)
  - [Canonical String](#canonical-string)
  - [测试向量](#测试向量)
  - [JavaScript 签名示例](#javascript-签名示例)
  - [Java 25 调用示例](#java-25-调用示例)
  - [可复制调用示例](#可复制调用示例)
  - [安全要求](#安全要求)
  - [权限端点元数据](#权限端点元数据)

---

## JWT Bearer Token

内部后台接口使用 JWT Bearer Token 认证。登录成功后获取 `accessToken`，后续请求需在请求头中携带：

```http
Authorization: Bearer <accessToken>
```

**登录入口**：`POST /api/auth/login`（详见 [internal.md § 认证接口](./internal.md#认证接口)）。

**Token 类型**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | string | JWT，用于 `Authorization` 请求头，默认有效期较短 |
| `refreshToken` | string | 长效令牌，用于刷新 AccessToken（见下文） |
| `tokenType` | string | 固定 `Bearer` |
| `expiresIn` | int | 刷新接口返回的 AccessToken 剩余秒数 |
| `user` | object | 登录用户信息（id、username、nickname、role、roles 等） |
| `permissions` | string[] | 当前用户的权限编码列表 |

**前端调用约束**：

- 所有内部 API（除公开路径外）必须携带 `Authorization: Bearer <accessToken>`，缺失或失效返回 `401`。
- AccessToken 过期后必须使用 RefreshToken 调用 `POST /api/auth/refresh-token` 获取新 Token，**不得**捕获 401 后跳登录页（除非 RefreshToken 也失效）。
- 修改密码、退出全部设备成功后，前端必须清理本地 Token 并跳转登录页。

---

## 公开路径

以下接口**无需**携带 `Authorization` 即可访问（`permitAll`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/auth/captcha` | 获取登录验证码 |
| `POST` | `/api/auth/login` | 用户登录 |
| `POST` | `/api/auth/refresh-token` | 刷新 AccessToken |
| `GET` | `/api/menus/tree` | 公开的菜单树（用于登录前展示） |
| `GET` | `/api/dict/active` | 公开的启用字典项 |
| `GET` | `/api/style/config` | 公开的样式配置（登录页使用） |
| `GET` | `/api/style/themes`、`/api/style/color-schemes`、`/api/style/layout-styles`、`/api/style/font-presets` | 公开的样式预设 |
| `GET` | `/api/style/presets` | 公开的全部样式预设 |
| `GET` | `/api/external/v1/**` | 外部 API，使用 API Key 签名而非 JWT（见下文） |
| `GET` | `/actuator/health` | 健康检查（如启用） |
| `GET` | `/api/home/dashboard`、`/api/home/summary`、`/api/home/alerts`、`/api/home/trend` | 已登录用户开放（JWT） |
| `GET` | `/api/notifications/events` | SSE 事件流（需要 JWT） |

> 公开路径以外的 `/api/**` 端点均受保护，必须携带有效 JWT。

---

## Refresh Token 机制

### 获取

登录成功或刷新接口响应中同时返回 `accessToken` 和 `refreshToken`。前端必须**双 Token 持久化**（`localStorage` / `Pinia` / Vuex / Redux 任选）。

### 刷新

```
POST /api/auth/refresh-token
Content-Type: application/json

{ "refreshToken": "<refresh-token-string>" }
```

**成功响应**：

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

**典型失败**：

| 状态码 | 场景 |
|--------|------|
| 401 | RefreshToken 不存在、已过期、已撤销、对应用户不存在、账号禁用/锁定或无有效角色 |
| 429 | Refresh 触发限流 |

刷新时后端会重新校验 `sys_user.status`、`is_locked` 和有效角色。校验失败会撤销当前 RefreshToken，并统一返回 401，避免禁用或锁定账号继续续签 AccessToken。

### 撤销

以下行为会**撤销当前用户全部 Refresh Token**，下次刷新会失败：

- `PUT /api/profile/password` —— 修改密码成功后
- `DELETE /api/profile/sessions/all` —— 退出全部设备
- `DELETE /api/profile/sessions/{id}` —— 单设备撤销（不影响当前会话）

---

## 公开 / 受保护端点分类

| 类别 | 范围 |
|------|------|
| 完全公开（`permitAll`） | `/api/auth/captcha`、`/api/auth/login`、`/api/auth/refresh-token`、`/api/menus/tree`、`/api/dict/active`、所有 `/api/style/*` 公开项、`/actuator/health` |
| 已登录用户（需 JWT） | `/api/auth/profile`、`/api/auth/password`、`/api/auth/logout`、`/api/profile/**`、`/api/home/dashboard`、`/api/home/summary`、`/api/home/alerts`、`/api/home/trend`、`/api/notifications/events`、`/api/notifications/my` 等 |
| 角色受限（需 JWT + 角色） | 业务接口（产品/价格/分类/产地/客户/字典的写操作要求 ADMIN/EDITOR）、用户/角色/权限/部门/菜单/日志/导入导出/样式配置/审批/通知/系统公告/定时任务/API Key 等管理接口（要求 ADMIN） |
| 外部 API（API Key 签名） | `/api/external/v1/**` |

---

## 外部 API Key 签名认证

外部系统对接使用 API Key + HMAC-SHA256 签名认证，仅允许调用 `/api/external/v1/**`，**禁止**携带 `Authorization`，也**禁止**传输 `X-App-Secret` 明文头。

### 启用条件

外部 API 默认关闭。服务端必须配置：

| 环境变量 | 说明 |
|----------|------|
| `API_KEY_ENABLED=true` | 启用外部 API |
| `API_KEY_ENCRYPTION_KEY` | Base64 编码的 32 字节 AES-GCM 主密钥 |
| `API_KEY_ENCRYPTION_KEY_VERSION` | 主密钥版本，默认 `v1` |

启用后还受后台页面运行时开关控制：

- `API_KEY_ENABLED=false`：部署级关闭，外部接口始终不可用。
- `API_KEY_ENABLED=true` 且 API 授权管理页开关为"开启"：外部接口正常服务。
- `API_KEY_ENABLED=true` 但页面开关为"暂停"：所有 `/api/external/v1/**` 返回 `503`，认证结果为 `SERVICE_DISABLED`。

### 请求头

```http
X-App-Id: app_xxxxxxxxxxxxxxxx
X-Timestamp: 1779990000
X-Nonce: nonce_test_001
X-Signature: 7221cc7d6fc7d2f6cde1c20d7cdf62aa9669e0c965fe9e91902efac24d4e37cf
```

**禁止请求头**：

```http
X-App-Secret       # 明文 Secret 不得外传
Authorization      # 外部接口不使用 JWT
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

**规则**：

- 使用 `\n`（LF）连接 6 行。
- `canonicalPath` 使用请求 path，不含域名和 query。
- `canonicalQuery` 按 key 升序、value 升序排序后 URL 编码；无 query 时为空字符串。
- `bodySha256Hex` 对原始请求体字节计算 SHA-256。空 body 固定为 `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`。
- `X-Timestamp` 使用 Unix **秒**，默认允许服务器时间 ±300 秒。
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

**Canonical String**：

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

/** 构造并发送一个外部 API 请求 */
async function callExternalApi({ appId, appSecret, method, path, query = '', body = '' }) {
  const timestamp = Math.floor(Date.now() / 1000).toString()
  const nonce = 'nonce_' + crypto.randomUUID()
  const bodySha256Hex = await sha256Hex(body)

  const canonical = [
    method.toUpperCase(),
    path,
    query,
    timestamp,
    nonce,
    bodySha256Hex
  ].join('\n')

  const signature = await hmacSha256Hex(appSecret, canonical)

  const url = 'https://api.example.com' + path + (query ? '?' + query : '')
  const headers = {
    'X-App-Id': appId,
    'X-Timestamp': timestamp,
    'X-Nonce': nonce,
    'X-Signature': signature
  }
  if (body) headers['Content-Type'] = 'application/json'

  const resp = await fetch(url, { method, headers, body: body || undefined })
  return resp.json()
}
```

### Java 25 调用示例

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class ExternalApiClient {
    private static final String BASE = "https://api.example.com";
    private final HttpClient http = HttpClient.newHttpClient();

    public String callExternal(
            String appId, String appSecret,
            String method, String path, Map<String, String> query, String jsonBody) throws Exception {

        // 1. 规范化 query（key 升序、value 升序，URL 编码）
        String canonicalQuery = canonicalQuery(query);

        // 2. 计算 bodySha256Hex
        String bodySha256Hex = sha256Hex(jsonBody == null ? "" : jsonBody);

        // 3. 构造 timestamp / nonce
        long ts = System.currentTimeMillis() / 1000;
        String timestamp = Long.toString(ts);
        String nonce = "nonce_" + java.util.UUID.randomUUID();

        // 4. Canonical String（6 行，LF 连接）
        String canonical = String.join("\n",
                method.toUpperCase(),
                path,
                canonicalQuery,
                timestamp,
                nonce,
                bodySha256Hex);

        // 5. HMAC-SHA256 签名
        String signature = hmacSha256Hex(appSecret, canonical);

        // 6. 构造请求
        String url = BASE + path + (canonicalQuery.isEmpty() ? "" : "?" + canonicalQuery);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("X-App-Id", appId)
                .header("X-Timestamp", timestamp)
                .header("X-Nonce", nonce)
                .header("X-Signature", signature);
        HttpRequest req;
        if (jsonBody != null && !jsonBody.isEmpty()) {
            req = builder
                    .header("Content-Type", "application/json")
                    .method(method, BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
        } else {
            req = builder.method(method, BodyPublishers.noBody()).build();
        }
        return http.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String canonicalQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        TreeMap<String, String> sorted = new TreeMap<>(query);
        return sorted.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String urlEncode(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static String hmacSha256Hex(String secret, String canonical) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(sig);
    }
}
```

### 可复制调用示例

后台 `/api-keys` 新增密钥弹窗会基于 `sys_external_api_endpoint` 的结构化元数据生成 **Node.js、Java 25、Postman、PowerShell、curl** 五种示例。创建成功弹窗中的示例会临时带入真实 `App ID` 和一次性 `App Secret`，关闭后不再保留。

### 安全要求

- `App Secret` **只**应保存在外部系统服务端环境变量中。
- **不要**把 `App Secret` 写入浏览器前端、Git 仓库、URL、日志或操作记录。
- 示例代码中的 body 字符串会参与 SHA-256 和 HMAC 签名，实际发送内容必须与签名时内容**完全一致**（字节级）。
- Java 调用方优先复制 Java 25 示例，示例只使用 JDK 标准库 `HttpClient`、`Mac`、`MessageDigest`、`HexFormat`，不依赖第三方 SDK。
- Postman 调试时复制 Postman 示例，把 `base_url`、`app_id`、`app_secret` 填到 Environment，并把 Pre-request Script 粘贴到请求中。
- Windows 环境优先复制 PowerShell 示例；Linux/macOS 可使用 curl + openssl 示例。

### 权限端点元数据

`GET /api/api-keys/permissions/tree`（管理端接口，需 JWT + ADMIN）会返回外部 API 全部权限端点的元数据，用于管理页"代码生成 / 示例预览"功能：

| 字段 | 说明 |
|------|------|
| `queryExample` / `bodyExample` / `pathParamsExample` | 代码生成使用的示例参数 |
| `querySchema` / `bodySchema` / `pathParamsSchema` | 字段名、类型、是否必填、默认值和说明 |
| `successExample` / `failureExample` | 成功和失败响应示例 |
| `codeNotes` | 复制运行前的端点级注意事项 |
