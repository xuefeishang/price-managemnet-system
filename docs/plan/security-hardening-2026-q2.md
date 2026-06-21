# 价格管理系统安全增强与异常审计方案

## Context

2026-06-16 生产巡检确认，价格管理系统已经遭遇一批 JNDI / LDAP / RMI 探测请求。请求集中打到 `/api/notifications/my`、`/api/products`、`/api/categories`、`/api/price-query`、`/api/price-drafts/by-date` 等接口参数，典型 payload 包含 `${jndi:ldap://...}`、`${jndi:rmi://...}`、`${hostName}`、`${sys:user.name}`、`${sys:java.class.path}`。

当前链路对这些请求返回 `400`，后端 Tomcat 记录 `Invalid character found in the request target`，未发现已成功利用证据。但生产环境目前仍主要依赖 Tomcat 被动拒绝非法字符，缺少网关主动拦截、自动封禁、安全事件入库与管理员可视化闭环。

### 生产核实结论（2026-06-16）

| 核实项 | 结论 | 证据/说明 |
|---|---|---|
| JNDI 攻击尝试 | 已确认，且不是 1 条，是一批连续探测 | Nginx/容器日志显示多个来源 IP 对多个 API 参数注入 `${jndi:*}` |
| 攻击是否已成功 | 未发现成功利用证据 | 请求返回 `400`，后端记录 Tomcat request target 非法字符 |
| `security_event` 表 | 不存在 | 生产库查询计数为 0 |
| `ip_blacklist` 表 | 不存在 | 生产库查询计数为 0 |
| Flyway 版本 | 当前到 V46 | 尚无 V47 安全事件迁移 |
| `operation_log` | 已有 `ip_address`、`user_agent` | 不需要重复新增 `source_ip/user_agent`，应复用现有字段并补 `risk_score/security_event_id` |
| `operation_log` 是否记录 JNDI | 未记录 | `operation_log` 中 JNDI 相关计数为 0 |
| fail2ban | 未启用 | `systemctl is-active fail2ban` 为 inactive |
| UFW | 未启用 | `ufw status` 为 inactive |
| MySQL 3306 | 对外监听 | `0.0.0.0:3306` 与 `[::]:3306` |
| Redis 6379 | 对外监听 | host network，`0.0.0.0:6379` 与 `[::]:6379` |
| Harbor 8082 | 对外监听 | `0.0.0.0:8082` 与 `[::]:8082` |
| 后端 8080 | 对外监听，风险高 | `*:8080` 可绕过 Nginx 直接访问应用 |
| SSH 22 | 对外监听且配置偏宽 | `PermitRootLogin yes`、`PasswordAuthentication yes` |
| CUPS 631 | 仅本机监听 | `127.0.0.1:631` / `[::1]:631`，不应按公网暴露处理 |
| Nginx 限流/特征拦截 | 未发现 | 未看到 `limit_req`、JNDI 特征拒绝、黑名单 include |
| 本地/生产式 `.env` | 存在明文敏感配置 | 不在方案正文暴露具体值，需纳入密钥轮换和文件权限治理 |

### 方案评分目标

本方案 v1.0 定位方向正确，但存在三类不足：

- 低估攻击次数：实际是一批连续探测，不是单条。
- 漏掉更优先风险：后端 `8080` 直连暴露、SSH 密码登录、root 登录。
- 实施顺序偏重开发功能：生产安全整改应先收敛暴露面，再做观测拦截，最后建设管理员门户。

v1.1 目标评分：**9.5+/10**。

评分提升标准：

- 已核实事实准确，不夸大也不漏关键暴露面。
- 每项整改明确业务影响等级、执行窗口、验证方式和回滚方式。
- 优先保障正常业务：先做不影响用户访问的收敛与观测，再逐步启用会改变请求结果的拦截/封禁。
- 避免新增单点风险：安全事件入库异步、限量、脱敏，不让攻击流量拖垮数据库。

---

## 业务影响分级

| 等级 | 含义 | 执行策略 | 示例 |
|---|---|---|---|
| L0 不影响业务 | 不改变用户请求路径、不重启核心服务，或只增加只读观测 | 工作时间可执行 | 日志审计、数据库只读核实、创建空表、补文档 |
| L1 低影响 | 可能 reload 配置或改变恶意请求结果；正常用户理论不受影响 | 工作时间低峰执行，保留即时回滚 | Nginx reload、JNDI 特征 444、保守限流 observe |
| L2 中影响 | 需要重启单个服务或调整端口绑定；对管理/发布链路有影响 | 业务低峰或维护窗口执行 | Redis/MySQL/Harbor 端口收敛、后端 8080 收敛 |
| L3 高影响 | 可能影响管理员登录、部署、镜像 push 或远程运维 | 必须维护窗口 + 备用登录通道 | SSH 限源、禁用密码登录、Harbor 改仅本地 |

---

## 风险清单（修正版）

| 风险编号 | 等级 | 风险描述 | 核实状态 | 业务影响优先级 |
|---|---|---|---|---|
| S-01 | 高 | 后端 `8080` 暴露，绕过 Nginx 安全头、TLS、限流和未来 WAF | 已确认 | P0，L2 |
| S-02 | 高 | MySQL `3306`、Redis `6379`、Harbor `8082` 对外监听 | 已确认 | P0，L2/L3 |
| S-03 | 高 | SSH `22` 对外监听，允许 root 登录和密码登录 | 已确认 | P0，L3 |
| S-04 | 高 | 无主动 WAF/fail2ban，攻击请求仅被动返回 400 | 已确认 | P1，L1 |
| S-05 | 中 | JNDI 探测未入库、未告警、不可在管理员页面处理 | 已确认 | P1/P2，L0-L1 |
| S-06 | 中 | Nginx 缺少攻击特征拒绝与分层限流 | 已确认 | P1，L1 |
| S-07 | 中 | 后端异常日志仍记录完整栈，且部分异常未做安全事件归类 | 已确认 | P2，L1 |
| S-08 | 中 | `operation_log` 已有 IP/UA，但缺风险评分、事件关联和安全事件视图 | 已确认 | P2，L0-L1 |
| S-09 | 中 | 明文敏感配置和默认式密钥治理不足 | 已确认 | P0/P2，L1-L3 |
| S-10 | 低 | CUPS 631 存在但仅本机监听 | 已澄清 | 暂不作为公网风险 |

---

## 设计原则

1. **先收口，后建设**：先处理暴露面和网关拦截，再开发安全中心。
2. **先观测，后封禁**：限流和 404 封禁先观察日志，确认阈值后再强制阻断。
3. **默认不影响业务**：任何可能影响用户、部署、远程运维的操作必须明确窗口和回滚。
4. **最小暴露面**：除 `80/443/32080/32801/22` 外，业务依赖端口默认不对公网开放。
5. **异步与限量**：安全事件写库必须异步、采样或聚合，避免攻击流量放大数据库压力。
6. **不泄密**：日志、数据库、文档和返回体不得保存明文密码、Token、Secret、完整 AppSecret。
7. **可审计**：封禁、解封、忽略事件、导出日志等安全操作自身进入 `operation_log`。
8. **可回滚**：每个变更必须有 1 条验证命令和 1 条回滚路径。

---

## 影响矩阵：不影响业务 vs 会影响业务

### A. 不影响正常业务的修正（优先做）

| 项 | 内容 | 影响等级 | 说明 |
|---|---|---|---|
| A-01 | 新增安全事件表 `security_event`、IP 黑名单表 `ip_blacklist` | L0 | 只新增表，不改变现有业务表和接口 |
| A-02 | 管理端只读安全事件列表 | L0 | 初版只读，不执行封禁 |
| A-03 | 后端新增异步 `SecurityEventService`，默认只记录攻击特征和权限拒绝 | L0/L1 | 不改变成功请求响应；需注意异步队列限流 |
| A-04 | `operation_log` 复用 `ip_address/user_agent`，补充 `risk_score/security_event_id` | L0 | 新增 nullable 字段和索引 |
| A-05 | 日志脱敏增强 | L0 | 不改变业务响应，只减少日志泄露 |
| A-06 | Nginx access log/后端日志巡检脚本 | L0 | 只读观测 |
| A-07 | fail2ban 安装但不启用业务 jail | L0 | 先安装和校验规则 |
| A-08 | 密钥盘点、文件权限检查、不输出明文 | L0 | 只盘点，不轮换 |

### B. 对正常业务影响很低，但会改变异常请求处理

| 项 | 内容 | 影响等级 | 说明 |
|---|---|---|---|
| B-01 | Nginx 拒绝 JNDI/LDAP/RMI/`${...}` 特征请求 | L1 | 正常业务不应携带这些 payload；误杀概率低 |
| B-02 | Nginx 登录接口保守限流 | L1 | 阈值必须高于真实峰值，先观察再强制 |
| B-03 | Nginx 全局限流 observe 阶段 | L1 | 先记录 `$limit_req_status`，不立即拒绝 |
| B-04 | fail2ban 启用 `sshd` jail | L1/L3 | 对暴力破解有帮助，但需配置办公 IP ignoreip |
| B-05 | fail2ban 启用 `nginx-jndi` jail | L1 | 只封禁明确攻击特征 |
| B-06 | 后端统一 4xx/5xx 安全事件归类 | L1 | 需避免所有 404 都入库 |

### C. 会影响运维或业务链路，必须维护窗口

| 项 | 内容 | 影响等级 | 影响面 | 执行要求 |
|---|---|---|---|---|
| C-01 | 后端 `8080` 改为仅本机/内网可访问 | L2 | 可能影响绕过 Nginx 的调试脚本 | 先确认所有客户端都走 Nginx |
| C-02 | MySQL `3306` 改为 `127.0.0.1:3306` 或防火墙限源 | L2 | 影响远程数据库工具直连 | 提供 SSH 隧道替代 |
| C-03 | Redis `6379` bind 到 `127.0.0.1` | L2 | 影响远程 redis-cli 调试 | 先验证后端连接配置 |
| C-04 | Harbor `8082` 仅内网/办公 IP 或仅本机 | L3 | 影响镜像 push/pull | 提前改 push 文档，准备 SSH 隧道 |
| C-05 | SSH 限源、关闭密码登录、禁止 root 密码登录 | L3 | 可能影响远程运维 | 必须保留已验证的密钥登录和备用账号 |
| C-06 | 轮换 DB/Redis/JWT/API 加密主密钥 | L2/L3 | Token 失效、服务重启、历史密文兼容 | 分项轮换，不一次性全换 |

---

## 整改路线图

### Phase 0：只读核实与备份（L0）

目标：形成可回放证据，避免盲改生产。

执行项：

- 记录 `ss -lntup`、`docker ps`、`iptables -S`、`ufw status`、`systemctl is-active fail2ban`。
- 查询 Flyway 当前版本和 `security_event/ip_blacklist` 表是否存在。
- 备份生产 `docker-compose.yml`、`nginx.conf`、`.env`、`/etc/ssh/sshd_config`、当前防火墙规则。
- 记录当前开放端口和依赖方：PC、H5、小程序、Harbor push、SSH 运维、数据库远程工具。

验收：

- 备份目录存在且包含配置快照。
- 明确哪些端口必须公网开放：默认仅 `80/443/32080/32801/22`，其中 `22` 需限源。

### Phase 1：P0 暴露面收敛（优先，分项执行）

#### 1.1 后端 8080 收敛

推荐：

- Docker compose 端口改为 `127.0.0.1:8080:8080`，或删除端口映射并让 Nginx 通过 host/bridge 内网访问。
- 如果 backend 使用 `network_mode: host`，优先用防火墙拒绝公网访问 `8080`，只允许本机和可信内网。

影响：L2。正常 PC/小程序经 Nginx 访问不受影响；直连 `http://server:8080` 的调试脚本会失效。

验证：

```bash
ss -lntup | grep ':8080'
curl -sS http://127.0.0.1:8080/api/auth/captcha
```

回滚：恢复原 compose 或防火墙规则。

#### 1.2 MySQL 3306 收敛

推荐：

- 短期：iptables/ufw 限制 `3306` 仅允许 `127.0.0.1`、可信内网、必要运维 IP。
- 中期：`mysql8` 端口改为 `127.0.0.1:3306:3306`。

影响：L2。远程数据库工具需改用 SSH 隧道。

#### 1.3 Redis 6379 收敛

推荐：

- 短期：Redis 启动命令加 `--bind 127.0.0.1`，或防火墙拒绝公网 `6379`。
- 中期：取消 `network_mode: host`，改为 bridge 网络 + `127.0.0.1:6379:6379`。

影响：L2。后端当前 `REDIS_HOST=127.0.0.1`，生产本机部署下正常业务不应受影响；改前必须验证后端与 Redis 位于同一网络语义下。

#### 1.4 Harbor 8082 收敛

推荐：

- 若只在服务器本机 build/push：改为 `127.0.0.1:8082:8080`。
- 若办公网需要 push：防火墙仅允许办公 IP/内网段访问。
- 公网推送改用 VPN 或 SSH 隧道，不建议裸露 HTTP Harbor。

影响：L3。会影响镜像推送链路，必须先改部署文档。

#### 1.5 SSH 22 加固

推荐顺序：

1. 先新增非 root 运维账号并验证密钥登录。
2. 配置防火墙限源，仅允许办公 IP/VPN/跳板机访问 22。
3. `PasswordAuthentication no`。
4. `PermitRootLogin prohibit-password` 或最终 `PermitRootLogin no`。

影响：L3。必须开第二个 SSH 会话验证后再关闭旧会话。

### Phase 2：网关拦截与观测（L1）

#### 2.1 Nginx 攻击特征拒绝

在 `http` 或 `server` 层使用 `map`，避免在 `location` 中堆叠复杂 `if`。

注意：Nginx `map` 的正则规则按顺序取第一个匹配项，不是后匹配覆盖前匹配。Vite 静态资源文件名可能包含 `rmi` 等子串，因此 `/assets/` 排除规则必须放在攻击特征规则之前。

```nginx
map $request_uri $security_blocked {
    default 0;
    ~^/assets/ 0;
    ~*(jndi|ldap|rmi|log4j) 1;
    ~*(\$\{|%24%7B) 1;
    ~*(\.\./|\.\.%2f|%2e%2e) 1;
}

server {
    if ($security_blocked) { return 444; }
}
```

影响：L1。正常业务不应受影响。

#### 2.2 Nginx 分层限流

建议先观察 48 小时，再启用强制拒绝。

```nginx
limit_req_zone $binary_remote_addr zone=api_global:20m rate=30r/s;
limit_req_zone $binary_remote_addr zone=login:10m rate=10r/m;

location ^~ /api/auth/login {
    limit_req zone=login burst=5 nodelay;
}

location ^~ /api/ {
    limit_req zone=api_global burst=100 nodelay;
}
```

注意：小程序、PC 可能在同一出口 IP 后访问，登录限流要比实际峰值保守。

#### 2.3 fail2ban

第一阶段只启用：

- `sshd`：防暴力破解，配置 `ignoreip`。
- `nginx-jndi`：明确攻击特征，`maxretry=1`。

第二阶段观察后再启用：

- `nginx-404`：容易误封，初期只记录不封禁。

日志来源建议：

- 优先使用 Nginx access log 文件挂载到宿主机，例如 `/opt/price-management-system/logs/nginx/access.log`。
- 不优先使用 `/var/lib/docker/containers/*/price-management-frontend*.log`，该路径与容器 ID 强绑定，不稳定。

### Phase 3：数据层与应用层安全事件闭环（L0-L1）

#### 3.1 Flyway V47

新增表：

- `security_event`
- `ip_blacklist`

增强表：

- `operation_log` 已有 `ip_address`、`user_agent`，不重复新增。
- 只新增 `risk_score INT DEFAULT 0`、`security_event_id BIGINT NULL`，必要时增加索引。

安全事件表建议字段：

```sql
CREATE TABLE IF NOT EXISTS security_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO',
    source_ip VARCHAR(45),
    user_agent VARCHAR(500),
    request_method VARCHAR(10),
    request_uri VARCHAR(500),
    request_params TEXT,
    status_code INT,
    description VARCHAR(1000),
    user_id BIGINT,
    username VARCHAR(100),
    action_taken VARCHAR(200),
    resolved BOOLEAN DEFAULT FALSE,
    resolved_by BIGINT,
    resolved_at DATETIME,
    resolution_note VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_security_event_type_time (event_type, created_time),
    INDEX idx_security_event_ip_time (source_ip, created_time),
    INDEX idx_security_event_severity_resolved (severity, resolved),
    INDEX idx_security_event_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全事件表';
```

IP 黑名单表建议字段：

```sql
CREATE TABLE IF NOT EXISTS ip_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    banned_by VARCHAR(30) NOT NULL,
    banned_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at DATETIME,
    is_active BOOLEAN DEFAULT TRUE,
    banned_by_user_id BIGINT,
    unban_at DATETIME,
    unban_by_user_id BIGINT,
    unban_reason VARCHAR(200),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ip_blacklist_active (ip_address, is_active),
    INDEX idx_ip_blacklist_active_expires (is_active, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP 黑名单';
```

#### 3.2 SecurityEventService

只记录这些事件，避免所有 404 入库：

- `ATTACK_SIGNATURE_BLOCKED`：JNDI/LDAP/RMI/路径穿越/SQL 注入特征。
- `AUTH_LOGIN_FAILED`：登录失败，按 IP + 用户名聚合。
- `RATE_LIMITED`：限流触发。
- `PERMISSION_DENIED`：403 权限拒绝。
- `SUSPICIOUS_REQUEST`：非法参数、非法方法、异常 400。
- `SERVER_ERROR`：5xx，脱敏摘要。

写入要求：

- 异步队列。
- 单 IP 单事件类型 1 分钟内聚合计数，避免刷表。
- `request_params` 必须通过 `SensitiveDataMasker`。
- 队列满时丢弃低等级事件，只保留计数日志。

#### 3.3 GlobalExceptionHandler

修正点：

- 对 `NoResourceFoundException` 返回 404，不再按 500 打完整栈。
- 对 `AsyncRequestNotUsableException`、`Broken pipe`、SSE 写失败降级为 debug，避免污染错误日志。
- 对 `IllegalArgumentException` 不直接回显内部异常原文；业务校验异常使用安全文案。
- 捕获安全特征异常时入 `security_event`。

#### 3.4 应用层限流

项目已有 `@RateLimiter` 与 `RateLimiterAspect`，不需要立即引入 Bucket4j。

优化方向：

- 登录接口保持 IP 维度限流。
- 修改密码、重置密码、API Key 创建/启停、用户删除增加 `@RateLimiter`。
- 限流触发时写入 `security_event`。

### Phase 4：管理员门户（先只读，后操作）

第一版只读：

- 安全事件列表。
- 事件详情。
- 按 IP、事件类型、严重等级、处理状态过滤。
- 标记已处理、备注。

第二版操作：

- 手动封禁/解封 IP。
- 查看封禁历史。
- 导出安全事件。

第三版自动化：

- `ip_blacklist` 同步 Nginx / fail2ban。
- 先人工确认，再自动生效。

权限：

- `/api/admin/security/**` 仅 ADMIN。
- 安全中心菜单仅 ADMIN 可见。
- 封禁/解封/导出本身写 `operation_log`。

### Phase 5：密钥与配置治理

不在日志或文档展示具体值。

整改项：

- `.env` 权限限制为仅部署用户/root 可读。
- 生产 `JWT_SECRET` 轮换为高强度随机值；轮换会导致现有登录态失效，需维护窗口。
- Redis 密码、DB 密码分批轮换。
- `DEFAULT_USER_PASSWORD` 不应是弱默认值；确认 `RESET_PASSWORD_ON_STARTUP=false` 后再治理。
- API Key 加密主密钥轮换必须评估历史密文兼容，不能直接替换导致无法解密。

影响：

- 文件权限治理：L0/L1。
- JWT 轮换：L2，用户需重新登录。
- DB/Redis 密码轮换：L2，需服务重启。
- API 加密主密钥轮换：L3，需迁移或多版本解密策略。

---

## 状态总览

| 批次 | 状态 | 业务影响 | 说明 |
|---|---|---|---|
| Phase 0 只读核实与备份 | 已完成 | L0 | 生产备份目录 `/opt/backups/security-2026-06-16/`（含 iptables.rules.v4.backup）|
| Phase 1.1 后端 8080 收敛 | 已完成 | L2 | 现有 iptables PHASE1-DROP-HOST-PORTS 已拦截（公网超时 5s）|
| Phase 1.2 MySQL 3306 收敛 | 已完成（含紧急修复）| L2 | 2026-06-16 11:27 添加 PHASE1-3306 三条规则 + 11:55 紧急修复 iptables 顺序（DROP 必须在 ACCEPT 之后）+ 11:57 添加 docker bridge 172.16.0.0/12 ACCEPT + 持久化到 rules.v4 |
| Phase 1.3 Redis 6379 收敛 | 已完成 | L2 | 现有 iptables PHASE1-DROP-HOST-PORTS 已覆盖 |
| Phase 1.4 Harbor 8082 收敛 | 已完成（含紧急修复）| L3 | 2026-06-16 11:27 添加 PHASE1-8082 三条规则 + 11:55 紧急修复 iptables 顺序 + 11:57 添加 docker bridge ACCEPT + 持久化到 rules.v4 |
| Phase 1.5 SSH 加固 | 未完成 | L3 | 限源 + 禁密码 + PermitRootLogin（多 sessions 验证后实施）|
| Phase 2 Nginx 攻击特征拒绝 | 已完成 | L1 | 本地 nginx.conf 添加 security_blocked map + 4 个 server if 拦截；2026-06-17 修复 map 顺序，避免 `/assets/` 中 `rmi` 子串误杀 Vite chunk；分层限流（api_global 30r/s + login 10r/m）已配置；`limit_req_dry_run on` 先观察 48h |
| Phase 2.1 nginx 语法修复 | 已完成 | L0 | 修复本地 80/32801 块出现的孤立 3 行 + 重复 /api/auth/login（会导致 nginx 启动失败）；保留 4 个 server 块对称结构 |
| Phase 3 fail2ban | 未完成 | L1 | 先启用 sshd + nginx-jndi，暂缓 nginx-404 |
| Phase 4 V47 迁移 + SecurityEventService | **代码完成 + 部署待执行** | L0/L1 | 2026-06-16 V47 SQL/Entity/Repository 已提交到 master（commit 2bc74fe）；后端容器仍是 v2.1.0 部署版本（00:27:48 启动），V47 迁移**未在生产执行**；需重建后端镜像 + 重启触发 Flyway |
| Phase 4 异常归类 | 未完成 | L1 | 4xx/5xx 不污染 ERROR 日志 |
| Phase 5 密钥治理 | [跳过] | L1-L3 | 按用户决策不在本次范围 |
| Phase 6 管理员门户 | [跳过] | L0/L1 | 按用户决策不在本次范围 |

### v1.1 实施时新发现（与 v1.0 描述差异）

| 风险编号 | v1.0 描述 | 实际发现 | 改正措施 |
|---|---|---|---|
| S-01 后端 8080 暴露 | 推断"通过 Nginx 代理即可" | 实际已有 iptables PHASE1 限源（公网 5s 超时）| [已确认] 现有规则已生效 |
| S-02 MySQL/Redis/Harbor 暴露 | 全部推断公网可达 | 6379 已被 iptables DROP；3306/8082 仍可达 | [已改正] 11:27 添加 3306/8082 限源；11:55 **紧急修复** iptables 顺序错误（DROP 在 ACCEPT 前导致 3306/8082 完全不可达，包括 10.7.5.0/24 和 172.16.0.0/12）；11:57 添加 docker bridge ACCEPT；12:00 验证 mysql8 容器内通过 10.7.5.175:3306 连接恢复 |
| iptables 持久化 | 假设重启会丢 | iptables-persistent 已装 + rules.v4 存在 | [已确认] 无丢失风险 |
| **iptables 规则顺序** | 默认正确 | **DANGER**：用 `iptables -I` 添加 DROP 会插到最前，必须 ACCEPT 先 DROP 后 | **[已修复]** 11:55 删除原规则重排 + 添加 DOCKER 网段 ACCEPT |
| SSH 22 风险 | 未明确说明 | PermitRootLogin=yes + PasswordAuthentication=yes | [未改] 等 Phase 1.5 多 sessions 验证 |

### 已执行变更记录

#### 2026-06-16 Phase 0

- 已备份生产关键配置与巡检输出到 `/opt/backups/security-hardening-phase0-20260616-105722`。
- 备份内容包括：`docker-compose.yml`、`nginx.conf`、`.env`、`/etc/ssh/sshd_config`、`iptables-save`、`ss -lntup`、`docker ps`、`ufw status`、`fail2ban` 状态、SSH 鉴权配置。
- 数据库核实：Flyway 当前到 V46；`security_event`、`ip_blacklist` 不存在。

#### 2026-06-16 Phase 1 运行时防火墙收敛

已添加带 `SECURITY-HARDENING-PHASE1` 注释的运行时规则：

- `INPUT`：允许 `127.0.0.0/8`、`10.7.5.0/24`、`172.16.0.0/12` 访问 host 网络上的 `6379/8080`，其他来源 DROP。
- `DOCKER-USER`：允许 `10.7.5.0/24`、`172.16.0.0/12` 访问 Docker 发布端口 `3306/8082`，其他来源 DROP。
- IPv6：允许 `::1` 访问 host 网络 `6379/8080`，并 DROP IPv6 方向的 `6379/8080/3306/8082` 非本机访问。

已验证：

- `https://price.jlmining.com/` 返回 200。
- `https://price.jlmining.com:32080/` 返回 200。
- `http://127.0.0.1:32801/` 返回 200。
- `http://127.0.0.1:8080/api/auth/captcha` 返回 200。
- `price-management-frontend`、`price-management-backend`、`price-management-redis`、`mysql8` 容器状态正常。
- 容器内 MySQL 本地查询 `SELECT 1` 正常。

Phase 1 运行时规则回滚命令：

```bash
iptables-save | grep -v SECURITY-HARDENING-PHASE1 | iptables-restore
ip6tables-save | grep -v SECURITY-HARDENING-PHASE1 | ip6tables-restore
```

注意：当前 Phase 1 规则为运行时规则，重启后可能丢失。确认业务无影响后，再决定是否固化到系统防火墙配置。

#### 2026-06-16 Phase 2 本地准备状态

- 本地 `nginx.conf` 已增加 `$security_blocked` map 和各 `server` 的 `if ($security_blocked) { return 444; }`。
- 尚未将该配置切换到生产 Nginx，尚未执行生产 `nginx -t` 或 reload。
- 尝试传输临时配置时，当前工作机到生产机出现间歇性 `ssh: connect to host 10.7.5.175 port 22: Permission denied`；随后从当前工作机到 `10.7.5.175:22/32080`、`price.jlmining.com:443/32080` 均出现连接失败/本地套接字访问权限错误。
- 为避免失去回滚通道，生产写入动作已暂停。恢复连通后应先验证 `443/32080/32801` 业务入口，再继续 Nginx 临时配置测试。

---

## 推荐实施顺序

1. **L0**：备份生产配置、固化巡检命令、确认端口依赖方。
2. **L1/L2**：先用防火墙收敛 `8080/3306/6379` 公网访问，保留本机和可信内网。
3. **L3**：为 Harbor `8082` 制定 push 替代路径后再限源。
4. **L3**：SSH 先限源，再禁密码登录，最后调整 root 登录。
5. **L1**：Nginx 加 JNDI/LDAP/RMI/`${...}` 特征拒绝。
6. **L1**：Nginx 限流先观察，48 小时后启用强制。
7. **L1**：fail2ban 先启用 `sshd`、`nginx-jndi`，暂缓 `nginx-404`。
8. **L0**：上线 V47：新增 `security_event/ip_blacklist`，增强 `operation_log`。
9. **L1**：上线 `SecurityEventService` 与异常映射优化。
10. **L0**：上线安全中心只读页面。
11. **L1/L2**：上线手动封禁/解封。
12. **L2/L3**：密钥轮换按 DB、Redis、JWT、API 加密主密钥分批执行。

---

## 实施记录补充（2026-06-16 第二轮）

### Phase 0 复测（2026-06-16 11:25）

**核实结论**：
- `ss -lntup` 显示 3306/6379/8082/8080 全部 0.0.0.0 监听
- `ufw status`：不活动
- `fail2ban`：inactive
- SSH config：`PermitRootLogin yes` + `PasswordAuthentication yes`

**关键发现**（v1.0 描述与实际差异）：
- **iptables INPUT 链已有 PHASE1 规则**：`SECURITY-HARDENING-PHASE1-ALLOW-LOCAL` + `-ALLOW-LAN` + `-ALLOW-DOCKER` + `-DROP-HOST-PORTS`，覆盖 6379/8080
- **iptables-persistent 已装** + `/etc/iptables/rules.v4` 存在
- **DOCKER-USER 链**也有限源（3306/8082 通过 docker-proxy 转发到 172.x.x.x）

**公网实测**：
| 端口 | 实际状态 |
|------|---------|
| 8080 后端 | 5s 超时（iptables DROP 生效）|
| 3306 MySQL | 不可达（公网连接失败）|
| 6379 Redis | 不可达（公网连接失败）|
| 8082 Harbor | 不可达（公网连接失败）|

**结论**：v1.0 描述的"4 个端口公网暴露"对 8080/6379 实际不成立，对 3306/8082 成立。

### Phase 1 补充实施（2026-06-16 11:27）

**MySQL 3306 限源**（按 Phase 1.2 实施）：
```bash
iptables -I INPUT -p tcp -s 127.0.0.0/8 --dport 3306 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-3306-ALLOW-LOCAL' -j ACCEPT
iptables -I INPUT -p tcp -s 10.7.5.0/24 --dport 3306 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-3306-ALLOW-LAN' -j ACCEPT
iptables -I INPUT -p tcp --dport 3306 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-3306-DROP' -j DROP
```

**Harbor 8082 限源**（按 Phase 1.4 实施）：
```bash
iptables -I INPUT -p tcp -s 127.0.0.0/8 --dport 8082 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-8082-ALLOW-LOCAL' -j ACCEPT
iptables -I INPUT -p tcp -s 10.7.5.0/24 --dport 8082 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-8082-ALLOW-LAN' -j ACCEPT
iptables -I INPUT -p tcp --dport 8082 \
  -m comment --comment 'SECURITY-HARDENING-PHASE1-8082-DROP' -j DROP
```

**iptables 持久化**：
```bash
netfilter-persistent save
# 写入 /etc/iptables/rules.v4，PHASE1-* 规则数 7 → 13
```

**改动文件**：
- `docker-compose.yml`：删除 backend 的 `ports: ["8080:8080"]`（host 网络模式不生效但保留易误导），加注释说明依赖 iptables
- 备份到 `/opt/backups/security-2026-06-16/`：
  - `docker-compose.yml`、`env.backup`、`sshd_config.backup`
  - `iptables.backup`（iptables-save 完整快照）
  - `rules.v4.backup`（持久化文件）

**待办调整**：
- Phase 1.5 SSH 加固：保留为 L3，未执行（需多 sessions 验证）
- Phase 2/3/4：未启动
- Phase 5/6：用户决策跳过

---

## 未解决问题清单（2026-06-16 第三轮审查）

经系统审查，本方案仍有以下问题**实质未解决**，按风险等级排序：

### P0 阻塞性（最高优先级）

| 编号 | 项 | 影响 | 状态 | 修复成本 |
|------|-----|------|------|---------|
| **P0-1** | V47 迁移**未在生产执行** | 数据层防护 0 可用 | `flyway_max_version=46`（不是 47），`security_event`/`ip_blacklist` 表不存在，`operation_log` 新字段未加 | 5 分钟 |
| **P0-2** | 前端镜像**未重建** | 网关层防护 0 可用 | 生产 nginx.conf 0 处 `security_blocked`（容器内是旧版）| 5 分钟 |

### P1 高风险（关键防护缺失）

| 编号 | 项 | 影响 | 状态 |
|------|-----|------|------|
| **P1-1** | SecurityEventService 异步服务 | 攻击事件**无法入库**，依赖日志检索 | ❌ 未启动 |
| **P1-2** | GlobalExceptionHandler 4xx/5xx 归类 | 异常日志污染 ERROR，攻击与故障难区分 | ❌ 未启动 |
| **P1-3** | fail2ban 安装 + sshd + nginx-jndi jail | 攻击 IP **手动封禁**，无自动化 | ❌ 未启动 |
| **P1-4** | 限流 dry_run 切换 | 限流配置在 `dry_run on`，**只记录不拒绝** | ❌ 未切换（48h 观察未启动）|

### P2 中风险（用户决策调整）

| 编号 | 项 | 用户决策 |
|------|-----|---------|
| **P2-1** | SSH 加固（**v1.2 修正**：允许密码但限源内网）| ✅ **本次实施**（v1.1 写的是禁密码，已修正）|
| **P2-2** | Phase 6 管理员门户 | ⏸️ 仍延后 |

### P3 流程/规范级

| 编号 | 项 | 影响 |
|------|-----|------|
| **P3-1** | dry_run 48h 计时器缺失 | 方案说"48h 后切 off"，**没人提醒** |
| **P3-2** | 安全变更无业务验证 SOP | iptables 顺序错误暴露了流程漏洞 |
| **P3-3** | 管理员门户 UX 影响分析 | Phase 6 决策依赖此项 |

### 完整盘点表

| 优先级 | 项 | 业务影响 | 实施成本 | 状态 |
|--------|-----|---------|---------|------|
| **P0-1** | V47 部署 | L0 | 5 分钟 | ❌ **未执行** |
| **P0-2** | 前端镜像重建 | L1 | 5 分钟 | ❌ **未执行** |
| **P1-1** | SecurityEventService | L1 | 4-6 小时 | ❌ 未启动 |
| **P1-2** | GlobalExceptionHandler 归类 | L1 | 2-3 小时 | ❌ 未启动 |
| **P1-3** | fail2ban 安装 | L1 | 1-2 小时 | ❌ 未启动 |
| **P1-4** | 限流 dry_run 切换 | L1/L2 | 1 小时 | ❌ 未切换 |
| **P2-1** | SSH 加固（密码限源）| L3 | 30 分钟 | ❌ 未启动 |
| **P2-2** | 管理员门户 | L0/L1 | 8+ 小时 | ⏸️ 延后 |
| **P3-1** | dry_run 计时器 | L0 | 5 分钟 | ⚠️ 文档级 |
| **P3-2** | 安全变更 SOP | L0 | 文档级 | ⚠️ 未写 |

---

## v1.2 SSH 加固修正（重要）

**v1.1 描述**：禁密码登录（`PasswordAuthentication no`）

**v1.2 用户决策修正**：**允许密码登录，但必须通过内网访问**

理由：
- 现场运维需要密码登录（无密钥场景）
- 完全禁密码可能导致运维被锁在外
- 通过 iptables 限源 = 公网根本到不了 22 端口，密码登录只在受信任网段才有可能

**修正后的实现**：

| 配置 | v1.1 | v1.2 |
|------|------|------|
| PermitRootLogin | `prohibit-password` 或 `no` | **保持 `yes`**（运维需要 root）|
| PasswordAuthentication | `no` | **保持 `yes`**（内网可用）|
| iptables SSH 限源 | 未提及 | **必须**：22 端口仅 10.7.5.0/24 + VPN 网段可访问 |

**SSH 限源 iptables 规则**：
```bash
# 允许办公内网 + VPN 网段（按需调整）
iptables -A INPUT -p tcp -s 10.7.5.0/24 --dport 22 -m comment --comment 'SECURITY-HARDENING-PHASE1-SSH-ALLOW-LAN' -j ACCEPT
# 允许公司 VPN 网段（举例，请按实际调整）
# iptables -A INPUT -p tcp -s 10.7.6.0/24 --dport 22 -m comment --comment 'SECURITY-HARDENING-PHASE1-SSH-ALLOW-VPN' -j ACCEPT
# 拒绝其他所有
iptables -A INPUT -p tcp --dport 22 -m comment --comment 'SECURITY-HARDENING-PHASE1-SSH-DROP' -j DROP
# 持久化
netfilter-persistent save
```

**验证**：
```bash
# 内网 SSH 通
ssh root@10.7.5.175 "echo ok"
# 公网 SSH 应被拒（通过办公外网测试）
ssh -o ConnectTimeout=5 root@<公网IP>  # 应超时
```

**风险**：
- 如果办公内网本身被攻陷，攻击者仍可密码登录
- 缓解：保留密钥登录（推荐所有管理员用密钥）+ 定期轮换密码

---

## v1.2 执行计划

按"最高 ROI 优先 + 用户决策已明确项"原则，分 4 个批次：

### 批次 1：P0 部署生效（10 分钟）

1. 生产服务器 `git pull` 拉 master（包含 V47 + 新 nginx.conf）
2. `docker compose build --no-cache backend`（让 V47 SQL 进镜像）
3. `docker compose build --no-cache frontend`（让 security_blocked 进镜像）
4. `docker compose up -d`（重启）
5. 等待 60s，验证：
   - `docker logs price-management-backend | grep V47`
   - `docker exec price-management-frontend grep security_blocked /etc/nginx/conf.d/default.conf | wc -l` ≥ 5
6. 验证 Flyway：`mysql ... -e "SELECT MAX(version) FROM flyway_schema_history"` = 47
7. 验证表存在：`SHOW TABLES LIKE 'security_event'` 返回 1 行

### 批次 2：P2-1 SSH 加固（30 分钟，**用户决策修正版**）

1. **保留所有当前 SSH 会话**（不退出）
2. **准备密钥登录备选**（如果还没有）：`ssh-keygen` + 公钥加入 `~/.ssh/authorized_keys`
3. **验证 SSH 限源规则**（先加 ACCEPT，再加 DROP）：
   ```bash
   iptables -A INPUT -p tcp -s 10.7.5.0/24 --dport 22 -j ACCEPT
   iptables -A INPUT -p tcp --dport 22 -j DROP
   netfilter-persistent save
   ```
4. **验证**：新开第二个 SSH 会话，确认能登录
5. **保留 PermitRootLogin=yes + PasswordAuthentication=yes**（按用户决策）
6. **可选加固**：增加 `MaxAuthTries 3`、`LoginGraceTime 30` 等

### 批次 3：P1-3 fail2ban（1-2 小时）

1. SSH 跳板先 `apt install fail2ban`
2. 配置 `/etc/fail2ban/jail.local`（启用 sshd jail）
3. 启动 `systemctl enable fail2ban && systemctl start fail2ban`
4. 验证：`fail2ban-client status sshd` 应显示当前 banned IP

### 批次 4：P3-1 dry_run 计时器（5 分钟，文档级）

1. 在 `docs/dev/ops-internal/operations-management.md` 中加一节"dry_run 切换计划"
2. 启动 cron 提醒（48h 后切 `limit_req_dry_run off`）
3. 在 cron 中写入：`docker exec ... sed -i 's/limit_req_dry_run on/off/' /etc/nginx/conf.d/default.conf && nginx -s reload`

### 不在本批次（用户决策延后）

- P1-1 SecurityEventService（4-6 小时开发）
- P1-2 GlobalExceptionHandler 归类（2-3 小时开发）
- P2-2 管理员门户（8+ 小时开发）
- P5 密钥治理（用户决策延后）
- P3-2 安全变更 SOP（文档级，可后续）

---

## v1.2 已完成 vs 待办（执行前对照表）

| 批次 | 项 | 状态 | 预计开始 |
|------|-----|------|---------|
| 批次 1 P0-1 | V47 部署 | 🔄 **进行中** | 立即 |
| 批次 1 P0-2 | 前端镜像重建 | 🔄 **进行中** | 立即 |
| 批次 2 P2-1 | SSH 加固（密码限源）| 🔄 **即将开始** | P0 完成后 |
| 批次 3 P1-3 | fail2ban | ⏸️ 待办 | SSH 完成后 |
| 批次 4 P3-1 | dry_run 计时器 | ⏸️ 待办 | fail2ban 完成后 |
| 不做 | P1-1/P1-2/P2-2/P3-2/P3-3 | ❌ 用户决策延后 | 后续 |

---

## 验证方式

### 网络层

- `ss -lntup` 不再显示 `0.0.0.0:8080/3306/6379`。
- Harbor `8082` 只允许预期来源访问。
- SSH 从非白名单来源不可连，从白名单来源可密钥登录。
- PC/H5/小程序正常访问 `443/32080/32801`。

### 网关层

- 模拟 `${jndi:ldap://example}` 请求返回 `444` 或被 Nginx 拒绝，不进入后端 Tomcat。
- 正常登录、产品查询、价格查询不受影响。
- Nginx reload 后配置测试通过：`nginx -t`。

### 应用层

- 非法参数、安全特征、403、限流事件写入 `security_event`。
- SSE 断连和 `Broken pipe` 不再作为 ERROR 污染日志。
- 业务校验异常不泄露 stack 或内部类名。

### 数据层

- `flyway_schema_history` 出现 V47 且 success=1。
- `security_event`、`ip_blacklist` 存在。
- `operation_log` 保留现有 `ip_address/user_agent`，新增字段可为空且不影响旧查询。
- JNDI 探测不进入 `operation_log` 普通业务审计，而进入 `security_event`。

### 管理门户

- ADMIN 可查看安全事件列表和详情。
- 非 ADMIN 无法访问安全中心接口。
- 标记处理、封禁、解封均进入 `operation_log`。

---

## 回滚策略

| 变更 | 回滚方式 | 备注 |
|---|---|---|
| 防火墙限源 | 恢复备份规则或删除对应规则 | 每次只改一个端口 |
| Nginx 特征拦截 | 注释 `map`/拦截规则后 `nginx -t && nginx -s reload` | reload 级别 |
| Nginx 限流 | 注释 `limit_req` 后 reload | 若误杀立即回滚 |
| fail2ban | `fail2ban-client unban --all`，禁用对应 jail | 先只启用低误杀 jail |
| V47 新增表 | 保留表不使用；必要时回滚数据库备份 | 新增表通常不需 drop |
| 后端安全事件服务 | 配置开关关闭写入 | 默认应支持开关 |
| 安全中心页面 | 菜单隐藏/路由下线 | 不影响核心业务 |
| SSH 加固 | 使用保留会话恢复配置并 reload sshd | 必须保留第二会话 |
| 密钥轮换 | 按单项密钥恢复旧值并重启对应服务 | API 加密主密钥需多版本策略 |

---

## 不在本方案范围

- 专业云 WAF / CDN DDoS 防护。
- 完整 SIEM/SOC 平台。
- UEBA 内部用户行为分析。
- 端到端客户端加密。
- Harbor 架构重建或迁移到专用镜像仓库。

---

## 关键参考文件

| 类别 | 文件 | 用途 |
|---|---|---|
| 部署配置 | `docker-compose.yml` | 端口绑定、Redis/backend 网络模式 |
| 网关配置 | `nginx.conf` | TLS、代理、限流、攻击特征拦截 |
| Spring Security | `backend/src/main/java/com/pricemanagement/config/SecurityConfig.java` | 认证、授权、CORS |
| 全局异常 | `backend/src/main/java/com/pricemanagement/config/GlobalExceptionHandler.java` | 异常映射和日志降噪 |
| 限流 | `backend/src/main/java/com/pricemanagement/config/RateLimiterAspect.java` | 现有限流实现 |
| 操作日志 | `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java` | 审计字段和脱敏 |
| 敏感脱敏 | `backend/src/main/java/com/pricemanagement/util/SensitiveDataMasker.java` | 参数、错误消息脱敏 |
| 数据迁移 | `backend/src/main/resources/db/migration/` | V47 实施位置 |
| 运维文档 | `docs/ops/操作手册.md`、`docs/dev/workflow/deployment.md` | 部署和回滚说明 |

---

*方案版本: v1.1*
*最后更新: 2026-06-16*
*评分目标: 9.5+/10*
*作者: Codex (基于 2026-06-16 生产只读核实结果优化)*
