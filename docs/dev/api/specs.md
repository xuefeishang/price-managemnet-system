---
title: API 通用规范
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/API调用手册.md
---

# API 通用规范

本文档定义矿产品价格管理系统后端 API 的通用约定：基础信息、响应格式、错误码、分页、路径与查询规范、调用示例。所有内部与外部接口均遵循本文档。

---

## 基础信息

| 项目 | 值 |
|------|-----|
| 基础 URL | `http://<服务器地址>:8080/api` |
| 协议 | HTTP / HTTPS |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |
| 时间格式 | `yyyy-MM-dd HH:mm:ss` |
| 日期格式 | `yyyy-MM-dd` |

> 内部接口与外部接口共用同一基础前缀 `/api`，外部接口统一追加 `/external/v1/**` 路径段以做命名空间隔离。

---

## 通用响应格式

所有接口（内部 + 外部）统一返回以下 JSON 结构：

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
| `code` | int | 业务态码：`200`=成功；`4xx`=客户端错误；`5xx`=服务端错误 |
| `message` | string | 响应消息。**默认成功文案为"操作成功"**（不是 `"success"`），各接口可自定义文案 |
| `data` | object | 响应数据，泛型；成功时承载业务负载，分页接口承载 `PageResponse` |
| `timestamp` | long | 服务端响应时间戳，**毫秒**（Long 类型） |

---

## 错误码说明

| HTTP 状态码 | 含义 | 典型场景 |
|-------------|------|----------|
| 200 | 成功 | 业务执行成功 |
| 400 | 请求参数错误 | 参数缺失、类型错误、字段格式不合法、导入预检未通过 |
| 401 | 未登录或 Token 失效 | 缺失 Authorization、AccessToken 过期、RefreshToken 无效 |
| 403 | 权限不足 | 角色不匹配、IP 白名单拒绝、签名失败 |
| 404 | 资源不存在 | id 不存在、菜单/部门/审批单/通知等资源缺失 |
| 409 | 资源冲突 | 用户名/工号唯一约束冲突、并发写冲突 |
| 429 | 请求过频 | 登录/外部 API 触发限流 |
| 500 | 服务器内部错误 | 业务异常、JPA 异常、未捕获异常 |

> 错误响应同样遵循"通用响应格式"：`code` 字段与 HTTP 状态码语义对齐，`message` 直接面向用户或开发者，`data` 可为空或承载校验错误明细。

---

## 分页响应格式

分页接口的 `data` 字段统一为 `PageResponse<T>`：

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

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | array | 当前页数据列表 |
| `totalElements` | long | 总记录数 |
| `totalPages` | int | 总页数 |
| `size` | int | 每页大小（与请求 `size` 一致） |
| `number` | int | 当前页码（**从 0 开始**，与请求 `page` 一致） |

---

## RESTful 路径规范

| 操作类型 | HTTP 方法 | 路径示例 | 备注 |
|----------|-----------|----------|------|
| 列表查询 | `GET` | `/api/products`、`/api/products?page=0&size=10` | 支持分页与过滤参数 |
| 详情查询 | `GET` | `/api/products/{id}` | 路径参数 `id` |
| 新增 | `POST` | `/api/products` | 请求体承载业务字段 |
| 更新 | `PUT` | `/api/products/{id}` | 全量更新；部分更新也使用 PUT |
| 删除 | `DELETE` | `/api/products/{id}` | ADMIN 限定 |
| 子资源操作 | `POST`/`PUT` | `/api/products/{id}/prices`、`/api/products/batch-sort` | 动词型子路径，使用连字符 |

**命名约定**：

- 资源名使用复数名词：`/products`、`/users`、`/categories`。
- 子操作使用动词或资源子集：`/api/price-drafts/batch-save`、`/api/price-drafts/publish-all`、`/api/products/batch-sort`。
- 启用 / 停用 / 吊销等状态切换使用 `PUT /{resource}/{id}/<state>`：`/api/api-keys/{id}/enable`、`/api/api-keys/{id}/revoke`。
- 公开/内部接口无特殊路径段，仅通过安全配置和角色控制。

---

## 分页查询规范

所有分页接口统一接受以下查询参数：

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | `0` | 页码，**从 0 开始** |
| `size` | int | `10` 或 `20`（按接口） | 每页大小 |
| `sortBy` | string | 视接口而定 | 排序字段，如 `id`、`createdTime` |
| `sortDirection` | string | `asc` | 排序方向：`asc` 或 `desc` |

> 部分接口在分页基础上额外支持关键字、状态、日期范围等过滤参数（如 `keyword`、`status`、`categoryId`、`startDate`/`endDate`），具体见 [internal.md](./internal.md) 中各接口说明。

---

## 调用示例

### cURL

```bash
# 1. 登录获取 Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password"}'

# 2. 获取产品列表（带分页）
curl -X GET "http://localhost:8080/api/products?page=0&size=10" \
  -H "Authorization: Bearer <accessToken>"

# 3. 创建产品
curl -X POST http://localhost:8080/api/products \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{"name":"产品A","spec":"规格A","unit":"吨","categoryId":1,"status":"ACTIVE","sortOrder":0}'
```

### JavaScript（Fetch）

```javascript
// 1. 登录
const loginRes = await fetch('/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'password' })
});
const { data } = await loginRes.json();
const token = data.accessToken;

// 2. 获取产品列表
const productsRes = await fetch('/api/products?page=0&size=10', {
  headers: { 'Authorization': `Bearer ${token}` }
});
const products = await productsRes.json();

// 3. 创建产品
await fetch('/api/products', {
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    name: '产品A',
    spec: '规格A',
    unit: '吨',
    categoryId: 1,
    status: 'ACTIVE',
    sortOrder: 0
  })
});
```

### Java 25（HttpClient）

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.time.Duration;

public class ApiClient {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final String BASE = "http://localhost:8080/api";

    /** 登录获取 AccessToken */
    public String login(String username, String password) throws Exception {
        String body = """
                {"username":"%s","password":"%s"}
                """.formatted(username, password);
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        // 解析 Result<String>，此处省略 JSON 解析
        return resp.body(); // 实际返回 accessToken 字段
    }

    /** GET /api/products?page=0&size=10 */
    public String listProducts(String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/products?page=0&size=10"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }

    /** POST /api/products */
    public String createProduct(String token, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/products"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonBody))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString()).body();
    }
}
```

---

## 调用约定

- **时区**：所有 `date` / `datetime` 类型字段，服务端按本地时区（系统默认 `Asia/Shanghai`）序列化字符串；客户端无需做时区转换，直接按 `yyyy-MM-dd` 或 `yyyy-MM-dd HH:mm:ss` 解析即可。
- **ID 类型**：路径与请求体中的主键统一为 `Long`（Java 序列化表现为 JSON `number`）。
- **金额字段**：使用 `BigDecimal`，JSON 序列化为数字字面量；建议客户端以字符串方式接收以避免精度丢失。
- **状态字段**：使用字符串枚举（如 `ACTIVE`、`INACTIVE`），详见各接口请求/响应示例。
- **空值处理**：更新接口中省略字段表示保持原值；显式传 `null` 表示清空（见 `PUT /api/users/{id}/admin-edit` 的 `deptId: null` 语义）。
