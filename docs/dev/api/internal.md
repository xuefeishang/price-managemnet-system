---
title: 内部 API 速查
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/API调用手册.md
---

# 内部 API 速查

本文档列出矿产品价格管理系统**全部内部接口**（路径前缀 `/api/**`，**不含** `/api/external/v1/**`）。所有接口遵循 [specs.md](./specs.md) 中的通用响应/分页/调用规范，认证方式见 [auth.md § JWT](./auth.md#jwt-bearer-token)。

> **范围**：认证、产品、价格、价格草稿、价格查询、首页、分类、产地、客户、字典、用户、角色、权限、部门、菜单、操作日志、导入导出、样式配置、审批流程、个人中心、通知、管理员通知、系统公告、定时任务、产品年度预算、API 授权管理、API 调用日志。

---

## 目录

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

## 认证接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/auth/captcha` | 无需认证 | 获取登录验证码 |
| POST | `/api/auth/login` | 无需认证 | 用户登录 |
| POST | `/api/auth/refresh-token` | 无需认证 | 刷新 AccessToken |
| GET | `/api/auth/profile` | 已登录用户 | 获取当前用户信息 |
| PUT | `/api/auth/profile` | 已登录用户 | 更新个人信息 |
| PUT | `/api/auth/password` | 已登录用户 | 修改密码（兼容接口，委托 `/api/profile/password`） |
| POST | `/api/auth/logout` | 已登录用户 | 退出登录 |

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
| `username` | string | 是 | 用户名或工号 |
| `password` | string | 是 | 密码 |
| `loginType` | string | 否 | `USERNAME` 或 `EMPLOYEE_ID` |
| `captchaKey` | string | 否 | 验证码 Key |
| `captchaCode` | string | 否 | 验证码内容 |

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
{ "refreshToken": "refresh-token-string" }
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

### 更新个人信息

```
PUT /api/auth/profile
```

**权限：** 已登录用户

### 修改密码（兼容接口）

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

## 产品接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/products` | ADMIN / EDITOR / VIEWER | 分页查询产品列表 |
| GET | `/api/products/{id}` | ADMIN / EDITOR / VIEWER | 获取产品详情 |
| POST | `/api/products` | ADMIN / EDITOR | 创建产品 |
| PUT | `/api/products/{id}` | ADMIN / EDITOR | 更新产品 |
| DELETE | `/api/products/{id}` | ADMIN | 删除产品 |
| POST | `/api/products/batch-sort` | ADMIN / EDITOR | 批量更新排序 |

### 获取产品列表

```
GET /api/products
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | int | 否 | 0 | 页码（从 0 开始） |
| `size` | int | 否 | 10 | 每页数量 |
| `keyword` | string | 否 | - | 搜索关键字 |
| `categoryId` | long | 否 | - | 分类 ID |
| `status` | string | 否 | - | 状态：`ACTIVE` / `INACTIVE` |
| `sortBy` | string | 否 | `id` | 排序字段 |
| `sortDirection` | string | 否 | `asc` | 排序方向 |

**响应：** 分页产品列表。

### 获取产品详情

```
GET /api/products/{id}
```

### 创建产品

```
POST /api/products
```

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

### 删除产品

```
DELETE /api/products/{id}
```

### 批量更新排序

```
POST /api/products/batch-sort
```

**请求体：**

```json
[
  { "id": 1, "sortOrder": 0 },
  { "id": 2, "sortOrder": 1 }
]
```

---

## 价格接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/products/{productId}/price-history` | ADMIN / EDITOR / VIEWER | 产品价格历史 |
| GET | `/api/products/{productId}/current-price` | ADMIN / EDITOR / VIEWER | 当前价格 |
| GET | `/api/products/{productId}/price-by-date` | ADMIN / EDITOR / VIEWER | 指定日期价格 |
| GET | `/api/products/{productId}/yesterday-price` | ADMIN / EDITOR / VIEWER | 昨日价格 |
| GET | `/api/products/{productId}/monthly-average-price` | ADMIN / EDITOR / VIEWER | 月均价格 |
| GET | `/api/products/{productId}/price-trend` | ADMIN / EDITOR / VIEWER | 价格走势 |
| GET | `/api/products/{productId}/price-years` | ADMIN / EDITOR / VIEWER | 价格录入年份 |
| POST | `/api/products/{productId}/prices` | ADMIN / EDITOR | 添加产品价格 |
| PUT | `/api/prices/{id}` | ADMIN / EDITOR | 更新价格 |
| POST | `/api/prices/cleanup-duplicates` | ADMIN | 清理重复价格数据 |
| GET | `/api/prices/by-date` | ADMIN / EDITOR / VIEWER | 按日期价格列表 |
| GET | `/api/prices/by-date-with-stats` | ADMIN / EDITOR / VIEWER | 按日期价格列表（带统计） |

### 获取产品价格历史

```
GET /api/products/{productId}/price-history
```

### 获取产品当前价格

```
GET /api/products/{productId}/current-price
```

### 按日期获取价格列表

```
GET /api/prices/by-date
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `date` | date | 否 | 今天 | 日期，格式 `yyyy-MM-dd` |

### 按日期获取价格列表（带统计）

```
GET /api/prices/by-date-with-stats
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `date` | date | 否 | 今天 | 日期 |

**响应：** 包含昨日价格和月均价。

### 获取产品指定日期价格

```
GET /api/products/{productId}/price-by-date?date=2026-05-29
```

### 获取产品昨日价格

```
GET /api/products/{productId}/yesterday-price
```

### 获取产品月均价格

```
GET /api/products/{productId}/monthly-average-price
```

### 添加产品价格

```
POST /api/products/{productId}/prices
```

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

### 获取产品价格走势

```
GET /api/products/{productId}/price-trend
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `days` | int | 否 | 30 | 天数（30 / 180 / 365） |
| `startDate` | date | 否 | - | 精确开始日期；传入后优先于 `days` |
| `endDate` | date | 否 | 当天 | 结束日期 |

> 查看某个自然年时，传入该年的 `startDate=YYYY-01-01` 与 `endDate=YYYY-12-31`；查看当前年时，`endDate` 使用当天。

### 获取产品价格录入年份

```
GET /api/products/{productId}/price-years
```

> 返回该产品正式价格记录中实际存在的年份，按年份倒序排列；不返回无数据年份，也不限制历史年限。

---

## 价格草稿接口

价格维护页采用"保存草稿 / 发布全部草稿"双阶段。保存只写当前日期草稿；发布会发布全系统所有 `DRAFT` 草稿，不受当前页面日期限制。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/price-drafts/by-date?date=yyyy-MM-dd` | ADMIN / EDITOR | 查询指定日期活动价格草稿 |
| GET | `/api/price-drafts/publishable-summary` | ADMIN / EDITOR | 查询全系统待发布 DRAFT 草稿汇总 |
| POST | `/api/price-drafts/batch-save` | ADMIN / EDITOR | 批量保存当前日期价格草稿 |
| POST | `/api/price-drafts/publish-all` | ADMIN / EDITOR | 发布全系统所有 DRAFT 草稿 |
| POST | `/api/price-drafts/by-date/publish?date=yyyy-MM-dd` | ADMIN / EDITOR | 按日期发布 DRAFT 草稿 |
| POST | `/api/price-drafts/{batchId}/publish` | ADMIN / EDITOR | 兼容/维护用单批次发布接口 |

### 待发布草稿汇总

```http
GET /api/price-drafts/publishable-summary
Authorization: Bearer <accessToken>
```

**响应 data 示例：**

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

> 响应 `data` 会返回 `successCount`、`failCount`、`publishGroupId`、`publishLogIds` 和 `batchResults`。发布成功后生成一条发布组通知；无 DRAFT 草稿时返回业务错误"暂无可发布草稿"。

---

## 价格查询接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/price-query` | ADMIN / EDITOR / VIEWER | 分页查询价格 |
| GET | `/api/price-query/export` | ADMIN / EDITOR / VIEWER | 导出价格查询结果 |

### 查询价格列表

```
GET /api/price-query
```

**请求参数：**

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

**响应指标字段：**

| 字段 | 说明 |
|------|------|
| `latestPrice`、`latestPriceDate` | 截至查询日期的最新有效价格及日期 |
| `previousPrice`、`previousPriceDate` | 上期有效价格及日期（跳过无记录日期） |
| `previousChangeAmount`、`previousChangePercent` | 最新价格较上期有效价格的差额及差异率 |
| `budgetPrice`、`budgetChangeAmount`、`budgetChangePercent` | 最新有效价格日所属年度预算价及预算偏差 |
| `monthlyAveragePrice`、`previousMonthAveragePrice`、`monthOverMonthPercent` | 月累计均价、上月均价、环比 |
| `lastYearSamePeriodAveragePrice`、`yearOverYearPercent` | 上年同月均价、同比 |

> 最新有效价格会排除在查询日已显式过期的价格。指标分组、名称、排序、启停和说明分别由 `price_metric_group`、`price_metric` 字典维护。`yesterdayPrice`、`changeAmount`、`changePercent` 为 deprecated 的 v1 历史兼容字段，分别始终等于 `budgetPrice`、`budgetChangeAmount`、`budgetChangePercent`；内部页面和导出不再使用这些字段。

### 导出价格查询结果

```
GET /api/price-query/export
```

**响应：** Excel 文件下载。

---

## 首页仪表盘接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/home/dashboard` | 已登录用户 | 获取仪表盘数据 |
| GET | `/api/home/summary` | 已登录用户 | 获取摘要统计 |
| GET | `/api/home/alerts` | 已登录用户 | 获取价格预警 |
| GET | `/api/home/trend` | 已登录用户 | 获取趋势分析 |
| GET | `/api/home/product-order` | ADMIN / EDITOR / VIEWER | 获取首页产品排序 |

**通用请求参数：** `date`（date，默认昨天，格式 `yyyy-MM-dd`），`trend` 接口额外支持 `days`（int，默认 30）。

---

## 分类接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/categories` | ADMIN / EDITOR / VIEWER | 获取分类列表 |
| GET | `/api/categories/{id}` | ADMIN / EDITOR / VIEWER | 获取分类详情 |
| POST | `/api/categories` | ADMIN / EDITOR | 创建分类 |
| PUT | `/api/categories/{id}` | ADMIN / EDITOR | 更新分类 |
| DELETE | `/api/categories/{id}` | ADMIN | 删除分类 |
| POST | `/api/categories/batch-sort` | ADMIN / EDITOR | 批量更新排序 |

**请求参数：** `status`（string，可选，传 `ACTIVE` 获取启用项）。

**创建分类请求体：**

```json
{
  "name": "分类名称",
  "status": "ACTIVE",
  "sortOrder": 0
}
```

---

## 产地接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/origins` | ADMIN / EDITOR / VIEWER | 获取产地列表 |
| GET | `/api/origins/{id}` | ADMIN / EDITOR / VIEWER | 获取产地详情 |
| POST | `/api/origins` | ADMIN / EDITOR | 创建产地 |
| PUT | `/api/origins/{id}` | ADMIN / EDITOR | 更新产地 |
| DELETE | `/api/origins/{id}` | ADMIN | 删除产地 |

**请求参数：** `status`（string，可选）。

**创建产地请求体：**

```json
{
  "name": "产地名称",
  "status": "ACTIVE"
}
```

---

## 客户接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/customers` | ADMIN / EDITOR / VIEWER | 获取客户列表 |
| GET | `/api/customers/{id}` | ADMIN / EDITOR / VIEWER | 获取客户详情 |
| POST | `/api/customers` | ADMIN / EDITOR | 创建客户 |
| PUT | `/api/customers/{id}` | ADMIN / EDITOR | 更新客户 |
| DELETE | `/api/customers/{id}` | ADMIN | 删除客户 |

**请求参数：** `status`（string，可选）。

**创建客户请求体：**

```json
{
  "name": "客户名称",
  "contact": "联系人",
  "phone": "联系电话",
  "status": "ACTIVE"
}
```

---

## 字典接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/dict` | ADMIN / EDITOR / VIEWER | 获取字典列表 |
| GET | `/api/dict/active` | 已登录用户（公开） | 获取启用的字典项 |
| GET | `/api/dict/categories` | ADMIN / EDITOR / VIEWER | 获取字典分类列表 |
| GET | `/api/dict/{id}` | ADMIN / EDITOR / VIEWER | 获取字典项详情 |
| POST | `/api/dict` | ADMIN / EDITOR | 创建字典项 |
| POST | `/api/dict/batch` | ADMIN | 批量创建字典项 |
| PUT | `/api/dict/{id}` | ADMIN / EDITOR | 更新字典项 |
| DELETE | `/api/dict/{id}` | ADMIN | 删除字典项 |

### 获取字典列表

```
GET /api/dict
```

**请求参数：** `category`（string，可选）——字典分类。

### 获取启用的字典项

```
GET /api/dict/active?category=CATEGORY_NAME
```

### 获取字典分类列表

```
GET /api/dict/categories
```

**请求参数：** `all`（boolean，默认 `false`；`true`=全部，`false`=仅启用）。

### 创建字典项

```
POST /api/dict
```

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

**批量创建请求体：** 字典项数组。

---

## 用户接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/users` | ADMIN | 分页获取用户列表 |
| GET | `/api/users/all` | ADMIN | 获取所有用户（不分页） |
| GET | `/api/users/{id}` | ADMIN | 获取用户详情 |
| GET | `/api/users/{id}/permissions` | ADMIN | 获取用户权限列表 |
| GET | `/api/users/{id}/roles` | ADMIN | 获取用户角色列表 |
| GET | `/api/users/roles-batch?ids=1,2,3` | ADMIN | 批量获取用户角色映射 |
| POST | `/api/users` | ADMIN | 创建用户 |
| PUT | `/api/users/{id}` | ADMIN | 更新用户 |
| DELETE | `/api/users/{id}` | ADMIN | 删除用户 |
| POST | `/api/users/{id}/reset-password` | ADMIN | 重置用户密码 |
| PUT | `/api/users/{id}/admin-edit` | ADMIN | 管理员原子编辑用户 |
| POST | `/api/users/{id}/lock` | ADMIN | 锁定用户 |
| POST | `/api/users/{id}/unlock` | ADMIN | 解锁用户 |

### 获取用户列表

```
GET /api/users
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | int | 否 | 0 | 页码 |
| `size` | int | 否 | 20 | 每页数量 |
| `keyword` | string | 否 | - | 搜索关键字 |
| `role` | string | 否 | - | 角色过滤 |
| `status` | string | 否 | - | 状态过滤 |
| `deptId` | long | 否 | - | 部门 ID |

### 创建用户

```
POST /api/users
```

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

### 重置用户密码

```
POST /api/users/{id}/reset-password
```

**JSON 请求体：**

```json
{ "newPassword": "Password123" }
```

> `newPassword` 可省略，此时使用服务端默认密码。密码**禁止**通过 URL 查询参数传输。

### 管理员原子编辑用户

```
PUT /api/users/{id}/admin-edit
```

> 一次事务内更新基础资料、状态和可选新密码。`deptId: null` 表示显式清空部门关联；省略字段表示保持原值。

---

## 角色接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/roles` | ADMIN | 获取角色列表 |
| GET | `/api/roles/active` | 已登录用户 | 获取启用的角色列表 |
| GET | `/api/roles/{id}` | ADMIN | 获取角色详情 |
| GET | `/api/roles/{id}/permissions` | ADMIN | 获取角色权限 ID 列表 |
| PUT | `/api/roles/{id}/permissions` | ADMIN | 为角色分配权限 |
| POST | `/api/roles` | ADMIN | 创建角色 |
| PUT | `/api/roles/{id}` | ADMIN | 更新角色 |
| DELETE | `/api/roles/{id}` | ADMIN | 删除角色 |
| POST | `/api/roles/assign/{userId}` | ADMIN | 为用户分配角色 |

**为角色分配权限请求体：** 权限 ID 数组 `[1, 2, 3]`。

**创建角色请求体：**

```json
{
  "roleCode": "ROLE_CODE",
  "roleName": "角色名称",
  "description": "角色描述",
  "status": "ACTIVE"
}
```

**为用户分配角色请求体：** 角色 ID 数组 `[1, 2]`。

---

## 权限接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/permissions` | 已登录用户 | 获取所有权限列表 |
| GET | `/api/permissions/tree` | 已登录用户 | 获取权限树 |

> `/api/permissions/tree` 用于内部角色权限分配；外部 API 权限树由 `/api/api-keys/permissions/tree` 提供（见下文）。

---

## 部门接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/departments/tree` | 已登录用户 | 获取部门树 |
| GET | `/api/departments` | 已登录用户 | 获取部门列表 |
| GET | `/api/departments/{id}` | 已登录用户 | 获取部门详情 |
| POST | `/api/departments` | ADMIN | 创建部门 |
| PUT | `/api/departments/{id}` | ADMIN | 更新部门 |
| PUT | `/api/departments/{id}/move?parentId=新父部门ID` | ADMIN | 移动部门 |
| PUT | `/api/departments/sort` | ADMIN | 批量排序部门 |
| DELETE | `/api/departments/{id}` | ADMIN | 删除部门 |

**创建部门请求体：**

```json
{
  "deptCode": "DEPT001",
  "deptName": "部门名称",
  "parentId": null,
  "sortOrder": 0,
  "status": "ACTIVE"
}
```

**批量排序部门请求体：** 部门 ID 数组，按顺序排列 `[1, 2, 3]`。

---

## 菜单接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/menus/tree` | ADMIN / EDITOR / VIEWER（公开） | 获取菜单树 |
| GET | `/api/menus/visible?role=ADMIN` | 已登录用户 | 获取可见菜单 |
| GET | `/api/menus` | ADMIN | 获取所有菜单 |
| GET | `/api/menus/{id}` | ADMIN | 获取菜单详情 |
| POST | `/api/menus` | ADMIN | 创建菜单 |
| PUT | `/api/menus/{id}` | ADMIN | 更新菜单 |
| DELETE | `/api/menus/{id}` | ADMIN | 删除菜单 |
| POST | `/api/menus/batch-sort` | ADMIN | 批量更新菜单排序 |
| POST | `/api/menus/init` | ADMIN | 初始化默认菜单 |

**创建菜单请求体：**

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

---

## 操作日志接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/logs` | ADMIN | 分页查询操作日志 |
| GET | `/api/logs/recent?limit=10` | ADMIN | 获取最近操作日志 |
| GET | `/api/logs/user/{userId}` | ADMIN | 按用户 ID 查询日志 |
| GET | `/api/logs/type/{operationType}` | ADMIN | 按操作类型查询日志 |
| GET | `/api/logs/statistics` | ADMIN | 获取日志统计 |
| GET | `/api/logs/reports/monthly?year=2026&month=5` | ADMIN | 获取月度报表 |
| GET | `/api/logs/reports/yearly?year=2026` | ADMIN | 获取年度报表 |

### 分页查询操作日志

```
GET /api/logs
```

**请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | int | 否 | 0 | 页码 |
| `size` | int | 否 | 10 | 每页数量 |
| `username` | string | 否 | - | 用户名 |
| `operationType` | string | 否 | - | 操作类型 |
| `operationModule` | string | 否 | - | 操作模块 |
| `startTime` | datetime | 否 | - | 开始时间 |
| `endTime` | datetime | 否 | - | 结束时间 |

### 获取日志统计

```
GET /api/logs/statistics
```

**请求参数：** `startTime`（datetime，默认 30 天前）、`endTime`（datetime，默认现在）。

---

## 导入导出接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/import/products` | ADMIN / EDITOR | 导入产品（`multipart/form-data`，字段名 `file`） |
| GET | `/api/import/products` | ADMIN / EDITOR / VIEWER | 导出产品（Excel） |
| POST | `/api/import/users` | ADMIN | 导入用户（`multipart/form-data`，字段名 `file`） |
| GET | `/api/import/users` | ADMIN | 导出用户（Excel） |
| GET | `/api/import/users/template` | ADMIN | 下载用户导入模板（仅表头） |

### 导入用户

导入前会**全量**检查模板表头、字段格式、密码策略、文件内重复以及数据库中的用户名/工号重复。只有全部检查通过才会在单一事务中写入；存在任一异常时不会导入任何用户。

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

> 写入阶段发生并发唯一约束冲突时返回 `HTTP 409`。默认最大导入数据行数为 1000，可通过环境变量 `USER_IMPORT_MAX_ROWS` 调整。

---

## 样式配置接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/style/config` | 已登录用户（公开） | 获取样式配置 |
| GET | `/api/style/themes` | ADMIN / EDITOR / VIEWER（公开） | 获取预设主题 |
| GET | `/api/style/presets` | ADMIN / EDITOR / VIEWER（公开） | 获取所有预设 |
| GET | `/api/style/color-schemes` | ADMIN / EDITOR / VIEWER（公开） | 获取色彩方案 |
| GET | `/api/style/layout-styles` | ADMIN / EDITOR / VIEWER（公开） | 获取布局方案 |
| GET | `/api/style/font-presets` | ADMIN / EDITOR / VIEWER（公开） | 获取字号预设 |
| PUT | `/api/style/config` | ADMIN | 更新样式配置 |
| PUT | `/api/style/theme/{themeKey}` | ADMIN | 切换主题 |
| PUT | `/api/style/color-scheme/{schemeKey}` | ADMIN | 切换色彩方案 |
| PUT | `/api/style/layout-style/{layoutKey}` | ADMIN | 切换布局方案 |
| PUT | `/api/style/font-preset/{presetKey}` | ADMIN | 切换字号预设 |
| POST | `/api/style/logo` | ADMIN | 上传 Logo（`multipart/form-data`，`file`） |
| POST | `/api/style/logo/login` | ADMIN | 上传登录页 Logo |
| POST | `/api/style/logo/nav` | ADMIN | 上传导航栏 Logo |
| GET | `/api/style/versions` | ADMIN | 获取版本列表（分页） |
| GET | `/api/style/versions/{versionId}` | ADMIN | 获取版本详情 |
| POST | `/api/style/rollback/{versionId}` | ADMIN | 回滚到指定版本 |

**获取所有预设响应：**

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

**版本列表请求参数：** `page`（默认 0）、`size`（默认 10）。

---

## 审批流程接口

### 工作流管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/approvals/workflows` | ADMIN | 获取所有工作流 |
| GET | `/api/approvals/workflows/active` | 已登录用户 | 获取激活的工作流 |
| GET | `/api/approvals/workflows/{id}` | ADMIN | 获取工作流详情 |
| POST | `/api/approvals/workflows` | ADMIN | 创建工作流 |
| PUT | `/api/approvals/workflows/{id}` | ADMIN | 更新工作流 |
| DELETE | `/api/approvals/workflows/{id}` | ADMIN | 删除工作流 |
| PUT | `/api/approvals/workflows/{id}/activate` | ADMIN | 激活工作流 |
| PUT | `/api/approvals/workflows/{id}/deactivate` | ADMIN | 停用工作流 |

**创建工作流请求体：**

```json
{
  "workflowName": "工作流名称",
  "businessType": "PRODUCT_CREATE",
  "description": "描述",
  "status": "ACTIVE"
}
```

### 节点管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/approvals/workflows/{workflowId}/nodes` | ADMIN | 获取工作流节点 |
| POST | `/api/approvals/workflows/{workflowId}/nodes` | ADMIN | 添加审批节点 |
| PUT | `/api/approvals/nodes/{id}` | ADMIN | 更新审批节点 |
| DELETE | `/api/approvals/nodes/{id}` | ADMIN | 删除审批节点 |

**添加审批节点请求体：**

```json
{
  "nodeName": "节点名称",
  "nodeType": "APPROVAL",
  "approverRole": "ADMIN",
  "sortOrder": 0
}
```

### 审批请求管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/approvals/requests` | ADMIN | 分页查询审批请求 |
| GET | `/api/approvals/requests/pending` | ADMIN / EDITOR | 获取待我审批的请求 |
| GET | `/api/approvals/requests/my` | 已登录用户 | 获取我提交的审批请求 |
| GET | `/api/approvals/requests/{id}` | 已登录用户 | 获取审批请求详情 |
| POST | `/api/approvals/requests` | 已登录用户 | 创建审批请求 |
| PUT | `/api/approvals/requests/{id}/approve?comment=审批意见` | ADMIN / EDITOR | 审批通过 |
| PUT | `/api/approvals/requests/{id}/reject?comment=拒绝原因` | ADMIN / EDITOR | 审批拒绝 |
| PUT | `/api/approvals/requests/{id}/cancel` | 已登录用户（仅申请人） | 撤回审批请求 |
| GET | `/api/approvals/requests/{id}/records` | 已登录用户 | 获取审批记录 |

**分页查询请求参数：**

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `page` | int | 否 | 0 | 页码 |
| `size` | int | 否 | 10 | 每页数量 |
| `status` | string | 否 | - | 状态：`PENDING` / `APPROVED` / `REJECTED` |
| `businessType` | string | 否 | - | 业务类型 |
| `applicantId` | long | 否 | - | 申请人 ID |

**创建审批请求请求体：**

```json
{
  "workflowId": 1,
  "businessType": "PRODUCT_CREATE",
  "businessId": 123,
  "businessData": "{...}"
}
```

---

## 个人中心接口

所有接口均要求已登录，并且**只能操作当前认证用户**。

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

**小程序订阅授权上报示例：**

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

> 后端只使用当前认证用户 ID 和 `sys_user.wechat_openid`，客户端**不得**传入 userId。`templateId` 必须来自后端订阅状态接口返回的已配置模板；授权结果为 `accept` 时累计一次可用授权次数，`reject` / `ban` 会清空对应模板可用次数。

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

## 管理员通知接口

通知管理后台接口，仅 `ADMIN` 角色可访问。路由前缀：`/api/admin/notifications`，完整端点见上节"通知中心接口 - 管理侧"。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/notifications/dashboard` | 仪表盘 |
| GET | `/api/admin/notifications/providers/health` | 服务商健康检查 |
| GET | `/api/admin/notifications/throttle-rules` | 节流规则 |
| PUT | `/api/admin/notifications/throttle-rules` | 保存节流规则 |
| GET | `/api/admin/notifications` | 通知列表 |
| GET | `/api/admin/notifications/{id}` | 单条详情 |
| POST | `/api/admin/notifications` | 创建通知 |
| POST | `/api/admin/notifications/{id}/cancel` | 取消通知 |
| POST | `/api/admin/notifications/{id}/resend` | 重发通知 |
| GET | `/api/admin/notifications/{id}/recipients` | 接收人列表 |
| GET | `/api/admin/notifications/{id}/deliveries` | 投递日志 |
| POST | `/api/admin/notifications/mini-program/authorization-guides` | 小程序订阅授权引导 |
| POST | `/api/admin/notifications/mini-program/authorization-guides/{userId}` | 针对指定用户的订阅授权引导 |

> 字段级（收件人 / 投递 / Provider / 模板 / 覆盖率 / 订阅运维）说明见上一节"通知中心接口 - 管理侧"。

---

## 系统公告接口

管理员发布/取消系统公告，路由前缀：`/api/admin/system-notices`。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/admin/system-notices` | ADMIN | 公告列表 |
| POST | `/api/admin/system-notices` | ADMIN | 发布公告 |
| POST | `/api/admin/system-notices/{id}/cancel` | ADMIN | 取消公告 |

---

## 定时任务接口

调度任务管理与执行日志，路由前缀：`/api/scheduled-tasks`。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/scheduled-tasks` | ADMIN | 任务列表 |
| GET | `/api/scheduled-tasks/{id}` | ADMIN | 任务详情 |
| POST | `/api/scheduled-tasks` | ADMIN | 创建任务 |
| PUT | `/api/scheduled-tasks/{id}` | ADMIN | 更新任务 |
| PUT | `/api/scheduled-tasks/{id}/enable` | ADMIN | 启用任务 |
| PUT | `/api/scheduled-tasks/{id}/disable` | ADMIN | 停用任务 |
| POST | `/api/scheduled-tasks/{id}/run` | ADMIN | 立即执行一次 |
| DELETE | `/api/scheduled-tasks/{id}` | ADMIN | 删除任务 |
| GET | `/api/scheduled-tasks/{id}/logs` | ADMIN | 执行日志 |

**创建任务请求体：**

```json
{
  "name": "每日价格汇总",
  "cron": "0 0 1 * * ?",
  "handler": "priceSummaryJob",
  "enabled": true
}
```

---

## 产品年度预算接口

按年度存储产品预算价格，供价格走势参考，路由前缀：`/api/product-budgets`。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/product-budgets` | ADMIN / EDITOR / VIEWER | 预算列表（按产品+年份） |
| POST | `/api/product-budgets` | ADMIN / EDITOR | 创建/更新年度预算 |
| DELETE | `/api/product-budgets/{id}` | ADMIN | 删除预算 |

**预算列表查询参数：** `productId`、`year`、`page`、`size`。

**创建/更新年度预算请求体：**

```json
{
  "productId": 1,
  "year": 2026,
  "budgetPrice": 5500.00,
  "currency": "CNY",
  "remark": "年度目标价"
}
```

---

## API 授权管理接口

外部 API Key 的增删改查、服务开关、权限树，仅 `ADMIN` 角色。路由前缀：`/api/api-keys`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-keys` | 列表（分页） |
| POST | `/api/api-keys` | 创建（返回一次性 App Secret） |
| GET | `/api/api-keys/{id}` | 详情 |
| PUT | `/api/api-keys/{id}` | 更新名称、描述、环境、过期时间、白名单、限流和权限 |
| PUT | `/api/api-keys/{id}/enable` | 启用 |
| PUT | `/api/api-keys/{id}/disable` | 停用 |
| PUT | `/api/api-keys/{id}/revoke` | 吊销（不可逆） |
| GET | `/api/api-keys/permissions/tree` | 获取外部 API 权限端点元数据 |
| GET | `/api/api-keys/service-status` | 服务总开关状态 |
| PUT | `/api/api-keys/service-status` | 切换服务总开关 |

> 列表权限树实际接口路径为 `/api/api-keys/permissions/tree`（归属 `ApiKeyController`），而 `/api/permissions/tree` 归属 `PermissionController`，用于内部角色权限。

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

> 响应中的 `appSecret` **只展示一次**，关闭后无法再次查看。

**入参校验：**

| 字段 | 规则 |
|------|------|
| `name` | 必填，不能为空白 |
| `environment` | 必填，取值来自字典 `api_key_environment` |
| `rateLimitPerMinute` | 必填，0 或正整数；0 表示不限制分钟请求次数 |
| `dailyLimit` | 必填，0 或正整数；0 表示不限制每日请求次数 |
| `expireTime` | 可选，格式 `yyyy-MM-dd HH:mm:ss` |
| `permissionCodes` | 管理页面要求至少选择一项；接口层会忽略不存在或未启用的权限编码 |

---

## API 调用日志接口

外部 API 调用流水记录，仅 `ADMIN` 角色，路由前缀：`/api/api-call-logs`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/api-call-logs` | 日志列表（分页） |
| GET | `/api/api-call-logs/statistics` | 统计概览（按时间/状态/密钥） |
