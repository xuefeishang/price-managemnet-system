# 通知管理平台验证证据模板

更新时间：2026-06-10

## 文档职责

本文用于归档通知管理平台真实环境验证证据。执行微信联调、真实 MySQL 验证、生产权限复核和敏感日志巡检时，必须复制本模板并填写实际结果。

建议归档文件：

- `notification-management-wechat-verification-YYYYMMDD.md`
- `notification-management-mysql-verification-YYYYMMDD.md`
- `notification-management-production-security-verification-YYYYMMDD.md`

所有截图、日志和接口响应必须脱敏，不得出现 AppSecret、access_token、完整 OpenID、手机号全量或完整用户敏感信息。

## 1. 微信联调验证

| 项目 | 内容 |
| --- | --- |
| 验证日期 |  |
| 验证环境 |  |
| 小程序 AppID | 仅填写脱敏值 |
| 模板 ID | 仅填写脱敏值 |
| 测试用户 | 用户名/角色，OpenID 脱敏 |
| 操作人 |  |

### 1.1 access_token 远程校验

| 项目 | 结果 |
| --- | --- |
| PC 操作入口 | `/notifications` -> 渠道配置 -> 远程校验 |
| 后端接口 | `POST /api/admin/notifications/channels/MINI_PROGRAM/test-token` |
| 结果 | 通过/失败 |
| 错误码 | 失败时填写脱敏错误码 |
| 操作日志 ID |  |
| 证据截图 |  |

### 1.2 真机订阅授权

| 项目 | 结果 |
| --- | --- |
| 小程序入口 |  |
| 授权模板 |  |
| 后端保存状态 | `ACCEPT/REJECT/BAN/UNKNOWN` |
| 可用授权次数 |  |
| PC 订阅授权页结果 |  |
| 证据截图 |  |

### 1.3 隔离测试投递

| 项目 | 结果 |
| --- | --- |
| PC 操作入口 | 订阅授权详情 -> 测试投递 |
| delivery ID |  |
| `is_test` | 必须为 `true` |
| 微信返回码 |  |
| 用户是否收到消息 | 是/否 |
| 是否消耗授权次数 | 是/否，填写前后次数 |
| 是否进入站内列表 | 必须为否 |
| 证据截图 |  |

### 1.4 正式公告投递

| 项目 | 结果 |
| --- | --- |
| 公告 ID |  |
| 通知消息 ID |  |
| 渠道 | `IN_APP + MINI_PROGRAM` |
| 站内通知结果 |  |
| 小程序订阅消息结果 |  |
| delivery/outbox 状态 |  |
| 证据截图 |  |

### 1.5 点击跳转

| 项目 | 结果 |
| --- | --- |
| 模板 page |  |
| 点击后页面 |  |
| REST 数据加载 | 通过/失败 |
| 异常说明 |  |
| 证据截图 |  |

## 2. 真实 MySQL 验证

| 项目 | 内容 |
| --- | --- |
| 验证日期 |  |
| MySQL 版本 |  |
| 数据规模 | 用户/消息/recipient/delivery/outbox 数量 |
| 验证人 |  |

### 2.1 `/api/notifications/my`

- SQL/Explain：

```sql

```

- 结论：

### 2.2 `/api/notifications/unread-count`

- SQL/Explain：

```sql

```

- 结论：

### 2.3 订阅资格分页

- 场景：角色 + 关键词 + 行状态筛选。
- SQL/Explain：

```sql

```

- P50/P95/P99：
- 结论：

### 2.4 Outbox 并发

| 项目 | 结果 |
| --- | --- |
| worker 实例数 |  |
| 并发数 |  |
| 重复领取 | 有/无 |
| 唯一约束冲突处理 | 符合/不符合 |
| 失败样本 |  |

## 3. 生产权限复核

| 角色 | 页签可见 | 远程校验 | 测试投递 | 订阅处理 | 后端拒绝验证 |
| --- | --- | --- | --- | --- | --- |
| ADMIN |  |  |  |  |  |
| EDITOR |  |  |  |  |  |
| VIEWER |  |  |  |  |  |

## 4. 敏感日志巡检

| 范围 | 采样时间 | 结果 | 处置 |
| --- | --- | --- | --- |
| `operation_log.request_params` |  |  |  |
| 通知投递错误 |  |  |  |
| 应用日志 |  |  |  |
| 反向代理日志 |  |  |  |
| 备份文件 |  |  |  |
| 导出文件 |  |  |  |

## 5. 最终结论

- 是否通过：
- 阻塞项：
- 后续任务：
