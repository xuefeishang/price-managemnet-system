---
title: API 设计文档
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/项目设计文档.md
---

# API 设计文档

本系统提供两类 API：

- **内部 API**（`/api/**`）：JWT 认证，面向 PC 与小程序前端
- **外部 API**（`/api/external/v1/**`）：API Key HMAC-SHA256 签名认证，面向第三方系统

## RESTful 规范

| 操作 | 方法 | 路径 |
|------|------|------|
| 列表查询 | GET | `/api/xxx` |
| 详情查询 | GET | `/api/xxx/{id}` |
| 新增 | POST | `/api/xxx` |
| 更新 | PUT | `/api/xxx/{id}` |
| 删除 | DELETE | `/api/xxx/{id}` |

**统一响应格式：**

```json
{
  "code": 200,
  "message": "success",
  "timestamp": "2026-06-15T10:30:00",
  "data": { ... }
}
```

`timestamp` 字段由 v1.3.3 引入，便于前端统一处理本地时间和时区。

**分页参数：** `page`（从 0 开始）、`size`、`sort`

**分页响应格式：**

```json
{
  "code": 200,
  "data": {
    "content": [...],
    "totalElements": 100,
    "totalPages": 5,
    "number": 0,
    "size": 20
  }
}
```

## 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/refresh-token | 刷新 AccessToken；刷新时重新校验账号启用、未锁定和有效角色 |
| GET | /api/auth/profile | 兼容获取当前用户信息，委托个人中心服务 |
| PUT | /api/auth/profile | 兼容更新当前用户昵称、邮箱、手机号 |
| PUT | /api/auth/password | 兼容修改密码；成功后撤销全部 refresh token 并要求重新登录 |

## 个人中心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/profile | 获取个人中心资料、角色和权限 |
| PUT | /api/profile | 更新昵称、邮箱、手机号 |
| GET | /api/profile/security | 获取安全信息 |
| PUT | /api/profile/password | 修改密码并撤销全部 refresh token |
| GET | /api/profile/operation-logs | 查询我的操作记录，仅当前用户 |
| GET | /api/profile/sessions | 查询当前有效会话 |
| DELETE | /api/profile/sessions/{id} | 撤销指定其他设备会话 |
| DELETE | /api/profile/sessions/others | 退出其他设备 |
| DELETE | /api/profile/sessions/all | 退出全部设备 |
| GET | /api/profile/login-history | 查询我的登录历史 |
| GET | /api/profile/preferences | 获取个人偏好 |
| PUT | /api/profile/preferences | 更新个人偏好 |

## 用户管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/users | 分页查询用户，支持关键字、角色、状态、部门筛选 |
| POST | /api/users | 使用 `UserCreateRequest` 新增用户，写入主角色并同步初始用户角色关联；参数错误返回 HTTP 400，用户名、工号或角色关联冲突返回 HTTP 409 和安全明确原因 |
| PUT | /api/users/{id} | 使用 `UserUpdateRequest` 更新基础资料、部门和状态；拒绝角色、密码、锁定状态等越权字段 |
| PUT | /api/users/{id}/admin-edit | 管理员在单一事务内更新资料、状态和可选新密码，支持显式清空可空字段 |
| POST | /api/users/{id}/reset-password | 管理员通过 JSON 请求体重置指定用户密码，更新 `password_updated_time`；禁止 URL 密码参数 |
| GET | /api/users/{id}/roles | 获取用户角色 ID 列表 |
| GET | /api/users/roles-batch | 批量获取用户角色 ID 映射 |
| POST | /api/roles/assign/{userId} | 独立分配用户多角色；用户编辑弹窗不直接承载角色选择 |

`UserCreateRequest` 仅允许 `username`、`password`、`employeeId`、`role`、`nickname`、`email`、
`phone`、`department`、`deptId`；不得通过创建接口写入锁定状态、微信绑定信息、登录统计或审计字段。
创建用户与初始 `sys_user_role` 关联保持同一事务，写入时主动 flush 以准确识别唯一约束冲突。
系统不提供公开注册接口，`/api/auth/register` 不属于公开路径。

用户 Excel 导入采用"全量预检 + 原子写入"两阶段流程：先校验固定模板、最大行数、字段格式、
密码策略、角色/状态、文件内重复及数据库重复；只有错误清单为空时，才在单一事务内写入
`sys_user` 与 `sys_user_role`。预检失败返回 HTTP 400 和结构化错误清单，并保证导入数量为 0；
写入阶段唯一约束并发冲突返回 HTTP 409。
创建、导入、角色分配和权限解析统一只接受状态为 `ACTIVE` 的角色；停用角色不得继续授权。
导入写入使用批量用户与角色关联持久化，不逐行 flush。

## 产品分类接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/categories | 获取分类列表 |
| POST | /api/categories | 新增分类 |
| PUT | /api/categories/{id} | 更新分类 |
| DELETE | /api/categories/{id} | 删除分类 |
| POST | /api/categories/batch-sort | 批量更新分类排序 |

`GET /api/categories` 支持 `status=ACTIVE|INACTIVE`；不传返回全部，非法状态返回 `code=400`。`/api/origins`、`/api/customers` 使用相同状态过滤语义。

## 产品接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/products | 获取产品分页列表；`keyword` 匹配名称、编码、规格，支持 `categoryId`、`status`、`sortBy`、`sortDirection` |
| GET | /api/products/{id} | 获取产品详情 |
| GET | /api/products/{productId}/price-years | 获取该产品实际存在价格记录的年份 |
| GET | /api/products/{productId}/price-by-date | 获取截至指定日期有效的正式价格（产品详情历史快照） |
| GET | /api/products/{productId}/price-trend | 获取价格趋势数据；支持 `days`、`startDate`、`endDate` |
| POST | /api/products | 新增产品 |
| PUT | /api/products/{id} | 更新产品 |
| DELETE | /api/products/{id} | 删除产品 |
| POST | /api/products/batch-sort | 批量更新产品排序 |

## 预算管理接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/product-budgets | ADMIN/EDITOR/VIEWER | 按 `year`、`keyword`、`categoryId`、`status` 获取产品年度预算工作台数据 |
| GET | /api/product-budgets/{productId} | ADMIN/EDITOR/VIEWER | 按 `productId` 与 `year` 精确获取单个产品年度预算 |
| PUT | /api/product-budgets | ADMIN/EDITOR | 批量保存年度预算，提交 `budgetYear` 与产品预算明细 |

## 价格接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/prices/{productId} | 获取产品价格 |
| PUT | /api/prices/{productId} | 更新价格 |
| GET | /api/prices/{productId}/history | 获取价格历史 |
| POST | /api/products/{productId}/prices | ADMIN/EDITOR | 兼容入口：直接新增正式价格，支持 source 审计参数 |
| PUT | /api/prices/{id} | ADMIN/EDITOR | 兼容入口：直接更新正式价格，支持 source 审计参数 |

## 价格查询接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/price-query | ADMIN/EDITOR/VIEWER | 日常价格分页查询，支持 date、keyword、categoryId、status、page、size；指标以各产品最新有效价格日为基准，v1 兼容预算别名字段保留但已 deprecated |
| GET | /api/price-query/export | ADMIN/EDITOR/VIEWER | 按同一筛选条件导出全部匹配价格指标数据，单次上限 10000 行，不导出 deprecated 兼容字段 |
| GET | /api/price-drafts/by-date | ADMIN/EDITOR | 查询指定日期活动价格草稿 |
| GET | /api/price-drafts/publishable-summary | ADMIN/EDITOR | 查询全系统待发布 DRAFT 草稿汇总 |
| POST | /api/price-drafts/batch-save | ADMIN/EDITOR | 批量保存价格草稿 |
| POST | /api/price-drafts/publish-all | ADMIN/EDITOR | 发布全系统所有 DRAFT 草稿，使价格正式生效 |
| POST | /api/price-drafts/by-date/publish | ADMIN/EDITOR | 按日期发布 DRAFT 草稿；仅用于定时任务、补发或维护入口 |
| POST | /api/price-drafts/{batchId}/publish | ADMIN/EDITOR | 兼容/维护用单批次发布接口 |
| POST | /api/price-drafts/{batchId}/cancel | ADMIN/EDITOR | 取消批次 |

## 导入导出接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/import/excel | Excel 导入 |
| GET | /api/export/excel | Excel 导出 |

## 日志管理接口（仅管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/logs | 分页查询操作日志，返回审计登录名 `username`、展示姓名 `operatorName`、`responseCode`、`errorMessage` 以及派生展示字段 `status` / `errorMsg` |
| GET | /api/logs/recent | 获取最近操作日志 |
| GET | /api/logs/statistics | 获取日志统计信息 |
| GET | /api/logs/reports/monthly | 获取月度报表 |
| GET | /api/logs/reports/yearly | 获取年度报表 |

## 首页仪表盘接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/home/dashboard | 获取仪表盘数据（摘要+重点产品+预警+趋势） |
| GET | /api/home/summary | 获取摘要统计（产品总数、今日更新、覆盖品类、价格异动） |
| GET | /api/home/alerts | 获取价格预警列表 |
| GET | /api/home/trend | 获取趋势分析数据 |
| GET | /api/home/product-order | 获取首页产品列表排序树（启用分类+启用产品） |

首页产品相关响应约定：

- `ProductMetricDTO` 返回 `originIds`，用于重点关注指标等产品卡片解析产地。
- `/api/home/product-order` 的产品节点返回 `originIds`，用于样式设置"首页排序"页签展示真实启用产品信息。
- 前端只展示已配置且可通过 `origin` 字典解析的产地；无产地时不显示占位文本。

## 通知中心接口（v1.5.0+）

### 用户侧接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/notifications/my | 拉取我的通知列表 |
| GET | /api/notifications/events | **SSE 长连接**，推送连接、新通知和未读数变化事件 |
| GET | /api/notifications/unread-count | 未读数 |
| PUT | /api/notifications/{id}/read | 标记单条已读 |
| PUT | /api/notifications/read-all | 全部已读 |
| GET | /api/notifications/preferences | 获取通知偏好 |
| PUT | /api/notifications/preferences | 更新通知偏好 |
| DELETE | /api/notifications/{id} | 删除单条 |

### 管理员接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/admin/notifications | ADMIN | 查询全局通知消息，支持 type、priority、businessType、channel、deliveryStatus、keyword、startTime、endTime 筛选，并返回收件人数、未读人数、失败投递数 |
| GET | /api/admin/notifications/{id} | ADMIN | 查询通知详情 |
| GET | /api/admin/notifications/{id}/recipients | ADMIN | 查询通知收件人，返回 `username` / `nickname` 供管理端展示人名 |
| GET | /api/admin/notifications/{id}/deliveries | ADMIN | 查询渠道投递日志 |
| POST | /api/admin/notifications/deliveries/{id}/retry | ADMIN | 重试指定失败外部投递记录；拒绝站内、成功、待处理和已跳过投递 |
| GET | /api/admin/notifications/dashboard | ADMIN | 查询通知指标看板、Outbox 积压和渠道投递指标 |
| GET | /api/admin/notifications/providers/health | ADMIN | 查询 Provider 健康状态和配置状态 |
| GET | /api/admin/notifications/throttle-rules | ADMIN | 查询聚合频控规则 |

### 渠道配置接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/admin/notifications/channels/{channel}/config | ADMIN | 查询渠道配置详情；当前支持 `MINI_PROGRAM`，返回可编辑 AppID、AppSecret 配置状态/来源/脱敏指纹、生效模板摘要和诊断清单，不返回 AppSecret 明文 |
| PUT | /api/admin/notifications/channels/{channel}/config | ADMIN | 保存渠道基础配置；需要 `system:setting`，AppSecret 只写入密文不回显，模板版本改由小程序模板接口维护 |
| POST | /api/admin/notifications/channels/{channel}/test | ADMIN | 执行渠道配置本地诊断，返回通过项、缺项和严重性 |

### 小程序模板接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/admin/notifications/mini-program/templates | ADMIN | 查询小程序模板通知类型分组、版本状态、字段映射、测试状态和重新授权影响摘要 |
| POST | /api/admin/notifications/mini-program/templates | ADMIN | 创建小程序模板草稿；需要 `system:setting` |
| PUT | /api/admin/notifications/mini-program/templates/{id} | ADMIN | 编辑非生效模板的模板 ID、跳转页和字段映射；需要 `system:setting` |
| POST | /api/admin/notifications/mini-program/templates/{id}/validate | ADMIN | 对模板版本执行本地结构校验；生效模板真实微信投递继续通过渠道测试投递入口执行 |
| POST | /api/admin/notifications/mini-program/templates/{id}/publish | ADMIN | 发布模板为当前通知类型生效版本，自动停用旧生效模板并刷新资格快照；需要 `system:setting` |
| POST | /api/admin/notifications/mini-program/templates/{id}/disable | ADMIN | 停用模板并刷新资格快照；需要 `system:setting` |
| POST | /api/admin/notifications/mini-program/templates/{id}/rollback | ADMIN | 将历史模板复制为新的生效版本并刷新资格快照；需要 `system:setting` |

### 小程序订阅授权接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/admin/notifications/mini-program/coverage | ADMIN | 查询小程序订阅消息覆盖率，支持目标角色和通知类型 |
| GET | /api/admin/notifications/mini-program/subscriptions | ADMIN | 分页查询小程序订阅授权台账，支持角色、状态、关键词筛选 |
| GET | /api/admin/notifications/mini-program/subscriptions/{userId} | ADMIN | 查询单用户小程序订阅授权详情，OpenID 和模板 ID 脱敏返回 |
| POST | /api/admin/notifications/mini-program/authorization-guides | ADMIN | 按筛选条件发送小程序订阅授权站内引导 |
| POST | /api/admin/notifications/mini-program/authorization-guides/{userId} | ADMIN | 对单用户发送小程序订阅授权站内引导 |

### 系统公告接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/admin/system-notices | ADMIN | 查询系统公告列表 |
| POST | /api/admin/system-notices | ADMIN | 创建系统公告，支持立即发布或定时发布 |
| POST | /api/admin/system-notices/{id}/publish | ADMIN | 立即发布公告并生成标准通知 |
| POST | /api/admin/system-notices/{id}/cancel | ADMIN | 撤回未发布或已发布未过期公告 |

## 定时任务接口（v1.5.0+）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/scheduled-tasks | ADMIN | 查询通用定时任务 |
| POST | /api/scheduled-tasks | ADMIN | 创建任务（name/cron/handler/enabled） |
| PUT | /api/scheduled-tasks/{id}/enable | ADMIN | 启用 |
| PUT | /api/scheduled-tasks/{id}/disable | ADMIN | 停用 |
| POST | /api/scheduled-tasks/{id}/run | ADMIN | 立即执行一次 |
| POST | /api/scheduled-tasks/{id}/run-once | ADMIN | 手动执行一次定时任务 |
| GET | /api/scheduled-tasks/{id}/logs | ADMIN | 执行日志 |

## 审批流程接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/approvals/workflows | 获取所有工作流 |
| GET | /api/approvals/workflows/active | 获取激活的工作流 |
| GET | /api/approvals/workflows/{id} | 获取工作流详情 |
| POST | /api/approvals/workflows | 创建工作流 |
| PUT | /api/approvals/workflows/{id} | 更新工作流 |
| DELETE | /api/approvals/workflows/{id} | 删除工作流 |
| PUT | /api/approvals/workflows/{id}/activate | 激活工作流 |
| PUT | /api/approvals/workflows/{id}/deactivate | 停用工作流 |
| GET | /api/approvals/workflows/{workflowId}/nodes | 获取工作流节点 |
| POST | /api/approvals/workflows/{workflowId}/nodes | 添加节点 |
| PUT | /api/approvals/nodes/{id} | 更新节点 |
| DELETE | /api/approvals/nodes/{id} | 删除节点 |
| GET | /api/approvals/requests | 分页查询审批请求 |
| GET | /api/approvals/requests/pending | 获取待我审批 |
| GET | /api/approvals/requests/my | 获取我的申请 |
| POST | /api/approvals/requests | 创建审批请求 |
| PUT | /api/approvals/requests/{id}/approve | 审批通过 |
| PUT | /api/approvals/requests/{id}/reject | 审批拒绝 |
| PUT | /api/approvals/requests/{id}/cancel | 撤回申请 |
| GET | /api/approvals/requests/{id}/records | 获取审批记录 |

## 字典管理接口

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/dict | ADMIN/EDITOR/VIEWER | 获取字典列表（可选 category 筛选） |
| GET | /api/dict/active | ADMIN/EDITOR/VIEWER | 获取启用字典列表（按分类） |
| GET | /api/dict/categories | ADMIN/EDITOR/VIEWER | 获取字典分类列表 |
| GET | /api/dict/{id} | ADMIN/EDITOR/VIEWER | 获取字典项详情 |
| POST | /api/dict | ADMIN/EDITOR | 创建字典项 |
| POST | /api/dict/batch | ADMIN | 批量创建字典项 |
| PUT | /api/dict/{id} | ADMIN/EDITOR | 更新字典项 |
| DELETE | /api/dict/{id} | ADMIN | 删除字典项 |

## 样式管理接口（仅管理员）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /api/style/config | ADMIN/EDITOR/VIEWER | 获取样式配置 |
| GET | /api/style/themes | ADMIN/EDITOR/VIEWER | 获取预设主题列表 |
| PUT | /api/style/config | ADMIN | 更新样式配置 |
| PUT | /api/style/theme/{themeKey} | ADMIN | 切换主题 |
| POST | /api/style/logo | ADMIN | 上传 Logo |

## 外部 API 授权管理接口（仅管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/api-keys | 分页查询 API Key |
| POST | /api/api-keys | 创建 API Key，返回 Secret 一次 |
| GET | /api/api-keys/{id} | 获取 API Key 详情 |
| PUT | /api/api-keys/{id} | 更新名称、描述、环境、过期时间、白名单、限流和权限 |
| PUT | /api/api-keys/{id}/enable | 启用 API Key |
| PUT | /api/api-keys/{id}/disable | 停用 API Key |
| PUT | /api/api-keys/{id}/revoke | 吊销 API Key |
| GET | /api/api-keys/service-status | 获取外部 API 部署级和运行时服务状态 |
| PUT | /api/api-keys/service-status | 开启或暂停外部 API 运行时服务 |
| GET | /api/api-keys/permissions/tree | 获取外部 API 权限端点 |
| GET | /api/api-call-logs | 分页查询外部 API 调用日志 |
| GET | /api/api-call-logs/statistics | 查询外部 API 基础统计 |

## 外部 API 接口（API Key HMAC-SHA256 签名）

> 独立安全链 `externalApiSecurityFilterChain`，路径前缀 `/api/external/v1/`。

| 权限编码 | 方法 | 路径 | 说明 |
|----------|------|------|------|
| product:read | GET | /api/external/v1/products、/api/external/v1/products/{id} | 产品读取 |
| product:write | POST/PUT | /api/external/v1/products、/api/external/v1/products/{id} | 产品写入 |
| product:delete | DELETE | /api/external/v1/products/{id} | 产品删除 |
| price:read | GET | /api/external/v1/products/{id}/price-*、/api/external/v1/prices/by-date* | 价格读取 |
| price:write | POST/PUT | /api/external/v1/products/{id}/prices、/api/external/v1/prices/{id} | 价格写入 |
| price-query:read | GET | /api/external/v1/price-query | 价格查询 |
| price-query:export | GET | /api/external/v1/price-query/export | 价格导出 |
| category:read | GET | /api/external/v1/categories/** | 分类读取 |
| origin:read | GET | /api/external/v1/origins/** | 产地读取 |
| customer:read | GET | /api/external/v1/customers/** | 客户读取 |
| dict:read | GET | /api/external/v1/dict/** | 字典读取，`/dict` 必须指定 category |
| home:read | GET | /api/external/v1/home/** | 首页数据读取 |

外部基础数据列表支持 `status=ACTIVE|INACTIVE`；为兼容 v1 既有调用，不传或非法 `status` 均按未过滤列表返回。

### 外部 API 控制器清单（v1.3.0+）

| 控制器 | 端点数 | 覆盖资源 |
|--------|--------|----------|
| ExternalBasicDataController | 11 | 分类/产地/客户/字典 只读 |
| ExternalProductController | 5 | 产品 CRUD |
| ExternalPriceController | 9 | 价格 CRUD + 历史 + 趋势 |
| ExternalPriceQueryController | 2 | 价格查询 + 导出 |
| ExternalHomeController | 5 | 首页仪表盘 5 项 |

详细端点定义见 [api/external.md](../api/external.md) 的"外部 API 业务接口"章节。

### 签名规则

请求头：

```
X-App-Id: 应用 ID
X-Timestamp: Unix 时间戳（秒）
X-Nonce: 一次性随机串
X-Signature: HMAC-SHA256 签名
```

签名计算：

```
signature = HMAC-SHA256(
  secret,
  METHOD + "\n" +
  PATH + "\n" +
  QUERY_STRING + "\n" +
  TIMESTAMP + "\n" +
  NONCE + "\n" +
  BODY_SHA256
)
```

详见 [api/auth.md](../api/auth.md)。

## 错误码

| 码 | 说明 | 处理方式 |
|------|------|----------|
| 200 | 成功 | - |
| 400 | 参数错误 | 显示错误信息 |
| 401 | 未认证 | 跳转登录页 |
| 403 | 无权限 | 显示无权限提示 |
| 404 | 资源不存在 | 显示不存在提示 |
| 409 | 业务冲突 | 显示冲突原因 |
| 500 | 服务器错误 | 显示通用错误 |

---

*版本：v2.0.0*
*最后更新：2026-06-15 — v2.0 文档拆分重构*
