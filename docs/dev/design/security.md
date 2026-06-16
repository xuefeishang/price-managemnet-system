---
title: 安全设计
version: v2.1.0
last_updated: 2026-06-16
source: docs/plan/security-hardening-2026-q2.md
---

# 安全设计

价格管理系统安全架构、防御层次、应急响应与操作规范。
基于 2026-06-16 实战发现（JNDI 注入攻击）总结。

---

## 安全架构总览

5 层防御 + 1 个管理员门户（暂未实施）：

```
┌─────────────────────────────────────────────────────────┐
│ 第 1 层：网络层 - 端口暴露最小化 + 防火墙白名单         │
├─────────────────────────────────────────────────────────┤
│ 第 2 层：网关层 - Nginx 攻击特征 + 分层限流             │
├─────────────────────────────────────────────────────────┤
│ 第 3 层：应用层 - Spring Security 拦截器                 │
├─────────────────────────────────────────────────────────┤
│ 第 4 层：数据层 - 全量审计 + 异常存库                    │
├─────────────────────────────────────────────────────────┤
│ 第 5 层：管理门户 - 异常列表 + 封禁管理（规划中）       │
└─────────────────────────────────────────────────────────┘
```

详细方案见 [docs/plan/security-hardening-2026-q2.md](../../plan/security-hardening-2026-q2.md)。

---

## 1. 网络层（生产服务器 10.7.5.175）

### 1.1 端口暴露原则

| 端口 | 服务 | 暴露范围 | 说明 |
|------|------|----------|------|
| 80 | Nginx HTTP | 公网 | 保留，可选 |
| 443 | Nginx HTTPS | 公网 | 正式域名 |
| 32080 | Nginx HTTPS | 公网 | PC + 小程序统一入口 |
| 32801 | Nginx HTTP | 内网/办公 | 小程序内网测试 |
| 22 | SSH | 公网（需限源）| 远程运维 |
| 3306 | MySQL | **仅内网** | iptables 限源 |
| 6379 | Redis | **仅本机/内网** | iptables 限源 |
| 8080 | Spring Boot | **仅本机** | iptables 限源 |
| 8082 | Harbor | **仅本机/内网** | iptables 限源 |

### 1.2 iptables 限源规则

所有内网服务用 `SECURITY-HARDENING-PHASE1-*` 注释标记：

```bash
# MySQL 3306
iptables -I INPUT -p tcp -s 127.0.0.0/8 --dport 3306 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-3306-ALLOW-LOCAL' -j ACCEPT
iptables -I INPUT -p tcp -s 10.7.5.0/24 --dport 3306 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-3306-ALLOW-LAN' -j ACCEPT
iptables -I INPUT -p tcp --dport 3306 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-3306-DROP' -j DROP

# Harbor 8082 / Redis 6379 / Backend 8080 同模式
```

### 1.3 持久化

**关键**：iptables 默认重启丢失。必须安装 iptables-persistent 并定期保存：

```bash
# 安装（首次）
apt install iptables-persistent
# 保存当前规则
netfilter-persistent save
# 文件位置
/etc/iptables/rules.v4
/etc/iptables/rules.v6
```

修改规则后**必须**重新 `netfilter-persistent save`，否则重启失效。

---

## 2. 网关层（Nginx）

### 2.1 攻击特征拒绝

`nginx.conf` 顶部定义 `map`：

```nginx
map $request_uri $security_blocked {
    default 0;
    ~*(jndi|ldap|rmi|log4j|\$\{|%24%7B|\.\./|\.\.%2f|%2e%2e) 1;
}

server {
    if ($security_blocked) { return 444; }
    # ...
}
```

**覆盖特征**：
- JNDI/LDAP/RMI/log4j（Log4Shell 类）
- `${...}` 表达式注入
- `%24%7B` URL 编码的 `${`
- `../` 路径穿越（含 URL 编码形式）

**为什么不白名单精确路径**：
- 攻击 payload 形态多变
- 业务请求**不应**携带这些特征
- 误杀风险极低

### 2.2 分层限流

```nginx
# 限流 zone 定义（http 层）
limit_req_zone $binary_remote_addr zone=api_global:20m rate=30r/s;
limit_req_zone $binary_remote_addr zone=login:10m rate=10r/m;

# 启停开关
limit_req_dry_run on;   # 第一周观察（仅记录不拒绝）

# 应用
location ^~ /api/ {
    limit_req zone=api_global burst=100 nodelay;
    # ...
}
location ^~ /api/auth/login {
    limit_req zone=login burst=5 nodelay;
    # ...
}
```

**阈值依据**：PC 端日常使用峰值 < 10 r/s，api_global 30r/s 留 3x 余量。
登录 10 r/m（每 6 秒一次）足够人工，挡住自动化爆破。

**观察期 48h 后**：
- 检查 `error_log` 中 `limiting requests` 频率
- 确认无正常用户被误伤
- 改 `limit_req_dry_run off` 启用强制拒绝

### 2.3 安全响应头

每个 server 块必须包含：
```nginx
add_header X-Frame-Options "SAMEORIGIN" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
```

---

## 3. 应用层（Spring Security）

### 3.1 异常处理

`GlobalExceptionHandler` 必须：
- 不直接返回 stack 给前端
- 统一 `Result.error(code, message)` 响应
- 关键异常（4xx/5xx）异步写入 `security_event` 表

### 3.2 敏感数据脱敏

所有日志与操作审计必须经过 `SensitiveDataMasker`：
- password / token / secret / apiKey → `***`
- SQL 关键字 / URL 摘要而非原文

### 3.3 公开端点策略

按 [jwt_public_path_auth_fix](../../../memory/jwt_public_path_auth_fix.md)：
- 公开端点**仍需 JWT**（不白名单）
- 例外：仅 `/api/auth/login`、`/api/auth/refresh-token`、`/api/auth/captcha`、`/actuator/health`（需单独加入 `SystemConstants.PUBLIC_PATHS`）

---

## 4. 数据层（审计与异常）

### 4.1 operation_log 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| ip_address | VARCHAR(45) | 来源 IP（IPv4/IPv6）|
| user_agent | VARCHAR(500) | 浏览器/客户端标识 |
| risk_score | INT | 风险评分（0-100，规划中）|
| security_event_id | BIGINT | 关联 security_event.id（规划中）|

### 4.2 security_event 表（规划中，V47 迁移）

| 字段 | 说明 |
|------|------|
| event_type | ATTACK_BLOCKED / SUSPICIOUS_REQUEST / IP_BANNED / LOGIN_FAILED_BRUTE_FORCE / PERMISSION_DENIED |
| severity | INFO / WARN / ERROR / CRITICAL |
| source_ip | 来源 IP |
| user_agent | UA |
| request_method | HTTP 方法 |
| request_uri | 请求 URI |
| request_params | 请求参数（脱敏后）|
| status_code | HTTP 状态码 |
| description | 事件描述 |
| user_id | 关联用户 |
| action_taken | 已采取的措施 |
| resolved | 是否已处理 |
| resolved_by | 处理人 |
| resolution_note | 处理说明 |

### 4.3 ip_blacklist 表（规划中，V47 迁移）

| 字段 | 说明 |
|------|------|
| ip_address | 被封 IP（UNIQUE）|
| reason | 封禁原因 |
| banned_by | AUTO_FAIL2BAN / AUTO_NGINX / MANUAL_ADMIN |
| banned_at | 封禁时间 |
| expires_at | 过期时间（NULL=永久）|
| is_active | 是否生效 |
| banned_by_user_id | 人工封禁的管理员 |

---

## 5. 应急响应

### 5.1 发现攻击迹象

1. **后端日志** 含 `${jndi:...}`、`Invalid character`、高频 5xx
2. **nginx access log** 含 `444`、`499`、攻击特征
3. **系统监控** 异常 CPU/带宽/连接数

### 5.2 立即响应

```bash
# 1. 查看来源 IP 分布
grep -E 'jndi|rmi' /var/lib/docker/containers/*/price-management-frontend*.log | \
  awk '{print $1}' | sort | uniq -c | sort -rn | head

# 2. 手动封禁 IP（立即生效）
iptables -I INPUT -s <ATTACKER_IP> -j DROP
# 持久化
netfilter-persistent save

# 3. 同步到 nginx（如使用 include）
echo "deny <ATTACKER_IP>;" >> /etc/nginx/conf.d/blocked_ips.conf
nginx -t && nginx -s reload
```

### 5.3 长期加固

1. 调整限流阈值
2. 增加 fail2ban 自动封禁（Phase 3）
3. 评估是否升级 WAF

---

## 6. 安全审计清单

### 部署前

- [ ] 代码无明文敏感信息（grep 密码/Token）
- [ ] `.env` 不入库（`.gitignore` 已覆盖）
- [ ] 数据库迁移幂等（IF NOT EXISTS / IF EXISTS）
- [ ] Flyway 校验通过

### 部署后（24h 内）

- [ ] nginx 配置测试通过（`nginx -t`）
- [ ] iptables 规则已保存（`netfilter-persistent save`）
- [ ] SSH 配置仍可登录
- [ ] 业务接口 200/401 正常
- [ ] 后端日志无未预期 ERROR

### 每周

- [ ] 运行 [ops-check skill](../../../.claude/skills/ops-check/skill.md)
- [ ] 查看 nginx access log 攻击趋势
- [ ] 检查 fail2ban 封禁列表

### 每月

- [ ] 审查 iptables 规则是否需要更新
- [ ] 备份所有安全相关配置
- [ ] 检查密钥是否需要轮换

---

## 7. 安全相关 skill 索引

| Skill | 用途 |
|-------|------|
| [ops-check](../../../.claude/skills/ops-check/skill.md) | 运维体检（10 项检查 + 10 分制）|
| [deploy](../../../.claude/skills/deploy/skill.md) | 部署 + Harbor 备份 + 故障排查 |
| [api-doc](../../../.claude/skills/api-doc/SKILL.md) | 生成 API 文档 |
| [db-migration](../../../.claude/skills/db-migration/SKILL.md) | Flyway 迁移脚本 |
| [git-version](../../../.claude/skills/git-version/skill.md) | 版本规范 |

---

*文档版本: v2.1.0*
*最后更新: 2026-06-16 — 基于 v2.1.0 安全加固实战*
*对应方案: [docs/plan/security-hardening-2026-q2.md](../../plan/security-hardening-2026-q2.md)*
