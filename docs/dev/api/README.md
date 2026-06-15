---
title: API 文档总览
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/API调用手册.md
---

# API 文档总览

矿产品价格管理系统后端 API 文档入口。本目录按"规范 → 认证 → 内部接口 → 外部接口"分层组织，便于不同读者快速定位所需内容。

> 本目录由 v2.0 文档重构从 `docs/dev/backup/API调用手册.md` 拆分而来，原始单文件不再更新。

---

## 子文档索引

| 文档 | 用途 | 适用读者 |
|------|------|---------|
| [specs.md](./specs.md) | API 通用规范：基础信息、响应格式、错误码、分页、调用示例 | 所有对接方 |
| [auth.md](./auth.md) | 认证方式：JWT Bearer Token、外部 API Key + HMAC-SHA256 签名、公开路径、Refresh Token | 前端、外部系统 |
| [internal.md](./internal.md) | 内部 API 速查（`/api/**`，不含 `/api/external/v1/**`） | 前端、内部开发 |
| [external.md](./external.md) | 外部 API 详细（`/api/external/v1/**`） | 外部系统对接方 |

---

## 内部 vs 外部 API 速查

| 资源域 | 内部 API 前缀 | 外部 API 路径 |
|--------|---------------|---------------|
| 认证 | `/api/auth/*`、`/api/profile/*` | 无 |
| 产品 | `/api/products/*` | `/products`、`/products/{id}` |
| 价格 | `/api/products/{id}/prices`、`/api/prices/*` | `/products/{id}/price-history`、`/current-price`、`/price-by-date`、`/price-trend`、`/prices/by-date`、`/prices/by-date-with-stats` |
| 价格草稿 | `/api/price-drafts/*` | 无 |
| 价格查询 | `/api/price-query/*` | `/price-query`、`/price-query/export` |
| 首页 | `/api/home/*` | `/home/dashboard`、`/home/summary`、`/home/alerts`、`/home/trend`、`/home/product-order` |
| 分类 | `/api/categories/*` | `/categories`、`/categories/{id}` |
| 产地 | `/api/origins/*` | `/origins`、`/origins/{id}` |
| 客户 | `/api/customers/*` | `/customers`、`/customers/{id}` |
| 字典 | `/api/dict/*` | `/dict?category=`、`/dict/active`、`/dict/categories`、`/dict/{id}` |
| 用户 | `/api/users/*` | 无 |
| 角色 | `/api/roles/*` | 无 |
| 权限 | `/api/permissions/*` | 无 |
| 部门 | `/api/departments/*` | 无 |
| 菜单 | `/api/menus/*` | 无 |
| 操作日志 | `/api/logs/*` | 无 |
| 导入导出 | `/api/import/*` | 无 |
| 样式配置 | `/api/style/*` | 无 |
| 审批流程 | `/api/approvals/*` | 无 |
| 通知中心 | `/api/notifications/*` | 无 |
| 管理员通知 | `/api/admin/notifications/*` | 无 |
| 系统公告 | `/api/admin/system-notices/*` | 无 |
| 定时任务 | `/api/scheduled-tasks/*` | 无 |
| 产品年度预算 | `/api/product-budgets/*` | 无 |
| API 授权管理 | `/api/api-keys/*` | 无（自身是管理外部密钥的接口） |
| API 调用日志 | `/api/api-call-logs/*` | 无 |

> **关键差异**：外部 API 仅暴露"读取类"和"产品/价格写入类"能力；所有用户、角色、权限、审批、日志、定时任务、内部通知、系统设置等管理能力**不**对外暴露。

---

## 认证方式总览

| 维度 | 内部 API | 外部 API |
|------|----------|----------|
| 协议 | JWT Bearer Token | API Key + HMAC-SHA256 签名 |
| 凭据来源 | 用户登录 `/api/auth/login` | 后台 `/api-keys` 创建并发放 |
| 请求头 | `Authorization: Bearer <accessToken>` | `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature` |
| 禁用请求头 | — | `Authorization`、`X-App-Secret` |
| 启用条件 | 默认启用 | 需 `API_KEY_ENABLED=true` 且管理页面开关启用 |
| 适用范围 | `/api/**`（不含 `/api/external/v1/**`） | 仅 `/api/external/v1/**` |
| 详细规范 | 见 [auth.md § JWT](./auth.md#jwt-bearer-token) | 见 [auth.md § 外部 API Key 签名](./auth.md#外部-api-key-签名认证) |

---

## 角色权限矩阵

| 角色 | 权限范围 |
|------|----------|
| **ADMIN** | 全部接口，包括所有管理类、删除类、API Key 管理、调用日志、定时任务、系统公告、菜单配置等 |
| **EDITOR** | 查看 + 新增 + 编辑（**无删除权限**）。覆盖产品、价格、分类、产地、客户、价格草稿、审批提交与审批操作等 |
| **VIEWER** | 仅查看，只读访问产品、价格、分类、产地、客户、字典、首页数据等 |

> 标记为"已登录用户"的接口表示不做角色判断，仅需 JWT 有效即可访问；标记为 `ADMIN` 的接口严格限制仅管理员可调用。

---

## 阅读路径建议

| 场景 | 建议阅读顺序 |
|------|-------------|
| 前端开发对接 | specs → auth（JWT 部分）→ internal |
| 外部系统对接 | specs → auth（完整）→ external |
| API Key 管理后台开发 | specs → auth（外部签名）→ internal（`/api/api-keys/*`、`/api/api-call-logs/*`） |
| 通用规范查阅 | specs → auth（按章节） |
