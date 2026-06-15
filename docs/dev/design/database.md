---
title: 数据库设计
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/项目设计文档.md
---

# 数据库设计

## ER 图

```
sys_department (树状自关联)
    ↑
    │ 1:N
    │
sys_user ──M:N── sys_role ──M:N── sys_permission
    │
    └── dept_id 外键

sys_role.dept_id → sys_department.id (部门专属角色)

product_category (产品分类表)
    │
    └── 1:N

product (产品表)
    │
    ├── 1:N price (价格表) ←── sys_user (创建人)
    │       │
    │       └── 1:N price_history (价格历史表)
    │
    └── 1:N product_annual_budget (产品年度预算表)

sync_log (同步日志表)

sys_api_key
    │
    ├── 1:N sys_api_key_permission
    ├── 1:N sys_api_call_log
    └── 1:N sys_api_key_operation_log

sys_external_api_endpoint
    └── method + path_pattern -> permission_code

# === 价格草稿表簇（V23）===
price_draft_batch (草稿批次)
    │
    └── 1:N price_draft_item (草稿明细)
            │
            └── 1:1 price_publish_log (发布日志) → price

# === 通知中心表簇（V25-V41）===
notification_message (通知主表)
    │
    ├── 1:N notification_recipient (收件人)
    │       └── status: PENDING/READ/ARCHIVED
    │
    ├── 1:N notification_delivery_log (投递日志)
    │
    └── 1:1 notification_outbox (可靠投递队列)

notification_channel_config (渠道配置)
    ├── 1:N notification_mini_program_template (小程序模板)
    │       └── 1:N notification_mini_program_template_history (历史)
    ├── 1:N notification_mini_program_subscription (用户订阅)
    ├── 1:N notification_mini_program_eligibility (资格快照)
    └── 1:N notification_mini_program_resolution (解析日志)

# === 调度任务表簇（V23）===
sys_scheduled_task (任务定义)
    │
    └── 1:N sys_scheduled_task_log (执行日志)

# === 样式管理表簇（V7-V8）===
sys_style_config (样式配置) ── 1:N ── sys_style_version (版本快照)
sys_style_preset (样式预设) ── 引用 ── sys_style_config

# === 系统公告表簇（V26）===
system_notice (系统公告)
    └── 定时任务自动发布到期，撤回/过期归档对应 notification_message
```

## 表结构说明

### 1. sys_user（用户表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 用户 ID，主键 |
| username | VARCHAR(50) | 用户名，唯一 |
| password | VARCHAR(200) | 密码（BCrypt 加密） |
| role | VARCHAR(20) | 角色：ADMIN/EDITOR/VIEWER |
| dept_id | BIGINT | 部门 ID，外键 |
| status | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| nickname | VARCHAR(50) | 昵称 |
| email | VARCHAR(100) | 邮箱 |
| phone | VARCHAR(20) | 电话 |
| last_login_time | DATETIME | 最近登录时间 |
| last_login_ip | VARCHAR(50) | 最近登录 IP |
| login_count | INT | 登录次数 |
| password_updated_time | DATETIME | 密码更新时间 |
| is_locked | BOOLEAN | 是否锁定 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

### 2. product_category（产品分类表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 分类 ID，主键 |
| name | VARCHAR(100) | 分类名称 |
| code | VARCHAR(50) | 分类编码，唯一 |
| sort_order | INT | 首页产品列表分类分组排序 |
| status | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| remark | TEXT | 备注 |

### 3. product（产品表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 产品 ID，主键 |
| name | VARCHAR(200) | 产品名称 |
| code | VARCHAR(100) | 产品编码，唯一 |
| selling_price | DECIMAL(15,4) | 售价 |
| budget_price | DECIMAL(15,4) | 预算价格 |
| category_id | BIGINT | 分类 ID，外键 |
| status | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| description | TEXT | 产品描述 |
| specs | TEXT | 规格参数 |
| image_url | VARCHAR(500) | 图片 URL |
| origin_ids | VARCHAR(500) | 产地 ID 列表（JSON 数组） |
| customer_ids | VARCHAR(500) | 客户 ID 列表（JSON 数组） |
| remark | TEXT | 备注 |
| unit | VARCHAR(50) | 计量单位 |
| sort_order | INT | 分类内产品排序 |
| show_on_home | BOOLEAN | 是否在首页展示 |
| currency | VARCHAR(20) | 计价币种：CNY/USD/EUR |
| version | BIGINT | 乐观锁版本号（NOT NULL） |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

### 4. price（价格表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 价格 ID，主键 |
| product_id | BIGINT | 产品 ID，外键 |
| original_price | DECIMAL(15,4) | 原价 |
| current_price | DECIMAL(15,4) | 现价 |
| cost_price | DECIMAL(15,4) | 成本价 |
| budget_price | DECIMAL(15,4) | 预算价格 |
| effective_date | DATE | 生效日期 |
| expiry_date | DATE | 失效日期 |
| unit | VARCHAR(50) | 单位 |
| price_spec | VARCHAR(200) | 价格规格 |
| created_by | BIGINT | 创建人 |
| version | BIGINT | 乐观锁版本号（NOT NULL） |
| created_time | DATETIME | 创建时间 |

### 5. product_annual_budget（产品年度预算表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 年度预算 ID，主键 |
| product_id | BIGINT | 产品 ID，外键 |
| budget_year | INT | 预算年度 |
| budget_price | DECIMAL(15,4) | 年度预算价格 |
| created_by | BIGINT | 创建人 |
| updated_by | BIGINT | 更新人 |
| remark | VARCHAR(500) | 备注 |
| version | BIGINT | 乐观锁版本号（NOT NULL） |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

约束：`uk_product_budget_year(product_id, budget_year)` 保证同一产品同一年度仅有一条预算；`product_id` 外键关联 `product(id)`。

### 6. price_history（价格历史表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 历史 ID，主键 |
| price_id | BIGINT | 价格 ID，外键 |
| product_id | BIGINT | 产品 ID，外键 |
| old_price | DECIMAL(15,4) | 旧价格 |
| new_price | DECIMAL(15,4) | 新价格 |
| change_type | VARCHAR(20) | 变动类型：CREATE/UPDATE/DELETE |
| changed_by | BIGINT | 变更操作人 ID |
| remark | TEXT | 备注 |
| changed_time | DATETIME | 变动时间 |

### 7. operation_log（操作日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 日志 ID，主键 |
| username | VARCHAR(50) | 操作人登录名 |
| operation_module | VARCHAR(100) | 操作模块 |
| operation_type | VARCHAR(50) | 操作类型 |
| operation_desc | VARCHAR(500) | 操作描述 |
| response_code | VARCHAR(10) | 响应状态码 |
| error_message | TEXT | 错误信息 |
| ip_address | VARCHAR(50) | IP 地址 |
| operation_time | DATETIME | 操作时间 |

接口返回会额外提供派生字段：`operatorName`（操作人姓名，来自 `sys_user.nickname`，查不到用户时回退 `username`）、`status`（中文值 `成功` / `失败`，由 `response_code` 与 `error_message` 计算）和 `errorMsg`（兼容前端展示，等同于 `error_message`）。

操作日志由两种方式写入：常规控制器可手动调用 `OperationLogHelper`；标注 `@OperationLog` 的接口由 `OperationLogAspect` 自动记录，避免同一接口同时使用两种方式造成重复日志。日志查询的时间筛选参数统一为本地时间字符串 `yyyy-MM-dd HH:mm:ss`。

### 8. refresh_token（刷新令牌表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| token | VARCHAR(500) | Refresh Token 原文，唯一 |
| user_id | BIGINT | 用户 ID |
| username | VARCHAR(50) | 用户名 |
| device_name | VARCHAR(100) | 设备展示名 |
| ip_address | VARCHAR(50) | 登录或最近刷新 IP |
| user_agent | VARCHAR(500) | 浏览器 User-Agent |
| expiry_date | DATETIME | 过期时间 |
| revoked | BOOLEAN | 是否撤销 |
| created_time | DATETIME | 创建时间 |
| last_used_time | DATETIME | 最近使用时间 |

### 9. sys_login_history（登录历史表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID，失败且无法识别用户时为空 |
| username | VARCHAR(100) | 登录名 |
| login_time | DATETIME | 登录时间 |
| ip_address | VARCHAR(50) | 登录 IP |
| user_agent | VARCHAR(500) | 登录设备 |
| result | VARCHAR(20) | SUCCESS / FAILED |
| failure_reason | VARCHAR(500) | 失败原因摘要 |
| created_time | DATETIME | 创建时间 |

### 10. sys_user_preference（个人偏好表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID，唯一 |
| table_density | VARCHAR(20) | 表格密度：COMPACT / DEFAULT / COMFORTABLE |
| default_home_path | VARCHAR(200) | 默认首页路径 |
| theme_mode | VARCHAR(20) | 主题模式：SYSTEM / LIGHT / DARK |
| page_size | INT | 默认分页大小 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |
| version | BIGINT | 乐观锁 |

### 11. approval_workflow（审批工作流表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 流程 ID，主键 |
| workflow_name | VARCHAR(100) | 流程名称 |
| workflow_type | VARCHAR(50) | 流程类型：PRICE_CHANGE, PRODUCT_CREATE |
| approval_level | INT | 审批级别（1-3 级） |
| is_active | BOOLEAN | 是否启用 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

### 12. approval_node（审批节点表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 节点 ID，主键 |
| workflow_id | BIGINT | 流程 ID，外键 |
| node_order | INT | 节点顺序 |
| node_type | VARCHAR(20) | 节点类型：APPROVER 审批, NOTIFIER 知会 |
| approver_role | VARCHAR(20) | 审批角色：ADMIN, EDITOR |
| is_required | BOOLEAN | 是否必须审批 |

### 13. approval_request（审批请求表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 请求 ID，主键 |
| workflow_id | BIGINT | 流程 ID，外键 |
| business_type | VARCHAR(50) | 业务类型：PRICE, PRODUCT |
| business_id | BIGINT | 业务数据 ID |
| current_node_id | BIGINT | 当前节点 ID，外键 |
| status | VARCHAR(20) | 状态：PENDING/APPROVED/REJECTED/CANCELLED |
| applicant_id | BIGINT | 申请人 ID |
| created_time | DATETIME | 创建时间 |

### 14. approval_record（审批记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 记录 ID，主键 |
| request_id | BIGINT | 请求 ID，外键 |
| node_id | BIGINT | 节点 ID，外键 |
| approver_id | BIGINT | 审批人 ID |
| action | VARCHAR(20) | 操作：APPROVE, REJECT |
| comment | TEXT | 审批意见 |
| old_value | TEXT | 变更前值 |
| new_value | TEXT | 变更后值 |
| action_time | DATETIME | 操作时间 |

### 15. sys_dict（系统字典表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 字典 ID，主键 |
| category | VARCHAR(50) | 字典分类（如 currency, common_status） |
| dict_key | VARCHAR(100) | 字典键（唯一约束：category + dict_key） |
| dict_value | VARCHAR(200) | 字典显示值 |
| extra_value | TEXT | 扩展值（如货币符号、图标名、JSON 配置等） |
| sort_order | INT | 排序顺序 |
| status | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| remark | TEXT | 备注 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

**索引：**

- `idx_dict_category` - category 索引
- `idx_dict_category_key` - (category, dict_key) 唯一索引
- `idx_dict_status` - status 索引
- `idx_dict_sort` - sort_order 索引

### 16. sys_department（部门组织表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 部门 ID，主键 |
| parent_id | BIGINT | 父部门 ID（NULL 表示顶级） |
| dept_code | VARCHAR(50) | 部门编码，唯一 |
| dept_name | VARCHAR(100) | 部门名称 |
| dept_type | VARCHAR(20) | 类型：HEADQUARTERS 总部/COMPANY 公司/DEPARTMENT 部门 |
| leader_id | BIGINT | 部门负责人 ID |
| sort_order | INT | 排序 |
| status | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| path | VARCHAR(500) | 层级路径（如：1/2/3） |
| level | INT | 层级深度 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

**索引：**

- `idx_dept_parent` - parent_id 索引
- `idx_dept_code` - dept_code 唯一索引
- `idx_dept_path` - path 索引

> v1.6.10 澄清：`sys_department` 表 DDL 在 `backend/src/main/resources/init.sql`（基线初始化脚本）中创建，**不在 Flyway V1-V46 迁移中**。这是项目早期的设计选择，新业务表才用 Flyway 管理。

### 17. sys_role（角色表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 角色 ID，主键 |
| role_code | VARCHAR(50) | 角色编码，唯一 |
| role_name | VARCHAR(100) | 角色名称 |
| dept_id | BIGINT | 所属部门 ID（NULL 表示全局角色） |
| is_system | BOOLEAN | 是否系统内置角色 |
| sort_order | INT | 排序 |
| status | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| remark | TEXT | 备注 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

### 18. sys_permission（权限表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 权限 ID，主键 |
| permission_code | VARCHAR(100) | 权限编码，唯一 |
| permission_name | VARCHAR(100) | 权限名称 |
| permission_type | VARCHAR(20) | 权限类型：MENU/BUTTON/API |
| parent_id | BIGINT | 父权限 ID |
| resource_url | VARCHAR(200) | 资源 URL |
| sort_order | INT | 排序 |
| status | VARCHAR(20) | 状态：ACTIVE/INACTIVE |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

### 19. sys_user_role（用户角色关联表）

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 用户 ID |
| role_id | BIGINT | 角色 ID |

### 20. sys_role_permission（角色权限关联表）

| 字段 | 类型 | 说明 |
|------|------|------|
| role_id | BIGINT | 角色 ID |
| permission_id | BIGINT | 权限 ID |

### 21. sync_log（同步日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 日志 ID，主键 |
| sync_type | VARCHAR(50) | 同步类型 |
| sync_source | VARCHAR(100) | 同步源 |
| sync_status | VARCHAR(20) | 同步状态 |
| sync_count | INT | 同步数量 |
| success_count | INT | 成功数量 |
| failed_count | INT | 失败数量 |
| error_message | TEXT | 错误信息 |
| started_time | DATETIME | 开始时间 |
| completed_time | DATETIME | 完成时间 |

### 22. sys_api_key（外部 API 密钥表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| name | VARCHAR(100) | 密钥名称 |
| app_id | VARCHAR(64) | 外部应用 ID，唯一 |
| app_secret_cipher | TEXT | AES-GCM 加密后的 Secret |
| app_secret_key_version | VARCHAR(20) | 加密主密钥版本 |
| app_secret_fingerprint | VARCHAR(64) | Secret SHA-256 指纹，用于审计，不用于验签 |
| description | VARCHAR(500) | 描述 |
| status | VARCHAR(20) | API Key 状态，字典 `api_key_status` |
| environment | VARCHAR(20) | 使用环境，字典 `api_key_environment` |
| expire_time | DATETIME | 过期时间 |
| ip_whitelist | TEXT | IP 白名单 JSON 数组，支持单 IP 和 CIDR |
| rate_limit_per_minute | INT | 分钟限流，0 表示不限制 |
| daily_limit | INT | 日限额，0 表示不限制 |
| created_by | BIGINT | 创建人 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |
| last_used_time | DATETIME | 最后调用时间 |
| version | BIGINT | 乐观锁版本 |

### 23. sys_api_key_permission（外部 API 授权表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| api_key_id | BIGINT | API Key ID，外键到 `sys_api_key.id` |
| permission_code | VARCHAR(100) | 外部接口权限编码 |
| created_time | DATETIME | 创建时间 |

唯一约束：`uk_api_key_permission(api_key_id, permission_code)`。

### 24. sys_external_api_endpoint（外部 API 端点映射表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| permission_code | VARCHAR(100) | 权限编码 |
| method | VARCHAR(10) | HTTP 方法 |
| path_pattern | VARCHAR(200) | Ant 风格路径，如 `/api/external/v1/products/*` |
| description | VARCHAR(200) | 接口说明 |
| request_example | TEXT | 请求示例，用于管理端权限详情说明 |
| response_example | TEXT | 响应示例，用于管理端权限详情说明 |
| error_codes | TEXT | 常见错误码说明 |
| usage_notes | TEXT | 使用提示、安全提示或业务注意事项 |
| query_example | TEXT | Query 示例 JSON，用于生成可复制调用代码 |
| body_example | TEXT | Body 示例 JSON，用于生成可复制调用代码 |
| path_params_example | TEXT | 路径参数示例 JSON |
| query_schema | TEXT | Query 参数轻量 schema |
| body_schema | TEXT | Body 参数轻量 schema |
| path_params_schema | TEXT | 路径参数轻量 schema |
| success_example | TEXT | 成功响应示例 |
| failure_example | TEXT | 失败响应示例 |
| code_notes | TEXT | 代码示例调用注意事项 |
| status | VARCHAR(20) | ACTIVE / INACTIVE |
| sort_order | INT | 排序 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

授权决策以本表为准，字典 `api_permission` 仅负责显示名称。

### 25. sys_api_call_log（外部 API 调用日志表）

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
| nonce | VARCHAR(64) | 请求 Nonce |
| auth_result | VARCHAR(30) | 认证结果，字典 `api_auth_result` |
| error_message | VARCHAR(500) | 错误摘要 |
| created_time | DATETIME | 创建时间 |

### 26. sys_api_key_operation_log（API Key 管理操作日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| api_key_id | BIGINT | API Key ID |
| operation | VARCHAR(50) | 管理操作，字典 `api_key_operation` |
| operator_id | BIGINT | 操作人 ID |
| operator_ip | VARCHAR(50) | 操作人 IP |
| detail | TEXT | 操作详情 JSON，不记录 Secret 原文 |
| created_time | DATETIME | 创建时间 |

### 27. notification_message / notification_recipient / notification_delivery_log / notification_outbox / notification_preference / notification_mini_program_subscription / notification_mini_program_eligibility / notification_mini_program_template / notification_channel_config（通知中心表）

| 表 | 关键字段 | 说明 |
|------|----------|------|
| notification_message | type、title、summary、content、business_type、business_id、channels、priority、link_type、link_params、dedupe_key、expire_time、event_count、created_by、created_time | 通知主表；业务模块统一通过 `NotificationService.create(NotificationCreateCommand)` 写入，`dedupe_key` 防重复生成，`event_count` 记录聚合消息包含的真实事件数量，`link_type/link_params` 支撑 PC 端结构化跳转 |
| notification_recipient | message_id、user_id、read_status、read_time、archived、archived_time、first_seen_time | 接收人状态表；当前支持已读/未读、归档，首次触达字段为通知中心扩展预留 |
| notification_delivery_log | message_id、recipient_id、user_id、channel、status、provider、retry_count、delivered_time、error_code、error_message、is_test | 渠道投递日志；测试投递以 `is_test=true` 隔离，不进入站内列表、不参与聚合和用户偏好延迟；仅失败的外部渠道投递允许人工重试 |
| notification_outbox | event_type、aggregate_type、aggregate_id、payload_json、status、retry_count、next_retry_time、locked_by、lock_until、last_error_code、last_error_message | 外部渠道可靠投递任务表；`uk_notification_outbox_aggregate` 保证同一投递只入队一次；worker 采用 claim、Provider send、result 三段事务边界，发送前重新读取用户偏好，失败退避，超过最大重试后保留 FAILED |
| notification_preference | user_id、notification_type、channel、enabled、quiet_start_time、quiet_end_time、version | 用户通知偏好表；站内通知作为可靠底座不可关闭，外部渠道可按通知类型关闭；普通外部通知命中免打扰时延迟投递，URGENT 可绕过 |
| notification_mini_program_subscription | user_id、openid、notification_type、template_id、status、available_count、last_authorized_time、source、created_time、updated_time | 小程序订阅消息授权表；用户在小程序端点击授权后写入，`available_count` 按微信一次性订阅授权累计，`MINI_PROGRAM` Provider 发送前通过数据库条件更新预占 1 次，临时失败释放预占，授权失效同步清零 |
| notification_mini_program_eligibility | user_id、row_status、openid_bound、configured_template_count、authorized_template_count、available_total、last_authorized_time、config_fingerprint、version | 小程序订阅用户资格查询快照；管理端按聚合行状态 JOIN 数据库分页，授权、额度和用户变化在核心事务提交后刷新，模板配置变化触发重建；不参与 Provider 真实投递判断 |
| notification_mini_program_resolution | user_id、resolve_status、resolve_remark、remind_after、follow_up_required、resolved_by、resolved_time、version | 用户级订阅异常处理记录；支持已处理、暂不提醒、备注和跟进标记，覆盖未绑定 OpenID 的用户；使用乐观锁避免并发覆盖 |
| notification_mini_program_template | notification_type、template_id、page、fields_json、status、last_test_status、last_test_message、last_test_delivery_id、last_test_time、published_by、published_time、active_notification_type（生成列，唯一索引）、created_by、updated_by、version | 小程序订阅模板版本表；PC "小程序模板"页签维护草稿、测试中、生效和停用版本，Provider 优先读取 `ACTIVE` 版本；同一通知类型最多一个 ACTIVE |
| notification_mini_program_template_history | template_id_ref、notification_type、action、operator_id、status_before、status_after、template_id_masked、message、created_time | 小程序订阅模板运维历史表；记录创建、更新、测试、发布、停用和回滚操作，模板 ID 脱敏保存 |
| notification_channel_config | channel、enabled、app_id、endpoint_url、secret_cipher、secret_key_version、secret_fingerprint、timeout_ms、default_page、config_json、created_by、updated_by、version | 通知渠道运行配置表；PC `/notifications` 渠道配置页维护小程序 AppID、启用状态、默认跳转页和密钥托管状态。模板配置优先由 `notification_mini_program_template` 管理，旧 `config_json` 模板仅作兼容兜底 |

小程序消息发布规则：PC `/notifications` 是唯一信息发布管理台。管理员创建系统公告并选择 `MINI_PROGRAM` 渠道后，后端仍强制生成 `IN_APP` 站内消息作为可靠兜底；`MINI_PROGRAM` 投递通过 Outbox 异步调用微信订阅消息接口，跳转到通知页时携带 `messageId` 参数。Provider 未配置、用户未绑定 openid、模板未授权、永久微信错误或微信接口拒收均写入 `notification_delivery_log`，不回滚公告发布。

### 28. system_notice（系统公告表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| title | VARCHAR(200) | 公告标题 |
| summary | VARCHAR(500) | 公告摘要 |
| content | TEXT | 公告正文 |
| target_roles | TEXT | 目标角色 JSON 数组，按 `user_role` 字典编码 |
| channels | TEXT | 渠道 JSON 数组，按 `notification_channel` 字典编码 |
| priority | VARCHAR(20) | 优先级，字典 `notification_priority` |
| status | VARCHAR(20) | 公告状态，字典 `system_notice_status` |
| scheduled_publish_time | DATETIME | 计划发布时间 |
| published_time | DATETIME | 实际发布时间 |
| cancelled_time | DATETIME | 撤回时间 |
| expire_time | DATETIME | 过期时间 |
| notification_message_id | BIGINT | 发布后生成的通知消息 ID |
| created_by | BIGINT | 创建人 |
| created_time / updated_time | DATETIME | 审计时间 |

## 产品目录

系统预置 20 种产品：

| 序号 | 产品名称 | 分类 | 规格 |
|------|----------|------|------|
| 1 | 硫精砂 | 化工产品 | 出厂承兑，As < 0.1% |
| 2 | 硫酸 | 化工产品 | 93% 出厂承兑 |
| 3 | 钼精矿 | 有色金属 | 45-50（元/吨度） |
| 4 | 电铜 | 有色金属 | A 级（元/吨） |
| 5 | 金 | 贵金属 | 2#（元/克） |
| 6 | 银 | 贵金属 | 3#（元/千克） |
| 7 | 钢坯 | 黑色金属 | 方坯 Q235 |
| 8 | 废钢（河北纵横） | 黑色金属 | 厚 6mm |
| 9 | 五氧化二钒 | 有色金属 | 98% 片状 |
| 10 | 镁锭 | 有色金属 | 99990（闻喜）元/吨 |
| 11 | 铅锭 | 有色金属 | 1#（元/吨） |
| 12 | 锌锭 | 有色金属 | 0#（元/吨） |
| 13 | 钯金 | 贵金属 | 99.95（元/克） |
| 14 | 铂金 | 贵金属 | 99.95（元/克） |
| 15 | 硫酸钴 | 化工产品 | ≥20.5% 国产 |
| 16 | 碳酸锂 | 化工产品 | 电池级 99.5% |
| 17 | 钛精矿 | 有色金属 | 48% 不含税 |
| 18 | 无烟煤 | 煤炭及焦炭 | C>85% |
| 19 | 萤石湿粉 | 化工产品 | 97% |
| 20 | 铁精粉 | 黑色金属 | 66% |

## 实施进度

### 第一阶段：项目架构设计 [已完成]

- [x] 项目目录结构设计
- [x] 后端 Spring Boot 项目基础架构
- [x] 前端 Vue3 项目基础架构
- [x] 数据库设计与初始化

### 第二阶段：用户认证与权限管理 [已完成]

- [x] 后端 JWT 认证实现
- [x] 前端用户状态管理
- [x] 三种用户角色实现

### 第三阶段：产品分类管理 [已完成]

- [x] 后端 API 实现
- [x] 前端分类管理页面
- [x] 分类启用/禁用状态管理

### 第四阶段：产品与价格管理 [已完成]

- [x] 产品 CRUD API
- [x] 价格管理 API
- [x] 价格历史记录
- [x] 20 个产品数据初始化

### 第五阶段：导入导出功能 [已完成]

- [x] Excel 导入功能
- [x] Excel 导出功能

### 第六阶段：Service 层架构完善 [已完成]

- [x] ProductService
- [x] PriceService
- [x] ProductCategoryService
- [x] ImportExportService

### 第七阶段：响应式设计 [已完成]

- [x] PC 端布局
- [x] 移动端布局
- [x] 窗口自适应

### 第八阶段：生产环境部署 [已完成]

- [x] 配置文件优化
- [x] 安全加固

### 第九阶段：日志管理与数据可视化 [已完成]

- [x] 操作日志实体与 Repository
- [x] 日志统计 API（getStatistics）
- [x] 月度报表 API（getMonthlyReport）
- [x] 年度报表 API（getYearlyReport）
- [x] 日志管理前端页面（4 个标签页）
- [x] ECharts 数据可视化（趋势图、饼图、柱状图）
- [x] 首页价格趋势图
- [x] 产品详情页响应式价格决策视图（历史日期快照、售价/预算双折线、坐标轴、决策摘要与审计时间线）
- [x] 菜单选中状态逻辑修复
- [x] 菜单层级导航重构（左侧完整菜单树、顶部三级上下文导航、四级下拉收纳）
- [x] 左侧菜单响应性能优化（角色菜单缓存、点击即时选中反馈、active id 预计算）
- [x] 价格查询菜单归一化（`/price-query` 固定为产品管理下唯一二级菜单，避免历史重复数据）

### 第十阶段：审批流程管理 [已完成]

- [x] 审批工作流实体（ApprovalWorkflow）
- [x] 审批节点实体（ApprovalNode）
- [x] 审批请求实体（ApprovalRequest）
- [x] 审批记录实体（ApprovalRecord）
- [x] ApprovalService（CRUD + 审批流程）
- [x] ApprovalController（完整 API 接口）
- [x] 工作流激活/停用功能
- [x] 审批流配置页面（ApprovalConfig.vue）
- [x] 审批管理页面（Approval.vue - 待我审批/我的申请/工作流配置）
- [x] 菜单配置（审批流配置入口）

### 第十一阶段：字典管理 [已完成]

- [x] 字典实体（SysDict）与 Repository
- [x] SysDictService（CRUD + 分类管理）
- [x] SysDictController（完整 API 接口）
- [x] 前端 API 模块（api/dict.ts）
- [x] 前端 useDict composable（全局缓存 + 便捷方法）
- [x] 字典管理页面（DictManagement.vue）
- [x] 所有页面硬编码编码标签替换为字典服务动态获取
  - 状态标签（ACTIVE/INACTIVE → getStatusLabel）
  - 角色选项（ADMIN/EDITOR/VIEWER → getRoleLabel/getDictOptions）
  - 货币符号（CNY/USD/EUR → getCurrencySymbol）
  - 操作类型/模块（operation_type/operation_module → getDictValue）
  - 变更类型/工作流类型/节点类型 → getDictValue
- [x] 数据初始化（DataInitializer 自动初始化字典数据）

### 第十二阶段：Redis 缓存优化 [已完成]

- [x] Docker 部署 Redis（10.7.5.175:6379）
- [x] 后端添加 Redis 依赖
- [x] 配置 Redis 连接和缓存
- [x] SysDictService 缓存改造
- [x] StyleConfigService 缓存改造
- [ ] 测试验证

## Redis 缓存设计

### 缓存策略

| 数据 | 缓存键 | TTL | 说明 |
|------|--------|-----|------|
| 字典数据 | dict:{category} | 1 小时 | 按分类缓存 |
| 主题配置 | style:config | 1 小时 | 全量配置 |
| 用户信息 | user:{id} | 30 分钟 | 用户数据 |

### 技术方案

- 依赖：`spring-boot-starter-data-redis` + `spring-boot-starter-cache`
- 配置：`application.yml` 中 `spring.cache.type=simple`（默认）使用内存缓存；设为 `redis` 时使用 Redis
- 缓存注解：`@Cacheable`、`@CacheEvict`、`@CachePut`
- **注意**：当 Redis 服务器不可达时（如远程办公场景），应用会自动降级为内存缓存，不影响正常运行

### 部署方案

```bash
# Docker 部署 Redis
docker run -d --name redis -p 6379:6379 redis:latest

# 验证连接
docker exec -it redis redis-cli ping
# 应返回 PONG
```

---

## v1.6.8 / v1.6.10 / v1.6.11 数据库增量章节

### v1.6.8 新增表结构清单（v1.5.0 ~ v1.6.7 Flyway V23-V46）

#### 价格草稿（V23）

```sql
price_draft_batch
  id BIGINT PK
  product_id BIGINT
  draft_date DATE
  status VARCHAR(20)  -- DRAFT/PUBLISHED/CANCELLED
  created_by BIGINT
  created_time DATETIME
  updated_time DATETIME

price_draft_item
  id BIGINT PK
  batch_id BIGINT FK
  product_id BIGINT
  draft_date DATE
  price DECIMAL(15,2)
  currency VARCHAR(10)
  item_status VARCHAR(20)  -- DRAFT/PUBLISHED/SKIPPED
  published_price_id BIGINT NULL  -- 已发布后的正式价格 ID

price_publish_log
  id BIGINT PK
  batch_id BIGINT
  item_id BIGINT
  product_id BIGINT
  publish_date DATE
  status VARCHAR(20)  -- SUCCESS/FAILED
  error_message TEXT NULL
  published_at DATETIME
```

#### 调度任务（V23）

```sql
sys_scheduled_task
  id BIGINT PK
  name VARCHAR(100)
  cron VARCHAR(50)
  handler VARCHAR(200)  -- Spring Bean 名称
  task_type VARCHAR(50)  -- PRICE_PUBLISH/NOTIFICATION_CLEANUP/...
  enabled BOOLEAN
  last_run_time DATETIME NULL
  next_run_time DATETIME NULL
  created_time DATETIME
  updated_time DATETIME

sys_scheduled_task_log
  id BIGINT PK
  task_id BIGINT FK
  started_at DATETIME
  finished_at DATETIME NULL
  status VARCHAR(20)  -- RUNNING/SUCCESS/FAILED
  error_message TEXT NULL
  duration_ms BIGINT NULL
```

#### 通知中心（V25-V41，多表）

```sql
notification_message          -- 通知主表（V25）
notification_recipient        -- 收件人
notification_outbox           -- 可靠投递
notification_preference       -- 用户偏好
notification_delivery_log     -- 投递日志
notification_channel_config   -- 渠道配置
notification_mini_program_template              -- 小程序模板
notification_mini_program_template_history      -- 模板历史
notification_mini_program_subscription          -- 用户订阅
notification_mini_program_eligibility           -- 资格快照
notification_mini_program_resolution            -- 解析日志
```

#### 样式管理（V7-V8）

```sql
sys_style_config
  id BIGINT PK
  config_key VARCHAR(100) UNIQUE
  config_value JSON
  is_active BOOLEAN
  updated_by BIGINT
  updated_time DATETIME

sys_style_preset
  id BIGINT PK
  preset_key VARCHAR(50) UNIQUE
  preset_name VARCHAR(100)
  preset_data JSON
  is_built_in BOOLEAN

sys_style_version
  id BIGINT PK
  config_snapshot JSON
  version_label VARCHAR(50)
  created_by BIGINT
  created_time DATETIME
```

#### 产品年度预算（V44）

```sql
product_annual_budget
  id BIGINT PK
  product_id BIGINT
  year SMALLINT
  budget_price DECIMAL(15,2)
  currency VARCHAR(10)
  remark VARCHAR(500)
  created_time DATETIME
  updated_time DATETIME
  UNIQUE KEY uk_product_year (product_id, year)
```

#### 价格指标元数据（V45-V46）

```sql
-- V45 价格指标字典
-- V46 价格指标展示元数据
-- 详见 sys_dict 中 price_metric_group / price_metric 分类
```

### v1.6.10 表结构澄清

- `sys_department` 表 DDL 位于 `backend/src/main/resources/init.sql`（基线初始化脚本），**不在 Flyway V1-V46 迁移中**。
- 这是项目早期的设计选择，新业务表才用 Flyway 管理；基础组织表保留在 init.sql 中。

### v1.6.11 表结构说明

无新增/修改表结构。

---

*版本：v2.0.0*
*最后更新：2026-06-15 — v2.0 文档拆分重构*
