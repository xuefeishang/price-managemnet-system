---
title: 外部 API 详细
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/API调用手册.md
---

# 外部 API 详细

本文档描述矿产品价格管理系统对**外部系统**开放的全部接口，统一前缀：

```
/api/external/v1
```

外部接口使用 **API Key + HMAC-SHA256** 签名认证，**不**使用 JWT。签名算法的完整规范、测试向量和代码示例见 [auth.md § 外部 API Key 签名认证](./auth.md#外部-api-key-签名认证)。

> **快速索引**：内部 vs 外部资源对照表见 [README.md § 内部 vs 外部 API 速查](./README.md#内部-vs-外部-api-速查)。

---

## 目录

- [认证与签名](#认证与签名)
- [启用与运行时开关](#启用与运行时开关)
- [外部 API 管理接口](#外部-api-管理接口)
- [外部 API 业务接口](#外部-api-业务接口)
  - [基础数据](#基础数据)
  - [产品](#产品)
  - [价格](#价格)
  - [价格查询](#价格查询)
  - [首页](#首页)
- [端点权限树](#端点权限树)
- [阶段一外部写入说明](#阶段一外部写入说明)

---

## 认证与签名

| 项 | 值 |
|------|-----|
| 协议前缀 | `/api/external/v1` |
| 认证方式 | API Key + HMAC-SHA256 签名 |
| 必传请求头 | `X-App-Id`、`X-Timestamp`（Unix 秒）、`X-Nonce`、`X-Signature`（hex 小写） |
| 禁用请求头 | `Authorization`（JWT 头）、`X-App-Secret`（明文 Secret） |
| 签名输入 | 见 [auth.md § Canonical String](./auth.md#canonical-string) |
| 安全要求 | 见 [auth.md § 安全要求](./auth.md#安全要求) |

**最小调用示例**（伪代码）：

```javascript
const resp = await callExternalApi({
  appId: process.env.APP_ID,
  appSecret: process.env.APP_SECRET, // 仅服务端使用
  method: 'GET',
  path: '/api/external/v1/products',
  query: 'page=0&size=20',
  body: ''
});
```

完整实现（含 SHA-256、HMAC、URL 规范化、Unix 秒时间戳与一次性 Nonce）见 [auth.md § JavaScript 签名示例](./auth.md#javascript-签名示例) 和 [auth.md § Java 25 调用示例](./auth.md#java-25-调用示例)。

---

## 启用与运行时开关

外部 API 默认关闭。完整说明见 [auth.md § 启用条件](./auth.md#启用条件)：

| 层级 | 控制项 | 默认 |
|------|--------|------|
| 部署级 | `API_KEY_ENABLED=true` | 关闭 |
| 部署级 | `API_KEY_ENCRYPTION_KEY`（Base64 32 字节 AES-GCM 主密钥） | 未配置 |
| 部署级 | `API_KEY_ENCRYPTION_KEY_VERSION` | `v1` |
| 运行时 | API 授权管理页开关：开启 / 暂停 | 暂停 |

- 部署级关闭 → 全部 `/api/external/v1/**` 不可用。
- 部署级开启 + 运行时暂停 → 返回 `503`，认证结果为 `SERVICE_DISABLED`。
- 部署级开启 + 运行时开启 → 按 App Key 权限、IP 白名单、限流正常服务。

---

## 外部 API 管理接口

> **管理接口仍使用内部 JWT**，且仅 `ADMIN` 可访问。完整规范见 [internal.md § API 授权管理接口](./internal.md#api-授权管理接口)。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-keys` | 分页查询 API Key |
| POST | `/api/api-keys` | 创建 API Key，返回 Secret 一次 |
| GET | `/api/api-keys/{id}` | API Key 详情 |
| PUT | `/api/api-keys/{id}` | 更新名称、描述、环境、过期时间、白名单、限流和权限 |
| PUT | `/api/api-keys/{id}/enable` | 启用 |
| PUT | `/api/api-keys/{id}/disable` | 停用 |
| PUT | `/api/api-keys/{id}/revoke` | 吊销 |
| GET | `/api/api-keys/permissions/tree` | 获取外部 API 权限端点元数据 |
| GET | `/api/api-keys/service-status` | 服务总开关状态 |
| PUT | `/api/api-keys/service-status` | 切换服务总开关 |
| GET | `/api/api-call-logs` | 查询外部 API 调用日志 |
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

> 响应中的 `appSecret` **只展示一次**，关闭后无法再次查看。完整入参校验规则见 [internal.md § 创建 API Key](./internal.md#创建-api-key)。

### 权限端点元数据

`GET /api/api-keys/permissions/tree` 会返回外部 API 全部权限端点的结构化元数据，管理页"代码生成"弹窗基于它生成 Node.js / Java 25 / Postman / PowerShell / curl 五种示例。

返回字段（每个端点）：

| 字段 | 说明 |
|------|------|
| `queryExample` / `bodyExample` / `pathParamsExample` | 代码生成使用的示例参数 |
| `querySchema` / `bodySchema` / `pathParamsSchema` | 字段名、类型、是否必填、默认值和说明 |
| `successExample` / `failureExample` | 成功和失败响应示例 |
| `codeNotes` | 复制运行前的端点级注意事项 |

---

## 外部 API 业务接口

外部业务接口全部位于 `/api/external/v1/**` 命名空间。下列表格中，路径均相对于 `/api/external/v1`，完整 URL 为 `https://<host>/api/external/v1/<path>`。

> **`GET /api/external/v1/dict` 必须指定 `category`**，外部接口不提供一次性读取全部字典的能力。

| 权限编码 | 方法 | 路径 | 说明 |
|----------|------|------|------|
| `product:read` | GET | `/products`、`/products/{id}` | 产品读取 |
| `product:write` | POST / PUT | `/products`、`/products/{id}` | 产品写入 |
| `product:delete` | DELETE | `/products/{id}` | 产品删除 |
| `price:read` | GET | `/products/{productId}/price-history` | 价格历史 |
| `price:read` | GET | `/products/{productId}/current-price` | 当前价格 |
| `price:read` | GET | `/products/{productId}/price-by-date` | 指定日期价格 |
| `price:read` | GET | `/products/{productId}/price-trend` | 价格走势 |
| `price:read` | GET | `/prices/by-date`、`/prices/by-date-with-stats` | 按日期价格 |
| `price:write` | POST / PUT | `/products/{productId}/prices`、`/prices/{id}` | 价格写入 |
| `price-query:read` | GET | `/price-query` | 价格查询 |
| `price-query:export` | GET | `/price-query/export` | 价格查询导出 |
| `category:read` | GET | `/categories`、`/categories/{id}` | 分类读取 |
| `origin:read` | GET | `/origins`、`/origins/{id}` | 产地读取 |
| `customer:read` | GET | `/customers`、`/customers/{id}` | 客户读取 |
| `dict:read` | GET | `/dict?category=xxx`、`/dict/active`、`/dict/categories`、`/dict/{id}` | 字典读取 |
| `home:read` | GET | `/home/dashboard`、`/home/summary`、`/home/alerts`、`/home/trend`、`/home/product-order` | 首页数据 |

### 基础数据

| 方法 | 路径 | 权限编码 | 说明 |
|------|------|----------|------|
| GET | `/categories` | `category:read` | 分类列表 |
| GET | `/categories/{id}` | `category:read` | 分类详情 |
| GET | `/origins` | `origin:read` | 产地列表 |
| GET | `/origins/{id}` | `origin:read` | 产地详情 |
| GET | `/customers` | `customer:read` | 客户列表 |
| GET | `/customers/{id}` | `customer:read` | 客户详情 |
| GET | `/dict?category=xxx` | `dict:read` | 按分类读取字典 |
| GET | `/dict/active` | `dict:read` | 读取启用字典项 |
| GET | `/dict/categories` | `dict:read` | 字典分类列表 |
| GET | `/dict/{id}` | `dict:read` | 字典项详情 |

> `GET /dict` 必须指定 `category`，外部接口不提供一次性读取全部字典能力。

### 产品

| 方法 | 路径 | 权限编码 | 说明 |
|------|------|----------|------|
| GET | `/products` | `product:read` | 分页查询产品列表 |
| GET | `/products/{id}` | `product:read` | 产品详情 |
| POST | `/products` | `product:write` | 创建产品 |
| PUT | `/products/{id}` | `product:write` | 更新产品 |
| DELETE | `/products/{id}` | `product:delete` | 删除产品 |

**列表查询参数**（与内部一致）：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | int | 否 | 0 | 页码（从 0 开始） |
| `size` | int | 否 | 10 | 每页数量 |
| `keyword` | string | 否 | - | 搜索关键字 |
| `categoryId` | long | 否 | - | 分类 ID |
| `status` | string | 否 | - | 状态：`ACTIVE` / `INACTIVE` |
| `sortBy` | string | 否 | `id` | 排序字段 |
| `sortDirection` | string | 否 | `asc` | 排序方向 |

**创建产品请求体：**

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

### 价格

| 方法 | 路径 | 权限编码 | 说明 |
|------|------|----------|------|
| GET | `/products/{productId}/price-history` | `price:read` | 产品价格历史 |
| GET | `/products/{productId}/current-price` | `price:read` | 产品当前价格 |
| GET | `/products/{productId}/price-by-date` | `price:read` | 产品指定日期价格 |
| GET | `/products/{productId}/price-trend` | `price:read` | 产品价格走势 |
| POST | `/products/{productId}/prices` | `price:write` | 添加产品价格 |
| PUT | `/prices/{id}` | `price:write` | 更新价格 |
| GET | `/prices/by-date` | `price:read` | 按日期价格列表 |
| GET | `/prices/by-date-with-stats` | `price:read` | 按日期价格列表（带统计） |

**指定日期价格查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `date` | date | 否 | 今天 | 日期，格式 `yyyy-MM-dd` |

**价格走势查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `days` | int | 否 | 30 | 天数（30 / 180 / 365） |
| `startDate` | date | 否 | - | 精确开始日期；传入后优先于 `days` |
| `endDate` | date | 否 | 当天 | 结束日期 |

**添加产品价格请求体：**

```json
{
  "price": 100.50,
  "priceDate": "2026-05-29",
  "remark": "备注"
}
```

**更新价格请求体：**

```json
{
  "price": 105.00,
  "priceDate": "2026-05-29",
  "remark": "更新备注"
}
```

### 价格查询

| 方法 | 路径 | 权限编码 | 说明 |
|------|------|----------|------|
| GET | `/price-query` | `price-query:read` | 分页查询价格 |
| GET | `/price-query/export` | `price-query:export` | 导出价格查询结果 |

**查询参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `date` | date | 否 | - | 日期 |
| `keyword` | string | 否 | - | 搜索关键字 |
| `categoryId` | long | 否 | - | 分类 ID |
| `status` | string | 否 | - | 状态 |
| `page` | int | 否 | 0 | 页码 |
| `size` | int | 否 | 20 | 每页数量 |
| `sortBy` | string | 否 | - | 排序字段 |
| `sortDirection` | string | 否 | `asc` | 排序方向 |

**响应指标字段**（与内部 `/api/price-query` 完全一致）：

| 字段 | 说明 |
|------|------|
| `latestPrice`、`latestPriceDate` | 截至查询日期的最新有效价格及日期 |
| `previousPrice`、`previousPriceDate` | 上期有效价格及日期（跳过无记录日期） |
| `previousChangeAmount`、`previousChangePercent` | 最新价格较上期有效价格的差额及差异率 |
| `budgetPrice`、`budgetChangeAmount`、`budgetChangePercent` | 最新有效价格日所属年度预算价及预算偏差 |
| `monthlyAveragePrice`、`previousMonthAveragePrice`、`monthOverMonthPercent` | 月累计均价、上月均价、环比 |
| `lastYearSamePeriodAveragePrice`、`yearOverYearPercent` | 上年同月均价、同比 |

> 导出接口返回 Excel 文件下载。

### 首页

| 方法 | 路径 | 权限编码 | 说明 |
|------|------|----------|------|
| GET | `/home/dashboard` | `home:read` | 仪表盘数据 |
| GET | `/home/summary` | `home:read` | 摘要统计 |
| GET | `/home/alerts` | `home:read` | 价格预警 |
| GET | `/home/trend` | `home:read` | 趋势分析 |
| GET | `/home/product-order` | `home:read` | 首页产品排序 |

**通用请求参数：** `date`（date，默认昨天，格式 `yyyy-MM-dd`）；`/home/trend` 额外支持 `days`（int，默认 30）。

---

## 端点权限树

外部 API 全部端点按权限编码归类如下（也是创建 API Key 时 `permissionCodes` 字段的可选值）：

```
product:read
  └─ GET    /products
  └─ GET    /products/{id}

product:write
  └─ POST   /products
  └─ PUT    /products/{id}

product:delete
  └─ DELETE /products/{id}

price:read
  ├─ GET /products/{productId}/price-history
  ├─ GET /products/{productId}/current-price
  ├─ GET /products/{productId}/price-by-date
  ├─ GET /products/{productId}/price-trend
  ├─ GET /prices/by-date
  └─ GET /prices/by-date-with-stats

price:write
  ├─ POST /products/{productId}/prices
  └─ PUT  /prices/{id}

price-query:read
  └─ GET /price-query

price-query:export
  └─ GET /price-query/export

category:read
  ├─ GET /categories
  └─ GET /categories/{id}

origin:read
  ├─ GET /origins
  └─ GET /origins/{id}

customer:read
  ├─ GET /customers
  └─ GET /customers/{id}

dict:read
  ├─ GET /dict?category=xxx
  ├─ GET /dict/active
  ├─ GET /dict/categories
  └─ GET /dict/{id}

home:read
  ├─ GET /home/dashboard
  ├─ GET /home/summary
  ├─ GET /home/alerts
  ├─ GET /home/trend
  └─ GET /home/product-order
```

---

## 阶段一外部写入说明

阶段一对外暴露的写入接口（`POST /products`、`POST /products/{productId}/prices`、`PUT /prices/{id}`）会复用现有产品/价格服务和审批流：

- 若审批流启用，外部写入产生的审批请求使用系统外部申请人占位 `0`，调用来源以 `sys_api_call_log.app_id` 和 `sys_api_key_operation_log` 追溯；外部应用到内部用户/部门的身份映射放入阶段二。
- 外部写入口会**清理**请求体中的 `id`、`version`、`createdTime`、`updatedTime` 等系统字段，**不允许**外部系统覆盖服务端生成字段。

> 调试与开发：所有外部接口均要求生产环境启用 HTTPS，并正确配置 `API_KEY_ENABLED`、`API_KEY_ENCRYPTION_KEY` 与运行时开关。详细安全要求见 [auth.md § 安全要求](./auth.md#安全要求)。
