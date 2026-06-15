---
title: 设计文档总览
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/项目设计文档.md + 项目设计规范.md + UI设计说明.md
---

# 设计文档总览

本目录是矿产品价格管理系统的设计文档集（v2.0.0）。系统由 v1.x 的三份混合设计文档（项目设计文档.md / 项目设计规范.md / UI设计说明.md）拆分而来。

## 文件导航

| 文件 | 职责 | 受众 | 目标读者 |
|------|------|------|----------|
| [architecture.md](./architecture.md) | 项目概述、技术选型、功能模块、架构设计 | 架构师、新成员 | 需要快速了解系统全貌 |
| [database.md](./database.md) | ER 图、核心表结构、产品目录、实施进度 | DBA、后端开发 | 设计或修改数据库时 |
| [api-design.md](./api-design.md) | REST API 端点清单、请求响应、外部 API 控制器 | 前端、后端、第三方集成 | 实现或对接接口时 |
| [specifications.md](./specifications.md) | 字典治理、模块架构、配置分层、CSS 变量、组件规范 | 全栈开发 | 编码时遵循规范 |
| [ui.md](./ui.md) | 页面清单、配色、字体、响应式、交互、触摸规范 | 前端、设计师 | 设计或调整 UI 时 |

## 设计原则

本系统遵循以下核心原则，详细规范见 [specifications.md](./specifications.md) 与 [CLAUDE.md](../../../CLAUDE.md)。

### 1. 禁止硬编码

所有业务编码的显示名称必须从字典服务动态获取，严禁在前端代码中硬编码中文标签。

- 后端：`sys_dict` 表存储所有编码映射，`SysDictService` 提供分类查询
- 前端：`useDict` composable 提供全局缓存和便捷方法
- 字典分类和键值对是系统的**数据契约**，修改字典即可改变全系统显示，无需改代码

### 2. 单一职责

每个文档、每个模块、每个 Controller 只负责一个明确的领域：

- **设计文档**：按 architecture / database / api-design / specifications / ui 拆分，互不重复
- **后端模块**：controller / service / repository / entity / dto 五层清晰分离
- **API 控制器**：内部 `/api/**` 与外部 `/api/external/v1/**` 走不同的安全链

### 3. 简洁优先

能用约定就不写配置，能复用就不重新实现：

- 表结构：使用 `init.sql`（基线）+ Flyway V1-V46（业务迁移）
- 配置：环境变量 > application-{profile}.yml > application.yml > sys_dict 字典
- 前端：UnoCSS shortcuts > scoped CSS > 一次性原子类
- 样式：CSS 变量绑定后台配置，支持动态主题切换

### 4. 一致性优先

每次功能变化必须执行三步流程（详见 [CLAUDE.md](../../../CLAUDE.md)）：

1. 检查前后端与数据库一致性（API 路径、DTO 字段、Entity 注解）
2. 更新项目文档（README.md、设计文档、API 文档）
3. 同步数据字典（`sys_dict` 表、数据字典文档）

## 文档与代码对应关系

| 设计文档 | 对应代码 | 增量章节 |
|----------|----------|----------|
| architecture.md §功能模块 | `backend/src/main/java/com/pricemanagement/{controller,service}/` | v1.6.8 通知管理平台、v1.5.0+ 价格草稿、v1.6.3+ 定时任务、v1.6.11 站内通知中心 |
| database.md §表结构 | `backend/src/main/resources/init.sql` + `db/migration/V*.sql` | v1.6.10 sys_department 说明、V23 价格草稿簇、V25-V41 通知中心簇、V44 年度预算 |
| api-design.md §端点 | `backend/src/main/java/com/pricemanagement/controller/*Controller.java` | v1.6.8 4 大模块端点、v1.6.11 SSE `/api/notifications/events` |
| specifications.md §字典 | `frontend/src/composables/useDict.ts` + `frontend/src/constants/dictCategoryMeta.ts` | v1.6.9 受保护分类实现位置、v1.6.10 CSS 变量实测、v1.6.11 PasswordPolicy |
| ui.md §页面清单 | `frontend/src/views/*.vue` + `frontend-uniapp/src/pages*/*.vue` | v1.6.8 5 大新页面、v1.6.11 3 大区块澄清、触摸交互规范 |

## 版本演进

| 版本 | 日期 | 主要变更 |
|------|------|----------|
| v1.0.0 | 2026-03-30 | 首次设计完成 |
| v1.3.0 | 2026-05-18 | 用户管理 + 日志管理 UI 优化 |
| v1.4.0 | 2026-05-19 | 首页驾驶舱布局升级 |
| v1.5.0 | 2026-05-20 | 样式设置页面重构为响应式工作台 |
| v1.5.3 | 2026-05-28 | 新增日常价格查询页面 |
| v1.5.4 | 2026-06-03 | 小程序角色化导航升级 |
| v1.6.8 | 2026-06-14 | 补充 4 大新模块（价格草稿/通知/定时任务/年度预算） |
| v1.6.10 | 2026-06-14 | 样式设置实测核对、CSS 变量实测 |
| v1.6.11 | 2026-06-15 | 首页 3 大区块澄清、触摸交互规范 |
| **v2.0.0** | **2026-06-15** | **设计文档拆分重构为 5 个子文件** |

## 历史文档

`docs/dev/backup/` 目录下保留了 v1.x 的三份原始设计文档：

- `项目设计文档.md`（1691 行）→ 已拆分为 architecture.md + database.md + api-design.md
- `项目设计规范.md`（1638 行）→ 已拆分到 specifications.md
- `UI设计说明.md`（549 行）→ 已拆分到 ui.md

备份文件保留作为变更追溯依据，不再直接更新。

## 相关文档

- [CLAUDE.md](../../../CLAUDE.md) — 项目规范（功能变更三步流程）
- [workflow/development.md](../workflow/development.md) — 开发流程、代码规范、Git 规范入口
- [api/README.md](../api/README.md) — API 文档总览
- [api/auth.md](../api/auth.md) — 外部 API 签名与调用规范
- [README.md](../../../README.md) — 项目入口
