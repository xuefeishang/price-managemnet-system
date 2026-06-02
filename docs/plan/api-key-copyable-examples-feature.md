# API Key 可复制调用示例功能方案

## Context

当前 `/api-keys` 新增密钥弹窗已经实现“左侧接口列表 + 右侧接口说明”，但右侧说明仍偏笼统：

- 只有请求示例、响应示例、错误码、使用提示四段文本。
- 请求示例不是完整调用代码，外部系统开发者仍需要自己拼接签名、请求头、body hash、query 排序。
- 新增密钥前没有真实 `appId` / `appSecret`，只能展示占位模板；新增成功后虽然能看到 Secret，但没有顺手生成“带真实密钥的可运行代码”。
- 现有示例无法覆盖不同语言、不同端点、GET/POST/PUT/DELETE、query/body/导出流等差异。

本需求目标：在创建 API Key 时，用户既能理解接口用途，也能复制代码后尽量直接运行调用。

本方案按 9.5 分标准执行：优先解决真实联调中最容易卡住的 Windows 调用示例、参数结构不清、签名模板漂移和组件可维护性问题；暂不扩展到完整 SDK、OpenAPI 在线调试器或多语言包发布，避免范围失控。

## 需求评审

### 用户价值

1. 降低外部系统接入成本：调用方不需要重新理解 HMAC 签名细节。
2. 降低联调错误率：签名算法、canonical query、body hash 与后端测试向量保持一致。
3. 提升密钥创建后的闭环体验：创建成功后立刻给出带真实 `appId` 和一次性 `appSecret` 的可运行示例。

### 需求边界

本阶段应实现：

- API 权限详情中展示接口用途、参数结构、响应结构、错误码和使用注意事项。
- 每个端点至少提供 `Node.js`、`Java 25`、`Postman`、`PowerShell`、`curl` 五类可复制示例。
- 创建成功弹窗中，基于真实 `appId` / `appSecret` 和已授权接口生成“一次性可运行示例”。
- 示例代码内置签名计算、请求头生成、body hash 计算、query 排序。
- 示例中的 `baseUrl` 可配置，开发默认 `http://localhost:8080`，生产由页面输入或配置项带出。
- 前端示例生成逻辑必须独立成工具模块，并用后端文档测试向量校验签名一致性。

本阶段不做：

- 不保存明文 App Secret。
- 不在数据库存储真实密钥代码片段。
- 不做完整 SDK 包发布。
- 不让外部 API 使用内部 JWT。

### 架构决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| 项目结构 | 沿用当前 layer-first Spring Boot + Vue3 结构 | 与现有项目一致，改动范围可控 |
| API 客户端方式 | 继续使用现有 typed axios 封装 | 页面已接入 `src/api/apiKey.ts`，无须引入 OpenAPI codegen |
| 认证策略 | 内部 JWT 管理密钥，外部 API Key + HMAC 调用 | 与现有安全模型一致 |
| 实时能力 | 不需要 | 示例生成是静态/一次性动作 |
| 错误处理 | 沿用全局异常处理 + 前端 toast/form error | 与现有后台页面一致 |

## 实现方案

### 1. 示例展示分两层

#### A. 权限选择阶段：模板示例

位置：新增密钥弹窗右侧接口详情。

展示内容：

- 接口用途：一句话说明该端点解决什么业务问题。
- 请求结构：方法、路径、query 参数、body 示例。
- 响应结构：成功响应、典型失败响应。
- 代码示例：语言 Tabs，提供 `Node.js`、`Java 25`、`Postman`、`PowerShell`、`curl`。
- 使用提示：限流、权限、导出流、写操作审计等注意事项。

此阶段使用占位符：

```text
APP_ID
APP_SECRET
BASE_URL
```

#### B. 创建成功阶段：可运行示例

位置：`createdSecret` 弹窗。

展示内容：

- 保留现有 App Secret 只展示一次的警告。
- 增加“选择一个已授权接口生成示例”。
- 代码自动带入本次返回的真实 `appId` 和 `appSecret`。
- 提供 `baseUrl` 输入框，默认本地开发地址，用户可改为生产地址。
- 点击复制后得到尽量可直接运行的代码。

安全约束：

- 代码只在前端内存中即时生成。
- 关闭弹窗后不保留 Secret。
- 不发送 Secret 到后端生成示例。

### 2. 数据模型设计

现有 `sys_external_api_endpoint` 已有：

| 字段 | 当前用途 |
|------|----------|
| `request_example` | 粗粒度请求示例 |
| `response_example` | 响应示例 |
| `error_codes` | 错误码 |
| `usage_notes` | 使用提示 |

建议新增结构化字段，避免把完整代码硬塞进数据库：

| 字段 | 类型 | 说明 |
|------|------|------|
| `query_example` | TEXT | 示例 query JSON，如 `{"page":0,"size":20}` |
| `body_example` | TEXT | 示例 body JSON，GET/DELETE 可为空 |
| `path_params_example` | TEXT | 路径变量示例，如 `{"id":1}` |
| `query_schema` | TEXT | query 参数轻量 schema，记录字段、类型、是否必填、说明 |
| `body_schema` | TEXT | body 参数轻量 schema，记录字段、类型、是否必填、说明 |
| `path_params_schema` | TEXT | 路径参数轻量 schema，记录字段、类型、是否必填、说明 |
| `success_example` | TEXT | 成功响应 JSON |
| `failure_example` | TEXT | 失败响应 JSON |
| `code_notes` | TEXT | 针对代码调用的注意事项 |

轻量 schema 使用 JSON 文本，不引入完整 OpenAPI。推荐结构：

```json
[
  {
    "name": "page",
    "type": "number",
    "required": false,
    "defaultValue": 0,
    "description": "页码，从 0 开始"
  },
  {
    "name": "size",
    "type": "number",
    "required": false,
    "defaultValue": 20,
    "description": "每页条数"
  }
]
```

不建议新增 `curl_example` / `javascript_example` 静态字段。原因：

- 签名算法一旦调整，多处静态代码需要同步修改。
- 不同环境 `baseUrl`、真实 `appId`、真实 `appSecret` 只能运行时注入。
- 端点新增后，模板生成比人工维护长代码更稳定。

### 3. 后端接口设计

#### 方案一：前端生成代码，后端只返回结构化元数据

扩展现有：

```http
GET /api/api-keys/permissions/tree
```

`ExternalApiEndpointDTO` 增加：

```json
{
  "queryExample": "{\"page\":0,\"size\":20}",
  "bodyExample": "{}",
  "pathParamsExample": "{}",
  "querySchema": "[{\"name\":\"page\",\"type\":\"number\",\"required\":false}]",
  "bodySchema": "[]",
  "pathParamsSchema": "[]",
  "successExample": "{\"code\":200,\"data\":{...}}",
  "failureExample": "{\"code\":401,\"message\":\"签名失败\"}",
  "codeNotes": "分页接口建议显式传 page 和 size"
}
```

优点：改动少，页面响应快。

风险：签名代码模板在前端维护，必须用测试向量校验。

#### 方案二：后端提供示例生成接口

新增内部管理接口：

```http
POST /api/api-keys/code-examples
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "endpointId": 1,
  "language": "javascript",
  "baseUrl": "http://localhost:8080",
  "appId": "APP_ID",
  "appSecret": "APP_SECRET"
}
```

返回：

```json
{
  "language": "javascript",
  "code": "...",
  "warnings": ["Secret 只应保存在服务端环境变量中"]
}
```

不推荐本阶段使用，因为会诱导把明文 Secret 发回后端，和“Secret 只展示一次”的安全模型冲突。

推荐选择：方案一。代码模板放前端，但签名测试向量来自后端 `ApiSignatureUtilTests` 和 `docs/dev/API调用手册.md`。

### 4. 前端交互设计

#### 右侧接口详情

布局建议：

- 顶部：接口名称、权限码、授权开关。
- 第一段：接口用途 + 方法路径。
- 第二段：请求/响应结构，左右并列或上下 Tabs。
- 第三段：代码示例 Tabs。
- 第四段：错误码和注意事项。

代码示例控件：

- Tabs：`Node.js`、`Java 25`、`Postman`、`PowerShell`、`curl`。
- 每段代码右上角有复制按钮。
- 复制成功显示轻量 toast。
- 代码块固定最大高度，避免撑爆弹窗。
- 参数结构区域展示字段名、类型、必填、默认值、说明；当 schema 为空时回退展示 example JSON。

#### 创建成功弹窗

新增区域：

- `baseUrl` 输入框。
- 端点下拉，只显示本次授权的权限下端点。
- 语言 Tabs。
- “复制可运行示例”按钮。

默认选择逻辑：

1. 优先选择第一个 GET 读取接口。
2. 如果只有写接口，选择第一个 POST/PUT。
3. 删除接口默认不作为首选，避免误复制高风险代码。

### 5. 代码模板设计

#### Node.js 示例要求

必须包含：

- `sha256Hex`
- `hmacSha256Hex`
- `canonicalQuery`
- `signRequest`
- `callApi`
- 示例调用入口

Node.js 示例使用 `node:crypto`，因为外部 API Secret 不应放在浏览器端。示例需支持 Node.js 18+ 原生 `fetch`；如果运行环境较旧，在代码注释中提示升级 Node 或引入 fetch polyfill。

示例骨架：

```javascript
import crypto from 'node:crypto'

const APP_ID = 'APP_ID'
const APP_SECRET = 'APP_SECRET'
const BASE_URL = 'http://localhost:8080'

function sha256Hex(body) {
  return crypto.createHash('sha256').update(body, 'utf8').digest('hex')
}

function hmacSha256Hex(secret, text) {
  return crypto.createHmac('sha256', secret).update(text, 'utf8').digest('hex')
}

function canonicalQuery(params) {
  return Object.entries(params)
    .flatMap(([key, value]) => Array.isArray(value) ? value.map(item => [key, item]) : [[key, value]])
    .sort(([aKey, aValue], [bKey, bValue]) => aKey.localeCompare(bKey) || String(aValue).localeCompare(String(bValue)))
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
}
```

#### PowerShell 示例要求

PowerShell 是本项目本地开发和企业 Windows 环境的一等示例，不放到第二阶段。

必须包含：

- UTF-8 SHA-256 body hash。
- HMAC-SHA256 签名。
- query 排序。
- `Invoke-RestMethod` 调用。

示例骨架：

```powershell
$AppId = "APP_ID"
$AppSecret = "APP_SECRET"
$BaseUrl = "http://localhost:8080"
$Method = "GET"
$Path = "/api/external/v1/products"
$Query = "page=0&size=20"
$Body = ""
$Timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
$Nonce = [Guid]::NewGuid().ToString("N")

function Get-Sha256Hex([string]$Text) {
  $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
  $hash = [Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
  return -join ($hash | ForEach-Object { $_.ToString("x2") })
}

function Get-HmacSha256Hex([string]$Secret, [string]$Text) {
  $key = [Text.Encoding]::UTF8.GetBytes($Secret)
  $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
  $hmac = [Security.Cryptography.HMACSHA256]::new($key)
  $hash = $hmac.ComputeHash($bytes)
  return -join ($hash | ForEach-Object { $_.ToString("x2") })
}
```

#### curl 示例要求

纯 curl 无法独立计算 HMAC。`curl` 示例定位为 Linux/macOS Shell 示例，使用 “openssl + curl” 组合：

```bash
APP_ID="..."
APP_SECRET="..."
BASE_URL="http://localhost:8080"
METHOD="GET"
PATH="/api/external/v1/products"
QUERY="page=0&size=20"
BODY=""
TIMESTAMP="$(date +%s)"
NONCE="$(openssl rand -hex 16)"
BODY_SHA256="$(printf "%s" "$BODY" | openssl dgst -sha256 -hex | awk '{print $2}')"
CANONICAL="${METHOD}
${PATH}
${QUERY}
${TIMESTAMP}
${NONCE}
${BODY_SHA256}"
SIGNATURE="$(printf "%s" "$CANONICAL" | openssl dgst -sha256 -hmac "$APP_SECRET" -hex | awk '{print $2}')"

curl -X "$METHOD" "$BASE_URL$PATH?$QUERY" \
  -H "X-App-Id: $APP_ID" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Nonce: $NONCE" \
  -H "X-Signature: $SIGNATURE"
```

Windows 环境默认推荐复制 PowerShell 示例，不建议复制 Linux/macOS Shell 示例。

### 6. 前端实现设计

示例生成逻辑必须从 `ApiKeyList.vue` 中拆出，避免弹窗组件继续膨胀。

新增工具文件：

```text
frontend/src/utils/externalApiCodeExamples.ts
```

建议导出：

```typescript
export type CodeExampleLanguage = 'node' | 'java' | 'postman' | 'powershell' | 'curl'

export interface CodeExampleInput {
  endpoint: ExternalApiEndpoint
  baseUrl: string
  appId: string
  appSecret: string
  usePlaceholders?: boolean
}

export function buildNodeExample(input: CodeExampleInput): string
export function buildPowerShellExample(input: CodeExampleInput): string
export function buildCurlExample(input: CodeExampleInput): string
export function buildCanonicalQuery(params: Record<string, unknown>): string
export async function buildBrowserHmacSha256Hex(secret: string, text: string): Promise<string>
```

页面组件只负责：

- 选择 endpoint。
- 选择语言。
- 传入 `baseUrl/appId/appSecret` 或占位符。
- 展示代码和复制。

工具模块负责：

- 替换路径变量。
- 构造 query。
- 序列化 body。
- 生成不同语言代码。
- 提供签名测试向量校验能力。

### 7. 示例内容标准

每个端点的示例必须满足：

- 路径变量有真实示例值，例如 `/products/{id}` 渲染为 `/products/1`。
- query 参数有真实示例值。
- query/body/path 参数 schema 至少包含字段名、类型、是否必填、说明。
- POST/PUT body 是业务字段，不包含 `id`、`createdTime`、`updatedTime`、`version` 等系统字段。
- DELETE 示例要显示高风险提示。
- 导出接口要说明响应是文件流，示例包含 `-o export.xlsx` 或 Blob 处理。
- 错误响应至少覆盖 401、403、429，写接口补充 400/409。

### 8. 安全设计

- App Secret 只在创建成功响应和成功弹窗中出现一次。
- “可运行示例”区域需要提示：生产中应把 `APP_SECRET` 放到服务端环境变量，不应写进前端页面或提交到 Git。
- 代码示例默认不包含真实生产域名。
- 复制操作不写入操作日志，避免日志记录 Secret。
- 如果后续增加“下载 SDK 示例文件”，文件生成也必须在前端内存中完成。
- 生成示例时不得把真实 `appSecret` 写入 localStorage、sessionStorage、URL、日志、操作记录或后端请求。

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `frontend/src/views/ApiKeyList.vue` | 新增密钥弹窗、权限工作台、创建成功弹窗 |
| `frontend/src/utils/externalApiCodeExamples.ts` | 统一生成 Node.js、Java 25、Postman、PowerShell、curl 示例 |
| `frontend/src/types/apiKey.ts` | 外部端点 DTO 类型 |
| `frontend/src/api/apiKey.ts` | API Key 管理接口 |
| `backend/src/main/java/com/pricemanagement/entity/ExternalApiEndpoint.java` | 外部端点实体 |
| `backend/src/main/java/com/pricemanagement/dto/ExternalApiEndpointDTO.java` | 外部端点响应 DTO |
| `backend/src/main/java/com/pricemanagement/service/ApiKeyService.java` | 权限树组装 |
| `backend/src/main/java/com/pricemanagement/util/ApiSignatureUtil.java` | 后端签名规则 |
| `backend/src/test/java/com/pricemanagement/util/ApiSignatureUtilTests.java` | 签名测试向量 |
| `docs/dev/API调用手册.md` | 外部调用规则说明 |

## 实现步骤

1. 梳理当前 `sys_external_api_endpoint` 所有端点，补齐每类接口的 query/body/path 示例。
2. 新增 Flyway 迁移 `V19__external_api_endpoint_code_examples.sql`，增加结构化示例字段和轻量 schema 字段并初始化数据。
3. 扩展 `ExternalApiEndpoint`、`ExternalApiEndpointDTO`、`ApiKeyService.toEndpointDTO`。
4. 扩展前端 `ExternalApiEndpoint` 类型。
5. 新增 `frontend/src/utils/externalApiCodeExamples.ts`，优先支持 Node.js、Java 25、Postman、PowerShell、curl。
6. 增加前端签名测试向量校验，确保模板与后端 `ApiSignatureUtilTests` 一致。
7. 右侧接口详情增加代码 Tabs、复制按钮、结构化请求/响应展示。
8. 创建成功弹窗增加真实密钥可运行示例区域。
9. 更新 `docs/dev/API调用手册.md`，把页面示例能力、PowerShell 示例和安全注意事项写入。
10. 更新数据字典、设计文档、UI 说明。

## Verification

### 后端验证

```bash
cd backend
mvn test
```

数据库验证：

```sql
SHOW COLUMNS FROM sys_external_api_endpoint;
SELECT COUNT(*) FROM sys_external_api_endpoint WHERE query_example IS NOT NULL OR body_example IS NOT NULL;
SELECT COUNT(*) FROM sys_external_api_endpoint WHERE query_schema IS NOT NULL OR body_schema IS NOT NULL OR path_params_schema IS NOT NULL;
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

### 前端验证

```bash
cd frontend
npm run build
```

页面验证：

1. 打开 `/api-keys`。
2. 新增密钥，选择任意读取接口。
3. 右侧说明能看到用途、参数 schema、请求结构、响应结构、代码示例。
4. 复制 Node.js 示例，替换占位符后可调用。
5. 复制 Java 25、Postman 或 PowerShell 示例，替换占位符后可调用。
6. 保存密钥后，在成功弹窗选择一个已授权接口，复制带真实 `appId` / `appSecret` 的示例。
7. 示例调用后，在 `/api-call-logs` 可看到调用日志。

### 签名一致性验证

前端模板生成的测试向量必须与后端一致：

| 项目 | 值 |
|------|-----|
| secret | `sec_test_1234567890` |
| method | `GET` |
| path | `/api/external/v1/products` |
| query | `page=0&size=20` |
| timestamp | `1779990000` |
| nonce | `nonce_test_001` |
| signature | `7221cc7d6fc7d2f6cde1c20d7cdf62aa9669e0c965fe9e91902efac24d4e37cf` |

前端工具模块必须能用同一组输入生成一致的 canonical query 和 signature。若项目当前未配置 Vitest，可先用轻量构建期函数或 Node 脚本验证；后续接入前端测试框架后迁移为单元测试。

### 通过标准

- 用户不看文档，仅通过新增密钥弹窗也能完成一次外部 API 调用。
- 创建成功后的 Node.js 示例，复制到本地服务端脚本中，在只调整 `baseUrl` 的情况下可运行。
- 创建成功后的 Java 25、Postman、PowerShell 示例，复制后可按对应环境运行。
- 前端不保存 Secret，后端不再次接收明文 Secret。
- 前端示例生成逻辑不堆在 `ApiKeyList.vue`，而是通过独立工具模块维护。
- 前端签名测试向量和后端 `ApiSignatureUtilTests` 一致。
- 新增迁移不修改已执行的 V17/V18。
